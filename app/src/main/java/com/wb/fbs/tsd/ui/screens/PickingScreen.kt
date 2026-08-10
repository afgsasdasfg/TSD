package com.wb.fbs.tsd.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wb.fbs.tsd.data.model.Order
import com.wb.fbs.tsd.data.model.OrderItem
import com.wb.fbs.tsd.ui.theme.*

/**
 * Экран 2: Подготовка заказа (Сборка)
 * Группировка по артикулу+размеру: «Футболка белая L × 5» — одна карточка
 */
@Composable
fun PickingScreen(
    order: Order,
    onBackClick: () -> Unit,
    onItemPicked: (OrderItem) -> Unit,
    onNextClick: () -> Unit
) {
    val totalItems = order.items.sumOf { it.quantity }
    val pickedItems = order.items.sumOf { it.scannedQuantity }
    val progress = if (totalItems > 0) pickedItems.toFloat() / totalItems else 0f
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        // Заголовок с навигацией
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Назад",
                    tint = OnDarkPrimary
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = "📦 Сборка: ${order.clientName}",
                fontSize = TextSizeExtraLarge,
                fontWeight = FontWeight.Bold,
                color = OnDarkPrimary
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Прогресс сборки
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = if (progress >= 1.0f) PrimaryGreen else InfoBlue,
                    trackColor = DarkSurface
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Собрано: $pickedItems из $totalItems",
                    fontSize = TextSizeLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (progress >= 1.0f) PrimaryGreen else OnDarkPrimary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Список товаров (сгруппированных)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(order.items, key = { it.id }) { item ->
                PickingItemCard(item = item, onPickClick = { onItemPicked(item) })
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Кнопка перехода к проверке
        Button(
            onClick = onNextClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(ButtonHeightLarge),
            enabled = pickedItems > 0,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (progress >= 1.0f) PrimaryGreen else InfoBlue,
                disabledContainerColor = OnDarkDisabled
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = "✅ К проверке",
                fontSize = TextSizeLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun PickingItemCard(item: OrderItem, onPickClick: () -> Unit) {
    val isFullyPicked = item.isFullyScanned
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isFullyPicked, onClick = onPickClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isFullyPicked) DarkSurfaceVariant else DarkSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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
                    text = "${item.name}",
                    fontSize = TextSizeLarge,
                    fontWeight = FontWeight.Bold,
                    color = OnDarkPrimary
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Арт: ${item.article} | ${item.color} | ${item.size}",
                    fontSize = TextSizeMedium,
                    color = OnDarkSecondary
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Штрихкод: ${item.barcode}",
                    fontSize = TextSizeSmall,
                    color = OnDarkDisabled
                )
            }
            
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(start = 16.dp)
            ) {
                Icon(
                    imageVector = if (isFullyPicked) Icons.Default.CheckCircle else Icons.Default.Circle,
                    contentDescription = null,
                    tint = if (isFullyPicked) PrimaryGreen else OnDarkSecondary,
                    modifier = Modifier.size(IconSizeLarge)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "× ${item.quantity}",
                    fontSize = TextSizeExtraLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isFullyPicked) PrimaryGreen else OnDarkPrimary
                )
                
                Text(
                    text = item.progressText,
                    fontSize = TextSizeSmall,
                    color = OnDarkSecondary
                )
            }
        }
    }
}
