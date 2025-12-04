import org.gradle.api.Project

/**
 * Configuration for a KMP (Kotlin Multiplatform) extension source.
 * 
 * Each KMP extension can build to:
 * - APK (Android)
 * - JAR (Desktop/JVM)
 * - JS (Browser/iOS interop)
 */
data class KmpExtension(
    /** Display name of the extension */
    val name: String,
    
    /** Language code (e.g., "en", "ar", "fr") */
    val lang: String,
    
    /** Version code for updates */
    val versionCode: Int,
    
    /** Library version compatibility */
    val libVersion: String = "2",
    
    /** Description shown in extension list */
    val description: String = "",
    
    /** Whether the source contains NSFW content */
    val nsfw: Boolean = false,
    
    /** Icon URL or asset path */
    val icon: String = DEFAULT_ICON,
    
    /** Source directory relative to project */
    val sourceDir: String = "",
    
    /** Assets directory for icons */
    val assetsDir: String = "",
    
    /** Unique source ID */
    val sourceId: Long = 0L,
    
    /** Enable JS output */
    val enableJs: Boolean = true,
    
    /** Enable Desktop JAR output */
    val enableDesktop: Boolean = true,
    
    /** Project dependencies */
    val projectDependencies: List<String> = emptyList(),
    
    /** Remote Maven dependencies */
    val remoteDependencies: List<String> = emptyList()
) {
    val versionName: String
        get() = "$libVersion.$versionCode"
    
    val flavor: String
        get() = "${name.toLowerCase().replace(" ", "")}-$lang"
    
    val packageName: String
        get() = "ireader.${name.toLowerCase().replace(" ", "").replace("-", "")}"
}

/**
 * Register KMP extensions in the project
 */
fun org.gradle.api.Project.registerKmpExtensions(kmpExtensions: List<KmpExtension>) {
    this.extensions.extraProperties.set("kmpExtensionList", kmpExtensions)
}
