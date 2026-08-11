package com.wb.fbs.tsd.data.model

/**
 * Клиент (владелец товаров)
 */
data class Client(
    val id: String,
    val name: String,
    val INN: String? = null
)

/**
 * Заказ от клиента
 */
data class Order(
    val id: String,
    val clientId: String,
    val clientName: String,
    val createdAt: Long, // timestamp
    val status: OrderStatus,
    val items: List<OrderItem>,
    val wbOrderId: String? = null // ID заказа в WB
)

enum class OrderStatus {
    NEW,           // Новый заказ
    ACCEPTED,      // Принят в работу
    PICKING,       // Сборка
    CHECKING,      // Проверка (сканирование)
    MARKING,       // Ввод КИЗ
    READY,         // Готов к отправке
    SHIPPED        // Отправлен
}

/**
 * Позиция в заказе (товар)
 * Группируется по артикулу + размеру для удобства сборки
 */
data class OrderItem(
    val id: String,
    val orderId: String,
    val article: String,        // Артикул WB
    val name: String,           // Наименование товара
    val color: String,          // Цвет
    val size: String,           // Размер
    val barcode: String,        // Штрихкод для сканирования
    val quantity: Int,          // Общее количество
    val scannedQuantity: Int = 0, // Отсканировано
    val requiresMarking: Boolean = false, // Требуется ли маркировка
    val markedQuantity: Int = 0,  // Сколько КИЗ введено
    val isGrouped: Boolean = true // Флаг группировки одинаковых SKU
) {
    val isFullyScanned: Boolean
        get() = scannedQuantity >= quantity
    
    val isFullyMarked: Boolean
        get() = !requiresMarking || markedQuantity >= quantity
    
    val progressText: String
        get() = "$scannedQuantity/$quantity"
    
    val markingProgressText: String
        get() = if (requiresMarking) "$markedQuantity/$quantity" else "—"
}

/**
 * КИЗ (Код Идентификации Знака) для маркировки
 */
data class KizCode(
    val code: String,           // Data Matrix код
    val itemId: String,         // ID позиции заказа
    val scannedAt: Long,        // Время сканирования
    val isValid: Boolean = true // Валидность кода
)
