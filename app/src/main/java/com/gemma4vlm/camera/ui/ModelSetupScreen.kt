package com.gemma4vlm.camera.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gemma4vlm.camera.ui.theme.ErrorRed
import com.gemma4vlm.camera.ui.theme.PrimaryBlue
import com.gemma4vlm.camera.ui.theme.SecondaryGreen

enum class LoadingState {
    Idle, Loading, Success, Error
}

@Composable
fun ModelSetupScreen(
    loadingState: LoadingState,
    errorMessage: String?,
    onLoadModel: (String) -> Unit,
) {
    var modelPath by remember {
        mutableStateOf("/data/local/tmp/gemma-4-E2B-it.litertlm")
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // App icon
            Icon(
                imageVector = Icons.Default.Memory,
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(64.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Gemma 4 VLM",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Text(
                text = "Real-time camera vision assistant",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Setup instructions card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Setup",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryBlue,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. Install HuggingFace CLI:\n" +
                               "   pip install -U huggingface_hub\n\n" +
                               "2. Download the LiteRT-LM model:\n" +
                               "   hf download litert-community/\n" +
                               "   gemma-4-E2B-it-litert-lm \\\n" +
                               "   gemma-4-E2B-it.litertlm \\\n" +
                               "   --local-dir ./gemma4-model\n\n" +
                               "3. Push to device via ADB:\n" +
                               "   adb push ./gemma4-model/\n" +
                               "   gemma-4-E2B-it.litertlm \\\n" +
                               "   /data/local/tmp/\n\n" +
                               "Source: google/gemma-4-E2B-it (Apache 2.0)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Model path input
            OutlinedTextField(
                value = modelPath,
                onValueChange = { modelPath = it },
                label = { Text("Model file path") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = loadingState != LoadingState.Loading,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Load button
            Button(
                onClick = { onLoadModel(modelPath) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = loadingState != LoadingState.Loading && modelPath.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
            ) {
                when (loadingState) {
                    LoadingState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Loading model...")
                    }
                    else -> Text("Load Model & Start Camera")
                }
            }

            // Loading progress
            AnimatedVisibility(visible = loadingState == LoadingState.Loading) {
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = PrimaryBlue,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Initializing Gemma 4 E2B engine...\nThis may take 10-30 seconds on first load.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                }
            }

            // Success
            AnimatedVisibility(visible = loadingState == LoadingState.Success) {
                Row(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = SecondaryGreen,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Model loaded successfully!",
                        color = SecondaryGreen,
                    )
                }
            }

            // Error
            AnimatedVisibility(visible = loadingState == LoadingState.Error) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = ErrorRed.copy(alpha = 0.1f),
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = ErrorRed,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMessage ?: "Failed to load model",
                            color = ErrorRed,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
