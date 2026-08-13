package com.wb.fbs.tsd.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.wb.fbs.tsd.data.db.OrderEntity
import com.wb.fbs.tsd.ui.theme.*

@Composable
fun KizInputDialog(
    order: OrderEntity,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var kizInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "🏷️ Ввод КИЗ",
                    fontSize = TextSizeExtraLarge,
                    fontWeight = FontWeight.Bold,
                    color = OnDarkPrimary
                )

                // Инфо о товаре
                Text(
                    "Артикул: ${order.article}",
                    fontSize = TextSizeMedium,
                    color = OnDarkPrimary
                )
                Text(
                    "Штрихкод: ${order.barcode ?: "—"}",
                    fontSize = TextSizeSmall,
                    color = OnDarkSecondary
                )

                // Поле ввода
                OutlinedTextField(
                    value = kizInput,
                    onValueChange = {
                        kizInput = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Код КИЗ (Data Matrix)", color = OnDarkSecondary) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it, color = ErrorRed) } },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryGreen,
                        unfocusedBorderColor = OnDarkDisabled,
                        focusedTextColor = OnDarkPrimary,
                        unfocusedTextColor = OnDarkPrimary,
                        errorBorderColor = ErrorRed
                    )
                )

                // Кнопки
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = OnDarkSecondary
                        )
                    ) {
                        Text("Отмена")
                    }

                    Button(
                        onClick = {
                            if (kizInput.isBlank()) {
                                error = "Введите код КИЗ"
                            } else {
                                onConfirm(kizInput.trim())
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                    ) {
                        Text("✅ Сохранить", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}