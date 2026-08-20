# Chat Command Economy v1

**Status:** **Implemented** (server-side — July 2026). Streamer.bot Stream Offline wiring is manual; see [economy-v11-apply.md](economy-v11-apply.md).

**Related:** [streaming-system-rework-plan.md](streaming-system-rework-plan.md), [COMMANDS.md](../COMMANDS.md), [archive/streamerbot-points-from-scratch.md](archive/streamerbot-points-from-scratch.md), [fard-system.md](fard-system.md), [summon-march-system.md](summon-march-system.md), [streaming-setup-guide.md](streaming-setup-guide.md)

---

## Problem

Viewers earn chat points across multiple stream nights and dump large balances on a single run (e.g. ~5,000 pts from Mon–Thu lurking → dozens of `!transmute` / `!heal` on Friday). `!doublepoints` is often left on for whole streams, so 2× feels like baseline rather than a bonus.

**Root cause:** chat points persist across streams with no earn ceiling.

**Not the problem:** the live cost table. Under a ~1 pt/min lurker baseline, mob costs already form a sensible 1–16 minute ladder. v1 fixes **stockpiling**, not pricing.

---

## Design principles

1. **Chat points are per-stream fuel.** Earned by engagement; capped; spent or converted; then reset.
2. **Donor points are the long-term wallet.** From donations and banking; never capped, decayed, or wiped.
3. **Costs stay on live JSON** unless a future rebalance explicitly changes them.
4. **`!fard` is the intended global 2× moment** — not always-on `!doublepoints`.
5. **Banking is a loyalty carrot** — strictly worse than donating; rewards repeat viewers without rivaling Super Chats.
6. **Donor wallets are uncapped and can be OP** — paying always beats free grind; chat cap bounds only the free path.
7. **Active chatters cap in ~90 minutes** — lurker earn rate is secondary.
8. **All spend commands use chat + donor** — no donor-only gates; no per-viewer spend caps.
9. **`!points` always shows chat and donor separately** — never a single opaque balance.

---

## v1.1 tuning (approved)

Locked decisions for implementation:

| Parameter | v1 (superseded) | **v1.1** |
|-----------|-----------------|----------|
| Points per message | 1 | **2** |
| Chat cooldown | 30 s | **20 s** |
| Passive tick | 1 / 60 s | **unchanged** |
| Chat point cap | 500 | **500** |
| `bank_ratio_manual` | 10% | **10%** |
| `bank_ratio_auto` (regular) | 5% | **5%** |
| `bank_ratio_auto_member` | 10% | **10%** |
| Donation multiplier cap | 2× | **4×** |
| Donor wallet cap | none | **none (always uncapped)** |

**Design targets:**
- **Chatter** (2 pt/msg, 20s CD, no multipliers): **6 pts/min** → **500 cap in ~83 min** (≤ 1.5 h goal).
- **Lurkers** stay ~1 pt/min passive — do not drive balance.
- **Donors OP:** uncapped donor balance; donations beat max grind+bank; **4× donation cap** allows member + fard SC without full 8× chat stack.

**Config keys (Phase 6):** `points_per_message = 2`, `chat_cooldown_sec = 20` in `points_config.json` (server earn module after Phase 4 gateway).

---

## Two-bucket model

Stored in `viewer_points.txt` per user:

`username|pts|lastEarnUnix|donation_pts|role`

| Field | Meaning (v1) |
|-------|----------------|
| `pts` | **Total spendable** = chat pts + donor pts |
| `donation_pts` | **Donor subset** — persisted forever |
| Chat pts (derived) | `max(0, pts - donation_pts)` — capped, per-stream |

### Bucket rules

| Bucket | Sources | Cap | Persists across streams | Deduction order |
|--------|---------|-----|-------------------------|-----------------|
| **Chat** | Messages, passive, first-word bonus | **500 / stream** | **No** | Spent **first** |
| **Donor** | Super Chat, bits, gift subs/memberships, `!bank` | None | **Yes** | Spent after chat exhausted |

**Donor points are sacred:** never capped, never decayed, never removed by reset.

---

## Earning

### Chat earn (server earn module — post Phase 4 gateway)

v1.1 constants are **live** (Phase 6 complete). Plumbing used 1 pt/msg, 30s CD until July 2026 cutover.

| Source | Rate (v1.1) | Notes |
|--------|-------------|-------|
| Chat message | **+2** | **20 s** cooldown per user |
| Passive tick | +1 | Every 60 s; shares `lastEarn` with chat |
| First Words | +5 | Once per user per stream; server `session_state.json` |

Earn writes apply **only to the chat portion** of the ledger. Donor pts are not reduced when chat is awarded.

### Chat earn multipliers (multiplicative)

Applied at earn time for **chat, passive, and first-word** only:

```
effective = base × (global2x ? 2 : 1) × (heatLeader ? 2 : 1) × (subOrMember ? 2 : 1)
```

| Multiplier | Source | Scope |
|------------|--------|-------|
| Global 2× | `!fard` extensions, or streamer `!doublepoints` | Everyone |
| Heat leader 2× | Bestiary rolling **15 min** summon-XP leader (`!heat`; `is_top_summoner()`) | Personal |
| Sub / member 2× | Twitch sub, YouTube member | Personal |

**Deprecated:** Session-long `!summon` **count** as the personal 2× source. See [bestiary-summon-system.md](bestiary-summon-system.md).

**Maximum on chat earn:** 8× (all three active).

### Donation earn multipliers (4× cap)

Applied to **Super Chat, cheer, and gift sub/membership** awards. Donations use the same bonus sources but **never stack above 4× total** (v1.1 — raised from 2× so donors can be OP during hype, without full 8× chat stack):

```
donation_multiplier = min(4, (global2x ? 2 : 1) × (heatLeader ? 2 : 1) × (subOrMember ? 2 : 1))
```

| Scenario | Chat earn | Donation earn |
|----------|-----------|---------------|
| Member, no fard | 2× | 2× |
| Regular, fard active | 2× | 2× |
| Member + fard | 4× on chat | **4×** (at cap) |
| Member + fard + heat leader | 8× on chat | **4×** (capped) |

**Example:** $1 Super Chat (100 pts base) during `!fard` as a member → **400 donor pts**, not 800.

**Donor wallet:** uncapped forever. Large saved balances are intentional — paying beats grinding.

Donations credit **donor pts directly** (and increase total `pts`). Not subject to the 500 chat cap.

| Source | Base rate |
|--------|-----------|
| Super Chat | ~1 pt per $0.01 USD (Frankfurter FX + fallbacks) |
| Cheer | 1 pt per bit |
| Gift sub / membership | Tier defaults from config (500 / 1000 / 2500) |

### Global 2× policy (v1)

- **`!fard`** is the intended global 2× for viewers: once per user per stream → **+3 min** global 2× (**+6 min** for subs/members). See [fard-system.md](fard-system.md).
- **`!doublepoints`** remains streamer-only for emergencies or special events. **Do not leave it on for whole streams** — that defeats the fard design and inflates earn toward the cap.
- Chat cap absorbs multiplier stacking on chat earn; retiring the always-on `!doublepoints` habit is an **operational** change, not a code requirement.

### Member / sub perks (summary)

| Perk | Regular | Sub / member |
|------|---------|--------------|
| Chat earn multipliers | Up to 8× | Up to 8× |
| `!fard` extension | +3 min global 2× | +6 min global 2× |
| Donation multiplier | Up to **4× cap** | Up to **4× cap** (member + fard reaches cap) |
| Manual `!bank` | 10% | 10% |
| Auto-bank on stream reset | **5%** | **10%** (same as manual) |

---

## Chat cap (500 / stream)

| Parameter | Value |
|-----------|-------|
| `chat_point_cap` | **500** |

- Cap applies to **chat pts only** (`pts - donation_pts`).
- When an award would exceed cap: **silently drop** the excess (no chat pts above 500 from earn).
- **Every time** a capped earn is attempted (viewer already at 500 chat), send a chat nudge prompting `!bank`:

  > @viewer — Chat cap reached (500/500)! Use `!bank` to save points permanently, or spend them on a command.

  Repeat on each capped earn attempt — not once per stream. After banking or spending chat down, earning can resume until cap again.

- **No auto-bank on overflow** (including during `!fard`). Fard helps reach cap faster; it does not bypass it.

### Cap math (v1.1)

| Profile | pts/min | Time to 500 cap |
|---------|---------|-----------------|
| Lurker (passive only) | 1 | ~8.3 h (secondary) |
| **Chatter (2/msg, 20s)** | **6** | **~83 min** ✓ |
| Chatter + member | 12 | ~42 min |
| Chatter + member + fard | 24 | ~21 min |
| Chatter + max 8× | 48 | ~10 min |

Active chatters hit cap within the **1.5 h** target. After cap: spend, `!bank`, or use **donor wallet**.

---

## `!bank` command (new)

Viewers convert chat pts → donor pts at a **manual haircut**.

| Parameter | Value |
|-----------|-------|
| `bank_ratio_manual` | **10%** (0.10) |

### Forms

| Command | Behavior |
|---------|----------|
| `!bank` | Preview only — no conversion |
| `!bank all` | Convert all current chat pts |
| `!bank <amount>` | Convert exactly `<amount>` chat pts (integer ≥ 1) |

### Rules

- Only **chat pts** are convertible (`pts - donation_pts`).
- Conversion: `donor_gain = floor(chat_amount × bank_ratio_manual)`; chat reduced by `chat_amount`; `donation_pts` and total `pts` increase by `donor_gain`.
- Available **any time during a live stream** (while spend is enabled or disabled — banking is not spending).
- **No reverse** (`donor → chat`) in v1.

### Example responses

**Preview (`!bank`):**
> @viewer — Bankable: 320 chat pts → 32 donor pts (10%). Use `!bank all` or `!bank 200`.

**Success (`!bank all` with 400 chat):**
> @viewer — Banked 400 chat → 40 donor pts. Donor balance: 180 (saved forever).

### Economics (why 10%)

| Path | ~Donor pts |
|------|------------|
| Max chat cap banked manually | 500 → **50** |
| $1 Super Chat | **~100** |
| 4 stream nights, perfect manual banks | **~200** donor |

Donations stay ~2× better than max grind+bank. Banking rewards loyalty without replacing real support.

**Implicit per-stream ceiling:** 500 chat × 10% = **50 donor pts max** from manual banking per stream.

See [Bank rate analysis](#bank-rate-analysis) for 10% / 25% / 33% / 50% comparisons and the viewer-perception tradeoff.

---

## Stream reset (chat wipe + auto-bank)

Chat pts reset at **end of stream**. Donor pts are untouched.

| Parameter | Regular | Sub / member |
|-----------|---------|--------------|
| `bank_ratio_auto` | **5%** (0.05) | **10%** (0.10) |
| `reset_debounce_hours` | **4** | **4** |

Members auto-bank at the **same rate as manual `!bank`** — they do not lose the banking bonus by forgetting `!bank` at end of stream. Regular viewers still benefit from typing `!bank` (10% vs 5% auto).

### Triggers (both)

1. **Automatic:** Streamer.bot **Stream Offline**, after **≥ 4 h** debounce (avoids accidental reset on brief disconnects).
2. **Manual:** Stream Deck / hotkey — “End stream — reset chat pts” for a hype **“bank now!”** moment before reset fires.

### Reset sequence

For each user with `chat_pts > 0`:

1. `auto_donor = floor(chat_pts × bank_ratio_auto)` — use **10%** for subs/members, **5%** for everyone else
2. `donation_pts += auto_donor`; `pts` adjusted accordingly
3. `chat_pts = 0` → set `pts = donation_pts`

Then broadcast (optional chat / OBS):

> Stream ended — unbanked chat points converted at 5% (10% for members). Donor balances saved. See you next stream!

### Incentive structure

| Viewer | Behavior | 400 unbanked chat at reset |
|--------|----------|----------------------------|
| Regular | `!bank all` during stream (10%) | **+40 donor** before reset |
| Regular | No banking; auto on reset (5%) | **+20 donor** |
| Member | `!bank all` during stream (10%) | **+40 donor** before reset |
| Member | No banking; auto on reset (10%) | **+40 donor** |
| Anyone | Banked all; 0 chat left at reset | **+0 auto** |

Regular viewers earn **2×** the passive rate by banking manually. Members get full manual rate automatically on reset.

---

## Spending

- All existing spend commands unchanged in cost shape; values read from **live** `points_config.json`.
- **Any viewer** can use **any** spend command — paid for from chat pts first, then donor pts. **No donor-only commands.**
- **Deduction:** chat pts first, then donor pts (`deduct_points` behavior preserved).
- **No per-viewer spend cap** — only the 500 **chat earn** cap limits free grinding per stream; donor spend is uncapped.
- **Global kill switch:** `spend_disabled.txt` (Stream Deck OFF/ON) — unchanged.
- **Free windows:** `free_until.json` — unchanged.
- **v1:** no diminishing returns on repeated commands.

### Cost reference (current live JSON)

Flat costs:

| Command | Cost |
|---------|------|
| `!gas`, `!bomb`, `!buff`, `!wand`, `!hex`, `!degrade`, `!sabotage` | 75 |
| `!scroll`, `!row`, `!transmute` | 100 / 100 / 150 |
| `!trap`, `!debuff` | 50 |
| `!bee`, `!corruptally` | 40 |
| `!ward` | 9 |
| `!heal`, `!cleanse` | 25 |
| `!dew` | 5 |
| `!plant` | 30 |
| `!curse` (base) | 100 × 2^known cursed equipped items |
| `!gold N` | N × 5 (N clamped 1–100) |

Formula commands: `!spawn`, `!champion` — zone-adjusted per `points_command.py` (unchanged).

Full mob table: [COMMANDS.md](../COMMANDS.md).

---

## Viewer commands (v1)

| Command | Cost | Description |
|---------|------|-------------|
| `!points` | Free | **Chat pts**, **donor pts**, and **Bestiary** sprint/heat XP (see below) |
| `!bank` | Free | Preview bank conversion |
| `!bank all` | Free | Convert all chat pts at 10% |
| `!bank <n>` | Free | Convert n chat pts at 10% |
| `!toppoints` | Free | Top holders by **donor pts** (all-time) |
| `!givepoints` | Free | Transfer to another viewer (chat first, then donor) |
| *(all spend commands)* | Per JSON | Unchanged |

### `!points` display (v1.1 + Bestiary)

Always show **chat and donor separately** — never a single combined number without breakdown. Also append Bestiary XP:

```
@viewer - Chat points: 120/500 | Donor points: 340 | Bestiary: sprint 8 XP, heat 14 XP
```

| Field | Meaning |
|-------|---------|
| **Chat points** | `pts − donation_pts`, capped at 500 this stream; resets on stream end |
| **Donor points** | `donation_pts`; permanent, uncapped |
| **Sprint XP** | Bestiary XP this level (resets on level-up) |
| **Heat XP** | Bestiary XP in the last 15 minutes |

Optional: `Total spendable now: 460` (chat + donor). The **chat** and **donor** lines are required; total alone is not sufficient.

### Viewer-facing one-liner (panel / !commands)

> Earn up to **500 chat points per stream** (active chatters ~**83 min**). **`!bank`** saves **10%** into donor points permanently. Donations go straight to donor points (up to **4×** during bonuses). Chat resets when the stream ends; **donor points never expire or cap**. **Members:** auto-bank on reset at **10%** (same as `!bank`).

---

## Streamer operations

| Control | Purpose |
|---------|---------|
| `!doublepoints <min>` | **Rare** manual global 2× (max 120 min). Not a default. |
| `!fard` | Viewer-driven global 2× extension — primary hype lever |
| Spend toggle (Stream Deck Action Switch) | Disable/enable spend commands mid-run (`R8 - Spend Toggle`) |
| Kesha / Seed (Set Command State) | Disable/enable `!kesha` and/or `!seed` Command entries in Streamer.bot |
| Manual chat reset | End-of-stream “bank now!” before auto reset |
| Points config UI (`/points-config`) | Tune costs; v1 adds cap/bank ratio fields |

### Stream start checklist

- [ ] `!doublepoints` timer **off** (or expired)
- [ ] Spend **ON** (unless testing)
- [ ] Summon march reset (existing Action 22) — session leader only; unrelated to chat cap

---

## Config keys (new in v1)

Add to `points_config.json` when implemented:

| Key | Default | Description |
|-----|---------|-------------|
| `chat_point_cap` | `500` | Max chat pts per user per stream |
| `bank_ratio_manual` | `0.10` | `!bank` conversion rate (all viewers) |
| `bank_ratio_auto` | `0.05` | Auto-bank on stream reset (regular viewers) |
| `bank_ratio_auto_member` | `0.10` | Auto-bank on stream reset (subs / YouTube members) |
| `donation_multiplier_cap` | `4` | Max multiplier on Super Chat, cheer, gift sub/membership (v1.1) |
| `points_per_message` | `2` | Streamer.bot Action 01 — chat earn base (v1.1) |
| `chat_cooldown_sec` | `20` | Streamer.bot Action 01 — per-user message cooldown (v1.1) |
| `reset_debounce_hours` | `4` | Hours after Stream Offline before auto reset |

Mirror defaults in `points_command.py` `load_config()` and `points-config.html` `value=` fallbacks.

---

## Migration (one-time, existing balances)

For each user in `viewer_points.txt`:

1. `chat_pts = max(0, pts - donation_pts)`
2. If `chat_pts > chat_point_cap`: migrate `floor(chat_pts × bank_ratio_manual)` into `donation_pts`; zero excess chat
3. If `chat_pts ≤ cap`: optionally leave as-is until next reset, **or** force-reset chat to 0 on go-live (streamer choice)

**Recommended:** migrate excess above 500 at **10%** into donor (generous, matches `!bank`). Announce in stream + panel before go-live.

---

## Explicitly not in v1.1

- Friday-only spend gate
- **Donor-only commands** — all commands accept chat + donor; never donor-exclusive (now or planned v2)
- **Per-viewer spend caps** — only chat **earn** cap (500); no limit on how much a viewer can spend if they have points
- Diminishing returns on repeated commands
- Global cost inflation
- Point decay / age-of-earnings
- Sub-scaled chat cap (500 flat for everyone)
- Reverse bank (donor → chat)
- Repricing the live cost table
- Donation multipliers above 4× (chat earn may still reach 8×)

---

## Implementation surface (when approved)

**Prerequisite:** Complete Phases 0–5 of [streaming-system-rework-plan.md](streaming-system-rework-plan.md) (HTTP gateway). After Phase 4, earn/cap/bank/reset logic lives entirely in the server — not Streamer.bot C#.

| Area | Changes |
|------|---------|
| `chat_command.py` | Cap on chat earn + **cap nudge on every blocked earn**; reset/auto-bank helper; flip earn rates to v1.1 |
| `points_command.py` | `cmd_bank`; `donation_earn_multiplier()` **4×** cap; member-aware auto-bank ratio; `!points` shows chat/donor separately |
| `points_config.json` | New cap/ratio/donation-cap keys |
| `points-config.html` | UI for cap/ratios; reset trigger |
| `server.py` | `POST /api/session/end` (debounced stream reset) |
| Streamer.bot (thin) | Stream Offline trigger → `POST /api/session/end` — **only new SB wiring in v1.1** |
| Docs | This file + `COMMANDS.md`, `user-facing-summary.md`, `twitch-panel.md`, `youtube-description.md`, `archive/streamerbot-points-from-scratch.md` |

**Phase 6 checklist:** See [streaming-system-rework-plan.md § Phase 6](streaming-system-rework-plan.md#phase-6--economy-v11-server-only-after-plumbing-stable).

---

## Doc sync checklist

On implementation, update together:

- [ ] `docs/Chat Command Economy v1.md` (this file) — mark **Implemented**
- [ ] `COMMANDS.md` — `!bank`, cap/reset wording, donation 2× cap, member auto-bank
- [ ] `docs/user-facing-summary.md`
- [ ] `docs/twitch-panel.md`
- [ ] `docs/youtube-description.md`
- [ ] `docs/archive/streamerbot-points-from-scratch.md` — 9-action model, server earn module, cap, 2 pt/20s, 4× donations, `!bank`, reset
- [ ] `points_config.json` + Python defaults + HTML `value=` — keep identical

---

## Economy analysis

Work-backwards sanity check using **live `points_config.json` costs**, **v1.1 earn rules** (2 pt/msg, 20s CD, 500 chat cap, 4× donation cap), and **native-depth** spawns. Generated July 2026.

**Anchor concept:** lurker baseline still ~1 pt/min for mob ladder; **chatters** at 6 pts/min hit cap in **~83 min**. v1.1 speeds free earn; **500 cap** still bounds per-stream chat chaos.

### Earn rate profiles (v1.1)

Passive and chat **share `lastEarn`** — active chatters earn primarily from messages; lurkers earn from passive only. First Words (+5) omitted below.

| Profile | pts/min | Basis |
|---------|---------|-------|
| Lurker | 1 | Passive +1 / 60s |
| Lurker + member | 2 | Passive × sub/member 2× |
| **Chatter** | **6** | **2 pt / 20s** |
| Chatter + member | 12 | × member 2× |
| Chatter + member + fard | 24 | × global 2× |
| Chatter + max stack (8×) | 48 | fard + heat leader + member |

### Time to earn (minutes, native depth, v1.1)

| Command | Cost | Lurker | Chatter | Chatter + member | Chatter + member + fard | Max 8× |
|---------|------|--------|---------|------------------|-------------------------|--------|
| `!dew` / `!spawn rat` / `!gold 1` | 5 | 5m | 1m | <1m | <1m | <1m |
| `!ward` | 9 | 9m | 2m | 1m | <1m | <1m |
| `!heal` / `!cleanse` | 25 | 25m | 4m | 2m | 1m | 1m |
| `!plant` | 30 | 30m | 5m | 3m | 1m | 1m |
| `!bee` / `!corruptally` | 40 | 40m | 7m | 3m | 2m | 1m |
| `!trap` / `!debuff` | 50 | 50m | 8m | 4m | 2m | 1m |
| `!gas` / `!bomb` / `!buff` / `!wand` | 75 | 1.2h | 12m | 6m | 3m | 2m |
| `!spawn eye` | 70 | 1.2h | 12m | 6m | 3m | 1m |
| `!scroll` / `!row` / `!curse` (base) | 100 | 1.7h | 17m | 8m | 4m | 2m |
| `!transmute` | 150 | 2.5h | **25m** | 12m | 6m | 3m |
| `!champion eye` | 140 | 2.3h | 23m | 12m | 6m | 3m |

**Zone adjustments (unchanged):** deeper than native → **½** spawn/champion cost; shallower → up to **+40%**. Example: `!spawn eye` deep = 35 pts (lurker 35m). `!transmute` is not zone-adjusted.

### Max command uses per stream (500 chat cap only)

Bank rate does **not** change these — cap limits **in-stream chat spend**, not cross-stream donor.

| Command | Cost | Max uses / stream |
|---------|------|-------------------|
| `!dew` / `!spawn rat` / `!gold 1` | 5 | 100 |
| `!ward` | 9 | **55** |
| `!heal` / `!cleanse` | 25 | 20 |
| `!plant` | 30 | 16 |
| `!bee` / `!corruptally` | 40 | 12 |
| `!trap` / `!debuff` | 50 | 10 |
| `!gas` / `!bomb` / `!buff` / `!wand` | 75 | 6 |
| `!spawn eye` | 70 | 7 |
| `!scroll` / `!row` / `!curse` (base) | 100 | 5 |
| `!transmute` | 150 | **3** |
| `!champion eye` | 140 | **3** |

**Pre-v1 problem:** ~5,000 chat pts carried in → **33× `!transmute`**. **Post-v1:** even a full 500-cap grind → **3× `!transmute`** from chat alone.

### Time to hit 500 chat cap (v1.1)

| Profile | Time to cap |
|---------|-------------|
| Lurker | 8.3 h (secondary — does not drive balance) |
| **Chatter** | **~83 min** (1.5 h target) |
| Chatter + member | ~42 min |
| Chatter + member + fard | ~21 min |
| Chatter + max 8× | **~10 min** |

After cap: **spend**, **`!bank`**, or **donor wallet** (uncapped).

### Curse ladder (donor dependency at high stacks)

Base `cost_per_curse` = 100. Cost = 100 × 2^known cursed equipped items.

| Known cursed | Cost | Lurker time | Fits in 500 chat cap? |
|--------------|------|-------------|------------------------|
| 0 | 100 | 1.7 h | 5× |
| 1 | 200 | 3.3 h | 2× |
| 2 | 400 | 6.7 h | 1× |
| 3 | 800 | 13.3 h | **0×** — needs saved donor pts |

### One-stream affordance (v1.1, ~2 h stream)

| Viewer type | ~2 h chat earned | Can afford (chat only) |
|-------------|------------------|-------------------------|
| Lurker | ~120 | heals, traps, some gas |
| **Chatter** | **500 (capped)** | **full cap budget** — 3 transmutes OR 5 scrolls OR 20 heals |
| Capped + donor wallet | 500 + saved | chat cap + **uncapped donor** (OP payers) |

### Donation vs bank vs grind (v1.1 @ 10% bank, 4× donation cap)

| Path | Donor pts |
|------|-----------|
| Max `!bank all` (500 chat) | **50** |
| $1 Super Chat @ 4× cap (member + fard) | **400** |
| $1 Super Chat @ 2× | **200** |
| $0.25 Super Chat @ 4× | **100** (= 4 heals) |
| 4 streams, perfect manual bank | **~200** |

| Target | Cost | Grind path (10% bank) | Donate path (v1.1) |
|--------|------|----------------------|---------------------|
| `!heal` | 25 | <1 bank stream | $0.25 SC @ 4× |
| `!transmute` | 150 | **3** bank streams | **$1 SC @ 4×** (one shot) |
| `!champion eye` | 140 | **3** bank streams | **$1 SC @ 4×** |
| 3rd `!curse` | 800 | **16** bank streams | **$2 SC @ 4×** |

**$1 @ 4× = 400 donor** beats **8 perfect bank streams** (400 donor). Donating always beats grinding.

### Donor OP examples (uncapped wallet, 4× cap)

| Donation | Donor pts @ 4× | `!transmute` uses |
|----------|----------------|-------------------|
| $1 | 400 | 2 |
| $5 | 2,000 | 13 |
| $10 | 4,000 | 26 |

Saved donor balances stack forever — whales can walk in with run-breaking power **by design**. Chat cap does not limit donor spend.

### Lurker cross-stream scenario (Mon–Thu, ~120 chat/night, never hits cap)

| Behavior | 10% bank | 4-night donor total |
|----------|----------|---------------------|
| `!bank all` each night | manual 10% | **~48 donor** |
| Never types `!bank` (5% auto) | auto 5% | **~24 donor** |
| Member, never `!bank` (10% auto) | auto 10% | **~48 donor** |

Manageable saved power — not a Friday bomb.

### Bank rate analysis

Viewers often perceive 10% as “losing 90%.” Higher rates feel fairer but change **cross-stream donor growth** and the **donation > grind** rule.

Assumes: 500 chat cap, `!bank all` each stream, auto ratios scale like v1 (auto regular = ½ manual, auto member = manual).

| Manual bank rate | Max donor / stream | vs $1 SC @ 4× (400) | 4 streams banked | 4-stream transmutes |
|------------------|-------------------|---------------------|--------------------|---------------------|
| **10% (v1.1 default)** | 50 | 12.5% of SC | 200 donor | 1 |
| **25% (compromise)** | 125 | 31% of SC | 500 donor | 3 |
| **33%** | 165 | 41% of SC | 660 donor | 4 |
| **50%** | 250 | 62% of SC | 1,000 donor | 6 |

**10% (v1.1 locked):** donations dominate even at 4× cap; slow donor growth; **feels** harsh (“90% lost”) — use messaging fix.

**25%:** better optics; still worse than $1 SC @ 4× (400); 4-night grinder ≈ 3 saved transmutes.

**50%:** still below $1 @ 4× for single stream (250 vs 400), but 4 streams approach donor bomb territory.

**Streams to afford via banking alone (`!transmute` = 150):**

| Bank rate | Streams needed |
|-----------|----------------|
| 10% | 3 |
| 25% | 2 |
| 50% | **1** |

**Auto-bank at scaled rates (400 unbanked chat at reset):**

| Config | Regular auto | Member auto | Regular → donor | Member → donor |
|--------|--------------|-------------|-----------------|----------------|
| 10% v1 | 5% | 10% | 20 | 40 |
| 25% proposal | 12.5% | 25% | 50 | 100 |
| 50% proposal | 25% | 50% | 100 | 200 |

### Messaging tip (10% rate)

Frame for viewers: *“Save **50 of every 500** chat points forever”* — not *“10% conversion.”* Same math, better reception.

**Open tuning item:** `bank_ratio_manual` locked at **10%** for v1.1. **25%** remains documented fallback if chat feedback demands it.

### Watch items (costs OK; cap does the work)

| Item | Risk | v1 stance |
|------|------|-----------|
| `!ward` (9 pts, 55×/stream at cap) | Cheap spam | Monitor; v2 lever: small price bump or diminishing returns |
| `!dew` (5 pts, 100×/stream) | Low impact spam | Probably fine |
| Max 8× cap in ~10 min (v1.1) | Rest of stream is spend/bank/donor | Intended for engaged chatters |
| Donor OP via uncapped wallet | Whales can dominate a run | **Intentional** — paying > grinding |
| Cheap spawns deep (½ cost) | More rats deep in run | Existing design; cap bounds volume |

### Analysis verdict (v1.1)

**Live JSON costs + v1.1 earn/cap/bank rules = sensible economy.**

- **Chatter caps ~83 min** — meets 1.5 h target; lurkers secondary.
- **500 cap** still bounds per-stream **chat** chaos (3 transmutes max from chat).
- **10% bank** + **4× donation cap** — donate beats grind; donors can be OP via uncapped wallet.
- **First knobs if something feels wrong:** `!ward` spam, bank messaging (not rate), or chat cap — not transmute price.

---

## Expected outcomes

| Before v1 | After v1.1 |
|-----------|------------|
| 5,000+ chat pts carried into one stream | ≤ 500 chat pts per stream + **uncapped** donor wallet |
| Always-on 2× normalizes earn | `!fard` moments matter; chatter caps in **~83 min** |
| Member SC during fard stacked 4×+ on donations | Donations capped at **4×**; chat can still hit 8× |
| Lurker stockpile dominates Friday | Chat resets; donor OP only via pay + slow bank |
| Donations ≈ grind | **$1 SC @ 4× (400)** beats **8** max bank streams |
| Members forget `!bank` → 5% only | Members get **10%** auto-bank on reset |
| 1 pt/msg, 30s CD — slow chatter cap | **2 pt/msg, 20s CD** — active chatters cap in 1.5 h |
