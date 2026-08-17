package com.wb.fbs.tsd.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wb.fbs.tsd.ui.theme.*

// ui/screens/HomeScreen.kt — добавить кнопку

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    onSyncClick: () -> Unit,
    lastSyncTime: Long = 0,  // ← ДОБАВИТЬ
    isLoading: Boolean = false  // ← ДОБАВИТЬ
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "📦 TSD Wildberries",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = OnDarkPrimary
        )
        Spacer(modifier = Modifier.height(32.dp))

        // Кнопка синхронизации с временем
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .clickable(enabled = !isLoading) { onSyncClick() },
            colors = CardDefaults.cardColors(
                containerColor = if (isLoading)
                    InfoBlue.copy(alpha = 0.3f)
                else
                    DarkSurfaceVariant
            ),
            border = if (isLoading) BorderStroke(2.dp, InfoBlue) else null
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (isLoading) "⏳" else "☁️",
                    fontSize = 24.sp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        if (isLoading) "Синхронизация..." else "Проверить обновления",
                        color = if (isLoading) InfoBlue else OnDarkPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = TextSizeLarge
                    )
                    if (lastSyncTime > 0 && !isLoading) {
                        Text(
                            "Последняя: ${formatTime(lastSyncTime)}",
                            color = OnDarkSecondary,
                            fontSize = TextSizeSmall
                        )
                    }
                }
            }
        }

        val buttons = listOf(
            listOf("📋", "Заказы FBS", "#2196F3", "orders"),
            listOf("🚚", "Сборка заказов", "#9C27B0", "picking"),
            listOf("📦", "Собранные заказы", "#FF9800", "collected"),
            listOf("📋", "Сканирование", "#4CAF50", "scan")
        )

        for (button in buttons) {
            val icon = button[0] as String
            val label = button[1] as String
            val colorHex = button[2] as String
            val route = button[3] as String

            Button(
                onClick = { onNavigate(route) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(android.graphics.Color.parseColor(colorHex))
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(icon, fontSize = 24.sp)
                    Text(label, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Сканируйте стикер WB для привязки КИЗ к заказу",
            fontSize = TextSizeSmall,
            color = OnDarkSecondary
        )
    }
}

// Форматирование времени
private fun formatTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "только что"
        diff < 3_600_000 -> "${diff / 60_000} мин. назад"
        diff < 86_400_000 -> "${diff / 3_600_000} ч. назад"
        else -> {
            val sdf = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
            sdf.format(java.util.Date(timestamp))
        }
    }
}