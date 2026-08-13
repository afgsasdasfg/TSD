package com.wb.fbs.tsd.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplyDao {
    @Query("SELECT * FROM supplies WHERE status = 'active' ORDER BY createdAt DESC")
    fun getActiveSupplies(): Flow<List<SupplyEntity>>

    @Query("SELECT * FROM supplies WHERE id = :supplyId")
    suspend fun getSupplyById(supplyId: String): SupplyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupply(supply: SupplyEntity)

    @Update
    suspend fun updateSupply(supply: SupplyEntity)

    @Query("UPDATE supplies SET status = 'delivered', isSynced = 0 WHERE id = :supplyId")
    suspend fun markSupplyDelivered(supplyId: String)

    @Query("UPDATE supplies SET qrCodeSvg = :svg, isSynced = 0 WHERE id = :supplyId")
    suspend fun setSupplyQrCode(supplyId: String, svg: String)

    @Query("DELETE FROM supplies WHERE status = 'delivered' AND closedAt < :olderThan")
    suspend fun deleteOldDeliveredSupplies(olderThan: Long)

    @Query("SELECT * FROM supplies WHERE isSynced = 0")
    suspend fun getUnsyncedSupplies(): List<SupplyEntity>

    @Query("UPDATE supplies SET isSynced = 1 WHERE id = :supplyId")
    suspend fun markSupplySynced(supplyId: String)
}