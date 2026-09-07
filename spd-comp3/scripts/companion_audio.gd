extends Node
## Companion SFX: output device routing + shatter event playback.

const SHATTER_STREAM_PATH := "res://assets/sounds/ShatterEvent1.mp3"
const DEFAULT_DEVICE := "Default"

var _player: AudioStreamPlayer
var _shatter_stream: AudioStream


func _ready() -> void:
	_player = AudioStreamPlayer.new()
	_player.name = "CompanionSfxPlayer"
	add_child(_player)
	if ResourceLoader.exists(SHATTER_STREAM_PATH):
		_shatter_stream = load(SHATTER_STREAM_PATH) as AudioStream
		_player.stream = _shatter_stream
	else:
		push_warning("[CompanionAudio] Missing shatter SFX at %s" % SHATTER_STREAM_PATH)
	CompanionConfig.settings_saved.connect(_on_settings_changed)
	CompanionConfig.settings_loaded.connect(_on_settings_changed)
	BestiaryPollService.shatter_event.connect(_on_shatter_event)
	_apply_from_config()


func _on_settings_changed() -> void:
	_apply_from_config()


func _on_shatter_event(_payload: Dictionary) -> void:
	play_shatter()


func _apply_from_config() -> void:
	apply_output_device(CompanionConfig.audio_output_device)
	apply_volume(CompanionConfig.audio_volume, CompanionConfig.audio_mute)


func apply_output_device(device_name: String) -> void:
	var wanted := device_name.strip_edges()
	if wanted.is_empty():
		wanted = DEFAULT_DEVICE
	var devices := AudioServer.get_output_device_list()
	var resolved := DEFAULT_DEVICE
	if wanted == DEFAULT_DEVICE or wanted.to_lower() == "default":
		resolved = DEFAULT_DEVICE
	elif devices.has(wanted):
		resolved = wanted
	else:
		# Keep the stored name in config; fall back so audio still plays.
		resolved = DEFAULT_DEVICE
	AudioServer.output_device = resolved


func apply_volume(linear: float, muted: bool) -> void:
	var vol := clampf(linear, 0.0, 1.5)
	if muted or vol <= 0.0001:
		AudioServer.set_bus_mute(0, true)
		AudioServer.set_bus_volume_db(0, linear_to_db(0.0001))
	else:
		AudioServer.set_bus_mute(0, false)
		AudioServer.set_bus_volume_db(0, linear_to_db(vol))


func play_shatter(force: bool = false) -> void:
	if not force:
		if CompanionConfig.audio_mute:
			return
		if not CompanionConfig.shatter_sfx_enabled:
			return
	if _player == null or _shatter_stream == null:
		return
	_player.stream = _shatter_stream
	_player.play()


func list_output_devices() -> PackedStringArray:
	var out := PackedStringArray()
	out.append(DEFAULT_DEVICE)
	for name in AudioServer.get_output_device_list():
		var s := str(name)
		if s.is_empty() or s == DEFAULT_DEVICE:
			continue
		if not out.has(s):
			out.append(s)
	return out
