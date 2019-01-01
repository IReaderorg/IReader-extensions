package tachiyomix.sample.multisrcs

import tachiyomi.source.Dependencies
import tachiyomix.annotations.Extension

@Extension
abstract class SiteA(deps: Dependencies) : MultiSiteCommon(deps)
