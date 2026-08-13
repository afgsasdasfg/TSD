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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.wb.fbs.tsd.ui.theme.*
import com.wb.fbs.tsd.utils.KizParser
import java.util.concurrent.Executors

@Composable
fun KizScannerScreen(
    onBackClick: () -> Unit,
    onKizScanned: (String) -> Unit  // Полная строка КИЗ
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
                                onKizScanned(value)
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
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
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

        // Результат
        scannedKiz?.let { kiz ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = PrimaryGreen.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "✅ КИЗ отсканирован",
                        color = PrimaryGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = TextSizeLarge
                    )
                    Text(
                        "GTIN: ${KizParser.extractGtin(kiz) ?: "—"}",
                        color = OnDarkPrimary,
                        fontSize = TextSizeMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            scannedKiz = null
                            isScanning = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = InfoBlue)
                    ) {
                        Text("📷 Сканировать ещё")
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