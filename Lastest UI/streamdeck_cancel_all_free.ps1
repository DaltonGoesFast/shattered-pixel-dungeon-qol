# Clears every timed "free" promo (same data as the points dashboard).
# Overwrites free_until.json with an empty object. Lastest UI server does not need to be running.

$path = Join-Path $PSScriptRoot 'free_until.json'
Set-Content -LiteralPath $path -Value '{}' -Encoding utf8
