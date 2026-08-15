package com.wb.fbs.tsd.utils

/**
 * Парсер КИЗ (Контрольный Идентификационный Знак) из Data Matrix
 * Формат: GS1 Digital Link или GS1-128
 */
object KizParser {

    /**
     * Извлекает GTIN из строки КИЗ
     * Поддерживает форматы:
     * - 01046079406932772112345678 (GS1-128)
     * - https://id.gs1.org/01/04607940693277/21/12345678 (Digital Link)
     * - 04607940693277 (чистый GTIN)
     */

    fun validateFull(kizString: String, expectedBarcode: String? = null): KizValidationResult {
        val cleaned = kizString.trim()

        // 1. Длина
        if (cleaned.length < 20) {
            return KizValidationResult.Invalid("КИЗ слишком короткий (${cleaned.length} симв., нужно 20+)")
        }

        // 2. GTIN
        val gtin = extractGtin(cleaned)
            ?: return KizValidationResult.Invalid("GTIN не найден. Формат: 01 + 14 цифр")

        // 3. Контрольная сумма GTIN
        if (!isValidGtinChecksum(gtin)) {
            return KizValidationResult.Invalid("Неверная контрольная сумма GTIN $gtin")
        }

        // 4. Серийный номер
        val serial = extractSerial(cleaned)
            ?: return KizValidationResult.Invalid("Серийный номер не найден (после 21)")

        if (serial.length < 6) {
            return KizValidationResult.Invalid("Серийный номер слишком короткий: $serial")
        }

        // 5. Соответствие заказу
        if (expectedBarcode != null) {
            val expectedGtin = extractGtin(expectedBarcode)
                ?: expectedBarcode.filter { it.isDigit() }.padStart(14, '0')

            if (gtin != expectedGtin) {
                return KizValidationResult.Invalid(
                    "Несовпадение GTIN!\n" +
                            "КИЗ: $gtin\n" +
                            "Товар: $expectedGtin\n" +
                            "Этот КИЗ от другого товара"
                )
            }
        }

        // 6. SGTIN
        val sgtin = gtin + serial

        return KizValidationResult.Valid(gtin, serial, sgtin)
    }

    /**
     * Проверка контрольной суммы GTIN-14
     */
    private fun isValidGtinChecksum(gtin: String): Boolean {
        if (gtin.length != 14 || !gtin.all { it.isDigit() }) return false

        val digits = gtin.map { it.digitToInt() }
        val sum = digits.take(13).mapIndexed { index, digit ->
            if (index % 2 == 0) digit * 3 else digit
        }.sum()

        val checkDigit = (10 - (sum % 10)) % 10
        return checkDigit == digits[13]
    }

    /**
     * Извлекает GTIN из строки КИЗ
     */
    fun extractGtin(kizString: String): String? {
        val cleaned = kizString.trim()

        // Формат 1: GS1-128 с AI (01)
        if (cleaned.startsWith("01") && cleaned.length >= 16) {
            return cleaned.substring(2, 16) // 14 цифр GTIN
        }

        // Формат 2: GS1 Digital Link
        val digitalLinkRegex = """/01/(\d{14})""".toRegex()
        digitalLinkRegex.find(cleaned)?.groupValues?.get(1)?.let { return it }

        // Формат 3: Чистый GTIN (14 цифр)
        if (cleaned.matches("""\d{14}""".toRegex())) {
            return cleaned
        }

        // Формат 4: GTIN с ведущим нулём или без (13-14 цифр)
        val digitsOnly = cleaned.filter { it.isDigit() }
        if (digitsOnly.length in 13..14) {
            return digitsOnly.padStart(14, '0')
        }

        return null
    }

    /**
     * Извлекает серийный номер из КИЗ
     */
    fun extractSerial(kizString: String): String? {
        val cleaned = kizString.trim()

        // GS1-128: AI (21) = серийный номер
        val idx21 = cleaned.indexOf("21")
        if (idx21 >= 0 && idx21 + 2 < cleaned.length) {
            return cleaned.substring(idx21 + 2).takeWhile { it.isDigit() || it.isLetter() }
        }

        // Digital Link: /21/XXXX
        val serialRegex = """/21/([A-Za-z0-9]+)""".toRegex()
        serialRegex.find(cleaned)?.groupValues?.get(1)?.let { return it }

        return null
    }

    /**
     * Полный КИЗ для отправки в WB (sgtin)
     */
    fun toSgtin(kizString: String): String? {
        val gtin = extractGtin(kizString) ?: return null
        val serial = extractSerial(kizString) ?: return null
        return gtin + serial
    }

    /**
     * Проверяет, похожа ли строка на КИЗ
     */
    fun isValidKizFormat(kizString: String): Boolean {
        return extractGtin(kizString) != null
    }
}

sealed class KizValidationResult {
    data class Valid(val gtin: String, val serial: String, val sgtin: String) : KizValidationResult()
    data class Invalid(val reason: String) : KizValidationResult()
}