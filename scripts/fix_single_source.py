#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Fix Single Source - Interactive fixer for individual sources
Runs tests, shows errors, and helps apply fixes
"""

import sys
import io
import os
import re
import subprocess
from pathlib import Path
from typing import Optional, List, Dict

# Fix Windows console encoding
if sys.platform == 'win32':
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

class SourceFixer:
    """Interactive source fixer"""
    
    def __init__(self, source_name: str, lang: str, workspace_root: Path):
        self.source_name = source_name
        self.lang = lang
        self.workspace_root = workspace_root
        self.source_dir = workspace_root / "sources-v5-batch" / lang / source_name
        self.test_module = workspace_root / "test-extensions"
        
        if not self.source_dir.exists():
            raise FileNotFoundError(f"Source not found: {self.source_dir}")
    
    def run_test(self) -> Dict:
        """Run test and return results"""
        print("🧪 Running tests...")
        print()
        
        # Configure test module
        self._configure_test_module()
        
        # Run gradle test with test module enabled
        # Use .\gradlew.bat on Windows, ./gradlew on Unix
        if sys.platform == 'win32':
            gradle_cmd = ".\\gradlew.bat"
        else:
            gradle_cmd = "./gradlew"
        
        # Enable test module via environment variable
        env = os.environ.copy()
        env['ENABLE_TEST_MODULE'] = 'true'
        
        result = subprocess.run(
            [gradle_cmd, ":test-extensions:test", "--rerun-tasks", "--info"],
            capture_output=True,
            text=True,
            encoding='utf-8',
            errors='replace',
            timeout=180,
            cwd=str(self.workspace_root),
            shell=True,  # Use shell to handle path resolution
            env=env
        )
        
        # Parse results
        success = "BUILD SUCCESSFUL" in result.stdout
        errors = self._extract_errors(result.stdout, result.stderr)
        
        return {
            'success': success,
            'errors': errors,
            'stdout': result.stdout,
            'stderr': result.stderr
        }
    
    def _configure_test_module(self):
        """Configure test module for this source"""
        # Find source info
        src_dir = self.source_dir / "main" / "src" / "ireader"
        package_dirs = [d for d in src_dir.iterdir() if d.is_dir()]
        if not package_dirs:
            raise Exception("No package directory found")
        
        package_dir = package_dirs[0]
        package_name = f"ireader.{package_dir.name}"
        
        kt_files = list(package_dir.glob("*.kt"))
        if not kt_files:
            raise Exception("No Kotlin file found")
        
        class_name = kt_files[0].stem
        
        # Read Kotlin file to get baseUrl
        content = kt_files[0].read_text(encoding='utf-8')
        base_url_match = re.search(r'override val baseUrl: String get\(\) = "([^"]+)"', content)
        base_url = base_url_match.group(1) if base_url_match else "https://example.com"
        
        # Update build.gradle.kts
        build_gradle = self.test_module / "build.gradle.kts"
        build_content = build_gradle.read_text(encoding='utf-8')
        
        project_path = f":extensions:v5:{self.lang}:{self.source_name}"
        build_content = re.sub(
            r'//implementation\(project\(".*?"\)\)',
            f'implementation(project("{project_path}"))',
            build_content
        )
        build_content = re.sub(
            r'implementation\(project\(":extensions:.*?"\)\)',
            f'implementation(project("{project_path}"))',
            build_content
        )
        
        build_gradle.write_text(build_content, encoding='utf-8')
        
        # Update Constants.kt
        constants_file = self.test_module / "src" / "main" / "java" / "ireader" / "constants" / "Constants.kt"
        constants_file.parent.mkdir(parents=True, exist_ok=True)
        
        constants_content = f'''package ireader.constants

import ireader.core.source.Dependencies
import ireader.core.source.HttpSource
import {package_name}.{class_name}
import ireader.utility.TestConstants

object Constants : TestConstants {{
    override val bookUrl: String
        get() = "{base_url}/novel/test"
    override val bookName: String
        get() = "Test Novel"
    override val chapterUrl: String
        get() = "{base_url}/novel/test/chapter-1"
    override val chapterName: String
        get() = "Chapter 1"

    override fun getExtension(deps: Dependencies): HttpSource {{
        return object : {class_name}(deps) {{
            // Test instance
        }}
    }}
}}
'''
        constants_file.write_text(constants_content, encoding='utf-8')
        
        print(f"✓ Configured test for: {class_name}")
        print(f"✓ Base URL: {base_url}")
        print()
    
    def _extract_errors(self, stdout: str, stderr: str) -> List[str]:
        """Extract error messages"""
        errors = []
        
        # Common error patterns
        patterns = [
            r'FAILURE: (.+)',
            r'> (.+) FAILED',
            r'Exception: (.+)',
            r'Error: (.+)',
            r'Caused by: (.+)',
        ]
        
        combined = stdout + "\n" + stderr
        
        for pattern in patterns:
            matches = re.findall(pattern, combined)
            errors.extend(matches)
        
        # Deduplicate
        seen = set()
        unique_errors = []
        for error in errors:
            if error not in seen:
                seen.add(error)
                unique_errors.append(error)
        
        return unique_errors[:10]  # Limit to 10
    
    def suggest_fixes(self, errors: List[str]) -> List[str]:
        """Suggest fixes based on errors"""
        suggestions = []
        
        error_text = " ".join(errors).lower()
        
        # Pattern-based suggestions
        if "selector" in error_text or "element not found" in error_text:
            suggestions.append("🔍 Selector Issue Detected")
            suggestions.append("   - Open the website in browser")
            suggestions.append("   - Use DevTools to inspect elements")
            suggestions.append("   - Update CSS selectors in the source")
            suggestions.append("   - Check if website structure changed")
        
        if "null" in error_text or "nullpointer" in error_text:
            suggestions.append("⚠️  Null Safety Issue")
            suggestions.append("   - Add null checks: ?.let { }")
            suggestions.append("   - Use safe calls: selectFirst()?.text()")
            suggestions.append("   - Provide default values: ?: \"\"")
        
        if "json" in error_text or "parse" in error_text:
            suggestions.append("📄 JSON Parsing Issue")
            suggestions.append("   - Verify API response format")
            suggestions.append("   - Add ignoreUnknownKeys = true")
            suggestions.append("   - Make all data class fields nullable")
            suggestions.append("   - Check API endpoint URL")
        
        if "timeout" in error_text or "connection" in error_text:
            suggestions.append("🌐 Network Issue")
            suggestions.append("   - Check if website is accessible")
            suggestions.append("   - Verify baseUrl is correct")
            suggestions.append("   - Test URL in browser")
            suggestions.append("   - Check for cloudflare protection")
        
        if "404" in error_text or "not found" in error_text:
            suggestions.append("🔗 URL Issue")
            suggestions.append("   - Check URL construction")
            suggestions.append("   - Verify baseUrl has/doesn't have trailing slash")
            suggestions.append("   - Check relative URL handling")
            suggestions.append("   - Test URLs manually")
        
        if not suggestions:
            suggestions.append("🤔 General Debugging Steps")
            suggestions.append("   - Review the Kotlin source code")
            suggestions.append("   - Compare with TypeScript original")
            suggestions.append("   - Check test URLs are valid")
            suggestions.append("   - Run tests with --info flag")
        
        return suggestions
    
    def apply_common_fixes(self):
        """Apply common fixes automatically"""
        print("🔧 Applying common fixes...")
        print()
        
        # Find Kotlin source file
        src_dir = self.source_dir / "main" / "src" / "ireader"
        package_dirs = [d for d in src_dir.iterdir() if d.is_dir()]
        if not package_dirs:
            return
        
        kt_files = list(package_dirs[0].glob("*.kt"))
        if not kt_files:
            return
        
        kt_file = kt_files[0]
        content = kt_file.read_text(encoding='utf-8')
        original_content = content
        
        fixes_applied = []
        
        # Fix 1: Replace .asText() with .bodyAsText()
        if '.asText()' in content:
            content = content.replace('.asText()', '.bodyAsText()')
            fixes_applied.append("✓ Fixed .asText() -> .bodyAsText()")
        
        # Fix 2: Add null safety to selectors
        # Pattern: .select("selector").text()
        # Replace: .select("selector").firstOrNull()?.text() ?: ""
        unsafe_patterns = [
            (r'\.select\("([^"]+)"\)\.text\(\)', r'.select("\1").firstOrNull()?.text() ?: ""'),
            (r'\.select\("([^"]+)"\)\.attr\("([^"]+)"\)', r'.select("\1").firstOrNull()?.attr("\2") ?: ""'),
        ]
        
        for pattern, replacement in unsafe_patterns:
            if re.search(pattern, content):
                content = re.sub(pattern, replacement, content)
                fixes_applied.append(f"✓ Added null safety to selectors")
                break
        
        # Fix 3: Fix relative URLs
        if 'key = novel.slug' in content:
            content = content.replace('key = novel.slug', 'key = "$baseUrl/series/${novel.slug}"')
            fixes_applied.append("✓ Fixed relative URLs in manga.key")
        
        # Fix 4: Add missing imports
        required_imports = [
            'import io.ktor.client.request.*',
            'import io.ktor.client.statement.*',
            'import ireader.core.source.asJsoup',
        ]
        
        for imp in required_imports:
            if imp not in content:
                # Add after package line
                content = re.sub(
                    r'(package\s+[\w.]+\s*\n)',
                    f'\\1\n{imp}',
                    content,
                    count=1
                )
                fixes_applied.append(f"✓ Added import: {imp.split()[-1]}")
        
        # Write back if changes were made
        if content != original_content:
            kt_file.write_text(content, encoding='utf-8')
            print(f"📝 Applied {len(fixes_applied)} fixes:")
            for fix in fixes_applied:
                print(f"   {fix}")
            print()
            return True
        else:
            print("   No automatic fixes available")
            print()
            return False
    
    def interactive_fix(self):
        """Interactive fixing session"""
        print("╔" + "═" * 68 + "╗")
        print("║" + f" 🔧 FIXING: {self.source_name}".center(68) + "║")
        print("╚" + "═" * 68 + "╝")
        print()
        
        iteration = 1
        max_iterations = 5
        
        while iteration <= max_iterations:
            print(f"🔄 Iteration {iteration}/{max_iterations}")
            print("─" * 70)
            print()
            
            # Run test
            result = self.run_test()
            
            if result['success']:
                print("✅ ALL TESTS PASSED!")
                print()
                print("🎉 Source is working correctly!")
                return True
            
            # Show errors
            print("❌ Tests failed")
            print()
            print("📋 Errors:")
            for i, error in enumerate(result['errors'][:5], 1):
                print(f"   {i}. {error}")
            print()
            
            # Suggest fixes
            suggestions = self.suggest_fixes(result['errors'])
            print("💡 Suggested Fixes:")
            for suggestion in suggestions:
                print(f"   {suggestion}")
            print()
            
            # Try automatic fixes
            if iteration == 1:
                if self.apply_common_fixes():
                    iteration += 1
                    continue
            
            # Ask user
            print("Options:")
            print("  1. Try automatic fixes again")
            print("  2. Open source file for manual editing")
            print("  3. Show full test output")
            print("  4. Skip to next source")
            print("  5. Exit")
            print()
            
            try:
                choice = input("Choose option (1-5): ").strip()
                
                if choice == '1':
                    self.apply_common_fixes()
                elif choice == '2':
                    kt_file = list((self.source_dir / "main" / "src" / "ireader").rglob("*.kt"))[0]
                    print(f"\n📝 Edit: {kt_file}")
                    print("   Press Enter when done...")
                    input()
                elif choice == '3':
                    print("\n" + "─" * 70)
                    print(result['stdout'])
                    print("─" * 70)
                    input("\nPress Enter to continue...")
                elif choice == '4':
                    return False
                elif choice == '5':
                    sys.exit(0)
            except KeyboardInterrupt:
                print("\n\n⚠️  Interrupted")
                return False
            
            iteration += 1
            print()
        
        print("⚠️  Max iterations reached")
        print("   Manual intervention required")
        return False

def main():
    import argparse
    
    parser = argparse.ArgumentParser(
        description='Fix a single source interactively',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog='''
Examples:
  python scripts/fix_single_source.py novelbuddy en
  python scripts/fix_single_source.py readnovelfull en --auto-fix
        '''
    )
    
    parser.add_argument('source_name', help='Source name (e.g., novelbuddy)')
    parser.add_argument('lang', help='Language code (e.g., en)')
    parser.add_argument('--auto-fix', action='store_true',
                       help='Apply automatic fixes without interaction')
    
    args = parser.parse_args()
    
    workspace_root = Path.cwd()
    
    try:
        fixer = SourceFixer(args.source_name, args.lang, workspace_root)
        
        if args.auto_fix:
            # Just apply fixes and test once
            fixer.apply_common_fixes()
            result = fixer.run_test()
            
            if result['success']:
                print("✅ Tests passed!")
                sys.exit(0)
            else:
                print("❌ Tests still failing")
                print("\nErrors:")
                for error in result['errors'][:5]:
                    print(f"  - {error}")
                sys.exit(1)
        else:
            # Interactive mode
            success = fixer.interactive_fix()
            sys.exit(0 if success else 1)
    
    except Exception as e:
        print(f"❌ Error: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

if __name__ == "__main__":
    main()
