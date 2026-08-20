package com.wb.fbs.tsd.data.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

// Content API WB — https://content-api.wildberries.ru
// Токен для этого API должен иметь категорию доступа "Контент" (Content),
// не только "Маркетплейс" — если у seller-токена нет этой категории,
// getCardsList() будет падать 401/403.
interface WbContentApiService {

    @POST("/content/v2/get/cards/list")
    suspend fun getCardsList(@Body request: WbCardsListRequest): Response<WbCardsListResponse>
}

data class WbCardsListRequest(
    val settings: WbCardsSettings
)

data class WbCardsSettings(
    val cursor: WbCardsCursor,
    val filter: WbCardsFilter = WbCardsFilter()
)

data class WbCardsCursor(
    val limit: Int = 100,
    val updatedAt: String? = null, // для пагинации: значение из cursor предыдущего ответа
    val nmID: Long? = null         // для пагинации: значение из cursor предыдущего ответа
)

data class WbCardsFilter(
    val withPhoto: Int = -1 // -1 = все карточки, вне зависимости от наличия фото
)

data class WbCardsListResponse(
    val cards: List<WbCardDto>?,
    val cursor: WbCardsResponseCursor?
)

data class WbCardsResponseCursor(
    val updatedAt: String?,
    val nmID: Long?,
    val total: Int?
)

data class WbCardDto(
    val nmID: Long,
    val sizes: List<WbCardSizeDto>?
)

data class WbCardSizeDto(
    val chrtID: Long,
    val techSize: String?, // размер продавца, напр. "56", "62", "68" — обычно это и нужен
    val wbSize: String?,   // "рыночный" размер WB, не всегда задан
    val skus: List<String>?
)