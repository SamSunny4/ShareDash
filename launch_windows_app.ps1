# ShareDash Windows App PowerShell Launcher
Set-Location -Path $PSScriptRoot

Write-Host "===============================================================" -ForegroundColor Cyan
Write-Host "       ShareDash: Quick Share Windows Desktop App              " -ForegroundColor Cyan
Write-Host "===============================================================" -ForegroundColor Cyan

# Start the Rust release engine in the background
$proc = Get-Process -Name "sharedash" -ErrorAction SilentlyContinue
if (-not $proc) {
    Write-Host "Starting ShareDash background core engine..." -ForegroundColor Yellow
    Start-Process -FilePath ".\target\release\sharedash.exe" -ArgumentList "--port 54321" -WindowStyle Hidden
    Start-Sleep -Seconds 1
}

$url = "http://127.0.0.1:54321"
$edgePath = "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe"
$chromePath = "${env:ProgramFiles}\Google\Chrome\Application\chrome.exe"

Write-Host "Opening Quick Share desktop window..." -ForegroundColor Green
if (Test-Path $edgePath) {
    Start-Process -FilePath $edgePath -ArgumentList "--app=$url", "--window-size=1180,820"
} elseif (Test-Path $chromePath) {
    Start-Process -FilePath $chromePath -ArgumentList "--app=$url", "--window-size=1180,820"
} else {
    Start-Process $url
}

Write-Host "ShareDash Windows App launched successfully!" -ForegroundColor Green
