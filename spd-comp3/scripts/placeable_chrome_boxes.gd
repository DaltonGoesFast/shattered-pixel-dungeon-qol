extends Control
## Placeable SPD chrome panels (optional header + body text) via Settings.

const _SpdUi := preload("res://scripts/spd_ui_art.gd")
const _FONT: FontFile = preload("res://assets/fonts/pixel_font.ttf")

## True while OBS sync is connected and reporting a program scene.
var _obs_scene_known: bool = false
var _scene_kind: StringName = CompanionConfig.SCENE_UNKNOWN
var _panels: Array = []


func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	CompanionConfig.settings_saved.connect(_rebuild)
	CompanionConfig.settings_loaded.connect(_rebuild)
	get_viewport().size_changed.connect(_apply_layouts)
	var obs := get_node_or_null("/root/ObsWebSocketClient")
	if obs:
		if obs.has_signal("program_scene_kind_changed"):
			obs.program_scene_kind_changed.connect(_on_obs_scene_kind)
		else:
			obs.pause_scene_active_changed.connect(_on_obs_pause_scene_active)
		obs.disconnected_from_obs.connect(_on_obs_disconnected)
	_rebuild()


func _on_obs_scene_kind(kind: StringName) -> void:
	_obs_scene_known = true
	_scene_kind = kind
	_update_visibility()


func _on_obs_pause_scene_active(is_pause: bool) -> void:
	_obs_scene_known = true
	_scene_kind = CompanionConfig.SCENE_PAUSE if is_pause else CompanionConfig.SCENE_MAIN
	_update_visibility()


func _on_obs_disconnected() -> void:
	_obs_scene_known = false
	_scene_kind = CompanionConfig.SCENE_UNKNOWN
	_update_visibility()


func _rebuild() -> void:
	for child in get_children():
		child.queue_free()
	_panels.clear()
	for entry in CompanionConfig.chrome_boxes_for(self):
		if typeof(entry) != TYPE_DICTIONARY:
			continue
		var d: Dictionary = CompanionConfig.normalize_chrome_box(entry)
		var panel := PanelContainer.new()
		panel.mouse_filter = Control.MOUSE_FILTER_IGNORE
		panel.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
		panel.add_theme_stylebox_override("panel", _style_for_entry(d))
		panel.set_meta("chrome_entry", d)
		_build_text_content(panel, d)
		add_child(panel)
		_panels.append(panel)
	_apply_layouts()
	_update_visibility()


func _build_text_content(panel: PanelContainer, d: Dictionary) -> void:
	var margin := MarginContainer.new()
	margin.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var pad_h := clampi(int(d.get("padding_h_px", 14)), 0, 64)
	var pad_v := clampi(int(d.get("padding_v_px", 10)), 0, 64)
	margin.add_theme_constant_override("margin_left", pad_h)
	margin.add_theme_constant_override("margin_right", pad_h)
	margin.add_theme_constant_override("margin_top", pad_v)
	margin.add_theme_constant_override("margin_bottom", pad_v)
	panel.add_child(margin)

	var vbox := VBoxContainer.new()
	vbox.mouse_filter = Control.MOUSE_FILTER_IGNORE
	vbox.add_theme_constant_override(
		"separation", clampi(int(d.get("line_separation_px", 6)), 0, 48)
	)
	margin.add_child(vbox)

	var header_text := str(d.get("header_text", ""))
	var body_text := str(d.get("body_text", ""))
	var align := (
		HORIZONTAL_ALIGNMENT_CENTER
		if str(d.get("text_align", "left")) == "center"
		else HORIZONTAL_ALIGNMENT_LEFT
	)
	var shadow := bool(d.get("text_shadow", true))

	var header := Label.new()
	header.mouse_filter = Control.MOUSE_FILTER_IGNORE
	header.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	header.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	header.text = header_text
	header.visible = not header_text.strip_edges().is_empty()
	_style_label(
		header,
		_snap_pixel_font_size(int(d.get("header_font_size_px", 24))),
		d.get("header_font_color", Color(1.0, 1.0, 0.27, 1.0)) as Color,
		align,
		shadow
	)
	vbox.add_child(header)

	var body := Label.new()
	body.mouse_filter = Control.MOUSE_FILTER_IGNORE
	body.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	body.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	body.text = body_text
	body.visible = not body_text.strip_edges().is_empty()
	_style_label(
		body,
		_snap_pixel_font_size(int(d.get("body_font_size_px", 16))),
		d.get("body_font_color", Color(0.95, 0.92, 0.85, 1.0)) as Color,
		align,
		shadow
	)
	vbox.add_child(body)


func _snap_pixel_font_size(fs: int) -> int:
	var n := clampi(fs, 8, 96)
	if n % 8 == 0:
		return n
	if n % 2 != 0:
		n += 1
	return clampi(n, 8, 96)


func _style_label(
	lab: Label, fs: int, color: Color, align: HorizontalAlignment, shadow: bool
) -> void:
	if lab == null:
		return
	lab.add_theme_font_override("font", _FONT)
	lab.add_theme_font_size_override("font_size", fs)
	lab.add_theme_color_override("font_color", color)
	lab.horizontal_alignment = align
	if shadow:
		lab.add_theme_color_override("font_shadow_color", Color(0, 0, 0, 0.85))
		lab.add_theme_constant_override("shadow_offset_x", 1)
		lab.add_theme_constant_override("shadow_offset_y", 1)
	else:
		lab.add_theme_color_override("font_shadow_color", Color(0, 0, 0, 0))
		lab.add_theme_constant_override("shadow_offset_x", 0)
		lab.add_theme_constant_override("shadow_offset_y", 0)


func _style_for_entry(d: Dictionary) -> StyleBoxTexture:
	return _SpdUi.chrome_style(
		str(d.get("style", "window")), float(d.get("chrome_scale", 1.0))
	)


func _apply_layouts() -> void:
	var canvas := CompanionConfig.layout_canvas_size(self)
	for panel in _panels:
		if not is_instance_valid(panel):
			continue
		var d: Dictionary = panel.get_meta("chrome_entry")
		CompanionConfig.apply_pixel_zone_layout(
			panel,
			canvas,
			int(d.get("zone_x_px", 16)),
			int(d.get("zone_y_px", 16)),
			int(d.get("zone_width_px", 200)),
			int(d.get("zone_height_px", 120)),
			int(d.get("zone_bottom_margin_px", 0))
		)
		panel.add_theme_stylebox_override("panel", _style_for_entry(d))


func _update_visibility() -> void:
	for panel in _panels:
		if not is_instance_valid(panel):
			continue
		var d: Dictionary = panel.get_meta("chrome_entry")
		panel.visible = _entry_visible(d)


func _entry_visible(d: Dictionary) -> bool:
	if not CompanionConfig.element_enabled(self, "chrome_boxes"):
		return false
	if not bool(d.get("enabled", true)):
		return false
	var on_pause := bool(d.get("show_on_pause", false))
	var on_main := bool(d.get("show_on_main", d.get("show_on_live", true)))
	var on_other := bool(d.get("show_on_other", d.get("show_on_live", true)))
	if not _obs_scene_known:
		return on_pause or on_main or on_other
	match _scene_kind:
		CompanionConfig.SCENE_PAUSE:
			return on_pause
		CompanionConfig.SCENE_MAIN:
			return on_main
		CompanionConfig.SCENE_OTHER:
			return on_other
		_:
			return on_pause or on_main or on_other
