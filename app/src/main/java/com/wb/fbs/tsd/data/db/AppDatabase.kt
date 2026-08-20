package com.wb.fbs.tsd.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
@Database(
    entities = [
        OrderEntity::class,
        SupplyEntity::class,
        SupplyOrderEntity::class,
        ScanLogEntity::class,
        SettingsEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun orderDao(): OrderDao
    abstract fun supplyDao(): SupplyDao
    abstract fun scanLogDao(): ScanLogDao
    abstract fun settingsDao(): SettingsDao
}