package com.wb.fbs.tsd.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Query("SELECT * FROM orders WHERE status IN ('new', 'confirm') ORDER BY createdAt DESC")
    fun getNewOrders(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE supplyId = :supplyId ORDER BY article, size")
    fun getOrdersBySupply(supplyId: String): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE id = :orderId")
    suspend fun getOrderById(orderId: Long): OrderEntity?

    @Query("SELECT * FROM orders WHERE article = :article AND size = :size AND status IN ('new', 'confirm')")
    suspend fun getOrdersByArticleSize(article: String, size: String?): List<OrderEntity>

    @Query("SELECT COUNT(*) FROM orders WHERE status IN ('new', 'confirm')")
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

    @Query("SELECT id FROM orders")
    fun getAllOrderIds(): Flow<List<Long>>

    @Query("UPDATE orders SET status = :newStatus, isSynced = 0 WHERE id = :orderId")
    suspend fun updateOrderStatus(orderId: Long, newStatus: String)

    @Delete
    suspend fun deleteOrder(order: OrderEntity)

}