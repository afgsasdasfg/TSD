package com.wb.fbs.tsd.data.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Клиент сервера Честный ЗНАК (ЧЗ)
 *
 * Сервер: https://185-198-152-86.sslip.io
 * Kill switch: проверка /api/health?key=XXX перед каждой операцией
 *
 * Использование:
 *   CrptApiClient.init(licenseKey)
 *   if (CrptApiClient.isHealthy()) { ... }
 *   CrptApiClient.getService().checkKiz(...)
 */
object CrptApiClient {

    private const val BASE_URL = "https://185-198-152-86.sslip.io/"

    // Ключ лицензии (kill switch). Без него сервер отдаёт 403.
    // Хранится в памяти (не в SharedPreferences — при смене ключа
    // на сервере, достаточно обновить в приложении и пересобрать).
    private const val LICENSE_KEY = "tsd-1ae30a6529e4a0cd89ac834e42ba4b14"

    private var apiService: CrptApiService? = null
    private var healthy: Boolean = false

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private fun buildClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Content-Type", "application/json")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(loggingInterceptor)
            .retryOnConnectionFailure(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    fun init() {
        val client = buildClient()
        apiService = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CrptApiService::class.java)
    }

    fun getService(): CrptApiService {
        return apiService ?: throw IllegalStateException(
            "CRPT API not initialized. Call CrptApiClient.init() first."
        )
    }

    fun isInitialized(): Boolean = apiService != null

    /**
     * Проверка kill switch / лицензии.
     * Вызывать перед КИЗ-операциями и при старте приложения.
     */
    suspend fun checkHealth(): Boolean {
        if (!isInitialized()) return false
        return try {
            val response = getService().checkHealth(LICENSE_KEY)
            healthy = response.isSuccessful && response.body()?.status == "ok"
            healthy
        } catch (e: Exception) {
            healthy = false
            false
        }
    }

    fun isHealthy(): Boolean = healthy

    fun getLicenseKey(): String = LICENSE_KEY
}
