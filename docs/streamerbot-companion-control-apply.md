# Stream Deck → SPD Companion UI controls

Show, hide, or toggle any companion overlay from Stream Deck through one Streamer.bot
action. Commands use the companion's existing UDP port (default `5100`), apply to both
the horizontal and vertical layouts, and survive a companion restart.

## Requirements

- SPD Companion running with **F2 → Connection → Streamer.bot UDP port** set to `5100`
- Streamer.bot running
- Streamer.bot Stream Deck plugin connected

## Create the one Streamer.bot action

Create **C06 - Companion Control** in the **Companion Commands** group.

- **Trigger:** none (Stream Deck invokes it)
- **Queue:** any queue other than `points`
- **Sub-action:** Execute C#

Paste
`[Lastest UI/streamerbot/phase2/SendCompanionControlUdp.cs](../Lastest%20UI/streamerbot/phase2/SendCompanionControlUdp.cs)`
into Execute C# and compile it.

Do not create another Streamer.bot action for each button. Every Stream Deck key uses
this same C06 action and supplies its own arguments.

## Create a Stream Deck button

1. Add **Streamer.bot → Action** to a Stream Deck key.
2. Under **Press**, choose **C06 - Companion Control**.
3. In **Arguments**, use **Add Argument +** to add these two String rows:


| Name                     | Value                           |
| ------------------------ | ------------------------------- |
| `companionControlTarget` | One target from the table below |
| `companionControlAction` | `show`, `hide`, or `toggle`     |


The plugin-provided `sdButtonId` argument can remain; the C# ignores it.

Duplicate the key to make another control. Keep C06 selected and change only the
argument values.

## Targets


| Target                  | Companion element                               |
| ----------------------- | ----------------------------------------------- |
| `title`                 | Title backdrop                                  |
| `live_water`            | Live water                                      |
| `chrome_boxes`          | Placeable chrome boxes                          |
| `id_overlay`            | Potion/scroll identification overlay            |
| `alerts`                | Command alerts                                  |
| `tip_toasts`            | Custom tip toasts                               |
| `paid_notices`          | Superchat, membership, and subscription notices |
| `first_words`           | First Words notices                             |
| `bestiary`              | Bestiary HUD                                    |
| `summon_march`          | Summon march                                    |
| `spend_indicator`       | Chat-spend indicator                            |
| `free_promos`           | Free-promotions panel                           |
| `double_points`         | Double-points panel                             |
| `nine_challenge_deaths` | 9-challenge death counter                       |
| `starting_soon`         | Starting-soon banner (glow + side flames)       |


Examples:

- One-button 9c toggle: `nine_challenge_deaths` + `toggle`
- One-button starting-soon toggle: `starting_soon` + `toggle`
- Dedicated Hide Alerts button: `alerts` + `hide`
- Dedicated Show ID button: `id_overlay` + `show`

`on` and `off` are accepted aliases for `show` and `hide`.

## Behavior

- A valid command updates both horizontal and vertical master visibility.
- The result is saved in `user://companion_settings.cfg`.
- OBS scene gates still apply. For example, `show` does not override an element that
F2 Scene gates intentionally hides on the current OBS scene.
- Stream Deck button art is local and does not receive the companion's current state.
- Unknown targets and actions are ignored and logged by Godot; extra JSON fields are
ignored safely.



## Test without Stream Deck

Restart the companion after installing the Godot changes, then run:

```powershell
$udp = New-Object System.Net.Sockets.UdpClient
$json = '{"ui":"control","target":"starting_soon","action":"toggle"}'
$bytes = [Text.Encoding]::UTF8.GetBytes($json)
$udp.Send($bytes, $bytes.Length, "127.0.0.1", 5100) | Out-Null
$udp.Close()
```

The starting-soon banner should switch on both companion windows. Run it again to
hide it, then restart the companion and confirm the last state persisted.

To test 9c deaths instead, use `"target":"nine_challenge_deaths"`.

For explicit alert testing, replace the JSON with:

```json
{"ui":"control","target":"alerts","action":"hide"}
```

Then send the same packet with `"action":"show"`. Control packets must not create a
paid notice or command alert.

## Troubleshooting


| Symptom                                   | Check                                                                  |
| ----------------------------------------- | ---------------------------------------------------------------------- |
| Nothing changes                           | Companion is running; UDP port is `5100`; C06 log shows a send         |
| Godot logs unknown target                 | Target exactly matches the table (lowercase and underscores)           |
| Element remains hidden after `show`       | Check F2 Scene gates for the active OBS scene                          |
| Horizontal changes but vertical is absent | Enable/open the vertical companion window                              |
| Button image disagrees with the UI        | The Action key has no state feedback; press `show` or `hide` to resync |


