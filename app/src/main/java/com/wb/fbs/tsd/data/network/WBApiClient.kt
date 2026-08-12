package com.wb.fbs.tsd.data.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.wb.fbs.tsd.data.model.*

object WBApiClient {

    private const val BASE_URL_TEST = "https://api-test.wildberries.ru/"
    var baseUrl: String = BASE_URL_TEST

    private lateinit var apiService: WBApiService
    private var authToken: String? = null
    private var clientId: Int = 0

    fun init(apiKey: String?, wbClientId: Int) {
        if (!this::apiService.isInitialized) {
            apiService = Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(WBApiService::class.java)
        }
        clientId = wbClientId
        // Токен получим при первом вызове authorize()
    }

    suspend fun authenticate(apiKey: String): Result<WBAuthorizeResponse> = try {
        val response = apiService.authorize(mapOf("apiKey" to apiKey))
        if (response.isSuccessful && response.body()?.token != null) {
            authToken = response.body()!!.token
            Result.success(response.body()!!)
        } else {
            Result.failure(Exception("Авторизация провалилась: ${response.code()}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun loadOrders(warehouseId: Int, page: Int = 1): Result<List<WBOrderDto>> = try {
        require(::apiService.isInitialized) { "Сначала вызовите init()" }
        val authHeader = "Bearer $authToken"
        val response = apiService.getOrders(
            auth = authHeader,
            warehouseId = warehouseId,
            page = page,
            limit = 50
        )
        if (response.isSuccessful && response.body()?.data != null) {
            Result.success(response.body()!!.data)
        } else {
            Result.failure(Exception("Заказы не получены: ${response.code()} ${response.errorBody()?.string()}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}