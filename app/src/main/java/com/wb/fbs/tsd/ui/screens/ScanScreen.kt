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
    onStickerScanned: (String) -> Unit,  // ← НОВЫЙ
    requiresSgtin: Boolean,
    article: String?,
    size: String?
) {
    var scanMode by remember { mutableStateOf("sticker") } // sticker | barcode | sgtin
    var input by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    )  {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Default.ArrowBack, "Назад", tint = OnDarkPrimary)
            }
            Text(
                text = when (scanMode) {
                    "sticker" -> "📋 Сканирование стикера WB"
                    "barcode" -> "🔍 Сканирование товара"
                    "sgtin" -> "🏷️ Сканирование КИЗ"
                    else -> "Сканирование"
                },
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
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = { scanMode = "sticker" },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (scanMode == "sticker") WarningOrange else Color.Transparent
                )
            ) {
                Text("Стикер WB", color = if (scanMode == "sticker") Color.White else OnDarkPrimary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(
                    when (scanMode) {
                        "barcode" -> "Штрихкод товара"
                        "sgtin" -> "Код КИЗ (Data Matrix)"
                        "sticker" -> "Номер стикера WB (QR-код)"
                        else -> "Введите код"
                    },
                    color = OnDarkSecondary
                )
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = when (scanMode) {
                    "sticker" -> WarningOrange
                    "barcode" -> InfoBlue
                    else -> PrimaryGreen
                },
                unfocusedBorderColor = OnDarkDisabled,
                focusedTextColor = OnDarkPrimary,
                unfocusedTextColor = OnDarkPrimary
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (input.isNotBlank()) {
                    when (scanMode) {
                        "barcode" -> onBarcodeScanned(input)
                        "sgtin" -> onSgtinScanned(input)
                        "sticker" -> onStickerScanned(input)
                    }
                    input = ""
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(ButtonHeightLarge),
            colors = ButtonDefaults.buttonColors(
                containerColor = when (scanMode) {
                    "sticker" -> WarningOrange
                    "barcode" -> InfoBlue
                    else -> PrimaryGreen
                }
            )
        ) {
            Text(
                when (scanMode) {
                    "barcode" -> "✅ Подтвердить штрихкод"
                    "sgtin" -> "✅ Подтвердить КИЗ"
                    "sticker" -> "✅ Подтвердить стикер"
                    else -> "✅ Подтвердить"
                },
                fontSize = TextSizeLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = when (scanMode) {
                "barcode" -> "💡 Отсканируйте штрихкод товара"
                "sgtin" -> "💡 Отсканируйте Data Matrix с маркировки"
                "sticker" -> "💡 Отсканируйте QR-код на стикере WB"
                else -> ""
            },
            fontSize = TextSizeSmall,
            color = OnDarkSecondary
        )
    }
}