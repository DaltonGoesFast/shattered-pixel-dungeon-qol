extends PanelContainer

## Reads [code]free_until[/code] from Lastest UI [code]GET /api/points-config[/code] and shows each active cost key with time left.

const _FONT: FontFile = preload("res://assets/fonts/pixel_font.ttf")
const _SpdUi := preload("res://scripts/spd_ui_art.gd")

## Display strings match Lastest UI [code]points_command.py[/code] chat commands ([code]!spawn[/code], [code]!row[/code], etc.). Billing keys stay [code]cost_per_*[/code].
const _COST_COMMAND_LABEL: Dictionary = {
	"cost_per_gold": "!gold",
	"cost_per_curse": "!curse",
	"cost_per_gas": "!gas",
	"cost_per_scroll": "!scroll",
	"cost_per_trap": "!trap",
	"cost_per_bomb": "!bomb",
	"cost_per_transmute": "!transmute",
	"cost_per_ally_bee": "!bee",
	"cost_per_ward": "!ward",
	"cost_per_buff": "!buff",
	"cost_per_debuff": "!debuff",
	"cost_per_wand": "!wand",
	"cost_per_heal": "!heal",
	"cost_per_cleanse": "!cleanse",
	"cost_per_dew": "!dew",
	"cost_per_hex": "!hex",
	"cost_per_degrade": "!degrade",
	"cost_per_sabotage": "!sabotage",
	"cost_per_corrupt_ally": "!corruptally",
	"cost_per_ring_of_wealth": "!row",
}

@onready var _margin: MarginContainer = $MarginContainer
@onready var _outer_vbox: VBoxContainer = $MarginContainer/VBox
@onready var _title: Label = $MarginContainer/VBox/Title
@onready var _list: VBoxContainer = $MarginContainer/VBox/List

var _http: HTTPRequest
var _poll_accum: float = 999.0
var _tick_accum: float = 0.0
## Each entry: [code]{ "key": String, "end": int }[/code] unix seconds.
var _active: Array = []


func _ready() -> void:
	_http = HTTPRequest.new()
	add_child(_http)
	_http.request_completed.connect(_on_http_done)
	CompanionConfig.settings_saved.connect(_on_cfg)
	CompanionConfig.settings_loaded.connect(_on_cfg)
	get_viewport().size_changed.connect(_schedule_reposition)
	if not FreePromosState.active_changed.is_connected(_on_free_state_changed):
		FreePromosState.active_changed.connect(_on_free_state_changed)
	_apply_anchor_pins()
	_apply_compact_container_flags()
	_on_cfg()


func _on_cfg() -> void:
	_poll_accum = 999.0
	_apply_anchor_pins()
	_apply_chrome()
	_apply_padding()
	_apply_compact_container_flags()
	_apply_size_cap()
	_apply_fonts()
	if CompanionConfig.is_vertical_layout(self):
		_pull_from_shared_state()
	else:
		_update_root_visibility()
		_schedule_reposition()
		_request_poll()


func _on_free_state_changed() -> void:
	if CompanionConfig.is_vertical_layout(self):
		_pull_from_shared_state()


func _pull_from_shared_state() -> void:
	_active = FreePromosState.get_active()
	_rebuild_list()
	_update_root_visibility()
	_schedule_reposition()


func _apply_chrome() -> void:
	add_theme_stylebox_override(
		"panel",
		_SpdUi.chrome_style(
			CompanionConfig.free_promos_chrome_style, CompanionConfig.free_promos_chrome_scale
		)
	)


func _apply_padding() -> void:
	if _margin == null:
		return
	var pad_h := clampi(CompanionConfig.free_promos_padding_h_px, 0, 64)
	var pad_v := clampi(CompanionConfig.free_promos_padding_v_px, 0, 64)
	_margin.add_theme_constant_override("margin_left", pad_h)
	_margin.add_theme_constant_override("margin_right", pad_h)
	_margin.add_theme_constant_override("margin_top", pad_v)
	_margin.add_theme_constant_override("margin_bottom", pad_v)


func _apply_anchor_pins() -> void:
	anchor_left = 0.0
	anchor_top = 0.0
	anchor_right = 0.0
	anchor_bottom = 0.0
	offset_left = 0.0
	offset_top = 0.0
	offset_right = 0.0
	offset_bottom = 0.0


## Toast panels were growing to the viewport: VBox defaults use vertical [constant Control.SIZE_FILL], so the list ate all height.
func _apply_compact_container_flags() -> void:
	size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
	size_flags_vertical = Control.SIZE_SHRINK_BEGIN
	if _margin:
		_margin.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		_margin.size_flags_vertical = Control.SIZE_SHRINK_BEGIN
	if _outer_vbox:
		_outer_vbox.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		_outer_vbox.size_flags_vertical = Control.SIZE_SHRINK_BEGIN
	if _title:
		_title.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		_title.size_flags_vertical = Control.SIZE_SHRINK_BEGIN
	if _list:
		_list.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		_list.size_flags_vertical = Control.SIZE_SHRINK_BEGIN


func _apply_size_cap() -> void:
	var cap_w: float = float(clampi(CompanionConfig.free_promos_max_width_px, 120, 1600))
	## Was wrongly setting [member Control.custom_minimum_size].x to “max width”, forcing a huge strip.
	custom_minimum_size = Vector2.ZERO
	set("custom_maximum_size", Vector2(cap_w, 0.0))


func _physics_process(delta: float) -> void:
	if CompanionConfig.is_vertical_layout(self):
		_tick_accum += delta
		if _tick_accum >= 1.0:
			_tick_accum = 0.0
			_pull_from_shared_state()
			_refresh_time_labels()
		return
	if not _should_run():
		return
	_poll_accum += delta
	var step: float = maxf(1.0, CompanionConfig.free_promos_poll_sec)
	if _poll_accum >= step:
		_poll_accum = 0.0
		_request_poll()
	_tick_accum += delta
	if _tick_accum >= 1.0:
		_tick_accum = 0.0
		_prune_expired()
		_refresh_time_labels()


func _prune_expired() -> void:
	var now: int = int(Time.get_unix_time_from_system())
	var keep: Array = []
	for item in _active:
		if int(item["end"]) > now:
			keep.append(item)
	if keep.size() == _active.size():
		return
	_active = keep
	_sync_free_state()
	_rebuild_list()
	_update_root_visibility()
	_schedule_reposition()


func _should_run() -> bool:
	return not CompanionConfig.free_promos_http_url.strip_edges().is_empty()


func _sync_free_state() -> void:
	if CompanionConfig.is_vertical_layout(self):
		return
	FreePromosState.set_active(_active)


func _update_root_visibility() -> void:
	var url_ok: bool = not CompanionConfig.free_promos_http_url.strip_edges().is_empty()
	visible = (
		CompanionConfig.element_enabled(self, "free_promos")
		and url_ok
		and not _active.is_empty()
	)
	if _list:
		_list.visible = not _active.is_empty()
	if not visible:
		_fit_to_content()
		_schedule_reposition()


func _request_poll() -> void:
	if CompanionConfig.is_vertical_layout(self):
		return
	if not _should_run():
		return
	if _http.get_http_client_status() != HTTPClient.STATUS_DISCONNECTED:
		return
	var url: String = CompanionConfig.free_promos_http_url.strip_edges()
	var err := _http.request(url)
	if err != OK:
		push_warning("FreePromosPanel: HTTP request failed err=%d url=%s" % [err, url])


func _on_http_done(_result: int, response_code: int, _headers: PackedStringArray, body: PackedByteArray) -> void:
	if body.is_empty():
		return
	if response_code < 200 or response_code >= 300:
		return
	var json := JSON.new()
	if json.parse(body.get_string_from_utf8()) != OK:
		return
	var data = json.data
	if data is Dictionary:
		_apply_free_until_payload(data as Dictionary)


func _apply_free_until_payload(data: Dictionary) -> void:
	var fu: Variant = data.get("free_until")
	if fu is Dictionary:
		_parse_free_until(fu as Dictionary)
	else:
		_active.clear()
		_sync_free_state()
		_rebuild_list()
	_update_root_visibility()


func _parse_free_until(d: Dictionary) -> void:
	var now: int = int(Time.get_unix_time_from_system())
	var rows: Array = []
	for k in d:
		var raw: Variant = d[k]
		var end_ts: int = int(raw) if raw is int or raw is float else int(str(raw).to_int())
		if end_ts > now:
			rows.append({"key": str(k), "end": end_ts})
	rows.sort_custom(func(a: Dictionary, b: Dictionary) -> bool: return a["end"] < b["end"])
	_active = rows
	_sync_free_state()
	_rebuild_list()
	_update_root_visibility()
	_schedule_reposition()


func _rebuild_list() -> void:
	for i in range(_list.get_child_count() - 1, -1, -1):
		_list.get_child(i).free()
	for item in _active:
		var row := HBoxContainer.new()
		row.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		row.size_flags_vertical = Control.SIZE_SHRINK_BEGIN
		row.add_theme_constant_override("separation", 12)
		var name_lab := Label.new()
		name_lab.text = _pretty_cost_name(str(item["key"]))
		name_lab.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		name_lab.size_flags_vertical = Control.SIZE_SHRINK_BEGIN
		name_lab.autowrap_mode = TextServer.AUTOWRAP_OFF
		## Timers sit in a sibling label; avoid clipping so [code]!spawn bat[/code]-style labels stay readable.
		name_lab.clip_text = false
		name_lab.text_overrun_behavior = TextServer.OVERRUN_NO_TRIMMING
		var time_lab := Label.new()
		time_lab.set_meta("end_ts", int(item["end"]))
		time_lab.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
		time_lab.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
		time_lab.size_flags_vertical = Control.SIZE_SHRINK_BEGIN
		row.add_child(name_lab)
		row.add_child(time_lab)
		_list.add_child(row)
	_apply_fonts()
	_refresh_time_labels()
	call_deferred("_fit_name_column_widths")


func _pretty_cost_name(key: String) -> String:
	var k := str(key).strip_edges()
	var slug: Variant = _monster_slug_from_cost_key(k)
	if slug == null:
		var kc := k.to_lower()
		if _COST_COMMAND_LABEL.has(kc):
			return str(_COST_COMMAND_LABEL[kc])
		return _fallback_cost_command_label(k)
	var mob_slug: String = slug as String
	## Same ids as chat — [code]!spawn bat[/code], [code]effective_cost("cost_per_monster." + monster, …)[/code].
	if mob_slug.is_empty():
		return "!spawn"
	return "!spawn %s" % mob_slug.to_lower()


## [code]cost_per_monster.rat[/code], [code]cost_per_monster_rat[/code], or bare [code]cost_per_monster[/code]. Case-insensitive on the prefix.
func _monster_slug_from_cost_key(k: String) -> Variant:
	var lower := k.to_lower()
	var dot_prefix := "cost_per_monster."
	var us_prefix := "cost_per_monster_"
	if lower == "cost_per_monster":
		return ""
	if lower.begins_with(dot_prefix):
		return k.substr(dot_prefix.length()).strip_edges()
	if lower.begins_with(us_prefix):
		return k.substr(us_prefix.length()).strip_edges()
	return null


func _fallback_cost_command_label(k: String) -> String:
	var tail: String = k.to_lower().strip_edges()
	var pfx := "cost_per_"
	if tail.begins_with(pfx):
		tail = tail.substr(pfx.length())
	return "!" + tail.replace("_", "")


func _fit_name_column_widths() -> void:
	if not is_instance_valid(_list):
		return
	for row in _list.get_children():
		if row.get_child_count() < 2:
			continue
		var name_lab: Label = row.get_child(0) as Label
		if name_lab == null:
			continue
		var ms: Vector2 = name_lab.get_minimum_size()
		name_lab.custom_minimum_size.x = maxf(ms.x, 1.0)
	_fit_to_content()


func _refresh_time_labels() -> void:
	for row in _list.get_children():
		if row.get_child_count() < 2:
			continue
		var time_lab: Label = row.get_child(1) as Label
		if time_lab == null:
			continue
		var end_ts: int = int(time_lab.get_meta("end_ts", 0))
		time_lab.text = _format_remain(end_ts)


func _format_remain(end_ts: int) -> String:
	var now: int = int(Time.get_unix_time_from_system())
	var s: int = maxi(0, end_ts - now)
	var h: int = int(s / 3600.0)
	var m: int = int((s % 3600) / 60.0)
	var sec: int = s % 60
	if h > 0:
		return "%d:%02d:%02d left" % [h, m, sec]
	return "%d:%02d left" % [m, sec]


func _apply_fonts() -> void:
	var fs: int = clampi(CompanionConfig.free_promos_font_size_px, 8, 36)
	var title_sz: int = mini(fs + 2, 40)
	_title.add_theme_font_override("font", _FONT)
	_title.add_theme_font_size_override("font_size", title_sz)
	_title.add_theme_color_override("font_color", Color(1, 1, 68.0 / 255.0, 1))
	_title.add_theme_color_override("font_shadow_color", Color(0, 0, 0, 0.65))
	_title.add_theme_constant_override("shadow_offset_x", 1)
	_title.add_theme_constant_override("shadow_offset_y", 1)
	for row in _list.get_children():
		var kids := row.get_children()
		if kids.size() < 2:
			continue
		_style_body_label(kids[0] as Label, fs)
		_style_timer_label(kids[1] as Label, fs)
	_apply_size_cap()
	call_deferred("_fit_name_column_widths")


func _style_body_label(lab: Label, fs: int) -> void:
	if lab == null:
		return
	lab.add_theme_font_override("font", _FONT)
	lab.add_theme_font_size_override("font_size", fs)
	lab.add_theme_color_override("font_color", Color(0.95, 0.92, 0.85, 1))
	lab.add_theme_color_override("font_shadow_color", Color(0, 0, 0, 0.55))
	lab.add_theme_constant_override("shadow_offset_x", 1)
	lab.add_theme_constant_override("shadow_offset_y", 1)


func _style_timer_label(lab: Label, fs: int) -> void:
	if lab == null:
		return
	lab.add_theme_font_override("font", _FONT)
	lab.add_theme_font_size_override("font_size", fs)
	lab.add_theme_color_override("font_color", Color(1, 0.95, 0.62, 1))
	lab.add_theme_color_override("font_shadow_color", Color(0, 0, 0, 0.55))
	lab.add_theme_constant_override("shadow_offset_x", 1)
	lab.add_theme_constant_override("shadow_offset_y", 1)


func _schedule_reposition() -> void:
	call_deferred("_reposition_to_corner")


func _fit_to_content() -> void:
	if _list:
		_list.reset_size()
	if _outer_vbox:
		_outer_vbox.reset_size()
	if _margin:
		_margin.reset_size()
	custom_minimum_size = Vector2.ZERO
	reset_size()
	if not visible or _active.is_empty():
		size = Vector2.ZERO
		return
	var sz: Vector2 = get_combined_minimum_size()
	if sz.x < 2.0 or sz.y < 2.0:
		return
	size = sz


func _reposition_to_corner() -> void:
	if not visible:
		return
	_fit_to_content()
	var r: Rect2 = get_viewport().get_visible_rect()
	var L := CompanionConfig.layout_data_for(self)
	var mx: int = L.free_promos_margin_x
	var my: int = L.free_promos_margin_y
	var corner: int = clampi(L.free_promos_corner, 0, 3)
	var sz: Vector2 = size
	if sz.x < 2.0 or sz.y < 2.0:
		sz = get_combined_minimum_size()
	if sz.x < 2.0 or sz.y < 2.0:
		return
	var x: float = 0.0
	var y: float = 0.0
	match corner:
		0:
			x = float(mx)
			y = float(my)
		1:
			x = r.size.x - float(mx) - sz.x
			y = float(my)
		2:
			x = float(mx)
			y = r.size.y - float(my) - sz.y
		_:
			x = r.size.x - float(mx) - sz.x
			y = r.size.y - float(my) - sz.y
	position = Vector2(x, y)
	size = sz
