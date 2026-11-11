# Builds app/libs/libbox.aar with gomobile from
#   github.com/sagernet/sing-box v1.14.4  experimental/libbox (the proxy core, unmodified)
#   ./nextbox/mobile                      nbcore: data store, share links, subscriptions, config builder
#   ./nbtest                              in-process URL latency test
#
#   powershell -ExecutionPolicy Bypass -File core/build-core.ps1            # arm64 + armv7 + x86_64
#   powershell -ExecutionPolicy Bypass -File core/build-core.ps1 -Targets android/arm64
#
# Needs Go, gomobile/gobind (github.com/sagernet/gomobile v0.1.12), the
# Android SDK + NDK and a JDK; defaults below match this machine and can be
# overridden through the usual environment variables.
param(
    [string]$Targets = "android/arm64,android/arm,android/amd64",
    [switch]$DebugBuild
)
$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path

if (-not (Get-Command go -ErrorAction SilentlyContinue)) {
    $env:Path = "C:\Program Files\Go\bin;" + $env:Path
}
$env:Path = (Join-Path (& go env GOPATH) "bin") + ";" + $env:Path
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA "Android\Sdk" }
if (-not $env:ANDROID_NDK_HOME) {
    $ndk = Get-ChildItem (Join-Path $env:ANDROID_HOME "ndk") -Directory | Sort-Object Name -Descending | Select-Object -First 1
    $env:ANDROID_NDK_HOME = $ndk.FullName
}
# gomobile only recognises "platforms/android-NN"; newer SDKs name the folder
# "android-37.0". Point it at a shim SDK with a junction instead of touching
# the real one.
$plain = Get-ChildItem (Join-Path $env:ANDROID_HOME "platforms") -Directory | Where-Object { $_.Name -match '^android-\d+$' }
if (-not $plain) {
    $real = Get-ChildItem (Join-Path $env:ANDROID_HOME "platforms") -Directory | Where-Object { $_.Name -match '^android-(\d+)\.\d+$' } | Sort-Object Name -Descending | Select-Object -First 1
    if ($real) {
        $shim = Join-Path $env:TEMP "nextbox-android-sdk"
        $level = [regex]::Match($real.Name, '^android-(\d+)').Groups[1].Value
        $link = Join-Path $shim "platforms\android-$level"
        if (-not (Test-Path $link)) {
            New-Item -ItemType Directory -Force (Join-Path $shim "platforms") | Out-Null
            New-Item -ItemType Junction -Path $link -Target $real.FullName | Out-Null
        }
        $env:ANDROID_HOME = $shim
    }
}
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr" }
$env:Path = (Join-Path $env:JAVA_HOME "bin") + ";" + $env:Path

# Only what NextBox uses: gvisor (TUN stack), QUIC (Hysteria2 / TUIC),
# uTLS (fingerprints, Reality). Leaving out tailscale, wireguard, openvpn,
# openconnect, usbip, naive and the clash API keeps the library small.
$tags = "with_gvisor,with_quic,with_utls,badlinkname,tfogo_checklinkname0,ts_omit_logtail,ts_omit_ssh,ts_omit_drive,ts_omit_taildrop,ts_omit_webclient,ts_omit_doctor,ts_omit_capture,ts_omit_kube,ts_omit_aws,ts_omit_synology,ts_omit_bird"
$version = "1.14.4"
$ldflags = "-X github.com/sagernet/sing-box/constant.Version=$version -X runtime.godebugDefault=multipathtcp=0,tlssha1=1 -checklinkname=0"
if (-not $DebugBuild) { $ldflags += " -s -w -buildid=" }

$libs = Join-Path $here "..\app\libs"
New-Item -ItemType Directory -Force $libs | Out-Null
$out = Join-Path $libs "libbox.aar"

Push-Location $here
# gomobile keeps per-ABI sources in ./build and fails on leftovers.
Remove-Item -Recurse -Force (Join-Path $here "build") -ErrorAction SilentlyContinue
try {
    go mod tidy
    if ($LASTEXITCODE -ne 0) { throw "go mod tidy failed" }
    # An argument array: PowerShell would otherwise split "-javapkg=a.b.c"
    # and the space separated linker flags.
    $bindArgs = @(
        'bind', '-v', '-o', $out, '-target', $Targets, '-androidapi', '29',
        '-javapkg=dev.efah.nextbox', '-libname=box',
        '-trimpath', '-buildvcs=false', '-ldflags', $ldflags, '-tags', $tags,
        'github.com/sagernet/sing-box/experimental/libbox', 'nextbox/mobile', 'nextbox-android/core/nbtest'
    )
    # gomobile panics on Windows' hidden "=C:" variables; cleanenv drops them.
    $cleanenv = Join-Path $env:TEMP "nextbox-cleanenv.exe"
    go build -o $cleanenv ./cmd/cleanenv
    if ($LASTEXITCODE -ne 0) { throw "building cleanenv failed" }
    & $cleanenv gomobile @bindArgs
    if ($LASTEXITCODE -ne 0) { throw "gomobile bind failed" }
} finally {
    Remove-Item -Recurse -Force (Join-Path $here "build") -ErrorAction SilentlyContinue
    Pop-Location
}
Get-Item $out | Select-Object Name, @{n = "MB"; e = { [math]::Round($_.Length / 1MB, 1) } }
