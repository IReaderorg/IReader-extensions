#!/usr/bin/env python3
"""
Empty Source Generator for IReader Extensions
Creates a complete extension structure with boilerplate code
"""

import sys
import argparse
from pathlib import Path
from typing import Optional
import hashlib

def generate_source_id(name: str, lang: str) -> int:
    """Generate a unique ID for the extension"""
    key = f"{name.lower()}/{lang}/1"
    hash_bytes = hashlib.md5(key.encode()).digest()
    id_value = int.from_bytes(hash_bytes[:8], 'big') & 0x7FFFFFFFFFFFFFFF
    return id_value

def create_kotlin_source(name: str, package: str, base_url: str, lang: str, source_id: int) -> str:
    """Generate Kotlin source code"""
    return f'''package ireader.{package}

import io.ktor.client.request.*
import io.ktor.http.*
import ireader.common.utils.*
import ireader.core.source.*
import ireader.core.source.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import tachiyomix.annotations.Extension

@Extension
abstract class {name}(private val deps: Dependencies) : ParsedHttpSource(deps) {{

    override val name = "{name}"
    override val id: Long = {source_id}L
    override val baseUrl = "{base_url}"
    override val lang = "{lang}"

    companion object {{
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }}

    // MARK: - Filters
    override fun getFilters(): FilterList {{
        return listOf(
            Filter.Title(),
            Filter.Sort(
                "Sort By:",
                arrayOf("Latest", "Popular", "Rating")
            ),
        )
    }}

    // MARK: - Listings
    override fun getListings(): List<Listing> {{
        return listOf(
            LatestListing(),
            PopularListing()
        )
    }}

    class LatestListing : Listing("Latest")
    class PopularListing : Listing("Popular")

    // MARK: - Search & Browse
    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {{
        val query = filters.findInstance<Filter.Title>()?.value
        
        if (!query.isNullOrBlank()) {{
            return getSearch(query, page)
        }}
        
        val sort = filters.findInstance<Filter.Sort>()?.value?.index
        return when (sort) {{
            0 -> getLatest(page)
            1 -> getPopular(page)
            else -> getLatest(page)
        }}
    }}
'''

def create_kotlin_source_part2() -> str:
    """Generate second part of Kotlin source"""
    return '''
    private suspend fun getLatest(page: Int): MangasPageInfo {
        val url = "$baseUrl/novels/page/$page/"
        return ErrorHandler.safeRequest {
            bookListParse(
                client.get(requestBuilder(url)).asJsoup(),
                ".novel-item", // TODO: Update this selector
                ".pagination .next" // TODO: Update this selector
            ) { bookFromElement(it) }
        }.getOrThrow()
    }

    private suspend fun getPopular(page: Int): MangasPageInfo {
        val url = "$baseUrl/novels/page/$page/?orderby=popular"
        return ErrorHandler.safeRequest {
            bookListParse(
                client.get(requestBuilder(url)).asJsoup(),
                ".novel-item", // TODO: Update this selector
                ".pagination .next" // TODO: Update this selector
            ) { bookFromElement(it) }
        }.getOrThrow()
    }

    private suspend fun getSearch(query: String, page: Int): MangasPageInfo {
        val url = "$baseUrl/search?q=${query}&page=$page"
        return ErrorHandler.safeRequest {
            bookListParse(
                client.get(requestBuilder(url)).asJsoup(),
                ".search-result", // TODO: Update this selector
                null
            ) { searchFromElement(it) }
        }.getOrThrow()
    }
'''

def create_kotlin_source_part3() -> str:
    """Generate third part of Kotlin source"""
    return '''
    // MARK: - Book Parsing
    private fun bookFromElement(element: Element): MangaInfo {
        val title = element.select(".title").text() // TODO: Update selector
        val url = element.select("a").attr("href") // TODO: Update selector
        val cover = ImageUrlHelper.extractImageUrl(
            element.select("img").first()!! // TODO: Update selector
        )
        
        return MangaInfo(
            key = url,
            title = title,
            cover = ImageUrlHelper.normalizeUrl(cover, baseUrl)
        )
    }

    private fun searchFromElement(element: Element): MangaInfo {
        return bookFromElement(element)
    }

    // MARK: - Details
    override fun detailParse(document: Document): MangaInfo {
        val title = document.select("h1.novel-title").text() // TODO: Update selector
        val cover = ImageUrlHelper.extractImageUrl(
            document.select(".novel-cover img").first()!! // TODO: Update selector
        )
        val author = document.select(".author-name").text() // TODO: Update selector
        val description = document.select(".novel-description").text() // TODO: Update selector
        val genres = document.select(".genre-tag").eachText() // TODO: Update selector
        val status = document.select(".novel-status").text() // TODO: Update selector

        return MangaInfo(
            title = title,
            cover = ImageUrlHelper.normalizeUrl(cover, baseUrl),
            description = description,
            author = author,
            genres = genres,
            status = StatusParser.parseStatus(status),
            key = ""
        )
    }
'''

def create_kotlin_source_part4() -> str:
    """Generate fourth part of Kotlin source"""
    return '''
    // MARK: - Chapters
    override fun chaptersSelector(): String {
        return ".chapter-list li" // TODO: Update selector
    }

    override fun chapterFromElement(element: Element): ChapterInfo {
        val link = element.select("a").attr("href") // TODO: Update selector
        val name = element.select("a").text() // TODO: Update selector
        val date = element.select(".chapter-date").text() // TODO: Update selector

        return ChapterInfo(
            name = name,
            key = link,
            dateUpload = DateParser.parseRelativeOrAbsoluteDate(date)
        )
    }

    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        return withContext(Dispatchers.IO) {
            ErrorHandler.safeRequest {
                chaptersParse(client.get(requestBuilder(manga.key)).asJsoup())
            }.getOrThrow().reversed()
        }
    }

    // MARK: - Content
    override fun pageContentParse(document: Document): List<String> {
        val paragraphs = document.select(".chapter-content p") // TODO: Update selector
            .eachText()
            .filter { it.isNotBlank() }
        
        return paragraphs
    }

    override suspend fun getContents(chapter: ChapterInfo): List<String> {
        return ErrorHandler.safeRequest {
            pageContentParse(client.get(contentRequest(chapter)).asJsoup())
        }.getOrThrow()
    }

    // MARK: - Headers
    override fun HttpRequestBuilder.headersBuilder(block: HeadersBuilder.() -> Unit) {
        headers {
            append(HttpHeaders.UserAgent, USER_AGENT)
            append(HttpHeaders.CacheControl, "max-age=0")
            append(HttpHeaders.Referrer, baseUrl)
        }
    }
}
'''

def create_build_gradle(name: str, lang: str, description: str, nsfw: bool) -> str:
    """Generate build.gradle.kts"""
    return f'''listOf("{lang}").map {{ lang ->
    Extension(
        name = "{name}",
        versionCode = 1,
        libVersion = "1",
        lang = lang,
        description = "{description}",
        nsfw = {str(nsfw).lower()},
        icon = DEFAULT_ICON,
    )
}}.also(::register)
'''

def create_readme(name: str, base_url: str) -> str:
    """Generate README.md"""
    return f'''# {name}

Source: {base_url}

## TODO

- [ ] Update all CSS selectors marked with TODO comments
- [ ] Test search functionality
- [ ] Test book details parsing
- [ ] Test chapter list parsing
- [ ] Test chapter content parsing
- [ ] Verify image loading
- [ ] Test date parsing
- [ ] Test status parsing
- [ ] Add custom filters if needed
- [ ] Add icon (96x96px) to res/mipmap-* folders

## Selectors to Update

1. Book list selector
2. Book title selector
3. Book cover selector
4. Book link selector
5. Chapter list selector
6. Chapter content selector
7. And more...

## Testing

Run the extension in Android Studio and verify:
- Search works
- Latest/Popular listings load
- Book details display correctly
- Chapters load
- Content displays properly
'''

def main():
    parser = argparse.ArgumentParser(description='Create an empty IReader extension')
    parser.add_argument('name', help='Extension name (e.g., NovelExample)')
    parser.add_argument('url', help='Base URL (e.g., https://example.com)')
    parser.add_argument('lang', help='Language code (e.g., en, ar, fr)')
    parser.add_argument('--nsfw', action='store_true', help='Mark as NSFW')
    parser.add_argument('--output', default='./sources', help='Output directory')
    parser.add_argument('--description', default='', help='Extension description')
    
    args = parser.parse_args()
    
    # Sanitize inputs
    name = args.name.replace(' ', '')
    package = name.lower().replace('-', '').replace('_', '')
    base_url = args.url.rstrip('/')
    lang = args.lang.lower()
    
    # Generate source ID
    source_id = generate_source_id(name, lang)
    
    # Create directory structure
    output_dir = Path(args.output)
    extension_dir = output_dir / lang / package
    extension_dir.mkdir(parents=True, exist_ok=True)
    
    # Create source directory
    src_dir = extension_dir / "main" / "src" / "ireader" / package
    src_dir.mkdir(parents=True, exist_ok=True)
    
    # Write Kotlin source file
    kotlin_file = src_dir / f"{name}.kt"
    kotlin_content = (
        create_kotlin_source(name, package, base_url, lang, source_id) +
        create_kotlin_source_part2() +
        create_kotlin_source_part3() +
        create_kotlin_source_part4()
    )
    kotlin_file.write_text(kotlin_content, encoding='utf-8')
    
    # Write build.gradle.kts
    build_file = extension_dir / "build.gradle.kts"
    description = args.description or f"Read novels from {name}"
    build_file.write_text(create_build_gradle(name, lang, description, args.nsfw), encoding='utf-8')
    
    # Write README
    readme_file = extension_dir / "README.md"
    readme_file.write_text(create_readme(name, base_url), encoding='utf-8')
    
    print(f"✓ Created extension: {name}")
    print(f"  Location: {extension_dir}")
    print(f"  Language: {lang}")
    print(f"  Base URL: {base_url}")
    print(f"  Source ID: {source_id}")
    print(f"\nNext steps:")
    print(f"1. Update all TODO comments in {kotlin_file.name}")
    print(f"2. Add icon to main/res/mipmap-* folders (96x96px)")
    print(f"3. Test in Android Studio")
    print(f"4. Update selectors based on website structure")

if __name__ == "__main__":
    main()
