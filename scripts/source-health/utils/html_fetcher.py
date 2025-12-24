"""
HTML Fetcher with Playwright Support

Fetches HTML from websites, handling JavaScript rendering when needed.
"""

import asyncio
import hashlib
import json
import os
import re
from pathlib import Path
from typing import Dict, List, Optional, Any, Tuple
from dataclasses import dataclass
from datetime import datetime, timedelta
from bs4 import BeautifulSoup

try:
    from playwright.async_api import async_playwright, Browser, Page
    PLAYWRIGHT_AVAILABLE = True
except ImportError:
    PLAYWRIGHT_AVAILABLE = False

try:
    import httpx
    HTTPX_AVAILABLE = True
except ImportError:
    HTTPX_AVAILABLE = False


@dataclass
class FetchResult:
    """Result of fetching a URL"""
    url: str
    html: str
    status_code: int
    fetch_time: float
    used_js: bool
    error: Optional[str] = None
    cached: bool = False


@dataclass
class SelectorResult:
    """Result of running a selector"""
    selector: str
    attribute: Optional[str]
    matches: List[str]
    count: int
    success: bool
    error: Optional[str] = None


class HtmlFetcher:
    """Fetch HTML from websites with optional JS rendering"""
    
    DEFAULT_USER_AGENT = (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/120.0.0.0 Safari/537.36"
    )
    
    def __init__(
        self,
        cache_dir: Optional[str] = None,
        cache_ttl_hours: int = 24,
        use_js: bool = False,
        timeout: int = 30000,
        user_agent: Optional[str] = None
    ):
        self.cache_dir = Path(cache_dir) if cache_dir else None
        self.cache_ttl = timedelta(hours=cache_ttl_hours)
        self.use_js = use_js
        self.timeout = timeout
        self.user_agent = user_agent or self.DEFAULT_USER_AGENT
        self._browser: Optional[Browser] = None
        self._playwright = None
    
    async def __aenter__(self):
        if self.use_js and PLAYWRIGHT_AVAILABLE:
            self._playwright = await async_playwright().start()
            self._browser = await self._playwright.chromium.launch(headless=True)
        return self
    
    async def __aexit__(self, exc_type, exc_val, exc_tb):
        if self._browser:
            await self._browser.close()
        if self._playwright:
            await self._playwright.stop()
    
    def _get_cache_path(self, url: str) -> Path:
        """Get cache file path for a URL"""
        if not self.cache_dir:
            return None
        url_hash = hashlib.md5(url.encode()).hexdigest()
        return self.cache_dir / f"{url_hash}.json"
    
    def _load_from_cache(self, url: str) -> Optional[FetchResult]:
        """Load cached result if valid"""
        cache_path = self._get_cache_path(url)
        if not cache_path or not cache_path.exists():
            return None
        
        try:
            data = json.loads(cache_path.read_text())
            cached_time = datetime.fromisoformat(data['cached_at'])
            if datetime.now() - cached_time < self.cache_ttl:
                return FetchResult(
                    url=data['url'],
                    html=data['html'],
                    status_code=data['status_code'],
                    fetch_time=data['fetch_time'],
                    used_js=data['used_js'],
                    cached=True
                )
        except Exception:
            pass
        return None
    
    def _save_to_cache(self, result: FetchResult):
        """Save result to cache"""
        cache_path = self._get_cache_path(result.url)
        if not cache_path:
            return
        
        self.cache_dir.mkdir(parents=True, exist_ok=True)
        data = {
            'url': result.url,
            'html': result.html,
            'status_code': result.status_code,
            'fetch_time': result.fetch_time,
            'used_js': result.used_js,
            'cached_at': datetime.now().isoformat()
        }
        cache_path.write_text(json.dumps(data))
    
    async def fetch(self, url: str, force_js: bool = False) -> FetchResult:
        """Fetch HTML from URL"""
        # Check cache first
        cached = self._load_from_cache(url)
        if cached:
            return cached
        
        start_time = asyncio.get_event_loop().time()
        use_js = force_js or self.use_js
        
        try:
            if use_js and PLAYWRIGHT_AVAILABLE and self._browser:
                result = await self._fetch_with_playwright(url)
            elif HTTPX_AVAILABLE:
                result = await self._fetch_with_httpx(url)
            else:
                raise RuntimeError("No HTTP client available. Install httpx or playwright.")
            
            result.fetch_time = asyncio.get_event_loop().time() - start_time
            
            # Cache successful results
            if result.status_code == 200:
                self._save_to_cache(result)
            
            return result
            
        except Exception as e:
            return FetchResult(
                url=url,
                html="",
                status_code=0,
                fetch_time=asyncio.get_event_loop().time() - start_time,
                used_js=use_js,
                error=str(e)
            )
    
    async def _fetch_with_playwright(self, url: str) -> FetchResult:
        """Fetch using Playwright (JS rendering)"""
        page = await self._browser.new_page(user_agent=self.user_agent)
        try:
            response = await page.goto(url, timeout=self.timeout, wait_until='networkidle')
            html = await page.content()
            return FetchResult(
                url=url,
                html=html,
                status_code=response.status if response else 0,
                fetch_time=0,
                used_js=True
            )
        finally:
            await page.close()
    
    async def _fetch_with_httpx(self, url: str) -> FetchResult:
        """Fetch using httpx (no JS)"""
        async with httpx.AsyncClient(
            headers={"User-Agent": self.user_agent},
            timeout=self.timeout / 1000,
            follow_redirects=True
        ) as client:
            response = await client.get(url)
            return FetchResult(
                url=url,
                html=response.text,
                status_code=response.status_code,
                fetch_time=0,
                used_js=False
            )
    
    def run_selector(
        self,
        html: str,
        selector: str,
        attribute: Optional[str] = None,
        limit: int = 10
    ) -> SelectorResult:
        """Run a CSS selector on HTML and return results"""
        try:
            soup = BeautifulSoup(html, 'lxml')
            elements = soup.select(selector)
            
            results = []
            for elem in elements[:limit]:
                if attribute:
                    value = elem.get(attribute, '')
                else:
                    value = elem.get_text(strip=True)
                if value:
                    results.append(value)
            
            return SelectorResult(
                selector=selector,
                attribute=attribute,
                matches=results,
                count=len(elements),
                success=len(elements) > 0
            )
        except Exception as e:
            return SelectorResult(
                selector=selector,
                attribute=attribute,
                matches=[],
                count=0,
                success=False,
                error=str(e)
            )
    
    def run_selectors(
        self,
        html: str,
        selectors: Dict[str, Dict[str, Any]]
    ) -> Dict[str, SelectorResult]:
        """Run multiple selectors and return results"""
        results = {}
        for name, config in selectors.items():
            selector = config.get('selector', '')
            attribute = config.get('attribute')
            results[name] = self.run_selector(html, selector, attribute)
        return results
    
    def extract_html_context(
        self,
        html: str,
        selector: str,
        context_chars: int = 500
    ) -> str:
        """Extract HTML context around a selector match for AI repair"""
        try:
            soup = BeautifulSoup(html, 'lxml')
            
            # Try to find elements matching the selector
            elements = soup.select(selector)
            if elements:
                # Get the parent context
                elem = elements[0]
                parent = elem.parent
                while parent and len(str(parent)) < context_chars:
                    parent = parent.parent
                if parent:
                    return str(parent)[:context_chars * 2]
            
            # If selector doesn't match, try to find similar structure
            # This helps AI understand what changed
            body = soup.find('body')
            if body:
                return str(body)[:context_chars * 2]
            
            return html[:context_chars * 2]
            
        except Exception:
            return html[:context_chars * 2]


async def main():
    """CLI for testing the fetcher"""
    import sys
    
    if len(sys.argv) < 2:
        print("Usage: python html_fetcher.py <url> [selector]")
        return
    
    url = sys.argv[1]
    selector = sys.argv[2] if len(sys.argv) > 2 else None
    
    async with HtmlFetcher(use_js=False) as fetcher:
        print(f"Fetching: {url}")
        result = await fetcher.fetch(url)
        
        print(f"Status: {result.status_code}")
        print(f"Time: {result.fetch_time:.2f}s")
        print(f"JS: {result.used_js}")
        print(f"HTML length: {len(result.html)}")
        
        if result.error:
            print(f"Error: {result.error}")
        
        if selector and result.html:
            print(f"\nRunning selector: {selector}")
            sel_result = fetcher.run_selector(result.html, selector)
            print(f"Matches: {sel_result.count}")
            for i, match in enumerate(sel_result.matches[:5]):
                print(f"  {i+1}. {match[:100]}...")


if __name__ == "__main__":
    asyncio.run(main())
