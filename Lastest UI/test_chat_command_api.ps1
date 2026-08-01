# Test script for POST /api/chat-command and related endpoints.
# Phase 0: defines the minimum test matrix from streaming-system-rework-plan.md.
# Run after server.py is up. Chat-command tests skip until Phase 1 implements the route.
#
# Usage:
#   cd "Lastest UI"
#   .\test_chat_command_api.ps1
#   .\test_chat_command_api.ps1 -BaseUrl "http://127.0.0.1:5000" -Verbose

param(
    [string]$BaseUrl = "http://127.0.0.1:5000",
    [string]$TestUser = "phase0_tester",
    [switch]$Verbose
)

$ErrorActionPreference = "Continue"
$script:Passed = 0
$script:Skipped = 0
$script:Failed = 0

function Write-TestResult {
    param([string]$Name, [string]$Status, [string]$Detail = "")
    switch ($Status) {
        "PASS" { $script:Passed++; Write-Host "[PASS] $Name" -ForegroundColor Green }
        "SKIP" { $script:Skipped++; Write-Host "[SKIP] $Name - $Detail" -ForegroundColor Yellow }
        "FAIL" { $script:Failed++; Write-Host "[FAIL] $Name - $Detail" -ForegroundColor Red }
    }
    if ($Verbose -and $Detail -and $Status -ne "SKIP") {
        Write-Host "       $Detail" -ForegroundColor DarkGray
    }
}

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body = $null
    )
    $uri = "$BaseUrl$Path"
    $params = @{
        Uri = $uri
        Method = $Method
        TimeoutSec = 15
        ErrorAction = "Stop"
    }
    if ($Body -ne $null) {
        $params.ContentType = "application/json"
        $params.Body = ($Body | ConvertTo-Json -Depth 6 -Compress)
    }
    return Invoke-RestMethod @params
}

function Test-ServerUp {
    try {
        $r = Invoke-Api -Method GET -Path "/api/status"
        if ($r) {
            Write-TestResult "Server /api/status" "PASS"
            return $true
        }
    } catch {
        Write-TestResult "Server /api/status" "FAIL" $_.Exception.Message
        return $false
    }
    return $false
}

function Test-ChatCommandRouteExists {
    $body = @{
        rawMessage = "!points"
        username = $TestUser
        platform = "twitch"
        context = @{
            isSubscribed = $false
            isMember = $false
            isBroadcaster = $false
            bits = 0
        }
    }
    try {
        $r = Invoke-Api -Method POST -Path "/api/chat-command" -Body $body
        if ($null -ne $r.ok) {
            Write-TestResult "POST /api/chat-command (route exists)" "PASS" "ok=$($r.ok)"
            return $true
        }
        Write-TestResult "POST /api/chat-command" "FAIL" "Unexpected response shape"
        return $false
    } catch {
        $msg = $_.Exception.Message
        if ($msg -match "404|Not Found") {
            Write-TestResult "POST /api/chat-command" "SKIP" "Not implemented yet (Phase 1)"
            return $false
        }
        Write-TestResult "POST /api/chat-command" "FAIL" $msg
        return $false
    }
}

function Test-SessionResetRouteExists {
    try {
        $r = Invoke-Api -Method POST -Path "/api/session/reset" -Body @{}
        Write-TestResult "POST /api/session/reset" "PASS"
        return $true
    } catch {
        $msg = $_.Exception.Message
        if ($msg -match "404|Not Found") {
            Write-TestResult "POST /api/session/reset" "SKIP" "Not implemented yet (Phase 1)"
            return $false
        }
        Write-TestResult "POST /api/session/reset" "FAIL" $msg
        return $false
    }
}

function Test-DonationCheer {
    $body = @{
        bits = 1
        username = $TestUser
        isSubscribed = "0"
        userIsSponsor = "0"
    }
    try {
        $r = Invoke-Api -Method POST -Path "/api/donation/cheer" -Body $body
        if ($null -ne $r.ok) {
            Write-TestResult "POST /api/donation/cheer" "PASS" "ok=$($r.ok) pointsAdded=$($r.pointsAdded)"
        } else {
            Write-TestResult "POST /api/donation/cheer" "FAIL" "Missing ok field"
        }
    } catch {
        Write-TestResult "POST /api/donation/cheer" "FAIL" $_.Exception.Message
    }
}

function Test-DonationSuperchat {
    $body = @{
        microAmount = 100
        currencyCode = "USD"
        username = $TestUser
        isSubscribed = "0"
        userIsSponsor = "0"
    }
    try {
        $r = Invoke-Api -Method POST -Path "/api/donation/superchat" -Body $body
        if ($null -ne $r.ok) {
            Write-TestResult "POST /api/donation/superchat" "PASS" "ok=$($r.ok)"
        } else {
            Write-TestResult "POST /api/donation/superchat" "FAIL" "Missing ok field"
        }
    } catch {
        Write-TestResult "POST /api/donation/superchat" "FAIL" $_.Exception.Message
    }
}

# --- Chat-command matrix (runs only when route exists) ---

function Test-ChatCommandMatrix {
    param([bool]$RouteReady)

    if (-not $RouteReady) {
        @(
            "earn.message (non-command chat)"
            "earn.message cooldown skip"
            "earn.passive"
            "first_words +5"
            "!points query"
            "!toppoints query"
            "!spawn rat ok / insufficient / disabled"
            "!gold 50 args"
            "!fard presentation + silent repeat"
            "!doublepoints broadcaster gate"
            "!summon rat"
            "unknown !foo error"
            "Twitch + YouTube platform reply (manual)"
        ) | ForEach-Object {
            Write-TestResult $_ "SKIP" "Phase 1+ / manual"
        }
        return
    }

    $cases = @(
        @{ Name = "!points query"; Body = @{
                rawMessage = "!points"; username = $TestUser; platform = "twitch"
                context = @{ isSubscribed = $false; isMember = $false; isBroadcaster = $false; bits = 0 }
            }
        }
        @{ Name = "!spawn usage error"; Body = @{
                rawMessage = "!spawn"; username = $TestUser; platform = "twitch"
                context = @{ isSubscribed = $false; isMember = $false; isBroadcaster = $false; bits = 0 }
            }
        }
        @{ Name = "unknown command"; Body = @{
                rawMessage = "!notacommand"; username = $TestUser; platform = "twitch"
                context = @{ isSubscribed = $false; isMember = $false; isBroadcaster = $false; bits = 0 }
            }
        }
        @{ Name = "earn.message"; Body = @{
                rawMessage = "hello from phase0 test"; username = $TestUser; platform = "twitch"
                context = @{ isSubscribed = $false; isMember = $false; isBroadcaster = $false; bits = 0 }
            }
        }
    )

    foreach ($case in $cases) {
        try {
            $r = Invoke-Api -Method POST -Path "/api/chat-command" -Body $case.Body
            if ($null -ne $r.ok) {
                Write-TestResult $case.Name "PASS" "message=$($r.message)"
            } else {
                Write-TestResult $case.Name "FAIL" "Bad response"
            }
        } catch {
            Write-TestResult $case.Name "FAIL" $_.Exception.Message
        }
    }
}

# --- Main ---

Write-Host ""
Write-Host "Streaming rework API tests - $BaseUrl" -ForegroundColor Cyan
Write-Host ""

if (-not (Test-ServerUp)) {
    Write-Host ""
    Write-Host "Start the overlay first: python server.py" -ForegroundColor Yellow
    exit 1
}

$chatReady = Test-ChatCommandRouteExists
Test-SessionResetRouteExists | Out-Null
Test-DonationCheer
Test-DonationSuperchat
Test-ChatCommandMatrix -RouteReady $chatReady

Write-Host ""
Write-Host "Results: $($script:Passed) passed, $($script:Skipped) skipped, $($script:Failed) failed" -ForegroundColor Cyan
Write-Host ""

if ($script:Failed -gt 0) { exit 1 }
exit 0
