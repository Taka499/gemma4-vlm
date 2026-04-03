-keepclassmembers class com.google.ai.edge.litertlm.** { *; }
-keep class com.google.ai.edge.litertlm.** { *; }

# Strip verbose/debug logs in release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
