package com.wb.fbs.tsd

import android.app.Application
import androidx.room.Room
import com.wb.fbs.tsd.data.db.AppDatabase
import com.wb.fbs.tsd.data.network.WbApiClient
import com.wb.fbs.tsd.data.repository.WbRepository

class TsdApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: WbRepository
        private set

    override fun onCreate() {
        super.onCreate()

        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "tsd_database"
        ).build()

        // Repository без API — инициализируем позже
        repository = WbRepository(
            orderDao = database.orderDao(),
            supplyDao = database.supplyDao(),
            scanLogDao = database.scanLogDao()
        )

        instance = this
    }

    fun initWbApi(token: String) {
        WbApiClient.init(token)
        repository.setApiService(WbApiClient.getService())
    }

    companion object {
        lateinit var instance: TsdApplication
            private set
    }
}