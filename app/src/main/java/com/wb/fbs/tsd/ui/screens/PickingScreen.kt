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
 * Группировка заказов по артикулу для сборки
 * Как в WB: артикул → количество → прогресс
 */
@Composable
fun PickingScreen(
    orders: List<OrderEntity>,
    onBackClick: () -> Unit,
    onOrderClick: (OrderEntity) -> Unit,
    onScanKizForOrder: (Long) -> Unit,
    onScanClick: () -> Unit
) {
    // Группировка по article + barcode (barcode = уникальный для размера)
    val groupedBySku = remember(orders) {
        orders.groupBy { "${it.article}|${it.barcode ?: "no-barcode"}" }
            .toSortedMap() // Сортировка по артикулу
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

        // Статистика общая
        val totalOrders = orders.size
        val scannedOrders = orders.count { it.scannedAt != null }

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
                Text(
                    "Собрано: $scannedOrders / $totalOrders",
                    color = if (scannedOrders == totalOrders) PrimaryGreen else OnDarkSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Список групп
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            groupedBySku.forEach { (key, skuOrders) ->
                val (article, barcode) = key.split("|", limit = 2)

                item(key = key) {
                    SkuGroupCard(
                        article = article,
                        barcode = barcode,
                        orders = skuOrders,
                        onOrderClick = onOrderClick,
                        onScanKizForOrder = onScanKizForOrder
                    )
                }
            }
        }
    }
}

@Composable
private fun SkuGroupCard(
    article: String,
    barcode: String,
    orders: List<OrderEntity>,
    onOrderClick: (OrderEntity) -> Unit,
    onScanKizForOrder: (Long) -> Unit
) {
    val total = orders.size
    val scanned = orders.count { it.scannedAt != null }
    val isComplete = scanned == total

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isComplete) DarkSurfaceVariant else DarkSurface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Шапка группы
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
                    val size = orders.firstOrNull()?.size
                    if (!size.isNullOrBlank()) {
                        Text(
                            text = "Размер: $size",
                            fontSize = TextSizeMedium,
                            fontWeight = FontWeight.Medium,
                            color = OnDarkPrimary
                        )
                    }
                    val color = orders.firstOrNull()?.color
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
                if (isComplete) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Готово",
                        tint = PrimaryGreen,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // Прогресс-бар
            LinearProgressIndicator(
                progress = { scanned.toFloat() / total.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                color = if (isComplete) PrimaryGreen else InfoBlue,
                trackColor = OnDarkDisabled.copy(alpha = 0.3f)
            )

            // Список заказов (раскрывается по клику или всегда виден)
            orders.forEachIndexed { index, order ->
                val isOrderScanned = order.scannedAt != null
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
                        val orderSize = order.size?.let { " | $it" } ?: ""
                        val orderColor = order.color?.let { " | $it" } ?: ""
                        Text(
                            text = "${index + 1}. Заказ #${order.id}$orderSize$orderColor",
                            fontSize = TextSizeSmall,
                            color = OnDarkSecondary
                        )
                        if (hasKiz) {
                            Text(
                                text = "КИЗ: ${order.sgtin ?: "—"}",
                                fontSize = TextSizeSmall,
                                color = if (kizDone) PrimaryGreen else WarningOrange
                            )
                        }
                    }

                    Checkbox(
                        checked = isOrderScanned,
                        onCheckedChange = {
                            onOrderClick(order)
                        }
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

@Composable
private fun ArticleGroupCard(
    article: String,
    orders: List<OrderEntity>,
    onOrderClick: (OrderEntity) -> Unit
) {
    val total = orders.size
    val scanned = orders.count { it.scannedAt != null }
    val hasKiz = orders.any { it.isMarked }
    val kizDone = orders.count { it.isMarked && !it.sgtin.isNullOrBlank() }
    val isComplete = scanned == total && (!hasKiz || kizDone == orders.count { it.isMarked })

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                // Открываем детали — список заказов этого артикула
                // TODO: навигация на детали
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isComplete) DarkSurfaceVariant else DarkSurface
        ),
        border = if (!isComplete && hasKiz && kizDone < orders.count { it.isMarked })
            BorderStroke(2.dp, WarningOrange) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                        text = "Количество: $total шт",
                        fontSize = TextSizeMedium,
                        color = OnDarkSecondary
                    )
                    Text(
                        text = "Собрано: $scanned / $total",
                        fontSize = TextSizeMedium,
                        color = if (scanned == total) PrimaryGreen else WarningOrange
                    )
                    if (hasKiz) {
                        Text(
                            text = "КИЗ: $kizDone / ${orders.count { it.isMarked }}",
                            fontSize = TextSizeSmall,
                            color = if (kizDone == orders.count { it.isMarked }) PrimaryGreen else WarningOrange
                        )
                    }
                }
                if (isComplete) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Готово",
                        tint = PrimaryGreen,
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    // Кнопка "Сканировать" для этого артикула
                    Button(
                        onClick = {
                            // TODO: открыть сканер для этого артикула
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = InfoBlue)
                    ) {
                        Text("Скан", fontSize = TextSizeSmall)
                    }
                }
            }
        }
    }
}
