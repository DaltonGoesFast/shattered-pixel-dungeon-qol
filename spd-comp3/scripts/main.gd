extends Control

## Root scene: 1080p layout for OBS window capture.

## Saturated pink for chroma key (live / non-pause layout only); toggle with F9 (not F8 — editor Stop).
const _LIVE_FILL_CHROMA_DEBUG := Color(1.0, 8.0 / 255.0, 147.0 / 255.0, 1.0)

@onready var _settings: Window = $SettingsWindow
@onready var _live_background: Control = $LiveBackground
@onready var _chroma_overlay: ColorRect = $LiveBackground/ChromaOverlay
@onready var _hud_status: Control = $CanvasLayerHUD/HUDStatus
@onready var _title_backdrop: Control = $TitleBackdrop
@onready var _id_layer: CanvasLayer = $CanvasLayerID
@onready var _summon_march_layer: CanvasLayer = $CanvasLayerSummonMarch
@onready var _bestiary_layer: CanvasLayer = $CanvasLayerBestiary
@onready var _id_overlay: Control = $CanvasLayerID/IdentificationOverlay
var _obs: Node
var _debug_chroma_layout: bool = false


func _apply_window_transparency() -> void:
	var vp := get_viewport()
	var on := CompanionConfig.window_per_pixel_transparency_enabled
	vp.transparent_bg = on
	DisplayServer.window_set_flag(DisplayServer.WINDOW_FLAG_TRANSPARENT, on, vp.get_window_id())


func _ready() -> void:
	# Viewport uses DefaultCanvasItemTextureFilter: 0 = nearest, 1 = linear (not CanvasItem.TextureFilter).
	get_viewport().canvas_item_default_texture_filter = Viewport.DEFAULT_CANVAS_ITEM_TEXTURE_FILTER_NEAREST
	CompanionConfig.settings_loaded.connect(_apply_window_transparency)
	CompanionConfig.settings_saved.connect(_apply_window_transparency)
	CompanionConfig.load_settings()
	StreamerBotUdp.rebind()
	_settings.hide()
	call_deferred("_apply_window_transparency")

	_obs = get_node_or_null("/root/ObsWebSocketClient")
	if _obs:
		_obs.pause_scene_active_changed.connect(_on_obs_pause_scene_active)
		_obs.disconnected_from_obs.connect(_on_obs_disconnected)
		# OBS may answer GetCurrentProgramScene before this node connects signals; refresh once after wiring.
		_obs.call_deferred("refresh_program_scene")


func _sync_live_chroma_overlay() -> void:
	if _debug_chroma_layout and _live_background.visible:
		_chroma_overlay.visible = true
		var sm := _chroma_overlay.material as ShaderMaterial
		if sm:
			sm.set_shader_parameter("chroma_color", _LIVE_FILL_CHROMA_DEBUG)
		else:
			_chroma_overlay.color = _LIVE_FILL_CHROMA_DEBUG
	else:
		_chroma_overlay.visible = false


func _on_obs_pause_scene_active(is_pause: bool) -> void:
	_title_backdrop.visible = is_pause
	_id_layer.visible = not is_pause
	_live_background.visible = not is_pause
	_summon_march_layer.visible = true
	_bestiary_layer.visible = not is_pause
	_sync_live_chroma_overlay()
	if not is_pause:
		_id_overlay.sync_visibility_after_obs()


func _on_obs_disconnected() -> void:
	_title_backdrop.visible = true
	_id_layer.visible = true
	_live_background.visible = false
	_summon_march_layer.visible = true
	_bestiary_layer.visible = true
	_sync_live_chroma_overlay()
	_id_overlay.sync_visibility_after_obs()


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
		elif event.keycode == KEY_F9:
			_debug_chroma_layout = not _debug_chroma_layout
			_sync_live_chroma_overlay()
			get_viewport().set_input_as_handled()
