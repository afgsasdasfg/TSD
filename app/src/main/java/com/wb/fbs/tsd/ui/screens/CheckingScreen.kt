package com.wb.fbs.tsd.ui.screens
import android.view.ViewGroup
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.Preview
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.unit.sp

/**
 * Экран 3: Проверка (чекинг) — сканирование штрихкодов камерой/сканером
 *
 * Обновление: onBarcodeScanned теперь реально увеличивает scannedQuantity.
 * Камера запускается и сканирует 1D/2D баркоды автоматически.
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
                        fontSize = 48.sp,
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

                // Размер — отдельно и крупно: складчик при проверке ориентируется
                // по размеру, а не по штрихкоду.
                if (item.size.isNotBlank()) {
                    Text(
                        text = "Размер: ${item.size}",
                        fontSize = TextSizeLarge,
                        fontWeight = FontWeight.Bold,
                        color = WarningOrange
                    )
                }

                Text(
                    text = "Арт: ${item.article} | ${item.color}",
                    fontSize = TextSizeSmall,
                    color = OnDarkSecondary
                )

                Text(
                    text = "Штрихкод: ${item.barcode}",
                    fontSize = TextSizeSmall,
                    color = OnDarkDisabled
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "○",  // обычный кружок Unicode
                    fontSize = TextSizeLarge,
                    fontWeight = FontWeight.Bold,
                    color = OnDarkSecondary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = item.progressText,
                    fontSize = TextSizeLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isFullyChecked) PrimaryGreen else WarningOrange
                )
            }
        }
    }
}

@Composable
fun BarcodeScannerView(onBarcodeScanned: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(Unit) {
        val previewView = PreviewView(context)

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val imageAnalysis = ImageAnalysis.Builder().build().also { analysis ->
                    analysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                        @OptIn(ExperimentalGetImage::class)
                        processImageProxy(imageProxy, onBarcodeScanned)
                    }
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))

        previewView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        previewView.setPadding(0, 0, 0, 0)

        if (modifier is Modifier.Element) {
            // Применяем модифайер напрямую к представлению
        }

        // Добавляем view в root (если нужно)
        // Обычно AndroidView сам рисует view, но можно добавить вручную

        onDispose {
            cameraProviderFuture.get()?.unbindAll()
        }
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).also {
                it.scaleType = PreviewView.ScaleType.FIT_CENTER
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black)
    )
}


@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun processImageProxy(imageProxy: ImageProxy, onBarcodeScanned: (String) -> Unit) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }

    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

    val barcodeScanner = BarcodeScanning.getClient()
    barcodeScanner.process(image)
        .addOnSuccessListener { barcodes ->
            for (barcode in barcodes) {
                val value = barcode.rawValue
                if (!value.isNullOrBlank()) {
                    onBarcodeScanned(value)
                    break
                }
            }
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}
