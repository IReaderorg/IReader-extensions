#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Quick Start - Interactive setup and first run
"""

import sys
import os
import subprocess
from pathlib import Path

def check_api_key():
    """Check if Gemini API key is set"""
    api_key = os.getenv('GEMINI_API_KEY')
    if not api_key:
        print("❌ GEMINI_API_KEY not set")
        print()
        print("To set it:")
        print("  Windows PowerShell:")
        print("    $env:GEMINI_API_KEY='your-key-here'")
        print()
        print("  Linux/Mac:")
        print("    export GEMINI_API_KEY='your-key-here'")
        print()
        print("Get your key at: https://makersuite.google.com/app/apikey")
        print()
        return False
    
    print(f"✅ API Key: {api_key[:10]}...{api_key[-4:]}")
    return True

def check_plugins_dir():
    """Check if plugins directory exists"""
    common_paths = [
        Path("lnreader-plugins-master/plugins/english"),
        Path("lnreader-plugins-plugins-v3.0.0/plugins/english"),
        Path("plugins/english"),
    ]
    
    for path in common_paths:
        if path.exists():
            print(f"✅ Plugins: {path}")
            return path
    
    print("❌ Plugins directory not found")
    print()
    print("Expected locations:")
    for path in common_paths:
        print(f"  - {path}")
    print()
    return None

def check_test_module():
    """Check if test module is enabled"""
    settings_file = Path("settings.gradle.kts")
    if not settings_file.exists():
        print("❌ settings.gradle.kts not found")
        return False
    
    content = settings_file.read_text(encoding='utf-8')
    if 'include(":test-extensions")' in content:
        print("✅ Test module: Enabled")
        return True
    else:
        print("⚠️  Test module: Not enabled")
        print("   Run: Uncomment include(':test-extensions') in settings.gradle.kts")
        return False

def main():
    print("╔" + "═" * 68 + "╗")
    print("║" + " 🚀 BATCH TEST & FIX SYSTEM - QUICK START".center(68) + "║")
    print("╚" + "═" * 68 + "╝")
    print()
    
    print("📋 Checking prerequisites...")
    print()
    
    # Check API key
    if not check_api_key():
        sys.exit(1)
    
    # Check plugins directory
    plugins_dir = check_plugins_dir()
    if not plugins_dir:
        sys.exit(1)
    
    # Check test module
    if not check_test_module():
        print()
        print("⚠️  Test module not enabled. Enable it first!")
        sys.exit(1)
    
    print()
    print("✅ All prerequisites met!")
    print()
    
    # Ask user what to do
    print("What would you like to do?")
    print()
    print("  1. Quick test (5 sources)")
    print("  2. Medium test (10 sources)")
    print("  3. Large test (20 sources)")
    print("  4. Test all sources")
    print("  5. Test existing sources only")
    print("  6. View dashboard")
    print("  7. Exit")
    print()
    
    try:
        choice = input("Choose option (1-7): ").strip()
    except KeyboardInterrupt:
        print("\n\n⚠️  Cancelled")
        sys.exit(0)
    
    print()
    
    if choice == '1':
        limit = 5
        print(f"🚀 Starting quick test with {limit} sources...")
        print()
        subprocess.run([
            sys.executable,
            "scripts/batch_test_fix_system.py",
            str(plugins_dir),
            "en",
            "--limit", str(limit)
        ])
    
    elif choice == '2':
        limit = 10
        print(f"🚀 Starting medium test with {limit} sources...")
        print()
        subprocess.run([
            sys.executable,
            "scripts/batch_test_fix_system.py",
            str(plugins_dir),
            "en",
            "--limit", str(limit)
        ])
    
    elif choice == '3':
        limit = 20
        print(f"🚀 Starting large test with {limit} sources...")
        print()
        subprocess.run([
            sys.executable,
            "scripts/batch_test_fix_system.py",
            str(plugins_dir),
            "en",
            "--limit", str(limit)
        ])
    
    elif choice == '4':
        print("🚀 Starting full test (all sources)...")
        print()
        print("⚠️  This may take a while!")
        print()
        confirm = input("Continue? (y/n): ").strip().lower()
        if confirm == 'y':
            subprocess.run([
                sys.executable,
                "scripts/batch_test_fix_system.py",
                str(plugins_dir),
                "en"
            ])
        else:
            print("Cancelled")
    
    elif choice == '5':
        print("🧪 Testing existing sources...")
        print()
        subprocess.run([
            sys.executable,
            "scripts/batch_test_fix_system.py",
            "--test-only",
            "en",
            "sources-v5-batch"
        ])
    
    elif choice == '6':
        print("📊 Loading dashboard...")
        print()
        subprocess.run([
            sys.executable,
            "scripts/test_dashboard.py"
        ])
    
    elif choice == '7':
        print("👋 Goodbye!")
        sys.exit(0)
    
    else:
        print("❌ Invalid choice")
        sys.exit(1)
    
    print()
    print("─" * 70)
    print()
    print("💡 Next steps:")
    print("   - View dashboard: python scripts/test_dashboard.py")
    print("   - Fix sources: python scripts/fix_single_source.py <source> en")
    print("   - Re-test: python scripts/batch_test_fix_system.py --test-only en sources-v5-batch")
    print()

if __name__ == "__main__":
    main()
