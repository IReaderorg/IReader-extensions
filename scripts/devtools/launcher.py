#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
IReader Source Creator - Launcher
Easy-to-use launcher for the devtools workflow
"""

import sys
import io
import os
import json
import webbrowser
import subprocess
from pathlib import Path

# Fix Windows console encoding
if sys.platform == 'win32':
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')


def print_banner():
    print()
    print("╔" + "═" * 60 + "╗")
    print("║" + " 📚 IReader Source Creator - DevTools".center(60) + "║")
    print("╚" + "═" * 60 + "╝")
    print()


def print_menu():
    print("Choose an option:")
    print()
    print("  [1] 🌐 Open website & start selecting")
    print("  [2] 📄 Generate source from config file")
    print("  [3] 🔧 Install Chrome extension (instructions)")
    print("  [4] 📖 View documentation")
    print("  [5] 🧪 Test existing source")
    print("  [6] ❌ Exit")
    print()


def open_website():
    print("\n📝 Enter the website URL to create a source for:")
    print("   Examples: https://www.realmnovel.com")
    print("             https://rewayatfans.com")
    print()
    url = input("   URL: ").strip()
    
    if not url:
        print("   ⚠️ No URL provided")
        return
    
    if not url.startswith('http'):
        url = 'https://' + url
    
    print(f"\n🌐 Opening {url}...")
    print()
    print("📋 Instructions:")
    print("   1. Click the IReader extension icon in Chrome")
    print("   2. Enter the source name (e.g., 'RealmNovel')")
    print("   3. Navigate to different pages and select elements:")
    print("      • Novel list page → Select novel items")
    print("      • Novel detail page → Select title, author, cover, etc.")
    print("      • Chapter page → Select content")
    print("   4. Export the configuration when done")
    print("   5. Run option [2] to generate the Kotlin source")
    print()
    
    webbrowser.open(url)
    input("Press Enter when you've exported the config file...")


def generate_source():
    print("\n📄 Enter the path to your config JSON file:")
    print("   (or drag and drop the file here)")
    print()
    config_path = input("   Path: ").strip().strip('"').strip("'")
    
    if not config_path:
        print("   ⚠️ No path provided")
        return
    
    config_path = Path(config_path)
    if not config_path.exists():
        print(f"   ❌ File not found: {config_path}")
        return
    
    # Check for API key
    api_key = os.getenv('GEMINI_API_KEY')
    if not api_key:
        print("\n⚠️ GEMINI_API_KEY not set!")
        print("   Set it with: $env:GEMINI_API_KEY='your-key'")
        print()
        key = input("   Enter your Gemini API key (or press Enter to skip): ").strip()
        if key:
            os.environ['GEMINI_API_KEY'] = key
        else:
            print("   ❌ Cannot generate without API key")
            return
    
    # Run the generator
    script_dir = Path(__file__).parent
    generator_script = script_dir / "create_source_interactive.py"
    
    print(f"\n🚀 Generating source from {config_path.name}...")
    print()
    
    try:
        result = subprocess.run(
            [sys.executable, str(generator_script), str(config_path)],
            cwd=str(script_dir.parent.parent),  # Project root
            capture_output=False
        )
        if result.returncode != 0:
            print("\n❌ Generation failed. Check the error messages above.")
    except Exception as e:
        print(f"\n❌ Error: {e}")


def show_extension_instructions():
    print("\n🔧 Chrome Extension Installation:")
    print()
    print("   1. Open Chrome and go to: chrome://extensions/")
    print("   2. Enable 'Developer mode' (toggle in top right)")
    print("   3. Click 'Load unpacked'")
    print("   4. Select the folder:")
    print()
    
    script_dir = Path(__file__).parent
    ext_dir = script_dir / "extension"
    print(f"      {ext_dir.absolute()}")
    print()
    print("   5. The extension icon should appear in your toolbar")
    print("   6. Pin it for easy access")
    print()
    
    open_chrome = input("   Open chrome://extensions/ now? [y/N]: ").strip().lower()
    if open_chrome == 'y':
        webbrowser.open('chrome://extensions/')


def view_documentation():
    script_dir = Path(__file__).parent
    readme_path = script_dir / "README.md"
    
    print(f"\n📖 Documentation: {readme_path}")
    print()
    
    if readme_path.exists():
        with open(readme_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # Show first part
        lines = content.split('\n')[:50]
        for line in lines:
            print(f"   {line}")
        
        if len(content.split('\n')) > 50:
            print("\n   ... (truncated)")
            print(f"\n   Full documentation: {readme_path.absolute()}")
    else:
        print("   ❌ README.md not found")


def test_source():
    print("\n🧪 Test an existing source")
    print()
    print("   Enter the source name (e.g., 'novelfire', 'realmnovel'):")
    source_name = input("   Name: ").strip().lower()
    
    if not source_name:
        print("   ⚠️ No name provided")
        return
    
    # Find the source
    script_dir = Path(__file__).parent
    project_root = script_dir.parent.parent
    
    # Check common locations
    locations = [
        project_root / "sources-v5-batch" / "en" / source_name,
        project_root / "sources-v5-batch" / "ar" / source_name,
        project_root / "sources" / "en" / source_name,
    ]
    
    found = None
    for loc in locations:
        if loc.exists():
            found = loc
            break
    
    if not found:
        print(f"   ❌ Source '{source_name}' not found")
        print("   Checked locations:")
        for loc in locations:
            print(f"      • {loc}")
        return
    
    print(f"\n   ✓ Found: {found}")
    print()
    print("   To test this source, run:")
    print(f"   ./gradlew :test-extensions:test --tests \"*{source_name.capitalize()}*\"")
    print()
    
    run_test = input("   Run test now? [y/N]: ").strip().lower()
    if run_test == 'y':
        print("\n   Running tests...")
        subprocess.run(
            ["./gradlew", ":test-extensions:test", f"--tests", f"*{source_name.capitalize()}*"],
            cwd=str(project_root),
            shell=True
        )


def main():
    print_banner()
    
    while True:
        print_menu()
        choice = input("   Enter choice [1-6]: ").strip()
        
        if choice == '1':
            open_website()
        elif choice == '2':
            generate_source()
        elif choice == '3':
            show_extension_instructions()
        elif choice == '4':
            view_documentation()
        elif choice == '5':
            test_source()
        elif choice == '6':
            print("\n👋 Goodbye!")
            break
        else:
            print("\n   ⚠️ Invalid choice")
        
        print()
        input("Press Enter to continue...")
        print("\n" + "─" * 60 + "\n")


if __name__ == "__main__":
    main()
