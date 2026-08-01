extends Node

## Listens for UTF-8 JSON datagrams from Streamer.bot (Send UDP).

signal command_attempt(data: Dictionary)

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
			push_warning("StreamerBotUdp: invalid JSON datagram")
			continue
		var data = json.data
		if data is Dictionary:
			command_attempt.emit(data)


func _bind_udp() -> void:
	if _udp == null:
		return
	if _bound_port >= 0:
		_udp.close()
	var port: int = CompanionConfig.streamerbot_udp_port
	var err := _udp.bind(port, "127.0.0.1")
	if err != OK:
		push_warning(
			"StreamerBotUdp: bind failed on 127.0.0.1:%d err=%d — check port in use" % [port, err]
		)
		_bound_port = -1
		return
	_bound_port = port
	_udp.set_broadcast_enabled(false)
