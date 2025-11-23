"""
Enhanced TypeScript Analyzer
Properly extracts selectors, API calls, and code structure
"""

import re
from typing import Dict, List, Optional, Set, Tuple
from dataclasses import dataclass, field
from enum import Enum

class PluginType(Enum):
    SCRAPING = "scraping"
    API = "api"
    HYBRID = "hybrid"
    UNKNOWN = "unknown"

@dataclass
class MethodInfo:
    name: str
    is_async: bool
    params: List[str]
    return_type: Optional[str]
    body: str
    selectors: List[str] = field(default_factory=list)
    api_calls: List[str] = field(default_factory=list)
    cheerio_calls: List[str] = field(default_factory=list)
    json_parsing: bool = False

@dataclass
class PluginMetadata:
    id: str
    name: str
    site: str
    version: str
    icon: str
    plugin_type: PluginType
    methods: Dict[str, MethodInfo]
    all_selectors: Set[str]
    all_api_endpoints: Set[str]
    uses_cheerio: bool
    uses_fetch_api: bool

class TypeScriptAnalyzer:
    """Advanced TypeScript code analyzer"""
    
    def __init__(self, content: str):
        self.content = content
        self.lines = content.split('\n')
        
    def analyze(self) -> PluginMetadata:
        """Perform complete analysis"""
        metadata = self._extract_metadata()
        methods = self._extract_methods()
        
        # Analyze each method
        all_selectors = set()
        all_api_endpoints = set()
        
        for method_name, method in methods.items():
            method.selectors = self._extract_selectors_from_body(method.body)
            method.api_calls = self._extract_api_calls(method.body)
            method.cheerio_calls = self._extract_cheerio_calls(method.body)
            method.json_parsing = self._has_json_parsing(method.body)
            
            all_selectors.update(method.selectors)
            all_api_endpoints.update(method.api_calls)
        
        # Determine plugin type
        plugin_type = self._determine_plugin_type(methods, all_api_endpoints, all_selectors)
        
        return PluginMetadata(
            id=metadata['id'],
            name=metadata['name'],
            site=metadata['site'],
            version=metadata['version'],
            icon=metadata['icon'],
            plugin_type=plugin_type,
            methods=methods,
            all_selectors=all_selectors,
            all_api_endpoints=all_api_endpoints,
            uses_cheerio='loadedCheerio' in self.content or 'loadCheerio' in self.content,
            uses_fetch_api='fetchApi' in self.content or 'fetch(' in self.content
        )
    
    def _extract_metadata(self) -> Dict[str, str]:
        """Extract plugin metadata"""
        metadata = {
            'id': '',
            'name': '',
            'site': '',
            'version': '',
            'icon': ''
        }
        
        # Extract from class properties
        patterns = {
            'id': r"id\s*=\s*['\"]([^'\"]+)['\"]",
            'name': r"name\s*=\s*['\"]([^'\"]+)['\"]",
            'site': r"site\s*=\s*['\"]([^'\"]+)['\"]",
            'version': r"version\s*=\s*['\"]([^'\"]+)['\"]",
            'icon': r"icon\s*=\s*['\"]([^'\"]+)['\"]",
        }
        
        for key, pattern in patterns.items():
            match = re.search(pattern, self.content)
            if match:
                metadata[key] = match.group(1)
        
        return metadata
    
    def _extract_methods(self) -> Dict[str, MethodInfo]:
        """Extract all methods from the class"""
        methods = {}
        
        # Pattern to match method declarations
        method_pattern = r'(async\s+)?(\w+)\s*\([^)]*\)(?:\s*:\s*[^{]+)?\s*\{'
        
        for match in re.finditer(method_pattern, self.content):
            is_async = match.group(1) is not None
            method_name = match.group(2)
            
            # Skip constructor and non-method functions
            if method_name in ['constructor', 'class']:
                continue
            
            # Extract method body
            start = match.end() - 1  # Include opening brace
            body = self._extract_balanced_braces(start)
            
            # Extract parameters
            params_match = re.search(rf'{method_name}\s*\(([^)]*)\)', self.content[match.start():match.end()])
            params = []
            if params_match:
                params_str = params_match.group(1)
                params = [p.strip().split(':')[0].strip() for p in params_str.split(',') if p.strip()]
            
            # Extract return type
            return_type = None
            return_match = re.search(r':\s*([^{]+)\s*\{', self.content[match.start():match.end()])
            if return_match:
                return_type = return_match.group(1).strip()
            
            methods[method_name] = MethodInfo(
                name=method_name,
                is_async=is_async,
                params=params,
                return_type=return_type,
                body=body
            )
        
        return methods
    
    def _extract_balanced_braces(self, start: int) -> str:
        """Extract content between balanced braces"""
        if start >= len(self.content) or self.content[start] != '{':
            return ""
        
        brace_count = 0
        i = start
        in_string = False
        string_char = None
        escape_next = False
        
        while i < len(self.content):
            char = self.content[i]
            
            if escape_next:
                escape_next = False
                i += 1
                continue
            
            if char == '\\':
                escape_next = True
                i += 1
                continue
            
            if char in ['"', "'", '`'] and not in_string:
                in_string = True
                string_char = char
            elif char == string_char and in_string:
                in_string = False
                string_char = None
            elif not in_string:
                if char == '{':
                    brace_count += 1
                elif char == '}':
                    brace_count -= 1
                    if brace_count == 0:
                        return self.content[start+1:i]
            
            i += 1
        
        return self.content[start+1:]
    
    def _extract_selectors_from_body(self, body: str) -> List[str]:
        """Extract ALL CSS selectors from method body"""
        selectors = []
        
        # Comprehensive patterns for selector extraction
        patterns = [
            # Cheerio patterns
            r"loadedCheerio\(['\"]([^'\"]+)['\"]\)",
            r"loadedCheerio\('([^']+)'\)",
            r"loadedCheerio\(\"([^\"]+)\"\)",
            r"loadedCheerio\(`([^`]+)`\)",
            r"loadCheerio\([^)]*\)\.select\(['\"]([^'\"]+)['\"]\)",
            
            # jQuery patterns
            r"\$\(['\"]([^'\"]+)['\"]\)",
            r"\$\('([^']+)'\)",
            r"\$\(\"([^\"]+)\"\)",
            
            # .select() patterns
            r"\.select\(['\"]([^'\"]+)['\"]\)",
            r"\.select\('([^']+)'\)",
            r"\.select\(\"([^\"]+)\"\)",
            
            # .selectFirst() patterns
            r"\.selectFirst\(['\"]([^'\"]+)['\"]\)",
            
            # DOM patterns
            r"\.querySelector\(['\"]([^'\"]+)['\"]\)",
            r"\.querySelectorAll\(['\"]([^'\"]+)['\"]\)",
            
            # .find() patterns
            r"\.find\(['\"]([^'\"]+)['\"]\)",
        ]
        
        for pattern in patterns:
            matches = re.findall(pattern, body, re.MULTILINE | re.DOTALL)
            selectors.extend(matches)
        
        # Remove duplicates while preserving order
        seen = set()
        unique_selectors = []
        for sel in selectors:
            # Clean selector
            sel = sel.strip()
            if sel and sel not in seen:
                seen.add(sel)
                unique_selectors.append(sel)
        
        return unique_selectors
    
    def _extract_api_calls(self, body: str) -> List[str]:
        """Extract API endpoint calls"""
        api_calls = []
        
        patterns = [
            r"fetchApi\(['\"]([^'\"]+)['\"]\)",
            r"fetchApi\(`([^`]+)`\)",
            r"fetch\(['\"]([^'\"]+)['\"]\)",
            r"fetch\(`([^`]+)`\)",
            r"client\.get\(['\"]([^'\"]+)['\"]\)",
            r"client\.post\(['\"]([^'\"]+)['\"]\)",
            r"\.get\(['\"]([^'\"]+)['\"]\)",
            r"\.post\(['\"]([^'\"]+)['\"]\)",
        ]
        
        for pattern in patterns:
            matches = re.findall(pattern, body)
            api_calls.extend(matches)
        
        # Filter to only include paths that look like API endpoints
        api_endpoints = []
        for call in api_calls:
            if '/api/' in call or call.startswith('/') or call.startswith('http'):
                api_endpoints.append(call)
        
        return list(set(api_endpoints))
    
    def _extract_cheerio_calls(self, body: str) -> List[str]:
        """Extract cheerio method calls"""
        calls = []
        
        patterns = [
            r"loadedCheerio\([^)]+\)\.(text|html|attr|val|prop)\(",
            r"\$\([^)]+\)\.(text|html|attr|val|prop)\(",
            r"\.select\([^)]+\)\.(text|html|attr|eachText|first|last)\(",
        ]
        
        for pattern in patterns:
            matches = re.findall(pattern, body)
            calls.extend(matches)
        
        return list(set(calls))
    
    def _has_json_parsing(self, body: str) -> bool:
        """Check if method parses JSON"""
        json_indicators = [
            '.json()',
            'JSON.parse',
            'json.parse',
            'await.*json()',
        ]
        
        for indicator in json_indicators:
            if re.search(indicator, body, re.IGNORECASE):
                return True
        
        return False
    
    def _determine_plugin_type(self, methods: Dict[str, MethodInfo], 
                               api_endpoints: Set[str], selectors: Set[str]) -> PluginType:
        """Determine if plugin is scraping, API, or hybrid"""
        has_api = len(api_endpoints) > 0
        has_selectors = len(selectors) > 0
        
        # Check if methods use JSON parsing
        json_methods = sum(1 for m in methods.values() if m.json_parsing)
        
        if has_api and json_methods >= 2:
            if has_selectors and len(selectors) > 5:
                return PluginType.HYBRID
            return PluginType.API
        elif has_selectors:
            return PluginType.SCRAPING
        elif has_api:
            return PluginType.API
        
        return PluginType.UNKNOWN
