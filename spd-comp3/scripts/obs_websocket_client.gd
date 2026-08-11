extends Node

## Minimal **obs-websocket 5** client (program scene only). Toggles companion UI when OBS switches scenes.

signal connected_to_obs
signal disconnected_from_obs
## True when the current **program** scene name contains [member CompanionConfig.obs_pause_scene_name] (non-empty).
signal pause_scene_active_changed(is_pause_scene: bool)
## Current OBS program scene bucket: pause / main / other (see CompanionConfig.SCENE_*).
signal program_scene_kind_changed(kind: StringName)

const _OP_HELLO := 0
const _OP_IDENTIFY := 1
const _OP_IDENTIFIED := 2
const _OP_EVENT := 5
const _OP_REQUEST := 6
const _OP_REQUEST_RESPONSE := 7

## [code]EventSubscription::All[/code] (non–high-volume bitmask) so [code]CurrentProgramSceneChanged[/code] is never missed on odd builds.
const _EVENT_SUB_DEFAULT := 4095

const _STATE_OPEN := WebSocketPeer.STATE_OPEN

var _peer: WebSocketPeer
var _was_identified: bool = false
var _reconnect_acc: float = 0.0
var _rpc_version: int = 1
var _req_seq: int = 0
## Dedup by exact scene name, not pause bool — otherwise startup on a non-pause scene never emitted (both were [code]false[/code]).
var _last_scene_applied: String = "\u0001"
var current_scene_kind: StringName = CompanionConfig.SCENE_UNKNOWN
var current_scene_name: String = ""


func _ready() -> void:
	_peer = WebSocketPeer.new()
	CompanionConfig.settings_saved.connect(_on_settings_reloaded)
	CompanionConfig.settings_loaded.connect(_on_settings_reloaded)
	set_physics_process(true)
	call_deferred("_try_connect")


func is_connected_to_obs() -> bool:
	return _peer != null and _peer.get_ready_state() == _STATE_OPEN and _was_identified


func _on_settings_reloaded() -> void:
	var had_ident := _was_identified
	_peer.close()
	_was_identified = false
	_reconnect_acc = 0.0
	if had_ident:
		_last_scene_applied = "\u0001"
		current_scene_kind = CompanionConfig.SCENE_UNKNOWN
		current_scene_name = ""
		disconnected_from_obs.emit()
	call_deferred("_try_connect")


func _physics_process(delta: float) -> void:
	if not CompanionConfig.obs_scene_sync_enabled:
		_peer.poll()
		if _was_identified:
			_peer.close()
			_was_identified = false
			_last_scene_applied = "\u0001"
			disconnected_from_obs.emit()
		return

	_peer.poll()
	var st := _peer.get_ready_state()
	if st == _STATE_OPEN:
		while _peer.get_available_packet_count() > 0:
			var txt := _peer.get_packet().get_string_from_utf8()
			_handle_packet(txt)
		return

	if _was_identified and (
		st == WebSocketPeer.STATE_CLOSED or st == WebSocketPeer.STATE_CLOSING
	):
		_was_identified = false
		_last_scene_applied = "\u0001"
		disconnected_from_obs.emit()

	if st == WebSocketPeer.STATE_CLOSED:
		_reconnect_acc += delta
		if _reconnect_acc >= CompanionConfig.obs_reconnect_sec:
			_reconnect_acc = 0.0
			_try_connect()


func _try_connect() -> void:
	if not CompanionConfig.obs_scene_sync_enabled:
		return
	var st := _peer.get_ready_state()
	if st == WebSocketPeer.STATE_CLOSING:
		return
	if st == _STATE_OPEN or st == WebSocketPeer.STATE_CONNECTING:
		return
	var url := "ws://%s:%d" % [CompanionConfig.obs_ws_host, CompanionConfig.obs_ws_port]
	var err := _peer.connect_to_url(url)
	if err != OK:
		push_warning("ObsWebSocketClient: connect_to_url failed err=%d url=%s" % [err, url])


func _handle_packet(text: String) -> void:
	var parsed: Variant = JSON.parse_string(text)
	if typeof(parsed) != TYPE_DICTIONARY:
		return
	var root: Dictionary = parsed
	var op := int(root.get("op", -1))
	var dvar: Variant = root.get("d")
	if typeof(dvar) != TYPE_DICTIONARY:
		return
	var dd: Dictionary = dvar

	if op == _OP_HELLO:
		_rpc_version = int(dd.get("rpcVersion", 1))
		var ident: Dictionary = {"rpcVersion": _rpc_version, "eventSubscriptions": _EVENT_SUB_DEFAULT}
		var auth: Variant = dd.get("authentication", null)
		if auth != null and typeof(auth) == TYPE_DICTIONARY:
			var ad: Dictionary = auth
			var challenge := str(ad.get("challenge", ""))
			var salt := str(ad.get("salt", ""))
			ident["authentication"] = _obs_auth_response(
				CompanionConfig.obs_ws_password,
				salt,
				challenge
			)
		_send_dict({"op": _OP_IDENTIFY, "d": ident})
	elif op == _OP_IDENTIFIED:
		var first := not _was_identified
		_was_identified = true
		if first:
			connected_to_obs.emit()
		_request_get_current_program_scene()
	elif op == _OP_EVENT:
		if str(dd.get("eventType", "")) != "CurrentProgramSceneChanged":
			return
		var ed: Variant = dd.get("eventData")
		if typeof(ed) != TYPE_DICTIONARY:
			return
		_apply_scene_name(_scene_name_from_response_dict(ed as Dictionary))
	elif op == _OP_REQUEST_RESPONSE:
		if str(dd.get("requestType", "")) != "GetCurrentProgramScene":
			return
		var rs: Variant = dd.get("requestStatus")
		if typeof(rs) != TYPE_DICTIONARY:
			return
		if not bool((rs as Dictionary).get("result", false)):
			return
		var rd: Variant = dd.get("responseData")
		if typeof(rd) != TYPE_DICTIONARY:
			return
		_apply_scene_name(_scene_name_from_response_dict(rd as Dictionary))


func _send_dict(obj: Dictionary) -> void:
	## obs-websocket JSON mode expects **text** frames; [method PacketPeer.put_packet] sends binary (OBS closes with 4002).
	var err := _peer.send_text(JSON.stringify(obj))
	if err != OK:
		push_warning("ObsWebSocketClient: send_text failed err=%d" % err)


func _request_get_current_program_scene() -> void:
	_req_seq += 1
	_send_dict(
		{
			"op": _OP_REQUEST,
			"d":
			{
				"requestType": "GetCurrentProgramScene",
				"requestId": "spdc-obs-%d" % _req_seq,
			}
		}
	)


func _apply_scene_name(scene_name: String) -> void:
	if not CompanionConfig.obs_scene_sync_enabled:
		return
	var sn := scene_name.strip_edges()
	if sn == _last_scene_applied:
		return
	_last_scene_applied = sn
	current_scene_name = sn
	var kind := CompanionConfig.classify_obs_scene(sn)
	current_scene_kind = kind
	var want_pause: bool = kind == CompanionConfig.SCENE_PAUSE
	if CompanionConfig.obs_log_program_scene:
		print(
			"ObsWebSocket: program scene %s → kind=%s pause=%s (pause marker %s, main marker %s)"
			% [
				sn,
				String(kind),
				want_pause,
				CompanionConfig.obs_pause_scene_name,
				CompanionConfig.obs_main_scene_name,
			]
		)
	pause_scene_active_changed.emit(want_pause)
	program_scene_kind_changed.emit(kind)


func _scene_name_from_response_dict(d: Dictionary) -> String:
	var s := str(d.get("sceneName", ""))
	if s.is_empty():
		s = str(d.get("currentProgramSceneName", ""))
	return s


## Call after UI wires [signal pause_scene_active_changed] so the first [method GetCurrentProgramScene] response is not missed.
func refresh_program_scene() -> void:
	if not CompanionConfig.obs_scene_sync_enabled or not _was_identified:
		return
	if _peer.get_ready_state() != _STATE_OPEN:
		return
	_last_scene_applied = "\u0001"
	_request_get_current_program_scene()


## obs-websocket v5 authentication ([code]base64(sha256(base64(sha256(password+salt)) + challenge))[/code]).
func _obs_auth_response(password: String, salt: String, challenge: String) -> String:
	var secret_b64 := Marshalls.raw_to_base64(_sha256_bytes((password + salt).to_utf8_buffer()))
	return Marshalls.raw_to_base64(_sha256_bytes((secret_b64 + challenge).to_utf8_buffer()))


func _sha256_bytes(data: PackedByteArray) -> PackedByteArray:
	var ctx := HashingContext.new()
	ctx.start(HashingContext.HASH_SHA256)
	ctx.update(data)
	return ctx.finish()
