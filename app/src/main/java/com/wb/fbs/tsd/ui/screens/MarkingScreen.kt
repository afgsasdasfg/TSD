package com.wb.fbs.tsd.ui.screens

import androidx.compose.foundation.background
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
 * Экран 4: Ввод КИЗ (маркировка) — сканирование Data Matrix кодов
 * Прогресс по каждому товару: сколько нужно ввести, сколько уже введено
 */
@Composable
fun MarkingScreen(
    order: Order,
    onBackClick: () -> Unit,
    onKizScanned: (String, OrderItem) -> Unit,
    onCompleteClick: () -> Unit
) {
    val itemsRequiringMarking = order.items.filter { it.requiresMarking }
    val totalKizNeeded = itemsRequiringMarking.sumOf { it.quantity }
    val totalKizScanned = itemsRequiringMarking.sumOf { it.markedQuantity }
    val progress = if (totalKizNeeded > 0) totalKizScanned.toFloat() / totalKizNeeded else 1f
    val isComplete = progress >= 1.0f
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        // Заголовок
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
                text = "🏷️ Маркировка (КИЗ)",
                fontSize = TextSizeExtraLarge,
                fontWeight = FontWeight.Bold,
                color = OnDarkPrimary
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Общий прогресс КИЗ КРУПНЫМ шрифтом
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$totalKizScanned / $totalKizNeeded",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isComplete) PrimaryGreen else WarningOrange
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp),
                        color = if (isComplete) PrimaryGreen else WarningOrange,
                        trackColor = DarkSurface
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = if (isComplete) "✅ ВСЕ КИЗ ВВЕДЕНЫ" else "Сканируйте Data Matrix",
                        fontSize = TextSizeLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isComplete) PrimaryGreen else OnDarkSecondary
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Список товаров с прогрессом маркировки
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(order.items, key = { it.id }) { item ->
                MarkingItemCard(item = item, onKizScanned = { code -> onKizScanned(code, item) })
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Кнопка завершения
        Button(
            onClick = onCompleteClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(ButtonHeightLarge),
            enabled = isComplete || itemsRequiringMarking.isEmpty(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isComplete) PrimaryGreen else OnDarkDisabled
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = "✅ ГОТОВО К ОТПРАВКЕ",
                fontSize = TextSizeLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun MarkingItemCard(item: OrderItem, onKizScanned: (String) -> Unit) {
    val isFullyMarked = item.isFullyMarked
    val needsMarking = item.requiresMarking
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (!needsMarking) DarkSurface.copy(alpha = 0.5f) 
                           else if (isFullyMarked) DarkSurfaceVariant 
                           else DarkSurface
        ),
        border = if (needsMarking && !isFullyMarked) androidx.compose.foundation.BorderStroke(2.dp, WarningOrange) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${item.name}",
                            fontSize = TextSizeMedium,
                            fontWeight = FontWeight.Bold,
                            color = OnDarkPrimary
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        if (!needsMarking) {
                            Badge {
                                Text("Без маркировки", fontSize = TextSizeSmall)
                            }
                        }
                    }
                    
                    Text(
                        text = "${item.article} | ${item.size}",
                        fontSize = TextSizeSmall,
                        color = OnDarkSecondary
                    )
                }
                
                Icon(
                    imageVector = if (isFullyMarked || !needsMarking) Icons.Default.CheckCircle else Icons.Default.Circle,
                    contentDescription = null,
                    tint = if (isFullyMarked || !needsMarking) PrimaryGreen else WarningOrange,
                    modifier = Modifier.size(IconSizeMedium)
                )
            }
            
            if (needsMarking) {
                Spacer(modifier = Modifier.height(12.dp))
                
                // Прогресс-бар для этого товара
                val itemProgress = item.markedQuantity.toFloat() / item.quantity
                LinearProgressIndicator(
                    progress = itemProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = if (isFullyMarked) PrimaryGreen else WarningOrange,
                    trackColor = DarkSurface
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "КИЗ: ${item.markingProgressText}",
                        fontSize = TextSizeLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isFullyMarked) PrimaryGreen else OnDarkPrimary
                    )
                    
                    Text(
                        text = "× ${item.quantity}",
                        fontSize = TextSizeMedium,
                        color = OnDarkSecondary
                    )
                }
            }
        }
    }
}
