package com.wb.fbs.tsd.data.model

// Список заказов
data class WBOrdersResponse(
    val data: List<WBOrderDto>,
    val totalOrderCount: Int? = null,
    val nextPageToken: String? = null
)

data class WBOrderDto(
    val orderNumber: String,
    val supplierNumber: String? = null,
    val dtmCode: String? = null,
    val status: String,
    val customerName: String? = null,
    val warehouseName: String? = null,
    val purchaserName: String? = null,
    val chc: Int? = null,
    val items: List<WBOrderItemDto>? = null,
    val createdAt: String? = null,
    val id: Long? = null
)

data class WBOrderItemDto(
    val nmSku: Int,
    val name: String,
    val color: String? = null,
    val size: String? = null,
    val quantity: Int,
    val markedQuantity: Int = 0,
    val barcode: String? = null,
    val isKiz: Boolean? = null,
    val uuid: String? = null
)

// Обновление статуса заказа
data class WBUpdateStatusRequest(
    val orders: List<WBUpdateStatusEntry>
)

data class WBUpdateStatusEntry(
    val orderNumber: String,
    val status: String
)

// Комплектация
data class WBCompletenessRequest(
    val orders: List<WBCompletenessOrder>
)

data class WBCompletenessOrder(
    val orderNumber: String,
    val completeness: Boolean
)