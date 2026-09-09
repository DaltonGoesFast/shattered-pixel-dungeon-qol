extends Node

## Polls platform APIs once and shares results with both companion canvases.

var _twitch_http: HTTPRequest
var _youtube_http: HTTPRequest
var _twitch_phase: String = ""
var _youtube_phase: String = ""
var _twitch_token: String = ""
var _twitch_token_expires_msec: int = 0
var _youtube_stream_ids: PackedStringArray = []
var _twitch_elapsed: float = 999.0
var _youtube_elapsed: float = 999.0
var _youtube_search_elapsed: float = 999.0

var _caster_peer := WebSocketPeer.new()
var _caster_url: String = ""
var _caster_reconnect_elapsed: float = 999.0
var _caster_was_open: bool = false


func _ready() -> void:
	_twitch_http = HTTPRequest.new()
	_twitch_http.timeout = 8.0
	add_child(_twitch_http)
	_twitch_http.request_completed.connect(_on_twitch_http_done)
	_youtube_http = HTTPRequest.new()
	_youtube_http.timeout = 8.0
	add_child(_youtube_http)
	_youtube_http.request_completed.connect(_on_youtube_http_done)
	CompanionConfig.settings_loaded.connect(_reset)
	CompanionConfig.settings_saved.connect(_reset)
	_reset()


func _reset() -> void:
	_twitch_elapsed = 999.0
	_youtube_elapsed = 999.0
	_youtube_search_elapsed = 999.0
	_youtube_stream_ids.clear()
	_twitch_token = ""
	_twitch_token_expires_msec = 0
	_twitch_phase = ""
	_youtube_phase = ""
	_connect_casterlabs(true)


func _process(delta: float) -> void:
	_poll_casterlabs(delta)
	if not CompanionConfig.viewer_counts_panel_visible:
		return

	if CompanionConfig.viewer_counts_show_twitch:
		_twitch_elapsed += delta
		if _twitch_elapsed >= CompanionConfig.viewer_counts_twitch_poll_sec:
			_twitch_elapsed = 0.0
			_request_twitch()

	if CompanionConfig.viewer_counts_show_youtube:
		_youtube_elapsed += delta
		_youtube_search_elapsed += delta
		if _youtube_search_elapsed >= CompanionConfig.viewer_counts_youtube_search_sec:
			_request_youtube_search()
		elif _youtube_elapsed >= CompanionConfig.viewer_counts_youtube_poll_sec:
			_request_youtube_details()


func _request_twitch() -> void:
	if _twitch_http.get_http_client_status() != HTTPClient.STATUS_DISCONNECTED:
		return
	var client_id := CompanionConfig.viewer_counts_twitch_client_id.strip_edges()
	var secret := CompanionConfig.viewer_counts_twitch_client_secret.strip_edges()
	var channel := CompanionConfig.viewer_counts_twitch_channel.strip_edges()
	if client_id.is_empty() or secret.is_empty() or channel.is_empty():
		ViewerCountsState.set_error("twitch", "Twitch credentials are incomplete")
		return
	if _twitch_token.is_empty() or Time.get_ticks_msec() >= _twitch_token_expires_msec:
		_twitch_phase = "token"
		var body := (
			"client_id=%s&client_secret=%s&grant_type=client_credentials"
			% [client_id.uri_encode(), secret.uri_encode()]
		)
		var err := _twitch_http.request(
			"https://id.twitch.tv/oauth2/token",
			PackedStringArray(["Content-Type: application/x-www-form-urlencoded"]),
			HTTPClient.METHOD_POST,
			body
		)
		if err != OK:
			_twitch_phase = ""
			ViewerCountsState.set_error("twitch", error_string(err))
		return
	_request_twitch_count()


func _request_twitch_count() -> void:
	if _twitch_token.is_empty():
		return
	_twitch_phase = "count"
	var client_id := CompanionConfig.viewer_counts_twitch_client_id.strip_edges()
	var channel := CompanionConfig.viewer_counts_twitch_channel.strip_edges()
	var headers := PackedStringArray(
		["Client-ID: %s" % client_id, "Authorization: Bearer %s" % _twitch_token]
	)
	var url := "https://api.twitch.tv/helix/streams?user_login=%s" % channel.uri_encode()
	var err := _twitch_http.request(url, headers)
	if err != OK:
		_twitch_phase = ""
		ViewerCountsState.set_error("twitch", error_string(err))


func _on_twitch_http_done(
	result: int,
	response_code: int,
	_headers: PackedStringArray,
	body: PackedByteArray,
) -> void:
	var phase := _twitch_phase
	_twitch_phase = ""
	if result != HTTPRequest.RESULT_SUCCESS:
		ViewerCountsState.set_error("twitch", "Twitch request failed")
		return
	if response_code == 401 and phase == "count":
		_twitch_token = ""
		_twitch_elapsed = 999.0
		return
	if response_code < 200 or response_code >= 300:
		ViewerCountsState.set_error("twitch", "Twitch HTTP %d" % response_code)
		return
	var data := _parse_json_dict(body)
	if data.is_empty():
		ViewerCountsState.set_error("twitch", "Invalid Twitch response")
		return
	if phase == "token":
		_twitch_token = str(data.get("access_token", ""))
		var expires_sec := maxi(60, int(data.get("expires_in", 3600)) - 60)
		_twitch_token_expires_msec = Time.get_ticks_msec() + expires_sec * 1000
		if _twitch_token.is_empty():
			ViewerCountsState.set_error("twitch", "Twitch token missing")
			return
		_request_twitch_count()
		return
	var streams: Variant = data.get("data", [])
	if typeof(streams) != TYPE_ARRAY or (streams as Array).is_empty():
		ViewerCountsState.set_count("twitch", 0)
		return
	var first: Variant = (streams as Array)[0]
	if typeof(first) == TYPE_DICTIONARY:
		ViewerCountsState.set_count("twitch", int((first as Dictionary).get("viewer_count", 0)))


func _request_youtube_search() -> void:
	if _youtube_http.get_http_client_status() != HTTPClient.STATUS_DISCONNECTED:
		return
	var api_key := CompanionConfig.viewer_counts_youtube_api_key.strip_edges()
	var channel_id := CompanionConfig.viewer_counts_youtube_channel_id.strip_edges()
	if api_key.is_empty() or channel_id.is_empty():
		ViewerCountsState.set_error("youtube", "YouTube credentials are incomplete")
		return
	_youtube_phase = "search"
	_youtube_search_elapsed = 0.0
	var url := (
		"https://www.googleapis.com/youtube/v3/search"
		+ "?part=id&eventType=live&type=video&channelId=%s&key=%s"
		% [channel_id.uri_encode(), api_key.uri_encode()]
	)
	var err := _youtube_http.request(url)
	if err != OK:
		_youtube_phase = ""
		ViewerCountsState.set_error("youtube", error_string(err))


func _request_youtube_details() -> void:
	if _youtube_http.get_http_client_status() != HTTPClient.STATUS_DISCONNECTED:
		return
	_youtube_elapsed = 0.0
	if _youtube_stream_ids.is_empty():
		ViewerCountsState.set_count("youtube", 0)
		return
	_youtube_phase = "details"
	var api_key := CompanionConfig.viewer_counts_youtube_api_key.strip_edges()
	var url := (
		"https://www.googleapis.com/youtube/v3/videos"
		+ "?part=liveStreamingDetails&id=%s&key=%s"
		% [",".join(_youtube_stream_ids).uri_encode(), api_key.uri_encode()]
	)
	var err := _youtube_http.request(url)
	if err != OK:
		_youtube_phase = ""
		ViewerCountsState.set_error("youtube", error_string(err))


func _on_youtube_http_done(
	result: int,
	response_code: int,
	_headers: PackedStringArray,
	body: PackedByteArray,
) -> void:
	var phase := _youtube_phase
	_youtube_phase = ""
	if result != HTTPRequest.RESULT_SUCCESS:
		ViewerCountsState.set_error("youtube", "YouTube request failed")
		return
	if response_code < 200 or response_code >= 300:
		ViewerCountsState.set_error("youtube", "YouTube HTTP %d" % response_code)
		return
	var data := _parse_json_dict(body)
	if data.is_empty():
		ViewerCountsState.set_error("youtube", "Invalid YouTube response")
		return
	var items: Variant = data.get("items", [])
	if typeof(items) != TYPE_ARRAY:
		ViewerCountsState.set_error("youtube", "YouTube items missing")
		return
	if phase == "search":
		_youtube_stream_ids.clear()
		for item in items as Array:
			if typeof(item) != TYPE_DICTIONARY:
				continue
			var item_id: Variant = (item as Dictionary).get("id", {})
			if typeof(item_id) == TYPE_DICTIONARY:
				var video_id := str((item_id as Dictionary).get("videoId", ""))
				if not video_id.is_empty():
					_youtube_stream_ids.append(video_id)
		_request_youtube_details()
		return
	var total := 0
	for item in items as Array:
		if typeof(item) != TYPE_DICTIONARY:
			continue
		var details: Variant = (item as Dictionary).get("liveStreamingDetails", {})
		if typeof(details) == TYPE_DICTIONARY:
			total += int((details as Dictionary).get("concurrentViewers", 0))
	ViewerCountsState.set_count("youtube", total)


func _poll_casterlabs(delta: float) -> void:
	if not CompanionConfig.viewer_counts_panel_visible or not CompanionConfig.viewer_counts_show_tiktok:
		if _caster_peer.get_ready_state() != WebSocketPeer.STATE_CLOSED:
			_caster_peer.close()
		return
	var wanted := _caster_websocket_url(CompanionConfig.viewer_counts_casterlabs_url)
	if wanted.is_empty():
		ViewerCountsState.set_error("tiktok", "Casterlabs URL is missing")
		return
	if wanted != _caster_url:
		_connect_casterlabs(true)

	_caster_peer.poll()
	var state := _caster_peer.get_ready_state()
	if state == WebSocketPeer.STATE_OPEN:
		_caster_was_open = true
		_caster_reconnect_elapsed = 0.0
		while _caster_peer.get_available_packet_count() > 0:
			_handle_caster_packet(_caster_peer.get_packet().get_string_from_utf8())
	elif state == WebSocketPeer.STATE_CLOSED:
		if _caster_was_open:
			ViewerCountsState.set_error("tiktok", "Casterlabs disconnected")
			_caster_was_open = false
		_caster_reconnect_elapsed += delta
		if _caster_reconnect_elapsed >= CompanionConfig.viewer_counts_tiktok_poll_sec:
			_connect_casterlabs(false)


func _connect_casterlabs(force: bool) -> void:
	var wanted := _caster_websocket_url(CompanionConfig.viewer_counts_casterlabs_url)
	if force and _caster_peer.get_ready_state() != WebSocketPeer.STATE_CLOSED:
		_caster_peer.close()
		_caster_peer = WebSocketPeer.new()
	_caster_peer.inbound_buffer_size = 8 * 1024 * 1024
	_caster_peer.max_queued_packets = 64
	_caster_url = wanted
	_caster_reconnect_elapsed = 0.0
	_caster_was_open = false
	if wanted.is_empty():
		return
	var err := _caster_peer.connect_to_url(wanted)
	if err != OK:
		ViewerCountsState.set_error("tiktok", error_string(err))


func _caster_websocket_url(browser_url: String) -> String:
	var query_at := browser_url.find("?")
	if query_at < 0:
		return ""
	var params := _parse_query(browser_url.substr(query_at + 1))
	var plugin_id := str(params.get("pluginId", ""))
	var widget_id := str(params.get("widgetId", ""))
	var authorization := str(params.get("authorization", ""))
	var port := str(params.get("port", "8092"))
	if plugin_id.is_empty() or widget_id.is_empty() or authorization.is_empty():
		return ""
	return (
		"ws://127.0.0.1:%s/api/plugin/%s/widget/%s/realtime?authorization=%s"
		% [port, plugin_id.uri_encode(), widget_id.uri_encode(), authorization.uri_encode()]
	)


func _parse_query(raw: String) -> Dictionary:
	var out := {}
	for pair in raw.split("&"):
		var equals_at := pair.find("=")
		if equals_at < 0:
			continue
		out[pair.left(equals_at).uri_decode()] = pair.substr(equals_at + 1).uri_decode()
	return out


func _handle_caster_packet(text: String) -> void:
	var json := JSON.new()
	if json.parse(text) != OK or typeof(json.data) != TYPE_DICTIONARY:
		return
	var packet: Dictionary = json.data
	var packet_type := str(packet.get("type", "")).to_upper()
	if packet_type == "PING":
		_caster_peer.send_text(JSON.stringify({"type": "PONG", "data": {}}))
		return
	if packet_type == "INIT":
		_caster_peer.send_text(JSON.stringify({"type": "READY", "data": {}}))
		return
	var data: Variant = packet.get("data", {})
	var count := -1
	if packet_type == "KOI_STATICS" and typeof(data) == TYPE_DICTIONARY:
		var statics: Dictionary = data
		count = _extract_tiktok_count(statics.get("viewerCounts", null))
		if count < 0 and _tiktok_explicitly_offline(statics):
			count = 0
	elif packet_type == "KOI":
		count = _extract_tiktok_count(data)
		if count < 0 and _contains_tiktok(data):
			count = _extract_count_field(data)
	if count >= 0:
		ViewerCountsState.set_count("tiktok", count)


func _extract_tiktok_count(value: Variant, in_tiktok: bool = false) -> int:
	match typeof(value):
		TYPE_INT, TYPE_FLOAT:
			return maxi(0, int(value)) if in_tiktok else -1
		TYPE_DICTIONARY:
			var dict: Dictionary = value
			var platform := str(dict.get("platform", "")).to_lower()
			var here := in_tiktok or platform == "tiktok"
			for key in dict.keys():
				var key_text := str(key).to_lower()
				var child_tiktok := here or key_text == "tiktok"
				if child_tiktok and key_text in ["count", "viewers", "viewer_count", "value"]:
					var direct: Variant = dict[key]
					if typeof(direct) in [TYPE_INT, TYPE_FLOAT]:
						return maxi(0, int(direct))
				var found := _extract_tiktok_count(dict[key], child_tiktok)
				if found >= 0:
					return found
		TYPE_ARRAY:
			for child in value as Array:
				var found := _extract_tiktok_count(child, in_tiktok)
				if found >= 0:
					return found
	return -1


func _contains_tiktok(value: Variant) -> bool:
	match typeof(value):
		TYPE_STRING:
			return str(value).to_lower() == "tiktok"
		TYPE_DICTIONARY:
			var dict: Dictionary = value
			for key in dict.keys():
				if str(key).to_lower() == "tiktok" or _contains_tiktok(dict[key]):
					return true
		TYPE_ARRAY:
			for child in value as Array:
				if _contains_tiktok(child):
					return true
	return false


func _extract_count_field(value: Variant) -> int:
	if typeof(value) != TYPE_DICTIONARY:
		return -1
	var dict: Dictionary = value
	for key in ["viewer_count", "viewerCount", "viewers", "count", "value"]:
		var direct: Variant = dict.get(key, null)
		if typeof(direct) in [TYPE_INT, TYPE_FLOAT]:
			return maxi(0, int(direct))
	for child in dict.values():
		var found := _extract_count_field(child)
		if found >= 0:
			return found
	return -1


func _tiktok_explicitly_offline(statics: Dictionary) -> bool:
	var states: Variant = statics.get("streamStates", {})
	if typeof(states) != TYPE_DICTIONARY:
		return false
	var tiktok: Variant = (states as Dictionary).get("TIKTOK", null)
	if typeof(tiktok) != TYPE_DICTIONARY:
		return false
	return not bool((tiktok as Dictionary).get("is_live", true))


func _parse_json_dict(body: PackedByteArray) -> Dictionary:
	var json := JSON.new()
	if json.parse(body.get_string_from_utf8()) != OK or typeof(json.data) != TYPE_DICTIONARY:
		return {}
	return json.data
