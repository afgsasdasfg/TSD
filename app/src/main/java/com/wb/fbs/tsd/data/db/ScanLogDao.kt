package com.wb.fbs.tsd.data.db

import androidx.room.*

@Dao
interface ScanLogDao {
    @Insert
    suspend fun insert(log: ScanLogEntity)

    @Query("SELECT * FROM scan_logs ORDER BY timestamp DESC LIMIT 100")
    suspend fun getRecentLogs(): List<ScanLogEntity>

    @Query("DELETE FROM scan_logs WHERE timestamp < :olderThan")
    suspend fun deleteOldLogs(olderThan: Long)
}