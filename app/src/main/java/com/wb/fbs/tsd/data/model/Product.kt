package com.wb.fbs.tsd.data.model

data class Product(
    val id: String,
    val article: String,
    val name: String,
    val color: String,
    val size: String,
    val barcode: String,
    val requiresMarking: Boolean = false,
    val quantityInStock: Int = 0,
    val kizCodes: List<String> = emptyList(),
    val receivedAt: Long = System.currentTimeMillis()
) {
    val isFullyMarked: Boolean
        get() = !requiresMarking || kizCodes.isNotEmpty()
}