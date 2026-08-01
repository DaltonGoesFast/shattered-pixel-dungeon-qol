extends PanelContainer

@onready var _ws: Label = $MarginContainer/VBox/WSRow/Value
@onready var _udp: Label = $MarginContainer/VBox/UDPRow/Value
@onready var _snap: Label = $MarginContainer/VBox/SnapshotRow/Value
@onready var _summon: Label = $MarginContainer/VBox/SummonMarchRow/Value
@onready var _settings_btn: Button = $MarginContainer/VBox/HintRow/SettingsBtn

var _last_snap_keys: int = 0


func _ready() -> void:
	visible = CompanionConfig.hud_status_panel_visible
	GameWebSocketClient.connected_to_game.connect(_on_ws_conn)
	GameWebSocketClient.disconnected_from_game.connect(_on_ws_disc)
	GameWebSocketClient.snapshot_received.connect(_on_snap)
	StreamerBotUdp.command_attempt.connect(_on_udp)
	SummonPollService.connection_restored.connect(_refresh_summon)
	SummonPollService.connection_lost.connect(_refresh_summon)
	SummonPollService.summon_received.connect(_on_summon_received)
	BestiaryPollService.connection_restored.connect(_refresh_summon)
	BestiaryPollService.connection_lost.connect(_refresh_summon)
	CompanionConfig.settings_saved.connect(_on_settings_saved)
	CompanionConfig.settings_loaded.connect(_on_settings_loaded)
	_settings_btn.pressed.connect(_on_settings_btn_pressed)
	_refresh_ws(false)
	_udp.text = "UDP :%d" % CompanionConfig.streamerbot_udp_port
	_refresh_summon()


func _on_ws_conn() -> void:
	_refresh_ws(true)


func _on_ws_disc() -> void:
	_refresh_ws(false)


func _refresh_ws(connected: bool) -> void:
	if connected:
		_ws.text = "WS connected (%s:%d)" % [CompanionConfig.game_ws_host, CompanionConfig.game_ws_port]
	else:
		_ws.text = "WS reconnecting…"


func _refresh_summon() -> void:
	if not CompanionConfig.summon_march_enabled and not CompanionConfig.bestiary_hud_enabled:
		_summon.text = "disabled"
		return
	var parts: PackedStringArray = []
	if CompanionConfig.summon_march_enabled:
		parts.append("march:%s" % ("OK" if SummonPollService.is_server_connected else "off"))
	if CompanionConfig.bestiary_hud_enabled:
		parts.append("bestiary:%s" % ("OK" if BestiaryPollService.is_server_connected else "off"))
	_summon.text = " / ".join(parts)


func _on_summon_received(event: Dictionary) -> void:
	_summon.text = "Last: %s (%s)" % [event.get("monster", "?"), event.get("username", "?")]


func _on_settings_saved() -> void:
	visible = CompanionConfig.hud_status_panel_visible
	_udp.text = "UDP :%d" % CompanionConfig.streamerbot_udp_port
	_refresh_ws(GameWebSocketClient.is_connected_to_game())
	_refresh_summon()


func _on_settings_loaded() -> void:
	visible = CompanionConfig.hud_status_panel_visible


func _on_settings_btn_pressed() -> void:
	var w: Window = get_node_or_null("../../SettingsWindow") as Window
	if w:
		w.visible = not w.visible


func _on_udp(_data: Dictionary) -> void:
	_udp.text = "UDP :%d (packet)" % CompanionConfig.streamerbot_udp_port


func _on_snap(data: Dictionary) -> void:
	_last_snap_keys = data.size()
	_snap.text = "Snapshot keys: %d" % _last_snap_keys
