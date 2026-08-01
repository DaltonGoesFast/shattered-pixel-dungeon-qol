extends Node
## Polls Lastest UI GET /api/bestiary for co-op XP bar, sprint, and heat (read-only).

signal state_updated(payload: Dictionary)
signal level_up(payload: Dictionary)
signal connection_lost
signal connection_restored

var last_payload: Dictionary = {}
var is_server_connected: bool = false

var _http: HTTPRequest
var _timer: Timer
var _was_connected: bool = true
var _poll_in_flight: bool = false
var _last_level: int = -1


func _ready() -> void:
	_http = HTTPRequest.new()
	_http.timeout = 5.0
	add_child(_http)
	_http.request_completed.connect(_on_request_completed)

	_timer = Timer.new()
	add_child(_timer)
	_timer.timeout.connect(_poll)
	CompanionConfig.settings_saved.connect(_on_settings)
	CompanionConfig.settings_loaded.connect(_on_settings)
	_apply_poll_timer()
	_poll()


func _on_settings() -> void:
	_apply_poll_timer()


func _apply_poll_timer() -> void:
	if not CompanionConfig.bestiary_hud_enabled:
		_timer.stop()
		return
	_timer.wait_time = maxf(0.25, CompanionConfig.bestiary_poll_sec)
	if _timer.is_stopped():
		_timer.start()
	else:
		_timer.start()


func _poll_url() -> String:
	var base := CompanionConfig.bestiary_base_url.strip_edges().trim_suffix("/")
	if base.is_empty():
		base = CompanionConfig.summon_march_base_url.strip_edges().trim_suffix("/")
	return base + "/api/bestiary"


func _poll() -> void:
	if not CompanionConfig.bestiary_hud_enabled:
		return
	if _poll_in_flight:
		return
	_poll_in_flight = true
	var err := _http.request(_poll_url())
	if err != OK:
		_poll_in_flight = false
		_set_connected(false)


func _on_request_completed(
	result: int,
	response_code: int,
	_headers: PackedStringArray,
	body: PackedByteArray,
) -> void:
	_poll_in_flight = false
	if result != HTTPRequest.RESULT_SUCCESS or response_code != 200:
		_set_connected(false)
		return
	_set_connected(true)
	var text := body.get_string_from_utf8()
	var json := JSON.new()
	if json.parse(text) != OK:
		push_warning("[BestiaryPoll] Invalid JSON: %s" % text.left(120))
		return
	var data = json.get_data()
	if typeof(data) != TYPE_DICTIONARY:
		return
	var payload: Dictionary = data
	var lvl := int(payload.get("level", 1))
	if _last_level >= 1 and lvl > _last_level:
		level_up.emit(payload)
	_last_level = lvl
	last_payload = payload.duplicate(true)
	state_updated.emit(payload)


func _set_connected(ok: bool) -> void:
	is_server_connected = ok
	if ok and not _was_connected:
		connection_restored.emit()
	elif not ok and _was_connected:
		connection_lost.emit()
	_was_connected = ok
