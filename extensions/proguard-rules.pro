# ProGuard rules for IReader Extensions
# Extensions are loaded dynamically by the main app, so we need to keep the Extension class

# Keep the generated Extension class (entry point for the main app)
-keep class ireader.**.Extension { *; }

# Keep source classes annotated with @Extension
-keep @tachiyomix.annotations.Extension class * { *; }

# Keep all source implementations
-keep class ireader.** extends ireader.core.source.Source { *; }
-keep class ireader.** extends ireader.core.source.SourceFactory { *; }
-keep class ireader.** extends ireader.core.source.ParsedHttpSource { *; }

# Keep common utils (not yet in main app)
-keep class ireader.common.utils.** { *; }

# Keep Kotlin metadata for reflection
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations

# Remove logging
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Optimization
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# Don't warn about missing classes (they're in the main app)
-dontwarn ireader.core.**
-dontwarn io.ktor.**
-dontwarn kotlinx.**
-dontwarn com.fleeksoft.ksoup.**
-dontwarn kotlin.**
-dontwarn org.jetbrains.**
