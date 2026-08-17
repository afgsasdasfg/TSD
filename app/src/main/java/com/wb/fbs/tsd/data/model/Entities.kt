package com.wb.fbs.tsd.data.db

import androidx.room.*
import java.time.Instant

// ==================== СБОРОЧНЫЕ ЗАДАНИЯ ====================

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey
    val id: Long,              // ID сборочного задания в WB
    val orderUid: String,      // orderUid для группировки
    val article: String,       // Артикул WB
    val nmId: Long,            // nmId карточки
    val chrtId: Long,          // chrtId (размер/цвет)
    val name: String,          // Название товара
    val color: String?,        // Цвет
    val size: String?,         // Размер
    val barcode: String?,      // Штрихкод (sku)
    val price: Long,           // Цена (в копейках)
    val finalPrice: Long,      // Цена со скидкой
    val warehouseId: Long,     // ID склада
    val officeId: Long?,       // ID ПВЗ/склада назначения
    val cargoType: Int,        // Тип габаритов
    val deliveryType: String,  // fbs / dbw / dbs
    val requiredMeta: String,  // JSON массив requiredMeta
    val optionalMeta: String,  // JSON массив optionalMeta
    val comment: String?,      // Комментарий покупателя
    val createdAt: Long,       // Timestamp создания
    val supplyId: String?,     // ID поставки (null если не в поставке)
    val status: String,        // new / confirm / complete / cancel
    val isMarked: Boolean,     // Требуется маркировка
    val sgtin: String?,        // Закреплённый КИЗ
    val isSynced: Boolean,     // Синхронизировано с WB
    val scannedAt: Long?,      // Когда отсканирован на ТСД
    val stickerPrinted: Boolean,// Стикер распечатан
    // Код со стикера заказа (WbStickerDto из POST /api/v3/orders/stickers).
    // Уникален на конкретный заказ — в отличие от barcode товара, который
    // может повторяться у одного и того же товара на разных кабинетах.
    val stickerBarcode: String? = null, // то, что реально сканируется со стикера
    val stickerPartA: String? = null,   // цифры под баркодом (часть A)
    val stickerPartB: String? = null,   // цифры под баркодом (часть B)
    val updatedAt: Long = System.currentTimeMillis()
)

// ==================== ПОСТАВКИ ====================

@Entity(tableName = "supplies")
data class SupplyEntity(
    @PrimaryKey
    val id: String,            // WB-GI-XXXXXXX
    val name: String,          // Название поставки
    val createdAt: Long,
    val closedAt: Long?,
    val scanDt: Long?,         // Когда отсканирована на ПВЗ
    val cargoType: Int,
    val crossBorderType: Int,
    val destinationOfficeId: Long?,
    val isDone: Boolean,
    val isB2b: Boolean,
    val orderCount: Int,
    val isSynced: Boolean,
    val qrCodeSvg: String?,    // SVG QR-кода
    val status: String         // active / delivered / cancelled
)

// ==================== СВЯЗЬ ЗАКАЗ-ПОСТАВКА ====================

@Entity(
    tableName = "supply_orders",
    primaryKeys = ["supplyId", "orderId"]
)
data class SupplyOrderEntity(
    val supplyId: String,
    val orderId: Long,
    val addedAt: Long = System.currentTimeMillis()
)

// ==================== ИСТОРИЯ СКАНИРОВАНИЯ ====================

@Entity(tableName = "scan_logs")
data class ScanLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val orderId: Long?,
    val supplyId: String?,
    val scanType: String,      // barcode / sgtin / qr_supply / sticker
    val scannedValue: String,
    val success: Boolean,
    val errorMessage: String?,
    val timestamp: Long = System.currentTimeMillis()
)

// ==================== НАСТРОЙКИ ====================

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey
    val key: String,
    val value: String
)