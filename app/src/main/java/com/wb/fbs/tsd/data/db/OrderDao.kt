package com.wb.fbs.tsd.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Query("SELECT * FROM orders WHERE status = 'new' ORDER BY createdAt DESC")
    fun getNewOrders(): Flow<List<OrderEntity>>

    // Заказы, с которыми реально идёт физическая работа на сборке:
    // new — ещё не подтверждён, confirm — уже в сборочном задании ("на сборке" в кабинете WB)
    @Query("SELECT * FROM orders WHERE status IN ('new', 'confirm') ORDER BY createdAt DESC")
    fun getActiveOrders(): Flow<List<OrderEntity>>

    @Query("SELECT COUNT(*) FROM orders WHERE status = 'confirm'")
    fun getConfirmOrdersCount(): Flow<Int>

    @Query("SELECT * FROM orders WHERE supplyId = :supplyId ORDER BY article, size")
    fun getOrdersBySupply(supplyId: String): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE id = :orderId")
    suspend fun getOrderById(orderId: Long): OrderEntity?

    @Query("SELECT * FROM orders WHERE article = :article AND size = :size AND status = 'new'")
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
    suspend fun markOrderScanned(orderId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE orders SET stickerPrinted = 1, isSynced = 0 WHERE id = :orderId")
    suspend fun markStickerPrinted(orderId: Long)

    @Query("""
        UPDATE orders 
        SET stickerBarcode = :barcode, stickerPartA = :partA, stickerPartB = :partB 
        WHERE id = :orderId
    """)
    suspend fun updateOrderSticker(orderId: Long, partA: String, partB: String, barcode: String)

    @Query("""
        SELECT * FROM orders 
        WHERE stickerBarcode = :code OR stickerPartA = :code OR stickerPartB = :code 
        LIMIT 1
    """)
    suspend fun findOrderByStickerCode(code: String): OrderEntity?

    @Query("DELETE FROM orders WHERE status = 'cancel' AND createdAt < :olderThan")
    suspend fun deleteOldCancelledOrders(olderThan: Long)

    @Query("SELECT * FROM orders WHERE isSynced = 0")
    suspend fun getUnsyncedOrders(): List<OrderEntity>

    @Query("UPDATE orders SET isSynced = 1 WHERE id = :orderId")
    suspend fun markOrderSynced(orderId: Long)

    @Query("SELECT * FROM orders")
    fun getAllOrders(): Flow<List<OrderEntity>>

    @Delete
    suspend fun deleteOrder(order: OrderEntity)

}