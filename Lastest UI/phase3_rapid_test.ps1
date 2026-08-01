# Rapid Phase 3 test runner - server API and/or Streamer.bot R1 end-to-end.
#
# Prerequisites:
#   - python server.py running (port 5000)
#   - StreamerBot mode: Streamer.bot HTTP server enabled (default 7474), OBS connected, R1-R9 enabled
#
# Usage:
#   cd "Lastest UI"
#   .\phase3_rapid_test.ps1                          # default: Streamer.bot R1 burst
#   .\phase3_rapid_test.ps1 -Mode Api                # server only (fast, no OBS/chat)
#   .\phase3_rapid_test.ps1 -Mode Both               # API then Streamer.bot
#   .\phase3_rapid_test.ps1 -DelayMs 2000            # slower (presentation queue / !fard)
#   .\phase3_rapid_test.ps1 -ResetSession            # POST /api/session/reset first
#   .\phase3_rapid_test.ps1 -Commands "!points","!spawn rat","hello"
#   .\phase3_rapid_test.ps1 -RotateUsers -Count 5    # 5 users x default command list
#   .\phase3_rapid_test.ps1 -Scenario Fard            # preset: fresh session + !fard x2
#   .\phase3_rapid_test.ps1 -Scenario SpawnStorm -ConcurrentUsers 20
#   .\phase3_rapid_test.ps1 -ListScenarios            # print all presets

param(
    [ValidateSet(
        "Default", "Phase3", "Fard", "Earn", "FirstWords", "Spend", "Summon",
        "Errors", "MultiUser", "Donations", "SpendGate", "YouTube", "Passive",
        "DoublePoints", "AllChat", "All", "SpawnStorm"
    )]
    [string]$Scenario = "Default",

    [switch]$ListScenarios,

    [ValidateSet("Api", "StreamerBot", "Both")]
    [string]$Mode = "StreamerBot",

    [string]$BaseUrl = "http://127.0.0.1:5000",
    [string]$StreamerBotUrl = "http://127.0.0.1:7474",
    [string]$ChatActionName = "R01 - Chat Router",
    [string]$CheerActionName = "R4 - Cheer",
    [string]$SuperchatActionName = "R5 - Superchat",
    [string]$SpendToggleActionName = "R8 - Spend Toggle",

    [string]$User = "rapidtest",
    [string]$Platform = "twitch",
    [switch]$RotateUsers,
    [int]$Count = 1,

    [int]$ConcurrentUsers = 10,
    [string]$StormCommand = "!spawn rat",
    [int]$SeedPoints = 0,

    [int]$DelayMs = 0,
    [switch]$ResetSession,
    [switch]$VerboseOutput,

    [string[]]$Commands = @(
        "hello from rapid test",
        "!points",
        "!toppoints",
        "!spawn rat",
        "!fard",
        "!fard",
        "!notacommand"
    )
)

$ErrorActionPreference = "Continue"

if ($DelayMs -le 0) {
    $DelayMs = if ($Mode -eq "Api") { 50 } else { 1500 }
}

function Write-Line([string]$Text, [string]$Color = "Gray") {
    Write-Host $Text -ForegroundColor $Color
}

function Invoke-ChatApi {
    param(
        [string]$Message,
        [string]$Username,
        [string]$ApiPlatform = $script:RunPlatform,
        [bool]$IsSubscribed = $true,
        [bool]$IsBroadcaster = $true,
        [bool]$IsMember = $false
    )

    $body = @{
        rawMessage = $Message
        username   = $Username
        platform   = $ApiPlatform
        context    = @{
            isSubscribed    = $IsSubscribed
            isMember        = $IsMember
            isBroadcaster   = $IsBroadcaster
            bits            = 0
        }
    }
    $json = $body | ConvertTo-Json -Depth 6 -Compress
    return Invoke-RestMethod -Uri "$BaseUrl/api/chat-command" -Method POST `
        -ContentType "application/json" -Body $json -TimeoutSec 20
}

function Invoke-StreamerBotAction {
    param([string]$ActionName, [hashtable]$ActionArgs)

    $payload = @{
        action = @{ name = $ActionName }
        args   = $ActionArgs
    }
    $json = $payload | ConvertTo-Json -Depth 6 -Compress
    try {
        $response = Invoke-WebRequest -Uri "$StreamerBotUrl/DoAction" -Method POST `
            -ContentType "application/json" -Body $json -TimeoutSec 30 -UseBasicParsing
        if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
            return $true
        }
        throw "DoAction returned HTTP $($response.StatusCode)"
    } catch {
        $msg = $_.Exception.Message
        if ($_.Exception.Response) {
            try {
                $stream = $_.Exception.Response.GetResponseStream()
                if ($stream) {
                    $reader = New-Object System.IO.StreamReader($stream)
                    $body = $reader.ReadToEnd()
                    if ($body) { $msg = "$msg | $body" }
                }
            } catch { }
        }
        throw $msg
    }
}

function Invoke-R1Chat {
    param(
        [string]$Message,
        [string]$Username,
        [string]$ChatPlatform = $script:RunPlatform,
        [bool]$IsSubscribed = $true,
        [bool]$IsBroadcaster = $true,
        [bool]$IsMember = $false
    )

    $actionArgs = @{
        userName      = $Username
        rawMessage    = $Message
        message       = $Message
        eventSource   = $ChatPlatform
        commandSource = $ChatPlatform
        isSubscribed  = $(if ($IsSubscribed) { "True" } else { "False" })
        isModerator   = "True"
        isBroadcaster = $(if ($IsBroadcaster) { "True" } else { "False" })
        userIsSponsor = $(if ($IsMember) { "True" } else { "False" })
    }
    Invoke-StreamerBotAction -ActionName $ChatActionName -ActionArgs $actionArgs | Out-Null
}

function Test-ServerUp {
    try {
        Invoke-RestMethod -Uri "$BaseUrl/api/status" -Method GET -TimeoutSec 5 | Out-Null
        return $true
    } catch {
        Write-Line "Server not reachable at $BaseUrl - start: python server.py" "Red"
        return $false
    }
}

function Resolve-ActionName {
    param([string[]]$Available, [string]$Preferred, [string]$Pattern)

    if ($Available -contains $Preferred) { return $Preferred }
    $match = $Available | Where-Object { $_ -match $Pattern } | Select-Object -First 1
    if ($match) {
        Write-Line ("  Action name: {0} -> {1}" -f $Preferred, $match) "DarkGray"
        return $match
    }
    return $Preferred
}

function Get-StreamerBotActionNames {
    try {
        $r = Invoke-RestMethod -Uri "$StreamerBotUrl/GetActions" -Method GET -TimeoutSec 5
        return @($r.actions | ForEach-Object { $_.name })
    } catch {
        return @()
    }
}

function Test-StreamerBotUp {
    param([ref]$ActionNames)

    try {
        $names = Get-StreamerBotActionNames
        $ActionNames.Value = $names
        $r1 = Resolve-ActionName $names $ChatActionName '^R0*1\b.*Chat'
        if ($names -notcontains $r1 -and ($names | Where-Object { $_ -match '^R1' }).Count -eq 0) {
            Write-Line ("Streamer.bot HTTP OK but action {0} not found." -f $ChatActionName) "Yellow"
            Write-Line ("Available: {0}" -f ($names -join ", ")) "DarkGray"
            return $false
        }
        return $true
    } catch {
        Write-Line "Streamer.bot HTTP not reachable at $StreamerBotUrl" "Red"
        Write-Line "Enable: Streamer.bot -> Settings -> Servers -> HTTP Server" "Yellow"
        return $false
    }
}

function Format-ApiResult($r) {
    $parts = @("ok=$($r.ok)")
    if ($r.message) { $parts += "msg=$($r.message)" }
    if ($r.pts -ne $null) { $parts += "pts=$($r.pts)" }
    if ($r.presentation) { $parts += "presentation=1" }
    if ($r.extra) {
        $sk = $r.extra.skipped
        if ($sk) { $parts += "skipped=$sk" }
    }
    return ($parts -join " | ")
}

function Get-ScenarioCatalog {
    return @(
        @{ Name = "Default";   Desc = "7-command smoke test (hello, points, spawn, fard x2, unknown)" }
        @{ Name = "Phase3";    Desc = "Same as Default (official Phase 3 matrix)" }
        @{ Name = "Fard";      Desc = "Session reset + !fard twice (OBS + already-used reply)" }
        @{ Name = "Earn";      Desc = "Message earn + cooldown (3 hellos, then !points)" }
        @{ Name = "FirstWords"; Desc = "3 new users first chat (first-words bonus path)" }
        @{ Name = "Spend";     Desc = "Spend commands: spawn, gold, wand, heal" }
        @{ Name = "Summon";    Desc = "!summon march: rat, topsummoner, mysummons" }
        @{ Name = "Errors";      Desc = "Usage errors and unknown commands" }
        @{ Name = "MultiUser"; Desc = "5 rotating users x hello/points/spawn" }
        @{ Name = "Donations"; Desc = "API cheer + superchat + gift-membership" }
        @{ Name = "SpendGate"; Desc = "R8 OFF -> !spawn -> R8 ON -> !spawn (StreamerBot)" }
        @{ Name = "YouTube";   Desc = "YouTube platform branch (hello, !points)" }
        @{ Name = "Passive";   Desc = "API earn.passive x3 users (R3 path)" }
        @{ Name = "DoublePoints"; Desc = "Broadcaster !doublepoints then !points" }
        @{ Name = "AllChat";   Desc = "Long chat suite (earn, spend, summon, errors)" }
        @{ Name = "All";       Desc = "Run Default + Fard + Donations + SpendGate sections" }
        @{ Name = "SpawnStorm"; Desc = "Many users fire same command in parallel (!spawn rat)" }
    )
}

function Show-ScenarioList {
    Write-Line "Available -Scenario presets:" "Cyan"
    foreach ($s in Get-ScenarioCatalog) {
        Write-Line ("  {0,-14} {1}" -f $s.Name, $s.Desc) "Gray"
    }
    Write-Line ""
    Write-Line "Examples:" "DarkGray"
    Write-Line "  .\phase3_rapid_test.ps1 -Scenario Fard" "DarkGray"
    Write-Line "  .\phase3_rapid_test.ps1 -Scenario Donations -Mode Api" "DarkGray"
    Write-Line "  .\phase3_rapid_test.ps1 -Scenario MultiUser -Mode StreamerBot" "DarkGray"
    Write-Line "  .\phase3_rapid_test.ps1 -Scenario SpawnStorm -ConcurrentUsers 20" "DarkGray"
    Write-Line ""
}

function New-ChatStep {
    param(
        [string]$Message,
        [string]$Label = "",
        [string]$StepUser = "",
        [string]$StepPlatform = "",
        [bool]$IsSubscribed = $true,
        [bool]$IsBroadcaster = $true,
        [bool]$IsMember = $false
    )
    return @{
        Kind          = "chat"
        Message       = $Message
        Label         = $(if ($Label) { $Label } else { $Message })
        User          = $StepUser
        Platform      = $StepPlatform
        IsSubscribed  = $IsSubscribed
        IsBroadcaster = $IsBroadcaster
        IsMember      = $IsMember
    }
}

function Resolve-ScenarioPlan {
    param([string]$Name)

    $chat = { param($m, $l = "") New-ChatStep -Message $m -Label $l }

    switch ($Name) {
        "Default" {
            return @{
                DelayMs      = 1500
                RotateUsers  = $false
                Count        = 1
                RunPlatform  = $Platform
                Steps        = @(
                    (& $chat "hello from rapid test")
                    (& $chat "!points")
                    (& $chat "!toppoints")
                    (& $chat "!spawn rat")
                    (& $chat "!fard")
                    (& $chat "!fard" "fard repeat")
                    (& $chat "!notacommand")
                )
            }
        }
        "Phase3" { return (Resolve-ScenarioPlan -Name "Default") }
        "Fard" {
            return @{
                DelayMs      = 4500
                ResetSession = $true
                Steps        = @(
                    @{ Kind = "reset" }
                    (& $chat "!fard" "fard first use")
                    @{ Kind = "pause"; Ms = 4500; Label = "wait for R9" }
                    (& $chat "!fard" "fard already used")
                )
            }
        }
        "Earn" {
            return @{
                DelayMs = 300
                Steps   = @(
                    (& $chat "hello earn 1")
                    (& $chat "hello earn 2" "earn cooldown?")
                    (& $chat "hello earn 3" "earn cooldown?")
                    (& $chat "!points" "balance after earn")
                )
            }
        }
        "FirstWords" {
            return @{
                DelayMs     = 800
                RotateUsers = $true
                Count       = 3
                Steps       = @((& $chat "first time chatter here"))
            }
        }
        "Spend" {
            return @{
                DelayMs = 1200
                Steps   = @(
                    (& $chat "!points")
                    (& $chat "!spawn rat")
                    (& $chat "!spawn gnoll")
                    (& $chat "!gold 10")
                    (& $chat "!wand")
                    (& $chat "!heal")
                    (& $chat "!points" "balance after spend")
                )
            }
        }
        "Summon" {
            return @{
                DelayMs = 2000
                Steps   = @(
                    (& $chat "!summon rat")
                    (& $chat "!summon bat")
                    (& $chat "!topsummoner")
                    (& $chat "!mysummons")
                )
            }
        }
        "Errors" {
            return @{
                DelayMs = 800
                Steps   = @(
                    (& $chat "!spawn" "spawn usage error")
                    (& $chat "!gold" "gold usage error")
                    (& $chat "!doublepoints" "broadcaster gate")
                    (& $chat "!notacommand" "unknown command")
                )
            }
        }
        "MultiUser" {
            return @{
                DelayMs     = 1000
                RotateUsers = $true
                Count       = 5
                Steps       = @(
                    (& $chat "hello multi user")
                    (& $chat "!points")
                    (& $chat "!spawn rat")
                )
            }
        }
        "Donations" {
            return @{
                DelayMs = 500
                Steps   = @(
                    @{ Kind = "section"; Label = "Donations API" }
                    @{ Kind = "donation"; Endpoint = "/api/donation/cheer"; Label = "cheer 100 bits"; Body = @{
                        bits = 100; username = "rapidtest"; isSubscribed = $true; userIsSponsor = $false } }
                    @{ Kind = "donation"; Endpoint = "/api/donation/superchat"; Label = "superchat $1"; Body = @{
                        microAmount = 1000000; currencyCode = "USD"; username = "rapidtest"; isSubscribed = $false; userIsSponsor = $false } }
                    @{ Kind = "donation"; Endpoint = "/api/donation/gift-membership"; Label = "gift membership tier1"; Body = @{
                        username = "rapidtest"; tier = "1000"; isSubscribed = $false; userIsSponsor = $false; platform = "twitch" } }
                    @{ Kind = "sbaction"; Action = $CheerActionName; Label = "R4 cheer via SB"; Args = @{
                        userName = "rapidtest"; bits = "50"; isSubscribed = "True"; commandSource = "twitch" } }
                    @{ Kind = "sbaction"; Action = $SuperchatActionName; Label = "R5 superchat via SB"; Args = @{
                        userName = "rapidtest"; user = "rapidtest"; microAmount = "1000000"
                        currencyCode = "USD"; userIsSponsor = "False"
                        eventSource = "youtube"; commandSource = "youtube" } }
                )
            }
        }
        "SpendGate" {
            return @{
                DelayMs = 2000
                Steps   = @(
                    @{ Kind = "section"; Label = "Spend gate (R8 Toggle)" }
                    (& $chat "!points" "balance before gate")
                    @{ Kind = "sbaction"; Action = $SpendToggleActionName; Label = "R8 Spend OFF"; Args = @{ state = "0" } }
                    (& $chat "!spawn rat" "spawn while disabled")
                    @{ Kind = "sbaction"; Action = $SpendToggleActionName; Label = "R8 Spend ON"; Args = @{ state = "1" } }
                    (& $chat "!spawn rat" "spawn after re-enabled")
                    (& $chat "!points" "balance after gate")
                )
            }
        }
        "YouTube" {
            return @{
                DelayMs       = 1200
                RunPlatform   = "youtube"
                Steps         = @(
                    (New-ChatStep -Message "hello youtube" -StepPlatform "youtube")
                    (New-ChatStep -Message "!points" -StepPlatform "youtube")
                    (New-ChatStep -Message "!fard" -StepPlatform "youtube" -IsMember $true)
                )
            }
        }
        "Passive" {
            return @{
                DelayMs     = 300
                RotateUsers = $true
                Count       = 3
                Steps       = @(
                    @{ Kind = "passive"; Label = "earn.passive" }
                )
            }
        }
        "DoublePoints" {
            return @{
                DelayMs = 1000
                Steps   = @(
                    (& $chat "!points" "before 2x")
                    (& $chat "!doublepoints 2" "broadcaster extends 2x")
                    (& $chat "!points" "after 2x")
                )
            }
        }
        "AllChat" {
            return @{
                DelayMs = 1000
                Steps   = @(
                    @{ Kind = "section"; Label = "Earn" }
                    (& $chat "hello allchat suite")
                    (& $chat "!points")
                    @{ Kind = "section"; Label = "Spend" }
                    (& $chat "!spawn rat")
                    (& $chat "!gold 5")
                    @{ Kind = "section"; Label = "Summon" }
                    (& $chat "!summon rat")
                    (& $chat "!topsummoner")
                    @{ Kind = "section"; Label = "Errors" }
                    (& $chat "!spawn")
                    (& $chat "!badcmd")
                )
            }
        }
        "All" {
            $d = Resolve-ScenarioPlan -Name "Default"
            $f = Resolve-ScenarioPlan -Name "Fard"
            $don = Resolve-ScenarioPlan -Name "Donations"
            $sg = Resolve-ScenarioPlan -Name "SpendGate"
            return @{
                DelayMs = 1500
                Steps   = @(
                    @{ Kind = "section"; Label = "=== Default smoke ===" }
                ) + $d.Steps + @(
                    @{ Kind = "section"; Label = "=== Fard ===" }
                ) + $f.Steps + @(
                    @{ Kind = "section"; Label = "=== Donations ===" }
                ) + $don.Steps + @(
                    @{ Kind = "section"; Label = "=== Spend gate ===" }
                ) + $sg.Steps
            }
        }
        default { return (Resolve-ScenarioPlan -Name "Default") }
    }
}

function Invoke-DonationApi {
    param([string]$Endpoint, [hashtable]$Body, [string]$Label)

    $json = $Body | ConvertTo-Json -Depth 6 -Compress
    return Invoke-RestMethod -Uri "$BaseUrl$Endpoint" -Method POST `
        -ContentType "application/json" -Body $json -TimeoutSec 20
}

function Invoke-PassiveApi {
    param([string]$Username)

    $body = @{
        type     = "earn.passive"
        username = $Username
        platform = $script:RunPlatform
        context  = @{
            isSubscribed  = $true
            isMember      = $false
            isBroadcaster = $false
            bits          = 0
        }
    }
    $json = $body | ConvertTo-Json -Depth 6 -Compress
    return Invoke-RestMethod -Uri "$BaseUrl/api/chat-command" -Method POST `
        -ContentType "application/json" -Body $json -TimeoutSec 20
}

function Invoke-TestStep {
    param(
        [hashtable]$Step,
        [int]$RunIndex,
        [string]$Username,
        [ref]$ApiOk,
        [ref]$ApiFail,
        [ref]$SbOk,
        [ref]$SbFail
    )

    $kind = $Step.Kind
    $label = if ($Step.Label) { $Step.Label } else { $kind }

    if ($kind -eq "section") {
        Write-Line ""
        Write-Line ("--- {0} ---" -f $Step.Label) "Yellow"
        return
    }

    if ($kind -eq "reset") {
        try {
            Invoke-RestMethod -Uri "$BaseUrl/api/session/reset" -Method POST `
                -ContentType "application/json" -Body "{}" -TimeoutSec 15 | Out-Null
            Write-Line ("[{0,3}] RESET session OK" -f $RunIndex) "Green"
        } catch {
            Write-Line ("[{0,3}] RESET FAIL: {1}" -f $RunIndex, $_.Exception.Message) "Red"
            $ApiFail.Value++
        }
        return
    }

    if ($kind -eq "pause") {
        $ms = [int]$Step.Ms
        Write-Line ("[{0,3}] PAUSE {1}ms ({2})" -f $RunIndex, $ms, $label) "DarkGray"
        Start-Sleep -Milliseconds $ms
        return
    }

    if ($kind -eq "donation") {
        try {
            $r = Invoke-DonationApi -Endpoint $Step.Endpoint -Body $Step.Body -Label $label
            $ApiOk.Value++
            $pts = if ($r.pointsAdded) { $r.pointsAdded } else { "" }
            Write-Line ("[{0,3}] API  {1,-28} ok=$($r.ok) pts=$pts" -f $RunIndex, $label) "Green"
            if ($VerboseOutput) { $r | ConvertTo-Json -Depth 6 | Write-Host }
        } catch {
            $ApiFail.Value++
            Write-Line ("[{0,3}] API  {1,-28} FAIL: {2}" -f $RunIndex, $label, $_.Exception.Message) "Red"
        }
        return
    }

    if ($kind -eq "passive") {
        if ($Mode -in @("Api", "Both")) {
            try {
                $r = Invoke-PassiveApi -Username $Username
                $ApiOk.Value++
                Write-Line ("[{0,3}] API  {1,-28} {2}" -f $RunIndex, $label, (Format-ApiResult $r)) "Green"
            } catch {
                $ApiFail.Value++
                Write-Line ("[{0,3}] API  {1,-28} FAIL: {2}" -f $RunIndex, $label, $_.Exception.Message) "Red"
            }
        } else {
            Write-Line ("[{0,3}] SKIP passive (use -Mode Api or Both)" -f $RunIndex) "Yellow"
        }
        return
    }

    if ($kind -eq "sbaction") {
        if ($Mode -in @("StreamerBot", "Both")) {
            try {
                $actionArgs = @{}
                if ($Step.Args) {
                    foreach ($k in $Step.Args.Keys) { $actionArgs[$k] = $Step.Args[$k] }
                }
                Invoke-StreamerBotAction -ActionName $Step.Action -ActionArgs $actionArgs | Out-Null
                $SbOk.Value++
                Write-Line ("[{0,3}] SB   {1,-28} queued $($Step.Action)" -f $RunIndex, $label) "Cyan"
            } catch {
                $SbFail.Value++
                Write-Line ("[{0,3}] SB   {1,-28} FAIL: {2}" -f $RunIndex, $label, $_.Exception.Message) "Red"
            }
        } else {
            Write-Line ("[{0,3}] SKIP sbaction (StreamerBot mode only)" -f $RunIndex) "Yellow"
        }
        return
    }

    if ($kind -eq "chat") {
        $msg = $Step.Message
        $user = if ($Step.User) { $Step.User } else { $Username }
        $plat = if ($Step.Platform) { $Step.Platform } else { $script:RunPlatform }
        $isSub = if ($null -ne $Step.IsSubscribed) { [bool]$Step.IsSubscribed } else { $true }
        $isBroad = if ($null -ne $Step.IsBroadcaster) { [bool]$Step.IsBroadcaster } else { $true }
        $isMember = if ($null -ne $Step.IsMember) { [bool]$Step.IsMember } else { $false }

        if ($Mode -in @("Api", "Both")) {
            try {
                $r = Invoke-ChatApi -Message $msg -Username $user -ApiPlatform $plat `
                    -IsSubscribed $isSub -IsBroadcaster $isBroad -IsMember $isMember
                $ApiOk.Value++
                Write-Line ("[{0,3}] API  {1,-28} {2}" -f $RunIndex, $label, (Format-ApiResult $r)) "Green"
                if ($VerboseOutput) { $r | ConvertTo-Json -Depth 6 | Write-Host }
            } catch {
                $ApiFail.Value++
                Write-Line ("[{0,3}] API  {1,-28} FAIL: {2}" -f $RunIndex, $label, $_.Exception.Message) "Red"
            }
        }

        if ($Mode -in @("StreamerBot", "Both")) {
            try {
                Invoke-R1Chat -Message $msg -Username $user -ChatPlatform $plat `
                    -IsSubscribed $isSub -IsBroadcaster $isBroad -IsMember $isMember
                $SbOk.Value++
                Write-Line ("[{0,3}] SB   {1,-28} queued R1 ($user)" -f $RunIndex, $label) "Cyan"
            } catch {
                $SbFail.Value++
                Write-Line ("[{0,3}] SB   {1,-28} FAIL: {2}" -f $RunIndex, $label, $_.Exception.Message) "Red"
            }
        }
    }
}

function Invoke-SeedStormUsers {
    param([int]$Points, [string[]]$Users)

    if ($Points -le 0 -or $Users.Count -eq 0) { return $true }
    $body = @{
        points = $Points
        users  = @($Users | ForEach-Object { $_.ToLowerInvariant() })
    }
    $json = $body | ConvertTo-Json -Depth 4 -Compress
    try {
        Invoke-RestMethod -Uri "$BaseUrl/api/viewer-points/bulk/set" -Method POST `
            -ContentType "application/json" -Body $json -TimeoutSec 20 | Out-Null
        Write-Line ("[seed] Set {0} pts for {1} users ({2}...)" -f $Points, $Users.Count, $Users[0]) "Green"
        return $true
    } catch {
        Write-Line ("[seed] FAILED: {0}" -f $_.Exception.Message) "Red"
        return $false
    }
}

function Invoke-SpawnStorm {
    param(
        [int]$UserCount,
        [string]$Message,
        [string]$UserPrefix,
        [int]$PointsSeed
    )

    $script:RunPlatform = $Platform
    $users = 1..$UserCount | ForEach-Object { "{0}{1}" -f $UserPrefix, $_ }

    Write-Line ""
    Write-Line "SpawnStorm - $UserCount users x `"$Message`" (parallel) | Mode=$Mode" "Cyan"
    Write-Line ("Users: {0} ... {1}" -f $users[0], $users[-1]) "DarkGray"
    Write-Line ""

    if (-not (Test-ServerUp)) { exit 1 }

    $sbNames = @()
    $resolvedR1 = $ChatActionName
    if ($Mode -in @("StreamerBot", "Both")) {
        if (-not (Test-StreamerBotUp ([ref]$sbNames))) { exit 1 }
        $resolvedR1 = Resolve-ActionName $sbNames $ChatActionName '^R0*1\b.*Chat'
    }

    if ($PointsSeed -gt 0) {
        if (-not (Invoke-SeedStormUsers -Points $PointsSeed -Users $users)) { exit 1 }
    }

    $apiJob = {
        param($BaseUrl, $Message, $Username, $Platform)
        $body = @{
            rawMessage = $Message
            username   = $Username
            platform   = $Platform
            context    = @{
                isSubscribed  = $true
                isMember      = $false
                isBroadcaster = $true
                bits          = 0
            }
        }
        $json = $body | ConvertTo-Json -Depth 6 -Compress
        try {
            $r = Invoke-RestMethod -Uri "$BaseUrl/api/chat-command" -Method POST `
                -ContentType "application/json" -Body $json -TimeoutSec 45
            $msg = if ($r.message) { $r.message } else { "" }
            return @{
                channel = "API"
                user    = $Username
                ok      = [bool]$r.ok
                detail  = "ok=$($r.ok) pts=$($r.pts) $msg"
            }
        } catch {
            return @{ channel = "API"; user = $Username; ok = $false; detail = $_.Exception.Message }
        }
    }

    $sbJob = {
        param($SbUrl, $ActionName, $Message, $Username, $Platform)
        $payload = @{
            action = @{ name = $ActionName }
            args   = @{
                userName      = $Username
                rawMessage    = $Message
                message       = $Message
                eventSource   = $Platform
                commandSource = $Platform
                isSubscribed  = "True"
                isBroadcaster = "True"
            }
        }
        $json = $payload | ConvertTo-Json -Depth 6 -Compress
        try {
            $response = Invoke-WebRequest -Uri "$SbUrl/DoAction" -Method POST `
                -ContentType "application/json" -Body $json -TimeoutSec 45 -UseBasicParsing
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
                return @{ channel = "SB"; user = $Username; ok = $true; detail = "queued R1" }
            }
            return @{ channel = "SB"; user = $Username; ok = $false; detail = "HTTP $($response.StatusCode)" }
        } catch {
            return @{ channel = "SB"; user = $Username; ok = $false; detail = $_.Exception.Message }
        }
    }

    $jobs = @()
    $sw = [System.Diagnostics.Stopwatch]::StartNew()

    foreach ($u in $users) {
        if ($Mode -in @("Api", "Both")) {
            $jobs += Start-Job -ScriptBlock $apiJob -ArgumentList $BaseUrl, $Message, $u, $script:RunPlatform
        }
        if ($Mode -in @("StreamerBot", "Both")) {
            $jobs += Start-Job -ScriptBlock $sbJob -ArgumentList $StreamerBotUrl, $resolvedR1, $Message, $u, $script:RunPlatform
        }
    }

    Write-Line ("Fired {0} parallel jobs..." -f $jobs.Count) "Yellow"
    $results = $jobs | Wait-Job | Receive-Job
    $jobs | Remove-Job -Force
    $sw.Stop()

    $apiOk = 0; $apiFail = 0; $sbOk = 0; $sbFail = 0
    foreach ($r in ($results | Sort-Object user, channel)) {
        $color = if ($r.ok) { "Green" } else { "Red" }
        if ($r.channel -eq "API") { if ($r.ok) { $apiOk++ } else { $apiFail++ } }
        else { if ($r.ok) { $sbOk++ } else { $sbFail++ } }
        $short = $r.detail
        if ($short.Length -gt 72) { $short = $short.Substring(0, 69) + "..." }
        Write-Line ("  {0,-3} {1,-10} {2,-16} {3}" -f $r.channel, $r.user, $(if ($r.ok) { "OK" } else { "FAIL" }), $short) $color
    }

    Write-Line ""
    $rate = if ($sw.Elapsed.TotalSeconds -gt 0) { [math]::Round($jobs.Count / $sw.Elapsed.TotalSeconds, 1) } else { 0 }
    Write-Line ("Done in {0:N1}s ({1} jobs/s) - API ok=$apiOk fail=$apiFail | SB ok=$sbOk fail=$sbFail" -f $sw.Elapsed.TotalSeconds, $rate) "Cyan"
    Write-Line "Watch Streamer.bot Action History (points queue) and game spawns." "DarkGray"
    Write-Line ""

    if ($apiFail -gt 0 -or $sbFail -gt 0) { exit 1 }
    exit 0
}

# --- Main ---

if ($ListScenarios) {
    Show-ScenarioList
    exit 0
}

if ($Scenario -eq "SpawnStorm") {
    if (-not $PSBoundParameters.ContainsKey("ConcurrentUsers")) { $ConcurrentUsers = 15 }
    if (-not $PSBoundParameters.ContainsKey("SeedPoints")) { $SeedPoints = 500 }
    if (-not $PSBoundParameters.ContainsKey("User")) { $User = "storm" }
    Invoke-SpawnStorm -UserCount $ConcurrentUsers -Message $StormCommand -UserPrefix $User -PointsSeed $SeedPoints
}

if ($Scenario -eq "All" -and -not $PSBoundParameters.ContainsKey("Mode")) {
    $Mode = "Both"
}

if (-not (Test-ServerUp)) { exit 1 }

$sbNames = @()
if ($Mode -in @("StreamerBot", "Both")) {
    if (-not (Test-StreamerBotUp ([ref]$sbNames))) { exit 1 }
    $ChatActionName = Resolve-ActionName $sbNames $ChatActionName '^R0*1\b.*Chat'
    $CheerActionName = Resolve-ActionName $sbNames $CheerActionName '^R0*4\b.*Cheer'
    $SuperchatActionName = Resolve-ActionName $sbNames $SuperchatActionName '^R0*5\b.*Super'
    $SpendToggleActionName = Resolve-ActionName $sbNames $SpendToggleActionName '^R0*8\b.*(Toggle|Spend)'
}

$plan = Resolve-ScenarioPlan -Name $Scenario

if (-not $PSBoundParameters.ContainsKey("DelayMs") -or $DelayMs -le 0) {
    if ($plan.DelayMs -gt 0) { $DelayMs = $plan.DelayMs }
    elseif ($Mode -eq "Api") { $DelayMs = 50 }
    else { $DelayMs = 1500 }
}
if ($plan.ContainsKey("RotateUsers") -and -not $PSBoundParameters.ContainsKey("RotateUsers")) {
    $RotateUsers = [bool]$plan.RotateUsers
}
if ($plan.ContainsKey("Count") -and -not $PSBoundParameters.ContainsKey("Count")) {
    $Count = [int]$plan.Count
}
if ($plan.ResetSession -and -not $PSBoundParameters.ContainsKey("ResetSession")) {
    $ResetSession = $true
}

$script:RunPlatform = if ($plan.RunPlatform) { $plan.RunPlatform } else { $Platform }

$steps = if ($PSBoundParameters.ContainsKey("Commands")) {
    foreach ($cmd in $Commands) { New-ChatStep -Message $cmd }
} elseif ($plan.Steps) {
    $plan.Steps
} else {
    foreach ($cmd in $Commands) { New-ChatStep -Message $cmd }
}

$totalSteps = $Count * $steps.Count

Write-Line ""
Write-Line "Phase 3 rapid test - Scenario=$Scenario Mode=$Mode DelayMs=$DelayMs Count=$Count" "Cyan"
Write-Line ("Steps: $totalSteps | Platform: $script:RunPlatform") "DarkGray"
Write-Line ""

if ($ResetSession -and -not ($steps | Where-Object { $_.Kind -eq "reset" })) {
    try {
        Invoke-RestMethod -Uri "$BaseUrl/api/session/reset" -Method POST `
            -ContentType "application/json" -Body "{}" -TimeoutSec 15 | Out-Null
        Write-Line "[reset] POST /api/session/reset OK" "Green"
    } catch {
        Write-Line "[reset] FAILED: $($_.Exception.Message)" "Red"
    }
    Start-Sleep -Milliseconds $DelayMs
}

$sw = [System.Diagnostics.Stopwatch]::StartNew()
$apiOk = 0
$apiFail = 0
$sbOk = 0
$sbFail = 0
$run = 0

for ($c = 1; $c -le $Count; $c++) {
    foreach ($step in $steps) {
        $run++
        $user = if ($RotateUsers) { "${User}${c}" } else { $User }

        Invoke-TestStep -Step $step -RunIndex $run -Username $user `
            -ApiOk ([ref]$apiOk) -ApiFail ([ref]$apiFail) -SbOk ([ref]$sbOk) -SbFail ([ref]$sbFail)

        if ($run -lt $totalSteps -and $step.Kind -ne "pause" -and $step.Kind -ne "section") {
            Start-Sleep -Milliseconds $DelayMs
        }
    }
}

$sw.Stop()
Write-Line ""
Write-Line ("Done in {0:N1}s - API ok=$apiOk fail=$apiFail | SB ok=$sbOk fail=$sbFail" -f $sw.Elapsed.TotalSeconds) "Cyan"
Write-Line ""
Write-Line "Tips:" "DarkGray"
Write-Line "  List presets:   .\phase3_rapid_test.ps1 -ListScenarios" "DarkGray"
Write-Line "  Fard + OBS:     .\phase3_rapid_test.ps1 -Scenario Fard" "DarkGray"
Write-Line "  Donations:      .\phase3_rapid_test.ps1 -Scenario Donations -Mode Api" "DarkGray"
Write-Line "  Spend gate:     .\phase3_rapid_test.ps1 -Scenario SpendGate" "DarkGray"
Write-Line "  Full suite:     .\phase3_rapid_test.ps1 -Scenario All  (auto Both mode)" "DarkGray"
Write-Line "  Spawn storm:    .\phase3_rapid_test.ps1 -Scenario SpawnStorm -ConcurrentUsers 25" "DarkGray"
Write-Line "  Full COMMANDS.md matrix + double-fire diagnose: .\test_all_commands.ps1" "DarkGray"
Write-Line ""

if ($apiFail -gt 0 -or $sbFail -gt 0) { exit 1 }
exit 0
