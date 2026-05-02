# Saves how many minutes “free” actions should last for Stream Deck buttons that don’t pass a duration.
# Writes streamdeck_free_duration.txt next to this script (gitignored).
#
# Plugin (script body), examples:
#   & "...\Lastest UI\streamdeck_set_free_duration.ps1" 1
#   & "...\Lastest UI\streamdeck_set_free_duration.ps1" 5
#
# Then run rat-free (or future scripts) with no minute argument so they use this value:
#   & "...\Lastest UI\streamdeck_rat_free.ps1"

param(
    [Parameter(Position = 0)]
    [int]$Minutes = 5
)

$Minutes = [Math]::Max(1, [Math]::Min(1440, $Minutes))
$path = Join-Path $PSScriptRoot 'streamdeck_free_duration.txt'
Set-Content -LiteralPath $path -Value "$Minutes" -Encoding utf8
