package ireader.mtlnovelin

import ireader.core.source.Dependencies
import ireader.mtlnovelmodel.MtlNovelModel
import tachiyomix.annotations.Extension
import tachiyomix.annotations.AutoSourceId

/**
 * 🇮🇩 MTLNovel Indonesian - Machine Translation Source
 * 
 * Indonesian version of MTLNovel.
 * Uses @AutoSourceId for automatic ID generation.
 */
@Extension
@AutoSourceId(seed = "MtlNovelIn")
abstract class MtlNovelIn(val deps: Dependencies) : MtlNovelModel(deps) {
    
    // ═══════════════════════════════════════════════════════════════
    // 📋 BASIC SOURCE INFO - Override base class
    // ═══════════════════════════════════════════════════════════════
    override val baseUrl: String get() = "https://id.mtlnovel.com"
    override val lang = "in"
}
