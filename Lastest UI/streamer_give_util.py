"""Parse give-item lines and POST to overlay /api/streamer-debug/give."""
import json
import os
import re
import urllib.error
import urllib.request
from typing import List, Optional, Tuple

BASE_URL = os.environ.get("SPD_OVERLAY_URL", "http://127.0.0.1:5000")


def parse_give_line(line: str) -> Tuple[str, int, int]:
    """
    Parse e.g. 'Scroll of Upgrade x10', 'Battle Axe +99', 'Battle Axe +5 x2'.
    Returns (item_name, quantity, level).
    """
    text = (line or "").strip()
    if not text:
        return "", 1, 0

    quantity = 1
    level = 0

    m = re.search(r"\s+x(\d+)\s*$", text, re.IGNORECASE)
    if m:
        quantity = max(1, min(999, int(m.group(1))))
        text = text[: m.start()].strip()

    m = re.search(r"\s+\+(\d+)\s*$", text)
    if m:
        level = max(0, min(99, int(m.group(1))))
        text = text[: m.start()].strip()

    return text, quantity, level


def post_give(item_name: str, quantity: int = 1, level: int = 0) -> Tuple[bool, str]:
    url = BASE_URL.rstrip("/") + "/api/streamer-debug/give"
    body = {"item": item_name, "quantity": quantity, "level": level}
    data = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(
        url, data=data, method="POST", headers={"Content-Type": "application/json"}
    )
    try:
        with urllib.request.urlopen(req, timeout=25) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        try:
            payload = json.loads(e.read().decode("utf-8"))
            if payload.get("error"):
                return False, str(payload["error"])
        except Exception:
            pass
        return False, f"HTTP {e.code}"
    except urllib.error.URLError:
        return False, f"Cannot reach overlay at {BASE_URL} (is server.py running?)"
    except Exception as e:
        return False, str(e)

    if payload.get("ok"):
        return True, str(payload.get("detail", "ok"))
    return False, str(payload.get("error", "Give failed"))


def post_search(query: str, limit: int = 12) -> Tuple[bool, str]:
    """Search via game/overlay (full catalog + class names)."""
    url = BASE_URL.rstrip("/") + "/api/streamer-debug/search"
    body = {"query": query, "limit": limit}
    data = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(
        url, data=data, method="POST", headers={"Content-Type": "application/json"}
    )
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        try:
            payload = json.loads(e.read().decode("utf-8"))
            if payload.get("error"):
                return False, str(payload["error"])
        except Exception:
            pass
        return False, f"HTTP {e.code}"
    except urllib.error.URLError:
        return False, ""
    except Exception:
        return False, ""

    if payload.get("ok"):
        detail = str(payload.get("detail", ""))
        return True, _format_game_search(query, detail)
    return False, str(payload.get("error", "Search failed"))


def parse_buff_line(line: str) -> Tuple[str, float]:
    """Parse 'Haste 30' -> (name, duration turns). 0 duration = game default."""
    text = (line or "").strip()
    if not text:
        return "", 0.0
    m = re.search(r"\s+(\d+(?:\.\d+)?)\s*$", text)
    if m:
        duration = max(0.0, min(9999.0, float(m.group(1))))
        text = text[: m.start()].strip()
        return text, duration
    return text, 0.0


def post_buff(buff_name: str, duration: float = 0.0) -> Tuple[bool, str]:
    url = BASE_URL.rstrip("/") + "/api/streamer-debug/buff"
    body = {"buff": buff_name, "duration": duration}
    return _post_json(url, body, "Apply buff failed")


def post_debuff(debuff_name: str, duration: float = 0.0) -> Tuple[bool, str]:
    url = BASE_URL.rstrip("/") + "/api/streamer-debug/debuff"
    body = {"debuff": debuff_name, "duration": duration}
    return _post_json(url, body, "Apply debuff failed")


def _post_json(url: str, body: dict, fallback: str) -> Tuple[bool, str]:
    data = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(
        url, data=data, method="POST", headers={"Content-Type": "application/json"}
    )
    try:
        with urllib.request.urlopen(req, timeout=25) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        try:
            payload = json.loads(e.read().decode("utf-8"))
            if payload.get("error"):
                return False, str(payload["error"])
        except Exception:
            pass
        return False, f"HTTP {e.code}"
    except urllib.error.URLError:
        return False, f"Cannot reach overlay at {BASE_URL} (is server.py running?)"
    except Exception as e:
        return False, str(e)
    if payload.get("ok"):
        return True, str(payload.get("detail", "ok"))
    return False, str(payload.get("error", fallback))


def _format_game_search(query: str, detail: str) -> str:
    """detail: '3 match(es): [item] ...|[buff/debuff] ...'"""
    if "|" in detail and "match(es):" in detail:
        _, _, rest = detail.partition(":")
        parts = [p.strip() for p in rest.split("|") if p.strip()]
        lines = [f"Matches for '{query}' (game):"]
        for p in parts:
            if p.startswith("[item]"):
                p = p[6:].strip()
                hint = "give"
            elif p.startswith("[buff/debuff]"):
                p = p[13:].strip()
                hint = "buff/debuff"
            else:
                hint = "give"
            if "(" in p and ")" in p:
                display, _, rest2 = p.rpartition("(")
                cls = rest2.rstrip(")").split(",")[0].strip()
                if "debuff" in p.lower():
                    lines.append(f"  {display.strip()}  ->  debuff {cls}")
                elif "buff" in p.lower() and "debuff" not in display.lower():
                    lines.append(f"  {display.strip()}  ->  buff {cls}")
                else:
                    lines.append(f"  {display.strip()}  ->  {hint} {cls}")
            else:
                lines.append(f"  {p}")
        return "\n".join(lines)
    return detail


def search_items(query: str, limit: int = 15) -> str:
    """Game search if connected; always include local item + buff lists."""
    from streamer_buff_index import format_buff_search, search_buffs_local
    from streamer_item_index import format_search_results, search_items_local

    sections = []
    ok, msg = post_search(query, limit=limit)
    if ok and msg:
        sections.append(msg)

    item_rows = search_items_local(query, limit=limit)
    if item_rows:
        sections.append(format_search_results(query, item_rows))

    buff_rows = search_buffs_local(query, limit=limit)
    if buff_rows:
        sections.append(format_buff_search(query, buff_rows))

    if sections:
        return "\n\n".join(sections)
    if msg:
        return msg
    return f"No matches for '{query}' (items or buffs)"


def print_suggestions_for_failed_buff(name: str, debuff: bool = False) -> None:
    from streamer_buff_index import format_buff_search, search_buffs_local

    rows = search_buffs_local(name, limit=6)
    if rows:
        print(format_buff_search(name, rows))


def print_suggestions_for_failed_give(item_name: str) -> None:
    """After a failed give, print local + game suggestions."""
    text = search_items(item_name, limit=8)
    if text and "No local matches" not in text and "No matches" not in text:
        print(text)
