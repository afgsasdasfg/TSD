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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wb.fbs.tsd.data.db.OrderEntity
import com.wb.fbs.tsd.ui.theme.*

@Composable
fun CollectedOrdersScreen(
    orders: List<OrderEntity>,
    onBackClick: () -> Unit,
    onDeliverClick: (List<Long>) -> Unit,  // Список orderId для передачи
    onShowQrClick: (String) -> Unit  // Показать QR поставки
) {
    // Только собранные заказы (scannedAt != null)
    val collectedOrders = remember(orders) {
        orders.filter { it.scannedAt != null }
    }

    // Выбранные заказы
    var selectedOrders by remember { mutableStateOf(setOf<Long>()) }
    val allSelected = selectedOrders.size == collectedOrders.size && collectedOrders.isNotEmpty()

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
                "📦 Собранные заказы",
                fontSize = TextSizeExtraLarge,
                fontWeight = FontWeight.Bold,
                color = OnDarkPrimary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Статистика
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Собрано: ${collectedOrders.size}",
                    fontSize = TextSizeLarge,
                    fontWeight = FontWeight.Bold,
                    color = OnDarkPrimary
                )
                Text(
                    "Выбрано: ${selectedOrders.size}",
                    color = OnDarkSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Кнопки массового выбора
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    selectedOrders = if (allSelected) emptySet() else collectedOrders.map { it.id }.toSet()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (allSelected) "Снять все" else "Выбрать все")
            }

            Button(
                onClick = { onDeliverClick(selectedOrders.toList()) },
                enabled = selectedOrders.isNotEmpty(),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
            ) {
                Text("🚚 Передать в доставку")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Список собранных заказов
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(collectedOrders, key = { it.id }) { order ->
                CollectedOrderCard(
                    order = order,
                    isSelected = selectedOrders.contains(order.id),
                    onToggle = {
                        selectedOrders = if (selectedOrders.contains(order.id)) {
                            selectedOrders - order.id
                        } else {
                            selectedOrders + order.id
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun CollectedOrderCard(
    order: OrderEntity,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) DarkSurfaceVariant else DarkSurface
        ),
        border = if (isSelected) BorderStroke(2.dp, PrimaryGreen) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                    text = "Заказ #${order.id}",
                    fontSize = TextSizeSmall,
                    color = OnDarkSecondary
                )
                Text(
                    text = "Штрихкод: ${order.barcode ?: "—"}",
                    fontSize = TextSizeSmall,
                    color = OnDarkDisabled
                )
                if (order.isMarked) {
                    Text(
                        text = "КИЗ: ${order.sgtin ?: "—"}",
                        fontSize = TextSizeSmall,
                        color = if (!order.sgtin.isNullOrBlank()) PrimaryGreen else WarningOrange
                    )
                }
            }

            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = PrimaryGreen,
                    uncheckedColor = OnDarkDisabled
                )
            )
        }
    }
}