extends Window

## In-app editor for companion settings. Open with F2 or the HUD button.

var _net_host: LineEdit
var _net_port: SpinBox
var _udp_port: SpinBox
var _ws_recon: SpinBox
var _http_on: CheckBox
var _http_url: LineEdit
var _http_poll: SpinBox
var _spawn_result_file: LineEdit
var _spawn_result_poll: SpinBox
var _spawn_result_icons: CheckBox
var _spend_file: LineEdit
var _spend_poll: SpinBox
var _spend_ind: CheckBox
var _spend_corner: OptionButton
var _spend_mx: SpinBox
var _spend_my: SpinBox
var _spend_label_fs: SpinBox
var _free_url: LineEdit
var _free_poll: SpinBox
var _free_vis: CheckBox
var _free_corner: OptionButton
var _free_mx: SpinBox
var _free_my: SpinBox
var _free_fs: SpinBox
var _free_mw: SpinBox
var _sm_on: CheckBox
var _sm_url: LineEdit
var _sm_poll: SpinBox
var _sm_skip: CheckBox
var _sm_duration: SpinBox
var _sm_max: SpinBox
var _sm_lane_y_min: SpinBox
var _sm_lane_y_max: SpinBox
var _sm_lane_spacing: SpinBox
var _sm_edge_margin: SpinBox
var _sm_sprite_sz: SpinBox
var _sm_mob_fps: SpinBox
var _sm_show_user: CheckBox
var _sm_user_center: CheckBox
var _sm_user_fs: SpinBox
var _sm_user_color: ColorPickerButton
var _sm_user_ox: SpinBox
var _sm_user_oy: SpinBox
var _sm_show_monster: CheckBox
var _sm_monster_fs: SpinBox
var _sm_monster_color: ColorPickerButton
var _sm_monster_ox: SpinBox
var _sm_monster_oy: SpinBox
var _best_on: CheckBox
var _best_url: LineEdit
var _best_poll: SpinBox
var _best_sprint: CheckBox
var _best_heat: CheckBox
var _best_hall: CheckBox
var _best_zx: SpinBox
var _best_zy: SpinBox
var _best_zw: SpinBox
var _best_zh: SpinBox
var _best_zbm: SpinBox
var _best_bar_w: SpinBox
var _best_bar_hs: SpinBox
var _best_hud_scale: SpinBox
var _best_compact: CheckBox
var _best_zone_fs: SpinBox
var _best_header_fmt: LineEdit
var _best_chip_fs: SpinBox
var _best_chip_name_max: SpinBox
var _best_hall_name_max: SpinBox
var _best_trunc_names: CheckBox
var _best_xp_text: CheckBox
var _best_banner_sec: SpinBox
var _best_banner_scale: SpinBox
var _best_banner_fs: SpinBox
var _best_zone_color: ColorPickerButton
var _best_xp_color: ColorPickerButton
var _best_sprint_color: ColorPickerButton
var _best_heat_color: ColorPickerButton
var _best_hall_color: ColorPickerButton
var _best_banner_color: ColorPickerButton
var _obs_on: CheckBox
var _obs_host: LineEdit
var _obs_port: SpinBox
var _obs_pw: LineEdit
var _obs_scene: LineEdit
var _obs_log_scene: CheckBox
var _obs_recon: SpinBox
var _win_transparent: CheckBox
var _lw_bottom: SpinBox
var _lw_left: SpinBox
var _lw_left_top: SpinBox
var _lw_grad_start: SpinBox
var _lw_grad_end: SpinBox

var _pending_udp: CheckBox
var _hud_status_panel: CheckBox
var _render_max_fps: SpinBox
var _aq_max: SpinBox
var _hold: SpinBox
var _fade_in: SpinBox
var _fade_out: SpinBox
var _hold_free: SpinBox
var _fade_in_free: SpinBox
var _fade_out_free: SpinBox
var _ping_alerts: CheckBox
var _fail_alerts: CheckBox
var _az_x: SpinBox
var _az_y: SpinBox
var _az_w: SpinBox
var _az_h: SpinBox
var _az_bm: SpinBox
var _ata: OptionButton
var _alert_title_fs: SpinBox
var _alert_subtitle_fs: SpinBox
var _alert_icon_sz: SpinBox
var _mob_idle_fps: SpinBox

var _id_on: CheckBox
var _iz_x: SpinBox
var _iz_y: SpinBox
var _iz_w: SpinBox
var _iz_h: SpinBox
var _iz_bm: SpinBox
var _id_cw: SpinBox
var _id_ch: SpinBox
var _id_pad: SpinBox
var _id_iconf: SpinBox
var _id_flow: SpinBox
var _id_block: SpinBox
var _icon_cell_bg: ColorPickerButton


func _ready() -> void:
	title = "SPD Companion — settings"
	size = Vector2i(780, 700)
	min_size = Vector2i(600, 480)
	initial_position = Window.WINDOW_INITIAL_POSITION_CENTER_MAIN_WINDOW_SCREEN
	close_requested.connect(hide)
	visibility_changed.connect(_on_visibility_changed)
	_build_ui()


func _on_visibility_changed() -> void:
	if visible:
		_sync_from_config()


func _build_ui() -> void:
	var margin := MarginContainer.new()
	margin.name = "MarginRoot"
	margin.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	margin.add_theme_constant_override("margin_left", 12)
	margin.add_theme_constant_override("margin_top", 12)
	margin.add_theme_constant_override("margin_right", 12)
	margin.add_theme_constant_override("margin_bottom", 12)
	add_child(margin)

	var outer := VBoxContainer.new()
	outer.name = "VBoxOuter"
	outer.add_theme_constant_override("separation", 8)
	margin.add_child(outer)

	var tabs := TabContainer.new()
	tabs.size_flags_vertical = Control.SIZE_EXPAND_FILL
	tabs.custom_minimum_size = Vector2(0, 520)
	outer.add_child(tabs)

	# --- Connection: live links to game + companion transport ---
	var conn_sc := _make_scroll_vbox()
	tabs.add_child(conn_sc)
	tabs.set_tab_title(conn_sc.get_index(), "Connection")

	_net_host = _line()
	_net_port = _spin_i(1, 65535)
	_udp_port = _spin_i(1, 65535)
	_ws_recon = _spin_f(0.25, 120.0, 0.25)
	_http_on = CheckBox.new()
	_http_url = _line()
	_http_poll = _spin_f(0.25, 60.0, 0.25)
	_hud_status_panel = CheckBox.new()
	_render_max_fps = _spin_i(0, 480)
	_add_rows(
		conn_sc,
		[
			["Game WebSocket host", _net_host],
			["Game WebSocket port", _net_port],
			["Streamer.bot UDP port", _udp_port],
			["WS reconnect interval (sec)", _ws_recon],
			["HTTP fallback when WS down", _http_on],
			["HTTP fallback URL", _http_url],
			["HTTP poll interval (sec)", _http_poll],
			["Show connection status panel (F3 toggles)", _hud_status_panel],
			["Max FPS cap (0 = unlimited; 30–60 lowers GPU)", _render_max_fps],
		]
	)

	# --- Hooks: optional filesystem signals from Streamer.bot / tooling ---
	var hooks_sc := _make_scroll_vbox()
	tabs.add_child(hooks_sc)
	tabs.set_tab_title(hooks_sc.get_index(), "File hooks")

	_spawn_result_file = _line()
	_spawn_result_poll = _spin_f(0.05, 2.0, 0.05)
	_spawn_result_icons = CheckBox.new()
	_spend_file = _line()
	_spend_poll = _spin_f(0.05, 2.0, 0.05)
	_add_rows(
		hooks_sc,
		[
			["spawn_result.txt path (optional)", _spawn_result_file],
			["spawn_result poll interval (sec)", _spawn_result_poll],
			["Use spawn_result middle field for scroll icon", _spawn_result_icons],
			["spend_disabled.txt path (chat spend ON/OFF)", _spend_file],
			["spend lock poll interval (sec)", _spend_poll],
		]
	)

	# --- Chat spend: on-screen indicator layout ---
	var spend_sc := _make_scroll_vbox()
	tabs.add_child(spend_sc)
	tabs.set_tab_title(spend_sc.get_index(), "Chat spend")

	_spend_ind = CheckBox.new()
	_spend_corner = _opt(PackedStringArray(["Top-left", "Top-right", "Bottom-left", "Bottom-right"]))
	_spend_mx = _spin_i(0, 4000)
	_spend_my = _spin_i(0, 4000)
	_spend_label_fs = _spin_i(8, 64)
	_add_rows(
		spend_sc,
		[
			["Show on-screen Chat spend indicator", _spend_ind],
			["Panel corner", _spend_corner],
			["Margin X (px from edge)", _spend_mx],
			["Margin Y (px from edge)", _spend_my],
			["Label font size (px)", _spend_label_fs],
		]
	)

	# --- Timed free promos (Lastest UI /api/points-config → free_until) ---
	var free_sc := _make_scroll_vbox()
	tabs.add_child(free_sc)
	tabs.set_tab_title(free_sc.get_index(), "Free promos")

	_free_url = _line()
	_free_poll = _spin_f(0.5, 120.0, 0.5)
	_free_vis = CheckBox.new()
	_free_corner = _opt(PackedStringArray(["Top-left", "Top-right", "Bottom-left", "Bottom-right"]))
	_free_mx = _spin_i(0, 4000)
	_free_my = _spin_i(0, 4000)
	_free_fs = _spin_i(8, 36)
	_free_mw = _spin_i(160, 1600)
	_add_rows(
		free_sc,
		[
			["Points config URL (free_until JSON)", _free_url],
			["Poll interval (sec)", _free_poll],
			["Show panel when any promo is active", _free_vis],
			["Panel corner", _free_corner],
			["Margin X (px)", _free_mx],
			["Margin Y (px)", _free_my],
			["Font size (px)", _free_fs],
			["Max panel width (px)", _free_mw],
		]
	)

	# --- Summon march (!summon chat overlay from Lastest UI) ---
	var sm_sc := _make_scroll_vbox()
	tabs.add_child(sm_sc)
	tabs.set_tab_title(sm_sc.get_index(), "Summon march")

	_sm_on = CheckBox.new()
	_sm_url = _line()
	_sm_poll = _spin_f(0.15, 5.0, 0.05)
	_sm_skip = CheckBox.new()
	_sm_duration = _spin_f(1.0, 30.0, 0.5)
	_sm_max = _spin_i(1, 32)
	_sm_lane_y_min = _spin_f(0.05, 0.95, 0.01)
	_sm_lane_y_max = _spin_f(0.05, 0.95, 0.01)
	_sm_lane_spacing = _spin_i(8, 200)
	_sm_edge_margin = _spin_i(16, 400)
	_sm_sprite_sz = _spin_i(16, 256)
	_sm_mob_fps = _spin_f(0.5, 30.0, 0.5)
	_sm_show_user = CheckBox.new()
	_sm_user_center = CheckBox.new()
	_sm_user_fs = _spin_i(6, 48)
	_sm_user_color = _color_picker()
	_sm_user_ox = _spin_i(-400, 400)
	_sm_user_oy = _spin_i(-400, 400)
	_sm_show_monster = CheckBox.new()
	_sm_monster_fs = _spin_i(6, 48)
	_sm_monster_color = _color_picker()
	_sm_monster_ox = _spin_i(-400, 400)
	_sm_monster_oy = _spin_i(-400, 400)
	_add_rows(
		sm_sc,
		[
			["Enable summon march polling", _sm_on],
			["Server base URL", _sm_url],
			["Poll interval (sec)", _sm_poll],
			["Skip events queued before app start", _sm_skip],
			["March duration (sec)", _sm_duration],
			["Max concurrent marches", _sm_max],
			["Lane Y min (0=top, 1=bottom)", _sm_lane_y_min],
			["Lane Y max (0=top, 1=bottom)", _sm_lane_y_max],
			["Vertical march lane spacing (px)", _sm_lane_spacing],
			["Off-screen edge margin (px)", _sm_edge_margin],
			["Monster sprite size (px)", _sm_sprite_sz],
			["Mob walk animation (FPS)", _sm_mob_fps],
			["Show username label", _sm_show_user],
			["Center username under sprite", _sm_user_center],
			["Username font size (px)", _sm_user_fs],
			["Username color", _sm_user_color],
			["Username offset X (px)", _sm_user_ox],
			["Username offset Y (px)", _sm_user_oy],
			["Show monster name label", _sm_show_monster],
			["Monster name font size (px)", _sm_monster_fs],
			["Monster name color", _sm_monster_color],
			["Monster name offset X (px)", _sm_monster_ox],
			["Monster name offset Y (px)", _sm_monster_oy],
		]
	)

	# --- Bestiary (exp bar / sprint / heat HUD) ---
	var best_sc := _make_scroll_vbox()
	tabs.add_child(best_sc)
	tabs.set_tab_title(best_sc.get_index(), "Bestiary")

	_best_on = CheckBox.new()
	_best_url = _line()
	_best_poll = _spin_f(0.25, 10.0, 0.05)
	_best_sprint = CheckBox.new()
	_best_heat = CheckBox.new()
	_best_hall = CheckBox.new()
	_best_zx = _spin_i(0, 4000)
	_best_zy = _spin_i(0, 4000)
	_best_zw = _spin_i(64, 1200)
	_best_zh = _spin_i(0, 800)
	_best_zbm = _spin_i(0, 400)
	_best_bar_w = _spin_i(64, 1600)
	_best_bar_hs = _spin_f(0.5, 4.0, 0.1)
	_best_hud_scale = _spin_f(0.5, 4.0, 0.05)
	_best_compact = CheckBox.new()
	_best_zone_fs = _spin_i(8, 96)
	_best_header_fmt = _line()
	_best_chip_fs = _spin_i(8, 96)
	_best_chip_name_max = _spin_i(0, 32)
	_best_hall_name_max = _spin_i(0, 32)
	_best_trunc_names = CheckBox.new()
	_best_xp_text = CheckBox.new()
	_best_banner_sec = _spin_f(0.5, 15.0, 0.25)
	_best_banner_scale = _spin_f(0.25, 8.0, 0.05)
	_best_banner_fs = _spin_i(8, 96)
	_best_zone_color = _color_picker()
	_best_xp_color = _color_picker()
	_best_sprint_color = _color_picker()
	_best_heat_color = _color_picker()
	_best_hall_color = _color_picker()
	_best_banner_color = _color_picker()
	_add_rows(
		best_sc,
		[
			["Enable Bestiary HUD", _best_on],
			["Server base URL", _best_url],
			["Poll interval (sec)", _best_poll],
			["Show sprint leader chip", _best_sprint],
			["Show heat leader chip", _best_heat],
			["Show hall of fame strip", _best_hall],
			["HUD zone X (px)", _best_zx],
			["HUD zone Y (px)", _best_zy],
			["HUD zone width (px)", _best_zw],
			["HUD zone height (px, 0=auto)", _best_zh],
			["HUD zone bottom margin (px)", _best_zbm],
			["HUD overall scale", _best_hud_scale],
			["Exp bar width (px; panel uses zone width)", _best_bar_w],
			["Exp bar height scale", _best_bar_hs],
			["Use compact StatusPane exp art", _best_compact],
			["Header text ({level} {zone})", _best_header_fmt],
			["Zone label font size (px)", _best_zone_fs],
			["Zone label font color", _best_zone_color],
			["Sprint/heat/hall font size (px)", _best_chip_fs],
			["Sprint font color", _best_sprint_color],
			["Heat font color", _best_heat_color],
			["Hall of fame font color", _best_hall_color],
			["Truncate long usernames", _best_trunc_names],
			["Sprint/heat name max chars (0=off)", _best_chip_name_max],
			["Hall name max chars (0=off)", _best_hall_name_max],
			["Show XP fraction text", _best_xp_text],
			["XP fraction font color", _best_xp_color],
			["Level-up banner duration (sec)", _best_banner_sec],
			["Level-up banner image scale", _best_banner_scale],
			["Level-up banner text size (px)", _best_banner_fs],
			["Level-up banner text color", _best_banner_color],
		]
	)

	# --- OBS (optional scene-driven title vs ID layer) ---
	var obs_sc := _make_scroll_vbox()
	tabs.add_child(obs_sc)
	tabs.set_tab_title(obs_sc.get_index(), "OBS")

	_obs_on = CheckBox.new()
	_obs_host = _line()
	_obs_port = _spin_i(1, 65535)
	_obs_pw = _line()
	_obs_pw.secret = true
	_obs_scene = _line()
	_obs_log_scene = CheckBox.new()
	_obs_recon = _spin_f(0.5, 120.0, 0.5)
	_win_transparent = CheckBox.new()
	_lw_bottom = _spin_i(0, 4000)
	_lw_left = _spin_i(0, 4000)
	_lw_left_top = _spin_i(0, 4000)
	_lw_grad_start = _spin_f(0.0, 1.0, 0.01)
	_lw_grad_end = _spin_f(0.0, 1.0, 0.01)
	_add_rows(
		obs_sc,
		[
			["Sync title vs ID layer (obs-websocket v5)", _obs_on],
			["WebSocket host", _obs_host],
			["WebSocket port (default 4455)", _obs_port],
			["WebSocket password (if enabled in OBS)", _obs_pw],
			["Pause/backdrop if scene name contains this text", _obs_scene],
			["Log program scene to Output (debug)", _obs_log_scene],
			["Reconnect interval (sec)", _obs_recon],
			["Transparent window (OBS sees layers beneath gaps)", _win_transparent],
			["Live water: bottom bar height (px)", _lw_bottom],
			["Live water: left strip width (px)", _lw_left],
			["Live water: left strip top inset (px, below chat)", _lw_left_top],
			["Live water: fade start Y (0=top, 1=bottom)", _lw_grad_start],
			["Live water: fade end Y (full black)", _lw_grad_end],
		]
	)

	var al_sc := _make_scroll_vbox()
	tabs.add_child(al_sc)
	tabs.set_tab_title(al_sc.get_index(), "Alerts")

	_pending_udp = CheckBox.new()
	_aq_max = _spin_i(1, 99)
	_hold = _spin_f(0.0, 30.0, 0.25)
	_fade_in = _spin_f(0.05, 5.0, 0.05)
	_fade_out = _spin_f(0.05, 5.0, 0.05)
	_hold_free = _spin_f(0.0, 10.0, 0.05)
	_fade_in_free = _spin_f(0.05, 5.0, 0.05)
	_fade_out_free = _spin_f(0.05, 5.0, 0.05)
	_ping_alerts = CheckBox.new()
	_fail_alerts = CheckBox.new()
	_add_rows(
		al_sc,
		[
			["Show pending UDP alerts", _pending_udp],
			["Alert queue max", _aq_max],
			["Alert hold (sec)", _hold],
			["Alert fade in (sec)", _fade_in],
			["Alert fade out (sec)", _fade_out],
			["Hold when command is free (sec)", _hold_free],
			["Fade in when free (sec)", _fade_in_free],
			["Fade out when free (sec)", _fade_out_free],
			["Show ping_result alerts", _ping_alerts],
			["Show failed command alerts", _fail_alerts],
		]
	)

	var alert_geom_sc := _make_scroll_vbox()
	tabs.add_child(alert_geom_sc)
	tabs.set_tab_title(alert_geom_sc.get_index(), "Alert layout")

	_az_x = _spin_i(0, 4000)
	_az_y = _spin_i(0, 4000)
	_az_w = _spin_i(32, 4000)
	_az_h = _spin_i(0, 4000)
	_az_bm = _spin_i(0, 400)
	_ata = _opt(["left", "center"])
	_alert_title_fs = _spin_i(6, 72)
	_alert_subtitle_fs = _spin_i(6, 72)
	_alert_icon_sz = _spin_i(24, 256)
	_mob_idle_fps = _spin_f(0.25, 24.0, 0.25)
	_add_rows(
		alert_geom_sc,
		[
			["Toast zone X (px)", _az_x],
			["Toast zone Y (px)", _az_y],
			["Toast zone width (px)", _az_w],
			["Toast zone height (0=auto)", _az_h],
			["Bottom margin if height is auto", _az_bm],
			["Title/subtitle align", _ata],
			["Title font size (px)", _alert_title_fs],
			["Subtitle font size (px)", _alert_subtitle_fs],
			["Command icon size (px, scroll + mob)", _alert_icon_sz],
			["Mob idle animation (FPS)", _mob_idle_fps],
		]
	)

	var id_sc := _make_scroll_vbox()
	tabs.add_child(id_sc)
	tabs.set_tab_title(id_sc.get_index(), "ID strip")

	_id_on = CheckBox.new()
	_iz_x = _spin_i(0, 4000)
	_iz_y = _spin_i(0, 4000)
	_iz_w = _spin_i(32, 4000)
	_iz_h = _spin_i(0, 4000)
	_iz_bm = _spin_i(0, 400)
	_add_rows(
		id_sc,
		[
			["ID overlay enabled", _id_on],
			["Zone X (px)", _iz_x],
			["Zone Y (px)", _iz_y],
			["Zone width (px)", _iz_w],
			["Zone height (0=auto)", _iz_h],
			["Bottom margin if height is auto", _iz_bm],
		]
	)

	var id_cells_sc := _make_scroll_vbox()
	tabs.add_child(id_cells_sc)
	tabs.set_tab_title(id_cells_sc.get_index(), "ID cells")

	_id_cw = _spin_i(16, 200)
	_id_ch = _spin_i(16, 200)
	_id_pad = _spin_i(0, 32)
	_id_iconf = _spin_f(0.1, 0.95, 0.01)
	_id_flow = _spin_i(0, 48)
	_id_block = _spin_i(0, 80)
	_icon_cell_bg = ColorPickerButton.new()
	_icon_cell_bg.edit_alpha = true
	_icon_cell_bg.custom_minimum_size = Vector2(140, 28)
	_add_rows(
		id_cells_sc,
		[
			["Cell width (px)", _id_cw],
			["Cell height (px)", _id_ch],
			["Cell padding (px)", _id_pad],
			["Known icon fraction", _id_iconf],
			["Flow separation (px)", _id_flow],
			["Potion / scroll block gap (px)", _id_block],
			["Icon tile background (ID + alerts, A=0 = clear)", _icon_cell_bg],
		]
	)

	var path_l := Label.new()
	path_l.name = "PathHint"
	path_l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	outer.add_child(path_l)

	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", 10)
	outer.add_child(row)

	var apply := Button.new()
	apply.text = "Apply & save"
	apply.pressed.connect(_on_apply_pressed)
	row.add_child(apply)

	var reload := Button.new()
	reload.text = "Reload from disk"
	reload.pressed.connect(_on_reload_pressed)
	row.add_child(reload)

	var close_b := Button.new()
	close_b.text = "Close"
	close_b.pressed.connect(hide)
	row.add_child(close_b)


func _make_scroll_vbox() -> Control:
	var sc := ScrollContainer.new()
	sc.name = "Scroll"
	sc.size_flags_vertical = Control.SIZE_EXPAND_FILL
	sc.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	var vb := VBoxContainer.new()
	vb.name = "InnerVBox"
	vb.add_theme_constant_override("separation", 6)
	vb.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	vb.custom_minimum_size = Vector2(620, 0)
	sc.add_child(vb)
	return sc


func _add_rows(parent_sc: ScrollContainer, rows: Array) -> void:
	var vb: VBoxContainer = parent_sc.get_node("InnerVBox") as VBoxContainer
	for row in rows:
		var pair: Array = row
		vb.add_child(_form_row(str(pair[0]), pair[1] as Control))


func _form_row(label_text: String, control: Control) -> HBoxContainer:
	var hb := HBoxContainer.new()
	hb.add_theme_constant_override("separation", 12)
	var lab := Label.new()
	lab.text = label_text
	lab.custom_minimum_size = Vector2(220, 0)
	lab.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	hb.add_child(lab)
	control.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	hb.add_child(control)
	return hb


func _line() -> LineEdit:
	var le := LineEdit.new()
	le.clear_button_enabled = true
	return le


func _spin_i(mn: int, mx: int) -> SpinBox:
	var s := SpinBox.new()
	s.min_value = mn
	s.max_value = mx
	s.step = 1
	s.rounded = true
	s.custom_minimum_size = Vector2(120, 0)
	return s


func _spin_f(mn: float, mx: float, step: float) -> SpinBox:
	var s := SpinBox.new()
	s.min_value = mn
	s.max_value = mx
	s.step = step
	s.allow_greater = false
	s.allow_lesser = false
	s.update_on_text_changed = true
	s.custom_minimum_size = Vector2(120, 0)
	return s


func _opt(items: PackedStringArray) -> OptionButton:
	var o := OptionButton.new()
	for it in items:
		o.add_item(it)
	o.custom_minimum_size = Vector2(160, 0)
	return o


func _color_picker() -> ColorPickerButton:
	var cp := ColorPickerButton.new()
	cp.edit_alpha = true
	cp.custom_minimum_size = Vector2(140, 28)
	return cp


func _find_path_label() -> Label:
	var m: MarginContainer = get_node_or_null("MarginRoot") as MarginContainer
	if m == null:
		return null
	var vb: VBoxContainer = m.get_node_or_null("VBoxOuter") as VBoxContainer
	if vb == null:
		return null
	return vb.get_node_or_null("PathHint") as Label


func _sync_from_config() -> void:
	_net_host.text = CompanionConfig.game_ws_host
	_net_port.value = CompanionConfig.game_ws_port
	_udp_port.value = CompanionConfig.streamerbot_udp_port
	_ws_recon.value = CompanionConfig.ws_reconnect_sec
	_http_on.button_pressed = CompanionConfig.http_fallback_enabled
	_http_url.text = CompanionConfig.http_fallback_url
	_http_poll.value = CompanionConfig.http_poll_interval_sec
	_render_max_fps.value = CompanionConfig.render_max_fps
	_spawn_result_file.text = CompanionConfig.spawn_result_file_path
	_spawn_result_poll.value = CompanionConfig.spawn_result_file_poll_sec
	_spawn_result_icons.button_pressed = CompanionConfig.spawn_result_file_icons
	_spend_file.text = CompanionConfig.spend_lock_file_path
	_spend_poll.value = CompanionConfig.spend_lock_poll_sec
	_spend_ind.button_pressed = CompanionConfig.spend_indicator_visible
	_spend_corner.select(clampi(CompanionConfig.spend_indicator_corner, 0, 3))
	_spend_mx.value = CompanionConfig.spend_indicator_margin_x
	_spend_my.value = CompanionConfig.spend_indicator_margin_y
	_spend_label_fs.value = CompanionConfig.spend_indicator_font_size_px
	_free_url.text = CompanionConfig.free_promos_http_url
	_free_poll.value = CompanionConfig.free_promos_poll_sec
	_free_vis.button_pressed = CompanionConfig.free_promos_panel_visible
	_free_corner.select(clampi(CompanionConfig.free_promos_corner, 0, 3))
	_free_mx.value = CompanionConfig.free_promos_margin_x
	_free_my.value = CompanionConfig.free_promos_margin_y
	_free_fs.value = CompanionConfig.free_promos_font_size_px
	_free_mw.value = CompanionConfig.free_promos_max_width_px
	_sm_on.button_pressed = CompanionConfig.summon_march_enabled
	_sm_url.text = CompanionConfig.summon_march_base_url
	_sm_poll.value = CompanionConfig.summon_march_poll_sec
	_sm_skip.button_pressed = CompanionConfig.summon_march_skip_backlog
	_sm_duration.value = CompanionConfig.summon_march_duration_sec
	_sm_max.value = CompanionConfig.summon_march_max_concurrent
	_sm_lane_y_min.value = CompanionConfig.summon_march_lane_y_min_fraction
	_sm_lane_y_max.value = CompanionConfig.summon_march_lane_y_max_fraction
	_sm_lane_spacing.value = CompanionConfig.summon_march_lane_spacing_px
	_sm_edge_margin.value = CompanionConfig.summon_march_edge_margin_px
	_sm_sprite_sz.value = CompanionConfig.summon_march_sprite_size_px
	_sm_mob_fps.value = CompanionConfig.summon_march_mob_fps
	_sm_show_user.button_pressed = CompanionConfig.summon_march_show_username
	_sm_user_center.button_pressed = CompanionConfig.summon_march_username_centered
	_sm_user_fs.value = CompanionConfig.summon_march_username_font_size_px
	_sm_user_color.color = CompanionConfig.summon_march_username_color
	_sm_user_ox.value = CompanionConfig.summon_march_username_offset_x
	_sm_user_oy.value = CompanionConfig.summon_march_username_offset_y
	_sm_show_monster.button_pressed = CompanionConfig.summon_march_show_monster_name
	_sm_monster_fs.value = CompanionConfig.summon_march_monster_font_size_px
	_sm_monster_color.color = CompanionConfig.summon_march_monster_color
	_sm_monster_ox.value = CompanionConfig.summon_march_monster_offset_x
	_sm_monster_oy.value = CompanionConfig.summon_march_monster_offset_y
	_best_on.button_pressed = CompanionConfig.bestiary_hud_enabled
	_best_url.text = CompanionConfig.bestiary_base_url
	_best_poll.value = CompanionConfig.bestiary_poll_sec
	_best_sprint.button_pressed = CompanionConfig.bestiary_show_sprint_chip
	_best_heat.button_pressed = CompanionConfig.bestiary_show_heat_chip
	_best_hall.button_pressed = CompanionConfig.bestiary_show_hall
	_best_zx.value = CompanionConfig.bestiary_zone_x_px
	_best_zy.value = CompanionConfig.bestiary_zone_y_px
	_best_zw.value = CompanionConfig.bestiary_zone_width_px
	_best_zh.value = CompanionConfig.bestiary_zone_height_px
	_best_zbm.value = CompanionConfig.bestiary_zone_bottom_margin_px
	_best_hud_scale.value = CompanionConfig.bestiary_hud_scale
	_best_bar_w.value = CompanionConfig.bestiary_exp_bar_width_px
	_best_bar_hs.value = CompanionConfig.bestiary_exp_bar_height_scale
	_best_compact.button_pressed = CompanionConfig.bestiary_use_compact_exp_bar
	_best_header_fmt.text = CompanionConfig.bestiary_header_format
	_best_zone_fs.value = CompanionConfig.bestiary_zone_font_size_px
	_best_chip_fs.value = CompanionConfig.bestiary_chip_font_size_px
	_best_chip_name_max.value = CompanionConfig.bestiary_chip_name_max_chars
	_best_hall_name_max.value = CompanionConfig.bestiary_hall_name_max_chars
	_best_trunc_names.button_pressed = CompanionConfig.bestiary_truncate_names
	_best_xp_text.button_pressed = CompanionConfig.bestiary_show_xp_text
	_best_banner_sec.value = CompanionConfig.bestiary_level_up_banner_sec
	_best_banner_scale.value = CompanionConfig.bestiary_level_up_banner_scale
	_best_banner_fs.value = CompanionConfig.bestiary_level_up_banner_font_size_px
	_best_zone_color.color = CompanionConfig.bestiary_zone_font_color
	_best_xp_color.color = CompanionConfig.bestiary_xp_font_color
	_best_sprint_color.color = CompanionConfig.bestiary_sprint_font_color
	_best_heat_color.color = CompanionConfig.bestiary_heat_font_color
	_best_hall_color.color = CompanionConfig.bestiary_hall_font_color
	_best_banner_color.color = CompanionConfig.bestiary_banner_font_color
	_obs_on.button_pressed = CompanionConfig.obs_scene_sync_enabled
	_obs_host.text = CompanionConfig.obs_ws_host
	_obs_port.value = CompanionConfig.obs_ws_port
	_obs_pw.text = CompanionConfig.obs_ws_password
	_obs_scene.text = CompanionConfig.obs_pause_scene_name
	_obs_log_scene.button_pressed = CompanionConfig.obs_log_program_scene
	_obs_recon.value = CompanionConfig.obs_reconnect_sec
	_win_transparent.button_pressed = CompanionConfig.window_per_pixel_transparency_enabled
	_lw_bottom.value = CompanionConfig.live_water_bottom_bar_px
	_lw_left.value = CompanionConfig.live_water_left_strip_px
	_lw_left_top.value = CompanionConfig.live_water_left_strip_top_px
	_lw_grad_start.value = CompanionConfig.live_water_gradient_fade_start
	_lw_grad_end.value = CompanionConfig.live_water_gradient_fade_end

	_pending_udp.button_pressed = CompanionConfig.show_pending_udp_alerts
	_hud_status_panel.button_pressed = CompanionConfig.hud_status_panel_visible
	_aq_max.value = CompanionConfig.alert_queue_max
	_hold.value = CompanionConfig.alert_hold_sec
	_fade_in.value = CompanionConfig.alert_fade_in_sec
	_fade_out.value = CompanionConfig.alert_fade_out_sec
	_hold_free.value = CompanionConfig.alert_hold_sec_when_free
	_fade_in_free.value = CompanionConfig.alert_fade_in_sec_when_free
	_fade_out_free.value = CompanionConfig.alert_fade_out_sec_when_free
	_ping_alerts.button_pressed = CompanionConfig.show_ping_alerts
	_fail_alerts.button_pressed = CompanionConfig.show_failed_command_alerts
	_az_x.value = CompanionConfig.alert_zone_x_px
	_az_y.value = CompanionConfig.alert_zone_y_px
	_az_w.value = CompanionConfig.alert_zone_width_px
	_az_h.value = CompanionConfig.alert_zone_height_px
	_az_bm.value = CompanionConfig.alert_zone_bottom_margin_px
	_select_option(_ata, CompanionConfig.alert_text_align.to_lower())
	_alert_title_fs.value = CompanionConfig.alert_title_font_size_px
	_alert_subtitle_fs.value = CompanionConfig.alert_subtitle_font_size_px
	_alert_icon_sz.value = CompanionConfig.alert_command_icon_size_px
	_mob_idle_fps.value = CompanionConfig.alert_mob_idle_anim_fps

	_id_on.button_pressed = CompanionConfig.id_overlay_enabled
	_iz_x.value = CompanionConfig.id_zone_x_px
	_iz_y.value = CompanionConfig.id_zone_y_px
	_iz_w.value = CompanionConfig.id_zone_width_px
	_iz_h.value = CompanionConfig.id_zone_height_px
	_iz_bm.value = CompanionConfig.id_zone_bottom_margin_px
	_id_cw.value = CompanionConfig.id_cell_width_px
	_id_ch.value = CompanionConfig.id_cell_height_px
	_id_pad.value = CompanionConfig.id_cell_padding_px
	_id_iconf.value = CompanionConfig.id_known_icon_fraction
	_id_flow.value = CompanionConfig.id_flow_h_separation_px
	_id_block.value = CompanionConfig.id_block_separation_px
	_icon_cell_bg.color = CompanionConfig.icon_cell_background_color

	var path_l := _find_path_label()
	if path_l:
		path_l.text = "Saved to: %s — F2 closes/opens this window after apply" % CompanionConfig.SETTINGS_PATH


func _select_option(ob: OptionButton, value: String) -> void:
	for i in range(ob.item_count):
		if ob.get_item_text(i).to_lower() == value:
			ob.select(i)
			return
	ob.select(0)


func _on_apply_pressed() -> void:
	CompanionConfig.game_ws_host = _net_host.text.strip_edges()
	CompanionConfig.game_ws_port = int(_net_port.value)
	CompanionConfig.streamerbot_udp_port = int(_udp_port.value)
	CompanionConfig.ws_reconnect_sec = float(_ws_recon.value)
	CompanionConfig.http_fallback_enabled = _http_on.button_pressed
	CompanionConfig.http_fallback_url = _http_url.text.strip_edges()
	CompanionConfig.http_poll_interval_sec = float(_http_poll.value)
	CompanionConfig.render_max_fps = clampi(int(_render_max_fps.value), 0, 480)
	CompanionConfig.spawn_result_file_path = _spawn_result_file.text.strip_edges()
	CompanionConfig.spawn_result_file_poll_sec = float(_spawn_result_poll.value)
	CompanionConfig.spawn_result_file_icons = _spawn_result_icons.button_pressed
	CompanionConfig.spend_lock_file_path = _spend_file.text.strip_edges()
	CompanionConfig.spend_lock_poll_sec = float(_spend_poll.value)
	CompanionConfig.spend_indicator_visible = _spend_ind.button_pressed
	CompanionConfig.spend_indicator_corner = clampi(_spend_corner.selected, 0, 3)
	CompanionConfig.spend_indicator_margin_x = int(_spend_mx.value)
	CompanionConfig.spend_indicator_margin_y = int(_spend_my.value)
	CompanionConfig.spend_indicator_font_size_px = clampi(int(_spend_label_fs.value), 8, 64)
	CompanionConfig.free_promos_http_url = _free_url.text.strip_edges()
	CompanionConfig.free_promos_poll_sec = maxf(0.5, float(_free_poll.value))
	CompanionConfig.free_promos_panel_visible = _free_vis.button_pressed
	CompanionConfig.free_promos_corner = clampi(_free_corner.selected, 0, 3)
	CompanionConfig.free_promos_margin_x = int(_free_mx.value)
	CompanionConfig.free_promos_margin_y = int(_free_my.value)
	CompanionConfig.free_promos_font_size_px = clampi(int(_free_fs.value), 8, 36)
	CompanionConfig.free_promos_max_width_px = clampi(int(_free_mw.value), 160, 1600)
	CompanionConfig.summon_march_enabled = _sm_on.button_pressed
	CompanionConfig.summon_march_base_url = _sm_url.text.strip_edges()
	CompanionConfig.summon_march_poll_sec = maxf(0.15, float(_sm_poll.value))
	CompanionConfig.summon_march_skip_backlog = _sm_skip.button_pressed
	CompanionConfig.summon_march_duration_sec = maxf(1.0, float(_sm_duration.value))
	CompanionConfig.summon_march_max_concurrent = clampi(int(_sm_max.value), 1, 32)
	var lane_y_min := clampf(float(_sm_lane_y_min.value), 0.05, 0.95)
	var lane_y_max := clampf(float(_sm_lane_y_max.value), 0.05, 0.95)
	if lane_y_max < lane_y_min:
		lane_y_max = lane_y_min
	CompanionConfig.summon_march_lane_y_min_fraction = lane_y_min
	CompanionConfig.summon_march_lane_y_max_fraction = lane_y_max
	CompanionConfig.summon_march_lane_y_fraction = (lane_y_min + lane_y_max) * 0.5
	CompanionConfig.summon_march_lane_spacing_px = clampi(int(_sm_lane_spacing.value), 8, 200)
	CompanionConfig.summon_march_edge_margin_px = clampi(int(_sm_edge_margin.value), 16, 400)
	CompanionConfig.summon_march_sprite_size_px = clampi(int(_sm_sprite_sz.value), 16, 256)
	CompanionConfig.summon_march_mob_fps = clampf(float(_sm_mob_fps.value), 0.5, 30.0)
	CompanionConfig.summon_march_show_username = _sm_show_user.button_pressed
	CompanionConfig.summon_march_username_centered = _sm_user_center.button_pressed
	CompanionConfig.summon_march_username_font_size_px = clampi(int(_sm_user_fs.value), 6, 48)
	CompanionConfig.summon_march_username_color = _sm_user_color.color
	CompanionConfig.summon_march_username_offset_x = int(_sm_user_ox.value)
	CompanionConfig.summon_march_username_offset_y = int(_sm_user_oy.value)
	CompanionConfig.summon_march_show_monster_name = _sm_show_monster.button_pressed
	CompanionConfig.summon_march_monster_font_size_px = clampi(int(_sm_monster_fs.value), 6, 48)
	CompanionConfig.summon_march_monster_color = _sm_monster_color.color
	CompanionConfig.summon_march_monster_offset_x = int(_sm_monster_ox.value)
	CompanionConfig.summon_march_monster_offset_y = int(_sm_monster_oy.value)
	CompanionConfig.bestiary_hud_enabled = _best_on.button_pressed
	CompanionConfig.bestiary_base_url = _best_url.text.strip_edges()
	CompanionConfig.bestiary_poll_sec = maxf(0.25, float(_best_poll.value))
	CompanionConfig.bestiary_show_sprint_chip = _best_sprint.button_pressed
	CompanionConfig.bestiary_show_heat_chip = _best_heat.button_pressed
	CompanionConfig.bestiary_show_hall = _best_hall.button_pressed
	CompanionConfig.bestiary_zone_x_px = int(_best_zx.value)
	CompanionConfig.bestiary_zone_y_px = int(_best_zy.value)
	CompanionConfig.bestiary_zone_width_px = clampi(int(_best_zw.value), 64, 1200)
	CompanionConfig.bestiary_zone_height_px = clampi(int(_best_zh.value), 0, 800)
	CompanionConfig.bestiary_zone_bottom_margin_px = int(_best_zbm.value)
	CompanionConfig.bestiary_hud_scale = clampf(float(_best_hud_scale.value), 0.5, 4.0)
	CompanionConfig.bestiary_exp_bar_width_px = clampi(int(_best_bar_w.value), 64, 1600)
	CompanionConfig.bestiary_exp_bar_height_scale = clampf(float(_best_bar_hs.value), 0.5, 4.0)
	CompanionConfig.bestiary_use_compact_exp_bar = _best_compact.button_pressed
	var hdr := _best_header_fmt.text.strip_edges()
	CompanionConfig.bestiary_header_format = (
		hdr if not hdr.is_empty() else "Bestiary Lv {level} - {zone}"
	)
	CompanionConfig.bestiary_zone_font_size_px = clampi(int(_best_zone_fs.value), 8, 96)
	CompanionConfig.bestiary_chip_font_size_px = clampi(int(_best_chip_fs.value), 8, 96)
	CompanionConfig.bestiary_chip_name_max_chars = clampi(int(_best_chip_name_max.value), 0, 32)
	CompanionConfig.bestiary_hall_name_max_chars = clampi(int(_best_hall_name_max.value), 0, 32)
	CompanionConfig.bestiary_truncate_names = _best_trunc_names.button_pressed
	CompanionConfig.bestiary_show_xp_text = _best_xp_text.button_pressed
	CompanionConfig.bestiary_level_up_banner_sec = maxf(0.5, float(_best_banner_sec.value))
	CompanionConfig.bestiary_level_up_banner_scale = clampf(float(_best_banner_scale.value), 0.25, 8.0)
	CompanionConfig.bestiary_level_up_banner_font_size_px = clampi(int(_best_banner_fs.value), 8, 96)
	CompanionConfig.bestiary_zone_font_color = _best_zone_color.color
	CompanionConfig.bestiary_xp_font_color = _best_xp_color.color
	CompanionConfig.bestiary_sprint_font_color = _best_sprint_color.color
	CompanionConfig.bestiary_heat_font_color = _best_heat_color.color
	CompanionConfig.bestiary_hall_font_color = _best_hall_color.color
	CompanionConfig.bestiary_banner_font_color = _best_banner_color.color
	CompanionConfig.obs_scene_sync_enabled = _obs_on.button_pressed
	CompanionConfig.obs_ws_host = _obs_host.text.strip_edges()
	CompanionConfig.obs_ws_port = int(_obs_port.value)
	CompanionConfig.obs_ws_password = _obs_pw.text
	CompanionConfig.obs_pause_scene_name = _obs_scene.text.strip_edges()
	CompanionConfig.obs_log_program_scene = _obs_log_scene.button_pressed
	CompanionConfig.obs_reconnect_sec = float(_obs_recon.value)
	CompanionConfig.window_per_pixel_transparency_enabled = _win_transparent.button_pressed
	CompanionConfig.live_water_bottom_bar_px = int(_lw_bottom.value)
	CompanionConfig.live_water_left_strip_px = int(_lw_left.value)
	CompanionConfig.live_water_left_strip_top_px = int(_lw_left_top.value)
	var grad_start := clampf(float(_lw_grad_start.value), 0.0, 1.0)
	var grad_end := clampf(float(_lw_grad_end.value), 0.0, 1.0)
	if grad_end < grad_start:
		grad_end = grad_start
	CompanionConfig.live_water_gradient_fade_start = grad_start
	CompanionConfig.live_water_gradient_fade_end = grad_end

	CompanionConfig.show_pending_udp_alerts = _pending_udp.button_pressed
	CompanionConfig.hud_status_panel_visible = _hud_status_panel.button_pressed
	CompanionConfig.alert_queue_max = int(_aq_max.value)
	CompanionConfig.alert_hold_sec = float(_hold.value)
	CompanionConfig.alert_fade_in_sec = float(_fade_in.value)
	CompanionConfig.alert_fade_out_sec = float(_fade_out.value)
	CompanionConfig.alert_hold_sec_when_free = float(_hold_free.value)
	CompanionConfig.alert_fade_in_sec_when_free = float(_fade_in_free.value)
	CompanionConfig.alert_fade_out_sec_when_free = float(_fade_out_free.value)
	CompanionConfig.show_ping_alerts = _ping_alerts.button_pressed
	CompanionConfig.show_failed_command_alerts = _fail_alerts.button_pressed
	CompanionConfig.alert_zone_x_px = int(_az_x.value)
	CompanionConfig.alert_zone_y_px = int(_az_y.value)
	CompanionConfig.alert_zone_width_px = int(_az_w.value)
	CompanionConfig.alert_zone_height_px = int(_az_h.value)
	CompanionConfig.alert_zone_bottom_margin_px = int(_az_bm.value)
	CompanionConfig.alert_text_align = _ata.get_item_text(_ata.selected).strip_edges()
	CompanionConfig.alert_title_font_size_px = int(_alert_title_fs.value)
	CompanionConfig.alert_subtitle_font_size_px = int(_alert_subtitle_fs.value)
	CompanionConfig.alert_command_icon_size_px = int(_alert_icon_sz.value)
	CompanionConfig.alert_mob_idle_anim_fps = float(_mob_idle_fps.value)

	CompanionConfig.id_overlay_enabled = _id_on.button_pressed
	CompanionConfig.id_zone_x_px = int(_iz_x.value)
	CompanionConfig.id_zone_y_px = int(_iz_y.value)
	CompanionConfig.id_zone_width_px = int(_iz_w.value)
	CompanionConfig.id_zone_height_px = int(_iz_h.value)
	CompanionConfig.id_zone_bottom_margin_px = int(_iz_bm.value)
	CompanionConfig.id_cell_width_px = int(_id_cw.value)
	CompanionConfig.id_cell_height_px = int(_id_ch.value)
	CompanionConfig.id_cell_padding_px = int(_id_pad.value)
	CompanionConfig.id_known_icon_fraction = float(_id_iconf.value)
	CompanionConfig.id_flow_h_separation_px = int(_id_flow.value)
	CompanionConfig.id_block_separation_px = int(_id_block.value)
	CompanionConfig.icon_cell_background_color = _icon_cell_bg.color

	CompanionConfig.save_settings()


func _on_reload_pressed() -> void:
	CompanionConfig.load_settings()
	_sync_from_config()
