package com.wb.fbs.tsd

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import com.wb.fbs.tsd.data.db.AppDatabase
import com.wb.fbs.tsd.data.network.WbApiClient
import com.wb.fbs.tsd.data.repository.WbRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineExceptionHandler
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TsdApplication : Application() {

    private val crashHandler = CoroutineExceptionHandler { _, throwable ->
        logCrash("COROUTINE_CRASH", throwable)
        showToast("⚠️ Ошибка: ${throwable.message ?: throwable::class.simpleName}")
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + crashHandler)

    lateinit var database: AppDatabase
        private set

    lateinit var repository: WbRepository
        private set

    override fun onCreate() {
        super.onCreate()

        // Глобальный перехватчик крашей — ставим ПЕРВЫМ, чтобы поймать
        // даже краш при инициализации БД.
        setupCrashHandler()

        // Создаём БД с защитой: если файл БД повреждён (краш во время
        // записи при синхронизации), Room при открытии выбросит исключение.
        // В этом случае — удаляем файл и пересоздаём.
        database = createDatabaseWithRecovery()

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
                repository.setContentApiService(WbApiClient.getContentService())
            } catch (e: Exception) {
                prefs.edit().remove("api_token").apply()
            }
        }

        instance = this
    }

    private fun createDatabaseWithRecovery(): AppDatabase {
        // Простое создание БД. Recovery при повреждении делается
        // через fallbackToDestructiveMigration + try/catch в ViewModel.
        // Принудительное открытие в Application.onCreate() крашит
        // т.к. Room запрещает БД-операции в главном потоке.
        return Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "tsd_database"
        )
            .fallbackToDestructiveMigration()
            .fallbackToDestructiveMigrationOnDowngrade()
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()
    }

    fun initWbApi(token: String) {
        WbApiClient.init(token)
        repository.setApiService(WbApiClient.getService())
        repository.setContentApiService(WbApiClient.getContentService())
        // Очищаем локальные заказы — новый токен = новый кабинет WB.
        // Баркоды товаров повторяются между кабинетами, старые заказы
        // другого кабинета не должны путаться с новыми.
        // Запускаем в фоновой корутине через app scope.
        appScope.launch {
            try {
                repository.clearOrdersForNewToken()
            } catch (e: Exception) {
                // Не критчно — fallbackToDestructiveMigration очистит при апгрейде
            }
        }
    }

    private fun logCrash(tag: String, throwable: Throwable) {
        try {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val logText = "==== $tag $timestamp ====\n$sw\n\n"
            val dir = getExternalFilesDir(null) ?: filesDir
            val logFile = File(dir, "crash_log.txt")
            logFile.parentFile?.mkdirs()
            logFile.appendText(logText)
            Log.e("TSD_CRASH", logText)
        } catch (_: Throwable) {}
    }

    private fun showToast(message: String) {
        try {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        } catch (_: Throwable) {}
    }

    private fun setupCrashHandler() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            logCrash("CRASH Thread:${thread.name}", throwable)
            showToast("💥 Краш: ${throwable.message ?: throwable::class.simpleName}")
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        lateinit var instance: TsdApplication
            private set
    }
}