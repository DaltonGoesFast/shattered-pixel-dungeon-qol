# Sequential load test for points_command.py (matches Streamer.bot blocking queue).
# Usage:
#   .\points_stress_test.ps1
#   .\points_stress_test.ps1 -Count 10
#   .\points_stress_test.ps1 -Command scroll -Count 50
#   .\points_stress_test.ps1 -Command spawn -Arg rat -Count 100

param(
    [string]$Command = "spawn",
    [string]$Arg = "rat",
    [int]$Count = 100,
    [string]$UserPrefix = "loadtest"
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$script = Join-Path $PSScriptRoot "points_command.py"
if (-not (Test-Path $script)) {
    Write-Error "Not found: $script"
}

Write-Host "Running $Count x: python points_command.py $Command $Arg ${UserPrefix}N"
Write-Host "Game + server.py must be running. Set command free in points-config if needed."
Write-Host ""

$sw = [System.Diagnostics.Stopwatch]::StartNew()
$ok = 0
$fail = 0

for ($i = 1; $i -le $Count; $i++) {
    $user = "${UserPrefix}${i}"
    if ($Command -eq "spawn" -or $Command -eq "champion") {
        & python $script $Command $Arg $user
    } elseif ($Command -eq "gold") {
        & python $script $Command $Arg $user
    } else {
        & python $script $Command $user
    }
    if ($LASTEXITCODE -eq 0) { $ok++ } else { $fail++ }
    if ($i % 10 -eq 0) { Write-Host "  ... $i / $Count" }
}

$sw.Stop()
$sec = [math]::Round($sw.Elapsed.TotalSeconds, 1)
$rate = if ($sw.Elapsed.TotalSeconds -gt 0) { [math]::Round($Count / $sw.Elapsed.TotalSeconds, 2) } else { 0 }
Write-Host ""
Write-Host "Done: $Count commands in ${sec}s ($rate per second). ok=$ok fail=$fail"
Write-Host "See points_command_trace.log and spawn_result_last.txt in this folder."
