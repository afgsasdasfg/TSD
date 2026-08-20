package com.wb.fbs.tsd.data.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object WbApiClient {

    private const val BASE_URL = "https://marketplace-api.wildberries.ru"
    private const val CONTENT_BASE_URL = "https://content-api.wildberries.ru"

    private var apiService: WbApiService? = null
    private var contentApiService: WbContentApiService? = null
    private var authToken: String = ""

    // Level.BASIC логирует только строку запроса (URL + код ответа),
    // без тела. Level.BODY на некоторых ТСД/роутерах убивает WiFi-запросы:
    // тело логируется в память полностью, и на слабом WiFi поток зависает.
    // Симптом: «приложение не работает по WiFi, только по мобильному интернету».
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    fun init(token: String) {
        authToken = token.trim().replace("\n", "").replace("\r", "").replace("\t", "")

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $authToken")
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

        apiService = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WbApiService::class.java)

        contentApiService = Retrofit.Builder()
            .baseUrl(CONTENT_BASE_URL)
            .client(client) // тот же клиент/токен, другой baseUrl
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WbContentApiService::class.java)
    }

    fun getService(): WbApiService {
        return apiService ?: throw IllegalStateException("WB API not initialized. Call init(token) first.")
    }

    fun getContentService(): WbContentApiService {
        return contentApiService ?: throw IllegalStateException("WB Content API not initialized. Call init(token) first.")
    }

    fun isInitialized(): Boolean = apiService != null
}