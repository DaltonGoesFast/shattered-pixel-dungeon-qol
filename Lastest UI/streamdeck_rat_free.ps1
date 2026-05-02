# Rat spawn free for N minutes. Wrapper around streamdeck_cost_free.ps1.
# See that file and streamdeck-free-commands.md for plugin usage.
#
param(
    [Parameter(Position = 0)]
    [int]$Minutes = 0
)

& (Join-Path $PSScriptRoot 'streamdeck_cost_free.ps1') -CostKey 'cost_per_monster.rat' -Minutes $Minutes
