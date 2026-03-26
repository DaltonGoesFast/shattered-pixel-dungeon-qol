# Points / chat command ideas (backlog)

Scratch pad for possible future commands—no implementation plan yet.

## Temporary curse

Curse command, but the viewer chooses which slot. Effect lasts a **configurable number of turns**, then the item is cleansed and the original glyph or enchantment is restored.

## Temporary upgrade

Upgrade equipment for a **configurable number of turns** (then revert or similar).

## Snack

Triggers a Horn of Plenty–style snack for the hero.

## Drop bag

Similar to dying with an **unblessed ankh**: prompt to choose **2 items to keep**, then the rest of the backpack is moved somewhere on the level. The bag is presented in a way comparable to how a **disarm trap** shows the dropped weapon (visual / UX parallel).

## Disarm

Applies the **disarm trap** effect directly on the player (weapon knocked to the floor as that trap does).

---

## More ideas (brainstorm)

Ideas below are **not** committed—just possibilities that fit the same “viewer spends points → timed or one-shot effect” vibe.

- **Temporary identify** — Reveal full stats / true name of one chosen inventory item for **N turns**, then hide again (or show “?”) for chaos without a permanent scroll.
- **Inventory shuffle** — Randomly reorder backpack slots (no items lost). Cheap “annoy the runner” option; could exclude equipped / quickslot.
- **Swap two slots** — Viewer names two indices (or “random two”); those backpack cells swap contents.
- **Floor pull / loot magnet** — For **N turns** or once: pull loose heap items on the current level toward the hero (or into adjacent cells), with sane caps so it cannot vacuum the whole dungeon.
- **Mind vision pulse** — Short **mapping / mob sense** burst (like a potion or class perk), then back to normal fog—good for clutch info without a full mapping scroll.
- **Forced short rest** — Hero **waits** one or several turns (vulnerable, no regen benefit unless you add one). Simple timing sabotage or “breather” for narrative.
- **Soak / oil / fire** — Apply **wet**, **oiled**, or a brief **burning** tick without a trap tile—pairs well with gas and trap commands.
- **Phantom encumbrance** — Fake **overweight** or **slow** for **N turns** without changing real inventory (mirror of temp upgrade).
- **Decoy / noise** — Spawn a short-lived decoy, or force the hero to **make noise** (wake nearby mobs) once—lighter than a full spawn.
- **Equipment “jam”** — Random equipped slot **cannot be unequipped or swapped** for **N turns** (opposite of disarm in feel).
- **Blessing token** — Tiny temporary buff: e.g. one free **revive** charge on ankh, or next hit **guarded**—high cost, clear rules to avoid ruining runs.
- **Sacrifice / tithe** — Destroy or **drop** the **lowest-value** stackable from bag (gold, food, etc.) for a small boon—viewer chooses category if you want interaction.

---

## Voting system (outline)

Design sketch for **chat-driven votes** on run setup and progression: hero, challenges, subclass, talents, etc. No fixed implementation—this is how it *could* work alongside the existing **points + file bridge** (Python overlay server, Streamer.bot, Java game reading commands).

### Goals

- Let viewers collectively steer **high-impact choices** the runner would otherwise make alone.
- Keep **rules obvious** on stream (what is being voted on, time left, current tallies).
- Avoid **spam and brigading** where possible (one person ≠ entire outcome unless you want that).
- Fit **moments** in a run: pre-run votes, votes at level-ups, votes when entering a new region, optional “challenge of the floor.”

### What chat could vote on (categories)

| Category | When | Notes |
|----------|------|--------|
| **Hero class** | New run, or rare “reroll” if you allow it | Small fixed set (Warrior, Mage, Rogue, Huntress, Duelist). |
| **Subclass** | On subclass choice screen / first opportunity | Depends on mod hook: must align with when the game actually offers the choice. |
| **Talents** | Each talent point gained | Vote **tier + branch** or **specific talent ID**; may need simplified menus (e.g. “+1 to row 2 left branch”). |
| **Challenges** | Start of run or per chapter | On/off toggles or pick **N** from a list (some challenges conflict—in outline, enforce **valid combinations** server-side or in mod). |
| **Cosmetic / low stakes** | Anytime | Sprite tint, pet name, “title for the run,” UI banner text—good test bed before subclass votes. |
| **Points-adjacent** | Optional | “Spend 10 pts to add a vote” vs free democracy; or **weighted votes** (1 point = 1 vote weight). |

### High-level architecture (options)

1. **Streamer.bot + files (matches your stack)**  
   - Streamer.bot opens a **poll window**: commands like `!vote warrior` or `!vote A` / `!vote 2`.  
   - A small **Python tally script** or extension to `server.py` appends votes to `votes.jsonl` or merges into `vote_state.json` (option → count, voter ids for dedupe).  
   - **Overlay** (existing HTML) shows a live bar chart or countdown.  
   - When the window closes, write **`vote_winner.txt`** or a structured JSON the **Java mod** reads on next safe moment (title screen, subclass window, level-up).

2. **Game-native UI**  
   - Mod shows an in-game window listing options; chat still votes via bridge; game applies winner when tally file updates. Best **feel**, more Java work.

3. **External poll (Strawpoll, Twitch native poll)**  
   - Streamer copies result into game manually or via bot paste—low engineering, higher friction and less automatable.

### Vote session lifecycle

1. **Streamer starts vote** (hotkey, `!startvote hero`, or dashboard button) → sets **topic**, **options**, **duration** (e.g. 60s), optional **quorum** (min votes).  
2. **Chat votes** during window; server **dedupes** (one vote per user per topic, or “last vote counts”).  
3. **Close** → compute winner → **broadcast** to overlay + chat + file for game.  
4. **Apply** — Runner confirms in-game or mod **auto-applies** if state matches (e.g. hero select screen open).

### Rules worth deciding early

- **Plurality vs ranked** — Plurality is simplest; ranked (IRV) helps crowded options.  
- **Ties** — Random tiebreak, streamer override, or extend timer 30s.  
- **Eligibility** — All chat vs only viewers with **≥1 point** vs **weighted by points balance**.  
- **Spam** — Ignore unknown options; rate-limit messages; cap options to **4–6** per poll for readability.  
- **Sync with game** — Votes for talents are useless if the hero levels while the poll is open; either **pause** (hard) or **snapshot** (“next talent point goes to winning choice” queue).

### Subclass and talent specifics

- **Subclass:** Binary or ternary per class—easy to map to `!vote 1` / `!vote 2`. Need a **stable ID** per subclass in the mod for the apply step.  
- **Talents:** Game trees are dense; for stream clarity, offer **aggregated choices**: e.g. “Offense / Defense / Utility” bucket that maps to the next point in that bucket, or vote on **row** first then **column** in a second poll.  
- **Invalid votes** — If chat picks a talent that’s **locked** or **maxed**, fall back to runner pick or re-run mini-poll.

### Challenges

- Present as **checkbox poll** (“Vote YES on Hostile Champions”) or **pick one theme** (“Champion run vs Pharmacophobia run”).  
- **Validator** in Python or Java rejects incompatible sets and returns a **visible error** on overlay (“those two challenges can’t combine—revote”).

### Integration with existing points system

- **Free votes** — Engagement only; separate from `viewer_points.txt`.  
- **Pay to vote** — Deduct points per vote; richer users swing harder (document clearly).  
- **Hybrid** — Free base vote + optional **boost** with points.  

All of this can still **log** to the same overlay WebSocket for dashboards.

### UX checklist for stream

- Overlay: **question + time + live counts**.  
- Chat: single line **instructions** when vote opens (`!vote 1-4`).  
- Post-close: **announced winner** in chat and on overlay until applied.  
- Failure modes: **no quorum** → runner decides; **game not ready** → queue winner until valid screen.

---

## Viewer-facing dashboard (outline)

**Yes, it’s possible.** Your Flask app in `Lastest UI/server.py` already serves pages and JSON (`/api/game-data`, `game_summary.json`, `GET /api/viewer-points`, etc.) while the game pushes live state over the game WebSocket. A **separate route** (e.g. `/viewer` or `/dashboard`) could be a static HTML/JS page that polls or uses **Server-Sent Events / WebSocket** for live updates—same data the overlay uses, presented for chat instead of OBS.

### What viewers could see

| Feature | Feasibility | Notes |
|--------|---------------|--------|
| **Own points** | Straightforward | `GET /api/viewer-points` already returns balances; page filters by **username**. |
| **Side** (e.g. `!switch` teams) | Straightforward if it’s in game JSON | If the mod includes side in `game_summary` / WebSocket payload, the dashboard just displays it. If only in a file like `assigned_role.txt`, expose a **read-only** `GET` or merge into `game_summary`. |
| **Inventory** | Straightforward if exposed | Hero inventory is likely already in the live game snapshot used by `/api/game-data`—trim for a **read-only** list (icons optional: item IDs + names). |
| **Voting** | New backend + UI | Same tally logic as the voting outline: `POST /api/vote` with `{ user, choice }`, dedupe, optional point cost; dashboard buttons instead of `!vote 2`. |

### Identity and trust (“who is this viewer?”)

- **Soft login:** User types **Twitch/YouTube username** → show that row from viewer-points. **Anyone can impersonate** unless you add checks—fine for “fun stats,” bad for spending points from the site.  
- **OAuth (Twitch / Google for YouTube):** Server runs the normal **authorization-code** flow (login → provider → callback on your site → session cookie). You map the returned **stable ID** (`login` / channel id) to rows in `viewer_points` the same way chat does.  
- **Hybrid:** Dashboard is **read-only** (points, side, inventory); **spending** stays in chat / Streamer.bot only until OAuth exists.

### OAuth: difficulty and security

**Difficulty — moderate, not exotic.** Expect roughly:

- Register an app in **Twitch Developer Console** and (for YouTube chat identity) **Google Cloud Console**; note client id/secret, set **redirect URIs** to your real URLs (including **tunnel hostname** if you use Cloudflare/ngrok).  
- In Flask: 2–3 routes (redirect to provider, callback, logout), a small library (**Authlib** is common) or hand-rolled HTTPS calls; store **session** server-side or a **signed cookie** with user id only—avoid putting long-lived provider tokens in the browser if you don’t need them.  
- **Twitch:** straightforward “Get Users” → canonical `login` for your points file.  
- **YouTube:** people usually use **Google sign-in**; map **Google account ↔ YouTube channel** only if you need channel name; scopes should stay **minimal** (e.g. read-only profile / channel id)—you do **not** need access to their videos or Gmail for a dashboard.

**Security — generally lowers risk vs “type any name,” if you avoid classic mistakes:**

| Concern | Mitigation |
|--------|------------|
| **CSRF on login** | Use `state` (and **PKCE** for public clients) on the OAuth redirect. |
| **Secret leakage** | Keep **client secret** only on the server; never embed in static JS. |
| **Session hijack** | **HTTPS only** (tunnels give this); `HttpOnly` + `Secure` cookies; reasonable session lifetime. |
| **Token storage** | Prefer **session id** in cookie over storing refresh tokens unless you need offline API calls. |
| **Scope creep** | Request the **smallest** scopes; users trust “identify me” more than broad Google access. |
| **Open redirect / callback** | Validate `redirect_uri` against an allowlist. |

**Net:** Requiring login is **standard practice** for “this action is really that viewer” and is **not inherently unsafe**—risk is proportional to **bugs in your implementation** and **how much power** the dashboard has (read-only stats vs spending points). Start with **narrow scopes** and **read-only** features until the flow is stable.

### Security and ops

- **Do not** expose `POST` admin endpoints (bulk set, prune) without auth; keep the viewer page on **read + vote-only** routes.  
- If the site is reachable on a **public tunnel URL**, treat it as **semi-public**; rate-limit votes and `GET` by IP if needed.  
- **CORS** is already a consideration for APIs; a same-origin `/viewer` page avoids most pain.

### Hosting without port forwarding (CGNAT / ISP blocks)

You **do not** need inbound port forwarding on your router. Keep Flask (and the game WebSocket to your PC) as they are; use a tunnel that only needs **outbound** connections from your stream PC.

| Approach | Idea |
|----------|------|
| **Cloudflare Tunnel** (`cloudflared`) | Free tier; you run a small agent on the PC; it opens a secure tunnel to Cloudflare; viewers use `https://something.yourdomain` or a Cloudflare-assigned host. **No open ports** on your home IP. |
| **ngrok** (or similar) | Same pattern: local `http://127.0.0.1:5000` → public `https://….ngrok.io`. Free URLs often rotate unless paid; fine for testing. |
| **Tailscale Funnel** | If you already use Tailscale, Funnel can expose **one** local service to the internet through Tailscale’s edge (policy/availability depends on plan). |
| **VPS in the cloud** | Run the web app on Fly.io, Railway, etc. **Only** makes sense if the **game** can reach that host (e.g. game opens an **outbound** WebSocket to the VPS). Today your game likely talks to **localhost**; moving “the website” to a VPS without changing the game means viewers still need a path to **your** data—usually you still tunnel **to the PC** or sync state to the cloud. |

**Practical default:** run **`cloudflared`** (or ngrok) on the stream machine, point it at whatever port `server.py` uses, and share that **HTTPS** link in chat or panels.

**Caveats:** Any **public** URL can be probed—lock down or remove dangerous `POST` routes for the viewer site, use **OAuth** before letting the site spend points, and set OAuth **redirect URIs** to the tunnel hostname if you add login later.

### Summary

A viewer dashboard is mostly **UI + small read APIs** on top of data you already aggregate; **voting** adds state and validation; **trusted identity** is the main fork between “display only” and “dashboard replaces chat for actions.”
