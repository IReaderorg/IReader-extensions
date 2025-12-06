#!/usr/bin/env python3
"""
IReader Source Creator
Uses KSP annotations for minimal boilerplate.

Usage: python scripts/add-source.py
"""

import sys
import hashlib
import re
from pathlib import Path

def generate_id(name: str, lang: str) -> int:
    key = f"{name.lower()}/{lang}/1"
    h = hashlib.md5(key.encode()).digest()
    return int.from_bytes(h[:8], 'big') & 0x7FFFFFFFFFFFFFFF

def clean_name(name: str) -> str:
    return ''.join(word.capitalize() for word in re.sub(r'[^a-zA-Z0-9\s]', '', name).split())

def main():
    print("\n=== IReader Source Creator ===\n")
    
    # 1. Name
    name = input("Source name (e.g. NovelFull): ").strip()
    if not name:
        print("Error: Name required")
        return
    
    # 2. URL  
    url = input("Website URL (e.g. https://novelfull.com): ").strip()
    if not url:
        print("Error: URL required")
        return
    if not url.startswith("http"):
        url = "https://" + url
    url = url.rstrip("/")
    
    # 3. Language
    lang = input("Language code [en]: ").strip().lower() or "en"
    
    # 4. Is it a Madara site?
    print("\nIs this a Madara/WordPress theme site?")
    print("(URLs like /novel/name/chapter-1/, has /wp-admin/)")
    is_madara = input("Madara site? [y/N]: ").strip().lower() == 'y'
    
    # Generate values
    class_name = clean_name(name)
    package = class_name.lower()
    source_id = generate_id(class_name, lang)
    
    # Create directories
    base = Path("sources") / lang / package
    src = base / "main" / "src" / "ireader" / package
    assets = base / "main" / "assets"
    src.mkdir(parents=True, exist_ok=True)
    assets.mkdir(parents=True, exist_ok=True)
    
    if is_madara:
        # Zero-code Madara source
        source_code = f'''package ireader.{package}

import tachiyomix.annotations.MadaraSource

@MadaraSource(
    name = "{class_name}",
    baseUrl = "{url}",
    lang = "{lang}",
    id = {source_id}L
)
object {class_name}Config
'''
    else:
        # SourceFactory with KSP annotations
        source_code = f'''package ireader.{package}

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

    // NOVEL LIST - Update selectors to match {url}
    override val exploreFetchers = listOf(
        BaseExploreFetcher(
            "Latest",
            endpoint = "/novels/page/{{page}}/",  // URL pattern
            selector = ".novel-item",            // Each novel card
            nameSelector = ".title",             // Novel title
            coverSelector = "img",               // Cover image
            coverAtt = "src",
            linkSelector = "a",
            linkAtt = "href"
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
            type = SourceFactory.Type.Search
        )
    )

    // NOVEL DETAILS
    override val detailFetcher = SourceFactory.Detail(
        nameSelector = "h1",
        coverSelector = ".cover img",
        coverAtt = "src",
        descriptionSelector = ".description",
        authorBookSelector = ".author",
        categorySelector = ".genres a"
    )

    // CHAPTERS
    override val chapterFetcher = SourceFactory.Chapters(
        selector = ".chapter-list li",
        nameSelector = "a",
        linkSelector = "a",
        linkAtt = "href",
        reverseChapterList = true
    )

    // CONTENT
    override val contentFetcher = SourceFactory.Content(
        pageContentSelector = ".chapter-content p"
    )
}}
'''
    
    # Write source file
    (src / f"{class_name}.kt").write_text(source_code, encoding='utf-8')
    
    # Write build.gradle.kts
    (base / "build.gradle.kts").write_text(f'''listOf("{lang}").map {{ lang ->
    Extension(
        name = "{class_name}",
        versionCode = 1,
        libVersion = "2",
        lang = lang,
        description = "",
        nsfw = false,
        icon = DEFAULT_ICON
    )
}}.also(::register)
''', encoding='utf-8')
    
    print(f"\nCreated: sources/{lang}/{package}/")
    print(f"Source ID: {source_id}")
    
    if is_madara:
        print(f"\nMadara source created - no code needed!")
        print("If it doesn't work, the site may use custom paths.")
    else:
        print(f"\nNext: Update CSS selectors in {class_name}.kt")
    
    print(f"\nBuild: ./gradlew :sources:{lang}:{package}:assembleDebug")

if __name__ == "__main__":
    main()
