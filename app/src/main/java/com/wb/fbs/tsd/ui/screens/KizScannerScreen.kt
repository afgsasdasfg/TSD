package com.wb.fbs.tsd.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.wb.fbs.tsd.ui.theme.*
import com.wb.fbs.tsd.utils.KizParser
import com.wb.fbs.tsd.utils.KizValidationResult
import com.wb.fbs.tsd.utils.ScanFeedback
import com.wb.fbs.tsd.utils.clipboardScanner
import java.util.concurrent.Executors

@Composable
fun KizScannerScreen(
    onBackClick: () -> Unit,
    onKizScanned: (String) -> Unit,
    orderBarcode: String? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    var scannedKiz by remember { mutableStateOf<String?>(null) }
    var isScanning by remember { mutableStateOf(true) }

    // ТСД-сканер копирует КИЗ в буфер — подхватываем
    clipboardScanner { code: String ->
        if (isScanning && scannedKiz == null && KizParser.isValidKizFormat(code)) {
            scannedKiz = code
            isScanning = false
            ScanFeedback.success(context)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Заголовок
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Default.ArrowBack, "Назад", tint = OnDarkPrimary)
            }
            Text(
                "🏷️ Сканирование КИЗ",
                fontSize = TextSizeExtraLarge,
                fontWeight = FontWeight.Bold,
                color = OnDarkPrimary
            )
        }

        // Камера
        if (hasCameraPermission) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                CameraPreview(
                    onBarcodeDetected = { barcode ->
                        if (isScanning && scannedKiz == null) {
                            val value = barcode.rawValue
                            if (!value.isNullOrBlank() && KizParser.isValidKizFormat(value)) {
                                scannedKiz = value
                                isScanning = false
                                ScanFeedback.success(context)
                            }
                        }
                    }
                )

                // Оверлей с подсказкой
                if (isScanning) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Наведите на Data Matrix\n(КИЗ Честный ЗНАК)",
                            color = Color.White,
                            fontSize = TextSizeLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "⚠️ Нужно разрешение на камеру",
                    color = ErrorRed,
                    fontSize = TextSizeLarge
                )
            }
        }

        // Результат валидации
        scannedKiz?.let { kiz ->
            val validation = remember(kiz) {
                KizParser.validateFull(kiz, orderBarcode)
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (validation) {
                        is KizValidationResult.Valid -> PrimaryGreen.copy(alpha = 0.2f)
                        is KizValidationResult.Invalid -> ErrorRed.copy(alpha = 0.2f)
                    }
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    when (validation) {
                        is KizValidationResult.Valid -> {
                            Text(
                                "✅ КИЗ ВАЛИДЕН",
                                color = PrimaryGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = TextSizeLarge
                            )
                            Text(
                                "GTIN: ${validation.gtin}",
                                color = OnDarkPrimary,
                                fontSize = TextSizeMedium
                            )
                            Text(
                                "Серия: ${validation.serial}",
                                color = OnDarkSecondary,
                                fontSize = TextSizeSmall
                            )
                            if (orderBarcode != null) {
                                Text(
                                    "✅ Совпадает с товаром",
                                    color = PrimaryGreen,
                                    fontSize = TextSizeMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        is KizValidationResult.Invalid -> {
                            Text(
                                "❌ КИЗ ОТКЛОНЁН",
                                color = ErrorRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = TextSizeLarge
                            )
                            Text(
                                validation.reason,
                                color = ErrorRed,
                                fontSize = TextSizeMedium
                            )
                            Text(
                                "⚠️ Не упаковывайте этот товар!",
                                color = WarningOrange,
                                fontSize = TextSizeMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (validation is KizValidationResult.Valid) {
                                onKizScanned(kiz)
                            }
                            scannedKiz = null
                            isScanning = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (validation is KizValidationResult.Valid) PrimaryGreen else InfoBlue
                        )
                    ) {
                        Text(
                            if (validation is KizValidationResult.Valid) "✅ Подтвердить и сохранить"
                            else "📷 Сканировать другой КИЗ",
                            fontSize = TextSizeLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(onBarcodeDetected: (Barcode) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember { BarcodeScanning.getClient() }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    processImageProxy(imageProxy, barcodeScanner, onBarcodeDetected)
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
                    Log.e("Camera", "Ошибка привязки камеры", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

@OptIn(ExperimentalGetImage::class)
private fun processImageProxy(
    imageProxy: ImageProxy,
    barcodeScanner: BarcodeScanner,
    onBarcodeDetected: (Barcode) -> Unit
) {
    val mediaImage = imageProxy.image ?: run {
        imageProxy.close()
        return
    }

    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

    barcodeScanner.process(image)
        .addOnSuccessListener { barcodes ->
            for (barcode in barcodes) {
                val value = barcode.rawValue
                if (!value.isNullOrBlank()) {
                    // Проверяем, что это КИЗ (Data Matrix с GTIN)
                    if (KizParser.isValidKizFormat(value)) {
                        onBarcodeDetected(barcode)
                        break
                    }
                }
            }
        }
        .addOnFailureListener {
            Log.e("Barcode", "Ошибка сканирования", it)
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}