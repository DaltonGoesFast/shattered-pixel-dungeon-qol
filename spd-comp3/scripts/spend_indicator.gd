extends HBoxContainer

## Mirrors WndChallenges rows: RedButton chrome (Chrome.Type.RED_BUTTON) + CheckBox Icons.CHECKED / UNCHECKED on the right.
## Locked when [code]spend_disabled.txt[/code] exists (spending off).

const _SpdUi := preload("res://scripts/spd_ui_art.gd")
const _TEX_ON := preload("res://assets/ui_spd/icons/icons_checked.png")
const _TEX_OFF := preload("res://assets/ui_spd/icons/icons_unchecked.png")

@onready var _red_strip: PanelContainer = $RedStrip
@onready var _row_margin: MarginContainer = $RedStrip/RowMargin
@onready var _status_label: Label = $RedStrip/RowMargin/HBox/StatusLabel
@onready var _toggle: TextureRect = $RedStrip/RowMargin/HBox/ToggleTex

var _accum: float = 0.0
## -1 = not yet polled; 0 = spending disabled (lock file present); 1 = spending allowed (no file).
var _last_code: int = -1


func _ready() -> void:
	_toggle.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	CompanionConfig.settings_saved.connect(_on_cfg)
	CompanionConfig.settings_loaded.connect(_on_cfg)
	get_viewport().size_changed.connect(_schedule_reposition)
	_apply_anchor_pins()
	_apply_chrome()
	_apply_padding()
	_apply_label_scale()
	_apply_visibility()
	_poll_now(true)


func _on_cfg() -> void:
	_accum = 0.0
	_apply_anchor_pins()
	_apply_chrome()
	_apply_padding()
	_apply_label_scale()
	_apply_visibility()
	_poll_now(true)


func _apply_chrome() -> void:
	if _red_strip == null:
		return
	_red_strip.add_theme_stylebox_override(
		"panel",
		_SpdUi.chrome_style(
			CompanionConfig.spend_indicator_chrome_style,
			CompanionConfig.spend_indicator_chrome_scale
		)
	)


func _apply_padding() -> void:
	if _row_margin == null:
		return
	var pad_h := clampi(CompanionConfig.spend_indicator_padding_h_px, 0, 64)
	var pad_v := clampi(CompanionConfig.spend_indicator_padding_v_px, 0, 64)
	_row_margin.add_theme_constant_override("margin_left", pad_h)
	_row_margin.add_theme_constant_override("margin_right", pad_h)
	_row_margin.add_theme_constant_override("margin_top", pad_v)
	_row_margin.add_theme_constant_override("margin_bottom", pad_v)


func _apply_label_scale() -> void:
	var fs: int = clampi(CompanionConfig.spend_indicator_font_size_px, 8, 64)
	_status_label.add_theme_font_size_override("font_size", fs)
	## Keep checkbox icon similar proportions to vanilla (~14px at 15px label).
	var icon_side: int = maxi(10, int(round(float(fs) * (14.0 / 15.0))))
	_toggle.custom_minimum_size = Vector2(icon_side, icon_side)
	_red_strip.custom_minimum_size.y = maxf(28.0, float(fs) + 14.0)


func _apply_anchor_pins() -> void:
	anchor_left = 0.0
	anchor_top = 0.0
	anchor_right = 0.0
	anchor_bottom = 0.0
	offset_left = 0.0
	offset_top = 0.0
	offset_right = 0.0
	offset_bottom = 0.0


func _physics_process(delta: float) -> void:
	if not CompanionConfig.element_enabled(self, "spend_indicator"):
		return
	if CompanionConfig.spend_lock_file_path.strip_edges().is_empty():
		return
	var step: float = maxf(0.05, CompanionConfig.spend_lock_poll_sec)
	_accum += delta
	if _accum < step:
		return
	_accum = 0.0
	_poll_now(false)


func _apply_visibility() -> void:
	var path_ok: bool = not CompanionConfig.spend_lock_file_path.strip_edges().is_empty()
	var want := CompanionConfig.element_enabled(self, "spend_indicator") and path_ok
	if want and CompanionConfig.is_vertical_layout(self):
		var L := CompanionConfig.layout_data_for(self)
		if L.hide_spend_when_off:
			var path: String = CompanionConfig.spend_lock_file_path.strip_edges()
			if FileAccess.file_exists(path):
				want = false
	visible = want
	if not visible:
		_last_code = -1
		return
	_schedule_reposition()


func _schedule_reposition() -> void:
	call_deferred("_reposition_to_corner")


func _reposition_to_corner() -> void:
	if not visible:
		return
	var r: Rect2 = get_viewport().get_visible_rect()
	var L := CompanionConfig.layout_data_for(self)
	var mx: int = L.spend_indicator_margin_x
	var my: int = L.spend_indicator_margin_y
	var corner: int = clampi(L.spend_indicator_corner, 0, 3)
	var sz: Vector2 = get_rect().size
	if sz.x < 2.0 or sz.y < 2.0:
		return
	var x: float = 0.0
	var y: float = 0.0
	match corner:
		0:
			x = float(mx)
			y = float(my)
		1:
			x = r.size.x - float(mx) - sz.x
			y = float(my)
		2:
			x = float(mx)
			y = r.size.y - float(my) - sz.y
		_:
			x = r.size.x - float(mx) - sz.x
			y = r.size.y - float(my) - sz.y
	position = Vector2(x, y)


func _poll_now(force: bool) -> void:
	if not CompanionConfig.element_enabled(self, "spend_indicator"):
		return
	var path: String = CompanionConfig.spend_lock_file_path.strip_edges()
	if path.is_empty():
		return
	var locked: bool = FileAccess.file_exists(path)
	var code: int = 0 if locked else 1
	if not force and code == _last_code:
		return
	_last_code = code
	_toggle.texture = _TEX_OFF if locked else _TEX_ON
	_toggle.modulate = Color.WHITE
	# Vertical may hide entirely while locked; re-evaluate after each poll.
	_apply_visibility()
	if visible:
		_schedule_reposition()
