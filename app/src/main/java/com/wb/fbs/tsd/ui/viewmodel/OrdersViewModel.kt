package com.wb.fbs.tsd.ui.viewmodel

import com.wb.fbs.tsd.data.db.OrderDao
import com.wb.fbs.tsd.data.repository.ApiException
import com.wb.fbs.tsd.data.repository.WbRepository
import kotlinx.coroutines.delay
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wb.fbs.tsd.data.db.OrderEntity
import com.wb.fbs.tsd.data.repository.KizScanResult
import com.wb.fbs.tsd.data.repository.ScanResult
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class OrdersUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val lastSyncCount: Int = 0,
    val lastSyncTime: Long = 0,
    val scanResult: ScanUiResult? = null,
    val sgtinSaved: Boolean = false,
    val createdSupplyId: String? = null,
    val kizValidation: KizValidationUiResult? = null,
    val supplyQrSvg: String? = null,
    val supplyStage: Int = 0  // 0=сборка, 1=грузоместа, 2=передача
)

sealed class ScanUiResult {
    data class Success(
        val orderId: Long,
        val article: String,
        val name: String,
        val size: String?,
        val requiresSgtin: Boolean,
        val groupScanned: Int = 0,
        val groupTotal: Int = 1,
        val cargoType: Int = 1,
        val onDevice: Int = 0,
        val onServer: Int = 0
    ) : ScanUiResult()
    object NotFound : ScanUiResult()
    data class Error(val message: String) : ScanUiResult()
}

class OrdersViewModel(private val repository: WbRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(OrdersUiState())
    val uiState: StateFlow<OrdersUiState> = _uiState.asStateFlow()

    val newOrders: StateFlow<List<OrderEntity>> = repository.getNewOrders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Автосинхронизация при старте — только если есть API.
        // loadOrders() обёрнут в try/catch: если WB API недоступен
        // или токен протух, приложение не должно падать при запуске.
        // Пользователь увидит ошибку в UI, а не краш.
        if (repository.hasApi) {
            loadOrders()
        }
    }

    fun loadOrders() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                repository.syncNewOrders()
                    .onSuccess { count ->
                        // Подтягиваем реальные размеры через Content API.
                        // Не падает если Content API недоступен — syncProductSizes
                        // имеет внутренний try/catch.
                        try {
                            repository.syncProductSizes()
                        } catch (e: Throwable) {
                            // Не критично — размеры подтянутся позже
                        }
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                lastSyncCount = count,
                                lastSyncTime = System.currentTimeMillis()
                            )
                        }
                    }
                    .onFailure { error ->
                        val message = when (error) {
                            is ApiException.Unauthorized -> "Токен невалиден. Выйдите и войдите заново."
                            is ApiException.RateLimit -> "Слишком много запросов. Подождите..."
                            is ApiException.ServerError -> "Сервер WB временно недоступен. Повторите позже."
                            is ApiException.Timeout -> "Медленное соединение. Проверьте WiFi."
                            is ApiException.NoInternet -> "Нет интернета. Заказы сохранены локально."
                            else -> "Ошибка: ${error.message}"
                        }
                        _uiState.update { it.copy(isLoading = false, error = message) }
                    }
            } catch (e: Throwable) {
                // Ловим даже Error (OutOfMemoryError, NoClassDefFoundError и т.д.)
                _uiState.update { it.copy(isLoading = false, error = "Ошибка синхронизации: ${e.message}") }
            }
        }
    }

    fun unmarkOrderScanned(orderId: Long) {
        viewModelScope.launch {
            repository.unmarkOrderScanned(orderId)
        }
    }

    fun markOrderPacked(orderId: Long) {
        viewModelScope.launch {
            repository.markOrderPacked(orderId)
        }
    }

    fun unmarkOrderPacked(orderId: Long) {
        viewModelScope.launch {
            repository.unmarkOrderPacked(orderId)
        }
    }

    fun syncOrderStatuses() {
        viewModelScope.launch {
            repository.syncOrderStatuses()
        }
    }

    fun scanWbSticker(stickerData: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(scanResult = null) }
            when (val result = repository.scanWbSticker(stickerData)) {
                is ScanResult.Success -> {
                    val order = result.order
                    // Считаем сколько собрано по группе этого артикула+размера
                    val groupOrders = newOrders.value.filter {
                        it.article == order.article && it.size == order.size
                    }
                    val groupScanned = groupOrders.count { it.scannedAt != null }
                    val groupTotal = groupOrders.size
                    val onDevice = newOrders.value.count { it.scannedAt != null }
                    val onServer = newOrders.value.count { it.isSynced }
                    _uiState.update {
                        it.copy(
                            scanResult = ScanUiResult.Success(
                                orderId = order.id,
                                article = order.article,
                                name = order.name,
                                size = order.size,
                                requiresSgtin = order.isMarked,
                                groupScanned = groupScanned,
                                groupTotal = groupTotal,
                                cargoType = order.cargoType,
                                onDevice = onDevice,
                                onServer = onServer
                            )
                        )
                    }
                }
                is ScanResult.NotFound -> {
                    _uiState.update { it.copy(scanResult = ScanUiResult.NotFound) }
                }
                is ScanResult.Error -> {
                    _uiState.update { it.copy(scanResult = ScanUiResult.Error(result.message)) }
                }
            }
        }
    }

    fun syncPendingOrders() {
        viewModelScope.launch {
            val unsynced = repository.getUnsyncedOrders()
            if (unsynced.isEmpty()) return@launch

            _uiState.update { it.copy(isLoading = true, error = null) }

            var successCount = 0
            var failCount = 0

            unsynced.forEach { order: OrderEntity ->
                if (order.sgtin != null && !order.isSynced) {
                    repository.scanSgtin(order.id, order.sgtin)
                        .onSuccess { successCount++ }
                        .onFailure { failCount++ }
                }
            }

            val message = when {
                failCount == 0 -> "Все $successCount заказов синхронизированы"
                successCount == 0 -> "Синхронизация не удалась. Проверьте интернет."
                else -> "Синхронизировано $successCount из ${successCount + failCount}"
            }

            _uiState.update { it.copy(isLoading = false, error = message) }
        }
    }

    fun scanBarcode(barcode: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(scanResult = null) }
            when (val result = repository.scanBarcode(barcode)) {
                is ScanResult.Success -> {
                    val order = result.order
                    val groupOrders = newOrders.value.filter {
                        it.article == order.article && it.size == order.size
                    }
                    val groupScanned = groupOrders.count { it.scannedAt != null }
                    val groupTotal = groupOrders.size
                    val onDevice = newOrders.value.count { it.scannedAt != null }
                    val onServer = newOrders.value.count { it.isSynced }
                    _uiState.update {
                        it.copy(
                            scanResult = ScanUiResult.Success(
                                orderId = order.id,
                                article = order.article,
                                name = order.name,
                                size = order.size,
                                requiresSgtin = order.isMarked,
                                groupScanned = groupScanned,
                                groupTotal = groupTotal,
                                cargoType = order.cargoType,
                                onDevice = onDevice,
                                onServer = onServer
                            )
                        )
                    }
                }

                is ScanResult.NotFound -> {
                    _uiState.update { it.copy(scanResult = ScanUiResult.NotFound) }
                }

                is ScanResult.Error -> {
                    _uiState.update { it.copy(scanResult = ScanUiResult.Error(result.message)) }
                }
            }
        }
    }

    fun scanSgtin(orderId: Long, sgtin: String) {
        viewModelScope.launch {
            repository.scanSgtin(orderId, sgtin)
                .onSuccess {
                    _uiState.update { it.copy(sgtinSaved = true) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message, sgtinSaved = false) }
                }
        }
    }

    fun clearScanResult() {
        _uiState.update { it.copy(scanResult = null, sgtinSaved = false) }
    }

    fun createSupply(name: String, orderIds: List<Long>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.createSupply(name)
                .onSuccess { supplyId ->
                    repository.addOrdersToSupply(supplyId, orderIds)
                        .onSuccess {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    createdSupplyId = supplyId
                                )
                            }
                        }
                        .onFailure { error ->
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    error = error.message
                                )
                            }
                        }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
        }
    }

    fun scanKiz(kizString: String) {
        viewModelScope.launch {
            when (val result = repository.scanKiz(kizString)) {
                is KizScanResult.Success -> {
                    val order = result.order
                    val groupOrders = newOrders.value.filter {
                        it.article == order.article && it.size == order.size
                    }
                    val groupScanned = groupOrders.count { it.scannedAt != null }
                    val groupTotal = groupOrders.size
                    val onDevice = newOrders.value.count { it.scannedAt != null }
                    val onServer = newOrders.value.count { it.isSynced }
                    _uiState.update {
                        it.copy(
                            scanResult = ScanUiResult.Success(
                                orderId = order.id,
                                article = order.article,
                                name = order.name,
                                size = order.size,
                                requiresSgtin = order.isMarked,
                                groupScanned = groupScanned,
                                groupTotal = groupTotal,
                                cargoType = order.cargoType,
                                onDevice = onDevice,
                                onServer = onServer
                            ),
                            sgtinSaved = true
                        )
                    }
                }

                is KizScanResult.OrderNotFound -> {
                    _uiState.update {
                        it.copy(
                            scanResult = ScanUiResult.Error("Заказ с GTIN ${result.gtin} не найден")
                        )
                    }
                }

                is KizScanResult.InvalidFormat -> {
                    _uiState.update {
                        it.copy(
                            scanResult = ScanUiResult.Error("Неверный формат КИЗ")
                        )
                    }
                }
            }
        }
    }

    fun markOrderScanned(orderId: Long) {
        viewModelScope.launch {
            repository.markOrderScanned(orderId)
        }
    }

    fun scanKizForOrder(orderId: Long, kizString: String) {
        viewModelScope.launch {
            repository.scanSgtin(orderId, kizString)
        }
    }

    fun createSupplyAndDeliver(orderIds: List<Long>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val supplyName =
                "Поставка от ${java.text.SimpleDateFormat("dd.MM.yyyy").format(java.util.Date())}"

            repository.createSupply(supplyName)
                .onSuccess { supplyId ->
                    repository.addOrdersToSupply(supplyId, orderIds)
                        .onSuccess {
                            repository.deliverSupply(supplyId)
                                .onSuccess {
                                    _uiState.update {
                                        it.copy(
                                            isLoading = false,
                                            createdSupplyId = supplyId
                                        )
                                    }
                                }
                                .onFailure { error ->
                                    _uiState.update {
                                        it.copy(
                                            isLoading = false,
                                            error = "Ошибка доставки: ${error.message}"
                                        )
                                    }
                                }
                        }
                        .onFailure { error ->
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    error = "Ошибка добавления: ${error.message}"
                                )
                            }
                        }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Ошибка создания: ${error.message}"
                        )
                    }
                }
        }
    }

    fun getSupplyQr(supplyId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.getSupplyBarcode(supplyId)
                .onSuccess { barcodeResp ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            supplyQrSvg = barcodeResp.file
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, error = error.message)
                    }
                }
        }
    }

    fun clearSupplyQr() {
        _uiState.update { it.copy(supplyQrSvg = null) }
    }
}

sealed class KizValidationUiResult {
    data class Valid(
        val gtin: String,
        val status: String,
        val productName: String
    ) : KizValidationUiResult()
    data class Invalid(val reason: String) : KizValidationUiResult()
    data class Error(val message: String) : KizValidationUiResult()
}
