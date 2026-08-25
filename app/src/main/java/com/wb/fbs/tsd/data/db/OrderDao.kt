package com.wb.fbs.tsd.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Query("SELECT * FROM orders WHERE status = 'new' ORDER BY createdAt DESC")
    fun getNewOrders(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE supplyId = :supplyId ORDER BY article, size")
    fun getOrdersBySupply(supplyId: String): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE id = :orderId")
    suspend fun getOrderById(orderId: Long): OrderEntity?

    @Query("SELECT * FROM orders WHERE article = :article AND size = :size AND status IN ('new', 'confirm')")
    suspend fun getOrdersByArticleSize(article: String, size: String?): List<OrderEntity>

    @Query("SELECT COUNT(*) FROM orders WHERE status = 'new'")
    fun getNewOrdersCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM orders WHERE supplyId = :supplyId")
    suspend fun getOrdersCountInSupply(supplyId: String): Int

    @Query("SELECT COUNT(*) FROM orders WHERE supplyId = :supplyId AND scannedAt IS NOT NULL")
    suspend fun getScannedCountInSupply(supplyId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrders(orders: List<OrderEntity>)

    @Update
    suspend fun updateOrder(order: OrderEntity)

    @Query("UPDATE orders SET supplyId = :supplyId, status = 'confirm', isSynced = 0 WHERE id IN (:orderIds)")
    suspend fun addOrdersToSupply(orderIds: List<Long>, supplyId: String)

    @Query("UPDATE orders SET sgtin = :sgtin, isSynced = 0 WHERE id = :orderId")
    suspend fun setOrderSgtin(orderId: Long, sgtin: String)

    @Query("UPDATE orders SET scannedAt = :timestamp, isSynced = 0 WHERE id = :orderId")
    suspend fun markOrderScanned(orderId: Long, timestamp: Long?)

    @Query("UPDATE orders SET packedAt = :timestamp, isSynced = 0 WHERE id = :orderId")
    suspend fun markOrderPacked(orderId: Long, timestamp: Long?)

    @Query("UPDATE orders SET stickerPrinted = 1, isSynced = 0 WHERE id = :orderId")
    suspend fun markStickerPrinted(orderId: Long)

    @Query("DELETE FROM orders WHERE status = 'cancel' AND createdAt < :olderThan")
    suspend fun deleteOldCancelledOrders(olderThan: Long)

    @Query("SELECT * FROM orders WHERE isSynced = 0")
    suspend fun getUnsyncedOrders(): List<OrderEntity>

    @Query("UPDATE orders SET isSynced = 1 WHERE id = :orderId")
    suspend fun markOrderSynced(orderId: Long)

    @Query("SELECT * FROM orders")
    fun getAllOrders(): Flow<List<OrderEntity>>

    @Query("SELECT id FROM orders WHERE status IN ('new', 'confirm')")
    fun getActiveOrderIds(): Flow<List<Long>>

    @Query("SELECT * FROM orders WHERE status IN ('new', 'confirm', 'complete', 'cancel') AND sgtin IS NOT NULL AND sgtin != ''")
    suspend fun getActiveOrdersWithSgtin(): List<OrderEntity>

    @Query("SELECT id FROM orders")
    fun getAllOrderIds(): Flow<List<Long>>

    @Query("UPDATE orders SET status = :newStatus, isSynced = 0 WHERE id = :orderId")
    suspend fun updateOrderStatus(orderId: Long, newStatus: String)

    @Delete
    suspend fun deleteOrder(order: OrderEntity)

    // ==================== СТИКЕРЫ WB (уникальны для заказа, в отличие от
    // баркода товара, который может повторяться в разных кабинетах WB) ====================

    // Заказы "на сборке"/"в доставке", для которых ещё не скачан стикер
    @Query("SELECT * FROM orders WHERE status IN ('confirm', 'complete') AND stickerBarcode IS NULL")
    suspend fun getOrdersNeedingStickers(): List<OrderEntity>

    @Query("UPDATE orders SET stickerBarcode = :barcode, stickerPartA = :partA, stickerPartB = :partB, isSynced = 0 WHERE id = :orderId")
    suspend fun updateStickerData(orderId: Long, barcode: String?, partA: String?, partB: String?)

    // Поиск заказа по коду, реально закодированному в стикере WB — именно
    // это сканирует ТСД при сборке, а не баркод товара
    @Query("SELECT * FROM orders WHERE stickerBarcode = :barcode LIMIT 1")
    suspend fun getOrderByStickerBarcode(barcode: String): OrderEntity?

    // Полная очистка локальных заказов — нужна при переключении между
    // кабинетами WB, чтобы старые заказы одного кабинета не путались с
    // новыми заказами другого (баркоды товаров совпадают между кабинетами)
    @Query("DELETE FROM orders")
    suspend fun clearAllOrders()

    // ==================== РАЗМЕРЫ ИЗ CONTENT API ====================

    // Батч-апдейт: обновить размер конкретного заказа по chrtId.
    // Content API отдаёт размеры карточки по nmId — каждая карточка
    // может иметь несколько размеров, каждый со своим chrtId.
    @Query("UPDATE orders SET size = :size, updatedAt = :updatedAt WHERE chrtId = :chrtId AND (size IS NULL OR size = '' OR size = '0')")
    suspend fun updateSizeByChrtId(chrtId: Long, size: String, updatedAt: Long = System.currentTimeMillis())

    // Получить все уникальные nmId из заказов, где размер пустой/нулевой
    @Query("SELECT DISTINCT nmId FROM orders WHERE size IS NULL OR size = '' OR size = '0'")
    suspend fun getNmIdsNeedingSizes(): List<Long>

}
