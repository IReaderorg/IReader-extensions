package tachiyomix.sample.multi

import tachiyomi.source.Dependencies
import tachiyomi.source.HttpSource
import tachiyomi.source.model.ChapterInfo
import tachiyomi.source.model.FilterList
import tachiyomi.source.model.Listing
import tachiyomi.source.model.MangaInfo
import tachiyomi.source.model.MangasPageInfo
import tachiyomi.source.model.PageInfo
import tachiyomix.annotations.Extension

@Extension
abstract class MultiSite(deps: Dependencies): HttpSource(deps) {

  override val baseUrl = ""

  override fun fetchChapterList(manga: MangaInfo): List<ChapterInfo> {
    throw Exception("TODO")
  }

  override fun fetchMangaDetails(manga: MangaInfo): MangaInfo {
    throw Exception("TODO")
  }

  override fun fetchMangaList(filters: FilterList, page: Int): MangasPageInfo {
    throw Exception("TODO")
  }

  override fun fetchMangaList(sort: Listing?, page: Int): MangasPageInfo {
    throw Exception("TODO")
  }

  override fun fetchPageList(chapter: ChapterInfo): List<PageInfo> {
    throw Exception("TODO")
  }

  override fun getFilters(): FilterList {
    return emptyList()
  }

}
