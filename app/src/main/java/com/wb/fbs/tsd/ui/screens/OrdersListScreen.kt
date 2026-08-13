package com.wb.fbs.tsd.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wb.fbs.tsd.data.db.OrderEntity
import com.wb.fbs.tsd.ui.theme.*
import com.wb.fbs.tsd.ui.viewmodel.OrdersUiState
import com.wb.fbs.tsd.ui.viewmodel.ScanUiResult

@Composable
fun OrderListScreen(
    orders: List<OrderEntity>,
    uiState: OrdersUiState,
    onSyncClick: () -> Unit,
    onOrderClick: (OrderEntity) -> Unit,
    onScanClick: () -> Unit,
    onCreateSupplyClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        // Заголовок
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📋 Заказы FBS",
                fontSize = TextSizeExtraLarge,
                fontWeight = FontWeight.Bold,
                color = OnDarkPrimary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onSyncClick) {
                    Icon(
                        Icons.Default.CloudDownload,
                        "Синхронизировать",
                        tint = InfoBlue
                    )
                }
                IconButton(onClick = onScanClick) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        "Сканировать",
                        tint = PrimaryGreen
                    )
                }
            }
        }

        // Статус синхронизации
        uiState.error?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.2f))
            ) {
                Text(
                    text = "⚠️ $error",
                    color = ErrorRed,
                    fontSize = TextSizeSmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        if (uiState.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                color = InfoBlue
            )
        }

        if (uiState.lastSyncCount > 0 && uiState.error == null) {
            Text(
                text = "✅ Загружено ${uiState.lastSyncCount} заказов",
                color = PrimaryGreen,
                fontSize = TextSizeSmall,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        // Скан-результат
        uiState.scanResult?.let { result ->
            ScanResultCard(result = result, onDismiss = { /* clearScanResult */ })
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Список заказов
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(orders, key = { it.id }) { order ->
                OrderCard(
                    order = order,
                    onClick = { onOrderClick(order) }
                )
            }
        }

        // Кнопка создания поставки
        if (orders.any { it.scannedAt != null && it.supplyId == null }) {
            Button(
                onClick = onCreateSupplyClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ButtonHeightLarge)
                    .padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
            ) {
                Text(
                    "📦 Создать поставку (${orders.count { it.scannedAt != null && it.supplyId == null }})",
                    fontSize = TextSizeLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun OrderCard(order: OrderEntity, onClick: () -> Unit) {
    val statusColor = when (order.status) {
        "new" -> InfoBlue
        "confirm" -> WarningOrange
        "complete" -> PrimaryGreen
        else -> OnDarkDisabled
    }

    val isScanned = order.scannedAt != null
    val hasSgtin = !order.sgtin.isNullOrBlank()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isScanned) DarkSurfaceVariant else DarkSurface
        ),
        border = if (order.isMarked && !hasSgtin)
            androidx.compose.foundation.BorderStroke(2.dp, WarningOrange)
        else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Арт: ${order.article}",
                        fontSize = TextSizeLarge,
                        fontWeight = FontWeight.Bold,
                        color = OnDarkPrimary
                    )
                    Text(
                        text = "Размер: ${order.size ?: "—"} | Цвет: ${order.color ?: "—"}",
                        fontSize = TextSizeMedium,
                        color = OnDarkSecondary
                    )
                    Text(
                        text = "Штрихкод: ${order.barcode ?: "—"}",
                        fontSize = TextSizeSmall,
                        color = OnDarkDisabled
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Box(
                        modifier = Modifier
                            .background(statusColor, shape = MaterialTheme.shapes.small)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = when (order.status) {
                                "new" -> "Новый"
                                "confirm" -> "На сборке"
                                "complete" -> "Готов"
                                else -> order.status
                            },
                            fontSize = TextSizeSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (order.isMarked) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (hasSgtin) "✅ КИЗ" else "⚠️ КИЗ",
                            fontSize = TextSizeSmall,
                            color = if (hasSgtin) PrimaryGreen else WarningOrange
                        )
                    }
                }
            }
            if (isScanned) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "✅ Отсканирован",
                    fontSize = TextSizeSmall,
                    color = PrimaryGreen
                )
            }
        }
    }
}

@Composable
private fun ScanResultCard(result: ScanUiResult, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (result) {
                is ScanUiResult.Success -> PrimaryGreen.copy(alpha = 0.2f)
                is ScanUiResult.NotFound -> ErrorRed.copy(alpha = 0.2f)
                is ScanUiResult.Error -> ErrorRed.copy(alpha = 0.2f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            when (result) {
                is ScanUiResult.Success -> {
                    Text(
                        "✅ Найден: ${result.article}",
                        color = PrimaryGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = TextSizeLarge
                    )
                    Text(
                        "Размер: ${result.size ?: "—"}",
                        color = OnDarkPrimary
                    )
                    if (result.requiresSgtin) {
                        Text(
                            "⚠️ Требуется КИЗ!",
                            color = WarningOrange,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                is ScanUiResult.NotFound -> {
                    Text(
                        "❌ Заказ не найден",
                        color = ErrorRed,
                        fontWeight = FontWeight.Bold
                    )
                }
                is ScanUiResult.Error -> {
                    Text(
                        "❌ Ошибка: ${result.message}",
                        color = ErrorRed
                    )
                }
            }
        }
    }
}