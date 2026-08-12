package com.wb.fbs.tsd.data.network

import retrofit2.Response
import retrofit2.http.*
import com.wb.fbs.tsd.data.model.WBOrdersResponse
import com.wb.fbs.tsd.data.model.WBAuthorizeResponse
import com.wb.fbs.tsd.data.model.WBOrderDto
import com.wb.fbs.tsd.data.model.WBUpdateStatusRequest

interface WBApiService {

    @POST("api/auth")
    suspend fun authorize(@Body request: Map<String, String>): Response<WBAuthorizeResponse>

    @GET("orders/get")
    suspend fun getOrders(
        @Header("Authorization") auth: String,
        @Query("warehouse_id") warehouseId: Int,
        @Query("status") status: String? = null,
        @Query("from_date") fromDate: String? = null,
        @Query("to_date") toDate: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 100
    ): Response<WBOrdersResponse>

    @POST("orders/update-status")
    suspend fun updateOrderStatus(
        @Header("Authorization") auth: String,
        @Body request: WBUpdateStatusRequest
    ): Response<Unit>

    @POST("orders/set-completeness")
    suspend fun setCompleteness(
        @Header("Authorization") auth: String,
        @Body request: Any
    ): Response<Unit>
}