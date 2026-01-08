#!/usr/bin/env python3
"""
LightNovel-Crawler Source Analyzer

This script analyzes Python source files from lightnovel-crawler and classifies them
into categories for migration to IReader-extensions:
- Madara: WordPress Madara theme sites (zero-code @MadaraSource)
- SourceFactory: Standard HTML sites with declarative fetchers
- ParsedHttpSource: Complex sites requiring custom logic (API, pagination)
- Skipped: Broken/rejected sources

Usage:
    python analyze_sources.py <lncrawl_sources_path> [--output <output_file>]
"""

import os
import re
import json
import argparse
from pathlib import Path
from dataclasses import dataclass, field, asdict
from typing import List, Dict, Optional, Set
from enum import Enum


class SourceType(Enum):
    MADARA = "madara"
    SOURCE_FACTORY = "source_factory"
    PARSED_HTTP_SOURCE = "parsed_http_source"
    SKIPPED = "skipped"
    UNKNOWN = "unknown"


@dataclass
class SourceInfo:
    """Information about a single source"""
    name: str
    file_path: str
    language: str
    base_urls: List[str]
    source_type: str
    template: Optional[str] = None
    has_search: bool = False
    has_login: bool = False
    uses_api: bool = False
    uses_browser: bool = False
    custom_paths: Dict[str, str] = field(default_factory=dict)
    selectors: Dict[str, str] = field(default_factory=dict)
    notes: List[str] = field(default_factory=list)


@dataclass
class AnalysisReport:
    """Complete analysis report"""
    total_sources: int = 0
    by_type: Dict[str, int] = field(default_factory=dict)
    by_language: Dict[str, int] = field(default_factory=dict)
    sources: List[SourceInfo] = field(default_factory=list)
    rejected_urls: Dict[str, str] = field(default_factory=dict)


# Template patterns to detect
TEMPLATE_PATTERNS = {
    "MadaraTemplate": SourceType.MADARA,
    "NovelFullTemplate": SourceType.SOURCE_FACTORY,
    "SearchableSoupTemplate": SourceType.SOURCE_FACTORY,
    "ChapterOnlySoupTemplate": SourceType.SOURCE_FACTORY,
    "GeneralSoupTemplate": SourceType.SOURCE_FACTORY,
    "SearchableBrowserTemplate": SourceType.PARSED_HTTP_SOURCE,
    "ChapterOnlyBrowserTemplate": SourceType.PARSED_HTTP_SOURCE,
    "Crawler": SourceType.PARSED_HTTP_SOURCE,
}

# Language code mapping (LNCrawl -> IReader)
LANGUAGE_MAP = {
    "en": "en",
    "zh": "zh",
    "id": "in",  # Indonesian
    "ru": "ru",
    "fr": "fr",
    "pt": "pt",
    "ar": "ar",
    "es": "es",
    "ja": "jp",  # Japanese
    "tr": "tu",  # Turkish
    "vi": "vi",
    "multi": "multi",
}


def extract_class_name(content: str) -> Optional[str]:
    """Extract the main crawler class name"""
    match = re.search(r'class\s+(\w+Crawler)\s*\(', content)
    if match:
        return match.group(1)
    match = re.search(r'class\s+(\w+)\s*\(', content)
    return match.group(1) if match else None


def extract_base_urls(content: str) -> List[str]:
    """Extract base_url list from source"""
    # Match base_url = ["url1", "url2"]
    match = re.search(r'base_url\s*=\s*\[(.*?)\]', content, re.DOTALL)
    if match:
        urls_str = match.group(1)
        urls = re.findall(r'["\']([^"\']+)["\']', urls_str)
        return urls
    return []


def extract_template(content: str) -> Optional[str]:
    """Extract the template/parent class being used"""
    # Look for class inheritance
    match = re.search(r'class\s+\w+\s*\(([^)]+)\)', content)
    if match:
        parents = match.group(1)
        for template in TEMPLATE_PATTERNS.keys():
            if template in parents:
                return template
    return None


def detect_features(content: str) -> Dict[str, bool]:
    """Detect various features in the source"""
    return {
        "has_search": "search_novel" in content or "select_search_items" in content,
        "has_login": "def login" in content,
        "uses_api": "get_json" in content or "post_json" in content or "/api/" in content,
        "uses_browser": "BrowserTemplate" in content or "self.browser" in content,
    }


def extract_selectors(content: str) -> Dict[str, str]:
    """Extract CSS selectors from BeautifulSoup calls"""
    selectors = {}
    
    # Match soup.select_one("selector") patterns
    for match in re.finditer(r'select_one\s*\(\s*["\']([^"\']+)["\']', content):
        selector = match.group(1)
        # Try to determine what it's selecting
        context = content[max(0, match.start()-100):match.start()]
        if "title" in context.lower():
            selectors["title"] = selector
        elif "cover" in context.lower() or "img" in context.lower():
            selectors["cover"] = selector
        elif "author" in context.lower():
            selectors["author"] = selector
        elif "chapter" in context.lower():
            selectors["chapter"] = selector
        elif "content" in context.lower() or "body" in context.lower():
            selectors["content"] = selector
    
    # Match soup.select("selector") patterns
    for match in re.finditer(r'\.select\s*\(\s*["\']([^"\']+)["\']', content):
        selector = match.group(1)
        context = content[max(0, match.start()-100):match.start()]
        if "chapter" in context.lower():
            selectors["chapter_list"] = selector
        elif "genre" in context.lower() or "tag" in context.lower():
            selectors["genres"] = selector
    
    return selectors


def detect_madara_patterns(content: str) -> bool:
    """Detect if source uses Madara-like patterns even without explicit inheritance"""
    madara_indicators = [
        "wp-manga-chapter",
        ".summary_image",
        "/ajax/chapters",
        "manga_get_chapters",
        ".post-title h3",
        ".genres-content",
        ".author-content",
        "div.reading-content",
        "post_type=wp-manga",
        "#manga-chapters-holder",
        ".c-tabs-item__content",
        "novel-author",
    ]
    matches = sum(1 for indicator in madara_indicators if indicator in content)
    return matches >= 2  # At least 2 indicators suggest Madara-like structure


def classify_source(content: str, template: Optional[str]) -> SourceType:
    """Classify source based on template and content analysis"""
    # First check for explicit Madara template
    if template == "MadaraTemplate":
        return SourceType.MADARA
    
    # Check for Madara-like patterns even without explicit template
    # This catches sources that use Madara selectors but inherit from Crawler
    if detect_madara_patterns(content):
        return SourceType.MADARA
    
    # Check other templates
    if template:
        return TEMPLATE_PATTERNS.get(template, SourceType.UNKNOWN)
    
    features = detect_features(content)
    
    # API-based sources need ParsedHttpSource
    if features["uses_api"]:
        return SourceType.PARSED_HTTP_SOURCE
    
    # Browser-required sources need ParsedHttpSource
    if features["uses_browser"]:
        return SourceType.PARSED_HTTP_SOURCE
    
    # Default to SourceFactory for standard HTML
    return SourceType.SOURCE_FACTORY


def analyze_source_file(file_path: Path, language: str) -> Optional[SourceInfo]:
    """Analyze a single source file"""
    try:
        content = file_path.read_text(encoding='utf-8')
    except Exception as e:
        return None
    
    # Skip __init__.py and other non-source files
    if file_path.name.startswith('_'):
        return None
    
    class_name = extract_class_name(content)
    if not class_name:
        return None
    
    base_urls = extract_base_urls(content)
    if not base_urls:
        return None
    
    template = extract_template(content)
    source_type = classify_source(content, template)
    features = detect_features(content)
    selectors = extract_selectors(content)
    
    # Generate source name from class name
    name = class_name.replace("Crawler", "").replace("Source", "")
    
    notes = []
    if features["has_login"]:
        notes.append("Requires authentication")
    if features["uses_browser"]:
        notes.append("Requires browser/JavaScript")
    
    return SourceInfo(
        name=name,
        file_path=str(file_path),
        language=LANGUAGE_MAP.get(language, language),
        base_urls=base_urls,
        source_type=source_type.value,
        template=template,
        has_search=features["has_search"],
        has_login=features["has_login"],
        uses_api=features["uses_api"],
        uses_browser=features["uses_browser"],
        selectors=selectors,
        notes=notes,
    )


def load_rejected_sources(sources_path: Path) -> Dict[str, str]:
    """Load rejected sources from _rejected.json"""
    rejected_file = sources_path / "_rejected.json"
    if rejected_file.exists():
        try:
            return json.loads(rejected_file.read_text(encoding='utf-8'))
        except:
            pass
    return {}


def analyze_sources(sources_path: Path) -> AnalysisReport:
    """Analyze all sources in the given path"""
    report = AnalysisReport()
    report.rejected_urls = load_rejected_sources(sources_path)
    
    # Initialize counters
    report.by_type = {t.value: 0 for t in SourceType}
    report.by_language = {}
    
    # Scan all language directories
    for lang_dir in sources_path.iterdir():
        if not lang_dir.is_dir() or lang_dir.name.startswith('_'):
            continue
        
        language = lang_dir.name
        
        # Handle nested structure (en/a/, en/b/, etc.)
        source_files = []
        for item in lang_dir.rglob("*.py"):
            if not item.name.startswith('_'):
                source_files.append(item)
        
        for source_file in source_files:
            source_info = analyze_source_file(source_file, language)
            if source_info:
                # Check if any base URL is in rejected list
                is_rejected = any(
                    url in report.rejected_urls or url.rstrip('/') + '/' in report.rejected_urls
                    for url in source_info.base_urls
                )
                if is_rejected:
                    source_info.source_type = SourceType.SKIPPED.value
                    source_info.notes.append("Site is rejected/broken")
                
                report.sources.append(source_info)
                report.total_sources += 1
                report.by_type[source_info.source_type] = report.by_type.get(source_info.source_type, 0) + 1
                report.by_language[source_info.language] = report.by_language.get(source_info.language, 0) + 1
    
    return report


def generate_summary(report: AnalysisReport) -> str:
    """Generate a human-readable summary"""
    lines = [
        "=" * 60,
        "LightNovel-Crawler Source Analysis Report",
        "=" * 60,
        "",
        f"Total Sources: {report.total_sources}",
        "",
        "By Type:",
    ]
    
    for source_type, count in sorted(report.by_type.items(), key=lambda x: -x[1]):
        if count > 0:
            lines.append(f"  {source_type}: {count}")
    
    lines.extend(["", "By Language:"])
    for lang, count in sorted(report.by_language.items(), key=lambda x: -x[1]):
        lines.append(f"  {lang}: {count}")
    
    lines.extend(["", "Madara Sources (zero-code migration):"])
    madara_sources = [s for s in report.sources if s.source_type == "madara"]
    for source in sorted(madara_sources, key=lambda x: x.name)[:20]:
        lines.append(f"  - {source.name} ({source.base_urls[0]})")
    if len(madara_sources) > 20:
        lines.append(f"  ... and {len(madara_sources) - 20} more")
    
    lines.extend(["", "API-based Sources (need ParsedHttpSource):"])
    api_sources = [s for s in report.sources if s.uses_api and s.source_type != "skipped"]
    for source in sorted(api_sources, key=lambda x: x.name)[:10]:
        lines.append(f"  - {source.name} ({source.base_urls[0]})")
    if len(api_sources) > 10:
        lines.append(f"  ... and {len(api_sources) - 10} more")
    
    lines.extend(["", f"Rejected/Broken Sources: {report.by_type.get('skipped', 0)}"])
    
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description="Analyze LightNovel-Crawler sources")
    parser.add_argument("sources_path", help="Path to lncrawl sources directory")
    parser.add_argument("--output", "-o", default="source-analysis.json", help="Output JSON file")
    parser.add_argument("--summary", "-s", action="store_true", help="Print summary to console")
    
    args = parser.parse_args()
    
    sources_path = Path(args.sources_path)
    if not sources_path.exists():
        print(f"Error: Path does not exist: {sources_path}")
        return 1
    
    print(f"Analyzing sources in: {sources_path}")
    report = analyze_sources(sources_path)
    
    # Save JSON report
    output_path = Path(args.output)
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(asdict(report), f, indent=2)
    print(f"Report saved to: {output_path}")
    
    # Print summary
    if args.summary:
        print()
        print(generate_summary(report))
    
    return 0


if __name__ == "__main__":
    exit(main())
