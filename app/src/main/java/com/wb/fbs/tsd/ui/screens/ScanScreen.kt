package com.wb.fbs.tsd.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wb.fbs.tsd.ui.theme.*

@Composable
fun ScanScreen(
    onBackClick: () -> Unit,
    onBarcodeScanned: (String) -> Unit,
    onSgtinScanned: (String) -> Unit,
    requiresSgtin: Boolean,
    article: String?,
    size: String?
) {
    var scanMode by remember { mutableStateOf("barcode") }
    var input by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Default.ArrowBack, "Назад", tint = OnDarkPrimary)
            }
            Text(
                text = if (scanMode == "barcode") "🔍 Сканирование товара" else "🏷️ Сканирование КИЗ",
                fontSize = TextSizeExtraLarge,
                fontWeight = FontWeight.Bold,
                color = OnDarkPrimary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (article != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Текущий товар:", fontSize = TextSizeSmall, color = OnDarkSecondary)
                    Text("Артикул: $article", fontSize = TextSizeLarge, fontWeight = FontWeight.Bold, color = OnDarkPrimary)
                    if (size != null) {
                        Text("Размер: $size", fontSize = TextSizeMedium, color = OnDarkSecondary)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { scanMode = "barcode" },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (scanMode == "barcode") InfoBlue else Color.Transparent
                )
            ) {
                Text("Штрихкод", color = if (scanMode == "barcode") Color.White else OnDarkPrimary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = { scanMode = "sgtin" },
                modifier = Modifier.weight(1f),
                enabled = requiresSgtin,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (scanMode == "sgtin") PrimaryGreen else Color.Transparent
                )
            ) {
                Text("КИЗ", color = if (scanMode == "sgtin") Color.White else OnDarkPrimary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(
                    if (scanMode == "barcode") "Штрихкод товара" else "Код КИЗ (Data Matrix)",
                    color = OnDarkSecondary
                )
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (scanMode == "barcode") InfoBlue else PrimaryGreen,
                unfocusedBorderColor = OnDarkDisabled,
                focusedTextColor = OnDarkPrimary,
                unfocusedTextColor = OnDarkPrimary
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (input.isNotBlank()) {
                    if (scanMode == "barcode") {
                        onBarcodeScanned(input)
                    } else {
                        onSgtinScanned(input)
                    }
                    input = ""
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(ButtonHeightLarge),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (scanMode == "barcode") InfoBlue else PrimaryGreen
            )
        ) {
            Text(
                if (scanMode == "barcode") "✅ Подтвердить штрихкод" else "✅ Подтвердить КИЗ",
                fontSize = TextSizeLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (scanMode == "barcode")
                "💡 Отсканируйте штрихкод товара или введите вручную"
            else
                "💡 Отсканируйте Data Matrix код с маркировки Честный ЗНАК",
            fontSize = TextSizeSmall,
            color = OnDarkSecondary
        )
    }
}