#!/usr/bin/env python3
"""
Madara Source Generator for IReader-extensions

This script generates @MadaraSource annotation files for Madara-based sources
identified by the source analyzer.

Usage:
    python generate_madara_sources.py <analysis_json> [--output-dir <dir>]
"""

import os
import re
import json
import argparse
from pathlib import Path
from dataclasses import dataclass
from typing import List, Dict, Optional


# Language code mapping (LNCrawl -> IReader)
LANGUAGE_MAP = {
    "en": "en",
    "zh": "zh",
    "id": "in",
    "ru": "ru",
    "fr": "fr",
    "pt": "pt",
    "ar": "ar",
    "es": "es",
    "ja": "jp",
    "tr": "tu",
    "vi": "vi",
    "multi": "multi",
}


def sanitize_name(name: str) -> str:
    """Convert source name to valid Kotlin identifier"""
    # Remove special characters and spaces
    name = re.sub(r'[^a-zA-Z0-9]', '', name)
    # Ensure starts with uppercase
    if name and name[0].islower():
        name = name[0].upper() + name[1:]
    return name


def to_package_name(name: str) -> str:
    """Convert source name to package name (lowercase)"""
    return re.sub(r'[^a-z0-9]', '', name.lower())


def generate_source_id(name: str, lang: str) -> int:
    """Generate a stable source ID based on name and language"""
    import hashlib
    seed = f"{name.lower()}/{lang}/1"
    hash_bytes = hashlib.md5(seed.encode()).digest()[:8]
    return int.from_bytes(hash_bytes, byteorder='big') & 0x7FFFFFFFFFFFFFFF


def extract_custom_paths(file_path: str) -> Dict[str, str]:
    """Extract custom paths from Python source if any"""
    try:
        content = Path(file_path).read_text(encoding='utf-8')
        paths = {}
        
        # Look for custom path overrides
        if 'novel_path' in content or 'novels_path' in content:
            # Try to extract path values
            match = re.search(r'novels?_path\s*=\s*["\']([^"\']+)["\']', content)
            if match:
                paths['novelsPath'] = match.group(1)
        
        return paths
    except:
        return {}


def generate_madara_source(source: dict, output_dir: Path) -> Optional[Path]:
    """Generate a Madara source file"""
    name = sanitize_name(source['name'])
    package_name = to_package_name(source['name'])
    lang = source['language']
    base_url = source['base_urls'][0].rstrip('/')
    
    # Skip if already exists in IReader-extensions
    existing_path = output_dir / package_name / "src" / "ireader" / package_name / f"{name}.kt"
    if existing_path.exists():
        print(f"  Skipping {name} - already exists")
        return None
    
    # Check if source directory already exists (different file name)
    source_dir = output_dir / package_name
    if source_dir.exists():
        print(f"  Skipping {name} - directory already exists")
        return None
    
    # Generate source ID
    source_id = generate_source_id(name, lang)
    
    # Extract custom paths if any
    custom_paths = extract_custom_paths(source['file_path'])
    
    # Build annotation parameters
    params = [
        f'    name = "{name}",',
        f'    baseUrl = "{base_url}",',
        f'    lang = "{lang}",',
        f'    id = {source_id}L',
    ]
    
    # Add custom paths if present
    if custom_paths.get('novelsPath'):
        params.insert(-1, f'    novelsPath = "{custom_paths["novelsPath"]}",')
        params.insert(-1, f'    novelPath = "{custom_paths["novelsPath"]}",')
        params.insert(-1, f'    chapterPath = "{custom_paths["novelsPath"]}",')
    
    # Generate Kotlin source
    kotlin_source = f'''package ireader.{package_name}

import tachiyomix.annotations.MadaraSource

/**
 * {name} - Zero-code Madara source
 * Migrated from lightnovel-crawler
 */
@MadaraSource(
{chr(10).join(params)}
)
object {name}Config
'''
    
    # Create directory structure
    source_dir = output_dir / package_name / "src" / "ireader" / package_name
    source_dir.mkdir(parents=True, exist_ok=True)
    
    # Write source file
    source_file = source_dir / f"{name}.kt"
    source_file.write_text(kotlin_source, encoding='utf-8')
    
    return source_file


def generate_build_gradle(sources: List[dict], output_dir: Path) -> Path:
    """Generate or update build.gradle.kts for Madara sources"""
    build_file = output_dir / "build.gradle.kts"
    
    # Group sources by language
    by_lang = {}
    for source in sources:
        lang = source['language']
        if lang not in by_lang:
            by_lang[lang] = []
        by_lang[lang].append(source)
    
    # Generate extension entries
    entries = []
    for lang, lang_sources in sorted(by_lang.items()):
        for source in sorted(lang_sources, key=lambda x: x['name']):
            name = sanitize_name(source['name'])
            entries.append(f'''    Extension(
        name = "{name}",
        versionCode = 1,
        libVersion = "2",
        lang = "{lang}",
        description = "",
        nsfw = false,
        icon = DEFAULT_ICON,
    ),''')
    
    # Build the gradle file content
    gradle_content = f'''// Auto-generated Madara sources from lightnovel-crawler migration
// Generated sources: {len(sources)}

listOf(
{chr(10).join(entries)}
).also(::register)
'''
    
    # If file exists, append to it; otherwise create new
    if build_file.exists():
        existing = build_file.read_text(encoding='utf-8')
        # Check if we need to add new entries
        new_entries = []
        for entry in entries:
            name_match = re.search(r'name = "(\w+)"', entry)
            if name_match:
                name = name_match.group(1)
                if f'name = "{name}"' not in existing:
                    new_entries.append(entry)
        
        if new_entries:
            # Find the last entry and add new ones
            print(f"  Adding {len(new_entries)} new entries to build.gradle.kts")
            # For now, just report - manual merge may be needed
            new_gradle = output_dir / "build.gradle.kts.new"
            new_gradle.write_text(gradle_content, encoding='utf-8')
            return new_gradle
    else:
        build_file.write_text(gradle_content, encoding='utf-8')
    
    return build_file


def main():
    parser = argparse.ArgumentParser(description="Generate Madara sources for IReader")
    parser.add_argument("analysis_json", help="Path to source-analysis.json")
    parser.add_argument("--output-dir", "-o", default="sources/multisrc/madara",
                        help="Output directory for generated sources")
    parser.add_argument("--dry-run", "-n", action="store_true",
                        help="Don't write files, just show what would be done")
    
    args = parser.parse_args()
    
    # Load analysis
    analysis_path = Path(args.analysis_json)
    if not analysis_path.exists():
        print(f"Error: Analysis file not found: {analysis_path}")
        return 1
    
    with open(analysis_path, 'r', encoding='utf-8') as f:
        analysis = json.load(f)
    
    # Filter Madara sources
    madara_sources = [s for s in analysis['sources'] if s['source_type'] == 'madara']
    print(f"Found {len(madara_sources)} Madara sources to generate")
    
    if args.dry_run:
        print("\nDry run - would generate:")
        for source in madara_sources:
            name = sanitize_name(source['name'])
            print(f"  - {name} ({source['base_urls'][0]})")
        return 0
    
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    
    # Generate sources
    generated = []
    for source in madara_sources:
        print(f"Generating {source['name']}...")
        result = generate_madara_source(source, output_dir)
        if result:
            generated.append(source)
            print(f"  Created: {result}")
    
    print(f"\nGenerated {len(generated)} Madara source files")
    
    # Generate build.gradle.kts
    if generated:
        build_file = generate_build_gradle(generated, output_dir)
        print(f"Build file: {build_file}")
    
    return 0


if __name__ == "__main__":
    exit(main())
