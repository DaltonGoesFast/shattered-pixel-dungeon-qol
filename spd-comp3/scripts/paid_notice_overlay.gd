extends Control
## Streamer.bot UDP overlays: YouTube superchat / gifted membership, Twitch sub / highlight.

const _SpdUi := preload("res://scripts/spd_ui_art.gd")
const _FONT := preload("res://assets/fonts/pixel_font.ttf")

const KIND_SUPERCHAT := "superchat"
const KIND_GIFTED := "gifted_membership"
const KIND_GIFTED_SUBS := "gifted_subs"
const KIND_MEMBERSHIP := "membership"
const KIND_SUB := "sub"
const KIND_HIGHLIGHT := "highlight"

var _slot: Control
var _row: Control
var _panel: PanelContainer
var _margin: MarginContainer
var _vbox: VBoxContainer
var _kind_label: Label
var _title_label: Label
var _body_label: Label

var _queue: Array[Dictionary] = []
var _busy: bool = false
var _obs_scene_known: bool = false
var _pause_active: bool = false


func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	_build()
	StreamerBotUdp.ui_event.connect(_on_ui_event)
	CompanionConfig.settings_saved.connect(_on_cfg)
	CompanionConfig.settings_loaded.connect(_on_cfg)
	get_viewport().size_changed.connect(_apply_layout)
	var obs := get_node_or_null("/root/ObsWebSocketClient")
	if obs:
		obs.pause_scene_active_changed.connect(_on_obs_pause)
		obs.disconnected_from_obs.connect(_on_obs_disconnected)
	_on_cfg()


func _build() -> void:
	_slot = Control.new()
	_slot.name = "PaidNoticeSlot"
	_slot.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_slot.clip_contents = false
	add_child(_slot)

	_row = Control.new()
	_row.name = "PaidNoticeRow"
	_row.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_row.set_anchors_preset(Control.PRESET_TOP_WIDE)
	_row.offset_bottom = 180.0
	_row.modulate.a = 0.0
	_slot.add_child(_row)

	_panel = PanelContainer.new()
	_panel.name = "Panel"
	_panel.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_panel.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	_panel.custom_minimum_size = Vector2(280, 80)
	_panel.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	_row.add_child(_panel)

	_margin = MarginContainer.new()
	_margin.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_panel.add_child(_margin)

	_vbox = VBoxContainer.new()
	_vbox.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_margin.add_child(_vbox)

	_kind_label = Label.new()
	_kind_label.autowrap_mode = TextServer.AUTOWRAP_OFF
	_title_label = Label.new()
	_title_label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_body_label = Label.new()
	_body_label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	for lab in [_kind_label, _title_label, _body_label]:
		lab.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	_vbox.add_child(_kind_label)
	_vbox.add_child(_title_label)
	_vbox.add_child(_body_label)


func _on_cfg() -> void:
	_apply_chrome()
	_apply_padding()
	_apply_fonts()
	_apply_layout()
	_update_layer_visibility()


func _on_obs_pause(is_pause: bool) -> void:
	_obs_scene_known = true
	_pause_active = is_pause
	_update_layer_visibility()


func _on_obs_disconnected() -> void:
	_obs_scene_known = false
	_pause_active = false
	_update_layer_visibility()


func _update_layer_visibility() -> void:
	var layer := get_parent() as CanvasLayer
	var show := CompanionConfig.paid_notice_enabled
	if show:
		var on_live := CompanionConfig.paid_notice_show_on_live
		var on_pause := CompanionConfig.paid_notice_show_on_pause
		if not _obs_scene_known:
			show = on_live or on_pause
		else:
			show = on_pause if _pause_active else on_live
	if layer:
		layer.visible = show
	visible = show


func _apply_chrome() -> void:
	if _panel == null:
		return
	var sb: StyleBoxTexture = _SpdUi.chrome_style(
		CompanionConfig.paid_notice_chrome_style, CompanionConfig.paid_notice_chrome_scale
	)
	if sb == null or sb.texture == null:
		sb = _SpdUi.chrome_style_toast(CompanionConfig.paid_notice_chrome_scale)
	_panel.add_theme_stylebox_override("panel", sb)


func _apply_padding() -> void:
	if _margin == null or _vbox == null:
		return
	var pad_h := clampi(CompanionConfig.paid_notice_padding_h_px, 0, 64)
	var pad_v := clampi(CompanionConfig.paid_notice_padding_v_px, 0, 64)
	_margin.add_theme_constant_override("margin_left", pad_h)
	_margin.add_theme_constant_override("margin_right", pad_h)
	_margin.add_theme_constant_override("margin_top", pad_v)
	_margin.add_theme_constant_override("margin_bottom", pad_v)
	_vbox.add_theme_constant_override(
		"separation", clampi(CompanionConfig.paid_notice_line_separation_px, 0, 48)
	)


func _apply_fonts() -> void:
	# Even sizes keep SPD pixel font glyphs on the pixel grid.
	var kind_fs := _snap_pixel_font_size(CompanionConfig.paid_notice_kind_font_size_px)
	var title_fs := _snap_pixel_font_size(CompanionConfig.paid_notice_title_font_size_px)
	var body_fs := _snap_pixel_font_size(CompanionConfig.paid_notice_body_font_size_px)
	var align := (
		HORIZONTAL_ALIGNMENT_CENTER
		if CompanionConfig.paid_notice_text_align == "center"
		else HORIZONTAL_ALIGNMENT_LEFT
	)
	_style_label(_kind_label, kind_fs, CompanionConfig.paid_notice_kind_font_color, align)
	_style_label(_title_label, title_fs, CompanionConfig.paid_notice_title_font_color, align)
	_style_label(_body_label, body_fs, CompanionConfig.paid_notice_body_font_color, align)


func _snap_pixel_font_size(fs: int) -> int:
	var n := clampi(fs, 8, 96)
	# Prefer multiples of 8 (native SPD pixel steps); fall back to even.
	if n % 8 == 0:
		return n
	if n % 2 != 0:
		n += 1
	return clampi(n, 8, 96)


func _style_label(lab: Label, fs: int, color: Color, align: HorizontalAlignment) -> void:
	if lab == null:
		return
	lab.add_theme_font_override("font", _FONT)
	lab.add_theme_font_size_override("font_size", fs)
	lab.add_theme_color_override("font_color", color)
	lab.horizontal_alignment = align
	if CompanionConfig.paid_notice_text_shadow:
		lab.add_theme_color_override("font_shadow_color", Color(0, 0, 0, 0.85))
		lab.add_theme_constant_override("shadow_offset_x", 1)
		lab.add_theme_constant_override("shadow_offset_y", 1)
	else:
		lab.add_theme_color_override("font_shadow_color", Color(0, 0, 0, 0))
		lab.add_theme_constant_override("shadow_offset_x", 0)
		lab.add_theme_constant_override("shadow_offset_y", 0)


func _apply_layout() -> void:
	if _slot == null:
		return
	var canvas := CompanionConfig.layout_canvas_size(self)
	CompanionConfig.apply_paid_notice_zone_layout(_slot, canvas)
	# Keep the toast on whole pixels so the bitmap font stays sharp.
	_slot.position = Vector2(roundf(_slot.position.x), roundf(_slot.position.y))
	_slot.size = Vector2(roundf(_slot.size.x), roundf(_slot.size.y))
	if _row:
		_row.set_anchors_preset(Control.PRESET_TOP_WIDE)
		_row.offset_left = 0.0
		_row.offset_top = 0.0
		_row.offset_right = 0.0
		_row.offset_bottom = maxf(120.0, _slot.size.y)
		_row.scale = Vector2.ONE
		_row.pivot_offset = Vector2(roundf(_row.size.x * 0.5), roundf(_row.size.y * 0.5))


func _on_ui_event(data: Dictionary) -> void:
	if not CompanionConfig.paid_notice_enabled:
		return
	var kind := _normalize_kind(data)
	if kind.is_empty():
		return
	if not _kind_enabled(kind):
		return
	var item := _build_item(kind, data)
	if str(item.get("title", "")).is_empty() and str(item.get("body", "")).is_empty():
		return
	var cap := clampi(CompanionConfig.paid_notice_queue_max, 1, 32)
	while _queue.size() >= cap:
		_queue.pop_front()
	_queue.append(item)
	if not _busy:
		_play_next()


func _normalize_kind(data: Dictionary) -> String:
	var raw := str(data.get("kind", "")).strip_edges().to_lower()
	if raw.is_empty():
		raw = str(data.get("ui", "")).strip_edges().to_lower()
	match raw:
		"superchat", "super_chat", "yt_superchat", "youtube_superchat":
			return KIND_SUPERCHAT
		"gifted_membership", "gift_membership", "membership_gift", "gifted", "yt_gift":
			# C03 may still send ui=gifted_membership for Twitch gift subs.
			var plat_g := str(data.get("platform", data.get("eventSource", ""))).strip_edges().to_lower()
			if plat_g.contains("twitch"):
				return KIND_GIFTED_SUBS
			return KIND_GIFTED
		"gifted_subs", "gift_sub", "gift_subscription", "gifted_subscriptions", "twitch_gift":
			return KIND_GIFTED_SUBS
		"membership", "member", "new_sponsor", "yt_member", "yt_membership", "sponsor":
			return KIND_MEMBERSHIP
		"sub", "subscription", "twitch_sub", "subscribe", "resub":
			# C02 may still send ui=sub for YouTube New Sponsor; prefer membership copy.
			var plat := str(data.get("platform", data.get("eventSource", ""))).strip_edges().to_lower()
			if plat.contains("youtube") or plat == "yt":
				return KIND_MEMBERSHIP
			return KIND_SUB
		"highlight", "highlighted_message", "highlight_my_message", "twitch_highlight":
			return KIND_HIGHLIGHT
		"paid_notice", "notice":
			# Require explicit kind when ui is generic.
			return str(data.get("kind", "")).strip_edges().to_lower()
		_:
			return ""


func _kind_enabled(kind: String) -> bool:
	match kind:
		KIND_SUPERCHAT:
			return CompanionConfig.paid_notice_enable_superchat
		KIND_GIFTED, KIND_GIFTED_SUBS:
			return CompanionConfig.paid_notice_enable_gifted_membership
		KIND_MEMBERSHIP, KIND_SUB:
			return CompanionConfig.paid_notice_enable_sub
		KIND_HIGHLIGHT:
			return CompanionConfig.paid_notice_enable_highlight
		_:
			return false


func _build_item(kind: String, data: Dictionary) -> Dictionary:
	var user := str(
		data.get(
			"username",
			data.get("user", data.get("userName", data.get("display_name", "")))
		)
	).strip_edges()
	var message := str(
		data.get("message", data.get("text", data.get("msg", "")))
	).strip_edges()
	var amount := str(data.get("amount", data.get("value", ""))).strip_edges()
	var tier := str(data.get("tier", data.get("sub_tier", ""))).strip_edges()
	var months := str(data.get("months", data.get("cumulative_months", ""))).strip_edges()
	var count := str(data.get("count", data.get("gift_count", data.get("total", "")))).strip_edges()
	var ttl := CompanionConfig.paid_notice_default_ttl_sec
	if data.has("ttl_sec"):
		ttl = maxf(0.5, float(data.get("ttl_sec")))
	elif data.has("duration_sec"):
		ttl = maxf(0.5, float(data.get("duration_sec")))

	var kind_title := _kind_banner(kind)
	var title := ""
	var body := message
	match kind:
		KIND_SUPERCHAT:
			if user.is_empty():
				title = "Super Chat"
			elif amount.is_empty():
				title = "%s — Super Chat" % user
			else:
				title = "%s — Super Chat %s" % [user, amount]
		KIND_GIFTED:
			if user.is_empty():
				title = "Gifted membership"
			elif count.is_empty():
				title = "%s gifted a membership" % user
			else:
				title = "%s gifted %s memberships" % [user, count]
			# Do not show YT membership level names (e.g. "Test Level") as body.
			body = ""
		KIND_GIFTED_SUBS:
			if user.is_empty():
				title = "Gifted subscriptions"
			elif count.is_empty():
				title = "%s gifted a subscription" % user
			else:
				title = "%s gifted %s subscriptions" % [user, count]
			body = ""
		KIND_MEMBERSHIP:
			if user.is_empty():
				title = "New member"
			else:
				title = "%s became a member" % user
			body = ""
		KIND_SUB:
			if user.is_empty():
				title = "New subscriber"
			elif months.is_empty():
				title = "%s subscribed" % user
			else:
				title = "%s subscribed · %s months" % [user, months]
			if not tier.is_empty():
				if body.is_empty():
					body = tier
				else:
					body = "%s — %s" % [tier, body]
		KIND_HIGHLIGHT:
			if user.is_empty():
				title = "Highlighted message"
			else:
				title = "%s — Highlighted message" % user
		_:
			title = user if not user.is_empty() else kind_title

	return {
		"kind": kind,
		"kind_title": kind_title,
		"title": title,
		"body": body,
		"ttl": ttl,
	}


func _kind_banner(kind: String) -> String:
	match kind:
		KIND_SUPERCHAT:
			return "SUPER CHAT"
		KIND_GIFTED:
			return "GIFTED MEMBERSHIP"
		KIND_GIFTED_SUBS:
			return "GIFTED SUBS"
		KIND_MEMBERSHIP:
			return "NEW MEMBER"
		KIND_SUB:
			return "SUBSCRIPTION"
		KIND_HIGHLIGHT:
			return "HIGHLIGHT"
		_:
			return "NOTICE"


func _play_next() -> void:
	if _queue.is_empty():
		_busy = false
		_hide()
		return
	_busy = true
	_apply_layout()
	_apply_chrome()
	_apply_padding()
	_apply_fonts()
	var item: Dictionary = _queue.pop_front()
	_kind_label.text = str(item.get("kind_title", ""))
	_title_label.text = str(item.get("title", ""))
	_body_label.text = str(item.get("body", ""))
	_body_label.visible = not _body_label.text.is_empty()
	await get_tree().process_frame
	_row.scale = Vector2.ONE
	_row.pivot_offset = Vector2(roundf(_row.size.x * 0.5), roundf(_row.size.y * 0.5))
	var fade_in := maxf(0.05, CompanionConfig.paid_notice_fade_in_sec)
	var hold := maxf(0.5, float(item.get("ttl", CompanionConfig.paid_notice_default_ttl_sec)))
	var fade_out := maxf(0.05, CompanionConfig.paid_notice_fade_out_sec)
	var tween := create_tween().set_parallel(true)
	tween.tween_property(_row, "modulate:a", 1.0, fade_in).from(0.0).set_trans(
		Tween.TRANS_SINE
	).set_ease(Tween.EASE_OUT)
	if CompanionConfig.paid_notice_pop_scale:
		tween.tween_property(_row, "scale", Vector2.ONE, fade_in).from(Vector2(0.94, 0.94)).set_trans(
			Tween.TRANS_BACK
		).set_ease(Tween.EASE_OUT)
	await tween.finished
	_row.scale = Vector2.ONE
	await get_tree().create_timer(hold).timeout
	var out := create_tween()
	out.tween_property(_row, "modulate:a", 0.0, fade_out)
	await out.finished
	_play_next()


func _hide() -> void:
	_row.modulate.a = 0.0
	_row.scale = Vector2.ONE
	_kind_label.text = ""
	_title_label.text = ""
	_body_label.text = ""
