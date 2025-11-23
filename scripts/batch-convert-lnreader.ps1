# Batch convert lnreader-plugins to IReader extensions (PowerShell)

$ErrorActionPreference = "Stop"

# Check if lnreader-plugins directory exists
if (-not (Test-Path "lnreader-plugins-master\plugins")) {
    Write-Host "Error: lnreader-plugins-master\plugins directory not found" -ForegroundColor Red
    Write-Host "Please ensure lnreader-plugins-master is in the current directory"
    exit 1
}

# Check if Python is available
if (-not (Get-Command python -ErrorAction SilentlyContinue)) {
    Write-Host "Error: python is required but not installed" -ForegroundColor Red
    exit 1
}

# Function to convert a single plugin
function Convert-Plugin {
    param(
        [string]$PluginFile,
        [string]$Lang
    )
    
    Write-Host "Converting: $(Split-Path $PluginFile -Leaf)" -ForegroundColor Yellow
    
    try {
        python scripts\js-to-kotlin-converter.py $PluginFile $Lang .\sources
        Write-Host "✓ Success" -ForegroundColor Green
        return $true
    }
    catch {
        Write-Host "✗ Failed" -ForegroundColor Red
        return $false
    }
}

# Language mapping
$langMap = @{
    "english" = "en"
    "arabic" = "ar"
    "chinese" = "cn"
    "french" = "fr"
    "indonesian" = "in"
    "japanese" = "ja"
    "korean" = "ko"
    "polish" = "pl"
    "portuguese" = "pt"
    "russian" = "ru"
    "spanish" = "es"
    "thai" = "th"
    "turkish" = "tu"
    "ukrainian" = "uk"
    "vietnamese" = "vi"
}

# Counters
$total = 0
$success = 0
$failed = 0

# Get language filter from command line
$langFilter = if ($args.Count -gt 0) { $args[0] } else { "all" }

Write-Host "=== IReader Extension Batch Converter ===" -ForegroundColor Green
Write-Host "Converting lnreader-plugins to IReader extensions"
Write-Host ""

# Iterate through language directories
Get-ChildItem "lnreader-plugins-master\plugins" -Directory | ForEach-Object {
    $langName = $_.Name
    
    # Skip if not in language map
    if (-not $langMap.ContainsKey($langName)) {
        return
    }
    
    $langCode = $langMap[$langName]
    
    # Skip if language filter is set and doesn't match
    if ($langFilter -ne "all" -and $langFilter -ne $langCode) {
        return
    }
    
    Write-Host "Processing $langName ($langCode)..." -ForegroundColor Green
    
    # Convert each plugin in the language directory
    Get-ChildItem $_.FullName -Filter "*.ts" | ForEach-Object {
        $total++
        
        if (Convert-Plugin -PluginFile $_.FullName -Lang $langCode) {
            $success++
        }
        else {
            $failed++
        }
        
        Write-Host ""
    }
}

# Summary
Write-Host "=== Conversion Summary ===" -ForegroundColor Green
Write-Host "Total plugins: $total"
Write-Host "Successful: $success" -ForegroundColor Green
if ($failed -gt 0) {
    Write-Host "Failed: $failed" -ForegroundColor Red
}

Write-Host ""
Write-Host "Next steps:"
Write-Host "1. Review generated extensions in .\sources"
Write-Host "2. Update TODO comments in each extension"
Write-Host "3. Test extensions in Android Studio"
Write-Host "4. Add icons for each extension"
