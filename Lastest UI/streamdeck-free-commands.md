# Stream Deck — free promos (PowerShell plugin)

Use these as **script lines** in plugins that wrap your text in a `.ps1` file.  
Do **not** paste `powershell.exe`, `-NoProfile`, or `-File` here.

Most free buttons call **`streamdeck_cost_free.ps1`** with a **`costKey`** (see [Cost keys](#cost-keys-you-can-make-free-costkey)). **`streamdeck_rat_free.ps1`** is a shortcut for rat only.

Adjust the path if your project folder is elsewhere.

**Base path (folder containing the scripts):**

`c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI`

PowerShell Stream Deck plugins often show the blue console window for a split second while the script runs. That is normal; everything below assumes that workflow.

---

## Set duration (one command per button)

After you press one of these, **every** free-promo button below uses that many minutes until you pick another (unless you pass a minute override as the second argument to `streamdeck_cost_free.ps1`).

**Set time — 1 min**

```powershell
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_set_free_duration.ps1" 1
```

**Set time — 3 min**

```powershell
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_set_free_duration.ps1" 3
```

**Set time — 5 min**

```powershell
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_set_free_duration.ps1" 5
```

**Set time — 7 min**

```powershell
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_set_free_duration.ps1" 7
```

**Set time — 10 min**

```powershell
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_set_free_duration.ps1" 10
```

**Set time — 15 min**

```powershell
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_set_free_duration.ps1" 15
```

---

## Free promo — one line per Stream Deck button (`!gold` omitted)

Use **`streamdeck_cost_free.ps1`** plus the **`costKey`**. Each line is one button; all use your last **set duration** (or **5** minutes if you never set one).

Pattern:

```powershell
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" <costKey>
```

### Standard commands (no `cost_per_gold`)

```powershell
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_curse
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_gas
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_scroll
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_ring_of_wealth
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_trap
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_bomb
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_transmute
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_ally_bee
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_ward
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_buff
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_debuff
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_wand
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_heal
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_cleanse
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_dew
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_corrupt_ally
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_hex
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_degrade
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_sabotage
```

### Monsters (`cost_per_monster.<name>`)

```powershell
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_monster.rat
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_monster.albino
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_monster.snake
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_monster.gnoll
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_monster.crab
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_monster.slime
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_monster.swarm
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_monster.thief
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_monster.skeleton
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_monster.bat
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_monster.brute
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_monster.shaman
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_monster.spinner
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_monster.dm100
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_monster.guard
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_monster.necromancer
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_monster.ghoul
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_monster.elemental
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_monster.warlock
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_monster.monk
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_monster.golem
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_monster.succubus
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_monster.eye
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cost_free.ps1" cost_per_monster.scorpio
```

One-off minutes for a single button (does not change saved duration): add a number after the key, e.g.  
`... streamdeck_cost_free.ps1" cost_per_hex 10`

---

## Rat free (shortcut — same as `cost_per_monster.rat`)

```powershell
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_rat_free.ps1"
```

If you never pressed a set-duration button, this defaults to **5** minutes.

---

## Rat free — one-off minutes (does not change the saved duration)

Example for 10 minutes this run only:

```powershell
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_rat_free.ps1" 10
```

---

## Cancel all free promos

Ends **every** timed free (all cost keys at once). Same effect as hitting **Cancel** for each row on the points dashboard.

```powershell
& "c:\Users\dalto\Documents\My Games\SPD\march26 mod\shattered-pixel-dungeon-qol\Lastest UI\streamdeck_cancel_all_free.ps1"
```

This writes an empty `free_until.json` in the Lastest UI folder. The Python server and chat commands both read that file, so pricing updates on the next request.

---

## Cost keys you can make free (`costKey`)

Anything the dashboard “Free” button can target matches chat pricing when that key is stored in `free_until.json`.  
Use the same string as **`costKey`** in `POST /api/cost-free` or in your own scripts.

Typical viewer/chat commands are shown as **`!command`** (your bot may use different prefixes).

### Standard commands

| `costKey` | Related command / meaning |
|-----------|---------------------------|
| `cost_per_gold` | `!gold` |
| `cost_per_curse` | `!curse` |
| `cost_per_gas` | `!gas` |
| `cost_per_scroll` | `!scroll` |
| `cost_per_ring_of_wealth` | `!row` (Ring of Wealth) |
| `cost_per_trap` | `!trap` |
| `cost_per_bomb` | `!bomb` |
| `cost_per_transmute` | `!transmute` |
| `cost_per_ally_bee` | `!bee` |
| `cost_per_ward` | `!ward` |
| `cost_per_buff` | `!buff` |
| `cost_per_debuff` | `!debuff` |
| `cost_per_wand` | `!wand` |
| `cost_per_heal` | `!heal` |
| `cost_per_cleanse` | `!cleanse` |
| `cost_per_dew` | `!dew` |
| `cost_per_corrupt_ally` | `!corrupt_ally` |
| `cost_per_hex` | `!hex` |
| `cost_per_degrade` | `!degrade` |
| `cost_per_sabotage` | `!sabotage` |

### Monsters (spawn & champion)

Use **`cost_per_monster.<name>`** — one key per mob. Examples: `cost_per_monster.rat`, `cost_per_monster.gnoll`.

Supported **`name`** values (same list as the points dashboard):

`rat`, `albino`, `snake`, `gnoll`, `crab`, `slime`, `swarm`, `thief`, `skeleton`, `bat`, `brute`, `shaman`, `spinner`, `dm100`, `guard`, `necromancer`, `ghoul`, `elemental`, `warlock`, `monk`, `golem`, `succubus`, `eye`, `scorpio`

Viewer spawn/champion commands use these monster ids (e.g. spawn syntax your bot defines).

### Dashboard-only quirk

The points page has a **Default monster** row (`default_monster_cost`). Chat logic applies free promos per **`cost_per_monster.<name>`**, not via `default_monster_cost`. Making `default_monster_cost` “free” in the UI/API does **not** zero viewer costs for unspecified monsters—use per-monster keys or change the default price in config instead.

---

## Requirements

- **`streamdeck_cost_free.ps1`**, **`streamdeck_rat_free.ps1`**, and anything that calls `http://127.0.0.1:5000` need Lastest UI running (`python server.py` from this folder).
- **Cancel all free promos** only rewrites `free_until.json`; server can be off.
- Default API URL: `http://127.0.0.1:5000`.
- Errors from `streamdeck_cost_free.ps1` are logged to **`streamdeck_cost_free_last_error.txt`** in this folder.
