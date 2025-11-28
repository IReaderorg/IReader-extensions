# Quick Test Commands Reference

## Full Workflow

### Generate + Test (Small Batch)
```bash
python scripts/batch_test_fix_system.py lnreader-plugins-master/plugins/english en --limit 5
```

### Generate + Test (All Sources)
```bash
python scripts/batch_test_fix_system.py lnreader-plugins-master/plugins/english en
```

### Test Only (Existing Sources)
```bash
python scripts/batch_test_fix_system.py --test-only en sources-v5-batch
```

## Single Source Testing

### Interactive Fix
```bash
python scripts/fix_single_source.py novelbuddy en
```

### Auto-Fix
```bash
python scripts/fix_single_source.py novelbuddy en --auto-fix
```

### Manual Test
```bash
# 1. Enable test module (required!)
# Windows PowerShell:
$env:ENABLE_TEST_MODULE='true'
# Linux/Mac:
export ENABLE_TEST_MODULE=true

# 2. Configure test module
# Edit: test-extensions/build.gradle.kts
# Add: implementation(project(":extensions:v5:en:novelbuddy"))

# 3. Create Constants.kt
# Location: test-extensions/src/main/java/ireader/constants/Constants.kt

# 4. Run test
# Windows:
gradlew.bat :test-extensions:test

# Linux/Mac:
./gradlew :test-extensions:test
```

## Batch Operations

### Test Multiple Sources
```bash
for source in novelbuddy readnovelfull wuxiaworld; do
    python scripts/fix_single_source.py $source en --auto-fix
done
```

### Generate Sources Only
```bash
python scripts/js-to-kotlin-v5-ai.py plugin.ts en
```

### Batch Generate
```bash
python scripts/batch-convert-v5.py lnreader-plugins-master/plugins/english en
```

## Results & Reports

### View Results
```bash
cat test-results/test_results.json | jq
```

### View Fix Report
```bash
cat test-results/fix_report.md
```

### Check Success Rate
```bash
cat test-results/test_results.json | jq '.passed, .failed, .total'
```

## Gradle Commands

### Test Specific Extension
```bash
# Windows:
gradlew.bat :test-extensions:test

# Linux/Mac:
./gradlew :test-extensions:test
```

### Test with Info
```bash
# Windows:
gradlew.bat :test-extensions:test --info

# Linux/Mac:
./gradlew :test-extensions:test --info
```

### Clean and Test
```bash
# Windows:
gradlew.bat :test-extensions:clean :test-extensions:test

# Linux/Mac:
./gradlew :test-extensions:clean :test-extensions:test
```

### Refresh Dependencies
```bash
# Windows:
gradlew.bat --refresh-dependencies

# Linux/Mac:
./gradlew --refresh-dependencies
```

## Debugging

### Show Full Test Output
```bash
# Windows:
gradlew.bat :test-extensions:test --info > test-output.log 2>&1
type test-output.log

# Linux/Mac:
./gradlew :test-extensions:test --info > test-output.log 2>&1
cat test-output.log
```

### Check Test Configuration
```bash
cat test-extensions/build.gradle.kts | grep implementation
cat test-extensions/src/main/java/ireader/constants/Constants.kt
```

### Verify Source Exists
```bash
ls -la sources-v5-batch/en/<source-name>/
```

### Check Package Structure
```bash
find sources-v5-batch/en/<source-name>/ -name "*.kt"
```

## Quick Fixes

### Fix Common Issues
```bash
# Run auto-fix
python scripts/fix_single_source.py <source> en --auto-fix

# If still failing, run interactive
python scripts/fix_single_source.py <source> en
```

### Edit Source Directly
```bash
# Find source file
find sources-v5-batch/en/<source-name>/ -name "*.kt"

# Edit with your editor
code sources-v5-batch/en/<source-name>/main/src/ireader/<package>/<Class>.kt

# Test again
python scripts/fix_single_source.py <source> en --auto-fix
```

## Environment Setup

### Check Gemini API Key
```bash
# Windows
echo $env:GEMINI_API_KEY

# Linux/Mac
echo $GEMINI_API_KEY
```

### Set API Key
```bash
# Windows
$env:GEMINI_API_KEY='your-key-here'

# Linux/Mac
export GEMINI_API_KEY='your-key-here'
```

### Verify Java/Gradle
```bash
java -version
./gradlew --version
```

## Monitoring

### Watch Test Progress
```bash
# Windows (PowerShell)
while ($true) { 
    Clear-Host
    Get-Content test-results/test_results.json | ConvertFrom-Json | Select passed, failed, total
    Start-Sleep -Seconds 5
}

# Linux/Mac
watch -n 5 'cat test-results/test_results.json | jq ".passed, .failed, .total"'
```

### Count Sources
```bash
# Total sources
ls sources-v5-batch/en/ | wc -l

# Sources with tests passing
cat test-results/test_results.json | jq '.results[] | select(.success==true) | .source_name' | wc -l
```

## Workflow Examples

### Example 1: Test 5 Sources
```bash
# Generate and test
python scripts/batch_test_fix_system.py lnreader-plugins-master/plugins/english en --limit 5

# Review results
cat test-results/fix_report.md

# Fix failing sources
python scripts/fix_single_source.py <failing-source> en

# Re-test
python scripts/batch_test_fix_system.py --test-only en sources-v5-batch
```

### Example 2: Fix Single Source
```bash
# Try auto-fix
python scripts/fix_single_source.py novelbuddy en --auto-fix

# If fails, go interactive
python scripts/fix_single_source.py novelbuddy en

# Manual edit if needed
code sources-v5-batch/en/novelbuddy/main/src/ireader/novelbuddy/NovelBuddy.kt

# Test again
./gradlew :test-extensions:test
```

### Example 3: Batch Fix
```bash
# Get list of failing sources
cat test-results/test_results.json | jq -r '.results[] | select(.success==false) | .source_name' > failing.txt

# Fix each one
while read source; do
    echo "Fixing $source..."
    python scripts/fix_single_source.py $source en --auto-fix
done < failing.txt

# Re-test all
python scripts/batch_test_fix_system.py --test-only en sources-v5-batch
```

## Tips

1. **Start small**: Use `--limit 5` for first run
2. **Check results**: Always review `fix_report.md`
3. **Iterate quickly**: Use `--auto-fix` for common issues
4. **Manual edit**: Use interactive mode for complex issues
5. **Re-test often**: Run tests after each fix

## Common Issues

### "Source not found"
```bash
# Check if source exists
ls sources-v5-batch/en/<source-name>/

# If not, generate it
python scripts/js-to-kotlin-v5-ai.py <plugin>.ts en
```

### "Cannot resolve extension"
```bash
# Refresh gradle
./gradlew --refresh-dependencies

# Check settings.gradle.kts includes v5 sources
grep "sources-v5-batch" settings.gradle.kts
```

### "Tests timeout"
```bash
# Increase timeout in script or run manually
./gradlew :test-extensions:test --no-daemon
```

### "Import errors"
```bash
# Clean and rebuild
./gradlew clean build

# Check package name matches
cat sources-v5-batch/en/<source>/main/src/ireader/<package>/*.kt | grep "package"
```
