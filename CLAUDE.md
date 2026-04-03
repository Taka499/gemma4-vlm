# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

This is a single-module Android app (Kotlin + Jetpack Compose). Open in Android Studio for the easiest workflow — it handles Gradle wrapper, SDK, and device management.

```bash
# Build debug APK
./gradlew assembleDebug

# Install directly to connected device
./gradlew installDebug

# Launch on device
adb shell am start -n com.gemma4vlm.camera/.MainActivity
```

No tests exist yet. No linter is configured.

## Model Setup

The app requires a Gemma 4 E2B-it model file (`.litertlm` format, ~2.58 GB) on the device at runtime. It is **not** bundled in the APK.

```bash
hf download litert-community/gemma-4-E2B-it-litert-lm \
  gemma-4-E2B-it.litertlm --local-dir ./gemma4-model
adb push ./gemma4-model/gemma-4-E2B-it.litertlm /data/local/tmp/
```

Default model path in the app: `/data/local/tmp/gemma-4-E2B-it.litertlm`

## Architecture

Two-screen app with a shared inference engine:

```
MainActivity
  ├─ Permission gate (Accompanist)
  └─ MainFlow
      ├─ ModelSetupScreen → user provides model path → engine.initialize()
      └─ CameraScreen (after model loads)
          ├─ CameraX preview + ImageAnalysis (continuous frame capture)
          ├─ Periodic inference loop (LaunchedEffect with configurable delay)
          └─ Streaming description overlay
```

**GemmaInferenceEngine** (`inference/GemmaInferenceEngine.kt`) is the core:
- Wraps LiteRT-LM `Engine` + `Conversation` with a persistent vision-oriented system prompt
- GPU backend by default, automatic CPU fallback on init failure
- Mutex-serialized inference — only one frame processed at a time
- Images downscaled to 512px max, JPEG-compressed before passing to the model
- Exposes both blocking (`describeImage`) and streaming (`describeImageStreaming`) flows

**Camera → VLM pipeline**: CameraX `ImageAnalysis` writes the latest frame to a `Bitmap` variable. A `LaunchedEffect` coroutine wakes every N seconds (2/3/5/10s, user-configurable), grabs the latest bitmap, sends it through the engine, and updates the description state which recomposes the overlay card.

## Key Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| LiteRT-LM | 0.10.1 | On-device Gemma 4 inference (GPU/CPU) |
| CameraX | 1.4.1 | Camera preview + frame capture |
| Compose BOM | 2024.12.01 | UI framework (Material 3) |
| Accompanist | 0.36.0 | Runtime permission handling |

## Android Config

- **minSdk 28**, targetSdk/compileSdk 35
- AndroidManifest declares `CAMERA` permission and `libOpenCL.so` / `libvndksupport.so` native libraries (required for LiteRT-LM GPU backend)
- ProGuard keeps all `com.google.ai.edge.litertlm.**` classes
