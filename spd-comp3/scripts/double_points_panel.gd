extends PanelContainer

## Polls Lastest UI [code]GET /api/double-points-remaining[/code] and shows global 2× time left.

const _FONT: FontFile = preload("res://assets/fonts/pixel_font.ttf")
const _SpdUi := preload("res://scripts/spd_ui_art.gd")

@onready var _margin: MarginContainer = $MarginContainer
@onready var _label: Label = $MarginContainer/Label

var _http: HTTPRequest
var _poll_accum: float = 999.0
var _tick_accum: float = 0.0
var _poll_in_flight: bool = false


func _ready() -> void:
	_http = HTTPRequest.new()
	_http.timeout = 5.0
	add_child(_http)
	_http.request_completed.connect(_on_http_done)
	CompanionConfig.settings_saved.connect(_on_cfg)
	CompanionConfig.settings_loaded.connect(_on_cfg)
	get_viewport().size_changed.connect(_schedule_reposition)
	if not DoublePointsState.active_changed.is_connected(_on_state_changed):
		DoublePointsState.active_changed.connect(_on_state_changed)
	_on_cfg()


func _on_cfg() -> void:
	_poll_accum = 999.0
	_apply_chrome()
	_apply_padding()
	_apply_fonts()
	_refresh_from_state()
	_schedule_reposition()
	if not CompanionConfig.is_vertical_layout(self):
		_request_poll()


func _on_state_changed() -> void:
	_refresh_from_state()
	_schedule_reposition()


func _process(delta: float) -> void:
	_tick_accum += delta
	if _tick_accum >= 0.25:
		_tick_accum = 0.0
		_refresh_from_state()
	if CompanionConfig.is_vertical_layout(self):
		return
	if not CompanionConfig.double_points_panel_visible:
		return
	_poll_accum += delta
	var step: float = maxf(0.5, CompanionConfig.double_points_poll_sec)
	if _poll_accum >= step:
		_poll_accum = 0.0
		_request_poll()


func _poll_url() -> String:
	var base := CompanionConfig.bestiary_base_url.strip_edges().trim_suffix("/")
	if base.is_empty():
		base = CompanionConfig.summon_march_base_url.strip_edges().trim_suffix("/")
	if base.is_empty():
		base = "http://127.0.0.1:5000"
	return base + "/api/double-points-remaining"


func _request_poll() -> void:
	if _poll_in_flight:
		return
	if not CompanionConfig.double_points_panel_visible:
		return
	_poll_in_flight = true
	var err := _http.request(_poll_url())
	if err != OK:
		_poll_in_flight = false


func _on_http_done(
	result: int,
	response_code: int,
	_headers: PackedStringArray,
	body: PackedByteArray,
) -> void:
	_poll_in_flight = false
	if result != HTTPRequest.RESULT_SUCCESS or response_code != 200:
		return
	var json := JSON.new()
	if json.parse(body.get_string_from_utf8()) != OK:
		return
	var data = json.get_data()
	if typeof(data) != TYPE_DICTIONARY:
		return
	var payload: Dictionary = data
	var is_active := bool(payload.get("active", false))
	var secs := int(payload.get("seconds_left", 0))
	DoublePointsState.set_countdown(is_active, secs)


func _refresh_from_state() -> void:
	var text := DoublePointsState.display_text()
	if _label:
		_label.text = text
	var want := (
		CompanionConfig.element_enabled(self, "double_points")
		and DoublePointsState.seconds_left() > 0
		and not text.is_empty()
	)
	visible = want
	if want:
		_schedule_reposition()


func _apply_chrome() -> void:
	add_theme_stylebox_override(
		"panel",
		_SpdUi.chrome_style(
			CompanionConfig.double_points_chrome_style, CompanionConfig.double_points_chrome_scale
		)
	)


func _apply_padding() -> void:
	if _margin == null:
		return
	var pad_h := clampi(CompanionConfig.double_points_padding_h_px, 0, 64)
	var pad_v := clampi(CompanionConfig.double_points_padding_v_px, 0, 64)
	_margin.add_theme_constant_override("margin_left", pad_h)
	_margin.add_theme_constant_override("margin_right", pad_h)
	_margin.add_theme_constant_override("margin_top", pad_v)
	_margin.add_theme_constant_override("margin_bottom", pad_v)


func _apply_fonts() -> void:
	if _label == null:
		return
	var fs: int = clampi(CompanionConfig.double_points_font_size_px, 8, 48)
	_label.add_theme_font_override("font", _FONT)
	_label.add_theme_font_size_override("font_size", fs)
	_label.add_theme_color_override("font_color", CompanionConfig.double_points_font_color)
	_label.add_theme_color_override("font_shadow_color", Color(0, 0, 0, 0.65))
	_label.add_theme_constant_override("shadow_offset_x", 1)
	_label.add_theme_constant_override("shadow_offset_y", 1)


func _schedule_reposition() -> void:
	call_deferred("_reposition_to_corner")


func _reposition_to_corner() -> void:
	if not visible:
		return
	var r: Rect2 = get_viewport().get_visible_rect()
	var L := CompanionConfig.layout_data_for(self)
	var mx: int = L.double_points_margin_x
	var my: int = L.double_points_margin_y
	var corner: int = clampi(L.double_points_corner, 0, 3)
	reset_size()
	var sz: Vector2 = get_combined_minimum_size()
	if sz.x < 2.0 or sz.y < 2.0:
		sz = size
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
