extends PanelContainer

## Polls Lastest UI GET /api/nine-challenge-deaths and shows the career count.

const _FONT: FontFile = preload("res://assets/fonts/pixel_font.ttf")
const _SpdUi := preload("res://scripts/spd_ui_art.gd")

@onready var _margin: MarginContainer = $MarginContainer
@onready var _label: Label = $MarginContainer/Label

var _http: HTTPRequest
var _poll_accum: float = 999.0
var _poll_in_flight: bool = false


func _ready() -> void:
	_http = HTTPRequest.new()
	_http.timeout = 5.0
	add_child(_http)
	_http.request_completed.connect(_on_http_done)
	CompanionConfig.settings_saved.connect(_on_cfg)
	CompanionConfig.settings_loaded.connect(_on_cfg)
	get_viewport().size_changed.connect(_schedule_reposition)
	if not NineChallengeDeathsState.count_changed.is_connected(_on_state_changed):
		NineChallengeDeathsState.count_changed.connect(_on_state_changed)
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
	if CompanionConfig.is_vertical_layout(self):
		return
	if not CompanionConfig.nine_challenge_deaths_panel_visible:
		return
	_poll_accum += delta
	var step: float = maxf(0.5, CompanionConfig.nine_challenge_deaths_poll_sec)
	if _poll_accum >= step:
		_poll_accum = 0.0
		_request_poll()


func _poll_url() -> String:
	var base := CompanionConfig.bestiary_base_url.strip_edges().trim_suffix("/")
	if base.is_empty():
		base = CompanionConfig.summon_march_base_url.strip_edges().trim_suffix("/")
	if base.is_empty():
		base = "http://127.0.0.1:5000"
	return base + "/api/nine-challenge-deaths"


func _request_poll() -> void:
	if _poll_in_flight or not CompanionConfig.nine_challenge_deaths_panel_visible:
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
	NineChallengeDeathsState.set_count(int((data as Dictionary).get("count", 1314)))


func _refresh_from_state() -> void:
	if _label:
		_label.text = NineChallengeDeathsState.display_text()
	var want := CompanionConfig.element_enabled(self, "nine_challenge_deaths")
	visible = want
	if want:
		_schedule_reposition()


func _apply_chrome() -> void:
	add_theme_stylebox_override(
		"panel",
		_SpdUi.chrome_style(
			CompanionConfig.nine_challenge_deaths_chrome_style,
			CompanionConfig.nine_challenge_deaths_chrome_scale
		)
	)


func _apply_padding() -> void:
	if _margin == null:
		return
	var pad_h := clampi(CompanionConfig.nine_challenge_deaths_padding_h_px, 0, 64)
	var pad_v := clampi(CompanionConfig.nine_challenge_deaths_padding_v_px, 0, 64)
	_margin.add_theme_constant_override("margin_left", pad_h)
	_margin.add_theme_constant_override("margin_right", pad_h)
	_margin.add_theme_constant_override("margin_top", pad_v)
	_margin.add_theme_constant_override("margin_bottom", pad_v)


func _apply_fonts() -> void:
	if _label == null:
		return
	var fs: int = clampi(CompanionConfig.nine_challenge_deaths_font_size_px, 8, 48)
	_label.add_theme_font_override("font", _FONT)
	_label.add_theme_font_size_override("font_size", fs)
	_label.add_theme_color_override("font_color", CompanionConfig.nine_challenge_deaths_font_color)
	_label.add_theme_color_override("font_shadow_color", Color(0, 0, 0, 0.65))
	_label.add_theme_constant_override("shadow_offset_x", 1)
	_label.add_theme_constant_override("shadow_offset_y", 1)


func _schedule_reposition() -> void:
	call_deferred("_reposition_to_corner")


func _reposition_to_corner() -> void:
	if not visible:
		return
	var r: Rect2 = get_viewport().get_visible_rect()
	var layout := CompanionConfig.layout_data_for(self)
	var mx: int = layout.nine_challenge_deaths_margin_x
	var my: int = layout.nine_challenge_deaths_margin_y
	var corner: int = clampi(layout.nine_challenge_deaths_corner, 0, 3)
	reset_size()
	var panel_size: Vector2 = get_combined_minimum_size()
	if panel_size.x < 2.0 or panel_size.y < 2.0:
		panel_size = size
	var x: float
	var y: float
	match corner:
		0:
			x = float(mx)
			y = float(my)
		1:
			x = r.size.x - float(mx) - panel_size.x
			y = float(my)
		2:
			x = float(mx)
			y = r.size.y - float(my) - panel_size.y
		_:
			x = r.size.x - float(mx) - panel_size.x
			y = r.size.y - float(my) - panel_size.y
	position = Vector2(x, y)
	size = panel_size
