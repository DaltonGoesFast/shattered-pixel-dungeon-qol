class_name LiveWaterArt
extends RefCounted

## Resolves SPD environment water textures for the live overlay (matches [code]Level.waterTex()[/code] / [code]Assets.Environment.WATER_*[/code]).

const ENV_DIR := "res://assets/environment/"
const DEFAULT_STEM := "water0"

static var _tex_cache: Dictionary = {}


static func texture_for_snapshot(snapshot: Dictionary) -> Texture2D:
	var stem := water_stem_from_snapshot(snapshot)
	return texture_for_stem(stem)


static func texture_for_stem(stem: String) -> Texture2D:
	var path := "%s%s.png" % [ENV_DIR, stem]
	if _tex_cache.has(path):
		return _tex_cache[path] as Texture2D
	var tex: Texture2D = load(path) as Texture2D
	if tex != null:
		_tex_cache[path] = tex
	else:
		push_warning("LiveWaterArt: missing %s" % path)
	return tex


static func water_stem_from_snapshot(snapshot: Dictionary) -> String:
	var from_tex := _stem_from_water_tex_field(snapshot)
	if not from_tex.is_empty():
		return from_tex
	var depth := depth_from_snapshot(snapshot)
	if depth > 0:
		return _stem_for_depth(depth)
	return DEFAULT_STEM


static func depth_from_snapshot(snapshot: Dictionary) -> int:
	if snapshot.is_empty():
		return 0
	var stats: Variant = snapshot.get("stats", null)
	if stats is Dictionary and stats.has("depth"):
		return int(stats["depth"])
	if snapshot.has("depth"):
		return int(snapshot["depth"])
	return 0


static func _stem_from_water_tex_field(snapshot: Dictionary) -> String:
	for key in ["water_tex", "water_texture", "waterTexture"]:
		var v: Variant = snapshot.get(key, null)
		if v == null:
			continue
		var stem := _stem_from_asset_path(str(v))
		if not stem.is_empty():
			return stem
	return ""


static func _stem_from_asset_path(path: String) -> String:
	var base := path.get_file().get_basename()
	if base.is_empty():
		return ""
	if base.begins_with("01water") or base.begins_with("water"):
		return base
	return ""


## Chapter bands: depths 1–5 → water0, 6–10 → water1, … (same as [code]1 + depth/5[/code] region in vanilla SPD).
static func _stem_for_depth(depth: int) -> String:
	var idx := clampi(int((depth - 1) / 5.0), 0, 4)
	return "water%d" % idx
