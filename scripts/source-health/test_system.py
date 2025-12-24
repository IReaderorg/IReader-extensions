#!/usr/bin/env python3
"""
Quick test script to verify the source health system works.
Run from workspace root: python scripts/source-health/test_system.py
"""

import asyncio
import sys
from pathlib import Path

# Add parent to path
sys.path.insert(0, str(Path(__file__).parent))

from utils.selector_parser import SelectorParser
from utils.html_fetcher import HtmlFetcher


async def test_parser():
    """Test the selector parser"""
    print("=" * 50)
    print("Testing Selector Parser")
    print("=" * 50)
    
    parser = SelectorParser(".")
    
    # Find sources
    sources = parser.parse_all_sources("en")
    print(f"\nFound {len(sources)} English sources")
    
    # Parse fanmtl specifically
    fanmtl = parser.get_source_by_name("fanmtl")
    if fanmtl:
        print(f"\n✓ Found fanmtl:")
        print(f"  Name: {fanmtl.name}")
        print(f"  Base URL: {fanmtl.base_url}")
        print(f"  Selectors: {len(fanmtl.selectors)}")
        print(f"  Endpoints: {len(fanmtl.explore_endpoints)}")
        
        if fanmtl.test_fixture:
            print(f"  Test Fixture: ✓")
            print(f"    Novel URL: {fanmtl.test_fixture.get('novelUrl', 'N/A')}")
        
        print("\n  Selectors by page type:")
        by_type = {}
        for sel in fanmtl.selectors:
            by_type.setdefault(sel.page_type, []).append(sel.name)
        for page_type, names in by_type.items():
            print(f"    {page_type}: {', '.join(names)}")
    else:
        print("✗ fanmtl not found")
    
    return fanmtl is not None


async def test_fetcher():
    """Test the HTML fetcher"""
    print("\n" + "=" * 50)
    print("Testing HTML Fetcher")
    print("=" * 50)
    
    test_url = "https://www.fanmtl.com/list/all/all-newstime-0.html"
    
    async with HtmlFetcher() as fetcher:
        print(f"\nFetching: {test_url}")
        result = await fetcher.fetch(test_url)
        
        if result.error:
            print(f"✗ Error: {result.error}")
            return False
        
        print(f"✓ Status: {result.status_code}")
        print(f"  HTML length: {len(result.html)} chars")
        print(f"  Fetch time: {result.fetch_time:.2f}s")
        
        # Test selector
        sel_result = fetcher.run_selector(result.html, "ul > li", None)
        print(f"\n  Selector 'ul > li': {sel_result.count} matches")
        
        if sel_result.matches:
            print(f"  First match preview: {sel_result.matches[0][:50]}...")
        
        return result.status_code == 200


async def test_snapshot():
    """Test snapshot loading"""
    print("\n" + "=" * 50)
    print("Testing Snapshot Loading")
    print("=" * 50)
    
    import json
    snapshot_path = Path("scripts/source-health/snapshots/en/fanmtl.json")
    
    if snapshot_path.exists():
        snapshot = json.loads(snapshot_path.read_text())
        print(f"\n✓ Loaded fanmtl snapshot")
        print(f"  Source: {snapshot.get('source')}")
        print(f"  Base URL: {snapshot.get('baseUrl')}")
        print(f"  Last verified: {snapshot.get('lastVerified')}")
        print(f"  Selector groups: {list(snapshot.get('selectors', {}).keys())}")
        return True
    else:
        print(f"✗ Snapshot not found at {snapshot_path}")
        return False


async def main():
    """Run all tests"""
    print("\n🔍 Source Health System Test\n")
    
    results = []
    
    # Test parser
    results.append(("Parser", await test_parser()))
    
    # Test fetcher (skip if no network)
    try:
        results.append(("Fetcher", await test_fetcher()))
    except Exception as e:
        print(f"\n⚠ Fetcher test skipped: {e}")
        results.append(("Fetcher", None))
    
    # Test snapshot
    results.append(("Snapshot", await test_snapshot()))
    
    # Summary
    print("\n" + "=" * 50)
    print("Test Summary")
    print("=" * 50)
    
    for name, passed in results:
        if passed is None:
            status = "⚠ SKIPPED"
        elif passed:
            status = "✓ PASSED"
        else:
            status = "✗ FAILED"
        print(f"  {name}: {status}")
    
    all_passed = all(r[1] for r in results if r[1] is not None)
    print(f"\n{'✓ All tests passed!' if all_passed else '✗ Some tests failed'}")
    
    return 0 if all_passed else 1


if __name__ == "__main__":
    exit_code = asyncio.run(main())
    sys.exit(exit_code)
