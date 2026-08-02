extends Node

## Listens for UTF-8 JSON datagrams from Streamer.bot (Send UDP).

signal command_attempt(data: Dictionary)
## Overlay / HUD events (superchat, sub, highlight, …). Payload must include a string [code]ui[/code] field.
signal ui_event(data: Dictionary)

var _udp: PacketPeerUDP
var _bound_port: int = -1


func _ready() -> void:
	_udp = PacketPeerUDP.new()
	CompanionConfig.settings_saved.connect(rebind)
	rebind()


func rebind() -> void:
	_bind_udp()


func _physics_process(_delta: float) -> void:
	if _udp == null:
		return
	while _udp.get_available_packet_count() > 0:
		var raw := _udp.get_packet()
		var text := raw.get_string_from_utf8()
		if text.is_empty():
			continue
		var json := JSON.new()
		if json.parse(text) != OK:
			push_warning("StreamerBotUdp: invalid JSON datagram: %s" % text.substr(0, 120))
			continue
		var data = json.data
		if data is Dictionary:
			var d: Dictionary = data
			if _is_ui_event(d):
				print("StreamerBotUdp: ui_event ui=%s" % str(d.get("ui", "")))
				ui_event.emit(d)
			else:
				command_attempt.emit(d)


func _is_ui_event(data: Dictionary) -> bool:
	var ui := str(data.get("ui", "")).strip_edges()
	return not ui.is_empty()


func _bind_udp() -> void:
	if _udp == null:
		return
	if _bound_port >= 0:
		_udp.close()
	var port: int = CompanionConfig.streamerbot_udp_port
	# Bind all interfaces ("*") so Streamer.bot **UDP Broadcast** reaches us (127.0.0.1-only
	# sockets do not receive broadcast datagrams on Windows).
	var err := _udp.bind(port, "*")
	if err != OK:
		push_warning(
			"StreamerBotUdp: bind failed on *:%d err=%d — check port in use" % [port, err]
		)
		_bound_port = -1
		return
	_bound_port = port
	_udp.set_broadcast_enabled(true)
