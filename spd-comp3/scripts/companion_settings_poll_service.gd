extends Node
## Polls Lastest UI GET /api/companion-settings and applies layout/UI when revision increases.
## Also POSTs on request (Settings upload / push-on-save).

signal applied(revision: int)
signal pushed(revision: int)
signal connection_lost
signal connection_restored

var is_server_connected: bool = false
var last_server_revision: int = 0

var _http: HTTPRequest
var _push_http: HTTPRequest
var _hb_http: HTTPRequest
var _timer: Timer
var _was_connected: bool = true
var _poll_in_flight: bool = false
var _push_in_flight: bool = false
var _force_next_apply: bool = false


func _ready() -> void:
	_http = HTTPRequest.new()
	_http.timeout = 5.0
	add_child(_http)
	_http.request_completed.connect(_on_poll_completed)

	_push_http = HTTPRequest.new()
	_push_http.timeout = 8.0
	add_child(_push_http)
	_push_http.request_completed.connect(_on_push_completed)

	_hb_http = HTTPRequest.new()
	_hb_http.timeout = 4.0
	add_child(_hb_http)

	_timer = Timer.new()
	add_child(_timer)
	_timer.timeout.connect(_poll)
	CompanionConfig.settings_saved.connect(_on_settings_saved)
	CompanionConfig.settings_loaded.connect(_on_settings_reloaded)
	_apply_poll_timer()
	_poll()


func _on_settings_reloaded() -> void:
	_apply_poll_timer()


func _on_settings_saved() -> void:
	_apply_poll_timer()
	if CompanionConfig._remote_apply_in_progress:
		return
	if CompanionConfig.remote_settings_push_on_save and CompanionConfig.remote_settings_enabled:
		push_now()


func _apply_poll_timer() -> void:
	if not CompanionConfig.remote_settings_enabled:
		_timer.stop()
		return
	_timer.wait_time = maxf(0.5, CompanionConfig.remote_settings_poll_sec)
	if _timer.is_stopped():
		_timer.start()
	else:
		_timer.start()


func _base_url() -> String:
	var base := CompanionConfig.remote_settings_base_url.strip_edges().trim_suffix("/")
	if base.is_empty():
		base = CompanionConfig.bestiary_base_url.strip_edges().trim_suffix("/")
	if base.is_empty():
		base = CompanionConfig.summon_march_base_url.strip_edges().trim_suffix("/")
	if base.is_empty():
		base = "http://127.0.0.1:5000"
	return base


func _poll_url() -> String:
	return _base_url() + "/api/companion-settings"


func _heartbeat_url() -> String:
	return _base_url() + "/api/companion-settings/heartbeat"


func _post_heartbeat(poll_ok: bool = true) -> void:
	if not CompanionConfig.remote_settings_enabled:
		return
	var body := JSON.stringify(
		{
			"applied_revision": CompanionConfig.remote_settings_applied_revision,
			"poll_ok": poll_ok,
		}
	)
	_hb_http.cancel_request()
	_hb_http.request(
		_heartbeat_url(),
		PackedStringArray(["Content-Type: application/json"]),
		HTTPClient.METHOD_POST,
		body
	)


func _poll() -> void:
	if not CompanionConfig.remote_settings_enabled:
		return
	if _poll_in_flight:
		return
	_poll_in_flight = true
	var err := _http.request(_poll_url())
	if err != OK:
		_poll_in_flight = false
		_set_connected(false)


## Force apply even if revision is unchanged (Settings "Pull now").
func pull_now(force: bool = true) -> void:
	if _poll_in_flight:
		return
	_force_next_apply = force
	_poll_in_flight = true
	var err := _http.request(_poll_url())
	if err != OK:
		_poll_in_flight = false
		_force_next_apply = false
		_set_connected(false)


func push_now() -> void:
	if _push_in_flight:
		return
	_push_in_flight = true
	var body := JSON.stringify(
		{
			"settings": CompanionConfig.to_remote_dict(),
			"source": "companion",
		}
	)
	var err := _push_http.request(
		_poll_url(),
		PackedStringArray(["Content-Type: application/json"]),
		HTTPClient.METHOD_POST,
		body
	)
	if err != OK:
		_push_in_flight = false
		_set_connected(false)


func _on_poll_completed(
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
		push_warning("[CompanionSettingsPoll] Invalid JSON: %s" % text.left(120))
		_force_next_apply = false
		return
	var data = json.get_data()
	if typeof(data) != TYPE_DICTIONARY:
		_force_next_apply = false
		return
	var force := _force_next_apply
	_force_next_apply = false
	_apply_server_payload(data as Dictionary, force)
	_post_heartbeat(true)


func _apply_server_payload(data: Dictionary, force: bool) -> void:
	var rev := int(data.get("revision", 0))
	last_server_revision = rev
	var settings: Variant = data.get("settings", {})
	if typeof(settings) != TYPE_DICTIONARY:
		return
	if (not force) and rev <= CompanionConfig.remote_settings_applied_revision:
		return
	if (settings as Dictionary).is_empty() and rev <= 0:
		return
	CompanionConfig._remote_apply_in_progress = true
	CompanionConfig.apply_remote_dict(settings as Dictionary)
	CompanionConfig.remote_settings_applied_revision = rev
	CompanionConfig.save_settings()
	CompanionConfig._remote_apply_in_progress = false
	applied.emit(rev)


func apply_payload_force(data: Dictionary) -> void:
	_apply_server_payload(data, true)


func _on_push_completed(
	result: int,
	response_code: int,
	_headers: PackedStringArray,
	body: PackedByteArray,
) -> void:
	_push_in_flight = false
	if result != HTTPRequest.RESULT_SUCCESS or response_code != 200:
		_set_connected(false)
		return
	_set_connected(true)
	var text := body.get_string_from_utf8()
	var json := JSON.new()
	if json.parse(text) != OK:
		return
	var data = json.get_data()
	if typeof(data) != TYPE_DICTIONARY:
		return
	var rev := int((data as Dictionary).get("revision", 0))
	if rev > 0:
		CompanionConfig.remote_settings_applied_revision = rev
		last_server_revision = rev
		CompanionConfig.save_settings_quiet()
		pushed.emit(rev)


func _set_connected(ok: bool) -> void:
	is_server_connected = ok
	if ok and not _was_connected:
		connection_restored.emit()
	elif not ok and _was_connected:
		connection_lost.emit()
	_was_connected = ok
