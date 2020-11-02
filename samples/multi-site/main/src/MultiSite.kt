package tachiyomix.sample.multi

import tachiyomi.source.Dependencies
import tachiyomi.source.HttpSource
import tachiyomi.source.model.ChapterInfo
import tachiyomi.source.model.FilterList
import tachiyomi.source.model.Listing
import tachiyomi.source.model.MangaInfo
import tachiyomi.source.model.MangasPageInfo
import tachiyomi.source.model.Page
import tachiyomix.annotations.Extension

@Extension
abstract class MultiSite(deps: Dependencies): HttpSource(deps) {

  override val baseUrl = ""

  override suspend fun getChapterList(manga: MangaInfo): List<ChapterInfo> {
    throw Exception("TODO")
  }

  override suspend fun getMangaDetails(manga: MangaInfo): MangaInfo {
    throw Exception("TODO")
  }

  override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
    throw Exception("TODO")
  }

  override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
    throw Exception("TODO")
  }

  override suspend fun getPageList(chapter: ChapterInfo): List<Page> {
    throw Exception("TODO")
  }

  override fun getFilters(): FilterList {
    return emptyList()
  }

}
