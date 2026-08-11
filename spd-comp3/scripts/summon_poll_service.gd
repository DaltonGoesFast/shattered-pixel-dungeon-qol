extends Node
## Polls Lastest UI /api/summon-march for chat !summon events (read-only).

signal summon_received(event: Dictionary)
signal connection_lost
signal connection_restored

var last_seen_id: String = ""
var seen_ids: Dictionary = {}
var is_server_connected: bool = false

var _http: HTTPRequest
var _timer: Timer
var _was_connected: bool = true
var _first_poll: bool = true
var _poll_in_flight: bool = false
var _start_unix: int = 0


func _ready() -> void:
	_start_unix = int(Time.get_unix_time_from_system())
	_http = HTTPRequest.new()
	_http.timeout = 5.0
	add_child(_http)
	_http.request_completed.connect(_on_request_completed)

	_timer = Timer.new()
	add_child(_timer)
	_timer.timeout.connect(_poll)
	CompanionConfig.settings_saved.connect(_on_settings_saved)
	CompanionConfig.settings_loaded.connect(_on_settings_saved)
	_apply_poll_timer()
	_poll()


func _on_settings_saved() -> void:
	_apply_poll_timer()


func _apply_poll_timer() -> void:
	if not CompanionConfig.summon_march_enabled:
		_timer.stop()
		return
	_timer.wait_time = maxf(0.15, CompanionConfig.summon_march_poll_sec)
	if not _timer.is_stopped():
		_timer.start()
	else:
		_timer.start()


func _poll_url() -> String:
	var base := CompanionConfig.summon_march_base_url.strip_edges().trim_suffix("/")
	var url := base + "/api/summon-march"
	if last_seen_id != "":
		url += "?since=" + last_seen_id.uri_encode()
	return url


func _poll() -> void:
	if not CompanionConfig.summon_march_enabled:
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
		push_warning("[SummonPoll] Invalid JSON: %s" % text.left(120))
		return

	var data = json.get_data()
	if typeof(data) != TYPE_DICTIONARY:
		return

	var events: Array = data.get("events", [])
	if events.is_empty():
		return

	if _first_poll and CompanionConfig.summon_march_skip_backlog:
		var fresh: Array = []
		var skipped := 0
		for raw in events:
			if typeof(raw) != TYPE_DICTIONARY:
				continue
			var event: Dictionary = raw
			var ts := int(event.get("ts", 0))
			if ts > 0 and ts < _start_unix:
				skipped += 1
				var id := str(event.get("id", ""))
				if not id.is_empty():
					seen_ids[id] = true
					last_seen_id = id
			else:
				fresh.append(event)
		_first_poll = false
		if skipped > 0:
			print(
				"[SummonPoll] Skipped %d pre-start event(s); cursor at %s"
				% [skipped, last_seen_id]
			)
		if not fresh.is_empty():
			_process_events(fresh)
		return

	_first_poll = false
	_process_events(events)


func _process_events(events: Array) -> void:
	for raw in events:
		if typeof(raw) != TYPE_DICTIONARY:
			continue
		var event: Dictionary = raw
		var id: String = str(event.get("id", ""))
		if id.is_empty() or seen_ids.has(id):
			continue

		seen_ids[id] = true
		last_seen_id = id
		print(
			"[SummonPoll] New summon: %s from %s badge=%s crowned=%s (%s)"
			% [
				event.get("monster", "?"),
				event.get("username", "?"),
				event.get("badge", ""),
				event.get("crowned", false),
				id,
			]
		)
		summon_received.emit(event)


func _set_connected(ok: bool) -> void:
	is_server_connected = ok
	if ok and not _was_connected:
		connection_restored.emit()
	elif not ok and _was_connected:
		connection_lost.emit()
	_was_connected = ok
