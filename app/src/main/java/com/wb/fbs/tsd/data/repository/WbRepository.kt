package com.wb.fbs.tsd.data.repository

import retrofit2.Response
import kotlin.Result
import kotlinx.coroutines.delay
import com.wb.fbs.tsd.data.db.*
import com.wb.fbs.tsd.data.network.*
import com.wb.fbs.tsd.utils.KizParser
import com.wb.fbs.tsd.utils.WbStickerParser
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.flow.first
class WbRepository(
    private val orderDao: OrderDao,
    private val supplyDao: SupplyDao,
    private val scanLogDao: ScanLogDao
) {
    private var apiService: WbApiService? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

    fun setApiService(service: WbApiService) {
        apiService = service
    }

    private fun requireApi(): WbApiService {
        return apiService ?: throw IllegalStateException(
            "WB API не инициализирован. Войдите через настройки."
        )
    }

    val hasApi: Boolean
        get() = apiService != null

    // ==================== OFFLINE: Заказы ====================

    fun getNewOrders(): Flow<List<OrderEntity>> = orderDao.getNewOrders()

    fun getOrdersBySupply(supplyId: String): Flow<List<OrderEntity>> =
        orderDao.getOrdersBySupply(supplyId)

    suspend fun getOrderById(orderId: Long): OrderEntity? = orderDao.getOrderById(orderId)

        suspend fun syncNewOrders(): kotlin.Result<Int> = safeApiCall {
        requireApi().getNewOrders()
    }.map { response ->
        val serverOrders = response.orders?.map { it.toEntity() } ?: emptyList()
        orderDao.insertOrders(serverOrders)
        serverOrders.size
    }

    suspend fun createSupply(name: String): kotlin.Result<String> = safeApiCall {
        requireApi().createSupply(WbCreateSupplyRequest(name))
    }.map { response ->
        val supplyId = response.id
        supplyDao.insertSupply(
            SupplyEntity(
                id = supplyId,
                name = name,
                createdAt = System.currentTimeMillis(),
                closedAt = null,
                scanDt = null,
                cargoType = 0,
                crossBorderType = 0,
                destinationOfficeId = null,
                isDone = false,
                isB2b = false,
                orderCount = 0,
                isSynced = true,
                qrCodeSvg = null,
                status = "active"
            )
        )
        supplyId
    }

    suspend fun addOrdersToSupply(supplyId: String, orderIds: List<Long>): kotlin.Result<Unit> = safeApiCall {
        requireApi().addOrdersToSupply(supplyId, WbAddOrdersToSupplyRequest(orderIds))
    }.map {
        orderDao.addOrdersToSupply(orderIds, supplyId)
    }

    suspend fun deliverSupply(supplyId: String): kotlin.Result<Unit> = safeApiCall {
        requireApi().deliverSupply(supplyId)
    }.map {
        supplyDao.markSupplyDelivered(supplyId)
    }

    suspend fun scanSgtin(orderId: Long, sgtin: String): kotlin.Result<Unit> = safeApiCall {
        requireApi().setSgtin(orderId, WbSgtinRequest(sgtin))
    }.map {
        orderDao.setOrderSgtin(orderId, sgtin)
        orderDao.markOrderSynced(orderId)
    }

    suspend fun downloadStickers(orderIds: List<Long>): kotlin.Result<List<WbStickerDto>> = safeApiCall {
        requireApi().getStickers(
            type = "svg",
            width = 58,
            height = 40,
            request = WbStickerRequest(orderIds)
        )
    }.map { response ->
        response.stickers ?: emptyList()
    }

    suspend fun syncStatuses(orderIds: List<Long>): kotlin.Result<Unit> = safeApiCall {
        requireApi().getOrdersStatus(WbStatusRequest(orderIds))
    }.map { response ->
        response.orders?.forEach { statusDto ->
            val order = orderDao.getOrderById(statusDto.id) ?: return@forEach
            orderDao.updateOrder(order.copy(status = statusDto.supplierStatus))
        }
    }

    // ==================== OFFLINE: Сканирование ====================
    suspend fun scanBarcode(barcode: String): ScanResult {
        val allOrders: List<OrderEntity> = orderDao.getNewOrders().first()

        // 1. Точное совпадение по barcode
        val exactMatch = allOrders.find { it.barcode == barcode }
        // 2. Частичное совпадение (если штрихкод сканером считался не полностью)
            ?: allOrders.find {
                it.barcode != null && (
                        it.barcode.contains(barcode) ||
                                barcode.contains(it.barcode)
                        )
            }

        return if (exactMatch != null) {
            orderDao.markOrderScanned(exactMatch.id)
            scanLogDao.insert(
                ScanLogEntity(
                    orderId = exactMatch.id,
                    supplyId = null,
                    scanType = "barcode",
                    scannedValue = barcode,
                    success = true,
                    errorMessage = null
                )
            )
            ScanResult.Success(exactMatch)
        } else {
            scanLogDao.insert(
                ScanLogEntity(
                    orderId = null,
                    supplyId = null,
                    scanType = "barcode",
                    scannedValue = barcode,
                    success = false,
                    errorMessage = "Заказ со штрихкодом $barcode не найден"
                )
            )
            ScanResult.NotFound
        }
    }
    /**
     * Сканирование КИЗ — ищем заказ по GTIN/skuss
     */
    suspend fun scanKiz(kizString: String): KizScanResult {
        val gtin = KizParser.extractGtin(kizString) ?: return KizScanResult.InvalidFormat

        // Получаем список заказов из Flow
        val allOrders: List<OrderEntity> = orderDao.getNewOrders().first()

        val match: OrderEntity? = allOrders.find { order: OrderEntity ->
            val barcode = order.barcode
            barcode != null && (
                    barcode.contains(gtin) ||
                            barcode.contains(gtin.trimStart('0')) ||
                            gtin.contains(barcode)
                    )
        }

        return if (match != null) {
            val sgtin = KizParser.toSgtin(kizString) ?: kizString
            orderDao.setOrderSgtin(match.id, sgtin)
            scanLogDao.insert(
                ScanLogEntity(
                    orderId = match.id,
                    supplyId = null,
                    scanType = "sgtin",
                    scannedValue = kizString,
                    success = true,
                    errorMessage = null
                )
            )
            KizScanResult.Success(match, sgtin)
        } else {
            scanLogDao.insert(
                ScanLogEntity(
                    orderId = null,
                    supplyId = null,
                    scanType = "sgtin",
                    scannedValue = kizString,
                    success = false,
                    errorMessage = "Заказ с GTIN $gtin не найден"
                )
            )
            KizScanResult.OrderNotFound(gtin)
        }
    }

    // ==================== УТИЛИТЫ ====================

    private fun WbOrderDto.toEntity(): OrderEntity {
        return OrderEntity(
            id = id,
            orderUid = orderUid ?: "",
            article = article ?: "",
            nmId = nmId ?: 0,
            chrtId = chrtId ?: 0,
            name = "", // TODO: получать из карточки товара отдельным запросом
            color = colorCode,
            size = size,
            barcode = skus?.firstOrNull(),
            price = price ?: 0,
            finalPrice = finalPrice ?: 0,
            warehouseId = warehouseId ?: 0,
            officeId = officeId,
            cargoType = cargoType ?: 0,
            deliveryType = deliveryType ?: "fbs",
            requiredMeta = requiredMeta?.joinToString(",") ?: "",
            optionalMeta = optionalMeta?.joinToString(",") ?: "",
            comment = comment,
            createdAt = parseDate(createdAt),
            supplyId = supplyId,
            status = "new",
            isMarked = (requiredMeta?.joinToString(",") ?: "").contains("sgtin") || (optionalMeta?.joinToString(",") ?: "").contains("sgtin"),// || true,
            sgtin = null,
            isSynced = true,
            scannedAt = null,
            stickerPrinted = false,
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun parseDate(dateStr: String?): Long {
        return try {
            dateFormat.parse(dateStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
    suspend fun getUnsyncedOrders(): List<OrderEntity> = orderDao.getUnsyncedOrders()
    suspend fun markOrderScanned(orderId: Long) {
        orderDao.markOrderScanned(orderId)
    }
    suspend fun unmarkOrderScanned(orderId: Long) {
        orderDao.markOrderScanned(orderId, null)
    }

    // ЗАМЕНИТЬ syncOrderStatuses на это:

    suspend fun syncOrderStatuses() {
        try {
            val localIds = orderDao.getAllOrderIds().first().take(100)
            if (localIds.isEmpty()) return

            val response = requireApi().getOrdersStatus(WbStatusRequest(localIds))
            val ordersList = response.orders
            if (ordersList != null) {
                for (dto in ordersList) {
                    orderDao.updateOrderStatus(dto.id, dto.supplierStatus)
                }
            }
        } catch (e: Exception) {
            // Не критично
        }
    }

    suspend fun scanWbSticker(stickerData: String): ScanResult {
        val orderId = stickerData.filter { it.isDigit() }.takeIf { it.length >= 5 }?.toLongOrNull()
            ?: return ScanResult.Error("Неверный формат стикера")

        val order = orderDao.getOrderById(orderId)
            ?: return ScanResult.NotFound

        return if (order.status == "cancel") {
            ScanResult.Error("Заказ отменён")
        } else {
            orderDao.markOrderScanned(order.id, System.currentTimeMillis())
            ScanResult.Success(order)
        }
    }
    private suspend fun <T> safeApiCall(
        maxRetries: Int = 3,
        block: suspend () -> retrofit2.Response<T>
    ): kotlin.Result<T> {
        var lastException: Exception? = null

        repeat(maxRetries) { attempt ->
            try {
                val response = block()

                when (response.code()) {
                    200, 201, 204 -> {
                        val body = response.body()
                        return if (body != null) {
                            kotlin.Result.success(body)
                        } else {
                            @Suppress("UNCHECKED_CAST")
                            kotlin.Result.success(Unit as T)
                        }
                    }
                    401 -> {
                        return kotlin.Result.failure(
                            ApiException.Unauthorized("Токен невалиден. Авторизуйтесь заново.")
                        )
                    }
                    429 -> {
                        val delayMs = (1 shl attempt) * 1000L
                        delay(delayMs)
                        lastException = ApiException.RateLimit("429, retry ${attempt + 1}/$maxRetries")
                    }
                    500, 502, 503, 504 -> {
                        val delayMs = (1 shl attempt) * 1000L
                        delay(delayMs)
                        lastException = ApiException.ServerError(
                            "HTTP ${response.code()}, retry ${attempt + 1}/$maxRetries"
                        )
                    }
                    else -> {
                        return kotlin.Result.failure(
                            ApiException.HttpError(
                                response.code(),
                                response.errorBody()?.string() ?: "Unknown error"
                            )
                        )
                    }
                }
            } catch (e: java.net.SocketTimeoutException) {
                lastException = ApiException.Timeout("Таймаут, retry ${attempt + 1}/$maxRetries")
                delay(2000)
            } catch (e: java.net.UnknownHostException) {
                return kotlin.Result.failure(ApiException.NoInternet("Нет подключения к интернету"))
            } catch (e: Exception) {
                lastException = e
                delay(1000)
            }
        }

        return kotlin.Result.failure(lastException ?: Exception("Unknown error"))
    }

    // Sealed class для ошибок
    sealed class ApiException(message: String) : Exception(message) {
        class Unauthorized(message: String) : ApiException(message)
        class RateLimit(message: String) : ApiException(message)
        class ServerError(message: String) : ApiException(message)
        class HttpError(val code: Int, message: String) : ApiException("HTTP $code: $message")
        class Timeout(message: String) : ApiException(message)
        class NoInternet(message: String) : ApiException(message)
    }



}

sealed class ScanResult {
    data class Success(val order: OrderEntity) : ScanResult()
    object NotFound : ScanResult()
    data class Error(val message: String) : ScanResult()
}
sealed class KizScanResult {
    data class Success(val order: OrderEntity, val sgtin: String) : KizScanResult()
    data class OrderNotFound(val gtin: String) : KizScanResult()
    object InvalidFormat : KizScanResult()
}
// Sealed class для типизации ошибок
sealed class ApiException(message: String) : Exception(message) {
    class Unauthorized(message: String) : ApiException(message)
    class RateLimit(message: String) : ApiException(message)
    class ServerError(message: String) : ApiException(message)
    class HttpError(val code: Int, message: String) : ApiException("HTTP $code: $message")
    class Timeout(message: String) : ApiException(message)
    class NoInternet(message: String) : ApiException(message)

}