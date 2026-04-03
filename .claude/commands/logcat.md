Dump Android logcat errors from the Gemma4 VLM app for debugging.

1. Run `adb logcat -d --pid=$(adb shell pidof com.gemma4vlm.camera 2>/dev/null) '*:E' 2>&1 | tail -100` to get errors from the running app process.
2. If the app is not running (empty PID), fall back to: `adb logcat -d -s AndroidRuntime:E GemmaInference:E System.err:E FATAL:E 2>&1 | tail -100`
3. Read the output, identify the root cause exception/error, and suggest a fix.
4. If the user provides additional context like "$ARGUMENTS", factor that into your analysis.
