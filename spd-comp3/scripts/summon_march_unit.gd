extends Node2D
## Monster march across the overlay; uses MobCommandArt walk frames when available.
## Active leaders wear crowns: heat, or sprint gold / silver / bronze (top 3).
## Past sprint winners are not crowned on marches — Hall / level-up banner is enough.

const _MobArt := preload("res://scripts/mob_command_art.gd")
const _FONT: FontFile = preload("res://assets/fonts/pixel_font.ttf")
const _CROWN_GOLD: Texture2D = preload("res://assets/item_sheet/crown.png")
const _CROWN_SILVER: Texture2D = preload("res://assets/item_sheet/crown_silver.png")
const _CROWN_BRONZE: Texture2D = preload("res://assets/item_sheet/crown_bronze.png")
const _CROWN_HEAT: Texture2D = preload("res://assets/item_sheet/crown_heat.png")

var _sprite: Sprite2D
var _crown: Sprite2D = null
var _idle_frames: Array[Texture2D] = []
var _anim_idx: int = 0
var _anim_accum: float = 0.0
var _march_duration: float = 6.0
## heat | gold | silver | bronze | ""
var _badge: String = ""
var _aura: Node2D = null


func setup(event: Dictionary, viewport_size: Vector2) -> void:
	_march_duration = maxf(1.0, CompanionConfig.summon_march_duration_sec)

	var monster := str(event.get("monster", "rat")).to_lower()
	var username := str(event.get("username", ""))
	var layout := str(event.get("layout", "horizontal")).to_lower()
	_badge = _resolve_badge(event, username)
	if not _badge.is_empty():
		print("[SummonMarch] %s badge=%s" % [username, _badge])

	_build_visual(monster, username)

	if layout == "vertical":
		_march_vertical(viewport_size)
	else:
		_march_horizontal(viewport_size)

	set_process(_idle_frames.size() > 1 or not _badge.is_empty())


func _is_leader() -> bool:
	return not _badge.is_empty()


func _resolve_badge(event: Dictionary, username: String) -> String:
	var raw := str(event.get("badge", "")).strip_edges().to_lower()
	match raw:
		"heat", "gold", "silver", "bronze":
			return raw
	# Heat (red) first, then sprint metals.
	if bool(event.get("heat_leader", false)):
		return "heat"
	var rank := int(event.get("sprint_rank", 0))
	match rank:
		1:
			return "gold"
		2:
			return "silver"
		3:
			return "bronze"
	return _badge_from_bestiary_poll(username)


func _badge_from_bestiary_poll(username: String) -> String:
	if username.is_empty():
		return ""
	var svc := get_node_or_null("/root/BestiaryPollService")
	if svc == null:
		return ""
	var payload: Variant = svc.get("last_payload")
	if typeof(payload) != TYPE_DICTIONARY:
		return ""
	var key := username.to_lower()
	var heat: Variant = payload.get("heat", {})
	if typeof(heat) == TYPE_DICTIONARY:
		if str(heat.get("username", "")).to_lower() == key:
			return "heat"
	var sprint: Variant = payload.get("sprint", {})
	if typeof(sprint) == TYPE_DICTIONARY:
		var top: Variant = sprint.get("top", [])
		if typeof(top) == TYPE_ARRAY:
			for entry in top:
				if typeof(entry) != TYPE_DICTIONARY:
					continue
				if str(entry.get("username", "")).to_lower() != key:
					continue
				match int(entry.get("rank", 0)):
					1:
						return "gold"
					2:
						return "silver"
					3:
						return "bronze"
		if str(sprint.get("username", "")).to_lower() == key:
			return "gold"
	return ""


func _crown_texture_for_badge(badge: String) -> Texture2D:
	match badge:
		"heat":
			return _CROWN_HEAT
		"silver":
			return _CROWN_SILVER
		"bronze":
			return _CROWN_BRONZE
		"gold":
			return _CROWN_GOLD
		_:
			return null


func _sprite_target_size() -> float:
	var base := float(clampi(CompanionConfig.summon_march_sprite_size_px, 16, 256))
	if not _is_leader():
		return base
	return base * clampf(CompanionConfig.summon_crowned_sprite_scale, 1.0, 3.0)


func _build_visual(monster: String, username: String) -> void:
	var sprite_px := _sprite_target_size()
	_idle_frames = _MobArt.march_frames_for_monster_label(monster)
	_sprite = Sprite2D.new()
	_sprite.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	_sprite.centered = true
	_sprite.z_index = 1
	if _idle_frames.is_empty():
		var img := Image.create(int(sprite_px), int(sprite_px), false, Image.FORMAT_RGBA8)
		img.fill(Color(1, 1, 1, 1))
		_sprite.texture = ImageTexture.create_from_image(img)
	else:
		_sprite.texture = _idle_frames[0]
		var tex_size := _sprite.texture.get_size()
		if tex_size.x > 0 and tex_size.y > 0:
			var spr_scale := sprite_px / maxf(tex_size.x, tex_size.y)
			_sprite.scale = Vector2(spr_scale, spr_scale)
	# FX first (behind), then mob on top — normal blend so green chroma doesn't erase ADD glow.
	if _is_leader():
		_sprite.modulate = CompanionConfig.summon_crowned_mob_modulate
		if CompanionConfig.summon_crowned_show_glow:
			_add_crowned_fx(sprite_px)
	add_child(_sprite)
	if _is_leader() and CompanionConfig.summon_crowned_show_crown:
		_add_leader_crown(sprite_px)

	if CompanionConfig.summon_march_show_username and not username.is_empty():
		var user_label := Label.new()
		var star: bool = _is_leader() and CompanionConfig.summon_crowned_show_star_prefix
		user_label.text = ("★ " if star else "") + username
		var user_fs := CompanionConfig.summon_march_username_font_size_px
		if _is_leader() and CompanionConfig.summon_crowned_username_font_size_px > 0:
			user_fs = CompanionConfig.summon_crowned_username_font_size_px
		user_label.add_theme_font_size_override("font_size", user_fs)
		user_label.add_theme_font_override("font", _FONT)
		user_label.modulate = (
			CompanionConfig.summon_crowned_username_color
			if _is_leader()
			else CompanionConfig.summon_march_username_color
		)
		user_label.z_index = 5
		add_child(user_label)
		_place_username_label(user_label)

	if CompanionConfig.summon_march_show_monster_name:
		var name_label := Label.new()
		name_label.text = SummonMarchRegistry.display_name(monster)
		if _is_leader() and CompanionConfig.summon_crowned_show_star_prefix:
			name_label.text += " ★"
		name_label.position = Vector2(
			CompanionConfig.summon_march_monster_offset_x,
			CompanionConfig.summon_march_monster_offset_y
		)
		name_label.add_theme_font_size_override(
			"font_size", CompanionConfig.summon_march_monster_font_size_px
		)
		name_label.add_theme_font_override("font", _FONT)
		name_label.modulate = (
			CompanionConfig.summon_crowned_username_color
			if _is_leader()
			else CompanionConfig.summon_march_monster_color
		)
		name_label.z_index = 5
		add_child(name_label)


func _glow_color_for_badge(badge: String) -> Color:
	match badge:
		"heat":
			return Color(1.0, 0.18, 0.12, 1.0)  # red
		"silver":
			return Color(0.78, 0.82, 0.92, 1.0)  # cool silver
		"bronze":
			return Color(0.85, 0.48, 0.22, 1.0)  # bronze
		"gold":
			return Color(1.0, 1.0, 0.0, 1.0)  # Blessed yellow
		_:
			return Color(1.0, 1.0, 0.0, 1.0)


func _add_crowned_fx(sprite_px: float) -> void:
	## Flare aura: gold=yellow, silver/bronze metal tones, heat=red.
	var glow_color: Color = _glow_color_for_badge(_badge)
	var n := clampi(CompanionConfig.summon_crowned_glow_rays, 3, 16)
	var spin_deg := maxf(0.0, CompanionConfig.summon_crowned_glow_spin_deg)
	var rad_scale := clampf(CompanionConfig.summon_crowned_glow_radius_scale, 0.25, 3.0)

	_aura = Node2D.new()
	_aura.name = "LeaderFlare"
	_aura.z_index = 0
	_aura.rotation_degrees = 45.0  # Flare default starting angle
	var mat: CanvasItemMaterial = CanvasItemMaterial.new()
	mat.blend_mode = CanvasItemMaterial.BLEND_MODE_ADD

	# CharSprite: size = max(max(w,h)+4, 16); Flare radius = size
	var radius := maxf(sprite_px + 4.0, 16.0) * rad_scale
	for i in range(n):
		# Each ray is a triangle: center → rim point i → rim midpoint (Flare.java)
		var a0 := float(i) * TAU / float(n)
		var a1 := a0 + TAU / float(n) / 2.0
		var ray := Polygon2D.new()
		ray.material = mat
		ray.polygon = PackedVector2Array(
			[
				Vector2.ZERO,
				Vector2(cos(a0), sin(a0)) * radius,
				Vector2(cos(a1), sin(a1)) * radius,
			]
		)
		# Soft fade toward tips (gradient texture stand-in)
		ray.vertex_colors = PackedColorArray(
			[
				Color(glow_color.r, glow_color.g, glow_color.b, 0.95),
				Color(glow_color.r, glow_color.g, glow_color.b, 0.0),
				Color(glow_color.r, glow_color.g, glow_color.b, 0.15),
			]
		)
		_aura.add_child(ray)

	add_child(_aura)
	if spin_deg > 0.01:
		# Relative + loops: absolute end values stop after one turn (target already reached).
		var spin := create_tween().set_loops()
		spin.tween_property(_aura, "rotation_degrees", 360.0, 360.0 / spin_deg).as_relative()


func _add_leader_crown(sprite_px: float) -> void:
	## Heat / gold / silver / bronze crown for active competition leaders.
	var tex: Texture2D = _crown_texture_for_badge(_badge)
	if tex == null:
		return
	_crown = Sprite2D.new()
	_crown.name = "LeaderCrown"
	_crown.texture = tex
	_crown.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	_crown.centered = true
	_crown.z_index = 4
	var tex_w := maxf(tex.get_size().x, 1.0)
	var crown_frac := clampf(CompanionConfig.summon_crowned_crown_scale, 0.1, 3.0)
	var cscale := (sprite_px * crown_frac) / tex_w
	_crown.scale = Vector2(cscale, cscale)
	# Offset Y is a fraction of sprite height; negative = above center (default -0.32).
	_crown.position = Vector2(0.0, sprite_px * CompanionConfig.summon_crowned_crown_offset_y)
	# Crown art is already colored — no tint.
	_crown.modulate = Color.WHITE
	add_child(_crown)
	var bob := create_tween().set_loops()
	var y0 := _crown.position.y
	bob.tween_property(_crown, "position:y", y0 - sprite_px * 0.04, 0.45)
	bob.tween_property(_crown, "position:y", y0 + sprite_px * 0.02, 0.45)


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
	if _is_leader():
		ext *= 1.15
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
