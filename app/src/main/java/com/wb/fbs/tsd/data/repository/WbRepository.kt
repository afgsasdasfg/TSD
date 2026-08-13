package com.wb.fbs.tsd.data.repository

import com.wb.fbs.tsd.data.db.*
import com.wb.fbs.tsd.data.network.*
import com.wb.fbs.tsd.utils.KizParser
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

    // ==================== SYNC: Загрузка новых заказов ====================

    suspend fun syncNewOrders(): Result<Int> = try {
        val response = requireApi().getNewOrders()
        if (response.isSuccessful) {
            val orders = response.body()?.orders?.map { it.toEntity() } ?: emptyList()
            orderDao.insertOrders(orders)
            Result.success(orders.size)
        } else {
            Result.failure(Exception("HTTP ${response.code()}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    // ==================== SYNC: Статусы ====================

    suspend fun syncStatuses(orderIds: List<Long>): Result<Unit> = try {
        val response = requireApi().getOrdersStatus(WbStatusRequest(orderIds))
        if (response.isSuccessful) {
            response.body()?.orders?.forEach { statusDto ->
                val order = orderDao.getOrderById(statusDto.id) ?: return@forEach
                orderDao.updateOrder(order.copy(status = statusDto.supplierStatus))
            }
            Result.success(Unit)
        } else {
            Result.failure(Exception("HTTP ${response.code()}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    // ==================== SYNC: Стикеры ====================

    suspend fun downloadStickers(orderIds: List<Long>): Result<List<WbStickerDto>> = try {
        val response = requireApi().getStickers(
            type = "svg",
            width = 58,
            height = 40,
            request = WbStickerRequest(orderIds)
        )
        if (response.isSuccessful) {
            Result.success(response.body()?.stickers ?: emptyList())
        } else {
            Result.failure(Exception("HTTP ${response.code()}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    // ==================== OFFLINE: Сканирование ====================

    suspend fun scanBarcode(barcode: String): ScanResult {
        val orders = orderDao.getOrdersByArticleSize(barcode, null)
        val exactMatch = orders.firstOrNull { it.barcode == barcode }
            ?: orders.firstOrNull()

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
                    errorMessage = "Заказ не найден"
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

    suspend fun scanSgtin(orderId: Long, sgtin: String): Result<Unit> = try {
        val response = requireApi().setSgtin(orderId, WbSgtinRequest(sgtin))
        if (response.isSuccessful) {
            orderDao.setOrderSgtin(orderId, sgtin)
            scanLogDao.insert(
                ScanLogEntity(
                    orderId = orderId,
                    supplyId = null,
                    scanType = "sgtin",
                    scannedValue = sgtin,
                    success = true,
                    errorMessage = null
                )
            )
            Result.success(Unit)
        } else {
            // Offline fallback
            orderDao.setOrderSgtin(orderId, sgtin)
            Result.failure(Exception("HTTP ${response.code()}, сохранено локально"))
        }
    } catch (e: Exception) {
        // Offline fallback
        orderDao.setOrderSgtin(orderId, sgtin)
        Result.failure(Exception("Оффлайн: ${e.message}"))
    }

    // ==================== ПОСТАВКИ ====================

    suspend fun createSupply(name: String): Result<String> = try {
        val response = requireApi().createSupply(WbCreateSupplyRequest(name))
        if (response.isSuccessful) {
            val supplyId = response.body()?.id ?: throw Exception("No supply ID")
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
            Result.success(supplyId)
        } else {
            Result.failure(Exception("HTTP ${response.code()}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun addOrdersToSupply(supplyId: String, orderIds: List<Long>): Result<Unit> = try {
        val response = requireApi().addOrdersToSupply(
            supplyId,
            WbAddOrdersToSupplyRequest(orderIds)
        )
        if (response.isSuccessful) {
            orderDao.addOrdersToSupply(orderIds, supplyId)
            Result.success(Unit)
        } else {
            Result.failure(Exception("HTTP ${response.code()}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun deliverSupply(supplyId: String): Result<Unit> = try {
        val response = requireApi().deliverSupply(supplyId)
        if (response.isSuccessful) {
            supplyDao.markSupplyDelivered(supplyId)
            Result.success(Unit)
        } else {
            Result.failure(Exception("HTTP ${response.code()}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
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

    suspend fun markOrderScanned(orderId: Long) {
        orderDao.markOrderScanned(orderId)
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