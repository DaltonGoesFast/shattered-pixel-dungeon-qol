# OBS Scene Rebuild Plan

**Source:** `obs31 SPD` (`%APPDATA%\obs-studio\basic\scenes\obs31_SPD.json`)  
**Parsed:** July 2026  
**Goal:** Single main-canvas control plane + Aitum vertical as linked output (no duplicate `V - HUD` tree for Streamer.bot)

**Related:** [streaming-setup-guide.md](streaming-setup-guide.md), [streamerbot-http-gateway-apply.md](streamerbot-http-gateway-apply.md), [fard-system.md](fard-system.md)

---

## 1. Current scene map (every scene → what it nests)

Legend: **LIVE** = switch-to-live composite · **BASE** = building block · **OVERLAY** = nested HUD only · **V** = vertical duplicate tree · **ELSE** = non-SPD game layout

### LIVE composites (horizontal — nest `HUD`)

| Scene | Size | Nested scenes | Groups / notable top-level sources |
|-------|------|---------------|-----------------------------------|
| **LIVE - MAIN** | 1920×1080 | `CAP - SPD`, `CAP - COMP`, `CAP - CAM`, **`HUD`** | `GROUP - CAM`, `GROUP - AUDIO`, audio captures, `kesha` |
| **LIVE - MAIN Dual Screen** | 1920×1080 | same as MAIN | + `DISCORD STREAM`, `LEN` |
| **LIVE - FULL** | 1920×1080 | `CAP - DESKTOP 1`, `CAP - COMP`, `CAP - CAM`, **`HUD`** | `BG MAIN`, `GROUP - CAM`, `GROUP - AUDIO` |
| **LIVE - CAM** | 1920×1080 | `CAP - CAM`, **`HUD`** | `GROUP - AUDIO` |
| **LIVE - CAM green screened** | 1920×1080 | `CAP - CAM`, **`HUD`** | `Media Source`, `GROUP - AUDIO` |
| **LIVE - CAM cropped** | 1920×1080 | `CAP - COMP`, `CAP - SPD`, `CAP - CAM` | **No HUD** — `GROUP - SPD CROP`, debug caps |
| **LIVE - COMP** | 1920×1080 | — | Companion/audio only (no game, no HUD) |
| **LIVE - ELSE** | 1920×1080 | `CAP - CAM`, **`HUD`** | Many game caps (STS, QUD, RE, DuckStation), `HUD - CHAT` duplicated at top level |
| **LIVE - ALCHEMY** | 1920×1080 | `CAP - COMP`, `CAP - SPD`, **`HUD`**, `CAP - CAM` | `GROUP - CAM`, `GROUP - AUDIO`, `Nintedo Cam` |
| **LIVE - PAUSE** | 1920×1080 | `CAP - COMP`, **`HUD`** | `IMAGE - PAUSE`, `GROUP - AUDIO` |
| **Minecraft** | 1920×1080 | **`HUD`**, `CAP - CAM` | `GENERAL CAP`, `GROUP - AUDIO` |

### V composites (vertical — nest `V - HUD` or vertical HUD stack)

| Scene | Size | Nested scenes | Notes |
|-------|------|---------------|-------|
| **V - MAIN** | 1080×1920 | `CAP - SPD`, `CAP - CAM`, `CAP - COMP`, `V - LOG`, `V - INV HUD`, `V - STAT HUD`, `V - CHAL HUD`, **`V - HUD`** | Full SPD vertical stack + `V - Summon Group`, `kesha` |
| **V- MAIN DUAL SCREEN** | 1080×1920 | same SPD stack + **`V - HUD`** | Typo: missing space after `V-` |
| **V - WIP** | 1080×1920 | same as V - MAIN | + `Browser` test source |
| **V - FULL** | 1080×1920 | `CAP - DESKTOP 1`, `CAP - CAM`, **`V - HUD`** | |
| **V - CAM** | 1080×1920 | `CAP - CAM`, **`V - HUD`** | |
| **V - PAUSE** | 1080×1920 | `CAP - COMP`, **`V - HUD`** | |
| **V - ALCHEMY** | 1080×1920 | `CAP - SPD`, `V - INV/STAT/CHAL HUD`, `CAP - CAM`, **`V - HUD`** | `GROUP - V ALCH` |
| **V - Minecraft** | 1080×1920 | **`V - HUD`**, `CAP - CAM` | |
| **V - HORROR WEEK** | 1080×1920 | `CAP - CAM` | Inline chat/viewers (no `V - HUD` nest) |
| **V - CAVESTORY** | 1080×1920 | `CAP - CAM` | Inline chat/viewers |
| **V - ELSE - DS EMU** | 1080×1920 | `CAP - CAM` | DS emu screens, inline viewers/chat |
| **V - ELSE** | 1080×1920 | `CAP - CAM` | Multi-game else layout, inline HUD bits |
| **V - COMP** | 1080×1920 | — | Empty shell |

### ELSE / game-specific (horizontal — mostly no HUD nest)

| Scene | Size | Nested scenes | Notes |
|-------|------|---------------|-------|
| **HORROR WEEK** | 1920×1080 | `CAP - CAM` | Inline HUD/chat/viewers |
| **ELSE - CAVESTORY** | 1920×1080 | `CAP - CAM` | Inline HUD/chat/viewers |
| **ELSE - DS EMU** | 1920×1080 | `CAP - CAM` | DS emu, inline viewers; **no HUD nest** |
| **test scene** | 1080×1920 | `CAP - SPD` | Dev |

### BASE capture scenes (`CAP - *`)

| Scene | Sources |
|-------|---------|
| **CAP - SPD** | `BG MAIN`, `SPD CAP` (game capture) |
| **CAP - COMP** | `COMP CAP` (window capture) |
| **CAP - CAM** | `CAM CAP` (webcam) |
| **CAP - DESKTOP 1** | `DESKTOP 1 CAP` |

### Vertical SPD HUD sub-scenes (game overlay layers)

| Scene | Nests | Purpose |
|-------|-------|---------|
| **V - INV HUD** | `CAP - SPD` + `V - INV HUD GROUP` | Inventory strip + dynamic crop (`obs_inv_layout.json`) |
| **V - STAT HUD** | `CAP - SPD` + `V - STAT HUD GROUP` | Run stats overlay |
| **V - CHAL HUD** | `CAP - SPD` + `V - CHAL HUD GROUP` | Challenges overlay |
| **V - LOG** | `CAP - SPD` + `V - GROUP LOG` | Run log overlay |

### OVERLAY scenes

| Scene | Size | Nests | Role |
|-------|------|-------|------|
| **HUD** | 1920×1080 | `CAP - COMP`, `CAP - CAM` (both usually off) | Horizontal overlay — chat, alerts, deaths, fard, pause |
| **V - HUD** | 1080×1920 | `CAP - CAM`, `CAP - COMP`, `V - COMP CROP` | Vertical overlay duplicate |

### Separator / junk

| Scene | Notes |
|-------|-------|
| **========** | Empty separator scene |

---

## 2. Consistent naming scheme

Use **three prefixes by tier**, then **four prefixes by source type**. Names are `UPPER - Title Case` for scenes and `PREFIX - Name` for sources.

### Scene tiers

| Prefix | Meaning | Switch live? | Example |
|--------|---------|--------------|---------|
| **`LIVE -`** | Composite = base + overlay (+ audio) | **Yes** (Stream Deck) | `LIVE - MAIN`, `LIVE - CAM`, `LIVE - FULL` |
| **`BASE -`** | Content only, no overlay | No | `BASE - SPD`, `BASE - CAM`, `BASE - AUDIO` |
| **`OVERLAY -`** | Nested HUD only | No | `OVERLAY - HUD` (replaces `HUD`) |
| **`CAP -`** | Atomic capture scene | No | `CAP - SPD`, `CAP - COMP` (keep as-is) |
| **`ELSE -`** | Non-SPD game layout base | Sometimes | `ELSE - DS EMU`, `ELSE - CAVESTORY` |

**Drop the `V - LIVE` duplicate scene tree** in a single-canvas rebuild. Vertical becomes an **Aitum linked view** of each `LIVE -` scene, named in Aitum only (e.g. link `LIVE - MAIN` → vertical output). You do not need `V - MAIN`, `V - HUD`, etc. as separate control targets.

### Source type prefixes

| Prefix | Replaces | Use for |
|--------|----------|---------|
| **`GRP -`** | `GROUP -` | Alert wrappers Streamer.bot toggles |
| **`HUD -`** | (same) | Browser sources |
| **`TXT -`** | `TEXT -` | GDI+ text Streamer.bot sets |
| **`IMG -`** | `IMAGE -` | Static images, memes, kesha |
| **`CAP -`** | (same) | Leaf capture inside `CAP -` scenes |

### Group layout inside `OVERLAY - HUD`

```
OVERLAY - HUD
├── GRP - BASE                 # persistent browser HUD
│   ├── HUD - CHAT             # or HUD - CHAT WIDE / HUD - CHAT TALL if both needed
│   ├── HUD - 2X COUNTER
│   └── HUD - DISCORD
├── GRP - STATS
│   ├── TXT - DEATH COUNT
│   ├── TXT - DEATHS
│   └── TXT - TOTAL FARD
├── GRP - ALERTS               # hidden by default
│   ├── GRP - FARDER
│   │   └── TXT - FARDER
│   ├── GRP - SUPERCHAT
│   │   └── TXT - SUPERCHAT
│   ├── GRP - VIEWERS
│   │   ├── TXT - VIEWERS
│   │   └── IMG - VIEWERS
│   ├── GRP - FIRST WORDS
│   │   └── TXT - FIRST WORDS
│   ├── GRP - SUMMONER
│   └── IMG - KESHA
└── GRP - STREAM STATE
    ├── TXT - PAUSE
    └── TXT - COUNTDOWN
```

### Streamer.bot stable names (migrate last)

These paths are wired in Streamer.bot export and `presentation_config.py`. **Keep old names until Phase 5**, or update SB + Python together:

| Current path | Rebuild name (optional) |
|--------------|-------------------------|
| `HUD :: GROUP - Farder` | `OVERLAY - HUD :: GRP - FARDER` |
| `HUD :: TEXT - farder` | `OVERLAY - HUD :: TXT - FARDER` |
| `V - HUD :: …` | **Delete** — single path only |
| `kesha` | `IMG - KESHA` inside `GRP - ALERTS` |
| `V - INV HUD GROUP` | `GRP - INV HUD` (keep if `server.py` crop automation stays) |

### Naming rules

1. **No loose sources** in LIVE scenes — only nested `BASE -`, `OVERLAY -`, `CAP -`, and `GRP - AUDIO`.
2. **No cam/game capture inside overlay** — move `VTUBER`, `Nintedo Cam`, `CAP - CAM` nests out of HUD.
3. **One group per alert** — Streamer.bot toggles `GRP - *`; text/image children stay visible inside hidden group.
4. **No duplicate sources** — one `IMG - HUNTRESS DANCE`, one `IMG - KESHA`.
5. **Scene Collection folders:** `00 LIVE` · `01 OVERLAY` · `02 BASE` · `03 ELSE` · `99 DEV`.

### Current → rebuild quick reference

| Current | Rebuild |
|---------|---------|
| `HUD` | `OVERLAY - HUD` |
| `V - HUD` | *(remove — Aitum link)* |
| `LIVE - MAIN` | `LIVE - MAIN` (nest `BASE - SPD` + `BASE - COMP` + `OVERLAY - HUD`) |
| `V - MAIN` | Aitum vertical link of `LIVE - MAIN` |
| `V - INV HUD` | `BASE - INV HUD` (vertical layout group on main canvas) |
| `GROUP - Farder` | `GRP - FARDER` |
| `TEXT - farder` | `TXT - FARDER` |
| `V- MAIN DUAL SCREEN` | `LIVE - MAIN DUAL` + Aitum link |

---

## 3. Single-canvas rebuild plan

### Architecture target

```
Main canvas (1920×1080 sources)
  LIVE - MAIN
    ├── BASE - SPD        (game + companion crops)
    ├── BASE - AUDIO      (optional group)
    └── OVERLAY - HUD     (scene source, nested)

Aitum Vertical
  LIVE - MAIN (linked) → 9:16 output (crop/reframe only)
```

Streamer.bot and `server.py` only touch **main canvas** source paths.

---

### Phase 0 — Backup & inventory

- [ ] OBS → Scene Collection → **Export** `obs31 SPD` to `Lastest UI/obs/backup/`
- [ ] Export Streamer.bot actions
- [ ] Note current Stream Deck scene list (which map to `LIVE - *` / `V - *`)

---

### Phase 1 — Build `OVERLAY - HUD` (slim)

Create new scene **`OVERLAY - HUD`** on main canvas. Migrate from current `HUD` **only**:

| Keep | Remove from overlay |
|------|---------------------|
| `HUD - CHAT`, `HUD - 2x Counter`, `HUD - DISCORD` | `CAP - COMP`, `CAP - CAM` nests |
| `GRP -` alerts (fard, superchat, viewers, first words, deaths) | `VTUBER`, `Nintedo Cam` |
| `TXT -` pause, countdown, deaths, total fard | `kesha` at LIVE level (alerts only) |
| `IMG - KESHA` in `GRP - ALERTS` | Duplicate huntress image |
| | `HUD - TEST SCENE`, `IMAGE - V TEST SCENE` → `99 DEV` |

**Test:** Nest `OVERLAY - HUD` over a color source; confirm browser URLs work.

---

### Phase 2 — Build `BASE - *` scenes

Split content out of `LIVE - MAIN`:

| New scene | Contents from today |
|-----------|---------------------|
| **`BASE - SPD`** | `CAP - SPD` (game) + `GRP - SPD CROP` if needed |
| **`BASE - COMP`** | `CAP - COMP` (companion) |
| **`BASE - CAM`** | `CAP - CAM` + `GRP - CAM` |
| **`BASE - AUDIO`** | `MIC 1/2`, `DESKTOP`, `AUX`, `SPD OST`, `DISCORD`, `CHROME`, `GRP - AUDIO` |
| **`BASE - INV HUD`** | Today’s `V - INV HUD` group + crop filters (for `obs_inv_layout.json`) |
| **`BASE - STAT HUD`** | Today’s `V - STAT HUD GROUP` |
| **`BASE - CHAL HUD`** | Today’s `V - CHAL HUD GROUP` |
| **`BASE - LOG HUD`** | Today’s `V - GROUP LOG` |

Vertical game strips (`BASE - INV/STAT/CHAL/LOG`) stay on **main canvas** as positioned groups — Aitum reframes them for 9:16. Update `obs_inv_layout.json` source names when renamed.

---

### Phase 3 — Rebuild `LIVE - *` composites

Rebuild each live scene as **base layers + one overlay nest**:

```
LIVE - MAIN
  ├── BASE - SPD
  ├── BASE - COMP
  ├── BASE - CAM          (optional visibility)
  ├── BASE - INV HUD      (locked, for vertical crop region)
  ├── BASE - STAT HUD
  ├── BASE - CHAL HUD
  ├── BASE - LOG HUD
  ├── BASE - AUDIO
  └── OVERLAY - HUD       (always on top)
```

| Scene | Bases to nest |
|-------|---------------|
| `LIVE - MAIN` | SPD + COMP + CAM + INV/STAT/CHAL/LOG + AUDIO + OVERLAY |
| `LIVE - CAM` | CAM + AUDIO + OVERLAY |
| `LIVE - FULL` | DESKTOP + COMP + CAM + AUDIO + OVERLAY |
| `LIVE - ELSE` | Game-specific caps + CAM + AUDIO + OVERLAY |
| `LIVE - PAUSE` | COMP + AUDIO + OVERLAY (+ pause image in OVERLAY or BASE) |
| `ELSE - *` | Game base only; add `OVERLAY - HUD` nest if chat/alerts needed |

**Fix:** `LIVE - CAM cropped` should nest `OVERLAY - HUD` like other LIVE scenes.

---

### Phase 4 — Aitum vertical links (no `V - HUD`)

For each `LIVE -` scene you stream vertically:

1. In Aitum Vertical, create a **linked vertical scene** pointing at the horizontal `LIVE -` scene.
2. Set crop/safe area for 9:16.
3. **Do not duplicate** `TXT - FARDER`, `GRP - FARDER`, etc. on vertical canvas.
4. Retire: `V - MAIN`, `V - HUD`, `V - CAM`, `V - FULL`, `V - PAUSE`, `V - ALCHEMY`, `V - Minecraft`, etc.

**Chat layout:** Either

- **Option A (simplest):** One `HUD - CHAT` browser source designed for a vertical-safe center strip (Aitum crops), or  
- **Option B:** Two browser sources on main canvas (`HUD - CHAT WIDE` visible in 16:9, `HUD - CHAT TALL` positioned for 9:16 crop) — still **no Streamer.bot duplication** if both are always on and Aitum reframes.

---

### Phase 5 — Streamer.bot dedupe

Remove every duplicate `V - HUD ::` sub-action. One path per target:

| Action | Before | After |
|--------|--------|-------|
| R09 / Fard GDI | `HUD` + `V - HUD` text set | `OVERLAY - HUD :: TXT - FARDER` once |
| R09 / Fard show | 2× Source Visibility | `OVERLAY - HUD :: GRP - FARDER` once |
| Kesha | `kesha` on HUD + V-HUD | `OVERLAY - HUD :: IMG - KESHA` once |
| First Words | 4+ OBS steps | 2 steps (GDI + show group) |

Update `Lastest UI/presentation_config.py`:

```python
OBS_SOURCES = {
    "GRP_FARDER": "OVERLAY - HUD :: GRP - FARDER",
    "TXT_FARDER": "OVERLAY - HUD :: TXT - FARDER",
    # remove _H / _V pairs
}
```

---

### Phase 6 — Stream Deck & cleanup

| Button | Scene |
|--------|-------|
| Main gameplay | `LIVE - MAIN` |
| Cam | `LIVE - CAM` |
| Full / desktop | `LIVE - FULL` |
| Else / emu | `ELSE - DS EMU` or `LIVE - ELSE` |
| Pause | `LIVE - PAUSE` |

- [ ] Delete or archive old `V - *` scenes after Aitum links verified
- [ ] Delete separator `========`
- [ ] Rename `V- MAIN DUAL SCREEN` → fold into `LIVE - MAIN DUAL`
- [ ] Move `test scene` to `99 DEV` folder

---

### Phase 7 — Verification checklist

| Test | Pass criteria |
|------|---------------|
| `!fard` | GRP + TXT flash on **both** 16:9 and 9:16 outputs |
| `!kesha` | IMG flashes both outputs |
| First Words | GDI + group show both outputs |
| `!doublepoints` | HUD - 2x Counter visible both outputs |
| Inventory popup | `server.py` crop still moves `GRP - INV HUD` filter |
| Scene switch | Stream Deck only touches `LIVE - *` |
| Streamer.bot OBS picker | All targets appear under main canvas scenes |

---

## Summary

| Item | Today | Target |
|------|-------|--------|
| Scene count | 39 (H + V duplicates) | ~20 main canvas + Aitum links |
| Streamer.bot OBS steps per alert | 2 (H + V) | 1 |
| HUD contents | Game cam, VTuber, captures, alerts, chat | Alerts + chat + stats only |
| Vertical control | Separate `V - HUD` tree | Aitum reframe of `LIVE - *` |
| Naming | Mixed (`GROUP -`, `kesha`, `V- MAIN`) | Tier prefixes + consistent `GRP -` / `TXT -` / `IMG -` |

**Order of operations:** OVERLAY → BASE → LIVE composites → Aitum links → Streamer.bot → delete `V - *`.
