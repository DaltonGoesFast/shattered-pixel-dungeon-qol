extends PanelContainer

## Displays hero talents with icons and illuminated pip ranks matching the in-game UI.
## Only shows invested talents (points > 0), grouped in compact horizontal rows per tier.

const _SpdUiArt := preload("res://scripts/spd_ui_art.gd")

const _TALENT_ICON_DIR := "res://assets/talent_icons/"
const _FRAME_DIR := "res://assets/ui_spd/talent_button/"
const _TIERS: PackedStringArray = ["tier1", "tier2", "tier3", "tier4"]
const _TIER_MAX_POINTS: Dictionary = {
	"tier1": 2,
	"tier2": 2,
	"tier3": 3,
	"tier4": 4,
}
const _PIP_COLOR := Color(1.0, 1.0, 0.4, 1.0)  # 0xFFFFFF66 bright yellow

var _margin: MarginContainer
var _tiers_vbox: VBoxContainer
var _tier_rows: Dictionary = {}  # tier_name -> HBoxContainer
var _icon_cache: Dictionary = {}  # talent_name -> Texture2D
var _frame_cache: Dictionary = {}  # max_pts -> Texture2D
var _last_snapshot: Dictionary = {}


func _init() -> void:
	mouse_filter = Control.MOUSE_FILTER_PASS
	_setup_nodes()
	_preload_frames()


func _ready() -> void:
	var gws: Node = get_node_or_null("/root/GameWebSocketClient")
	if gws:
		if gws.has_signal("snapshot_received"):
			gws.snapshot_received.connect(_on_snapshot)
		if "last_game_snapshot" in gws and not gws.last_game_snapshot.is_empty():
			_last_snapshot = gws.last_game_snapshot.duplicate(true)

	var cfg: Node = _get_config()
	if cfg:
		if cfg.has_signal("settings_saved"):
			cfg.settings_saved.connect(_on_cfg)
		if cfg.has_signal("settings_loaded"):
			cfg.settings_loaded.connect(_on_cfg)
		if cfg.has_signal("preview_visibility_changed"):
			cfg.preview_visibility_changed.connect(_on_cfg)

	get_viewport().size_changed.connect(_schedule_reposition)
	_on_cfg()


func _get_config() -> Node:
	if not is_inside_tree():
		return null
	return get_node_or_null("/root/CompanionConfig")


func _setup_nodes() -> void:
	size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
	size_flags_vertical = Control.SIZE_SHRINK_BEGIN

	_margin = MarginContainer.new()
	_margin.mouse_filter = Control.MOUSE_FILTER_PASS
	_margin.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
	_margin.size_flags_vertical = Control.SIZE_SHRINK_BEGIN
	add_child(_margin)

	_tiers_vbox = VBoxContainer.new()
	_tiers_vbox.mouse_filter = Control.MOUSE_FILTER_PASS
	_tiers_vbox.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
	_tiers_vbox.size_flags_vertical = Control.SIZE_SHRINK_BEGIN
	_margin.add_child(_tiers_vbox)

	for tier in _TIERS:
		var row := HBoxContainer.new()
		row.mouse_filter = Control.MOUSE_FILTER_PASS
		row.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
		row.size_flags_vertical = Control.SIZE_SHRINK_BEGIN
		_tiers_vbox.add_child(row)
		_tier_rows[tier] = row


func _preload_frames() -> void:
	for i in range(1, 5):
		var path := _FRAME_DIR + ("talent_btn_maxpts_%d.png" % i)
		if ResourceLoader.exists(path):
			_frame_cache[i] = load(path)


func _get_frame_texture(max_pts: int) -> Texture2D:
	var clamped_pts := clampi(max_pts, 1, 4)
	if _frame_cache.has(clamped_pts):
		return _frame_cache[clamped_pts]
	var path := _FRAME_DIR + ("talent_btn_maxpts_%d.png" % clamped_pts)
	if ResourceLoader.exists(path):
		var tex: Texture2D = load(path)
		_frame_cache[clamped_pts] = tex
		return tex
	return null


func _get_talent_icon(talent_name: String) -> Texture2D:
	var key := talent_name.strip_edges().to_lower()
	if _icon_cache.has(key):
		return _icon_cache[key]
	var path := _TALENT_ICON_DIR + key + ".png"
	if ResourceLoader.exists(path):
		var tex: Texture2D = load(path)
		_icon_cache[key] = tex
		return tex
	return null


func _format_talent_name(raw: String) -> String:
	var parts := raw.strip_edges().to_lower().split("_")
	var formatted := []
	for p in parts:
		if not p.is_empty():
			formatted.append(p.capitalize())
	return " ".join(formatted)


func _create_talent_button(talent_name: String, points: int, max_pts: int) -> Control:
	var btn := Control.new()
	btn.custom_minimum_size = Vector2(20, 26)
	btn.mouse_filter = Control.MOUSE_FILTER_PASS
	btn.tooltip_text = "%s (%d/%d)" % [_format_talent_name(talent_name), points, max_pts]

	# 1. Pip fill color behind the button frame cutouts
	var bar_w := 16.0
	var applied_w := (float(points) / float(max_pts)) * bar_w
	var fill := ColorRect.new()
	fill.mouse_filter = Control.MOUSE_FILTER_IGNORE
	fill.color = _PIP_COLOR
	fill.position = Vector2(2, 19)
	fill.size = Vector2(applied_w, 5)
	fill.visible = points > 0
	btn.add_child(fill)

	# 2. Button frame image (20x26) with transparent pip apertures
	var frame := TextureRect.new()
	frame.mouse_filter = Control.MOUSE_FILTER_IGNORE
	frame.texture = _get_frame_texture(max_pts)
	frame.position = Vector2(0, 0)
	frame.size = Vector2(20, 26)
	frame.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	btn.add_child(frame)

	# 3. Talent icon (16x16)
	var icon := TextureRect.new()
	icon.mouse_filter = Control.MOUSE_FILTER_IGNORE
	icon.texture = _get_talent_icon(talent_name)
	icon.position = Vector2(2, 2)
	icon.size = Vector2(16, 16)
	icon.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	btn.add_child(icon)

	return btn


func _on_snapshot(data: Dictionary) -> void:
	_last_snapshot = data.duplicate(true)
	_refresh_display()


func _on_cfg() -> void:
	_apply_theme_and_layout()
	_refresh_display()


func _apply_theme_and_layout() -> void:
	var cfg: Node = _get_config()
	var chrome_style: String = str(cfg.talents_chrome_style) if cfg and "talents_chrome_style" in cfg else ""
	var chrome_scale: float = float(cfg.talents_chrome_scale) if cfg and "talents_chrome_scale" in cfg else 1.0
	var pad_h: int = int(cfg.talents_padding_h_px) if cfg and "talents_padding_h_px" in cfg else 4
	var pad_v: int = int(cfg.talents_padding_v_px) if cfg and "talents_padding_v_px" in cfg else 4
	var h_sep: int = int(cfg.talents_h_separation) if cfg and "talents_h_separation" in cfg else 2
	var v_sep: int = int(cfg.talents_v_separation) if cfg and "talents_v_separation" in cfg else 2

	# Chrome background panel style
	if chrome_style.is_empty() or chrome_style == "blank":
		var empty_style := StyleBoxEmpty.new()
		add_theme_stylebox_override("panel", empty_style)
	else:
		add_theme_stylebox_override(
			"panel",
			_SpdUiArt.chrome_style(chrome_style, chrome_scale)
		)

	# Margin padding
	_margin.add_theme_constant_override("margin_left", clampi(pad_h, 0, 64))
	_margin.add_theme_constant_override("margin_right", clampi(pad_h, 0, 64))
	_margin.add_theme_constant_override("margin_top", clampi(pad_v, 0, 64))
	_margin.add_theme_constant_override("margin_bottom", clampi(pad_v, 0, 64))

	# Separations
	_tiers_vbox.add_theme_constant_override("separation", clampi(v_sep, 0, 32))
	for tier in _TIERS:
		var row: HBoxContainer = _tier_rows.get(tier)
		if row:
			row.add_theme_constant_override("separation", clampi(h_sep, 0, 32))


func _refresh_display() -> void:
	var cfg: Node = _get_config()
	var enabled: bool = bool(cfg.talents_overlay_enabled) if cfg and "talents_overlay_enabled" in cfg else true
	if not enabled:
		visible = false
		return

	var talents_map: Dictionary = _last_snapshot.get("talents", {})
	var is_preview: bool = bool(cfg.preview_pin_for_element("talents")) if cfg and cfg.has_method("preview_pin_for_element") else false

	if talents_map.is_empty() and is_preview:
		talents_map = {
			"tier1": {"HEARTY_MEAL": 2, "IRON_WILL": 1},
			"tier2": {"INVIGORATING_MEAL": 2},
			"tier3": {"STRONGMAN": 3},
		}

	var total_invested := 0

	for tier in _TIERS:
		var row: HBoxContainer = _tier_rows.get(tier)
		if not row:
			continue

		for child in row.get_children():
			row.remove_child(child)
			child.queue_free()

		var tier_dict: Dictionary = talents_map.get(tier, {})
		var count := 0
		var max_pts: int = _TIER_MAX_POINTS.get(tier, 2)

		var sorted_keys := tier_dict.keys()
		sorted_keys.sort()

		for talent_key in sorted_keys:
			var pts: int = int(tier_dict[talent_key])
			if pts <= 0:
				continue
			var btn := _create_talent_button(str(talent_key), pts, max_pts)
			row.add_child(btn)
			count += 1
			total_invested += 1

		row.visible = count > 0

	if total_invested == 0:
		visible = false
	else:
		visible = true
		_schedule_reposition()


func _schedule_reposition() -> void:
	call_deferred("_reposition_to_corner")


func _reposition_to_corner() -> void:
	if not visible:
		return

	var r: Rect2 = get_viewport().get_visible_rect()
	var cfg: Node = _get_config()
	var mx: int = 16
	var my: int = 16
	var corner: int = 2
	var sc: float = 2.0

	if cfg:
		if cfg.has_method("layout_data_for"):
			var L = cfg.layout_data_for(self)
			if L:
				mx = L.talents_margin_x
				my = L.talents_margin_y
				corner = clampi(L.talents_corner, 0, 3)
		elif "talents_margin_x" in cfg:
			mx = cfg.talents_margin_x
			my = cfg.talents_margin_y
			corner = clampi(cfg.talents_corner, 0, 3)

		if "talents_scale" in cfg:
			sc = clampf(float(cfg.talents_scale), 0.5, 6.0)

	scale = Vector2(sc, sc)
	for tier in _TIERS:
		var row: HBoxContainer = _tier_rows.get(tier)
		if row:
			row.reset_size()
	_tiers_vbox.reset_size()
	_margin.reset_size()
	reset_size()

	var sz: Vector2 = get_combined_minimum_size()
	if sz.x < 2.0 or sz.y < 2.0:
		sz = size
	else:
		size = sz

	var effective_w := sz.x * sc
	var effective_h := sz.y * sc

	var x: float = 0.0
	var y: float = 0.0
	match corner:
		0:  # Top-left
			x = float(mx)
			y = float(my)
		1:  # Top-right
			x = r.size.x - float(mx) - effective_w
			y = float(my)
		2:  # Bottom-left
			x = float(mx)
			y = r.size.y - float(my) - effective_h
		_:  # Bottom-right
			x = r.size.x - float(mx) - effective_w
			y = r.size.y - float(my) - effective_h

	position = Vector2(x, y)
