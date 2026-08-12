package com.wb.fbs.tsd.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wb.fbs.tsd.ui.theme.*

@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("📦 TSD Wildberries", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = OnDarkPrimary)
        Spacer(modifier = Modifier.height(32.dp))

        val buttons = listOf(
            Triple("📋 Приёмка товаров", "#4CAF50", "receiving"),
            Triple("🚚 Сборка заказов", "#2196F3", "orders"),
            Triple("🏷️ Маркировка", "#FF9800", "marking")
        )

        for ((label, colorHex, route) in buttons) {
            Button(
                onClick = { onNavigate(route) },
                modifier = Modifier.fillMaxWidth().height(64.dp).padding(vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(android.graphics.Color.parseColor(colorHex)))
            ) {
                Text(label, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Color.White)
            }
        }
    }
}