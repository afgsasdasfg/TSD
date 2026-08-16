package com.wb.fbs.tsd.utils

// utils/WbStickerParser.kt — новый файл
object WbStickerParser {

    fun extractOrderId(qrData: String): Long? {
        // Формат QR стикера WB: просто число (orderId)
        // Или: "WB-ORDER-12345"
        // Или: JSON с orderId

        // Пробуем разные форматы
        return when {
            qrData.matches("""\d+""".toRegex()) -> qrData.toLongOrNull()
            qrData.contains("orderId=") -> {
                qrData.substringAfter("orderId=").takeWhile { it.isDigit() }.toLongOrNull()
            }
            else -> qrData.filter { it.isDigit() }.takeIf { it.length >= 5 }?.toLongOrNull()
        }
    }

    fun isWbSticker(qrData: String): Boolean {
        return extractOrderId(qrData) != null
    }
}