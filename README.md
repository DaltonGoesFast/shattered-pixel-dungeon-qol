# Shattered Pixel Dungeon — QoL & Shatter the Streamer mod

This repo is a fork of **Shattered Pixel Dungeon** focused on **quality-of-life UI**, **optional visibility into fights**, and **game-facing rules** for chat-driven actions when you run the **desktop** build with the streaming integration. The Android/iOS builds still benefit from the in-game UI changes; external commands require the desktop WebSocket path.

**Collaborating?** See [CONTRIBUTING.md](CONTRIBUTING.md), [docs/README.md](docs/README.md), and [streaming-setup-guide.md](docs/streaming-setup-guide.md).

---

## Mod changes (gameplay + QoL)

### UI and controls

- **Dual quickslot set:** Swap between two full quickslot bars with the **Quickslot swap** action (bindable in **Settings → Controls**; many keymaps use **`~`**). Turn the feature on or off under interface-related game settings (`quickslot_swapper`, on by default).
- **Tile selection indicator:** Highlights the tile you are targeting / interacting with. Toggle via **Settings** (`tile_indicator`, on by default).
- **Item note marker:** Items that have a **custom note** show a **note icon** in inventory lists so you can spot tagged gear at a glance.
- **Boss-style HP bar:** A wide boss-health style bar can track combatants. By default the game can show this style of bar more broadly (**boss bar on all enemies** setting is on by default; turn it off in settings if you only want vanilla-style presentation). Major bosses still hook the bar for phase / bleed styling where the base game does.

### Alt tilesets (visual)

Optional alternate environment art for the **first three chapters** (sewers, prison, caves). Each chapter is decided **independently** when a **new run** starts.

- **Setting:** **Settings → Display → Alt tilesets** — slider from **never** through **always**, in **10% steps** (stored as 0–100). At **0%** no chapter uses alt tiles; at **100%** all three do; in between, each chapter rolls its own chance when the run begins (saved with that run).
- **What changes:** Region **floor tiles**, **water**, and **`terrain_features`** load from `01`-prefixed sheets under `core/src/main/assets/environment/` (e.g. `01tiles_sewers.png`, `01tiles_prison.png`, `01tiles_caves.png`, matching waters and `01terrain_features.png` where used).
- **Prison extras:** While the prison chapter uses alt tiles, matching **mob sprites** use `01`-prefixed textures (e.g. guards, skeletons, tengu). **Quest tilemaps** (`prison_quest`, `prison_exit`) use `01`-prefixed **custom_tiles** when alt prison applies.
- **Caves extras:** The **DM-300 arena** custom tilemap uses **`01caves_boss.png`** when alt caves applies.
- **Blacksmith mine:** The **mining quest branch** always uses **vanilla** caves floor/water/terrain art so quest layouts stay readable even when the main caves use alt tiles.

### Enemies spawned via chat / streaming (desktop)

When a spawn or champion command reaches a live run through the streaming server:

- **ChatSpawned marking:** Enemies get the **`ChatSpawned`** buff — **region-scaled XP** when killed (by dungeon chapter instead of the mob’s raw base XP, so early floors aren’t flooded with late-game XP) and a **visible dark “champion-style” aura** (cosmetic).
- **Out-of-depth tuning:** Spawning something **above** the current floor’s native area applies **`SpawnScaled`** — HP is scaled down toward the current depth, and **outgoing damage and effective armor** are scaled down further so the fight stays closer to fair.
- **Cross-region spawn stagger:** Spawning a mob **far above** its home chapter can apply **short paralysis** on arrival (duration scales with how far “up” you pulled it), giving a beat to reposition.
- **Variant rolls:** `!spawn shaman` / champions pick a **random Red / Blue / Purple** shaman; `elemental` similarly rolls an elemental variant. **Champion** spawns add a **random champion modifier** (e.g. Blazing, Antimagic, Growing) at **2×** whatever **`!spawn`** would charge at the current depth (same half-price / chapter-gap surcharge when shallower; see [COMMANDS.md](COMMANDS.md)).
- **Spawn zone pricing (overlay):** **`!spawn`** is **half** the table cost when **deeper** than where that mob normally starts. When **shallower**, extra cost comes from **chapter gap** (five 5-floor chapters); you pay **base**, **+20%**, or **+40%** on that base (rounded—see `compute_spawn_cost` in `Lastest UI/points_command.py`). **`!champion`** uses **2×** that zone-adjusted spawn cost.
- **Ghoul splits:** Ghoul children spawned from a chat-spawned ghoul **inherit** `ChatSpawned` (and `SpawnScaled` when relevant) so behavior stays consistent.

### Extra chat spend commands

Viewers can use **`!heal`**, **`!cleanse`**, **`!dew`**, **`!corruptally`** (corrupted ally from the **current biome**, boss floors allowed), **`!hex`**, **`!degrade`**, and **`!sabotage`** (removes a **random positive buff that has a visible icon**). Costs are set in the overlay; there are no helper/hurter roles or side discounts.

### Other run-affecting chat actions (summary)

Full syntax and tables live in **[COMMANDS.md](COMMANDS.md)**. At a glance, viewers can spend points to:

- Spawn normal or **champion** mobs, drop **gold**, apply **curse** to a random equipped item, spawn **gas** or **traps**, fire a random **scroll** or **cursed wand** (**!wand**, one price, weighted effect), **transmute** an item, summon **bees** / **wards**, shuffle **buffs** and **debuffs**, and (streamer-only) run **`!doublepoints`**.

Point costs in the tables are the **defaults**; the overlay can override them.

### What is configurable (without rebuilding the game)

- **Per-command costs** and overlay behavior (see `Lastest UI` config / overlay). Optional per-command **disable** via `command_allowed_roles` in `points_config.json` (`"disabled"`).
- **Game settings:** tile indicator, quickslot swapper, boss bar on all enemies, **alt tilesets** chance (display tab), and other SPD interface toggles.

---

## Where to read more

- **[COMMANDS.md](COMMANDS.md)** — authoritative command list, monster costs, and Streamer.bot notes.
- **[docs/user-facing-summary.md](docs/user-facing-summary.md)** — short copy you can paste for panels / descriptions.
- **[docs/streaming-setup-guide.md](docs/streaming-setup-guide.md)** — full desktop + overlay + HTTP gateway wiring.
- **[`spd-comp3/`](spd-comp3/)** — Godot companion (summon marches, Bestiary HUD); see [`spd-comp3/AGENTS.md`](spd-comp3/AGENTS.md).
- **[docs/RELEASE.md](docs/RELEASE.md)** — build JAR / Windows `.exe` zip / APK for GitHub Releases.
- **[docs/README.md](docs/README.md)** — documentation index (gateway, economy, tests, archive).

---

# Shattered Pixel Dungeon

[Shattered Pixel Dungeon](https://shatteredpixel.com/shatteredpd/) is an open-source traditional roguelike dungeon crawler with randomized levels and enemies, and hundreds of items to collect and use. It's based on the [source code of Pixel Dungeon](https://github.com/00-Evan/pixel-dungeon-gradle), by [Watabou](https://watabou.itch.io/).

Shattered Pixel Dungeon currently compiles for Android, iOS, and Desktop platforms. You can find official releases of the game on:

[![Get it on Google Play](https://shatteredpixel.com/assets/images/badges/gplay.png)](https://play.google.com/store/apps/details?id=com.shatteredpixel.shatteredpixeldungeon)
[![Download on the App Store](https://shatteredpixel.com/assets/images/badges/appstore.png)](https://apps.apple.com/app/shattered-pixel-dungeon/id1563121109)
[![Steam](https://shatteredpixel.com/assets/images/badges/steam.png)](https://store.steampowered.com/app/1769170/Shattered_Pixel_Dungeon/)<br>
[![GOG.com](https://shatteredpixel.com/assets/images/badges/gog.png)](https://www.gog.com/game/shattered_pixel_dungeon)
[![Itch.io](https://shatteredpixel.com/assets/images/badges/itch.png)](https://shattered-pixel.itch.io/shattered-pixel-dungeon)
[![Github Releases](https://shatteredpixel.com/assets/images/badges/github.png)](https://github.com/00-Evan/shattered-pixel-dungeon/releases)

If you like this game, please consider [supporting me on Patreon](https://www.patreon.com/ShatteredPixel)!

There is an official blog for this project at [ShatteredPixel.com](https://www.shatteredpixel.com/blog/).

The game also has a translation project hosted on [Transifex](https://explore.transifex.com/shattered-pixel/shattered-pixel-dungeon/).

Note that **this repository does not accept pull requests!** The code here is provided in hopes that others may find it useful for their own projects, not to allow community contribution. Issue reports of all kinds (bug reports, feature requests, etc.) are welcome.

If you'd like to work with the code, you can find the following guides in `/docs`:
- [Compiling for Android.](docs/getting-started-android.md)
    - **[If you plan to distribute on Google Play please read the end of this guide.](docs/getting-started-android.md#distributing-your-app)**
- [Compiling for desktop platforms.](docs/getting-started-desktop.md)
- [Compiling for iOS.](docs/getting-started-ios.md)
- [Recommended changes for making your own version.](docs/recommended-changes.md)
