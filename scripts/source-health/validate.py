#!/usr/bin/env python3
"""
Source Health Validator

Validates IReader extension selectors against live websites.
"""

import asyncio
import json
import os
import re
import sys
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional, Any
from dataclasses import dataclass, asdict

# Add parent to path for imports
sys.path.insert(0, str(Path(__file__).parent))

from utils.selector_parser import SelectorParser, SourceInfo
from utils.html_fetcher import HtmlFetcher, FetchResult, SelectorResult

try:
    from rich.console import Console
    from rich.table import Table
    from rich.progress import Progress, SpinnerColumn, TextColumn
    from rich import print as rprint
    RICH_AVAILABLE = True
except ImportError:
    RICH_AVAILABLE = False
    rprint = print

try:
    import click
    CLICK_AVAILABLE = True
except ImportError:
    CLICK_AVAILABLE = False


@dataclass
class ValidationResult:
    """Result of validating a single selector"""
    name: str
    selector: str
    attribute: Optional[str]
    status: str  # "pass", "fail", "warn", "skip"
    expected: Optional[str]
    actual: Optional[str]
    match_count: int
    message: str


@dataclass
class SourceValidationResult:
    """Result of validating an entire source"""
    source_name: str
    base_url: str
    lang: str
    timestamp: str
    overall_status: str  # "healthy", "degraded", "broken"
    results: List[ValidationResult]
    fetch_errors: List[str]
    total_selectors: int
    passed: int
    failed: int
    warnings: int


class SourceValidator:
    """Validate source selectors against live websites"""
    
    def __init__(
        self,
        workspace_root: str = ".",
        snapshots_dir: Optional[str] = None,
        cache_dir: Optional[str] = None,
        use_js: bool = False,
        verbose: bool = False
    ):
        self.workspace_root = Path(workspace_root)
        self.snapshots_dir = Path(snapshots_dir) if snapshots_dir else self.workspace_root / "scripts/source-health/snapshots"
        self.cache_dir = Path(cache_dir) if cache_dir else self.workspace_root / "scripts/source-health/.cache"
        self.use_js = use_js
        self.verbose = verbose
        self.parser = SelectorParser(workspace_root)
        self.console = Console() if RICH_AVAILABLE else None
    
    def load_snapshot(self, source_name: str) -> Optional[Dict[str, Any]]:
        """Load snapshot for a source"""
        # Try different paths
        for lang in ['en', 'ar', 'tu', 'cn', 'fr', 'es']:
            snapshot_path = self.snapshots_dir / lang / f"{source_name.lower()}.json"
            if snapshot_path.exists():
                return json.loads(snapshot_path.read_text())
        
        # Try without lang subdirectory
        snapshot_path = self.snapshots_dir / f"{source_name.lower()}.json"
        if snapshot_path.exists():
            return json.loads(snapshot_path.read_text())
        
        return None
    
    async def validate_source(
        self,
        source: SourceInfo,
        snapshot: Optional[Dict[str, Any]] = None
    ) -> SourceValidationResult:
        """Validate a single source"""
        results = []
        fetch_errors = []
        
        # Determine test URLs
        test_urls = {}
        if snapshot and 'testUrls' in snapshot:
            test_urls = snapshot['testUrls']
        elif source.test_fixture:
            test_urls = {
                'novel': source.test_fixture.get('novelUrl'),
                'chapter': source.test_fixture.get('chapterUrl')
            }
        else:
            # Generate URLs from endpoints
            for ep in source.explore_endpoints:
                if ep['type'] == 'listing':
                    endpoint = ep['endpoint'].replace('{page}', '1').replace('{page}', '0')
                    test_urls['latest'] = source.base_url + endpoint
                    break
        
        if not test_urls:
            return SourceValidationResult(
                source_name=source.name,
                base_url=source.base_url,
                lang=source.lang,
                timestamp=datetime.now().isoformat(),
                overall_status="skip",
                results=[],
                fetch_errors=["No test URLs available"],
                total_selectors=len(source.selectors),
                passed=0,
                failed=0,
                warnings=0
            )
        
        async with HtmlFetcher(
            cache_dir=str(self.cache_dir),
            use_js=self.use_js
        ) as fetcher:
            # Fetch pages
            pages = {}
            for page_type, url in test_urls.items():
                if url:
                    if self.verbose:
                        rprint(f"  Fetching {page_type}: {url}")
                    result = await fetcher.fetch(url)
                    if result.error:
                        fetch_errors.append(f"{page_type}: {result.error}")
                    else:
                        pages[page_type] = result.html
            
            # Validate selectors
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
                    results.append(ValidationResult(
                        name=sel.name,
                        selector=sel.selector,
                        attribute=sel.attribute,
                        status="skip",
                        expected=None,
                        actual=None,
                        match_count=0,
                        message="No page available for validation"
                    ))
                    continue
                
                # Run selector
                sel_result = fetcher.run_selector(
                    page_html,
                    sel.selector,
                    sel.attribute
                )
                
                # Get expected value from snapshot
                expected = None
                if snapshot and 'selectors' in snapshot:
                    page_selectors = snapshot['selectors'].get(sel.page_type, {})
                    sel_config = page_selectors.get(sel.name, {})
                    expected = sel_config.get('expected')
                
                # Determine status
                if sel_result.error:
                    status = "fail"
                    message = f"Selector error: {sel_result.error}"
                elif sel_result.count == 0:
                    status = "fail"
                    message = "No matches found"
                elif expected and sel_result.matches and expected not in sel_result.matches[0]:
                    status = "warn"
                    message = f"Expected '{expected}' but got '{sel_result.matches[0][:50]}...'"
                else:
                    status = "pass"
                    message = f"Found {sel_result.count} matches"
                
                results.append(ValidationResult(
                    name=sel.name,
                    selector=sel.selector,
                    attribute=sel.attribute,
                    status=status,
                    expected=expected,
                    actual=sel_result.matches[0] if sel_result.matches else None,
                    match_count=sel_result.count,
                    message=message
                ))
        
        # Calculate overall status
        passed = sum(1 for r in results if r.status == "pass")
        failed = sum(1 for r in results if r.status == "fail")
        warnings = sum(1 for r in results if r.status == "warn")
        
        if failed > len(results) * 0.5:
            overall_status = "broken"
        elif failed > 0 or warnings > len(results) * 0.3:
            overall_status = "degraded"
        else:
            overall_status = "healthy"
        
        return SourceValidationResult(
            source_name=source.name,
            base_url=source.base_url,
            lang=source.lang,
            timestamp=datetime.now().isoformat(),
            overall_status=overall_status,
            results=results,
            fetch_errors=fetch_errors,
            total_selectors=len(source.selectors),
            passed=passed,
            failed=failed,
            warnings=warnings
        )
    
    async def validate_by_name(self, source_name: str) -> Optional[SourceValidationResult]:
        """Validate a source by name"""
        source = self.parser.get_source_by_name(source_name)
        if not source:
            rprint(f"[red]Source '{source_name}' not found[/red]" if RICH_AVAILABLE else f"Source '{source_name}' not found")
            return None
        
        snapshot = self.load_snapshot(source_name)
        return await self.validate_source(source, snapshot)
    
    async def validate_all(self, lang: Optional[str] = None) -> List[SourceValidationResult]:
        """Validate all sources"""
        sources = self.parser.parse_all_sources(lang)
        results = []
        
        for source in sources:
            if self.verbose:
                rprint(f"\nValidating {source.name}...")
            snapshot = self.load_snapshot(source.name)
            result = await self.validate_source(source, snapshot)
            results.append(result)
        
        return results
    
    def print_result(self, result: SourceValidationResult):
        """Print validation result"""
        if RICH_AVAILABLE and self.console:
            # Status color
            status_colors = {
                "healthy": "green",
                "degraded": "yellow",
                "broken": "red",
                "skip": "dim"
            }
            color = status_colors.get(result.overall_status, "white")
            
            self.console.print(f"\n[bold]{result.source_name}[/bold] ({result.base_url})")
            self.console.print(f"Status: [{color}]{result.overall_status.upper()}[/{color}]")
            self.console.print(f"Selectors: {result.passed}✓ {result.failed}✗ {result.warnings}⚠")
            
            if result.fetch_errors:
                self.console.print("[red]Fetch errors:[/red]")
                for err in result.fetch_errors:
                    self.console.print(f"  - {err}")
            
            if self.verbose and result.results:
                table = Table(show_header=True)
                table.add_column("Selector")
                table.add_column("Status")
                table.add_column("Matches")
                table.add_column("Message")
                
                for r in result.results:
                    status_style = {
                        "pass": "green",
                        "fail": "red",
                        "warn": "yellow",
                        "skip": "dim"
                    }.get(r.status, "white")
                    
                    table.add_row(
                        r.name,
                        f"[{status_style}]{r.status}[/{status_style}]",
                        str(r.match_count),
                        r.message[:50]
                    )
                
                self.console.print(table)
        else:
            print(f"\n{result.source_name} ({result.base_url})")
            print(f"Status: {result.overall_status.upper()}")
            print(f"Selectors: {result.passed} passed, {result.failed} failed, {result.warnings} warnings")
            
            if result.fetch_errors:
                print("Fetch errors:")
                for err in result.fetch_errors:
                    print(f"  - {err}")
    
    def save_report(self, results: List[SourceValidationResult], output_path: str):
        """Save validation report to JSON"""
        report = {
            "generated_at": datetime.now().isoformat(),
            "total_sources": len(results),
            "healthy": sum(1 for r in results if r.overall_status == "healthy"),
            "degraded": sum(1 for r in results if r.overall_status == "degraded"),
            "broken": sum(1 for r in results if r.overall_status == "broken"),
            "sources": [asdict(r) for r in results]
        }
        
        Path(output_path).write_text(json.dumps(report, indent=2))
        rprint(f"Report saved to {output_path}")


async def main():
    """CLI entry point"""
    import argparse
    
    parser = argparse.ArgumentParser(description="Validate IReader source selectors")
    parser.add_argument("--source", "-s", help="Source name to validate")
    parser.add_argument("--all", "-a", action="store_true", help="Validate all sources")
    parser.add_argument("--lang", "-l", help="Filter by language")
    parser.add_argument("--output", "-o", help="Output JSON report path")
    parser.add_argument("--js", action="store_true", help="Use JavaScript rendering")
    parser.add_argument("--verbose", "-v", action="store_true", help="Verbose output")
    parser.add_argument("--workspace", "-w", default=".", help="Workspace root")
    
    args = parser.parse_args()
    
    validator = SourceValidator(
        workspace_root=args.workspace,
        use_js=args.js,
        verbose=args.verbose
    )
    
    if args.source:
        result = await validator.validate_by_name(args.source)
        if result:
            validator.print_result(result)
            if args.output:
                validator.save_report([result], args.output)
    
    elif args.all:
        results = await validator.validate_all(args.lang)
        for result in results:
            validator.print_result(result)
        
        if args.output:
            validator.save_report(results, args.output)
        
        # Summary
        healthy = sum(1 for r in results if r.overall_status == "healthy")
        degraded = sum(1 for r in results if r.overall_status == "degraded")
        broken = sum(1 for r in results if r.overall_status == "broken")
        
        rprint(f"\n{'='*50}")
        rprint(f"Summary: {healthy} healthy, {degraded} degraded, {broken} broken")
    
    else:
        parser.print_help()


if __name__ == "__main__":
    asyncio.run(main())
