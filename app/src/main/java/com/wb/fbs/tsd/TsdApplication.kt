package com.wb.fbs.tsd

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import com.wb.fbs.tsd.data.db.AppDatabase
import com.wb.fbs.tsd.data.network.CrptApiClient
import com.wb.fbs.tsd.data.network.WbApiClient
import com.wb.fbs.tsd.data.repository.CrptRepository
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

    lateinit var crptRepository: CrptRepository
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

        // ЧЗ-сервер (Честный ЗНАК)
        CrptApiClient.init()
        crptRepository = CrptRepository()

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
        // Принудительно удаляем старую БД при несовпадении версий.
        // fallbackToDestructiveMigration() в Room 2.6.1 не всегда срабатывает
        // при больших скачках (1→4) — краш всё равно падает.
        // Поэтому: пробуем открыть, при краше — удаляем файл и пересоздаём.
        val dbName = "tsd_database"
        val dbFile = applicationContext.getDatabasePath(dbName)
        // Проверяем: если файл БД существует, но схема старая — удаляем.
        // Room сам не умеет мигрировать 1→4 (нет Migration path),
        // fallbackToDestructiveMigration должен сработать, но на некоторых
        // устройствах/версиях Android он не перехватывает IllegalStateException.
        // Надёжнее: удалить файл до открытия Room.
        try {
            if (dbFile.exists()) {
                // Открываем SQLite напрямую, чтобы проверить версию
                val db = SQLiteDatabase.openDatabase(
                    dbFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READWRITE
                )
                val dbVersion = db.version
                db.close()
                if (dbVersion < 4) {
                    // Старая схема — удаляем файл и wal/shm
                    dbFile.delete()
                    File(dbFile.absolutePath + "-wal").delete()
                    File(dbFile.absolutePath + "-shm").delete()
                    File(dbFile.absolutePath + "-journal").delete()
                }
            }
        } catch (_: Throwable) {
            // Не смогли открыть/проверить — удаляем
            try { dbFile.delete() } catch (_: Throwable) {}
            try { File(dbFile.absolutePath + "-wal").delete() } catch (_: Throwable) {}
            try { File(dbFile.absolutePath + "-shm").delete() } catch (_: Throwable) {}
        }

        return Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            dbName
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