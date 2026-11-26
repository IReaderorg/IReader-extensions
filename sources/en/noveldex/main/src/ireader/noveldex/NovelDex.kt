package ireader.noveldex

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLParameter
import ireader.core.log.Log
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.findInstance
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.Listing
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.model.Page
import ireader.core.source.model.Text
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tachiyomix.annotations.AutoSourceId
import tachiyomix.annotations.Extension

/**
 * NovelDex source.
 *
 * Catalogue data comes from the site's public read-only `/api/ai` catalogue API.
 * Chapter text uses the same client-side decryption pipeline as the site's own
 * reader (RSC flight payload + XOR key derivation) and is only used for free,
 * publicly readable chapters. Paid chapters are listed but never unlocked here.
 */
@Extension
@AutoSourceId(seed = "NovelDex")
abstract class NovelDex(private val deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://noveldex.io"
    private val apiBase: String get() = "$baseUrl/api/ai"
    override val name: String get() = "NovelDex"

    override fun getUserAgent(): String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val sortOptions = arrayOf(
        "Popular", "Latest", "Updated", "Trending", "Longest",
        "Bookmarked", "Rating", "Newest", "Alphabetical",
    )
    private val sortValues = arrayOf(
        "popular", "latest", "updated", "trending", "longest",
        "bookmarked", "rating", "newest", "alphabetical",
    )

    private val statusPairs = listOf(
        "" to "Any", "ONGOING" to "Ongoing", "COMPLETED" to "Completed",
        "HIATUS" to "Hiatus", "DROPPED" to "Dropped",
        "DISCONTINUED" to "Discontinued", "UPCOMING" to "Upcoming",
    )
    private val typePairs = listOf("" to "Any", "novel" to "Novel", "comic" to "Comic")
    private val originPairs = listOf(
        "" to "Any", "korean" to "Korean", "japanese" to "Japanese",
        "chinese" to "Chinese", "other" to "Other",
    )
    private val nsfwPairs = listOf(
        "exclude" to "Exclude", "include" to "Include", "only" to "Only",
    )
    // Genre slugs/names are static snapshots. Refresh from:
    //   GET https://noveldex.io/api/ai/genres
    //   -> { "genres": [{ "slug": "...", "name": "...", "series_count": n, "is_nsfw": bool }, ...] }
    // Keep the list in "slug" to "Display Name" form; "" to "Any" must stay first.
    private val genrePairs = listOf(
        "action" to "Action", "adult" to "Adult", "adventure" to "Adventure",
        "campus" to "Campus", "comedy" to "Comedy", "contemporary" to "Contemporary",
        "contemporary-romance" to "Contemporary Romance", "dark-romance" to "Dark Romance",
        "drama" to "Drama", "ecchi" to "Ecchi", "erotica" to "Erotica",
        "fantasy" to "Fantasy", "gamelit" to "GameLit", "gender-bender" to "Gender Bender",
        "harem" to "Harem", "historical" to "Historical",
        "historical-fantasy" to "Historical Fantasy", "horror" to "Horror",
        "isekai" to "Isekai", "josei" to "Josei", "litrpg" to "LitRPG",
        "martial-arts" to "Martial Arts", "mature" to "Mature", "mecha" to "Mecha",
        "medical" to "Medical", "melodrama" to "Melodrama", "military" to "Military",
        "military-drama" to "Military Drama", "mystery" to "Mystery",
        "omegaverse" to "Omegaverse", "political" to "Political",
        "psychological" to "Psychological", "psychological-drama" to "Psychological Drama",
        "psychological-thriller" to "Psychological Thriller", "regression" to "Regression",
        "revenge" to "Revenge", "reverse-harem" to "Reverse Harem", "romance" to "Romance",
        "romance-fantasy" to "Romance Fantasy", "romantic-comedy" to "Romantic Comedy",
        "school-life" to "School Life", "sci-fi" to "Sci-Fi", "seinen" to "Seinen",
        "shoujo" to "Shoujo", "shoujo-ai" to "Shoujo Ai", "shounen" to "Shounen",
        "shounen-ai" to "Shounen Ai", "slice-of-life" to "Slice of Life",
        "smut" to "Smut", "sports" to "Sports", "supernatural" to "Supernatural",
        "survival" to "Survival", "suspense" to "Suspense", "thriller" to "Thriller",
        "tragedy" to "Tragedy", "virtual-reality" to "Virtual Reality",
        "wuxia" to "Wuxia", "xianxia" to "Xianxia", "xuanhuan" to "Xuanhuan",
        "yaoi" to "Yaoi", "yuri" to "Yuri",
    )
    // Tag slugs/names are static snapshots. Refresh from:
    //   GET https://noveldex.io/api/ai/tags
    //   -> { "tags": [{ "slug": "...", "name": "...", "series_count": n, "is_nsfw": bool }, ...] }
    // Keep the list in "slug" to "Display Name" form; "" to "Any" must stay first.
    // After adding site values here, bump versionCode in build.gradle.kts and republish.
    private val tagPairs = listOf(
        "" to "Any", "male-protagonist" to "Male Protagonist",
        "modern-day" to "Modern Day", "overpowered-protagonist" to "Overpowered Protagonist",
        "obsessive-love" to "Obsessive Love", "fantasy" to "Fantasy",
        "misunderstandings" to "Misunderstandings", "harem" to "Harem",
        "character-growth" to "Character Growth", "academy" to "Academy",
        "modern-fantasy" to "Modern Fantasy", "transmigration" to "Transmigration",
        "r-18" to "R-18", "magic" to "Magic", "fantasy-world" to "Fantasy World",
        "gender-bender" to "Gender Bender", "game-elements" to "Game Elements",
        "female-protagonist" to "Female Protagonist", "reincarnation" to "Reincarnation",
        "romance" to "Romance", "beautiful-female-lead" to "Beautiful Female Lead",
        "hunters" to "Hunters", "smut" to "Smut", "action" to "Action",
        "possession" to "Possession", "special-abilities" to "Special Abilities",
        "survival" to "Survival", "monsters" to "Monsters",
        "genius-protagonist" to "Genius Protagonist", "slice-of-life" to "Slice of Life",
        "nobles" to "Nobles", "weak-to-strong" to "Weak to Strong",
        "dungeons" to "Dungeons", "yandere" to "Yandere", "adventure" to "Adventure",
        "light-novel" to "Light Novel", "adult" to "Adult",
        "dark-fantasy" to "Dark Fantasy", "aristocracy" to "Aristocracy",
        "system" to "System", "comedy" to "Comedy",
        "hidden-abilities" to "Hidden Abilities", "clever-protagonist" to "Clever Protagonist",
        "medieval" to "Medieval", "secret-identity" to "Secret Identity",
        "drama" to "Drama", "pure-love" to "Pure Love",
        "handsome-male-lead" to "Handsome Male Lead", "revenge" to "Revenge",
        "calm-protagonist" to "Calm Protagonist", "regression" to "Regression",
        "protagonist-strong-from-the-start" to "Protagonist Strong from the Start",
        "livestreaming" to "Livestreaming", "politics" to "Politics",
        "sword-and-magic" to "Sword And Magic", "second-chance" to "Second Chance",
        "adapted-to-manhwa" to "Adapted to Manhwa", "heroes" to "Heroes",
        "sword-wielder" to "Sword Wielder", "corruption" to "Corruption",
        "dark" to "Dark", "demons" to "Demons", "martial-arts" to "Martial Arts",
        "european-ambience" to "European Ambience", "male-to-female" to "Male to Female",
        "transported-into-a-game-world" to "Transported into a Game World",
        "regret" to "Regret", "adventurers" to "Adventurers", "mature" to "Mature",
        "royalty" to "Royalty", "antihero-protagonist" to "Antihero Protagonist",
        "apocalypse" to "Apocalypse", "alternate-world" to "Alternate World",
        "netori" to "Netori", "schemes-and-conspiracies" to "Schemes And Conspiracies",
        "level-system" to "Level System", "wars" to "Wars",
        "hard-working-protagonist" to "Hard-Working Protagonist",
        "possessive-characters" to "Possessive Characters", "chat-rooms" to "Chat Rooms",
        "training" to "Training", "school-life" to "School Life", "acting" to "Acting",
        "tower-climbing" to "Tower Climbing", "supernatural" to "Supernatural",
        "isekai" to "Isekai", "netorare" to "Netorare",
        "romance-fantasy" to "Romance Fantasy", "slow-romance" to "Slow Romance",
        "strong-to-stronger" to "Strong to Stronger",
        "devoted-love-interests" to "Devoted Love Interests", "incest" to "Incest",
        "fantasy-creatures" to "Fantasy Creatures", "yuri" to "Yuri",
        "accelerated-growth" to "Accelerated Growth", "urban-fantasy" to "Urban Fantasy",
        "betrayal" to "Betrayal", "childhood-friends" to "Childhood Friends",
        "kingdom-building" to "Kingdom Building", "business-management" to "Business Management",
        "military" to "Military",
    )

    // IReader strips Filter.Group before calling getMangaList because a Group always reports
    // default. A few top-level Select slots stay compact, look grouped by their names, and
    // survive the filter round-trip: index 0 is "Any" and is stripped, other indexes are passed.
    private val genreOptions = listOf("" to "Any") + genrePairs.sortedBy { it.second }
    private val tagOptions = listOf("" to "Any") + tagPairs.sortedBy { it.second }
    private val genreSlotNames = listOf("Genre 1", "Genre 2", "Genre 3")
    private val excludeGenreSlotName = "Exclude Genre"
    private val tagSlotNames = listOf("Tag 1", "Tag 2", "Tag 3")
    private val excludeTagSlotName = "Exclude Tag"

    private fun List<*>.selectValue(name: String, pairs: List<Pair<String, String>>): String {
        val select = firstOrNull { it is Filter.Select && it.name == name } as? Filter.Select
            ?: return ""
        val label = select.options.getOrNull(select.value) ?: return ""
        return pairs.firstOrNull { it.second == label }?.first.orEmpty()
    }

    private fun List<*>.selectValues(
        names: List<String>,
        pairs: List<Pair<String, String>>,
    ): List<String> = names.mapNotNull { selectValue(it, pairs).takeIf(String::isNotBlank) }.distinct()

    private fun pairOptions(pairs: List<Pair<String, String>>): Array<String> =
        pairs.map { it.second }.toTypedArray()

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Select("Sort", sortOptions),
        Filter.Select("Status", pairOptions(statusPairs)),
        Filter.Select("Type", pairOptions(typePairs)),
        Filter.Select("Origin", pairOptions(originPairs)),
        Filter.Note("Genres"),
        *genreSlotNames.map { Filter.Select(it, pairOptions(genreOptions)) }.toTypedArray(),
        Filter.Select(excludeGenreSlotName, pairOptions(genreOptions)),
        Filter.Note("Tags"),
        *tagSlotNames.map { Filter.Select(it, pairOptions(tagOptions)) }.toTypedArray(),
        Filter.Select(excludeTagSlotName, pairOptions(tagOptions)),
        Filter.Text("Min Chapters"),
        Filter.Select("NSFW", pairOptions(nsfwPairs)),
        Filter.Check("On Sale"),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    override fun getListings(): List<Listing> = listOf(
        PopularListing(),
        LatestListing(),
        UpdatedListing(),
        TrendingListing(),
    )

    class PopularListing : Listing("Popular")
    class LatestListing : Listing("Latest")
    class UpdatedListing : Listing("Updated")
    class TrendingListing : Listing("Trending")

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return querySeries(page, sortValueByName(sort?.name))
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value?.trim().orEmpty()
        if (query.length >= 2) {
            val status = filters.selectValue("Status", statusPairs)
            val genres = filters.selectValues(genreSlotNames, genreOptions)
            val exgenres = filters.selectValues(listOf(excludeGenreSlotName), genreOptions)
            val tags = filters.selectValues(tagSlotNames, tagOptions)
            val extags = filters.selectValues(listOf(excludeTagSlotName), tagOptions)
            return searchSeries(query).let { result ->
                val filtered = result.mangas.filter { manga ->
                    val values = manga.genres.map { it.lowercase() }
                    (status.isBlank() || manga.status == parseStatus(status)) &&
                        (genres.isEmpty() || genres.any { it.lowercase() in values }) &&
                        (exgenres.isEmpty() || exgenres.none { it.lowercase() in values }) &&
                        (tags.isEmpty() || tags.any { it.lowercase() in values }) &&
                        (extags.isEmpty() || extags.none { it.lowercase() in values })
                }
                MangasPageInfo(filtered, result.hasNextPage)
            }
        }

        val sort = (filters.firstOrNull { it is Filter.Select && it.name == "Sort" } as? Filter.Select)
            ?.value?.let { sortValues.getOrNull(it) }
            ?: "popular"
        return querySeries(page, sort, filters)
    }

    private suspend fun querySeries(
        page: Int,
        sort: String,
        filters: FilterList? = null,
    ): MangasPageInfo {
        val params = mutableListOf(
            "page" to page.toString(),
            "limit" to "20",
            "sort" to sort,
            "nsfw" to (filters?.selectValue("NSFW", nsfwPairs)?.ifBlank { "exclude" } ?: "exclude"),
        )
        if (filters != null) {
            fun add(name: String, key: String, pairs: List<Pair<String, String>>) {
                val value = filters.selectValue(name, pairs)
                if (value.isNotBlank()) params += key to value
            }
            add("Status", "status", statusPairs)
            add("Type", "type", typePairs)
            add("Origin", "origin", originPairs)
            val genres = filters.selectValues(genreSlotNames, genreOptions)
            if (genres.isNotEmpty()) params += "genre" to genres.joinToString(",")
            val exgenres = filters.selectValues(listOf(excludeGenreSlotName), genreOptions)
            if (exgenres.isNotEmpty()) params += "exgenre" to exgenres.joinToString(",")
            val tags = filters.selectValues(tagSlotNames, tagOptions)
            if (tags.isNotEmpty()) params += "tag" to tags.joinToString(",")
            val extags = filters.selectValues(listOf(excludeTagSlotName), tagOptions)
            if (extags.isNotEmpty()) params += "extag" to extags.joinToString(",")
            val min = (filters.firstOrNull { it is Filter.Text && it.name == "Min Chapters" } as? Filter.Text)
                ?.value?.trim()?.toIntOrNull()
            if (min != null && min > 0) params += "ch_min" to min.toString()
            if ((filters.firstOrNull { it is Filter.Check && it.name == "On Sale" } as? Filter.Check)?.value == true) {
                params += "sale" to "true"
            }
        }
        val query = params.joinToString("&") { (key, value) -> "$key=${value.encodeURLParameter()}" }
        val body = client.get(requestBuilder("$apiBase/series?$query")).bodyAsText()
        val obj = json.parseToJsonElement(body).jsonObject
        val mangas = obj["data"]?.jsonArray?.mapNotNull { parseSeries(it.jsonObject) }.orEmpty()
        val hasNext = obj["pagination"]?.jsonObject?.bool("has_more") ?: false
        return MangasPageInfo(mangas, hasNext)
    }

    private suspend fun searchSeries(rawQuery: String): MangasPageInfo {
        if (rawQuery.length < 2) return MangasPageInfo(emptyList(), false)
        val query = rawQuery.take(100)
        val url = "$apiBase/search?q=${query.encodeURLParameter()}&limit=20&nsfw=exclude"
        val body = client.get(requestBuilder(url)).bodyAsText()
        val results = json.parseToJsonElement(body).jsonObject["results"]?.jsonArray.orEmpty()
        return MangasPageInfo(results.mapNotNull { parseSeries(it.jsonObject) }, false)
    }

    private fun parseSeries(obj: JsonObject): MangaInfo? {
        val slug = obj.str("slug").takeIf { it.isNotBlank() } ?: return null
        val title = obj.str("title").takeIf { it.isNotBlank() } ?: return null
        val typePath = if (obj.str("type").equals("comic", true)) "comic" else "novel"
        val key = obj.str("url").takeIf { it.startsWith("http") }
            ?: "$baseUrl/series/$typePath/$slug"
        return MangaInfo(
            key = key,
            title = title,
            artist = obj.str("artist"),
            author = obj.str("author"),
            description = obj.str("description"),
            genres = (obj.stringList("genres") + obj.stringList("tags")).distinct(),
            status = parseStatus(obj.str("status")),
            cover = obj.str("cover_image"),
        )
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val slug = manga.key.slug()
        val body = client.get(
            requestBuilder("$apiBase/series/${slug.encodeURLParameter()}?chapters=none")
        ).bodyAsText()
        val obj = json.parseToJsonElement(body).jsonObject
        val parsed = parseSeries(obj) ?: return manga
        return manga.copy(
            title = parsed.title,
            artist = parsed.artist.ifBlank { manga.artist },
            author = parsed.author.ifBlank { manga.author },
            description = parsed.description.ifBlank { manga.description },
            genres = parsed.genres.ifEmpty { manga.genres },
            status = if (parsed.status != MangaInfo.UNKNOWN) parsed.status else manga.status,
            cover = parsed.cover.ifBlank { manga.cover },
        )
    }

    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>,
    ): List<ChapterInfo> {
        val slug = manga.key.slug()
        val chapters = mutableListOf<ChapterInfo>()
        var page = 1
        var hasMore = true

        while (hasMore && page <= 1000) {
            val url = "$apiBase/series/${slug.encodeURLParameter()}/chapters" +
                "?page=$page&limit=100&order=asc"
            val body = client.get(requestBuilder(url)).bodyAsText()
            val obj = json.parseToJsonElement(body).jsonObject
            val items = obj["chapters"]?.jsonArray ?: break
            items.forEach { element ->
                val chapter = element.jsonObject
                val number = chapter.float("number")
                val title = chapter.str("title").ifBlank { "Chapter ${numberStr(number)}" }
                val isFree = chapter.bool("is_free")
                chapters.add(
                    ChapterInfo(
                        key = chapter.str("url").ifBlank {
                            "$baseUrl/series/novel/$slug/chapter/${numberStr(number)}"
                        },
                        name = if (isFree) title else "$title [Paid]",
                        dateUpload = chapter.str("published_at").epochMillis(),
                        number = number,
                        scanlator = if (isFree) "free" else "paid",
                    )
                )
            }
            hasMore = obj["pagination"]?.jsonObject?.bool("has_more") ?: false
            page++
        }
        return chapters
    }

    override suspend fun getPageList(
        chapter: ChapterInfo,
        commands: List<Command<*>>,
    ): List<Page> {
        // Never attempt to unlock paid or early-access chapters.
        if (chapter.scanlator == "paid" || chapter.name.contains("[Paid]", ignoreCase = true)) {
            return emptyList()
        }

        return try {
            val url = if (chapter.key.startsWith("http")) {
                chapter.key
            } else {
                "$baseUrl/series/novel/${chapter.key}"
            }
            val flight = client.get(
                requestBuilder(url) {
                    append(HttpHeaders.UserAgent, getUserAgent())
                    append(HttpHeaders.CacheControl, "max-age=0")
                    append("RSC", "1")
                }
            ).bodyAsText()

            val html = decryptChapter(flight)
                ?: extractPlainRsc(flight)
                ?: return emptyList()
            extractParagraphs(html).map { Text(it) }
        } catch (e: Exception) {
            Log.error { "NovelDex chapter fetch failed: ${e.message}" }
            emptyList()
        }
    }

    /**
     * Reproduces the reader's own decryption: the RSC flight payload references a
     * base64 row and carries the key seed hint. No anti-bot mechanism is bypassed;
     * this is exactly the transformation the browser performs for the reader.
     */
    private fun decryptChapter(flight: String): String? {
        val normalized = flight.replace("\\\"", "\"")
        val start = normalized.indexOf("\"xorEncryption\"")
        if (start < 0) return null
        val section = normalized.substring(start, minOf(start + 2000, normalized.length))

        val rowId = valueAfter(section, "encryptedBase64")?.removePrefix("$") ?: return null
        val hint = valueAfter(section, "partialKeyHint") ?: return null
        val timestamp = numberAfter(section, "timestamp") ?: return null
        val nonce = valueAfter(section, "clientNonce") ?: "nd-xor-v1-nonce"

        val base64 = resolveFlightRow(normalized, rowId)?.let(::rowPayload)?.trim()
        if (base64.isNullOrBlank()) return null

        val key = deriveKey("$hint|$timestamp|$nonce")
        val raw = decodeBase64(base64)
        if (raw.isEmpty()) return null
        val plain = ByteArray(raw.size) { i ->
            (raw[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        return plain.decodeToString()
    }

    /**
     * Some chapters ship plaintext HTML in the RSC payload instead of an encrypted row.
     * Resolve the `"content":"$KEY"` reference and read that row directly.
     */
    private fun extractPlainRsc(flight: String): String? {
        val normalized = flight.replace("\\\"", "\"")
        val marker = Regex(""""content"\s*:\s*"\$([0-9a-fA-F]+)"""")
        val rowId = marker.find(normalized)?.groupValues?.get(1) ?: return null
        val content = resolveFlightRow(normalized, rowId)?.let(::rowPayload)?.trim()
        if (content.isNullOrBlank() || content.length < 50) return null
        if (content.contains("xorEncryption") || content.contains("encryptedBase64")) return null
        return content
    }

    private fun resolveFlightRow(flight: String, rowId: String): String? {
        val rowRegex = Regex(
            "(?s)(?:^|\\n)" + Regex.escape(rowId) + ":(.*?)(?=\\n[0-9a-f]+:|\\z)"
        )
        return rowRegex.find(flight)?.groupValues?.get(1)
    }

    private fun rowPayload(row: String): String =
        if (row.startsWith("T")) row.substring(row.indexOf(',') + 1) else row

    private fun valueAfter(text: String, key: String): String? {
        val marker = "\"$key\":\""
        val index = text.indexOf(marker)
        if (index < 0) return null
        return text.substring(index + marker.length)
            .substringBefore('"')
            .takeIf { it.isNotEmpty() }
    }

    private fun numberAfter(text: String, key: String): String? {
        val marker = "\"$key\":"
        val index = text.indexOf(marker)
        if (index < 0) return null
        return text.substring(index + marker.length)
            .trimStart()
            .takeWhile { it.isDigit() || it == '-' }
            .takeIf { it.isNotEmpty() }
    }

    private fun deriveKey(seed: String): ByteArray {
        var hash = 0x811c9dc5u
        for (ch in seed) {
            hash = (hash xor ch.code.toUInt()) * 0x1000193u
        }
        return ByteArray(32) { i ->
            hash = hash xor (i.toUInt() * 0x9e3779b9u)
            hash *= 0x1000193u
            (hash and 0xFFu).toByte()
        }
    }

    private fun decodeBase64(input: String): ByteArray {
        val table = IntArray(128) { -1 }
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        for (i in alphabet.indices) table[alphabet[i].code] = i

        val out = ArrayList<Byte>(input.length * 3 / 4 + 4)
        var accumulator = 0
        var bits = 0
        for (ch in input) {
            if (ch == '=') break
            val value = if (ch.code < 128) table[ch.code] else -1
            if (value < 0) continue
            accumulator = (accumulator shl 6) or value
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.add(((accumulator ushr bits) and 0xFF).toByte())
            }
        }
        return out.toByteArray()
    }

    private val paragraphBreakRegex = Regex(
        """(?:</(?:div|p|li|h[1-6]|blockquote)>|(?:<br\s*/?>\s*){2,}|\n\s*\n)""",
    )

    private fun extractParagraphs(html: String): List<String> {
        val paragraphRegex = Regex("""(?s)<p(?:\s[^>]*)?>(.*?)</p>""")
        // Some chapters wrap the whole chapter in one <p> and use <br><br>
        // between paragraphs. Split those blocks too; a single <br> remains a
        // line break inside the same Text item.
        val fromParagraphs = paragraphRegex.findAll(html)
            .flatMap { splitParagraphBlock(it.groupValues[1]) }
            .toList()
        if (fromParagraphs.isNotEmpty()) return fromParagraphs

        // Fallback for block HTML without <p> tags.
        return splitParagraphBlock(html)
    }

    private fun splitParagraphBlock(block: String): List<String> =
        block.split(paragraphBreakRegex)
            .map(::cleanParagraphText)
            .filter { it.isNotBlank() }

    private fun cleanParagraphText(raw: String): String {
        var text = stripWatermark(raw)
        // Keep reader line breaks; only flatten horizontal formatting whitespace.
        text = text.replace(Regex("""<br\s*/?>"""), "\n")
        text = text.replace(Regex("""<[^>]+>"""), " ")
        text = Regex("""&#(\d+);""").replace(text) { match ->
            runCatching { match.groupValues[1].toInt().toChar().toString() }.getOrDefault(" ")
        }
        text = Regex("""&#x([0-9a-fA-F]+);""").replace(text) { match ->
            runCatching { match.groupValues[1].toInt(16).toChar().toString() }.getOrDefault(" ")
        }
        text = text
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
        text = text.replace(Regex("[ \\t\\r\\f\\u000B\\u00A0]+"), " ")
        text = text.replace(Regex("\\n[ \\t]*"), "\n")
        text = text.replace(Regex("\\n{2,}"), "\n")
        return text.trim()
    }

    /** Zero-width characters NovelDex injects for fingerprinting. */
    private fun stripWatermark(text: String): String = text
        .replace(Regex("[\\uFEFF\\u200B\\u200C\\u200D\\u200E\\u200F\\u034F\\u2060-\\u2064\\u2069\\uFFFE]+"), "")

    private fun sortValueByName(name: String?): String = when (name?.lowercase()) {
        "latest" -> "latest"
        "updated" -> "updated"
        "trending" -> "trending"
        "longest" -> "longest"
        "bookmarked" -> "bookmarked"
        "rating" -> "rating"
        "newest" -> "newest"
        "alphabetical" -> "alphabetical"
        else -> "popular"
    }

    private fun parseStatus(status: String): Long = when (status.trim().uppercase()) {
        "ONGOING" -> MangaInfo.ONGOING
        "COMPLETED" -> MangaInfo.COMPLETED
        "HIATUS" -> MangaInfo.ON_HIATUS
        "DROPPED", "CANCELLED" -> MangaInfo.CANCELLED
        "DISCONTINUED" -> MangaInfo.CANCELLED
        else -> MangaInfo.UNKNOWN
    }

    private fun String.slug(): String {
        return Regex("""/series/(?:novel|comic)/([^/?#]+)""")
            .find(this)?.groupValues?.get(1)
            ?: this
    }

    private fun numberStr(number: Float): String =
        if (number == number.toInt().toFloat()) number.toInt().toString() else number.toString()

    private fun String.epochMillis(): Long = if (isBlank()) {
        0L
    } else {
        runCatching { Instant.parse(this).toEpochMilliseconds() }.getOrDefault(0L)
    }

    private fun JsonObject.str(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull ?: ""

    private fun JsonObject.bool(key: String): Boolean =
        this[key]?.jsonPrimitive?.booleanOrNull ?: false

    private fun JsonObject.float(key: String): Float =
        this[key]?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: -1f

    private fun JsonObject.stringList(key: String): List<String> {
        val array = this[key] as? JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            when (element) {
                is JsonPrimitive -> element.contentOrNull?.takeIf { it.isNotBlank() }
                is JsonObject -> element.str("name")
                    .ifBlank { element.str("title") }
                    .takeIf { it.isNotBlank() }
                else -> null
            }
        }
    }
}
