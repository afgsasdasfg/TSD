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

// ← СНАЧАЛА data class и sealed class
// ui/viewmodel/OrdersViewModel.kt — обновить OrdersUiState

data class OrdersUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val lastSyncCount: Int = 0,
    val lastSyncTime: Long = 0,  // ← НОВОЕ: timestamp последней синхронизации
    val scanResult: ScanUiResult? = null,
    val sgtinSaved: Boolean = false,
    val createdSupplyId: String? = null,
    val kizValidation: KizValidationUiResult? = null
)

sealed class ScanUiResult {
    data class Success(
        val orderId: Long,
        val article: String,
        val size: String?,
        val requiresSgtin: Boolean
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
        loadOrders()
        viewModelScope.launch {}
    }

// ui/viewmodel/OrdersViewModel.kt — обновить loadOrders()

    fun loadOrders() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.syncNewOrders()
                .onSuccess { count ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            lastSyncCount = count,
                            lastSyncTime = System.currentTimeMillis()  // ← СОХРАНЯЕМ ВРЕМЯ
                        )
                    }
                }
                .onFailure { error ->
                    val message = when (error) {
                        is ApiException.Unauthorized -> "🔑 Токен невалиден. Выйдите и войдите заново."
                        is ApiException.RateLimit -> "⏳ Слишком много запросов. Подождите..."
                        is ApiException.ServerError -> "🔧 Сервер WB временно недоступен. Повторите позже."
                        is ApiException.Timeout -> "⏱ Медленное соединение. Проверьте WiFi."
                        is ApiException.NoInternet -> "📡 Нет интернета. Заказы сохранены локально."
                        else -> "⚠️ Ошибка: ${error.message}"
                    }
                    _uiState.update { it.copy(isLoading = false, error = message) }
                }
        }
    }
    // Добавить в OrdersViewModel

    /**
     * Синхронизация несинхронизированных заказов (при восстановлении интернета)
     */
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
                failCount == 0 -> "✅ Все $successCount заказов синхронизированы"
                successCount == 0 -> "❌ Синхронизация не удалась. Проверьте интернет."
                else -> "⚠️ Синхронизировано $successCount из ${successCount + failCount}"
            }

            _uiState.update { it.copy(isLoading = false, error = message) }
        }
    }

    fun scanBarcode(barcode: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(scanResult = null) }
            when (val result = repository.scanBarcode(barcode)) {
                is ScanResult.Success -> {
                    _uiState.update {
                        it.copy(
                            scanResult = ScanUiResult.Success(
                                orderId = result.order.id,
                                article = result.order.article,
                                size = result.order.size,
                                requiresSgtin = result.order.isMarked
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
                    _uiState.update { it.copy(error = error.message) }
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
                    _uiState.update {
                        it.copy(
                            scanResult = ScanUiResult.Success(
                                orderId = result.order.id,
                                article = result.order.article,
                                size = result.order.size,
                                requiresSgtin = result.order.isMarked
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

            // 1. Создаём поставку
            val supplyName =
                "Поставка от ${java.text.SimpleDateFormat("dd.MM.yyyy").format(java.util.Date())}"

            repository.createSupply(supplyName)
                .onSuccess { supplyId ->
                    // 2. Добавляем заказы
                    repository.addOrdersToSupply(supplyId, orderIds)
                        .onSuccess {
                            // 3. Передаём в доставку
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