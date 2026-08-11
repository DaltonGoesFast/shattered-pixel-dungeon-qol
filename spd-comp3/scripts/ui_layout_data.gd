extends RefCounted
class_name UiLayoutData
## Per-window layout zones, corners, live-water L, chrome boxes, and element toggles.

var alert_zone_x_px: int = 24
var alert_zone_y_px: int = 80
var alert_zone_width_px: int = 520
var alert_zone_height_px: int = 0
var alert_zone_bottom_margin_px: int = 24

var id_zone_x_px: int = 24
var id_zone_y_px: int = 188
var id_zone_width_px: int = 360
var id_zone_height_px: int = 0
var id_zone_bottom_margin_px: int = 24

var bestiary_zone_x_px: int = 16
var bestiary_zone_y_px: int = 16
var bestiary_zone_width_px: int = 420
var bestiary_zone_height_px: int = 0
var bestiary_zone_bottom_margin_px: int = 0

var paid_notice_zone_x_px: int = 560
var paid_notice_zone_y_px: int = 80
var paid_notice_zone_width_px: int = 800
var paid_notice_zone_height_px: int = 220
var paid_notice_zone_bottom_margin_px: int = 0

var live_water_bottom_bar_px: int = 200
var live_water_left_strip_px: int = 420
var live_water_left_strip_top_px: int = 0
var live_water_gradient_fade_start: float = 0.73
var live_water_gradient_fade_end: float = 1.0
## Soften top edge of bottom bar / left-strip top (px). 0 = hard cut.
var live_water_edge_feather_v_px: int = 0

var spend_indicator_corner: int = 1
var spend_indicator_margin_x: int = 16
var spend_indicator_margin_y: int = 16

var free_promos_corner: int = 2
var free_promos_margin_x: int = 16
var free_promos_margin_y: int = 64

var double_points_corner: int = 1
var double_points_margin_x: int = 16
var double_points_margin_y: int = 16

var chrome_boxes: Array = []

## Vertical-window element gates (main window ignores these; uses existing global enables).
var show_live_water: bool = true
var show_title: bool = true
var show_chrome_boxes: bool = true
var show_id_overlay: bool = true
var show_alerts: bool = true
var show_paid_notices: bool = true
var show_bestiary: bool = true
var show_summon_march: bool = true
var show_spend_indicator: bool = true
var show_free_promos: bool = true
var show_double_points: bool = true
## Vertical only: hide chat-spend badge while spend_disabled.txt exists.
var hide_spend_when_off: bool = false

## Per-element OBS scene gates: key → { "pause": bool, "main": bool, "other": bool }.
## Keys match CompanionConfig.element_enabled / stream_canvas layers.
var scene_show: Dictionary = {}


static func element_keys() -> PackedStringArray:
	return PackedStringArray(
		[
			"title",
			"live_water",
			"chrome_boxes",
			"id_overlay",
			"alerts",
			"paid_notices",
			"bestiary",
			"summon_march",
			"spend_indicator",
			"free_promos",
			"double_points",
		]
	)


static func element_label(key: String) -> String:
	match key:
		"title":
			return "Title backdrop"
		"live_water":
			return "Live water"
		"chrome_boxes":
			return "Chrome boxes"
		"id_overlay":
			return "ID overlay"
		"alerts":
			return "Alerts"
		"paid_notices":
			return "Paid notices"
		"bestiary":
			return "Bestiary"
		"summon_march":
			return "Summon march"
		"spend_indicator":
			return "Chat spend"
		"free_promos":
			return "Free promos"
		"double_points":
			return "2x points"
		_:
			return key


## Defaults match prior hardcoded OBS behavior (title on pause only; ID/bestiary/live off pause).
static func default_scene_show() -> Dictionary:
	var d := {}
	for key in element_keys():
		match key:
			"title":
				d[key] = {"pause": true, "main": false, "other": false}
			"live_water", "id_overlay", "bestiary":
				d[key] = {"pause": false, "main": true, "other": true}
			_:
				d[key] = {"pause": true, "main": true, "other": true}
	return d


static func normalize_scene_entry(raw: Variant, fallback: Dictionary) -> Dictionary:
	var base := {
		"pause": bool(fallback.get("pause", true)),
		"main": bool(fallback.get("main", true)),
		"other": bool(fallback.get("other", true)),
	}
	if typeof(raw) != TYPE_DICTIONARY:
		return base
	var r: Dictionary = raw
	base["pause"] = bool(r.get("pause", base["pause"]))
	base["main"] = bool(r.get("main", base["main"]))
	base["other"] = bool(r.get("other", base["other"]))
	return base


func ensure_scene_show() -> void:
	var defaults := default_scene_show()
	if scene_show.is_empty():
		scene_show = defaults.duplicate(true)
		return
	var merged: Dictionary = {}
	for key in element_keys():
		var fb: Dictionary = defaults.get(key, {"pause": true, "main": true, "other": true})
		merged[key] = normalize_scene_entry(scene_show.get(key, null), fb)
	scene_show = merged


func allows_scene(key: String, kind: StringName) -> bool:
	ensure_scene_show()
	var entry: Dictionary = scene_show.get(key, {"pause": true, "main": true, "other": true})
	match String(kind):
		"pause":
			return bool(entry.get("pause", true))
		"main":
			return bool(entry.get("main", true))
		"other":
			return bool(entry.get("other", true))
		_:
			# Unknown / disconnected: allow if any scene bucket is enabled.
			return (
				bool(entry.get("pause", false))
				or bool(entry.get("main", false))
				or bool(entry.get("other", false))
			)


static func default_vertical() -> UiLayoutData:
	var L := UiLayoutData.new()
	L.alert_zone_x_px = 140
	L.alert_zone_y_px = 720
	L.alert_zone_width_px = 800
	L.alert_zone_height_px = 0
	L.alert_zone_bottom_margin_px = 24
	L.id_zone_x_px = 40
	L.id_zone_y_px = 1600
	L.id_zone_width_px = 1000
	L.id_zone_height_px = 0
	L.id_zone_bottom_margin_px = 24
	L.bestiary_zone_x_px = 40
	L.bestiary_zone_y_px = 1480
	L.bestiary_zone_width_px = 1000
	L.bestiary_zone_height_px = 0
	L.bestiary_zone_bottom_margin_px = 0
	L.paid_notice_zone_x_px = 140
	L.paid_notice_zone_y_px = 420
	L.paid_notice_zone_width_px = 800
	L.paid_notice_zone_height_px = 260
	L.paid_notice_zone_bottom_margin_px = 0
	L.live_water_bottom_bar_px = 280
	L.live_water_left_strip_px = 0
	L.live_water_left_strip_top_px = 0
	L.live_water_gradient_fade_start = 0.55
	L.live_water_gradient_fade_end = 1.0
	L.live_water_edge_feather_v_px = 0
	L.spend_indicator_corner = 1
	L.spend_indicator_margin_x = 16
	L.spend_indicator_margin_y = 16
	L.hide_spend_when_off = true
	L.free_promos_corner = 3
	L.free_promos_margin_x = 16
	L.free_promos_margin_y = 100
	L.double_points_corner = 1
	L.double_points_margin_x = 16
	L.double_points_margin_y = 120
	L.chrome_boxes = []
	L.scene_show = default_scene_show()
	return L


func duplicate_deep() -> UiLayoutData:
	var L := UiLayoutData.new()
	L.copy_from(self)
	return L


func copy_from(other: UiLayoutData) -> void:
	alert_zone_x_px = other.alert_zone_x_px
	alert_zone_y_px = other.alert_zone_y_px
	alert_zone_width_px = other.alert_zone_width_px
	alert_zone_height_px = other.alert_zone_height_px
	alert_zone_bottom_margin_px = other.alert_zone_bottom_margin_px
	id_zone_x_px = other.id_zone_x_px
	id_zone_y_px = other.id_zone_y_px
	id_zone_width_px = other.id_zone_width_px
	id_zone_height_px = other.id_zone_height_px
	id_zone_bottom_margin_px = other.id_zone_bottom_margin_px
	bestiary_zone_x_px = other.bestiary_zone_x_px
	bestiary_zone_y_px = other.bestiary_zone_y_px
	bestiary_zone_width_px = other.bestiary_zone_width_px
	bestiary_zone_height_px = other.bestiary_zone_height_px
	bestiary_zone_bottom_margin_px = other.bestiary_zone_bottom_margin_px
	paid_notice_zone_x_px = other.paid_notice_zone_x_px
	paid_notice_zone_y_px = other.paid_notice_zone_y_px
	paid_notice_zone_width_px = other.paid_notice_zone_width_px
	paid_notice_zone_height_px = other.paid_notice_zone_height_px
	paid_notice_zone_bottom_margin_px = other.paid_notice_zone_bottom_margin_px
	live_water_bottom_bar_px = other.live_water_bottom_bar_px
	live_water_left_strip_px = other.live_water_left_strip_px
	live_water_left_strip_top_px = other.live_water_left_strip_top_px
	live_water_gradient_fade_start = other.live_water_gradient_fade_start
	live_water_gradient_fade_end = other.live_water_gradient_fade_end
	live_water_edge_feather_v_px = other.live_water_edge_feather_v_px
	spend_indicator_corner = other.spend_indicator_corner
	spend_indicator_margin_x = other.spend_indicator_margin_x
	spend_indicator_margin_y = other.spend_indicator_margin_y
	free_promos_corner = other.free_promos_corner
	free_promos_margin_x = other.free_promos_margin_x
	free_promos_margin_y = other.free_promos_margin_y
	double_points_corner = other.double_points_corner
	double_points_margin_x = other.double_points_margin_x
	double_points_margin_y = other.double_points_margin_y
	chrome_boxes = []
	for entry in other.chrome_boxes:
		if typeof(entry) == TYPE_DICTIONARY:
			chrome_boxes.append((entry as Dictionary).duplicate(true))
		else:
			chrome_boxes.append(entry)
	show_live_water = other.show_live_water
	show_title = other.show_title
	show_chrome_boxes = other.show_chrome_boxes
	show_id_overlay = other.show_id_overlay
	show_alerts = other.show_alerts
	show_paid_notices = other.show_paid_notices
	show_bestiary = other.show_bestiary
	show_summon_march = other.show_summon_march
	show_spend_indicator = other.show_spend_indicator
	show_free_promos = other.show_free_promos
	show_double_points = other.show_double_points
	hide_spend_when_off = other.hide_spend_when_off
	scene_show = {}
	other.ensure_scene_show()
	for key in other.scene_show.keys():
		var v: Variant = other.scene_show[key]
		if typeof(v) == TYPE_DICTIONARY:
			scene_show[key] = (v as Dictionary).duplicate(true)
	ensure_scene_show()


## JSON-safe layout payload for Flask / HTML remote settings (no Color objects).
func to_remote_dict() -> Dictionary:
	ensure_scene_show()
	var ss: Dictionary = {}
	for key in scene_show.keys():
		var entry: Variant = scene_show[key]
		if typeof(entry) == TYPE_DICTIONARY:
			ss[key] = (entry as Dictionary).duplicate(true)
	return {
		"alert_zone_x_px": alert_zone_x_px,
		"alert_zone_y_px": alert_zone_y_px,
		"alert_zone_width_px": alert_zone_width_px,
		"alert_zone_height_px": alert_zone_height_px,
		"alert_zone_bottom_margin_px": alert_zone_bottom_margin_px,
		"id_zone_x_px": id_zone_x_px,
		"id_zone_y_px": id_zone_y_px,
		"id_zone_width_px": id_zone_width_px,
		"id_zone_height_px": id_zone_height_px,
		"id_zone_bottom_margin_px": id_zone_bottom_margin_px,
		"bestiary_zone_x_px": bestiary_zone_x_px,
		"bestiary_zone_y_px": bestiary_zone_y_px,
		"bestiary_zone_width_px": bestiary_zone_width_px,
		"bestiary_zone_height_px": bestiary_zone_height_px,
		"bestiary_zone_bottom_margin_px": bestiary_zone_bottom_margin_px,
		"paid_notice_zone_x_px": paid_notice_zone_x_px,
		"paid_notice_zone_y_px": paid_notice_zone_y_px,
		"paid_notice_zone_width_px": paid_notice_zone_width_px,
		"paid_notice_zone_height_px": paid_notice_zone_height_px,
		"paid_notice_zone_bottom_margin_px": paid_notice_zone_bottom_margin_px,
		"live_water_bottom_bar_px": live_water_bottom_bar_px,
		"live_water_left_strip_px": live_water_left_strip_px,
		"live_water_left_strip_top_px": live_water_left_strip_top_px,
		"live_water_gradient_fade_start": live_water_gradient_fade_start,
		"live_water_gradient_fade_end": live_water_gradient_fade_end,
		"live_water_edge_feather_v_px": live_water_edge_feather_v_px,
		"spend_indicator_corner": spend_indicator_corner,
		"spend_indicator_margin_x": spend_indicator_margin_x,
		"spend_indicator_margin_y": spend_indicator_margin_y,
		"free_promos_corner": free_promos_corner,
		"free_promos_margin_x": free_promos_margin_x,
		"free_promos_margin_y": free_promos_margin_y,
		"double_points_corner": double_points_corner,
		"double_points_margin_x": double_points_margin_x,
		"double_points_margin_y": double_points_margin_y,
		"chrome_boxes": chrome_boxes.duplicate(true),
		"show_live_water": show_live_water,
		"show_title": show_title,
		"show_chrome_boxes": show_chrome_boxes,
		"show_id_overlay": show_id_overlay,
		"show_alerts": show_alerts,
		"show_paid_notices": show_paid_notices,
		"show_bestiary": show_bestiary,
		"show_summon_march": show_summon_march,
		"show_spend_indicator": show_spend_indicator,
		"show_free_promos": show_free_promos,
		"show_double_points": show_double_points,
		"hide_spend_when_off": hide_spend_when_off,
		"scene_show": ss,
	}


func apply_remote_dict(raw: Dictionary) -> void:
	if raw.is_empty():
		return
	if raw.has("alert_zone_x_px"):
		alert_zone_x_px = int(raw["alert_zone_x_px"])
	if raw.has("alert_zone_y_px"):
		alert_zone_y_px = int(raw["alert_zone_y_px"])
	if raw.has("alert_zone_width_px"):
		alert_zone_width_px = int(raw["alert_zone_width_px"])
	if raw.has("alert_zone_height_px"):
		alert_zone_height_px = int(raw["alert_zone_height_px"])
	if raw.has("alert_zone_bottom_margin_px"):
		alert_zone_bottom_margin_px = int(raw["alert_zone_bottom_margin_px"])
	if raw.has("id_zone_x_px"):
		id_zone_x_px = int(raw["id_zone_x_px"])
	if raw.has("id_zone_y_px"):
		id_zone_y_px = int(raw["id_zone_y_px"])
	if raw.has("id_zone_width_px"):
		id_zone_width_px = int(raw["id_zone_width_px"])
	if raw.has("id_zone_height_px"):
		id_zone_height_px = int(raw["id_zone_height_px"])
	if raw.has("id_zone_bottom_margin_px"):
		id_zone_bottom_margin_px = int(raw["id_zone_bottom_margin_px"])
	if raw.has("bestiary_zone_x_px"):
		bestiary_zone_x_px = int(raw["bestiary_zone_x_px"])
	if raw.has("bestiary_zone_y_px"):
		bestiary_zone_y_px = int(raw["bestiary_zone_y_px"])
	if raw.has("bestiary_zone_width_px"):
		bestiary_zone_width_px = int(raw["bestiary_zone_width_px"])
	if raw.has("bestiary_zone_height_px"):
		bestiary_zone_height_px = int(raw["bestiary_zone_height_px"])
	if raw.has("bestiary_zone_bottom_margin_px"):
		bestiary_zone_bottom_margin_px = int(raw["bestiary_zone_bottom_margin_px"])
	if raw.has("paid_notice_zone_x_px"):
		paid_notice_zone_x_px = int(raw["paid_notice_zone_x_px"])
	if raw.has("paid_notice_zone_y_px"):
		paid_notice_zone_y_px = int(raw["paid_notice_zone_y_px"])
	if raw.has("paid_notice_zone_width_px"):
		paid_notice_zone_width_px = int(raw["paid_notice_zone_width_px"])
	if raw.has("paid_notice_zone_height_px"):
		paid_notice_zone_height_px = int(raw["paid_notice_zone_height_px"])
	if raw.has("paid_notice_zone_bottom_margin_px"):
		paid_notice_zone_bottom_margin_px = int(raw["paid_notice_zone_bottom_margin_px"])
	if raw.has("live_water_bottom_bar_px"):
		live_water_bottom_bar_px = clampi(int(raw["live_water_bottom_bar_px"]), 0, 8192)
	if raw.has("live_water_left_strip_px"):
		live_water_left_strip_px = clampi(int(raw["live_water_left_strip_px"]), 0, 8192)
	if raw.has("live_water_left_strip_top_px"):
		live_water_left_strip_top_px = clampi(int(raw["live_water_left_strip_top_px"]), 0, 8192)
	if raw.has("live_water_gradient_fade_start"):
		live_water_gradient_fade_start = float(raw["live_water_gradient_fade_start"])
	if raw.has("live_water_gradient_fade_end"):
		live_water_gradient_fade_end = float(raw["live_water_gradient_fade_end"])
	if raw.has("live_water_edge_feather_v_px"):
		live_water_edge_feather_v_px = clampi(int(raw["live_water_edge_feather_v_px"]), 0, 512)
	if raw.has("spend_indicator_corner"):
		spend_indicator_corner = clampi(int(raw["spend_indicator_corner"]), 0, 3)
	if raw.has("spend_indicator_margin_x"):
		spend_indicator_margin_x = int(raw["spend_indicator_margin_x"])
	if raw.has("spend_indicator_margin_y"):
		spend_indicator_margin_y = int(raw["spend_indicator_margin_y"])
	if raw.has("free_promos_corner"):
		free_promos_corner = clampi(int(raw["free_promos_corner"]), 0, 3)
	if raw.has("free_promos_margin_x"):
		free_promos_margin_x = int(raw["free_promos_margin_x"])
	if raw.has("free_promos_margin_y"):
		free_promos_margin_y = int(raw["free_promos_margin_y"])
	if raw.has("double_points_corner"):
		double_points_corner = clampi(int(raw["double_points_corner"]), 0, 3)
	if raw.has("double_points_margin_x"):
		double_points_margin_x = int(raw["double_points_margin_x"])
	if raw.has("double_points_margin_y"):
		double_points_margin_y = int(raw["double_points_margin_y"])
	if raw.has("chrome_boxes") and typeof(raw["chrome_boxes"]) == TYPE_ARRAY:
		chrome_boxes = (raw["chrome_boxes"] as Array).duplicate(true)
	if raw.has("show_live_water"):
		show_live_water = bool(raw["show_live_water"])
	if raw.has("show_title"):
		show_title = bool(raw["show_title"])
	if raw.has("show_chrome_boxes"):
		show_chrome_boxes = bool(raw["show_chrome_boxes"])
	if raw.has("show_id_overlay"):
		show_id_overlay = bool(raw["show_id_overlay"])
	if raw.has("show_alerts"):
		show_alerts = bool(raw["show_alerts"])
	if raw.has("show_paid_notices"):
		show_paid_notices = bool(raw["show_paid_notices"])
	if raw.has("show_bestiary"):
		show_bestiary = bool(raw["show_bestiary"])
	if raw.has("show_summon_march"):
		show_summon_march = bool(raw["show_summon_march"])
	if raw.has("show_spend_indicator"):
		show_spend_indicator = bool(raw["show_spend_indicator"])
	if raw.has("show_free_promos"):
		show_free_promos = bool(raw["show_free_promos"])
	if raw.has("show_double_points"):
		show_double_points = bool(raw["show_double_points"])
	if raw.has("hide_spend_when_off"):
		hide_spend_when_off = bool(raw["hide_spend_when_off"])
	if raw.has("scene_show") and typeof(raw["scene_show"]) == TYPE_DICTIONARY:
		scene_show = (raw["scene_show"] as Dictionary).duplicate(true)
	ensure_scene_show()
