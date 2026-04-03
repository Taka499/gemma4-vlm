# Gemma 4 VLM Camera

Real-time camera descriptions powered by [Gemma 4](https://ai.google.dev/gemma) running on-device via [LiteRT-LM](https://github.com/nicholasgasior/litert-lm). Point your phone at anything and get a streaming text description. Supports both **E2B-it** (2.3B, lighter) and **E4B-it** (4.5B, higher quality) model variants.

> **Note:** This project was vibe-coded with [Claude Code](https://claude.ai/code).

## Prerequisites

- Android device with **API 28+** (Android 9) and GPU support (tested on Samsung S25)
- Android Studio (handles Gradle wrapper, SDK, and device setup)
- [Hugging Face CLI](https://huggingface.co/docs/huggingface_hub/guides/cli)
- ~2.6 GB (E2B) or ~3.7 GB (E4B) free on device for the model file

## Setup

**1. Download a model** (pick one)

```bash
# E2B — smaller, lower RAM (~2.6 GB)
hf download litert-community/gemma-4-E2B-it-litert-lm \
  gemma-4-E2B-it.litertlm --local-dir ./gemma4-model

# E4B — larger, higher quality (~3.7 GB)
hf download litert-community/gemma-4-E4B-it-litert-lm \
  gemma-4-E4B-it.litertlm --local-dir ./gemma4-model
```

**2. Push to device**

```bash
# Push whichever model you downloaded
adb push ./gemma4-model/gemma-4-E2B-it.litertlm /data/local/tmp/
# or
adb push ./gemma4-model/gemma-4-E4B-it.litertlm /data/local/tmp/
```

**3. Build & install**

Open in Android Studio and run, or:

```bash
./gradlew installDebug
adb shell am start -n com.gemma4vlm.camera/.MainActivity
```

**4. In the app** — select your model variant (E2B or E4B), then tap "Load Model". Once loaded, the camera view streams descriptions automatically.

## How it works

```
Camera frame (CameraX) → JPEG compress + downscale (512px max)
  → LiteRT-LM Gemma 4 inference (GPU, CPU fallback)
  → Streaming token-by-token description overlay
```

Inference runs every N seconds (2/3/5/10s, tap the speed icon to cycle). You can pause/resume and switch front/back camera.

## Debugging

Debug builds write inference output to a log file (capped at 512 KB):

```bash
adb shell run-as com.gemma4vlm.camera cat cache/inference_log.txt > inference_log.txt
```

## Tech stack

| Component | Detail |
|-----------|--------|
| Language | Kotlin 2.3.0 |
| UI | Jetpack Compose (Material 3) |
| Camera | CameraX 1.4.1 |
| Inference | LiteRT-LM 0.10.0 |
| Min API | 28 (Android 9) |
