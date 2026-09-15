extends SubViewportContainer
## Portrait pane on the single atlas window (1080×1920 at x=1920). Not a native Window.
## StreamCanvas lives in a child SubViewport so overlays still layout against 1080×1920.

const VERT_SIZE := Vector2i(1080, 1920)
const VERT_POS := Vector2i(1920, 0)

@onready var _canvas: Control = $SubViewport/StreamCanvas
@onready var _sv: SubViewport = $SubViewport


func _ready() -> void:
	position = Vector2(VERT_POS)
	size = Vector2(VERT_SIZE)
	stretch = true
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	if _canvas:
		_canvas.layout_profile = CompanionConfig.LAYOUT_VERTICAL
		_canvas.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	CompanionConfig.settings_loaded.connect(_sync_from_settings)
	CompanionConfig.settings_saved.connect(_sync_from_settings)
	call_deferred("_sync_from_settings")


func toggle_visible() -> void:
	if visible:
		hide()
	else:
		show_vertical()


func show_vertical() -> void:
	if not CompanionConfig.vertical_window_enabled:
		hide()
		return
	_apply_pane_metrics()
	show()
	call_deferred("_ensure_canvas_fill")


func _sync_from_settings() -> void:
	if CompanionConfig.vertical_window_enabled:
		if not visible:
			show_vertical()
		else:
			_apply_pane_metrics()
			call_deferred("_ensure_canvas_fill")
	else:
		hide()


func _apply_pane_metrics() -> void:
	position = Vector2(VERT_POS)
	size = Vector2(VERT_SIZE)
	stretch = true
	if _sv:
		_sv.size = VERT_SIZE
		_sv.transparent_bg = CompanionConfig.window_per_pixel_transparency_enabled
		_sv.canvas_item_default_texture_filter = Viewport.DEFAULT_CANVAS_ITEM_TEXTURE_FILTER_NEAREST
		_sv.render_target_update_mode = SubViewport.UPDATE_ALWAYS


func _ensure_canvas_fill() -> void:
	_apply_pane_metrics()
	if _canvas == null:
		return
	_canvas.layout_profile = CompanionConfig.LAYOUT_VERTICAL
	_canvas.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	_canvas.size = Vector2(VERT_SIZE)
	if _sv:
		_sv.size_changed.emit()
