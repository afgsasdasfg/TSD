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
import com.wb.fbs.tsd.ui.viewmodel.OrdersUiState  // ← ПРЯМОЙ ИМПОРТ

@Composable
fun OrderListScreen(
    orders: List<OrderEntity>,
    uiState: OrdersUiState,  // ← БЕЗ OrdersViewModel.
    onSyncClick: () -> Unit,
    onOrderClick: (OrderEntity) -> Unit,
    onScanClick: () -> Unit,
    onScanKizClick: () -> Unit,
    onCreateSupplyClick: () -> Unit,
    onSgtinEntered: (Long, String) -> Unit
) {
    var showKizDialog by remember { mutableStateOf(false) }
    var selectedOrder by remember { mutableStateOf<OrderEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(top = 32.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        // ЗАГОЛОВОК
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

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onSyncClick) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = "Синхронизировать",
                        tint = InfoBlue
                    )
                }
                IconButton(onClick = onScanClick) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Сканировать товар",
                        tint = PrimaryGreen
                    )
                }
                IconButton(onClick = onScanKizClick) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Сканировать КИЗ",
                        tint = WarningOrange
                    )
                }
            }
        }

        // Статус синхронизации
        if (uiState.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                color = InfoBlue
            )
        }

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

        // ← ИСПРАВЛЕНО: убрано if вне Composable, заменено на remember
        val syncText = remember(uiState.lastSyncCount, uiState.error) {
            if (uiState.lastSyncCount > 0 && uiState.error == null) "✅ Загружено ${uiState.lastSyncCount} заказов" else null
        }
        syncText?.let {
            Text(
                text = it,
                color = PrimaryGreen,
                fontSize = TextSizeSmall,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ДИАЛОГ КИЗ
        if (showKizDialog && selectedOrder != null) {
            KizInputDialog(
                order = selectedOrder!!,
                onConfirm = { sgtin ->
                    onSgtinEntered(selectedOrder!!.id, sgtin)
                    showKizDialog = false
                    selectedOrder = null
                },
                onDismiss = {
                    showKizDialog = false
                    selectedOrder = null
                }
            )
        }

        // СПИСОК ЗАКАЗОВ
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(orders, key = { it.id }) { order ->
                OrderCard(
                    order = order,
                    onClick = {
                        if (order.isMarked && order.sgtin.isNullOrBlank()) {
                            selectedOrder = order
                            showKizDialog = true
                        } else {
                            onOrderClick(order)
                        }
                    }
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!order.size.isNullOrBlank()) {
                            Text(
                                text = "Размер: ${order.size}",
                                fontSize = TextSizeMedium,
                                fontWeight = FontWeight.Medium,
                                color = OnDarkPrimary
                            )
                        }
                        if (!order.color.isNullOrBlank()) {
                            Text(
                                text = "Цвет: ${order.color}",
                                fontSize = TextSizeSmall,
                                color = OnDarkSecondary
                            )
                        }
                    }
                    Text(
                        text = "Заказ #${order.id}",
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