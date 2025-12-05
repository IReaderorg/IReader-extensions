#!/usr/bin/env python3
"""
Bump versionCode by +1 in all build.gradle.kts files under sources/

Usage:
    python scripts/bump-version-codes.py           # Apply changes
    python scripts/bump-version-codes.py --dry-run # Preview changes
"""

import re
import sys
from pathlib import Path

def bump_version_codes(base_path: str = "sources", dry_run: bool = False):
    total_updates = 0
    
    for gradle_file in Path(base_path).rglob("build.gradle.kts"):
        content = gradle_file.read_text(encoding="utf-8")
        
        def replace_version(match):
            nonlocal total_updates
            old_version = int(match.group(1))
            new_version = old_version + 1
            total_updates += 1
            print(f"  {gradle_file.name}: versionCode {old_version} -> {new_version}")
            return f"versionCode = {new_version}"
        
        new_content = re.sub(r'versionCode\s*=\s*(\d+)', replace_version, content)
        
        if new_content != content and not dry_run:
            gradle_file.write_text(new_content, encoding="utf-8")
    
    if dry_run:
        print(f"\nDry run complete. {total_updates} version codes would be updated.")
    else:
        print(f"\nDone! Updated {total_updates} version codes.")

if __name__ == "__main__":
    dry_run = "--dry-run" in sys.argv
    bump_version_codes(dry_run=dry_run)
