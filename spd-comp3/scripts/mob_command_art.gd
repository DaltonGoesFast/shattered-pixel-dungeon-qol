extends RefCounted
## Resolve monster idle sprites under `res://assets/mobicons/` for command alerts.
## Uses `<stem>/Idle_*.png` when present; otherwise `<stem>.png` at mobicons root.
class_name MobCommandArt

const _MOB_DIR := "res://assets/mobicons/"
static var _tex_cache: Dictionary = {}  ## path String -> Texture2D

## Normalized spawn hint → `assets/mobicons/<value>/` folder (Shattered Pixel Dungeon mob classes).
const _STEM_ALIASES: Dictionary = {
	"elemental": "fire_elemental",
	"evileye": "eye",
	"evil_eye": "eye",
	"scorpion": "scorpio",
	"dm_100": "dm100",
	"dm_200": "dm200",
	"dm_201": "dm201",
	"dm_300": "dm300",
}

## Parsed streamer point tier list: cost → names accepted by [method idle_frames_for_monster_label].
## Each name matches a Java mob class / mobicons stem except `elemental` (alias → `fire_elemental`).
const STREAMER_POINT_COST_TO_MOBS: Dictionary = {
	5: ["rat"],
	10: ["albino", "snake", "gnoll"],
	15: ["crab", "slime", "swarm"],
	20: ["thief", "skeleton", "dm100"],
	25: ["guard", "necromancer", "spinner"],
	30: ["bat", "brute"],
	35: ["shaman"],
	40: ["ghoul", "elemental"],
	45: ["warlock"],
	50: ["monk", "golem"],
	60: ["succubus"],
	70: ["eye"],
	80: ["scorpio"],
}


## Names configured for [member STREAMER_POINT_COST_TO_MOBS] at this cost (empty if unknown).
static func mob_names_for_point_cost(cost: int) -> PackedStringArray:
	if not STREAMER_POINT_COST_TO_MOBS.has(cost):
		return PackedStringArray()
	var v: Variant = STREAMER_POINT_COST_TO_MOBS[cost]
	if v is PackedStringArray:
		return (v as PackedStringArray).duplicate()
	var out := PackedStringArray()
	for el in v as Array:
		out.append(str(el))
	return out


static func idle_frames_for_monster_label(raw_label: String) -> Array[Texture2D]:
	return _frames_for_monster_label(raw_label, "Idle_")


static func walk_frames_for_monster_label(raw_label: String) -> Array[Texture2D]:
	return _frames_for_monster_label(raw_label, "Walk_")


## Walk cycle for march overlay; falls back to idle, then mobicons root PNG.
## Frames are baked opaque for transparent OBS overlays (no custom shader / sRGB issues).
static func march_frames_for_monster_label(raw_label: String) -> Array[Texture2D]:
	var frames: Array[Texture2D] = walk_frames_for_monster_label(raw_label)
	if frames.is_empty():
		frames = idle_frames_for_monster_label(raw_label)
	return _overlay_opaque_frames(frames)


static func _frames_for_monster_label(raw_label: String, prefix: String) -> Array[Texture2D]:
	var acc: Array[Texture2D] = []
	for stem in _stem_candidates(raw_label):
		if stem.is_empty():
			continue
		var folder: String = _MOB_DIR + stem + "/"
		var names := _prefix_png_names_sorted(folder, prefix)
		if names.size() > 0:
			for n in names:
				var t: Texture2D = _load_tex(folder + n)
				if t:
					acc.append(t)
			if acc.size() > 0:
				return acc
		if prefix == "Idle_":
			var single: Texture2D = _load_tex(_MOB_DIR + stem + ".png")
			if single:
				return [single]
	return acc


static func _stem_candidates(raw_label: String) -> Array[String]:
	var t: String = raw_label.strip_edges()
	if t.is_empty():
		return []
	var slug: String = t.to_lower().replace(" ", "_").replace("-", "_")
	var nospace: String = t.to_lower().replace(" ", "").replace("-", "")
	var out: Array[String] = []
	var seen: Dictionary = {}
	_push_alias_stems(slug, seen, out)
	if nospace != slug:
		_push_alias_stems(nospace, seen, out)
	for s in [slug, nospace]:
		if s != "" and not seen.has(s):
			seen[s] = true
			out.append(s)
	if "_" in slug:
		var parts: PackedStringArray = slug.split("_", false)
		if parts.size() > 1:
			var last: String = str(parts[parts.size() - 1])
			if last != "" and not seen.has(last):
				out.append(last)
	return out


static func _push_alias_stems(key: String, seen: Dictionary, out: Array[String]) -> void:
	if key.is_empty() or not _STEM_ALIASES.has(key):
		return
	var stem: String = str(_STEM_ALIASES[key]).strip_edges()
	if stem.is_empty() or seen.has(stem):
		return
	seen[stem] = true
	out.append(stem)


static func _prefix_png_names_sorted(folder: String, prefix: String) -> PackedStringArray:
	var acc: PackedStringArray = []
	var dir_norm: String = _normalize_dir_for_diraccess(folder)
	for fn in ResourceLoader.list_directory(dir_norm):
		if fn.ends_with("/"):
			continue
		if fn.begins_with(prefix) and fn.to_lower().ends_with(".png"):
			acc.append(fn)
	if acc.is_empty():
		var d: DirAccess = DirAccess.open(dir_norm)
		if d != null:
			d.list_dir_begin()
			var fn2: String = d.get_next()
			while fn2 != "":
				if not d.current_is_dir() and fn2.begins_with(prefix) and fn2.to_lower().ends_with(
					".png"
				):
					acc.append(fn2)
				fn2 = d.get_next()
			d.list_dir_end()
	if acc.is_empty():
		for i in range(64):
			var name: String = "%s%d.png" % [prefix, i]
			if ResourceLoader.exists(dir_norm.path_join(name)):
				acc.append(name)
	acc.sort()
	return acc


static func _idle_png_names_sorted(folder: String) -> PackedStringArray:
	return _prefix_png_names_sorted(folder, "Idle_")


## DirAccess.open needs a `res://...` path without trailing slash issues.
static func _normalize_dir_for_diraccess(path: String) -> String:
	var p: String = path.strip_edges()
	if p.ends_with("/"):
		p = p.substr(0, p.length() - 1)
	return p


static func _overlay_opaque_frames(frames: Array[Texture2D]) -> Array[Texture2D]:
	var out: Array[Texture2D] = []
	for tex in frames:
		if tex == null:
			continue
		out.append(_bake_overlay_opaque(tex))
	return out


## Force visible pixels fully opaque; unpremultiply only when RGB looks premultiplied.
static func _bake_overlay_opaque(tex: Texture2D) -> Texture2D:
	var path := tex.resource_path
	var cache_key := path + "|overlay_opaque"
	if path != "" and _tex_cache.has(cache_key):
		return _tex_cache[cache_key]
	var img: Image = tex.get_image()
	if img.is_empty():
		return tex
	if img.get_format() != Image.FORMAT_RGBA8:
		img.convert(Image.FORMAT_RGBA8)
	for y in img.get_height():
		for x in img.get_width():
			var c: Color = img.get_pixel(x, y)
			if c.a <= 0.001:
				continue
			if c.a < 0.999 and c.r <= c.a + 0.02 and c.g <= c.a + 0.02 and c.b <= c.a + 0.02:
				c.r = clampf(c.r / c.a, 0.0, 1.0)
				c.g = clampf(c.g / c.a, 0.0, 1.0)
				c.b = clampf(c.b / c.a, 0.0, 1.0)
			c.a = 1.0
			img.set_pixel(x, y, c)
	var baked := ImageTexture.create_from_image(img)
	if path != "":
		_tex_cache[cache_key] = baked
	return baked


static func _load_tex(path: String) -> Texture2D:
	if _tex_cache.has(path):
		return _tex_cache[path]
	if not ResourceLoader.exists(path):
		return null
	var t: Texture2D = load(path) as Texture2D
	if t:
		_tex_cache[path] = t
	return t
