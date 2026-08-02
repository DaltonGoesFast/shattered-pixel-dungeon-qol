extends Control

const _ScrollArt := preload("res://scripts/scroll_command_art.gd")
const _MobArt := preload("res://scripts/mob_command_art.gd")
const _SpdUi := preload("res://scripts/spd_ui_art.gd")

@onready var _slot: Control = $AlertSlot
@onready var _row: HBoxContainer = $AlertSlot/AlertRow
@onready var _icons_row: HBoxContainer = $AlertSlot/AlertRow/PanelContainer/MarginContainer/AlertInner/CommandIcons
@onready var _icon_cell: Control = $AlertSlot/AlertRow/PanelContainer/MarginContainer/AlertInner/CommandIcons/CommandIconCell
@onready var _icon_cell_bg: ColorRect = $AlertSlot/AlertRow/PanelContainer/MarginContainer/AlertInner/CommandIcons/CommandIconCell/CellBg
@onready var _icon_margin: MarginContainer = $AlertSlot/AlertRow/PanelContainer/MarginContainer/AlertInner/CommandIcons/CommandIconCell/Margin
@onready var _icon_rune: TextureRect = $AlertSlot/AlertRow/PanelContainer/MarginContainer/AlertInner/CommandIcons/CommandIconCell/Margin/RuneIcon
@onready var _icon_item: TextureRect = $AlertSlot/AlertRow/PanelContainer/MarginContainer/AlertInner/CommandIcons/CommandIconCell/ItemIcon
@onready var _panel: PanelContainer = $AlertSlot/AlertRow/PanelContainer
@onready var _title: Label = $AlertSlot/AlertRow/PanelContainer/MarginContainer/AlertInner/VBox/Title
@onready var _subtitle: Label = $AlertSlot/AlertRow/PanelContainer/MarginContainer/AlertInner/VBox/Subtitle
@onready var _fx: Node2D = $FxAnchor
@onready var _particles: CPUParticles2D = $FxAnchor/CPUParticles2D

var _queue: Array[Dictionary] = []
var _busy: bool = false
## { "request_id", "username", "headline_hint", "command", "monster_hint", "scroll_hint", "slot", "amount", "msec" }
var _pending_tracker: Array[Dictionary] = []
var _mob_idle_frames: Array[Texture2D] = []
var _mob_anim_idx: int = 0
var _mob_anim_accum: float = 0.0
## "none" | "scroll" | "mob" — scroll matches ID strip (rune + corner item).
var _active_command_icon_layout: String = "none"


func _ready() -> void:
	GameWebSocketClient.command_result.connect(_on_command_result)
	StreamerBotUdp.command_attempt.connect(_on_streamerbot_command)
	get_node("/root/SpawnResultFileBridge").spawn_result_file_parsed.connect(
		_on_spawn_result_file_parsed
	)
	get_viewport().size_changed.connect(_on_viewport_size_changed)
	CompanionConfig.settings_saved.connect(_on_settings_saved)
	_row.modulate.a = 0.0
	_row.scale = Vector2.ONE
	_icon_rune.texture = null
	_icon_rune.visible = false
	_icon_item.texture = null
	_icon_item.visible = false
	_icons_row.visible = false
	_title.text = ""
	_subtitle.text = ""
	_icon_rune.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	_icon_item.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	_apply_command_icon_sizes()
	_configure_particles()
	_apply_alert_chrome()
	_apply_alert_layout()
	_apply_text_alignment()
	_apply_alert_font_sizes()
	_panel.pivot_offset = _panel.size * 0.5
	_row.pivot_offset = _row.size * 0.5
	set_process(true)


func _unhandled_input(event: InputEvent) -> void:
	if event is InputEventKey and event.pressed and not event.echo:
		if event.keycode == KEY_F5:
			CompanionConfig.load_settings()
			_apply_alert_chrome()
			_apply_alert_layout()
			_apply_text_alignment()
			_apply_alert_font_sizes()
			_apply_command_icon_sizes()
			get_viewport().set_input_as_handled()


func _on_viewport_size_changed() -> void:
	_apply_alert_layout()


func _on_settings_saved() -> void:
	_apply_alert_chrome()
	_apply_alert_layout()
	_apply_text_alignment()
	_apply_alert_font_sizes()
	_apply_command_icon_sizes()


func _process(delta: float) -> void:
	var gr: Rect2 = _row.get_global_rect()
	if gr.size.x > 1.0 and gr.size.y > 1.0:
		_fx.global_position = gr.get_center()
	else:
		_fx.global_position = get_viewport_rect().get_center()
	if _mob_idle_frames.size() > 1 and _icon_item.visible:
		_mob_anim_accum += delta
		var fps: float = clampf(CompanionConfig.alert_mob_idle_anim_fps, 0.25, 30.0)
		var spf: float = 1.0 / fps
		while _mob_anim_accum >= spf:
			_mob_anim_accum -= spf
			_mob_anim_idx = (_mob_anim_idx + 1) % _mob_idle_frames.size()
			_icon_item.texture = _mob_idle_frames[_mob_anim_idx]


func _apply_text_alignment() -> void:
	var a: String = CompanionConfig.alert_text_align.to_lower()
	var h: HorizontalAlignment = (
		HORIZONTAL_ALIGNMENT_LEFT if a == "left" else HORIZONTAL_ALIGNMENT_CENTER
	)
	_title.horizontal_alignment = h
	_subtitle.horizontal_alignment = h


func _apply_alert_font_sizes() -> void:
	if _title == null or _subtitle == null:
		return
	var ts: int = maxi(6, CompanionConfig.alert_title_font_size_px)
	var ss: int = maxi(6, CompanionConfig.alert_subtitle_font_size_px)
	_title.add_theme_font_size_override("font_size", ts)
	_subtitle.add_theme_font_size_override("font_size", ss)


func _apply_alert_chrome() -> void:
	if _panel == null:
		return
	var style_id := str(CompanionConfig.alert_chrome_style)
	var scale := clampf(CompanionConfig.alert_chrome_scale, 0.5, 4.0)
	var sb: StyleBoxTexture = _SpdUi.chrome_style(style_id, scale)
	if sb == null or sb.texture == null:
		sb = _SpdUi.chrome_style_toast(scale)
	_panel.add_theme_stylebox_override("panel", sb)


func _apply_alert_layout() -> void:
	if _slot == null:
		return
	var canvas: Vector2 = CompanionConfig.layout_canvas_size(self)
	CompanionConfig.apply_alert_zone_layout(_slot, canvas)
	# Keep toast row inside the zone (scene had a fixed 140px height that could clip large fonts).
	if _row:
		_slot.clip_contents = false
		_row.set_anchors_preset(Control.PRESET_TOP_WIDE)
		_row.offset_left = 0.0
		_row.offset_top = 0.0
		_row.offset_right = 0.0
		_row.offset_bottom = maxf(140.0, _slot.size.y)
	_apply_command_icon_sizes()
	if _panel:
		_panel.pivot_offset = _panel.size * 0.5
	if _row:
		_row.pivot_offset = _row.size * 0.5


func _apply_command_icon_sizes() -> void:
	if _icon_cell == null or _icon_rune == null or _icon_item == null:
		return
	var s: int = maxi(16, CompanionConfig.alert_command_icon_size_px)
	if _active_command_icon_layout == "mob" or _active_command_icon_layout == "scroll":
		_icon_cell.custom_minimum_size = Vector2(float(s), float(s))
		match _active_command_icon_layout:
			"mob":
				_apply_mob_command_icon_geometry()
			"scroll":
				_apply_scroll_command_icon_geometry()
	else:
		_icon_cell.custom_minimum_size = Vector2.ZERO
	if _icon_cell_bg != null:
		_icon_cell_bg.color = CompanionConfig.icon_cell_background_color
func _apply_scroll_command_icon_geometry() -> void:
	if _icon_margin == null or _icon_item == null:
		return
	_icon_margin.visible = true
	var pad: int = maxi(0, CompanionConfig.id_cell_padding_px)
	_icon_margin.add_theme_constant_override("margin_left", pad)
	_icon_margin.add_theme_constant_override("margin_top", pad)
	_icon_margin.add_theme_constant_override("margin_right", pad)
	_icon_margin.add_theme_constant_override("margin_bottom", pad)
	var s: int = maxi(16, CompanionConfig.alert_command_icon_size_px)
	var inner: int = mini(s - 2 * pad, s - 2 * pad)
	var frac: float = clampf(CompanionConfig.id_known_icon_fraction, 0.15, 0.85)
	var icon_sz: int = maxi(8, int(round(float(inner) * frac)))
	var edge: float = float(maxi(1, pad)) * 0.35
	_icon_item.custom_minimum_size = Vector2.ZERO
	_icon_item.set_anchor(SIDE_LEFT, 1.0)
	_icon_item.set_anchor(SIDE_TOP, 0.0)
	_icon_item.set_anchor(SIDE_RIGHT, 1.0)
	_icon_item.set_anchor(SIDE_BOTTOM, 0.0)
	_icon_item.offset_left = float(-icon_sz) - edge * 0.5
	_icon_item.offset_right = edge * 0.5
	_icon_item.offset_top = edge
	_icon_item.offset_bottom = edge + float(icon_sz)


func _apply_mob_command_icon_geometry() -> void:
	if _icon_margin == null or _icon_item == null:
		return
	_icon_margin.visible = false
	_icon_item.custom_minimum_size = Vector2.ZERO
	_icon_item.set_anchor(SIDE_LEFT, 0.0)
	_icon_item.set_anchor(SIDE_TOP, 0.0)
	_icon_item.set_anchor(SIDE_RIGHT, 1.0)
	_icon_item.set_anchor(SIDE_BOTTOM, 1.0)
	_icon_item.offset_left = 0.0
	_icon_item.offset_top = 0.0
	_icon_item.offset_right = 0.0
	_icon_item.offset_bottom = 0.0


func _configure_particles() -> void:
	_particles.emitting = false
	_particles.one_shot = true
	_particles.amount = 48
	_particles.lifetime = 0.85
	_particles.explosiveness = 0.95
	_particles.direction = Vector2(0, -1)
	_particles.spread = 120.0
	_particles.initial_velocity_min = 80.0
	_particles.initial_velocity_max = 180.0
	_particles.scale_amount_min = 2.0
	_particles.scale_amount_max = 5.0


func _on_command_result(success: bool, type_name: String, data: Dictionary) -> void:
	if type_name == "ping_result" and not CompanionConfig.show_ping_alerts:
		return
	if not success and not CompanionConfig.show_failed_command_alerts:
		return
	var row := _pop_pending_row_for_request(data.get("request_id", null))
	var who_key := str(row.get("username", "")).strip_edges()
	if who_key.is_empty():
		for k in ["username", "chatter", "user", "display_name", "login"]:
			if data.has(k):
				var v := str(data[k]).strip_edges()
				if v != "":
					who_key = v
					break
	var who := _spectator_label(who_key)
	var err: String = str(data.get("error", "Unknown error"))
	var headline := ""
	var subtitle := ""
	if success:
		headline = _narration_success(who, type_name, data, row)
	else:
		headline = _narration_failure(who, type_name, err)
	var kind := "confirm" if success else "error"
	var icon_rune: Texture2D = null
	var icon_item: Texture2D = null
	var mob_frames: Array[Texture2D] = []
	if type_name == "scroll_result":
		var pair: Dictionary = _ScrollArt.rune_and_item_for_scroll_result(
			data, row, GameWebSocketClient.last_game_snapshot
		)
		icon_rune = pair.get("rune", null) as Texture2D
		icon_item = pair.get("item", null) as Texture2D
	elif success and (type_name == "spawn_result" or type_name == "champion_result"):
		mob_frames = _mob_idle_frames_for_result(type_name, data, row)
		if mob_frames.size() > 0:
			icon_item = mob_frames[0]
	var cmd_name := ""
	if type_name.ends_with("_result"):
		cmd_name = type_name.trim_suffix("_result")
	_enqueue(
		{
			"kind": kind,
			"headline": headline,
			"subtitle": subtitle,
			"command": cmd_name,
			"monster_hint": str(row.get("monster_hint", "")).strip_edges(),
			"command_icon_rune": icon_rune,
			"command_icon_item": icon_item,
			"mob_idle_frames": mob_frames,
		}
	)


func _is_trivial_spawn_file_ack_headline(headline: String) -> bool:
	var h: String = headline.strip_edges().to_lower()
	if h.is_empty():
		return false
	return (
		h == "ok"
		or h == "success"
		or h == "true"
		or h == "1"
		or h == "done"
		or h == "yes"
	)


func _on_spawn_result_file_parsed(spawn_text: String, item_name: String, points: String) -> void:
	var headline: String = spawn_text.strip_edges()
	var iname: String = item_name.strip_edges()
	var pts: String = points.strip_edges()
	if headline.is_empty() and iname.is_empty():
		return
	## Streamer.bot / file bridge often writes `ok|anything|points` as an ack while the real result already
	## came from WebSocket. Skipping **only** when the middle segment was empty missed `ok|leftovers|123`,
	## which still flashed "ok" + "Points remaining" under rapid successive commands.
	if headline.is_empty():
		headline = iname
	if headline.is_empty():
		return
	if _is_trivial_spawn_file_ack_headline(headline):
		return
	var subtitle: String = ""
	if pts != "":
		subtitle = "Points remaining: %s" % pts
	var icon_rune: Texture2D = null
	var icon_item: Texture2D = null
	var mob_frames_sb: Array[Texture2D] = []
	if CompanionConfig.spawn_result_file_icons and iname != "":
		var pair2: Dictionary = _ScrollArt.rune_and_item_for_scroll_hint(
			iname, GameWebSocketClient.last_game_snapshot
		)
		icon_rune = pair2.get("rune", null) as Texture2D
		icon_item = pair2.get("item", null) as Texture2D
	else:
		mob_frames_sb = _MobArt.idle_frames_for_monster_label(iname)
		if mob_frames_sb.size() > 0:
			icon_item = mob_frames_sb[0]
	_enqueue(
		{
			"kind": "confirm",
			"headline": headline,
			"subtitle": subtitle,
			"command": "",
			"monster_hint": iname,
			"command_icon_rune": icon_rune,
			"command_icon_item": icon_item,
			"mob_idle_frames": mob_frames_sb,
		}
	)


func _spectator_label(raw_username: String) -> String:
	var u := raw_username.strip_edges()
	if u.begins_with("@"):
		u = u.substr(1)
	if u.is_empty():
		return "Someone"
	return u


func _narration_failure(who: String, _type_name: String, err: String) -> String:
	if who == "Someone":
		return err
	return "%s — %s" % [who, err]


func _narration_success(who: String, type_name: String, data: Dictionary, row: Dictionary) -> String:
	if type_name == "ping_result":
		var ver: String = str(data.get("version", "")).strip_edges()
		if ver != "":
			return "%s pinged the companion (%s)" % [who, ver]
		return "%s pinged the companion" % who
	if type_name == "spawn_result":
		var m := _readable_mob_name(_spawn_monster_label(data, row))
		if m.is_empty():
			m = "something"
		return "%s spawned %s" % [who, _with_article(m)]
	if type_name == "champion_result":
		var m := _readable_mob_name(
			str(data.get("monster", row.get("monster_hint", ""))).strip_edges()
		)
		if m.is_empty():
			m = _readable_mob_name(_monster_from_headline_hint(str(row.get("headline_hint", ""))))
		if m.is_empty():
			m = "champion"
		return "%s spawned a champion %s" % [who, m]
	if type_name == "gold_result":
		var amt_v: Variant = row.get("amount", null)
		if amt_v != null and str(amt_v).strip_edges() != "":
			return "%s dropped %s gold for you" % [who, str(amt_v)]
		return "%s dropped gold for you" % who
	if type_name == "gas_result":
		var g := str(data.get("gas_name", "gas")).strip_edges()
		return "%s released %s" % [who, g]
	if type_name == "curse_result":
		var slot := str(row.get("slot", "")).strip_edges().to_lower()
		var item := str(data.get("item_name", "")).strip_edges()
		if slot != "":
			if item != "":
				return "%s cursed your %s (%s)" % [who, slot, item]
			return "%s cursed your %s" % [who, slot]
		if item != "":
			return "%s cursed your gear (%s)" % [who, item]
		return "%s applied a curse" % who
	if type_name == "scroll_result":
		var s := str(data.get("scroll_name", "a scroll")).strip_edges()
		return "%s gave you %s" % [who, _with_article(s)]
	if type_name == "wand_result":
		var eff := str(data.get("effect_name", "an effect")).strip_edges()
		var rare := str(data.get("rarity", "")).strip_edges()
		if rare != "":
			return "%s triggered a cursed wand — %s (%s)" % [who, eff, rare]
		return "%s triggered a cursed wand — %s" % [who, eff]
	if type_name == "buff_result":
		var b := str(data.get("buff_name", "a buff")).strip_edges()
		return "%s gave you %s" % [who, _with_article(b)]
	if type_name == "debuff_result":
		var d := str(data.get("debuff_name", "a debuff")).strip_edges()
		return "%s hit you with %s" % [who, _with_article(d)]
	if type_name == "trap_result":
		var t := str(data.get("trap_name", "a trap")).strip_edges()
		return "%s placed %s" % [who, _with_article(t)]
	if type_name == "bomb_result":
		var b2 := str(data.get("bomb_name", "a bomb")).strip_edges()
		return "%s dropped %s" % [who, _with_article(b2)]
	if type_name == "transmute_result":
		var newn := str(data.get("item_name", "")).strip_edges()
		var oldn := str(
			data.get("original_item_name", data.get("original_name", data.get("from_item", "")))
		).strip_edges()
		if oldn != "" and newn != "":
			return "%s transmuted your %s into %s" % [who, oldn, newn]
		if newn != "":
			return "%s transmuted an item into %s" % [who, newn]
		return "%s transmuted an item" % who
	if type_name == "summon_bee_result":
		var ally := str(data.get("ally_name", "a bee")).strip_edges()
		return "%s summoned %s" % [who, _with_article(ally)]
	if type_name == "ward_result":
		var w := str(data.get("ward_name", "")).strip_edges()
		if w != "" and w.to_lower() != "ward":
			return "%s placed %s" % [who, _with_article(w)]
		return "%s placed a ward" % who
	if type_name == "heal_result":
		var h := str(data.get("buff_name", "")).strip_edges()
		if h != "":
			return "%s healed you (%s)" % [who, h]
		return "%s healed you" % who
	if type_name == "cleanse_result":
		var c := str(data.get("buff_name", "")).strip_edges()
		if c != "":
			return "%s cleansed you (%s)" % [who, c]
		return "%s cleansed you" % who
	if type_name == "dew_result":
		var d2 := str(data.get("item_name", "dew")).strip_edges()
		return "%s gave you %s" % [who, _with_article(d2)]
	if type_name == "corrupt_ally_result":
		var mob := str(data.get("mob_name", "an enemy")).strip_edges()
		return "%s corrupted an ally into %s" % [who, mob]
	if type_name == "hex_result":
		var hx := str(data.get("debuff_name", "hex")).strip_edges()
		return "%s hexed you with %s" % [who, hx]
	if type_name == "degrade_result":
		var dg := str(data.get("debuff_name", "misfortune")).strip_edges()
		return "%s degraded your gear (%s)" % [who, dg]
	if type_name == "sabotage_result":
		var sb := str(data.get("buff_name", "sabotage")).strip_edges()
		return "%s sabotaged you (%s)" % [who, sb]
	if type_name == "ring_of_wealth_result":
		var det := str(data.get("detail", "loot")).strip_edges()
		return "%s triggered Ring of Wealth — %s" % [who, det]
	return "%s — %s" % [who, _pretty_result_type(type_name)]


func _spawn_monster_label(data: Dictionary, row: Dictionary) -> String:
	for k in ["monster", "display", "entity", "spawn_name", "name", "mob"]:
		if data.has(k):
			var s := str(data[k]).strip_edges()
			if s != "":
				return s
	var mh := str(row.get("monster_hint", "")).strip_edges()
	if mh != "":
		return mh
	return _monster_from_headline_hint(str(row.get("headline_hint", "")))


func _mob_idle_frames_for_result(type_name: String, data: Dictionary, row: Dictionary) -> Array[Texture2D]:
	var label: String = ""
	if type_name == "spawn_result":
		label = _spawn_monster_label(data, row)
	elif type_name == "champion_result":
		label = str(data.get("monster", row.get("monster_hint", ""))).strip_edges()
		if label.is_empty():
			label = _monster_from_headline_hint(str(row.get("headline_hint", "")))
	return _MobArt.idle_frames_for_monster_label(label)


func _clear_mob_idle_anim() -> void:
	_mob_idle_frames.clear()
	_mob_anim_idx = 0
	_mob_anim_accum = 0.0


func _apply_mob_frames_from_item(item: Dictionary) -> void:
	_clear_mob_idle_anim()
	var v: Variant = item.get("mob_idle_frames", null)
	if v is Array:
		for el in v:
			if el is Texture2D:
				_mob_idle_frames.append(el)


## Best-effort pretty text for spawn ids from the game (e.g. sewer_crab → sewer crab).
func _readable_mob_name(s: String) -> String:
	return s.strip_edges().replace("_", " ").to_lower()


func _monster_from_headline_hint(hint: String) -> String:
	var h := hint.strip_edges()
	if h.is_empty():
		return ""
	for prefix in ["spawn:", "champion:"]:
		if h.to_lower().begins_with(prefix):
			return h.substr(prefix.length()).strip_edges()
	return ""


func _with_article(phrase: String) -> String:
	var p := phrase.strip_edges()
	if p.is_empty():
		return "something"
	var lower := p.to_lower()
	var c: String = lower.substr(0, 1)
	var article := "a"
	if c == "a" or c == "e" or c == "i" or c == "o" or c == "u":
		article = "an"
	# Seeds/items often start with "seed of" — use "a" not "an" for "a seed of …"
	if lower.begins_with("seed ") or lower.begins_with("scroll of"):
		article = "a"
	return "%s %s" % [article, p]


func _pretty_result_type(type_name: String) -> String:
	var stem := type_name.trim_suffix("_result")
	return stem.replace("_", " ").capitalize()


func _on_streamerbot_command(data: Dictionary) -> void:
	if not CompanionConfig.show_pending_udp_alerts:
		return
	var cmd := str(data.get("command", "")).to_lower()
	if cmd == "":
		if not data.has("text") or str(data["text"]).strip_edges() == "":
			return
	var headline := _headline_from_streamerbot(data)
	var who_raw := str(data.get("username", data.get("user", "")))
	var who := _spectator_label(who_raw)
	var rid: Variant = data.get("request_id", null)
	var monster_hint := ""
	var scroll_hint := ""
	if cmd == "scroll":
		if data.has("monster") and str(data["monster"]).strip_edges() != "":
			scroll_hint = str(data["monster"]).strip_edges()
		else:
			var args_s = data.get("args", null)
			if args_s is Array and args_s.size() > 0:
				scroll_hint = str(args_s[0]).strip_edges()
	else:
		if data.has("monster") and str(data["monster"]).strip_edges() != "":
			monster_hint = str(data["monster"]).strip_edges()
		else:
			var args0 = data.get("args", null)
			if args0 is Array and args0.size() > 0:
				monster_hint = str(args0[0]).strip_edges()
	var slot_s := str(data.get("slot", "")).strip_edges().to_lower()
	var amt_v: Variant = data.get("amount", null)
	_pending_tracker.append(
		{
			"request_id": rid,
			"username": who_raw,
			"headline_hint": headline,
			"command": cmd,
			"monster_hint": monster_hint,
			"scroll_hint": scroll_hint,
			"slot": slot_s,
			"amount": amt_v,
			"msec": Time.get_ticks_msec(),
		}
	)
	while _pending_tracker.size() > 32:
		_pending_tracker.pop_front()
	var sub := "Waiting for game…"
	if who != "Someone":
		sub = "%s — waiting for game…" % who
	var icon_rune: Texture2D = null
	var icon_item: Texture2D = null
	var mob_pending: Array[Texture2D] = []
	if cmd == "scroll":
		var pair3: Dictionary = _ScrollArt.rune_and_item_for_scroll_hint(
			scroll_hint, GameWebSocketClient.last_game_snapshot
		)
		icon_rune = pair3.get("rune", null) as Texture2D
		icon_item = pair3.get("item", null) as Texture2D
	elif cmd == "spawn" or cmd == "champion":
		mob_pending = _MobArt.idle_frames_for_monster_label(monster_hint)
		if mob_pending.size() > 0:
			icon_item = mob_pending[0]
	_enqueue(
		{
			"kind": "pending",
			"headline": headline,
			"subtitle": sub,
			"command": cmd,
			"monster_hint": monster_hint,
			"command_icon_rune": icon_rune,
			"command_icon_item": icon_item,
			"mob_idle_frames": mob_pending,
		}
	)


func _headline_from_streamerbot(data: Dictionary) -> String:
	var cmd := str(data.get("command", "")).to_lower()
	match cmd:
		"spawn", "champion":
			if data.has("monster") and str(data["monster"]).strip_edges() != "":
				return "%s: %s" % [cmd, str(data["monster"])]
			var args = data.get("args", null)
			if args is Array and args.size() > 0:
				return "%s: %s" % [cmd, str(args[0])]
		"gold":
			var amt: Variant = data.get("amount", 5)
			return "Gold: %s" % str(amt)
		"curse":
			var slot: Variant = data.get("slot", "?")
			return "Curse: %s" % str(slot)
		"wand":
			var tier: Variant = data.get("tier", -1)
			if str(tier) != "-1" and tier != null:
				return "Wand (tier %s)" % str(tier)
			return "Wand"
		"ping":
			return "Ping"
		"gas":
			return "Gas"
		"scroll":
			if data.has("monster") and str(data["monster"]).strip_edges() != "":
				return "Scroll: %s" % str(data["monster"])
			var args_sc = data.get("args", null)
			if args_sc is Array and args_sc.size() > 0:
				return "Scroll: %s" % str(args_sc[0])
			return "Scroll"
		"buff":
			return "Buff"
		"debuff":
			return "Debuff"
		"trap":
			return "Trap"
		"bomb":
			return "Bomb"
		"transmute":
			return "Transmute"
		"summon_bee":
			return "Summon bee"
		"ward":
			return "Ward"
		"heal":
			return "Heal"
		"cleanse":
			return "Cleanse"
		"dew":
			return "Dew"
		"corrupt_ally":
			return "Corrupt ally"
		"hex":
			return "Hex"
		"degrade":
			return "Degrade"
		"sabotage":
			return "Sabotage"
		"ring_of_wealth":
			return "Ring of wealth"
		_:
			if cmd != "":
				return cmd.replace("_", " ").capitalize()
	if data.has("text") and str(data["text"]).strip_edges() != "":
		return str(data["text"])
	return "Command"


func _pop_pending_row_for_request(request_id: Variant) -> Dictionary:
	if request_id == null or str(request_id).is_empty():
		return {}
	for i in range(_pending_tracker.size() - 1, -1, -1):
		var row: Dictionary = _pending_tracker[i]
		if _ids_match(row.get("request_id", null), request_id):
			_pending_tracker.remove_at(i)
			return row
	return {}


func _ids_match(a: Variant, b: Variant) -> bool:
	return str(a) == str(b)


func _toast_timing_for_item(item: Dictionary) -> Dictionary:
	var fade_in: float = maxf(0.05, CompanionConfig.alert_fade_in_sec)
	var hold: float = maxf(0.0, CompanionConfig.alert_hold_sec)
	var fade_out: float = maxf(0.05, CompanionConfig.alert_fade_out_sec)
	var free_promo := _item_matches_free_promo(item)
	if free_promo:
		fade_in = maxf(0.05, CompanionConfig.alert_fade_in_sec_when_free)
		hold = maxf(0.0, CompanionConfig.alert_hold_sec_when_free)
		fade_out = maxf(0.05, CompanionConfig.alert_fade_out_sec_when_free)
	return {"fade_in": fade_in, "hold": hold, "fade_out": fade_out, "free_promo": free_promo}


func _item_matches_free_promo(item: Dictionary) -> bool:
	var cmd := str(item.get("command", "")).strip_edges().to_lower()
	var monster := str(item.get("monster_hint", "")).strip_edges()
	if cmd.is_empty():
		return FreePromosState.has_any_active()
	return FreePromosState.is_command_free(cmd, monster)


func _enqueue(item: Dictionary) -> void:
	var cap: int = maxi(1, CompanionConfig.alert_queue_max)
	while _queue.size() >= cap:
		_queue.pop_front()
	_queue.append(item)
	if not _busy:
		_play_next()


func _play_next() -> void:
	if _queue.is_empty():
		_busy = false
		_hide_panel()
		return
	_busy = true
	var item: Dictionary = _queue.pop_front()
	_apply_mob_frames_from_item(item)
	_title.text = str(item.get("headline", ""))
	_subtitle.text = str(item.get("subtitle", ""))
	var tex_r: Texture2D = item.get("command_icon_rune", null) as Texture2D
	var tex_i: Texture2D = item.get("command_icon_item", null) as Texture2D
	if tex_r == null and tex_i == null:
		var legacy: Texture2D = item.get("command_icon", null) as Texture2D
		if legacy != null:
			tex_i = legacy
	if tex_r != null:
		_icon_rune.texture = tex_r
		_icon_rune.visible = true
	else:
		_icon_rune.texture = null
		_icon_rune.visible = false
	if tex_i != null:
		_icon_item.texture = tex_i
		_icon_item.visible = true
	else:
		_icon_item.texture = null
		_icon_item.visible = false

	if _mob_idle_frames.size() > 0:
		_active_command_icon_layout = "mob"
	elif tex_r != null or tex_i != null:
		_active_command_icon_layout = "scroll"
	else:
		_active_command_icon_layout = "none"

	if _active_command_icon_layout != "none":
		_apply_command_icon_sizes()

	_icons_row.visible = _active_command_icon_layout != "none"
	await get_tree().process_frame
	_panel.pivot_offset = _panel.size * 0.5
	_row.pivot_offset = _row.size * 0.5
	var kind := str(item.get("kind", ""))
	_burst_fx(kind)
	var timing := _toast_timing_for_item(item)
	var fade_in: float = timing["fade_in"]
	var hold: float = timing["hold"]
	var fade_out: float = timing["fade_out"]
	if kind == "confirm" and not bool(timing.get("free_promo", false)):
		hold += 0.75
	elif kind == "error":
		hold = maxf(0.0, hold * 0.65)
	var tween := create_tween().set_parallel(true)
	tween.set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
	tween.tween_property(_row, "modulate:a", 1.0, fade_in).from(0.0)
	tween.tween_property(_row, "scale", Vector2.ONE, fade_in).from(Vector2(0.92, 0.92))
	await tween.finished
	await get_tree().create_timer(hold).timeout
	var out := create_tween()
	out.tween_property(_row, "modulate:a", 0.0, fade_out)
	await out.finished
	_play_next()


func _hide_panel() -> void:
	_row.modulate.a = 0.0
	_row.scale = Vector2.ONE
	_clear_mob_idle_anim()
	_active_command_icon_layout = "none"
	if _icon_margin:
		_icon_margin.visible = true
	_icon_rune.texture = null
	_icon_rune.visible = false
	_icon_item.texture = null
	_icon_item.visible = false
	_icons_row.visible = false
	_title.text = ""
	_subtitle.text = ""


func _burst_fx(kind: String) -> void:
	if kind == "confirm":
		_particles.amount = 64
	elif kind == "pending":
		_particles.amount = 28
	else:
		_particles.amount = 16
	_particles.restart()
	_particles.emitting = true
