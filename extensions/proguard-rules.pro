# ProGuard rules for IReader Extensions
# Extensions are loaded dynamically by the main app via reflection

# ============================================================================
# CRITICAL: Keep the generated Extension class
# AndroidCatalogLoader loads this via:
#   Class.forName("tachiyomix.extension.Extension", false, loader)
#       .getConstructor(Dependencies::class.java)
#       .newInstance(dependencies)
# ============================================================================

# Keep the Extension class and ALL its members
-keep,allowobfuscation class tachiyomix.extension.Extension {
    public <init>(ireader.core.source.Dependencies);
    *;
}

# Keep the class name (don't obfuscate)
-keepnames class tachiyomix.extension.Extension

# ============================================================================
# Keep source implementations
# ============================================================================

# Keep all classes in ireader.* packages (source code)
-keep class ireader.** { *; }
-keepnames class ireader.**

# Keep common utils (bundled in APK, not in main app yet)
-keep class ireader.common.** { *; }

# ============================================================================
# Keep Kotlin features needed for reflection
# ============================================================================

-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

# ============================================================================
# Don't warn about classes in main app
# ============================================================================

-dontwarn ireader.core.**
-dontwarn io.ktor.**
-dontwarn kotlinx.**
-dontwarn com.fleeksoft.ksoup.**
-dontwarn kotlin.**
-dontwarn org.jetbrains.**
-dontwarn tachiyomix.annotations.**

# ============================================================================
# Optimization settings
# ============================================================================

# Don't optimize - keep code as-is for compatibility
-dontoptimize

# Don't obfuscate - makes debugging easier
-dontobfuscate

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
