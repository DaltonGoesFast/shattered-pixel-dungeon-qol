extends ColorRect

## Scroll speed matches [method AlchemyScene.update]: water.offset(0, -5 * Game.elapsed).
const SCROLL_PX_PER_SEC := 2.0
## >1.0 = larger water patches on screen (alchemy UI uses an effectively zoomed tile).
const TEXTURE_SCALE := 4.5

static var _shader: Shader = preload("res://shaders/live_water_scroll.gdshader")
static var _chroma_shader: Shader = preload("res://shaders/live_chroma_l_shape.gdshader")
const _WaterArt := preload("res://scripts/live_water_art.gd")

var _scroll_y: float = 0.0
var _water_stem: String = LiveWaterArt.DEFAULT_STEM


func _ready() -> void:
	var mat := ShaderMaterial.new()
	mat.shader = _shader
	material = mat
	_set_water_texture(_WaterArt.texture_for_stem(_water_stem))
	mat.set_shader_parameter("texture_scale", TEXTURE_SCALE)
	_sync_gradient_uniforms()
	item_rect_changed.connect(_update_rect_uniform)
	GameWebSocketClient.snapshot_received.connect(_on_game_snapshot)
	var snap: Dictionary = GameWebSocketClient.last_game_snapshot
	if not snap.is_empty():
		_apply_water_from_snapshot(snap)
	CompanionConfig.settings_loaded.connect(_sync_live_water_layout)
	CompanionConfig.settings_saved.connect(_sync_live_water_layout)
	get_viewport().size_changed.connect(_sync_live_water_layout)
	var p := get_parent()
	if p:
		p.visibility_changed.connect(_on_parent_visibility_changed)
		set_process(p.visible)
	_update_rect_uniform()
	call_deferred("_sync_live_water_layout")


func _on_game_snapshot(data: Dictionary) -> void:
	_apply_water_from_snapshot(data)


func _apply_water_from_snapshot(snapshot: Dictionary) -> void:
	var stem := _WaterArt.water_stem_from_snapshot(snapshot)
	if stem == _water_stem:
		return
	_water_stem = stem
	var tex := _WaterArt.texture_for_stem(stem)
	if tex == null:
		return
	_set_water_texture(tex)
	_scroll_y = 0.0
	if material != null:
		material.set_shader_parameter("scroll_px", Vector2.ZERO)


func _set_water_texture(tex: Texture2D) -> void:
	if material == null:
		return
	if tex == null:
		push_warning(
			"live_water_background: missing water texture (import assets/environment/*.png in Godot)."
		)
		return
	material.set_shader_parameter("water_tex", tex)


func _on_parent_visibility_changed() -> void:
	var p := get_parent()
	set_process(p != null and p.visible)


func _process(delta: float) -> void:
	var p := get_parent()
	if p == null or not p.visible:
		return
	var tex: Texture2D = material.get_shader_parameter("water_tex") as Texture2D
	if tex == null:
		return
	_scroll_y -= SCROLL_PX_PER_SEC * delta
	var h := float(tex.get_height())
	while _scroll_y < -h:
		_scroll_y += h
	while _scroll_y > 0.0:
		_scroll_y -= h
	material.set_shader_parameter("scroll_px", Vector2(0.0, _scroll_y))


func _update_rect_uniform() -> void:
	if material != null:
		material.set_shader_parameter("rect_px", size)
	_sync_live_water_layout()


func _ensure_chroma_material() -> void:
	var chroma := get_parent().get_node_or_null("ChromaOverlay") as ColorRect
	if chroma == null or chroma.material != null:
		return
	var cmat := ShaderMaterial.new()
	cmat.shader = _chroma_shader
	chroma.material = cmat
	chroma.color = Color.WHITE


func _sync_gradient_uniforms() -> void:
	if material == null:
		return
	var g := CompanionConfig.live_water_gradient_for(self)
	material.set_shader_parameter("gradient_fade_start", g.x)
	material.set_shader_parameter("gradient_fade_end", g.y)


func _sync_live_water_layout() -> void:
	_ensure_chroma_material()
	_sync_gradient_uniforms()
	var v: Vector3 = CompanionConfig.live_water_l_shape_uv_vector(self)
	var feather_v: float = CompanionConfig.live_water_edge_feather_v_uv(self)
	if material != null:
		material.set_shader_parameter("l_shape_uv", v)
		material.set_shader_parameter("edge_feather_v", feather_v)
	var chroma := get_parent().get_node_or_null("ChromaOverlay") as ColorRect
	if chroma != null and chroma.material is ShaderMaterial:
		chroma.material.set_shader_parameter("l_shape_uv", v)
		chroma.material.set_shader_parameter("edge_feather_v", feather_v)
