package com.wb.fbs.tsd.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wb.fbs.tsd.data.db.OrderEntity
import com.wb.fbs.tsd.ui.theme.*

/**
 * Экран «Дубликат КМ» — показывает заказы с маркировкой (isMarked=true),
 * у которых уже есть sgtin. Позволяет скопировать КИЗ для печати дубликата.
 */
@Composable
fun DuplicateKmScreen(
    orders: List<OrderEntity> = emptyList(),
    onBackClick: () -> Unit
) {
    val markedOrders = remember(orders) {
        orders.filter { it.isMarked && !it.sgtin.isNullOrBlank() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(top = 32.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Default.ArrowBack, "Назад", tint = OnDarkPrimary)
            }
            Text(
                "🏷️ Дубликат КМ",
                fontSize = TextSizeExtraLarge,
                fontWeight = FontWeight.Bold,
                color = OnDarkPrimary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Заказов с КИЗ: ${markedOrders.size}",
                    fontSize = TextSizeLarge,
                    fontWeight = FontWeight.Bold,
                    color = OnDarkPrimary
                )
                Text(
                    "Нажмите на заказ, чтобы скопировать КИЗ",
                    fontSize = TextSizeSmall,
                    color = OnDarkSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (markedOrders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Нет заказов с КИЗ",
                    fontSize = TextSizeLarge,
                    color = OnDarkSecondary
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(markedOrders, key = { it.id }) { order ->
                    DuplicateKmCard(order = order)
                }
            }
        }
    }
}

@Composable
private fun DuplicateKmCard(order: OrderEntity) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        onClick = {
            order.sgtin?.let {
                clipboard.setText(AnnotatedString(it))
                copied = true
            }
        }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Арт: ${order.article}",
                    fontSize = TextSizeLarge,
                    fontWeight = FontWeight.Bold,
                    color = OnDarkPrimary
                )
                Text(
                    order.name,
                    fontSize = TextSizeMedium,
                    color = OnDarkSecondary
                )
                if (!order.size.isNullOrBlank()) {
                    Text(
                        "Размер: ${order.size}",
                        fontSize = TextSizeMedium,
                        color = OnDarkPrimary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "КИЗ: ${order.sgtin}",
                    fontSize = TextSizeSmall,
                    color = WarningOrange,
                    fontWeight = FontWeight.Medium
                )
            }
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = "Копировать",
                tint = if (copied) PrimaryGreen else OnDarkSecondary
            )
        }
        if (copied) {
            Text(
                "✅ Скопировано",
                fontSize = TextSizeSmall,
                color = PrimaryGreen,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
        }
    }
}
