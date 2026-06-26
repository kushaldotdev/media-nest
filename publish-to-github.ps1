# publish-to-github.ps1
param(
    [string]$VersionArg = ""
)

$ErrorActionPreference = "Stop"

# 1. Update version in app/build.gradle.kts
Write-Host "Checking version configuration..."
$file = "app/build.gradle.kts"
if (-not (Test-Path $file)) {
    Write-Error "File not found: $file"
}

$content = Get-Content $file -Raw

$codeMatch = [regex]::Match($content, 'versionCode\s*=\s*(\d+)')
$nameMatch = [regex]::Match($content, 'versionName\s*=\s*"([^"]+)"')

if (-not $codeMatch.Success -or -not $nameMatch.Success) {
    Write-Error "Could not find versionCode or versionName in $file"
}

$oldCode = [int]$codeMatch.Groups[1].Value
$oldName = $nameMatch.Groups[1].Value
$newCode = $oldCode + 1

if ($VersionArg -ne "") {
    $newName = $VersionArg -replace '^v', ''
} else {
    if ($oldName -match '^(\d+)\.(\d+)\.(\d+)$') {
        $major = $Matches[1]
        $minor = $Matches[2]
        $patch = [int]$Matches[3] + 1
        $newName = "$major.$minor.$patch"
    } else {
        Write-Error "Invalid version format '$oldName' for auto-increment. Please provide a version argument (e.g., v1.0.3)."
    }
}

$versionTag = "v$newName"
Write-Host "Updating version: $oldName (code: $oldCode) -> $newName (code: $newCode)"

$content = $content -replace "versionCode\s*=\s*$oldCode", "versionCode = $newCode"
$content = $content -replace "versionName\s*=\s*`"$oldName`"", "versionName = `"$newName`""

Set-Content $file -Value $content -NoNewline

# Check for uncommitted changes
$gitStatus = git status --porcelain
if ($gitStatus) {
    Write-Warning "You have uncommitted changes in your repository. These will be included in the release build!"
}

# 2. Git commit and push version bump
Write-Host "Committing version bump to Git..."
git add $file
git commit -m "Bump version to $versionTag"
git push

# Create and push git tag
if (git tag -l $versionTag) {
    Write-Warning "Tag $versionTag already exists. Skipping tag creation."
} else {
    Write-Host "Creating and pushing git tag $versionTag..."
    git tag $versionTag
    git push origin $versionTag
}

# 3. Build APK
Write-Host "Building Release APK..."
cmd.exe /c ".\build-release.bat clean"
if ($LASTEXITCODE -ne 0) {
    Write-Error "Build failed"
}

# 4. Release via GitHub CLI
Write-Host "Publishing release $versionTag to GitHub..."
$apkSigned = ".\app\build\outputs\apk\release\app-release.apk"
$apkUnsigned = ".\app\build\outputs\apk\release\app-release-unsigned.apk"
$apkDest = ".\medianest-$versionTag.apk"

if (Test-Path $apkSigned) {
    $apkSource = $apkSigned
} elseif (Test-Path $apkUnsigned) {
    Write-Warning "Using unsigned APK (no signing config in build.gradle.kts)"
    $apkSource = $apkUnsigned
} else {
    Write-Error "No APK found in .\app\build\outputs\apk\release\"
}

Copy-Item $apkSource -Destination $apkDest

# Check if gh CLI is authenticated
& gh auth status
if ($LASTEXITCODE -ne 0) {
    Write-Error "GitHub CLI is not authenticated. Please run 'gh auth login' first."
}

& gh release create $versionTag $apkDest --title "Release $versionTag" --notes "Update to $versionTag"
$releaseExit = $LASTEXITCODE

# Clean up
if (Test-Path $apkDest) {
    Remove-Item $apkDest
}

if ($releaseExit -eq 0) {
    Write-Host "Successfully published $versionTag"
} else {
    Write-Error "Failed to publish $versionTag to GitHub releases"
}
