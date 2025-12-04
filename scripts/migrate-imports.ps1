<#
.SYNOPSIS
    Quick script to migrate Jsoup imports to Ksoup in Kotlin files.

.DESCRIPTION
    This is a lightweight script that only updates imports and function calls,
    without restructuring the project. Useful for quick migrations.

.PARAMETER Path
    Path to a file or directory to migrate

.PARAMETER Recursive
    Process directories recursively

.EXAMPLE
    .\migrate-imports.ps1 -Path "sources/en/novelupdates"
#>

param(
    [Parameter(Mandatory=$true)]
    [string]$Path,
    [switch]$Recursive
)

$replacements = @(
    # Jsoup -> Ksoup imports
    @{ Old = 'import org.jsoup.Jsoup'; New = 'import com.fleeksoft.ksoup.Ksoup' }
    @{ Old = 'import org.jsoup.nodes.Document'; New = 'import com.fleeksoft.ksoup.nodes.Document' }
    @{ Old = 'import org.jsoup.nodes.Element'; New = 'import com.fleeksoft.ksoup.nodes.Element' }
    @{ Old = 'import org.jsoup.select.Elements'; New = 'import com.fleeksoft.ksoup.select.Elements' }
    @{ Old = 'import org.jsoup.nodes.TextNode'; New = 'import com.fleeksoft.ksoup.nodes.TextNode' }
    @{ Old = 'import org.jsoup.parser.Parser'; New = 'import com.fleeksoft.ksoup.parser.Parser' }
    @{ Old = 'import org.jsoup.safety.Safelist'; New = 'import com.fleeksoft.ksoup.safety.Safelist' }
    @{ Old = 'import org.jsoup.safety.Whitelist'; New = 'import com.fleeksoft.ksoup.safety.Safelist' }
    
    # Jsoup -> Ksoup code
    @{ Old = 'Jsoup.parse('; New = 'Ksoup.parse(' }
    @{ Old = 'Jsoup.clean('; New = 'Ksoup.clean(' }
    @{ Old = 'Whitelist.'; New = 'Safelist.' }
    
    # Date/Time (java -> kotlinx)
    @{ Old = 'import java.text.SimpleDateFormat'; New = 'import kotlinx.datetime.*' }
    @{ Old = 'System.currentTimeMillis()'; New = 'Clock.System.now().toEpochMilliseconds()' }
)

function Migrate-File {
    param([string]$FilePath)
    
    $content = Get-Content $FilePath -Raw
    $original = $content
    $changes = @()
    
    foreach ($r in $replacements) {
        if ($content.Contains($r.Old)) {
            $content = $content.Replace($r.Old, $r.New)
            $changes += "$($r.Old) -> $($r.New)"
        }
    }
    
    # Remove duplicate kotlinx.datetime imports
    $content = $content -replace '(import kotlinx\.datetime\.\*\r?\n)+', "import kotlinx.datetime.*`n"
    
    if ($content -ne $original) {
        Set-Content -Path $FilePath -Value $content -NoNewline
        Write-Host "[MODIFIED] $FilePath" -ForegroundColor Green
        foreach ($c in $changes) {
            Write-Host "  - $c" -ForegroundColor Gray
        }
        return $true
    }
    return $false
}

# Main
$files = @()
if (Test-Path $Path -PathType Leaf) {
    $files = @(Get-Item $Path)
} else {
    $params = @{ Path = $Path; Filter = "*.kt" }
    if ($Recursive) { $params.Recurse = $true }
    $files = Get-ChildItem @params
}

$modified = 0
foreach ($file in $files) {
    if (Migrate-File -FilePath $file.FullName) {
        $modified++
    }
}

Write-Host "`nMigrated $modified files" -ForegroundColor Cyan
