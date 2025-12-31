/*
 * Copyright (C) IReader Project
 * SPDX-License-Identifier: Apache-2.0
 */

package tachiyomix.annotations

/**
 * 🚫 SKIP SOURCE - Mark a source as broken/not working
 * 
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  Use this to exclude broken sources from the repository output           ║
 * ╠══════════════════════════════════════════════════════════════════════════╣
 * ║                                                                          ║
 * ║  @Extension                                                              ║
 * ║  @SkipSource(reason = "Site is down")                                    ║
 * ║  abstract class BrokenSource(deps: Dependencies) : SourceFactory(deps)   ║
 * ║                                                                          ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 * 
 * WHAT THIS DOES:
 * ───────────────
 * When a source is marked with @SkipSource:
 *   • The source is EXCLUDED from the repository index (index.json)
 *   • The source APK is NOT included in the repo output
 *   • The source code is still compiled (for future fixes)
 *   • A warning is logged during build showing the skip reason
 * 
 * USE CASES:
 * ──────────
 *   • Site is permanently down or blocked
 *   • Source has critical bugs that can't be fixed yet
 *   • Site changed structure and needs major rewrite
 *   • Temporary exclusion while debugging
 * 
 * EXAMPLE:
 * ────────
 * ```kotlin
 * @Extension
 * @SkipSource(
 *     reason = "Site requires Cloudflare bypass - needs WebView implementation",
 *     since = "2024-01-15"
 * )
 * abstract class BrokenSite(deps: Dependencies) : SourceFactory(deps) {
 *     // ... source code kept for future reference
 * }
 * ```
 * 
 * TO RE-ENABLE:
 * ─────────────
 * Simply remove the @SkipSource annotation when the source is fixed.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class SkipSource(
    /**
     * Reason why this source is being skipped.
     * This will be logged during build and shown in reports.
     * 
     * Examples:
     *   - "Site is permanently down"
     *   - "Requires Cloudflare bypass"
     *   - "API changed, needs rewrite"
     *   - "Critical parsing bug - investigating"
     */
    val reason: String,
    
    /**
     * Date when the source was marked as skipped (optional).
     * Format: "YYYY-MM-DD"
     * 
     * Helps track how long a source has been broken.
     */
    val since: String = "",
    
    /**
     * Whether to still compile the source code (default: true).
     * 
     * Set to false to completely skip compilation.
     * Useful for sources with compile errors that block the build.
     */
    val compile: Boolean = true
)

/**
 * 🔧 BROKEN SOURCE - Alias for @SkipSource with clearer intent
 * 
 * Use this when a source is broken and needs fixing.
 * Functionally identical to @SkipSource.
 * 
 * ```kotlin
 * @Extension
 * @BrokenSource(reason = "Selectors outdated after site redesign")
 * abstract class OldSite(deps: Dependencies) : SourceFactory(deps)
 * ```
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class BrokenSource(
    /** Reason why this source is broken */
    val reason: String,
    /** Date when marked as broken (YYYY-MM-DD) */
    val since: String = "",
    /** Whether to still compile the source */
    val compile: Boolean = true
)

/**
 * 🚧 DEPRECATED SOURCE - Mark a source as deprecated (will be removed)
 * 
 * Use this for sources that will be removed in a future version.
 * The source is still included in the repo but shows a deprecation warning.
 * 
 * ```kotlin
 * @Extension
 * @DeprecatedSource(
 *     reason = "Site merged with another - use NewSite instead",
 *     removeIn = "2.0.0"
 * )
 * abstract class OldSite(deps: Dependencies) : SourceFactory(deps)
 * ```
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class DeprecatedSource(
    /** Reason for deprecation */
    val reason: String,
    /** Version when this source will be removed */
    val removeIn: String = "",
    /** Alternative source to use instead */
    val replacement: String = ""
)
