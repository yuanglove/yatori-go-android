param(
    [switch]$ImportCore,
    [string]$DesktopReleaseDir = "",
    [string]$Version = "0.3.8",
    [int]$ExpectedApiSchemaVersion = 1
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $RepoRoot

if ($ImportCore) {
    & (Join-Path $PSScriptRoot "import-desktop-core.ps1") -DesktopReleaseDir $DesktopReleaseDir -Version $Version -ExpectedApiSchemaVersion $ExpectedApiSchemaVersion
}

$Aar = Join-Path $RepoRoot "app\libs\yatori-mobile.aar"
if (-not (Test-Path $Aar)) {
    throw "Missing app\libs\yatori-mobile.aar. Run scripts\import-desktop-core.ps1 first."
}
$CoreAssetDir = Join-Path $RepoRoot "app\src\main\assets\core"
foreach ($file in @("api-schema.json", "yatori-core-version.json", "yatori-mobilecore-checksums.json")) {
    $path = Join-Path $CoreAssetDir $file
    if (-not (Test-Path $path)) {
        throw "Missing app\src\main\assets\core\$file. Run scripts\import-desktop-core.ps1 first."
    }
}

$env:JAVA_HOME = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "C:\Program Files\Android\Android Studio\jbr" }
$env:ANDROID_HOME = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "C:\Users\35862\AppData\Local\Android\Sdk" }
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

Write-Host ""
Write-Host "====================================================" -ForegroundColor Cyan
Write-Host "  Build Android APK" -ForegroundColor Cyan
Write-Host "====================================================" -ForegroundColor Cyan

$WorkRoot = Split-Path -Parent $RepoRoot
$GradleBat = Join-Path $WorkRoot "tools\gradle-8.10.2\bin\gradle.bat"
if (-not (Test-Path $GradleBat)) {
    $GradleBat = Join-Path $RepoRoot "gradlew.bat"
}
& $GradleBat assembleDebug --no-daemon "-Dorg.gradle.vfs.watch=false"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ""
Write-Host "Done." -ForegroundColor Green
Write-Host "  APK: app\build\outputs\apk\debug\app-debug.apk"

