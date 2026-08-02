extends Control
## Empty SPD window chrome panels placed via Settings (pixel zone + scene flags).

const _SpdUi := preload("res://scripts/spd_ui_art.gd")

## True while OBS sync is connected and reporting a program scene.
var _obs_scene_known: bool = false
var _pause_active: bool = false
var _panels: Array = []


func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	CompanionConfig.settings_saved.connect(_rebuild)
	CompanionConfig.settings_loaded.connect(_rebuild)
	get_viewport().size_changed.connect(_apply_layouts)
	var obs := get_node_or_null("/root/ObsWebSocketClient")
	if obs:
		obs.pause_scene_active_changed.connect(_on_obs_pause_scene_active)
		obs.disconnected_from_obs.connect(_on_obs_disconnected)
	_rebuild()


func _on_obs_pause_scene_active(is_pause: bool) -> void:
	_obs_scene_known = true
	_pause_active = is_pause
	_update_visibility()


func _on_obs_disconnected() -> void:
	_obs_scene_known = false
	_pause_active = false
	_update_visibility()


func _rebuild() -> void:
	for child in get_children():
		child.queue_free()
	_panels.clear()
	for entry in CompanionConfig.chrome_boxes:
		if typeof(entry) != TYPE_DICTIONARY:
			continue
		var d: Dictionary = entry
		var panel := PanelContainer.new()
		panel.mouse_filter = Control.MOUSE_FILTER_IGNORE
		panel.add_theme_stylebox_override("panel", _style_for_entry(d))
		panel.set_meta("chrome_entry", d)
		add_child(panel)
		_panels.append(panel)
	_apply_layouts()
	_update_visibility()


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
	if not bool(d.get("enabled", true)):
		return false
	var on_live := bool(d.get("show_on_live", true))
	var on_pause := bool(d.get("show_on_pause", false))
	if not _obs_scene_known:
		# OBS sync off / disconnected: show if any scene checkbox is on.
		return on_live or on_pause
	return on_pause if _pause_active else on_live
