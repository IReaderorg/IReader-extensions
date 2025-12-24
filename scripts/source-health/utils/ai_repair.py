"""
AI-Powered Selector Repair

Uses AI to suggest new CSS selectors when old ones break.
Optimized for minimal token usage.
"""

import os
import json
import re
from typing import Dict, List, Optional, Any
from dataclasses import dataclass


@dataclass
class RepairSuggestion:
    """AI-suggested selector repair"""
    original_selector: str
    suggested_selector: str
    confidence: float  # 0.0 to 1.0
    explanation: str
    tokens_used: int


@dataclass
class RepairContext:
    """Context for AI repair"""
    selector_name: str
    original_selector: str
    expected_value: Optional[str]
    expected_pattern: Optional[str]
    html_context: str
    page_type: str  # explore, detail, chapters, content


class AIRepair:
    """AI-powered selector repair using various providers"""
    
    # Minimal prompt template for token efficiency
    REPAIR_PROMPT = """Fix this broken CSS selector.

SELECTOR: {selector}
EXPECTED: {expected}
PAGE TYPE: {page_type}

HTML CONTEXT:
```html
{html_context}
```

Return ONLY a JSON object:
{{"selector": "new_css_selector", "confidence": 0.0-1.0, "explanation": "brief reason"}}"""

    def __init__(self, provider: str = "auto"):
        """
        Initialize AI repair with specified provider.
        
        Args:
            provider: "gemini", "openai", "anthropic", or "auto" (detect from env)
        """
        self.provider = self._detect_provider(provider)
        self._client = None
    
    def _detect_provider(self, provider: str) -> str:
        """Detect available AI provider from environment"""
        if provider != "auto":
            return provider
        
        if os.environ.get("GEMINI_API_KEY"):
            return "gemini"
        elif os.environ.get("OPENAI_API_KEY"):
            return "openai"
        elif os.environ.get("ANTHROPIC_API_KEY"):
            return "anthropic"
        else:
            return "none"
    
    def _get_client(self):
        """Get or create AI client"""
        if self._client:
            return self._client
        
        if self.provider == "gemini":
            import google.generativeai as genai
            genai.configure(api_key=os.environ["GEMINI_API_KEY"])
            self._client = genai.GenerativeModel('gemini-1.5-flash')
        elif self.provider == "openai":
            from openai import OpenAI
            self._client = OpenAI()
        elif self.provider == "anthropic":
            import anthropic
            self._client = anthropic.Anthropic()
        else:
            raise RuntimeError(
                "No AI provider configured. Set one of: "
                "GEMINI_API_KEY, OPENAI_API_KEY, or ANTHROPIC_API_KEY"
            )
        
        return self._client
    
    def _build_prompt(self, context: RepairContext) -> str:
        """Build minimal prompt for repair"""
        expected = context.expected_value or context.expected_pattern or "any matching content"
        
        # Truncate HTML context to save tokens
        html = context.html_context
        if len(html) > 1500:
            html = html[:1500] + "\n... (truncated)"
        
        return self.REPAIR_PROMPT.format(
            selector=context.original_selector,
            expected=expected,
            page_type=context.page_type,
            html_context=html
        )
    
    def _parse_response(self, response_text: str) -> Dict[str, Any]:
        """Parse AI response JSON"""
        # Try to extract JSON from response
        json_match = re.search(r'\{[^{}]*\}', response_text, re.DOTALL)
        if json_match:
            try:
                return json.loads(json_match.group())
            except json.JSONDecodeError:
                pass
        
        # Fallback: try to parse entire response
        try:
            return json.loads(response_text)
        except json.JSONDecodeError:
            return {
                "selector": "",
                "confidence": 0.0,
                "explanation": f"Failed to parse response: {response_text[:200]}"
            }
    
    async def repair_selector(self, context: RepairContext) -> RepairSuggestion:
        """Get AI suggestion for fixing a broken selector"""
        prompt = self._build_prompt(context)
        tokens_used = 0
        
        try:
            client = self._get_client()
            
            if self.provider == "gemini":
                response = client.generate_content(prompt)
                response_text = response.text
                # Estimate tokens (Gemini doesn't always return usage)
                tokens_used = len(prompt.split()) + len(response_text.split())
                
            elif self.provider == "openai":
                response = client.chat.completions.create(
                    model="gpt-3.5-turbo",
                    messages=[{"role": "user", "content": prompt}],
                    max_tokens=200,
                    temperature=0.3
                )
                response_text = response.choices[0].message.content
                tokens_used = response.usage.total_tokens
                
            elif self.provider == "anthropic":
                response = client.messages.create(
                    model="claude-3-haiku-20240307",
                    max_tokens=200,
                    messages=[{"role": "user", "content": prompt}]
                )
                response_text = response.content[0].text
                tokens_used = response.usage.input_tokens + response.usage.output_tokens
            
            else:
                raise RuntimeError(f"Unknown provider: {self.provider}")
            
            # Parse response
            result = self._parse_response(response_text)
            
            return RepairSuggestion(
                original_selector=context.original_selector,
                suggested_selector=result.get("selector", ""),
                confidence=float(result.get("confidence", 0.0)),
                explanation=result.get("explanation", ""),
                tokens_used=tokens_used
            )
            
        except Exception as e:
            return RepairSuggestion(
                original_selector=context.original_selector,
                suggested_selector="",
                confidence=0.0,
                explanation=f"Error: {str(e)}",
                tokens_used=tokens_used
            )
    
    async def repair_multiple(
        self,
        contexts: List[RepairContext],
        batch_size: int = 5
    ) -> List[RepairSuggestion]:
        """Repair multiple selectors, optionally batching for efficiency"""
        suggestions = []
        
        for context in contexts:
            suggestion = await self.repair_selector(context)
            suggestions.append(suggestion)
        
        return suggestions
    
    def estimate_tokens(self, context: RepairContext) -> int:
        """Estimate token usage for a repair request"""
        prompt = self._build_prompt(context)
        # Rough estimate: 1 token ≈ 4 characters
        return len(prompt) // 4 + 50  # +50 for response


class MockAIRepair(AIRepair):
    """Mock AI repair for testing without API calls"""
    
    def __init__(self):
        self.provider = "mock"
        self._client = None
    
    async def repair_selector(self, context: RepairContext) -> RepairSuggestion:
        """Return mock suggestion based on common patterns"""
        original = context.original_selector
        
        # Simple heuristics for common selector changes
        suggestions = {
            # Class changes
            r'\.(\w+)-item': lambda m: f'.{m.group(1)}-card',
            r'\.(\w+)-title': lambda m: f'.{m.group(1)}-name',
            # ID to class
            r'#(\w+)': lambda m: f'.{m.group(1)}',
            # Add parent context
            r'^(\w+)$': lambda m: f'div.content {m.group(1)}',
        }
        
        suggested = original
        for pattern, replacement in suggestions.items():
            match = re.match(pattern, original)
            if match:
                suggested = replacement(match)
                break
        
        return RepairSuggestion(
            original_selector=original,
            suggested_selector=suggested,
            confidence=0.5,
            explanation="Mock suggestion based on common patterns",
            tokens_used=0
        )


async def main():
    """CLI for testing AI repair"""
    import sys
    
    if len(sys.argv) < 3:
        print("Usage: python ai_repair.py <selector> <html_file>")
        print("       python ai_repair.py '.old-class' page.html")
        return
    
    selector = sys.argv[1]
    html_file = sys.argv[2]
    
    with open(html_file, 'r', encoding='utf-8') as f:
        html = f.read()
    
    context = RepairContext(
        selector_name="test",
        original_selector=selector,
        expected_value=None,
        expected_pattern=None,
        html_context=html[:2000],
        page_type="detail"
    )
    
    # Use mock for testing without API
    repair = MockAIRepair()
    suggestion = await repair.repair_selector(context)
    
    print(f"Original: {suggestion.original_selector}")
    print(f"Suggested: {suggestion.suggested_selector}")
    print(f"Confidence: {suggestion.confidence}")
    print(f"Explanation: {suggestion.explanation}")
    print(f"Tokens: {suggestion.tokens_used}")


if __name__ == "__main__":
    import asyncio
    asyncio.run(main())
