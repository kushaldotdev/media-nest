# publish-to-github.ps1
param(
    [string]$VersionArg = ""
)

$ErrorActionPreference = "Stop"

# Overall stopwatch + publish log file
$global:Stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
$PublishLog = "D:\dev\media-nest\build-publish.log"

function Format-Duration([TimeSpan]$t) {
    "{0:00}:{1:00}:{2:00}" -f $t.Hours, $t.Minutes, $t.Seconds
}

function Write-Step([string]$Message) {
    $line = "[ {0} ] [ {1} ] {2}" -f (Get-Date -Format 'HH:mm:ss'), (Format-Duration $global:Stopwatch.Elapsed), $Message
    Add-Content -Path $PublishLog -Value $line -Encoding utf8
    Write-Host ""
    Write-Host $line -ForegroundColor Cyan
}

$apkDest = $null  # cleaned up in finally

try {
    # 1. Update version in app/build.gradle.kts
    Write-Step "Checking version configuration..."
    $file = "app/build.gradle.kts"
    if (-not (Test-Path $file)) {
        Write-Error "File not found: $file"
    }

    $content = Get-Content $file -Raw -Encoding UTF8

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
            $major = [int]$Matches[1]
            $minor = [int]$Matches[2]
            $patch = [int]$Matches[3]

            if ($patch -lt 9) {
                $patch++
            } else {
                $patch = 0
                if ($minor -lt 1) {
                    $minor++
                } else {
                    $minor = 0
                    $major++
                }
            }
            $newName = "$major.$minor.$patch"
        } else {
            Write-Error "Invalid version format '$oldName' for auto-increment. Please provide a version argument (e.g., v1.0.3)."
        }
    }

    $versionTag = "v$newName"
    Write-Host "Updating version: $oldName (code: $oldCode) -> $newName (code: $newCode)"

    $content = $content -replace "versionCode\s*=\s*$oldCode", "versionCode = $newCode"
    $content = $content -replace "versionName\s*=\s*`"$oldName`"", "versionName = `"$newName`""

    # Write BOM-less UTF-8 (PS 5.1's Set-Content -Encoding UTF8 adds a BOM which can
    # break Kotlin .kts parsing; and default ANSI would corrupt the em-dash).
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText((Resolve-Path $file), $content, $utf8NoBom)

    # Check for uncommitted changes
    $gitStatus = git status --porcelain
    if ($gitStatus) {
        Write-Warning "You have uncommitted changes in your repository. These will be included in the release build!"
    }

    # 2. Git commit and push version bump
    Write-Step "Committing version bump to Git..."
    git add $file
    git commit -m "Bump version to $versionTag"
    if ($LASTEXITCODE -ne 0) { Write-Error "git commit failed" }
    git push
    if ($LASTEXITCODE -ne 0) { Write-Error "git push failed" }

    # Create and push git tag
    if (git tag -l $versionTag) {
        Write-Warning "Tag $versionTag already exists. Skipping tag creation."
    } else {
        Write-Host "Creating and pushing git tag $versionTag..."
        git tag $versionTag
        git push origin $versionTag
        if ($LASTEXITCODE -ne 0) { Write-Error "git push of tag $versionTag failed" }
    }

    # 3. Build APK
    Write-Step "Building Release APK..."
    # -nopause: don't block on the 'Press any key' prompt when invoked from a script.
    cmd.exe /c "build-release.bat clean -nopause"
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Build failed"
    }

    # 4. Copy APK into dist\publish and release via GitHub CLI
    Write-Step "Publishing release $versionTag to GitHub..."
    $apkSource = ".\app\build\outputs\apk\release\app-release.apk"
    if (-not (Test-Path $apkSource)) {
        Write-Error "No APK found at $apkSource"
    }
    Write-Host "APK: $((Resolve-Path $apkSource).Path) ($([math]::Round((Get-Item $apkSource).Length / 1MB, 1)) MB)"

    # Keep a copy in dist\publish for local reference
    $publishDir = ".\dist\publish"
    New-Item -ItemType Directory -Force -Path $publishDir | Out-Null
    $apkDest = Join-Path $publishDir "medianest-$versionTag.apk"
    Copy-Item $apkSource -Destination $apkDest
    Write-Host "Kept local copy at $apkDest"

    # Check if gh CLI is authenticated
    & gh auth status
    if ($LASTEXITCODE -ne 0) {
        Write-Error "GitHub CLI is not authenticated. Please run 'gh auth login' first."
    }

    # 5. Upload with visible progress: gh shows only a silent spinner when stdout
    #    is not a TTY (as in this script). Run gh in a background job and animate a
    #    spinner + elapsed counter until it finishes.
    $apkSizeMB = [math]::Round((Get-Item $apkDest).Length / 1MB, 1)
    Write-Host ""
    Write-Host ("  Uploading {0} ({1} MB) to GitHub..." -f (Split-Path $apkDest -Leaf), $apkSizeMB)

    $uploadJob = Start-Job -ScriptBlock {
        param($tag, $asset, $title, $notes)
        & gh release create $tag $asset --title $title --notes $notes 2>&1
        exit $LASTEXITCODE
    } -ArgumentList $versionTag, $apkDest, "Release $versionTag", "Update to $versionTag"

    $spinner = @('|', '/', '-', '\\')
    $spinIndex = 0
    $uploadSw = [System.Diagnostics.Stopwatch]::StartNew()
    $lastLine = ""
    while ($uploadJob.State -eq 'Running') {
        $elapsed = $uploadSw.Elapsed
        $line = ("  {0} uploading... [{1}]" -f $spinner[$spinIndex], (Format-Duration $elapsed))
        Write-Host ("`r" + $line) -NoNewline
        $lastLine = $line
        $spinIndex = ($spinIndex + 1) % 4
        Start-Sleep -Milliseconds 250
    }

    # Clear the spinner line
    if ($lastLine) { Write-Host ("`r" + (' ' * $lastLine.Length) + "`r") -NoNewline }

    Receive-Job $uploadJob -Wait | ForEach-Object { Write-Host $_ }
    $releaseExit = $uploadJob.ExitCode
    Remove-Job $uploadJob -Force

    if ($releaseExit -eq 0) {
        Write-Host ("  ✓ Release {0} published (took {1})" -f $versionTag, (Format-Duration $uploadSw.Elapsed)) -ForegroundColor Green
    } else {
        Write-Error "Failed to publish $versionTag to GitHub releases"
    }
}
catch {
    Write-Host ""
    Write-Host ("[ ERROR ] {0}" -f $_.Exception.Message) -ForegroundColor Red
    exit 1
}
finally {
    # Report total runtime in the terminal AND the publish log (keep dist\publish copy)
    $doneLine = "[ DONE ] Total publish time: {0}" -f (Format-Duration $global:Stopwatch.Elapsed)
    Add-Content -Path $PublishLog -Value $doneLine -Encoding utf8
    Write-Host ""
    Write-Host $doneLine -ForegroundColor Cyan
    if ($apkDest -and (Test-Path $apkDest)) {
        $copyLine = "[ DONE ] Kept local copy at {0}" -f $apkDest
        Add-Content -Path $PublishLog -Value $copyLine -Encoding utf8
        Write-Host $copyLine -ForegroundColor Green
    }
}
