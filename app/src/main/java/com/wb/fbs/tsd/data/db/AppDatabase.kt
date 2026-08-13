package com.wb.fbs.tsd.data.db

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        OrderEntity::class,
        SupplyEntity::class,
        SupplyOrderEntity::class,
        ScanLogEntity::class,
        SettingsEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun orderDao(): OrderDao
    abstract fun supplyDao(): SupplyDao
    abstract fun scanLogDao(): ScanLogDao
    abstract fun settingsDao(): SettingsDao
}

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): java.time.Instant? {
        return value?.let { java.time.Instant.ofEpochMilli(it) }
    }

    @TypeConverter
    fun instantToTimestamp(instant: java.time.Instant?): Long? {
        return instant?.toEpochMilli()
    }
}