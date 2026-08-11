extends Window

## In-app editor for companion settings. Open with F2 or the HUD button.

const _SpdUiArt := preload("res://scripts/spd_ui_art.gd")

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
var _spend_pad_h: SpinBox
var _spend_pad_v: SpinBox
var _spend_chrome_style: OptionButton
var _spend_chrome_scale: SpinBox
var _free_url: LineEdit
var _free_poll: SpinBox
var _free_vis: CheckBox
var _free_corner: OptionButton
var _free_mx: SpinBox
var _free_my: SpinBox
var _free_pad_h: SpinBox
var _free_pad_v: SpinBox
var _free_fs: SpinBox
var _free_mw: SpinBox
var _free_chrome_style: OptionButton
var _free_chrome_scale: SpinBox
var _dp_vis: CheckBox
var _dp_poll: SpinBox
var _dp_corner: OptionButton
var _dp_mx: SpinBox
var _dp_my: SpinBox
var _dp_pad_h: SpinBox
var _dp_pad_v: SpinBox
var _dp_fs: SpinBox
var _dp_color: ColorPickerButton
var _dp_chrome_style: OptionButton
var _dp_chrome_scale: SpinBox
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
var _sm_crown_scale: SpinBox
var _sm_crown_mob_mod: ColorPickerButton
var _sm_crown_glow_on: CheckBox
var _sm_crown_glow_color: ColorPickerButton
var _sm_crown_glow_rays: SpinBox
var _sm_crown_glow_spin: SpinBox
var _sm_crown_glow_rad: SpinBox
var _sm_crown_show: CheckBox
var _sm_crown_sz: SpinBox
var _sm_crown_oy: SpinBox
var _sm_crown_mod: ColorPickerButton
var _sm_crown_user_color: ColorPickerButton
var _sm_crown_user_fs: SpinBox
var _sm_crown_star: CheckBox
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
var _best_pad: SpinBox
var _best_bar_w: SpinBox
var _best_bar_hs: SpinBox
var _best_hud_scale: SpinBox
var _best_chrome_scale: SpinBox
var _best_compact: CheckBox
var _best_zone_fs: SpinBox
var _best_header_fmt: LineEdit
var _best_chip_fs: SpinBox
var _best_chip_name_max: SpinBox
var _best_hall_name_max: SpinBox
var _best_trunc_names: CheckBox
var _best_xp_text: CheckBox
var _best_xp_over: CheckBox
var _best_banner_sec: SpinBox
var _best_banner_scale: SpinBox
var _best_banner_fs: SpinBox
var _best_zone_color: ColorPickerButton
var _best_xp_color: ColorPickerButton
var _best_sprint_color: ColorPickerButton
var _best_heat_color: ColorPickerButton
var _best_hall_color: ColorPickerButton
var _best_banner_color: ColorPickerButton
var _remote_on: CheckBox
var _remote_url: LineEdit
var _remote_poll: SpinBox
var _remote_push: CheckBox
var _remote_status: Label
var _obs_on: CheckBox
var _obs_host: LineEdit
var _obs_port: SpinBox
var _obs_pw: LineEdit
var _obs_scene: LineEdit
var _obs_main_scene: LineEdit
var _obs_log_scene: CheckBox
var _obs_recon: SpinBox
var _win_transparent: CheckBox
var _lw_bottom: SpinBox
var _lw_left: SpinBox
var _lw_left_top: SpinBox
var _lw_grad_start: SpinBox
var _lw_grad_end: SpinBox
var _lw_feather_v: SpinBox

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
var _alert_pad_h: SpinBox
var _alert_pad_v: SpinBox
var _alert_icon_sz: SpinBox
var _alert_chrome_style: OptionButton
var _alert_chrome_scale: SpinBox
var _mob_idle_fps: SpinBox
var _paid_on: CheckBox
var _paid_qmax: SpinBox
var _paid_ttl: SpinBox
var _paid_fade_in: SpinBox
var _paid_fade_out: SpinBox
var _paid_zx: SpinBox
var _paid_zy: SpinBox
var _paid_zw: SpinBox
var _paid_zh: SpinBox
var _paid_zbm: SpinBox
var _paid_chrome_style: OptionButton
var _paid_chrome_scale: SpinBox
var _paid_kind_fs: SpinBox
var _paid_title_fs: SpinBox
var _paid_body_fs: SpinBox
var _paid_kind_color: ColorPickerButton
var _paid_title_color: ColorPickerButton
var _paid_body_color: ColorPickerButton
var _paid_shadow: CheckBox
var _paid_pad_h: SpinBox
var _paid_pad_v: SpinBox
var _paid_sep: SpinBox
var _paid_align: OptionButton
var _paid_pop_scale: CheckBox
var _paid_superchat: CheckBox
var _paid_gifted: CheckBox
var _paid_sub: CheckBox
var _paid_highlight: CheckBox

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

var _nav: ItemList
var _page_host: Control
var _pages: Array = []
## Draft list edited in Settings before Apply (Array of Dictionary).
var _chrome_draft: Array = []
## Active chrome box editor widgets (0–1 entries).
var _chrome_editors: Array = []
var _chrome_mgmt_sc: ScrollContainer
var _chrome_count_label: Label
var _chrome_box_pick: OptionButton
var _chrome_editor_host: VBoxContainer
var _chrome_selected_index: int = 0
var _ui_panels_nav_index: int = -1
## Which layout profile the UI panels chrome editor edits: 0 = Main, 1 = Vertical.
var _chrome_layout_profile_idx: int = 0
var _chrome_layout_pick: OptionButton
## Scene gate checkboxes: "h|key|pause" / "v|key|main" → CheckBox.
var _scene_gate_cbs: Dictionary = {}

## Vertical companion window page controls.
var _vert_enabled: CheckBox
var _vert_show_live: CheckBox
var _vert_show_title: CheckBox
var _vert_show_chrome: CheckBox
var _vert_show_id: CheckBox
var _vert_show_alerts: CheckBox
var _vert_show_paid: CheckBox
var _vert_show_bestiary: CheckBox
var _vert_show_march: CheckBox
var _vert_show_spend: CheckBox
var _vert_hide_spend_off: CheckBox
var _vert_show_free: CheckBox
var _vert_show_double: CheckBox
var _vaz_x: SpinBox
var _vaz_y: SpinBox
var _vaz_w: SpinBox
var _vaz_h: SpinBox
var _vaz_bm: SpinBox
var _viz_x: SpinBox
var _viz_y: SpinBox
var _viz_w: SpinBox
var _viz_h: SpinBox
var _viz_bm: SpinBox
var _vbz_x: SpinBox
var _vbz_y: SpinBox
var _vbz_w: SpinBox
var _vbz_h: SpinBox
var _vbz_bm: SpinBox
var _vpz_x: SpinBox
var _vpz_y: SpinBox
var _vpz_w: SpinBox
var _vpz_h: SpinBox
var _vpz_bm: SpinBox
var _vlw_bottom: SpinBox
var _vlw_left: SpinBox
var _vlw_left_top: SpinBox
var _vlw_grad_start: SpinBox
var _vlw_grad_end: SpinBox
var _vlw_feather_v: SpinBox
var _vspend_corner: OptionButton
var _vspend_mx: SpinBox
var _vspend_my: SpinBox
var _vfree_corner: OptionButton
var _vfree_mx: SpinBox
var _vfree_my: SpinBox
var _vdp_corner: OptionButton
var _vdp_mx: SpinBox
var _vdp_my: SpinBox


func _ready() -> void:
	title = "SPD Companion — settings"
	min_size = Vector2i(720, 480)
	initial_position = Window.WINDOW_INITIAL_POSITION_CENTER_MAIN_WINDOW_SCREEN
	close_requested.connect(_on_close_requested)
	visibility_changed.connect(_on_visibility_changed)
	_apply_saved_window_size()
	_build_ui()
	if CompanionSettingsPollService != null:
		CompanionSettingsPollService.applied.connect(func(_r: int) -> void: _refresh_remote_status())
		CompanionSettingsPollService.pushed.connect(func(_r: int) -> void: _refresh_remote_status())


func _on_close_requested() -> void:
	_remember_window_size()
	hide()


func _on_visibility_changed() -> void:
	if visible:
		_apply_saved_window_size()
		_sync_from_config()
	else:
		_remember_window_size()


func _apply_saved_window_size() -> void:
	var w := clampi(CompanionConfig.settings_window_width_px, min_size.x, 3840)
	var h := clampi(CompanionConfig.settings_window_height_px, min_size.y, 2160)
	size = Vector2i(w, h)


func _remember_window_size() -> void:
	if size.x < min_size.x or size.y < min_size.y:
		return
	if (
		CompanionConfig.settings_window_width_px == size.x
		and CompanionConfig.settings_window_height_px == size.y
	):
		return
	CompanionConfig.settings_window_width_px = size.x
	CompanionConfig.settings_window_height_px = size.y
	CompanionConfig.save_settings_quiet()


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

	var body := HBoxContainer.new()
	body.size_flags_vertical = Control.SIZE_EXPAND_FILL
	body.add_theme_constant_override("separation", 12)
	body.custom_minimum_size = Vector2(0, 520)
	outer.add_child(body)

	_nav = ItemList.new()
	_nav.custom_minimum_size = Vector2(188, 0)
	_nav.size_flags_vertical = Control.SIZE_EXPAND_FILL
	_nav.select_mode = ItemList.SELECT_SINGLE
	_nav.allow_reselect = true
	_nav.item_selected.connect(_on_nav_selected)
	body.add_child(_nav)

	_page_host = Control.new()
	_page_host.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_page_host.size_flags_vertical = Control.SIZE_EXPAND_FILL
	body.add_child(_page_host)
	_pages.clear()

	# --- Connection: live links to game + companion transport ---
	var conn_sc := _make_scroll_vbox()
	_add_settings_page("Connection", conn_sc)

	_net_host = _line()
	_net_port = _spin_i(1, 65535)
	_udp_port = _spin_i(1, 65535)
	_ws_recon = _spin_f(0.25, 120.0, 0.25)
	_http_on = CheckBox.new()
	_http_url = _line()
	_http_poll = _spin_f(0.25, 60.0, 0.25)
	_hud_status_panel = CheckBox.new()
	_render_max_fps = _spin_i(0, 480)
	_add_section_header(conn_sc, "Game & Streamer.bot")
	_add_rows(
		conn_sc,
		[
			["Game WebSocket host", _net_host],
			["Game WebSocket port", _net_port],
			["Streamer.bot UDP port", _udp_port],
			["WS reconnect interval (sec)", _ws_recon],
		]
	)
	_add_section_header(conn_sc, "HTTP fallback")
	_add_rows(
		conn_sc,
		[
			["HTTP fallback when WS down", _http_on],
			["HTTP fallback URL", _http_url],
			["HTTP poll interval (sec)", _http_poll],
		]
	)
	_add_section_header(conn_sc, "Display")
	_add_rows(
		conn_sc,
		[
			["Show connection status panel (F3 toggles)", _hud_status_panel],
			["Max FPS cap (0 = unlimited; 30–60 lowers GPU)", _render_max_fps],
		]
	)

	# --- Hooks: optional filesystem signals from Streamer.bot / tooling ---
	var hooks_sc := _make_scroll_vbox()
	_add_settings_page("File hooks", hooks_sc)

	_spawn_result_file = _line()
	_spawn_result_poll = _spin_f(0.05, 2.0, 0.05)
	_spawn_result_icons = CheckBox.new()
	_spend_file = _line()
	_spend_poll = _spin_f(0.05, 2.0, 0.05)
	_add_section_header(hooks_sc, "spawn_result")
	_add_rows(
		hooks_sc,
		[
			["spawn_result.txt path (optional)", _spawn_result_file],
			["spawn_result poll interval (sec)", _spawn_result_poll],
			["Use spawn_result middle field for scroll icon", _spawn_result_icons],
		]
	)
	_add_section_header(hooks_sc, "Chat spend lock file")
	_add_rows(
		hooks_sc,
		[
			["spend_disabled.txt path (chat spend ON/OFF)", _spend_file],
			["spend lock poll interval (sec)", _spend_poll],
		]
	)

	# --- Chat spend: on-screen indicator layout ---
	var spend_sc := _make_scroll_vbox()
	_add_settings_page("Chat spend", spend_sc)

	_spend_ind = CheckBox.new()
	_spend_corner = _opt(PackedStringArray(["Top-left", "Top-right", "Bottom-left", "Bottom-right"]))
	_spend_mx = _spin_i(0, 4000)
	_spend_my = _spin_i(0, 4000)
	_spend_label_fs = _spin_i(8, 64)
	_spend_pad_h = _spin_i(0, 64)
	_spend_pad_v = _spin_i(0, 64)
	_spend_chrome_style = _opt(PackedStringArray(_SpdUiArt.CHROME_STYLE_LABELS))
	_spend_chrome_scale = _spin_f(0.5, 4.0, 0.05)
	_add_section_header(spend_sc, "Indicator")
	_add_rows(
		spend_sc,
		[
			["Show on-screen Chat spend indicator", _spend_ind],
			["Panel corner", _spend_corner],
			["Margin X (px from edge)", _spend_mx],
			["Margin Y (px from edge)", _spend_my],
		]
	)
	_add_section_header(spend_sc, "Look")
	_add_rows(
		spend_sc,
		[
			["Label font size (px)", _spend_label_fs],
			["Padding horizontal (px)", _spend_pad_h],
			["Padding vertical (px)", _spend_pad_v],
			["Chrome style", _spend_chrome_style],
			["Chrome scale (border)", _spend_chrome_scale],
		]
	)

	# --- Timed free promos (Lastest UI /api/points-config → free_until) ---
	var free_sc := _make_scroll_vbox()
	_add_settings_page("Free promos", free_sc)

	_free_url = _line()
	_free_poll = _spin_f(0.5, 120.0, 0.5)
	_free_vis = CheckBox.new()
	_free_corner = _opt(PackedStringArray(["Top-left", "Top-right", "Bottom-left", "Bottom-right"]))
	_free_mx = _spin_i(0, 4000)
	_free_my = _spin_i(0, 4000)
	_free_pad_h = _spin_i(0, 64)
	_free_pad_v = _spin_i(0, 64)
	_free_fs = _spin_i(8, 36)
	_free_mw = _spin_i(160, 1600)
	_free_chrome_style = _opt(PackedStringArray(_SpdUiArt.CHROME_STYLE_LABELS))
	_free_chrome_scale = _spin_f(0.5, 4.0, 0.05)
	_add_section_header(free_sc, "Polling")
	_add_rows(
		free_sc,
		[
			["Points config URL (free_until JSON)", _free_url],
			["Poll interval (sec)", _free_poll],
			["Show panel when any promo is active", _free_vis],
		]
	)
	_add_section_header(free_sc, "Panel layout")
	_add_rows(
		free_sc,
		[
			["Panel corner", _free_corner],
			["Margin X (px)", _free_mx],
			["Margin Y (px)", _free_my],
			["Padding horizontal (px)", _free_pad_h],
			["Padding vertical (px)", _free_pad_v],
			["Font size (px)", _free_fs],
			["Max panel width (px)", _free_mw],
			["Chrome style", _free_chrome_style],
			["Chrome scale (border)", _free_chrome_scale],
		]
	)
	_dp_vis = CheckBox.new()
	_dp_poll = _spin_f(0.5, 120.0, 0.5)
	_dp_corner = _opt(PackedStringArray(["Top-left", "Top-right", "Bottom-left", "Bottom-right"]))
	_dp_mx = _spin_i(0, 4000)
	_dp_my = _spin_i(0, 4000)
	_dp_pad_h = _spin_i(0, 64)
	_dp_pad_v = _spin_i(0, 64)
	_dp_fs = _spin_i(8, 48)
	_dp_color = _color_picker()
	_dp_chrome_style = _opt(PackedStringArray(_SpdUiArt.CHROME_STYLE_LABELS))
	_dp_chrome_scale = _spin_f(0.5, 4.0, 0.05)
	_add_section_header(free_sc, "Global 2x points (!doublepoints / !fard)")
	_add_rows(
		free_sc,
		[
			["Show panel while 2x is active", _dp_vis],
			["Poll interval (sec)", _dp_poll],
			["Panel corner", _dp_corner],
			["Margin X (px)", _dp_mx],
			["Margin Y (px)", _dp_my],
			["Padding horizontal (px)", _dp_pad_h],
			["Padding vertical (px)", _dp_pad_v],
			["Font size (px)", _dp_fs],
			["Font color", _dp_color],
			["Chrome style", _dp_chrome_style],
			["Chrome scale (border)", _dp_chrome_scale],
		]
	)

	# --- Summon march (!summon chat overlay from Lastest UI) ---
	var sm_sc := _make_scroll_vbox()
	_add_settings_page("Summon march", sm_sc)

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
	_sm_crown_scale = _spin_f(1.0, 3.0, 0.05)
	_sm_crown_mob_mod = _color_picker()
	_sm_crown_glow_on = CheckBox.new()
	_sm_crown_glow_color = _color_picker()
	_sm_crown_glow_rays = _spin_i(3, 16)
	_sm_crown_glow_spin = _spin_f(0.0, 720.0, 5.0)
	_sm_crown_glow_rad = _spin_f(0.25, 3.0, 0.05)
	_sm_crown_show = CheckBox.new()
	_sm_crown_sz = _spin_f(0.1, 3.0, 0.05)
	_sm_crown_oy = _spin_f(-1.5, 1.5, 0.02)
	_sm_crown_mod = _color_picker()
	_sm_crown_user_color = _color_picker()
	_sm_crown_user_fs = _spin_i(0, 72)
	_sm_crown_star = CheckBox.new()
	_add_section_header(sm_sc, "Polling")
	_add_rows(
		sm_sc,
		[
			["Enable summon march polling", _sm_on],
			["Server base URL", _sm_url],
			["Poll interval (sec)", _sm_poll],
			["Skip events queued before app start", _sm_skip],
		]
	)
	_add_section_header(sm_sc, "March timing")
	_add_rows(
		sm_sc,
		[
			["March duration (sec)", _sm_duration],
			["Max concurrent marches", _sm_max],
		]
	)
	_add_section_header(sm_sc, "Lanes")
	_add_rows(
		sm_sc,
		[
			["Lane Y min (0=top, 1=bottom)", _sm_lane_y_min],
			["Lane Y max (0=top, 1=bottom)", _sm_lane_y_max],
			["Vertical march lane spacing (px)", _sm_lane_spacing],
			["Off-screen edge margin (px)", _sm_edge_margin],
		]
	)
	_add_section_header(sm_sc, "Monster sprite")
	_add_rows(
		sm_sc,
		[
			["Monster sprite size (px)", _sm_sprite_sz],
			["Mob walk animation (FPS)", _sm_mob_fps],
		]
	)
	_add_section_header(sm_sc, "Username label")
	_add_rows(
		sm_sc,
		[
			["Show username label", _sm_show_user],
			["Center username under sprite", _sm_user_center],
			["Username font size (px)", _sm_user_fs],
			["Username color", _sm_user_color],
			["Username offset X (px)", _sm_user_ox],
			["Username offset Y (px)", _sm_user_oy],
		]
	)
	_add_section_header(sm_sc, "Monster name label")
	_add_rows(
		sm_sc,
		[
			["Show monster name label", _sm_show_monster],
			["Monster name font size (px)", _sm_monster_fs],
			["Monster name color", _sm_monster_color],
			["Monster name offset X (px)", _sm_monster_ox],
			["Monster name offset Y (px)", _sm_monster_oy],
		]
	)
	_add_section_header(sm_sc, "Leader glow")
	_add_rows(
		sm_sc,
		[
			["Leader sprite scale", _sm_crown_scale],
			["Leader mob tint", _sm_crown_mob_mod],
			["Show leader glow (yellow / silver / bronze / red heat)", _sm_crown_glow_on],
			["Glow ray count", _sm_crown_glow_rays],
			["Glow spin (deg/sec)", _sm_crown_glow_spin],
			["Glow radius scale", _sm_crown_glow_rad],
		]
	)
	_add_section_header(sm_sc, "Crown badge")
	_add_rows(
		sm_sc,
		[
			["Show heat / gold / silver / bronze crown", _sm_crown_show],
			["Crown size (vs sprite)", _sm_crown_sz],
			["Crown Y offset (fraction)", _sm_crown_oy],
		]
	)
	_add_section_header(sm_sc, "Leader username")
	_add_rows(
		sm_sc,
		[
			["Leader username color", _sm_crown_user_color],
			["Leader username font (0=same)", _sm_crown_user_fs],
			["★ prefix on leader username", _sm_crown_star],
		]
	)

	# --- Bestiary (exp bar / sprint / heat HUD) ---
	var best_sc := _make_scroll_vbox()
	_add_settings_page("Bestiary", best_sc)

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
	_best_pad = _spin_i(0, 64)
	_best_bar_w = _spin_i(64, 1600)
	_best_bar_hs = _spin_f(0.5, 4.0, 0.1)
	_best_hud_scale = _spin_f(0.5, 4.0, 0.05)
	_best_chrome_scale = _spin_f(0.5, 4.0, 0.05)
	_best_compact = CheckBox.new()
	_best_zone_fs = _spin_i(8, 96)
	_best_header_fmt = _line()
	_best_chip_fs = _spin_i(8, 96)
	_best_chip_name_max = _spin_i(0, 32)
	_best_hall_name_max = _spin_i(0, 32)
	_best_trunc_names = CheckBox.new()
	_best_xp_text = CheckBox.new()
	_best_xp_over = CheckBox.new()
	_best_banner_sec = _spin_f(0.5, 15.0, 0.25)
	_best_banner_scale = _spin_f(0.25, 8.0, 0.05)
	_best_banner_fs = _spin_i(8, 96)
	_best_zone_color = _color_picker()
	_best_xp_color = _color_picker()
	_best_sprint_color = _color_picker()
	_best_heat_color = _color_picker()
	_best_hall_color = _color_picker()
	_best_banner_color = _color_picker()
	_add_section_header(best_sc, "Polling")
	_add_rows(
		best_sc,
		[
			["Enable Bestiary HUD", _best_on],
			["Server base URL", _best_url],
			["Poll interval (sec)", _best_poll],
		]
	)
	_add_section_header(best_sc, "Leader chips")
	_add_rows(
		best_sc,
		[
			["Show sprint leader chip", _best_sprint],
			["Show heat leader chip", _best_heat],
			["Show hall of fame strip", _best_hall],
		]
	)
	_add_section_header(best_sc, "Zone & scale")
	_add_rows(
		best_sc,
		[
			["HUD zone X (px)", _best_zx],
			["HUD zone Y (px)", _best_zy],
			["HUD zone width (px) — panel width", _best_zw],
			["HUD zone height (px, 0=fit content)", _best_zh],
			["Bottom margin from canvas edge (px)", _best_zbm],
			["Panel inner padding (px)", _best_pad],
			["HUD overall scale", _best_hud_scale],
			["Grey box chrome scale (border only)", _best_chrome_scale],
		]
	)
	_add_section_header(best_sc, "Exp bar")
	_add_rows(
		best_sc,
		[
			["Exp bar width (px; panel uses zone width)", _best_bar_w],
			["Exp bar height scale", _best_bar_hs],
			["Use compact StatusPane exp art", _best_compact],
			["Show XP fraction text", _best_xp_text],
			["XP fraction over exp bar", _best_xp_over],
			["XP fraction font color", _best_xp_color],
		]
	)
	_add_section_header(best_sc, "Labels & colors")
	_add_rows(
		best_sc,
		[
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
		]
	)
	_add_section_header(best_sc, "Level-up banner")
	_add_rows(
		best_sc,
		[
			["Level-up banner duration (sec)", _best_banner_sec],
			["Level-up banner image scale", _best_banner_scale],
			["Level-up banner text size (px)", _best_banner_fs],
			["Level-up banner text color", _best_banner_color],
		]
	)

	# --- Remote settings (Flask /points-config companion section) ---
	var remote_sc := _make_scroll_vbox()
	_add_settings_page("Remote", remote_sc)
	_remote_on = CheckBox.new()
	_remote_url = _line()
	_remote_poll = _spin_f(0.5, 60.0, 0.5)
	_remote_push = CheckBox.new()
	_remote_status = Label.new()
	_remote_status.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_add_section_header(remote_sc, "Flask companion settings")
	var remote_note := _section_note()
	remote_note.text = (
		"When poll is on, HTML (/points-config → Companion) is the day-to-day editor. "
		+ "Upload once to seed Flask, then leave Push on Apply off so HTML wins. "
		+ "Does not sync OBS password or file paths."
	)
	(remote_sc.get_node("InnerVBox") as VBoxContainer).add_child(remote_note)
	_add_rows(
		remote_sc,
		[
			["Enable remote settings poll", _remote_on],
			["Base URL (Flask)", _remote_url],
			["Poll interval (sec)", _remote_poll],
			["Push to Flask on Apply & save", _remote_push],
		]
	)
	var remote_vb := remote_sc.get_node("InnerVBox") as VBoxContainer
	remote_vb.add_child(_remote_status)
	var remote_btns := HBoxContainer.new()
	remote_btns.add_theme_constant_override("separation", 8)
	var pull_btn := Button.new()
	pull_btn.text = "Pull from Flask now"
	pull_btn.pressed.connect(_on_remote_pull)
	var push_btn := Button.new()
	push_btn.text = "Upload current to Flask"
	push_btn.pressed.connect(_on_remote_push)
	remote_btns.add_child(pull_btn)
	remote_btns.add_child(push_btn)
	remote_vb.add_child(remote_btns)

	# --- OBS (optional scene-driven title vs ID layer) ---
	var obs_sc := _make_scroll_vbox()
	_add_settings_page("OBS", obs_sc)

	_obs_on = CheckBox.new()
	_obs_host = _line()
	_obs_port = _spin_i(1, 65535)
	_obs_pw = _line()
	_obs_pw.secret = true
	_obs_scene = _line()
	_obs_main_scene = _line()
	_obs_log_scene = CheckBox.new()
	_obs_recon = _spin_f(0.5, 120.0, 0.5)
	_win_transparent = CheckBox.new()
	_lw_bottom = _spin_i(0, 4000)
	_lw_left = _spin_i(0, 4000)
	_lw_left_top = _spin_i(0, 4000)
	_lw_grad_start = _spin_f(0.0, 1.0, 0.01)
	_lw_grad_end = _spin_f(0.0, 1.0, 0.01)
	_lw_feather_v = _spin_i(0, 512)
	_add_section_header(obs_sc, "obs-websocket")
	_add_rows(
		obs_sc,
		[
			["Sync title vs ID layer (obs-websocket v5)", _obs_on],
			["WebSocket host", _obs_host],
			["WebSocket port (default 4455)", _obs_port],
			["WebSocket password (if enabled in OBS)", _obs_pw],
			["LIVE - PAUSE if scene name contains", _obs_scene],
			["LIVE - MAIN if scene name contains", _obs_main_scene],
			["Log program scene to Output (debug)", _obs_log_scene],
			["Reconnect interval (sec)", _obs_recon],
		]
	)
	_add_section_header(obs_sc, "Window & live water")
	_add_rows(
		obs_sc,
		[
			["Transparent window (OBS sees layers beneath gaps)", _win_transparent],
			["Live water: bottom bar height (px)", _lw_bottom],
			["Live water: left strip width (px)", _lw_left],
			["Live water: left strip top inset (px, below chat)", _lw_left_top],
			["Live water: fade start Y (0=top, 1=bottom)", _lw_grad_start],
			["Live water: fade end Y (full black)", _lw_grad_end],
			["Live water: edge feather vertical (px)", _lw_feather_v],
		]
	)

	_build_scene_gates_page()

	var paid_sc := _make_scroll_vbox()
	_add_settings_page("Paid notices", paid_sc)
	_paid_on = CheckBox.new()
	_paid_qmax = _spin_i(1, 32)
	_paid_ttl = _spin_f(0.5, 60.0, 0.25)
	_paid_fade_in = _spin_f(0.05, 5.0, 0.05)
	_paid_fade_out = _spin_f(0.05, 5.0, 0.05)
	_paid_zx = _spin_i(0, 4000)
	_paid_zy = _spin_i(0, 4000)
	_paid_zw = _spin_i(64, 1920)
	_paid_zh = _spin_i(0, 1080)
	_paid_zbm = _spin_i(0, 400)
	_paid_chrome_style = _opt(PackedStringArray(_SpdUiArt.CHROME_STYLE_LABELS))
	_paid_chrome_scale = _spin_f(0.5, 4.0, 0.05)
	_paid_kind_fs = _spin_i(8, 96)
	_paid_title_fs = _spin_i(8, 96)
	_paid_body_fs = _spin_i(8, 96)
	_paid_kind_color = _color_picker()
	_paid_title_color = _color_picker()
	_paid_body_color = _color_picker()
	_paid_shadow = CheckBox.new()
	_paid_pad_h = _spin_i(0, 64)
	_paid_pad_v = _spin_i(0, 64)
	_paid_sep = _spin_i(0, 48)
	_paid_align = _opt(PackedStringArray(["Left", "Center"]))
	_paid_pop_scale = CheckBox.new()
	_paid_superchat = CheckBox.new()
	_paid_gifted = CheckBox.new()
	_paid_sub = CheckBox.new()
	_paid_highlight = CheckBox.new()
	var paid_note := _section_note()
	paid_note.text = (
		"Streamer.bot UDP JSON with a ui field (superchat, gifted_membership, sub, highlight). "
		+ "Does not go to the game — companion overlay only. Keep Speaker.bot as a separate sub-action. "
		+ "Even font sizes (16/24/32) look sharpest with the pixel font; leave pop-scale off for crisp text."
	)
	paid_note.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	(paid_sc.get_node("InnerVBox") as VBoxContainer).add_child(paid_note)
	_add_section_header(paid_sc, "Timing")
	_add_rows(
		paid_sc,
		[
			["Enable paid / highlight notices", _paid_on],
			["Queue max", _paid_qmax],
			["Default hold (sec)", _paid_ttl],
			["Fade in (sec)", _paid_fade_in],
			["Fade out (sec)", _paid_fade_out],
		]
	)
	_add_section_header(paid_sc, "Position & chrome")
	_add_rows(
		paid_sc,
		[
			["Zone X (px)", _paid_zx],
			["Zone Y (px)", _paid_zy],
			["Zone width (px)", _paid_zw],
			["Zone height (0=auto)", _paid_zh],
			["Bottom margin if height is auto", _paid_zbm],
			["Chrome style", _paid_chrome_style],
			["Chrome scale (border)", _paid_chrome_scale],
			["Padding horizontal (px)", _paid_pad_h],
			["Padding vertical (px)", _paid_pad_v],
			["Line separation (px)", _paid_sep],
			["Text align", _paid_align],
		]
	)
	_add_section_header(paid_sc, "Typography")
	_add_rows(
		paid_sc,
		[
			["Banner font size (px)", _paid_kind_fs],
			["Title font size (px)", _paid_title_fs],
			["Body font size (px)", _paid_body_fs],
			["Banner color", _paid_kind_color],
			["Title color", _paid_title_color],
			["Body color", _paid_body_color],
			["Text shadow", _paid_shadow],
			["Pop-in scale animation", _paid_pop_scale],
		]
	)
	_add_section_header(paid_sc, "Event types")
	_add_rows(
		paid_sc,
		[
			["YouTube Super Chat", _paid_superchat],
			["YouTube gifted membership / Twitch gift subs", _paid_gifted],
			["Twitch sub / YouTube new member", _paid_sub],
			["Twitch Highlight My Message", _paid_highlight],
		]
	)
	var paid_scene_note := _section_note()
	paid_scene_note.text = (
		"When to show paid notices (LIVE - PAUSE / LIVE - MAIN / other) is under Scene gates "
		+ "for Horizontal and Vertical."
	)
	paid_scene_note.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	(paid_sc.get_node("InnerVBox") as VBoxContainer).add_child(paid_scene_note)

	var al_sc := _make_scroll_vbox()
	_add_settings_page("Alerts", al_sc)

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
	_add_section_header(al_sc, "Queue & timing")
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
		]
	)
	_add_section_header(al_sc, "Which alerts")
	_add_rows(
		al_sc,
		[
			["Show ping_result alerts", _ping_alerts],
			["Show failed command alerts", _fail_alerts],
		]
	)

	_az_x = _spin_i(0, 4000)
	_az_y = _spin_i(0, 4000)
	_az_w = _spin_i(32, 4000)
	_az_h = _spin_i(0, 4000)
	_az_bm = _spin_i(0, 400)
	_ata = _opt(["left", "center"])
	_alert_chrome_style = _opt(PackedStringArray(_SpdUiArt.CHROME_STYLE_LABELS))
	_alert_chrome_scale = _spin_f(0.5, 4.0, 0.05)
	_alert_title_fs = _spin_i(6, 72)
	_alert_subtitle_fs = _spin_i(6, 72)
	_alert_pad_h = _spin_i(0, 64)
	_alert_pad_v = _spin_i(0, 64)
	_alert_icon_sz = _spin_i(24, 256)
	_mob_idle_fps = _spin_f(0.25, 24.0, 0.25)
	_add_section_header(al_sc, "Toast zone")
	_add_rows(
		al_sc,
		[
			["Toast zone X (px)", _az_x],
			["Toast zone Y (px)", _az_y],
			["Toast zone width (px)", _az_w],
			["Toast zone height (0=auto)", _az_h],
			["Bottom margin if height is auto", _az_bm],
		]
	)
	_add_section_header(al_sc, "Chrome & text")
	_add_rows(
		al_sc,
		[
			["Chrome style", _alert_chrome_style],
			["Chrome scale (border)", _alert_chrome_scale],
			["Title/subtitle align", _ata],
			["Title font size (px)", _alert_title_fs],
			["Subtitle font size (px)", _alert_subtitle_fs],
			["Padding horizontal (px)", _alert_pad_h],
			["Padding vertical (px)", _alert_pad_v],
			["Command icon size (px, scroll + mob)", _alert_icon_sz],
			["Mob idle animation (FPS)", _mob_idle_fps],
		]
	)

	var id_sc := _make_scroll_vbox()
	_add_settings_page("ID overlay", id_sc)

	_id_on = CheckBox.new()
	_iz_x = _spin_i(0, 4000)
	_iz_y = _spin_i(0, 4000)
	_iz_w = _spin_i(32, 4000)
	_iz_h = _spin_i(0, 4000)
	_iz_bm = _spin_i(0, 400)
	_add_section_header(id_sc, "Strip")
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

	_id_cw = _spin_i(16, 200)
	_id_ch = _spin_i(16, 200)
	_id_pad = _spin_i(0, 32)
	_id_iconf = _spin_f(0.1, 0.95, 0.01)
	_id_flow = _spin_i(0, 48)
	_id_block = _spin_i(0, 80)
	_icon_cell_bg = ColorPickerButton.new()
	_icon_cell_bg.edit_alpha = true
	_icon_cell_bg.custom_minimum_size = Vector2(140, 28)
	_add_section_header(id_sc, "Cells")
	_add_rows(
		id_sc,
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

	# --- Vertical companion (1080×1920 second window) ---
	var vert_sc := _make_scroll_vbox() as ScrollContainer
	_add_settings_page("Vertical", vert_sc)
	_vert_enabled = CheckBox.new()
	_vert_show_live = CheckBox.new()
	_vert_show_title = CheckBox.new()
	_vert_show_chrome = CheckBox.new()
	_vert_show_id = CheckBox.new()
	_vert_show_alerts = CheckBox.new()
	_vert_show_paid = CheckBox.new()
	_vert_show_bestiary = CheckBox.new()
	_vert_show_march = CheckBox.new()
	_vert_show_spend = CheckBox.new()
	_vert_hide_spend_off = CheckBox.new()
	_vert_show_free = CheckBox.new()
	_vert_show_double = CheckBox.new()
	_vaz_x = _spin_i(0, 4000)
	_vaz_y = _spin_i(0, 4000)
	_vaz_w = _spin_i(32, 4000)
	_vaz_h = _spin_i(0, 4000)
	_vaz_bm = _spin_i(0, 4000)
	_viz_x = _spin_i(0, 4000)
	_viz_y = _spin_i(0, 4000)
	_viz_w = _spin_i(32, 4000)
	_viz_h = _spin_i(0, 4000)
	_viz_bm = _spin_i(0, 4000)
	_vbz_x = _spin_i(0, 4000)
	_vbz_y = _spin_i(0, 4000)
	_vbz_w = _spin_i(32, 4000)
	_vbz_h = _spin_i(0, 4000)
	_vbz_bm = _spin_i(0, 4000)
	_vpz_x = _spin_i(0, 4000)
	_vpz_y = _spin_i(0, 4000)
	_vpz_w = _spin_i(32, 4000)
	_vpz_h = _spin_i(0, 4000)
	_vpz_bm = _spin_i(0, 4000)
	_vlw_bottom = _spin_i(0, 4000)
	_vlw_left = _spin_i(0, 4000)
	_vlw_left_top = _spin_i(0, 4000)
	_vlw_grad_start = _spin_f(0.0, 1.0, 0.01)
	_vlw_grad_end = _spin_f(0.0, 1.0, 0.01)
	_vlw_feather_v = _spin_i(0, 512)
	_vspend_corner = _opt(PackedStringArray(["Top-left", "Top-right", "Bottom-left", "Bottom-right"]))
	_vspend_mx = _spin_i(0, 4000)
	_vspend_my = _spin_i(0, 4000)
	_vfree_corner = _opt(PackedStringArray(["Top-left", "Top-right", "Bottom-left", "Bottom-right"]))
	_vfree_mx = _spin_i(0, 4000)
	_vfree_my = _spin_i(0, 4000)
	_vdp_corner = _opt(PackedStringArray(["Top-left", "Top-right", "Bottom-left", "Bottom-right"]))
	_vdp_mx = _spin_i(0, 4000)
	_vdp_my = _spin_i(0, 4000)
	var vert_note := _section_note()
	vert_note.text = (
		"Second 1080×1920 window for vertical OBS (F4). Capture it separately — "
		+ "no crop of the main companion. Per-scene show/hide for this window is under "
		+ "Scene gates → Vertical. Master toggles below still enable/disable elements. "
		+ "Chrome boxes for vertical are edited under UI panels → layout Vertical."
	)
	vert_note.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	(vert_sc.get_node("InnerVBox") as VBoxContainer).add_child(vert_note)
	_add_section_header(vert_sc, "Window")
	_add_rows(vert_sc, [["Enable vertical companion window", _vert_enabled]])
	_add_section_header(vert_sc, "Show elements")
	_add_rows(
		vert_sc,
		[
			["Live water", _vert_show_live],
			["Title (pause)", _vert_show_title],
			["Chrome boxes", _vert_show_chrome],
			["ID overlay", _vert_show_id],
			["Alerts", _vert_show_alerts],
			["Paid notices", _vert_show_paid],
			["Bestiary", _vert_show_bestiary],
			["Summon march", _vert_show_march],
			["Chat spend", _vert_show_spend],
			["Hide chat spend when spending is off", _vert_hide_spend_off],
			["Free promos", _vert_show_free],
			["2x points countdown", _vert_show_double],
		]
	)
	_add_section_header(vert_sc, "Alert zone")
	_add_rows(
		vert_sc,
		[
			["X", _vaz_x],
			["Y", _vaz_y],
			["Width", _vaz_w],
			["Height (0 = auto)", _vaz_h],
			["Bottom margin", _vaz_bm],
		]
	)
	_add_section_header(vert_sc, "ID zone")
	_add_rows(
		vert_sc,
		[
			["X", _viz_x],
			["Y", _viz_y],
			["Width", _viz_w],
			["Height (0 = auto)", _viz_h],
			["Bottom margin", _viz_bm],
		]
	)
	_add_section_header(vert_sc, "Bestiary zone")
	_add_rows(
		vert_sc,
		[
			["X", _vbz_x],
			["Y", _vbz_y],
			["Width", _vbz_w],
			["Height (0 = auto)", _vbz_h],
			["Bottom margin", _vbz_bm],
		]
	)
	_add_section_header(vert_sc, "Paid notice zone")
	_add_rows(
		vert_sc,
		[
			["X", _vpz_x],
			["Y", _vpz_y],
			["Width", _vpz_w],
			["Height", _vpz_h],
			["Bottom margin", _vpz_bm],
		]
	)
	_add_section_header(vert_sc, "Live water L-shape")
	_add_rows(
		vert_sc,
		[
			["Bottom bar height (px)", _vlw_bottom],
			["Left strip width (px)", _vlw_left],
			["Left strip top inset (px)", _vlw_left_top],
			["Gradient fade start", _vlw_grad_start],
			["Gradient fade end", _vlw_grad_end],
			["Edge feather vertical (px)", _vlw_feather_v],
		]
	)
	_add_section_header(vert_sc, "Corners")
	_add_rows(
		vert_sc,
		[
			["Spend corner", _vspend_corner],
			["Spend margin X", _vspend_mx],
			["Spend margin Y", _vspend_my],
			["Free promos corner", _vfree_corner],
			["Free promos margin X", _vfree_mx],
			["Free promos margin Y", _vfree_my],
			["2x points corner", _vdp_corner],
			["2x points margin X", _vdp_mx],
			["2x points margin Y", _vdp_my],
		]
	)

	# --- Placeable UI panels (chrome boxes, etc.) ---
	_chrome_mgmt_sc = _make_scroll_vbox() as ScrollContainer
	_add_settings_page("UI panels", _chrome_mgmt_sc)
	_ui_panels_nav_index = _pages.size() - 1
	var chrome_vb := _chrome_mgmt_sc.get_node("InnerVBox") as VBoxContainer
	var mgmt_note := _section_note()
	mgmt_note.text = (
		"Add SPD chrome panels to the canvas. Pick a box below to edit "
		+ "(style, optional header/body text, zone, chrome scale, Live/Pause). "
		+ "Styles match in-game Chrome (window, toast, buttons, gem, scroll, tabs, …). "
		+ ("Leave header and body empty for a blank frame. Max %d. " % CompanionConfig.CHROME_BOXES_MAX)
		+ "Use Layout to edit Main vs Vertical chrome lists separately."
	)
	mgmt_note.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	chrome_vb.add_child(mgmt_note)
	_chrome_layout_pick = _opt(PackedStringArray(["Main (1920×1080)", "Vertical (1080×1920)"]))
	_chrome_layout_pick.item_selected.connect(_on_chrome_layout_picked)
	chrome_vb.add_child(_form_row("Layout", _chrome_layout_pick))
	_chrome_count_label = Label.new()
	chrome_vb.add_child(_chrome_count_label)
	var add_chrome := Button.new()
	add_chrome.text = "Add chrome box"
	add_chrome.pressed.connect(_on_add_chrome_box)
	chrome_vb.add_child(add_chrome)
	_chrome_box_pick = OptionButton.new()
	_chrome_box_pick.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_chrome_box_pick.item_selected.connect(_on_chrome_box_picked)
	chrome_vb.add_child(_form_row("Edit box", _chrome_box_pick))
	_chrome_editor_host = VBoxContainer.new()
	_chrome_editor_host.add_theme_constant_override("separation", 8)
	_chrome_editor_host.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	chrome_vb.add_child(_chrome_editor_host)

	if _nav.item_count > 0:
		_nav.select(0)
		_show_settings_page(0)

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

	if OS.has_feature("editor"):
		var ship := Button.new()
		ship.text = "Save as export defaults"
		ship.tooltip_text = (
			"Copy current settings into res://defaults/ and bump defaults_revision. "
			+ "Exported builds overwrite user:// when that revision changes."
		)
		ship.pressed.connect(_on_save_as_export_defaults)
		row.add_child(ship)

	var close_b := Button.new()
	close_b.text = "Close"
	close_b.pressed.connect(hide)
	row.add_child(close_b)


func _build_scene_gates_page() -> void:
	var sc := _make_scroll_vbox() as ScrollContainer
	_add_settings_page("Scene gates", sc)
	_scene_gate_cbs.clear()
	var note := _section_note()
	note.text = (
		"Per overlay: show on LIVE - PAUSE, LIVE - MAIN, or other OBS scenes. "
		+ "Horizontal = main 1920×1080 window; Vertical = F4 companion. "
		+ "Chrome boxes also have per-box scene toggles under UI panels."
	)
	note.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	(sc.get_node("InnerVBox") as VBoxContainer).add_child(note)
	for layout_id in ["h", "v"]:
		var layout_title := (
			"Horizontal (main window)" if layout_id == "h" else "Vertical (F4 window)"
		)
		_add_section_header(sc, layout_title)
		for key in UiLayoutData.element_keys():
			var row := HBoxContainer.new()
			row.add_theme_constant_override("separation", 10)
			var lab := Label.new()
			lab.text = UiLayoutData.element_label(key)
			lab.custom_minimum_size = Vector2(160, 0)
			lab.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
			row.add_child(lab)
			for bucket in ["pause", "main", "other"]:
				var cb := CheckBox.new()
				cb.text = {
					"pause": "PAUSE",
					"main": "MAIN",
					"other": "Other",
				}[bucket]
				cb.size_flags_horizontal = Control.SIZE_EXPAND_FILL
				row.add_child(cb)
				_scene_gate_cbs["%s|%s|%s" % [layout_id, key, bucket]] = cb
			(sc.get_node("InnerVBox") as VBoxContainer).add_child(row)


func _sync_scene_gates_from_config() -> void:
	CompanionConfig.ensure_main_scene_show()
	var h: Dictionary = CompanionConfig.main_scene_show
	var L := CompanionConfig.vertical_layout
	if L == null:
		L = UiLayoutData.default_vertical()
	L.ensure_scene_show()
	var v: Dictionary = L.scene_show
	for key in UiLayoutData.element_keys():
		var he: Dictionary = UiLayoutData.normalize_scene_entry(
			h.get(key, null), {"pause": true, "main": true, "other": true}
		)
		var ve: Dictionary = UiLayoutData.normalize_scene_entry(
			v.get(key, null), {"pause": true, "main": true, "other": true}
		)
		for bucket in ["pause", "main", "other"]:
			var hcb: CheckBox = _scene_gate_cbs.get("h|%s|%s" % [key, bucket]) as CheckBox
			var vcb: CheckBox = _scene_gate_cbs.get("v|%s|%s" % [key, bucket]) as CheckBox
			if hcb:
				hcb.button_pressed = bool(he.get(bucket, true))
			if vcb:
				vcb.button_pressed = bool(ve.get(bucket, true))


func _apply_scene_gates_to_config() -> void:
	var h: Dictionary = {}
	var v: Dictionary = {}
	for key in UiLayoutData.element_keys():
		var he := {}
		var ve := {}
		for bucket in ["pause", "main", "other"]:
			var hcb: CheckBox = _scene_gate_cbs.get("h|%s|%s" % [key, bucket]) as CheckBox
			var vcb: CheckBox = _scene_gate_cbs.get("v|%s|%s" % [key, bucket]) as CheckBox
			he[bucket] = hcb.button_pressed if hcb else true
			ve[bucket] = vcb.button_pressed if vcb else true
		h[key] = he
		v[key] = ve
	CompanionConfig.main_scene_show = h
	CompanionConfig.ensure_main_scene_show()
	if CompanionConfig.vertical_layout == null:
		CompanionConfig.vertical_layout = UiLayoutData.default_vertical()
	CompanionConfig.vertical_layout.scene_show = v
	CompanionConfig.vertical_layout.ensure_scene_show()
	# Keep vertical master toggles in sync (any scene on → show_* true).
	var VL := CompanionConfig.vertical_layout
	VL.show_title = VL.allows_scene("title", CompanionConfig.SCENE_UNKNOWN)
	VL.show_live_water = VL.allows_scene("live_water", CompanionConfig.SCENE_UNKNOWN)
	VL.show_chrome_boxes = VL.allows_scene("chrome_boxes", CompanionConfig.SCENE_UNKNOWN)
	VL.show_id_overlay = VL.allows_scene("id_overlay", CompanionConfig.SCENE_UNKNOWN)
	VL.show_alerts = VL.allows_scene("alerts", CompanionConfig.SCENE_UNKNOWN)
	VL.show_paid_notices = VL.allows_scene("paid_notices", CompanionConfig.SCENE_UNKNOWN)
	VL.show_bestiary = VL.allows_scene("bestiary", CompanionConfig.SCENE_UNKNOWN)
	VL.show_summon_march = VL.allows_scene("summon_march", CompanionConfig.SCENE_UNKNOWN)
	VL.show_spend_indicator = VL.allows_scene("spend_indicator", CompanionConfig.SCENE_UNKNOWN)
	VL.show_free_promos = VL.allows_scene("free_promos", CompanionConfig.SCENE_UNKNOWN)
	VL.show_double_points = VL.allows_scene("double_points", CompanionConfig.SCENE_UNKNOWN)
	# Legacy paid fields ← horizontal paid_notices gates.
	var paid_h: Dictionary = CompanionConfig.main_scene_show.get("paid_notices", {})
	CompanionConfig.paid_notice_show_on_pause = bool(paid_h.get("pause", true))
	CompanionConfig.paid_notice_show_on_live = (
		bool(paid_h.get("main", true)) or bool(paid_h.get("other", true))
	)


func _make_scroll_vbox() -> Control:
	var sc := ScrollContainer.new()
	sc.name = "Scroll"
	sc.size_flags_vertical = Control.SIZE_EXPAND_FILL
	sc.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	sc.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	var vb := VBoxContainer.new()
	vb.name = "InnerVBox"
	vb.add_theme_constant_override("separation", 12)
	vb.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	vb.custom_minimum_size = Vector2(620, 0)
	sc.add_child(vb)
	return sc


func _add_settings_page(page_title: String, page: Control) -> void:
	page.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	page.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	page.size_flags_vertical = Control.SIZE_EXPAND_FILL
	page.visible = _pages.is_empty()
	_page_host.add_child(page)
	_nav.add_item(page_title)
	_pages.append(page)


func _on_nav_selected(index: int) -> void:
	_show_settings_page(index)


func _show_settings_page(index: int) -> void:
	if index < 0 or index >= _pages.size():
		return
	for i in range(_pages.size()):
		(_pages[i] as Control).visible = (i == index)
	if _nav != null and _nav.item_count > index:
		var sel := _nav.get_selected_items()
		if sel.is_empty() or sel[0] != index:
			_nav.select(index)


func _add_rows(parent_sc: ScrollContainer, rows: Array) -> void:
	var vb: VBoxContainer = parent_sc.get_node("InnerVBox") as VBoxContainer
	for row in rows:
		var pair: Array = row
		vb.add_child(_form_row(str(pair[0]), pair[1] as Control))


func _add_section_header(parent_sc: ScrollContainer, text: String) -> void:
	var vb: VBoxContainer = parent_sc.get_node("InnerVBox") as VBoxContainer
	vb.add_child(_section_header(text))


func _form_row(label_text: String, control: Control) -> HBoxContainer:
	var hb := HBoxContainer.new()
	hb.add_theme_constant_override("separation", 12)
	var lab := Label.new()
	lab.text = label_text
	lab.custom_minimum_size = Vector2(260, 0)
	lab.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	lab.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	hb.add_child(lab)
	if control is SpinBox:
		hb.add_child(_wrap_spin_with_slider(control as SpinBox))
	elif control is CheckBox:
		control.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		hb.add_child(control)
	else:
		control.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		hb.add_child(control)
	return hb


func _wrap_spin_with_slider(spin: SpinBox) -> HBoxContainer:
	var row := HBoxContainer.new()
	row.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_theme_constant_override("separation", 6)

	var slider := HSlider.new()
	slider.min_value = spin.min_value
	slider.max_value = spin.max_value
	slider.step = spin.step
	slider.value = spin.value
	slider.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	slider.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	slider.custom_minimum_size = Vector2(100, 22)
	slider.scrollable = false
	row.add_child(slider)

	var minus := Button.new()
	minus.text = "−"
	minus.tooltip_text = "Decrease"
	minus.custom_minimum_size = Vector2(36, 0)
	minus.focus_mode = Control.FOCUS_NONE
	row.add_child(minus)

	spin.custom_minimum_size = Vector2(96, 0)
	spin.size_flags_horizontal = Control.SIZE_SHRINK_END
	row.add_child(spin)

	var plus := Button.new()
	plus.text = "+"
	plus.tooltip_text = "Increase"
	plus.custom_minimum_size = Vector2(36, 0)
	plus.focus_mode = Control.FOCUS_NONE
	row.add_child(plus)

	slider.value_changed.connect(_on_settings_slider_changed.bind(spin))
	spin.value_changed.connect(_on_settings_spin_changed.bind(slider))
	minus.pressed.connect(_nudge_settings_spin.bind(spin, -1.0))
	plus.pressed.connect(_nudge_settings_spin.bind(spin, 1.0))
	return row


func _on_settings_slider_changed(value: float, spin: SpinBox) -> void:
	if not is_equal_approx(spin.value, value):
		spin.value = value


func _on_settings_spin_changed(value: float, slider: HSlider) -> void:
	if not is_equal_approx(slider.value, value):
		slider.set_value_no_signal(value)


func _nudge_settings_spin(spin: SpinBox, direction: float) -> void:
	var step: float = spin.step if spin.step > 0.0 else 1.0
	spin.value = clampf(spin.value + direction * step, spin.min_value, spin.max_value)


func _line() -> LineEdit:
	var le := LineEdit.new()
	le.clear_button_enabled = true
	return le


func _section_header(text: String) -> Control:
	var header := VBoxContainer.new()
	header.add_theme_constant_override("separation", 4)
	header.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var spacer := Control.new()
	spacer.custom_minimum_size = Vector2(0, 10)
	header.add_child(spacer)
	var lab := Label.new()
	lab.text = text
	lab.add_theme_font_size_override("font_size", 15)
	lab.modulate = Color(0.95, 0.92, 0.85, 1.0)
	lab.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	header.add_child(lab)
	var sep := HSeparator.new()
	sep.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	header.add_child(sep)
	return header


func _section_note() -> Label:
	var lab := Label.new()
	lab.text = ""
	lab.modulate = Color(0.75, 0.75, 0.8, 1.0)
	return lab


func _spin_i(mn: int, mx: int) -> SpinBox:
	var s := SpinBox.new()
	s.min_value = mn
	s.max_value = mx
	s.step = 1
	s.rounded = true
	s.custom_minimum_size = Vector2(96, 0)
	return s


func _spin_f(mn: float, mx: float, step: float) -> SpinBox:
	var s := SpinBox.new()
	s.min_value = mn
	s.max_value = mx
	s.step = step
	s.allow_greater = false
	s.allow_lesser = false
	s.update_on_text_changed = true
	s.custom_minimum_size = Vector2(96, 0)
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
	_spend_pad_h.value = CompanionConfig.spend_indicator_padding_h_px
	_spend_pad_v.value = CompanionConfig.spend_indicator_padding_v_px
	_spend_chrome_style.select(
		_SpdUiArt.chrome_style_id_index(CompanionConfig.spend_indicator_chrome_style)
	)
	_spend_chrome_scale.value = CompanionConfig.spend_indicator_chrome_scale
	_free_url.text = CompanionConfig.free_promos_http_url
	_free_poll.value = CompanionConfig.free_promos_poll_sec
	_free_vis.button_pressed = CompanionConfig.free_promos_panel_visible
	_free_corner.select(clampi(CompanionConfig.free_promos_corner, 0, 3))
	_free_mx.value = CompanionConfig.free_promos_margin_x
	_free_my.value = CompanionConfig.free_promos_margin_y
	_free_fs.value = CompanionConfig.free_promos_font_size_px
	_free_pad_h.value = CompanionConfig.free_promos_padding_h_px
	_free_pad_v.value = CompanionConfig.free_promos_padding_v_px
	_free_mw.value = CompanionConfig.free_promos_max_width_px
	_free_chrome_style.select(
		_SpdUiArt.chrome_style_id_index(CompanionConfig.free_promos_chrome_style)
	)
	_free_chrome_scale.value = CompanionConfig.free_promos_chrome_scale
	_dp_vis.button_pressed = CompanionConfig.double_points_panel_visible
	_dp_poll.value = CompanionConfig.double_points_poll_sec
	_dp_corner.select(clampi(CompanionConfig.double_points_corner, 0, 3))
	_dp_mx.value = CompanionConfig.double_points_margin_x
	_dp_my.value = CompanionConfig.double_points_margin_y
	_dp_pad_h.value = CompanionConfig.double_points_padding_h_px
	_dp_pad_v.value = CompanionConfig.double_points_padding_v_px
	_dp_fs.value = CompanionConfig.double_points_font_size_px
	_dp_color.color = CompanionConfig.double_points_font_color
	_dp_chrome_style.select(
		_SpdUiArt.chrome_style_id_index(CompanionConfig.double_points_chrome_style)
	)
	_dp_chrome_scale.value = CompanionConfig.double_points_chrome_scale
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
	_sm_crown_scale.value = CompanionConfig.summon_crowned_sprite_scale
	_sm_crown_mob_mod.color = CompanionConfig.summon_crowned_mob_modulate
	_sm_crown_glow_on.button_pressed = CompanionConfig.summon_crowned_show_glow
	_sm_crown_glow_color.color = CompanionConfig.summon_crowned_glow_color
	_sm_crown_glow_rays.value = CompanionConfig.summon_crowned_glow_rays
	_sm_crown_glow_spin.value = CompanionConfig.summon_crowned_glow_spin_deg
	_sm_crown_glow_rad.value = CompanionConfig.summon_crowned_glow_radius_scale
	_sm_crown_show.button_pressed = CompanionConfig.summon_crowned_show_crown
	_sm_crown_sz.value = CompanionConfig.summon_crowned_crown_scale
	_sm_crown_oy.value = CompanionConfig.summon_crowned_crown_offset_y
	_sm_crown_mod.color = CompanionConfig.summon_crowned_crown_modulate
	_sm_crown_user_color.color = CompanionConfig.summon_crowned_username_color
	_sm_crown_user_fs.value = CompanionConfig.summon_crowned_username_font_size_px
	_sm_crown_star.button_pressed = CompanionConfig.summon_crowned_show_star_prefix
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
	_best_pad.value = CompanionConfig.bestiary_panel_pad_px
	_best_hud_scale.value = CompanionConfig.bestiary_hud_scale
	_best_chrome_scale.value = CompanionConfig.bestiary_chrome_scale
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
	_best_xp_over.button_pressed = CompanionConfig.bestiary_xp_text_over_bar
	_best_banner_sec.value = CompanionConfig.bestiary_level_up_banner_sec
	_best_banner_scale.value = CompanionConfig.bestiary_level_up_banner_scale
	_best_banner_fs.value = CompanionConfig.bestiary_level_up_banner_font_size_px
	_best_zone_color.color = CompanionConfig.bestiary_zone_font_color
	_best_xp_color.color = CompanionConfig.bestiary_xp_font_color
	_best_sprint_color.color = CompanionConfig.bestiary_sprint_font_color
	_best_heat_color.color = CompanionConfig.bestiary_heat_font_color
	_best_hall_color.color = CompanionConfig.bestiary_hall_font_color
	_best_banner_color.color = CompanionConfig.bestiary_banner_font_color
	_remote_on.button_pressed = CompanionConfig.remote_settings_enabled
	_remote_url.text = CompanionConfig.remote_settings_base_url
	_remote_poll.value = CompanionConfig.remote_settings_poll_sec
	_remote_push.button_pressed = CompanionConfig.remote_settings_push_on_save
	_refresh_remote_status()
	_obs_on.button_pressed = CompanionConfig.obs_scene_sync_enabled
	_obs_host.text = CompanionConfig.obs_ws_host
	_obs_port.value = CompanionConfig.obs_ws_port
	_obs_pw.text = CompanionConfig.obs_ws_password
	_obs_scene.text = CompanionConfig.obs_pause_scene_name
	_obs_main_scene.text = CompanionConfig.obs_main_scene_name
	_obs_log_scene.button_pressed = CompanionConfig.obs_log_program_scene
	_obs_recon.value = CompanionConfig.obs_reconnect_sec
	_win_transparent.button_pressed = CompanionConfig.window_per_pixel_transparency_enabled
	_lw_bottom.value = CompanionConfig.live_water_bottom_bar_px
	_lw_left.value = CompanionConfig.live_water_left_strip_px
	_lw_left_top.value = CompanionConfig.live_water_left_strip_top_px
	_lw_grad_start.value = CompanionConfig.live_water_gradient_fade_start
	_lw_grad_end.value = CompanionConfig.live_water_gradient_fade_end
	_lw_feather_v.value = CompanionConfig.live_water_edge_feather_v_px

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
	_alert_chrome_style.select(
		_SpdUiArt.chrome_style_id_index(CompanionConfig.alert_chrome_style)
	)
	_alert_chrome_scale.value = CompanionConfig.alert_chrome_scale
	_alert_title_fs.value = CompanionConfig.alert_title_font_size_px
	_alert_subtitle_fs.value = CompanionConfig.alert_subtitle_font_size_px
	_alert_pad_h.value = CompanionConfig.alert_padding_h_px
	_alert_pad_v.value = CompanionConfig.alert_padding_v_px
	_alert_icon_sz.value = CompanionConfig.alert_command_icon_size_px
	_mob_idle_fps.value = CompanionConfig.alert_mob_idle_anim_fps

	_paid_on.button_pressed = CompanionConfig.paid_notice_enabled
	_paid_qmax.value = CompanionConfig.paid_notice_queue_max
	_paid_ttl.value = CompanionConfig.paid_notice_default_ttl_sec
	_paid_fade_in.value = CompanionConfig.paid_notice_fade_in_sec
	_paid_fade_out.value = CompanionConfig.paid_notice_fade_out_sec
	_paid_zx.value = CompanionConfig.paid_notice_zone_x_px
	_paid_zy.value = CompanionConfig.paid_notice_zone_y_px
	_paid_zw.value = CompanionConfig.paid_notice_zone_width_px
	_paid_zh.value = CompanionConfig.paid_notice_zone_height_px
	_paid_zbm.value = CompanionConfig.paid_notice_zone_bottom_margin_px
	_paid_chrome_style.select(
		_SpdUiArt.chrome_style_id_index(CompanionConfig.paid_notice_chrome_style)
	)
	_paid_chrome_scale.value = CompanionConfig.paid_notice_chrome_scale
	_paid_kind_fs.value = CompanionConfig.paid_notice_kind_font_size_px
	_paid_title_fs.value = CompanionConfig.paid_notice_title_font_size_px
	_paid_body_fs.value = CompanionConfig.paid_notice_body_font_size_px
	_paid_kind_color.color = CompanionConfig.paid_notice_kind_font_color
	_paid_title_color.color = CompanionConfig.paid_notice_title_font_color
	_paid_body_color.color = CompanionConfig.paid_notice_body_font_color
	_paid_shadow.button_pressed = CompanionConfig.paid_notice_text_shadow
	_paid_pad_h.value = CompanionConfig.paid_notice_padding_h_px
	_paid_pad_v.value = CompanionConfig.paid_notice_padding_v_px
	_paid_sep.value = CompanionConfig.paid_notice_line_separation_px
	_paid_align.select(1 if CompanionConfig.paid_notice_text_align == "center" else 0)
	_paid_pop_scale.button_pressed = CompanionConfig.paid_notice_pop_scale
	_paid_superchat.button_pressed = CompanionConfig.paid_notice_enable_superchat
	_paid_gifted.button_pressed = CompanionConfig.paid_notice_enable_gifted_membership
	_paid_sub.button_pressed = CompanionConfig.paid_notice_enable_sub
	_paid_highlight.button_pressed = CompanionConfig.paid_notice_enable_highlight

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

	_chrome_draft = CompanionConfig.duplicate_chrome_boxes_for_profile(_chrome_profile_name())
	if _chrome_layout_pick:
		_chrome_layout_pick.select(_chrome_layout_profile_idx)
	_rebuild_chrome_tabs()

	_sync_scene_gates_from_config()
	_sync_vertical_from_config()

	var path_l := _find_path_label()
	if path_l:
		path_l.text = (
			"Saved to: %s — shipped defaults rev %d (%s) — F2 toggles this window · F4 vertical"
			% [
				CompanionConfig.SETTINGS_PATH,
				CompanionConfig.defaults_revision,
				CompanionConfig.SHIPPED_DEFAULTS_PATH,
			]
		)


func _chrome_profile_name() -> StringName:
	return (
		CompanionConfig.LAYOUT_VERTICAL
		if _chrome_layout_profile_idx == 1
		else CompanionConfig.LAYOUT_MAIN
	)


func _on_chrome_layout_picked(index: int) -> void:
	_flush_chrome_editors_into_draft()
	_commit_chrome_draft_to_config()
	_chrome_layout_profile_idx = clampi(index, 0, 1)
	_chrome_draft = CompanionConfig.duplicate_chrome_boxes_for_profile(_chrome_profile_name())
	_chrome_selected_index = 0
	_rebuild_chrome_tabs()


func _commit_chrome_draft_to_config() -> void:
	var boxes := _collect_chrome_boxes_from_editors()
	if _chrome_layout_profile_idx == 1:
		if CompanionConfig.vertical_layout == null:
			CompanionConfig.vertical_layout = UiLayoutData.default_vertical()
		CompanionConfig.vertical_layout.chrome_boxes = boxes
	else:
		CompanionConfig.chrome_boxes = boxes


func _sync_vertical_from_config() -> void:
	var L: UiLayoutData = CompanionConfig.vertical_layout
	if L == null:
		L = UiLayoutData.default_vertical()
	_vert_enabled.button_pressed = CompanionConfig.vertical_window_enabled
	_vert_show_live.button_pressed = L.show_live_water
	_vert_show_title.button_pressed = L.show_title
	_vert_show_chrome.button_pressed = L.show_chrome_boxes
	_vert_show_id.button_pressed = L.show_id_overlay
	_vert_show_alerts.button_pressed = L.show_alerts
	_vert_show_paid.button_pressed = L.show_paid_notices
	_vert_show_bestiary.button_pressed = L.show_bestiary
	_vert_show_march.button_pressed = L.show_summon_march
	_vert_show_spend.button_pressed = L.show_spend_indicator
	_vert_hide_spend_off.button_pressed = L.hide_spend_when_off
	_vert_show_free.button_pressed = L.show_free_promos
	_vert_show_double.button_pressed = L.show_double_points
	_vaz_x.value = L.alert_zone_x_px
	_vaz_y.value = L.alert_zone_y_px
	_vaz_w.value = L.alert_zone_width_px
	_vaz_h.value = L.alert_zone_height_px
	_vaz_bm.value = L.alert_zone_bottom_margin_px
	_viz_x.value = L.id_zone_x_px
	_viz_y.value = L.id_zone_y_px
	_viz_w.value = L.id_zone_width_px
	_viz_h.value = L.id_zone_height_px
	_viz_bm.value = L.id_zone_bottom_margin_px
	_vbz_x.value = L.bestiary_zone_x_px
	_vbz_y.value = L.bestiary_zone_y_px
	_vbz_w.value = L.bestiary_zone_width_px
	_vbz_h.value = L.bestiary_zone_height_px
	_vbz_bm.value = L.bestiary_zone_bottom_margin_px
	_vpz_x.value = L.paid_notice_zone_x_px
	_vpz_y.value = L.paid_notice_zone_y_px
	_vpz_w.value = L.paid_notice_zone_width_px
	_vpz_h.value = L.paid_notice_zone_height_px
	_vpz_bm.value = L.paid_notice_zone_bottom_margin_px
	_vlw_bottom.value = L.live_water_bottom_bar_px
	_vlw_left.value = L.live_water_left_strip_px
	_vlw_left_top.value = L.live_water_left_strip_top_px
	_vlw_grad_start.value = L.live_water_gradient_fade_start
	_vlw_grad_end.value = L.live_water_gradient_fade_end
	_vlw_feather_v.value = L.live_water_edge_feather_v_px
	_vspend_corner.select(clampi(L.spend_indicator_corner, 0, 3))
	_vspend_mx.value = L.spend_indicator_margin_x
	_vspend_my.value = L.spend_indicator_margin_y
	_vfree_corner.select(clampi(L.free_promos_corner, 0, 3))
	_vfree_mx.value = L.free_promos_margin_x
	_vfree_my.value = L.free_promos_margin_y
	_vdp_corner.select(clampi(L.double_points_corner, 0, 3))
	_vdp_mx.value = L.double_points_margin_x
	_vdp_my.value = L.double_points_margin_y


func _apply_vertical_to_config() -> void:
	CompanionConfig.vertical_window_enabled = _vert_enabled.button_pressed
	var L: UiLayoutData = CompanionConfig.vertical_layout
	if L == null:
		L = UiLayoutData.default_vertical()
		CompanionConfig.vertical_layout = L
	L.show_live_water = _vert_show_live.button_pressed
	L.show_title = _vert_show_title.button_pressed
	L.show_chrome_boxes = _vert_show_chrome.button_pressed
	L.show_id_overlay = _vert_show_id.button_pressed
	L.show_alerts = _vert_show_alerts.button_pressed
	L.show_paid_notices = _vert_show_paid.button_pressed
	L.show_bestiary = _vert_show_bestiary.button_pressed
	L.show_summon_march = _vert_show_march.button_pressed
	L.show_spend_indicator = _vert_show_spend.button_pressed
	L.hide_spend_when_off = _vert_hide_spend_off.button_pressed
	L.show_free_promos = _vert_show_free.button_pressed
	L.show_double_points = _vert_show_double.button_pressed
	L.alert_zone_x_px = int(_vaz_x.value)
	L.alert_zone_y_px = int(_vaz_y.value)
	L.alert_zone_width_px = int(_vaz_w.value)
	L.alert_zone_height_px = int(_vaz_h.value)
	L.alert_zone_bottom_margin_px = int(_vaz_bm.value)
	L.id_zone_x_px = int(_viz_x.value)
	L.id_zone_y_px = int(_viz_y.value)
	L.id_zone_width_px = int(_viz_w.value)
	L.id_zone_height_px = int(_viz_h.value)
	L.id_zone_bottom_margin_px = int(_viz_bm.value)
	L.bestiary_zone_x_px = int(_vbz_x.value)
	L.bestiary_zone_y_px = int(_vbz_y.value)
	L.bestiary_zone_width_px = int(_vbz_w.value)
	L.bestiary_zone_height_px = int(_vbz_h.value)
	L.bestiary_zone_bottom_margin_px = int(_vbz_bm.value)
	L.paid_notice_zone_x_px = int(_vpz_x.value)
	L.paid_notice_zone_y_px = int(_vpz_y.value)
	L.paid_notice_zone_width_px = int(_vpz_w.value)
	L.paid_notice_zone_height_px = int(_vpz_h.value)
	L.paid_notice_zone_bottom_margin_px = int(_vpz_bm.value)
	L.live_water_bottom_bar_px = int(_vlw_bottom.value)
	L.live_water_left_strip_px = int(_vlw_left.value)
	L.live_water_left_strip_top_px = int(_vlw_left_top.value)
	var gs := clampf(float(_vlw_grad_start.value), 0.0, 1.0)
	var ge := clampf(float(_vlw_grad_end.value), 0.0, 1.0)
	if ge < gs:
		ge = gs
	L.live_water_gradient_fade_start = gs
	L.live_water_gradient_fade_end = ge
	L.live_water_edge_feather_v_px = clampi(int(_vlw_feather_v.value), 0, 2048)
	L.spend_indicator_corner = clampi(_vspend_corner.selected, 0, 3)
	L.spend_indicator_margin_x = int(_vspend_mx.value)
	L.spend_indicator_margin_y = int(_vspend_my.value)
	L.free_promos_corner = clampi(_vfree_corner.selected, 0, 3)
	L.free_promos_margin_x = int(_vfree_mx.value)
	L.free_promos_margin_y = int(_vfree_my.value)
	L.double_points_corner = clampi(_vdp_corner.selected, 0, 3)
	L.double_points_margin_x = int(_vdp_mx.value)
	L.double_points_margin_y = int(_vdp_my.value)


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
	CompanionConfig.spend_indicator_padding_h_px = clampi(int(_spend_pad_h.value), 0, 64)
	CompanionConfig.spend_indicator_padding_v_px = clampi(int(_spend_pad_v.value), 0, 64)
	if (
		_spend_chrome_style.selected >= 0
		and _spend_chrome_style.selected < _SpdUiArt.CHROME_STYLE_IDS.size()
	):
		CompanionConfig.spend_indicator_chrome_style = (
			_SpdUiArt.CHROME_STYLE_IDS[_spend_chrome_style.selected]
		)
	CompanionConfig.spend_indicator_chrome_scale = clampf(
		float(_spend_chrome_scale.value), 0.5, 4.0
	)
	CompanionConfig.free_promos_http_url = _free_url.text.strip_edges()
	CompanionConfig.free_promos_poll_sec = maxf(0.5, float(_free_poll.value))
	CompanionConfig.free_promos_panel_visible = _free_vis.button_pressed
	CompanionConfig.free_promos_corner = clampi(_free_corner.selected, 0, 3)
	CompanionConfig.free_promos_margin_x = int(_free_mx.value)
	CompanionConfig.free_promos_margin_y = int(_free_my.value)
	CompanionConfig.free_promos_font_size_px = clampi(int(_free_fs.value), 8, 36)
	CompanionConfig.free_promos_padding_h_px = clampi(int(_free_pad_h.value), 0, 64)
	CompanionConfig.free_promos_padding_v_px = clampi(int(_free_pad_v.value), 0, 64)
	CompanionConfig.free_promos_max_width_px = clampi(int(_free_mw.value), 160, 1600)
	if (
		_free_chrome_style.selected >= 0
		and _free_chrome_style.selected < _SpdUiArt.CHROME_STYLE_IDS.size()
	):
		CompanionConfig.free_promos_chrome_style = (
			_SpdUiArt.CHROME_STYLE_IDS[_free_chrome_style.selected]
		)
	CompanionConfig.free_promos_chrome_scale = clampf(float(_free_chrome_scale.value), 0.5, 4.0)
	CompanionConfig.double_points_panel_visible = _dp_vis.button_pressed
	CompanionConfig.double_points_poll_sec = maxf(0.5, float(_dp_poll.value))
	CompanionConfig.double_points_corner = clampi(_dp_corner.selected, 0, 3)
	CompanionConfig.double_points_margin_x = int(_dp_mx.value)
	CompanionConfig.double_points_margin_y = int(_dp_my.value)
	CompanionConfig.double_points_padding_h_px = clampi(int(_dp_pad_h.value), 0, 64)
	CompanionConfig.double_points_padding_v_px = clampi(int(_dp_pad_v.value), 0, 64)
	CompanionConfig.double_points_font_size_px = clampi(int(_dp_fs.value), 8, 48)
	CompanionConfig.double_points_font_color = _dp_color.color
	if _dp_chrome_style.selected >= 0 and _dp_chrome_style.selected < _SpdUiArt.CHROME_STYLE_IDS.size():
		CompanionConfig.double_points_chrome_style = (
			_SpdUiArt.CHROME_STYLE_IDS[_dp_chrome_style.selected]
		)
	CompanionConfig.double_points_chrome_scale = clampf(float(_dp_chrome_scale.value), 0.5, 4.0)
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
	CompanionConfig.summon_crowned_sprite_scale = clampf(float(_sm_crown_scale.value), 1.0, 3.0)
	CompanionConfig.summon_crowned_mob_modulate = _sm_crown_mob_mod.color
	CompanionConfig.summon_crowned_show_glow = _sm_crown_glow_on.button_pressed
	CompanionConfig.summon_crowned_glow_color = _sm_crown_glow_color.color
	CompanionConfig.summon_crowned_glow_rays = clampi(int(_sm_crown_glow_rays.value), 3, 16)
	CompanionConfig.summon_crowned_glow_spin_deg = clampf(float(_sm_crown_glow_spin.value), 0.0, 720.0)
	CompanionConfig.summon_crowned_glow_radius_scale = clampf(float(_sm_crown_glow_rad.value), 0.25, 3.0)
	CompanionConfig.summon_crowned_show_crown = _sm_crown_show.button_pressed
	CompanionConfig.summon_crowned_crown_scale = clampf(float(_sm_crown_sz.value), 0.1, 3.0)
	CompanionConfig.summon_crowned_crown_offset_y = clampf(float(_sm_crown_oy.value), -1.5, 1.5)
	CompanionConfig.summon_crowned_crown_modulate = _sm_crown_mod.color
	CompanionConfig.summon_crowned_username_color = _sm_crown_user_color.color
	CompanionConfig.summon_crowned_username_font_size_px = clampi(int(_sm_crown_user_fs.value), 0, 72)
	CompanionConfig.summon_crowned_show_star_prefix = _sm_crown_star.button_pressed
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
	CompanionConfig.bestiary_panel_pad_px = clampi(int(_best_pad.value), 0, 64)
	CompanionConfig.bestiary_hud_scale = clampf(float(_best_hud_scale.value), 0.5, 4.0)
	CompanionConfig.bestiary_chrome_scale = clampf(float(_best_chrome_scale.value), 0.5, 4.0)
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
	CompanionConfig.bestiary_xp_text_over_bar = _best_xp_over.button_pressed
	CompanionConfig.bestiary_level_up_banner_sec = maxf(0.5, float(_best_banner_sec.value))
	CompanionConfig.bestiary_level_up_banner_scale = clampf(float(_best_banner_scale.value), 0.25, 8.0)
	CompanionConfig.bestiary_level_up_banner_font_size_px = clampi(int(_best_banner_fs.value), 8, 96)
	CompanionConfig.bestiary_zone_font_color = _best_zone_color.color
	CompanionConfig.bestiary_xp_font_color = _best_xp_color.color
	CompanionConfig.bestiary_sprint_font_color = _best_sprint_color.color
	CompanionConfig.bestiary_heat_font_color = _best_heat_color.color
	CompanionConfig.bestiary_hall_font_color = _best_hall_color.color
	CompanionConfig.bestiary_banner_font_color = _best_banner_color.color
	CompanionConfig.remote_settings_enabled = _remote_on.button_pressed
	CompanionConfig.remote_settings_base_url = _remote_url.text.strip_edges()
	CompanionConfig.remote_settings_poll_sec = maxf(0.5, float(_remote_poll.value))
	CompanionConfig.remote_settings_push_on_save = _remote_push.button_pressed
	CompanionConfig.obs_scene_sync_enabled = _obs_on.button_pressed
	CompanionConfig.obs_ws_host = _obs_host.text.strip_edges()
	CompanionConfig.obs_ws_port = int(_obs_port.value)
	CompanionConfig.obs_ws_password = _obs_pw.text
	CompanionConfig.obs_pause_scene_name = _obs_scene.text.strip_edges()
	CompanionConfig.obs_main_scene_name = _obs_main_scene.text.strip_edges()
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
	CompanionConfig.live_water_edge_feather_v_px = clampi(int(_lw_feather_v.value), 0, 2048)

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
	if (
		_alert_chrome_style.selected >= 0
		and _alert_chrome_style.selected < _SpdUiArt.CHROME_STYLE_IDS.size()
	):
		CompanionConfig.alert_chrome_style = _SpdUiArt.CHROME_STYLE_IDS[_alert_chrome_style.selected]
	CompanionConfig.alert_chrome_scale = clampf(float(_alert_chrome_scale.value), 0.5, 4.0)
	CompanionConfig.alert_title_font_size_px = int(_alert_title_fs.value)
	CompanionConfig.alert_subtitle_font_size_px = int(_alert_subtitle_fs.value)
	CompanionConfig.alert_padding_h_px = clampi(int(_alert_pad_h.value), 0, 64)
	CompanionConfig.alert_padding_v_px = clampi(int(_alert_pad_v.value), 0, 64)
	CompanionConfig.alert_command_icon_size_px = int(_alert_icon_sz.value)
	CompanionConfig.alert_mob_idle_anim_fps = float(_mob_idle_fps.value)

	CompanionConfig.paid_notice_enabled = _paid_on.button_pressed
	CompanionConfig.paid_notice_queue_max = clampi(int(_paid_qmax.value), 1, 32)
	CompanionConfig.paid_notice_default_ttl_sec = maxf(0.5, float(_paid_ttl.value))
	CompanionConfig.paid_notice_fade_in_sec = maxf(0.05, float(_paid_fade_in.value))
	CompanionConfig.paid_notice_fade_out_sec = maxf(0.05, float(_paid_fade_out.value))
	CompanionConfig.paid_notice_zone_x_px = int(_paid_zx.value)
	CompanionConfig.paid_notice_zone_y_px = int(_paid_zy.value)
	CompanionConfig.paid_notice_zone_width_px = clampi(int(_paid_zw.value), 64, 1920)
	CompanionConfig.paid_notice_zone_height_px = clampi(int(_paid_zh.value), 0, 1080)
	CompanionConfig.paid_notice_zone_bottom_margin_px = int(_paid_zbm.value)
	if (
		_paid_chrome_style.selected >= 0
		and _paid_chrome_style.selected < _SpdUiArt.CHROME_STYLE_IDS.size()
	):
		CompanionConfig.paid_notice_chrome_style = (
			_SpdUiArt.CHROME_STYLE_IDS[_paid_chrome_style.selected]
		)
	CompanionConfig.paid_notice_chrome_scale = clampf(float(_paid_chrome_scale.value), 0.5, 4.0)
	CompanionConfig.paid_notice_kind_font_size_px = clampi(int(_paid_kind_fs.value), 8, 96)
	CompanionConfig.paid_notice_title_font_size_px = clampi(int(_paid_title_fs.value), 8, 96)
	CompanionConfig.paid_notice_body_font_size_px = clampi(int(_paid_body_fs.value), 8, 96)
	CompanionConfig.paid_notice_kind_font_color = _paid_kind_color.color
	CompanionConfig.paid_notice_title_font_color = _paid_title_color.color
	CompanionConfig.paid_notice_body_font_color = _paid_body_color.color
	CompanionConfig.paid_notice_text_shadow = _paid_shadow.button_pressed
	CompanionConfig.paid_notice_padding_h_px = clampi(int(_paid_pad_h.value), 0, 64)
	CompanionConfig.paid_notice_padding_v_px = clampi(int(_paid_pad_v.value), 0, 64)
	CompanionConfig.paid_notice_line_separation_px = clampi(int(_paid_sep.value), 0, 48)
	CompanionConfig.paid_notice_text_align = (
		"center" if _paid_align.selected == 1 else "left"
	)
	CompanionConfig.paid_notice_pop_scale = _paid_pop_scale.button_pressed
	# Paid scene visibility is owned by Scene gates (main_scene_show.paid_notices).
	CompanionConfig.paid_notice_enable_superchat = _paid_superchat.button_pressed
	CompanionConfig.paid_notice_enable_gifted_membership = _paid_gifted.button_pressed
	CompanionConfig.paid_notice_enable_sub = _paid_sub.button_pressed
	CompanionConfig.paid_notice_enable_highlight = _paid_highlight.button_pressed

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

	_apply_vertical_to_config()
	_apply_scene_gates_to_config()
	_commit_chrome_draft_to_config()

	CompanionConfig.save_settings()
	_chrome_draft = CompanionConfig.duplicate_chrome_boxes_for_profile(_chrome_profile_name())
	_rebuild_chrome_tabs()
	_refresh_remote_status()


func _refresh_remote_status() -> void:
	if _remote_status == null:
		return
	var connected := "—"
	if CompanionSettingsPollService != null:
		connected = "yes" if CompanionSettingsPollService.is_server_connected else "no"
		_remote_status.text = (
			"Applied revision: %d · Server revision: %d · Flask reachable: %s"
			% [
				CompanionConfig.remote_settings_applied_revision,
				CompanionSettingsPollService.last_server_revision,
				connected,
			]
		)
	else:
		_remote_status.text = (
			"Applied revision: %d" % CompanionConfig.remote_settings_applied_revision
		)


func _on_remote_pull() -> void:
	CompanionSettingsPollService.pull_now(true)
	# Status refreshes after HTTP completes; give a hint immediately.
	_remote_status.text = "Pulling from Flask…"


func _on_remote_push() -> void:
	_on_apply_pressed()
	if not CompanionConfig.remote_settings_push_on_save:
		CompanionSettingsPollService.push_now()
	_remote_status.text = "Uploading to Flask…"


func _on_reload_pressed() -> void:
	CompanionConfig.load_settings()
	_sync_from_config()


func _on_save_as_export_defaults() -> void:
	# Persist UI first so the snapshot matches what you see.
	_on_apply_pressed()
	var err: String = CompanionConfig.save_as_shipped_defaults()
	var path_l := _find_path_label()
	if path_l:
		if err.is_empty():
			path_l.text = (
				"Export defaults saved (revision %d) → %s — include this file in the next export"
				% [CompanionConfig.defaults_revision, CompanionConfig.SHIPPED_DEFAULTS_PATH]
			)
		else:
			path_l.text = "Export defaults failed: %s" % err
	_sync_from_config()


func _on_add_chrome_box() -> void:
	if _chrome_draft.size() >= CompanionConfig.CHROME_BOXES_MAX:
		return
	_flush_chrome_editors_into_draft()
	_chrome_draft.append(CompanionConfig.default_chrome_box(_chrome_draft.size() + 1))
	_chrome_selected_index = _chrome_draft.size() - 1
	_rebuild_chrome_tabs()
	if _ui_panels_nav_index >= 0:
		_show_settings_page(_ui_panels_nav_index)


func _on_chrome_box_picked(index: int) -> void:
	if index == _chrome_selected_index:
		return
	_flush_chrome_editors_into_draft()
	_chrome_selected_index = index
	_rebuild_chrome_editor_only()


func _flush_chrome_editors_into_draft() -> void:
	_flush_active_chrome_editor_into_draft()


func _flush_active_chrome_editor_into_draft() -> void:
	if _chrome_editors.is_empty():
		return
	var ed: Dictionary = _chrome_editors[0]
	var idx := int(ed.get("draft_index", _chrome_selected_index))
	if idx < 0 or idx >= _chrome_draft.size():
		return
	_chrome_draft[idx] = _chrome_box_from_editor(ed, idx + 1)


func _chrome_box_from_editor(ed: Dictionary, fallback_n: int) -> Dictionary:
	var name_le: LineEdit = ed.get("name") as LineEdit
	var enabled_cb: CheckBox = ed.get("enabled") as CheckBox
	var style_ob: OptionButton = ed.get("style") as OptionButton
	var zx: SpinBox = ed.get("zx") as SpinBox
	var zy: SpinBox = ed.get("zy") as SpinBox
	var zw: SpinBox = ed.get("zw") as SpinBox
	var zh: SpinBox = ed.get("zh") as SpinBox
	var zbm: SpinBox = ed.get("zbm") as SpinBox
	var chrome_sc: SpinBox = ed.get("chrome_scale") as SpinBox
	var on_pause: CheckBox = ed.get("show_on_pause") as CheckBox
	var on_main: CheckBox = ed.get("show_on_main") as CheckBox
	var on_other: CheckBox = ed.get("show_on_other") as CheckBox
	var header_le: LineEdit = ed.get("header_text") as LineEdit
	var body_te: TextEdit = ed.get("body_text") as TextEdit
	var header_fs: SpinBox = ed.get("header_font_size") as SpinBox
	var body_fs: SpinBox = ed.get("body_font_size") as SpinBox
	var header_col: ColorPickerButton = ed.get("header_font_color") as ColorPickerButton
	var body_col: ColorPickerButton = ed.get("body_font_color") as ColorPickerButton
	var align_ob: OptionButton = ed.get("text_align") as OptionButton
	var shadow_cb: CheckBox = ed.get("text_shadow") as CheckBox
	var pad_h: SpinBox = ed.get("padding_h") as SpinBox
	var pad_v: SpinBox = ed.get("padding_v") as SpinBox
	var sep: SpinBox = ed.get("line_separation") as SpinBox
	var id_s := str(ed.get("id", ""))
	var style_id := "window"
	if style_ob and style_ob.selected >= 0 and style_ob.selected < _SpdUiArt.CHROME_STYLE_IDS.size():
		style_id = _SpdUiArt.CHROME_STYLE_IDS[style_ob.selected]
	var raw := {
		"id": id_s,
		"name": name_le.text if name_le else "",
		"enabled": enabled_cb.button_pressed if enabled_cb else true,
		"style": style_id,
		"zone_x_px": int(zx.value) if zx else 48,
		"zone_y_px": int(zy.value) if zy else 48,
		"zone_width_px": int(zw.value) if zw else 240,
		"zone_height_px": int(zh.value) if zh else 160,
		"zone_bottom_margin_px": int(zbm.value) if zbm else 0,
		"chrome_scale": float(chrome_sc.value) if chrome_sc else 1.0,
		"show_on_pause": on_pause.button_pressed if on_pause else false,
		"show_on_main": on_main.button_pressed if on_main else true,
		"show_on_other": on_other.button_pressed if on_other else true,
		"header_text": header_le.text if header_le else "",
		"body_text": body_te.text if body_te else "",
		"header_font_size_px": int(header_fs.value) if header_fs else 24,
		"body_font_size_px": int(body_fs.value) if body_fs else 16,
		"header_font_color": header_col.color if header_col else Color(1.0, 1.0, 0.27, 1.0),
		"body_font_color": body_col.color if body_col else Color(0.95, 0.92, 0.85, 1.0),
		"text_align": ("center" if align_ob and align_ob.selected == 1 else "left"),
		"text_shadow": shadow_cb.button_pressed if shadow_cb else true,
		"padding_h_px": int(pad_h.value) if pad_h else 14,
		"padding_v_px": int(pad_v.value) if pad_v else 10,
		"line_separation_px": int(sep.value) if sep else 6,
	}
	return CompanionConfig.normalize_chrome_box(raw, fallback_n)


func _collect_chrome_boxes_from_editors() -> Array:
	_flush_active_chrome_editor_into_draft()
	var out: Array = []
	for i in range(_chrome_draft.size()):
		out.append(CompanionConfig.normalize_chrome_box(_chrome_draft[i], i + 1))
	return out


func _clear_chrome_editor() -> void:
	_chrome_editors.clear()
	if _chrome_editor_host == null:
		return
	for child in _chrome_editor_host.get_children():
		_chrome_editor_host.remove_child(child)
		child.queue_free()


func _rebuild_chrome_tabs() -> void:
	_clear_chrome_editor()
	if _chrome_count_label:
		_chrome_count_label.text = "Chrome boxes: %d / %d" % [
			_chrome_draft.size(), CompanionConfig.CHROME_BOXES_MAX
		]
	_refresh_chrome_box_pick()
	_rebuild_chrome_editor_only()


func _rebuild_chrome_editor_only() -> void:
	_clear_chrome_editor()
	if _chrome_editor_host == null:
		return
	if _chrome_draft.is_empty():
		var empty := Label.new()
		empty.text = "No chrome boxes yet. Click Add chrome box."
		empty.modulate = Color(0.75, 0.75, 0.8, 1.0)
		_chrome_editor_host.add_child(empty)
		return
	if _chrome_selected_index < 0 or _chrome_selected_index >= _chrome_draft.size():
		_chrome_selected_index = 0
	var i := _chrome_selected_index
	var entry: Dictionary = CompanionConfig.normalize_chrome_box(_chrome_draft[i], i + 1)

	var name_le := _line()
	name_le.text = str(entry.get("name", ""))
	var enabled_cb := CheckBox.new()
	enabled_cb.button_pressed = bool(entry.get("enabled", true))
	var style_ob := _opt(PackedStringArray(_SpdUiArt.CHROME_STYLE_LABELS))
	style_ob.select(_SpdUiArt.chrome_style_id_index(str(entry.get("style", "window"))))
	var zx := _spin_i(0, 1920)
	var zy := _spin_i(0, 1080)
	var zw := _spin_i(32, 1920)
	var zh := _spin_i(0, 1080)
	var zbm := _spin_i(0, 400)
	var chrome_scale := _spin_f(0.5, 4.0, 0.05)
	var on_pause := CheckBox.new()
	var on_main := CheckBox.new()
	var on_other := CheckBox.new()
	zx.value = int(entry.get("zone_x_px", 48))
	zy.value = int(entry.get("zone_y_px", 48))
	zw.value = int(entry.get("zone_width_px", 240))
	zh.value = int(entry.get("zone_height_px", 160))
	zbm.value = int(entry.get("zone_bottom_margin_px", 0))
	chrome_scale.value = float(entry.get("chrome_scale", 1.0))
	on_pause.button_pressed = bool(entry.get("show_on_pause", false))
	on_main.button_pressed = bool(entry.get("show_on_main", entry.get("show_on_live", true)))
	on_other.button_pressed = bool(entry.get("show_on_other", entry.get("show_on_live", true)))

	var header_le := _line()
	header_le.text = str(entry.get("header_text", ""))
	var body_te := TextEdit.new()
	body_te.custom_minimum_size = Vector2(0, 88)
	body_te.wrap_mode = TextEdit.LINE_WRAPPING_BOUNDARY
	body_te.text = str(entry.get("body_text", ""))
	var header_fs := _spin_i(8, 96)
	var body_fs := _spin_i(8, 96)
	header_fs.value = int(entry.get("header_font_size_px", 24))
	body_fs.value = int(entry.get("body_font_size_px", 16))
	var header_col := _color_picker()
	var body_col := _color_picker()
	header_col.color = entry.get("header_font_color", Color(1.0, 1.0, 0.27, 1.0)) as Color
	body_col.color = entry.get("body_font_color", Color(0.95, 0.92, 0.85, 1.0)) as Color
	var align_ob := _opt(PackedStringArray(["Left", "Center"]))
	align_ob.select(1 if str(entry.get("text_align", "left")) == "center" else 0)
	var shadow_cb := CheckBox.new()
	shadow_cb.button_pressed = bool(entry.get("text_shadow", true))
	var pad_h := _spin_i(0, 64)
	var pad_v := _spin_i(0, 64)
	var sep := _spin_i(0, 48)
	pad_h.value = int(entry.get("padding_h_px", 14))
	pad_v.value = int(entry.get("padding_v_px", 10))
	sep.value = int(entry.get("line_separation_px", 6))

	_add_section_header_to(_chrome_editor_host, "Box settings")
	for pair in [
		["Name", name_le],
		["Enabled", enabled_cb],
		["Chrome style", style_ob],
		["Header text", header_le],
		["Body text", body_te],
		["Header font size (px)", header_fs],
		["Body font size (px)", body_fs],
		["Header color", header_col],
		["Body color", body_col],
		["Text align", align_ob],
		["Text shadow", shadow_cb],
		["Padding horizontal (px)", pad_h],
		["Padding vertical (px)", pad_v],
		["Line separation (px)", sep],
		["Zone X (px)", zx],
		["Zone Y (px)", zy],
		["Zone width (px)", zw],
		["Zone height (0=auto)", zh],
		["Bottom margin if height is auto", zbm],
		["Chrome scale (border)", chrome_scale],
		["Show on LIVE - PAUSE", on_pause],
		["Show on LIVE - MAIN", on_main],
		["Show on other scenes", on_other],
	]:
		_chrome_editor_host.add_child(_form_row(str(pair[0]), pair[1] as Control))

	var del_btn := Button.new()
	del_btn.text = "Delete this chrome box"
	del_btn.pressed.connect(_on_delete_chrome_box.bind(i))
	_chrome_editor_host.add_child(del_btn)

	name_le.text_changed.connect(_on_chrome_name_changed)

	_chrome_editors.append(
		{
			"draft_index": i,
			"id": str(entry.get("id", "")),
			"name": name_le,
			"enabled": enabled_cb,
			"style": style_ob,
			"header_text": header_le,
			"body_text": body_te,
			"header_font_size": header_fs,
			"body_font_size": body_fs,
			"header_font_color": header_col,
			"body_font_color": body_col,
			"text_align": align_ob,
			"text_shadow": shadow_cb,
			"padding_h": pad_h,
			"padding_v": pad_v,
			"line_separation": sep,
			"zx": zx,
			"zy": zy,
			"zw": zw,
			"zh": zh,
			"zbm": zbm,
			"chrome_scale": chrome_scale,
			"show_on_pause": on_pause,
			"show_on_main": on_main,
			"show_on_other": on_other,
		}
	)


func _add_section_header_to(host: VBoxContainer, text: String) -> void:
	host.add_child(_section_header(text))


func _refresh_chrome_box_pick() -> void:
	if _chrome_box_pick == null:
		return
	_chrome_box_pick.set_block_signals(true)
	_chrome_box_pick.clear()
	for i in range(_chrome_draft.size()):
		var entry: Dictionary = CompanionConfig.normalize_chrome_box(_chrome_draft[i], i + 1)
		var label := str(entry.get("name", "Chrome %d" % (i + 1))).strip_edges()
		if label.is_empty():
			label = "Chrome %d" % (i + 1)
		_chrome_box_pick.add_item(label, i)
	if _chrome_draft.is_empty():
		_chrome_selected_index = 0
	elif _chrome_selected_index >= _chrome_draft.size():
		_chrome_selected_index = _chrome_draft.size() - 1
	if not _chrome_draft.is_empty():
		_chrome_box_pick.select(_chrome_selected_index)
	_chrome_box_pick.set_block_signals(false)


func _on_chrome_name_changed(_new_text: String) -> void:
	if _chrome_editors.is_empty() or _chrome_box_pick == null:
		return
	var ed: Dictionary = _chrome_editors[0]
	var name_le: LineEdit = ed.get("name") as LineEdit
	if name_le == null:
		return
	var label := name_le.text.strip_edges()
	var idx := int(ed.get("draft_index", _chrome_selected_index))
	if label.is_empty():
		label = "Chrome %d" % (idx + 1)
	if idx >= 0 and idx < _chrome_box_pick.item_count:
		_chrome_box_pick.set_item_text(idx, label)


func _on_delete_chrome_box(ed_index: int) -> void:
	_flush_chrome_editors_into_draft()
	if ed_index < 0 or ed_index >= _chrome_draft.size():
		return
	_chrome_draft.remove_at(ed_index)
	if _chrome_selected_index >= _chrome_draft.size():
		_chrome_selected_index = maxi(_chrome_draft.size() - 1, 0)
	_rebuild_chrome_tabs()
	if _ui_panels_nav_index >= 0:
		_show_settings_page(_ui_panels_nav_index)
