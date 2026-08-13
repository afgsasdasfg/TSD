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
     * Формат: GTIN + Serial (без разделителей)
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