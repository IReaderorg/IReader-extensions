#!/usr/bin/env python3
"""
JavaScript/TypeScript to Kotlin Extension Converter
Converts lnreader-plugins (TypeScript) to IReader extensions (Kotlin)
"""

import re
import json
import sys
from pathlib import Path
from typing import Dict, List, Optional

class JSToKotlinConverter:
    def __init__(self, js_file: Path):
        self.js_file = js_file
        self.content = js_file.read_text(encoding='utf-8')
        self.class_name = ""
        self.plugin_id = ""
        self.plugin_name = ""
        self.site_url = ""
        self.version = ""
        self.lang = ""
        self.icon = ""
        
    def extract_metadata(self):
        """Extract plugin metadata from JS/TS file"""
        # Extract class name
        class_match = re.search(r'class\s+(\w+)\s+implements', self.content)
        if class_match:
            self.class_name = class_match.group(1)
        
        # Extract id
        id_match = re.search(r"id\s*=\s*['\"]([^'\"]+)['\"]", self.content)
        if id_match:
            self.plugin_id = id_match.group(1)
        
        # Extract name
        name_match = re.search(r"name\s*=\s*['\"]([^'\"]+)['\"]", self.content)
        if name_match:
            self.plugin_name = name_match.group(1)
        
        # Extract site
        site_match = re.search(r"site\s*=\s*['\"]([^'\"]+)['\"]", self.content)
        if site_match:
            self.site_url = site_match.group(1).rstrip('/')
        
        # Extract version
        version_match = re.search(r"version\s*=\s*['\"]([^'\"]+)['\"]", self.content)
        if version_match:
            self.version = version_match.group(1)
        
        # Extract icon
        icon_match = re.search(r"icon\s*=\s*['\"]([^'\"]+)['\"]", self.content)
        if icon_match:
            self.icon = icon_match.group(1)
    
    def convert_selectors(self, js_selector: str) -> str:
        """Convert JS selector syntax to Kotlin"""
        # Handle cheerio selectors
        js_selector = js_selector.replace('loadedCheerio(', '').replace(')', '')
        return js_selector
    
    def generate_kotlin_code(self) -> str:
        """Generate Kotlin extension code"""
        package_name = f"ireader.{self.plugin_id.replace('-', '').replace('_', '')}"
        
        kotlin_code = f'''package {package_name}

import io.ktor.client.request.*
import ireader.common.utils.*
import ireader.core.source.*
import ireader.core.source.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import tachiyomix.annotations.Extension

@Extension
abstract class {self.class_name}(private val deps: Dependencies) : ParsedHttpSource(deps) {{

    override val name = "{self.plugin_name}"
    override val id: Long = {self.generate_id()}
    override val baseUrl = "{self.site_url}"
    override val lang = "{self.lang}"

    companion object {{
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    }}

    // MARK: - Filters
    override fun getFilters(): FilterList {{
        return listOf(
            Filter.Title(),
            Filter.Sort(
                "Sort By:",
                arrayOf("Latest", "Popular")
            ),
        )
    }}

    // MARK: - Listings
    override fun getListings(): List<Listing> {{
        return listOf(LatestListing())
    }}

    class LatestListing : Listing("Latest")

    // MARK: - Search & Browse
    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {{
        val query = filters.findInstance<Filter.Title>()?.value
        
        if (!query.isNullOrBlank()) {{
            return getSearch(query, page)
        }}
        
        return getLatest(page)
    }}

    private suspend fun getLatest(page: Int): MangasPageInfo {{
        val url = "$baseUrl/novels/page/$page/"
        return ErrorHandler.safeRequest {{
            bookListParse(
                client.get(requestBuilder(url)).asJsoup(),
                ".book-item", // TODO: Update selector
                ".pagination .next" // TODO: Update selector
            ) {{ bookFromElement(it) }}
        }}.getOrThrow()
    }}

    private suspend fun getSearch(query: String, page: Int): MangasPageInfo {{
        val url = "$baseUrl/search?q=${{query}}&page=$page"
        return ErrorHandler.safeRequest {{
            bookListParse(
                client.get(requestBuilder(url)).asJsoup(),
                ".book-item", // TODO: Update selector
                null
            ) {{ searchFromElement(it) }}
        }}.getOrThrow()
    }}

    // MARK: - Book Parsing
    private fun bookFromElement(element: Element): MangaInfo {{
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
    }}

    private fun searchFromElement(element: Element): MangaInfo {{
        return bookFromElement(element)
    }}

    // MARK: - Details
    override fun detailParse(document: Document): MangaInfo {{
        val title = document.select("h1").text() // TODO: Update selector
        val cover = ImageUrlHelper.extractImageUrl(
            document.select(".cover img").first()!! // TODO: Update selector
        )
        val author = document.select(".author").text() // TODO: Update selector
        val description = document.select(".description").text() // TODO: Update selector
        val genres = document.select(".genre").eachText() // TODO: Update selector
        val status = document.select(".status").text() // TODO: Update selector

        return MangaInfo(
            title = title,
            cover = ImageUrlHelper.normalizeUrl(cover, baseUrl),
            description = description,
            author = author,
            genres = genres,
            status = StatusParser.parseStatus(status),
            key = ""
        )
    }}

    // MARK: - Chapters
    override fun chaptersSelector(): String {{
        return ".chapter-list li" // TODO: Update selector
    }}

    override fun chapterFromElement(element: Element): ChapterInfo {{
        val link = element.select("a").attr("href") // TODO: Update selector
        val name = element.select("a").text() // TODO: Update selector
        val date = element.select(".date").text() // TODO: Update selector

        return ChapterInfo(
            name = name,
            key = link,
            dateUpload = DateParser.parseRelativeOrAbsoluteDate(date)
        )
    }}

    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {{
        return withContext(Dispatchers.IO) {{
            ErrorHandler.safeRequest {{
                chaptersParse(client.get(requestBuilder(manga.key)).asJsoup())
            }}.getOrThrow().reversed()
        }}
    }}

    // MARK: - Content
    override fun pageContentParse(document: Document): List<String> {{
        val paragraphs = document.select(".chapter-content p") // TODO: Update selector
            .eachText()
            .filter {{ it.isNotBlank() }}
        
        return paragraphs
    }}

    override suspend fun getContents(chapter: ChapterInfo): List<String> {{
        return ErrorHandler.safeRequest {{
            pageContentParse(client.get(contentRequest(chapter)).asJsoup())
        }}.getOrThrow()
    }}

    // MARK: - Headers
    override fun HttpRequestBuilder.headersBuilder(block: HeadersBuilder.() -> Unit) {{
        headers {{
            append(HttpHeaders.UserAgent, USER_AGENT)
            append(HttpHeaders.CacheControl, "max-age=0")
            append(HttpHeaders.Referrer, baseUrl)
        }}
    }}
}}
'''
        return kotlin_code
    
    def generate_id(self) -> int:
        """Generate a unique ID for the extension"""
        import hashlib
        key = f"{self.plugin_id}/{self.lang}/1"
        hash_bytes = hashlib.md5(key.encode()).digest()
        id_value = int.from_bytes(hash_bytes[:8], 'big') & 0x7FFFFFFFFFFFFFFF
        return id_value
    
    def generate_build_gradle(self) -> str:
        """Generate build.gradle.kts file"""
        version_code = self.version.replace('.', '')[:2] if self.version else "1"
        
        return f'''listOf("{self.lang}").map {{ lang ->
    Extension(
        name = "{self.class_name}",
        versionCode = {version_code},
        libVersion = "1",
        lang = lang,
        description = "Read novels from {self.plugin_name}",
        nsfw = false,
        icon = DEFAULT_ICON,
    )
}}.also(::register)
'''
    
    def convert(self, output_dir: Path, lang: str):
        """Convert the JS/TS plugin to Kotlin extension"""
        self.lang = lang
        self.extract_metadata()
        
        if not self.class_name or not self.plugin_id:
            print(f"Error: Could not extract metadata from {self.js_file}")
            return False
        
        # Create output directory
        extension_dir = output_dir / lang / self.plugin_id
        extension_dir.mkdir(parents=True, exist_ok=True)
        
        # Create main source directory
        src_dir = extension_dir / "main" / "src" / "ireader" / self.plugin_id.replace('-', '').replace('_', '')
        src_dir.mkdir(parents=True, exist_ok=True)
        
        # Write Kotlin source file
        kotlin_file = src_dir / f"{self.class_name}.kt"
        kotlin_file.write_text(self.generate_kotlin_code(), encoding='utf-8')
        
        # Write build.gradle.kts
        build_file = extension_dir / "build.gradle.kts"
        build_file.write_text(self.generate_build_gradle(), encoding='utf-8')
        
        print(f"✓ Converted {self.plugin_id} to {extension_dir}")
        print(f"  Note: Please review and update the TODO comments in the generated code")
        print(f"  Selectors need to be manually verified against the website")
        
        return True

def main():
    if len(sys.argv) < 3:
        print("Usage: python js-to-kotlin-converter.py <js_file> <lang> [output_dir]")
        print("Example: python js-to-kotlin-converter.py novelbuddy.ts en ./sources")
        sys.exit(1)
    
    js_file = Path(sys.argv[1])
    lang = sys.argv[2]
    output_dir = Path(sys.argv[3]) if len(sys.argv) > 3 else Path("./sources")
    
    if not js_file.exists():
        print(f"Error: File {js_file} not found")
        sys.exit(1)
    
    converter = JSToKotlinConverter(js_file)
    success = converter.convert(output_dir, lang)
    
    if success:
        print("\n✓ Conversion complete!")
        print("Next steps:")
        print("1. Review the generated code and update TODO comments")
        print("2. Test the extension in Android Studio")
        print("3. Update selectors based on the actual website structure")
    else:
        print("\n✗ Conversion failed")
        sys.exit(1)

if __name__ == "__main__":
    main()
