# Renders docs/preview.png (Chinese) and docs/preview-en.png (English) from
# preview.html with headless Chrome or Edge.
#
#   powershell -ExecutionPolicy Bypass -File docs/render/render.ps1
$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$browser = @(
    "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
    "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe"
) | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $browser) { throw "Chrome or Edge is required" }
$page = "file:///" + ((Join-Path $here "preview.html") -replace "\\", "/")
foreach ($v in @(@("preview.png", ""), @("preview-en.png", "?lang=en"))) {
    $out = Join-Path (Split-Path -Parent $here) $v[0]
    $a = @("--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1",
        "--window-size=1800,1040", "--virtual-time-budget=2000", "--screenshot=$out", "$page$($v[1])")
    Start-Process -FilePath $browser -ArgumentList $a -Wait -NoNewWindow
    Get-Item $out | Select-Object Name, Length
}
