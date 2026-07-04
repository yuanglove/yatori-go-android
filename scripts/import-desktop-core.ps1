param(
    [string]$DesktopReleaseDir = "",
    [string]$Version = "0.3.8",
    [int]$ExpectedApiSchemaVersion = 1
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($DesktopReleaseDir)) {
    $WorkRoot = Split-Path -Parent $RepoRoot
    $LearningRoot = Split-Path -Parent $WorkRoot
    $DesktopReleaseDir = Join-Path $LearningRoot "yatori-go-desktop-V0.3.4-clean\release"
}
$AarSource = Join-Path $DesktopReleaseDir "yatori-mobile-v$Version.aar"
$SchemaSource = Join-Path $DesktopReleaseDir "api-schema.json"
$VersionSource = Join-Path $DesktopReleaseDir "yatori-core-version.json"
$ChecksumSource = Join-Path $DesktopReleaseDir "yatori-mobilecore-checksums.json"
$LibDir = Join-Path $RepoRoot "app\libs"
$CoreAssetDir = Join-Path $RepoRoot "app\src\main\assets\core"

Write-Host ""
Write-Host "====================================================" -ForegroundColor Cyan
Write-Host "  Import desktop mobile core artifacts" -ForegroundColor Cyan
Write-Host "====================================================" -ForegroundColor Cyan

foreach ($file in @($AarSource, $SchemaSource, $VersionSource, $ChecksumSource)) {
    if (-not (Test-Path $file)) {
        throw "Missing desktop core artifact: $file"
    }
}

$Schema = Get-Content $SchemaSource -Raw | ConvertFrom-Json
if ([int]$Schema.schemaVersion -ne $ExpectedApiSchemaVersion) {
    throw "api-schema.json schemaVersion=$($Schema.schemaVersion), expected $ExpectedApiSchemaVersion"
}

$CoreVersion = Get-Content $VersionSource -Raw | ConvertFrom-Json
if ($CoreVersion.desktopCoreVersion -ne $Version) {
    throw "desktopCoreVersion=$($CoreVersion.desktopCoreVersion), expected $Version"
}
if ([int]$CoreVersion.apiSchemaVersion -ne $ExpectedApiSchemaVersion) {
    throw "core apiSchemaVersion=$($CoreVersion.apiSchemaVersion), expected $ExpectedApiSchemaVersion"
}
if ($CoreVersion.aarFile -ne (Split-Path -Leaf $AarSource)) {
    throw "core aarFile=$($CoreVersion.aarFile), expected $(Split-Path -Leaf $AarSource)"
}

$AarSha256 = (Get-FileHash $AarSource -Algorithm SHA256).Hash.ToLowerInvariant()
if ($CoreVersion.aarSha256 -and $CoreVersion.aarSha256.ToLowerInvariant() -ne $AarSha256) {
    throw "AAR sha256 mismatch: metadata=$($CoreVersion.aarSha256), actual=$AarSha256"
}

$Checksums = Get-Content $ChecksumSource -Raw | ConvertFrom-Json
if ([int]$Checksums.apiSchemaVersion -ne $ExpectedApiSchemaVersion) {
    throw "checksum apiSchemaVersion=$($Checksums.apiSchemaVersion), expected $ExpectedApiSchemaVersion"
}
$AarName = Split-Path -Leaf $AarSource
$AarChecksum = ($Checksums.files.PSObject.Properties | Where-Object { $_.Name -eq $AarName } | Select-Object -First 1).Value
if (-not $AarChecksum) {
    throw "checksum missing for $AarName"
}
if ($AarChecksum.ToLowerInvariant() -ne $AarSha256) {
    throw "AAR sha256 mismatch: checksums=$AarChecksum, actual=$AarSha256"
}

New-Item -ItemType Directory -Force $LibDir | Out-Null
New-Item -ItemType Directory -Force $CoreAssetDir | Out-Null

Copy-Item -Force $AarSource (Join-Path $LibDir "yatori-mobile.aar")
Copy-Item -Force $SchemaSource (Join-Path $CoreAssetDir "api-schema.json")
Copy-Item -Force $VersionSource (Join-Path $CoreAssetDir "yatori-core-version.json")
Copy-Item -Force $ChecksumSource (Join-Path $CoreAssetDir "yatori-mobilecore-checksums.json")

Write-Host "Done." -ForegroundColor Green
Write-Host "  AAR    : app\libs\yatori-mobile.aar"
Write-Host "  Schema : app\src\main\assets\core\api-schema.json"
Write-Host "  Version: app\src\main\assets\core\yatori-core-version.json"
Write-Host "  Hashes : app\src\main\assets\core\yatori-mobilecore-checksums.json"

