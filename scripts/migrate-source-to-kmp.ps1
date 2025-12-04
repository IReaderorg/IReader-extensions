<#
.SYNOPSIS
    Migrates IReader extension sources from Jsoup to Ksoup (KMP-compatible).

.DESCRIPTION
    This script automates the migration of source extensions by:
    1. Converting Jsoup imports to Ksoup
    2. Updating Jsoup.parse() to Ksoup.parse()
    3. Updating date parsing to kotlinx-datetime

.PARAMETER SourcePath
    Path to the source directory (e.g., "sources/en/novelupdates")

.PARAMETER All
    Migrate all sources in the sources directory

.PARAMETER DryRun
    Show what would be changed without making actual changes

.EXAMPLE
    .\migrate-source-to-kmp.ps1 -SourcePath "sources/en/novelupdates"
    
.EXAMPLE
    .\migrate-source-to-kmp.ps1 -All

.EXAMPLE
    .\migrate-source-to-kmp.ps1 -SourcePath "sources/en/novelupdates" -DryRun
#>

param(
    [string]$SourcePath,
    [switch]$All,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

# Import replacements for Jsoup -> Ksoup
$ImportReplacements = @{
    'import org.jsoup.Jsoup' = 'import com.fleeksoft.ksoup.Ksoup'
    'import org.jsoup.nodes.Document' = 'import com.fleeksoft.ksoup.nodes.Document'
    'import org.jsoup.nodes.Element' = 'import com.fleeksoft.ksoup.nodes.Element'
    'import org.jsoup.select.Elements' = 'import com.fleeksoft.ksoup.select.Elements'
    'import org.jsoup.nodes.TextNode' = 'import com.fleeksoft.ksoup.nodes.TextNode'
    'import org.jsoup.parser.Parser' = 'import com.fleeksoft.ksoup.parser.Parser'
    'import org.jsoup.safety.Safelist' = 'import com.fleeksoft.ksoup.safety.Safelist'
    'import org.jsoup.safety.Whitelist' = 'import com.fleeksoft.ksoup.safety.Safelist'
    'import org.jsoup.Connection' = '// import org.jsoup.Connection - Use Ktor HttpClient instead'
}

# Code replacements for Jsoup -> Ksoup
$CodeReplacements = @{
    'Jsoup.parse(' = 'Ksoup.parse('
    'Jsoup.clean(' = 'Ksoup.clean('
    'Jsoup.connect(' = '// TODO: Replace Jsoup.connect with Ktor HttpClient - Jsoup.connect('
    'Whitelist.' = 'Safelist.'
}

# Date parsing replacements (java.text -> kotlinx.datetime)
$DateReplacements = @{
    'import java.text.SimpleDateFormat' = 'import kotlinx.datetime.*'
    'import java.util.Locale' = '// import java.util.Locale - Not needed for KMP'
    'import java.util.Calendar' = '// import java.util.Calendar - Use kotlinx.datetime instead'
    'import java.util.Date' = '// import java.util.Date - Use kotlinx.datetime instead'
    'import java.util.TimeZone' = '// import java.util.TimeZone - Use kotlinx.datetime instead'
    'System.currentTimeMillis()' = 'Clock.System.now().toEpochMilliseconds()'
}

function Write-Log {
    param([string]$Message, [string]$Level = "INFO")
    $color = switch ($Level) {
        "INFO" { "White" }
        "SUCCESS" { "Green" }
        "WARNING" { "Yellow" }
        "ERROR" { "Red" }
        default { "White" }
    }
    Write-Host "[$Level] $Message" -ForegroundColor $color
}

function Migrate-KotlinFile {
    param(
        [string]$FilePath,
        [switch]$DryRun
    )
    
    if (-not (Test-Path $FilePath)) {
        Write-Log "File not found: $FilePath" "ERROR"
        return $false
    }
    
    $content = Get-Content $FilePath -Raw
    $originalContent = $content
    $changes = @()
    
    # Apply import replacements
    foreach ($old in $ImportReplacements.Keys) {
        if ($content -match [regex]::Escape($old)) {
            $content = $content -replace [regex]::Escape($old), $ImportReplacements[$old]
            $changes += "Import: $old -> $($ImportReplacements[$old])"
        }
    }
    
    # Apply code replacements
    foreach ($old in $CodeReplacements.Keys) {
        if ($content -match [regex]::Escape($old)) {
            $content = $content -replace [regex]::Escape($old), $CodeReplacements[$old]
            $changes += "Code: $old -> $($CodeReplacements[$old])"
        }
    }
    
    # Apply date replacements
    foreach ($old in $DateReplacements.Keys) {
        if ($content -match [regex]::Escape($old)) {
            $content = $content -replace [regex]::Escape($old), $DateReplacements[$old]
            $changes += "Date: $old -> $($DateReplacements[$old])"
        }
    }
    
    # Remove duplicate kotlinx.datetime imports
    $lines = $content -split "`n"
    $seenDateTimeImport = $false
    $filteredLines = @()
    foreach ($line in $lines) {
        if ($line -match 'import kotlinx\.datetime\.\*') {
            if (-not $seenDateTimeImport) {
                $filteredLines += $line
                $seenDateTimeImport = $true
            }
        } else {
            $filteredLines += $line
        }
    }
    $content = $filteredLines -join "`n"
    
    if ($content -ne $originalContent) {
        if ($DryRun) {
            Write-Log "Would modify: $FilePath" "INFO"
            foreach ($change in $changes) {
                Write-Log "  - $change" "INFO"
            }
        } else {
            Set-Content -Path $FilePath -Value $content -NoNewline
            Write-Log "Modified: $FilePath" "SUCCESS"
            foreach ($change in $changes) {
                Write-Log "  - $change" "INFO"
            }
        }
        return $true
    }
    
    return $false
}

function Migrate-Source {
    param(
        [string]$SourceDir,
        [switch]$DryRun
    )
    
    Write-Log "========================================" "INFO"
    Write-Log "Migrating source: $SourceDir" "INFO"
    Write-Log "========================================" "INFO"
    
    if (-not (Test-Path $SourceDir)) {
        Write-Log "Source directory not found: $SourceDir" "ERROR"
        return $false
    }
    
    # Find all Kotlin files
    $ktFiles = Get-ChildItem -Path $SourceDir -Filter "*.kt" -Recurse
    $modified = 0
    
    foreach ($file in $ktFiles) {
        if (Migrate-KotlinFile -FilePath $file.FullName -DryRun:$DryRun) {
            $modified++
        }
    }
    
    Write-Log "Modified $modified Kotlin files" "SUCCESS"
    return $true
}

function Migrate-AllSources {
    param([switch]$DryRun)
    
    $sourcesDir = Join-Path $PSScriptRoot "..\sources"
    
    if (-not (Test-Path $sourcesDir)) {
        Write-Log "Sources directory not found: $sourcesDir" "ERROR"
        return
    }
    
    $migrated = 0
    $failed = 0
    $skipped = 0
    
    # Get all language directories
    $langDirs = Get-ChildItem -Path $sourcesDir -Directory
    
    foreach ($langDir in $langDirs) {
        if ($langDir.Name -eq "multisrc" -or $langDir.Name -eq "common") { continue }
        
        # Get all source directories in this language
        $sources = Get-ChildItem -Path $langDir.FullName -Directory
        
        foreach ($source in $sources) {
            $buildFile = Join-Path $source.FullName "build.gradle.kts"
            if (-not (Test-Path $buildFile)) { continue }
            
            try {
                if (Migrate-Source -SourceDir $source.FullName -DryRun:$DryRun) {
                    $migrated++
                } else {
                    $skipped++
                }
            } catch {
                Write-Log "Failed to migrate $($source.FullName): $_" "ERROR"
                $failed++
            }
        }
    }
    
    Write-Log "========================================" "INFO"
    Write-Log "Migration Summary:" "INFO"
    Write-Log "  Migrated: $migrated" "SUCCESS"
    Write-Log "  Skipped:  $skipped" "WARNING"
    Write-Log "  Failed:   $failed" "ERROR"
    Write-Log "========================================" "INFO"
}

# Main execution
if ($All) {
    Migrate-AllSources -DryRun:$DryRun
} elseif ($SourcePath) {
    # Resolve relative path
    if (-not [System.IO.Path]::IsPathRooted($SourcePath)) {
        $SourcePath = Join-Path $PSScriptRoot "..\$SourcePath"
    }
    Migrate-Source -SourceDir $SourcePath -DryRun:$DryRun
} else {
    Write-Host @"
IReader Source KMP Migration Script
====================================

Usage:
  .\migrate-source-to-kmp.ps1 -SourcePath "sources/en/novelupdates"
  .\migrate-source-to-kmp.ps1 -All
  .\migrate-source-to-kmp.ps1 -SourcePath "sources/en/novelupdates" -DryRun

Options:
  -SourcePath   Path to a specific source to migrate
  -All          Migrate all sources
  -DryRun       Preview changes without making them

What this script does:
  - Converts Jsoup imports to Ksoup (KMP-compatible HTML parser)
  - Updates Jsoup.parse() calls to Ksoup.parse()
  - Updates date parsing imports to kotlinx-datetime

"@
}
