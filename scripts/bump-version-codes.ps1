# Bump versionCode by +1 in all build.gradle.kts files under sources/
# Usage: .\scripts\bump-version-codes.ps1

param(
    [switch]$DryRun = $false,
    [string]$Path = "sources"
)

$files = Get-ChildItem -Path $Path -Recurse -Filter "build.gradle.kts"
$totalUpdates = 0

foreach ($file in $files) {
    $content = Get-Content $file.FullName -Raw
    $updated = $false
    
    # Match versionCode = X, and increment X
    $newContent = [regex]::Replace($content, 'versionCode\s*=\s*(\d+)', {
        param($match)
        $oldVersion = [int]$match.Groups[1].Value
        $newVersion = $oldVersion + 1
        $script:updated = $true
        $script:totalUpdates++
        Write-Host "  $($file.Name): versionCode $oldVersion -> $newVersion" -ForegroundColor Cyan
        return "versionCode = $newVersion"
    })
    
    if ($updated -and -not $DryRun) {
        Set-Content -Path $file.FullName -Value $newContent -NoNewline
    }
}

if ($DryRun) {
    Write-Host "`nDry run complete. $totalUpdates version codes would be updated." -ForegroundColor Yellow
} else {
    Write-Host "`nDone! Updated $totalUpdates version codes." -ForegroundColor Green
}
