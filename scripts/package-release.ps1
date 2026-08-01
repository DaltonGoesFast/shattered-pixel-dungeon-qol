# Build and stage Shattered Pixel Dungeon QoL release artifacts into ./release/
param(
    [switch]$SkipAndroid,
    [switch]$SkipJpackage,
    [switch]$SkipGradle
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

function Get-AppVersion {
    $gradle = Get-Content -LiteralPath (Join-Path $Root 'build.gradle') -Raw
    if ($gradle -match "appVersionName\s*=\s*'([^']+)'") { return $Matches[1] }
    throw 'Could not parse appVersionName from build.gradle'
}

$ver = Get-AppVersion
$slug = "Shattered-Pixel-Dungeon-QoL-$ver"
$releaseDir = Join-Path $Root 'release'
New-Item -ItemType Directory -Force -Path $releaseDir | Out-Null

Write-Host "Packaging release $ver ..." -ForegroundColor Cyan

if (-not $SkipGradle) {
    Write-Host "`n[gradle] desktop:release" -ForegroundColor Yellow
    & .\gradlew.bat desktop:release --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'desktop:release failed' }

    if (-not $SkipJpackage) {
        Write-Host "`n[gradle] desktop:jpackageImage (downloads JRE on first run)" -ForegroundColor Yellow
        & .\gradlew.bat desktop:jpackageImage --no-daemon
        if ($LASTEXITCODE -ne 0) { throw 'desktop:jpackageImage failed' }
    }

    if (-not $SkipAndroid) {
        if (-not (Test-Path (Join-Path $Root 'android\keystore.properties'))) {
            throw 'android/keystore.properties missing — use -SkipAndroid or configure signing (see docs/RELEASE.md)'
        }
        Write-Host "`n[gradle] android:assembleRelease" -ForegroundColor Yellow
        & .\gradlew.bat android:assembleRelease --no-daemon
        if ($LASTEXITCODE -ne 0) { throw 'android:assembleRelease failed' }
    }
}

# --- Desktop JAR ---
$jarSrc = Join-Path $Root "desktop\build\libs\desktop-$ver.jar"
if (-not (Test-Path -LiteralPath $jarSrc)) {
    $jarSrc = Get-ChildItem (Join-Path $Root 'desktop\build\libs') -Filter "desktop-$ver.jar" -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $jarSrc -or -not (Test-Path -LiteralPath $jarSrc)) {
    throw "Desktop JAR not found (expected desktop-$ver.jar under desktop/build/libs)"
}
$jarDst = Join-Path $releaseDir "$slug-desktop.jar"
Copy-Item -LiteralPath $jarSrc -Destination $jarDst -Force
Write-Host "OK JAR -> $jarDst"

# --- Windows jpackage zip ---
if (-not $SkipJpackage) {
    $jpackageDir = Join-Path $Root 'desktop\build\jpackage\Shattered Pixel Dungeon QoL'
    $exe = Join-Path $jpackageDir 'Shattered Pixel Dungeon QoL.exe'
    if (-not (Test-Path -LiteralPath $exe)) {
        throw "Windows EXE not found at: $exe (run desktop:jpackageImage on Windows)"
    }
    $zipDst = Join-Path $releaseDir "$slug-windows-x64.zip"
    if (Test-Path -LiteralPath $zipDst) { Remove-Item -LiteralPath $zipDst -Force }
    Compress-Archive -LiteralPath $jpackageDir -DestinationPath $zipDst -CompressionLevel Optimal
    Write-Host "OK ZIP -> $zipDst"
}

# --- Android APK ---
if (-not $SkipAndroid) {
    $apkSrc = Join-Path $Root 'android\build\outputs\apk\release\android-release.apk'
    if (-not (Test-Path -LiteralPath $apkSrc)) { throw "APK not found at $apkSrc" }
    $apkDst = Join-Path $releaseDir "$slug-android.apk"
    Copy-Item -LiteralPath $apkSrc -Destination $apkDst -Force
    Write-Host "OK APK -> $apkDst"
}

Write-Host "`nRelease files in: $releaseDir" -ForegroundColor Green
Get-ChildItem $releaseDir | Format-Table Name, @{ N = 'MB'; E = { [math]::Round($_.Length / 1MB, 2) } }
