<#
.SYNOPSIS
    Migrates IReader extension sources from JVM-only to Kotlin Multiplatform (KMP).

.DESCRIPTION
    This script automates the migration of source extensions to support both JVM (Android/Desktop) 
    and JS (iOS) targets by:
    1. Converting Jsoup imports to Ksoup
    2. Updating date parsing to kotlinx-datetime
    3. Restructuring source directories to KMP layout
    4. Creating JS init files
    5. Updating build.gradle.kts for multiplatform

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

function Get-SourcePackageName {
    param([string]$SourceDir)
    
    # Find the main Kotlin file to extract package name
    $ktFiles = Get-ChildItem -Path $SourceDir -Filter "*.kt" -Recurse | Where-Object { $_.Name -ne "Init.kt" }
    if ($ktFiles.Count -eq 0) { return $null }
    
    $content = Get-Content $ktFiles[0].FullName -Raw
    if ($content -match 'package\s+([\w.]+)') {
        return $matches[1]
    }
    return $null
}

function Get-SourceClassName {
    param([string]$SourceDir)
    
    $ktFiles = Get-ChildItem -Path $SourceDir -Filter "*.kt" -Recurse | Where-Object { $_.Name -ne "Init.kt" }
    foreach ($file in $ktFiles) {
        $content = Get-Content $file.FullName -Raw
        if ($content -match 'class\s+(\w+)\s*[:(]') {
            return $matches[1]
        }
    }
    return $null
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

function Create-KmpDirectoryStructure {
    param(
        [string]$SourceDir,
        [switch]$DryRun
    )
    
    $mainSrcDir = Join-Path $SourceDir "main\src"
    $srcDir = Join-Path $SourceDir "src"
    
    # Check if already migrated
    if (Test-Path (Join-Path $srcDir "commonMain")) {
        Write-Log "Source already has KMP structure: $SourceDir" "WARNING"
        return $false
    }
    
    # Check if old structure exists
    if (-not (Test-Path $mainSrcDir)) {
        # Try alternative structure: src/main/kotlin
        $altMainSrcDir = Join-Path $SourceDir "src\main\kotlin"
        if (Test-Path $altMainSrcDir) {
            $mainSrcDir = Join-Path $SourceDir "src\main"
        } else {
            Write-Log "No source files found in: $SourceDir" "WARNING"
            return $false
        }
    }
    
    $packageName = Get-SourcePackageName $SourceDir
    $className = Get-SourceClassName $SourceDir
    
    if (-not $packageName) {
        Write-Log "Could not determine package name for: $SourceDir" "ERROR"
        return $false
    }
    
    $packagePath = $packageName -replace '\.', '/'
    $commonMainDir = Join-Path $srcDir "commonMain\kotlin\$packagePath"
    $jsMainDir = Join-Path $srcDir "jsMain\kotlin\$packagePath"
    
    if ($DryRun) {
        Write-Log "Would create directory structure:" "INFO"
        Write-Log "  - $commonMainDir" "INFO"
        Write-Log "  - $jsMainDir" "INFO"
        return $true
    }
    
    # Create new directories
    New-Item -ItemType Directory -Path $commonMainDir -Force | Out-Null
    New-Item -ItemType Directory -Path $jsMainDir -Force | Out-Null
    
    Write-Log "Created KMP directory structure for: $SourceDir" "SUCCESS"
    return $true
}
