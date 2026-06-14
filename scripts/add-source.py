#!/usr/bin/env python3
"""
IReader Source Creator
Creates extension sources with proper structure.

Usage: python scripts/add-source.py
"""

import sys
import hashlib
import re
import argparse
from pathlib import Path

def generate_id(name: str, lang: str) -> int:
    key = f"{name.lower()}/{lang}/1"
    h = hashlib.md5(key.encode()).digest()
    return int.from_bytes(h[:8], 'big') & 0x7FFFFFFFFFFFFFFF

def clean_name(name: str) -> str:
    return ''.join(word.capitalize() for word in re.sub(r'[^a-zA-Z0-9\s]', '', name).split())

MADARA_TEMPLATE = '''package ireader.{package}

import tachiyomix.annotations.MadaraSource

@MadaraSource(
    name = "{class_name}",
    baseUrl = "{url}",
    lang = "{lang}",
    id = {source_id}L
)
object {class_name}Config
'''

HTML_TEMPLATE = '''package ireader.{package}

import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.model.*
import tachiyomix.annotations.Extension
import tachiyomix.annotations.AutoSourceId
import tachiyomix.annotations.GenerateFilters
import tachiyomix.annotations.GenerateCommands

@Extension
@AutoSourceId(seed = "{class_name}")
@GenerateFilters(title = true, sort = true, sortOptions = ["Latest", "Popular"])
@GenerateCommands(detailFetch = true, chapterFetch = true, contentFetch = true)
abstract class {class_name}(deps: Dependencies) : SourceFactory(deps = deps) {{

    override val name = "{class_name}"
    override val baseUrl = "{url}"
    override val lang = "{lang}"
    override val id = {source_id}L

    override val exploreFetchers = listOf(
        BaseExploreFetcher(
            "Latest",
            endpoint = "/novels/page/{{page}}/",
            selector = ".novel-item",
            nameSelector = ".title",
            coverSelector = "img",
            coverAtt = "src",
            linkSelector = "a",
            linkAtt = "href",
            addBaseUrlToLink = true,
            addBaseurlToCoverLink = true
        ),
        BaseExploreFetcher(
            "Search",
            endpoint = "/search?q={{query}}",
            selector = ".novel-item",
            nameSelector = ".title",
            coverSelector = "img",
            coverAtt = "src",
            linkSelector = "a",
            linkAtt = "href",
            addBaseUrlToLink = true,
            addBaseurlToCoverLink = true,
            type = SourceFactory.Type.Search
        )
    )

    override val detailFetcher = SourceFactory.Detail(
        nameSelector = "h1",
        coverSelector = ".cover img",
        coverAtt = "src",
        descriptionSelector = ".description",
        authorBookSelector = ".author",
        categorySelector = ".genres a",
        addBaseurlToCoverLink = true
    )

    override val chapterFetcher = SourceFactory.Chapters(
        selector = ".chapter-list li",
        nameSelector = "a",
        linkSelector = "a",
        linkAtt = "href",
        addBaseUrlToLink = true,
        reverseChapterList = true
    )

    override val contentFetcher = SourceFactory.Content(
        pageContentSelector = ".chapter-content p"
    )
}}
'''

JSON_TEMPLATE = '''package ireader.{package}

import io.ktor.client.request.*
import io.ktor.client.statement.*
import ireader.core.log.Log
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.*
import kotlinx.serialization.json.*
import tachiyomix.annotations.Extension
import tachiyomix.annotations.AutoSourceId

@Extension
@AutoSourceId(seed = "{class_name}")
abstract class {class_name}(deps: Dependencies) : SourceFactory(deps = deps) {{

    override val lang: String get() = "{lang}"
    override val baseUrl: String get() = "{url}"
    override val id: Long get() = {class_name}SourceId.ID
    override val name: String get() = "{class_name}"

    override fun getFilters(): FilterList = listOf(Filter.Title())
    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Content.Fetch(),
        Command.Chapter.Fetch(),
    )

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {{
        val query = filters.findInstance<Filter.Title>()?.value ?: ""
        val endpoint = if (query.isNotBlank()) {{
            "$baseUrl/api/search?q=$query&page=$page"
        }} else {{
            "$baseUrl/api/novels?page=$page&limit=20"
        }}
        return try {{
            val response = client.get(requestBuilder(endpoint))
            val body = response.bodyAsText()
            val json = Json.parseToJsonElement(body).jsonObject
            val data = json["data"]?.jsonArray ?: return MangasPageInfo(emptyList(), false)
            val hasMore = json["hasMore"]?.jsonPrimitive?.boolean ?: false

            val mangaList = data.map {{ element ->
                val obj = element.jsonObject
                MangaInfo(
                    key = "$baseUrl/novel/${{obj["slug"]?.jsonPrimitive?.content}}",
                    title = obj["title"]?.jsonPrimitive?.content ?: "",
                    cover = obj["cover"]?.jsonPrimitive?.content ?: ""
                )
            }}
            MangasPageInfo(mangaList, hasMore)
        }} catch (e: Exception) {{
            Log.error {{ "Error: ${{e.message}}" }}
            MangasPageInfo(emptyList(), false)
        }}
    }}

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {{
        return try {{
            val slug = manga.key.substringAfterLast("/")
            val response = client.get(requestBuilder("$baseUrl/api/novel/$slug"))
            val body = response.bodyAsText()
            val json = Json.parseToJsonElement(body).jsonObject
            manga.copy(
                title = json["title"]?.jsonPrimitive?.content ?: manga.title,
                cover = json["cover"]?.jsonPrimitive?.content ?: manga.cover,
                description = json["description"]?.jsonPrimitive?.content ?: "",
                author = json["author"]?.jsonPrimitive?.content ?: ""
            )
        }} catch (e: Exception) {{ manga }}
    }}

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {{
        return try {{
            val slug = manga.key.substringAfterLast("/")
            val response = client.get(requestBuilder("$baseUrl/api/novel/$slug/chapters"))
            val body = response.bodyAsText()
            val json = Json.parseToJsonElement(body).jsonObject
            val chapters = json["chapters"]?.jsonArray ?: return emptyList()

            chapters.map {{ ch ->
                val obj = ch.jsonObject
                ChapterInfo(
                    name = obj["title"]?.jsonPrimitive?.content ?: "",
                    key = "$baseUrl/novel/$slug/chapter/${{obj["number"]?.jsonPrimitive?.int}}"
                )
            }}.reversed()
        }} catch (e: Exception) {{ emptyList() }}
    }}

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {{
        return try {{
            val doc = client.get(requestBuilder(chapter.key)).asJsoup()
            doc.select(".chapter-content p").map {{ Text(it.text()) }}
        }} catch (e: Exception) {{ listOf(Text("Error loading content")) }}
    }}
}}
'''

BUILD_GRADLE = '''listOf("{lang}").map {{ lang ->
    Extension(
        name = "{class_name}",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "",
        nsfw = false,
        icon = DEFAULT_ICON,
        assetsDir = "{lang}/{package}/main/assets",
    )
}}.also(::register)
'''

def main():
    parser = argparse.ArgumentParser(description='IReader Source Creator')
    parser.add_argument('--name', '-n', help='Source name')
    parser.add_argument('--url', '-u', help='Base URL')
    parser.add_argument('--lang', '-l', default='en', help='Language code')
    parser.add_argument('--type', '-t', choices=['madara', 'html', 'json'], 
                       help='Source type')
    parser.add_argument('--nsfw', action='store_true', help='Mark as NSFW')
    parser.add_argument('--quick', '-q', action='store_true', help='Skip confirmations')
    args = parser.parse_args()
    
    print("\n=== IReader Source Creator ===\n")
    
    name = args.name or input("Source name (e.g. NovelFull): ").strip()
    if not name:
        print("Error: Name required")
        return
    
    url = args.url or input("Website URL (e.g. https://novelfull.com): ").strip()
    if not url:
        print("Error: URL required")
        return
    if not url.startswith("http"):
        url = "https://" + url
    url = url.rstrip("/")
    
    lang = args.lang or input("Language code [en]: ").strip().lower() or "en"
    
    source_type = args.type
    if not source_type:
        print("\nSource type:")
        print("  1. madara  - Madara/WordPress theme (zero code!)")
        print("  2. html    - Standard HTML scraping with CSS selectors")
        print("  3. json    - JSON API source")
        choice = input("Choice [1/2/3]: ").strip() or "2"
        source_type = {"1": "madara", "2": "html", "3": "json"}.get(choice, "html")
    
    nsfw = args.nsfw
    if not nsfw and not args.quick:
        nsfw = input("NSFW content? [y/N]: ").strip().lower() == 'y'
    
    class_name = clean_name(name)
    package = class_name.lower()
    source_id = generate_id(class_name, lang)
    
    base = Path("sources") / lang / package
    src = base / "main" / "src" / "ireader" / package
    assets = base / "main" / "assets"
    src.mkdir(parents=True, exist_ok=True)
    assets.mkdir(parents=True, exist_ok=True)
    
    templates = {
        "madara": MADARA_TEMPLATE,
        "html": HTML_TEMPLATE,
        "json": JSON_TEMPLATE,
    }
    
    source_code = templates[source_type].format(
        class_name=class_name,
        package=package,
        url=url,
        lang=lang,
        source_id=source_id
    )
    
    (src / f"{class_name}.kt").write_text(source_code, encoding='utf-8')
    (base / "build.gradle.kts").write_text(
        BUILD_GRADLE.format(class_name=class_name, lang=lang, package=package),
        encoding='utf-8'
    )
    
    print(f"\nCreated: sources/{lang}/{package}/")
    print(f"Source ID: {source_id}")
    print(f"Type: {source_type}")
    
    if source_type == "madara":
        print("\nMadara source created - no code needed!")
    else:
        print(f"\nUpdate selectors in {class_name}.kt")
    
    print(f"\nBuild: ./gradlew :extensions:individual:{lang}:{package}:assemble{lang.capitalize()}Debug")

if __name__ == "__main__":
    main()
