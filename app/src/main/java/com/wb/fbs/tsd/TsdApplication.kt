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
        )
            // MVP: схема поменялась (добавлены поля стикера в orders), полноценную
            // миграцию писать некогда — при апгрейде локальная база один раз очистится
            // и перезальётся с сервера через syncAllOrders()/syncNewOrders().
            .fallbackToDestructiveMigration()
            .build()

        repository = WbRepository(
            orderDao = database.orderDao(),
            supplyDao = database.supplyDao(),
            scanLogDao = database.scanLogDao()
        )

        // ВОССТАНАВЛИВАЕМ API ПРИ СТАРТЕ
        val prefs = getSharedPreferences("wb_prefs", MODE_PRIVATE)
        val savedToken = prefs.getString("api_token", null)
        if (!savedToken.isNullOrBlank()) {
            try {
                WbApiClient.init(savedToken)
                repository.setApiService(WbApiClient.getService())
            } catch (e: Exception) {
                // Токен невалидный — очистим
                prefs.edit().remove("api_token").apply()
            }
        }

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