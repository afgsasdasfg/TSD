package com.wb.fbs.tsd.data.network

import retrofit2.Response
import retrofit2.http.*

/**
 * API клиент для сервера Честный ЗНАК (ЧЗ)
 * Сервер: https://185-198-152-86.sslip.io
 *
 * Endpoints:
 * - GET  /api/health?key=XXX     — kill switch / лицензия
 * - GET  /api/auth/status        — статус токена ЧЗ
 * - POST /api/check-kiz          — проверка КИЗ
 * - POST /api/retire-kiz         — вывод КИЗ из оборота (продажа)
 * - POST /api/return-kiz         — возврат КИЗ в оборот (возврат товара)
 */
interface CrptApiService {

    @GET("api/health")
    suspend fun checkHealth(
        @Query("key") licenseKey: String
    ): Response<CrptHealthResponse>

    @GET("api/auth/status")
    suspend fun getAuthStatus(): Response<CrptAuthStatusResponse>

    @POST("api/check-kiz")
    @Headers("Content-Type: application/json")
    suspend fun checkKiz(
        @Body request: CrptCheckKizRequest
    ): Response<CrptCheckKizResponse>

    @POST("api/retire-kiz")
    @Headers("Content-Type: application/json")
    suspend fun retireKiz(
        @Body request: CrptRetireKizRequest
    ): Response<CrptRetireKizResponse>

    @POST("api/return-kiz")
    @Headers("Content-Type: application/json")
    suspend fun returnKiz(
        @Body request: CrptReturnKizRequest
    ): Response<CrptReturnKizResponse>
}

// === DTO ===

data class CrptHealthResponse(
    val status: String,           // "ok" или "blocked"
    val token_valid: Boolean? = null,
    val token_expires: String? = null,
    val message: String? = null
)

data class CrptAuthStatusResponse(
    val token_valid: Boolean,
    val token_expires: String? = null
)

data class CrptCheckKizRequest(
    val cis: String
)

data class CrptCheckKizResponse(
    val valid: Boolean,
    val cis: String? = null,
    val gtin: String? = null,
    val status: String? = null,
    val statusDescription: String? = null,
    val productName: String? = null,
    val ownerInn: String? = null,
    val verified: Boolean? = null,
    val error: String? = null
)

data class CrptRetireKizRequest(
    val cis: String,
    val reason: String = "RETAIL"
)

data class CrptRetireKizResponse(
    val retired: Boolean,
    val cis: String? = null,
    val documentId: String? = null,
    val error: String? = null
)

data class CrptReturnKizRequest(
    val cis: String
)

data class CrptReturnKizResponse(
    val returned: Boolean,
    val cis: String? = null,
    val documentId: String? = null,
    val error: String? = null
)
