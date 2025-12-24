"""
Kotlin Source Selector Parser

Extracts CSS selectors from IReader Kotlin source files.
"""

import re
import os
from pathlib import Path
from typing import Dict, List, Optional, Any
from dataclasses import dataclass, field


@dataclass
class SelectorInfo:
    """Information about a single selector"""
    name: str
    selector: str
    attribute: Optional[str] = None
    page_type: str = "unknown"  # explore, detail, chapters, content
    fetcher_name: Optional[str] = None  # e.g., "Latest", "Search"
    line_number: int = 0


@dataclass
class SourceInfo:
    """Parsed source information"""
    name: str
    package: str
    base_url: str
    lang: str
    source_id: int
    file_path: str
    selectors: List[SelectorInfo] = field(default_factory=list)
    explore_endpoints: List[Dict[str, Any]] = field(default_factory=list)
    test_fixture: Optional[Dict[str, str]] = None


class SelectorParser:
    """Parse selectors from Kotlin source files"""
    
    # Patterns for extracting source metadata
    PACKAGE_PATTERN = re.compile(r'package\s+([\w.]+)')
    NAME_PATTERN = re.compile(r'override\s+val\s+name[:\s]+(?:String\s*)?(?:get\(\)\s*=\s*)?["\']([^"\']+)["\']')
    BASE_URL_PATTERN = re.compile(r'override\s+val\s+baseUrl[:\s]+(?:String\s*)?(?:get\(\)\s*=\s*)?["\']([^"\']+)["\']')
    LANG_PATTERN = re.compile(r'override\s+val\s+lang[:\s]+(?:String\s*)?(?:get\(\)\s*=\s*)?["\']([^"\']+)["\']')
    ID_PATTERN = re.compile(r'override\s+val\s+id[:\s]+(?:Long\s*)?(?:get\(\)\s*=\s*)?(\d+)')
    
    # Patterns for test fixtures
    TEST_FIXTURE_PATTERN = re.compile(
        r'@TestFixture\s*\(\s*'
        r'novelUrl\s*=\s*["\']([^"\']+)["\'].*?'
        r'chapterUrl\s*=\s*["\']([^"\']+)["\'].*?'
        r'expectedTitle\s*=\s*["\']([^"\']+)["\']',
        re.DOTALL
    )
    
    # Patterns for selectors in different contexts
    SELECTOR_PATTERNS = {
        'simple': re.compile(r'(\w+Selector)\s*=\s*["\']([^"\']+)["\']'),
        'with_att': re.compile(r'(\w+Att)\s*=\s*["\']([^"\']+)["\']'),
    }
    
    # BaseExploreFetcher pattern
    EXPLORE_FETCHER_PATTERN = re.compile(
        r'BaseExploreFetcher\s*\(\s*'
        r'["\']([^"\']+)["\']',  # name
        re.DOTALL
    )
    
    def __init__(self, workspace_root: str = "."):
        self.workspace_root = Path(workspace_root)
    
    def find_source_files(self, lang: Optional[str] = None) -> List[Path]:
        """Find all Kotlin source files"""
        sources_dir = self.workspace_root / "sources"
        source_files = []
        
        if lang:
            search_dirs = [sources_dir / lang]
        else:
            search_dirs = [d for d in sources_dir.iterdir() if d.is_dir() and d.name != "multisrc"]
        
        for lang_dir in search_dirs:
            if not lang_dir.exists():
                continue
            for source_dir in lang_dir.iterdir():
                if source_dir.is_dir():
                    kt_files = list(source_dir.rglob("*.kt"))
                    source_files.extend(kt_files)
        
        # Also check multisrc
        multisrc_dir = sources_dir / "multisrc"
        if multisrc_dir.exists():
            for theme_dir in multisrc_dir.iterdir():
                if theme_dir.is_dir():
                    kt_files = list(theme_dir.rglob("*.kt"))
                    source_files.extend(kt_files)
        
        return source_files
    
    def parse_source_file(self, file_path: Path) -> Optional[SourceInfo]:
        """Parse a single Kotlin source file"""
        try:
            content = file_path.read_text(encoding='utf-8')
        except Exception as e:
            print(f"Error reading {file_path}: {e}")
            return None
        
        # Extract basic metadata
        package_match = self.PACKAGE_PATTERN.search(content)
        name_match = self.NAME_PATTERN.search(content)
        base_url_match = self.BASE_URL_PATTERN.search(content)
        lang_match = self.LANG_PATTERN.search(content)
        id_match = self.ID_PATTERN.search(content)
        
        if not all([package_match, base_url_match]):
            return None
        
        source_info = SourceInfo(
            name=name_match.group(1) if name_match else file_path.stem,
            package=package_match.group(1),
            base_url=base_url_match.group(1),
            lang=lang_match.group(1) if lang_match else "en",
            source_id=int(id_match.group(1)) if id_match else 0,
            file_path=str(file_path.relative_to(self.workspace_root))
        )
        
        # Extract test fixture if present
        fixture_match = self.TEST_FIXTURE_PATTERN.search(content)
        if fixture_match:
            source_info.test_fixture = {
                'novelUrl': fixture_match.group(1),
                'chapterUrl': fixture_match.group(2),
                'expectedTitle': fixture_match.group(3)
            }
        
        # Extract selectors
        source_info.selectors = self._extract_selectors(content)
        source_info.explore_endpoints = self._extract_explore_endpoints(content)
        
        return source_info
    
    def _extract_selectors(self, content: str) -> List[SelectorInfo]:
        """Extract all selectors from source content"""
        selectors = []
        lines = content.split('\n')
        
        # Track current context
        current_context = None
        current_fetcher = None
        
        for line_num, line in enumerate(lines, 1):
            # Detect context changes
            if 'exploreFetchers' in line:
                current_context = 'explore'
            elif 'detailFetcher' in line or 'Detail(' in line:
                current_context = 'detail'
            elif 'chapterFetcher' in line or 'Chapters(' in line:
                current_context = 'chapters'
            elif 'contentFetcher' in line or 'Content(' in line:
                current_context = 'content'
            
            # Detect fetcher name
            fetcher_match = re.search(r'BaseExploreFetcher\s*\(\s*["\']([^"\']+)["\']', line)
            if fetcher_match:
                current_fetcher = fetcher_match.group(1)
            
            # Extract selectors
            for pattern_name, pattern in self.SELECTOR_PATTERNS.items():
                for match in pattern.finditer(line):
                    selector_name = match.group(1)
                    selector_value = match.group(2)
                    
                    # Skip empty selectors
                    if not selector_value or selector_value == "":
                        continue
                    
                    # Determine if this is an attribute selector
                    attribute = None
                    if pattern_name == 'with_att':
                        attribute = selector_value
                        continue  # We'll pair this with the selector
                    
                    selectors.append(SelectorInfo(
                        name=selector_name,
                        selector=selector_value,
                        attribute=attribute,
                        page_type=current_context or 'unknown',
                        fetcher_name=current_fetcher if current_context == 'explore' else None,
                        line_number=line_num
                    ))
        
        # Pair selectors with their attributes
        selectors = self._pair_selector_attributes(selectors, content)
        
        return selectors
    
    def _pair_selector_attributes(self, selectors: List[SelectorInfo], content: str) -> List[SelectorInfo]:
        """Pair selectors with their corresponding attribute definitions"""
        # Find all attribute definitions
        att_pattern = re.compile(r'(\w+)Att\s*=\s*["\']([^"\']+)["\']')
        attributes = {}
        for match in att_pattern.finditer(content):
            base_name = match.group(1)
            att_value = match.group(2)
            attributes[base_name] = att_value
        
        # Update selectors with attributes
        for selector in selectors:
            base_name = selector.name.replace('Selector', '')
            if base_name in attributes:
                selector.attribute = attributes[base_name]
        
        return selectors
    
    def _extract_explore_endpoints(self, content: str) -> List[Dict[str, Any]]:
        """Extract explore fetcher endpoints"""
        endpoints = []
        
        # Pattern to match BaseExploreFetcher blocks
        fetcher_pattern = re.compile(
            r'BaseExploreFetcher\s*\(\s*'
            r'["\']([^"\']+)["\'].*?'  # name
            r'endpoint\s*=\s*["\']([^"\']+)["\']',  # endpoint
            re.DOTALL
        )
        
        for match in fetcher_pattern.finditer(content):
            name = match.group(1)
            endpoint = match.group(2)
            
            # Determine type
            fetcher_type = 'listing'
            if 'Search' in name or 'type = SourceFactory.Type.Search' in match.group(0):
                fetcher_type = 'search'
            
            endpoints.append({
                'name': name,
                'endpoint': endpoint,
                'type': fetcher_type
            })
        
        return endpoints
    
    def parse_all_sources(self, lang: Optional[str] = None) -> List[SourceInfo]:
        """Parse all source files"""
        sources = []
        for file_path in self.find_source_files(lang):
            source_info = self.parse_source_file(file_path)
            if source_info:
                sources.append(source_info)
        return sources
    
    def get_source_by_name(self, name: str) -> Optional[SourceInfo]:
        """Find and parse a source by name"""
        name_lower = name.lower()
        for file_path in self.find_source_files():
            if name_lower in file_path.stem.lower():
                return self.parse_source_file(file_path)
        return None


def main():
    """CLI for testing the parser"""
    import sys
    
    parser = SelectorParser()
    
    if len(sys.argv) > 1:
        # Parse specific source
        source_name = sys.argv[1]
        source = parser.get_source_by_name(source_name)
        if source:
            print(f"\n=== {source.name} ===")
            print(f"Package: {source.package}")
            print(f"Base URL: {source.base_url}")
            print(f"Lang: {source.lang}")
            print(f"File: {source.file_path}")
            
            if source.test_fixture:
                print(f"\nTest Fixture:")
                for k, v in source.test_fixture.items():
                    print(f"  {k}: {v}")
            
            print(f"\nSelectors ({len(source.selectors)}):")
            for sel in source.selectors:
                att_str = f" [{sel.attribute}]" if sel.attribute else ""
                print(f"  [{sel.page_type}] {sel.name}: {sel.selector}{att_str}")
            
            print(f"\nEndpoints ({len(source.explore_endpoints)}):")
            for ep in source.explore_endpoints:
                print(f"  [{ep['type']}] {ep['name']}: {ep['endpoint']}")
        else:
            print(f"Source '{source_name}' not found")
    else:
        # List all sources
        sources = parser.parse_all_sources()
        print(f"Found {len(sources)} sources:")
        for source in sources:
            print(f"  - {source.name} ({source.lang}): {source.base_url}")


if __name__ == "__main__":
    main()
