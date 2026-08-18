package com.wb.fbs.tsd.data.network

import retrofit2.Response
import retrofit2.http.*

// ==================== DTO ====================

data class WbNewOrdersResponse(
    val orders: List<WbOrderDto>
)

data class WbOrderDto(
    val id: Long,
    val orderUid: String?,
    val article: String?,
    val nmId: Long?,
    val chrtId: Long?,
    val colorCode: String?,
    val size: WbSizeDto?,
    val skus: List<String>?,
    val price: Long?,
    val finalPrice: Long?,
    val warehouseId: Long?,
    val officeId: Long?,
    val cargoType: Int?,
    val deliveryType: String?,
    val requiredMeta: List<String>?,
    val optionalMeta: List<String>?,
    val comment: String?,
    val createdAt: String?,  // ISO 8601
    val supplyId: String?,
    val address: WbAddressDto?,
    val options: WbOptionsDto?
)

data class WbSizeDto(
    val chrtId: Long?,
    val techSize: String?,
    val brandSize: String?,
    val waist: Double?,
    val chest: Double?,
    val hips: Double?,
    val weight: Double?,
    val length: Double?
)

data class WbAddressDto(
    val fullAddress: String?,
    val longitude: Double?,
    val latitude: Double?
)

data class WbOptionsDto(
    val isB2B: Boolean?
)

data class WbStatusRequest(
    val orders: List<Long>
)

data class WbStatusResponse(
    val orders: List<WbStatusDto>
)

data class WbStatusDto(
    val id: Long,
    val supplierStatus: String,
    val wbStatus: String
)

data class WbStickerRequest(
    val orders: List<Long>
)

data class WbStickerResponse(
    val stickers: List<WbStickerDto>
)

data class WbStickerDto(
    val orderId: Long,
    val partA: String,
    val partB: String,
    val barcode: String,
    val file: String  // base64
)

data class WbCreateSupplyRequest(
    val name: String
)

data class WbCreateSupplyResponse(
    val id: String  // WB-GI-XXXXXXX
)

data class WbSuppliesResponse(
    val next: Long?,
    val supplies: List<WbSupplyDto>
)

data class WbSupplyDto(
    val id: String,
    val name: String,
    val createdAt: String,
    val closedAt: String?,
    val scanDt: String?,
    val cargoType: Int?,
    val crossBorderType: Int?,
    val destinationOfficeId: Long?,
    val done: Boolean?,
    val isB2b: Boolean?
)

data class WbAddOrdersToSupplyRequest(
    val orders: List<Long>
)

data class WbSgtinRequest(
    val sgtin: String
)

data class WbBarcodeResponse(
    val barcode: String,
    val file: String  // base64 SVG/PNG/ZPL
)

// ==================== API ====================

interface WbApiService {

    // --- Сборочные задания ---

    @GET("/api/v3/orders/new")
    suspend fun getNewOrders(): Response<WbNewOrdersResponse>

    @POST("/api/v3/orders/stickers")
    suspend fun getStickers(
        @Query("type") type: String = "svg",
        @Query("width") width: Int = 58,
        @Query("height") height: Int = 40,
        @Body request: WbStickerRequest
    ): Response<WbStickerResponse>

    @PATCH("/api/v3/orders/{orderId}/cancel")
    suspend fun cancelOrder(
        @Path("orderId") orderId: Long
    ): Response<Unit>

    // --- Метаданные (КИЗ) ---

    @PUT("/api/v3/orders/{orderId}/meta/sgtin")
    suspend fun setSgtin(
        @Path("orderId") orderId: Long,
        @Body request: WbSgtinRequest
    ): Response<Unit>

    @POST("/api/marketplace/v3/orders/meta")
    suspend fun getOrdersMeta(
        @Body request: WbStatusRequest
    ): Response<WbMetaResponse>

    // --- Поставки ---

    @POST("/api/v3/supplies")
    suspend fun createSupply(
        @Body request: WbCreateSupplyRequest
    ): Response<WbCreateSupplyResponse>

    @GET("/api/v3/supplies")
    suspend fun getSupplies(
        @Query("limit") limit: Int = 1000,
        @Query("next") next: Long = 0
    ): Response<WbSuppliesResponse>

    @GET("/api/v3/supplies/{supplyId}")
    suspend fun getSupply(
        @Path("supplyId") supplyId: String
    ): Response<WbSupplyDto>

    @DELETE("/api/v3/supplies/{supplyId}")
    suspend fun deleteSupply(
        @Path("supplyId") supplyId: String
    ): Response<Unit>

    @PATCH("/api/marketplace/v3/supplies/{supplyId}/orders")
    suspend fun addOrdersToSupply(
        @Path("supplyId") supplyId: String,
        @Body request: WbAddOrdersToSupplyRequest
    ): Response<Unit>

    @PATCH("/api/v3/supplies/{supplyId}/deliver")
    suspend fun deliverSupply(
        @Path("supplyId") supplyId: String
    ): Response<Unit>

    @GET("/api/v3/supplies/{supplyId}/barcode")
    suspend fun getSupplyBarcode(
        @Path("supplyId") supplyId: String,
        @Query("type") type: String = "svg"
    ): Response<WbBarcodeResponse>

    @POST("/api/v3/orders/status")
    suspend fun getOrdersStatus(@Body request: WbStatusRequest): Response<WbStatusResponse>

    @GET("/api/marketplace/v3/supplies/{supplyId}/order-ids")
    suspend fun getSupplyOrderIds(
        @Path("supplyId") supplyId: String
    ): Response<WbSupplyOrdersResponse>
}

data class WbMetaResponse(
    val orders: List<WbMetaOrderDto>
)

data class WbMetaOrderDto(
    val id: Long,
    val meta: WbMetaDetailsDto?
)

data class WbMetaDetailsDto(
    val imei: WbMetaValueDto?,
    val uin: WbMetaValueDto?,
    val gtin: WbMetaValueDto?,
    val sgtin: WbMetaListDto?,
    val expiration: WbMetaValueDto?,
    val customsDeclaration: WbMetaValueDto?
)

data class WbMetaValueDto(
    val value: String?
)

data class WbMetaListDto(
    val value: List<String>?
)

data class WbSupplyOrdersResponse(
    val orderIds: List<Long>
)
