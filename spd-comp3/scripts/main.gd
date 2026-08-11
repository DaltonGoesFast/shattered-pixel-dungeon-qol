extends Control

## Root scene: 1080p layout for OBS window capture + optional vertical companion window.

@onready var _settings: Window = $SettingsWindow
@onready var _hud_status: Control = $CanvasLayerHUD/HUDStatus
@onready var _stream: Control = $StreamCanvas
@onready var _vertical: Window = $VerticalCompanionWindow


func _apply_window_transparency() -> void:
	var vp := get_viewport()
	var on := CompanionConfig.window_per_pixel_transparency_enabled
	vp.transparent_bg = on
	DisplayServer.window_set_flag(DisplayServer.WINDOW_FLAG_TRANSPARENT, on, vp.get_window_id())


func _ready() -> void:
	get_viewport().canvas_item_default_texture_filter = Viewport.DEFAULT_CANVAS_ITEM_TEXTURE_FILTER_NEAREST
	CompanionConfig.settings_loaded.connect(_apply_window_transparency)
	CompanionConfig.settings_saved.connect(_apply_window_transparency)
	CompanionConfig.load_settings()
	StreamerBotUdp.rebind()
	_settings.hide()
	call_deferred("_apply_window_transparency")
	if _stream and "layout_profile" in _stream:
		_stream.layout_profile = CompanionConfig.LAYOUT_MAIN


func _unhandled_key_input(event: InputEvent) -> void:
	if event is InputEventKey and event.pressed and not event.echo:
		if event.keycode == KEY_F2:
			_settings.visible = not _settings.visible
			get_viewport().set_input_as_handled()
		elif event.keycode == KEY_F3:
			_hud_status.visible = not _hud_status.visible
			CompanionConfig.hud_status_panel_visible = _hud_status.visible
			CompanionConfig.save_settings_quiet()
			get_viewport().set_input_as_handled()
		elif event.keycode == KEY_F4:
			if _vertical and _vertical.has_method("toggle_visible"):
				_vertical.toggle_visible()
			get_viewport().set_input_as_handled()
		elif event.keycode == KEY_F9:
			if _stream and _stream.has_method("toggle_debug_chroma"):
				_stream.toggle_debug_chroma()
			get_viewport().set_input_as_handled()
