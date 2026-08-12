package com.wb.fbs.tsd.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wb.fbs.tsd.data.model.Order
import com.wb.fbs.tsd.data.model.OrderStatus
import com.wb.fbs.tsd.ui.theme.*

@Composable
fun OrdersListScreen(
    orders: List<Order>,
    onLoadOrdersClick: () -> Unit,
    onOrderClick: (Order) -> Unit,
    onReceivingClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📋 Заказы",
                fontSize = TextSizeExtraLarge,
                fontWeight = FontWeight.Bold,
                color = OnDarkPrimary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onLoadOrdersClick) {
                    Icon(Icons.Default.CloudDownload, "Обновить из API", tint = InfoBlue)
                }
                IconButton(onClick = onReceivingClick) {
                    Icon(Icons.Default.ReceiptLong, "Приёмка", tint = PrimaryGreen)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(orders, key = { it.id }) { order ->
                OrderCard(order = order, onClick = { onOrderClick(order) })
            }
        }
    }
}

@Composable
private fun OrderCard(order: Order, onClick: () -> Unit) {
    val statusColor = when (order.status) {
        OrderStatus.READY, OrderStatus.SHIPPED -> PrimaryGreen
        OrderStatus.CHECKING, OrderStatus.MARKING -> WarningOrange
        OrderStatus.NEW, OrderStatus.ACCEPTED, OrderStatus.PICKING -> InfoBlue
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = order.clientName,
                    fontSize = TextSizeLarge,
                    fontWeight = FontWeight.Bold,
                    color = OnDarkPrimary
                )
                Box(
                    modifier = Modifier
                        .background(statusColor, shape = MaterialTheme.shapes.small)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = order.status.name,
                        fontSize = TextSizeSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Заказов: ${order.items.size} | Товаров: ${order.items.sumOf { it.quantity }}",
                fontSize = TextSizeMedium,
                color = OnDarkSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "ID: ${order.id}", fontSize = TextSizeSmall, color = OnDarkDisabled)
        }
    }
}