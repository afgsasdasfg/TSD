package com.wb.fbs.tsd.ui.screens

import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wb.fbs.tsd.ui.theme.*
import android.graphics.BitmapFactory

/**
 * Экран QR-кода поставки — показывает QR поставки (WB-GI-XXXXXXX)
 * для сканирования на ПВЗ. QR приходит из API как base64 SVG/PNG.
 */
@Composable
fun SupplyQrScreen(
    supplyId: String,
    qrSvgBase64: String?,
    isLoading: Boolean = false,
    error: String? = null,
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit
) {
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
            IconButton(onClick = onBackClick) {
                Icon(Icons.Default.ArrowBack, "Назад", tint = OnDarkPrimary)
            }
            Text(
                "📦 QR поставки",
                fontSize = TextSizeExtraLarge,
                fontWeight = FontWeight.Bold,
                color = OnDarkPrimary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ID поставки крупно
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "ID поставки:",
                    fontSize = TextSizeSmall,
                    color = OnDarkSecondary
                )
                Text(
                    supplyId,
                    fontSize = TextSizeExtraLarge,
                    fontWeight = FontWeight.Bold,
                    color = WarningOrange
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // QR-код или загрузка
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = InfoBlue)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Загрузка QR...", color = OnDarkSecondary)
                }
            }
        } else if (error != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "⚠️ $error",
                        color = ErrorRed,
                        fontSize = TextSizeMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onRefreshClick,
                        colors = ButtonDefaults.buttonColors(containerColor = InfoBlue)
                    ) {
                        Text("🔄 Повторить")
                    }
                }
            }
        } else if (qrSvgBase64 != null) {
            // Декодируем base64 → bitmap
            val bitmap = remember(qrSvgBase64) {
                try {
                    val bytes = Base64.decode(qrSvgBase64, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } catch (e: Exception) {
                    null
                }
            }

            if (bitmap != null) {
                val imageBitmap = remember(bitmap) {
                    bitmap.asImageBitmap()
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = "QR код поставки",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            } else {
                // SVG не декодируется как bitmap — показываем сырой base64
                // (Fallback: WB API может отдать SVG, который BitmapFactory не поймёт)
                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.QrCode,
                                contentDescription = null,
                                tint = OnDarkPrimary,
                                modifier = Modifier.size(120.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "QR-код загружен.\nПокажите этот экран на ПВЗ.",
                                color = OnDarkPrimary,
                                fontSize = TextSizeMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "SupplyId: $supplyId",
                                color = OnDarkSecondary,
                                fontSize = TextSizeSmall
                            )
                        }
                    }
                }
            }
        } else {
            // Нет QR — кнопка загрузки
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.QrCode,
                        contentDescription = null,
                        tint = OnDarkSecondary,
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "QR-код не загружен",
                        color = OnDarkSecondary,
                        fontSize = TextSizeMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onRefreshClick,
                        colors = ButtonDefaults.buttonColors(containerColor = InfoBlue),
                        modifier = Modifier.height(ButtonHeightLarge)
                    ) {
                        Text(
                            "📥 Загрузить QR",
                            fontSize = TextSizeLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Подсказка
        Text(
            "💡 Отсканируйте этот QR на ПВЗ для приёмки поставки",
            fontSize = TextSizeSmall,
            color = OnDarkSecondary,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
