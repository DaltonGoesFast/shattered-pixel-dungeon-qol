"""Local buff/debuff name list for search (no game required)."""
from typing import List, Tuple

# Mirrors StreamerBuffResolver.STREAMER_BUFFS / STREAMER_DEBUFFS
BUFFS = [
    "Haste",
    "Adrenaline",
    "Invisibility",
    "Levitation",
    "Barrier",
    "Healing",
    "Recharging",
    "MindVision",
]

DEBUFFS = [
    "Blindness",
    "Weakness",
    "Slow",
    "Cripple",
    "Roots",
    "Daze",
    "Vulnerable",
    "Hex",
    "Degrade",
]


def _normalize(text: str) -> str:
    return "".join(c.lower() for c in (text or "") if c.isalnum())


def search_buffs_local(query: str, limit: int = 15) -> List[Tuple[str, str, str]]:
    """Returns [(label, class_name, kind), ...] where kind is buff or debuff."""
    q = _normalize(query)
    if not q:
        return []
    out: List[Tuple[str, str, str, int]] = []
    for name in BUFFS:
        key = _normalize(name)
        score = _score(q, key)
        if score > 0:
            out.append((f"{name} (buff)", name, "buff", score))
    for name in DEBUFFS:
        key = _normalize(name)
        score = _score(q, key)
        if score > 0:
            out.append((f"{name} (debuff)", name, "debuff", score))
    out.sort(key=lambda x: -x[3])
    return [(a, b, c) for a, b, c, _ in out[:limit]]


def _score(q: str, key: str) -> int:
    if q == key:
        return 1000
    if q in key or key in q:
        return 100 + min(len(q), len(key))
    return 0


def format_buff_search(query: str, rows: List[Tuple[str, str, str]]) -> str:
    if not rows:
        return f"No buff/debuff matches for '{query}'"
    lines = [f"Buff/debuff matches for '{query}':"]
    for label, cls, kind in rows:
        lines.append(f"  {label}  ->  {kind} {cls}")
    return "\n".join(lines)
