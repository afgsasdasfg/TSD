package com.wb.fbs.tsd.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wb.fbs.tsd.data.db.OrderEntity
import com.wb.fbs.tsd.ui.theme.*

/**
 * Группировка заказов по артикул + размер для сборки.
 * Два этапа: Собрано X/N и Упаковано X/N (как в листе подбора WB).
 */
@Composable
fun PickingScreen(
    orders: List<OrderEntity>,
    onBackClick: () -> Unit,
    onOrderClick: (OrderEntity) -> Unit,
    onScanKizForOrder: (Long) -> Unit,
    onScanClick: () -> Unit
) {
    // Группировка по article + size (размер — главный ориентир, не баркод)
    val grouped = remember(orders) {
        orders.groupBy { "${it.article}|${it.size ?: "—"}" }
            .toSortedMap()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(top = 32.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        // Заголовок
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Default.ArrowBack, "Назад", tint = OnDarkPrimary)
            }
            Text(
                "📦 Сборка заказов",
                fontSize = TextSizeExtraLarge,
                fontWeight = FontWeight.Bold,
                color = OnDarkPrimary
            )
            IconButton(onClick = onScanClick) {
                Icon(Icons.Default.CheckCircle, "Сканировать", tint = PrimaryGreen)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Общая статистика
        val totalOrders = orders.size
        val scannedCount = orders.count { it.scannedAt != null }
        val packedCount = orders.count { it.packedAt != null }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Всего позиций: $totalOrders",
                    fontSize = TextSizeLarge,
                    fontWeight = FontWeight.Bold,
                    color = OnDarkPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Собрано: $scannedCount / $totalOrders",
                    color = if (scannedCount == totalOrders) PrimaryGreen else OnDarkSecondary
                )
                Text(
                    "Упаковано: $packedCount / $totalOrders",
                    color = if (packedCount == totalOrders) PrimaryGreen else OnDarkSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Список групп
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            grouped.forEach { (key, groupOrders) ->
                item(key = key) {
                    PickingGroupCard(
                        article = groupOrders.first().article,
                        name = groupOrders.first().name,
                        size = groupOrders.first().size,
                        color = groupOrders.first().color,
                        orders = groupOrders,
                        onOrderClick = onOrderClick,
                        onScanKizForOrder = onScanKizForOrder
                    )
                }
            }
        }
    }
}

@Composable
private fun PickingGroupCard(
    article: String,
    name: String,
    size: String?,
    color: String?,
    orders: List<OrderEntity>,
    onOrderClick: (OrderEntity) -> Unit,
    onScanKizForOrder: (Long) -> Unit
) {
    val total = orders.size
    val scanned = orders.count { it.scannedAt != null }
    val packed = orders.count { it.packedAt != null }
    val allDone = scanned == total && packed == total

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (allDone) DarkSurfaceVariant else DarkSurface
        ),
        border = if (scanned == total && packed < total)
            BorderStroke(2.dp, WarningOrange) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Шапка: артикул + размер крупно
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = article,
                        fontSize = TextSizeLarge,
                        fontWeight = FontWeight.Bold,
                        color = OnDarkPrimary
                    )
                    Text(
                        text = name,
                        fontSize = TextSizeMedium,
                        color = OnDarkSecondary
                    )
                    if (!size.isNullOrBlank()) {
                        Text(
                            text = "Размер: $size",
                            fontSize = TextSizeExtraLarge,
                            fontWeight = FontWeight.Bold,
                            color = WarningOrange
                        )
                    }
                    if (!color.isNullOrBlank()) {
                        Text(
                            text = "Цвет: $color",
                            fontSize = TextSizeSmall,
                            color = OnDarkSecondary
                        )
                    }
                    Text(
                        text = "Количество: $total шт",
                        fontSize = TextSizeMedium,
                        color = OnDarkSecondary
                    )
                }
                if (allDone) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Готово",
                        tint = PrimaryGreen,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Два этапа: Собрано и Упаковано
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Собрано
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Собрано $scanned/$total",
                        fontSize = TextSizeMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (scanned == total) PrimaryGreen else OnDarkSecondary
                    )
                    LinearProgressIndicator(
                        progress = { if (total > 0) scanned.toFloat() / total else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        color = PrimaryGreen,
                        trackColor = OnDarkDisabled.copy(alpha = 0.3f)
                    )
                }
                // Упаковано
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Упаковано $packed/$total",
                        fontSize = TextSizeMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (packed == total) PrimaryGreen else OnDarkSecondary
                    )
                    LinearProgressIndicator(
                        progress = { if (total > 0) packed.toFloat() / total else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        color = if (packed == total) PrimaryGreen else InfoBlue,
                        trackColor = OnDarkDisabled.copy(alpha = 0.3f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Список заказов в группе
            orders.forEachIndexed { index, order ->
                val isScanned = order.scannedAt != null
                val isPacked = order.packedAt != null
                val hasKiz = order.isMarked
                val kizDone = !order.sgtin.isNullOrBlank()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOrderClick(order) }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${index + 1}. Заказ #${order.id}",
                            fontSize = TextSizeSmall,
                            color = OnDarkSecondary
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Иконки этапов
                            Text(
                                if (isScanned) "✅" else "⬜",
                                fontSize = TextSizeSmall
                            )
                            Text(
                                "Собран",
                                fontSize = TextSizeSmall,
                                color = if (isScanned) PrimaryGreen else OnDarkDisabled
                            )
                            Text(
                                if (isPacked) "✅" else "⬜",
                                fontSize = TextSizeSmall
                            )
                            Text(
                                "Упакован",
                                fontSize = TextSizeSmall,
                                color = if (isPacked) PrimaryGreen else OnDarkDisabled
                            )
                        }
                        if (hasKiz) {
                            Text(
                                text = "КИЗ: ${order.sgtin ?: "—"}",
                                fontSize = TextSizeSmall,
                                color = if (kizDone) PrimaryGreen else WarningOrange
                            )
                        }
                    }

                    Checkbox(
                        checked = isScanned,
                        onCheckedChange = { onOrderClick(order) }
                    )
                }

                if (index < orders.size - 1) {
                    Divider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = OnDarkDisabled.copy(alpha = 0.2f)
                    )
                }
            }
        }
    }
}
