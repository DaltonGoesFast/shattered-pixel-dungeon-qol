extends Window
## Second OBS capture surface (1080×1920) with vertical layout profile.
## Must be a **native** OS window (project: embed_subwindows=false) so it is not drawn over the main 1080p canvas.
## Also overrides content_scale_* so this window does **not** inherit the project's 1920×1080 stretch base
## (that mismatch was causing overflow / sliced title art when the window was dragged).

const VERT_SIZE := Vector2i(1080, 1920)

@onready var _canvas: Control = $StreamCanvas


func _ready() -> void:
	title = "SPD Companion — Vertical"
	_apply_window_metrics()
	# Keep as a separate top-level window (not a popup glued to Main).
	transient = false
	exclusive = false
	always_on_top = false
	if "force_native" in self:
		set("force_native", true)
	close_requested.connect(_on_close_requested)
	if _canvas:
		_canvas.layout_profile = CompanionConfig.LAYOUT_VERTICAL
		_canvas.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	CompanionConfig.settings_loaded.connect(_sync_from_settings)
	CompanionConfig.settings_saved.connect(_sync_from_settings)
	call_deferred("_place_beside_main")
	call_deferred("_apply_transparency")
	call_deferred("_sync_from_settings")


func _apply_window_metrics() -> void:
	# Physical window size
	size = VERT_SIZE
	min_size = VERT_SIZE
	max_size = VERT_SIZE
	unresizable = true
	# Virtual content size must match portrait — default inherits project 1920×1080.
	content_scale_size = VERT_SIZE
	content_scale_mode = Window.CONTENT_SCALE_MODE_CANVAS_ITEMS
	content_scale_aspect = Window.CONTENT_SCALE_ASPECT_KEEP
	content_scale_factor = 1.0


func _place_beside_main() -> void:
	## Open to the right of the main companion so it does not sit on top of the 1080p capture.
	var main_id := DisplayServer.MAIN_WINDOW_ID
	var main_pos := DisplayServer.window_get_position(main_id)
	var main_size := DisplayServer.window_get_size(main_id)
	position = Vector2i(main_pos.x + main_size.x + 24, main_pos.y)


func _on_close_requested() -> void:
	hide()


func toggle_visible() -> void:
	if visible:
		hide()
	else:
		show_vertical()


func show_vertical() -> void:
	if not CompanionConfig.vertical_window_enabled:
		hide()
		return
	_apply_window_metrics()
	show()
	call_deferred("_apply_transparency")
	call_deferred("_ensure_canvas_fill")


func _sync_from_settings() -> void:
	if CompanionConfig.vertical_window_enabled:
		if not visible:
			show_vertical()
		else:
			_apply_window_metrics()
			call_deferred("_apply_transparency")
			call_deferred("_ensure_canvas_fill")
	else:
		hide()


func _ensure_canvas_fill() -> void:
	if _canvas == null:
		return
	_canvas.layout_profile = CompanionConfig.LAYOUT_VERTICAL
	_canvas.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	_canvas.size = Vector2(VERT_SIZE)
	# Force zone / water layouts against this window's viewport size.
	get_viewport().size_changed.emit()


func _apply_transparency() -> void:
	var vp := get_viewport()
	var on := CompanionConfig.window_per_pixel_transparency_enabled
	vp.transparent_bg = on
	# Window.transparent helps some platforms honor per-pixel alpha for this OS window.
	transparent = on
	DisplayServer.window_set_flag(DisplayServer.WINDOW_FLAG_TRANSPARENT, on, get_window_id())
	vp.canvas_item_default_texture_filter = Viewport.DEFAULT_CANVAS_ITEM_TEXTURE_FILTER_NEAREST
