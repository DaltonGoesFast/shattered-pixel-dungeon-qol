extends RefCounted
## Resolves item / potion / scroll / ring icons from `assets/icons_sheet`, `assets/item_sheet`, then legacy `assets/itemicons`.
class_name CompanionItemIcons

const _DIR_ICONS := "res://assets/icons_sheet/"
const _DIR_ITEMS := "res://assets/item_sheet/"
const _DIR_LEGACY := "res://assets/itemicons/"
static var _tex_cache: Dictionary = {}  ## path String -> Texture2D

## When `PotionOf` / `ScrollOf` + [method _of_style_stem] does not match the PNG basename (abbreviations, etc.).
const _CLASS_STEM_ALIASES: Dictionary = {
	"PotionOfLiquidFlame": "potion_liqflame",
	"PotionOfMindVision": "potion_mindvis",
	"PotionOfParalyticGas": "potion_paragas",
	"PotionOfLevitation": "potion_levitate",
	"PotionOfInvisibility": "potion_invis",
	"PotionOfExperience": "potion_exp",
	"PotionOfCorrosiveGas": "potion_corrogas",
	"PotionOfCleansing": "potion_cleanse",
	"PotionOfDragonsBreath": "potion_drgbreath",
	"PotionOfCorrosiveBreath": "potion_drgbreath",
	"PotionOfToxicGas": "potion_toxicgas",
	"PotionOfStormClouds": "potion_strmcloud",
	"PotionOfShroudingFog": "potion_shroudfog",
	"PotionOfSnapFreeze": "potion_snapfreez",
	"PotionOfSnapfreeze": "potion_snapfreez",
	"PotionOfEarthroot": "potion_eartharmr",
	"PotionOfEarthenArmor": "potion_eartharmr",
	"PotionOfMagicalSight": "potion_magisight",
	"ScrollOfMagicMapping": "scroll_magicmap",
	"ScrollOfMirrorImage": "scroll_mirrorimg",
	"ScrollOfRemoveCurse": "scroll_remcurse",
	"ScrollOfTransmutation": "scroll_transmute",
	"ScrollOfRetribution": "scroll_retrib",
	"ScrollOfPsionicBlast": "scroll_psiblast",
	"ScrollOfMysticalEnergy": "scroll_mystenrg",
	"ScrollOfPrismaticImage": "scroll_prisimg",
	"ScrollOfDivination": "scroll_divinate",
	"RingOfSharpshooting": "ring_sharpshoot",
}


static func texture_for_item_class(cls: String) -> Texture2D:
	var c: String = cls.strip_edges()
	if c.is_empty():
		return null
	var cache_key: String = "|cls|%s" % c
	if _tex_cache.has(cache_key):
		return _tex_cache[cache_key]
	if _CLASS_STEM_ALIASES.has(c):
		var t0: Texture2D = _load_path(_DIR_ICONS + str(_CLASS_STEM_ALIASES[c]) + ".png")
		if t0:
			_tex_cache[cache_key] = t0
			return t0
	if c.find("Of") > 0:
		var stem: String = _of_style_stem(c)
		if not stem.is_empty():
			var t1: Texture2D = _load_path(_DIR_ICONS + stem + ".png")
			if t1:
				_tex_cache[cache_key] = t1
				return t1
			var t1b: Texture2D = _load_path(_DIR_ITEMS + stem + ".png")
			if t1b:
				_tex_cache[cache_key] = t1b
				return t1b
	var snake_full: String = _java_class_to_snake(c)
	if not snake_full.is_empty():
		var t2: Texture2D = _load_path(_DIR_ITEMS + snake_full + ".png")
		if t2:
			_tex_cache[cache_key] = t2
			return t2
	var t3: Texture2D = _load_path(_DIR_LEGACY + c + ".png")
	if t3:
		_tex_cache[cache_key] = t3
		return t3
	_tex_cache[cache_key] = null
	return null


static func _of_style_stem(java_class: String) -> String:
	var t: String = java_class.strip_edges()
	var i: int = t.find("Of")
	if i <= 0:
		return ""
	var head: String = t.substr(0, i).to_lower()
	var tail: String = t.substr(i + 2).strip_edges()
	if tail.is_empty():
		return ""
	var tail_snake: String = _camel_tail_to_snake(tail)
	if tail_snake.is_empty():
		return ""
	return "%s_%s" % [head, tail_snake]


static func _camel_tail_to_snake(tail: String) -> String:
	var r: String = ""
	var n: int = tail.length()
	for j in range(n):
		var ch: String = tail[j]
		if j > 0 and ch == ch.to_upper() and tail[j - 1] != " ":
			var prev: String = tail[j - 1]
			if prev != "_" and prev != prev.to_upper():
				r += "_"
		r += ch.to_lower()
	return r


static func _java_class_to_snake(name: String) -> String:
	var r: String = ""
	var n: int = name.length()
	for j in range(n):
		var ch: String = name[j]
		if j > 0 and ch == ch.to_upper() and name[j - 1] != "_":
			var prev: String = name[j - 1]
			if prev != "_" and prev != prev.to_upper():
				r += "_"
		r += ch.to_lower()
	return r


static func _load_path(path: String) -> Texture2D:
	if _tex_cache.has(path):
		var cached: Variant = _tex_cache[path]
		return cached as Texture2D
	if not ResourceLoader.exists(path):
		return null
	var t: Texture2D = load(path) as Texture2D
	if t:
		_tex_cache[path] = t
	return t
