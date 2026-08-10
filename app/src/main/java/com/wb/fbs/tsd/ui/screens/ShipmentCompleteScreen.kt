package com.wb.fbs.tsd.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wb.fbs.tsd.data.model.Order
import com.wb.fbs.tsd.ui.theme.*

/**
 * Экран 5: Готово → Отправка в доставку
 * Финальный экран с подтверждением и статусом
 */
@Composable
fun ShipmentCompleteScreen(
    order: Order,
    onNewOrderClick: () -> Unit,
    onPrintLabelClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Большая зелёная галочка
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = PrimaryGreen,
                modifier = Modifier.size(120.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "✅ ЗАКАЗ ГОТОВ",
                fontSize = TextSizeExtraLarge,
                fontWeight = FontWeight.ExtraBold,
                color = PrimaryGreen
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = order.clientName,
                fontSize = TextSizeLarge,
                color = OnDarkPrimary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Заказ #${order.id}",
                fontSize = TextSizeMedium,
                color = OnDarkSecondary
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Сводка
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    SummaryRow("Товаров:", "${order.items.sumOf { it.quantity }} шт")
                    Spacer(modifier = Modifier.height(8.dp))
                    SummaryRow("Проверено:", "${order.items.count { it.isFullyScanned }} / ${order.items.size}")
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val markedItems = order.items.filter { it.requiresMarking }
                    if (markedItems.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        SummaryRow("КИЗ введено:", "${markedItems.sumOf { it.markedQuantity }} / ${markedItems.sumOf { it.quantity }}")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Кнопки действий
            Button(
                onClick = onPrintLabelClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ButtonHeightLarge),
                colors = ButtonDefaults.buttonColors(
                    containerColor = InfoBlue
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = "🏷️ Печать этикетки",
                    fontSize = TextSizeLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onNewOrderClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ButtonHeightLarge),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryGreen
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = "📋 Следующий заказ",
                    fontSize = TextSizeLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = TextSizeMedium,
            color = OnDarkSecondary
        )
        
        Text(
            text = value,
            fontSize = TextSizeMedium,
            fontWeight = FontWeight.Bold,
            color = OnDarkPrimary
        )
    }
}
