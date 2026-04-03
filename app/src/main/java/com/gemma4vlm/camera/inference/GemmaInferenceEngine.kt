package com.gemma4vlm.camera.inference

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Wraps LiteRT-LM Engine for Gemma 4 E2B vision inference.
 *
 * Usage:
 *   1. Call [initialize] with the model path on a background thread.
 *   2. Call [describeImage] with a camera Bitmap to get a streaming description.
 *   3. Call [close] when done.
 */
class GemmaInferenceEngine {

    companion object {
        private const val TAG = "GemmaInference"
        private const val JPEG_QUALITY = 85
        private const val IMAGE_MAX_DIM = 512
    }

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private val inferenceMutex = Mutex()
    private val initialized = AtomicBoolean(false)

    val isReady: Boolean get() = initialized.get()

    data class InitResult(val success: Boolean, val error: String? = null)

    /**
     * Load the Gemma 4 E2B model. Call from a coroutine on Dispatchers.IO.
     * [cacheDir] speeds up subsequent loads.
     */
    suspend fun initialize(
        modelPath: String,
        cacheDir: String,
        useGpu: Boolean = true,
    ): InitResult = withContext(Dispatchers.IO) {
        try {
            val backend = if (useGpu) Backend.GPU() else Backend.CPU()

            val config = EngineConfig(
                modelPath = modelPath,
                backend = backend,
                visionBackend = Backend.GPU(),
                cacheDir = cacheDir,
            )

            val eng = Engine(config)
            eng.initialize()
            engine = eng

            // Create a persistent conversation with a vision-oriented system prompt.
            val convConfig = ConversationConfig(
                systemInstruction = Contents.of(
                    Content.Text(
                        "You are a real-time camera assistant. " +
                        "Describe what you see in the image concisely in 1-2 sentences. " +
                        "Focus on the main objects, people, and actions visible. " +
                        "Be direct and specific."
                    )
                ),
                samplerConfig = SamplerConfig(
                    topK = 20,
                    topP = 0.9,
                    temperature = 0.4,
                ),
            )
            conversation = eng.createConversation(convConfig)

            initialized.set(true)
            Log.i(TAG, "Engine initialized successfully (GPU=$useGpu)")
            InitResult(success = true)
        } catch (e: Exception) {
            Log.e(TAG, "Engine init failed, falling back to CPU", e)
            // Fallback: try CPU if GPU failed
            if (useGpu) {
                return@withContext initialize(modelPath, cacheDir, useGpu = false)
            }
            InitResult(success = false, error = e.message ?: "Unknown error")
        }
    }

    /**
     * Send a camera frame to the VLM and stream back the description token by token.
     * Only one inference runs at a time; concurrent calls wait.
     */
    fun describeImage(bitmap: Bitmap, prompt: String = "What do you see?"): Flow<String> = flow<String> {
        inferenceMutex.withLock {
            val conv = conversation ?: throw IllegalStateException("Engine not initialized")

            val imageBytes = bitmapToJpegBytes(bitmap)

            val response = conv.sendMessage(
                Contents.of(
                    Content.ImageBytes(imageBytes),
                    Content.Text(prompt),
                )
            )
            emit(response.toString())
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Streaming variant — emits partial tokens as they arrive.
     */
    fun describeImageStreaming(bitmap: Bitmap, prompt: String = "What do you see?"): Flow<String> = flow<String> {
        inferenceMutex.withLock {
            val conv = conversation ?: throw IllegalStateException("Engine not initialized")

            val imageBytes = bitmapToJpegBytes(bitmap)
            Log.d(TAG, "Sending image: ${imageBytes.size} bytes, prompt: \"$prompt\"")

            var tokenCount = 0
            conv.sendMessageAsync(
                Contents.of(
                    Content.ImageBytes(imageBytes),
                    Content.Text(prompt),
                )
            ).collect { message ->
                tokenCount++
                val text = message.toString()
                Log.d(TAG, "Token #$tokenCount: \"$text\" (${text.length} chars)")
                emit(text)
            }
            Log.d(TAG, "Streaming complete: $tokenCount tokens")
        }
    }.flowOn(Dispatchers.IO)

    fun close() {
        initialized.set(false)
        runCatching { conversation?.close() }
        runCatching { engine?.close() }
        conversation = null
        engine = null
        Log.i(TAG, "Engine closed")
    }

    /**
     * Downscale + compress a Bitmap to JPEG bytes suitable for the VLM.
     */
    private fun bitmapToJpegBytes(bitmap: Bitmap): ByteArray {
        val scaled = downscale(bitmap, IMAGE_MAX_DIM)
        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        if (scaled !== bitmap) scaled.recycle()
        return stream.toByteArray()
    }

    private fun downscale(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap

        val scale = maxDim.toFloat() / maxOf(w, h)
        val newW = (w * scale).toInt()
        val newH = (h * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }
}
