#!/usr/bin/env python3
"""
AI-Powered Selector Repair

Automatically suggests and applies fixes for broken selectors.
"""

import asyncio
import json
import os
import re
import sys
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional, Any, Tuple

sys.path.insert(0, str(Path(__file__).parent))

from utils.selector_parser import SelectorParser, SourceInfo, SelectorInfo
from utils.html_fetcher import HtmlFetcher
from utils.ai_repair import AIRepair, RepairContext, RepairSuggestion, MockAIRepair

try:
    from rich.console import Console
    from rich.table import Table
    from rich.prompt import Confirm
    from rich import print as rprint
    RICH_AVAILABLE = True
except ImportError:
    RICH_AVAILABLE = False
    rprint = print


class SelectorRepairer:
    """Repair broken selectors using AI"""
    
    def __init__(
        self,
        workspace_root: str = ".",
        snapshots_dir: Optional[str] = None,
        use_mock_ai: bool = False,
        verbose: bool = False
    ):
        self.workspace_root = Path(workspace_root)
        self.snapshots_dir = Path(snapshots_dir) if snapshots_dir else self.workspace_root / "scripts/source-health/snapshots"
        self.verbose = verbose
        self.parser = SelectorParser(workspace_root)
        self.console = Console() if RICH_AVAILABLE else None
        
        # Initialize AI repair
        if use_mock_ai:
            self.ai = MockAIRepair()
        else:
            self.ai = AIRepair()
    
    def load_snapshot(self, source_name: str) -> Optional[Dict[str, Any]]:
        """Load snapshot for a source"""
        for lang in ['en', 'ar', 'tu', 'cn', 'fr', 'es']:
            snapshot_path = self.snapshots_dir / lang / f"{source_name.lower()}.json"
            if snapshot_path.exists():
                return json.loads(snapshot_path.read_text())
        
        snapshot_path = self.snapshots_dir / f"{source_name.lower()}.json"
        if snapshot_path.exists():
            return json.loads(snapshot_path.read_text())
        
        return None
    
    async def find_broken_selectors(
        self,
        source: SourceInfo,
        snapshot: Optional[Dict[str, Any]] = None
    ) -> List[Tuple[SelectorInfo, str, str]]:
        """Find selectors that no longer work
        
        Returns: List of (selector_info, page_html, expected_value)
        """
        broken = []
        
        # Get test URLs
        test_urls = {}
        if snapshot and 'testUrls' in snapshot:
            test_urls = snapshot['testUrls']
        elif source.test_fixture:
            test_urls = {
                'novel': source.test_fixture.get('novelUrl'),
                'chapter': source.test_fixture.get('chapterUrl')
            }
        
        if not test_urls:
            return broken
        
        async with HtmlFetcher() as fetcher:
            # Fetch pages
            pages = {}
            for page_type, url in test_urls.items():
                if url:
                    result = await fetcher.fetch(url)
                    if not result.error:
                        pages[page_type] = result.html
            
            # Check each selector
            for sel in source.selectors:
                # Determine which page to use
                page_html = None
                if sel.page_type == 'detail' and 'novel' in pages:
                    page_html = pages['novel']
                elif sel.page_type == 'content' and 'chapter' in pages:
                    page_html = pages['chapter']
                elif sel.page_type == 'chapters' and 'novel' in pages:
                    page_html = pages['novel']
                elif sel.page_type == 'explore' and 'latest' in pages:
                    page_html = pages['latest']
                elif pages:
                    page_html = list(pages.values())[0]
                
                if not page_html:
                    continue
                
                # Run selector
                result = fetcher.run_selector(page_html, sel.selector, sel.attribute)
                
                # Get expected value from snapshot
                expected = None
                if snapshot and 'selectors' in snapshot:
                    page_selectors = snapshot['selectors'].get(sel.page_type, {})
                    sel_config = page_selectors.get(sel.name, {})
                    expected = sel_config.get('expected')
                
                # Check if broken
                if result.count == 0:
                    broken.append((sel, page_html, expected))
                elif expected and result.matches and expected not in result.matches[0]:
                    # Selector works but returns different content
                    broken.append((sel, page_html, expected))
        
        return broken
    
    async def repair_selector(
        self,
        selector: SelectorInfo,
        html: str,
        expected: Optional[str]
    ) -> RepairSuggestion:
        """Get AI suggestion for repairing a selector"""
        # Extract relevant HTML context
        async with HtmlFetcher() as fetcher:
            html_context = fetcher.extract_html_context(html, selector.selector)
        
        context = RepairContext(
            selector_name=selector.name,
            original_selector=selector.selector,
            expected_value=expected,
            expected_pattern=None,
            html_context=html_context,
            page_type=selector.page_type
        )
        
        return await self.ai.repair_selector(context)
    
    async def repair_source(
        self,
        source_name: str,
        auto_fix: bool = False,
        run_tests: bool = False
    ) -> List[Tuple[SelectorInfo, RepairSuggestion]]:
        """Repair all broken selectors in a source"""
        source = self.parser.get_source_by_name(source_name)
        if not source:
            rprint(f"[red]Source '{source_name}' not found[/red]" if RICH_AVAILABLE else f"Source not found")
            return []
        
        snapshot = self.load_snapshot(source_name)
        
        rprint(f"\n[bold]Analyzing {source.name}...[/bold]" if RICH_AVAILABLE else f"\nAnalyzing {source.name}...")
        
        # Find broken selectors
        broken = await self.find_broken_selectors(source, snapshot)
        
        if not broken:
            rprint("[green]All selectors are working![/green]" if RICH_AVAILABLE else "All selectors working!")
            return []
        
        rprint(f"Found {len(broken)} broken selectors")
        
        # Get repair suggestions
        repairs = []
        total_tokens = 0
        
        for sel, html, expected in broken:
            rprint(f"\nRepairing: {sel.name} ({sel.selector})")
            suggestion = await self.repair_selector(sel, html, expected)
            repairs.append((sel, suggestion))
            total_tokens += suggestion.tokens_used
            
            if suggestion.suggested_selector:
                rprint(f"  Suggested: {suggestion.suggested_selector}")
                rprint(f"  Confidence: {suggestion.confidence:.0%}")
                rprint(f"  Reason: {suggestion.explanation}")
            else:
                rprint(f"  [red]No suggestion available[/red]" if RICH_AVAILABLE else "  No suggestion")
        
        rprint(f"\nTotal tokens used: {total_tokens}")
        
        # Apply fixes if requested
        if auto_fix and repairs:
            await self.apply_fixes(source, repairs, run_tests)
        
        return repairs
    
    async def apply_fixes(
        self,
        source: SourceInfo,
        repairs: List[Tuple[SelectorInfo, RepairSuggestion]],
        run_tests: bool = False
    ):
        """Apply repair suggestions to source file"""
        source_path = self.workspace_root / source.file_path
        
        if not source_path.exists():
            rprint(f"[red]Source file not found: {source_path}[/red]" if RICH_AVAILABLE else f"File not found")
            return
        
        # Create backup
        backup_path = source_path.with_suffix('.kt.bak')
        backup_path.write_text(source_path.read_text())
        rprint(f"Backup created: {backup_path}")
        
        # Read source content
        content = source_path.read_text()
        
        # Apply each fix
        changes_made = 0
        for sel, suggestion in repairs:
            if not suggestion.suggested_selector or suggestion.confidence < 0.5:
                continue
            
            # Confirm if interactive
            if RICH_AVAILABLE:
                if not Confirm.ask(f"Apply fix for {sel.name}? ({sel.selector} → {suggestion.suggested_selector})"):
                    continue
            
            # Replace selector in content
            # Pattern to match selector assignment
            pattern = rf'({sel.name}\s*=\s*["\'])({re.escape(sel.selector)})(["\'])'
            replacement = rf'\g<1>{suggestion.suggested_selector}\g<3>'
            
            new_content, count = re.subn(pattern, replacement, content)
            if count > 0:
                content = new_content
                changes_made += count
                rprint(f"  ✓ Fixed {sel.name}")
            else:
                rprint(f"  ✗ Could not find selector in source")
        
        if changes_made > 0:
            source_path.write_text(content)
            rprint(f"\n[green]Applied {changes_made} fixes[/green]" if RICH_AVAILABLE else f"Applied {changes_made} fixes")
            
            if run_tests:
                await self.run_integration_tests(source)
        else:
            rprint("No changes applied")
    
    async def run_integration_tests(self, source: SourceInfo):
        """Run integration tests for the source"""
        rprint("\nRunning integration tests...")
        
        # Build command
        source_path = source.file_path
        # Extract module path from file path
        # e.g., sources/en/fanmtl/main/src/... -> :sources:en:fanmtl
        parts = source_path.split('/')
        if 'sources' in parts:
            idx = parts.index('sources')
            if idx + 2 < len(parts):
                module = f":sources:{parts[idx+1]}:{parts[idx+2]}"
                
                import subprocess
                result = subprocess.run(
                    ['./gradlew', f'{module}:test'],
                    capture_output=True,
                    text=True,
                    cwd=str(self.workspace_root)
                )
                
                if result.returncode == 0:
                    rprint("[green]Tests passed![/green]" if RICH_AVAILABLE else "Tests passed!")
                else:
                    rprint("[red]Tests failed![/red]" if RICH_AVAILABLE else "Tests failed!")
                    if self.verbose:
                        rprint(result.stdout)
                        rprint(result.stderr)
    
    def print_repair_report(self, repairs: List[Tuple[SelectorInfo, RepairSuggestion]]):
        """Print repair suggestions as a report"""
        if not repairs:
            return
        
        if RICH_AVAILABLE and self.console:
            table = Table(title="Repair Suggestions")
            table.add_column("Selector")
            table.add_column("Original")
            table.add_column("Suggested")
            table.add_column("Confidence")
            
            for sel, suggestion in repairs:
                conf_color = "green" if suggestion.confidence > 0.7 else "yellow" if suggestion.confidence > 0.4 else "red"
                table.add_row(
                    sel.name,
                    sel.selector,
                    suggestion.suggested_selector or "-",
                    f"[{conf_color}]{suggestion.confidence:.0%}[/{conf_color}]"
                )
            
            self.console.print(table)
        else:
            print("\nRepair Suggestions:")
            for sel, suggestion in repairs:
                print(f"  {sel.name}:")
                print(f"    Original: {sel.selector}")
                print(f"    Suggested: {suggestion.suggested_selector or '-'}")
                print(f"    Confidence: {suggestion.confidence:.0%}")


async def main():
    """CLI entry point"""
    import argparse
    
    parser = argparse.ArgumentParser(description="Repair broken source selectors")
    parser.add_argument("--source", "-s", required=True, help="Source name to repair")
    parser.add_argument("--auto-fix", "-f", action="store_true", help="Automatically apply fixes")
    parser.add_argument("--test", "-t", action="store_true", help="Run tests after fixing")
    parser.add_argument("--mock", action="store_true", help="Use mock AI (no API calls)")
    parser.add_argument("--verbose", "-v", action="store_true", help="Verbose output")
    parser.add_argument("--workspace", "-w", default=".", help="Workspace root")
    
    args = parser.parse_args()
    
    repairer = SelectorRepairer(
        workspace_root=args.workspace,
        use_mock_ai=args.mock,
        verbose=args.verbose
    )
    
    repairs = await repairer.repair_source(
        args.source,
        auto_fix=args.auto_fix,
        run_tests=args.test
    )
    
    repairer.print_repair_report(repairs)


if __name__ == "__main__":
    asyncio.run(main())
