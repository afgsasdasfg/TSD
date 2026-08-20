package com.wb.fbs.tsd.utils

/**
 * ВГХ (весогабаритные характеристики) ПВЗ для Wildberries FBS.
 * Ограничения зависят от cargoType заказа — показываем на экране
 * сканирования, чтобы сборщик видел пройдёт ли товар на ПВЗ.
 *
 * Источник: WB FBS документация + ТЗ заказчика.
 * Базовые лимиты (cargoType=1): 0.15×0.15×0.02 м, 0.07 кг
 */
object VghLimits {
    data class Limits(
        val maxLengthCm: Double,
        val maxWidthCm: Double,
        val maxHeightCm: Double,
        val maxWeightKg: Double
    ) {
        fun dimensionsText(): String = "%.0f×%.0f×%.0f см".format(maxLengthCm, maxWidthCm, maxHeightCm)
        fun weightText(): String = if (maxWeightKg < 1) "%.0f г".format(maxWeightKg * 1000) else "%.1f кг".format(maxWeightKg)
    }

    // WB FBS cargo types:
    // 1 — Маленькие (одежда, мелочёвка)
    // 2 — Средние
    // 3 — Большие
    // 4 — Крупногабаритные
    private val limitsByCargoType = mapOf(
        1 to Limits(15.0, 15.0, 2.0, 0.07),
        2 to Limits(30.0, 30.0, 15.0, 2.0),
        3 to Limits(45.0, 45.0, 30.0, 5.0),
        4 to Limits(115.0, 115.0, 60.0, 25.0)
    )

    fun getLimits(cargoType: Int): Limits? = limitsByCargoType[cargoType]

    fun getLimitsOrDefault(cargoType: Int): Limits =
        limitsByCargoType[cargoType] ?: Limits(15.0, 15.0, 2.0, 0.07)
}
