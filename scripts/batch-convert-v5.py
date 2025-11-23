#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Batch Converter V5 - Process multiple plugins with V5 Complete
"""

import sys
import io
from pathlib import Path
import subprocess
import time
from typing import List, Tuple

# Fix Windows console encoding
if sys.platform == 'win32':
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

class BatchConverterV5:
    """Batch process multiple plugins with V5"""
    
    def __init__(self, plugins_dir: Path, output_dir: Path, lang: str, use_ai: bool = True):
        self.plugins_dir = plugins_dir
        self.output_dir = output_dir
        self.lang = lang
        self.use_ai = use_ai
        self.results = []
        
    def find_plugins(self) -> List[Path]:
        """Find all TypeScript plugin files"""
        plugins = []
        
        # Search in language directory
        lang_dir = self.plugins_dir / self.lang
        if lang_dir.exists():
            plugins.extend(lang_dir.glob("*.ts"))
        
        # Also search in root if no language dir
        if not plugins:
            plugins.extend(self.plugins_dir.glob("*.ts"))
        
        return sorted(plugins)
    
    def plugin_exists(self, plugin_file: Path) -> bool:
        """Check if plugin already exists in output directory"""
        plugin_name = plugin_file.stem
        plugin_dir = self.output_dir / self.lang / plugin_name
        
        # Check if directory exists and has the main Kotlin file
        if plugin_dir.exists():
            # Look for Kotlin source file
            src_dir = plugin_dir / "main" / "src" / "ireader"
            if src_dir.exists():
                # Check if any .kt file exists
                kt_files = list(src_dir.rglob("*.kt"))
                if kt_files:
                    return True
        
        return False
    
    def convert_plugin(self, plugin_file: Path) -> Tuple[bool, float, str, dict]:
        """Convert a single plugin"""
        start_time = time.time()
        
        # Check if plugin already exists
        if self.plugin_exists(plugin_file):
            elapsed = time.time() - start_time
            return True, elapsed, "Skipped (already exists)", {'skipped': True}
        
        try:
            result = subprocess.run(
                [
                    sys.executable,
                    "scripts/js-to-kotlin-v5-complete.py",
                    str(plugin_file),
                    self.lang,
                    str(self.output_dir)
                ],
                capture_output=True,
                text=True,
                encoding='utf-8',
                errors='replace',
                timeout=60
            )
            
            elapsed = time.time() - start_time
            success = result.returncode == 0
            
            # Extract stats from output
            stats = self._extract_stats(result.stdout)
            
            return success, elapsed, result.stdout if success else result.stderr, stats
            
        except subprocess.TimeoutExpired:
            elapsed = time.time() - start_time
            return False, elapsed, "Timeout", {}
        except Exception as e:
            elapsed = time.time() - start_time
            return False, elapsed, str(e), {}
    
    def _extract_stats(self, output: str) -> dict:
        """Extract statistics from converter output"""
        stats = {
            'selectors': 0,
            'api_endpoints': 0,
            'methods': 0,
            'plugin_type': 'unknown',
            'ai_enhanced': False
        }
        
        try:
            for line in output.split('\n'):
                if 'Selectors:' in line and 'Total' not in line:
                    stats['selectors'] = int(line.split(':')[1].strip())
                elif 'API Endpoints:' in line:
                    stats['api_endpoints'] = int(line.split(':')[1].strip())
                elif 'Methods:' in line and 'Total' not in line:
                    stats['methods'] = int(line.split(':')[1].strip())
                elif 'Plugin Type:' in line:
                    stats['plugin_type'] = line.split(':')[1].strip()
                elif 'AI Enhanced: Yes' in line:
                    stats['ai_enhanced'] = True
        except:
            pass
        
        return stats
    
    def run(self):
        """Run batch conversion"""
        plugins = self.find_plugins()
        
        if not plugins:
            print(f"❌ No plugins found in {self.plugins_dir}")
            return
        
        print("╔" + "═" * 68 + "╗")
        print("║" + " 🚀 BATCH CONVERTER V5 - SMART CODE GENERATION".center(68) + "║")
        print("╚" + "═" * 68 + "╝")
        print()
        print(f"📁 Source Directory : {self.plugins_dir}")
        print(f"📁 Output Directory : {self.output_dir}")
        print(f"🌍 Language         : {self.lang}")
        print(f"📦 Total Plugins    : {len(plugins)}")
        print(f"🤖 AI Enhancement   : {'Enabled' if self.use_ai else 'Disabled'}")
        print()
        print("─" * 70)
        print()
        
        successful = 0
        failed = 0
        skipped = 0
        total_time = 0
        total_selectors = 0
        total_api_endpoints = 0
        
        for i, plugin_file in enumerate(plugins, 1):
            plugin_name = plugin_file.stem
            progress = f"[{i}/{len(plugins)}]"
            print(f"{progress:>12} {plugin_name:<30}", end=" ", flush=True)
            
            success, elapsed, output, stats = self.convert_plugin(plugin_file)
            total_time += elapsed
            
            if success:
                # Check if skipped
                if stats.get('skipped'):
                    skipped += 1
                    print(f"⏭️  {elapsed:>5.1f}s │ SKIPPED (already exists)")
                    self.results.append((plugin_name, True, elapsed, None, {'skipped': True}))
                else:
                    successful += 1
                    total_selectors += stats.get('selectors', 0)
                    total_api_endpoints += stats.get('api_endpoints', 0)
                    
                    # Show stats
                    sel_count = stats.get('selectors', 0)
                    api_count = stats.get('api_endpoints', 0)
                    plugin_type = stats.get('plugin_type', '?')[:3].upper()
                    
                    print(f"✅ {elapsed:>5.1f}s │ {sel_count:>2} sel │ {api_count:>1} api │ {plugin_type}")
                    
                    self.results.append((plugin_name, True, elapsed, None, stats))
            else:
                failed += 1
                # Extract error message
                error_msg = "Unknown error"
                if "Error" in output:
                    lines = [l for l in output.split('\n') if 'Error' in l]
                    if lines:
                        error_msg = lines[0][:40]
                
                print(f"❌ {elapsed:>5.1f}s - {error_msg}")
                self.results.append((plugin_name, False, elapsed, error_msg, {}))
        
        # Summary
        print()
        print("╔" + "═" * 68 + "╗")
        print("║" + " 📊 CONVERSION SUMMARY".center(68) + "║")
        print("╚" + "═" * 68 + "╝")
        print()
        
        # Statistics
        success_rate = (successful / len(plugins) * 100) if plugins else 0
        avg_time = total_time / len(plugins) if plugins else 0
        avg_selectors = total_selectors / successful if successful else 0
        
        print(f"  Results:")
        print(f"    ✅ Converted     : {successful:>3} / {len(plugins):<3} ({success_rate:>5.1f}%)")
        print(f"    ⏭️  Skipped      : {skipped:>3} / {len(plugins):<3}")
        print(f"    ❌ Failed        : {failed:>3} / {len(plugins):<3}")
        print()
        print(f"  Performance:")
        print(f"    ⏱️  Total Time    : {total_time:>6.1f}s")
        print(f"    ⚡ Average Time  : {avg_time:>6.1f}s per plugin")
        if successful > 0:
            successful_times = [t for _, s, t, _, _ in self.results if s]
            print(f"    🎯 Fastest       : {min(successful_times):>6.1f}s")
            print(f"    🐌 Slowest       : {max(successful_times):>6.1f}s")
        print()
        print(f"  Extraction:")
        print(f"    🎯 Total Selectors    : {total_selectors}")
        print(f"    🌐 Total API Endpoints: {total_api_endpoints}")
        print(f"    📊 Avg Selectors/Plugin: {avg_selectors:.1f}")
        print()
        
        # Plugin type breakdown
        if successful > 0:
            types = {}
            for _, success, _, _, stats in self.results:
                if success and stats:
                    ptype = stats.get('plugin_type', 'unknown')
                    types[ptype] = types.get(ptype, 0) + 1
            
            print(f"  Plugin Types:")
            for ptype, count in sorted(types.items()):
                print(f"    {ptype.capitalize():<12} : {count:>2} plugins")
            print()
        
        # Failed plugins details
        if failed > 0:
            print(f"  ❌ Failed Plugins ({failed}):")
            print()
            for name, success, elapsed, error, _ in self.results:
                if not success:
                    print(f"    • {name:<30} {error or 'Unknown error'}")
            print()
        
        # Success message
        total_processed = successful + skipped
        if total_processed == len(plugins) and failed == 0:
            if skipped > 0:
                print("  🎉 " + f"ALL PLUGINS READY! ({successful} new, {skipped} existing)".center(64) + " 🎉")
            else:
                print("  🎉 " + "ALL CONVERSIONS SUCCESSFUL!".center(64) + " 🎉")
        elif successful > 0:
            print(f"  ✨ {successful} plugins converted successfully!")
            if skipped > 0:
                print(f"  ⏭️  {skipped} plugins skipped (already exist)")
            print(f"  📈 Success rate: {success_rate:.1f}% (Expected: 60-80%)")
        elif skipped > 0:
            print(f"  ⏭️  All {skipped} plugins already exist (none converted)")
        else:
            print("  ⚠️  No plugins were converted successfully.")
        
        print()
        print(f"  📁 Output: {self.output_dir}")
        print()
        print("─" * 70)

def main():
    if len(sys.argv) < 3:
        print("Usage: python batch-convert-v5.py <plugins_dir> <lang> [output_dir]")
        print()
        print("Examples:")
        print("  python scripts/batch-convert-v5.py lnreader-plugins-master/plugins/english en")
        print("  python scripts/batch-convert-v5.py lnreader-plugins-master/plugins/english en ./sources-v5")
        sys.exit(1)
    
    plugins_dir = Path(sys.argv[1])
    lang = sys.argv[2]
    output_dir = Path(sys.argv[3]) if len(sys.argv) > 3 else Path("./sources-v5-batch")
    
    if not plugins_dir.exists():
        print(f"❌ Directory not found: {plugins_dir}")
        sys.exit(1)
    
    converter = BatchConverterV5(plugins_dir, output_dir, lang)
    converter.run()

if __name__ == "__main__":
    main()
