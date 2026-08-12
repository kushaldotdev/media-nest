# scripts/build.ps1
# Shared helper for build-debug.bat / build-release.bat:
#  - runs gradlew with LIVE console output AND tees to build.log (buffered UTF-8)
#  - prefixes every line with elapsed time [+mm:ss] — in terminal AND log
#  - writes a timestamped header/footer + total duration into build.log
#  - optionally copies the produced APK to a dist\ folder and reports size
param(
    [string]$Task = ":app:assembleDebug",
    [switch]$Clean,
    [string]$ApkSource = "",
    [string]$ApkDestination = "",
    [string]$LogPath = "D:\dev\media-nest\build.log"
)

$ErrorActionPreference = "Stop"

$logPath = $LogPath
$start = Get-Date
$sw = [System.Diagnostics.Stopwatch]::StartNew()

function Format-Duration([TimeSpan]$t) {
    "{0:00}:{1:00}:{2:00}" -f $t.Hours, $t.Minutes, $t.Seconds
}

# Buffered UTF-8 (no BOM) writer — fast incremental writes, unlike Out-File per line.
$utf8 = New-Object System.Text.UTF8Encoding($false)
$writer = New-Object System.IO.StreamWriter($logPath, $false, $utf8)

function Emit([string]$Text) {
    $writer.WriteLine($Text)
    Write-Host $Text
}

$bar = "========================================================"
Emit $bar
Emit ("  Build started : " + $start.ToString('yyyy-MM-dd HH:mm:ss'))
Emit ("  Task          : " + $Task + $(if ($Clean) { '  (clean)' }))
Emit $bar

$gradleArgs = @($Task, "--console=plain")
if ($Clean) { $gradleArgs = @("clean") + $gradleArgs }

# Run gradlew with stderr merged into stdout. PS 5.1 under $ErrorActionPreference=Stop
# turns native stderr lines into terminating RemoteExceptions, killing the pipeline on
# any Gradle warning. Scope a tolerant EAP to this invocation so stderr is captured as
# plain lines (and $LASTEXITCODE still reflects the real exit code, verified empirically).
$prevEap = $ErrorActionPreference
$ErrorActionPreference = "Continue"
& ".\gradlew.bat" @gradleArgs 2>&1 | ForEach-Object {
    # Live progress with per-line elapsed timer (terminal + log)
    $text = $_.ToString()
    $display = "[+" + (Format-Duration $sw.Elapsed) + "] " + $text
    $writer.WriteLine($display)
    Write-Host $display
}
$ErrorActionPreference = $prevEap
$exit = $LASTEXITCODE
if ($null -eq $exit) { $exit = 1 }

# Footer (before closing the writer so it lands in the log)
Emit $bar
Emit ("  Build ended   : " + (Get-Date).ToString('yyyy-MM-dd HH:mm:ss'))
Emit ("  Total time    : " + (Format-Duration $sw.Elapsed))
Emit ("  Exit code     : " + $exit)
Emit $bar
$writer.Flush()
$writer.Close()

# Optional APK copy on success
if ($exit -eq 0 -and $ApkSource -ne "" -and (Test-Path $ApkSource)) {
    $destDir = Split-Path $ApkDestination -Parent
    if (-not (Test-Path $destDir)) { New-Item -ItemType Directory -Force -Path $destDir | Out-Null }
    Copy-Item $ApkSource -Destination $ApkDestination -Force
    $sizeMB = [math]::Round((Get-Item $ApkDestination).Length / 1MB, 1)
    Write-Host ""
    Write-Host "APK copied to: $ApkDestination  ($sizeMB MB)" -ForegroundColor Green
} elseif ($exit -eq 0 -and $ApkSource -ne "") {
    Write-Host ""
    Write-Host "WARNING: APK not found at $ApkSource" -ForegroundColor Yellow
}

exit $exit
