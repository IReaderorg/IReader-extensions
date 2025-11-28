#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
IReader Source Creator - Interactive Mode
Generates IReader extension from JSON configuration (exported from Chrome Extension)
"""

import sys
import io
import os
import json
import urllib.request
import argparse
from pathlib import Path
from typing import Dict, Optional
import hashlib

# Fix Windows console encoding
if sys.platform == 'win32':
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')


class InteractiveSourceGenerator:
    def __init__(self):
        self.api_key = os.getenv('GEMINI_API_KEY')
        if not self.api_key:
            print("[ERROR] GEMINI_API_KEY required!")
            print("   Set it with: $env:GEMINI_API_KEY='your-key'")
            sys.exit(1)

    def generate(self, config_path: Path, output_dir: Path):
        print("=" * 60)
        print(" IReader Source Generator ".center(60))
        print("=" * 60)
        print()

        print(f"Reading configuration from {config_path}...")
        try:
            with open(config_path, 'r', encoding='utf-8') as f:
                config = json.load(f)
        except Exception as e:
            print(f"[ERROR] Failed to read config file: {e}")
            return False

        name = config.get('name', 'UnknownSource')
        lang = config.get('lang', 'en')
        base_url = config.get('baseUrl', '').rstrip('/')
        selectors = config.get('selectors', {})

        # URL patterns
        latest_url = config.get('latestUrl', '/latest?page={{page}}')
        popular_url = config.get('popularUrl', '/popular?page={{page}}')
        search_url = config.get('searchUrl', '/search?q={{query}}&page={{page}}')
        
        # API configuration (optional)
        api_config = {
            'chaptersApiUrl': config.get('chaptersApiUrl', ''),
            'chaptersJsonPath': config.get('chaptersJsonPath', ''),
            'chapterNameField': config.get('chapterNameField', ''),
            'chapterUrlField': config.get('chapterUrlField', ''),
            'contentApiUrl': config.get('contentApiUrl', ''),
            'contentJsonPath': config.get('contentJsonPath', '')
        }
        has_api = any(api_config.values())

        print(f"   + Source: {name}")
        print(f"   + Language: {lang}")
        print(f"   + Base URL: {base_url}")
        print(f"   + Selectors: {len(selectors)} found")
        if has_api:
            print(f"   + API Config: Yes (chapters via API)")
        print()

        # Show selectors
        print("Selectors:")
        for key, value in selectors.items():
            print(f"   - {key}: {value[:50]}{'...' if len(value) > 50 else ''}")
        print()

        # Generate Kotlin Code
        print("Generating Kotlin code with Gemini...")
        kotlin_code = self._generate_kotlin(name, lang, base_url, selectors, latest_url, popular_url, search_url, api_config if has_api else None)

        if not kotlin_code:
            print("[ERROR] Failed to generate Kotlin code")
            return False

        print(f"   + Generated {len(kotlin_code)} characters")
        print()

        # Post-process the code
        kotlin_code = self._post_process(kotlin_code, name, lang, base_url, selectors)

        # Write files
        self._write_files(output_dir, name, lang, base_url, kotlin_code)

        print()
        print("[SUCCESS] Source generated successfully!")
        print()
        print("Next steps:")
        print(f"   1. Review: {output_dir}/{lang}/{name.lower().replace(' ', '')}/")
        print(f"   2. Build: ./gradlew :extensions:v5:{lang}:{name.lower().replace(' ', '')}:assembleDebug")
        print(f"   3. Test in IReader")
        print()

        return True

    def _generate_kotlin(self, name: str, lang: str, base_url: str, selectors: Dict[str, str],
                         latest_url: str, popular_url: str, search_url: str, api_config: Optional[Dict] = None) -> Optional[str]:
        # Try AI generation first
        prompt = self._create_prompt(name, lang, base_url, selectors, latest_url, popular_url, search_url, api_config)
        response = self._call_gemini(prompt)
        if response:
            code = self._extract_kotlin_code(response)
            if code and 'abstract class' in code:
                return code
        
        # Fallback to direct generation
        print("   [!] AI generation incomplete, using template...")
        return self._generate_kotlin_direct(name, lang, base_url, selectors, latest_url, popular_url, search_url, api_config)

    def _create_prompt(self, name: str, lang: str, base_url: str, selectors: Dict[str, str],
                       latest_url: str, popular_url: str, search_url: str, api_config: Optional[Dict] = None) -> str:
        # Calculate ID
        safe_name = name.lower().replace(' ', '').replace('-', '').replace('_', '')
        key = f"{safe_name}/{lang}/1"
        hash_bytes = hashlib.md5(key.encode()).digest()
        generated_id = int.from_bytes(hash_bytes[:8], 'big') & 0x7FFFFFFFFFFFFFFF

        package_name = f"ireader.{safe_name}"
        class_name = ''.join(word.capitalize() for word in name.replace('-', ' ').replace('_', ' ').split())

        selectors_str = json.dumps(selectors, indent=2)

        # Build the code directly instead of relying on AI for structure
        return f"""Create a Kotlin IReader extension using SourceFactory pattern.

CONFIGURATION:
- Name: {name}
- Language: {lang}
- Base URL: {base_url}
- ID: {generated_id}L
- Package: {package_name}
- Class: {class_name}

URL PATTERNS:
- Latest: {latest_url}
- Popular: {popular_url}
- Search: {search_url}

CSS SELECTORS (from user selection):
{selectors_str}

SELECTOR MAPPING:
- title → detailFetcher.nameSelector
- author → detailFetcher.authorBookSelector
- description → detailFetcher.descriptionSelector
- cover → detailFetcher.coverSelector (use coverAtt="src" or "data-src")
- status → detailFetcher.statusSelector
- genres → detailFetcher.categorySelector
- chapter-item → chapterFetcher.selector
- chapter-name → chapterFetcher.nameSelector
- chapter-link → chapterFetcher.linkSelector (use linkAtt="href")
- content → contentFetcher.pageContentSelector
- novel-item → exploreFetchers.selector
- explore-title → exploreFetchers.nameSelector
- explore-cover → exploreFetchers.coverSelector
- explore-link → exploreFetchers.linkSelector

KOTLIN TEMPLATE:
```kotlin
package {package_name}

import io.ktor.client.request.*
import io.ktor.client.statement.*
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
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
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import tachiyomix.annotations.Extension

@Extension
abstract class {class_name}(deps: Dependencies) : SourceFactory(deps = deps) {{

    override val lang: String get() = "{lang}"
    override val baseUrl: String get() = "{base_url}"
    override val id: Long get() = {generated_id}L
    override val name: String get() = "{name}"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Sort By:",
            arrayOf(
                "Popular",
                "Latest",
            )
        ),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Popular",
                endpoint = "{popular_url}",
                selector = "{selectors.get('novel-item', '.novel-item')}",
                nameSelector = "{selectors.get('explore-title', '.title')}",
                linkSelector = "{selectors.get('explore-link', 'a')}",
                linkAtt = "href",
                coverSelector = "{selectors.get('explore-cover', 'img')}",
                coverAtt = "src",
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = true,
            ),
            BaseExploreFetcher(
                "Latest",
                endpoint = "{latest_url}",
                selector = "{selectors.get('novel-item', '.novel-item')}",
                nameSelector = "{selectors.get('explore-title', '.title')}",
                linkSelector = "{selectors.get('explore-link', 'a')}",
                linkAtt = "href",
                coverSelector = "{selectors.get('explore-cover', 'img')}",
                coverAtt = "src",
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = true,
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "{search_url}",
                selector = "{selectors.get('novel-item', '.novel-item')}",
                nameSelector = "{selectors.get('explore-title', '.title')}",
                linkSelector = "{selectors.get('explore-link', 'a')}",
                linkAtt = "href",
                coverSelector = "{selectors.get('explore-cover', 'img')}",
                coverAtt = "src",
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = true,
                type = SourceFactory.Type.Search,
            ),
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "{selectors.get('title', '.title')}",
            coverSelector = "{selectors.get('cover', '.cover img')}",
            coverAtt = "src",
            descriptionSelector = "{selectors.get('description', '.description')}",
            authorBookSelector = "{selectors.get('author', '.author')}",
            statusSelector = "{selectors.get('status', '.status')}",
            categorySelector = "{selectors.get('genres', '.genres')}",
            addBaseurlToCoverLink = true,
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "{selectors.get('chapter-item', '.chapter-item')}",
            nameSelector = "{selectors.get('chapter-name', '.chapter-name')}",
            linkSelector = "{selectors.get('chapter-link', 'a')}",
            linkAtt = "href",
            addBaseUrlToLink = true,
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = "{selectors.get('content', '.content')}",
        )

    override fun pageContentParse(document: Document): List<Page> {{
        val contentSelector = "{selectors.get('content', '.content')}"
        
        // Remove common bloat elements
        document.select("script, style, .ads, .advertisement, .social-share, .comments").remove()
        
        // Select paragraphs from content
        val content = document.select("$contentSelector p, $contentSelector br").mapNotNull {{ element ->
            val text = if (element.tagName() == "br") "\\n" else element.text().trim()
            if (text.isNotEmpty()) Text(text) else null
        }}
        
        // Fallback: if no paragraphs, get all text
        return content.ifEmpty {{
            val text = document.select(contentSelector).text()
            if (text.isNotBlank()) listOf(Text(text)) else emptyList()
        }}
    }}
}}
```

IMPORTANT RULES:
1. Use the EXACT selectors provided - don't modify them
2. Always add `addBaseUrlToLink = true` and `addBaseurlToCoverLink = true`
3. Use `coverAtt = "src"` for images (or "data-src" if the selector suggests lazy loading)
4. The pageContentParse should handle both <p> tags and <br> tags
5. Include fallback for content parsing

Return ONLY the Kotlin code, no explanations."""

    def _call_gemini(self, prompt: str) -> Optional[str]:
        try:
            url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
            payload = {
                "contents": [{"parts": [{"text": prompt}]}],
                "generationConfig": {
                    "temperature": 0.1,
                    "maxOutputTokens": 8192,
                }
            }
            data = json.dumps(payload).encode('utf-8')
            req = urllib.request.Request(
                url,
                data=data,
                headers={
                    'Content-Type': 'application/json',
                    'X-goog-api-key': self.api_key
                }
            )
            with urllib.request.urlopen(req, timeout=90) as response:
                result = json.loads(response.read().decode('utf-8'))
                if 'candidates' in result and len(result['candidates']) > 0:
                    return result['candidates'][0]['content']['parts'][0]['text']
        except Exception as e:
            print(f"   [!] Gemini API Error: {e}")
        return None

    def _extract_kotlin_code(self, response: str) -> str:
        text = response.strip()
        if '```kotlin' in text:
            start = text.find('```kotlin') + 9
            end = text.find('```', start)
            if end > start:
                return text[start:end].strip()
        elif '```' in text:
            start = text.find('```') + 3
            end = text.find('```', start)
            if end > start:
                return text[start:end].strip()
        return text.strip()

    def _generate_kotlin_direct(self, name: str, lang: str, base_url: str, selectors: Dict[str, str],
                                latest_url: str, popular_url: str, search_url: str, api_config: Optional[Dict] = None) -> str:
        """Generate Kotlin code directly without AI - fallback method"""
        safe_name = name.lower().replace(' ', '').replace('-', '').replace('_', '')
        key = f"{safe_name}/{lang}/1"
        hash_bytes = hashlib.md5(key.encode()).digest()
        generated_id = int.from_bytes(hash_bytes[:8], 'big') & 0x7FFFFFFFFFFFFFFF

        package_name = f"ireader.{safe_name}"
        class_name = ''.join(word.capitalize() for word in name.replace('-', ' ').replace('_', ' ').split())

        # Get selectors with defaults
        s = lambda k, d: selectors.get(k, d)

        return f'''package {package_name}

import io.ktor.client.request.*
import io.ktor.client.statement.*
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import tachiyomix.annotations.Extension

@Extension
abstract class {class_name}(deps: Dependencies) : SourceFactory(deps = deps) {{

    override val lang: String get() = "{lang}"
    override val baseUrl: String get() = "{base_url}"
    override val id: Long get() = {generated_id}L
    override val name: String get() = "{name}"

    override fun getFilters(): FilterList = listOf(
        Filter.Title(),
        Filter.Sort(
            "Sort By:",
            arrayOf(
                "Popular",
                "Latest",
            )
        ),
    )

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Popular",
                endpoint = "{popular_url}",
                selector = "{s('novel-item', '.novel-item')}",
                nameSelector = "{s('explore-title', '.title')}",
                linkSelector = "{s('explore-link', 'a')}",
                linkAtt = "href",
                coverSelector = "{s('explore-cover', 'img')}",
                coverAtt = "src",
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = true,
            ),
            BaseExploreFetcher(
                "Latest",
                endpoint = "{latest_url}",
                selector = "{s('novel-item', '.novel-item')}",
                nameSelector = "{s('explore-title', '.title')}",
                linkSelector = "{s('explore-link', 'a')}",
                linkAtt = "href",
                coverSelector = "{s('explore-cover', 'img')}",
                coverAtt = "src",
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = true,
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "{search_url}",
                selector = "{s('novel-item', '.novel-item')}",
                nameSelector = "{s('explore-title', '.title')}",
                linkSelector = "{s('explore-link', 'a')}",
                linkAtt = "href",
                coverSelector = "{s('explore-cover', 'img')}",
                coverAtt = "src",
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = true,
                type = SourceFactory.Type.Search,
            ),
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "{s('title', '.title')}",
            coverSelector = "{s('cover', '.cover img')}",
            coverAtt = "src",
            descriptionSelector = "{s('description', '.description')}",
            authorBookSelector = "{s('author', '.author')}",
            statusSelector = "{s('status', '.status')}",
            categorySelector = "{s('genres', '.genres')}",
            addBaseurlToCoverLink = true,
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "{s('chapter-item', '.chapter-item')}",
            nameSelector = "{s('chapter-name', 'a')}",
            linkSelector = "{s('chapter-link', 'a')}",
            linkAtt = "href",
            addBaseUrlToLink = true,
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = "{s('content', '.content')}",
        )

    override fun pageContentParse(document: Document): List<Page> {{
        val contentSelector = "{s('content', '.content')}"
        
        // Remove common bloat elements
        document.select("script, style, .ads, .advertisement, .social-share, .comments, .hidden").remove()
        
        // Select paragraphs from content
        val content = document.select("$contentSelector p, $contentSelector br").mapNotNull {{ element ->
            val text = if (element.tagName() == "br") "\\n" else element.text().trim()
            if (text.isNotEmpty()) Text(text) else null
        }}
        
        // Fallback: if no paragraphs, get all text
        return content.ifEmpty {{
            val text = document.select(contentSelector).text()
            if (text.isNotBlank()) listOf(Text(text)) else emptyList()
        }}
    }}
}}
'''

    def _post_process(self, code: str, name: str, lang: str, base_url: str, selectors: Dict[str, str]) -> str:
        """Post-process generated code to fix common issues"""
        import re

        # Ensure abstract class
        if 'abstract class' not in code:
            code = re.sub(r'\bclass\s+(\w+)\s*\(', r'abstract class \1(', code)

        # Fix .asText() -> .bodyAsText()
        code = code.replace('.asText()', '.bodyAsText()')

        # Remove invalid Type references
        code = re.sub(r',?\s*type\s*=\s*SourceFactory\.Type\.Latest\s*,?', '', code)
        code = re.sub(r',?\s*type\s*=\s*SourceFactory\.Type\.Popular\s*,?', '', code)

        # Clean up double commas
        code = re.sub(r',\s*,', ',', code)
        code = re.sub(r',\s*\)', ')', code)

        # Ensure required imports
        required_imports = [
            'import io.ktor.client.request.*',
            'import io.ktor.client.statement.*',
            'import ireader.core.source.Dependencies',
            'import ireader.core.source.SourceFactory',
            'import ireader.core.source.asJsoup',
            'import ireader.core.source.findInstance',
            'import ireader.core.source.model.ChapterInfo',
            'import ireader.core.source.model.Command',
            'import ireader.core.source.model.CommandList',
            'import ireader.core.source.model.Filter',
            'import ireader.core.source.model.FilterList',
            'import ireader.core.source.model.Listing',
            'import ireader.core.source.model.MangaInfo',
            'import ireader.core.source.model.MangasPageInfo',
            'import ireader.core.source.model.Page',
            'import ireader.core.source.model.Text',
            'import org.jsoup.Jsoup',
            'import org.jsoup.nodes.Document',
            'import tachiyomix.annotations.Extension',
        ]

        for imp in required_imports:
            if imp not in code:
                # Add after package line
                code = re.sub(r'(package\s+[\w.]+\s*\n)', f'\\1\n{imp}', code, count=1)

        return code

    def _write_files(self, output_dir: Path, name: str, lang: str, base_url: str, kotlin_code: str):
        safe_name = name.lower().replace(' ', '')
        class_name = ''.join(word.capitalize() for word in name.split())

        # Directory structure
        extension_dir = output_dir / lang / safe_name
        src_dir = extension_dir / "main" / "src" / "ireader" / safe_name
        assets_dir = extension_dir / "main" / "assets"

        src_dir.mkdir(parents=True, exist_ok=True)
        assets_dir.mkdir(parents=True, exist_ok=True)

        # Write Kotlin file
        kt_file = src_dir / f"{class_name}.kt"
        kt_file.write_text(kotlin_code, encoding='utf-8')
        print(f"   + Created: {kt_file}")

        # Write build.gradle.kts
        build_content = f'''listOf("{lang}").map {{ lang ->
    Extension(
        name = "{name}",
        versionCode = 1,
        libVersion = "1",
        lang = lang,
        description = "Read novels from {name}",
        nsfw = false,
        icon = DEFAULT_ICON,
        assetsDir = "{lang}/{safe_name}/main/assets",
    )
}}.also(::register)
'''
        build_file = extension_dir / "build.gradle.kts"
        build_file.write_text(build_content, encoding='utf-8')
        print(f"   + Created: {build_file}")

        # Write README
        readme_content = f'''# {name}

**Source**: {base_url}
**Language**: {lang}
**Generated by**: IReader Source Creator

## Status
[OK] Generated
[!] Needs testing

## Build
```bash
./gradlew :extensions:v5:{lang}:{safe_name}:assembleDebug
```
'''
        readme_file = extension_dir / "README.md"
        readme_file.write_text(readme_content, encoding='utf-8')
        print(f"   + Created: {readme_file}")


def main():
    parser = argparse.ArgumentParser(
        description='Generate IReader source from JSON config',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog='''
Examples:
  python create_source_interactive.py realmnovel_config.json
  python create_source_interactive.py config.json --output sources-v5-batch

The JSON config should be exported from the IReader Source Creator Chrome extension.
        '''
    )
    parser.add_argument('input', type=Path, help='Input JSON configuration file')
    parser.add_argument('--output', type=Path, default=Path('./sources-v5-batch'),
                        help='Output directory (default: ./sources-v5-batch)')

    args = parser.parse_args()

    if not args.input.exists():
        print(f"[ERROR] Input file not found: {args.input}")
        sys.exit(1)

    generator = InteractiveSourceGenerator()
    success = generator.generate(args.input, args.output)
    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
