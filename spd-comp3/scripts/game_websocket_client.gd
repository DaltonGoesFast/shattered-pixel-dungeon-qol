extends Node

## Read-only WebSocket client to the SPD QoL desktop streaming server.

signal connected_to_game
signal disconnected_from_game
signal snapshot_received(data: Dictionary)
## Emitted for messages with top-level "type" string (results, events).
signal typed_message_received(type_name: String, data: Dictionary)
## Emitted for every inbound message whose top-level type ends with _result (StreamingServer.java).
signal command_result(success: bool, type_name: String, data: Dictionary)
## Convenience for spawn flows; success derives from common keys (verify in Java).
signal spawn_result(success: bool, data: Dictionary)

const _STATE_OPEN := WebSocketPeer.STATE_OPEN

var _peer: WebSocketPeer
var _http: HTTPRequest
var _http_accum: float = 0.0
var _reconnect_accum: float = 0.0
var _prev_connected: bool = false
var _ws_close_pump_remaining: int = 0
## Latest snapshot from the game (messages without top-level `type`). ID strip + rune lookup use this.
var last_game_snapshot: Dictionary = {}

func _ready() -> void:
	_peer = WebSocketPeer.new()
	_http = HTTPRequest.new()
	add_child(_http)
	_http.request_completed.connect(_on_http_completed)
	_http_accum = CompanionConfig.http_poll_interval_sec
	CompanionConfig.settings_saved.connect(_on_settings_saved)
	_try_connect()
	set_physics_process(true)


func _on_settings_saved() -> void:
	_http_accum = CompanionConfig.http_poll_interval_sec
	# Immediate close so we are not left in STATE_CLOSING when reconnecting the same frame.
	_peer.close(-1)
	_reconnect_accum = 0.0
	_try_connect()


func is_connected_to_game() -> bool:
	return _peer != null and _peer.get_ready_state() == WebSocketPeer.STATE_OPEN


func _unhandled_key_input(event: InputEvent) -> void:
	if event is InputEventKey and event.pressed and not event.echo:
		if event.keycode == KEY_F5:
			CompanionConfig.load_settings()
			StreamerBotUdp.rebind()
			_peer.close(-1)
			_reconnect_accum = 0.0
			_ws_close_pump_remaining = 120
			call_deferred("_try_connect")


func _physics_process(delta: float) -> void:
	_peer.poll()
	var state := _peer.get_ready_state()
	if state == _STATE_OPEN:
		if not _prev_connected:
			_prev_connected = true
			connected_to_game.emit()
		while _peer.get_available_packet_count() > 0:
			var pkt := _peer.get_packet()
			_handle_text(pkt.get_string_from_utf8())
		return

	if _prev_connected:
		_prev_connected = false
		disconnected_from_game.emit()

	if state == WebSocketPeer.STATE_CLOSED:
		_reconnect_accum += delta
		if _reconnect_accum >= CompanionConfig.ws_reconnect_sec:
			_reconnect_accum = 0.0
			_try_connect()

	if (
		state != _STATE_OPEN
		and CompanionConfig.http_fallback_enabled
	):
		_http_accum += delta
		if _http_accum >= CompanionConfig.http_poll_interval_sec:
			_http_accum = 0.0
			_request_http_fallback()


func disconnect_ws() -> void:
	_peer.close()


func _try_connect() -> void:
	## [method WebSocketPeer.connect_to_url] is not valid while the peer is in [constant WebSocketPeer.STATE_CLOSING].
	## A tight poll loop here has caused native crashes on some builds; defer and poll once per idle instead.
	if _peer == null:
		return
	_peer.poll()
	var st: int = _peer.get_ready_state()
	if st == WebSocketPeer.STATE_CLOSING:
		_ws_close_pump_remaining -= 1
		if _ws_close_pump_remaining <= 0:
			push_warning("GameWebSocketClient: WebSocket stuck in CLOSING; skipped connect_to_url")
			_ws_close_pump_remaining = 0
			return
		call_deferred("_try_connect")
		return
	_ws_close_pump_remaining = 0

	if st == WebSocketPeer.STATE_OPEN or st == WebSocketPeer.STATE_CONNECTING:
		return

	if st != WebSocketPeer.STATE_CLOSED:
		return

	var host := CompanionConfig.game_ws_host.strip_edges()
	var port: int = CompanionConfig.game_ws_port
	var url := "ws://%s:%d" % [host, port]
	var err := _peer.connect_to_url(url)
	if err != OK:
		push_warning("GameWebSocketClient: connect_to_url failed: %s err=%d" % [url, err])


func _handle_text(text: String) -> void:
	if text.is_empty():
		return
	var json := JSON.new()
	if json.parse(text) != OK:
		push_warning("GameWebSocketClient: JSON parse error")
		return
	var data = json.data
	if data is Dictionary:
		_dispatch_dictionary(data)


func _dispatch_dictionary(data: Dictionary) -> void:
	if data.has("type") and data["type"] is String:
		var t: String = data["type"]
		typed_message_received.emit(t, data)
		if t.ends_with("_result"):
			var ok := _infer_success(data)
			command_result.emit(ok, t, data)
			if t == "spawn_result":
				spawn_result.emit(ok, data)
	else:
		last_game_snapshot = (data as Dictionary).duplicate(true)
		snapshot_received.emit(data)


func _infer_success(data: Dictionary) -> bool:
	## Adjust keys after verifying StreamingServer.java
	if data.has("success"):
		return bool(data["success"])
	if data.has("ok"):
		return bool(data["ok"])
	if data.has("error"):
		return str(data["error"]).is_empty()
	return false


func _request_http_fallback() -> void:
	if _peer.get_ready_state() == _STATE_OPEN:
		return
	if _http.get_http_client_status() != HTTPClient.STATUS_DISCONNECTED:
		return
	var err := _http.request(CompanionConfig.http_fallback_url)
	if err != OK:
		push_warning("GameWebSocketClient: HTTP fallback request failed err=%d" % err)


func _on_http_completed(
	_result: int, _code: int, _headers: PackedStringArray, body: PackedByteArray
) -> void:
	if body.is_empty():
		return
	var text := body.get_string_from_utf8()
	var json := JSON.new()
	if json.parse(text) != OK:
		return
	var data = json.data
	if data is Dictionary:
		if data.has("type") and data["type"] is String:
			_dispatch_dictionary(data)
		else:
			last_game_snapshot = (data as Dictionary).duplicate(true)
			snapshot_received.emit(data)
