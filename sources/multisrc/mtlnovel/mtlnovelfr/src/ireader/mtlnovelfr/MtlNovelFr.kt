package ireader.mtlnovelfr

import ireader.core.source.Dependencies
import ireader.mtlnovelmodel.MtlNovelModel
import tachiyomix.annotations.Extension
import tachiyomix.annotations.AutoSourceId

/**
 * 🇫🇷 MTLNovel French - Machine Translation Source
 * 
 * French version of MTLNovel.
 * Uses @AutoSourceId for automatic ID generation.
 */
@Extension
@AutoSourceId(seed = "MtlNovelFr")
abstract class MtlNovelFr(val deps: Dependencies) : MtlNovelModel(deps) {
    
    // ═══════════════════════════════════════════════════════════════
    // 📋 BASIC SOURCE INFO - Override base class
    // ═══════════════════════════════════════════════════════════════
    override val baseUrl: String get() = "https://fr.mtlnovel.com"
    override val lang = "fr"
}
