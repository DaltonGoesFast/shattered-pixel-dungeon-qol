# Custom surface inventory (pre–SPD 4.0)

**Purpose:** Merge checklist for agents/humans bringing this fork onto upstream **v4.0**. Reapply listed hooks; do not rename WebSocket or settings contracts casually.

**Out of scope:** Economy rules, Streamer.bot R1–R10, Godot companion internals — already documented. This file covers the **Java game membrane** those systems talk through.

**How to use on merge day**

1. Restore **§A owned files** if git dropped them.
2. Re-patch **§B upstream hooks** (intent-first; methods may have moved).
3. Keep **§C protocol strings** stable unless you update Python + Godot together.
4. Run **§D smoke tests**.

**Related:** [project-structure.md](project-structure.md), [streaming-setup-guide.md](streaming-setup-guide.md), [COMMANDS.md](../COMMANDS.md), [setpiece-commands-brainstorm.md](setpiece-commands-brainstorm.md)

---

## A. Owned files (copy wholesale)

### Desktop streaming

| Path | Purpose |
|------|---------|
| `desktop/.../StreamingServer.java` | WS server `:5001`; inbound commands; result broadcasts |
| `desktop/.../StreamingCommandHandler.java` | All spend / streamer-debug game effects |
| `desktop/.../StreamingBootstrapper.java` | Start/stop server; snapshot ticker; `hero_died` / `boss_slain` |
| `desktop/.../StreamerItemResolver.java` | Streamer give-item / search |
| `desktop/.../StreamerBuffResolver.java` | Streamer apply buff/debuff search |
| `desktop/.../TrainingExportBootstrapper.java` | Optional ML export listener |
| `desktop/.../TrainingExportWriter.java` | ML export writer |
| `desktop/.../ItemShowcaseExporter.java` | Showcase export helper |
| `desktop/.../ItemShowcaseExportRunner.java` | Showcase export runner |

### Core utilities / buffs / UI (mod-owned)

| Path | Purpose |
|------|---------|
| `core/.../utils/GameStateSnapshot.java` | Periodic / on-connect JSON state for overlay |
| `core/.../utils/StreamingUI.java` | Item-info layout → `ui_layout` |
| `core/.../utils/StreamingEvents.java` | Flags: hero death / boss slain |
| `core/.../utils/AltAssetPaths.java` | `01` prefix helpers for alt tiles / sprites |
| `core/.../utils/TransparentVoid.java` | Desktop transparent dungeon void |
| `core/.../utils/TalentAutoPlan.java` | Auto talent plan when setting on |
| `core/.../utils/TrainingExport.java` | Training export bridge (mirrors StreamingUI) |
| `core/.../actors/buffs/ChatSpawned.java` | Chat-spawn XP + aura marker |
| `core/.../actors/buffs/SpawnScaled.java` | Out-of-depth HP/dmg/DR scale (baked factors) |
| `core/.../utils/SpawnScaleConfig.java` | Live spawn-scale knobs (combat/paralysis/XP) |
| `core/.../ui/TileIndicator.java` | Hover tile highlight |

### Messages / assets

| Path | Purpose |
|------|---------|
| `core/src/main/assets/messages/misc/misc.properties` | Keys under `desktop.streamingcommandhandler.*` |
| `core/src/main/assets/messages/windows/windows.properties` | Settings strings for QoL / streaming tabs |
| `core/src/main/assets/interfaces/noteicon.png` | Custom item-note marker in inventory |
| `core/src/main/assets/environment/01*.png` | Alt chapter tiles / water / terrain |
| `core/src/main/assets/environment/custom_tiles/01*.png` | Alt prison/caves custom tilemaps |
| `core/src/main/assets/sprites/01*.png` | Alt prison-chapter mob sprites |

**Alt asset list (game):**

- Environment: `01tiles_sewers.png`, `01tiles_prison.png`, `01tiles_caves.png`, `01water0.png`, `01water1.png`, `01water2.png`, `01terrain_features.png`
- Custom tiles: `01prison_quest.png`, `01prison_exit.png`, `01caves_boss.png`
- Sprites: `01bat`, `01dm100`, `01guard`, `01necromancer`, `01pylon`, `01skeleton`, `01swarm`, `01tengu`, `01thief`

---

## B. Upstream hooks (re-patch)

Small edits inside files Evan owns. After merge, re-open each and restore intent.

| File | Where / signal | Intent |
|------|----------------|--------|
| `desktop/.../DesktopLauncher.java` | After settings load | If `streamingEnabled()` → `StreamingBootstrapper.start()`; training export; transparent void GL setup |
| `core/.../SPDSettings.java` | Mod keys §C | Getters/setters for QoL + streaming prefs |
| `core/.../windows/WndSettings.java` | Display / UI / Data tabs | Checkboxes & alt-tileset slider |
| `core/.../Statistics.java` | Run start + bundle | Roll/save `sewersAltTileset`, `prisonAltTileset`, `cavesAltTileset` |
| `core/.../SPDAction.java` | Actions + defaults | `QUICKSLOT_SWAP_SET` (default `` ` `` / GRAVE) |
| `core/.../ui/Toolbar.java` | Quickslot bar | Dual quickslot set when `quickSwapper()` |
| `core/.../ui/BossHealthBar.java` | Assign / visibility | Broader boss bar when `bossBarAllEnemies()` |
| `core/.../ui/DangerIndicator.java` | Cycle targeting | `centerOnCycleNoEnemies()` |
| `core/.../ui/StatusPane.java` | HP/buff chrome | `obsChromaMasks()` visibility |
| `core/.../ui/ItemSlot.java` | Inventory slot | Note icon when item has custom note |
| `core/.../Assets.java` | Interfaces | `NOTE_ICON` path |
| `core/.../windows/WndInfoItem.java` | Open/close | `StreamingUI.notifyItemInfoLayout()` |
| `core/.../scenes/GameScene.java` | Layout / UI | `StreamingUI` + host `TileIndicator` |
| `core/.../ui/TalentsPane.java` | Talent spend UI | Skip prompts when `autoTalentPlan()` |
| `core/.../items/potions/exotic/PotionOfDivineInspiration.java` | Talent points | Auto plan path |
| `core/.../levels/SewerLevel.java` | Tilesheet / water / features | Alt via `useAltTileset(sewers…)` |
| `core/.../levels/PrisonLevel.java` (and related) | Same | Alt prison sheets |
| `core/.../levels/CavesLevel.java` | Same | Alt caves sheets |
| `core/.../levels/PrisonBossLevel.java` | Tiles + custom | Alt prison |
| `core/.../levels/CavesBossLevel.java` | Tiles + `01caves_boss` | Alt caves boss arena |
| `core/.../levels/MiningLevel.java` | Tile load | **Always vanilla** caves art (quest branch) |
| Prison mob sprites / custom tilemaps | Via `AltAssetPaths` | `01` sprites depths 6–10 when alt prison |
| `core/.../actors/mobs/Mob.java` | EXP on death | `ChatSpawned` → chapter-scaled XP |
| `core/.../actors/Char.java` | dmg / DR / defense | `SpawnScaled` factors |
| `core/.../actors/mobs/Ghoul.java` | Child spawn | Inherit `ChatSpawned` / `SpawnScaled` |
| `core/.../actors/mobs/Swarm.java` | Split | Same inherit |
| `core/.../actors/mobs/Necromancer.java` | Skeleton | Same inherit |
| `core/.../actors/mobs/DM100.java` | Zap damage | `SpawnScaled.damageFactor()` |
| `core/.../actors/mobs/Shaman.java` | Bolt damage | Same |
| `core/.../actors/mobs/Warlock.java` | Bolt damage | Same |
| `core/.../actors/mobs/Eye.java` | Death gaze | Same |
| `core/.../actors/mobs/Scorpio.java` | Chat-spawn specials | Tuned dmg/skill/cripple when `ChatSpawned` |
| `core/.../items/trinkets/ChaoticCenser.java` | Helper | Chat gas spawn distribution |

When adding setpieces later (`!ambush`, `!mimic`, …), add a new row here and a WS command row in §C.

---

## C. Protocol contracts (do not rename casually)

### C.1 WebSocket inbound `command` → result `type`

Common fields: `request_id`, optional `username` (echoed on results as `username`).

| `command` | Extra fields | Result `type` | Handler |
|-----------|--------------|---------------|---------|
| `ping` | — | `ping_result` (+ `version`) | inline |
| `spawn_scale_config` | nested `spawn_scale` knobs (HP/dmg/DR/paralysis/XP) | `spawn_scale_config_result` | `StreamingCommandHandler.handleSpawnScaleConfig` |
| `spawn` | `monster` | `spawn_result` | `handleSpawn` |
| `champion` | `monster` | `champion_result` | `handleSpawnChampion` |
| `gold` | `amount` (1–100) | `gold_result` | `handleDropGold` |
| `gas` | — | `gas_result` | `handleSpawnGas` |
| `curse` | optional `slot`; `class_kit_curse_duration_turns` | `curse_result` | `curseEquipped*` |
| `scroll` | — | `scroll_result` | `handleRandomScroll` |
| `wand` | optional `tier` 0–3 | `wand_result` | `handleCursedWand` |
| `buff` | — | `buff_result` | `handleChatBuff` |
| `debuff` | — | `debuff_result` | `handleChatDebuff` |
| `trap` | — | `trap_result` | `handleSpawnTrap` |
| `plant` | — | `plant_result` | `handlePlantRandom` |
| `bomb` | — | `bomb_result` | `handleSpawnBomb` |
| `transmute` | — | `transmute_result` | `transmuteForStream` |
| `summon_bee` | — | `summon_bee_result` | `handleSummonBee` |
| `ward` | — | `ward_result` | `handleSpawnWard` |
| `heal` | — | `heal_result` | `handleChatHeal` |
| `cleanse` | — | `cleanse_result` | `handleChatCleanse` |
| `dew` | — | `dew_result` | `handleChatDew` |
| `corrupt_ally` | — | `corrupt_ally_result` | `handleChatCorruptAlly` |
| `hex` | — | `hex_result` | `handleChatHex` |
| `degrade` | — | `degrade_result` | `handleChatDegrade` |
| `sabotage` | — | `sabotage_result` | `handleChatSabotage` |
| `ring_of_wealth` | — | `ring_of_wealth_result` | `handleRingOfWealthDrop` |
| `streamer_search_items` | `query`, `limit` | `streamer_debug_result` | search |
| `streamer_apply_buff` | `buff`, `duration` | `streamer_debug_result` | apply buff |
| `streamer_apply_debuff` | `debuff`, `duration` | `streamer_debug_result` | apply debuff |
| `streamer_give_item` | `item`, `quantity`, `level` | `streamer_debug_result` | give item |
| `streamer_heal_all` | — | `streamer_debug_result` | heal all |
| `streamer_identify_all` | — | `streamer_debug_result` | identify |
| `streamer_reveal_map` | — | `streamer_debug_result` | reveal |
| `streamer_goto_stairs_down` | — | `streamer_debug_result` | stairs |
| `streamer_goto_stairs_up` | — | `streamer_debug_result` | stairs |

**Consumers:** `Lastest UI/server.py` (relay), `spd-comp3/scripts/game_websocket_client.gd` (result events).

### C.2 Other outbound WS types

| `type` | Source | Notes |
|--------|--------|-------|
| (game snapshot JSON) | `GameStateSnapshot.build()` | On connect + ticker; overlay `game_summary` |
| `ui_layout` | `StreamingUI` → `broadcastUILayout` | Nested `item_info` for OBS crop |
| `hero_died` | Bootstrapper ticker | Via `StreamingEvents` |
| `boss_slain` | Bootstrapper ticker | Extra `depth` |

### C.3 Settings keys (mod-added)

| Key | Default | Feature |
|-----|---------|---------|
| `tile_indicator` | `true` | Hover tile highlight |
| `alt_tileset_chance` | `50` (0–100) | Per-chapter alt roll; migrates legacy `force_alt_tilesets` |
| `quickslot_swapper` | `true` | Dual quickslot sets |
| `show_quickslot_swap_button` | desktop `false` / mobile `true` | On-screen swap button |
| `center_on_cycle_no_enemies` | `false` | Danger indicator behavior |
| `boss_bar_all_enemies` | `true` | Boss-style HP bar for targets |
| `streaming_enabled` | `false` | Desktop WS server |
| `streaming_port` | `5001` (5000–5010) | WS bind port |
| `obs_chroma_masks` | `false` | StatusPane chroma helpers |
| `transparent_void` | `false` | Transparent undiscovered void (restart) |
| `streamer_boss_stasis_scroll` | `false` | Streamer boss/stasis scroll behavior |
| `auto_talent_plan` | `false` | Auto talent spend plan |
| `training_export_enabled` | `false` | ML dataset export (restart) |

### C.4 Run-save (`Statistics` bundle)

| Bundle key | Field | Notes |
|------------|-------|-------|
| `sewers_alt_tileset` | `sewersAltTileset` | Rolled at new run |
| `prison_alt_tileset` | `prisonAltTileset` | |
| `caves_alt_tileset` | `cavesAltTileset` | Mining branch still forces vanilla art |

### C.5 Spawn markers (runtime)

| Class | Applied when | Effect |
|-------|--------------|--------|
| `ChatSpawned` | Chat `spawn` / `champion` / corrupt ally (and inherits) | Chapter-scaled XP; dark aura |
| `SpawnScaled` | Out-of-depth chat spawn | HP + outgoing dmg + DR factors; inherits on Ghoul/Swarm/Necro |

---

## D. Smoke tests after merge

Desktop build, **Streaming** on, port **5001**, `python server.py` in `Lastest UI/`, active run:

1. **Ping** — WS `ping` → `ping_result` with `success` (version string may change; keep field).
2. **Spawn** — `!spawn rat` (or WS `spawn`) → mob has `ChatSpawned` aura; points deduct; chat reply OK.
3. **Deep spawn** — spawn a late-game mob on an early floor → `SpawnScaled` present; fight is not full vanilla power.
4. **Inherit** — chat-spawn ghoul/swarm/necro → children keep markers.
5. **Spend sample** — one each of `scroll`, `wand`, `heal`, `trap` success paths (or `Lastest UI/test_all_commands`).
6. **UI layout** — open item info → overlay/OBS sees `ui_layout` / inventory crop still works.
7. **QoL** — quickslot swap key works; tile indicator toggles; boss bar setting respected.
8. **Alt tiles** — new run at 100% alt chance → sewers (and prison/caves when reached) use `01` sheets; mining quest still vanilla caves.
9. **Death event** — die once → clients see `hero_died` (optional if not streaming events).

Offline helpers: `Lastest UI/test_chat_command_api.ps1`, `Lastest UI/phase3_rapid_test.ps1`, `Lastest UI/test_all_commands.ps1`.

---

## E. Agent rules for upstream work

- Prefer **conflict-resolution** of listed surfaces; do not rewrite streaming economy or companion unless asked.
- Preserve **§C command/result/settings strings** unless updating all consumers in the same change.
- New chat spends: add handler + WS branch + §B/§C rows + `COMMANDS.md` (if viewer-facing).
- City / enchant / AI / swarm overhauls from 4.0: merge first, then re-test spawn fairness — do not block merge on new setpieces.
