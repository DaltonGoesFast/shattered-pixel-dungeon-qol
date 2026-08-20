# Comprehensive COMMANDS.md / API matrix for POST /api/chat-command (+ donations).
# Also diagnoses double point charges and literal "%apiMessage%" chat replies.
#
# Prerequisites:
#   python server.py running in Lastest UI
#   Game optional - spends may fail without a live run; response shape still asserted
#   StreamerBot mode: HTTP server on 7474, R1 enabled, legacy Command actions DISABLED
#
# Usage (from PowerShell, or use test_all_commands.cmd from cmd/Explorer):
#   cd "Lastest UI"
#   .\test_all_commands.cmd -Mode Api -Section Free   # preferred if .ps1 opens Notepad
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\test_all_commands.ps1 -Mode Api -Section Free
#   .\test_all_commands.ps1                          # API full matrix (default; PowerShell only)
#   .\test_all_commands.ps1 -Mode Both               # API then R1 (watch for double charge)
#   .\test_all_commands.ps1 -Mode StreamerBot        # R1 only (live chat / OBS)
#   .\test_all_commands.ps1 -Section Free            # bestiary/heat/help/economy included
#   .\test_all_commands.ps1 -Section Spend           # one section
#   .\test_all_commands.ps1 -DiagnoseDoubleFire      # seed + !dew once; assert single cost
#   .\test_all_commands.ps1 -ListSections
#   .\test_all_commands.ps1 -SeedPoints 2000 -DelayMs 200

param(
    [ValidateSet("Api", "StreamerBot", "Both")]
    [string]$Mode = "Api",

    [ValidateSet(
        "All", "Free", "StreamInfo", "Query", "Spend", "ExtraSpend",
        "Meta", "Earn", "Donations", "Errors", "Aliases"
    )]
    [string]$Section = "All",

    [switch]$ListSections,
    [switch]$DiagnoseDoubleFire,
    [switch]$SkipDonations,
    [switch]$ResetSession,

    [string]$BaseUrl = "http://127.0.0.1:5000",
    [string]$StreamerBotUrl = "http://127.0.0.1:7474",
    [string]$ChatActionName = "R01 - Chat Router",

    [string]$User = "cmdtester",
    [string]$TransferTarget = "cmdtester_friend",
    [string]$Platform = "twitch",

    [int]$SeedPoints = 2500,
    [int]$SeedDonorPoints = 100,
    [int]$DelayMs = -1,
    [switch]$VerboseOutput
)

$ErrorActionPreference = "Continue"
$script:Passed = 0
$script:Failed = 0
$script:Warned = 0
$script:Skipped = 0
$script:RunPlatform = $Platform

if ($DelayMs -lt 0) {
    $DelayMs = if ($Mode -eq "Api") { 80 } else { 1500 }
}

# --- helpers ---------------------------------------------------------------

function Write-Line([string]$Text, [string]$Color = "Gray") {
    Write-Host $Text -ForegroundColor $Color
}

function Write-Result {
    param([string]$Name, [ValidateSet("PASS", "FAIL", "WARN", "SKIP")][string]$Status, [string]$Detail = "")
    switch ($Status) {
        "PASS" { $script:Passed++; Write-Line "[PASS] $Name" "Green" }
        "FAIL" { $script:Failed++; Write-Line "[FAIL] $Name - $Detail" "Red" }
        "WARN" { $script:Warned++; Write-Line "[WARN] $Name - $Detail" "Yellow" }
        "SKIP" { $script:Skipped++; Write-Line "[SKIP] $Name - $Detail" "DarkGray" }
    }
    if ($VerboseOutput -and $Detail -and $Status -eq "PASS") {
        Write-Line "       $Detail" "DarkGray"
    }
}

function Invoke-Json {
    param([string]$Method, [string]$Path, [object]$Body = $null)
    $params = @{
        Uri         = "$BaseUrl$Path"
        Method      = $Method
        TimeoutSec  = 25
        ErrorAction = "Stop"
    }
    if ($null -ne $Body) {
        $params.ContentType = "application/json"
        $params.Body = ($Body | ConvertTo-Json -Depth 8 -Compress)
    }
    return Invoke-RestMethod @params
}

function Test-ServerUp {
    try {
        Invoke-Json -Method GET -Path "/api/status" | Out-Null
        return $true
    } catch {
        Write-Line "Server not reachable at $BaseUrl - start: python server.py" "Red"
        return $false
    }
}

function Get-ViewerBalance([string]$Username) {
    try {
        $key = $Username.ToLowerInvariant()
        $all = Invoke-Json -Method GET -Path "/api/viewer-points"
        $row = $all.$key
        if ($null -eq $row) {
            return @{ Chat = 0; Donor = 0; Total = 0; Raw = $null }
        }
        # File model: points column is effective total; donationPts is donor wallet.
        $total = [int]$row.points
        $donor = [int]$row.donationPts
        $chat = [Math]::Max(0, $total - $donor)
        return @{ Chat = $chat; Donor = $donor; Total = $total; Raw = $row }
    } catch {
        return $null
    }
}

function Set-ViewerSeed {
    param([string[]]$Users, [int]$Points, [int]$Donor)
    $body = @{
        points      = $Points
        donationPts = $Donor
        users       = @($Users | ForEach-Object { $_.ToLowerInvariant() })
    }
    Invoke-Json -Method POST -Path "/api/viewer-points/bulk/set" -Body $body | Out-Null
}

function Invoke-ChatApi {
    param(
        [string]$Message,
        [string]$Username = $User,
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
            isSubscribed  = $IsSubscribed
            isMember      = $IsMember
            isBroadcaster = $IsBroadcaster
            bits          = 0
        }
    }
    return Invoke-Json -Method POST -Path "/api/chat-command" -Body $body
}

function Invoke-StreamerBotAction {
    param([string]$ActionName, [hashtable]$ActionArgs)
    $payload = @{
        action = @{ name = $ActionName }
        args   = $ActionArgs
    }
    $json = $payload | ConvertTo-Json -Depth 6 -Compress
    $response = Invoke-WebRequest -Uri "$StreamerBotUrl/DoAction" -Method POST `
        -ContentType "application/json" -Body $json -TimeoutSec 30 -UseBasicParsing
    if ($response.StatusCode -lt 200 -or $response.StatusCode -ge 300) {
        throw "DoAction HTTP $($response.StatusCode)"
    }
}

function Invoke-R1Chat {
    param(
        [string]$Message,
        [string]$Username = $User,
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
    Invoke-StreamerBotAction -ActionName $ChatActionName -ActionArgs $actionArgs
}

function Test-StreamerBotUp {
    try {
        $r = Invoke-RestMethod -Uri "$StreamerBotUrl/GetActions" -Method GET -TimeoutSec 5
        $names = @($r.actions | ForEach-Object { $_.name })
        if ($names -contains $ChatActionName) { return $true }
        # Live export uses "R01 - Chat Router"; older docs say "R1 - Chat Router"
        $alt = $names | Where-Object { $_ -match '^R0*1\b.*Chat' } | Select-Object -First 1
        if (-not $alt) {
            $alt = $names | Where-Object { $_ -match 'Chat\s*Router' } | Select-Object -First 1
        }
        if ($alt) {
            $script:ChatActionName = $alt
            Write-Line "Using Streamer.bot action: $alt" "DarkGray"
            return $true
        }
        Write-Line "R1 chat action not found. Available: $($names -join ', ')" "Yellow"
        return $false
    } catch {
        Write-Line "Streamer.bot HTTP not reachable at $StreamerBotUrl" "Red"
        return $false
    }
}

function Assert-NoLiteralApiMessage([string]$Message, [string]$CaseName) {
    if ($null -eq $Message) { return }
    if ($Message -match '%apiMessage%|%apiOk%|%apiPts%|%spawnResult%') {
        Write-Result $CaseName "FAIL" "Response contains unsubstituted Streamer.bot token: $Message"
        return $false
    }
    return $true
}

function Assert-ChatShape {
    param(
        [string]$CaseName,
        $Response,
        [bool]$ExpectMessage = $true,
        [bool]$AllowFail = $true
    )
    if ($null -eq $Response -or $null -eq $Response.ok) {
        Write-Result $CaseName "FAIL" "Missing ok field"
        return $false
    }
    $msg = $Response.message
    if (-not (Assert-NoLiteralApiMessage -Message "$msg" -CaseName $CaseName)) {
        return $false
    }
    if ($ExpectMessage -and [string]::IsNullOrWhiteSpace("$msg") -and [bool]$Response.ok) {
        # Some ok paths are intentionally silent (kesha/mimic); caller sets ExpectMessage=$false.
        Write-Result $CaseName "FAIL" "ok=true but message empty (chat would show blank or %apiMessage% if SB never set arg)"
        return $false
    }
    $status = if ($Response.ok -or $AllowFail) { "PASS" } else { "FAIL" }
    $detail = "ok=$($Response.ok)"
    if ($msg) { $detail += " | $msg" }
    if ($null -ne $Response.pts) { $detail += " | pts=$($Response.pts)" }
    if ($Response.presentation) { $detail += " | presentation" }
    Write-Result $CaseName $status $detail
    return ($status -eq "PASS")
}

# --- command catalog (COMMANDS.md) ----------------------------------------

function Get-SectionCatalog {
    return @(
        @{ Name = "Earn";       Desc = "Plain chat message earn path" }
        @{ Name = "Free";       Desc = "!fard !summon !bestiary !sprint !heat !summonhall !mysummons !topsummoner !economy !help" }
        @{ Name = "StreamInfo"; Desc = "!kesha !mimic !tooth !seed !challenge (API; R1 skips)" }
        @{ Name = "Query";      Desc = "!points !bank !toppoints !leaderboard !givepoints" }
        @{ Name = "Spend";      Desc = "Main spend table from COMMANDS.md" }
        @{ Name = "ExtraSpend"; Desc = "!heal !cleanse !dew !corruptally !hex !degrade !sabotage !plant" }
        @{ Name = "Meta";       Desc = "!doublepoints (broadcaster)" }
        @{ Name = "Aliases";    Desc = "!2x !summonlevel !hot !reminder !commands (+ Query !leaderboard)" }
        @{ Name = "Errors";     Desc = "Usage errors + unknown command" }
        @{ Name = "Donations";  Desc = "cheer / superchat / gift-membership HTTP" }
        @{ Name = "All";        Desc = "Every section above" }
    )
}

function New-ChatCase {
    param(
        [string]$Section,
        [string]$Message,
        [string]$Label,
        [hashtable]$Opts = @{}
    )
    return @{
        Section       = $Section
        Message       = $Message
        Label         = $Label
        ExpectMessage = $(if ($null -ne $Opts.ExpectMessage) { [bool]$Opts.ExpectMessage } else { $true })
        SilentOk      = $(if ($null -ne $Opts.SilentOk) { [bool]$Opts.SilentOk } else { $false })
        TrackBalance  = $(if ($null -ne $Opts.TrackBalance) { [bool]$Opts.TrackBalance } else { $false })
        IsBroadcaster = $(if ($null -ne $Opts.IsBroadcaster) { [bool]$Opts.IsBroadcaster } else { $true })
        IsSubscribed  = $(if ($null -ne $Opts.IsSubscribed) { [bool]$Opts.IsSubscribed } else { $true })
        Username      = $(if ($Opts.Username) { $Opts.Username } else { $User })
        Kind          = $(if ($Opts.Kind) { $Opts.Kind } else { "chat" })
        Endpoint      = $Opts.Endpoint
        Body          = $Opts.Body
        R1Skip        = $(if ($null -ne $Opts.R1Skip) { [bool]$Opts.R1Skip } else { $false })
    }
}

function Get-CommandCases {
    param([string]$SectionName)

    $all = [System.Collections.Generic.List[object]]::new()
    $want = {
        param($sec)
        return ($SectionName -eq "All" -or $SectionName -eq $sec)
    }

    if (& $want "Earn") {
        $all.Add((New-ChatCase "Earn" "hello from test_all_commands" "earn.message" @{ SilentOk = $true; ExpectMessage = $false }))
    }

    if (& $want "Free") {
        # Full free surface from COMMANDS.md (aliases covered in Aliases section).
        $all.Add((New-ChatCase "Free" "!fard" "!fard"))
        $all.Add((New-ChatCase "Free" "!fard" "!fard (repeat / already used)"))
        # Summon is 60s/user silent cooldown — fresh users so a prior Free run cannot poison All.
        $summonA = "{0}_s{1}" -f $User, (Get-Random -Maximum 999999)
        $summonB = "{0}_s{1}" -f $User, (Get-Random -Maximum 999999)
        $all.Add((New-ChatCase "Free" "!summon" "!summon (random unlocked)" @{ Username = $summonA }))
        $all.Add((New-ChatCase "Free" "!summon rat" "!summon rat" @{ Username = $summonB }))
        $all.Add((New-ChatCase "Free" "!summon" "!summon (cooldown silent)" @{ Username = $summonA; SilentOk = $true }))
        $all.Add((New-ChatCase "Free" "!bestiary" "!bestiary"))
        $all.Add((New-ChatCase "Free" "!topsummoner" "!topsummoner"))
        $all.Add((New-ChatCase "Free" "!sprint" "!sprint"))
        $all.Add((New-ChatCase "Free" "!heat" "!heat"))
        $all.Add((New-ChatCase "Free" "!summonhall" "!summonhall"))
        $all.Add((New-ChatCase "Free" "!mysummons" "!mysummons"))
        $all.Add((New-ChatCase "Free" "!economy" "!economy"))
        $all.Add((New-ChatCase "Free" "!help" "!help"))
        $all.Add((New-ChatCase "Free" "!help spawn" "!help spawn"))
    }

    if (& $want "StreamInfo") {
        # Separate SB Command actions; API still answers; R1 BuildChatCommandBody returns false
        $all.Add((New-ChatCase "StreamInfo" "!kesha" "!kesha" @{ SilentOk = $true; ExpectMessage = $false; R1Skip = $true }))
        $all.Add((New-ChatCase "StreamInfo" "!mimic" "!mimic" @{ SilentOk = $true; ExpectMessage = $false; R1Skip = $true }))
        $all.Add((New-ChatCase "StreamInfo" "!tooth" "!tooth" @{ SilentOk = $true; ExpectMessage = $false; R1Skip = $true }))
        $all.Add((New-ChatCase "StreamInfo" "!seed" "!seed" @{ R1Skip = $true }))
        $all.Add((New-ChatCase "StreamInfo" "!challenge" "!challenge" @{ R1Skip = $true }))
        $all.Add((New-ChatCase "StreamInfo" "!challenges" "!challenges" @{ R1Skip = $true }))
    }

    if (& $want "Query") {
        $all.Add((New-ChatCase "Query" "!points" "!points"))
        $all.Add((New-ChatCase "Query" "!bank" "!bank (all shorthand)" @{ TrackBalance = $true }))
        $all.Add((New-ChatCase "Query" "!bank 50" "!bank 50" @{ TrackBalance = $true }))
        $all.Add((New-ChatCase "Query" "!bank all" "!bank all" @{ TrackBalance = $true }))
        $all.Add((New-ChatCase "Query" "!toppoints" "!toppoints"))
        $all.Add((New-ChatCase "Query" "!leaderboard" "!leaderboard"))
        $all.Add((New-ChatCase "Query" "!givepoints 10 $TransferTarget" "!givepoints" @{ TrackBalance = $true }))
    }

    if (& $want "Spend") {
        foreach ($s in @(
                "!spawn rat", "!champion rat", "!gold 5", "!curse", "!gas", "!scroll",
                "!row", "!trap", "!bomb", "!transmute", "!bee", "!ward", "!buff", "!debuff", "!wand"
            )) {
            $all.Add((New-ChatCase "Spend" $s $s @{ TrackBalance = $true }))
        }
    }

    if (& $want "ExtraSpend") {
        foreach ($s in @("!heal", "!cleanse", "!dew", "!corruptally", "!hex", "!degrade", "!sabotage", "!plant")) {
            $all.Add((New-ChatCase "ExtraSpend" $s $s @{ TrackBalance = $true }))
        }
    }

    if (& $want "Meta") {
        $all.Add((New-ChatCase "Meta" "!doublepoints 1" "!doublepoints 1 (broadcaster)" @{ IsBroadcaster = $true }))
        # Different minutes so 2s spend-dedupe does not collapse this into the prior case.
        $all.Add((New-ChatCase "Meta" "!doublepoints 2" "!doublepoints (non-broadcaster gate)" @{ IsBroadcaster = $false }))
    }

    if (& $want "Aliases") {
        $all.Add((New-ChatCase "Aliases" "!2x 1" "!2x alias" @{ IsBroadcaster = $true }))
        $all.Add((New-ChatCase "Aliases" "!summonlevel" "!summonlevel alias (!bestiary)"))
        $all.Add((New-ChatCase "Aliases" "!hot" "!hot alias (!heat)"))
        $all.Add((New-ChatCase "Aliases" "!reminder" "!reminder alias (!economy)"))
        $all.Add((New-ChatCase "Aliases" "!commands" "!commands alias (!help)"))
    }

    if (& $want "Errors") {
        $all.Add((New-ChatCase "Errors" "!spawn" "!spawn (missing arg)"))
        $all.Add((New-ChatCase "Errors" "!gold" "!gold (missing arg)"))
        $all.Add((New-ChatCase "Errors" "!givepoints" "!givepoints (usage)"))
        $all.Add((New-ChatCase "Errors" "!doublepoints" "!doublepoints (missing minutes)"))
        $all.Add((New-ChatCase "Errors" "!bank -1" "!bank (bad amount)"))
        $all.Add((New-ChatCase "Errors" "!help nosuchcmd" "!help (unknown topic)"))
        $all.Add((New-ChatCase "Errors" "!notacommand" "!notacommand"))
    }

    if (-not $SkipDonations -and (& $want "Donations")) {
        $all.Add(@{
                Section = "Donations"; Kind = "donation"; Label = "POST /api/donation/cheer"
                Endpoint = "/api/donation/cheer"
                Body = @{ bits = 25; username = $User; isSubscribed = $true; userIsSponsor = $false }
            })
        $all.Add(@{
                Section = "Donations"; Kind = "donation"; Label = "POST /api/donation/superchat"
                Endpoint = "/api/donation/superchat"
                Body = @{
                    microAmount = 1000000; currencyCode = "USD"; username = $User
                    isSubscribed = $false; userIsSponsor = $false
                }
            })
        $all.Add(@{
                Section = "Donations"; Kind = "donation"; Label = "POST /api/donation/gift-membership"
                Endpoint = "/api/donation/gift-membership"
                Body = @{
                    username = $User; tier = "1000"; isSubscribed = $false
                    userIsSponsor = $false; platform = "twitch"
                }
            })
    }

    return @($all)
}

# --- diagnostics ----------------------------------------------------------

function Show-DoubleFireHelp {
    Write-Line ""
    Write-Line '=== Double fire / double charge ===' "Yellow"
    Write-Line 'Most common cause: legacy Streamer.bot Command entries still enabled' "Gray"
    Write-Line 'WHILE R1 Message Received is also enabled. One chat line then runs:' "Gray"
    Write-Line '  [1] R1 -> POST /api/chat-command  (charges once)' "Gray"
    Write-Line '  [2] old Command action -> points_command.py or second HTTP  (charges again)' "Gray"
    Write-Line ""
    Write-Line 'Fix (docs/phase-0-action-inventory.md): DISABLE/DELETE Command registry for' "Cyan"
    Write-Line '!spawn !champion !gold !curse !gas !scroll !row !trap !bomb !transmute' "Cyan"
    Write-Line '!bee !ward !buff !debuff !wand !points !toppoints !doublepoints !heal' "Cyan"
    Write-Line '!cleanse !dew !plant !corruptally !hex !degrade !sabotage !fard !summon' "Cyan"
    Write-Line '!bestiary !sprint !heat !summonhall !mysummons !topsummoner !economy !help' "Cyan"
    Write-Line '!givepoints !bank - keep only R1 + stream-info Commands.' "Cyan"
    Write-Line ""
    Write-Line 'Also check: two Message Received triggers on R1 (Twitch+YouTube both matching),' "Gray"
    Write-Line 'or -Mode Both in this script (API + R1 = intentional double hit on server).' "Gray"
}

function Show-ApiMessageHelp {
    Write-Line ""
    Write-Line '=== Literal %apiMessage% in live chat ===' "Yellow"
    Write-Line 'Streamer.bot prints the token when the argument was never set in THAT action.' "Gray"
    Write-Line 'Typical causes:' "Gray"
    Write-Line '  [1] Old Command action still sends Twitch/YT Message %apiMessage% but has no' "Gray"
    Write-Line '      ParseChatResponse.cs (R1-only variable). Disable those Commands.' "Gray"
    Write-Line '  [2] R1 order wrong - chat send BEFORE ParseChatResponse (1c).' "Gray"
    Write-Line '      Correct: BuildBody -> curl -> ParseChatResponse -> If apiMessage -> chat.' "Gray"
    Write-Line '  [3] curl Wait for exit = 0 so %output0% empty; parse may set a fallback message,' "Gray"
    Write-Line '      but if Parse sub-action is missing you get the literal token.' "Gray"
    Write-Line '  [4] Message uses %apiMessage% but C# sets a different name (typo).' "Gray"
    Write-Line ""
    Write-Line 'R1 step 1e must be: If %apiMessage% Is Null or Empty -> True: empty; False: send.' "Cyan"
}

function Invoke-DiagnoseDoubleFire {
    Write-Line ""
    Write-Line "DiagnoseDoubleFire - seed points, run !dew once via API, assert single charge" "Cyan"
    Write-Line ""

    if (-not (Test-ServerUp)) { exit 1 }

    $probeUser = "${User}_dewprobe"
    Set-ViewerSeed -Users @($probeUser) -Points 500 -Donor 0
    Start-Sleep -Milliseconds 100

    $before = Get-ViewerBalance $probeUser
    if (-not $before) {
        Write-Result "balance read" "FAIL" "Could not read /api/viewer-points/balance"
        Show-DoubleFireHelp
        Show-ApiMessageHelp
        exit 1
    }
    Write-Line ("Before: chat={0} donor={1} total={2}" -f $before.Chat, $before.Donor, $before.Total) "DarkGray"

    $cfg = Invoke-Json -Method GET -Path "/api/points-config"
    $baseCost = 5
    if ($cfg.cost_per_dew) { $baseCost = [int]$cfg.cost_per_dew }

    # Death-cost inflation: harmful commands get round(cost * 1.5^deaths). Dew is not exempt.
    $deaths = 0
    $deathFile = Join-Path $PSScriptRoot "death_cost_state.json"
    if (Test-Path $deathFile) {
        try {
            $deaths = [int]((Get-Content $deathFile -Raw | ConvertFrom-Json).deaths)
        } catch { $deaths = 0 }
    }
    $expected = $baseCost
    if ($deaths -gt 0) {
        $expected = [Math]::Max(1, [int][Math]::Round($baseCost * [Math]::Pow(1.5, $deaths)))
    }
    Write-Line ("Base dew cost={0}; death_cost deaths={1}; expected charge={2}" -f $baseCost, $deaths, $expected) "DarkGray"

    $r = Invoke-ChatApi -Message "!dew" -Username $probeUser
    Assert-ChatShape -CaseName "API !dew" -Response $r -ExpectMessage $true | Out-Null
    Start-Sleep -Milliseconds 200

    $after = Get-ViewerBalance $probeUser
    $delta = $before.Total - $after.Total
    Write-Line ("After:  chat={0} donor={1} total={2}  delta={3} (expect {4} or 0 if rejected)" -f `
        $after.Chat, $after.Donor, $after.Total, $delta, $expected) "DarkGray"

    if ($r.ok) {
        if ($delta -eq $expected) {
            Write-Result "single charge (!dew)" "PASS" "delta=$delta"
        } elseif ($delta -eq (2 * $expected)) {
            Write-Result "single charge (!dew)" "FAIL" "DOUBLE CHARGE: delta=$delta (2x $expected). Server path alone should not do this - check concurrent callers / Mode Both / double HTTP."
        } elseif ($delta -eq 0) {
            Write-Result "single charge (!dew)" "WARN" "ok=true but balance unchanged (refund?); check message: $($r.message)"
        } else {
            Write-Result "single charge (!dew)" "WARN" "delta=$delta expected=$expected (zone/config/refund). message=$($r.message)"
        }
    } else {
        Write-Result "single charge (!dew)" "PASS" "ok=false so no spend expected; msg=$($r.message)"
    }

    # Second identical API call immediately - should not double-apply from a single request;
    # this is a separate spend if ok.
    if ($Mode -in @("StreamerBot", "Both")) {
        if (-not (Test-StreamerBotUp)) { exit 1 }
        Set-ViewerSeed -Users @($probeUser) -Points 500 -Donor 0
        Start-Sleep -Milliseconds 150
        $b2 = Get-ViewerBalance $probeUser
        Write-Line "Firing R1 once for !dew (watch Action History - should be ONE R1 run)..." "Yellow"
        Invoke-R1Chat -Message "!dew" -Username $probeUser
        Start-Sleep -Milliseconds ([Math]::Max($DelayMs, 2000))
        $a2 = Get-ViewerBalance $probeUser
        $d2 = $b2.Total - $a2.Total
        Write-Line ("R1 delta={0} (expect 0, {1}, or 2x if legacy Command ALSO fired)" -f $d2, $expected) "DarkGray"
        if ($d2 -eq (2 * $expected)) {
            Write-Result "R1 single charge" "FAIL" "delta=$d2 = 2x cost - legacy Command + R1 almost certainly both enabled"
        } elseif ($d2 -eq $expected) {
            Write-Result "R1 single charge" "PASS" "delta=$d2"
        } elseif ($d2 -eq 0) {
            Write-Result "R1 single charge" "WARN" "no balance change (spend failed or R1 skipped)"
        } else {
            Write-Result "R1 single charge" "WARN" "delta=$d2"
        }
    }

    Write-Line ""
    Write-Line ("Results: {0} passed, {1} failed, {2} warned, {3} skipped" -f `
        $script:Passed, $script:Failed, $script:Warned, $script:Skipped) "Cyan"

    if ($script:Failed -gt 0) {
        Show-DoubleFireHelp
        Show-ApiMessageHelp
        exit 1
    }

    Write-Line ""
    Write-Line "DoAction path is clean (single charge). Live chat can still double-fire if" "Green"
    Write-Line "legacy Command entries exist - DoAction bypasses Command triggers." "Green"
    Write-Line "Next: .\test_all_commands.ps1 -Section Query -Mode StreamerBot" "Cyan"
    Write-Line "Watch live chat for real replies (not literal %apiMessage%)." "Cyan"
    Write-Line "Or type !dew / !points in Twitch/YouTube yourself and check Action History:" "DarkGray"
    Write-Line "  expect ONE R01 run per message; no second spend/Command action." "DarkGray"
    exit 0
}

# --- run cases ------------------------------------------------------------

function Invoke-Case {
    param([hashtable]$Case)

    if ($Case.Kind -eq "donation") {
        if ($Mode -eq "StreamerBot") {
            Write-Result $Case.Label "SKIP" "donations are API routes (use -Mode Api or Both)"
            return
        }
        try {
            $r = Invoke-Json -Method POST -Path $Case.Endpoint -Body $Case.Body
            if ($null -eq $r.ok) {
                Write-Result $Case.Label "FAIL" "Missing ok"
            } else {
                Write-Result $Case.Label "PASS" "ok=$($r.ok) pointsAdded=$($r.pointsAdded)"
            }
        } catch {
            Write-Result $Case.Label "FAIL" $_.Exception.Message
        }
        return
    }

    $msg = $Case.Message
    $uname = $Case.Username
    $label = $Case.Label
    $expectMsg = -not $Case.SilentOk
    $before = $null
    if ($Case.TrackBalance -and $Mode -in @("Api", "Both")) {
        $before = Get-ViewerBalance $uname
    }

    if ($Mode -in @("Api", "Both")) {
        try {
            $r = Invoke-ChatApi -Message $msg -Username $uname `
                -IsBroadcaster ([bool]$Case.IsBroadcaster) -IsSubscribed ([bool]$Case.IsSubscribed)
            Assert-ChatShape -CaseName ("API {0}" -f $label) -Response $r -ExpectMessage $expectMsg | Out-Null

            if ($Case.TrackBalance -and $before -and [bool]$r.ok) {
                Start-Sleep -Milliseconds 50
                $after = Get-ViewerBalance $uname
                if ($after) {
                    $delta = $before.Total - $after.Total
                    if ($delta -lt 0) {
                        Write-Result ("balance {0}" -f $label) "WARN" "total increased by $(-1 * $delta) (transfer/bank/earn?)"
                    } elseif ($VerboseOutput) {
                        Write-Line ("       balance delta={0} (before={1} after={2})" -f $delta, $before.Total, $after.Total) "DarkGray"
                    }
                }
            }
        } catch {
            Write-Result ("API {0}" -f $label) "FAIL" $_.Exception.Message
        }
    }

    if ($Mode -in @("StreamerBot", "Both")) {
        if ($Case.R1Skip) {
            Write-Result ("SB {0}" -f $label) "SKIP" "R1 BuildChatCommandBody returns false (native Command)"
            return
        }
        try {
            Invoke-R1Chat -Message $msg -Username $uname `
                -IsBroadcaster ([bool]$Case.IsBroadcaster) -IsSubscribed ([bool]$Case.IsSubscribed)
            Write-Result ("SB {0}" -f $label) "PASS" "queued R1 (check live chat is NOT literal %apiMessage%)"
        } catch {
            Write-Result ("SB {0}" -f $label) "FAIL" $_.Exception.Message
        }
    }
}

# --- main -----------------------------------------------------------------

if ($ListSections) {
    Write-Line "Sections (from COMMANDS.md + donation routes):" "Cyan"
    foreach ($s in Get-SectionCatalog) {
        Write-Line ("  {0,-12} {1}" -f $s.Name, $s.Desc)
    }
    Write-Line ""
    Write-Line "Diagnose: .\test_all_commands.ps1 -DiagnoseDoubleFire" "DarkGray"
    Write-Line "Full API:  .\test_all_commands.ps1 -Mode Api -Section All" "DarkGray"
    Write-Line "R1 live:   .\test_all_commands.ps1 -Mode StreamerBot -Section Query" "DarkGray"
    exit 0
}

if ($DiagnoseDoubleFire) {
    Invoke-DiagnoseDoubleFire
}

if (-not (Test-ServerUp)) { exit 1 }

if ($Mode -in @("StreamerBot", "Both")) {
    if (-not (Test-StreamerBotUp)) { exit 1 }
}

if ($Mode -eq "Both") {
    Write-Line "WARNING: -Mode Both hits the server twice per chat case (API + R1)." "Yellow"
    Write-Line "Use for wiring checks only - balance deltas will look like double charges." "Yellow"
    Write-Line "For charge tests use -Mode Api or -DiagnoseDoubleFire." "Yellow"
    Write-Line ""
}

if ($ResetSession) {
    try {
        Invoke-Json -Method POST -Path "/api/session/reset" -Body @{} | Out-Null
        Write-Line "[reset] POST /api/session/reset OK" "Green"
    } catch {
        Write-Line "[reset] FAILED: $($_.Exception.Message)" "Red"
    }
}

try {
    Set-ViewerSeed -Users @($User, $TransferTarget) -Points $SeedPoints -Donor $SeedDonorPoints
    Write-Line ("[seed] {0} chat + {1} donor for {2}, {3}" -f $SeedPoints, $SeedDonorPoints, $User, $TransferTarget) "Green"
} catch {
    Write-Line "[seed] FAILED: $($_.Exception.Message)" "Red"
}

$cases = @(Get-CommandCases -SectionName $Section)
Write-Line ""
Write-Line "test_all_commands - Section=$Section Mode=$Mode Cases=$($cases.Count) DelayMs=$DelayMs" "Cyan"
Write-Line "Source: COMMANDS.md + docs/stream-info-commands.md + donation routes" "DarkGray"
Write-Line ""

$i = 0
foreach ($case in $cases) {
    $i++
    if ($case.Section -and ($i -eq 1 -or $cases[$i - 2].Section -ne $case.Section)) {
        Write-Line ""
        Write-Line ("--- {0} ---" -f $case.Section) "Yellow"
    }
    Invoke-Case -Case $case
    if ($i -lt $cases.Count) {
        Start-Sleep -Milliseconds $DelayMs
    }
}

Write-Line ""
Write-Line ("Results: {0} passed, {1} failed, {2} warned, {3} skipped" -f `
    $script:Passed, $script:Failed, $script:Warned, $script:Skipped) "Cyan"

if ($script:Failed -gt 0) {
    Show-DoubleFireHelp
    Show-ApiMessageHelp
} elseif ($Mode -in @("StreamerBot", "Both")) {
    Write-Line "If live chat showed literal %apiMessage%, see R1 parse/order tips:" "DarkGray"
    Write-Line "  .\test_all_commands.ps1 -DiagnoseDoubleFire   (prints tips only on FAIL)" "DarkGray"
}

Write-Line ""
Write-Line "Quick re-runs:" "DarkGray"
Write-Line "  .\test_all_commands.ps1 -DiagnoseDoubleFire" "DarkGray"
Write-Line "  .\test_all_commands.ps1 -Section Spend -Mode Api" "DarkGray"
Write-Line "  .\test_all_commands.ps1 -Section Query -Mode StreamerBot" "DarkGray"
Write-Line ""

if ($script:Failed -gt 0) { exit 1 }
exit 0
