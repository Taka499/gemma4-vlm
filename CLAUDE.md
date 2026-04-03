# CLAUDE.md

## Build & Run

Single-module Android app (Kotlin 2.3.0 + Jetpack Compose). Open in Android Studio — it handles Gradle wrapper, SDK, and device management.

```bash
./gradlew assembleDebug        # build
./gradlew installDebug         # install to device
adb shell am start -n com.gemma4vlm.camera/.MainActivity
```

No tests or linter configured.

## Model

Gemma 4 E2B-it (`.litertlm`, ~2.58 GB) must be on the device — not bundled in the APK.

```bash
hf download litert-community/gemma-4-E2B-it-litert-lm \
  gemma-4-E2B-it.litertlm --local-dir ./gemma4-model
adb push ./gemma4-model/gemma-4-E2B-it.litertlm /data/local/tmp/
```

Default path in app: `/data/local/tmp/gemma-4-E2B-it.litertlm`

## Architecture

```
MainActivity
  ├─ Permission gate (Accompanist)
  └─ MainFlow
      ├─ ModelSetupScreen → engine.initialize()
      └─ CameraScreen
          ├─ CameraX preview + ImageAnalysis (frame capture)
          ├─ Periodic inference loop (LaunchedEffect, configurable 2/3/5/10s)
          └─ Streaming description overlay (animateContentSize)
```

**GemmaInferenceEngine** (`inference/GemmaInferenceEngine.kt`):
- Wraps LiteRT-LM `Engine` + `Conversation` with vision system prompt
- GPU by default, auto CPU fallback
- Mutex-serialized — one frame at a time
- Images downscaled to 512px max, JPEG 85%
- `describeImageStreaming()` emits **individual tokens** (not accumulated) — caller must concatenate

**Camera → VLM pipeline** (`ui/CameraScreen.kt`):
- `ImageAnalysis` captures latest frame as `Bitmap`
- `LaunchedEffect` loop grabs bitmap every N seconds, streams through engine
- Tokens accumulated into description string, displayed in overlay card
- Each completed inference appended to `inference_log.txt`

## Debugging

```bash
# Pull inference log (debug builds only, written to app cache dir)
adb shell run-as com.gemma4vlm.camera cat cache/inference_log.txt > inference_log.txt

# Logcat with inference detail (token-level, debug builds only)
adb logcat -s GemmaInference:D CameraScreen:D
```

`adb` path: `~/Library/Android/sdk/platform-tools/adb`

## Gotchas

- LiteRT-LM 0.10.0 requires Kotlin 2.3.0+ (compiled against it)
- Kotlin 2.3.0 removed `kotlinOptions` DSL — use `kotlin { compilerOptions { } }`
- `Message` has no `.text` — use `.toString()` which chains `Contents.toString()` → `Content.Text.text`
- `sendMessageAsync` emits individual tokens per `Message`, not accumulated text
- `SamplerConfig` params (`topP`, `temperature`) are `Double`, not `Float`
- Use `context.cacheDir` for LiteRT-LM cache (scoped storage)
- AndroidManifest requires `libOpenCL.so` + `libvndksupport.so` for GPU backend
- ProGuard must keep `com.google.ai.edge.litertlm.**`
