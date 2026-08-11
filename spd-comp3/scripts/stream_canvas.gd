extends Control
## Shared stream overlay canvas (instanced under Main and VerticalCompanionWindow).

@export var layout_profile: StringName = &"ui"

## Saturated pink for chroma key (live / non-pause layout only); toggle with F9 on main.
const _LIVE_FILL_CHROMA_DEBUG := Color(1.0, 8.0 / 255.0, 147.0 / 255.0, 1.0)

@onready var _live_background: Control = $LiveBackground
@onready var _chroma_overlay: ColorRect = $LiveBackground/ChromaOverlay
@onready var _title_backdrop: Control = $TitleBackdrop
@onready var _chrome_layer: CanvasLayer = $CanvasLayerChromeBoxes
@onready var _id_layer: CanvasLayer = $CanvasLayerID
@onready var _summon_march_layer: CanvasLayer = $CanvasLayerSummonMarch
@onready var _bestiary_layer: CanvasLayer = $CanvasLayerBestiary
@onready var _spend_layer: CanvasLayer = $CanvasLayerSpendIndicator
@onready var _free_layer: CanvasLayer = $CanvasLayerFreePromos
@onready var _double_points_layer: CanvasLayer = $CanvasLayerDoublePoints
@onready var _paid_layer: CanvasLayer = $CanvasLayerPaidNotices
@onready var _alerts_layer: CanvasLayer = $CanvasLayerAlerts
@onready var _id_overlay: Control = $CanvasLayerID/IdentificationOverlay

var _obs: Node
var _debug_chroma_layout: bool = false
var _scene_kind: StringName = CompanionConfig.SCENE_UNKNOWN
var _obs_scene_known: bool = false


func get_layout_profile() -> StringName:
	return layout_profile


func is_vertical_profile() -> bool:
	return layout_profile == CompanionConfig.LAYOUT_VERTICAL


func _ready() -> void:
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	CompanionConfig.settings_loaded.connect(_on_settings_changed)
	CompanionConfig.settings_saved.connect(_on_settings_changed)
	_obs = get_node_or_null("/root/ObsWebSocketClient")
	if _obs:
		if _obs.has_signal("program_scene_kind_changed"):
			_obs.program_scene_kind_changed.connect(_on_obs_scene_kind)
		else:
			_obs.pause_scene_active_changed.connect(_on_obs_pause_scene_active)
		_obs.disconnected_from_obs.connect(_on_obs_disconnected)
		_obs.call_deferred("refresh_program_scene")
	call_deferred("_apply_obs_visibility")


func _on_settings_changed() -> void:
	_apply_obs_visibility()


func set_debug_chroma(on: bool) -> void:
	_debug_chroma_layout = on
	_sync_live_chroma_overlay()


func toggle_debug_chroma() -> void:
	_debug_chroma_layout = not _debug_chroma_layout
	_sync_live_chroma_overlay()


func _sync_live_chroma_overlay() -> void:
	if _chroma_overlay == null or _live_background == null:
		return
	if _debug_chroma_layout and _live_background.visible:
		_chroma_overlay.visible = true
		var sm := _chroma_overlay.material as ShaderMaterial
		if sm:
			sm.set_shader_parameter("chroma_color", _LIVE_FILL_CHROMA_DEBUG)
		else:
			_chroma_overlay.color = _LIVE_FILL_CHROMA_DEBUG
	else:
		_chroma_overlay.visible = false


func _on_obs_scene_kind(kind: StringName) -> void:
	_obs_scene_known = true
	_scene_kind = kind
	_apply_obs_visibility()


func _on_obs_pause_scene_active(is_pause: bool) -> void:
	_obs_scene_known = true
	_scene_kind = CompanionConfig.SCENE_PAUSE if is_pause else CompanionConfig.SCENE_MAIN
	_apply_obs_visibility()


func _on_obs_disconnected() -> void:
	_obs_scene_known = false
	_scene_kind = CompanionConfig.SCENE_UNKNOWN
	_apply_obs_visibility()


func _apply_obs_visibility() -> void:
	var kind := _scene_kind if _obs_scene_known else CompanionConfig.SCENE_UNKNOWN
	# Disconnected: treat like pause for title/live water defaults (historical).
	if not _obs_scene_known:
		kind = CompanionConfig.SCENE_PAUSE

	var show_title := CompanionConfig.element_visible_on_scene(self, "title", kind)
	var show_live := CompanionConfig.element_visible_on_scene(self, "live_water", kind)
	var show_id := CompanionConfig.element_visible_on_scene(self, "id_overlay", kind)
	var show_bestiary := CompanionConfig.element_visible_on_scene(self, "bestiary", kind)
	var show_march := CompanionConfig.element_visible_on_scene(self, "summon_march", kind)
	var show_chrome := CompanionConfig.element_visible_on_scene(self, "chrome_boxes", kind)
	var show_alerts := CompanionConfig.element_visible_on_scene(self, "alerts", kind)
	var show_paid := CompanionConfig.element_visible_on_scene(self, "paid_notices", kind)
	var show_spend := CompanionConfig.element_visible_on_scene(self, "spend_indicator", kind)
	var show_free := CompanionConfig.element_visible_on_scene(self, "free_promos", kind)
	var show_double := CompanionConfig.element_visible_on_scene(self, "double_points", kind)

	if not _obs_scene_known:
		# Match prior disconnect: title on (if enabled), live water off, overlays follow gates with unknown→any.
		show_live = false
		show_id = CompanionConfig.element_enabled(self, "id_overlay")
		show_bestiary = CompanionConfig.element_enabled(self, "bestiary")
		show_march = CompanionConfig.element_enabled(self, "summon_march")
		show_chrome = CompanionConfig.element_enabled(self, "chrome_boxes")
		show_alerts = CompanionConfig.element_enabled(self, "alerts")
		show_paid = CompanionConfig.element_enabled(self, "paid_notices")
		show_spend = CompanionConfig.element_enabled(self, "spend_indicator")
		show_free = CompanionConfig.element_enabled(self, "free_promos")
		show_double = CompanionConfig.element_enabled(self, "double_points")
		show_title = CompanionConfig.element_enabled(self, "title")

	_title_backdrop.visible = show_title
	_live_background.visible = show_live
	_id_layer.visible = show_id
	_bestiary_layer.visible = show_bestiary
	_summon_march_layer.visible = show_march
	_chrome_layer.visible = show_chrome
	_alerts_layer.visible = show_alerts
	_paid_layer.visible = show_paid
	_spend_layer.visible = show_spend
	_free_layer.visible = show_free
	_double_points_layer.visible = show_double
	_sync_live_chroma_overlay()
	if show_id and _id_overlay and _id_overlay.has_method("sync_visibility_after_obs"):
		_id_overlay.sync_visibility_after_obs()
