package com.wb.fbs.tsd.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wb.fbs.tsd.data.db.OrderEntity
import com.wb.fbs.tsd.ui.theme.*
import com.wb.fbs.tsd.ui.viewmodel.OrdersUiState
import com.wb.fbs.tsd.ui.viewmodel.ScanUiResult
import kotlinx.coroutines.delay

@Composable
fun OrderListScreen(
    orders: List<OrderEntity>,
    uiState: OrdersUiState,
    onSyncClick: () -> Unit,
    onOrderClick: (OrderEntity) -> Unit,
    onScanClick: () -> Unit,
    onScanKizClick: () -> Unit,
    onCreateSupplyClick: () -> Unit,
    onSgtinEntered: (Long, String) -> Unit,
    onStickerScanned: (String) -> Unit = {},
    onBarcodeScanned: (String) -> Unit = {},
    onClearScan: () -> Unit = {}
) {
    var showKizDialog by remember { mutableStateOf(false) }
    var selectedOrder by remember { mutableStateOf<OrderEntity?>(null) }
    val context = LocalContext.current

    // === Скрытое TextField для ТСД-сканера (keyboard emulation) ===
    // ТСД-сканер печатает код в focused TextField + Enter.
    // Невидимое поле 1×1px с авто-фокусом — сканер печатает в него,
    // Enter (ImeAction.Done) → onStickerScanned → заказ найдётся.
    var scanInput by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // Авто-фокус при входе
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Восстановление фокуса после Toast/диалога
    LaunchedEffect(uiState.scanResult, showKizDialog) {
        if (uiState.scanResult == null && !showKizDialog) {
            delay(100)
            focusRequester.requestFocus()
        }
    }

    // Toast результата скана
    LaunchedEffect(uiState.scanResult) {
        uiState.scanResult?.let { result ->
            when (result) {
                is ScanUiResult.Success -> {
                    val order = orders.find { it.id == result.orderId }
                    val msg = if (order != null) {
                        "✅ ${order.article} — ${order.name}" + (order.size?.let { " (Размер: $it)" } ?: "")
                    } else {
                        "✅ Артикул ${result.article}"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    onClearScan()
                }
                is ScanUiResult.NotFound -> {
                    Toast.makeText(context, "❌ Товар не найден", Toast.LENGTH_LONG).show()
                    onClearScan()
                }
                is ScanUiResult.Error -> {
                    Toast.makeText(context, "⚠️ ${result.message}", Toast.LENGTH_LONG).show()
                    onClearScan()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(top = 32.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        // ЗАГОЛОВОК
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📋 Заказы FBS",
                fontSize = TextSizeExtraLarge,
                fontWeight = FontWeight.Bold,
                color = OnDarkPrimary
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onSyncClick) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = "Синхронизировать",
                        tint = InfoBlue
                    )
                }
                IconButton(onClick = onScanClick) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Сканировать товар",
                        tint = PrimaryGreen
                    )
                }
                IconButton(onClick = onScanKizClick) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Сканировать КИЗ",
                        tint = WarningOrange
                    )
                }
            }
        }

        Text(
            text = "💡 Сканируйте стикер ТСД — заказ найдётся и отметится автоматически",
            fontSize = TextSizeSmall,
            color = OnDarkSecondary,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        if (uiState.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                color = InfoBlue
            )
        }

        uiState.error?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.2f))
            ) {
                Text(
                    text = "⚠️ $error",
                    color = ErrorRed,
                    fontSize = TextSizeSmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        val syncText = remember(uiState.lastSyncCount, uiState.error) {
            if (uiState.lastSyncCount > 0 && uiState.error == null) "✅ Загружено ${uiState.lastSyncCount} заказов" else null
        }
        syncText?.let {
            Text(
                text = it,
                color = PrimaryGreen,
                fontSize = TextSizeSmall,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        // === Фильтр: confirm сверху, остальные под кнопкой ===
        val confirmOrders = orders.filter { it.status == "confirm" }
        val otherOrders = orders.filter { it.status != "confirm" }
        var showOtherOrders by remember { mutableStateOf(false) }

        Spacer(modifier = Modifier.height(8.dp))

        if (showKizDialog && selectedOrder != null) {
            KizInputDialog(
                order = selectedOrder!!,
                onConfirm = { sgtin ->
                    onSgtinEntered(selectedOrder!!.id, sgtin)
                    showKizDialog = false
                    selectedOrder = null
                },
                onDismiss = {
                    showKizDialog = false
                    selectedOrder = null
                }
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // === Confirm заказы (основные) ===
            if (confirmOrders.isEmpty()) {
                item {
                    Text(
                        text = "Нет заказов на сборке",
                        fontSize = TextSizeMedium,
                        color = OnDarkSecondary,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }
            items(confirmOrders, key = { it.id }) { order ->
                OrderCard(
                    order = order,
                    onClick = {
                        if (order.isMarked && order.sgtin.isNullOrBlank()) {
                            selectedOrder = order
                            showKizDialog = true
                        } else {
                            onOrderClick(order)
                        }
                    }
                )
            }

            // === Остальные заказы (под кнопкой) ===
            if (otherOrders.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { showOtherOrders = !showOtherOrders },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (showOtherOrders) "▼ Скрыть остальные (${otherOrders.size})" else "▶ Показать остальные (${otherOrders.size})",
                            fontSize = TextSizeMedium,
                            color = OnDarkSecondary
                        )
                    }
                }
                if (showOtherOrders) {
                    items(otherOrders, key = { it.id }) { order ->
                        OrderCard(
                            order = order,
                            onClick = {
                                if (order.isMarked && order.sgtin.isNullOrBlank()) {
                                    selectedOrder = order
                                    showKizDialog = true
                                } else {
                                    onOrderClick(order)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // === Скрытое TextField 1×1px — приём скана с ТСД ===
    Box(
        modifier = Modifier
            .size(1.dp)
            .focusRequester(focusRequester),
        contentAlignment = Alignment.Center
    ) {
        TextField(
            value = scanInput,
            onValueChange = { scanInput = it },
            modifier = Modifier.size(1.dp),
            textStyle = TextStyle(fontSize = 1.sp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (scanInput.isNotBlank()) {
                        onStickerScanned(scanInput)
                        scanInput = ""
                    }
                    focusRequester.requestFocus()
                }
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Color.Transparent
            ),
            singleLine = true
        )
    }
}

@Composable
private fun OrderCard(order: OrderEntity, onClick: () -> Unit) {
    val statusColor = when (order.status) {
        "new" -> InfoBlue
        "confirm" -> WarningOrange
        "complete" -> PrimaryGreen
        else -> OnDarkDisabled
    }

    val isScanned = order.scannedAt != null
    val hasSgtin = !order.sgtin.isNullOrBlank()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isScanned) DarkSurfaceVariant else DarkSurface
        ),
        border = if (order.isMarked && !hasSgtin)
            androidx.compose.foundation.BorderStroke(2.dp, WarningOrange)
        else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                        text = order.name,
                        fontSize = TextSizeMedium,
                        color = OnDarkSecondary
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!order.size.isNullOrBlank()) {
                            Text(
                                text = "Размер: ${order.size}",
                                fontSize = TextSizeMedium,
                                fontWeight = FontWeight.Medium,
                                color = OnDarkPrimary
                            )
                        }
                        if (!order.color.isNullOrBlank()) {
                            Text(
                                text = "Цвет: ${order.color}",
                                fontSize = TextSizeSmall,
                                color = OnDarkSecondary
                            )
                        }
                    }
                    Text(
                        text = "Заказ #${order.id}",
                        fontSize = TextSizeSmall,
                        color = OnDarkDisabled
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Box(
                        modifier = Modifier
                            .background(statusColor, shape = MaterialTheme.shapes.small)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = when (order.status) {
                                "new" -> "Новый"
                                "confirm" -> "На сборке"
                                "complete" -> "Готов"
                                else -> order.status
                            },
                            fontSize = TextSizeSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (order.isMarked) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (hasSgtin) "✅ КИЗ" else "⚠️ КИЗ",
                            fontSize = TextSizeSmall,
                            color = if (hasSgtin) PrimaryGreen else WarningOrange
                        )
                    }
                }
            }
            if (isScanned) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "✅ Отсканирован",
                    fontSize = TextSizeSmall,
                    color = PrimaryGreen
                )
            }
        }
    }
}
