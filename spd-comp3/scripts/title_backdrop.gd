extends Control
## Title parallax modeled on [TitleBackground] (Shattered Pixel Dungeon): **arches** (base scroll), cluster / small-far / mid×2 / small-close
## using exported [code]arches[/code], [code]large debris[/code] + [code]small debris[/code] PNGs. Bounded pools for floating layers; arches capped by [constant ARCH_PIECES_MAX].
## Layout supports landscape (main) and portrait (vertical companion window).

const ASSET := "res://assets/title_screen/"
const SCROLL_BASE := 15.0
## Landscape-only UI; torch cycles through 24 frames at this rate.
const FIREBALL_FPS := 20.0

const CLUSTER_FAR_COUNT := 8
const CLUSTER_COUNT := 8
const SMALL_FAR_COUNT := 10
const MID1_COUNT := 11
const MID2_COUNT := 11
const SMALL_CLOSE_COUNT := 10
## Cap arch [TextureRect]s (Java allocates without bound; we bound for stream stability).
const ARCH_PIECES_MAX := 160

## Extra random scale on [code]_tex_large[/code] after [method _layout_scale] (mid2 is the biggest).
const MID1_DEBRIS_SCALE_MIN := 0.7
const MID1_DEBRIS_SCALE_MAX := 1.1
const MID2_DEBRIS_SCALE_MIN := 1.0
const MID2_DEBRIS_SCALE_MAX := 1.4

## Title banner, additive glow, and torch flames share this scale (1.0 = 1 texture pixel : 1 px before stretch).
const TITLE_CHROME_SCALE := 2.704
## Vertical layout: title group is centered within this fraction of viewport height from the top.
const TITLE_CHROME_TOP_BAND := 0.46
## Portrait (h > w): tighter top band so title+torches match the cropped pause look.
const TITLE_CHROME_TOP_BAND_PORTRAIT := 0.30

## Recycle only after the sprite quad has fully crossed the **visible** viewport top (same canvas space as [method _debris_bottom_global]). Pivot is bottom-centered like Java [code]y + height[/code]; small margin in px.
const RECYCLE_PAST_TOP_PX := 4.0

var _w := 0.0
var _h := 0.0
var _density := 1.0

var _parallax: Control
var _chrome: Control
var _bg: ColorRect

var _arch_holder: Control
var _arch_pieces: Array[TextureRect] = []
var _tex_arch: Array[Texture2D] = []
var _arch_chances: PackedFloat32Array = PackedFloat32Array()

var _cluster_far_holder: Control
var _cluster_holder: Control
var _small_far_holder: Control
var _mid1_holder: Control
var _mid2_holder: Control
var _small_close_holder: Control

var _cluster_far_pieces: Array[TextureRect] = []
var _cluster_pieces: Array[TextureRect] = []
var _small_far_pieces: Array[TextureRect] = []
var _mid1_pieces: Array[TextureRect] = []
var _mid2_pieces: Array[TextureRect] = []
var _small_close_pieces: Array[TextureRect] = []

var _tex_cluster: Array[Texture2D] = []
var _tex_large: Array[Texture2D] = []
var _tex_small: Array[Texture2D] = []

var _mid_chances: PackedFloat32Array = PackedFloat32Array()
var _last_mids: Array[int] = []
var _small_chances: PackedFloat32Array = PackedFloat32Array()
var _last_smalls: Array[int] = []

var _title_tex: Texture2D
var _glow_tex: Texture2D
var _banner: TextureRect
var _glow: TextureRect
var _torch_l: TextureRect
var _torch_r: TextureRect
var _torch_frame_l := 0.0
var _torch_frame_r := 12.0
var _glow_phase := 0.0
var _tex_fireball: Array[Texture2D] = []


func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	_tex_cluster.append(load("%sback_clusters/cluster_%02d.png" % [ASSET, 0]) as Texture2D)
	_tex_cluster.append(load("%sback_clusters/cluster_%02d.png" % [ASSET, 1]) as Texture2D)
	_tex_large = _load_numbered_textures("%slarge debris/" % ASSET)
	_tex_small = _load_numbered_textures("%ssmall debris/" % ASSET)
	_tex_arch = _load_numbered_textures("%sarches/" % ASSET)
	if _tex_large.is_empty() or _tex_small.is_empty():
		push_error("title_backdrop: large debris / small debris folders must contain 000.png, 001.png, ...")

	_title_tex = load("%sbanners/title_land.png" % ASSET) as Texture2D
	_glow_tex = load("%sbanners/title_glow_land.png" % ASSET) as Texture2D
	for i in range(24):
		_tex_fireball.append(load("%sfireball_tall/frame_%02d.png" % [ASSET, i]) as Texture2D)

	_init_arch_chances()
	_init_tile_chances()

	_parallax = Control.new()
	_parallax.set_anchors_preset(Control.PRESET_FULL_RECT)
	_parallax.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_parallax.clip_contents = false
	add_child(_parallax)

	_bg = ColorRect.new()
	_bg.set_anchors_preset(Control.PRESET_FULL_RECT)
	_bg.color = Color(0, 0, 0, 1)
	_bg.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_parallax.add_child(_bg)

	_arch_holder = _mk_layer()
	_parallax.add_child(_arch_holder)

	_cluster_far_holder = _mk_layer()
	_cluster_holder = _mk_layer()
	_small_far_holder = _mk_layer()
	_mid1_holder = _mk_layer()
	_mid2_holder = _mk_layer()
	_small_close_holder = _mk_layer()

	_spawn_pool(_cluster_far_pieces, _cluster_far_holder, CLUSTER_FAR_COUNT)
	_spawn_pool(_cluster_pieces, _cluster_holder, CLUSTER_COUNT)
	_spawn_pool(_small_far_pieces, _small_far_holder, SMALL_FAR_COUNT)
	_spawn_pool(_mid1_pieces, _mid1_holder, MID1_COUNT)
	_spawn_pool(_mid2_pieces, _mid2_holder, MID2_COUNT)
	_spawn_pool(_small_close_pieces, _small_close_holder, SMALL_CLOSE_COUNT)

	_parallax.add_child(_cluster_far_holder)
	_parallax.add_child(_cluster_holder)
	_parallax.add_child(_small_far_holder)
	_parallax.add_child(_mid1_holder)
	_parallax.add_child(_mid2_holder)
	_parallax.add_child(_small_close_holder)

	_chrome = Control.new()
	_chrome.set_anchors_preset(Control.PRESET_FULL_RECT)
	_chrome.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(_chrome)
	_build_chrome()

	await get_tree().process_frame
	_apply_layout()
	set_process(true)


func _notification(what: int) -> void:
	if what == NOTIFICATION_RESIZED:
		call_deferred("_apply_layout")


func _load_numbered_textures(dir_path: String) -> Array[Texture2D]:
	var out: Array[Texture2D] = []
	var i := 0
	while i < 128:
		var path: String = "%s%03d.png" % [dir_path, i]
		if not ResourceLoader.exists(path):
			break
		var t: Texture2D = load(path) as Texture2D
		if t == null:
			break
		out.append(t)
		i += 1
	return out


func _init_tile_chances() -> void:
	_mid_chances.resize(_tex_large.size())
	for j in range(_mid_chances.size()):
		_mid_chances[j] = 1.0
	_small_chances.resize(_tex_small.size())
	for j in range(_small_chances.size()):
		_small_chances[j] = 1.0


## Matches Java [code]INIT_ARCH_CHANCES[/code] ({5,5,2,2,2,2} for six film cells); extra variants use weight 2.
func _init_arch_chances() -> void:
	if _tex_arch.is_empty():
		_arch_chances = PackedFloat32Array()
		return
	var initv: Array[float] = [5.0, 5.0, 2.0, 2.0, 2.0, 2.0]
	_arch_chances.resize(_tex_arch.size())
	for i in range(_tex_arch.size()):
		_arch_chances[i] = initv[i] if i < initv.size() else 2.0


func _pick_arch_frame() -> int:
	if _tex_arch.is_empty():
		return 0
	var tile := _weighted_pick_deplete(_arch_chances)
	if tile < 0:
		_init_arch_chances()
		tile = _weighted_pick_deplete(_arch_chances)
	return clampi(tile, 0, _tex_arch.size() - 1)


func _mk_layer() -> Control:
	var c := Control.new()
	c.set_anchors_preset(Control.PRESET_FULL_RECT)
	c.mouse_filter = Control.MOUSE_FILTER_IGNORE
	c.clip_contents = false
	return c


func _spawn_pool(arr: Array[TextureRect], holder: Control, n: int) -> void:
	for i in n:
		var piece := _mk_debris_rect()
		arr.append(piece)
		holder.add_child(piece)


func _mk_debris_rect() -> TextureRect:
	var t := TextureRect.new()
	t.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	t.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	t.stretch_mode = TextureRect.STRETCH_KEEP
	t.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return t


func _layout_scale() -> float:
	return _h / 450.0


func _update_density() -> void:
	var sc := _h / 450.0
	_density = (_w / (800.0 * sc) + 0.5) / 1.5


func _weighted_pick_deplete(w: PackedFloat32Array) -> int:
	var sum := 0.0
	for i in range(w.size()):
		if w[i] > 0:
			sum += w[i]
	if sum <= 0:
		return -1
	var r := randf() * sum
	var acc := 0.0
	for i in range(w.size()):
		if w[i] <= 0:
			continue
		acc += w[i]
		if r <= acc:
			w[i] -= 1.0
			return i
	return -1


func _pick_cluster_frame() -> int:
	return randi() % 2


func _pick_mid_frame() -> int:
	if _tex_large.is_empty():
		return 0
	var tile := -1
	var guard := 0
	while guard < 64:
		guard += 1
		tile = _weighted_pick_deplete(_mid_chances)
		if tile < 0:
			_mid_chances.resize(_tex_large.size())
			for j in range(_mid_chances.size()):
				_mid_chances[j] = 1.0
			tile = _weighted_pick_deplete(_mid_chances)
		if tile >= 0 and not _last_mids.has(tile):
			break
	_last_mids.insert(0, tile)
	if _last_mids.size() > 20:
		_last_mids.remove_at(19)
	return clampi(tile, 0, _tex_large.size() - 1)


func _pick_small_frame() -> int:
	if _tex_small.is_empty():
		return 0
	var tile := -1
	var guard := 0
	while guard < 64:
		guard += 1
		tile = _weighted_pick_deplete(_small_chances)
		if tile < 0:
			_small_chances.resize(_tex_small.size())
			for j in range(_small_chances.size()):
				_small_chances[j] = 1.0
			tile = _weighted_pick_deplete(_small_chances)
		if tile >= 0 and not _last_smalls.has(tile):
			break
	_last_smalls.insert(0, tile)
	if _last_smalls.size() > 15:
		_last_smalls.remove_at(14)
	return clampi(tile, 0, _tex_small.size() - 1)


## [code]EXPAND_IGNORE_SIZE[/code] would otherwise keep a 0×0 layout rect → wrong culling / AABB so sprites vanish while still visibly drawn.
func _sync_debris_layout_to_texture(piece: TextureRect) -> void:
	if piece.texture == null:
		return
	piece.custom_minimum_size = Vector2(
		float(piece.texture.get_width()),
		float(piece.texture.get_height())
	)
	piece.reset_size()


func _debris_bottom_global(piece: TextureRect) -> float:
	if piece.texture == null:
		return piece.get_global_rect().end.y
	var tw := float(piece.texture.get_width())
	var th := float(piece.texture.get_height())
	if tw < 1.0 or th < 1.0:
		return piece.get_global_rect().end.y
	var xf := piece.get_global_transform_with_canvas()
	var bot := -INF
	for corner in [Vector2.ZERO, Vector2(tw, 0.0), Vector2(0.0, th), Vector2(tw, th)]:
		bot = maxf(bot, (xf * corner).y)
	return bot


## Java image [code]x,y[/code] is top-left but stream advancement uses [code]y + height[/code]. Anchor pivot at bottom-center so motion + placement follow the visible “footing,” not the transparent top margin.
func _set_debris_pivot_bottom_center(piece: TextureRect) -> void:
	if piece.texture == null:
		return
	piece.pivot_offset = Vector2(
		float(piece.texture.get_width()) * 0.5,
		float(piece.texture.get_height())
	)


## Snap so the transformed quad's lowest global Y equals [param want_bottom_global_y] (holders are not scaled/rotated).
func _sync_debris_bottom_to_global(piece: TextureRect, want_bottom_global_y: float) -> void:
	var cur := _debris_bottom_global(piece)
	piece.position.y += want_bottom_global_y - cur


func _place_debris_with_bottom_at_global(piece: TextureRect, px_left: float, want_bottom_global_y: float) -> void:
	_sync_debris_layout_to_texture(piece)
	_set_debris_pivot_bottom_center(piece)
	piece.position = Vector2(px_left, 0.0)
	_sync_debris_bottom_to_global(piece, want_bottom_global_y)


## Viewport top in the same canvas space as [method CanvasItem.get_global_transform_with_canvas] (handles stretch / transform).
func _viewport_top_global_y() -> float:
	var vp := get_viewport()
	if vp == null:
		return (get_global_transform_with_canvas() * Vector2.ZERO).y
	var inv := vp.get_canvas_transform().affine_inverse()
	return (inv * Vector2(vp.get_visible_rect().position)).y


func _piece_fully_past_top(piece: TextureRect) -> bool:
	var top_y := _viewport_top_global_y()
	# Fully above visible top: southern edge of quad must be at least [constant RECYCLE_PAST_TOP_PX] past the top.
	return _debris_bottom_global(piece) < top_y - RECYCLE_PAST_TOP_PX


func _max_bottom_global_excluding(pieces: Array[TextureRect], exclude: TextureRect, holder: Control) -> float:
	var hr := holder.get_global_rect()
	var lo := hr.position.y
	for piece in pieces:
		if piece == exclude:
			continue
		lo = maxf(lo, _debris_bottom_global(piece))
	return lo


func _last_x_at_max_bottom(pieces: Array[TextureRect], exclude: TextureRect, _holder: Control) -> float:
	var best := -INF
	var lx := 0.0
	for piece in pieces:
		if piece == exclude:
			continue
		var b := _debris_bottom_global(piece)
		if b >= best:
			best = b
			lx = piece.position.x
	return lx


## Java small-far x: [w/3, w - 4w/3]
func _pick_x_small_far(pw: float, last_x: float) -> float:
	var flex := 0.0
	while true:
		var px := randf_range(pw / 3.0, _w - 4.0 * pw / 3.0)
		if absf(px - last_x) >= _density * (pw - flex):
			return px
		flex += 1.0
	return randf_range(pw / 3.0, _w - 4.0 * pw / 3.0)


## Java default float x: [-w/3, w - 2w/3]
func _pick_x_float(pw: float, last_x: float, width_factor: float) -> float:
	var flex := 0.0
	while true:
		var px := randf_range(-pw / 3.0, _w - 2.0 * pw / 3.0)
		if absf(px - last_x) >= _density * (pw * width_factor - flex):
			return px
		flex += 1.0
	return randf_range(-pw / 3.0, _w - 2.0 * pw / 3.0)


func _recycle_cluster_far(piece: TextureRect) -> void:
	var sc := _layout_scale() * 0.5
	piece.texture = _tex_cluster[_pick_cluster_frame()]
	piece.scale = Vector2(sc, sc)
	piece.rotation = deg_to_rad(randf_range(-20.0, 20.0))
	piece.modulate = Color(0.5, 0.5, 0.5, 1.0)
	var pw := float(piece.texture.get_width()) * sc
	var ph := float(piece.texture.get_height()) * sc
	var last_x := _last_x_at_max_bottom(_cluster_far_pieces, piece, _cluster_far_holder)
	var px := _pick_x_float(pw, last_x, 0.5)
	var lo_g := _max_bottom_global_excluding(_cluster_far_pieces, piece, _cluster_far_holder)
	var want_bottom_g := lo_g + randf_range(ph * 0.5, ph) / _density
	_place_debris_with_bottom_at_global(piece, px, want_bottom_g)


func _recycle_cluster(piece: TextureRect) -> void:
	var sc := _layout_scale()
	piece.texture = _tex_cluster[_pick_cluster_frame()]
	piece.scale = Vector2(sc, sc)
	piece.rotation = deg_to_rad(randf_range(-20.0, 20.0))
	piece.modulate = Color(0.75, 0.75, 0.75, 1.0)
	var pw := float(piece.texture.get_width()) * sc
	var ph := float(piece.texture.get_height()) * sc
	var last_x := _last_x_at_max_bottom(_cluster_pieces, piece, _cluster_holder)
	var px := _pick_x_float(pw, last_x, 0.5)
	var lo_g := _max_bottom_global_excluding(_cluster_pieces, piece, _cluster_holder)
	var want_bottom_g := lo_g + randf_range(ph * 0.5, ph) / _density
	_place_debris_with_bottom_at_global(piece, px, want_bottom_g)


func _recycle_small_far(piece: TextureRect) -> void:
	var sc := _layout_scale() * randf_range(0.75, 1.25)
	piece.texture = _tex_small[_pick_small_frame()]
	piece.scale = Vector2(sc, sc)
	piece.rotation = deg_to_rad(randf_range(-20.0, 20.0))
	piece.modulate = Color(0.8, 0.8, 0.8, 1.0)
	var pw := float(piece.texture.get_width()) * sc
	var ph := float(piece.texture.get_height()) * sc
	var last_x := _last_x_at_max_bottom(_small_far_pieces, piece, _small_far_holder)
	var px := _pick_x_small_far(pw, last_x)
	var lo_g := _max_bottom_global_excluding(_small_far_pieces, piece, _small_far_holder)
	# Java: bottom + height/2 + Random.Float(h/2, h) / density
	var want_bottom_g := lo_g + ph * 0.5 + randf_range(ph * 0.5, ph) / _density
	_place_debris_with_bottom_at_global(piece, px, want_bottom_g)


func _recycle_mid1(piece: TextureRect) -> void:
	var sc := _layout_scale() * randf_range(MID1_DEBRIS_SCALE_MIN, MID1_DEBRIS_SCALE_MAX)
	piece.texture = _tex_large[_pick_mid_frame()]
	piece.scale = Vector2(sc, sc)
	piece.rotation = deg_to_rad(randf_range(-20.0, 20.0))
	piece.modulate = Color(0.9, 0.9, 0.9, 1.0)
	var pw := float(piece.texture.get_width()) * sc
	var ph := float(piece.texture.get_height()) * sc
	var last_x := _last_x_at_max_bottom(_mid1_pieces, piece, _mid1_holder)
	var px := _pick_x_float(pw, last_x, 0.75)
	var lo_g := _max_bottom_global_excluding(_mid1_pieces, piece, _mid1_holder)
	# Java recycle path uses height*0.75f .. height
	var want_bottom_g := lo_g + randf_range(ph * 0.75, ph) / _density
	_place_debris_with_bottom_at_global(piece, px, want_bottom_g)


func _recycle_mid2(piece: TextureRect) -> void:
	var sc := _layout_scale() * randf_range(MID2_DEBRIS_SCALE_MIN, MID2_DEBRIS_SCALE_MAX)
	piece.texture = _tex_large[_pick_mid_frame()]
	piece.scale = Vector2(sc, sc)
	piece.rotation = deg_to_rad(randf_range(-20.0, 20.0))
	piece.modulate = Color.WHITE
	var pw := float(piece.texture.get_width()) * sc
	var ph := float(piece.texture.get_height()) * sc
	var last_x := _last_x_at_max_bottom(_mid2_pieces, piece, _mid2_holder)
	var px := _pick_x_float(pw, last_x, 0.75)
	var lo_g := _max_bottom_global_excluding(_mid2_pieces, piece, _mid2_holder)
	var want_bottom_g := lo_g + randf_range(ph * 0.5, ph) / _density
	_place_debris_with_bottom_at_global(piece, px, want_bottom_g)


func _recycle_small_close(piece: TextureRect) -> void:
	var sc := _layout_scale() * randf_range(2.0, 2.5)
	piece.texture = _tex_small[_pick_small_frame()]
	piece.scale = Vector2(sc, sc)
	piece.rotation = deg_to_rad(randf_range(-20.0, 20.0))
	piece.modulate = Color.WHITE
	var pw := float(piece.texture.get_width()) * sc
	var ph := float(piece.texture.get_height()) * sc
	var last_x := _last_x_at_max_bottom(_small_close_pieces, piece, _small_close_holder)
	var px := _pick_x_float(pw, last_x, 1.0)
	var lo_g := _max_bottom_global_excluding(_small_close_pieces, piece, _small_close_holder)
	var want_bottom_g := lo_g + randf_range(ph * 0.5, ph) / _density
	_place_debris_with_bottom_at_global(piece, px, want_bottom_g)


func _scatter_initial() -> void:
	if _tex_large.is_empty() or _tex_small.is_empty():
		return
	_scatter_clusters(_cluster_far_pieces, true)
	_scatter_clusters(_cluster_pieces, false)
	_scatter_smalls(_small_far_pieces, true)
	_scatter_mids(_mid1_pieces, MID1_DEBRIS_SCALE_MIN, MID1_DEBRIS_SCALE_MAX, Color(0.9, 0.9, 0.9, 1.0))
	_scatter_mids(_mid2_pieces, MID2_DEBRIS_SCALE_MIN, MID2_DEBRIS_SCALE_MAX, Color.WHITE)
	_scatter_smalls_close(_small_close_pieces)


func _scatter_clusters(pieces: Array[TextureRect], is_far: bool) -> void:
	for piece in pieces:
		piece.texture = _tex_cluster[randi() % 2]
		var sc := _layout_scale() * (0.5 if is_far else 1.0)
		piece.scale = Vector2(sc, sc)
		piece.rotation = deg_to_rad(randf_range(-20.0, 20.0))
		piece.modulate = Color(0.5, 0.5, 0.5, 1.0) if is_far else Color(0.75, 0.75, 0.75, 1.0)
		var pw := float(piece.texture.get_width()) * sc
		var ph := float(piece.texture.get_height()) * sc
		var px := randf_range(-pw / 3.0, _w - 2.0 * pw / 3.0)
		var holder := piece.get_parent() as Control
		var want_bottom_g := holder.get_global_rect().position.y + randf_range(0.15 * _h, 1.75 * _h) + ph * 0.5
		_place_debris_with_bottom_at_global(piece, px, want_bottom_g)


func _scatter_smalls(pieces: Array[TextureRect], _is_far: bool) -> void:
	for piece in pieces:
		piece.texture = _tex_small[_pick_small_frame()]
		var sc := _layout_scale() * randf_range(0.75, 1.25)
		piece.scale = Vector2(sc, sc)
		piece.rotation = deg_to_rad(randf_range(-20.0, 20.0))
		piece.modulate = Color(0.8, 0.8, 0.8, 1.0)
		var pw := float(piece.texture.get_width()) * sc
		var ph := float(piece.texture.get_height()) * sc
		var px := randf_range(pw / 3.0, _w - 4.0 * pw / 3.0)
		var holder := piece.get_parent() as Control
		var want_bottom_g := holder.get_global_rect().position.y + randf_range(0.15 * _h, 1.75 * _h) + ph * 0.5
		_place_debris_with_bottom_at_global(piece, px, want_bottom_g)


func _scatter_mids(pieces: Array[TextureRect], lo: float, hi: float, modu: Color) -> void:
	for piece in pieces:
		piece.texture = _tex_large[_pick_mid_frame()]
		var sc := _layout_scale() * randf_range(lo, hi)
		piece.scale = Vector2(sc, sc)
		piece.rotation = deg_to_rad(randf_range(-20.0, 20.0))
		piece.modulate = modu
		var pw := float(piece.texture.get_width()) * sc
		var ph := float(piece.texture.get_height()) * sc
		var px := randf_range(-pw / 3.0, _w - 2.0 * pw / 3.0)
		var holder := piece.get_parent() as Control
		var want_bottom_g := holder.get_global_rect().position.y + randf_range(0.15 * _h, 1.75 * _h) + ph * 0.5
		_place_debris_with_bottom_at_global(piece, px, want_bottom_g)


func _scatter_smalls_close(pieces: Array[TextureRect]) -> void:
	for piece in pieces:
		piece.texture = _tex_small[_pick_small_frame()]
		var sc := _layout_scale() * randf_range(2.0, 2.5)
		piece.scale = Vector2(sc, sc)
		piece.rotation = deg_to_rad(randf_range(-20.0, 20.0))
		piece.modulate = Color.WHITE
		var pw := float(piece.texture.get_width()) * sc
		var ph := float(piece.texture.get_height()) * sc
		var px := randf_range(-pw / 3.0, _w - 2.0 * pw / 3.0)
		var holder := piece.get_parent() as Control
		var want_bottom_g := holder.get_global_rect().position.y + randf_range(0.15 * _h, 1.75 * _h) + ph * 0.5
		_place_debris_with_bottom_at_global(piece, px, want_bottom_g)


func _build_chrome() -> void:
	_banner = _mk_tex_rect(_title_tex)
	_chrome.add_child(_banner)
	_glow = _mk_tex_rect(_glow_tex)
	_glow.material = CanvasItemMaterial.new()
	_glow.material.blend_mode = CanvasItemMaterial.BLEND_MODE_ADD
	_chrome.add_child(_glow)
	_torch_l = _mk_tex_rect(_tex_fireball[0])
	_torch_r = _mk_tex_rect(_tex_fireball[0])
	_torch_r.flip_h = true
	_chrome.add_child(_torch_l)
	_chrome.add_child(_torch_r)


func _mk_tex_rect(tex: Texture2D) -> TextureRect:
	var trect := TextureRect.new()
	trect.texture = tex
	trect.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	trect.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	trect.stretch_mode = TextureRect.STRETCH_KEEP
	trect.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return trect


func _sync_texrect_min_from_texture(trect: TextureRect) -> void:
	if trect.texture == null:
		return
	trect.custom_minimum_size = Vector2(
		float(trect.texture.get_width()),
		float(trect.texture.get_height())
	)
	trect.reset_size()


func _rebuild_arch_layer() -> void:
	if _arch_holder == null:
		return
	for ch in _arch_holder.get_children():
		_arch_holder.remove_child(ch)
		ch.free()
	_arch_pieces.clear()
	if _tex_arch.is_empty() or _w < 1.0 or _h < 1.0:
		return
	_init_arch_chances()
	var sc := _layout_scale()
	var th0 := float(_tex_arch[0].get_height())
	var tw0 := float(_tex_arch[0].get_width())
	var row_stride := th0 * sc
	var bottom := 0.0
	while bottom < _h + row_stride * 2.0:
		if _arch_pieces.size() >= ARCH_PIECES_MAX:
			return
		var left := -5.0 + (-33.334 * float(randi_range(1, 9)) * sc)
		while left < _w:
			if _arch_pieces.size() >= ARCH_PIECES_MAX:
				return
			var arch := _mk_debris_rect()
			arch.texture = _tex_arch[_pick_arch_frame()]
			arch.scale = Vector2(sc, sc)
			_sync_texrect_min_from_texture(arch)
			arch.position = Vector2(left, bottom - 5.0 * sc)
			_arch_holder.add_child(arch)
			_arch_pieces.append(arch)
			left += tw0 * sc - 9.0 * sc
		bottom += row_stride


func _update_arch_layer(shift: float) -> void:
	if _arch_pieces.is_empty() or _tex_arch.is_empty():
		return
	var sc := _layout_scale()
	var th0 := float(_tex_arch[0].get_height())
	var tw0 := float(_tex_arch[0].get_width())
	var row_stride := th0 * sc

	for arch in _arch_pieces:
		arch.position.y -= shift

	var stack_bottom := 0.0
	var to_move: Array[TextureRect] = []
	for arch in _arch_pieces:
		if arch.texture == null:
			continue
		var ah := float(arch.texture.get_height()) * sc
		if arch.position.y + ah < 0.0:
			to_move.append(arch)
		else:
			stack_bottom = maxf(stack_bottom, arch.position.y + ah)

	for arch in to_move:
		arch.texture = _tex_arch[_pick_arch_frame()]
		arch.scale = Vector2(sc, sc)
		_sync_texrect_min_from_texture(arch)
		arch.position.y = stack_bottom - 5.0 * sc

	if not to_move.is_empty():
		stack_bottom += row_stride

	var fill_guard := 0
	while stack_bottom < _h and _arch_pieces.size() < ARCH_PIECES_MAX and fill_guard < 16:
		fill_guard += 1
		var left := -5.0 + (-33.334 * float(randi_range(1, 9)) * sc)
		var placed := false
		while left < _w and _arch_pieces.size() < ARCH_PIECES_MAX:
			var arch_n := _mk_debris_rect()
			arch_n.texture = _tex_arch[_pick_arch_frame()]
			arch_n.scale = Vector2(sc, sc)
			_sync_texrect_min_from_texture(arch_n)
			arch_n.position = Vector2(left, stack_bottom - 5.0 * sc)
			_arch_holder.add_child(arch_n)
			_arch_pieces.append(arch_n)
			left += tw0 * sc - 9.0 * sc
			placed = true
		if not placed:
			break
		stack_bottom += row_stride


func _apply_layout() -> void:
	_w = float(size.x) if size.x > 0 else float(get_viewport_rect().size.x)
	_h = float(size.y) if size.y > 0 else float(get_viewport_rect().size.y)
	if _w < 1.0 or _h < 1.0:
		return
	_update_density()
	_rebuild_arch_layer()
	_init_tile_chances()
	_scatter_initial()
	_layout_chrome()


func _layout_chrome() -> void:
	if _banner == null or _banner.texture == null:
		return
	var s := TITLE_CHROME_SCALE
	var tw0 := float(_banner.texture.get_width())
	var th0 := float(_banner.texture.get_height())
	var tw := tw0 * s
	var th := th0 * s

	_sync_texrect_min_from_texture(_banner)
	_banner.scale = Vector2(s, s)
	var band := TITLE_CHROME_TOP_BAND_PORTRAIT if _h > _w * 1.05 else TITLE_CHROME_TOP_BAND
	var region_h := maxf(th, _h * band)
	_banner.position = Vector2((_w - tw) * 0.5, (region_h - th) * 0.5 + 6.0)

	var gw0 := float(_glow_tex.get_width())
	var gw := gw0 * s
	if _glow:
		_sync_texrect_min_from_texture(_glow)
		_glow.scale = Vector2(s, s)
		_glow.position = _banner.position + Vector2((tw - gw) * 0.5, 0.0)

	var ox := 34.0 * s
	var oy := 38.0 * s
	_layout_torch(_torch_l, _banner.position.x + ox, _banner.position.y + oy, s)
	_layout_torch(_torch_r, _banner.position.x + tw - ox, _banner.position.y + oy, s)


func _layout_torch(torch_rect: TextureRect, cx: float, cy: float, torch_scale: float) -> void:
	if torch_rect.texture == null:
		return
	_sync_texrect_min_from_texture(torch_rect)
	torch_rect.scale = Vector2(torch_scale, torch_scale)
	var fw := float(torch_rect.texture.get_width()) * torch_scale
	var fh := float(torch_rect.texture.get_height()) * torch_scale
	torch_rect.position = Vector2(cx - fw / 2.0, cy - fh)


func _process(delta: float) -> void:
	_glow_phase += delta
	if _glow:
		_glow.modulate.a = maxf(0.0, sin(_glow_phase))
	_torch_frame_l = fmod(_torch_frame_l + delta * FIREBALL_FPS, 24.0)
	_torch_frame_r = fmod(_torch_frame_r + delta * FIREBALL_FPS, 24.0)
	if _torch_l:
		_torch_l.texture = _tex_fireball[int(_torch_frame_l) % 24]
	if _torch_r:
		_torch_r.texture = _tex_fireball[int(_torch_frame_r) % 24]

	if _w < 1.0 or _h < 1.0:
		return

	var sc := _h / 450.0
	var sh := delta * SCROLL_BASE * sc
	_update_arch_layer(sh)

	if _tex_large.is_empty():
		return

	var s_cf := sh * 1.33
	var s_c := s_cf * 1.5
	var s_sf := s_c * 1.33
	var s_m1 := s_sf * 1.33
	var s_m2 := s_m1 * 1.33
	var s_sc := s_m2 * 1.33

	for piece in _cluster_far_pieces:
		piece.position.y -= s_cf
		if _piece_fully_past_top(piece):
			_recycle_cluster_far(piece)
	for piece in _cluster_pieces:
		piece.position.y -= s_c
		if _piece_fully_past_top(piece):
			_recycle_cluster(piece)
	for piece in _small_far_pieces:
		piece.position.y -= s_sf
		if _piece_fully_past_top(piece):
			_recycle_small_far(piece)
	for piece in _mid1_pieces:
		piece.position.y -= s_m1
		if _piece_fully_past_top(piece):
			_recycle_mid1(piece)
	for piece in _mid2_pieces:
		piece.position.y -= s_m2
		if _piece_fully_past_top(piece):
			_recycle_mid2(piece)
	for piece in _small_close_pieces:
		piece.position.y -= s_sc
		if _piece_fully_past_top(piece):
			_recycle_small_close(piece)
