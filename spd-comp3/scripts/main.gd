extends Control

## One OS window / swapchain atlas for a single OBS Game Capture:
##   1920×1080 pane at (0, 0) + 1080×1920 pane at (1920, 0) = 3000×1920.
## OBS Crop/Pad on a 3000×1920 capture:
##   Horizontal: left 0, top 0, right 1080, bottom 840  (rect 0,0 1920×1080)
##   Vertical:   left 1920, top 0, right 0, bottom 0    (rect 1920,0 1080×1920)
## F3 HUD lives on this root viewport at (16,16) so it stays in the 1080p crop only.

const ATLAS_SIZE := Vector2i(3000, 1920)
const HORIZ_SIZE := Vector2i(1920, 1080)
const WINDOW_TITLE := "SPD Companion 3"

@onready var _settings: Window = $SettingsWindow
@onready var _hud_status: Control = $CanvasLayerHUD/HUDStatus
@onready var _stream: Control = $HorizontalPane/SubViewport/StreamCanvas
@onready var _horiz_sv: SubViewport = $HorizontalPane/SubViewport
@onready var _vertical: Control = $VerticalCompanionWindow


func _apply_window_transparency() -> void:
	var vp := get_viewport()
	var on := CompanionConfig.window_per_pixel_transparency_enabled
	vp.transparent_bg = on
	# Same path as the old vertical Window: Window.transparent is what Windows honors for per-pixel alpha.
	var win := get_window()
	if win:
		win.transparent = on
	DisplayServer.window_set_flag(DisplayServer.WINDOW_FLAG_TRANSPARENT, on, vp.get_window_id())
	if _horiz_sv:
		_horiz_sv.transparent_bg = on


func _apply_atlas_window() -> void:
	var win := get_window()
	if win:
		win.title = WINDOW_TITLE
		win.size = ATLAS_SIZE
		win.content_scale_size = ATLAS_SIZE
		win.content_scale_mode = Window.CONTENT_SCALE_MODE_CANVAS_ITEMS
		win.content_scale_aspect = Window.CONTENT_SCALE_ASPECT_KEEP
		win.content_scale_factor = 1.0
		win.unresizable = true
	if _horiz_sv:
		_horiz_sv.size = HORIZ_SIZE
		_horiz_sv.transparent_bg = CompanionConfig.window_per_pixel_transparency_enabled
		_horiz_sv.canvas_item_default_texture_filter = Viewport.DEFAULT_CANVAS_ITEM_TEXTURE_FILTER_NEAREST
		_horiz_sv.render_target_update_mode = SubViewport.UPDATE_ALWAYS


func _ready() -> void:
	get_viewport().canvas_item_default_texture_filter = Viewport.DEFAULT_CANVAS_ITEM_TEXTURE_FILTER_NEAREST
	CompanionConfig.settings_loaded.connect(_apply_window_transparency)
	CompanionConfig.settings_saved.connect(_apply_window_transparency)
	CompanionConfig.load_settings()
	StreamerBotUdp.rebind()
	_settings.hide()
	var win := get_window()
	if win:
		win.title = WINDOW_TITLE
	call_deferred("_apply_atlas_window")
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
