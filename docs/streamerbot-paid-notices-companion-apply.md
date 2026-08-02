# Paid notices → SPD Companion (Streamer.bot apply guide)

Drive the Godot companion overlay for **YouTube Super Chat**, **YouTube gifted membership**, and **Twitch sub / resub** via UDP. Replaces OBS GDI / group show-hide for those events. **Speaker.bot** stays as its own sub-action.

**Companion:** Settings → **Paid notices**  
**UDP:** `127.0.0.1` port **`5100`** (or your Settings → Connection → Streamer.bot UDP port)  
**Related:** [`spd-comp3/AGENTS.md`](../spd-comp3/AGENTS.md) (payload reference)

---

## Not supported (Streamer.bot)

| Event | Why |
|-------|-----|
| **Twitch Highlight My Message** | Streamer.bot has **no trigger/action** for this reward. Do **not** create an action for it. The companion still accepts `"ui":"highlight"` if a trigger appears later; leave that toggle off in Settings for now. |

---

## Prerequisites

| Check | |
|-------|--|
| Companion | SPD Companion 3 running (editor or export) |
| UDP | Settings → Connection → Streamer.bot UDP port = **5100** (default) |
| Paid notices | Settings → **Paid notices** → enabled; types you use checked |
| OBS (optional) | If you sync Live/Pause, set **Show on Live** / **Show on Pause** as you want |

**Remove from old Super Chat (OBS) action:** source visibility (show/hide) and GDI text updates. **Keep:** Speaker.bot Speak (and any points/donation HTTP you already use).

---

## Shared pattern: Execute C# → companion UDP

Streamer.bot’s C# sandbox does **not** include `System.Net` / `System.Diagnostics` unless you manually add References. Use the built-in send API instead:

**Core → Softwares / Misc → Execute C# Code** (or **Execute C#**)  
Paste: [`Lastest UI/streamerbot/phase2/SendCompanionPaidNoticeUdp.cs`](../Lastest%20UI/streamerbot/phase2/SendCompanionPaidNoticeUdp.cs)

That script JSON-escapes fields and calls **`CPH.BroadcastUdp(port, json)`** (same engine as Core → Network → UDP Broadcast). It also writes `Lastest UI/companion_paid_notice_udp.json` and sets `%companionPaidNoticeJson%`.

| Optional Set Argument (before the C#) | Meaning | Default |
|--------------------------------------|---------|---------|
| `companionUi` | `superchat` / `gifted_membership` / `sub` | `superchat` |
| `companionTtlSec` | Hold time | `6` |
| `companionUdpPort` | Companion UDP port | `5100` |

It also reads trigger args automatically (`userName`, `message`, `amount`, `gifterUserName`, `totalGifts`, `tier`, `cumulativeMonths`, …).

**Speaker.bot → Speak** / **Speak (UDP)** = TTS only — not for the companion JSON.

### Speaker.bot Speak text (do not use `%message%` on C02/C03)

Gift / New Sponsor / many Gift Sub events **do not set** `%message%`. If Speak contains `%message%`, you will hear the literal words “percent message percent”.

Edit each action’s **Speaker.bot → Speak** (or Speak UDP) **Message** field:

| Action | Suggested Speak text |
|--------|----------------------|
| **C01 - Superchat** | `%userName% sent a super chat. %message%` *(message is OK here)* |
| **C02 - New Sub/Member** | `%userName% just subscribed. Thank you!` |
| **C03 - Gifted** | `%userName% gifted %totalGifts%! Thank you!` |

**C02 tip (YouTube vs Twitch wording):** one shared Speak can’t say both “subscribed” and “became a member”. Either keep a neutral line (`Thank you %userName%!`), or add **If / Else** on `%eventSource%` containing `youtube` vs `twitch` with two Speak lines:

- YouTube: `%userName% became a member. Thank you!`
- Twitch: `%userName% subscribed. Thank you!`

**C03 tip:** if `%totalGifts%` is empty on Twitch, try `%gifts%` or hardcode `a gift` (`%userName% gifted a sub. Thank you!`). Prefer `%gifterUserName%` if `%userName%` is blank on YouTube gifts.

### If the companion never shows the notice

Add **Run a Program** *after* the C# (unicast to localhost):

| Field | Value |
|-------|--------|
| Program / Path | `C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe` |
| Working directory | *(blank)* |
| Arguments | `-NoProfile -ExecutionPolicy Bypass -Command "$b=[IO.File]::ReadAllBytes('C:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\companion_paid_notice_udp.json');$u=New-Object Net.Sockets.UdpClient;$null=$u.Send($b,$b.Length,'127.0.0.1',5100);$u.Close()"` |

(Adjust the JSON path if your repo lives elsewhere.)

---

## Companion Commands group (actions)

Create a Streamer.bot group **Companion Commands** with these three actions:

| Action | `companionUi` | Typical triggers |
|--------|---------------|------------------|
| **C01 - Superchat** | `superchat` | YouTube → Chat → **Super Chat** |
| **C02 - New Sub/Member** | `sub` | Twitch → Subscriptions → **Subscription** (+ **Resubscription**); YouTube → **New Sponsor**. C# rewrites YouTube to `ui=membership` (“became a member”); Twitch stays `sub` (“subscribed”). |
| **C03 - Gifted** | `gifted_membership` | YouTube → **Membership Gift**; Twitch → **Gift Subscription**. C# keeps YT as memberships; Twitch → `ui=gifted_subs` (“subscriptions”). |

Shared sub-action pattern (all three):

1. **Set Argument** — `%companionUi%` = value from the table  
2. **Execute C#** — paste `SendCompanionPaidNoticeUdp.cs` (compile once per action, or share the same paste)  
3. **Speaker.bot → Speak** — TTS (optional on C02/C03)  
4. *(Optional)* Existing points / donation HTTP for that event  

Do **not** add a 6s delay + hide for the companion; `ttl_sec` / `companionTtlSec` handles lifetime. Remove any standalone **UDP Broadcast** sub-action meant for the companion overlay.

### C01 - Superchat

**Trigger:** YouTube → Chat → **Super Chat** · Criteria: Any  

1. Set `%companionUi%` = `superchat` *(optional; C# defaults to this)*  
2. Execute C# · 3. Speaker.bot Speak · 4. *(Optional)* R5 / superchat points HTTP  

### C02 - New Sub/Member

**Triggers (same action):** Twitch → **Subscription** / **Resubscription**; YouTube → General → **New Sponsor**.

1. Set `%companionUi%` = `sub` *(keep this for both platforms)*  
2. Execute C# · 3. Speaker.bot Speak *(optional)*  

C# reads `%eventSource%` / `%platform%` and sends `ui=membership` for YouTube (companion: “became a member”) or `ui=sub` for Twitch (“subscribed”). Also `%userName%`, `%message%`, `%cumulativeMonths%` / `%months%`, `%tier%`.

### C03 - Gifted

**Triggers (same action):** YouTube → Membership → **Membership Gift**; Twitch → Subscriptions → **Gift Subscription**.

1. Set `%companionUi%` = `gifted_membership` *(keep this for both platforms)*  
2. Execute C# · 3. Speaker.bot Speak *(optional)* · 4. *(Optional)* gift points HTTP  

C# uses `%eventSource%` so YouTube stays “gifted membership(s)” and Twitch becomes “gifted subscription(s)”. Also `%gifterUserName%`, `%totalGifts%` / `%count%`, `%tier%`.

---

## Migrating your existing Super Chat OBS action

Your current flow (groups visible → GDI text → Speaker.bot → delay 6s → hide) becomes **C01 - Superchat**:

| Old sub-action | New |
|----------------|-----|
| OBS show `… SUPERCHAT` groups | **Delete** |
| OBS GDI text updates | **Delete** → **Execute C#** (`SendCompanionPaidNoticeUdp.cs`) |
| Speaker.bot Speak | **Keep** |
| Delay 6000 ms | **Delete** (companion `ttl_sec`) |
| OBS hide groups | **Delete** |
| UDP Broadcast (if added) | **Delete** if it was only for the companion overlay |

Resulting **C01** flow: **Set Argument `companionUi`** → **Execute C#** → **Speaker.bot Speak** (+ optional points HTTP).

---

## Companion settings (quick)

Settings → **Paid notices**:

- **Zone X/Y/W/H** — place the toast where the old OBS Super Chat HUD sat  
- **Chrome style / scale**, padding, line separation, text align  
- **Banner / title / body** font sizes + colors (even sizes like 16/24/32 look sharpest)  
- Leave **Pop-in scale animation** off unless you want a soft zoom (it softens pixel text)  
- Event toggles: Super Chat, gifted membership / gift subs, sub / new member  
- **Twitch Highlight My Message** = leave **off**  
- Live / Pause checkboxes if you use OBS scene sync  

---

## Smoke test (no Streamer.bot)

With the companion running:

```powershell
$udp = New-Object System.Net.Sockets.UdpClient
function Send-Notice([string]$json) {
  $bytes = [Text.Encoding]::UTF8.GetBytes($json)
  $udp.Send($bytes, $bytes.Length, "127.0.0.1", 5100) | Out-Null
}
Send-Notice '{"ui":"superchat","username":"TestUser","message":"Hello from UDP","amount":"$5.00","ttl_sec":6}'
Send-Notice '{"ui":"gifted_membership","username":"Gifter","count":"5","ttl_sec":6}'
Send-Notice '{"ui":"sub","username":"SubUser","message":"Love the stream","months":"3","tier":"Tier 1","ttl_sec":6}'
Send-Notice '{"ui":"membership","username":"YtMember","ttl_sec":6}'
$udp.Close()
```

Each should animate the paid-notice panel and auto-hide.

---

## Troubleshooting

| Symptom | Check |
|---------|--------|
| Nothing appears | Companion running; Paid notices enabled; UDP port matches; JSON has `"ui":"…"`. |
| Wrong / empty name | Action History → fix `%userName%` / `%gifterUserName%`. |
| Message missing | Confirm `%message%` is set for that trigger; body can be empty for gifts. |
| Shows on wrong scene | Paid notices → Show on Live / Pause. |
| Still using OBS HUD | Remove visibility/GDI steps so you are not double-displaying. |

---

## Payload cheat sheet

| `ui` | Trigger | Useful fields |
|------|---------|----------------|
| `superchat` | YouTube → Super Chat | `username`, `message`, `amount`, `ttl_sec` |
| `membership` | YouTube → New Sponsor (C02) | `username`, `tier`, `ttl_sec` — title: “became a member” |
| `gifted_membership` | YouTube → Membership Gift | `username`, `count`, `tier`, `ttl_sec` |
| `gifted_subs` | Twitch → Gift Subscription (C03) | `username`, `count`, `tier`, `ttl_sec` — “subscriptions” |
| `sub` | Twitch → Subscription (+ Resub) | `username`, `message`, `months`, `tier`, `ttl_sec` |
| `highlight` | *(no Streamer.bot trigger)* | — |
