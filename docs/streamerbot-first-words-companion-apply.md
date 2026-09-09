# First Words → SPD Companion

Move the Twitch and YouTube First Words popup from OBS GDI sources into the dedicated
SPD Companion First Words overlay. The Python points award is unchanged.

## Companion

1. Restart or re-export SPD Companion after installing this change.
2. Open **F2 → Welcome toasts** and enable the overlay.
3. Set the horizontal zone and appearance there.
4. Set the vertical zone under **F2 → Vertical → First Words zone**.
5. Configure **F2 → Scene gates → First words** for both layouts.

The companion listens on the configured Streamer.bot UDP port (default `5100`) for:

```json
{"ui":"first_words","username":"viewer42","ttl_sec":7}
```

## Streamer.bot R02 - First Words

Keep both existing triggers:

- YouTube → Chat → First Words
- Twitch → Chat → First Words

Replace the OBS presentation sub-actions with:

1. **Set Argument**: `companionUi` = `first_words`
2. **Set Argument**: `companionTtlSec` = `7`
3. **Execute C#**: paste
   [`Lastest UI/streamerbot/phase2/SendCompanionPaidNoticeUdp.cs`](../Lastest%20UI/streamerbot/phase2/SendCompanionPaidNoticeUdp.cs)
4. Keep the existing **Play sound from folder at 94%** sub-action.

Remove from R02:

- Both OBS GDI Text updates
- Both OBS Source Visibility show actions
- The 7000 ms delay
- Both OBS Source Visibility hide actions

Do not add a companion delay or hide action; `ttl_sec` controls the hold time and the
companion handles fades.

## OBS cutover

Hide or remove `GROUP - FIRST WORDS` / `TEXT - FIRST WORDS` in both `HUD` and `V - HUD`
after the UDP test succeeds. Keep the companion window captures (`CAP - COMP` and
`CAP - COMP V`) visible.

## Test

With the companion running, send a local probe from PowerShell:

```powershell
$u = New-Object Net.Sockets.UdpClient
$b = [Text.Encoding]::UTF8.GetBytes('{"ui":"first_words","username":"TestViewer","ttl_sec":2}')
$null = $u.Send($b, $b.Length, '127.0.0.1', 5100)
$u.Close()
```

Confirm:

- Main and Vertical each show `Welcome TestViewer!`
- The toast uses its own zone and does not block paid notices or command alerts
- Main and Vertical scene gates hide/show it on the expected OBS program scenes
- A real Twitch and YouTube First Words trigger plays the existing sound once and shows
  only the companion toast
