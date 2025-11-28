#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Batch Test & Fix System
Generates sources, tests them, and helps fix issues iteratively

Workflow:
1. Generate batch sources using AI converter
2. Run tests for each source
3. Collect errors and failures
4. Provide AI-assisted fixes
5. Repeat until all pass
"""

import sys
import io
import os
import re
import json
import subprocess
import time
from pathlib import Path
from typing import List, Dict, Tuple, Optional
from dataclasses import dataclass, asdict

# Fix Windows console encoding
if sys.platform == 'win32':
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

@dataclass
class TestResult:
    """Test result for a single source"""
    source_name: str
    lang: str
    success: bool
    tests_run: int = 0
    tests_passed: int = 0
    tests_failed: int = 0
    errors: List[str] = None
    duration: float = 0.0
    
    def __post_init__(self):
        if self.errors is None:
            self.errors = []

@dataclass
class SourceInfo:
    """Information about a source to test"""
    name: str
    lang: str
    path: Path
    package_name: str
    class_name: str
    test_urls: Dict[str, str] = None
    
    def __post_init__(self):
        if self.test_urls is None:
            self.test_urls = {}

class BatchTestFixSystem:
    """Main system for batch testing and fixing"""
    
    def __init__(self, workspace_root: Path):
        self.workspace_root = workspace_root
        self.test_module = workspace_root / "test-extensions"
        self.results_dir = workspace_root / "test-results"
        self.results_dir.mkdir(exist_ok=True)
        
    def generate_sources(self, plugins_dir: Path, lang: str, output_dir: Path, limit: Optional[int] = None) -> List[SourceInfo]:
        """Step 1: Generate sources using AI converter"""
        print("╔" + "═" * 68 + "╗")
        print("║" + " 🤖 STEP 1: GENERATING SOURCES".center(68) + "║")
        print("╚" + "═" * 68 + "╝")
        print()
        
        # Find TypeScript files
        ts_files = list(plugins_dir.glob("*.ts"))
        if not ts_files:
            lang_dir = plugins_dir / lang
            if lang_dir.exists():
                ts_files = list(lang_dir.glob("*.ts"))
        
        if limit:
            ts_files = ts_files[:limit]
        
        print(f"📁 Found {len(ts_files)} plugins to convert")
        print(f"🌍 Language: {lang}")
        print(f"📂 Output: {output_dir}")
        print()
        
        generated_sources = []
        
        for i, ts_file in enumerate(ts_files, 1):
            plugin_name = ts_file.stem
            print(f"[{i}/{len(ts_files)}] {plugin_name:<30}", end=" ", flush=True)
            
            # Check if already exists
            source_dir = output_dir / lang / plugin_name
            if source_dir.exists():
                print("⏭️  SKIP (exists)")
                # Still add to list for testing
                source_info = self._extract_source_info(source_dir, lang, plugin_name)
                if source_info:
                    generated_sources.append(source_info)
                continue
            
            # Generate using AI converter
            try:
                result = subprocess.run(
                    [
                        sys.executable,
                        str(self.workspace_root / "scripts" / "js-to-kotlin-v5-ai.py"),
                        str(ts_file),
                        lang,
                        str(output_dir)
                    ],
                    capture_output=True,
                    text=True,
                    encoding='utf-8',
                    errors='replace',
                    timeout=120
                )
                
                if result.returncode == 0:
                    print("✅ GENERATED")
                    source_info = self._extract_source_info(source_dir, lang, plugin_name)
                    if source_info:
                        generated_sources.append(source_info)
                else:
                    print(f"❌ FAILED")
                    print(f"    Error: {result.stderr[:100]}")
            
            except subprocess.TimeoutExpired:
                print("❌ TIMEOUT")
            except Exception as e:
                print(f"❌ ERROR: {e}")
        
        print()
        print(f"✅ Generated/Found {len(generated_sources)} sources")
        print()
        
        return generated_sources
    
    def _extract_source_info(self, source_dir: Path, lang: str, plugin_name: str) -> Optional[SourceInfo]:
        """Extract information about a generated source"""
        # Find the Kotlin source file
        src_dir = source_dir / "main" / "src" / "ireader"
        if not src_dir.exists():
            return None
        
        # Find package directory
        package_dirs = [d for d in src_dir.iterdir() if d.is_dir()]
        if not package_dirs:
            return None
        
        package_dir = package_dirs[0]
        package_name = f"ireader.{package_dir.name}"
        
        # Find Kotlin file
        kt_files = list(package_dir.glob("*.kt"))
        if not kt_files:
            return None
        
        kt_file = kt_files[0]
        class_name = kt_file.stem
        
        # Try to extract test URLs from the source
        test_urls = self._extract_test_urls(kt_file)
        
        return SourceInfo(
            name=plugin_name,
            lang=lang,
            path=source_dir,
            package_name=package_name,
            class_name=class_name,
            test_urls=test_urls
        )
    
    def _extract_test_urls(self, kt_file: Path) -> Dict[str, str]:
        """Extract test URLs from Kotlin source"""
        try:
            content = kt_file.read_text(encoding='utf-8')
            
            # Extract baseUrl
            base_url_match = re.search(r'override val baseUrl: String get\(\) = "([^"]+)"', content)
            base_url = base_url_match.group(1) if base_url_match else ""
            
            return {
                'base_url': base_url,
                'book_url': f"{base_url}/novel/test-novel",  # Generic test URL
                'book_name': "Test Novel",
                'chapter_url': f"{base_url}/novel/test-novel/chapter-1",
                'chapter_name': "Chapter 1"
            }
        except:
            return {}
    
    def run_tests(self, sources: List[SourceInfo]) -> List[TestResult]:
        """Step 2: Run tests for all sources"""
        print("╔" + "═" * 68 + "╗")
        print("║" + " 🧪 STEP 2: RUNNING TESTS".center(68) + "║")
        print("╚" + "═" * 68 + "╝")
        print()
        
        results = []
        
        for i, source in enumerate(sources, 1):
            print(f"[{i}/{len(sources)}] Testing {source.name:<30}", end=" ", flush=True)
            
            result = self._test_single_source(source)
            results.append(result)
            
            if result.success:
                print(f"✅ PASS ({result.tests_passed}/{result.tests_run})")
            else:
                print(f"❌ FAIL ({result.tests_passed}/{result.tests_run})")
                if result.errors:
                    print(f"    {result.errors[0][:60]}...")
        
        print()
        self._print_test_summary(results)
        
        return results
    
    def _test_single_source(self, source: SourceInfo) -> TestResult:
        """Test a single source"""
        start_time = time.time()
        
        try:
            # Step 1: Update test configuration
            self._configure_test_module(source)
            
            # Step 2: Run gradle test with test module enabled
            # Use .\gradlew.bat on Windows, ./gradlew on Unix
            if sys.platform == 'win32':
                gradle_cmd = ".\\gradlew.bat"
            else:
                gradle_cmd = "./gradlew"
            
            # Enable test module via environment variable
            env = os.environ.copy()
            env['ENABLE_TEST_MODULE'] = 'true'
            
            result = subprocess.run(
                [gradle_cmd, ":test-extensions:test", "--rerun-tasks"],
                capture_output=True,
                text=True,
                encoding='utf-8',
                errors='replace',
                timeout=180,
                cwd=str(self.workspace_root),
                shell=True,  # Use shell to handle path resolution
                env=env
            )
            
            duration = time.time() - start_time
            
            # Parse test results
            test_result = self._parse_test_output(result.stdout, result.stderr, source)
            test_result.duration = duration
            
            return test_result
        
        except subprocess.TimeoutExpired:
            return TestResult(
                source_name=source.name,
                lang=source.lang,
                success=False,
                errors=["Test timeout (>180s)"],
                duration=180.0
            )
        except Exception as e:
            return TestResult(
                source_name=source.name,
                lang=source.lang,
                success=False,
                errors=[str(e)],
                duration=time.time() - start_time
            )
    
    def _configure_test_module(self, source: SourceInfo):
        """Configure test module to test specific source"""
        # Update build.gradle.kts
        build_gradle = self.test_module / "build.gradle.kts"
        content = build_gradle.read_text(encoding='utf-8')
        
        # Update implementation line
        project_path = f":extensions:v5:{source.lang}:{source.name}"
        content = re.sub(
            r'//implementation\(project\(".*?"\)\)',
            f'implementation(project("{project_path}"))',
            content
        )
        content = re.sub(
            r'implementation\(project\(":extensions:.*?"\)\)',
            f'implementation(project("{project_path}"))',
            content
        )
        
        build_gradle.write_text(content, encoding='utf-8')
        
        # Update Constants.kt
        constants_file = self.test_module / "src" / "main" / "java" / "ireader" / "constants" / "Constants.kt"
        constants_file.parent.mkdir(parents=True, exist_ok=True)
        
        constants_content = f'''package ireader.constants

import ireader.core.source.Dependencies
import ireader.core.source.HttpSource
import {source.package_name}.{source.class_name}
import ireader.utility.TestConstants

object Constants : TestConstants {{
    override val bookUrl: String
        get() = "{source.test_urls.get('book_url', 'https://example.com/novel/test')}"
    override val bookName: String
        get() = "{source.test_urls.get('book_name', 'Test Novel')}"
    override val chapterUrl: String
        get() = "{source.test_urls.get('chapter_url', 'https://example.com/novel/test/chapter-1')}"
    override val chapterName: String
        get() = "{source.test_urls.get('chapter_name', 'Chapter 1')}"

    override fun getExtension(deps: Dependencies): HttpSource {{
        return object : {source.class_name}(deps) {{
            // Test instance
        }}
    }}
}}
'''
        constants_file.write_text(constants_content, encoding='utf-8')
    
    def _parse_test_output(self, stdout: str, stderr: str, source: SourceInfo) -> TestResult:
        """Parse gradle test output"""
        errors = []
        tests_run = 0
        tests_passed = 0
        tests_failed = 0
        
        # Look for test results
        if "BUILD SUCCESSFUL" in stdout:
            # Parse test counts
            test_match = re.search(r'(\d+) tests completed, (\d+) failed', stdout)
            if test_match:
                tests_run = int(test_match.group(1))
                tests_failed = int(test_match.group(2))
                tests_passed = tests_run - tests_failed
            else:
                # Assume all passed if no failures mentioned
                test_match = re.search(r'(\d+) tests completed', stdout)
                if test_match:
                    tests_run = int(test_match.group(1))
                    tests_passed = tests_run
            
            success = tests_failed == 0
        else:
            success = False
            
            # Extract errors
            error_patterns = [
                r'FAILURE: (.+)',
                r'> (.+) FAILED',
                r'Exception: (.+)',
                r'Error: (.+)',
            ]
            
            for pattern in error_patterns:
                matches = re.findall(pattern, stdout + stderr)
                errors.extend(matches[:5])  # Limit to 5 errors
        
        return TestResult(
            source_name=source.name,
            lang=source.lang,
            success=success,
            tests_run=tests_run,
            tests_passed=tests_passed,
            tests_failed=tests_failed,
            errors=errors
        )
    
    def _print_test_summary(self, results: List[TestResult]):
        """Print test summary"""
        total = len(results)
        passed = sum(1 for r in results if r.success)
        failed = total - passed
        
        total_tests = sum(r.tests_run for r in results)
        total_passed = sum(r.tests_passed for r in results)
        total_failed = sum(r.tests_failed for r in results)
        
        print("╔" + "═" * 68 + "╗")
        print("║" + " 📊 TEST SUMMARY".center(68) + "║")
        print("╚" + "═" * 68 + "╝")
        print()
        print(f"  Sources:")
        print(f"    ✅ Passed: {passed}/{total} ({passed/total*100:.1f}%)")
        print(f"    ❌ Failed: {failed}/{total}")
        print()
        print(f"  Tests:")
        print(f"    ✅ Passed: {total_passed}/{total_tests}")
        print(f"    ❌ Failed: {total_failed}/{total_tests}")
        print()
        
        if failed > 0:
            print(f"  ❌ Failed Sources:")
            for result in results:
                if not result.success:
                    print(f"    • {result.source_name}")
                    if result.errors:
                        print(f"      {result.errors[0][:60]}")
            print()
    
    def save_results(self, results: List[TestResult], filename: str = "test_results.json"):
        """Save test results to JSON"""
        output_file = self.results_dir / filename
        
        data = {
            'timestamp': time.strftime('%Y-%m-%d %H:%M:%S'),
            'total': len(results),
            'passed': sum(1 for r in results if r.success),
            'failed': sum(1 for r in results if not r.success),
            'results': [asdict(r) for r in results]
        }
        
        with open(output_file, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
        
        print(f"💾 Results saved to: {output_file}")
        print()
    
    def generate_fix_report(self, results: List[TestResult]) -> Path:
        """Generate a detailed fix report"""
        report_file = self.results_dir / "fix_report.md"
        
        failed_results = [r for r in results if not r.success]
        
        with open(report_file, 'w', encoding='utf-8') as f:
            f.write("# Test Failure Report\n\n")
            f.write(f"Generated: {time.strftime('%Y-%m-%d %H:%M:%S')}\n\n")
            f.write(f"## Summary\n\n")
            f.write(f"- Total Sources: {len(results)}\n")
            f.write(f"- Failed: {len(failed_results)}\n")
            f.write(f"- Success Rate: {(len(results)-len(failed_results))/len(results)*100:.1f}%\n\n")
            
            f.write("## Failed Sources\n\n")
            
            for result in failed_results:
                f.write(f"### {result.source_name}\n\n")
                f.write(f"- Language: {result.lang}\n")
                f.write(f"- Tests Run: {result.tests_run}\n")
                f.write(f"- Tests Failed: {result.tests_failed}\n")
                f.write(f"- Duration: {result.duration:.1f}s\n\n")
                
                if result.errors:
                    f.write("**Errors:**\n\n")
                    for error in result.errors:
                        f.write(f"- {error}\n")
                    f.write("\n")
                
                f.write("**Suggested Fixes:**\n\n")
                f.write(self._suggest_fixes(result))
                f.write("\n---\n\n")
        
        print(f"📝 Fix report generated: {report_file}")
        print()
        
        return report_file
    
    def _suggest_fixes(self, result: TestResult) -> str:
        """Suggest fixes based on error patterns"""
        suggestions = []
        
        error_text = " ".join(result.errors).lower()
        
        # Common error patterns and fixes
        if "timeout" in error_text or "connection" in error_text:
            suggestions.append("- Check network connectivity and baseUrl")
            suggestions.append("- Verify the website is accessible")
            suggestions.append("- Consider increasing timeout values")
        
        if "selector" in error_text or "element not found" in error_text:
            suggestions.append("- Verify CSS selectors match the website structure")
            suggestions.append("- Check if website HTML has changed")
            suggestions.append("- Use browser DevTools to inspect elements")
        
        if "null" in error_text or "nullpointer" in error_text:
            suggestions.append("- Add null safety checks")
            suggestions.append("- Verify selectors return elements")
            suggestions.append("- Check optional chaining (?.) usage")
        
        if "json" in error_text or "parse" in error_text:
            suggestions.append("- Verify JSON API response format")
            suggestions.append("- Check if API endpoint has changed")
            suggestions.append("- Add error handling for JSON parsing")
        
        if "404" in error_text or "not found" in error_text:
            suggestions.append("- Verify URL construction is correct")
            suggestions.append("- Check if baseUrl needs trailing slash")
            suggestions.append("- Ensure relative URLs are handled properly")
        
        if not suggestions:
            suggestions.append("- Review the source code manually")
            suggestions.append("- Compare with TypeScript original")
            suggestions.append("- Check test URLs are valid")
        
        return "\n".join(suggestions)
    
    def run_full_workflow(self, plugins_dir: Path, lang: str, output_dir: Path, limit: Optional[int] = None):
        """Run the complete workflow"""
        print("╔" + "═" * 68 + "╗")
        print("║" + " 🚀 BATCH TEST & FIX SYSTEM".center(68) + "║")
        print("╚" + "═" * 68 + "╝")
        print()
        print(f"📁 Plugins: {plugins_dir}")
        print(f"🌍 Language: {lang}")
        print(f"📂 Output: {output_dir}")
        if limit:
            print(f"🔢 Limit: {limit} sources")
        print()
        print("─" * 70)
        print()
        
        # Step 1: Generate sources
        sources = self.generate_sources(plugins_dir, lang, output_dir, limit)
        
        if not sources:
            print("❌ No sources generated or found")
            return
        
        # Step 2: Run tests
        results = self.run_tests(sources)
        
        # Step 3: Save results
        self.save_results(results)
        
        # Step 4: Generate fix report
        self.generate_fix_report(results)
        
        # Final summary
        passed = sum(1 for r in results if r.success)
        failed = len(results) - passed
        
        print("╔" + "═" * 68 + "╗")
        print("║" + " 🎉 WORKFLOW COMPLETE".center(68) + "║")
        print("╚" + "═" * 68 + "╝")
        print()
        print(f"  ✅ {passed} sources passed all tests")
        print(f"  ❌ {failed} sources need fixes")
        print()
        
        if failed > 0:
            print("  📝 Next steps:")
            print("     1. Review fix_report.md for suggested fixes")
            print("     2. Fix issues in the generated sources")
            print("     3. Re-run tests: python scripts/batch_test_fix_system.py --test-only")
            print()

def main():
    import argparse
    
    parser = argparse.ArgumentParser(
        description='Batch Test & Fix System for IReader Extensions',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog='''
Examples:
  # Full workflow: generate + test
  python scripts/batch_test_fix_system.py lnreader-plugins-master/plugins/english en
  
  # Limit to 5 sources
  python scripts/batch_test_fix_system.py lnreader-plugins-master/plugins/english en --limit 5
  
  # Test only (skip generation)
  python scripts/batch_test_fix_system.py --test-only en sources-v5-batch
  
  # Custom output directory
  python scripts/batch_test_fix_system.py plugins/english en --output ./my-sources
        '''
    )
    
    parser.add_argument('plugins_dir', nargs='?', type=Path,
                       help='Directory containing TypeScript plugins')
    parser.add_argument('lang', nargs='?',
                       help='Language code (e.g., en, es, fr)')
    parser.add_argument('--output', type=Path, default=Path('./sources-v5-batch'),
                       help='Output directory (default: ./sources-v5-batch)')
    parser.add_argument('--limit', type=int,
                       help='Limit number of sources to process')
    parser.add_argument('--test-only', action='store_true',
                       help='Only run tests on existing sources')
    
    args = parser.parse_args()
    
    workspace_root = Path.cwd()
    system = BatchTestFixSystem(workspace_root)
    
    if args.test_only:
        # Test existing sources
        if not args.lang or not args.output:
            print("❌ --test-only requires lang and output directory")
            print("   Example: python scripts/batch_test_fix_system.py --test-only en sources-v5-batch")
            sys.exit(1)
        
        # Find existing sources
        sources_dir = args.output / args.lang
        if not sources_dir.exists():
            print(f"❌ Directory not found: {sources_dir}")
            sys.exit(1)
        
        sources = []
        for source_dir in sources_dir.iterdir():
            if source_dir.is_dir():
                source_info = system._extract_source_info(source_dir, args.lang, source_dir.name)
                if source_info:
                    sources.append(source_info)
        
        if not sources:
            print(f"❌ No sources found in {sources_dir}")
            sys.exit(1)
        
        print(f"🧪 Testing {len(sources)} existing sources...")
        print()
        
        results = system.run_tests(sources)
        system.save_results(results)
        system.generate_fix_report(results)
    
    else:
        # Full workflow
        if not args.plugins_dir or not args.lang:
            parser.print_help()
            sys.exit(1)
        
        if not args.plugins_dir.exists():
            print(f"❌ Directory not found: {args.plugins_dir}")
            sys.exit(1)
        
        system.run_full_workflow(args.plugins_dir, args.lang, args.output, args.limit)

if __name__ == "__main__":
    main()
