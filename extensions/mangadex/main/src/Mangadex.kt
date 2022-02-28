package tachiyomix.mangadex

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import tachiyomi.core.http.okhttp
import tachiyomi.core.util.runBlocking
import tachiyomi.source.DeepLinkSource
import tachiyomi.source.Dependencies
import tachiyomi.source.HttpSource
import tachiyomi.source.model.ChapterInfo
import tachiyomi.source.model.DeepLink
import tachiyomi.source.model.Filter
import tachiyomi.source.model.FilterList
import tachiyomi.source.model.Listing
import tachiyomi.source.model.MangaInfo
import tachiyomi.source.model.MangasPageInfo
import tachiyomi.source.model.Page
import tachiyomix.annotations.Extension
import tachiyomix.mangadex.dto.MangaListDto
import tachiyomix.mangadex.dto.asMdMap
import java.util.concurrent.TimeUnit

@Extension
abstract class Mangadex(private val deps: Dependencies,private val okHttpClient: OkHttpClient): HttpSource(deps), DeepLinkSource {

  override val baseUrl = "https://mangadex.org"

  override val client = HttpClient(OkHttp) {
    engine {
      preconfigured = clientBuilder()
    }
    install(JsonFeature) {
      serializer = KotlinxSerializer(Json {
        ignoreUnknownKeys = true
      })
    }
  }

  private fun clientBuilder(): OkHttpClient = okHttpClient
    .newBuilder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

  override fun getRegex(): Regex {
    return Regex("""(?<=Ch\.) *([0-9]+)(\.[0-9]+)?""")
  }

  override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
    val dto = client.get<MangaListDto>(MDConstants.apiMangaUrl) {
      parameter("limit", MDConstants.mangaLimit)
      parameter("offset", MDConstants.mangaLimit * (page - 1))
      parameter("includes[]", MDConstants.coverArt)
    }

    val hasNextPage = dto.limit + dto.offset < dto.total

    val mangaList = dto.data.map { mdto ->
      val titleMap = mdto.attributes.title.asMdMap()
      val dirtyTitle = titleMap[lang]
        ?: titleMap["en"]
        ?: mdto.attributes.altTitles.jsonArray
          .find {
            val altTitle = it.asMdMap()
            altTitle[lang] ?: altTitle["en"] != null
          }?.asMdMap()?.values?.singleOrNull()
      val title = cleanString(dirtyTitle ?: "")

      val coverFileName = mdto.relationships.firstOrNull { relationshipDto ->
        relationshipDto.type.equals(MDConstants.coverArt, true)
      }?.attributes?.fileName
      val cover = "${MDConstants.cdnUrl}/covers/${mdto.id}/$coverFileName"

      MangaInfo(
        key = "/manga/${mdto.id}",
        title = title,
        cover = cover
      )
    }

    return MangasPageInfo(mangaList, hasNextPage)
  }

  /**
   * Remove bbcode tags as well as parses any html characters in description or
   * chapter name to actual characters for example &hearts; will show ♥
   */
  fun cleanString(string: String): String {
    val bbRegex =
      """\[(\w+)[^]]*](.*?)\[/\1]""".toRegex()
    var intermediate = string
      .replace("[list]", "")
      .replace("[/list]", "")
      .replace("[*]", "")
    // Recursively remove nested bbcode
    while (bbRegex.containsMatchIn(intermediate)) {
      intermediate = intermediate.replace(bbRegex, "$2")
    }
    return Parser.unescapeEntities(intermediate, false)
  }

  override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
    return getMangaList(null, 1)
  }

  override suspend fun getMangaDetails(manga: MangaInfo): MangaInfo {
    // TODO
    return manga
  }

  override suspend fun getChapterList(manga: MangaInfo): List<ChapterInfo> {
    TODO()
  }

  override suspend fun getPageList(chapter: ChapterInfo): List<Page> {
    TODO()
  }

  override fun getListings(): List<Listing> {
    return listOf(Latest())
  }

  override fun getFilters(): FilterList {
    return listOf(
      Filter.Title(),
      Filter.Author(),
      Filter.Artist(),
      R18(),
      GenreList(getGenreList())
    )
  }

  inner class Latest : Listing("Latest")

  private class TextField(name: String, val key: String) : Filter.Text(name)
  private class Genre(val id: String, name: String) : Filter.Check(name)
  private class GenreList(genres: List<Genre>) : Filter.Group("Genres", genres)
  private class R18 : Filter.Select("R18+", arrayOf("Show all", "Show only", "Show none"))

  private fun getFilterList() = listOf(
    TextField("Author", "author"),
    TextField("Artist", "artist"),
    R18(),
    GenreList(getGenreList())
  )

  private fun getGenreList() = listOf(
    Filter.Genre("4-koma"),
    Filter.Genre("Action"),
    Filter.Genre("Adventure"),
    Filter.Genre("Award Winning"),
    Filter.Genre("Comedy"),
    Filter.Genre("Cooking"),
    Filter.Genre("Doujinshi"),
    Filter.Genre("Drama"),
    Filter.Genre("Ecchi"),
    Filter.Genre("Fantasy"),
    Filter.Genre("Gender Bender"),
    Filter.Genre("Harem"),
    Filter.Genre("Historical"),
    Filter.Genre("Horror"),
    Filter.Genre("Josei"),
    Filter.Genre("Martial Arts"),
    Filter.Genre("Mecha"),
    Filter.Genre("Medical"),
    Filter.Genre("Music"),
    Filter.Genre("Mystery"),
    Filter.Genre("Oneshot"),
    Filter.Genre("Psychological"),
    Filter.Genre("Romance"),
    Filter.Genre("School Life"),
    Filter.Genre("Sci-Fi"),
    Filter.Genre("Seinen"),
    Filter.Genre("Shoujo"),
    Filter.Genre("Shoujo Ai"),
    Filter.Genre("Shounen"),
    Filter.Genre("Shounen Ai"),
    Filter.Genre("Slice of Life"),
    Filter.Genre("Smut"),
    Filter.Genre("Sports"),
    Filter.Genre("Supernatural"),
    Filter.Genre("Tragedy"),
    Filter.Genre("Webtoon"),
    Filter.Genre("Yaoi"),
    Filter.Genre("Yuri"),
    Filter.Genre("[no chapters]"),
    Filter.Genre("Game"),
    Filter.Genre("Isekai")
  )

  override fun handleLink(url: String): DeepLink? {
    return when {
      "/chapter/" in url ->
        DeepLink.Chapter(url.substringAfter("/chapter/").substringBefore("/"))
      "/title/" in url -> DeepLink.Manga(url.substringAfter("/title/").substringBefore("/"))
      else -> null
    }
  }

  override fun findMangaKey(chapterKey: String): String? {
    return runBlocking {
      val response = client.get<String>(baseUrl + chapterKey) {
      }
      val document = Jsoup.parse(response)
      val mangaId = document.getElementsByTag("meta")
        .first { it.attr("property") == "og:image" }
        .attr("content")
        .substringAfterLast("/")
        .substringBefore(".")

      "/title/$mangaId"
    }
  }

}
