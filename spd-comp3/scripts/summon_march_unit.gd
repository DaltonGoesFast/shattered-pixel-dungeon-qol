extends Node2D
## Monster march across the overlay; uses MobCommandArt walk frames when available.

const _MobArt := preload("res://scripts/mob_command_art.gd")
const _FONT: FontFile = preload("res://assets/fonts/pixel_font.ttf")

var _sprite: Sprite2D
var _idle_frames: Array[Texture2D] = []
var _anim_idx: int = 0
var _anim_accum: float = 0.0
var _march_duration: float = 6.0


func setup(event: Dictionary, viewport_size: Vector2) -> void:
	_march_duration = maxf(1.0, CompanionConfig.summon_march_duration_sec)

	var monster := str(event.get("monster", "rat")).to_lower()
	var username := str(event.get("username", ""))
	var layout := str(event.get("layout", "horizontal")).to_lower()

	_build_visual(monster, username)

	if layout == "vertical":
		_march_vertical(viewport_size)
	else:
		_march_horizontal(viewport_size)

	set_process(_idle_frames.size() > 1)


func _sprite_target_size() -> float:
	return float(clampi(CompanionConfig.summon_march_sprite_size_px, 16, 256))


func _build_visual(monster: String, username: String) -> void:
	var sprite_px := _sprite_target_size()
	_idle_frames = _MobArt.march_frames_for_monster_label(monster)
	_sprite = Sprite2D.new()
	_sprite.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	_sprite.centered = true
	if _idle_frames.is_empty():
		var img := Image.create(int(sprite_px), int(sprite_px), false, Image.FORMAT_RGBA8)
		img.fill(Color(1, 1, 1, 1))
		_sprite.texture = ImageTexture.create_from_image(img)
	else:
		_sprite.texture = _idle_frames[0]
		var tex_size := _sprite.texture.get_size()
		if tex_size.x > 0 and tex_size.y > 0:
			var scale := sprite_px / maxf(tex_size.x, tex_size.y)
			_sprite.scale = Vector2(scale, scale)
	add_child(_sprite)

	if CompanionConfig.summon_march_show_username and not username.is_empty():
		var user_label := Label.new()
		user_label.text = username
		user_label.add_theme_font_size_override(
			"font_size", CompanionConfig.summon_march_username_font_size_px
		)
		user_label.add_theme_font_override("font", _FONT)
		user_label.modulate = CompanionConfig.summon_march_username_color
		add_child(user_label)
		_place_username_label(user_label)

	if CompanionConfig.summon_march_show_monster_name:
		var name_label := Label.new()
		name_label.text = SummonMarchRegistry.display_name(monster)
		name_label.position = Vector2(
			CompanionConfig.summon_march_monster_offset_x,
			CompanionConfig.summon_march_monster_offset_y
		)
		name_label.add_theme_font_size_override(
			"font_size", CompanionConfig.summon_march_monster_font_size_px
		)
		name_label.add_theme_font_override("font", _FONT)
		name_label.modulate = CompanionConfig.summon_march_monster_color
		add_child(name_label)


func _place_username_label(label: Label) -> void:
	var y := float(CompanionConfig.summon_march_username_offset_y)
	if CompanionConfig.summon_march_username_centered:
		label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		label.reset_size()
		var sz := label.get_combined_minimum_size()
		label.position = Vector2(-sz.x * 0.5, y)
	else:
		label.position = Vector2(
			CompanionConfig.summon_march_username_offset_x,
			int(y)
		)


func _process(delta: float) -> void:
	if _idle_frames.size() <= 1 or _sprite == null:
		return
	_anim_accum += delta
	var fps: float = clampf(CompanionConfig.summon_march_mob_fps, 0.5, 30.0)
	var spf: float = 1.0 / fps
	while _anim_accum >= spf:
		_anim_accum -= spf
		_anim_idx = (_anim_idx + 1) % _idle_frames.size()
		_sprite.texture = _idle_frames[_anim_idx]


func _vertical_content_extent() -> float:
	var ext := _sprite_target_size() * 0.5
	if CompanionConfig.summon_march_show_monster_name:
		var top := absf(float(CompanionConfig.summon_march_monster_offset_y))
		top += float(CompanionConfig.summon_march_monster_font_size_px)
		ext = maxf(ext, top)
	if CompanionConfig.summon_march_show_username:
		var bottom := absf(float(CompanionConfig.summon_march_username_offset_y))
		bottom += float(CompanionConfig.summon_march_username_font_size_px)
		ext = maxf(ext, bottom)
	return ext + 8.0


func _safe_y_range(viewport_size: Vector2) -> Vector2:
	var margin := _vertical_content_extent()
	var lo := margin
	var hi := viewport_size.y - margin
	if hi < lo:
		return Vector2(viewport_size.y * 0.5, viewport_size.y * 0.5)
	var y_min_frac := minf(
		CompanionConfig.summon_march_lane_y_min_fraction,
		CompanionConfig.summon_march_lane_y_max_fraction
	)
	var y_max_frac := maxf(
		CompanionConfig.summon_march_lane_y_min_fraction,
		CompanionConfig.summon_march_lane_y_max_fraction
	)
	lo = maxf(lo, viewport_size.y * y_min_frac)
	hi = minf(hi, viewport_size.y * y_max_frac)
	if hi < lo:
		hi = lo
	return Vector2(lo, hi)


func _pick_horizontal_y(viewport_size: Vector2) -> float:
	var band: Vector2 = _safe_y_range(viewport_size)
	if band.y <= band.x:
		return band.x
	return randf_range(band.x, band.y)


func _pick_horizontal_start_x(margin: float) -> float:
	# Stagger left-edge entry so rapid summons do not line up in one column.
	return -margin - randf() * margin


func _march_horizontal(viewport_size: Vector2) -> void:
	var margin := float(CompanionConfig.summon_march_edge_margin_px)
	var y := _pick_horizontal_y(viewport_size)
	var start_x := _pick_horizontal_start_x(margin)
	position = Vector2(start_x, y)

	var tween := create_tween()
	tween.set_trans(Tween.TRANS_LINEAR)
	tween.tween_property(
		self, "position", Vector2(viewport_size.x + margin, y), _march_duration
	)
	tween.tween_callback(queue_free)


func _pick_vertical_x(viewport_size: Vector2, margin: float) -> float:
	return randf_range(margin, viewport_size.x - margin)


func _march_vertical(viewport_size: Vector2) -> void:
	var margin := float(CompanionConfig.summon_march_edge_margin_px)
	var x := _pick_vertical_x(viewport_size, margin)
	position = Vector2(x, viewport_size.y + margin + randf() * margin)

	var tween := create_tween()
	tween.set_trans(Tween.TRANS_LINEAR)
	tween.tween_property(self, "position", Vector2(x, -margin), _march_duration)
	tween.tween_callback(queue_free)
