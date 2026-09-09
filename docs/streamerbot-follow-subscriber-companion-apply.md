# Twitch Follow/Sub and YouTube Subscriber → SPD Companion

Send Twitch follows/paid subscriptions and free YouTube channel subscriptions to the
shared SPD Companion welcome-toast zone. First Words keeps using the same zone and queue.
This change does not award points.

## Companion

1. Restart or re-export SPD Companion after installing the welcome-toast change.
2. Open **F2 → Welcome toasts** and enable the overlay.
3. Set the horizontal appearance and zone there.
4. Set the vertical zone under **F2 → Vertical → First Words zone**.
5. Configure **F2 → Scene gates → First words** for both layouts.

The companion listens on the configured Streamer.bot UDP port (default `5100`):

```json
{"ui":"follow","username":"new_follower","ttl_sec":7}
```

```json
{"ui":"subscriber","username":"new_subscriber","ttl_sec":7}
```

```json
{"ui":"youtube_subscriber","username":"new_youtube_subscriber","ttl_sec":7}
```

Use `subscriber` for Twitch paid subs and `youtube_subscriber` for free YouTube subs.
The `sub` kind remains reserved for the larger Paid notices card and would otherwise
display there instead of in the shared welcome queue.

## Streamer.bot C04 - New Follower

Create an action named **C04 - New Follower** in the **Companion Commands** group.

1. Add trigger **Twitch → Channel → Follow**.
2. Add **Set Argument**: `companionUi` = `follow`.
3. Add **Set Argument**: `companionTtlSec` = `7`.
4. Add **Execute C#** and paste
  `[Lastest UI/streamerbot/phase2/SendCompanionPaidNoticeUdp.cs](../Lastest%20UI/streamerbot/phase2/SendCompanionPaidNoticeUdp.cs)`.
5. Keep or add an existing follow sound if wanted.

Do not add OBS text, source visibility, delay, or hide steps. The companion handles text,
queueing, timing, and fades.

## Streamer.bot C05 - YouTube New Subscriber

Create an action named **C05 - YouTube New Subscriber** in the **Companion Commands**
group. This is the free YouTube equivalent of a Twitch follow, not a paid membership.

1. Add trigger **YouTube → General → New Subscriber**.
2. Add **Set Argument**: `companionUi` = `youtube_subscriber`.
3. Add **Set Argument**: `companionTtlSec` = `7`.
4. Add **Execute C#** and paste
  `[Lastest UI/streamerbot/phase2/SendCompanionPaidNoticeUdp.cs](../Lastest%20UI/streamerbot/phase2/SendCompanionPaidNoticeUdp.cs)`.
5. Keep or add a subscriber sound if wanted.

Streamer.bot polls YouTube for these events, so they may arrive roughly 10 seconds late.
YouTube does not expose subscribers whose subscriptions are private or whose Google
account has no public YouTube channel; those viewers cannot generate this toast.

## Streamer.bot C02 - New Sub/Member

C02 remains for paid Twitch subscriptions and paid YouTube memberships. Branch on the
platform:

- Twitch: set `companionUi` = `subscriber`.
- YouTube: set `companionUi` = `sub`; the C# sender remaps it to `membership`, preserving
the Paid notices membership card.

Place the platform If/Else and Set Argument before the existing Execute C# step. Keep the
existing Speaker.bot Speak step.

After this change, Twitch subscriptions no longer send `ui=sub`, so they do not duplicate
the welcome toast in Paid notices. Gift subscriptions and YouTube memberships remain on
Paid notices.

## Local test

With the companion running, send each probe from PowerShell:

```powershell
$u = New-Object Net.Sockets.UdpClient
$b = [Text.Encoding]::UTF8.GetBytes('{"ui":"follow","username":"TestFollow","ttl_sec":2}')
$null = $u.Send($b, $b.Length, '127.0.0.1', 5100)
$u.Close()
```

```powershell
$u = New-Object Net.Sockets.UdpClient
$b = [Text.Encoding]::UTF8.GetBytes('{"ui":"subscriber","username":"TestSub","ttl_sec":2}')
$null = $u.Send($b, $b.Length, '127.0.0.1', 5100)
$u.Close()
```

```powershell
$u = New-Object Net.Sockets.UdpClient
$b = [Text.Encoding]::UTF8.GetBytes('{"ui":"youtube_subscriber","username":"TestYouTubeSub","ttl_sec":2}')
$null = $u.Send($b, $b.Length, '127.0.0.1', 5100)
$u.Close()
```

Confirm:

- Main and Vertical show `Thanks for following, TestFollow!`.
- Main and Vertical then show `TestSub subscribed!` in the same zone.
- Main and Vertical show `Thanks for subscribing, TestYouTubeSub!` in the same zone.
- Sending `ui=first_words` joins the same queue.
- Sending `ui=sub` still displays only the Paid notices card.
- A real Twitch follow, Twitch paid subscription, and public YouTube free subscription
each play any retained sound once and show one companion toast.

