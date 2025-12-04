# PowerShell script to migrate from Gson to kotlinx.serialization
# This script handles:
# 1. Replacing Gson imports with kotlinx.serialization
# 2. Replacing Gson().fromJson() calls with Json.decodeFromString()
# 3. Adding @Serializable to data classes that don't have it

$ErrorActionPreference = "Stop"

# Files that use Gson
$sourceFiles = @(
    "sources/en/ranobes/main/src/ireader/ranobes/Ranobes.kt",
    "sources/en/qidianundergrond/main/src/ireader/qidianundergrond/QidianUnderground.kt",
    "sources/en/lightnovelsme/main/src/ireader/lightnovels/LightNovel.kt"
)

# Data class files that need @Serializable
$dataClassFiles = @(
    "sources/en/ranobes/main/src/ireader/ranobes/ChapterDTO.kt",
    "sources/en/ranobes/main/src/ireader/ranobes/Chapter.kt"
)

# Pandanovel files that use SerializedName (need to remove it)
$pandanovelFiles = @(
    "sources/en/pandanovel/main/src/ireader/pandanovel/chapter/Info.kt",
    "sources/en/pandanovel/main/src/ireader/pandanovel/chapter/Data.kt",
    "sources/en/pandanovel/main/src/ireader/pandanovel/chapter/ChapterDTO.kt"
)

Write-Host "=== Migrating Gson to kotlinx.serialization ===" -ForegroundColor Cyan

# Process source files
foreach ($file in $sourceFiles) {
    if (Test-Path $file) {
        Write-Host "Processing: $file" -ForegroundColor Yellow
        $content = Get-Content $file -Raw
        
        # Remove Gson import
        $content = $content -replace "import com\.google\.gson\.Gson\r?\n", ""
        
        # Remove gson() ContentNegotiation import if present
        $content = $content -replace "import io\.ktor\.serialization\.gson\.gson\r?\n", ""
        
        # Add kotlinx.serialization import if not present
        if ($content -notmatch "import kotlinx\.serialization\.json\.Json") {
            $content = $content -replace "(package [^\r\n]+)", "`$1`r`n`r`nimport kotlinx.serialization.json.Json"
        }
        
        # Replace gson() in ContentNegotiation with json()
        $content = $content -replace "install\(ContentNegotiation\)\s*\{\s*gson\(\)\s*\}", "install(ContentNegotiation) {`r`n            json(Json { ignoreUnknownKeys = true })`r`n        }"
        
        # Replace Gson().fromJson<Type>(string, Type::class.java) with Json.decodeFromString<Type>(string)
        # Pattern: Gson().fromJson<SomeType>(variable, SomeType::class.java)
        $content = $content -replace "Gson\(\)\.fromJson<([^>]+)>\(\s*([^,]+),\s*\1::class\.java\s*\)", "Json { ignoreUnknownKeys = true }.decodeFromString<`$1>(`$2)"
        
        # Pattern: Gson().fromJson(variable, SomeType::class.java)
        $content = $content -replace "Gson\(\)\.fromJson\(\s*([^,]+),\s*([^:]+)::class\.java\s*\)", "Json { ignoreUnknownKeys = true }.decodeFromString<`$2>(`$1)"
        
        Set-Content $file $content -NoNewline
        Write-Host "  Updated: $file" -ForegroundColor Green
    } else {
        Write-Host "  File not found: $file" -ForegroundColor Red
    }
}

# Add @Serializable to data classes
foreach ($file in $dataClassFiles) {
    if (Test-Path $file) {
        Write-Host "Processing data class: $file" -ForegroundColor Yellow
        $content = Get-Content $file -Raw
        
        # Check if already has @Serializable
        if ($content -notmatch "@Serializable") {
            # Add import
            $content = $content -replace "(package [^\r\n]+)", "`$1`r`n`r`nimport kotlinx.serialization.Serializable"
            
            # Add @Serializable before data class
            $content = $content -replace "(\r?\n)(data class)", "`r`n`r`n@Serializable`r`n`$2"
        }
        
        Set-Content $file $content -NoNewline
        Write-Host "  Updated: $file" -ForegroundColor Green
    } else {
        Write-Host "  File not found: $file" -ForegroundColor Red
    }
}

# Remove SerializedName from pandanovel files
foreach ($file in $pandanovelFiles) {
    if (Test-Path $file) {
        Write-Host "Processing pandanovel: $file" -ForegroundColor Yellow
        $content = Get-Content $file -Raw
        
        # Remove SerializedName import
        $content = $content -replace "import com\.google\.gson\.annotations\.SerializedName\r?\n", ""
        
        # Remove @SerializedName annotations (they're not needed if field names match JSON)
        $content = $content -replace '\s*@SerializedName\("[^"]+"\)\s*', "`r`n    "
        
        Set-Content $file $content -NoNewline
        Write-Host "  Updated: $file" -ForegroundColor Green
    } else {
        Write-Host "  File not found: $file" -ForegroundColor Red
    }
}

Write-Host "`n=== Migration Complete ===" -ForegroundColor Cyan
Write-Host "Please review the changes and run a build to verify." -ForegroundColor White
