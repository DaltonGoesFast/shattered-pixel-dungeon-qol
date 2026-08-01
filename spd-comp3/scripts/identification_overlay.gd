extends Control

const _ItemIcons := preload("res://scripts/companion_item_icons.gd")
const _ScrollArt := preload("res://scripts/scroll_command_art.gd")

## Renders potion/scroll identification from WebSocket snapshots using
## Rune art: item_sheet (preferred) then potions/scrolls; known icons: icons_sheet + item_sheet + legacy itemicons.

const _POT_DIR := "res://assets/potions/"
const _ITEM_SHEET := "res://assets/item_sheet/"

const _POTION_KEYS: PackedStringArray = ["potions", "potion_list", "identified_potions"]
const _SCROLL_KEYS: PackedStringArray = ["scrolls", "scroll_list", "identified_scrolls"]

@onready var _pot_flow: FlowContainer = $VBox/PotionsFlow
@onready var _scroll_flow: FlowContainer = $VBox/ScrollsFlow

var _last_snapshot: Dictionary = {}
var _scroll_class_rune_map: Dictionary = {}  ## Built from non-exotic scroll rows only; used for exotic stem + art.
var _tex_cache: Dictionary = {}  ## String path -> Texture2D


func _ready() -> void:
	GameWebSocketClient.snapshot_received.connect(_on_snapshot)
	get_viewport().size_changed.connect(_apply_panel_layout)
	CompanionConfig.settings_saved.connect(_on_settings_saved)
	_apply_panel_layout()
	visible = CompanionConfig.id_overlay_enabled


func _unhandled_key_input(event: InputEvent) -> void:
	if event is InputEventKey and event.pressed and not event.echo:
		if event.keycode == KEY_F5:
			CompanionConfig.load_settings()
			visible = CompanionConfig.id_overlay_enabled
			_apply_panel_layout()
			if not _last_snapshot.is_empty():
				_on_snapshot(_last_snapshot)
			else:
				_refresh_cell_backgrounds()
			get_viewport().set_input_as_handled()


func _on_settings_saved() -> void:
	visible = CompanionConfig.id_overlay_enabled
	_apply_panel_layout()
	if not _last_snapshot.is_empty():
		_on_snapshot(_last_snapshot)
	else:
		_refresh_cell_backgrounds()


func _apply_panel_layout() -> void:
	var canvas: Vector2 = CompanionConfig.layout_canvas_size(self)
	CompanionConfig.apply_id_zone_layout(self, canvas)
	_pot_flow.add_theme_constant_override("h_separation", CompanionConfig.id_flow_h_separation_px)
	_pot_flow.add_theme_constant_override("v_separation", CompanionConfig.id_flow_h_separation_px)
	_scroll_flow.add_theme_constant_override("h_separation", CompanionConfig.id_flow_h_separation_px)
	_scroll_flow.add_theme_constant_override("v_separation", CompanionConfig.id_flow_h_separation_px)
	var vbox: VBoxContainer = $VBox
	vbox.add_theme_constant_override("separation", CompanionConfig.id_block_separation_px)


func _on_snapshot(data: Dictionary) -> void:
	if not CompanionConfig.id_overlay_enabled:
		visible = false
		return
	_scroll_class_rune_map = _ScrollArt.build_scroll_class_to_rune_stem_map(data)
	_last_snapshot = data.duplicate(true)
	visible = true
	var id_root: Dictionary = _identification_root(data)
	var pots: Array = _array_from(id_root, _POTION_KEYS)
	var scrolls: Array = _array_from(id_root, _SCROLL_KEYS)
	if pots.is_empty() and id_root != data:
		pots = _array_from(data, _POTION_KEYS)
	if scrolls.is_empty() and id_root != data:
		scrolls = _array_from(data, _SCROLL_KEYS)
	_sort_id_items(pots, false)
	_sort_id_items(scrolls, true)
	_rebuild_flow(_pot_flow, pots, false)
	_rebuild_flow(_scroll_flow, scrolls, true)


func _identification_root(data: Dictionary) -> Dictionary:
	if data.get("identification") is Dictionary:
		return data["identification"]
	return data


func _array_from(container: Dictionary, keys: PackedStringArray) -> Array:
	for k in keys:
		if container.has(k) and container[k] is Array:
			return container[k].duplicate()
	return []


func _sort_id_items(items: Array, for_scroll: bool) -> void:
	if for_scroll:
		items.sort_custom(
			func(a: Dictionary, b: Dictionary) -> bool:
				return _ScrollArt.rune_stem_from_identification_item(a) < _ScrollArt.rune_stem_from_identification_item(b)
		)
	else:
		items.sort_custom(
			func(a: Dictionary, b: Dictionary) -> bool:
				return _potion_liquid_stem(a) < _potion_liquid_stem(b)
		)


func _item_rune(item: Dictionary) -> String:
	for k in ["rune_name", "runeName", "rune", "color", "potion_color", "scroll_rune", "scrollRune", "glyph", "scroll_glyph"]:
		if item.has(k):
			return str(item[k]).strip_edges()
	return ""


## Flask art: prefer **liquid** keys before generic [code]rune[/code] (serializer order can otherwise load [code]assets/potions/*.png[/code] fallbacks even when [code]item_sheet/potion_*.png[/code] exists).
func _potion_liquid_stem(item: Dictionary) -> String:
	for k in ["potion_color", "color", "liquidColor", "liquid_color"]:
		if item.has(k):
			var s: String = str(item[k]).strip_edges()
			if not s.is_empty():
				var stem: String = _normalize_potion_color_stem(s)
				if not stem.is_empty():
					return stem
	for k in ["rune_name", "runeName", "rune", "glyph", "scroll_glyph", "scroll_rune", "scrollRune"]:
		if item.has(k):
			var s2: String = str(item[k]).strip_edges()
			if not s2.is_empty():
				var stem2: String = _normalize_potion_color_stem(s2)
				if not stem2.is_empty():
					return stem2
	return ""


func _normalize_potion_color_stem(s: String) -> String:
	var t: String = s.strip_edges().to_lower()
	## Payloads sometimes use these for [PotionOfExperience]; that potion's **flask** is still one of
	## crimson/amber/golden/… from [code]Potion.handler.label[/code], never a separate "experience" liquid.
	if t == "exp" or t == "experience" or t == "xp":
		return ""
	return t


func _item_class_raw(item: Dictionary) -> String:
	for k in ["class_name", "className", "item_class", "itemType", "type"]:
		if item.has(k):
			var s: String = str(item[k]).strip_edges()
			if k == "type" and s.to_lower() == "scroll":
				continue
			if k == "type" and s.to_lower() == "potion":
				continue
			return s
	return ""


func _item_class(item: Dictionary) -> String:
	var raw := _item_class_raw(item)
	if raw.is_empty():
		return ""
	return _ScrollArt.normalize_scroll_item_class(raw)


func _item_known(item: Dictionary) -> bool:
	for k in ["is_known", "isKnown", "known", "identified"]:
		if item.has(k):
			var v: Variant = item[k]
			if v is bool:
				return v
			if v is int or v is float:
				return bool(v)
			var s := str(v).strip_edges().to_lower()
			return s == "1" or s == "true" or s == "yes"
	return false


func _rebuild_flow(flow: FlowContainer, items: Array, is_scroll: bool) -> void:
	for c in flow.get_children():
		c.free()
	for item in items:
		if item is Dictionary:
			var cell := _make_cell(item as Dictionary, is_scroll)
			flow.add_child(cell)


func _refresh_cell_backgrounds() -> void:
	var c: Color = CompanionConfig.icon_cell_background_color
	for flow in [_pot_flow, _scroll_flow]:
		for cell in flow.get_children():
			if cell.get_child_count() > 0:
				var ch: Node = cell.get_child(0)
				if ch is ColorRect:
					(ch as ColorRect).color = c


func _make_cell(item: Dictionary, is_scroll: bool) -> Control:
	var cw: int = maxi(16, CompanionConfig.id_cell_width_px)
	var ch: int = maxi(16, CompanionConfig.id_cell_height_px)
	var pad: int = maxi(0, CompanionConfig.id_cell_padding_px)
	var root := Control.new()
	root.custom_minimum_size = Vector2(cw, ch)
	root.mouse_filter = Control.MOUSE_FILTER_IGNORE

	var bg := ColorRect.new()
	bg.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	bg.color = CompanionConfig.icon_cell_background_color
	bg.mouse_filter = Control.MOUSE_FILTER_IGNORE
	root.add_child(bg)

	var margin := MarginContainer.new()
	margin.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	margin.add_theme_constant_override("margin_left", pad)
	margin.add_theme_constant_override("margin_top", pad)
	margin.add_theme_constant_override("margin_right", pad)
	margin.add_theme_constant_override("margin_bottom", pad)
	margin.mouse_filter = Control.MOUSE_FILTER_IGNORE
	root.add_child(margin)

	var stem: String = (
		_ScrollArt.rune_stem_from_identification_item(item)
		if is_scroll
		else _potion_liquid_stem(item)
	)
	var rune_tex: Texture2D
	if is_scroll:
		if stem.is_empty():
			var snap: Dictionary = _last_snapshot
			if snap.is_empty():
				snap = GameWebSocketClient.last_game_snapshot
			var mmap: Dictionary = _scroll_class_rune_map
			if mmap.is_empty() and not snap.is_empty():
				mmap = _ScrollArt.build_scroll_class_to_rune_stem_map(snap)
			stem = _ScrollArt.resolve_scroll_rune_stem(item, snap, mmap)
		if stem.is_empty():
			var snap2: Dictionary = GameWebSocketClient.last_game_snapshot
			if not snap2.is_empty():
				stem = _ScrollArt.resolve_scroll_rune_stem(
					item,
					snap2,
					_ScrollArt.build_scroll_class_to_rune_stem_map(snap2),
				)
		rune_tex = _ScrollArt.load_scroll_rune(stem, _ScrollArt.scroll_item_is_exotic(item))
	else:
		rune_tex = _load_potion_rune(stem)

	var rune_tr := TextureRect.new()
	rune_tr.name = "Rune"
	rune_tr.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	rune_tr.mouse_filter = Control.MOUSE_FILTER_IGNORE
	rune_tr.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	if rune_tex:
		rune_tr.texture = rune_tex
	rune_tr.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	rune_tr.size_flags_vertical = Control.SIZE_EXPAND_FILL
	## Item-sheet cells are ~12×14 / 15×14. Fallback files ([code]potions/crimson.png[/code], [code]scrolls/KAUNAN.png[/code]) are 160² — same “centered contain” mode makes them look smaller than neighbors.
	rune_tr.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	var tw: int = rune_tex.get_width() if rune_tex else 0
	var th: int = rune_tex.get_height() if rune_tex else 0
	var big_fallback: bool = tw > 24 or th > 24
	rune_tr.stretch_mode = (
		TextureRect.STRETCH_KEEP_ASPECT_COVERED
		if big_fallback
		else TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	)
	margin.add_child(rune_tr)

	var known := _item_known(item)
	if known:
		var cls := _item_class(item)
		var icon_tex: Texture2D = _load_item_icon(cls)
		if icon_tex:
			var inner: int = mini(cw - 2 * pad, ch - 2 * pad)
			var frac: float = clampf(CompanionConfig.id_known_icon_fraction, 0.15, 0.85)
			var icon_sz: int = maxi(8, int(round(float(inner) * frac)))
			var icon := TextureRect.new()
			icon.name = "Icon"
			icon.mouse_filter = Control.MOUSE_FILTER_IGNORE
			icon.texture = icon_tex
			icon.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
			icon.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
			icon.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
			icon.set_anchor(SIDE_LEFT, 1.0)
			icon.set_anchor(SIDE_TOP, 0.0)
			icon.set_anchor(SIDE_RIGHT, 1.0)
			icon.set_anchor(SIDE_BOTTOM, 0.0)
			var edge := float(maxi(1, pad)) * 0.35
			icon.offset_left = float(-icon_sz) - edge * 0.5
			icon.offset_right = edge * 0.5
			icon.offset_top = edge
			icon.offset_bottom = edge + float(icon_sz)
			root.add_child(icon)
	return root


func _stem_variants(stem: String) -> Array[String]:
	var t := stem.strip_edges()
	var out: Array[String] = []
	if t.is_empty():
		return out
	out.append(t)
	var lower := t.to_lower()
	var upper := t.to_upper()
	if lower != t:
		out.append(lower)
	if upper != t:
		out.append(upper)
	return out


func _load_tex(dir_path: String, stem: String) -> Texture2D:
	for v in _stem_variants(stem):
		var p: String = dir_path + v + ".png"
		if ResourceLoader.exists(p):
			if _tex_cache.has(p):
				return _tex_cache[p]
			var tex: Texture2D = load(p)
			_tex_cache[p] = tex
			return tex
	return null


func _load_potion_rune(stem: String) -> Texture2D:
	for v in _stem_variants(stem):
		var p: String = _ITEM_SHEET + "potion_" + v.to_lower() + ".png"
		if ResourceLoader.exists(p):
			if _tex_cache.has(p):
				return _tex_cache[p]
			var tex: Texture2D = load(p)
			_tex_cache[p] = tex
			return tex
	return _load_tex(_POT_DIR, stem)


func _load_item_icon(item_class: String) -> Texture2D:
	return _ItemIcons.texture_for_item_class(item_class)


## After OBS shows this layer again, re-apply [member CompanionConfig.id_overlay_enabled] and last snapshot.
func sync_visibility_after_obs() -> void:
	if not CompanionConfig.id_overlay_enabled:
		visible = false
		return
	if _last_snapshot.is_empty():
		visible = false
	else:
		_on_snapshot(_last_snapshot)
