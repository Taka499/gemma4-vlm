package com.gemma4vlm.camera.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gemma4vlm.camera.inference.GemmaInferenceEngine
import com.gemma4vlm.camera.ui.theme.OverlayCard
import com.gemma4vlm.camera.ui.theme.PrimaryBlue
import com.gemma4vlm.camera.ui.theme.SecondaryGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import java.util.concurrent.Executors

@Composable
fun CameraScreen(engine: GemmaInferenceEngine) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var description by remember { mutableStateOf("Point camera at an object to describe it...") }
    var isInferring by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var useFrontCamera by remember { mutableStateOf(false) }
    var captureIntervalSec by remember { mutableIntStateOf(3) }
    var latestBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var inferenceTimeMs by remember { mutableLongStateOf(0L) }
    var frameCount by remember { mutableIntStateOf(0) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // Periodic inference loop
    LaunchedEffect(isPaused, captureIntervalSec) {
        while (true) {
            delay(captureIntervalSec * 1000L)
            if (!isPaused && engine.isReady) {
                val bmp = latestBitmap
                if (bmp != null && !bmp.isRecycled) {
                    isInferring = true
                    val startTime = System.currentTimeMillis()
                    try {
                        engine.describeImageStreaming(bmp)
                            .catch { e ->
                                Log.e("CameraScreen", "Inference error", e)
                                description = "Error: ${e.message}"
                            }
                            .collect { token ->
                                description = token
                            }
                        frameCount++
                    } finally {
                        inferenceTimeMs = System.currentTimeMillis() - startTime
                        isInferring = false
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview
        CameraPreview(
            useFrontCamera = useFrontCamera,
            onFrameCaptured = { bitmap -> latestBitmap = bitmap },
            executor = cameraExecutor,
        )

        // Top status bar
        TopStatusBar(
            isInferring = isInferring,
            isPaused = isPaused,
            inferenceTimeMs = inferenceTimeMs,
            frameCount = frameCount,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding(),
        )

        // Bottom overlay with description + controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Description card
            DescriptionCard(
                description = description,
                isInferring = isInferring,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Controls
            ControlsRow(
                isPaused = isPaused,
                captureIntervalSec = captureIntervalSec,
                onTogglePause = { isPaused = !isPaused },
                onToggleCamera = { useFrontCamera = !useFrontCamera },
                onCycleInterval = {
                    captureIntervalSec = when (captureIntervalSec) {
                        2 -> 3
                        3 -> 5
                        5 -> 10
                        else -> 2
                    }
                },
            )
        }
    }
}

@Composable
private fun CameraPreview(
    useFrontCamera: Boolean,
    onFrameCaptured: (Bitmap) -> Unit,
    executor: java.util.concurrent.ExecutorService,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(useFrontCamera) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            imageAnalysis.setAnalyzer(executor) { imageProxy ->
                val bitmap = imageProxyToBitmap(imageProxy)
                if (bitmap != null) {
                    onFrameCaptured(bitmap)
                }
                imageProxy.close()
            }

            val cameraSelector = if (useFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis,
            )
        }, ContextCompat.getMainExecutor(context))
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun TopStatusBar(
    isInferring: Boolean,
    isPaused: Boolean,
    inferenceTimeMs: Long,
    frameCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Status indicator
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isPaused -> Color.Gray
                            isInferring -> Color.Yellow
                            else -> SecondaryGreen
                        }
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when {
                    isPaused -> "PAUSED"
                    isInferring -> "ANALYZING..."
                    else -> "READY"
                },
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )
        }

        // Stats
        if (inferenceTimeMs > 0) {
            Text(
                text = "${inferenceTimeMs}ms | #$frameCount",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun DescriptionCard(
    description: String,
    isInferring: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = OverlayCard),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Gemma 4 Vision",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryBlue,
                )
                if (isInferring) {
                    Spacer(modifier = Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = PrimaryBlue,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            AnimatedVisibility(
                visible = true,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    lineHeight = 24.sp,
                )
            }
        }
    }
}

@Composable
private fun ControlsRow(
    isPaused: Boolean,
    captureIntervalSec: Int,
    onTogglePause: () -> Unit,
    onToggleCamera: () -> Unit,
    onCycleInterval: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Interval control
        IconButton(onClick = onCycleInterval) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = "Change interval",
                    tint = Color.White,
                )
                Text(
                    text = "${captureIntervalSec}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
        }

        // Pause/Resume
        IconButton(
            onClick = onTogglePause,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (isPaused) SecondaryGreen.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.2f)),
        ) {
            Icon(
                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = if (isPaused) "Resume" else "Pause",
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }

        // Switch camera
        IconButton(onClick = onToggleCamera) {
            Icon(
                imageVector = Icons.Default.Cameraswitch,
                contentDescription = "Switch camera",
                tint = Color.White,
            )
        }
    }
}

private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
    return try {
        val buffer = imageProxy.planes[0].buffer
        val pixelStride = imageProxy.planes[0].pixelStride
        val rowStride = imageProxy.planes[0].rowStride
        val rowPadding = rowStride - pixelStride * imageProxy.width

        val bitmap = Bitmap.createBitmap(
            imageProxy.width + rowPadding / pixelStride,
            imageProxy.height,
            Bitmap.Config.ARGB_8888,
        )
        buffer.rewind()
        bitmap.copyPixelsFromBuffer(buffer)

        // Crop to actual size if there was padding
        if (rowPadding > 0) {
            Bitmap.createBitmap(bitmap, 0, 0, imageProxy.width, imageProxy.height)
        } else {
            bitmap
        }
    } catch (e: Exception) {
        Log.e("CameraScreen", "Failed to convert ImageProxy to Bitmap", e)
        null
    }
}
