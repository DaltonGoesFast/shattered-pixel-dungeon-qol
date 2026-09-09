extends Control
## Starting-soon banner: soon.png, additive soonglow pulse, and title fireball flames.

const FIREBALL_FPS := 20.0
const ASSET_SOON := "res://assets/soon.png"
const ASSET_GLOW := "res://assets/soonglow.png"
const FIREBALL := "res://assets/title_screen/fireball_tall/frame_%02d.png"
## Just outside STARTING; Y is the flame base so the fire sits on that line.
const FLAME_INSET_X := -0.06
const FLAME_ANCHOR_Y := 0.78

var _banner: TextureRect
var _glow: TextureRect
var _torch_l: TextureRect
var _torch_r: TextureRect
var _tex_fireball: Array[Texture2D] = []
var _glow_phase := 0.0
var _torch_frame_l := 0.0
var _torch_frame_r := 12.0


func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	var soon_tex := load(ASSET_SOON) as Texture2D
	var glow_tex := load(ASSET_GLOW) as Texture2D
	if soon_tex == null:
		push_error("starting_soon_banner: missing %s" % ASSET_SOON)
		return
	for i in range(24):
		var ft := load(FIREBALL % i) as Texture2D
		if ft:
			_tex_fireball.append(ft)
	_banner = _mk_tex_rect(soon_tex)
	add_child(_banner)
	_glow = _mk_tex_rect(glow_tex)
	var glow_mat := CanvasItemMaterial.new()
	glow_mat.blend_mode = CanvasItemMaterial.BLEND_MODE_ADD
	_glow.material = glow_mat
	add_child(_glow)
	if not _tex_fireball.is_empty():
		_torch_l = _mk_tex_rect(_tex_fireball[0])
		_torch_r = _mk_tex_rect(_tex_fireball[0])
		_torch_r.flip_h = true
		add_child(_torch_l)
		add_child(_torch_r)
	CompanionConfig.settings_saved.connect(_on_cfg)
	CompanionConfig.settings_loaded.connect(_on_cfg)
	get_viewport().size_changed.connect(_apply_layout)
	_on_cfg()
	set_process(true)


func _on_cfg() -> void:
	_apply_layout()


func _process(delta: float) -> void:
	_glow_phase += delta
	if _glow:
		_glow.modulate.a = maxf(0.0, sin(_glow_phase))
	if _tex_fireball.is_empty():
		return
	_torch_frame_l = fmod(_torch_frame_l + delta * FIREBALL_FPS, 24.0)
	_torch_frame_r = fmod(_torch_frame_r + delta * FIREBALL_FPS, 24.0)
	if _torch_l:
		_torch_l.texture = _tex_fireball[int(_torch_frame_l) % _tex_fireball.size()]
	if _torch_r:
		_torch_r.texture = _tex_fireball[int(_torch_frame_r) % _tex_fireball.size()]


func _apply_layout() -> void:
	var want := CompanionConfig.element_enabled(self, "starting_soon")
	visible = want
	if not want or _banner == null or _banner.texture == null:
		return
	var layout := CompanionConfig.layout_data_for(self)
	var s := clampf(layout.starting_soon_scale, 0.25, 12.0)
	var tw0 := float(_banner.texture.get_width())
	var th0 := float(_banner.texture.get_height())
	var tw := tw0 * s
	var th := th0 * s
	_sync_texrect_min_from_texture(_banner)
	_banner.scale = Vector2(s, s)
	_banner.position = Vector2(float(layout.starting_soon_x_px), float(layout.starting_soon_y_px))
	if _glow:
		_sync_texrect_min_from_texture(_glow)
		_glow.scale = Vector2(s, s)
		var gw := float(_glow.texture.get_width()) * s if _glow.texture else tw
		var gh := float(_glow.texture.get_height()) * s if _glow.texture else th
		_glow.position = _banner.position + Vector2((tw - gw) * 0.5, (th - gh) * 0.5)
	var cx_l := _banner.position.x + tw * FLAME_INSET_X
	var cx_r := _banner.position.x + tw * (1.0 - FLAME_INSET_X)
	var cy := _banner.position.y + th * FLAME_ANCHOR_Y
	_layout_torch(_torch_l, cx_l, cy, s)
	_layout_torch(_torch_r, cx_r, cy, s)


func _mk_tex_rect(tex: Texture2D) -> TextureRect:
	var trect := TextureRect.new()
	trect.texture = tex
	trect.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	trect.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	trect.stretch_mode = TextureRect.STRETCH_KEEP
	trect.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return trect


func _sync_texrect_min_from_texture(trect: TextureRect) -> void:
	if trect == null or trect.texture == null:
		return
	trect.custom_minimum_size = Vector2(
		float(trect.texture.get_width()),
		float(trect.texture.get_height())
	)
	trect.reset_size()


func _layout_torch(torch_rect: TextureRect, cx: float, cy: float, torch_scale: float) -> void:
	if torch_rect == null or torch_rect.texture == null:
		return
	_sync_texrect_min_from_texture(torch_rect)
	torch_rect.scale = Vector2(torch_scale, torch_scale)
	var fw := float(torch_rect.texture.get_width()) * torch_scale
	var fh := float(torch_rect.texture.get_height()) * torch_scale
	torch_rect.position = Vector2(cx - fw * 0.5, cy - fh)
