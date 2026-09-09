extends PanelContainer

## Persistent Twitch / YouTube / TikTok concurrent-viewer panel.

const _FONT: FontFile = preload("res://assets/fonts/pixel_font.ttf")
const _SpdUi := preload("res://scripts/spd_ui_art.gd")
const _PLATFORMS := ["twitch", "youtube", "tiktok"]
const _SHORT := {"twitch": "TW", "youtube": "YT", "tiktok": "TT"}
const _BADGE_COLORS := {
	"twitch": Color("9146ff"),
	"youtube": Color("ff0033"),
	"tiktok": Color("00d4d8"),
}

var _margin: MarginContainer


func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	_margin = MarginContainer.new()
	_margin.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(_margin)
	CompanionConfig.settings_loaded.connect(_refresh)
	CompanionConfig.settings_saved.connect(_refresh)
	ViewerCountsState.counts_changed.connect(_refresh)
	get_viewport().size_changed.connect(_schedule_reposition)
	_refresh()


func _refresh() -> void:
	visible = CompanionConfig.element_enabled(self, "viewer_counts") and _has_enabled_platform()
	_apply_style()
	_rebuild_content()
	_schedule_reposition()


func _apply_style() -> void:
	add_theme_stylebox_override(
		"panel",
		_SpdUi.chrome_style(
			CompanionConfig.viewer_counts_chrome_style,
			CompanionConfig.viewer_counts_chrome_scale
		)
	)
	var pad_h := clampi(CompanionConfig.viewer_counts_padding_h_px, 0, 64)
	var pad_v := clampi(CompanionConfig.viewer_counts_padding_v_px, 0, 64)
	_margin.add_theme_constant_override("margin_left", pad_h)
	_margin.add_theme_constant_override("margin_right", pad_h)
	_margin.add_theme_constant_override("margin_top", pad_v)
	_margin.add_theme_constant_override("margin_bottom", pad_v)


func _rebuild_content() -> void:
	for child in _margin.get_children():
		_margin.remove_child(child)
		child.queue_free()
	var mode := CompanionConfig.viewer_counts_layout_mode
	var box: BoxContainer
	if mode == "stack":
		box = VBoxContainer.new()
	else:
		box = HBoxContainer.new()
	box.add_theme_constant_override("separation", 10)
	box.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_margin.add_child(box)

	if mode == "total":
		_add_total(box)
		return
	for platform in _PLATFORMS:
		if _platform_enabled(platform):
			_add_platform(box, platform)


func _add_platform(parent: BoxContainer, platform: String) -> void:
	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", 4)
	row.mouse_filter = Control.MOUSE_FILTER_IGNORE
	parent.add_child(row)
	if CompanionConfig.viewer_counts_show_icons:
		row.add_child(_make_badge(platform))
	var prefix := "" if CompanionConfig.viewer_counts_show_icons else "%s " % _SHORT[platform]
	row.add_child(_make_label(prefix + _count_text(platform)))


func _add_total(parent: BoxContainer) -> void:
	var total := 0
	var known := 0
	var any_stale := false
	for platform in _PLATFORMS:
		if not _platform_enabled(platform):
			continue
		any_stale = any_stale or ViewerCountsState.is_stale(platform)
		var count := ViewerCountsState.count_for(platform)
		if count >= 0:
			total += count
			known += 1
	if CompanionConfig.viewer_counts_show_icons:
		var badge := _make_badge("tiktok")
		var badge_label := badge.get_node("Label") as Label
		badge_label.text = "Σ"
		badge.color = Color(0.18, 0.2, 0.24, 1.0)
		parent.add_child(badge)
	var text := str(total) if known > 0 else "--"
	if any_stale:
		text += "*"
	parent.add_child(_make_label(text))


func _make_badge(platform: String) -> ColorRect:
	var badge := ColorRect.new()
	var size_px := maxi(12, CompanionConfig.viewer_counts_font_size_px)
	badge.custom_minimum_size = Vector2(size_px, size_px)
	badge.color = _BADGE_COLORS[platform]
	badge.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var label := Label.new()
	label.name = "Label"
	label.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	label.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	label.add_theme_font_override("font", _FONT)
	label.add_theme_font_size_override("font_size", maxi(8, size_px - 6))
	label.add_theme_color_override("font_color", Color.WHITE)
	label.add_theme_color_override("font_shadow_color", Color(0, 0, 0, 0.8))
	label.add_theme_constant_override("shadow_offset_x", 1)
	label.add_theme_constant_override("shadow_offset_y", 1)
	label.text = str(_SHORT[platform]).left(1)
	label.mouse_filter = Control.MOUSE_FILTER_IGNORE
	badge.add_child(label)
	return badge


func _make_label(text: String) -> Label:
	var label := Label.new()
	label.add_theme_font_override("font", _FONT)
	label.add_theme_font_size_override(
		"font_size", clampi(CompanionConfig.viewer_counts_font_size_px, 8, 64)
	)
	label.add_theme_color_override("font_color", CompanionConfig.viewer_counts_font_color)
	label.add_theme_color_override("font_shadow_color", Color(0, 0, 0, 0.65))
	label.add_theme_constant_override("shadow_offset_x", 1)
	label.add_theme_constant_override("shadow_offset_y", 1)
	label.text = text
	label.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return label


func _count_text(platform: String) -> String:
	var count := ViewerCountsState.count_for(platform)
	var text := str(count) if count >= 0 else "--"
	if ViewerCountsState.is_stale(platform):
		text += "*"
	return text


func _platform_enabled(platform: String) -> bool:
	match platform:
		"twitch":
			return CompanionConfig.viewer_counts_show_twitch
		"youtube":
			return CompanionConfig.viewer_counts_show_youtube
		"tiktok":
			return CompanionConfig.viewer_counts_show_tiktok
	return false


func _has_enabled_platform() -> bool:
	for platform in _PLATFORMS:
		if _platform_enabled(platform):
			return true
	return false


func _schedule_reposition() -> void:
	call_deferred("_reposition_to_corner")


func _reposition_to_corner() -> void:
	if not visible:
		return
	var rect := get_viewport().get_visible_rect()
	var layout := CompanionConfig.layout_data_for(self)
	var corner := clampi(layout.viewer_counts_corner, 0, 3)
	var mx: int = layout.viewer_counts_margin_x
	var my: int = layout.viewer_counts_margin_y
	reset_size()
	var panel_size := get_combined_minimum_size()
	var x := float(mx) if corner in [0, 2] else rect.size.x - float(mx) - panel_size.x
	var y := float(my) if corner in [0, 1] else rect.size.y - float(my) - panel_size.y
	position = Vector2(x, y)
	size = panel_size
