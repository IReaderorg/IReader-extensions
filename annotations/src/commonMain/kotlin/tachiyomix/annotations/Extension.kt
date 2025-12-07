package tachiyomix.annotations

/*
    Copyright (C) 2018 The IReader Open Source Project

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

/**
 * 🏷️ EXTENSION - Mark your class as an IReader source
 * 
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  THIS IS REQUIRED FOR EVERY SOURCE!                                      ║
 * ╠══════════════════════════════════════════════════════════════════════════╣
 * ║                                                                          ║
 * ║  @Extension                                                              ║
 * ║  abstract class MySource(deps: Dependencies) : SourceFactory(deps) {     ║
 * ║      // Your source code here                                            ║
 * ║  }                                                                       ║
 * ║                                                                          ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 * 
 * REQUIREMENTS:
 * ─────────────
 * Your class MUST be:
 *   ✓ open or abstract (so KSP can extend it)
 *   ✓ Implement ireader.core.source.Source (usually via SourceFactory)
 *   ✓ Have a constructor that takes Dependencies
 * 
 * WHAT KSP GENERATES:
 * ───────────────────
 * When you build, KSP creates a concrete Extension class that:
 *   • Sets the source name, language, and ID
 *   • Handles instantiation by the app
 *   • Registers the source in the extension system
 * 
 * EXAMPLE:
 * ────────
 * ```kotlin
 * package ireader.mysource
 * 
 * import ireader.core.source.Dependencies
 * import ireader.core.source.SourceFactory
 * import tachiyomix.annotations.Extension
 * 
 * @Extension
 * abstract class MySource(deps: Dependencies) : SourceFactory(deps) {
 *     override val name = "My Source"
 *     override val lang = "en"
 *     override val baseUrl = "https://example.com"
 *     override val id: Long = 12345L
 *     
 *     // ... rest of your implementation
 * }
 * ```
 * 
 * SEE ALSO:
 * ─────────
 * • @AutoSourceId - Auto-generate the source ID
 * • @MadaraSource - For Madara-based sites (no code needed!)
 * • @ThemeSource - For other theme-based sites
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class Extension
