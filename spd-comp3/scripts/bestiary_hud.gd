extends Control
## SPD-styled Bestiary HUD: StatusPane exp bar + Chrome panels for sprint/heat/hall.

const _SpdUi := preload("res://scripts/spd_ui_art.gd")

var _panel: PanelContainer
var _zone_label: Label
var _bar_holder: Control
var _exp_track: ColorRect
var _exp_fill: TextureRect
var _exp_fill_atlas: AtlasTexture
var _exp_text: Label
var _sprint_label: Label
var _heat_label: Label
var _hall_row: HBoxContainer
var _banner: TextureRect
var _banner_label: Label
var _banner_tween: Tween

var _last_payload: Dictionary = {}


func _ready() -> void:
	_build()
	BestiaryPollService.state_updated.connect(_on_state)
	BestiaryPollService.level_up.connect(_on_level_up)
	CompanionConfig.settings_saved.connect(_on_cfg)
	CompanionConfig.settings_loaded.connect(_on_cfg)
	get_viewport().size_changed.connect(_apply_layout)
	_apply_layout()
	_apply_visibility()
	if not BestiaryPollService.last_payload.is_empty():
		_on_state(BestiaryPollService.last_payload)


func _on_cfg() -> void:
	_apply_layout()
	_apply_visibility()
	_refresh_fonts()
	if not _last_payload.is_empty():
		_on_state(_last_payload)


func _format_header(level: int, zone: String) -> String:
	var fmt := CompanionConfig.bestiary_header_format.strip_edges()
	if fmt.is_empty():
		fmt = "Bestiary Lv {level} - {zone}"
	return fmt.format({"level": level, "zone": zone})


func _short_name(raw: String, max_len: int) -> String:
	var s := raw.strip_edges()
	if s.is_empty():
		return "-"
	if not CompanionConfig.bestiary_truncate_names or max_len <= 0:
		return s
	if s.length() <= max_len:
		return s
	return s.substr(0, maxi(1, max_len - 2)) + ".."


func _build() -> void:
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	clip_contents = false

	_panel = PanelContainer.new()
	_panel.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_panel.clip_contents = true
	_panel.add_theme_stylebox_override(
		"panel", _SpdUi.chrome_style_window(CompanionConfig.bestiary_chrome_scale)
	)
	add_child(_panel)

	var margin := MarginContainer.new()
	margin.add_theme_constant_override("margin_left", 8)
	margin.add_theme_constant_override("margin_right", 8)
	margin.add_theme_constant_override("margin_top", 6)
	margin.add_theme_constant_override("margin_bottom", 6)
	_panel.add_child(margin)

	var vbox := VBoxContainer.new()
	vbox.add_theme_constant_override("separation", 4)
	vbox.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	margin.add_child(vbox)

	_zone_label = Label.new()
	_zone_label.text = _format_header(1, "Sewers")
	_zone_label.add_theme_font_override("font", _SpdUi.hud_font())
	_SpdUi.apply_label_smooth(_zone_label)
	vbox.add_child(_zone_label)

	_bar_holder = Control.new()
	_bar_holder.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_sync_bar_holder_min_size()
	vbox.add_child(_bar_holder)

	_exp_track = ColorRect.new()
	# Flat track — stretched HP atlas end-caps looked spear-tipped at HUD widths.
	_exp_track.color = Color(0.22, 0.07, 0.07, 0.95)
	_exp_track.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_bar_holder.add_child(_exp_track)

	_exp_fill = TextureRect.new()
	_exp_fill_atlas = _SpdUi.exp_fill_texture(false)
	_exp_fill.texture = _exp_fill_atlas
	_exp_fill.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	_exp_fill.stretch_mode = TextureRect.STRETCH_SCALE
	_SpdUi.apply_nearest(_exp_fill)
	_bar_holder.add_child(_exp_fill)

	_exp_text = Label.new()
	_exp_text.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_exp_text.add_theme_font_override("font", _SpdUi.hud_font())
	_SpdUi.apply_label_smooth(_exp_text)
	vbox.add_child(_exp_text)

	var chips := HBoxContainer.new()
	chips.add_theme_constant_override("separation", 8)
	chips.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	vbox.add_child(chips)

	var sprint_panel := PanelContainer.new()
	sprint_panel.add_theme_stylebox_override("panel", _SpdUi.chrome_style_red_button())
	sprint_panel.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	chips.add_child(sprint_panel)
	_sprint_label = Label.new()
	_sprint_label.add_theme_font_override("font", _SpdUi.hud_font())
	_sprint_label.text = "Sprint: -"
	_sprint_label.clip_text = true
	_sprint_label.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	_SpdUi.apply_label_smooth(_sprint_label)
	sprint_panel.add_child(_sprint_label)

	var heat_panel := PanelContainer.new()
	heat_panel.add_theme_stylebox_override("panel", _SpdUi.chrome_style_red_button())
	heat_panel.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	chips.add_child(heat_panel)
	_heat_label = Label.new()
	_heat_label.add_theme_font_override("font", _SpdUi.hud_font())
	_heat_label.text = "Heat: -"
	_heat_label.clip_text = true
	_heat_label.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	_SpdUi.apply_label_smooth(_heat_label)
	heat_panel.add_child(_heat_label)

	_hall_row = HBoxContainer.new()
	_hall_row.add_theme_constant_override("separation", 4)
	_hall_row.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_hall_row.clip_contents = true
	vbox.add_child(_hall_row)

	# Banner on CanvasLayer so it centers on the full viewport (not the HUD zone).
	_banner = TextureRect.new()
	_banner.texture = _SpdUi.banner_boss_slain()
	_banner.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	_banner.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	_SpdUi.apply_nearest(_banner)
	_banner.visible = false
	_banner.modulate.a = 0.0
	_banner.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_banner.z_index = 20

	_banner_label = Label.new()
	_banner_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_banner_label.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	_banner_label.add_theme_font_override("font", _SpdUi.hud_font())
	_SpdUi.apply_label_smooth(_banner_label)
	_banner_label.visible = false
	_banner_label.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_banner_label.z_index = 21

	_mount_banner_on_layer()

	_bar_holder.resized.connect(_layout_exp_bar)
	_layout_exp_bar()


func _mount_banner_on_layer() -> void:
	var layer := get_parent()
	if layer == null:
		add_child(_banner)
		add_child(_banner_label)
		return
	if _banner.get_parent() != layer:
		if _banner.get_parent():
			_banner.get_parent().remove_child(_banner)
		layer.add_child(_banner)
	if _banner_label.get_parent() != layer:
		if _banner_label.get_parent():
			_banner_label.get_parent().remove_child(_banner_label)
		layer.add_child(_banner_label)


func _content_width() -> float:
	## Panel follows HUD zone width; exp bar fills that (bar width is a floor only).
	var zone_w := float(maxi(CompanionConfig.bestiary_zone_width_px, 64))
	var bar_w := float(clampi(CompanionConfig.bestiary_exp_bar_width_px, 64, 1600))
	return maxf(zone_w, bar_w + 24.0)


func _exp_native_size() -> Vector2:
	if CompanionConfig.bestiary_use_compact_exp_bar:
		return Vector2(17.0, 4.0)
	return Vector2(128.0, 7.0)


func _bar_draw_size(holder_w: float) -> Vector2:
	## Width/height follow settings; track is a ColorRect so wide bars stay rectangular.
	## Height uses the large StatusPane strip (7px) so compact fill art doesn't shrink the bar.
	var h_scale := clampf(CompanionConfig.bestiary_exp_bar_height_scale, 0.5, 4.0)
	var bar_h := maxf(4.0, 7.0 * h_scale)
	var bar_w := float(clampi(CompanionConfig.bestiary_exp_bar_width_px, 64, 1600))
	if holder_w > 1.0:
		bar_w = minf(bar_w, holder_w)
	return Vector2(maxf(32.0, bar_w), bar_h)


func _sync_bar_holder_min_size() -> void:
	if _bar_holder == null:
		return
	var draw := _bar_draw_size(0.0)
	var inner_w := maxf(draw.x, _content_width() - 24.0)
	_bar_holder.custom_minimum_size = Vector2(inner_w, draw.y)


func _layout_exp_bar(_unused: Variant = null) -> void:
	if _bar_holder == null or _exp_track == null or _exp_fill == null:
		return
	var holder_w := _bar_holder.size.x
	if holder_w < 1.0:
		holder_w = maxf(64.0, _content_width() - 24.0)
	var draw := _bar_draw_size(holder_w)
	var bar_w := draw.x
	var bar_h := draw.y
	var frac := 0.0
	if not _last_payload.is_empty():
		frac = clampf(float(_last_payload.get("bar_fraction", 0.0)), 0.0, 1.0)

	_exp_track.position = Vector2.ZERO
	_exp_track.size = Vector2(bar_w, bar_h)

	# Crop the left portion of the SPD exp strip (don't squash the rounded tip).
	var native := _exp_native_size()
	var src_w := maxf(1.0, native.x * frac) if frac > 0.0 else 0.0
	if _exp_fill_atlas and frac > 0.0:
		var base_region := (
			_SpdUi.EXP_COMPACT if CompanionConfig.bestiary_use_compact_exp_bar else _SpdUi.EXP_LARGE
		)
		_exp_fill_atlas.region = Rect2(
			float(base_region.position.x),
			float(base_region.position.y),
			src_w,
			float(base_region.size.y)
		)
		_exp_fill.texture = _exp_fill_atlas
	var fill_w := bar_w * frac
	_exp_fill.position = Vector2.ZERO
	_exp_fill.size = Vector2(fill_w, bar_h)
	_exp_fill.visible = fill_w >= 0.5


func _layout_banner(canvas: Vector2) -> void:
	if _banner == null or _banner_label == null:
		return
	_mount_banner_on_layer()
	var native := Vector2(127, 68)
	if _banner.texture:
		var ts := _banner.texture.get_size()
		if ts.x > 1.0 and ts.y > 1.0:
			native = ts
	var sc := clampf(CompanionConfig.bestiary_level_up_banner_scale, 0.25, 8.0)
	var bw := minf(native.x * sc, canvas.x * 0.95)
	var bh := native.y * sc
	if bw < native.x * sc and native.x > 0.0:
		bh = bw * (native.y / native.x)
	var bx := (canvas.x - bw) * 0.5
	var by := (canvas.y - bh) * 0.5 - 24.0
	_banner.position = Vector2(bx, by)
	_banner.size = Vector2(bw, bh)
	var fs := clampi(CompanionConfig.bestiary_level_up_banner_font_size_px, 8, 96)
	_banner_label.add_theme_font_size_override("font_size", fs)
	_banner_label.add_theme_color_override("font_color", CompanionConfig.bestiary_banner_font_color)
	var lw := minf(maxf(bw, canvas.x * 0.5), canvas.x * 0.9)
	var lh := float(maxi(28, fs + 16))
	_banner_label.position = Vector2((canvas.x - lw) * 0.5, by + bh + 4.0)
	_banner_label.size = Vector2(lw, lh)


func _apply_layout() -> void:
	var canvas := CompanionConfig.layout_canvas_size(self)
	CompanionConfig.apply_bestiary_zone_layout(self, canvas)
	var content_w := _content_width()
	var hud_s := clampf(CompanionConfig.bestiary_hud_scale, 0.5, 4.0)
	if _panel:
		# Layout size is unscaled; scale grows chrome/fonts/bar/icons together.
		_panel.custom_minimum_size = Vector2(content_w, 0)
		_panel.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
		_panel.scale = Vector2(hud_s, hud_s)
		_panel.add_theme_stylebox_override(
			"panel", _SpdUi.chrome_style_window(CompanionConfig.bestiary_chrome_scale)
		)
	_sync_bar_holder_min_size()
	_refresh_fonts()
	_exp_fill_atlas = _SpdUi.exp_fill_texture(CompanionConfig.bestiary_use_compact_exp_bar)
	_exp_fill.texture = _exp_fill_atlas
	_layout_exp_bar()
	_layout_banner(canvas)


func _apply_visibility() -> void:
	visible = CompanionConfig.bestiary_hud_enabled
	if _sprint_label and _sprint_label.get_parent():
		_sprint_label.get_parent().visible = CompanionConfig.bestiary_show_sprint_chip
	if _heat_label and _heat_label.get_parent():
		_heat_label.get_parent().visible = CompanionConfig.bestiary_show_heat_chip
	_hall_row.visible = CompanionConfig.bestiary_show_hall


func _hall_font_size() -> int:
	# Hall strip follows sprint/heat chip size (Settings > Bestiary > Sprint/heat font size).
	return clampi(CompanionConfig.bestiary_chip_font_size_px, 8, 96)


func _refresh_fonts() -> void:
	var zfs := clampi(CompanionConfig.bestiary_zone_font_size_px, 8, 96)
	var cfs := clampi(CompanionConfig.bestiary_chip_font_size_px, 8, 96)
	if _zone_label:
		_zone_label.add_theme_font_size_override("font_size", zfs)
		_zone_label.add_theme_color_override("font_color", CompanionConfig.bestiary_zone_font_color)
	if _exp_text:
		_exp_text.add_theme_font_size_override("font_size", maxi(8, zfs - 2))
		_exp_text.add_theme_color_override("font_color", CompanionConfig.bestiary_xp_font_color)
		_exp_text.visible = CompanionConfig.bestiary_show_xp_text
	if _sprint_label:
		_sprint_label.add_theme_font_size_override("font_size", cfs)
		_sprint_label.add_theme_color_override("font_color", CompanionConfig.bestiary_sprint_font_color)
	if _heat_label:
		_heat_label.add_theme_font_size_override("font_size", cfs)
		_heat_label.add_theme_color_override("font_color", CompanionConfig.bestiary_heat_font_color)
	if _banner_label:
		_banner_label.add_theme_font_size_override(
			"font_size", clampi(CompanionConfig.bestiary_level_up_banner_font_size_px, 8, 96)
		)
		_banner_label.add_theme_color_override("font_color", CompanionConfig.bestiary_banner_font_color)
	_apply_hall_fonts()


func _apply_hall_fonts() -> void:
	if _hall_row == null:
		return
	var hfs := _hall_font_size()
	var icon_side := maxi(14, hfs)
	for c in _hall_row.get_children():
		if c is Label:
			(c as Label).add_theme_font_size_override("font_size", hfs)
		elif c is TextureRect:
			(c as TextureRect).custom_minimum_size = Vector2(icon_side, icon_side)


func _on_state(payload: Dictionary) -> void:
	_last_payload = payload.duplicate(true)
	if not CompanionConfig.bestiary_hud_enabled:
		visible = false
		return
	visible = true
	var level := int(payload.get("level", 1))
	var zone := str(payload.get("zone", ""))
	_zone_label.text = _format_header(level, zone)
	var bar_xp := int(payload.get("bar_xp", 0))
	var thresh := int(payload.get("bar_threshold", 0))
	if thresh > 0:
		_exp_text.text = "%d / %d XP" % [bar_xp, thresh]
	else:
		_exp_text.text = "MAX"
	var chip_max := CompanionConfig.bestiary_chip_name_max_chars
	var sprint: Dictionary = payload.get("sprint", {})
	if typeof(sprint) == TYPE_DICTIONARY and sprint.get("has_leader"):
		_sprint_label.text = "Sprint: %s (%d)" % [
			_short_name(str(sprint.get("username", "")), chip_max),
			int(sprint.get("xp", 0)),
		]
	else:
		_sprint_label.text = "Sprint: -"
	var heat: Dictionary = payload.get("heat", {})
	if typeof(heat) == TYPE_DICTIONARY and heat.get("has_leader"):
		_heat_label.text = "Heat 2x: %s (%d)" % [
			_short_name(str(heat.get("username", "")), chip_max),
			int(heat.get("xp", 0)),
		]
	else:
		_heat_label.text = "Heat: -"
	_rebuild_hall(payload)
	_layout_exp_bar()


func _rebuild_hall(payload: Dictionary) -> void:
	for c in _hall_row.get_children():
		c.queue_free()
	var hall = payload.get("hall_of_fame", [])
	if typeof(hall) != TYPE_ARRAY:
		hall = []
	var entries: Array = []
	for entry in hall:
		if typeof(entry) == TYPE_DICTIONARY:
			entries.append(entry)
	# Current zone has no frozen hall entry until the next level-up (Halls never levels up).
	# Show live sprint leader for the active zone when missing.
	var cur_level := int(payload.get("level", 1))
	var cur_zone := str(payload.get("zone", ""))
	var has_current := false
	for entry in entries:
		if int(entry.get("level", -1)) == cur_level:
			has_current = true
			break
	if not has_current:
		var sprint: Dictionary = payload.get("sprint", {})
		if typeof(sprint) == TYPE_DICTIONARY and sprint.get("has_leader"):
			entries.append({
				"level": cur_level,
				"zone": cur_zone,
				"user": str(sprint.get("username", "")),
				"xp": int(sprint.get("xp", 0)),
				"live": true,
			})
	var hfs := _hall_font_size()
	var icon_side := maxi(14, hfs)
	var panel_w := _content_width()
	var slot_budget := maxf(48.0, (panel_w - 24.0) / 5.0)
	var i := 0
	for entry in entries:
		if typeof(entry) != TYPE_DICTIONARY:
			continue
		var cell := TextureRect.new()
		var zname := str(entry.get("zone", ""))
		var zlvl := int(entry.get("level", i + 1))
		cell.texture = _SpdUi.level_icon_for_zone(zname, zlvl)
		if cell.texture == null:
			cell.texture = _SpdUi.badge_cell(i)
		cell.custom_minimum_size = Vector2(icon_side, icon_side)
		cell.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		cell.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
		_SpdUi.apply_nearest(cell)
		_hall_row.add_child(cell)
		var lab := Label.new()
		lab.add_theme_font_override("font", _SpdUi.hud_font())
		lab.add_theme_font_size_override("font_size", hfs)
		lab.add_theme_color_override("font_color", CompanionConfig.bestiary_hall_font_color)
		lab.clip_text = true
		lab.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
		lab.custom_minimum_size = Vector2(maxi(28, int(slot_budget) - icon_side - 4), 0)
		_SpdUi.apply_label_smooth(lab)
		lab.text = _short_name(
			str(entry.get("user", "")), CompanionConfig.bestiary_hall_name_max_chars
		)
		_hall_row.add_child(lab)
		i += 1
		if i >= 5:
			break


func _on_level_up(payload: Dictionary) -> void:
	var zone := str(payload.get("zone", ""))
	var hall = payload.get("hall_of_fame", [])
	var winner := ""
	if typeof(hall) == TYPE_ARRAY and hall.size() > 0:
		var last = hall[hall.size() - 1]
		if typeof(last) == TYPE_DICTIONARY:
			winner = str(last.get("user", ""))
	var msg := "%s unlocked!" % zone
	if winner != "":
		msg += "  Sprint: %s" % _short_name(winner, CompanionConfig.bestiary_chip_name_max_chars)
	_show_banner(msg)


func _show_banner(text: String) -> void:
	_layout_banner(CompanionConfig.layout_canvas_size(self))
	_banner_label.text = text
	_banner.visible = true
	_banner_label.visible = true
	_banner.modulate.a = 0.0
	_banner_label.modulate.a = 0.0
	if _banner_tween and _banner_tween.is_valid():
		_banner_tween.kill()
	var dur := maxf(0.5, CompanionConfig.bestiary_level_up_banner_sec)
	_banner_tween = create_tween()
	_banner_tween.tween_property(_banner, "modulate:a", 1.0, 0.35)
	_banner_tween.parallel().tween_property(_banner_label, "modulate:a", 1.0, 0.35)
	_banner_tween.tween_interval(dur)
	_banner_tween.tween_property(_banner, "modulate:a", 0.0, 0.5)
	_banner_tween.parallel().tween_property(_banner_label, "modulate:a", 0.0, 0.5)
	_banner_tween.tween_callback(func () -> void:
		_banner.visible = false
		_banner_label.visible = false
	)
