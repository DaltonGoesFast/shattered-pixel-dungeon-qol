extends Control
## Custom tip/reminder toasts (settings rotation + Streamer.bot UDP ui=tip).
## Reuses alert zone/chrome; yields while AlertPresenter command toasts are busy.

const _SpdUi := preload("res://scripts/spd_ui_art.gd")
const _FONT := preload("res://assets/fonts/pixel_font.ttf")

const TIP_UI_KINDS := ["tip", "custom_alert", "reminder"]

var _slot: Control
var _row: Control
var _panel: PanelContainer
var _margin: MarginContainer
var _vbox: VBoxContainer
var _title: Label
var _subtitle: Label

var _queue: Array[Dictionary] = []
var _playing: bool = false
var _rotate_idx: int = 0
var _idle_accum: float = 0.0
var _alerts: Node = null


func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	_build()
	call_deferred("_bind_alerts")
	StreamerBotUdp.ui_event.connect(_on_ui_event)
	CompanionConfig.settings_saved.connect(_on_cfg)
	CompanionConfig.settings_loaded.connect(_on_cfg)
	get_viewport().size_changed.connect(_apply_layout)
	_on_cfg()
	set_process(true)


func _bind_alerts() -> void:
	# Sibling canvas layer (tips are under CanvasLayerTipToasts; alerts under CanvasLayerAlerts).
	var root := get_parent().get_parent() if get_parent() else null
	if root:
		_alerts = root.get_node_or_null("CanvasLayerAlerts/AlertPresenter")
	if _alerts == null:
		_alerts = get_node_or_null("../CanvasLayerAlerts/AlertPresenter")
	if _alerts and _alerts.has_signal("busy_changed"):
		if not _alerts.busy_changed.is_connected(_on_alerts_busy_changed):
			_alerts.busy_changed.connect(_on_alerts_busy_changed)


func _build() -> void:
	_slot = Control.new()
	_slot.name = "TipToastSlot"
	_slot.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_slot.clip_contents = false
	add_child(_slot)

	_row = Control.new()
	_row.name = "TipToastRow"
	_row.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_row.set_anchors_preset(Control.PRESET_TOP_WIDE)
	_row.offset_bottom = 140.0
	_row.modulate.a = 0.0
	_slot.add_child(_row)

	_panel = PanelContainer.new()
	_panel.name = "Panel"
	_panel.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_panel.set_anchors_and_offsets_preset(Control.PRESET_TOP_LEFT)
	_panel.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	_row.add_child(_panel)

	_margin = MarginContainer.new()
	_margin.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_panel.add_child(_margin)

	_vbox = VBoxContainer.new()
	_vbox.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_margin.add_child(_vbox)

	_title = Label.new()
	_title.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_title.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	_subtitle = Label.new()
	_subtitle.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_subtitle.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	_vbox.add_child(_title)
	_vbox.add_child(_subtitle)


func _on_cfg() -> void:
	_apply_chrome()
	_apply_padding()
	_apply_fonts()
	_apply_layout()
	_idle_accum = 0.0


func _on_alerts_busy_changed(_busy: bool) -> void:
	if not _command_alerts_busy() and not _playing and not _queue.is_empty():
		_play_next()


func _command_alerts_busy() -> bool:
	if _alerts == null:
		return false
	if _alerts.has_method("is_busy"):
		return bool(_alerts.call("is_busy"))
	return false


func _process(delta: float) -> void:
	if not CompanionConfig.custom_alerts_enabled:
		_idle_accum = 0.0
		return
	if _playing or _command_alerts_busy() or not _queue.is_empty():
		_idle_accum = 0.0
		return
	var enabled_tips := _enabled_tips()
	if enabled_tips.is_empty():
		_idle_accum = 0.0
		return
	_idle_accum += delta
	var interval := maxf(5.0, CompanionConfig.custom_alerts_interval_sec)
	if _idle_accum < interval:
		return
	_idle_accum = 0.0
	var tip: Dictionary = enabled_tips[_rotate_idx % enabled_tips.size()]
	_rotate_idx = (_rotate_idx + 1) % enabled_tips.size()
	_enqueue(
		{
			"title": str(tip.get("title", "")),
			"subtitle": str(tip.get("subtitle", "")),
			"hold": CompanionConfig.custom_alerts_hold_sec,
		}
	)


func _enabled_tips() -> Array[Dictionary]:
	var out: Array[Dictionary] = []
	for raw in CompanionConfig.custom_alerts:
		var tip := CompanionConfig.normalize_custom_alert(raw)
		if not bool(tip.get("enabled", true)):
			continue
		if str(tip.get("title", "")).strip_edges().is_empty() and str(tip.get("subtitle", "")).strip_edges().is_empty():
			continue
		out.append(tip)
	return out


func _on_ui_event(data: Dictionary) -> void:
	var kind := str(data.get("ui", data.get("kind", ""))).strip_edges().to_lower()
	if kind not in TIP_UI_KINDS:
		return
	var title := str(data.get("title", data.get("headline", ""))).strip_edges()
	var subtitle := str(
		data.get("subtitle", data.get("message", data.get("text", "")))
	).strip_edges()
	# If only message/text was provided without title, promote it.
	if title.is_empty() and not subtitle.is_empty():
		title = subtitle
		subtitle = ""
	if title.is_empty() and subtitle.is_empty():
		return
	var hold := CompanionConfig.custom_alerts_hold_sec
	if data.has("ttl_sec"):
		hold = maxf(0.5, float(data.get("ttl_sec")))
	elif data.has("hold_sec"):
		hold = maxf(0.5, float(data.get("hold_sec")))
	elif data.has("duration_sec"):
		hold = maxf(0.5, float(data.get("duration_sec")))
	_enqueue({"title": title, "subtitle": subtitle, "hold": hold})


func _enqueue(item: Dictionary) -> void:
	while _queue.size() >= 8:
		_queue.pop_front()
	_queue.append(item)
	if not _playing and not _command_alerts_busy():
		_play_next()


func _play_next() -> void:
	if _queue.is_empty():
		_playing = false
		_hide()
		return
	if _command_alerts_busy():
		_playing = false
		return
	_playing = true
	_idle_accum = 0.0
	_apply_layout()
	_apply_chrome()
	_apply_padding()
	_apply_fonts()
	var item: Dictionary = _queue.pop_front()
	_title.text = str(item.get("title", ""))
	_subtitle.text = str(item.get("subtitle", ""))
	_title.visible = not _title.text.strip_edges().is_empty()
	_subtitle.visible = not _subtitle.text.strip_edges().is_empty()
	_fit_toast()
	await get_tree().process_frame
	_fit_toast()
	_row.pivot_offset = Vector2(roundf(_row.size.x * 0.5), roundf(_row.size.y * 0.5))
	var fade_in := maxf(0.05, CompanionConfig.alert_fade_in_sec)
	var hold := maxf(0.5, float(item.get("hold", CompanionConfig.custom_alerts_hold_sec)))
	var fade_out := maxf(0.05, CompanionConfig.alert_fade_out_sec)
	var tween := create_tween().set_parallel(true)
	tween.set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
	tween.tween_property(_row, "modulate:a", 1.0, fade_in).from(0.0)
	tween.tween_property(_row, "scale", Vector2.ONE, fade_in).from(Vector2(0.92, 0.92))
	await tween.finished
	await get_tree().create_timer(hold).timeout
	var out := create_tween()
	out.tween_property(_row, "modulate:a", 0.0, fade_out)
	await out.finished
	_play_next()


func _hide() -> void:
	_row.modulate.a = 0.0
	_row.scale = Vector2.ONE
	_title.text = ""
	_subtitle.text = ""


func _apply_chrome() -> void:
	if _panel == null:
		return
	var sb: StyleBoxTexture = _SpdUi.chrome_style(
		CompanionConfig.alert_chrome_style, CompanionConfig.alert_chrome_scale
	)
	if sb == null or sb.texture == null:
		sb = _SpdUi.chrome_style_toast(CompanionConfig.alert_chrome_scale)
	_panel.add_theme_stylebox_override("panel", sb)


func _apply_padding() -> void:
	if _margin == null:
		return
	var pad_h := clampi(CompanionConfig.alert_padding_h_px, 0, 64)
	var pad_v := clampi(CompanionConfig.alert_padding_v_px, 0, 64)
	_margin.add_theme_constant_override("margin_left", pad_h)
	_margin.add_theme_constant_override("margin_right", pad_h)
	_margin.add_theme_constant_override("margin_top", pad_v)
	_margin.add_theme_constant_override("margin_bottom", pad_v)


func _apply_fonts() -> void:
	var title_fs := clampi(CompanionConfig.alert_title_font_size_px, 6, 72)
	var sub_fs := clampi(CompanionConfig.alert_subtitle_font_size_px, 6, 72)
	var align := (
		HORIZONTAL_ALIGNMENT_CENTER
		if CompanionConfig.alert_text_align.to_lower() == "center"
		else HORIZONTAL_ALIGNMENT_LEFT
	)
	for lab in [_title, _subtitle]:
		if lab == null:
			continue
		lab.add_theme_font_override("font", _FONT)
		lab.horizontal_alignment = align
		lab.add_theme_color_override("font_shadow_color", Color(0, 0, 0, 0.85))
		lab.add_theme_constant_override("shadow_offset_x", 1)
		lab.add_theme_constant_override("shadow_offset_y", 1)
	_title.add_theme_font_size_override("font_size", title_fs)
	_title.add_theme_color_override("font_color", Color(1.0, 1.0, 0.27, 1.0))
	_subtitle.add_theme_font_size_override("font_size", sub_fs)
	_subtitle.add_theme_color_override("font_color", Color(0.95, 0.92, 0.85, 1.0))


func _apply_layout() -> void:
	if _slot == null:
		return
	var canvas := CompanionConfig.layout_canvas_size(self)
	CompanionConfig.apply_alert_zone_layout(_slot, canvas)
	_slot.position = Vector2(roundf(_slot.position.x), roundf(_slot.position.y))
	_slot.size = Vector2(roundf(_slot.size.x), roundf(_slot.size.y))
	if _row:
		_row.set_anchors_preset(Control.PRESET_TOP_WIDE)
		_row.offset_left = 0.0
		_row.offset_top = 0.0
		_row.offset_right = 0.0
		_row.offset_bottom = maxf(80.0, mini(160.0, _slot.size.y))
		_row.scale = Vector2.ONE


func _fit_toast() -> void:
	if _panel == null or _slot == null:
		return
	var zone_w := maxf(64.0, _slot.size.x)
	_panel.set("custom_maximum_size", Vector2(zone_w, 0.0))
	var pad_h := float(clampi(CompanionConfig.alert_padding_h_px, 0, 64) * 2)
	var text_max := maxf(64.0, zone_w - pad_h)
	for lab in [_title, _subtitle]:
		if lab == null or not lab.visible or lab.text.strip_edges().is_empty():
			continue
		lab.custom_minimum_size = Vector2(text_max, 0.0)
		lab.set("custom_maximum_size", Vector2(text_max, 0.0))
	_panel.custom_minimum_size = Vector2.ZERO
	_panel.reset_size()
