package com.wb.fbs.tsd.utils

import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.*
import kotlinx.coroutines.delay

/**
 * Мониторинг буфера обмена для ТСД-сканеров.
 *
 * ТСД-сканер копирует штрихкод в буфер обмена. Этот хук опрашивает буфер
 * каждые ~300мс и если находит новое значение — вызывает [onScan].
 *
 * Возвращает последний обработанный код, чтобы не срабатывать дважды на один код.
 *
 * Usage:
 *   val scanned by rememberClipboardScanner { code ->
 *       // handle scan
 *   }
 */
@Composable
fun clipboardScanner(
    onScan: (String) -> Unit
): State<String?> {
    val intervalMs = 300L
    val lastScan = remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        var lastSeen = clipboard.text?.toString() ?: ""

        while (true) {
            val current = clipboard.text?.toString()
            if (current != null && current != lastSeen && current.isNotBlank()) {
                lastSeen = current
                lastScan.value = current
                onScan(current)
            }
            delay(intervalMs)
        }
    }

    return lastScan
}
