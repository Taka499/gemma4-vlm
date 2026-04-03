Clear Android logcat to prepare for clean error capture.

1. Run `adb logcat -c`
2. Tell the user: "Logcat cleared. Reproduce the error in the app, then run /logcat to capture it."
