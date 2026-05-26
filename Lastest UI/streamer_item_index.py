"""Local item name search from items.properties (works without the game)."""
import os
import re
from typing import List, Tuple

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
_NAME_LINE = re.compile(r"^items\.([^.]+)\.name=(.+)\s*$")

_index_cache = None  # type: list


def _properties_path() -> str:
    return os.path.normpath(
        os.path.join(
            SCRIPT_DIR,
            "..",
            "core",
            "src",
            "main",
            "assets",
            "messages",
            "items",
            "items.properties",
        )
    )


def normalize_key(text: str) -> str:
    return "".join(c.lower() for c in (text or "") if c.isalnum())


def class_hint_from_key(key: str) -> str:
    """Best-effort Java class simple name from properties key (e.g. stylus -> Stylus)."""
    if not key:
        return key
    return key[0].upper() + key[1:]


def load_index() -> List[Tuple[str, str, str]]:
    """Returns list of (display_name, properties_key, class_hint)."""
    global _index_cache
    if _index_cache is not None:
        return _index_cache

    path = _properties_path()
    entries: List[Tuple[str, str, str]] = []
    if not os.path.isfile(path):
        _index_cache = entries
        return entries

    with open(path, encoding="utf-8") as f:
        for line in f:
            m = _NAME_LINE.match(line.strip())
            if not m:
                continue
            key, display = m.group(1), m.group(2)
            entries.append((display, key, class_hint_from_key(key)))

    _index_cache = entries
    return entries


def _levenshtein(a: str, b: str) -> int:
    if not a:
        return len(b)
    if not b:
        return len(a)
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        curr = [i]
        for j, cb in enumerate(b, 1):
            cost = 0 if ca == cb else 1
            curr.append(min(curr[-1] + 1, prev[j] + 1, prev[j - 1] + cost))
        prev = curr
    return prev[-1]


def _score(query_key: str, display: str, key: str, class_hint: str) -> int:
    dk = normalize_key(display)
    ck = normalize_key(class_hint)
    kk = normalize_key(key)
    if not query_key:
        return 0
    if query_key in (dk, ck, kk):
        return 1000
    if query_key in dk or dk in query_key:
        return 120 + min(len(query_key), len(dk))
    if query_key in ck or ck in query_key:
        return 100 + min(len(query_key), len(ck))
    if query_key in kk or kk in query_key:
        return 90 + min(len(query_key), len(kk))
    d1 = _levenshtein(query_key, dk)
    if d1 <= 4:
        return 55 - d1
    d2 = _levenshtein(query_key, ck)
    if d2 <= 4:
        return 45 - d2
    return 0


def search_items_local(query: str, limit: int = 15) -> List[Tuple[str, str]]:
    """Return [(display, class_hint), ...] sorted by relevance."""
    q = normalize_key(query)
    if not q:
        return []
    scored = []
    for display, key, hint in load_index():
        s = _score(q, display, key, hint)
        if s > 0:
            scored.append((s, display, hint))
    scored.sort(key=lambda x: -x[0])
    out: List[Tuple[str, str]] = []
    seen = set()
    for _, display, hint in scored:
        if display in seen:
            continue
        seen.add(display)
        out.append((display, hint))
        if len(out) >= limit:
            break
    return out


def format_search_results(query: str, rows: List[Tuple[str, str]]) -> str:
    if not rows:
        return f"No local matches for '{query}'"
    lines = [f"Matches for '{query}':"]
    for display, hint in rows:
        lines.append(f"  {display}  ->  give {hint}  (or: {display})")
    return "\n".join(lines)
