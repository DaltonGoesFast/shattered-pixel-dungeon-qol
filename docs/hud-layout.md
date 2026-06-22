# Gameplay HUD layout (desktop full UI)

Custom HUD positioning and scaling applies only when **Interface size** is set to **Large** (inventory pane on screen) on **desktop**.

## HUD regions

| Region | Components | Default position |
|--------|------------|------------------|
| **Status** | `StatusPane` — HP, XP, portrait, buffs, turn wheel, hero level | Bottom-left |
| **Log** | `GameLog`, `logBg` (chroma-key background) | Above status bar, ~160px wide |
| **Toolbar** | `Toolbar` — search, wait, quickslots, inventory toggle | Bottom, full width above inventory |
| **Inventory** | `InventoryPane` — equipped items, bag, gold | Bottom-right (187×82) |
| **Menu** | `MenuPane` — floor, challenges, journal, menu | Top-right |
| **Tags** | `AttackIndicator`, `LootIndicator`, `ActionIndicator`, `ResumeIndicator` | Right edge, stacked above toolbar |
| **Danger** | `DangerIndicator` — visible-enemy count / target switcher | Just below the menu pane |

`BossHealthBar` and safe-area filler bars are not customizable in v1.

## Edit mode

1. Use **Large** interface size in Settings → UI.
2. In-game, press **F8** to enter HUD edit mode.
3. **Drag** a highlighted region to move it. The drag snaps to a 4px grid by default.
4. Hold **Shift while dragging** to disable grid snapping (free 1px movement).
5. Click a region to select it (last-dragged region is auto-selected), then:
   - **Arrow keys** nudge it 1px. Hold **Shift** for 4px steps.
   - **+ / =** scale up by 5% (Shift = 10%).
   - **-** scale down by 5% (Shift = 10%).
   - **R / Home / End / Insert** reset the selected region (position back to 0, scale back to 100%). With nothing selected, resets every region.
     *(0, Backspace, and Delete are intentionally avoided because they are commonly remapped to mouse clicks via custom keybindings, which would never reach the edit-mode listener.)*
6. A region within 3px of its default position snaps back to 0 offset. Scale range is 75%–150%.
7. Press **F8** again to save and exit.

While edit mode is active, dungeon input is blocked so you can drag, nudge, and scale without moving the hero.

### How scaling works

Each region renders through its own `Camera` whose zoom equals `uiCamera.zoom × regionScale`, with a scroll offset chosen so the region's natural corner (e.g. bottom-left for the status bar, top-right for the menu) stays fixed in screen space. Because the leaf visuals are untouched, internal animations (HP bar fill, busy spinner, buff icons) keep working at any scale.

## Reset

Settings → UI → **Reset HUD layout** restores all regions to default position and scale.

## Persistence

Offsets and scales are stored in game settings (`hud_<region>_ox`, etc.) and persist across runs.

## Streaming

OBS chroma-key masks (`obsChromaMasks`) move with their parent regions (status, menu, log).
