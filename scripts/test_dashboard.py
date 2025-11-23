#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Test Dashboard - Monitor testing progress
Shows real-time statistics and status
"""

import sys
import json
from pathlib import Path
from datetime import datetime

def load_results(results_file: Path):
    """Load test results"""
    if not results_file.exists():
        return None
    
    try:
        with open(results_file, 'r', encoding='utf-8') as f:
            return json.load(f)
    except:
        return None

def print_dashboard(results):
    """Print dashboard"""
    print("\033[2J\033[H")  # Clear screen
    
    print("╔" + "═" * 68 + "╗")
    print("║" + " 📊 TEST DASHBOARD".center(68) + "║")
    print("╚" + "═" * 68 + "╝")
    print()
    
    if not results:
        print("  ⚠️  No test results found")
        print("  Run: python scripts/batch_test_fix_system.py")
        print()
        return
    
    # Summary
    total = results['total']
    passed = results['passed']
    failed = results['failed']
    success_rate = (passed / total * 100) if total > 0 else 0
    
    print(f"  📅 Last Run: {results['timestamp']}")
    print()
    
    print("  📈 Overall Statistics:")
    print(f"     Total Sources:  {total}")
    print(f"     ✅ Passed:      {passed} ({success_rate:.1f}%)")
    print(f"     ❌ Failed:      {failed}")
    print()
    
    # Progress bar
    bar_width = 50
    filled = int(bar_width * passed / total) if total > 0 else 0
    bar = "█" * filled + "░" * (bar_width - filled)
    print(f"  [{bar}] {success_rate:.1f}%")
    print()
    
    # Test details
    total_tests = sum(r['tests_run'] for r in results['results'])
    total_passed_tests = sum(r['tests_passed'] for r in results['results'])
    total_failed_tests = sum(r['tests_failed'] for r in results['results'])
    
    print("  🧪 Test Details:")
    print(f"     Total Tests:    {total_tests}")
    print(f"     ✅ Passed:      {total_passed_tests}")
    print(f"     ❌ Failed:      {total_failed_tests}")
    print()
    
    # Failed sources
    if failed > 0:
        print(f"  ❌ Failed Sources ({failed}):")
        print()
        
        failed_results = [r for r in results['results'] if not r['success']]
        for i, result in enumerate(failed_results[:10], 1):  # Show max 10
            source_name = result['source_name']
            tests_failed = result['tests_failed']
            
            print(f"     {i:2}. {source_name:<30} ({tests_failed} tests failed)")
            
            # Show first error
            if result['errors']:
                error = result['errors'][0][:50]
                print(f"         └─ {error}...")
        
        if len(failed_results) > 10:
            print(f"     ... and {len(failed_results) - 10} more")
        
        print()
    
    # Passed sources
    if passed > 0:
        print(f"  ✅ Passed Sources ({passed}):")
        print()
        
        passed_results = [r for r in results['results'] if r['success']]
        
        # Show first 5
        for i, result in enumerate(passed_results[:5], 1):
            source_name = result['source_name']
            tests_passed = result['tests_passed']
            duration = result.get('duration', 0)
            
            print(f"     {i}. {source_name:<30} ({tests_passed} tests, {duration:.1f}s)")
        
        if len(passed_results) > 5:
            print(f"     ... and {len(passed_results) - 5} more")
        
        print()
    
    # Next steps
    print("  💡 Next Steps:")
    if failed > 0:
        print("     1. Review: test-results/fix_report.md")
        print("     2. Fix: python scripts/fix_single_source.py <source> en")
        print("     3. Re-test: python scripts/batch_test_fix_system.py --test-only en sources-v5-batch")
    else:
        print("     🎉 All tests passing! Great job!")
    print()
    
    print("─" * 70)
    print()

def main():
    workspace_root = Path.cwd()
    results_file = workspace_root / "test-results" / "test_results.json"
    
    results = load_results(results_file)
    print_dashboard(results)

if __name__ == "__main__":
    main()
