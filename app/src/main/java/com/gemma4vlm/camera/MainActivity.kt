package com.gemma4vlm.camera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gemma4vlm.camera.inference.GemmaInferenceEngine
import com.gemma4vlm.camera.ui.CameraScreen
import com.gemma4vlm.camera.ui.LoadingState
import com.gemma4vlm.camera.ui.ModelSetupScreen
import com.gemma4vlm.camera.ui.theme.Gemma4VlmTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Gemma4VlmTheme {
                AppContent()
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun AppContent() {
    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)

    if (cameraPermission.status.isGranted) {
        MainFlow()
    } else {
        CameraPermissionRequest(
            shouldShowRationale = cameraPermission.status.shouldShowRationale,
            onRequestPermission = { cameraPermission.launchPermissionRequest() },
        )
    }
}

@Composable
private fun MainFlow() {
    val scope = rememberCoroutineScope()
    val engine = remember { GemmaInferenceEngine() }

    var loadingState by remember { mutableStateOf(LoadingState.Idle) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var modelLoaded by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { engine.close() }
    }

    if (modelLoaded) {
        CameraScreen(engine = engine)
    } else {
        ModelSetupScreen(
            loadingState = loadingState,
            errorMessage = errorMessage,
            onLoadModel = { path ->
                scope.launch {
                    loadingState = LoadingState.Loading
                    errorMessage = null

                    val cacheDir = android.os.Environment.getExternalStorageDirectory()
                        ?.resolve("Android/data/com.gemma4vlm.camera/cache")
                        ?.also { it.mkdirs() }
                        ?.absolutePath
                        ?: "/data/local/tmp/cache"

                    val result = engine.initialize(
                        modelPath = path,
                        cacheDir = cacheDir,
                    )

                    if (result.success) {
                        loadingState = LoadingState.Success
                        modelLoaded = true
                    } else {
                        loadingState = LoadingState.Error
                        errorMessage = result.error
                    }
                }
            },
        )
    }
}

@Composable
private fun CameraPermissionRequest(
    shouldShowRationale: Boolean,
    onRequestPermission: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp),
            ) {
                Text(
                    text = if (shouldShowRationale) {
                        "Camera access is required for real-time object description."
                    } else {
                        "This app needs camera permission to analyze what you see."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onRequestPermission) {
                    Text("Grant Camera Permission")
                }
            }
        }
    }
}
