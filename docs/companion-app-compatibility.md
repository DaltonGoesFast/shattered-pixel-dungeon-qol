# Companion App Compatibility (Construct 3)

Your companion app is **compatible** with the new server. The new server serves `game_summary.json` at the same URL (`http://localhost:5000/game_summary.json`) with the same structure (`stats.depth`, `identification.potions`, `identification.scrolls`, etc.).

## Required Changes

Add defensive checks for when the game is on the title screen or data is incomplete. The game WebSocket sends minimal data (`source`, `ui` only) when not in a dungeon, which can cause `data.stats` or `data.identification` to be undefined.

### 1. In `pollStats` – safe depth access

**Replace:**
```javascript
const newDepth = data.stats.depth;
```

**With:**
```javascript
const newDepth = data.stats?.depth ?? 0;
```

### 2. In `pollStats` – guard before depth check

**Replace:**
```javascript
// -> Check if Depth Changed
const newDepth = data.stats.depth;
if (newDepth !== runtime.globalVars.LastDepth) {
```

**With:**
```javascript
// -> Check if Depth Changed (stats may be missing when game is on title screen)
const newDepth = data.stats?.depth ?? 0;
if (newDepth !== runtime.globalVars.LastDepth) {
```

### 3. In `updateItemVisuals` – safe identification access

**Replace:**
```javascript
if (!potAnchor || !data.identification) return;

const potionsList = data.identification.potions;
const scrollsList = data.identification.scrolls || [];
```

**With:**
```javascript
if (!potAnchor || !data.identification) return;

const potionsList = data.identification.potions ?? [];
const scrollsList = data.identification.scrolls ?? [];
```

### 4. In `updateItemVisuals` – MODE B: match by `class_name` instead of index

The game sends potions/scrolls sorted by `true_name`; the companion app creates them sorted by `rune_name`. In MODE B (update), matching by index causes the wrong bottle/scroll to receive the wrong data. **Match by `class_name` instead.**

**Replace the MODE B potions block:**
```javascript
        // Update Potions
        for (let i = 0; i < existingPots.length; i++) {
            if (i >= potionsList.length) break;
            const bottle = existingPots[i];
            const myData = potionsList[i];

            bottle.setAnimation(myData.rune_name);
            bottle.instVars.ClassName = myData.class_name;

            if (bottle.getChildCount() > 0) {
                const icon = bottle.getChildAt(0);
                if (icon) {
                    icon.isVisible = myData.is_known;
                    if (myData.is_known) icon.setAnimation(myData.class_name);
                }
            }
        }
```

**With:**
```javascript
        // Update Potions (match by class_name; server sorts by true_name, we created by rune_name)
        for (const bottle of existingPots) {
            const className = bottle.instVars.ClassName;
            const myData = potionsList.find(p => p.class_name === className);
            if (!myData) continue;
            bottle.setAnimation(myData.rune_name);
            bottle.instVars.ClassName = myData.class_name;
            if (bottle.getChildCount() > 0) {
                const icon = bottle.getChildAt(0);
                if (icon) {
                    icon.isVisible = myData.is_known;
                    if (myData.is_known) icon.setAnimation(myData.class_name);
                }
            }
        }
```

**Replace the MODE B scrolls block:**
```javascript
        // Update Scrolls
        for (let i = 0; i < existingScrolls.length; i++) {
            if (i >= scrollsList.length) break;
            const scroll = existingScrolls[i];
            const myData = scrollsList[i];
            // ... same pattern
        }
```

**With:**
```javascript
        // Update Scrolls (match by class_name)
        for (const scroll of existingScrolls) {
            const className = scroll.instVars.ClassName;
            const myData = scrollsList.find(s => s.class_name === className);
            if (!myData) continue;
            scroll.setAnimation(myData.rune_name);
            scroll.instVars.ClassName = myData.class_name;
            if (scroll.getChildCount() > 0) {
                const icon = scroll.getChildAt(0);
                if (icon) {
                    icon.isVisible = myData.is_known;
                    if (myData.is_known) icon.setAnimation(myData.class_name);
                }
            }
        }
```

## No Other Changes Needed

- **URL:** `http://localhost:5000/game_summary.json` — unchanged
- **OBS WebSocket:** `ws://localhost:4455` — unchanged (connects to OBS, not the server)
- **Data structure:** `stats`, `identification.potions`, `identification.scrolls` with `class_name`, `rune_name`, `is_known` — same as before

## Summary of Edits

| Location | Change |
|----------|--------|
| `pollStats` | `data.stats.depth` → `data.stats?.depth ?? 0` |
| `updateItemVisuals` | `data.identification.potions` → `data.identification.potions ?? []` |
| `updateItemVisuals` | `data.identification.scrolls \|\| []` → `data.identification.scrolls ?? []` |
| `updateItemVisuals` MODE B | Match potions/scrolls by `class_name` instead of index |
