#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
IReader Source Creator - Config Validator
Validates selector configuration against a live website
"""

import sys
import io
import json
import argparse
import urllib.request
from pathlib import Path
from html.parser import HTMLParser

# Fix Windows console encoding
if sys.platform == 'win32':
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

try:
    from bs4 import BeautifulSoup
    HAS_BS4 = True
except ImportError:
    HAS_BS4 = False


def fetch_page(url: str) -> str:
    """Fetch a webpage"""
    headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
    }
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=30) as response:
        return response.read().decode('utf-8', errors='replace')


def test_selector(soup, selector: str) -> dict:
    """Test a CSS selector against the page"""
    try:
        elements = soup.select(selector)
        if elements:
            first = elements[0]
            text = first.get_text(strip=True)[:100] if first.get_text(strip=True) else None
            return {
                'success': True,
                'count': len(elements),
                'preview': text
            }
        return {'success': False, 'count': 0, 'error': 'No elements found'}
    except Exception as e:
        return {'success': False, 'count': 0, 'error': str(e)}


def validate_config(config_path: Path, test_url: str = None):
    """Validate a configuration file"""
    print()
    print("╔" + "═" * 60 + "╗")
    print("║" + " 🔍 IReader Config Validator".center(60) + "║")
    print("╚" + "═" * 60 + "╝")
    print()

    if not HAS_BS4:
        print("❌ BeautifulSoup4 is required for validation")
        print("   Install with: pip install beautifulsoup4")
        return False

    # Load config
    print(f"📖 Loading config: {config_path}")
    try:
        with open(config_path, 'r', encoding='utf-8') as f:
            config = json.load(f)
    except Exception as e:
        print(f"❌ Failed to load config: {e}")
        return False

    name = config.get('name', 'Unknown')
    base_url = config.get('baseUrl', '')
    selectors = config.get('selectors', {})

    print(f"   ✓ Source: {name}")
    print(f"   ✓ Base URL: {base_url}")
    print(f"   ✓ Selectors: {len(selectors)}")
    print()

    if not test_url:
        test_url = base_url
        if not test_url:
            print("❌ No URL to test. Provide --url or set baseUrl in config.")
            return False

    # Fetch page
    print(f"🌐 Fetching: {test_url}")
    try:
        html = fetch_page(test_url)
        print(f"   ✓ Fetched {len(html)} bytes")
    except Exception as e:
        print(f"❌ Failed to fetch page: {e}")
        return False

    # Parse HTML
    soup = BeautifulSoup(html, 'html.parser')
    print()

    # Test selectors
    print("🧪 Testing selectors:")
    print()

    results = {}
    for field, selector in selectors.items():
        result = test_selector(soup, selector)
        results[field] = result

        if result['success']:
            if result['count'] == 1:
                status = "✅"
                status_text = f"Found 1 element"
            else:
                status = "⚠️"
                status_text = f"Found {result['count']} elements"
        else:
            status = "❌"
            status_text = result.get('error', 'Not found')

        print(f"   {status} {field}")
        print(f"      Selector: {selector}")
        print(f"      Result: {status_text}")
        if result.get('preview'):
            preview = result['preview'][:50] + ('...' if len(result['preview']) > 50 else '')
            print(f"      Preview: \"{preview}\"")
        print()

    # Summary
    success_count = sum(1 for r in results.values() if r['success'])
    total_count = len(results)

    print("─" * 60)
    print()
    print(f"📊 Summary: {success_count}/{total_count} selectors working")
    print()

    if success_count == total_count:
        print("✅ All selectors validated successfully!")
    elif success_count > total_count // 2:
        print("⚠️ Some selectors need adjustment")
    else:
        print("❌ Many selectors failed - check the page structure")

    return success_count == total_count


def main():
    parser = argparse.ArgumentParser(
        description='Validate IReader source configuration',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog='''
Examples:
  python validate_config.py config.json
  python validate_config.py config.json --url https://example.com/novel/123
        '''
    )
    parser.add_argument('config', type=Path, help='Configuration JSON file')
    parser.add_argument('--url', type=str, help='URL to test against (default: baseUrl from config)')

    args = parser.parse_args()

    if not args.config.exists():
        print(f"❌ Config file not found: {args.config}")
        sys.exit(1)

    success = validate_config(args.config, args.url)
    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
