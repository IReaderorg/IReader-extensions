"""
AI-Powered Code Generator V2
Enhanced version with better prompts, validation, and post-processing
for higher conversion success rates
"""

import os
import re
import json
import urllib.request
from typing import Dict, Optional, List, Tuple
import hashlib

class AICodeGeneratorV2:
    """Enhanced AI code generator with validation and fixes"""
    
    def __init__(self):
        self.api_key = os.getenv('GEMINI_API_KEY')
        self.validation_errors = []
    
    def generate_kotlin_from_typescript(self, ts_code: str, metadata: Dict, lang: str) -> Optional[str]:
        """Generate Kotlin code with validation and auto-fixes"""
        if not self.api_key:
            print("   ⚠ GEMINI_API_KEY not set")
            return None
        
        # Step 1: Analyze TypeScript to extract key information
        analysis = self._analyze_typescript(ts_code)
        
        # Step 2: Create optimized prompt
        prompt = self._create_optimized_prompt(ts_code, metadata, lang, analysis)
        
        # Step 3: Call AI
        print("   🤖 Calling Gemini API...")
        response = self._call_gemini(prompt)
        if not response:
            return None
        
        # Step 4: Extract and validate code
        kotlin_code = self._extract_kotlin_code(response)
        
        # Step 5: Apply comprehensive post-processing
        kotlin_code = self._post_process_code(kotlin_code, metadata, lang, analysis)
        
        # Step 6: Validate and fix common issues
        kotlin_code, issues = self._validate_and_fix(kotlin_code, analysis)
        
        if issues:
            print(f"   ⚠ Fixed {len(issues)} issues:")
            for issue in issues[:3]:
                print(f"      - {issue}")
        
        return kotlin_code
    
    def _analyze_typescript(self, ts_code: str) -> Dict:
        """Analyze TypeScript code to extract key patterns"""
        analysis = {
            'has_pagination': False,
            'has_api_chapters': False,
            'has_json_api': False,
            'selectors': {},
            'endpoints': {},
            'content_selector': None,
            'chapter_selector': None,
            'novel_selector': None,
            'uses_data_src': False,
            'uses_title_attr': False,
            'has_rate_limiting': False,
        }
        
        # Detect pagination
        if 'page=' in ts_code or 'pageNo' in ts_code or '?page=' in ts_code:
            analysis['has_pagination'] = True
        
        # Detect API-based chapters
        api_patterns = [
            r'/chapters\?page=',
            r'api/.*chapters',
            r'chapters\?.*page',
            r'parseChapters.*pages',
        ]
        for pattern in api_patterns:
            if re.search(pattern, ts_code, re.IGNORECASE):
                analysis['has_api_chapters'] = True
                break
        
        # Detect JSON API usage
        if '.json()' in ts_code or 'r.json()' in ts_code:
            analysis['has_json_api'] = True
        
        # Detect rate limiting
        if 'rate limit' in ts_code.lower() or 'throttl' in ts_code.lower():
            analysis['has_rate_limiting'] = True
        
        # Extract novel list selector
        novel_patterns = [
            r"loadedCheerio\(['\"]([^'\"]+)['\"]\)\.map",
            r"loadedCheerio\(['\"]([^'\"]+)['\"]\)\.each",
            r"\$\(['\"]([^'\"]+)['\"]\)\.map",
            r"\.select\(['\"]([^'\"]+)['\"]\)\.map",
        ]
        for pattern in novel_patterns:
            match = re.search(pattern, ts_code)
            if match:
                analysis['novel_selector'] = match.group(1)
                break
        
        # Extract content selector
        content_patterns = [
            r"loadedCheerio\(['\"]([^'\"]+)['\"]\)\.html\(\)",
            r"\.select\(['\"]([^'\"]+)['\"]\)\.html\(\)",
            r"\$\(['\"]#content['\"]\)",
            r"getElementById\(['\"]content['\"]\)",
        ]
        for pattern in content_patterns:
            match = re.search(pattern, ts_code)
            if match:
                analysis['content_selector'] = match.group(1)
                break
        
        # Check for data-src usage
        if 'data-src' in ts_code:
            analysis['uses_data_src'] = True
        
        # Check for title attribute
        if ".attr('title')" in ts_code or '.attr("title")' in ts_code:
            analysis['uses_title_attr'] = True
        
        # Extract chapter list selector
        chapter_patterns = [
            r"\.chapter-list[^'\"]*",
            r"loadedCheerio\(['\"]([^'\"]*chapter[^'\"]*li)['\"]\)",
            r"\.select\(['\"]([^'\"]*chapter[^'\"]*li)['\"]\)",
        ]
        for pattern in chapter_patterns:
            match = re.search(pattern, ts_code, re.IGNORECASE)
            if match:
                if match.groups():
                    analysis['chapter_selector'] = match.group(1)
                else:
                    analysis['chapter_selector'] = match.group(0)
                break
        
        return analysis
    
    def _create_optimized_prompt(self, ts_code: str, metadata: Dict, lang: str, analysis: Dict) -> str:
        """Create an optimized prompt based on analysis"""
        
        # Calculate ID
        key = f"{metadata['id']}/{lang}/1"
        hash_bytes = hashlib.md5(key.encode()).digest()
        generated_id = int.from_bytes(hash_bytes[:8], 'big') & 0x7FFFFFFFFFFFFFFF
        
        package_name = f"ireader.{metadata['id'].replace('-', '').replace('_', '')}"
        class_name = ''.join(word.capitalize() for word in metadata['id'].replace('-', ' ').replace('_', ' ').split())
        
        # Build context based on analysis
        context_notes = []
        if analysis['has_api_chapters']:
            context_notes.append("⚠️ This plugin uses PAGINATED CHAPTER FETCHING - you MUST override getChapterList()")
        if analysis['has_json_api']:
            context_notes.append("⚠️ This plugin uses JSON APIs - include @Serializable data classes")
        if analysis['has_rate_limiting']:
            context_notes.append("⚠️ This plugin has rate limiting - consider adding delays")
        if analysis['uses_data_src']:
            context_notes.append("✓ Uses data-src for images - use coverAtt = \"data-src\"")
        if analysis['uses_title_attr']:
            context_notes.append("✓ Uses title attribute for names - use nameAtt = \"title\"")
        
        context_str = "\n".join(context_notes) if context_notes else "Standard HTML scraping plugin"
        
        return f"""Convert this TypeScript novel reader plugin to Kotlin for IReader.

ANALYSIS RESULTS:
{context_str}

TYPESCRIPT CODE:
```typescript
{ts_code[:15000]}
```

REQUIREMENTS:
- Package: {package_name}
- Class: {class_name} (MUST be "abstract class")
- Extends: SourceFactory(deps = deps)
- Plugin ID: {generated_id}L
- Base URL: {metadata['site'].rstrip('/')}
- Language: {lang}
- Name: {metadata['name']}

CRITICAL RULES:

1. **ALWAYS USE SOURCEFACTORY PATTERN** - NOT ParsedHttpSource

2. **EXTRACT EXACT SELECTORS FROM TYPESCRIPT**:
   - Novel list: Look for `.novel-item`, `.book-item`, etc.
   - Title: Look for `.novel-title`, `.title`, etc.
   - Cover: Look for `img` with `data-src` or `src`
   - Content: Look for `#content`, `.chapter-content`, etc.

3. **FOR PAGINATED CHAPTERS** (like this plugin):
   - Override `getChapterList()` method
   - Fetch chapters page by page
   - Handle the pagination URL pattern from TypeScript

4. **URL HANDLING**:
   - ALWAYS use absolute URLs for manga.key and chapter.key
   - Use `addBaseUrlToLink = true` in fetchers
   - Pattern: `key = "$baseUrl/${{path}}"`

5. **CONTENT PARSING**:
   - Override `pageContentParse()`
   - Remove ads/bloat elements
   - Return `listOf(Text(content.html()))`

KOTLIN TEMPLATE:
```kotlin
package {package_name}

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
    override val baseUrl: String get() = "{metadata['site'].rstrip('/')}"
    override val id: Long get() = {generated_id}L
    override val name: String get() = "{metadata['name']}"

    override fun getFilters(): FilterList = listOf(Filter.Title())

    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    // EXTRACT SELECTORS FROM TYPESCRIPT - DO NOT GUESS!
    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Popular",
                endpoint = "/search-adv?sort=rank-top&page={{{{page}}}}",
                selector = ".novel-item",
                nameSelector = ".novel-title > a",
                nameAtt = "title",
                linkSelector = ".novel-title > a",
                linkAtt = "href",
                coverSelector = ".novel-cover > img",
                coverAtt = "data-src",
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = true,
            ),
            BaseExploreFetcher(
                "Latest",
                endpoint = "/search-adv?sort=date&page={{{{page}}}}",
                selector = ".novel-item",
                nameSelector = ".novel-title > a",
                nameAtt = "title",
                linkSelector = ".novel-title > a",
                linkAtt = "href",
                coverSelector = ".novel-cover > img",
                coverAtt = "data-src",
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = true,
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/search?keyword={{{{query}}}}&page={{{{page}}}}",
                selector = ".novel-list.chapters .novel-item",
                nameSelector = "a",
                nameAtt = "title",
                linkSelector = "a",
                linkAtt = "href",
                coverSelector = ".novel-cover > img",
                coverAtt = "src",
                addBaseUrlToLink = true,
                addBaseurlToCoverLink = true,
                type = SourceFactory.Type.Search,
            ),
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = ".novel-title",
            coverSelector = ".cover > img",
            coverAtt = "data-src",
            descriptionSelector = ".summary .content",
            authorBookSelector = ".author .property-item > span",
            categorySelector = ".categories .property-item",
        )

    // For paginated chapters, override getChapterList
    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {{
        val novelPath = manga.key.removePrefix(baseUrl).removePrefix("/")
        val doc = client.get(requestBuilder(manga.key)).asJsoup()
        
        // Get total chapters to calculate pages
        val totalChaptersText = doc.selectFirst(".header-stats .icon-book-open")?.parent()?.text() ?: "0"
        val totalChapters = Regex("\\\\d+").find(totalChaptersText)?.value?.toIntOrNull() ?: 0
        val totalPages = (totalChapters + 99) / 100  // Ceiling division
        
        val chapters = mutableListOf<ChapterInfo>()
        
        for (page in 1..totalPages) {{
            try {{
                val chaptersUrl = "$baseUrl/$novelPath/chapters?page=$page"
                val chaptersDoc = client.get(requestBuilder(chaptersUrl)).asJsoup()
                
                chaptersDoc.select(".chapter-list li").forEach {{ element ->
                    val link = element.selectFirst("a")
                    val name = link?.attr("title")?.takeIf {{ it.isNotBlank() }} ?: link?.text() ?: return@forEach
                    val href = link?.attr("href") ?: return@forEach
                    
                    val chapterKey = if (href.startsWith("http")) href else "$baseUrl$href"
                    chapters.add(ChapterInfo(name = name.trim(), key = chapterKey))
                }}
            }} catch (e: Exception) {{
                // Continue on error
            }}
        }}
        
        return chapters
    }}

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = ".chapter-list li",
            nameSelector = "a",
            nameAtt = "title",
            linkSelector = "a",
            linkAtt = "href",
            addBaseUrlToLink = true,
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageContentSelector = "#content",
        )

    override fun pageContentParse(document: Document): List<Page> {{
        // Remove bloat elements
        document.select(".box-ads, .box-notification, [class^=nf]").remove()
        
        val content = document.selectFirst("#content") ?: return emptyList()
        return listOf(Text(content.html()))
    }}
}}
```

IMPORTANT:
1. Extract the EXACT selectors from the TypeScript code
2. The chapter pagination pattern is: `${{novelPath}}/chapters?page=${{page}}`
3. Use `nameAtt = "title"` when TypeScript uses `.attr('title')`
4. Use `coverAtt = "data-src"` when TypeScript uses `.attr('data-src')`
5. ALWAYS add `addBaseUrlToLink = true` and `addBaseurlToCoverLink = true`

OUTPUT ONLY THE KOTLIN CODE, NO EXPLANATIONS."""

    def _call_gemini(self, prompt: str) -> Optional[str]:
        """Call Gemini API with retry logic"""
        max_retries = 2
        
        for attempt in range(max_retries):
            try:
                url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
                
                payload = {
                    "contents": [{"parts": [{"text": prompt}]}],
                    "generationConfig": {
                        "temperature": 0.1,  # Lower temperature for more consistent output
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
                        text = result['candidates'][0]['content']['parts'][0]['text']
                        return text
                        
            except Exception as e:
                print(f"   ⚠ Gemini API Error (attempt {attempt + 1}): {e}")
                if attempt < max_retries - 1:
                    import time
                    time.sleep(2)
        
        return None
    
    def _extract_kotlin_code(self, response: str) -> str:
        """Extract Kotlin code from AI response"""
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
    
    def _post_process_code(self, code: str, metadata: Dict, lang: str, analysis: Dict) -> str:
        """Apply comprehensive post-processing fixes"""
        
        # Fix 1: Ensure abstract class
        if 'abstract class' not in code:
            code = re.sub(r'\bclass\s+(\w+)\s*\(', r'abstract class \1(', code)
        
        # Fix 2: Replace .asText() with .bodyAsText()
        code = code.replace('.asText()', '.bodyAsText()')
        
        # Fix 3: Fix double braces in endpoints (common AI mistake)
        code = re.sub(r'\{\{\{\{page\}\}\}\}', '{{page}}', code)
        code = re.sub(r'\{\{\{\{query\}\}\}\}', '{{query}}', code)
        
        # Fix 4: Ensure all required imports
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
            'import kotlinx.serialization.Serializable',
            'import kotlinx.serialization.json.Json',
            'import org.jsoup.Jsoup',
            'import org.jsoup.nodes.Document',
            'import tachiyomix.annotations.Extension',
        ]
        
        # Find package line and add imports after it
        package_match = re.search(r'package\s+[\w.]+', code)
        if package_match:
            package_line = package_match.group(0)
            
            # Check which imports are missing
            missing_imports = []
            for imp in required_imports:
                if imp not in code:
                    missing_imports.append(imp)
            
            if missing_imports:
                # Build import block
                import_block = '\n'.join(missing_imports)
                
                # Insert after package line
                code = code.replace(
                    package_line,
                    f"{package_line}\n\n{import_block}"
                )
        
        # Fix 5: Ensure addBaseUrlToLink is present in fetchers
        if 'addBaseUrlToLink' not in code:
            # Add to exploreFetchers
            code = re.sub(
                r'(coverAtt\s*=\s*"[^"]+",?\s*)\)',
                r'\1addBaseUrlToLink = true,\n                addBaseurlToCoverLink = true,\n            )',
                code
            )
        
        # Fix 6: Fix relative URLs in manga.key
        code = re.sub(
            r'key\s*=\s*([a-zA-Z]+\.(?:slug|path|key))\s*,',
            r'key = "$baseUrl/${\1}",',
            code
        )
        
        # Fix 7: Fix regex escaping - AI often over-escapes
        # Pattern: \\\d+ should be \\d+ in Kotlin strings
        code = re.sub(r'\\\\\\\\d\+', r'\\\\d+', code)  # Fix over-escaped \d+
        code = re.sub(r'\\\\\\\\s\+', r'\\\\s+', code)  # Fix over-escaped \s+
        code = re.sub(r'\\\\\\\\w\+', r'\\\\w+', code)  # Fix over-escaped \w+
        # Also fix triple backslash
        code = re.sub(r'\\\\\\d\+', r'\\\\d+', code)
        code = re.sub(r'\\\\\\s\+', r'\\\\s+', code)
        code = re.sub(r'\\\\\\w\+', r'\\\\w+', code)
        
        # Fix 8: Remove any duplicate imports
        lines = code.split('\n')
        seen_imports = set()
        new_lines = []
        for line in lines:
            if line.strip().startswith('import '):
                if line.strip() not in seen_imports:
                    seen_imports.add(line.strip())
                    new_lines.append(line)
            else:
                new_lines.append(line)
        code = '\n'.join(new_lines)
        
        # Fix 9: Remove invalid Type references (only Search and Others are valid for fetchers)
        code = re.sub(r',?\s*type\s*=\s*SourceFactory\.Type\.Latest\s*,?', '', code)
        code = re.sub(r',?\s*type\s*=\s*SourceFactory\.Type\.Popular\s*,?', '', code)
        code = re.sub(r',?\s*type\s*=\s*Type\.Latest\s*,?', '', code)
        code = re.sub(r',?\s*type\s*=\s*Type\.Popular\s*,?', '', code)
        
        # Fix 10: Clean up any double commas or trailing commas before )
        code = re.sub(r',\s*,', ',', code)
        code = re.sub(r',\s*\)', ')', code)
        
        # Fix 11: pageContentParse - should return list of Text() for each paragraph element
        # LNReader returns whole HTML, but IReader needs each paragraph as separate Text()
        content_selector_match = re.search(r'pageContentSelector\s*=\s*"([^"]+)"', code)
        content_selector = content_selector_match.group(1) if content_selector_match else "#content"
        
        wrong_pattern = r'val content = document\.selectFirst\("([^"]+)"\)[^}]*?return listOf\(Text\(content\.(?:html|text)\(\)\)\)'
        
        def fix_content_parse(match):
            selector = match.group(1)
            return f'''val content = document.select("{selector} p, {selector} br").mapNotNull {{ element ->
            val text = if (element.tagName() == "br") "\\n" else element.text().trim()
            if (text.isNotEmpty()) Text(text) else null
        }}
        return content.ifEmpty {{ listOf(Text(document.select("{selector}").text())) }}'''
        
        code = re.sub(wrong_pattern, fix_content_parse, code, flags=re.DOTALL)
        
        # Fix 12: Extract BaseExploreFetcher keys and add Filter.Sort
        fetcher_keys = re.findall(r'BaseExploreFetcher\(\s*"([^"]+)"', code)
        non_search_keys = [k for k in fetcher_keys if k.lower() != 'search']
        
        if non_search_keys and 'Filter.Sort' not in code:
            keys_array = ',\n                '.join([f'"{k}"' for k in non_search_keys])
            filter_sort = f'''Filter.Sort(
            "Sort By:",
            arrayOf(
                {keys_array},
            )
        ),'''
            code = re.sub(
                r'override fun getFilters\(\): FilterList = listOf\(Filter\.Title\(\)\)',
                f'override fun getFilters(): FilterList = listOf(\n        Filter.Title(),\n        {filter_sort}\n    )',
                code
            )
        
        return code
    
    def _validate_and_fix(self, code: str, analysis: Dict) -> Tuple[str, List[str]]:
        """Validate code and fix common issues"""
        issues_fixed = []
        
        # Check 1: Abstract class
        if 'abstract class' not in code:
            code = re.sub(r'\bclass\s+(\w+)\s*\(', r'abstract class \1(', code)
            issues_fixed.append("Added 'abstract' keyword to class")
        
        # Check 2: Extension annotation
        if '@Extension' not in code:
            code = re.sub(r'(abstract class)', r'@Extension\n\1', code)
            issues_fixed.append("Added @Extension annotation")
        
        # Check 3: SourceFactory extension
        if 'SourceFactory' not in code:
            issues_fixed.append("WARNING: Not using SourceFactory pattern")
        
        # Check 4: Required overrides
        required_overrides = ['lang', 'baseUrl', 'id', 'name']
        for override in required_overrides:
            if f'override val {override}' not in code and f'override fun {override}' not in code:
                issues_fixed.append(f"WARNING: Missing override for '{override}'")
        
        # Check 5: Paginated chapters - ensure getChapterList is overridden
        if analysis.get('has_api_chapters') and 'override suspend fun getChapterList' not in code:
            issues_fixed.append("WARNING: Plugin has paginated chapters but getChapterList not overridden")
        
        # Check 6: Content parsing
        if 'pageContentParse' not in code:
            issues_fixed.append("WARNING: Missing pageContentParse override")
        
        # Check 7: Fix common syntax errors
        # Fix unclosed braces
        open_braces = code.count('{')
        close_braces = code.count('}')
        if open_braces > close_braces:
            code += '\n}' * (open_braces - close_braces)
            issues_fixed.append(f"Added {open_braces - close_braces} missing closing braces")
        
        return code, issues_fixed


# Backward compatibility - use V2 as default
AICodeGenerator = AICodeGeneratorV2
