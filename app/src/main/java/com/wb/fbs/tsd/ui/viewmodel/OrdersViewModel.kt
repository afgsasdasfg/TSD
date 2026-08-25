package com.wb.fbs.tsd.ui.viewmodel

import com.wb.fbs.tsd.data.db.OrderDao
import com.wb.fbs.tsd.data.repository.ApiException
import com.wb.fbs.tsd.data.repository.CrptRepository
import com.wb.fbs.tsd.data.repository.WbRepository
import kotlinx.coroutines.delay
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wb.fbs.tsd.data.db.OrderEntity
import com.wb.fbs.tsd.data.repository.KizScanResult
import com.wb.fbs.tsd.data.repository.ScanResult
import com.wb.fbs.tsd.data.network.CrptRetireKizResponse
import com.wb.fbs.tsd.data.network.CrptReturnKizResponse
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
    val supplyStage: Int = 0,  // 0=сборка, 1=грузоместа, 2=передача
    val crptHealth: Boolean? = null,  // null=не проверено, true=ок, false=заблокирован
    val crptTokenValid: Boolean? = null,  // токен ЧЗ авторизован?
    val kizCheckResult: KizCheckUiResult? = null  // результат проверки КИЗ через ЧЗ
)

sealed class KizCheckUiResult {
    data class Valid(
        val cis: String,
        val status: String,
        val statusDescription: String,
        val productName: String,
        val verified: Boolean
    ) : KizCheckUiResult()
    data class Invalid(val cis: String, val error: String) : KizCheckUiResult()
    data class Error(val message: String) : KizCheckUiResult()
    data class Retired(val cis: String) : KizCheckUiResult()
    data class Returned(val cis: String) : KizCheckUiResult()
}

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

class OrdersViewModel(
    private val repository: WbRepository,
    private val crptRepository: CrptRepository? = null
) : ViewModel() {

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
            // До синхронизации: запоминаем заказы с КИЗ (sgtin),
            // которые ещё не complete/cancel — чтобы после узнать какие перешли.
            val beforeOrders = repository.getActiveOrdersWithSgtin()
            val beforeActive = beforeOrders
                .filter { it.status != "complete" && it.status != "cancel" }
                .map { it.id }
                .toSet()

            repository.syncOrderStatuses()

            // После: находим заказы которые стали complete или cancel и имеют sgtin
            val afterOrders = repository.getActiveOrdersWithSgtin()

            // Авто-вывод при complete (продажа)
            val newlyComplete = afterOrders.filter {
                it.status == "complete" && it.id in beforeActive && !it.sgtin.isNullOrEmpty()
            }
            for (order in newlyComplete) {
                autoRetireOrderKiz(order)
            }

            // Авто-возврат при cancel (возврат товара)
            val newlyCancelled = afterOrders.filter {
                it.status == "cancel" && it.id in beforeActive && !it.sgtin.isNullOrEmpty()
            }
            for (order in newlyCancelled) {
                autoReturnOrderKiz(order)
            }
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

    // ==================== ЧЕСТНЫЙ ЗНАК (ЧЗ) ====================

    /**
     * Проверка kill switch / лицензии ЧЗ-сервера.
     * Вызывать при старте и перед КИЗ-операциями.
     */
    fun checkCrptHealth() {
        viewModelScope.launch {
            val healthy = crptRepository?.checkHealth() ?: false
            _uiState.update { it.copy(crptHealth = healthy) }
        }
    }

    /**
     * Проверка статуса токена ЧЗ (авторизован ли Алекс через КриптоПро).
     */
    fun checkCrptAuthStatus() {
        viewModelScope.launch {
            val result = crptRepository?.getAuthStatus()
            _uiState.update {
                it.copy(crptTokenValid = result?.getOrNull() ?: false)
            }
        }
    }

    /**
     * Проверка КИЗ через сервер ЧЗ.
     * Отправляет cis на /api/check-kiz, получает статус КИЗ.
     */
    fun checkKizOnServer(cis: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(kizCheckResult = null) }
            val result = crptRepository?.checkKiz(cis)
            if (result == null) {
                _uiState.update {
                    it.copy(kizCheckResult = KizCheckUiResult.Error("ЧЗ-сервер не инициализирован"))
                }
                return@launch
            }
            result.onSuccess { response ->
                if (response.valid) {
                    _uiState.update {
                        it.copy(kizCheckResult = KizCheckUiResult.Valid(
                            cis = response.cis ?: cis,
                            status = response.status ?: "UNKNOWN",
                            statusDescription = response.statusDescription ?: "",
                            productName = response.productName ?: "",
                            verified = response.verified ?: false
                        ))
                    }
                } else {
                    _uiState.update {
                        it.copy(kizCheckResult = KizCheckUiResult.Invalid(
                            cis = cis,
                            error = response.error ?: "КИЗ невалиден"
                        ))
                    }
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(kizCheckResult = KizCheckUiResult.Error(error.message ?: "Ошибка ЧЗ"))
                }
            }
        }
    }

    /**
     * Вывод КИЗ из оборота через сервер ЧЗ.
     */
    fun retireKizOnServer(cis: String, reason: String = "RETAIL") {
        viewModelScope.launch {
            _uiState.update { it.copy(kizCheckResult = null) }
            val result = crptRepository?.retireKiz(cis, reason)
            if (result == null) {
                _uiState.update {
                    it.copy(kizCheckResult = KizCheckUiResult.Error("ЧЗ-сервер не инициализирован"))
                }
                return@launch
            }
            result.onSuccess { response: CrptRetireKizResponse ->
                if (response.retired) {
                    _uiState.update {
                        it.copy(kizCheckResult = KizCheckUiResult.Retired(cis))
                    }
                } else {
                    _uiState.update {
                        it.copy(kizCheckResult = KizCheckUiResult.Invalid(
                            cis = cis,
                            error = response.error ?: "Не удалось вывести КИЗ"
                        ))
                    }
                }
            }.onFailure { error: Throwable ->
                _uiState.update {
                    it.copy(kizCheckResult = KizCheckUiResult.Error(error.message ?: "Ошибка ЧЗ"))
                }
            }
        }
    }

    /**
     * Возврат КИЗ в оборот через сервер ЧЗ (возврат товара).
     */
    fun returnKizOnServer(cis: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(kizCheckResult = null) }
            val result = crptRepository?.returnKiz(cis)
            if (result == null) {
                _uiState.update {
                    it.copy(kizCheckResult = KizCheckUiResult.Error("ЧЗ-сервер не инициализирован"))
                }
                return@launch
            }
            result.onSuccess { response: CrptReturnKizResponse ->
                if (response.returned) {
                    _uiState.update {
                        it.copy(kizCheckResult = KizCheckUiResult.Returned(cis))
                    }
                } else {
                    _uiState.update {
                        it.copy(kizCheckResult = KizCheckUiResult.Invalid(
                            cis = cis,
                            error = response.error ?: "Не удалось вернуть КИЗ"
                        ))
                    }
                }
            }.onFailure { error: Throwable ->
                _uiState.update {
                    it.copy(kizCheckResult = KizCheckUiResult.Error(error.message ?: "Ошибка ЧЗ"))
                }
            }
        }
    }

    /**
     * Авто-вывод КИЗ из оборота при продаже.
     * Вызывается когда заказ становится complete (продан).
     * Тихо выводит КИЗ в фон, ошибки не блокируют UI.
     */
    fun autoRetireOrderKiz(order: OrderEntity) {
        val sgtin = order.sgtin
        if (sgtin.isNullOrEmpty()) return
        viewModelScope.launch {
            try {
                val crpt = crptRepository ?: return@launch
                val result = crpt.retireKiz(sgtin)
                result.onSuccess { resp ->
                    if (resp.retired) {
                        android.util.Log.i("OrdersVM", "Авто-вывод КИЗ: ${sgtin.take(20)}... OK")
                    } else {
                        android.util.Log.w("OrdersVM", "Авто-вывод КИЗ: не выведен — ${resp.error}")
                    }
                }.onFailure { e ->
                    android.util.Log.w("OrdersVM", "Авто-вывод КИЗ не удался: ${e.message}")
                }
            } catch (e: Exception) {
                android.util.Log.w("OrdersVM", "Авто-вывод КИЗ exception: ${e.message}")
            }
        }
    }

    /**
     * Авто-возврат КИЗ в оборот при возврате товара.
     * Вызывается когда заказ возвращается (cancel).
     * Тихо возвращает КИЗ в фон, ошибки не блокируют UI.
     */
    fun autoReturnOrderKiz(order: OrderEntity) {
        val sgtin = order.sgtin
        if (sgtin.isNullOrEmpty()) return
        viewModelScope.launch {
            try {
                val crpt = crptRepository ?: return@launch
                val result = crpt.returnKiz(sgtin)
                result.onSuccess { resp ->
                    if (resp.returned) {
                        android.util.Log.i("OrdersVM", "Авто-возврат КИЗ: ${sgtin.take(20)}... OK")
                    } else {
                        android.util.Log.w("OrdersVM", "Авто-возврат КИЗ: не возвращён — ${resp.error}")
                    }
                }.onFailure { e ->
                    android.util.Log.w("OrdersVM", "Авто-возврат КИЗ не удался: ${e.message}")
                }
            } catch (e: Exception) {
                android.util.Log.w("OrdersVM", "Авто-возврат КИЗ exception: ${e.message}")
            }
        }
    }

    fun clearKizCheckResult() {
        _uiState.update { it.copy(kizCheckResult = null) }
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
