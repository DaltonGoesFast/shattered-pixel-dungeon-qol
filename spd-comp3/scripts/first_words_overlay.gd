extends Control
## Shared Streamer.bot welcome toast for first words, follows, and subscriptions.

const _SpdUi := preload("res://scripts/spd_ui_art.gd")
const _FONT := preload("res://assets/fonts/pixel_font.ttf")
const FIRST_WORDS_KINDS := ["first_words", "firstwords", "first_word"]
const FOLLOW_KINDS := ["follow", "follower", "twitch_follow"]
const SUBSCRIBER_KINDS := ["subscriber", "new_sub", "new_subscriber"]
const YOUTUBE_SUBSCRIBER_KINDS := ["youtube_subscriber", "youtube_sub", "yt_subscriber"]
const UI_KINDS := (
	FIRST_WORDS_KINDS + FOLLOW_KINDS + SUBSCRIBER_KINDS + YOUTUBE_SUBSCRIBER_KINDS
)

var _slot: Control
var _row: Control
var _panel: PanelContainer
var _margin: MarginContainer
var _label: Label
var _queue: Array[Dictionary] = []
var _busy: bool = false
var _preview_pinned: bool = false
var _preview_sample: Dictionary = {}
var _playback_generation: int = 0
var _active_tween: Tween


func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	_build()
	StreamerBotUdp.ui_event.connect(_on_ui_event)
	CompanionConfig.settings_saved.connect(_on_config_changed)
	CompanionConfig.settings_loaded.connect(_on_config_changed)
	get_viewport().size_changed.connect(_apply_layout)
	_on_config_changed()


func _build() -> void:
	_slot = Control.new()
	_slot.name = "FirstWordsSlot"
	_slot.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_slot.clip_contents = false
	add_child(_slot)

	_row = Control.new()
	_row.name = "FirstWordsRow"
	_row.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_row.set_anchors_preset(Control.PRESET_TOP_WIDE)
	_row.offset_bottom = 140.0
	_slot.add_child(_row)

	_panel = PanelContainer.new()
	_panel.name = "Panel"
	_panel.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_panel.set_anchors_and_offsets_preset(Control.PRESET_TOP_LEFT)
	_panel.modulate.a = 0.0
	_panel.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	_row.add_child(_panel)

	_margin = MarginContainer.new()
	_margin.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_panel.add_child(_margin)

	_label = Label.new()
	_label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_label.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	_label.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	_margin.add_child(_label)


func _on_config_changed() -> void:
	_apply_chrome()
	_apply_padding()
	_apply_font()
	_apply_layout()
	if _preview_pinned:
		_render_item(_preview_sample)


func _apply_chrome() -> void:
	var style: StyleBoxTexture = _SpdUi.chrome_style(
		CompanionConfig.first_words_chrome_style, CompanionConfig.first_words_chrome_scale
	)
	if style == null or style.texture == null:
		style = _SpdUi.chrome_style_toast(CompanionConfig.first_words_chrome_scale)
	_panel.add_theme_stylebox_override("panel", style)


func _apply_padding() -> void:
	var pad_h := clampi(CompanionConfig.first_words_padding_h_px, 0, 64)
	var pad_v := clampi(CompanionConfig.first_words_padding_v_px, 0, 64)
	_margin.add_theme_constant_override("margin_left", pad_h)
	_margin.add_theme_constant_override("margin_right", pad_h)
	_margin.add_theme_constant_override("margin_top", pad_v)
	_margin.add_theme_constant_override("margin_bottom", pad_v)


func _apply_font() -> void:
	var font_size := clampi(CompanionConfig.first_words_font_size_px, 8, 96)
	if font_size % 2 != 0:
		font_size += 1
	_label.add_theme_font_override("font", _FONT)
	_label.add_theme_font_size_override("font_size", font_size)
	_label.add_theme_color_override("font_color", CompanionConfig.first_words_font_color)
	_label.horizontal_alignment = (
		HORIZONTAL_ALIGNMENT_CENTER
		if CompanionConfig.first_words_text_align == "center"
		else HORIZONTAL_ALIGNMENT_LEFT
	)
	if CompanionConfig.first_words_text_shadow:
		_label.add_theme_color_override("font_shadow_color", Color(0, 0, 0, 0.85))
		_label.add_theme_constant_override("shadow_offset_x", 1)
		_label.add_theme_constant_override("shadow_offset_y", 1)
	else:
		_label.add_theme_color_override("font_shadow_color", Color(0, 0, 0, 0))
		_label.add_theme_constant_override("shadow_offset_x", 0)
		_label.add_theme_constant_override("shadow_offset_y", 0)


func _apply_layout() -> void:
	var canvas := CompanionConfig.layout_canvas_size(self)
	CompanionConfig.apply_first_words_zone_layout(_slot, canvas)
	_slot.position = Vector2(roundf(_slot.position.x), roundf(_slot.position.y))
	_slot.size = Vector2(roundf(_slot.size.x), roundf(_slot.size.y))
	_row.set_anchors_preset(Control.PRESET_TOP_WIDE)
	_row.offset_left = 0.0
	_row.offset_top = 0.0
	_row.offset_right = 0.0
	_row.offset_bottom = maxf(1.0, _slot.size.y)
	if not _label.text.is_empty():
		_fit_toast()


func _fit_toast() -> void:
	var zone_width := maxf(64.0, _slot.size.x)
	var pad_width := float(clampi(CompanionConfig.first_words_padding_h_px, 0, 64) * 2)
	var text_max_width := maxf(32.0, zone_width - pad_width)
	var font_size := _label.get_theme_font_size("font_size")
	var measured_width := ceilf(
		_FONT.get_string_size(
			_label.text, HORIZONTAL_ALIGNMENT_LEFT, -1.0, font_size
		).x
	) + 2.0
	var text_width := minf(text_max_width, maxf(32.0, measured_width))
	_label.custom_minimum_size = Vector2(text_width, 0.0)
	_label.set("custom_maximum_size", Vector2(text_width, 0.0))
	_panel.custom_minimum_size = Vector2.ZERO
	_panel.reset_size()
	var panel_width := minf(_panel.size.x, zone_width)
	_panel.size = Vector2(panel_width, _panel.size.y)
	var panel_x := (
		roundf((zone_width - panel_width) * 0.5)
		if CompanionConfig.first_words_text_align == "center"
		else 0.0
	)
	_panel.position = Vector2(panel_x, 0.0)
	_panel.pivot_offset = Vector2(
		roundf(_panel.size.x * 0.5), roundf(_panel.size.y * 0.5)
	)


func _on_ui_event(data: Dictionary) -> void:
	if not CompanionConfig.first_words_enabled:
		return
	var kind := str(data.get("ui", data.get("kind", ""))).strip_edges().to_lower()
	if kind not in UI_KINDS:
		return
	var username := str(
		data.get(
			"username",
			data.get("user", data.get("userName", data.get("display_name", "")))
		)
	).strip_edges()
	if username.is_empty():
		return
	var hold := CompanionConfig.first_words_default_ttl_sec
	if data.has("ttl_sec"):
		hold = maxf(0.5, float(data.get("ttl_sec")))
	elif data.has("duration_sec"):
		hold = maxf(0.5, float(data.get("duration_sec")))
	var cap := clampi(CompanionConfig.first_words_queue_max, 1, 32)
	while _queue.size() >= cap:
		_queue.pop_front()
	var text := "Welcome %s!" % username
	if kind in FOLLOW_KINDS:
		text = "Thanks for following, %s!" % username
	elif kind in SUBSCRIBER_KINDS:
		text = "%s subscribed!" % username
	elif kind in YOUTUBE_SUBSCRIBER_KINDS:
		text = "Thanks for subscribing, %s!" % username
	_queue.append({"text": text, "hold": hold})
	if not _busy and not _preview_pinned:
		_play_next()


func show_preview(sample: Dictionary, pinned: bool) -> void:
	if not pinned:
		if _preview_pinned:
			clear_preview()
		_queue.push_front(sample.duplicate(true))
		if not _busy:
			_play_next()
		return
	_cancel_playback()
	_preview_pinned = true
	_preview_sample = sample.duplicate(true)
	_busy = true
	_render_item(_preview_sample)


func clear_preview() -> void:
	_cancel_playback()
	_preview_pinned = false
	_preview_sample.clear()
	_busy = false
	_hide()
	if not _queue.is_empty():
		_play_next()


func is_preview_pinned() -> bool:
	return _preview_pinned


func _cancel_playback() -> void:
	_playback_generation += 1
	if _active_tween and _active_tween.is_valid():
		_active_tween.kill()
	_active_tween = null


func _render_item(item: Dictionary) -> void:
	_apply_layout()
	_apply_chrome()
	_apply_padding()
	_apply_font()
	_label.text = str(item.get("text", "Welcome PreviewUser!"))
	_panel.scale = Vector2.ONE
	_panel.modulate.a = 1.0
	_fit_toast()
	call_deferred("_fit_toast")


func _play_next() -> void:
	if _preview_pinned:
		return
	if _queue.is_empty():
		_busy = false
		_hide()
		return
	_busy = true
	var generation := _playback_generation
	_apply_layout()
	_apply_chrome()
	_apply_padding()
	_apply_font()
	var item: Dictionary = _queue.pop_front()
	_label.text = str(item.get("text", ""))
	await get_tree().process_frame
	if generation != _playback_generation:
		return
	_fit_toast()
	await get_tree().process_frame
	if generation != _playback_generation:
		return
	_fit_toast()
	_panel.scale = Vector2.ONE
	var fade_in := maxf(0.05, CompanionConfig.first_words_fade_in_sec)
	var hold := maxf(
		0.5, float(item.get("hold", CompanionConfig.first_words_default_ttl_sec))
	)
	var fade_out := maxf(0.05, CompanionConfig.first_words_fade_out_sec)
	_active_tween = create_tween().set_parallel(true)
	_active_tween.tween_property(_panel, "modulate:a", 1.0, fade_in).from(0.0).set_trans(
		Tween.TRANS_SINE
	).set_ease(Tween.EASE_OUT)
	if CompanionConfig.first_words_pop_scale:
		_active_tween.tween_property(_panel, "scale", Vector2.ONE, fade_in).from(
			Vector2(0.94, 0.94)
		).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
	await _active_tween.finished
	if generation != _playback_generation:
		return
	_panel.scale = Vector2.ONE
	await get_tree().create_timer(hold).timeout
	if generation != _playback_generation:
		return
	_active_tween = create_tween()
	_active_tween.tween_property(_panel, "modulate:a", 0.0, fade_out)
	await _active_tween.finished
	if generation != _playback_generation:
		return
	_active_tween = null
	_play_next()


func _hide() -> void:
	_panel.modulate.a = 0.0
	_panel.scale = Vector2.ONE
	_label.text = ""
