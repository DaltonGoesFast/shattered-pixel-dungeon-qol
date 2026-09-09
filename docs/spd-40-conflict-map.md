# SPD 4.0 conflict map (merge prep)

**Purpose:** Conflict forecast and merge playbook for bringing this fork onto official **v4.0.0**. Owned-file lists and hook *intent* stay in [custom-surface-inventory.md](custom-surface-inventory.md); this map adds collision ratings.

**Sources:** [custom-surface-inventory.md](custom-surface-inventory.md) §A / §B / §C (do not duplicate those tables), [README.md](../README.md) QoL + streaming list. Ratings were first forecast from Steam beta JARs; official source is now on GitHub.

**Versions**

| Build | Identity |
|-------|----------|
| This fork (freeze) | Branch/tag **`v3.3.8-qol`**: `build.gradle` **3.3.8** / `appVersionCode` **896** |
| Official GitHub `00-Evan/shattered-pixel-dungeon` | Tag **`v4.0.0`** (source published): `appVersionName` **4.0.0** / `appVersionCode` **912**. Work branch: **`upstream-4.0`**. |
| Steam beta (historical) | `core-4.0.0-BETA-4.jar` was used for the original forecast (`Implementation-Version` **906**). Prefer GitHub source for the merge. |

**Class-name snapshot** (top-level types only; inner `$` classes skipped; from beta JAR forecast): Steam `core` JAR **1228** classes, this repo `core/src/main/java` **1210**. **45** types only in 4.0, **27** only in the fork.

---

## How to use

Official **v4.0.0** Java source is on GitHub. Merge on **`upstream-4.0`**; keep **`v3.3.8-qol`** as fallback. Do not land 4.0 on `master` until asked.

- Use severity tables below during conflict resolution.
- Owned-file lists and hook *intent* stay in [custom-surface-inventory.md](custom-surface-inventory.md).
- Do **not** decompile Steam/JAR builds into this repo — take `upstream` tag `v4.0.0`.
- Store-only extras (`Analytics`, `Payment`, `Sync`, …) are **not** in open-source `SPDSettings`; skip unless they land inside a §B file we already patch.

---

## Severity ratings

| Rating | Meaning on merge day |
|--------|----------------------|
| **safe copy** | Type / asset is fork-only. Restore wholesale if git dropped it. |
| **re-patch likely** | Same class still exists; neighborhood looks stable. Re-apply §B intent. |
| **expect rewrite** | 4.0 added fields, layers, or a new quest/API next to the hook. Take upstream, then re-apply intent. |
| **replace tester** | 3.3 vault-tester name that 4.0 deleted or renamed. Do not 3-way merge. |
| **leave until source** | New 4.0 type we do not hook. Ignore unless a later chat opts in. |

Store-only extras (`Analytics`, `Payment`, `Sync`, `WndAnalytics`, `WndSupporterTiers`, `WndGooglePlayGames`) are **not** in open-source 4.0 `SPDSettings` — skip unless they land inside a §B file we already patch (`SPDSettings` / `WndSettings`). Open-source does add `KEY_VAULT_INJURE_WARNS`.

---

## Severity table — §A owned files

Cite [custom-surface-inventory.md](custom-surface-inventory.md) §A. Paths abbreviated as `desktop/...` / `core/...` there.

### Desktop streaming

| §A path | Rating | Why |
|---------|--------|-----|
| `desktop/.../StreamingServer.java` | **safe copy** | Absent from official `desktop-4.0.0-BETA-4.jar`. |
| `desktop/.../StreamingCommandHandler.java` | **safe copy** | Same. `!curse` already calls `Weapon.Enchantment.randomCurse()` — new 4.0 curses ride along after `Weapon` is taken from upstream. |
| `desktop/.../StreamingBootstrapper.java` | **safe copy** | Official desktop JAR only has Launcher / PlatformSupport / WindowListener / LaunchValidator. |
| `desktop/.../StreamerItemResolver.java` | **safe copy** | Fork-only. |
| `desktop/.../StreamerBuffResolver.java` | **safe copy** | Fork-only. |
| `desktop/.../TrainingExportBootstrapper.java` | **safe copy** | Fork-only. |
| `desktop/.../TrainingExportWriter.java` | **safe copy** | Fork-only. |
| `desktop/.../ItemShowcaseExporter.java` | **safe copy** | Fork-only. |
| `desktop/.../ItemShowcaseExportRunner.java` | **safe copy** | Fork-only. |

### Core utilities / buffs / UI

| §A path | Rating | Why |
|---------|--------|-----|
| `core/.../utils/GameStateSnapshot.java` | **safe copy** | In the 27 repo-only types. |
| `core/.../utils/StreamingUI.java` | **safe copy** | Repo-only. |
| `core/.../utils/StreamingEvents.java` | **safe copy** | Repo-only. |
| `core/.../utils/AltAssetPaths.java` | **safe copy** | Repo-only. 4.0 new sheets (`raised_terrain`, `occlusion_shadows`) may need extra prefix rules later — that is a §B follow-up, not an owned-file rewrite. |
| `core/.../utils/TransparentVoid.java` | **safe copy** | Repo-only. |
| `core/.../utils/TalentAutoPlan.java` | **safe copy** | Repo-only. |
| `core/.../utils/TrainingExport.java` | **safe copy** | Repo-only. |
| `core/.../actors/buffs/ChatSpawned.java` | **safe copy** | Repo-only. |
| `core/.../actors/buffs/SpawnScaled.java` | **safe copy** | Repo-only. |
| `core/.../utils/SpawnScaleConfig.java` | **safe copy** | Repo-only. |
| `core/.../ui/TileIndicator.java` | **safe copy** | Repo-only. Hosted from `GameScene` (§B, **expect rewrite**). |

### Messages / assets

| §A path | Rating | Why |
|---------|--------|-----|
| `core/.../messages/misc/misc.properties` | **re-patch likely** | Shared file. Keep 4.0 keys; restore `desktop.streamingcommandhandler.*` only. Do not copy this fork’s whole file over 4.0. |
| `core/.../messages/windows/windows.properties` | **re-patch likely** | Shared. 4.0 already adds `windows.wndimpold.*` and will add more window copy. Restore QoL / streaming tab strings from §A. Official 4.0 already has `uitab.quickslot_swapper`. |
| `core/.../assets/interfaces/noteicon.png` | **safe copy** | Fork-only. 4.0 `Assets.Interfaces` has `CHANGE_ICONS`, not `NOTE_ICON`. |
| `core/.../assets/environment/01*.png` | **safe copy** | Keep. 4.0 new environment files are different names (`raised_terrain.png`, `occlusion_shadows.png`). |
| `core/.../assets/environment/custom_tiles/01*.png` | **safe copy** | Keep. 4.0 adds `carpet.png`, `rat_king_room.png` — do not overwrite `01*`. |
| `core/.../assets/sprites/01*.png` | **safe copy** | Keep. 4.0 adds `imp.png`, `sentry.png`, `vault_mirror.png`, `vault_tokens_door.png`. |

---

## Severity table — §B upstream hooks

Cite [custom-surface-inventory.md](custom-surface-inventory.md) §B. Every listed hook class **exists** in `core-4.0.0-BETA-4.jar` (or `desktop` JAR for the launcher).

| §B file | Rating | Why |
|---------|--------|-----|
| `desktop/.../DesktopLauncher.java` | **re-patch likely** | 4.0 still `main(String[])`. Restore streaming start, training export, transparent-framebuffer after settings load. |
| `core/.../SPDSettings.java` | **expect rewrite** | 4.0 adds `KEY_VAULT_INJURE_WARNS`, `KEY_ANALYTICS`, `KEY_NAME`, `KEY_DONATIONICON`, `KEY_GOLDENUI`. Official still has `KEY_QUICK_SWAP`. All §C.3 keys except that official swapper key must be re-inserted. |
| `core/.../windows/WndSettings.java` | **expect rewrite** | Same tab set (Display / UI / Input / Data / Audio / Langs). 4.0 Display/Data inners changed; fork adds tile indicator, alt-tileset slider, extra swap-button, boss-bar, auto-talent, streaming/chroma/training on Data. Take 4.0 tabs, re-add controls. |
| `core/.../Statistics.java` | **re-patch likely** | 4.0 adds `vaultInjureWarned`. No alt-tileset fields. Restore §C.4 bundle keys on `reset` / `store` / `restore`. |
| `core/.../SPDAction.java` | **re-patch likely** | 4.0 has no `QUICKSLOT_SWAP_SET`. Official swap is toolbar-button only (`Toolbar.SlotSwapTool` still exists). |
| `core/.../ui/Toolbar.java` | **re-patch likely** | 4.0 still has `btnSwap`, `swappedQuickslots`, `SWAP_INSTANCE`. Re-bind the fork key action + `quickSwapper()` gates. |
| `core/.../ui/BossHealthBar.java` | **re-patch likely** | Class present; restore `bossBarAllEnemies()`. |
| `core/.../ui/DangerIndicator.java` | **re-patch likely** | Class present; restore `centerOnCycleNoEnemies()`. |
| `core/.../ui/StatusPane.java` | **re-patch likely** | Class present; restore `obsChromaMasks()`. |
| `core/.../ui/ItemSlot.java` | **re-patch likely** | Class present; restore note-icon when a custom note exists. |
| `core/.../Assets.java` | **expect rewrite** | 4.0 Environment adds `RAISED_TERRAIN`, `OCCLUSION_SHADOWS`, `CARPET`, `RAT_KING_ROOM`. Interfaces adds `CHANGE_ICONS`. Sprites add `IMP`, `SENTRY`, `VAULT_TOKENS_DOOR`, `VAULT_MIRROR`. Restore `NOTE_ICON` only. |
| `core/.../windows/WndInfoItem.java` | **re-patch likely** | Class present; restore `StreamingUI.notifyItemInfoLayout()`. |
| `core/.../scenes/GameScene.java` | **expect rewrite** | 4.0 adds `WallOcclusionTilemap` + `RaisedTerrainTilemap` fields. Fork also hosts `TileIndicator`, `HudLayout` / `HudEditMode`, and `StreamingUI`. Take 4.0 layout, re-host HUD hooks. |
| `core/.../ui/TalentsPane.java` | **re-patch likely** | Class present; restore `autoTalentPlan()` skip-prompts. |
| `core/.../items/potions/exotic/PotionOfDivineInspiration.java` | **re-patch likely** | Class present; restore auto-plan path. |
| `core/.../levels/SewerLevel.java` | **re-patch likely** | `tilesTex()` / `waterTex()` still exist. Re-apply `useAltTileset(sewers…)`. Decide later whether raised/occlusion sheets get `01` prefixes. |
| `core/.../levels/PrisonLevel.java` (and related) | **re-patch likely** | Same `tilesTex()` / `waterTex()`. Alt prison sprites still go through `AltAssetPaths`. |
| `core/.../levels/CavesLevel.java` | **re-patch likely** | Same. |
| `core/.../levels/PrisonBossLevel.java` | **re-patch likely** | Same. |
| `core/.../levels/CavesBossLevel.java` | **re-patch likely** | Same + `01caves_boss` custom tile. |
| `core/.../levels/MiningLevel.java` | **re-patch likely** | `tilesTex()` still exists. Keep **always vanilla** caves art. |
| Prison mob sprites / custom tilemaps | **re-patch likely** | Still via `AltAssetPaths` when `prisonAltTileset`. No 4.0 rename of those `01` files. |
| `core/.../actors/mobs/Mob.java` | **re-patch likely** | Restore `ChatSpawned` chapter-scaled XP on death. |
| `core/.../actors/Char.java` | **re-patch likely** | Restore `SpawnScaled` dmg / DR / defense factors. |
| `core/.../actors/mobs/Ghoul.java` | **re-patch likely** | Inherit hooks stay on child spawn. 4.0 also adds `VaultGhoul extends Ghoul` — inherit must not leak onto quest copies unless a later chat says so. |
| `core/.../actors/mobs/Swarm.java` | **expect rewrite** | `split()` still exists, but 4.0 adds `die(Object)` and new `SwarmIntelTracker`. Re-apply inherit on `split()` after taking upstream AI. |
| `core/.../actors/mobs/Necromancer.java` | **re-patch likely** | Restore skeleton inherit. |
| `core/.../actors/mobs/DM100.java` | **re-patch likely** | Restore `SpawnScaled.damageFactor()` on zap. 4.0 `VaultDM100` is a separate quest type. |
| `core/.../actors/mobs/Shaman.java` | **re-patch likely** | Same for bolt. |
| `core/.../actors/mobs/Warlock.java` | **re-patch likely** | Same. |
| `core/.../actors/mobs/Eye.java` | **re-patch likely** | Same for death gaze. |
| `core/.../actors/mobs/Scorpio.java` | **re-patch likely** | Restore `ChatSpawned` specials. |
| `core/.../items/trinkets/ChaoticCenser.java` | **re-patch likely** | Class present; restore chat gas helper. |

### Extra hooks not in §B (will still fight)

| File | Rating | Why |
|------|--------|-----|
| `desktop/.../DesktopWindowListener.java` | **re-patch likely** | Fork stops `TrainingExportBootstrapper` / `StreamingBootstrapper` on close. Not listed in §B. |
| `core/.../items/weapon/Weapon.java` | **expect rewrite** | Enchant / curse **arrays** rewritten in 4.0 (`javap -c` on `Weapon.Enchantment`). Fork also has `saveEnchantmentBeforeChatCurse()`. Take 4.0 registry, restore chat-curse save/restore. |
| `core/.../items/armor/Armor.java` | **re-patch likely** | `saveGlyphBeforeChatCurse()`. Glyph tables were not a 4.0 headline; still re-apply after merge. |

### Fork types not in the §A table (restore with §A)

These are in the 27 repo-only classes. Treat as **safe copy** unless noted: `ChatClassKitCurse`, `TimedAllyDuration`, `TimeFreeze`, `ScrollOfTimeFreeze`, `ScrollOfStasis`, `StreamerBossScroll`, `HudEditMode`, `HudLayout`, `HudRegion`, `HudSlot`, `ItemInfoLayout`, `TrainingSnapshot`. HUD layout is hosted from `GameScene` (**expect rewrite**). `TimeFreeze` is a fork class; 4.0 still has `TimekeepersHourglass$timeFreeze` and cleric `Stasis` — different types.

`WndImp` is **replace tester**, not owned.

---

## Hottest collisions first

### 1. Imp quest + vault tester — **expect rewrite** / **replace tester**

This fork still has the **3.3 dwarf-token Imp** (`Imp.java` imports `WndImp`; `Quest.reward` is a `Ring`; `process(Mob)` drops `DwarfToken` from monks/golems).

4.0 `Imp.Quest` (`javap`): `oldQuest`, `reward` is `Item`, `rewardOptions`, `hazardFreebies`, `mirrorUsed`, `score`, plus `isOld()`, `oldProcess()`, `oldComplete()`, `complete(int)`, `earnedShop()`. English keys: `actors.mobs.npcs.imp.quest_intro_*` (gear-stripped vault, shop “further down”) and `old_*` for leftover token quest. Window rename: **`WndImp` → `WndImpOld`** (`windows.wndimpold.*`).

`AmbitiousImpRoom` / `ImpShopRoom` / `ImpShopkeeper` / `ImpSprite` / `VaultLevel` **keep the same class names** but 4.0 `VaultLevel` gained `createEquipment` / `createConsumabe` / `createMob`. Take upstream. Do not 3-way-merge tester rooms.

`EscapeCrystal` exists in **both** (missed by the “45 JAR-only” list). Repo file is tester-sized; 4.0 has a large leave/injure dialog (`items.quest.escapecrystal.*`). **Take 4.0.** New 4.0 items: `ImpStatue`, `VaultBeacon`. New NPCs: `VaultMirror`, `VaultTokenDoor`.

### 2. Enchant / curse registry — **expect rewrite**

`Weapon.Enchantment` static init in the beta (`javap -c`):

| Bucket | 3.3.8 / this fork | 4.0.0-BETA-4 |
|--------|-------------------|--------------|
| common | Blazing, Chilling, Kinetic, Shocking | + **Venomous** |
| uncommon | Blocking, Blooming, Elastic, Lucky, Projecting, Unstable | + **Eldritch**, **Vorpal** |
| rare | Corrupting, Grim, Vampiric | + **Crystal** |
| curses | 8 vanilla | + **Pressurized**, **Wondrous** |

Keys exist: `items.weapon.enchantments.{crystal,eldritch,venomous,vorpal}.*`, `items.weapon.curses.{pressurized,wondrous}.*`. Crystal has a shatter / repair state machine (not a one-line proc).

`random` / `randomCommon` / `randomUncommon` / `randomRare` / `randomCurse` **names are unchanged**. After merge, `!curse` will naturally roll the two new curses. There is no `!enchant` command today.

### 3. `SPDSettings` / `WndSettings` — **expect rewrite**

4.0 settings keys we do not have: vault injure-warn, analytics, display name, donation icon, golden UI. 4.0 `windows.wndsettings$*` still matches official 3.3 tabs (no streaming / alt-tileset / tile-indicator strings). Official UI tab already documents `quickslot_swapper`. Restore every §C.3 key that is not that official swapper.

### 4. `GameScene` / HUD / `Toolbar` / `StatusPane` / `BossHealthBar` — **expect rewrite** on scene, **re-patch** on widgets

4.0 `GameScene` loads `occlusion` (`WallOcclusionTilemap`) and `raisedTerrain` (`RaisedTerrainTilemap`). New assets: `environment/occlusion_shadows.png`, `environment/raised_terrain.png`, `interfaces/change_icons.png`. Fork `GameScene` also constructs `TileIndicator` and optional `HudLayout`. Take 4.0 tile stack, then re-host overlay hooks.

`Toolbar` swap button is upstream. Fork adds `SPDAction.QUICKSLOT_SWAP_SET` (default `` ` `` / GRAVE).

### 5. `Assets` + alt tiles (`SewerLevel` / `PrisonLevel` / `CavesLevel` + boss / mining) — **expect rewrite** on `Assets`, **re-patch** on levels

`tilesTex()` / `waterTex()` still exist on all six §B levels. Mining stays vanilla. 4.0 new sheets are **not** `01*`. Follow-up: whether `RAISED_TERRAIN` / `OCCLUSION_SHADOWS` should honor alt chance.

### 6. `Mob` / `Char` inherit hooks (`Ghoul` / `Swarm` / `Necromancer`) — **re-patch**, Swarm **expect rewrite**

`ChatSpawned` / `SpawnScaled` remain fork-only. 4.0 swarm AI adds `SwarmIntelTracker` (also called out in inventory §E). Re-test inherit after taking `Swarm.die` / intel. `VaultGhoul` extends `Ghoul` — keep quest mobs out of chat-spawn inherit unless opted in.

### 7. `Statistics` alt-tileset bundle — **re-patch likely**

4.0 has no `sewers_alt_tileset` / `prison_alt_tileset` / `caves_alt_tileset`. It does add `vaultInjureWarned`. Restore §C.4 on the 4.0 bundle methods.

---

## Do not merge these tester files blindly

**Deleted or renamed in 4.0 — drop the fork file, take the new name if any:**

| This repo (delete / do not 3-way) | 4.0 replacement |
|-----------------------------------|-----------------|
| `core/.../actors/mobs/VaultMob.java` | Quest mobs live under `actors/mobs/quest/vault/` (`VaultRat` now **extends `Rat`**, not `VaultMob`) |
| `core/.../actors/mobs/VaultRat.java` | `actors/mobs/quest/vault/VaultRat.java` |
| `core/.../levels/rooms/quest/vault/AlternatingTrapsRoom.java` | `VaultAlternatingFireRoom.java` |
| `core/.../windows/WndImp.java` | `windows/WndImpOld.java` (legacy token quest only) |

**Same path in both trees — take 4.0 wholesale, do not merge tester logic line-by-line:**

- `core/.../items/quest/EscapeCrystal.java`
- `core/.../levels/VaultLevel.java`
- `core/.../actors/mobs/npcs/Imp.java`
- `core/.../actors/mobs/npcs/ImpShopkeeper.java`
- `core/.../actors/mobs/npcs/VaultLaser.java`
- `core/.../actors/mobs/npcs/VaultSentry.java`
- `core/.../actors/blobs/VaultFlameTraps.java`
- `core/.../sprites/ImpSprite.java`
- `core/.../levels/rooms/quest/AmbitiousImpRoom.java`
- `core/.../levels/rooms/standard/ImpShopRoom.java`
- `core/.../levels/rooms/quest/vault/VaultCircleRoom.java`
- `core/.../levels/rooms/quest/vault/VaultCrossRoom.java`
- `core/.../levels/rooms/quest/vault/VaultEnemyCenterRoom.java`
- `core/.../levels/rooms/quest/vault/VaultEntranceRoom.java`
- `core/.../levels/rooms/quest/vault/VaultFinalRoom.java`
- `core/.../levels/rooms/quest/vault/VaultLasersRoom.java`
- `core/.../levels/rooms/quest/vault/VaultLongRoom.java`
- `core/.../levels/rooms/quest/vault/VaultQuadrantsRoom.java`
- `core/.../levels/rooms/quest/vault/VaultRingRoom.java`
- `core/.../levels/rooms/quest/vault/VaultRingsRoom.java`
- `core/.../levels/rooms/quest/vault/VaultSimpleEnemyTreasureRoom.java`
- `core/.../levels/rooms/quest/vault/treasure/VaultTreasureRoom.java`
- `core/.../levels/rooms/quest/vault/treasure/VaultBookcaseTreasureRoom.java`
- `core/.../levels/rooms/quest/vault/treasure/VaultFlamePathRoom.java`
- `core/.../levels/rooms/quest/vault/treasure/VaultLaserTreasureRoom.java`
- `core/.../levels/rooms/quest/vault/treasure/VaultManyScansRoom.java`
- `core/.../levels/rooms/quest/vault/treasure/VaultMultipleEnemyTreasureRoom.java`
- `core/.../levels/rooms/quest/vault/treasure/VaultSingleEnemyTreasureRoom.java`

**4.0-only vault types** (appear when taking upstream; no fork file to merge):  
`VaultHallwayRoom`, `VaultLongRingsRoom`, `VaultRoom`, `VaultTokensRoom`, `VaultCircleScanTreasureRoom`, `VaultFlamesTreasureRoom`, `VaultHardLaserTreasureRoom`, `VaultMirror`, `VaultTokenDoor`, `ImpStatue`, `VaultBeacon`, `VaultBossElemental`, `VaultDM100`, `VaultDM200`, `VaultElemental`, `VaultGhoul`, `VaultGolem`, `VaultShaman`, `VaultSkeleton`, plus sprites `SentrySprite`, `VaultBossElementalSprite`, `VaultMirrorSprite`, `VaultTokenDoorSprite`.

`CrystalVaultRoom` (special crystal-key room) is unrelated city-crystal content — not a tester file.

---

## Protocol stability

From [custom-surface-inventory.md](custom-surface-inventory.md) §C and §E:

> Preserve **§C command/result/settings strings** unless updating all consumers in the same change.

4.0 **does not** change WebSocket `command` / result `type` names, snapshot / `ui_layout` / `hero_died` / `boss_slain` types, or §C.3 settings keys. Those contracts stay put unless a later chat updates **Python + Godot together**.

`ping_result.version` may show `4.0.0` / `906` after a real source merge; keep the **field name**.

---

## Follow-ups (not this chat, not the first merge slice)

Inventory §E already says: city / enchant / AI / swarm overhauls — merge first, then re-test spawn fairness. Do not block merge on new setpieces.

| Topic | What 4.0 did | Later question |
|-------|----------------|----------------|
| New enchants | Crystal, Eldritch, Venomous, Vorpal; curses Pressurized, Wondrous | `!curse` will roll new curses automatically. No `!enchant` today. Crystal shatter + Wondrous chaotic-wand procs may need overlay copy, not protocol renames. |
| Vault mobs spawnable? | Quest subclasses (`VaultRat extends Rat`, etc.). Copy says “specific quest.” `StreamingCommandHandler.mobClassForName` only maps normal biome names. `VaultBossElemental` is a quest boss. | Default: **do not** add vault types to `!spawn` / `!champion`. |
| Imp shop timing | `Imp.Quest.earnedShop()`; shop “further down” after a good-enough vault result (partial victory still promises a shop). Old token quest is `isOld()` / `WndImpOld`. | Chat commands that assume a city Imp shop on a fixed floor need a re-read. |
| Gear-stripped vault | Hero enters without gear; `EscapeCrystal` + `VaultBeacon` + `VaultMirror`. | Chat spawn / curse / scroll during the vault is a fairness pass, not a protocol change. |
| Swarm intel | `SwarmIntelTracker` | Re-test `ChatSpawned` inherit on split after AI merge. |
| `HallOfHeroesScene` | New rankings-style scene + `ChangeIcons` / `v4_X_Changes` | Not a streaming hook. Companion “Hall of Fame” is unrelated. |
| New art | `imp.png`, `sentry.png`, `vault_mirror.png`, `vault_tokens_door.png`, `change_icons.png`, `occlusion_shadows.png`, `raised_terrain.png`, `custom_tiles/carpet.png`, `rat_king_room.png` | Take with official source (or scratch-extract for preview). Never overwrite `01*` or `noteicon.png`. |

---

## Suggested merge order

1. **Freeze** `v3.3.8-qol` (branch + tag) and work on `upstream-4.0`. Fetch official tag `v4.0.0`.
2. **Take upstream 4.0**. Let vault / Imp / enchant / tile-stack / swarm files become 4.0.
3. **Delete** the four replace-tester paths. Do not resolve those conflicts hunk-by-hunk.
4. **Restore §A** owned Java + `01*` / `noteicon`. For `misc.properties` / `windows.properties`, **merge keys** — do not wholesale-overwrite 4.0.
5. **Re-patch §B by severity:** `expect rewrite` first (`GameScene`, `Assets`, `SPDSettings`, `WndSettings`, `Weapon`, `Swarm`), then `re-patch likely` HUD / combat / alt-tiles / `Statistics` / launcher.
6. **Re-test vault + Imp** in a real city run (new quest, early escape, shop unlock, leftover `oldQuest` if a 3.3 save appears).
7. **Then** spawn fairness, Swarm inherit, new-enchant / `!curse` feel. Optional later: vault-mob spawn policy, Imp shop timing vs chat, alt-prefix for raised/occlusion.

Smoke list after that is inventory **§D** (unchanged commands).

---

## Out of scope

- No Vineflower / CFR / JADX dump into this repo.
- No economy, points, or `COMMANDS.md` cost changes.
- No Python overlay, Godot companion, or Streamer.bot work.
- No rebase and no “port 4.0 from the Steam JAR” (source is on GitHub).
- No store-module port (`Analytics` / `Payment` / `Sync`) unless it lands inside `SPDSettings` / `WndSettings`.
- Do not land 4.0 on `master` until explicitly asked.

---

## Evidence notes

- Compared `jar tf` class names in `C:\Program Files (x86)\Steam\steamapps\common\Shattered Pixel Dungeon\app\core-4.0.0-BETA-4.jar` to `core/src/main/java`.
- English keys from `desktop-4.0.0-BETA-4.jar` `messages/items/items.properties`, `scenes/scenes.properties`, `windows/windows.properties`, `actors/actors.properties`.
- `javap` on `Weapon.Enchantment` (array contents), `Imp.Quest` (new API), `Swarm` (`die` + `split`), `GameScene` (new tilemap fields), `SPDSettings` / `Assets` / `Statistics` / `SPDAction` / `Toolbar`.
- Welcome copy: `scenes.welcomescene.update_msg` — “massive new quest, a bunch of new in-game art, 6 new enchantments.”
