package com.wb.fbs.tsd.ui.screens

import androidx.camera.core.Preview
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.wb.fbs.tsd.data.model.Order
import com.wb.fbs.tsd.data.model.OrderItem
import com.wb.fbs.tsd.ui.theme.*

/**
 * Экран 3: Проверка (чекинг) — сканирование штрихкодов камерой/сканером
 */
@Composable
fun CheckingScreen(
    order: Order,
    onBackClick: () -> Unit,
    onBarcodeScanned: (String) -> Unit,
    onNextClick: () -> Unit
) {
    val totalItems = order.items.sumOf { it.quantity }
    val checkedItems = order.items.sumOf { it.scannedQuantity }
    val progress = if (totalItems > 0) checkedItems.toFloat() / totalItems else 0f
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
                text = "🔍 Проверка: ${order.clientName}",
                fontSize = TextSizeExtraLarge,
                fontWeight = FontWeight.Bold,
                color = OnDarkPrimary
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Прогресс сканирования КРУПНЫМ шрифтом
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
                        text = "$checkedItems / $totalItems",
                        fontSize = 48.sp, // ОЧЕНЬ крупно
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isComplete) PrimaryGreen else InfoBlue
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp),
                        color = if (isComplete) PrimaryGreen else InfoBlue,
                        trackColor = DarkSurface
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = if (isComplete) "✅ ГОТОВО" else "Сканируйте штрихкоды",
                        fontSize = TextSizeLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isComplete) PrimaryGreen else OnDarkSecondary
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Список товаров со статусами
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(order.items, key = { it.id }) { item ->
                CheckingItemCard(item = item)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Камера для сканирования (занимает нижнюю часть)
        BarcodeScannerView(
            onBarcodeScanned = onBarcodeScanned,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Кнопка перехода к КИЗ
        Button(
            onClick = onNextClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(ButtonHeightLarge),
            enabled = isComplete,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isComplete) PrimaryGreen else OnDarkDisabled
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = "🏷️ К маркировке (КИЗ)",
                fontSize = TextSizeLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun CheckingItemCard(item: OrderItem) {
    val isFullyChecked = item.isFullyScanned
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isFullyChecked) DarkSurfaceVariant else DarkSurface
        ),
        border = if (!isFullyChecked) androidx.compose.foundation.BorderStroke(2.dp, WarningOrange) else null
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
                    fontSize = TextSizeMedium,
                    fontWeight = FontWeight.Bold,
                    color = OnDarkPrimary
                )
                
                Text(
                    text = "${item.article} | ${item.size}",
                    fontSize = TextSizeSmall,
                    color = OnDarkSecondary
                )
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isFullyChecked) Icons.Default.CheckCircle else Icons.Default.Circle,
                    contentDescription = null,
                    tint = if (isFullyChecked) PrimaryGreen else WarningOrange,
                    modifier = Modifier.size(IconSizeMedium)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = item.progressText,
                    fontSize = TextSizeLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isFullyChecked) PrimaryGreen else OnDarkPrimary
                )
            }
        }
    }
}

@Composable
private fun BarcodeScannerView(
    onBarcodeScanned: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(this.surfaceProvider)
                        }
                        
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also {
                                it.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                                    processImageProxy(imageProxy, onBarcodeScanned)
                                }
                            }
                        
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Подсказка поверх камеры
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "📷 Наведите на штрихкод",
                fontSize = TextSizeMedium,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun processImageProxy(
    imageProxy: ImageProxy,
    onBarcodeScanned: (String) -> Unit
) {
    val mediaImage = imageProxy.image ?: return
    
    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    val scanner = BarcodeScanning.getClient()
    
    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            for (barcode in barcodes) {
                barcode.rawValue?.let { value ->
                    onBarcodeScanned(value)
                }
            }
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}
