package com.wb.fbs.tsd.data.repository

import com.wb.fbs.tsd.data.network.CrptApiClient
import com.wb.fbs.tsd.data.network.CrptCheckKizRequest
import com.wb.fbs.tsd.data.network.CrptCheckKizResponse
import com.wb.fbs.tsd.data.network.CrptRetireKizRequest
import com.wb.fbs.tsd.data.network.CrptRetireKizResponse
import com.wb.fbs.tsd.data.network.CrptReturnKizRequest
import com.wb.fbs.tsd.data.network.CrptReturnKizResponse
import kotlinx.coroutines.delay

/**
 * Repository для сервера Честный ЗНАК (ЧЗ)
 *
 * Обёртка над CrptApiClient с обработкой ошибок и ретраями.
 * Не зависит от WbRepository — отдельный слой для КИЗ-операций.
 */
class CrptRepository {

    private val tag = "CrptRepository"

    fun isInitialized(): Boolean = CrptApiClient.isInitialized()

    /**
     * Проверка kill switch. True — сервер доступен, лицензия валидна.
     */
    suspend fun checkHealth(): Boolean {
        return CrptApiClient.checkHealth()
    }

    /**
     * Проверка КИЗ через сервер ЧЗ.
     * Возвращает результат или null если сервер недоступен.
     */
    suspend fun checkKiz(cis: String): Result<CrptCheckKizResponse> {
        if (!CrptApiClient.isHealthy()) {
            // Пытаемся перепроверить
            if (!CrptApiClient.checkHealth()) {
                return Result.failure(Exception("Сервер ЧЗ недоступен или лицензия заблокирована"))
            }
        }
        return safeCall(maxRetries = 2) {
            CrptApiClient.getService().checkKiz(CrptCheckKizRequest(cis))
        }.map { response ->
            response
        }
    }

    /**
     * Вывод КИЗ из оборота (продажа).
     */
    suspend fun retireKiz(cis: String, reason: String = "RETAIL"): Result<CrptRetireKizResponse> {
        if (!CrptApiClient.isHealthy()) {
            if (!CrptApiClient.checkHealth()) {
                return Result.failure(Exception("Сервер ЧЗ недоступен или лицензия заблокирована"))
            }
        }
        return safeCall(maxRetries = 2) {
            CrptApiClient.getService().retireKiz(CrptRetireKizRequest(cis, reason))
        }.map { response ->
            response
        }
    }

    /**
     * Возврат КИЗ в оборот (возврат товара от покупателя).
     */
    suspend fun returnKiz(cis: String): Result<CrptReturnKizResponse> {
        if (!CrptApiClient.isHealthy()) {
            if (!CrptApiClient.checkHealth()) {
                return Result.failure(Exception("Сервер ЧЗ недоступен или лицензия заблокирована"))
            }
        }
        return safeCall(maxRetries = 2) {
            CrptApiClient.getService().returnKiz(CrptReturnKizRequest(cis))
        }.map { response ->
            response
        }
    }

    /**
     * Проверка статуса токена авторизации ЧЗ.
     */
    suspend fun getAuthStatus(): Result<Boolean> {
        return safeCall(maxRetries = 1) {
            CrptApiClient.getService().getAuthStatus()
        }.map { response ->
            response.token_valid
        }
    }

    // === internals ===

    private suspend fun <T> safeCall(
        maxRetries: Int = 3,
        block: suspend () -> retrofit2.Response<T>
    ): Result<T> {
        var lastException: Exception? = null

        repeat(maxRetries) { attempt ->
            try {
                val response = block()
                if (response.isSuccessful) {
                    val body = response.body()
                    return if (body != null) {
                        Result.success(body)
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        Result.success(Unit as T)
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                    when (response.code()) {
                        401 -> return Result.failure(Exception("Токен ЧЗ не получен. Авторизуйтесь на /auth"))
                        403 -> return Result.failure(Exception("Лицензия недействительна"))
                        429 -> {
                            val delayMs = (1 shl attempt) * 1000L
                            delay(delayMs)
                            lastException = Exception("Слишком много запросов к серверу ЧЗ")
                        }
                        else -> return Result.failure(Exception("Ошибка сервера ЧЗ: HTTP ${response.code()}"))
                    }
                }
            } catch (e: java.net.SocketTimeoutException) {
                lastException = Exception("Таймаут сервера ЧЗ")
                delay(2000)
            } catch (e: java.net.UnknownHostException) {
                return Result.failure(Exception("Нет подключения к серверу ЧЗ"))
            } catch (e: Exception) {
                lastException = e
                delay(1000)
            }
        }
        return Result.failure(lastException ?: Exception("Неизвестная ошибка ЧЗ"))
    }
}
