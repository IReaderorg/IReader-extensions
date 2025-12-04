<#
.SYNOPSIS
    Migrates IReader extension sources from Jsoup to Ksoup (KMP-compatible).

.DESCRIPTION
    This script automates the migration of source extensions by:
    1. Converting Jsoup imports to Ksoup
    2. Updating Jsoup.parse() to Ksoup.parse()
    3. Updating date parsing to kotlinx-datetime
    4. Migrating java.util.* to KMP alternatives
    5. Generating migration report

.PARAMETER SourcePath
    Path to the source directory (e.g., "sources/en/novelupdates")

.PARAMETER All
    Migrate all sources in the sources directory

.PARAMETER DryRun
    Show what would be changed without making actual changes

.PARAMETER Report
    Generate a detailed migration report

.PARAMETER Multisrc
    Also migrate multisrc sources

.EXAMPLE
    .\migrate-source-to-kmp.ps1 -SourcePath "sources/en/novelupdates"
    
.EXAMPLE
    .\migrate-source-to-kmp.ps1 -All -Report

.EXAMPLE
    .\migrate-source-to-kmp.ps1 -All -Multisrc -DryRun
#>

param(
    [string]$SourcePath,
    [switch]$All,
    [switch]$DryRun,
    [switch]$Report,
    [switch]$Multisrc
)

$ErrorActionPreference = "Stop"
$script:MigrationReport = @()

# ============================================
# REPLACEMENT DEFINITIONS
# ============================================

# Jsoup -> Ksoup imports
$ImportReplacements = @{
    'import org.jsoup.Jsoup'           = 'import com.fleeksoft.ksoup.Ksoup'
    'import org.jsoup.nodes.Document'  = 'import com.fleeksoft.ksoup.nodes.Document'
    'import org.jsoup.nodes.Element'   = 'import com.fleeksoft.ksoup.nodes.Element'
    'import org.jsoup.select.Elements' = 'import com.fleeksoft.ksoup.select.Elements'
    'import org.jsoup.nodes.TextNode'  = 'import com.fleeksoft.ksoup.nodes.TextNode'
    'import org.jsoup.parser.Parser'   = 'import com.fleeksoft.ksoup.parser.Parser'
    'import org.jsoup.safety.Safelist' = 'import com.fleeksoft.ksoup.safety.Safelist'
    'import org.jsoup.safety.Whitelist'= 'import com.fleeksoft.ksoup.safety.Safelist'
    'import org.jsoup.nodes.Attribute' = 'import com.fleeksoft.ksoup.nodes.Attribute'
    'import org.jsoup.nodes.Attributes'= 'import com.fleeksoft.ksoup.nodes.Attributes'
    'import org.jsoup.nodes.Node'      = 'import com.fleeksoft.ksoup.nodes.Node'
    'import org.jsoup.Connection'      = '// import org.jsoup.Connection - Use Ktor HttpClient instead'
}

# Jsoup -> Ksoup code
$CodeReplacements = @{
    'Jsoup.parse('    = 'Ksoup.parse('
    'Jsoup.clean('    = 'Ksoup.clean('
    'Jsoup.parseBodyFragment(' = 'Ksoup.parseBodyFragment('
    'Whitelist.'      = 'Safelist.'
    'Jsoup.connect('  = '// TODO: Replace Jsoup.connect with Ktor HttpClient - Jsoup.connect('
}

# Date/Time replacements (java -> kotlinx.datetime)
$DateReplacements = @{
    'import java.text.SimpleDateFormat'    = 'import kotlinx.datetime.*'
    'import java.text.DateFormat'          = '// import java.text.DateFormat - Use kotlinx.datetime'
    'import java.util.Locale'              = '// import java.util.Locale - Not needed for KMP'
    'import java.util.Calendar'            = '// import java.util.Calendar - Use kotlinx.datetime'
    'import java.util.Date'                = '// import java.util.Date - Use kotlinx.datetime'
    'import java.util.TimeZone'            = '// import java.util.TimeZone - Use kotlinx.datetime'
    'import java.util.GregorianCalendar'   = '// import java.util.GregorianCalendar - Use kotlinx.datetime'
    'System.currentTimeMillis()'           = 'Clock.System.now().toEpochMilliseconds()'
}

# Java collections -> Kotlin stdlib
$CollectionReplacements = @{
    'import java.util.ArrayList'    = '// import java.util.ArrayList - Use mutableListOf()'
    'import java.util.HashMap'      = '// import java.util.HashMap - Use mutableMapOf()'
    'import java.util.HashSet'      = '// import java.util.HashSet - Use mutableSetOf()'
    'import java.util.LinkedList'   = '// import java.util.LinkedList - Use mutableListOf()'
    'import java.util.Collections'  = '// import java.util.Collections - Use Kotlin stdlib'
    'import java.util.Arrays'       = '// import java.util.Arrays - Use Kotlin stdlib'
}

# URL encoding replacements
$UrlReplacements = @{
    'import java.net.URLEncoder'    = '// import java.net.URLEncoder - Use encodeURLParameter()'
    'import java.net.URLDecoder'    = '// import java.net.URLDecoder - Use decodeURLPart()'
    'import java.net.URL'           = '// import java.net.URL - Use Ktor Url'
    'import java.net.URI'           = '// import java.net.URI - Use Ktor Url'
    'URLEncoder.encode('            = '// TODO: Use encodeURLParameter() - URLEncoder.encode('
}

# Regex replacements
$RegexReplacements = @{
    'import java.util.regex.Pattern' = '// import java.util.regex.Pattern - Use Kotlin Regex'
    'import java.util.regex.Matcher' = '// import java.util.regex.Matcher - Use Kotlin Regex'
    'Pattern.compile('               = 'Regex('
}

# DateParser import addition
$DateParserImport = 'import ireader.common.utils.DateParser'

# ============================================
# HELPER FUNCTIONS
# ============================================

function Write-Log {
    param([string]$Message, [string]$Level = "INFO")
    $color = switch ($Level) {
        "INFO"    { "White" }
        "SUCCESS" { "Green" }
        "WARNING" { "Yellow" }
        "ERROR"   { "Red" }
        "DEBUG"   { "Gray" }
        default   { "White" }
    }
    Write-Host "[$Level] $Message" -ForegroundColor $color
}

function Add-ReportEntry {
    param(
        [string]$Source,
        [string]$File,
        [string]$Change,
        [string]$Status
    )
    $script:MigrationReport += [PSCustomObject]@{
        Source = $Source
        File   = $File
        Change = $Change
        Status = $Status
    }
}

function Get-SourceName {
    param([string]$SourceDir)
    return Split-Path $SourceDir -Leaf
}

# ============================================
# MIGRATION FUNCTIONS
# ============================================

function Migrate-KotlinFile {
    param(
        [string]$FilePath,
        [string]$SourceName,
        [switch]$DryRun
    )
    
    if (-not (Test-Path $FilePath)) {
        Write-Log "File not found: $FilePath" "ERROR"
        return $false
    }
    
    $content = Get-Content $FilePath -Raw
    $originalContent = $content
    $changes = @()
    $fileName = Split-Path $FilePath -Leaf
    
    # Apply all replacement categories
    $allReplacements = @{}
    $allReplacements += $ImportReplacements
    $allReplacements += $CodeReplacements
    $allReplacements += $DateReplacements
    $allReplacements += $CollectionReplacements
    $allReplacements += $UrlReplacements
    $allReplacements += $RegexReplacements
    
    foreach ($old in $allReplacements.Keys) {
        if ($content -match [regex]::Escape($old)) {
            $content = $content -replace [regex]::Escape($old), $allReplacements[$old]
            $changes += "$old -> $($allReplacements[$old])"
            Add-ReportEntry -Source $SourceName -File $fileName -Change "$old -> $($allReplacements[$old])" -Status "Replaced"
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
    
    # Auto-fix Calendar.getInstance date parsing patterns
    $dateParsePattern = @'
    fun parseChapterDate\(date: String\): Long \{
        return if \(date\.contains\("ago"\)\) \{
            val value = date\.split\(' '\)\[0\]\.toInt\(\)
            when \{
                "min" in date -> Calendar\.getInstance\(\)\.apply \{
                    add\(Calendar\.MINUTE, value \* -1\)
                \}\.timeInMillis
                "hour" in date -> Calendar\.getInstance\(\)\.apply \{
                    add\(Calendar\.HOUR_OF_DAY, value \* -1\)
                \}\.timeInMillis
                "day" in date -> Calendar\.getInstance\(\)\.apply \{
                    add\(Calendar\.DATE, value \* -1\)
                \}\.timeInMillis
                "week" in date -> Calendar\.getInstance\(\)\.apply \{
                    add\(Calendar\.DATE, value \* 7 \* -1\)
                \}\.timeInMillis
                "month" in date -> Calendar\.getInstance\(\)\.apply \{
                    add\(Calendar\.MONTH, value \* -1\)
                \}\.timeInMillis
                "year" in date -> Calendar\.getInstance\(\)\.apply \{
                    add\(Calendar\.YEAR, value \* -1\)
                \}\.timeInMillis
                else -> \{
                    0L
                \}
            \}
        \} else \{
            try \{
                dateFormat\.parse\(date\)\?\.time \?: 0
            \} catch \(_: Exception\) \{
                0L
            \}
        \}
    \}

    private val dateFormat: SimpleDateFormat = SimpleDateFormat\("[^"]+", Locale\.US\)
'@
    
    $dateParseReplacement = @'
    fun parseChapterDate(date: String): Long {
        return DateParser.parseRelativeOrAbsoluteDate(date)
    }
'@
    
    # Try to replace the full date parsing block
    if ($content -match 'Calendar\.getInstance') {
        # Simplified pattern matching for the date parsing function
        $content = $content -replace 'private val dateFormat: SimpleDateFormat = SimpleDateFormat\("[^"]+", Locale\.US\)', ''
        $content = $content -replace 'val dateFormatter = SimpleDateFormat\("[^"]+", Locale\.US\)', ''
        
        # Add DateParser import if not present and Calendar was used
        if ($content -notmatch 'import ireader\.common\.utils\.DateParser') {
            $content = $content -replace '(import [^\r\n]+\r?\n)(@Extension)', "`$1import ireader.common.utils.DateParser`n`$2"
            $changes += "Added DateParser import"
            Add-ReportEntry -Source $SourceName -File $fileName -Change "Added DateParser import" -Status "Replaced"
        }
    }
    
    # Check for remaining JVM-only code that needs manual attention
    $warnings = @()
    if ($content -match 'SimpleDateFormat') {
        $warnings += "Contains SimpleDateFormat - needs manual migration"
    }
    if ($content -match 'Calendar\.getInstance') {
        $warnings += "Contains Calendar.getInstance - needs manual migration"
    }
    if ($content -match 'java\.io\.') {
        $warnings += "Contains java.io.* - may need KMP alternative"
    }
    if ($content -match 'java\.net\.') {
        $warnings += "Contains java.net.* - may need KMP alternative"
    }
    
    foreach ($warning in $warnings) {
        Write-Log "  WARNING: $warning" "WARNING"
        Add-ReportEntry -Source $SourceName -File $fileName -Change $warning -Status "NeedsAttention"
    }
    
    if ($content -ne $originalContent) {
        if ($DryRun) {
            Write-Log "Would modify: $FilePath" "INFO"
            foreach ($change in $changes) {
                Write-Log "  - $change" "DEBUG"
            }
        } else {
            Set-Content -Path $FilePath -Value $content -NoNewline
            Write-Log "Modified: $fileName" "SUCCESS"
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
    
    $sourceName = Get-SourceName $SourceDir
    
    Write-Log "========================================" "INFO"
    Write-Log "Migrating: $sourceName" "INFO"
    Write-Log "========================================" "INFO"
    
    if (-not (Test-Path $SourceDir)) {
        Write-Log "Source directory not found: $SourceDir" "ERROR"
        return $false
    }
    
    # Find all Kotlin files
    $ktFiles = Get-ChildItem -Path $SourceDir -Filter "*.kt" -Recurse
    $modified = 0
    $total = $ktFiles.Count
    
    Write-Log "Found $total Kotlin files" "INFO"
    
    foreach ($file in $ktFiles) {
        if (Migrate-KotlinFile -FilePath $file.FullName -SourceName $sourceName -DryRun:$DryRun) {
            $modified++
        }
    }
    
    Write-Log "Modified $modified / $total files" "SUCCESS"
    return $modified -gt 0
}

function Migrate-AllSources {
    param(
        [switch]$DryRun,
        [switch]$Multisrc
    )
    
    $sourcesDir = Join-Path $PSScriptRoot "..\sources"
    
    if (-not (Test-Path $sourcesDir)) {
        Write-Log "Sources directory not found: $sourcesDir" "ERROR"
        return
    }
    
    $migrated = 0
    $failed = 0
    $skipped = 0
    $totalSources = 0
    
    # Get all language directories
    $langDirs = Get-ChildItem -Path $sourcesDir -Directory
    
    foreach ($langDir in $langDirs) {
        # Skip multisrc unless flag is set
        if ($langDir.Name -eq "multisrc" -and -not $Multisrc) { continue }
        if ($langDir.Name -eq "common") { continue }
        
        Write-Log "Processing language: $($langDir.Name)" "INFO"
        
        # Get all source directories in this language
        $sources = Get-ChildItem -Path $langDir.FullName -Directory
        
        foreach ($source in $sources) {
            # Skip build directories
            if ($source.Name -eq "build") { continue }
            
            $buildFile = Join-Path $source.FullName "build.gradle.kts"
            $hasKtFiles = (Get-ChildItem -Path $source.FullName -Filter "*.kt" -Recurse -ErrorAction SilentlyContinue).Count -gt 0
            
            if (-not (Test-Path $buildFile) -and -not $hasKtFiles) { continue }
            
            $totalSources++
            
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
    
    # Summary
    Write-Log "" "INFO"
    Write-Log "========================================" "INFO"
    Write-Log "MIGRATION SUMMARY" "INFO"
    Write-Log "========================================" "INFO"
    Write-Log "Total sources:  $totalSources" "INFO"
    Write-Log "Migrated:       $migrated" "SUCCESS"
    Write-Log "Skipped:        $skipped" "WARNING"
    Write-Log "Failed:         $failed" "ERROR"
    Write-Log "========================================" "INFO"
}

function Export-MigrationReport {
    param([string]$OutputPath)
    
    if ($script:MigrationReport.Count -eq 0) {
        Write-Log "No migration data to report" "WARNING"
        return
    }
    
    $reportPath = if ($OutputPath) { $OutputPath } else { Join-Path $PSScriptRoot "..\migration-report.csv" }
    
    $script:MigrationReport | Export-Csv -Path $reportPath -NoTypeInformation
    Write-Log "Migration report saved to: $reportPath" "SUCCESS"
    
    # Also create a summary
    $summaryPath = $reportPath -replace '\.csv$', '-summary.txt'
    $summary = @"
IReader KMP Migration Report
============================
Generated: $(Get-Date)

Total Changes: $($script:MigrationReport.Count)

By Status:
$(($script:MigrationReport | Group-Object Status | ForEach-Object { "  $($_.Name): $($_.Count)" }) -join "`n")

By Source:
$(($script:MigrationReport | Group-Object Source | ForEach-Object { "  $($_.Name): $($_.Count) changes" }) -join "`n")

Items Needing Attention:
$($script:MigrationReport | Where-Object { $_.Status -eq "NeedsAttention" } | ForEach-Object { "  - $($_.Source)/$($_.File): $($_.Change)" } | Select-Object -First 50)
"@
    
    Set-Content -Path $summaryPath -Value $summary
    Write-Log "Migration summary saved to: $summaryPath" "SUCCESS"
}

# ============================================
# MAIN EXECUTION
# ============================================

if ($All) {
    Migrate-AllSources -DryRun:$DryRun -Multisrc:$Multisrc
    if ($Report) {
        Export-MigrationReport
    }
} elseif ($SourcePath) {
    # Resolve relative path
    if (-not [System.IO.Path]::IsPathRooted($SourcePath)) {
        $SourcePath = Join-Path $PSScriptRoot "..\$SourcePath"
    }
    Migrate-Source -SourceDir $SourcePath -DryRun:$DryRun
    if ($Report) {
        Export-MigrationReport
    }
} else {
    Write-Host @"
IReader Source KMP Migration Script
====================================

Usage:
  .\migrate-source-to-kmp.ps1 -SourcePath "sources/en/novelupdates"
  .\migrate-source-to-kmp.ps1 -All
  .\migrate-source-to-kmp.ps1 -All -Multisrc
  .\migrate-source-to-kmp.ps1 -All -Report
  .\migrate-source-to-kmp.ps1 -SourcePath "sources/en/novelupdates" -DryRun

Options:
  -SourcePath   Path to a specific source to migrate
  -All          Migrate all sources
  -DryRun       Preview changes without making them
  -Report       Generate migration report (CSV + summary)
  -Multisrc     Also migrate multisrc sources

What this script does:
  - Converts Jsoup imports to Ksoup (KMP-compatible HTML parser)
  - Updates Jsoup.parse() calls to Ksoup.parse()
  - Updates date parsing imports to kotlinx-datetime
  - Converts java.util.* collections to Kotlin stdlib
  - Flags JVM-only code that needs manual attention
  - Generates detailed migration report

"@
}
