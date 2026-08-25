package com.wb.fbs.tsd.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wb.fbs.tsd.data.model.Product
import com.wb.fbs.tsd.ui.theme.*
import com.wb.fbs.tsd.utils.clipboardScanner

@Composable
fun ReceivingScreen(
    products: List<Product>,
    onBackClick: () -> Unit,
    onProductAdded: (Product) -> Unit,
    onDeleteProduct: (String) -> Unit,
    onAddKizToProduct: (productId: String, kizCode: String) -> Unit
) {
    var scanMode by remember { mutableStateOf<String?>(null) }
    var barcodeInput by remember { mutableStateOf("") }
    var kizInput by remember { mutableStateOf("") }
    var selectedProductId by remember { mutableStateOf<String?>(null) }

    val selectedProduct = selectedProductId?.let { pid -> products.find { it.id == pid } }

    // ТСД-сканер копирует код в буфер — подхватываем автоматически
    clipboardScanner { code: String ->
        when (scanMode) {
            "barcode" -> {
                if (code.isNotBlank()) {
                    onProductAdded(Product(
                        id = "PRD-${System.currentTimeMillis()}",
                        article = "", name = "Товар #${products.size + 1}",
                        color = "Не указан", size = "Универсальный",
                        barcode = code, requiresMarking = true
                    ))
                }
            }
            "kiz" -> {
                if (code.isNotBlank() && code.startsWith("4B")) {
                    if (selectedProductId != null) {
                        onAddKizToProduct(selectedProductId!!, code)
                    } else {
                        onProductAdded(Product(
                            id = "PRD-${System.currentTimeMillis()}",
                            article = "", name = "Товар #${products.size + 1}",
                            color = "Не указан", size = "Универсальный",
                            barcode = code.substring(0 until minOf(27, code.length)),
                            requiresMarking = true, kizCodes = listOf(code)
                        ))
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(DarkBackground).padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackClick, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.ArrowBack, "Назад", tint = OnDarkPrimary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("📦 Приёмка товаров", fontSize = TextSizeExtraLarge, fontWeight = FontWeight.Bold, color = OnDarkPrimary)
            Spacer(modifier = Modifier.weight(1f))
            Text("${products.size} шт на складе", fontSize = TextSizeMedium, color = InfoBlue)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)) {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                OutlinedButton(
                    onClick = { scanMode = "barcode"; kizInput = "" },
                    modifier = Modifier.weight(1f),
                    enabled = scanMode != "barcode",
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (scanMode == "barcode") InfoBlue else DarkSurface,
                        disabledContainerColor = OnDarkDisabled
                    )
                ) { Text("📋 Штрихкод", fontSize = TextSizeMedium) }

                OutlinedButton(
                    onClick = { scanMode = "kiz"; barcodeInput = "" },
                    modifier = Modifier.weight(1f),
                    enabled = scanMode != "kiz",
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (scanMode == "kiz") PrimaryGreen else DarkSurface,
                        disabledContainerColor = OnDarkDisabled
                    )
                ) { Text("🏷 КИЗ", fontSize = TextSizeMedium) }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (scanMode) {
            "barcode" -> {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("📋 WB-штрихкод", fontSize = TextSizeMedium, fontWeight = FontWeight.Bold, color = OnDarkPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        BasicTextField(
                            value = barcodeInput,
                            onValueChange = { barcodeInput = it },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = TextSizeLarge, color = OnDarkPrimary)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (barcodeInput.isNotBlank()) {
                                    onProductAdded(Product(
                                        id = "PRD-${System.currentTimeMillis()}",
                                        article = "", name = "Товар #${products.size + 1}",
                                        color = "Не указан", size = "Универсальный",
                                        barcode = barcodeInput, requiresMarking = true
                                    ))
                                    barcodeInput = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            enabled = barcodeInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = InfoBlue)
                        ) { Text("➕ Добавить товар", fontSize = TextSizeMedium) }
                    }
                }
            }
            "kiz" -> {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("🏷 Data Matrix (КИЗ)", fontSize = TextSizeMedium, fontWeight = FontWeight.Bold, color = OnDarkPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (selectedProduct != null) {
                            Surface(
                                modifier = Modifier.fillMaxWidth().border(1.dp, PrimaryGreen),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Text(
                                    text = "✅ Выбран: ${selectedProduct.name}",
                                    fontSize = TextSizeSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryGreen,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        } else {
                            Text("💡 Нажми ⭐ на товаре для привязки КИЗа", fontSize = TextSizeSmall, color = WarningOrange)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Формат: 4B + 27 символов (~29 всего)", fontSize = TextSizeSmall, color = OnDarkSecondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        BasicTextField(
                            value = kizInput,
                            onValueChange = { kizInput = it },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = TextSizeLarge, color = OnDarkPrimary)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (kizInput.isNotBlank() && kizInput.startsWith("4B")) {
                                    if (selectedProductId != null) {
                                        onAddKizToProduct(selectedProductId!!, kizInput)
                                    } else {
                                        onProductAdded(Product(
                                            id = "PRD-${System.currentTimeMillis()}",
                                            article = "", name = "Товар #${products.size + 1}",
                                            color = "Не указан", size = "Универсальный",
                                            barcode = kizInput.substring(0 until minOf(27, kizInput.length)),
                                            requiresMarking = true, kizCodes = listOf(kizInput)
                                        ))
                                    }
                                    kizInput = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            enabled = kizInput.isNotBlank() && kizInput.startsWith("4B"),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                        ) {
                            val label = if (selectedProductId != null) "✅ Добавить КИЗ к товару" else "✅ Зарегистрировать КИЗ"
                            Text(label, fontSize = TextSizeMedium)
                        }
                    }
                }
            }
            else -> {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Выберите режим ввода сверху", fontSize = TextSizeMedium, color = OnDarkSecondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("• Штрихкод — приём товара\n• КИЗ — регистрация кодов маркировки", fontSize = TextSizeSmall, color = OnDarkDisabled)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Принято на склад:", fontSize = TextSizeLarge, fontWeight = FontWeight.Bold, color = OnDarkPrimary)

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(products, key = { it.id }) { product ->
                ProductItemCard(
                    product = product,
                    isSelected = selectedProductId == product.id,
                    onDelete = { onDeleteProduct(product.id) },
                    onStarClick = {
                        if (selectedProductId == product.id) {
                            selectedProductId = null
                        } else {
                            selectedProductId = product.id
                            scanMode = "kiz"
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ProductItemCard(
    product: Product,
    isSelected: Boolean,
    onDelete: () -> Unit,
    onStarClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().then(if (isSelected) Modifier.border(2.dp, PrimaryGreen) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = if (product.requiresMarking && !product.isFullyMarked) DarkSurfaceVariant else DarkSurface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(product.name, fontSize = TextSizeMedium, fontWeight = FontWeight.Bold, color = OnDarkPrimary)
                    // Размер — отдельно и крупно, по штрихкоду никто не ориентируется
                    if (product.size.isNotBlank() && product.size != "Универсальный") {
                        Text("Размер: ${product.size}", fontSize = TextSizeLarge, fontWeight = FontWeight.Bold, color = WarningOrange)
                    }
                    Text("Арт: ${product.article} | ${product.color}", fontSize = TextSizeSmall, color = OnDarkSecondary)
                    Text("Штрихкод: ${product.barcode}", fontSize = TextSizeSmall, color = OnDarkDisabled)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (product.requiresMarking) {
                        IconButton(onClick = onStarClick, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Default.Star,
                                "Выбрать для КИЗа",
                                tint = if (isSelected) PrimaryGreen else OnDarkDisabled,
                                modifier = Modifier.size(IconSizeMedium)
                            )
                        }
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, "Удалить", tint = OnDarkDisabled, modifier = Modifier.size(IconSizeMedium))
                    }
                }
            }
            if (product.requiresMarking) {
                Spacer(modifier = Modifier.height(4.dp))
                if (product.isFullyMarked) {
                    Text("✅ КИЗы: ${product.kizCodes.size}", fontSize = TextSizeSmall, color = PrimaryGreen)
                } else {
                    Text("⚠️ КИЗы: ${product.kizCodes.size} / требуется ввод", fontSize = TextSizeSmall, color = WarningOrange)
                }
                if (product.kizCodes.isNotEmpty()) {
                    Text(product.kizCodes.take(2).joinToString(", "), fontSize = TextSizeSmall, color = OnDarkDisabled)
                }
            }
        }
    }
}