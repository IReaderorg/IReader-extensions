#!/usr/bin/env python3
"""
Snapshot Generator

Creates JSON snapshots of working selectors for validation.
"""

import asyncio
import json
import os
import sys
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional, Any

sys.path.insert(0, str(Path(__file__).parent))

from utils.selector_parser import SelectorParser, SourceInfo
from utils.html_fetcher import HtmlFetcher

try:
    from rich.console import Console
    from rich.prompt import Prompt, Confirm
    from rich import print as rprint
    RICH_AVAILABLE = True
except ImportError:
    RICH_AVAILABLE = False
    rprint = print


class SnapshotGenerator:
    """Generate validation snapshots for sources"""
    
    def __init__(
        self,
        workspace_root: str = ".",
        snapshots_dir: Optional[str] = None,
        use_js: bool = False
    ):
        self.workspace_root = Path(workspace_root)
        self.snapshots_dir = Path(snapshots_dir) if snapshots_dir else self.workspace_root / "scripts/source-health/snapshots"
        self.use_js = use_js
        self.parser = SelectorParser(workspace_root)
        self.console = Console() if RICH_AVAILABLE else None
    
    async def generate_snapshot(
        self,
        source: SourceInfo,
        novel_url: Optional[str] = None,
        chapter_url: Optional[str] = None,
        verify: bool = True
    ) -> Dict[str, Any]:
        """Generate snapshot for a source"""
        
        # Get test URLs
        test_urls = {}
        
        if source.test_fixture:
            test_urls['novel'] = novel_url or source.test_fixture.get('novelUrl')
            test_urls['chapter'] = chapter_url or source.test_fixture.get('chapterUrl')
        else:
            test_urls['novel'] = novel_url
            test_urls['chapter'] = chapter_url
        
        # Generate listing URL from endpoints
        for ep in source.explore_endpoints:
            if ep['type'] == 'listing':
                endpoint = ep['endpoint'].replace('{page}', '1').replace('{page}', '0')
                test_urls['latest'] = source.base_url + endpoint
                break
            elif ep['type'] == 'search':
                test_urls['search_template'] = source.base_url + ep['endpoint']
        
        snapshot = {
            "source": source.name.lower(),
            "baseUrl": source.base_url,
            "lang": source.lang,
            "version": 1,
            "lastVerified": datetime.now().isoformat(),
            "sourceFile": source.file_path,
            "testUrls": {k: v for k, v in test_urls.items() if v},
            "selectors": {},
            "urlValidation": {},
            "metadata": {
                "requiresJs": self.use_js,
                "hasCloudflare": False,
                "rateLimit": 1000
            }
        }
        
        if not verify:
            # Just create structure without fetching
            for sel in source.selectors:
                page_type = sel.page_type or 'unknown'
                if page_type not in snapshot['selectors']:
                    snapshot['selectors'][page_type] = {}
                
                snapshot['selectors'][page_type][sel.name] = {
                    "selector": sel.selector,
                    "attribute": sel.attribute,
                    "expected": None,
                    "expectedMinCount": 1
                }
            return snapshot
        
        # Fetch pages and verify selectors
        async with HtmlFetcher(use_js=self.use_js) as fetcher:
            pages = {}
            
            for page_type, url in test_urls.items():
                if url and not url.endswith('_template'):
                    rprint(f"Fetching {page_type}: {url}")
                    result = await fetcher.fetch(url)
                    if not result.error:
                        pages[page_type] = result.html
                    else:
                        rprint(f"[red]Error fetching {page_type}: {result.error}[/red]" if RICH_AVAILABLE else f"Error: {result.error}")
            
            # Run selectors and capture results
            for sel in source.selectors:
                page_type = sel.page_type or 'unknown'
                
                # Determine which page to use
                page_html = None
                if page_type == 'detail' and 'novel' in pages:
                    page_html = pages['novel']
                elif page_type == 'content' and 'chapter' in pages:
                    page_html = pages['chapter']
                elif page_type == 'chapters' and 'novel' in pages:
                    page_html = pages['novel']
                elif page_type == 'explore' and 'latest' in pages:
                    page_html = pages['latest']
                elif pages:
                    page_html = list(pages.values())[0]
                
                if page_type not in snapshot['selectors']:
                    snapshot['selectors'][page_type] = {}
                
                selector_data = {
                    "selector": sel.selector,
                    "attribute": sel.attribute
                }
                
                if page_html:
                    result = fetcher.run_selector(page_html, sel.selector, sel.attribute)
                    
                    if result.matches:
                        # Store first match as expected value
                        selector_data["expected"] = result.matches[0][:200]  # Truncate long values
                        selector_data["expectedMinCount"] = 1
                        rprint(f"  ✓ {sel.name}: {result.matches[0][:50]}..." if RICH_AVAILABLE else f"  OK {sel.name}")
                    else:
                        selector_data["expectedMinCount"] = 0
                        rprint(f"  ✗ {sel.name}: No matches" if RICH_AVAILABLE else f"  FAIL {sel.name}")
                
                snapshot['selectors'][page_type][sel.name] = selector_data
        
        return snapshot
    
    def save_snapshot(self, snapshot: Dict[str, Any]):
        """Save snapshot to file"""
        lang = snapshot.get('lang', 'en')
        source_name = snapshot.get('source', 'unknown')
        
        output_dir = self.snapshots_dir / lang
        output_dir.mkdir(parents=True, exist_ok=True)
        
        output_path = output_dir / f"{source_name}.json"
        output_path.write_text(json.dumps(snapshot, indent=2))
        
        rprint(f"Snapshot saved to {output_path}")
        return output_path
    
    async def interactive_generate(self, source_name: str):
        """Interactive snapshot generation with prompts"""
        source = self.parser.get_source_by_name(source_name)
        if not source:
            rprint(f"[red]Source '{source_name}' not found[/red]" if RICH_AVAILABLE else f"Source not found")
            return
        
        rprint(f"\n[bold]Generating snapshot for {source.name}[/bold]" if RICH_AVAILABLE else f"\nGenerating snapshot for {source.name}")
        rprint(f"Base URL: {source.base_url}")
        rprint(f"Selectors found: {len(source.selectors)}")
        
        # Get test URLs
        novel_url = None
        chapter_url = None
        
        if source.test_fixture:
            novel_url = source.test_fixture.get('novelUrl')
            chapter_url = source.test_fixture.get('chapterUrl')
            rprint(f"\nFound @TestFixture URLs:")
            rprint(f"  Novel: {novel_url}")
            rprint(f"  Chapter: {chapter_url}")
        
        if RICH_AVAILABLE:
            if not novel_url:
                novel_url = Prompt.ask("Enter novel detail URL", default="")
            if not chapter_url:
                chapter_url = Prompt.ask("Enter chapter content URL", default="")
            
            verify = Confirm.ask("Verify selectors against live site?", default=True)
        else:
            if not novel_url:
                novel_url = input("Enter novel detail URL: ").strip()
            if not chapter_url:
                chapter_url = input("Enter chapter content URL: ").strip()
            verify = input("Verify selectors? (y/n): ").lower() == 'y'
        
        snapshot = await self.generate_snapshot(
            source,
            novel_url=novel_url or None,
            chapter_url=chapter_url or None,
            verify=verify
        )
        
        self.save_snapshot(snapshot)
    
    async def batch_generate(self, lang: Optional[str] = None, verify: bool = False):
        """Generate snapshots for all sources"""
        sources = self.parser.parse_all_sources(lang)
        
        for source in sources:
            rprint(f"\nProcessing {source.name}...")
            try:
                snapshot = await self.generate_snapshot(source, verify=verify)
                self.save_snapshot(snapshot)
            except Exception as e:
                rprint(f"[red]Error: {e}[/red]" if RICH_AVAILABLE else f"Error: {e}")


async def main():
    """CLI entry point"""
    import argparse
    
    parser = argparse.ArgumentParser(description="Generate source validation snapshots")
    parser.add_argument("--source", "-s", help="Source name")
    parser.add_argument("--novel-url", help="Novel detail page URL")
    parser.add_argument("--chapter-url", help="Chapter content page URL")
    parser.add_argument("--interactive", "-i", action="store_true", help="Interactive mode")
    parser.add_argument("--verify", action="store_true", help="Verify selectors against live site")
    parser.add_argument("--all", "-a", action="store_true", help="Generate for all sources")
    parser.add_argument("--lang", "-l", help="Filter by language")
    parser.add_argument("--js", action="store_true", help="Use JavaScript rendering")
    parser.add_argument("--workspace", "-w", default=".", help="Workspace root")
    
    args = parser.parse_args()
    
    generator = SnapshotGenerator(
        workspace_root=args.workspace,
        use_js=args.js
    )
    
    if args.source:
        if args.interactive:
            await generator.interactive_generate(args.source)
        else:
            source = generator.parser.get_source_by_name(args.source)
            if source:
                snapshot = await generator.generate_snapshot(
                    source,
                    novel_url=args.novel_url,
                    chapter_url=args.chapter_url,
                    verify=args.verify
                )
                generator.save_snapshot(snapshot)
            else:
                rprint(f"Source '{args.source}' not found")
    
    elif args.all:
        await generator.batch_generate(args.lang, verify=args.verify)
    
    else:
        parser.print_help()


if __name__ == "__main__":
    asyncio.run(main())
