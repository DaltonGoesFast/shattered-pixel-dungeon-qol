# Set any points cost as free for N minutes (POST /api/cost-free).
# Minutes: use preset buttons (streamdeck_set_free_duration.ps1), optional override as 2nd argument.
#
# Plugin examples (script body only):
#   & "...\Lastest UI\streamdeck_cost_free.ps1" cost_per_curse
#   & "...\Lastest UI\streamdeck_cost_free.ps1" cost_per_monster.rat 10
#
param(
    [Parameter(Position = 0, Mandatory = $true)]
    [string]$CostKey,
    [Parameter(Position = 1)]
    [int]$Minutes = 0
)

$ErrorActionPreference = 'Stop'
$CostKey = $CostKey.Trim()
if (-not $CostKey) {
    throw 'CostKey is required.'
}

$logPath = Join-Path $PSScriptRoot 'streamdeck_cost_free_last_error.txt'

function Show-Err {
    param([string]$Title, [string]$Message)
    try {
        Add-Type -AssemblyName System.Windows.Forms -ErrorAction Stop
        [System.Windows.Forms.MessageBox]::Show($Message, $Title, 'OK', 'Error') | Out-Null
    } catch {
        [Console]::Error.WriteLine($Message)
    }
}

if ($Minutes -lt 1) {
    $durPath = Join-Path $PSScriptRoot 'streamdeck_free_duration.txt'
    $parsed = 0
    if (Test-Path $durPath) {
        [void][int]::TryParse((Get-Content -LiteralPath $durPath -Raw).Trim(), [ref]$parsed)
    }
    $Minutes = if ($parsed -ge 1) { $parsed } else { 5 }
}
$Minutes = [Math]::Max(1, [Math]::Min(1440, $Minutes))
$uri = 'http://127.0.0.1:5000/api/cost-free'
$body = @{ costKey = $CostKey; minutes = $Minutes } | ConvertTo-Json

try {
    $null = Invoke-RestMethod -Uri $uri -Method POST -ContentType 'application/json; charset=utf-8' -Body $body
} catch {
    $full = @"
$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')

URI: $uri
Body: $body

$($_.Exception | Format-List * -Force | Out-String)
$($_.ErrorDetails.Message)

$_
"@
    try {
        $full | Set-Content -Path $logPath -Encoding utf8
    } catch { }

    $short = "Free promo failed ($CostKey).`n`nCommon causes: server not running (python server.py), wrong port (5000).`n`nDetails:`n$logPath"
    Show-Err -Title 'streamdeck_cost_free.ps1' -Message $short
    exit 1
}
