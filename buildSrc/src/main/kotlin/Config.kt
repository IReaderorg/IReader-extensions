/**
 * Build configuration constants.
 * Centralized configuration for consistent build settings across all modules.
 */
object Config {
    const val compileSdk = 36
    const val minSdk = 26
    const val targetSdk = 35

    // Build optimization
    const val defaultChunkSize = 30

    // Network configuration
    const val defaultTimeoutSeconds = 30L
    const val defaultRetryAttempts = 3
}
