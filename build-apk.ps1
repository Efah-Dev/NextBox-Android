# Builds the APKs (one per CPU architecture) with the Gradle wrapper.
#
#   powershell -ExecutionPolicy Bypass -File build-apk.ps1            # debug
#   powershell -ExecutionPolicy Bypass -File build-apk.ps1 -Release
#
# core/build-core.ps1 must have produced app/libs/libbox.aar first.
# Release builds are signed with keystore.properties if present.
param([switch]$Release)
$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr" }

# Reuse HTTPS_PROXY (if set) for Gradle downloads: GRADLE_OPTS covers the
# wrapper, the -D arguments the build itself.
$gradleArgs = @()
$proxy = $env:HTTPS_PROXY
if (-not $proxy) { $proxy = $env:HTTP_PROXY }
if ($proxy) {
    $u = [Uri]$proxy
    # (no nonProxyHosts: its "|" breaks gradlew.bat; the JVM default already skips localhost)
    $gradleArgs = @("-Dhttps.proxyHost=$($u.Host)", "-Dhttps.proxyPort=$($u.Port)", "-Dhttp.proxyHost=$($u.Host)", "-Dhttp.proxyPort=$($u.Port)")
    $env:GRADLE_OPTS = $gradleArgs -join " "
}

if (-not (Test-Path (Join-Path $here "app\libs\libbox.aar"))) {
    throw "app/libs/libbox.aar is missing: run core/build-core.ps1 first"
}
$task = if ($Release) { "assembleRelease" } else { "assembleDebug" }
Push-Location $here
try {
    & .\gradlew.bat $task --console=plain @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw "gradle $task failed" }
} finally {
    Pop-Location
}
$kind = if ($Release) { "release" } else { "debug" }
Get-ChildItem (Join-Path $here "app\build\outputs\apk\$kind") -Filter *.apk | Select-Object Name, @{n = "MB"; e = { [math]::Round($_.Length / 1MB, 1) } }
