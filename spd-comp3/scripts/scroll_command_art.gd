extends RefCounted
## Scroll rune art: item_sheet + assets/scrolls; item icons: icons_sheet, item_sheet, legacy itemicons.
class_name ScrollCommandArt

const _Icons := preload("res://scripts/companion_item_icons.gd")

const _SCR_DIR := "res://assets/scrolls/"
const _ITEM_SHEET := "res://assets/item_sheet/"
const _ICO_DIR := "res://assets/itemicons/"
## Snapshot arrays: same keys as identification_overlay.
const _SCROLL_SNAPSHOT_KEYS: PackedStringArray = ["scrolls", "scroll_list", "identified_scrolls"]
## Fixed labels from vanilla [code]Scroll.java[/code] [code]runes[/code] map (per-run assignment uses these strings).
const _SCROLL_RUNE_LABELS_LOWER: Dictionary = {
	"kaunan": true,
	"sowilo": true,
	"laguz": true,
	"yngvi": true,
	"gyfu": true,
	"raido": true,
	"isaz": true,
	"mannaz": true,
	"naudiz": true,
	"berkanan": true,
	"odal": true,
	"tiwaz": true,
}
## [code]ItemSpriteSheet.SCROLL_KAUNAN[/code] main-sheet id (SPD 3.3.x). Exotic runes are the next row: same column +16 ([code]ExoticScroll.reset()[/code]).
const _ITEMSHEET_SCROLL_KAUNAN_ID: int = 304
const _SCROLL_RUNE_ORDER_BY_IMAGE_OFF: PackedStringArray = [
	"KAUNAN", "SOWILO", "LAGUZ", "YNGVI", "GYFU", "RAIDO", "ISAZ", "MANNAZ", "NAUDIZ", "BERKANAN", "ODAL", "TIWAZ",
]
## Exotic scroll classes ([i]ExoticScroll[/i] in SPD). Same rune label as the paired regular scroll
## ([code]ExoticScroll.reset()[/code] copies the base scroll's handler label); companion uses [code]exotic_%s.png[/code] rune art.
## Pairing from vanilla [code]ExoticScroll.java[/code] [code]regToExo[/code]:
## Upgrade→Enchantment, Identify→Divination, RemoveCurse→AntiMagic, MirrorImage→PrismaticImage, Recharging→MysticalEnergy,
## Teleportation→Passage, Lullaby→SirensSong, MagicMapping→Foresight, Rage→Challenge, Retribution→PsionicBlast,
## Terror→Dread, Transmutation→Metamorphosis.
## Rune strings (e.g. LAGUZ) are assigned per run by [code]Scroll.handler[/code]; they are not fixed to one scroll type in source.
const _EXOTIC_SCROLL_CLASSES: Dictionary = {
	"ScrollOfEnchantment": true,
	"ScrollOfDivination": true,
	"ScrollOfAntiMagic": true,
	"ScrollOfPrismaticImage": true,
	"ScrollOfMysticalEnergy": true,
	"ScrollOfPassage": true,
	"ScrollOfSirensSong": true,
	"ScrollOfForesight": true,
	"ScrollOfChallenge": true,
	"ScrollOfPsionicBlast": true,
	"ScrollOfDread": true,
	"ScrollOfMetamorphosis": true,
}
## Vanilla [code]ExoticScroll.java[/code] [code]regToExo[/code]: paired regular scroll class → exotic counterpart.
const _REGULAR_TO_EXOTIC_SCROLL: Dictionary = {
	"ScrollOfUpgrade": "ScrollOfEnchantment",
	"ScrollOfIdentify": "ScrollOfDivination",
	"ScrollOfRemoveCurse": "ScrollOfAntiMagic",
	"ScrollOfMirrorImage": "ScrollOfPrismaticImage",
	"ScrollOfRecharging": "ScrollOfMysticalEnergy",
	"ScrollOfTeleportation": "ScrollOfPassage",
	"ScrollOfLullaby": "ScrollOfSirensSong",
	"ScrollOfMagicMapping": "ScrollOfForesight",
	"ScrollOfRage": "ScrollOfChallenge",
	"ScrollOfRetribution": "ScrollOfPsionicBlast",
	"ScrollOfTerror": "ScrollOfDread",
	"ScrollOfTransmutation": "ScrollOfMetamorphosis",
}
const _EXOTIC_TO_REGULAR_SCROLL: Dictionary = {
	"ScrollOfEnchantment": "ScrollOfUpgrade",
	"ScrollOfDivination": "ScrollOfIdentify",
	"ScrollOfAntiMagic": "ScrollOfRemoveCurse",
	"ScrollOfPrismaticImage": "ScrollOfMirrorImage",
	"ScrollOfMysticalEnergy": "ScrollOfRecharging",
	"ScrollOfPassage": "ScrollOfTeleportation",
	"ScrollOfSirensSong": "ScrollOfLullaby",
	"ScrollOfForesight": "ScrollOfMagicMapping",
	"ScrollOfChallenge": "ScrollOfRage",
	"ScrollOfPsionicBlast": "ScrollOfRetribution",
	"ScrollOfDread": "ScrollOfTerror",
	"ScrollOfMetamorphosis": "ScrollOfTransmutation",
}
static var _tex_cache: Dictionary = {}  ## path String -> Texture2D


static func _coerce_int_for_image_id(v: Variant) -> int:
	if v is int:
		return v
	if v is float:
		return int(v)
	var s: String = str(v).strip_edges()
	if s.is_empty():
		return -1
	if s.is_valid_int():
		return int(s)
	if s.is_valid_float():
		return int(s.to_float())
	return -1


## Map item [code]image[/code] sprite index → rune name (same ordering as [code]Scroll.java[/code] [code]runes[/code] map). Handles exotic row (+16).
static func rune_stem_from_scroll_item_image_id(image_id: int) -> String:
	if image_id < 0:
		return ""
	var base: int = image_id
	var exo_lo: int = _ITEMSHEET_SCROLL_KAUNAN_ID + 16
	var exo_hi: int = exo_lo + 11
	if base >= exo_lo and base <= exo_hi:
		base = image_id - 16
	if base < _ITEMSHEET_SCROLL_KAUNAN_ID or base > _ITEMSHEET_SCROLL_KAUNAN_ID + 11:
		return ""
	var idx: int = base - _ITEMSHEET_SCROLL_KAUNAN_ID
	if idx < 0 or idx >= _SCROLL_RUNE_ORDER_BY_IMAGE_OFF.size():
		return ""
	return _SCROLL_RUNE_ORDER_BY_IMAGE_OFF[idx]


static func _rune_stem_from_scroll_image_fields_in_dict(d: Dictionary) -> String:
	for k in ["image", "sprite", "spriteId", "sprite_id", "itemImage", "item_image", "iconIndex", "icon_index"]:
		if not d.has(k):
			continue
		var stem: String = rune_stem_from_scroll_item_image_id(_coerce_int_for_image_id(d[k]))
		if stem != "":
			return stem
	return ""


static func _normalize_scroll_class_casing(s: String) -> String:
	var t: String = s.strip_edges()
	if t.begins_with("scrollOf"):
		return "S" + t.substr(1)
	return t


static func normalize_scroll_item_class(raw: String) -> String:
	return _normalize_scroll_class_casing(_simple_class_name(raw.strip_edges()))


static func _peer_scroll_class_set(cls: String) -> Dictionary:
	var out: Dictionary = {}
	if cls.is_empty():
		return out
	out[cls] = true
	if _EXOTIC_TO_REGULAR_SCROLL.has(cls):
		out[str(_EXOTIC_TO_REGULAR_SCROLL[cls])] = true
	if _REGULAR_TO_EXOTIC_SCROLL.has(cls):
		out[str(_REGULAR_TO_EXOTIC_SCROLL[cls])] = true
	return out


static func _first_rune_string_from_scroll_dict(d: Dictionary) -> String:
	for k in [
		"scroll_rune",
		"scrollRune",
		"scroll_rune_name",
		"scrollRuneName",
		"rune_name",
		"runeName",
		"glyph",
		"scroll_glyph",
		"glyphName",
		"glyph_name",
		"rune_label",
		"runeLabel",
		"rune",
		"Rune",
	]:
		if d.has(k):
			var s: String = str(d[k]).strip_edges()
			if s != "":
				return s
	# Streaming JSON sometimes only exposes the rune as a bare string field (e.g. message shape / odd keys).
	for _k in d.keys():
		var v: Variant = d[_k]
		var s2: String = str(v).strip_edges()
		if s2.length() < 3 or s2.length() > 12:
			continue
		if not _is_ascii_letters_only(s2):
			continue
		if _SCROLL_RUNE_LABELS_LOWER.has(s2.to_lower()):
			return s2.to_upper()
	return ""


static func _is_ascii_letters_only(s: String) -> bool:
	for i in range(s.length()):
		var c: int = s.unicode_at(i)
		if (c < 65 or c > 90) and (c < 97 or c > 122):
			return false
	return true


## Use for identification rows so rune art resolves even when keys are nonstandard.
static func rune_stem_from_identification_item(item: Dictionary) -> String:
	var s0: String = _first_rune_string_from_scroll_dict(item)
	if s0 != "":
		return s0
	var img0: String = _rune_stem_from_scroll_image_fields_in_dict(item)
	if img0 != "":
		return img0
	for _k in item.keys():
		var v: Variant = item[_k]
		if v is Dictionary:
			var s1: String = rune_stem_from_identification_item_shallow(v as Dictionary, 4)
			if s1 != "":
				return s1
	# Streaming payloads often wrap the real row one level deeper.
	for wrap_key in ["item", "payload", "value", "data", "inner", "content", "entry", "object", "ref"]:
		if not item.has(wrap_key):
			continue
		var w: Variant = item[wrap_key]
		if w is Dictionary:
			var s2: String = rune_stem_from_identification_item_shallow(w as Dictionary, 4)
			if s2 != "":
				return s2
	return ""


## Bounded recursion for nested wrappers (avoids runaway on circular refs — depth cap).
static func rune_stem_from_identification_item_shallow(d: Dictionary, depth_left: int) -> String:
	if depth_left <= 0:
		return ""
	var s0: String = _first_rune_string_from_scroll_dict(d)
	if s0 != "":
		return s0
	var img_s: String = _rune_stem_from_scroll_image_fields_in_dict(d)
	if img_s != "":
		return img_s
	for _k in d.keys():
		var v: Variant = d[_k]
		if v is Dictionary:
			var s1: String = rune_stem_from_identification_item_shallow(v as Dictionary, depth_left - 1)
			if s1 != "":
				return s1
	for wrap_key in ["item", "payload", "value", "data", "inner", "content", "entry"]:
		if not d.has(wrap_key) or not d[wrap_key] is Dictionary:
			continue
		var s3: String = rune_stem_from_identification_item_shallow(d[wrap_key] as Dictionary, depth_left - 1)
		if s3 != "":
			return s3
	return ""


static func _coerce_scroll_class_from_loose_fields(item: Dictionary) -> String:
	for fk in ["name", "displayName", "item_name", "label", "title", "scroll_name", "scrollName"]:
		if not item.has(fk):
			continue
		var bn: String = _scroll_itemicon_basename_from_label(str(item[fk]))
		if bn.begins_with("ScrollOf"):
			return bn
	return ""

static func _dict_might_be_scroll_item(d: Dictionary) -> bool:
	var cls: String = _item_best_scroll_class_string(d)
	if cls.begins_with("ScrollOf"):
		return true
	return d.has("scroll_rune") or d.has("scrollRune") or d.has("scroll_glyph")


static func _rune_from_pool_matching_peer_classes(pool: Array, peers: Dictionary) -> String:
	for item_var in pool:
		if not item_var is Dictionary:
			continue
		var o: Dictionary = item_var
		var ocls: String = _item_best_scroll_class_string(o)
		if ocls.is_empty():
			ocls = _coerce_scroll_class_from_loose_fields(o)
		if ocls.is_empty() or not peers.has(ocls):
			continue
		var got: String = rune_stem_from_identification_item(o)
		if got != "":
			return got
	return ""


static func _all_dicts_in_value(root: Variant, max_nodes: int) -> Array:
	var acc: Array = []
	var stack: Array = [root]
	var visited: int = 0
	while stack.size() > 0 and visited < max_nodes:
		var cur: Variant = stack.pop_back()
		visited += 1
		if cur is Dictionary:
			var d: Dictionary = cur
			acc.append(d)
			for k in d.keys():
				stack.append(d[k])
		elif cur is Array:
			for el in cur:
				stack.append(el)
	return acc


static func _flatten_scroll_like_dicts_from_snapshot(snapshot: Dictionary, max_nodes: int) -> Array:
	var acc: Array = []
	var stack: Array = [snapshot]
	var visited: int = 0
	while stack.size() > 0 and visited < max_nodes:
		var cur: Variant = stack.pop_back()
		visited += 1
		if cur is Dictionary:
			var d: Dictionary = cur
			if _dict_might_be_scroll_item(d):
				acc.append(d)
			for k in d.keys():
				stack.append(d[k])
		elif cur is Array:
			for el in cur:
				stack.append(el)
	return acc


## Rune / glyph string for scroll background art. Prefer [method build_scroll_class_to_rune_stem_map] when the snapshot omits exotic rows: rune↔spell is learned from **regular** scrolls, then applied to exotics via [member _EXOTIC_TO_REGULAR_SCROLL].
static func resolve_scroll_rune_stem(item: Dictionary, snapshot: Dictionary, class_rune_map: Dictionary = {}) -> String:
	var direct: String = rune_stem_from_identification_item(item)
	if direct != "":
		return direct
	var cls: String = _item_best_scroll_class_string(item)
	if cls.is_empty():
		cls = _coerce_scroll_class_from_loose_fields(item)
	if cls.is_empty() or snapshot.is_empty():
		return ""
	var mmap: Dictionary = class_rune_map
	if mmap.is_empty():
		mmap = build_scroll_class_to_rune_stem_map(snapshot)
	var from_map: String = resolve_rune_stem_for_scroll_class_using_regular_map(cls, mmap)
	if from_map != "":
		return from_map
	var peers: Dictionary = _peer_scroll_class_set(cls)
	var found: String = _rune_from_pool_matching_peer_classes(_iter_scroll_items(snapshot), peers)
	if found != "":
		return found
	found = _rune_from_pool_matching_peer_classes(
		_flatten_scroll_like_dicts_from_snapshot(snapshot, 12000), peers
	)
	if found != "":
		return found
	# Any dictionary in the snapshot may carry [code]ScrollOfRage[/code]+[code]ISAZ[/code] while ID rows are minimal — walk everything.
	return _rune_from_pool_matching_peer_classes(_all_dicts_in_value(snapshot, 12000), peers)


## One pass over the snapshot: [code]ScrollOfUpgrade[/code] → [code]LAGUZ[/code], etc. Only **non-exotic** rows populate the map so websocket bundles that omit exotic ID entries still carry rune↔spell data from regular scrolls ([code]ExoticScroll[/code] shares the same rune as the paired regular scroll).
static func build_scroll_class_to_rune_stem_map(snapshot: Dictionary, max_nodes: int = 12000) -> Dictionary:
	var out: Dictionary = {}
	if snapshot.is_empty():
		return out
	for d_var in _all_dicts_in_value(snapshot, max_nodes):
		if not d_var is Dictionary:
			continue
		var d: Dictionary = d_var
		var cls: String = _item_best_scroll_class_string(d)
		if cls.is_empty():
			cls = _coerce_scroll_class_from_loose_fields(d)
		if not cls.begins_with("ScrollOf"):
			continue
		if _EXOTIC_SCROLL_CLASSES.has(cls):
			continue
		var stem: String = rune_stem_from_identification_item(d)
		if stem.is_empty():
			continue
		out[cls] = stem
	return out


## Use [method build_scroll_class_to_rune_stem_map]; for an exotic class, use the paired regular class’s stem.
static func resolve_rune_stem_for_scroll_class_using_regular_map(cls: String, regular_class_to_rune: Dictionary) -> String:
	if cls.is_empty():
		return ""
	if regular_class_to_rune.has(cls):
		return str(regular_class_to_rune[cls]).strip_edges()
	if _EXOTIC_TO_REGULAR_SCROLL.has(cls):
		var reg: String = str(_EXOTIC_TO_REGULAR_SCROLL[cls])
		if regular_class_to_rune.has(reg):
			return str(regular_class_to_rune[reg]).strip_edges()
	return ""

static func _exotic_rune_filename_variants(stem: String) -> Array[String]:
	var acc: Array[String] = []
	var seen: Dictionary = {}
	for v in _stem_variants(stem):
		for cand in [v.to_lower(), v, v.to_upper()]:
			var c: String = cand.strip_edges()
			if c.is_empty() or seen.has(c):
				continue
			seen[c] = true
			acc.append(c)
	return acc


static func _item_best_scroll_class_string(item: Dictionary) -> String:
	## Include [code]itemType[/code] like [code]identification_overlay._item_class_raw[/code]; snapshots often put [code]ScrollOf…[/code] there without [code]class_name[/code].
	for k in [
		"class_name", "className", "item_class", "itemType", "itemClass", "cls", "java_class", "javaClass",
		"scroll_class", "scrollClass", "identified_class", "identifiedClass", "scroll_type", "scrollType",
		"item_kind", "itemKind", "full_class", "fullClass",
	]:
		if not item.has(k):
			continue
		var raw_class: String = str(item[k]).strip_edges()
		if k == "itemType" and (raw_class.to_lower() == "scroll" or raw_class.to_lower() == "potion"):
			continue
		var simple: String = _simple_class_name(raw_class)
		simple = _normalize_scroll_class_casing(simple)
		if simple.begins_with("ScrollOf"):
			return simple
	if item.has("type"):
		var t: String = _normalize_scroll_class_casing(_simple_class_name(str(item["type"]).strip_edges()))
		if t.begins_with("ScrollOf"):
			return t
	# Wrapped rows (same keys nested under [code]item[/code] / [code]data[/code]).
	for wrap_key in ["item", "payload", "value", "data", "inner", "content", "entry", "object", "ref"]:
		if not item.has(wrap_key) or not item[wrap_key] is Dictionary:
			continue
		var inner: String = _item_best_scroll_class_string(item[wrap_key] as Dictionary)
		if inner.begins_with("ScrollOf"):
			return inner
	return ""


static func scroll_item_is_exotic(item: Dictionary) -> bool:
	if item.is_empty():
		return false
	for k in ["exotic", "is_exotic", "isExotic", "exotic_scroll", "scroll_variant"]:
		if not item.has(k):
			continue
		var v: Variant = item[k]
		if v is bool:
			if v:
				return true
			continue
		var s := str(v).strip_edges().to_lower()
		if s == "1" or s == "true" or s == "yes" or s == "exotic":
			return true
	if item.has("variant"):
		var vs := str(item["variant"]).strip_edges().to_lower()
		if vs.contains("exotic"):
			return true
	var cls: String = _item_best_scroll_class_string(item)
	if cls.is_empty():
		cls = _coerce_scroll_class_from_loose_fields(item)
	return not cls.is_empty() and _EXOTIC_SCROLL_CLASSES.has(cls)


static func lookup_scroll_rune_display(phrase: String, snapshot: Dictionary) -> Dictionary:
	var out: Dictionary = {"stem": "", "exotic": false}
	var key: String = _scroll_phrase_norm_key(phrase)
	if key.is_empty():
		return out
	var mmap: Dictionary = build_scroll_class_to_rune_stem_map(snapshot)
	for item_var in _iter_scroll_items(snapshot):
		if not item_var is Dictionary:
			continue
		var item: Dictionary = item_var
		if not _scroll_item_matches_norm_key(item, key):
			continue
		out["exotic"] = scroll_item_is_exotic(item)
		var stem0: String = rune_stem_from_identification_item(item)
		if stem0 != "":
			out["stem"] = stem0
			return out
		out["stem"] = resolve_scroll_rune_stem(item, snapshot, mmap)
		return out
	## Snapshot may list only regular scrolls: derive [code]ScrollOf…[/code] from the phrase and map via paired regular class.
	var cls_from_phrase: String = _scroll_itemicon_basename_from_label(phrase)
	if not cls_from_phrase.begins_with("ScrollOf"):
		cls_from_phrase = _scroll_itemicon_basename_from_label("scroll of " + phrase.strip_edges())
	if cls_from_phrase.begins_with("ScrollOf"):
		out["exotic"] = _EXOTIC_SCROLL_CLASSES.has(cls_from_phrase)
		out["stem"] = resolve_rune_stem_for_scroll_class_using_regular_map(cls_from_phrase, mmap)
	return out


static func texture_for_scroll_result(data: Dictionary, row: Dictionary) -> Texture2D:
	return _texture_from_sources(_stem_candidates_from_result(data, row))


static func texture_for_scroll_hint(hint: String) -> Texture2D:
	return _texture_from_sources(_single_candidate_stems(hint))


static func _primary_scroll_phrase(data: Dictionary, row: Dictionary) -> String:
	for k in ["scroll_name", "scrollName"]:
		if data.has(k):
			var s: String = str(data[k]).strip_edges()
			if s != "":
				return s
	return str(row.get("scroll_hint", "")).strip_edges()


## Rune ↔ scroll **type** is **random per run** in vanilla SPD; use the latest game snapshot so alerts match the ID strip.
static func lookup_rune_stem_for_scroll_phrase(phrase: String, snapshot: Dictionary) -> String:
	return str(lookup_scroll_rune_display(phrase, snapshot).get("stem", ""))

static func _scroll_phrase_norm_key(phrase: String) -> String:
	var t: String = phrase.strip_edges().to_lower()
	if t.is_empty():
		return ""
	for prefix in ["a ", "an ", "the "]:
		if t.begins_with(prefix):
			t = t.substr(prefix.length()).strip_edges()
	if t.begins_with("scroll of "):
		t = t.substr(9).strip_edges()
	return t.replace(" ", "").replace("_", "").replace("-", "")


static func _simple_class_name(raw: String) -> String:
	var s: String = raw.strip_edges()
	if s.is_empty():
		return ""
	var li: int = s.rfind(".")
	if li != -1:
		return (s as String).substr(li + 1)
	return s


static func _scroll_item_matches_norm_key(item: Dictionary, key: String) -> bool:
	if key.is_empty():
		return false
	var cls: String = _item_best_scroll_class_string(item)
	if cls.is_empty():
		cls = _coerce_scroll_class_from_loose_fields(item)
	if cls.begins_with("ScrollOf"):
		var tail: String = cls.substr(8)
		if _scroll_phrase_norm_key(tail) == key:
			return true
	for fk in ["scroll_name", "name", "displayName", "item_name", "label"]:
		if item.has(fk):
			if _scroll_phrase_norm_key(str(item[fk])) == key:
				return true
	return false


static func _iter_scroll_items(snapshot: Dictionary) -> Array:
	var acc: Array = []
	if snapshot.is_empty():
		return acc
	var roots: Array = []
	var idn: Variant = snapshot.get("identification", null)
	if idn is Dictionary:
		roots.append(idn)
	roots.append(snapshot)
	for root_key in ["gameState", "game", "dungeon", "state", "hero"]:
		var sub: Variant = snapshot.get(root_key, null)
		if sub is Dictionary:
			var id2: Variant = (sub as Dictionary).get("identification", null)
			if id2 is Dictionary:
				roots.append(id2)
			roots.append(sub)
	var seen: Dictionary = {}
	for r in roots:
		if not r is Dictionary:
			continue
		var rk: int = (r as Dictionary).hash()
		if seen.has(rk):
			continue
		seen[rk] = true
		var d: Dictionary = r
		for sk in _SCROLL_SNAPSHOT_KEYS:
			if d.has(sk) and d[sk] is Array:
				for el in d[sk]:
					acc.append(el)
	return acc


static func rune_and_item_for_scroll_result(data: Dictionary, row: Dictionary, snapshot: Dictionary) -> Dictionary:
	var phrase: String = _primary_scroll_phrase(data, row)
	var rune_tex: Texture2D = null
	if not snapshot.is_empty() and phrase != "":
		var disp: Dictionary = lookup_scroll_rune_display(phrase, snapshot)
		var stem_snap: String = str(disp.get("stem", ""))
		if stem_snap != "":
			rune_tex = load_scroll_rune(stem_snap, bool(disp.get("exotic", false)))
	if rune_tex == null:
		var stems_r: Array[String] = _stem_candidates_from_result(data, row)
		var exo: bool = scroll_item_is_exotic(data)
		rune_tex = _rune_texture_from_stems(stems_r, exo)
	var stems_i: Array[String] = _stem_candidates_from_result(data, row)
	var item_tex: Texture2D = _item_texture_for_scroll_phrase(phrase, stems_i, snapshot)
	return {"rune": rune_tex, "item": item_tex}


static func rune_and_item_for_scroll_hint(hint: String, snapshot: Dictionary) -> Dictionary:
	var h: String = hint.strip_edges()
	var rune_tex: Texture2D = null
	if not snapshot.is_empty() and h != "":
		var disp: Dictionary = lookup_scroll_rune_display(h, snapshot)
		var stem_snap: String = str(disp.get("stem", ""))
		if stem_snap != "":
			rune_tex = load_scroll_rune(stem_snap, bool(disp.get("exotic", false)))
	if rune_tex == null:
		var stems_r: Array[String] = _single_candidate_stems(h)
		rune_tex = _rune_texture_from_stems(stems_r, false)
	var stems_i: Array[String] = _single_candidate_stems(h)
	var item_tex: Texture2D = _item_texture_for_scroll_phrase(h, stems_i, snapshot)
	return {"rune": rune_tex, "item": item_tex}


static func _stem_candidates_from_result(data: Dictionary, row: Dictionary) -> Array[String]:
	var out: Array[String] = []
	var hint: String = str(row.get("scroll_hint", "")).strip_edges()
	_append_stems(out, hint)
	for k in ["rune_name", "runeName", "rune", "scroll_rune", "scrollRune", "glyph", "scroll_glyph"]:
		if data.has(k):
			_append_stems(out, str(data[k]).strip_edges())
	if data.has("scroll_name"):
		_append_stems(out, str(data["scroll_name"]).strip_edges())
	for ik in ["image", "sprite", "spriteId", "sprite_id", "itemImage", "item_image", "iconIndex", "icon_index"]:
		if not data.has(ik):
			continue
		var st_img: String = rune_stem_from_scroll_item_image_id(_coerce_int_for_image_id(data[ik]))
		if st_img != "":
			_append_stems(out, st_img)
			break
	return out


static func _single_candidate_stems(s: String) -> Array[String]:
	var out: Array[String] = []
	_append_stems(out, s.strip_edges())
	return out


static func _append_stems(out: Array[String], raw: String) -> void:
	if raw.is_empty():
		return
	out.append(raw)
	var low := raw.to_lower()
	if low.begins_with("scroll of "):
		var rest: String = raw.substr(9).strip_edges()
		if not rest.is_empty():
			out.append(rest)
	var icon_bn: String = _scroll_itemicon_basename_from_label(raw)
	if icon_bn != "":
		out.append(icon_bn)


static func _texture_from_sources(stems: Array[String]) -> Texture2D:
	var rune_try: Texture2D = _rune_texture_from_stems(stems)
	if rune_try:
		return rune_try
	return _item_texture_from_stems(stems)


static func _rune_texture_from_stems(stems: Array[String], exotic: bool = false) -> Texture2D:
	for stem in stems:
		var tex: Texture2D = load_scroll_rune(stem, exotic)
		if tex:
			return tex
	return null


static func _item_texture_from_stems(stems: Array[String]) -> Texture2D:
	for stem in stems:
		var bases: Array[String] = []
		var bn: String = _scroll_itemicon_basename_from_label(stem)
		if bn != "":
			bases.append(bn)
		if stem.begins_with("ScrollOf"):
			bases.append(stem.strip_edges())
		for b in bases:
			var t1: Texture2D = _Icons.texture_for_item_class(b)
			if t1:
				return t1
	return null


static func _item_texture_for_scroll_phrase(phrase: String, stems_fallback: Array[String], snapshot: Dictionary) -> Texture2D:
	var ph: String = phrase.strip_edges()
	if not snapshot.is_empty() and ph != "":
		var key: String = _scroll_phrase_norm_key(ph)
		if key != "":
			for item_var in _iter_scroll_items(snapshot):
				if not item_var is Dictionary:
					continue
				var item: Dictionary = item_var
				if not _scroll_item_matches_norm_key(item, key):
					continue
				var cls: String = _item_best_scroll_class_string(item)
				if cls.begins_with("ScrollOf"):
					var tico0: Texture2D = _Icons.texture_for_item_class(cls)
					if tico0:
						return tico0
					var tico: Texture2D = _load_tex_in_dir(_ICO_DIR, cls)
					if tico:
						return tico
				break
	return _item_texture_from_stems(stems_fallback)


static func _scroll_itemicon_basename_from_label(label: String) -> String:
	var rest: String = label.strip_edges()
	if rest.is_empty():
		return ""
	if rest.begins_with("ScrollOf"):
		return rest
	if rest.to_lower().begins_with("scroll of "):
		rest = rest.substr(9).strip_edges()
	if rest.is_empty():
		return ""
	var words: PackedStringArray = rest.split(" ", false)
	if words.is_empty():
		return ""
	var sb := "ScrollOf"
	for w in words:
		var t: String = w.strip_edges()
		if t.is_empty():
			continue
		if t.length() == 1:
			sb += t.to_upper()
		else:
			sb += t.substr(0, 1).to_upper() + t.substr(1).to_lower()
	return sb


static func _stem_variants(stem: String) -> Array[String]:
	var t: String = stem.strip_edges()
	var out: Array[String] = []
	if t.is_empty():
		return out
	out.append(t)
	var lower: String = t.to_lower()
	var upper: String = t.to_upper()
	if lower != t:
		out.append(lower)
	if upper != t:
		out.append(upper)
	return out


static func _load_tex_in_dir(dir_path: String, stem: String) -> Texture2D:
	for v in _stem_variants(stem):
		var p: String = dir_path + v + ".png"
		if ResourceLoader.exists(p):
			if _tex_cache.has(p):
				return _tex_cache[p]
			var tex: Texture2D = load(p)
			_tex_cache[p] = tex
			return tex
	return null


static func _load_tex_file(stem: String) -> Texture2D:
	return _load_tex_in_dir(_SCR_DIR, stem)


static func load_scroll_rune(stem: String, exotic: bool = false) -> Texture2D:
	if stem.strip_edges().is_empty():
		return null
	if exotic:
		for fname in _exotic_rune_filename_variants(stem):
			for prefix in [_ITEM_SHEET + "exotic_", _SCR_DIR + "exotic_"]:
				var p: String = prefix + fname + ".png"
				if ResourceLoader.exists(p):
					if _tex_cache.has(p):
						return _tex_cache[p]
					var tex: Texture2D = load(p) as Texture2D
					if tex:
						_tex_cache[p] = tex
						return tex
	for v in _stem_variants(stem):
		var low: String = v.to_lower()
		var p: String = _ITEM_SHEET + "scroll_" + low + ".png"
		if ResourceLoader.exists(p):
			if _tex_cache.has(p):
				return _tex_cache[p]
			var tex2: Texture2D = load(p) as Texture2D
			if tex2:
				_tex_cache[p] = tex2
				return tex2
	return _load_tex_file(stem)


