extends Node

const SETTINGS_PATH := "user://companion_settings.cfg"
## Bundled with the project/export. When [member defaults_revision] here is newer than user://, user settings are overwritten.
const SHIPPED_DEFAULTS_PATH := "res://defaults/companion_settings.cfg"
const _SpdUiArt := preload("res://scripts/spd_ui_art.gd")

## Fired after settings are written to disk (e.g. from the settings UI or manual save).
signal settings_saved
## Fired after [method load_settings] successfully read the cfg file (incl. F5 reload).
signal settings_loaded

var _notify_listeners: bool = false
## Matches [code]meta/defaults_revision[/code] in the active cfg. Bumped when saving export defaults.
var defaults_revision: int = 0

## Seconds between OBS WebSocket reconnect attempts when disconnected.
var obs_reconnect_sec: float = 3.0
## When true, sync visibility: if program scene name **contains** [member obs_pause_scene_name] → title on / ID layer off; otherwise title off / ID layer on (if enabled). Requires OBS WebSocket 5+ and **Tools → obs-websocket Settings → Enable WebSocket server** (default port 4455).
var obs_scene_sync_enabled: bool = false
## obs-websocket server (usually loopback).
var obs_ws_host: String = "127.0.0.1"
var obs_ws_port: int = 4455
## Plain-text password from OBS WebSocket settings (empty if auth disabled).
var obs_ws_password: String = ""
## Substring in the **program** scene name that means “pause / BRB” (show title backdrop, hide ID strip). Empty = never treat as pause.
var obs_pause_scene_name: String = "LIVE - PAUSE"
## Substring that means the primary live program scene (LIVE - MAIN). Checked after pause; empty = never treat as main.
var obs_main_scene_name: String = "LIVE - MAIN"
## When true, prints each applied program scene and pause flag to the Godot **Output** panel (Editor) or stdout (exported).
var obs_log_program_scene: bool = false

## Hostname for the game WebSocket (usually loopback).
var game_ws_host: String = "127.0.0.1"
## Port from game Settings → streaming (often 5000–5010; Java default often 5001).
var game_ws_port: int = 5001
## Local UDP port Godot binds for Streamer.bot packets.
var streamerbot_udp_port: int = 5100
## Optional absolute path to spawn_result.txt (Lastest UI). Empty disables polling. Godot does not delete the file.
var spawn_result_file_path: String = ""
## How often to check the file (seconds). Lower values catch the file before Streamer.bot C# deletes it.
var spawn_result_file_poll_sec: float = 0.15
## If true, treat the file's middle (item) segment as a scroll name/rune for the alert icon. Most overlays only put "ok" + points there — leave false and use WebSocket scroll_result or UDP scroll for icons.
var spawn_result_file_icons: bool = false
## Absolute path to [code]spend_disabled.txt[/code] (Streamer.bot / C#). If the file **exists**, chat spending is **off**; if absent, **on**. Empty path hides the on-screen spend indicator.
var spend_lock_file_path: String = ""
## How often to poll [member spend_lock_file_path] (seconds).
var spend_lock_poll_sec: float = 0.35
## When true, show the top-right **Chat spend** badge (only if [member spend_lock_file_path] is non-empty).
var spend_indicator_visible: bool = true
## Corner for the spend panel: **0** top-left, **1** top-right, **2** bottom-left, **3** bottom-right.
var spend_indicator_corner: int = 1
## Pixel inset from the viewport edge along X (left or right, depending on corner).
var spend_indicator_margin_x: int = 16
## Pixel inset from the viewport edge along Y (top or bottom, depending on corner).
var spend_indicator_margin_y: int = 16
## Inner text padding inside the spend chrome.
var spend_indicator_padding_h_px: int = 2
var spend_indicator_padding_v_px: int = 2
## Pixel font size for the spend indicator label ([code]Chat Spending[/code]).
var spend_indicator_font_size_px: int = 15
## SPD chrome for the chat spend badge (default red button, matches WndChallenges).
var spend_indicator_chrome_style: String = "red_button"
var spend_indicator_chrome_scale: float = 1.0
## Seconds between WebSocket reconnect attempts after failure.
var ws_reconnect_sec: float = 2.0
## Optional poll of Flask last snapshot when WS is unavailable.
var http_fallback_enabled: bool = false
var http_fallback_url: String = "http://127.0.0.1:5000/api/game-data"
var http_poll_interval_sec: float = 2.0
## Lastest UI [code]/api/points-config[/code] URL; response may include [code]free_until[/code]. Empty disables free-promo polling.
var free_promos_http_url: String = "http://127.0.0.1:5000/api/points-config"
## Poll interval for points-config / free-until (seconds).
var free_promos_poll_sec: float = 3.0
## Show a short "pending" line when UDP fires before game confirms.
var show_pending_udp_alerts: bool = true
## Max alerts waiting in queue (excess dropped).
var alert_queue_max: int = 5
## Seconds the alert stays fully visible after popping in.
var alert_hold_sec: float = 3.0
## Fade-in duration for the alert panel.
var alert_fade_in_sec: float = 0.5
## Fade-out duration for the alert panel.
var alert_fade_out_sec: float = 0.75
## Toast timing while the matching command is in a [code]free_until[/code] promo (see Free promos tab URL).
var alert_hold_sec_when_free: float = 0.45
var alert_fade_in_sec_when_free: float = 0.15
var alert_fade_out_sec_when_free: float = 0.2
## If true, show an alert for ping_result (usually noisy).
var show_ping_alerts: bool = false
## If true, show alerts when a command fails (includes error text).
var show_failed_command_alerts: bool = false
## Show the top-left connection / snapshot HUD panel (WebSocket, UDP, F2/F5 hints).
var hud_status_panel_visible: bool = true
## Last Settings window size (remembered so you do not have to drag-expand every open).
var settings_window_width_px: int = 1200
var settings_window_height_px: int = 820
## Pixel rect from **top-left of the viewport** for **alert** popups only.
var alert_zone_x_px: int = 24
var alert_zone_y_px: int = 80
var alert_zone_width_px: int = 520
## Height in px; **0** = fill to bottom of canvas minus [member alert_zone_bottom_margin_px].
var alert_zone_height_px: int = 0
## Used only when [member alert_zone_height_px] is 0.
var alert_zone_bottom_margin_px: int = 24
## Pixel rect for the **potion/scroll ID** overlay (independent from alerts).
var id_zone_x_px: int = 24
var id_zone_y_px: int = 188
var id_zone_width_px: int = 360
var id_zone_height_px: int = 0
var id_zone_bottom_margin_px: int = 24
## Live layout only: bottom horizontal band height (px, full width) for flowing-water / chroma L-shape.
var live_water_bottom_bar_px: int = 200
## Width (px) of the left vertical band from the left edge.
var live_water_left_strip_px: int = 420
## Top inset (px): left band starts at this Y (0 = top of viewport; use ~240 to leave a chat-sized gap).
var live_water_left_strip_top_px: int = 0
## Shader UV.y (0 top, 1 bottom): water is full brightness above this line, then fades toward black.
var live_water_gradient_fade_start: float = 0.73
## Shader UV.y where fade reaches full black (must be >= [member live_water_gradient_fade_start]).
var live_water_gradient_fade_end: float = 1.0
## Soften L-shape edges along Y (top of bottom bar / left-strip top inset), in px. 0 = hard cut.
var live_water_edge_feather_v_px: int = 0
## When true, root viewport + window use per-pixel transparency so OBS (layer below) shows through masked regions. Default off: some Windows exports show a blank/white window with transparency on.
var window_per_pixel_transparency_enabled: bool = false
## Caps redraw rate ([member Engine.max_fps]). **0** = unlimited (higher GPU). **30–60** is usually enough for overlays and lowers compositor/GPU load.
var render_max_fps: int = 60
## Title/subtitle alignment: left or center.
var alert_text_align: String = "left"
## SPD chrome style for the alert toast panel (see SpdUiArt.CHROME_STYLE_IDS). Default matches original toast.
var alert_chrome_style: String = "toast"
## Thickens alert toast chrome border (nearest-neighbor), same idea as Bestiary / placeable panels.
var alert_chrome_scale: float = 1.0
## Pixel font size for alert title and subtitle (toast text).
var alert_title_font_size_px: int = 14
var alert_subtitle_font_size_px: int = 11
## Inner toast text padding (MarginContainer around title/subtitle/icons).
var alert_padding_h_px: int = 10
var alert_padding_v_px: int = 8
## Width and height (square) for scroll rune + item / mob icons in the alert row (px).
var alert_command_icon_size_px: int = 72
## Frames per second for monster idle animation in alerts (static scroll art ignores this).
var alert_mob_idle_anim_fps: float = 3.0

## Custom tip toasts (viewer reminders); reuse alert zone/chrome. Separate from command alerts.
const CUSTOM_ALERTS_MAX := 16
var custom_alerts_enabled: bool = false
## Idle seconds between auto-rotated tips (when command alerts are not busy).
var custom_alerts_interval_sec: float = 45.0
## Hold duration for tip toasts (fade in/out reuse alert fade timings).
var custom_alerts_hold_sec: float = 3.0
## Array of { title, subtitle, enabled }.
var custom_alerts: Array = []

## Streamer.bot UDP paid/highlight notices (superchat, gifted membership, sub, highlight).
var paid_notice_enabled: bool = true
var paid_notice_queue_max: int = 8
var paid_notice_default_ttl_sec: float = 6.0
var paid_notice_fade_in_sec: float = 0.35
var paid_notice_fade_out_sec: float = 0.45
var paid_notice_zone_x_px: int = 560
var paid_notice_zone_y_px: int = 80
var paid_notice_zone_width_px: int = 800
var paid_notice_zone_height_px: int = 220
var paid_notice_zone_bottom_margin_px: int = 0
var paid_notice_chrome_style: String = "toast"
var paid_notice_chrome_scale: float = 1.5
var paid_notice_kind_font_size_px: int = 16
var paid_notice_title_font_size_px: int = 24
var paid_notice_body_font_size_px: int = 20
var paid_notice_kind_font_color: Color = Color(1.0, 1.0, 0.35, 1.0)
var paid_notice_title_font_color: Color = Color(1.0, 1.0, 0.27, 1.0)
var paid_notice_body_font_color: Color = Color(0.95, 0.92, 0.85, 1.0)
var paid_notice_text_shadow: bool = true
var paid_notice_padding_h_px: int = 14
var paid_notice_padding_v_px: int = 10
var paid_notice_line_separation_px: int = 6
## left | center
var paid_notice_text_align: String = "left"
## Pop-in scale animation (softens pixel text — off by default).
var paid_notice_pop_scale: bool = false
var paid_notice_show_on_live: bool = true
var paid_notice_show_on_pause: bool = true
## Main (horizontal) per-element OBS scene gates (pause / main / other).
var main_scene_show: Dictionary = {}
var paid_notice_enable_superchat: bool = true
var paid_notice_enable_gifted_membership: bool = true
var paid_notice_enable_sub: bool = true
var paid_notice_enable_highlight: bool = true

## Show potion/scroll identification strip from WebSocket snapshot data.
var id_overlay_enabled: bool = true
## Pixel width of each ID cell (portrait tile in the flow grid).
var id_cell_width_px: int = 34
## Pixel height of each ID cell (slightly taller than wide, SPD-style).
var id_cell_height_px: int = 42
## Inset between the dark cell backing and the art (centered rune/scroll).
var id_cell_padding_px: int = 5
## Known-item icon size as a fraction of the inner cell min dimension (0–1).
var id_known_icon_fraction: float = 0.42
## Horizontal and vertical gap between cells in the flow layout.
var id_flow_h_separation_px: int = 2
## Vertical gap between potion block and scroll block.
var id_block_separation_px: int = 10
## Backing color for ID strip tiles and alert command icon cell (RGBA; alpha **0** = fully transparent).
var icon_cell_background_color: Color = Color(0.13, 0.13, 0.15, 1.0)
## Show timed “free command” promos from Lastest UI [code]free_until[/code].
var free_promos_panel_visible: bool = true
## Corner: **0** top-left, **1** top-right, **2** bottom-left, **3** bottom-right.
var free_promos_corner: int = 2
var free_promos_margin_x: int = 16
var free_promos_margin_y: int = 64
## Inner text padding inside the free-promos chrome.
var free_promos_padding_h_px: int = 10
var free_promos_padding_v_px: int = 8
var free_promos_font_size_px: int = 13
## Hard cap on panel width (content is narrower when possible).
var free_promos_max_width_px: int = 320
## SPD chrome for the free-promos panel (default toast).
var free_promos_chrome_style: String = "toast"
var free_promos_chrome_scale: float = 1.0
## Global !doublepoints / !fard 2× overlay (GET /api/double-points-remaining).
var double_points_panel_visible: bool = true
var double_points_poll_sec: float = 1.0
var double_points_corner: int = 1
var double_points_margin_x: int = 16
var double_points_margin_y: int = 16
var double_points_padding_h_px: int = 10
var double_points_padding_v_px: int = 8
var double_points_font_size_px: int = 14
var double_points_font_color: Color = Color(1.0, 0.85, 0.2, 1.0)
var double_points_chrome_style: String = "toast"
var double_points_chrome_scale: float = 1.0
## Poll chat !summon march queue from Lastest UI server (GET /api/summon-march).
var summon_march_enabled: bool = true
## Base URL e.g. http://127.0.0.1:5000 (no trailing path).
var summon_march_base_url: String = "http://127.0.0.1:5000"
var summon_march_poll_sec: float = 1.0
## Skip events queued before this app session started (still animates new summons on first connect).
var summon_march_skip_backlog: bool = true
var summon_march_duration_sec: float = 6.0
var summon_march_max_concurrent: int = 8
## Legacy single lane Y; migrated to min/max on first load if missing.
var summon_march_lane_y_fraction: float = 0.55
## 0–1 viewport height band for horizontal march (0=top, 1=bottom).
var summon_march_lane_y_min_fraction: float = 0.4
var summon_march_lane_y_max_fraction: float = 0.7
## Vertical lane spacing for vertical-layout marches only (px).
var summon_march_lane_spacing_px: int = 56
## Off-screen margin at march start/end (px).
var summon_march_edge_margin_px: int = 64
## Walk frame rate while marching (uses mobicons Walk_*; idle fallback).
var summon_march_mob_fps: float = 6.0
## Target max sprite dimension on screen (px); scales mobicons to fit.
var summon_march_sprite_size_px: int = 64
var summon_march_show_username: bool = true
var summon_march_username_centered: bool = true
var summon_march_show_monster_name: bool = false
var summon_march_username_font_size_px: int = 28
var summon_march_monster_font_size_px: int = 13
var summon_march_username_color: Color = Color(1.0, 1.0, 0.0, 1.0)
var summon_march_monster_color: Color = Color(1.0, 1.0, 1.0, 1.0)
## Label offsets relative to sprite center (px).
var summon_march_username_offset_x: int = -28
var summon_march_username_offset_y: int = 24
var summon_march_monster_offset_x: int = -48
var summon_march_monster_offset_y: int = -56
## Sprint crown holders (!summon after winning a level sprint this stream).
var summon_crowned_sprite_scale: float = 1.28
var summon_crowned_mob_modulate: Color = Color(1.08, 1.08, 0.92, 1.0)
## Flare behind active leaders: gold=yellow, silver, bronze, heat=red (colors fixed in march unit).
var summon_crowned_show_glow: bool = true
## Legacy single color (unused for badge glows; kept for cfg compatibility).
var summon_crowned_glow_color: Color = Color(1.0, 1.0, 0.0, 1.0)
var summon_crowned_glow_rays: int = 6
var summon_crowned_glow_spin_deg: float = 90.0
var summon_crowned_glow_radius_scale: float = 1.0
var summon_crowned_show_crown: bool = true
var summon_crowned_crown_scale: float = 0.75
var summon_crowned_crown_offset_y: float = -0.32
## Keep near white so heat / silver / bronze art colors stay readable.
var summon_crowned_crown_modulate: Color = Color(1.0, 1.0, 1.0, 1.0)
var summon_crowned_username_color: Color = Color(1.0, 1.0, 0.35, 1.0)
## 0 = use normal summon username font size.
var summon_crowned_username_font_size_px: int = 0
var summon_crowned_show_star_prefix: bool = true

## Bestiary HUD (GET /api/bestiary) — co-op XP bar, sprint, heat.
var bestiary_hud_enabled: bool = true
var bestiary_base_url: String = "http://127.0.0.1:5000"
var bestiary_poll_sec: float = 1.0
var bestiary_show_sprint_chip: bool = true
var bestiary_show_heat_chip: bool = true
var bestiary_show_hall: bool = true
var bestiary_zone_x_px: int = 16
var bestiary_zone_y_px: int = 16
var bestiary_zone_width_px: int = 420
## 0 = hug content height (clamped by canvas − bottom margin). >0 = max height; overflow clips.
var bestiary_zone_height_px: int = 0
## When height is 0, keeps the panel above canvas.y − this margin. When height > 0, caps the zone.
var bestiary_zone_bottom_margin_px: int = 0
## Extra space inside the grey chrome (on top of the border). Keep low for a tight HUD.
var bestiary_panel_pad_px: int = 2
var bestiary_exp_bar_width_px: int = 256
var bestiary_exp_bar_height_scale: float = 1.0
## Scales the whole Bestiary panel (chrome, bar, fonts, icons). Banner is separate.
var bestiary_hud_scale: float = 1.0
## Thickens the grey window chrome border (nearest-neighbor). Independent of HUD scale.
var bestiary_chrome_scale: float = 1.0
var bestiary_use_compact_exp_bar: bool = false
var bestiary_zone_font_size_px: int = 18
## Header template. Placeholders: {level} {zone}
var bestiary_header_format: String = "Bestiary Lv {level} - {zone}"
var bestiary_chip_font_size_px: int = 16
## 0 = no char cap (Label clip/ellipsis still applies if the slot is tight).
var bestiary_chip_name_max_chars: int = 12
var bestiary_hall_name_max_chars: int = 10
var bestiary_truncate_names: bool = true
var bestiary_show_xp_text: bool = true
## When true, draw XP fraction centered on the exp bar (with outline) instead of below it.
var bestiary_xp_text_over_bar: bool = false
var bestiary_level_up_banner_sec: float = 3.0
## Multiplier on native boss_slain.png size (127x68). ~4 matches the old ~520px width.
var bestiary_level_up_banner_scale: float = 4.0
var bestiary_level_up_banner_font_size_px: int = 22
var bestiary_zone_font_color: Color = Color(1.0, 1.0, 170.0 / 255.0, 1.0)
var bestiary_xp_font_color: Color = Color(1.0, 1.0, 170.0 / 255.0, 0.6)
var bestiary_sprint_font_color: Color = Color(1.0, 1.0, 1.0, 1.0)
var bestiary_heat_font_color: Color = Color(1.0, 1.0, 1.0, 1.0)
var bestiary_hall_font_color: Color = Color(1.0, 1.0, 1.0, 1.0)
var bestiary_banner_font_color: Color = Color(1.0, 1.0, 1.0, 1.0)
## Shatter Event mystery pips on the exp bar (claimed vs still locked).
var bestiary_shatter_pip_claimed_color: Color = Color(1.0, 0.85, 0.2, 1.0)
var bestiary_shatter_pip_unclaimed_color: Color = Color(0.95, 0.95, 1.0, 0.92)

## Windows playback device name for companion SFX (local only — not synced via Flask).
var audio_output_device: String = "Default"
## Master SFX volume as linear gain (1.0 = 100%, up to 1.5 = 150%). Synced via Flask.
var audio_volume: float = 1.0
## Mute all companion SFX. Synced via Flask.
var audio_mute: bool = false
## Play ShatterEvent1.mp3 when a Shatter Event grants. Synced via Flask.
var shatter_sfx_enabled: bool = true

## Poll Flask [code]/api/companion-settings[/code] and apply layout/UI when revision increases.
var remote_settings_enabled: bool = false
var remote_settings_base_url: String = "http://127.0.0.1:5000"
var remote_settings_poll_sec: float = 2.0
## When true, Apply & save (and upload button) POST current settings to Flask.
var remote_settings_push_on_save: bool = false
## Last Flask revision successfully applied locally (not written into remote payload).
var remote_settings_applied_revision: int = 0
## True while applying a remote payload (skips auto-push loops).
var _remote_apply_in_progress: bool = false

## Placeable empty SPD window chrome panels (Dictionary entries). Cap keeps Settings tabs reasonable.
const CHROME_BOXES_MAX := 16
## Keys: id, name, enabled, style, zone_*, chrome_scale, show_on_pause/main/other.
var chrome_boxes: Array = []

## Layout profile ids for dual-window stream canvases.
const LAYOUT_MAIN := &"ui"
const LAYOUT_VERTICAL := &"ui_vertical"
## OBS program scene buckets (see [method classify_obs_scene]).
const SCENE_PAUSE := &"pause"
const SCENE_MAIN := &"main"
const SCENE_OTHER := &"other"
const SCENE_UNKNOWN := &"unknown"
## Second OBS capture window (1080×1920). When false, VerticalCompanionWindow stays hidden.
var vertical_window_enabled: bool = true
## Zones / toggles / chrome for the vertical companion window ([code][ui_vertical][/code]).
var vertical_layout: UiLayoutData = UiLayoutData.default_vertical()


func _ready() -> void:
	load_settings()
	save_settings()
	_notify_listeners = true


func load_settings() -> void:
	var cfg := ConfigFile.new()
	var user_ok := cfg.load(SETTINGS_PATH) == OK
	var shipped := ConfigFile.new()
	var shipped_ok := shipped.load(SHIPPED_DEFAULTS_PATH) == OK
	if shipped_ok:
		var shipped_rev := int(shipped.get_value("meta", "defaults_revision", 1))
		var user_rev := int(cfg.get_value("meta", "defaults_revision", 0)) if user_ok else -1
		if (not user_ok) or user_rev != shipped_rev:
			# Option B: shipped defaults win when revision differs (or user cfg missing).
			cfg = shipped
			var install_err := cfg.save(SETTINGS_PATH)
			if install_err != OK:
				push_warning(
					"CompanionConfig: could not install shipped defaults to %s (%s)"
					% [SETTINGS_PATH, error_string(install_err)]
				)
			user_ok = true
	if not user_ok:
		return
	defaults_revision = int(cfg.get_value("meta", "defaults_revision", defaults_revision))
	game_ws_host = str(cfg.get_value("network", "game_ws_host", game_ws_host))
	game_ws_port = int(cfg.get_value("network", "game_ws_port", game_ws_port))
	streamerbot_udp_port = int(cfg.get_value("network", "streamerbot_udp_port", streamerbot_udp_port))
	obs_scene_sync_enabled = bool(cfg.get_value("network", "obs_scene_sync_enabled", obs_scene_sync_enabled))
	obs_ws_host = str(cfg.get_value("network", "obs_ws_host", obs_ws_host))
	obs_ws_port = int(cfg.get_value("network", "obs_ws_port", obs_ws_port))
	obs_ws_password = str(cfg.get_value("network", "obs_ws_password", obs_ws_password))
	obs_pause_scene_name = str(cfg.get_value("network", "obs_pause_scene_name", obs_pause_scene_name))
	obs_main_scene_name = str(cfg.get_value("network", "obs_main_scene_name", obs_main_scene_name))
	obs_log_program_scene = bool(cfg.get_value("network", "obs_log_program_scene", obs_log_program_scene))
	obs_reconnect_sec = float(cfg.get_value("network", "obs_reconnect_sec", obs_reconnect_sec))
	spawn_result_file_path = str(
		cfg.get_value("network", "spawn_result_file_path", spawn_result_file_path)
	)
	spawn_result_file_poll_sec = float(
		cfg.get_value("network", "spawn_result_file_poll_sec", spawn_result_file_poll_sec)
	)
	spawn_result_file_icons = bool(
		cfg.get_value("network", "spawn_result_file_icons", spawn_result_file_icons)
	)
	spend_lock_file_path = str(
		cfg.get_value("network", "spend_lock_file_path", spend_lock_file_path)
	)
	spend_lock_poll_sec = float(
		cfg.get_value("network", "spend_lock_poll_sec", spend_lock_poll_sec)
	)
	spend_indicator_visible = bool(
		cfg.get_value("network", "spend_indicator_visible", spend_indicator_visible)
	)
	# Prefer [ui] spend corner/margins; fall back to legacy [network] once.
	if cfg.has_section_key("ui", "spend_indicator_corner"):
		spend_indicator_corner = clampi(
			int(cfg.get_value("ui", "spend_indicator_corner", spend_indicator_corner)), 0, 3
		)
		spend_indicator_margin_x = int(
			cfg.get_value("ui", "spend_indicator_margin_x", spend_indicator_margin_x)
		)
		spend_indicator_margin_y = int(
			cfg.get_value("ui", "spend_indicator_margin_y", spend_indicator_margin_y)
		)
	else:
		spend_indicator_corner = clampi(
			int(cfg.get_value("network", "spend_indicator_corner", spend_indicator_corner)),
			0,
			3
		)
		spend_indicator_margin_x = int(
			cfg.get_value("network", "spend_indicator_margin_x", spend_indicator_margin_x)
		)
		spend_indicator_margin_y = int(
			cfg.get_value("network", "spend_indicator_margin_y", spend_indicator_margin_y)
		)
	spend_indicator_font_size_px = clampi(
		int(cfg.get_value("network", "spend_indicator_font_size_px", spend_indicator_font_size_px)),
		8,
		64
	)
	spend_indicator_padding_h_px = clampi(
		int(cfg.get_value("ui", "spend_indicator_padding_h_px", spend_indicator_padding_h_px)), 0, 64
	)
	spend_indicator_padding_v_px = clampi(
		int(cfg.get_value("ui", "spend_indicator_padding_v_px", spend_indicator_padding_v_px)), 0, 64
	)
	spend_indicator_chrome_style = _SpdUiArt.normalize_chrome_style_id(
		str(cfg.get_value("network", "spend_indicator_chrome_style", spend_indicator_chrome_style))
	)
	spend_indicator_chrome_scale = clampf(
		float(cfg.get_value("network", "spend_indicator_chrome_scale", spend_indicator_chrome_scale)),
		0.5,
		4.0
	)
	ws_reconnect_sec = float(cfg.get_value("network", "ws_reconnect_sec", ws_reconnect_sec))
	http_fallback_enabled = bool(cfg.get_value("network", "http_fallback_enabled", http_fallback_enabled))
	http_fallback_url = str(cfg.get_value("network", "http_fallback_url", http_fallback_url))
	http_poll_interval_sec = float(
		cfg.get_value("network", "http_poll_interval_sec", http_poll_interval_sec)
	)
	free_promos_http_url = str(cfg.get_value("network", "free_promos_http_url", free_promos_http_url))
	free_promos_poll_sec = maxf(0.5, float(cfg.get_value("network", "free_promos_poll_sec", free_promos_poll_sec)))
	double_points_poll_sec = maxf(
		0.5, float(cfg.get_value("network", "double_points_poll_sec", double_points_poll_sec))
	)
	summon_march_enabled = bool(cfg.get_value("network", "summon_march_enabled", summon_march_enabled))
	summon_march_base_url = str(cfg.get_value("network", "summon_march_base_url", summon_march_base_url))
	summon_march_poll_sec = maxf(0.15, float(cfg.get_value("network", "summon_march_poll_sec", summon_march_poll_sec)))
	summon_march_skip_backlog = bool(cfg.get_value("network", "summon_march_skip_backlog", summon_march_skip_backlog))
	summon_march_duration_sec = maxf(1.0, float(cfg.get_value("network", "summon_march_duration_sec", summon_march_duration_sec)))
	summon_march_max_concurrent = clampi(int(cfg.get_value("network", "summon_march_max_concurrent", summon_march_max_concurrent)), 1, 32)
	summon_march_lane_y_fraction = clampf(float(cfg.get_value("network", "summon_march_lane_y_fraction", summon_march_lane_y_fraction)), 0.05, 0.95)
	if cfg.has_section_key("network", "summon_march_lane_y_min_fraction"):
		summon_march_lane_y_min_fraction = clampf(
			float(cfg.get_value("network", "summon_march_lane_y_min_fraction", summon_march_lane_y_min_fraction)),
			0.05,
			0.95
		)
		summon_march_lane_y_max_fraction = clampf(
			float(cfg.get_value("network", "summon_march_lane_y_max_fraction", summon_march_lane_y_max_fraction)),
			0.05,
			0.95
		)
	else:
		var center := summon_march_lane_y_fraction
		summon_march_lane_y_min_fraction = clampf(center - 0.12, 0.05, 0.95)
		summon_march_lane_y_max_fraction = clampf(center + 0.12, 0.05, 0.95)
	if summon_march_lane_y_max_fraction < summon_march_lane_y_min_fraction:
		var swap := summon_march_lane_y_min_fraction
		summon_march_lane_y_min_fraction = summon_march_lane_y_max_fraction
		summon_march_lane_y_max_fraction = swap
	summon_march_lane_spacing_px = clampi(int(cfg.get_value("ui", "summon_march_lane_spacing_px", summon_march_lane_spacing_px)), 8, 200)
	summon_march_edge_margin_px = clampi(int(cfg.get_value("ui", "summon_march_edge_margin_px", summon_march_edge_margin_px)), 16, 400)
	summon_march_mob_fps = clampf(float(cfg.get_value("network", "summon_march_mob_fps", summon_march_mob_fps)), 0.5, 30.0)
	summon_march_sprite_size_px = clampi(int(cfg.get_value("ui", "summon_march_sprite_size_px", summon_march_sprite_size_px)), 16, 256)
	summon_march_show_username = bool(cfg.get_value("ui", "summon_march_show_username", summon_march_show_username))
	summon_march_username_centered = bool(
		cfg.get_value("ui", "summon_march_username_centered", summon_march_username_centered)
	)
	summon_march_show_monster_name = bool(cfg.get_value("ui", "summon_march_show_monster_name", summon_march_show_monster_name))
	summon_march_username_font_size_px = clampi(
		int(cfg.get_value("ui", "summon_march_username_font_size_px", summon_march_username_font_size_px)),
		6,
		48
	)
	summon_march_monster_font_size_px = clampi(
		int(cfg.get_value("ui", "summon_march_monster_font_size_px", summon_march_monster_font_size_px)),
		6,
		48
	)
	summon_march_username_color = _read_color_cfg(
		cfg, "ui", "summon_march_username_color", summon_march_username_color
	)
	summon_march_monster_color = _read_color_cfg(
		cfg, "ui", "summon_march_monster_color", summon_march_monster_color
	)
	summon_march_username_offset_x = int(
		cfg.get_value("ui", "summon_march_username_offset_x", summon_march_username_offset_x)
	)
	summon_march_username_offset_y = int(
		cfg.get_value("ui", "summon_march_username_offset_y", summon_march_username_offset_y)
	)
	summon_march_monster_offset_x = int(
		cfg.get_value("ui", "summon_march_monster_offset_x", summon_march_monster_offset_x)
	)
	summon_march_monster_offset_y = int(
		cfg.get_value("ui", "summon_march_monster_offset_y", summon_march_monster_offset_y)
	)
	summon_crowned_sprite_scale = clampf(
		float(cfg.get_value("ui", "summon_crowned_sprite_scale", summon_crowned_sprite_scale)),
		1.0,
		3.0
	)
	summon_crowned_mob_modulate = _read_color_cfg(
		cfg, "ui", "summon_crowned_mob_modulate", summon_crowned_mob_modulate
	)
	summon_crowned_show_glow = bool(
		cfg.get_value("ui", "summon_crowned_show_glow", summon_crowned_show_glow)
	)
	summon_crowned_glow_color = _read_color_cfg(
		cfg, "ui", "summon_crowned_glow_color", summon_crowned_glow_color
	)
	summon_crowned_glow_rays = clampi(
		int(cfg.get_value("ui", "summon_crowned_glow_rays", summon_crowned_glow_rays)), 3, 16
	)
	summon_crowned_glow_spin_deg = clampf(
		float(cfg.get_value("ui", "summon_crowned_glow_spin_deg", summon_crowned_glow_spin_deg)),
		0.0,
		720.0
	)
	summon_crowned_glow_radius_scale = clampf(
		float(cfg.get_value("ui", "summon_crowned_glow_radius_scale", summon_crowned_glow_radius_scale)),
		0.25,
		3.0
	)
	summon_crowned_show_crown = bool(
		cfg.get_value("ui", "summon_crowned_show_crown", summon_crowned_show_crown)
	)
	summon_crowned_crown_scale = clampf(
		float(cfg.get_value("ui", "summon_crowned_crown_scale", summon_crowned_crown_scale)),
		0.1,
		3.0
	)
	summon_crowned_crown_offset_y = clampf(
		float(cfg.get_value("ui", "summon_crowned_crown_offset_y", summon_crowned_crown_offset_y)),
		-1.5,
		1.5
	)
	summon_crowned_crown_modulate = _read_color_cfg(
		cfg, "ui", "summon_crowned_crown_modulate", summon_crowned_crown_modulate
	)
	summon_crowned_username_color = _read_color_cfg(
		cfg, "ui", "summon_crowned_username_color", summon_crowned_username_color
	)
	summon_crowned_username_font_size_px = clampi(
		int(cfg.get_value("ui", "summon_crowned_username_font_size_px", summon_crowned_username_font_size_px)),
		0,
		72
	)
	summon_crowned_show_star_prefix = bool(
		cfg.get_value("ui", "summon_crowned_show_star_prefix", summon_crowned_show_star_prefix)
	)
	bestiary_hud_enabled = bool(cfg.get_value("network", "bestiary_hud_enabled", bestiary_hud_enabled))
	bestiary_base_url = str(cfg.get_value("network", "bestiary_base_url", bestiary_base_url))
	bestiary_poll_sec = maxf(0.25, float(cfg.get_value("network", "bestiary_poll_sec", bestiary_poll_sec)))
	remote_settings_enabled = bool(
		cfg.get_value("network", "remote_settings_enabled", remote_settings_enabled)
	)
	remote_settings_base_url = str(
		cfg.get_value("network", "remote_settings_base_url", remote_settings_base_url)
	)
	remote_settings_poll_sec = maxf(
		0.5, float(cfg.get_value("network", "remote_settings_poll_sec", remote_settings_poll_sec))
	)
	remote_settings_push_on_save = bool(
		cfg.get_value("network", "remote_settings_push_on_save", remote_settings_push_on_save)
	)
	remote_settings_applied_revision = int(
		cfg.get_value(
			"network", "remote_settings_applied_revision", remote_settings_applied_revision
		)
	)
	bestiary_show_sprint_chip = bool(
		cfg.get_value("network", "bestiary_show_sprint_chip", bestiary_show_sprint_chip)
	)
	bestiary_show_heat_chip = bool(
		cfg.get_value("network", "bestiary_show_heat_chip", bestiary_show_heat_chip)
	)
	bestiary_show_hall = bool(cfg.get_value("network", "bestiary_show_hall", bestiary_show_hall))
	if cfg.has_section_key("ui", "bestiary_zone_x_px"):
		bestiary_zone_x_px = int(cfg.get_value("ui", "bestiary_zone_x_px", bestiary_zone_x_px))
		bestiary_zone_y_px = int(cfg.get_value("ui", "bestiary_zone_y_px", bestiary_zone_y_px))
		bestiary_zone_width_px = int(cfg.get_value("ui", "bestiary_zone_width_px", bestiary_zone_width_px))
		bestiary_zone_height_px = int(cfg.get_value("ui", "bestiary_zone_height_px", bestiary_zone_height_px))
		bestiary_zone_bottom_margin_px = int(
			cfg.get_value("ui", "bestiary_zone_bottom_margin_px", bestiary_zone_bottom_margin_px)
		)
		bestiary_panel_pad_px = clampi(
			int(cfg.get_value("ui", "bestiary_panel_pad_px", bestiary_panel_pad_px)), 0, 64
		)
	else:
		# Seed from alert zone once for older cfgs
		bestiary_zone_x_px = int(cfg.get_value("ui", "alert_zone_x_px", bestiary_zone_x_px))
		bestiary_zone_y_px = int(cfg.get_value("ui", "alert_zone_y_px", bestiary_zone_y_px))
	bestiary_exp_bar_width_px = clampi(
		int(cfg.get_value("ui", "bestiary_exp_bar_width_px", bestiary_exp_bar_width_px)), 64, 1600
	)
	bestiary_exp_bar_height_scale = clampf(
		float(cfg.get_value("ui", "bestiary_exp_bar_height_scale", bestiary_exp_bar_height_scale)),
		0.5,
		4.0
	)
	bestiary_hud_scale = clampf(
		float(cfg.get_value("ui", "bestiary_hud_scale", bestiary_hud_scale)), 0.5, 4.0
	)
	bestiary_chrome_scale = clampf(
		float(cfg.get_value("ui", "bestiary_chrome_scale", bestiary_chrome_scale)), 0.5, 4.0
	)
	bestiary_use_compact_exp_bar = bool(
		cfg.get_value("ui", "bestiary_use_compact_exp_bar", bestiary_use_compact_exp_bar)
	)
	bestiary_zone_font_size_px = clampi(
		int(cfg.get_value("ui", "bestiary_zone_font_size_px", bestiary_zone_font_size_px)), 8, 96
	)
	bestiary_header_format = str(
		cfg.get_value("ui", "bestiary_header_format", bestiary_header_format)
	).strip_edges()
	if bestiary_header_format.is_empty():
		bestiary_header_format = "Bestiary Lv {level} - {zone}"
	bestiary_chip_font_size_px = clampi(
		int(cfg.get_value("ui", "bestiary_chip_font_size_px", bestiary_chip_font_size_px)), 8, 96
	)
	bestiary_chip_name_max_chars = clampi(
		int(cfg.get_value("ui", "bestiary_chip_name_max_chars", bestiary_chip_name_max_chars)), 0, 32
	)
	bestiary_hall_name_max_chars = clampi(
		int(cfg.get_value("ui", "bestiary_hall_name_max_chars", bestiary_hall_name_max_chars)), 0, 32
	)
	bestiary_truncate_names = bool(
		cfg.get_value("ui", "bestiary_truncate_names", bestiary_truncate_names)
	)
	bestiary_show_xp_text = bool(cfg.get_value("ui", "bestiary_show_xp_text", bestiary_show_xp_text))
	bestiary_xp_text_over_bar = bool(
		cfg.get_value("ui", "bestiary_xp_text_over_bar", bestiary_xp_text_over_bar)
	)
	bestiary_level_up_banner_sec = maxf(
		0.5, float(cfg.get_value("ui", "bestiary_level_up_banner_sec", bestiary_level_up_banner_sec))
	)
	if cfg.has_section_key("ui", "bestiary_level_up_banner_scale"):
		bestiary_level_up_banner_scale = clampf(
			float(cfg.get_value("ui", "bestiary_level_up_banner_scale", bestiary_level_up_banner_scale)),
			0.25,
			8.0
		)
	elif cfg.has_section_key("ui", "bestiary_level_up_banner_width_px"):
		# Migrate old absolute width (~native 127px wide art).
		var old_w := float(cfg.get_value("ui", "bestiary_level_up_banner_width_px", 520))
		bestiary_level_up_banner_scale = clampf(old_w / 127.0, 0.25, 8.0)
	bestiary_level_up_banner_font_size_px = clampi(
		int(
			cfg.get_value(
				"ui", "bestiary_level_up_banner_font_size_px", bestiary_level_up_banner_font_size_px
			)
		),
		8,
		96
	)
	bestiary_zone_font_color = _read_color_cfg(
		cfg, "ui", "bestiary_zone_font_color", bestiary_zone_font_color
	)
	bestiary_xp_font_color = _read_color_cfg(cfg, "ui", "bestiary_xp_font_color", bestiary_xp_font_color)
	bestiary_sprint_font_color = _read_color_cfg(
		cfg, "ui", "bestiary_sprint_font_color", bestiary_sprint_font_color
	)
	bestiary_heat_font_color = _read_color_cfg(
		cfg, "ui", "bestiary_heat_font_color", bestiary_heat_font_color
	)
	bestiary_hall_font_color = _read_color_cfg(
		cfg, "ui", "bestiary_hall_font_color", bestiary_hall_font_color
	)
	bestiary_banner_font_color = _read_color_cfg(
		cfg, "ui", "bestiary_banner_font_color", bestiary_banner_font_color
	)
	bestiary_shatter_pip_claimed_color = _read_color_cfg(
		cfg, "ui", "bestiary_shatter_pip_claimed_color", bestiary_shatter_pip_claimed_color
	)
	bestiary_shatter_pip_unclaimed_color = _read_color_cfg(
		cfg, "ui", "bestiary_shatter_pip_unclaimed_color", bestiary_shatter_pip_unclaimed_color
	)
	audio_output_device = str(cfg.get_value("ui", "audio_output_device", audio_output_device)).strip_edges()
	if audio_output_device.is_empty():
		audio_output_device = "Default"
	audio_volume = clampf(float(cfg.get_value("ui", "audio_volume", audio_volume)), 0.0, 1.5)
	audio_mute = bool(cfg.get_value("ui", "audio_mute", audio_mute))
	shatter_sfx_enabled = bool(cfg.get_value("ui", "shatter_sfx_enabled", shatter_sfx_enabled))
	chrome_boxes = _read_chrome_boxes_cfg(cfg, "ui")
	custom_alerts_enabled = bool(
		cfg.get_value("ui", "custom_alerts_enabled", custom_alerts_enabled)
	)
	custom_alerts_interval_sec = maxf(
		5.0, float(cfg.get_value("ui", "custom_alerts_interval_sec", custom_alerts_interval_sec))
	)
	custom_alerts_hold_sec = maxf(
		0.5, float(cfg.get_value("ui", "custom_alerts_hold_sec", custom_alerts_hold_sec))
	)
	custom_alerts = _read_custom_alerts_cfg(cfg)
	show_pending_udp_alerts = bool(
		cfg.get_value("ui", "show_pending_udp_alerts", show_pending_udp_alerts)
	)
	hud_status_panel_visible = bool(
		cfg.get_value("ui", "hud_status_panel_visible", hud_status_panel_visible)
	)
	settings_window_width_px = clampi(
		int(cfg.get_value("ui", "settings_window_width_px", settings_window_width_px)), 600, 3840
	)
	settings_window_height_px = clampi(
		int(cfg.get_value("ui", "settings_window_height_px", settings_window_height_px)), 480, 2160
	)
	alert_queue_max = int(cfg.get_value("ui", "alert_queue_max", alert_queue_max))
	alert_hold_sec = float(cfg.get_value("ui", "alert_hold_sec", alert_hold_sec))
	alert_fade_in_sec = float(cfg.get_value("ui", "alert_fade_in_sec", alert_fade_in_sec))
	alert_fade_out_sec = float(cfg.get_value("ui", "alert_fade_out_sec", alert_fade_out_sec))
	alert_hold_sec_when_free = float(
		cfg.get_value("ui", "alert_hold_sec_when_free", alert_hold_sec_when_free)
	)
	alert_fade_in_sec_when_free = float(
		cfg.get_value("ui", "alert_fade_in_sec_when_free", alert_fade_in_sec_when_free)
	)
	alert_fade_out_sec_when_free = float(
		cfg.get_value("ui", "alert_fade_out_sec_when_free", alert_fade_out_sec_when_free)
	)
	show_ping_alerts = bool(cfg.get_value("ui", "show_ping_alerts", show_ping_alerts))
	show_failed_command_alerts = bool(
		cfg.get_value("ui", "show_failed_command_alerts", show_failed_command_alerts)
	)
	if cfg.has_section_key("ui", "alert_zone_x_px"):
		alert_zone_x_px = int(cfg.get_value("ui", "alert_zone_x_px", alert_zone_x_px))
		alert_zone_y_px = int(cfg.get_value("ui", "alert_zone_y_px", alert_zone_y_px))
		alert_zone_width_px = int(cfg.get_value("ui", "alert_zone_width_px", alert_zone_width_px))
		alert_zone_height_px = int(cfg.get_value("ui", "alert_zone_height_px", alert_zone_height_px))
		alert_zone_bottom_margin_px = int(
			cfg.get_value("ui", "alert_zone_bottom_margin_px", alert_zone_bottom_margin_px)
		)
	else:
		_migrate_alert_zone_from_legacy(cfg)
	if cfg.has_section_key("ui", "id_zone_x_px"):
		id_zone_x_px = int(cfg.get_value("ui", "id_zone_x_px", id_zone_x_px))
		id_zone_y_px = int(cfg.get_value("ui", "id_zone_y_px", id_zone_y_px))
		id_zone_width_px = int(cfg.get_value("ui", "id_zone_width_px", id_zone_width_px))
		id_zone_height_px = int(cfg.get_value("ui", "id_zone_height_px", id_zone_height_px))
		id_zone_bottom_margin_px = int(
			cfg.get_value("ui", "id_zone_bottom_margin_px", id_zone_bottom_margin_px)
		)
	else:
		_copy_id_zone_from_alert_zone()
	live_water_bottom_bar_px = clampi(
		int(cfg.get_value("ui", "live_water_bottom_bar_px", live_water_bottom_bar_px)),
		0,
		8192
	)
	live_water_left_strip_px = clampi(
		int(cfg.get_value("ui", "live_water_left_strip_px", live_water_left_strip_px)),
		0,
		8192
	)
	live_water_left_strip_top_px = clampi(
		int(cfg.get_value("ui", "live_water_left_strip_top_px", live_water_left_strip_top_px)),
		0,
		8192
	)
	live_water_gradient_fade_start = clampf(
		float(cfg.get_value("ui", "live_water_gradient_fade_start", live_water_gradient_fade_start)),
		0.0,
		1.0
	)
	live_water_gradient_fade_end = clampf(
		float(cfg.get_value("ui", "live_water_gradient_fade_end", live_water_gradient_fade_end)),
		0.0,
		1.0
	)
	if live_water_gradient_fade_end < live_water_gradient_fade_start:
		live_water_gradient_fade_end = live_water_gradient_fade_start
	live_water_edge_feather_v_px = clampi(
		int(cfg.get_value("ui", "live_water_edge_feather_v_px", live_water_edge_feather_v_px)),
		0,
		2048
	)
	window_per_pixel_transparency_enabled = bool(
		cfg.get_value("ui", "window_per_pixel_transparency_enabled", window_per_pixel_transparency_enabled)
	)
	render_max_fps = clampi(int(cfg.get_value("ui", "render_max_fps", render_max_fps)), 0, 480)
	alert_text_align = str(cfg.get_value("ui", "alert_text_align", alert_text_align))
	alert_chrome_style = _SpdUiArt.normalize_chrome_style_id(
		str(cfg.get_value("ui", "alert_chrome_style", alert_chrome_style))
	)
	alert_chrome_scale = clampf(
		float(cfg.get_value("ui", "alert_chrome_scale", alert_chrome_scale)), 0.5, 4.0
	)
	alert_title_font_size_px = int(
		cfg.get_value("ui", "alert_title_font_size_px", alert_title_font_size_px)
	)
	alert_subtitle_font_size_px = int(
		cfg.get_value("ui", "alert_subtitle_font_size_px", alert_subtitle_font_size_px)
	)
	alert_padding_h_px = clampi(
		int(cfg.get_value("ui", "alert_padding_h_px", alert_padding_h_px)), 0, 64
	)
	alert_padding_v_px = clampi(
		int(cfg.get_value("ui", "alert_padding_v_px", alert_padding_v_px)), 0, 64
	)
	alert_command_icon_size_px = int(
		cfg.get_value("ui", "alert_command_icon_size_px", alert_command_icon_size_px)
	)
	alert_mob_idle_anim_fps = float(
		cfg.get_value("ui", "alert_mob_idle_anim_fps", alert_mob_idle_anim_fps)
	)
	paid_notice_enabled = bool(cfg.get_value("ui", "paid_notice_enabled", paid_notice_enabled))
	paid_notice_queue_max = clampi(
		int(cfg.get_value("ui", "paid_notice_queue_max", paid_notice_queue_max)), 1, 32
	)
	paid_notice_default_ttl_sec = maxf(
		0.5, float(cfg.get_value("ui", "paid_notice_default_ttl_sec", paid_notice_default_ttl_sec))
	)
	paid_notice_fade_in_sec = maxf(
		0.05, float(cfg.get_value("ui", "paid_notice_fade_in_sec", paid_notice_fade_in_sec))
	)
	paid_notice_fade_out_sec = maxf(
		0.05, float(cfg.get_value("ui", "paid_notice_fade_out_sec", paid_notice_fade_out_sec))
	)
	paid_notice_zone_x_px = int(cfg.get_value("ui", "paid_notice_zone_x_px", paid_notice_zone_x_px))
	paid_notice_zone_y_px = int(cfg.get_value("ui", "paid_notice_zone_y_px", paid_notice_zone_y_px))
	paid_notice_zone_width_px = clampi(
		int(cfg.get_value("ui", "paid_notice_zone_width_px", paid_notice_zone_width_px)), 64, 1920
	)
	paid_notice_zone_height_px = clampi(
		int(cfg.get_value("ui", "paid_notice_zone_height_px", paid_notice_zone_height_px)), 0, 1080
	)
	paid_notice_zone_bottom_margin_px = int(
		cfg.get_value("ui", "paid_notice_zone_bottom_margin_px", paid_notice_zone_bottom_margin_px)
	)
	paid_notice_chrome_style = _SpdUiArt.normalize_chrome_style_id(
		str(cfg.get_value("ui", "paid_notice_chrome_style", paid_notice_chrome_style))
	)
	paid_notice_chrome_scale = clampf(
		float(cfg.get_value("ui", "paid_notice_chrome_scale", paid_notice_chrome_scale)), 0.5, 4.0
	)
	paid_notice_kind_font_size_px = clampi(
		int(cfg.get_value("ui", "paid_notice_kind_font_size_px", paid_notice_kind_font_size_px)),
		8,
		96
	)
	paid_notice_title_font_size_px = clampi(
		int(cfg.get_value("ui", "paid_notice_title_font_size_px", paid_notice_title_font_size_px)),
		8,
		96
	)
	paid_notice_body_font_size_px = clampi(
		int(cfg.get_value("ui", "paid_notice_body_font_size_px", paid_notice_body_font_size_px)),
		8,
		96
	)
	paid_notice_kind_font_color = _read_color_cfg(
		cfg, "ui", "paid_notice_kind_font_color", paid_notice_kind_font_color
	)
	paid_notice_title_font_color = _read_color_cfg(
		cfg, "ui", "paid_notice_title_font_color", paid_notice_title_font_color
	)
	paid_notice_body_font_color = _read_color_cfg(
		cfg, "ui", "paid_notice_body_font_color", paid_notice_body_font_color
	)
	paid_notice_text_shadow = bool(
		cfg.get_value("ui", "paid_notice_text_shadow", paid_notice_text_shadow)
	)
	paid_notice_padding_h_px = clampi(
		int(cfg.get_value("ui", "paid_notice_padding_h_px", paid_notice_padding_h_px)), 0, 64
	)
	paid_notice_padding_v_px = clampi(
		int(cfg.get_value("ui", "paid_notice_padding_v_px", paid_notice_padding_v_px)), 0, 64
	)
	paid_notice_line_separation_px = clampi(
		int(cfg.get_value("ui", "paid_notice_line_separation_px", paid_notice_line_separation_px)),
		0,
		48
	)
	paid_notice_text_align = _normalize_paid_notice_text_align(
		str(cfg.get_value("ui", "paid_notice_text_align", paid_notice_text_align))
	)
	paid_notice_pop_scale = bool(
		cfg.get_value("ui", "paid_notice_pop_scale", paid_notice_pop_scale)
	)
	paid_notice_show_on_live = bool(
		cfg.get_value("ui", "paid_notice_show_on_live", paid_notice_show_on_live)
	)
	paid_notice_show_on_pause = bool(
		cfg.get_value("ui", "paid_notice_show_on_pause", paid_notice_show_on_pause)
	)
	_load_main_scene_show(cfg)
	paid_notice_enable_superchat = bool(
		cfg.get_value("ui", "paid_notice_enable_superchat", paid_notice_enable_superchat)
	)
	paid_notice_enable_gifted_membership = bool(
		cfg.get_value(
			"ui", "paid_notice_enable_gifted_membership", paid_notice_enable_gifted_membership
		)
	)
	paid_notice_enable_sub = bool(
		cfg.get_value("ui", "paid_notice_enable_sub", paid_notice_enable_sub)
	)
	paid_notice_enable_highlight = bool(
		cfg.get_value("ui", "paid_notice_enable_highlight", paid_notice_enable_highlight)
	)
	id_overlay_enabled = bool(cfg.get_value("ui", "id_overlay_enabled", id_overlay_enabled))
	if cfg.has_section_key("ui", "id_cell_width_px"):
		id_cell_width_px = int(cfg.get_value("ui", "id_cell_width_px", id_cell_width_px))
	elif cfg.has_section_key("ui", "id_cell_size_px"):
		id_cell_width_px = int(cfg.get_value("ui", "id_cell_size_px", id_cell_width_px))
	if cfg.has_section_key("ui", "id_cell_height_px"):
		id_cell_height_px = int(cfg.get_value("ui", "id_cell_height_px", id_cell_height_px))
	elif cfg.has_section_key("ui", "id_cell_size_px"):
		id_cell_height_px = int(cfg.get_value("ui", "id_cell_size_px", id_cell_height_px))
	id_cell_padding_px = int(cfg.get_value("ui", "id_cell_padding_px", id_cell_padding_px))
	id_known_icon_fraction = float(
		cfg.get_value("ui", "id_known_icon_fraction", id_known_icon_fraction)
	)
	id_flow_h_separation_px = int(
		cfg.get_value("ui", "id_flow_h_separation_px", id_flow_h_separation_px)
	)
	id_block_separation_px = int(
		cfg.get_value("ui", "id_block_separation_px", id_block_separation_px)
	)
	icon_cell_background_color = _read_icon_cell_color_cfg(cfg)
	free_promos_panel_visible = bool(
		cfg.get_value("ui", "free_promos_panel_visible", free_promos_panel_visible)
	)
	free_promos_corner = clampi(
		int(cfg.get_value("ui", "free_promos_corner", free_promos_corner)),
		0,
		3
	)
	free_promos_margin_x = int(cfg.get_value("ui", "free_promos_margin_x", free_promos_margin_x))
	free_promos_margin_y = int(cfg.get_value("ui", "free_promos_margin_y", free_promos_margin_y))
	free_promos_padding_h_px = clampi(
		int(cfg.get_value("ui", "free_promos_padding_h_px", free_promos_padding_h_px)), 0, 64
	)
	free_promos_padding_v_px = clampi(
		int(cfg.get_value("ui", "free_promos_padding_v_px", free_promos_padding_v_px)), 0, 64
	)
	free_promos_font_size_px = clampi(
		int(cfg.get_value("ui", "free_promos_font_size_px", free_promos_font_size_px)),
		8,
		36
	)
	free_promos_max_width_px = clampi(
		int(cfg.get_value("ui", "free_promos_max_width_px", free_promos_max_width_px)),
		160,
		1600
	)
	free_promos_chrome_style = _SpdUiArt.normalize_chrome_style_id(
		str(cfg.get_value("ui", "free_promos_chrome_style", free_promos_chrome_style))
	)
	free_promos_chrome_scale = clampf(
		float(cfg.get_value("ui", "free_promos_chrome_scale", free_promos_chrome_scale)), 0.5, 4.0
	)
	double_points_panel_visible = bool(
		cfg.get_value("ui", "double_points_panel_visible", double_points_panel_visible)
	)
	double_points_corner = clampi(
		int(cfg.get_value("ui", "double_points_corner", double_points_corner)), 0, 3
	)
	double_points_margin_x = int(
		cfg.get_value("ui", "double_points_margin_x", double_points_margin_x)
	)
	double_points_margin_y = int(
		cfg.get_value("ui", "double_points_margin_y", double_points_margin_y)
	)
	double_points_padding_h_px = clampi(
		int(cfg.get_value("ui", "double_points_padding_h_px", double_points_padding_h_px)), 0, 64
	)
	double_points_padding_v_px = clampi(
		int(cfg.get_value("ui", "double_points_padding_v_px", double_points_padding_v_px)), 0, 64
	)
	double_points_font_size_px = clampi(
		int(cfg.get_value("ui", "double_points_font_size_px", double_points_font_size_px)), 8, 48
	)
	double_points_font_color = _read_color_cfg(
		cfg, "ui", "double_points_font_color", double_points_font_color
	)
	double_points_chrome_style = _SpdUiArt.normalize_chrome_style_id(
		str(cfg.get_value("ui", "double_points_chrome_style", double_points_chrome_style))
	)
	double_points_chrome_scale = clampf(
		float(cfg.get_value("ui", "double_points_chrome_scale", double_points_chrome_scale)), 0.5, 4.0
	)
	_load_vertical_layout(cfg)
	_apply_render_limits()
	settings_loaded.emit()


func live_water_gradient_for(for_control: Control) -> Vector2:
	var L := layout_data_for(for_control)
	var gs := clampf(L.live_water_gradient_fade_start, 0.0, 1.0)
	var ge := clampf(L.live_water_gradient_fade_end, 0.0, 1.0)
	if ge < gs:
		ge = gs
	return Vector2(gs, ge)


func _apply_render_limits() -> void:
	var cap := render_max_fps
	Engine.max_fps = cap if cap > 0 else 0


func layout_canvas_size(for_control: Control) -> Vector2:
	var vp: Viewport = for_control.get_viewport()
	if vp:
		var r: Rect2 = vp.get_visible_rect()
		if r.size.x >= 1.0 and r.size.y >= 1.0:
			return r.size
	return for_control.get_viewport_rect().size


## Normalized L-shape params for shaders: x = bottom band height fraction, y = left strip width fraction, z = left strip top Y fraction.
func live_water_l_shape_uv_vector(for_control: Control) -> Vector3:
	var L := layout_data_for(for_control)
	var sz := layout_canvas_size(for_control)
	var denom_y := maxf(sz.y, 1.0)
	var denom_x := maxf(sz.x, 1.0)
	var bf := clampf(float(L.live_water_bottom_bar_px) / denom_y, 0.0, 1.0)
	var lf := clampf(float(L.live_water_left_strip_px) / denom_x, 0.0, 1.0)
	var lt := clampf(float(L.live_water_left_strip_top_px) / denom_y, 0.0, 1.0)
	return Vector3(bf, lf, lt)


## Soft edge height as UV.y fraction for [code]edge_feather_v[/code] shader uniform.
func live_water_edge_feather_v_uv(for_control: Control) -> float:
	var L := layout_data_for(for_control)
	var sz := layout_canvas_size(for_control)
	return clampf(float(maxi(0, L.live_water_edge_feather_v_px)) / maxf(sz.y, 1.0), 0.0, 1.0)


func layout_profile_for(node: Node) -> StringName:
	var n: Node = node
	while n:
		if n.has_method("get_layout_profile"):
			return n.call("get_layout_profile") as StringName
		if "layout_profile" in n:
			var p: Variant = n.get("layout_profile")
			if typeof(p) == TYPE_STRING_NAME or typeof(p) == TYPE_STRING:
				var s := StringName(str(p))
				if s == LAYOUT_VERTICAL or s == LAYOUT_MAIN:
					return s
		n = n.get_parent()
	return LAYOUT_MAIN


func is_vertical_layout(node: Node) -> bool:
	return layout_profile_for(node) == LAYOUT_VERTICAL


## Main layout snapshot (current CompanionConfig zone fields) or vertical_layout.
func layout_data_for(node: Node) -> UiLayoutData:
	if is_vertical_layout(node):
		return vertical_layout
	return _main_layout_snapshot()


func _main_layout_snapshot() -> UiLayoutData:
	var L := UiLayoutData.new()
	L.alert_zone_x_px = alert_zone_x_px
	L.alert_zone_y_px = alert_zone_y_px
	L.alert_zone_width_px = alert_zone_width_px
	L.alert_zone_height_px = alert_zone_height_px
	L.alert_zone_bottom_margin_px = alert_zone_bottom_margin_px
	L.id_zone_x_px = id_zone_x_px
	L.id_zone_y_px = id_zone_y_px
	L.id_zone_width_px = id_zone_width_px
	L.id_zone_height_px = id_zone_height_px
	L.id_zone_bottom_margin_px = id_zone_bottom_margin_px
	L.bestiary_zone_x_px = bestiary_zone_x_px
	L.bestiary_zone_y_px = bestiary_zone_y_px
	L.bestiary_zone_width_px = bestiary_zone_width_px
	L.bestiary_zone_height_px = bestiary_zone_height_px
	L.bestiary_zone_bottom_margin_px = bestiary_zone_bottom_margin_px
	L.paid_notice_zone_x_px = paid_notice_zone_x_px
	L.paid_notice_zone_y_px = paid_notice_zone_y_px
	L.paid_notice_zone_width_px = paid_notice_zone_width_px
	L.paid_notice_zone_height_px = paid_notice_zone_height_px
	L.paid_notice_zone_bottom_margin_px = paid_notice_zone_bottom_margin_px
	L.live_water_bottom_bar_px = live_water_bottom_bar_px
	L.live_water_left_strip_px = live_water_left_strip_px
	L.live_water_left_strip_top_px = live_water_left_strip_top_px
	L.live_water_gradient_fade_start = live_water_gradient_fade_start
	L.live_water_gradient_fade_end = live_water_gradient_fade_end
	L.live_water_edge_feather_v_px = live_water_edge_feather_v_px
	L.spend_indicator_corner = spend_indicator_corner
	L.spend_indicator_margin_x = spend_indicator_margin_x
	L.spend_indicator_margin_y = spend_indicator_margin_y
	L.free_promos_corner = free_promos_corner
	L.free_promos_margin_x = free_promos_margin_x
	L.free_promos_margin_y = free_promos_margin_y
	L.double_points_corner = double_points_corner
	L.double_points_margin_x = double_points_margin_x
	L.double_points_margin_y = double_points_margin_y
	L.chrome_boxes = chrome_boxes
	L.show_live_water = true
	L.show_title = true
	L.show_chrome_boxes = true
	L.show_id_overlay = id_overlay_enabled
	L.show_alerts = true
	L.show_tip_toasts = custom_alerts_enabled
	L.show_paid_notices = paid_notice_enabled
	L.show_bestiary = bestiary_hud_enabled
	L.show_summon_march = summon_march_enabled
	L.show_spend_indicator = spend_indicator_visible
	L.show_free_promos = free_promos_panel_visible
	L.show_double_points = double_points_panel_visible
	L.hide_spend_when_off = false
	ensure_main_scene_show()
	L.scene_show = main_scene_show.duplicate(true)
	L.ensure_scene_show()
	return L


func chrome_boxes_for(node: Node) -> Array:
	if is_vertical_layout(node):
		return vertical_layout.chrome_boxes
	return chrome_boxes


func duplicate_chrome_boxes_for_profile(profile: StringName) -> Array:
	var src: Array = vertical_layout.chrome_boxes if profile == LAYOUT_VERTICAL else chrome_boxes
	var out: Array = []
	for i in range(src.size()):
		out.append(normalize_chrome_box(src[i], i + 1).duplicate(true))
	return out


func element_enabled(node: Node, key: String) -> bool:
	var L := layout_data_for(node)
	match key:
		"live_water":
			return L.show_live_water
		"title":
			return L.show_title
		"chrome_boxes":
			return L.show_chrome_boxes
		"id_overlay":
			return L.show_id_overlay and (id_overlay_enabled if not is_vertical_layout(node) else true)
		"alerts":
			return L.show_alerts
		"tip_toasts":
			return L.show_tip_toasts and (
				custom_alerts_enabled if not is_vertical_layout(node) else true
			)
		"paid_notices":
			return L.show_paid_notices and (paid_notice_enabled if not is_vertical_layout(node) else true)
		"bestiary":
			return L.show_bestiary and (bestiary_hud_enabled if not is_vertical_layout(node) else true)
		"summon_march":
			return L.show_summon_march and (summon_march_enabled if not is_vertical_layout(node) else true)
		"spend_indicator":
			return L.show_spend_indicator and (spend_indicator_visible if not is_vertical_layout(node) else true)
		"free_promos":
			return L.show_free_promos and (free_promos_panel_visible if not is_vertical_layout(node) else true)
		"double_points":
			return L.show_double_points and (
				double_points_panel_visible if not is_vertical_layout(node) else true
			)
		_:
			return true
	return true


## OBS program scene → pause / main / other (pause wins if both markers match).
func classify_obs_scene(scene_name: String) -> StringName:
	var sn := scene_name.strip_edges()
	var pause_m := obs_pause_scene_name.strip_edges()
	if not pause_m.is_empty() and sn.contains(pause_m):
		return SCENE_PAUSE
	var main_m := obs_main_scene_name.strip_edges()
	if not main_m.is_empty() and sn.contains(main_m):
		return SCENE_MAIN
	return SCENE_OTHER


func ensure_main_scene_show() -> void:
	var defaults := UiLayoutData.default_scene_show()
	# Migrate legacy paid live/pause into paid_notices row when scene_show missing.
	if main_scene_show.is_empty():
		main_scene_show = defaults.duplicate(true)
		var paid: Dictionary = main_scene_show.get("paid_notices", {})
		paid["pause"] = paid_notice_show_on_pause
		paid["main"] = paid_notice_show_on_live
		paid["other"] = paid_notice_show_on_live
		main_scene_show["paid_notices"] = paid
	var merged: Dictionary = {}
	for key in UiLayoutData.element_keys():
		var fb: Dictionary = defaults.get(key, {"pause": true, "main": true, "other": true})
		merged[key] = UiLayoutData.normalize_scene_entry(main_scene_show.get(key, null), fb)
	main_scene_show = merged


## Feature enable (layout show_* / globals) AND OBS scene gate for this canvas.
func element_visible_on_scene(node: Node, key: String, kind: StringName) -> bool:
	if not element_enabled(node, key):
		return false
	var L := layout_data_for(node)
	return L.allows_scene(key, kind)


func _load_main_scene_show(cfg: ConfigFile) -> void:
	main_scene_show = {}
	if cfg.has_section_key("ui", "main_scene_show"):
		var raw: Variant = cfg.get_value("ui", "main_scene_show", {})
		if typeof(raw) == TYPE_DICTIONARY:
			main_scene_show = (raw as Dictionary).duplicate(true)
	ensure_main_scene_show()


func _save_main_scene_show(cfg: ConfigFile) -> void:
	ensure_main_scene_show()
	cfg.set_value("ui", "main_scene_show", main_scene_show.duplicate(true))


func _load_vertical_layout(cfg: ConfigFile) -> void:
	var seeded := UiLayoutData.default_vertical()
	vertical_window_enabled = bool(
		cfg.get_value("ui_vertical", "vertical_window_enabled", vertical_window_enabled)
	)
	var sec := "ui_vertical"
	var L := vertical_layout if vertical_layout != null else UiLayoutData.default_vertical()
	L.alert_zone_x_px = int(cfg.get_value(sec, "alert_zone_x_px", seeded.alert_zone_x_px))
	L.alert_zone_y_px = int(cfg.get_value(sec, "alert_zone_y_px", seeded.alert_zone_y_px))
	L.alert_zone_width_px = int(cfg.get_value(sec, "alert_zone_width_px", seeded.alert_zone_width_px))
	L.alert_zone_height_px = int(cfg.get_value(sec, "alert_zone_height_px", seeded.alert_zone_height_px))
	L.alert_zone_bottom_margin_px = int(
		cfg.get_value(sec, "alert_zone_bottom_margin_px", seeded.alert_zone_bottom_margin_px)
	)
	L.id_zone_x_px = int(cfg.get_value(sec, "id_zone_x_px", seeded.id_zone_x_px))
	L.id_zone_y_px = int(cfg.get_value(sec, "id_zone_y_px", seeded.id_zone_y_px))
	L.id_zone_width_px = int(cfg.get_value(sec, "id_zone_width_px", seeded.id_zone_width_px))
	L.id_zone_height_px = int(cfg.get_value(sec, "id_zone_height_px", seeded.id_zone_height_px))
	L.id_zone_bottom_margin_px = int(
		cfg.get_value(sec, "id_zone_bottom_margin_px", seeded.id_zone_bottom_margin_px)
	)
	L.bestiary_zone_x_px = int(cfg.get_value(sec, "bestiary_zone_x_px", seeded.bestiary_zone_x_px))
	L.bestiary_zone_y_px = int(cfg.get_value(sec, "bestiary_zone_y_px", seeded.bestiary_zone_y_px))
	L.bestiary_zone_width_px = int(
		cfg.get_value(sec, "bestiary_zone_width_px", seeded.bestiary_zone_width_px)
	)
	L.bestiary_zone_height_px = int(
		cfg.get_value(sec, "bestiary_zone_height_px", seeded.bestiary_zone_height_px)
	)
	L.bestiary_zone_bottom_margin_px = int(
		cfg.get_value(sec, "bestiary_zone_bottom_margin_px", seeded.bestiary_zone_bottom_margin_px)
	)
	L.paid_notice_zone_x_px = int(
		cfg.get_value(sec, "paid_notice_zone_x_px", seeded.paid_notice_zone_x_px)
	)
	L.paid_notice_zone_y_px = int(
		cfg.get_value(sec, "paid_notice_zone_y_px", seeded.paid_notice_zone_y_px)
	)
	L.paid_notice_zone_width_px = int(
		cfg.get_value(sec, "paid_notice_zone_width_px", seeded.paid_notice_zone_width_px)
	)
	L.paid_notice_zone_height_px = int(
		cfg.get_value(sec, "paid_notice_zone_height_px", seeded.paid_notice_zone_height_px)
	)
	L.paid_notice_zone_bottom_margin_px = int(
		cfg.get_value(
			sec, "paid_notice_zone_bottom_margin_px", seeded.paid_notice_zone_bottom_margin_px
		)
	)
	L.live_water_bottom_bar_px = clampi(
		int(cfg.get_value(sec, "live_water_bottom_bar_px", seeded.live_water_bottom_bar_px)), 0, 8192
	)
	L.live_water_left_strip_px = clampi(
		int(cfg.get_value(sec, "live_water_left_strip_px", seeded.live_water_left_strip_px)), 0, 8192
	)
	L.live_water_left_strip_top_px = clampi(
		int(cfg.get_value(sec, "live_water_left_strip_top_px", seeded.live_water_left_strip_top_px)),
		0,
		8192
	)
	L.live_water_gradient_fade_start = clampf(
		float(
			cfg.get_value(sec, "live_water_gradient_fade_start", seeded.live_water_gradient_fade_start)
		),
		0.0,
		1.0
	)
	L.live_water_gradient_fade_end = clampf(
		float(cfg.get_value(sec, "live_water_gradient_fade_end", seeded.live_water_gradient_fade_end)),
		0.0,
		1.0
	)
	if L.live_water_gradient_fade_end < L.live_water_gradient_fade_start:
		L.live_water_gradient_fade_end = L.live_water_gradient_fade_start
	L.live_water_edge_feather_v_px = clampi(
		int(cfg.get_value(sec, "live_water_edge_feather_v_px", seeded.live_water_edge_feather_v_px)),
		0,
		2048
	)
	L.spend_indicator_corner = clampi(
		int(cfg.get_value(sec, "spend_indicator_corner", seeded.spend_indicator_corner)), 0, 3
	)
	L.spend_indicator_margin_x = int(
		cfg.get_value(sec, "spend_indicator_margin_x", seeded.spend_indicator_margin_x)
	)
	L.spend_indicator_margin_y = int(
		cfg.get_value(sec, "spend_indicator_margin_y", seeded.spend_indicator_margin_y)
	)
	L.free_promos_corner = clampi(
		int(cfg.get_value(sec, "free_promos_corner", seeded.free_promos_corner)), 0, 3
	)
	L.free_promos_margin_x = int(cfg.get_value(sec, "free_promos_margin_x", seeded.free_promos_margin_x))
	L.free_promos_margin_y = int(cfg.get_value(sec, "free_promos_margin_y", seeded.free_promos_margin_y))
	L.double_points_corner = clampi(
		int(cfg.get_value(sec, "double_points_corner", seeded.double_points_corner)), 0, 3
	)
	L.double_points_margin_x = int(
		cfg.get_value(sec, "double_points_margin_x", seeded.double_points_margin_x)
	)
	L.double_points_margin_y = int(
		cfg.get_value(sec, "double_points_margin_y", seeded.double_points_margin_y)
	)
	L.chrome_boxes = _read_chrome_boxes_cfg(cfg, sec)
	L.show_live_water = bool(cfg.get_value(sec, "show_live_water", true))
	L.show_title = bool(cfg.get_value(sec, "show_title", true))
	L.show_chrome_boxes = bool(cfg.get_value(sec, "show_chrome_boxes", true))
	L.show_id_overlay = bool(cfg.get_value(sec, "show_id_overlay", true))
	L.show_alerts = bool(cfg.get_value(sec, "show_alerts", true))
	L.show_tip_toasts = bool(cfg.get_value(sec, "show_tip_toasts", true))
	L.show_paid_notices = bool(cfg.get_value(sec, "show_paid_notices", true))
	L.show_bestiary = bool(cfg.get_value(sec, "show_bestiary", true))
	L.show_summon_march = bool(cfg.get_value(sec, "show_summon_march", true))
	L.show_spend_indicator = bool(cfg.get_value(sec, "show_spend_indicator", true))
	L.show_free_promos = bool(cfg.get_value(sec, "show_free_promos", true))
	L.show_double_points = bool(cfg.get_value(sec, "show_double_points", true))
	L.hide_spend_when_off = bool(
		cfg.get_value(sec, "hide_spend_when_off", seeded.hide_spend_when_off)
	)
	if cfg.has_section_key(sec, "scene_show"):
		var raw_ss: Variant = cfg.get_value(sec, "scene_show", {})
		if typeof(raw_ss) == TYPE_DICTIONARY:
			L.scene_show = (raw_ss as Dictionary).duplicate(true)
	else:
		# Seed from show_* masters: if an element is off on vertical, all scene buckets off.
		L.scene_show = UiLayoutData.default_scene_show()
		_seed_vertical_scene_show_from_toggles(L)
	L.ensure_scene_show()
	vertical_layout = L


func _seed_vertical_scene_show_from_toggles(L: UiLayoutData) -> void:
	var map := {
		"live_water": L.show_live_water,
		"title": L.show_title,
		"chrome_boxes": L.show_chrome_boxes,
		"id_overlay": L.show_id_overlay,
		"alerts": L.show_alerts,
		"tip_toasts": L.show_tip_toasts,
		"paid_notices": L.show_paid_notices,
		"bestiary": L.show_bestiary,
		"summon_march": L.show_summon_march,
		"spend_indicator": L.show_spend_indicator,
		"free_promos": L.show_free_promos,
		"double_points": L.show_double_points,
	}
	for key in map.keys():
		if not bool(map[key]):
			L.scene_show[key] = {"pause": false, "main": false, "other": false}


func _save_vertical_layout(cfg: ConfigFile) -> void:
	var sec := "ui_vertical"
	var L := vertical_layout if vertical_layout != null else UiLayoutData.default_vertical()
	cfg.set_value(sec, "vertical_window_enabled", vertical_window_enabled)
	cfg.set_value(sec, "alert_zone_x_px", L.alert_zone_x_px)
	cfg.set_value(sec, "alert_zone_y_px", L.alert_zone_y_px)
	cfg.set_value(sec, "alert_zone_width_px", L.alert_zone_width_px)
	cfg.set_value(sec, "alert_zone_height_px", L.alert_zone_height_px)
	cfg.set_value(sec, "alert_zone_bottom_margin_px", L.alert_zone_bottom_margin_px)
	cfg.set_value(sec, "id_zone_x_px", L.id_zone_x_px)
	cfg.set_value(sec, "id_zone_y_px", L.id_zone_y_px)
	cfg.set_value(sec, "id_zone_width_px", L.id_zone_width_px)
	cfg.set_value(sec, "id_zone_height_px", L.id_zone_height_px)
	cfg.set_value(sec, "id_zone_bottom_margin_px", L.id_zone_bottom_margin_px)
	cfg.set_value(sec, "bestiary_zone_x_px", L.bestiary_zone_x_px)
	cfg.set_value(sec, "bestiary_zone_y_px", L.bestiary_zone_y_px)
	cfg.set_value(sec, "bestiary_zone_width_px", L.bestiary_zone_width_px)
	cfg.set_value(sec, "bestiary_zone_height_px", L.bestiary_zone_height_px)
	cfg.set_value(sec, "bestiary_zone_bottom_margin_px", L.bestiary_zone_bottom_margin_px)
	cfg.set_value(sec, "paid_notice_zone_x_px", L.paid_notice_zone_x_px)
	cfg.set_value(sec, "paid_notice_zone_y_px", L.paid_notice_zone_y_px)
	cfg.set_value(sec, "paid_notice_zone_width_px", L.paid_notice_zone_width_px)
	cfg.set_value(sec, "paid_notice_zone_height_px", L.paid_notice_zone_height_px)
	cfg.set_value(sec, "paid_notice_zone_bottom_margin_px", L.paid_notice_zone_bottom_margin_px)
	cfg.set_value(sec, "live_water_bottom_bar_px", L.live_water_bottom_bar_px)
	cfg.set_value(sec, "live_water_left_strip_px", L.live_water_left_strip_px)
	cfg.set_value(sec, "live_water_left_strip_top_px", L.live_water_left_strip_top_px)
	cfg.set_value(sec, "live_water_gradient_fade_start", L.live_water_gradient_fade_start)
	cfg.set_value(sec, "live_water_gradient_fade_end", L.live_water_gradient_fade_end)
	cfg.set_value(sec, "live_water_edge_feather_v_px", L.live_water_edge_feather_v_px)
	cfg.set_value(sec, "spend_indicator_corner", L.spend_indicator_corner)
	cfg.set_value(sec, "spend_indicator_margin_x", L.spend_indicator_margin_x)
	cfg.set_value(sec, "spend_indicator_margin_y", L.spend_indicator_margin_y)
	cfg.set_value(sec, "free_promos_corner", L.free_promos_corner)
	cfg.set_value(sec, "free_promos_margin_x", L.free_promos_margin_x)
	cfg.set_value(sec, "free_promos_margin_y", L.free_promos_margin_y)
	cfg.set_value(sec, "double_points_corner", L.double_points_corner)
	cfg.set_value(sec, "double_points_margin_x", L.double_points_margin_x)
	cfg.set_value(sec, "double_points_margin_y", L.double_points_margin_y)
	cfg.set_value(sec, "chrome_boxes", _serialize_chrome_boxes_list(L.chrome_boxes))
	cfg.set_value(sec, "show_live_water", L.show_live_water)
	cfg.set_value(sec, "show_title", L.show_title)
	cfg.set_value(sec, "show_chrome_boxes", L.show_chrome_boxes)
	cfg.set_value(sec, "show_id_overlay", L.show_id_overlay)
	cfg.set_value(sec, "show_alerts", L.show_alerts)
	cfg.set_value(sec, "show_tip_toasts", L.show_tip_toasts)
	cfg.set_value(sec, "show_paid_notices", L.show_paid_notices)
	cfg.set_value(sec, "show_bestiary", L.show_bestiary)
	cfg.set_value(sec, "show_summon_march", L.show_summon_march)
	cfg.set_value(sec, "show_spend_indicator", L.show_spend_indicator)
	cfg.set_value(sec, "show_free_promos", L.show_free_promos)
	cfg.set_value(sec, "show_double_points", L.show_double_points)
	cfg.set_value(sec, "hide_spend_when_off", L.hide_spend_when_off)
	L.ensure_scene_show()
	cfg.set_value(sec, "scene_show", L.scene_show.duplicate(true))


func _migrate_alert_zone_from_legacy(cfg: ConfigFile) -> void:
	var m: int = int(cfg.get_value("ui", "alert_layout_margin_px", 24))
	var wf: float = float(cfg.get_value("ui", "alert_slot_width_fraction", 0.18))
	var top_extra: int = int(cfg.get_value("ui", "id_overlay_top_offset_px", 188))
	alert_zone_x_px = m
	alert_zone_y_px = m + top_extra
	alert_zone_width_px = maxi(100, int(round(1920.0 * wf)))
	alert_zone_height_px = 0
	alert_zone_bottom_margin_px = m
	_copy_id_zone_from_alert_zone()


func _copy_id_zone_from_alert_zone() -> void:
	id_zone_x_px = alert_zone_x_px
	id_zone_y_px = alert_zone_y_px
	id_zone_width_px = alert_zone_width_px
	id_zone_height_px = alert_zone_height_px
	id_zone_bottom_margin_px = alert_zone_bottom_margin_px


func _normalize_paid_notice_text_align(raw: String) -> String:
	var a := raw.strip_edges().to_lower()
	if a == "center" or a == "centre" or a == "middle":
		return "center"
	return "left"


func _read_color_cfg(cfg: ConfigFile, section: String, key: String, fallback: Color) -> Color:
	if not cfg.has_section_key(section, key):
		return fallback
	var v: Variant = cfg.get_value(section, key, fallback)
	if v is Color:
		return v as Color
	if v is String:
		var s: String = (v as String).strip_edges()
		if s.is_valid_html_color():
			return Color(s)
	return fallback


func _read_icon_cell_color_cfg(cfg: ConfigFile) -> Color:
	return _read_color_cfg(cfg, "ui", "icon_cell_background_color", icon_cell_background_color)


## Positions [param ctrl] using pixel rectangle (viewport). Height 0 = fill down to bottom minus margin.
func apply_pixel_zone_layout(
	ctrl: Control,
	canvas: Vector2,
	x: int,
	y: int,
	w: int,
	h: int,
	bottom_margin: int
) -> void:
	var cx: int = int(canvas.x)
	var cy: int = int(canvas.y)
	# Keep the rect on-canvas so toasts/HUDs cannot sit entirely below/beside the view.
	x = clampi(x, 0, maxi(cx - 32, 0))
	y = clampi(y, 0, maxi(cy - 32, 0))
	var hh: int = h
	if hh <= 0:
		hh = cy - y - bottom_margin
	hh = maxi(hh, 32)
	w = maxi(w, 32)
	if x + w > cx:
		w = maxi(cx - x, 32)
	if y + hh > cy:
		hh = maxi(cy - y, 32)
	ctrl.set_anchors_preset(Control.PRESET_TOP_LEFT)
	ctrl.offset_left = float(x)
	ctrl.offset_top = float(y)
	ctrl.offset_right = float(x + w)
	ctrl.offset_bottom = float(y + hh)


func apply_alert_zone_layout(ctrl: Control, canvas: Vector2) -> void:
	var L := layout_data_for(ctrl)
	apply_pixel_zone_layout(
		ctrl,
		canvas,
		L.alert_zone_x_px,
		L.alert_zone_y_px,
		L.alert_zone_width_px,
		L.alert_zone_height_px,
		L.alert_zone_bottom_margin_px
	)


func apply_id_zone_layout(ctrl: Control, canvas: Vector2) -> void:
	var L := layout_data_for(ctrl)
	apply_pixel_zone_layout(
		ctrl,
		canvas,
		L.id_zone_x_px,
		L.id_zone_y_px,
		L.id_zone_width_px,
		L.id_zone_height_px,
		L.id_zone_bottom_margin_px
	)


func apply_bestiary_zone_layout(ctrl: Control, canvas: Vector2) -> void:
	## Width/X/Y always apply. Height 0 = temporary stub; [code]BestiaryHud[/code] refits to content.
	var L := layout_data_for(ctrl)
	var h := L.bestiary_zone_height_px
	if h <= 0:
		h = 64
	apply_pixel_zone_layout(
		ctrl,
		canvas,
		L.bestiary_zone_x_px,
		L.bestiary_zone_y_px,
		L.bestiary_zone_width_px,
		h,
		L.bestiary_zone_bottom_margin_px
	)


func apply_paid_notice_zone_layout(ctrl: Control, canvas: Vector2) -> void:
	var L := layout_data_for(ctrl)
	apply_pixel_zone_layout(
		ctrl,
		canvas,
		L.paid_notice_zone_x_px,
		L.paid_notice_zone_y_px,
		L.paid_notice_zone_width_px,
		L.paid_notice_zone_height_px,
		L.paid_notice_zone_bottom_margin_px
	)


func default_chrome_box(index: int = 1) -> Dictionary:
	return {
		"id": _new_chrome_box_id(),
		"name": "Chrome box %d" % index,
		"enabled": true,
		"style": "window",
		"zone_x_px": 48,
		"zone_y_px": 48,
		"zone_width_px": 240,
		"zone_height_px": 160,
		"zone_bottom_margin_px": 0,
		"chrome_scale": 1.0,
		"show_on_pause": false,
		"show_on_main": true,
		"show_on_other": true,
		"header_text": "",
		"body_text": "",
		"header_font_size_px": 24,
		"body_font_size_px": 16,
		"header_font_color": Color(1.0, 1.0, 0.27, 1.0),
		"body_font_color": Color(0.95, 0.92, 0.85, 1.0),
		"text_align": "left",
		"text_shadow": true,
		"padding_h_px": 14,
		"padding_v_px": 10,
		"line_separation_px": 6,
	}


func normalize_chrome_box(raw: Variant, fallback_index: int = 1) -> Dictionary:
	var base := default_chrome_box(fallback_index)
	if typeof(raw) != TYPE_DICTIONARY:
		return base
	var d: Dictionary = raw
	var name_s := str(d.get("name", base["name"])).strip_edges()
	if name_s.is_empty():
		name_s = str(base["name"])
	var id_s := str(d.get("id", "")).strip_edges()
	if id_s.is_empty():
		id_s = _new_chrome_box_id()
	var style_s: String = _SpdUiArt.normalize_chrome_style_id(str(d.get("style", base["style"])))
	var align_s := str(d.get("text_align", base["text_align"])).strip_edges().to_lower()
	if align_s == "centre" or align_s == "middle":
		align_s = "center"
	elif align_s != "center":
		align_s = "left"
	return {
		"id": id_s,
		"name": name_s,
		"enabled": bool(d.get("enabled", true)),
		"style": style_s,
		"zone_x_px": int(d.get("zone_x_px", base["zone_x_px"])),
		"zone_y_px": int(d.get("zone_y_px", base["zone_y_px"])),
		"zone_width_px": clampi(int(d.get("zone_width_px", base["zone_width_px"])), 32, 4096),
		"zone_height_px": clampi(int(d.get("zone_height_px", base["zone_height_px"])), 0, 4096),
		"zone_bottom_margin_px": int(d.get("zone_bottom_margin_px", 0)),
		"chrome_scale": clampf(float(d.get("chrome_scale", 1.0)), 0.5, 4.0),
		"show_on_pause": bool(d.get("show_on_pause", false)),
		"show_on_main": bool(
			d.get("show_on_main", d.get("show_on_live", true))
		),
		"show_on_other": bool(
			d.get("show_on_other", d.get("show_on_live", true))
		),
		"header_text": str(d.get("header_text", "")),
		"body_text": str(d.get("body_text", "")),
		"header_font_size_px": clampi(
			int(d.get("header_font_size_px", base["header_font_size_px"])), 8, 96
		),
		"body_font_size_px": clampi(
			int(d.get("body_font_size_px", base["body_font_size_px"])), 8, 96
		),
		"header_font_color": _chrome_box_color(
			d.get("header_font_color", base["header_font_color"]), base["header_font_color"]
		),
		"body_font_color": _chrome_box_color(
			d.get("body_font_color", base["body_font_color"]), base["body_font_color"]
		),
		"text_align": align_s,
		"text_shadow": bool(d.get("text_shadow", true)),
		"padding_h_px": clampi(int(d.get("padding_h_px", base["padding_h_px"])), 0, 64),
		"padding_v_px": clampi(int(d.get("padding_v_px", base["padding_v_px"])), 0, 64),
		"line_separation_px": clampi(
			int(d.get("line_separation_px", base["line_separation_px"])), 0, 48
		),
	}


func _chrome_box_color(v: Variant, fallback: Color) -> Color:
	if v is Color:
		return v as Color
	if v is String:
		var s: String = (v as String).strip_edges()
		if s.is_valid_html_color():
			return Color(s)
	return fallback


func duplicate_chrome_boxes() -> Array:
	var out: Array = []
	for i in range(chrome_boxes.size()):
		out.append(normalize_chrome_box(chrome_boxes[i], i + 1).duplicate(true))
	return out


func _new_chrome_box_id() -> String:
	return "cb_%d_%d" % [Time.get_ticks_usec(), randi() % 100000]


func _serialize_chrome_boxes() -> Array:
	return _serialize_chrome_boxes_list(chrome_boxes)


func _serialize_chrome_boxes_list(boxes: Array) -> Array:
	var out: Array = []
	var n := mini(boxes.size(), CHROME_BOXES_MAX)
	for i in range(n):
		out.append(normalize_chrome_box(boxes[i], i + 1))
	return out


func default_custom_alert(index: int = 1) -> Dictionary:
	return {
		"title": "Tip %d" % index,
		"subtitle": "",
		"enabled": true,
	}


func normalize_custom_alert(raw: Variant, fallback_index: int = 1) -> Dictionary:
	var base := default_custom_alert(fallback_index)
	if typeof(raw) != TYPE_DICTIONARY:
		return base
	var d: Dictionary = raw
	var title := str(d.get("title", base["title"])).strip_edges()
	if title.is_empty():
		title = str(base["title"])
	return {
		"title": title,
		"subtitle": str(d.get("subtitle", "")),
		"enabled": bool(d.get("enabled", true)),
	}


func duplicate_custom_alerts() -> Array:
	var out: Array = []
	for i in range(custom_alerts.size()):
		out.append(normalize_custom_alert(custom_alerts[i], i + 1).duplicate(true))
	return out


func _serialize_custom_alerts() -> Array:
	return _serialize_custom_alerts_list(custom_alerts)


func _serialize_custom_alerts_list(tips: Array) -> Array:
	var out: Array = []
	var n := mini(tips.size(), CUSTOM_ALERTS_MAX)
	for i in range(n):
		out.append(normalize_custom_alert(tips[i], i + 1))
	return out


func _read_custom_alerts_cfg(cfg: ConfigFile) -> Array:
	if not cfg.has_section_key("ui", "custom_alerts"):
		return []
	var raw: Variant = cfg.get_value("ui", "custom_alerts", [])
	if typeof(raw) != TYPE_ARRAY:
		return []
	return _serialize_custom_alerts_list(raw as Array)


func _read_chrome_boxes_cfg(cfg: ConfigFile, section: String = "ui") -> Array:
	if not cfg.has_section_key(section, "chrome_boxes"):
		return []
	var raw: Variant = cfg.get_value(section, "chrome_boxes", [])
	var out: Array = []
	if typeof(raw) != TYPE_ARRAY:
		return out
	var arr: Array = raw
	var n := mini(arr.size(), CHROME_BOXES_MAX)
	for i in range(n):
		out.append(normalize_chrome_box(arr[i], i + 1))
	return out


func save_settings() -> void:
	var cfg := ConfigFile.new()
	cfg.set_value("meta", "defaults_revision", defaults_revision)
	cfg.set_value("network", "game_ws_host", game_ws_host)
	cfg.set_value("network", "game_ws_port", game_ws_port)
	cfg.set_value("network", "streamerbot_udp_port", streamerbot_udp_port)
	cfg.set_value("network", "obs_scene_sync_enabled", obs_scene_sync_enabled)
	cfg.set_value("network", "obs_ws_host", obs_ws_host)
	cfg.set_value("network", "obs_ws_port", obs_ws_port)
	cfg.set_value("network", "obs_ws_password", obs_ws_password)
	cfg.set_value("network", "obs_pause_scene_name", obs_pause_scene_name)
	cfg.set_value("network", "obs_main_scene_name", obs_main_scene_name)
	cfg.set_value("network", "obs_log_program_scene", obs_log_program_scene)
	cfg.set_value("network", "obs_reconnect_sec", obs_reconnect_sec)
	cfg.set_value("network", "spawn_result_file_path", spawn_result_file_path)
	cfg.set_value("network", "spawn_result_file_poll_sec", spawn_result_file_poll_sec)
	cfg.set_value("network", "spawn_result_file_icons", spawn_result_file_icons)
	cfg.set_value("network", "spend_lock_file_path", spend_lock_file_path)
	cfg.set_value("network", "spend_lock_poll_sec", spend_lock_poll_sec)
	cfg.set_value("network", "spend_indicator_visible", spend_indicator_visible)
	cfg.set_value("ui", "spend_indicator_corner", spend_indicator_corner)
	cfg.set_value("ui", "spend_indicator_margin_x", spend_indicator_margin_x)
	cfg.set_value("ui", "spend_indicator_margin_y", spend_indicator_margin_y)
	cfg.set_value("ui", "spend_indicator_padding_h_px", spend_indicator_padding_h_px)
	cfg.set_value("ui", "spend_indicator_padding_v_px", spend_indicator_padding_v_px)
	# Keep writing legacy network keys for older tooling.
	cfg.set_value("network", "spend_indicator_corner", spend_indicator_corner)
	cfg.set_value("network", "spend_indicator_margin_x", spend_indicator_margin_x)
	cfg.set_value("network", "spend_indicator_margin_y", spend_indicator_margin_y)
	cfg.set_value("network", "spend_indicator_font_size_px", spend_indicator_font_size_px)
	cfg.set_value("network", "spend_indicator_chrome_style", spend_indicator_chrome_style)
	cfg.set_value("network", "spend_indicator_chrome_scale", spend_indicator_chrome_scale)
	cfg.set_value("network", "ws_reconnect_sec", ws_reconnect_sec)
	cfg.set_value("network", "http_fallback_enabled", http_fallback_enabled)
	cfg.set_value("network", "http_fallback_url", http_fallback_url)
	cfg.set_value("network", "http_poll_interval_sec", http_poll_interval_sec)
	cfg.set_value("network", "free_promos_http_url", free_promos_http_url)
	cfg.set_value("network", "free_promos_poll_sec", free_promos_poll_sec)
	cfg.set_value("network", "double_points_poll_sec", double_points_poll_sec)
	cfg.set_value("network", "summon_march_enabled", summon_march_enabled)
	cfg.set_value("network", "summon_march_base_url", summon_march_base_url)
	cfg.set_value("network", "summon_march_poll_sec", summon_march_poll_sec)
	cfg.set_value("network", "summon_march_skip_backlog", summon_march_skip_backlog)
	cfg.set_value("network", "summon_march_duration_sec", summon_march_duration_sec)
	cfg.set_value("network", "summon_march_max_concurrent", summon_march_max_concurrent)
	cfg.set_value("network", "summon_march_lane_y_fraction", summon_march_lane_y_fraction)
	cfg.set_value("network", "summon_march_lane_y_min_fraction", summon_march_lane_y_min_fraction)
	cfg.set_value("network", "summon_march_lane_y_max_fraction", summon_march_lane_y_max_fraction)
	cfg.set_value("network", "summon_march_mob_fps", summon_march_mob_fps)
	cfg.set_value("ui", "summon_march_lane_spacing_px", summon_march_lane_spacing_px)
	cfg.set_value("ui", "summon_march_edge_margin_px", summon_march_edge_margin_px)
	cfg.set_value("ui", "summon_march_sprite_size_px", summon_march_sprite_size_px)
	cfg.set_value("ui", "summon_march_show_username", summon_march_show_username)
	cfg.set_value("ui", "summon_march_username_centered", summon_march_username_centered)
	cfg.set_value("ui", "summon_march_show_monster_name", summon_march_show_monster_name)
	cfg.set_value("ui", "summon_march_username_font_size_px", summon_march_username_font_size_px)
	cfg.set_value("ui", "summon_march_monster_font_size_px", summon_march_monster_font_size_px)
	cfg.set_value("ui", "summon_march_username_color", summon_march_username_color)
	cfg.set_value("ui", "summon_march_monster_color", summon_march_monster_color)
	cfg.set_value("ui", "summon_march_username_offset_x", summon_march_username_offset_x)
	cfg.set_value("ui", "summon_march_username_offset_y", summon_march_username_offset_y)
	cfg.set_value("ui", "summon_march_monster_offset_x", summon_march_monster_offset_x)
	cfg.set_value("ui", "summon_march_monster_offset_y", summon_march_monster_offset_y)
	cfg.set_value("ui", "summon_crowned_sprite_scale", summon_crowned_sprite_scale)
	cfg.set_value("ui", "summon_crowned_mob_modulate", summon_crowned_mob_modulate)
	cfg.set_value("ui", "summon_crowned_show_glow", summon_crowned_show_glow)
	cfg.set_value("ui", "summon_crowned_glow_color", summon_crowned_glow_color)
	cfg.set_value("ui", "summon_crowned_glow_rays", summon_crowned_glow_rays)
	cfg.set_value("ui", "summon_crowned_glow_spin_deg", summon_crowned_glow_spin_deg)
	cfg.set_value("ui", "summon_crowned_glow_radius_scale", summon_crowned_glow_radius_scale)
	cfg.set_value("ui", "summon_crowned_show_crown", summon_crowned_show_crown)
	cfg.set_value("ui", "summon_crowned_crown_scale", summon_crowned_crown_scale)
	cfg.set_value("ui", "summon_crowned_crown_offset_y", summon_crowned_crown_offset_y)
	cfg.set_value("ui", "summon_crowned_crown_modulate", summon_crowned_crown_modulate)
	cfg.set_value("ui", "summon_crowned_username_color", summon_crowned_username_color)
	cfg.set_value("ui", "summon_crowned_username_font_size_px", summon_crowned_username_font_size_px)
	cfg.set_value("ui", "summon_crowned_show_star_prefix", summon_crowned_show_star_prefix)
	cfg.set_value("network", "bestiary_hud_enabled", bestiary_hud_enabled)
	cfg.set_value("network", "bestiary_base_url", bestiary_base_url)
	cfg.set_value("network", "bestiary_poll_sec", bestiary_poll_sec)
	cfg.set_value("network", "remote_settings_enabled", remote_settings_enabled)
	cfg.set_value("network", "remote_settings_base_url", remote_settings_base_url)
	cfg.set_value("network", "remote_settings_poll_sec", remote_settings_poll_sec)
	cfg.set_value("network", "remote_settings_push_on_save", remote_settings_push_on_save)
	cfg.set_value("network", "remote_settings_applied_revision", remote_settings_applied_revision)
	cfg.set_value("network", "bestiary_show_sprint_chip", bestiary_show_sprint_chip)
	cfg.set_value("network", "bestiary_show_heat_chip", bestiary_show_heat_chip)
	cfg.set_value("network", "bestiary_show_hall", bestiary_show_hall)
	cfg.set_value("ui", "bestiary_zone_x_px", bestiary_zone_x_px)
	cfg.set_value("ui", "bestiary_zone_y_px", bestiary_zone_y_px)
	cfg.set_value("ui", "bestiary_zone_width_px", bestiary_zone_width_px)
	cfg.set_value("ui", "bestiary_zone_height_px", bestiary_zone_height_px)
	cfg.set_value("ui", "bestiary_zone_bottom_margin_px", bestiary_zone_bottom_margin_px)
	cfg.set_value("ui", "bestiary_panel_pad_px", bestiary_panel_pad_px)
	cfg.set_value("ui", "bestiary_exp_bar_width_px", bestiary_exp_bar_width_px)
	cfg.set_value("ui", "bestiary_exp_bar_height_scale", bestiary_exp_bar_height_scale)
	cfg.set_value("ui", "bestiary_hud_scale", bestiary_hud_scale)
	cfg.set_value("ui", "bestiary_chrome_scale", bestiary_chrome_scale)
	cfg.set_value("ui", "bestiary_use_compact_exp_bar", bestiary_use_compact_exp_bar)
	cfg.set_value("ui", "bestiary_zone_font_size_px", bestiary_zone_font_size_px)
	cfg.set_value("ui", "bestiary_header_format", bestiary_header_format)
	cfg.set_value("ui", "bestiary_chip_font_size_px", bestiary_chip_font_size_px)
	cfg.set_value("ui", "bestiary_chip_name_max_chars", bestiary_chip_name_max_chars)
	cfg.set_value("ui", "bestiary_hall_name_max_chars", bestiary_hall_name_max_chars)
	cfg.set_value("ui", "bestiary_truncate_names", bestiary_truncate_names)
	cfg.set_value("ui", "bestiary_show_xp_text", bestiary_show_xp_text)
	cfg.set_value("ui", "bestiary_xp_text_over_bar", bestiary_xp_text_over_bar)
	cfg.set_value("ui", "bestiary_level_up_banner_sec", bestiary_level_up_banner_sec)
	cfg.set_value("ui", "bestiary_level_up_banner_scale", bestiary_level_up_banner_scale)
	cfg.set_value("ui", "bestiary_level_up_banner_font_size_px", bestiary_level_up_banner_font_size_px)
	cfg.set_value("ui", "bestiary_zone_font_color", bestiary_zone_font_color)
	cfg.set_value("ui", "bestiary_xp_font_color", bestiary_xp_font_color)
	cfg.set_value("ui", "bestiary_sprint_font_color", bestiary_sprint_font_color)
	cfg.set_value("ui", "bestiary_heat_font_color", bestiary_heat_font_color)
	cfg.set_value("ui", "bestiary_hall_font_color", bestiary_hall_font_color)
	cfg.set_value("ui", "bestiary_banner_font_color", bestiary_banner_font_color)
	cfg.set_value("ui", "bestiary_shatter_pip_claimed_color", bestiary_shatter_pip_claimed_color)
	cfg.set_value("ui", "bestiary_shatter_pip_unclaimed_color", bestiary_shatter_pip_unclaimed_color)
	cfg.set_value("ui", "audio_output_device", audio_output_device)
	cfg.set_value("ui", "audio_volume", audio_volume)
	cfg.set_value("ui", "audio_mute", audio_mute)
	cfg.set_value("ui", "shatter_sfx_enabled", shatter_sfx_enabled)
	cfg.set_value("ui", "chrome_boxes", _serialize_chrome_boxes())
	cfg.set_value("ui", "custom_alerts_enabled", custom_alerts_enabled)
	cfg.set_value("ui", "custom_alerts_interval_sec", custom_alerts_interval_sec)
	cfg.set_value("ui", "custom_alerts_hold_sec", custom_alerts_hold_sec)
	cfg.set_value("ui", "custom_alerts", _serialize_custom_alerts())
	cfg.set_value("ui", "show_pending_udp_alerts", show_pending_udp_alerts)
	cfg.set_value("ui", "hud_status_panel_visible", hud_status_panel_visible)
	cfg.set_value("ui", "settings_window_width_px", settings_window_width_px)
	cfg.set_value("ui", "settings_window_height_px", settings_window_height_px)
	cfg.set_value("ui", "alert_queue_max", alert_queue_max)
	cfg.set_value("ui", "alert_hold_sec", alert_hold_sec)
	cfg.set_value("ui", "alert_fade_in_sec", alert_fade_in_sec)
	cfg.set_value("ui", "alert_fade_out_sec", alert_fade_out_sec)
	cfg.set_value("ui", "alert_hold_sec_when_free", alert_hold_sec_when_free)
	cfg.set_value("ui", "alert_fade_in_sec_when_free", alert_fade_in_sec_when_free)
	cfg.set_value("ui", "alert_fade_out_sec_when_free", alert_fade_out_sec_when_free)
	cfg.set_value("ui", "show_ping_alerts", show_ping_alerts)
	cfg.set_value("ui", "show_failed_command_alerts", show_failed_command_alerts)
	cfg.set_value("ui", "alert_zone_x_px", alert_zone_x_px)
	cfg.set_value("ui", "alert_zone_y_px", alert_zone_y_px)
	cfg.set_value("ui", "alert_zone_width_px", alert_zone_width_px)
	cfg.set_value("ui", "alert_zone_height_px", alert_zone_height_px)
	cfg.set_value("ui", "alert_zone_bottom_margin_px", alert_zone_bottom_margin_px)
	cfg.set_value("ui", "id_zone_x_px", id_zone_x_px)
	cfg.set_value("ui", "id_zone_y_px", id_zone_y_px)
	cfg.set_value("ui", "id_zone_width_px", id_zone_width_px)
	cfg.set_value("ui", "id_zone_height_px", id_zone_height_px)
	cfg.set_value("ui", "id_zone_bottom_margin_px", id_zone_bottom_margin_px)
	cfg.set_value("ui", "live_water_bottom_bar_px", live_water_bottom_bar_px)
	cfg.set_value("ui", "live_water_left_strip_px", live_water_left_strip_px)
	cfg.set_value("ui", "live_water_left_strip_top_px", live_water_left_strip_top_px)
	cfg.set_value("ui", "live_water_gradient_fade_start", live_water_gradient_fade_start)
	cfg.set_value("ui", "live_water_gradient_fade_end", live_water_gradient_fade_end)
	cfg.set_value("ui", "live_water_edge_feather_v_px", live_water_edge_feather_v_px)
	cfg.set_value("ui", "window_per_pixel_transparency_enabled", window_per_pixel_transparency_enabled)
	cfg.set_value("ui", "render_max_fps", render_max_fps)
	cfg.set_value("ui", "alert_text_align", alert_text_align)
	cfg.set_value("ui", "alert_chrome_style", alert_chrome_style)
	cfg.set_value("ui", "alert_chrome_scale", alert_chrome_scale)
	cfg.set_value("ui", "alert_title_font_size_px", alert_title_font_size_px)
	cfg.set_value("ui", "alert_subtitle_font_size_px", alert_subtitle_font_size_px)
	cfg.set_value("ui", "alert_padding_h_px", alert_padding_h_px)
	cfg.set_value("ui", "alert_padding_v_px", alert_padding_v_px)
	cfg.set_value("ui", "alert_command_icon_size_px", alert_command_icon_size_px)
	cfg.set_value("ui", "alert_mob_idle_anim_fps", alert_mob_idle_anim_fps)
	cfg.set_value("ui", "paid_notice_enabled", paid_notice_enabled)
	cfg.set_value("ui", "paid_notice_queue_max", paid_notice_queue_max)
	cfg.set_value("ui", "paid_notice_default_ttl_sec", paid_notice_default_ttl_sec)
	cfg.set_value("ui", "paid_notice_fade_in_sec", paid_notice_fade_in_sec)
	cfg.set_value("ui", "paid_notice_fade_out_sec", paid_notice_fade_out_sec)
	cfg.set_value("ui", "paid_notice_zone_x_px", paid_notice_zone_x_px)
	cfg.set_value("ui", "paid_notice_zone_y_px", paid_notice_zone_y_px)
	cfg.set_value("ui", "paid_notice_zone_width_px", paid_notice_zone_width_px)
	cfg.set_value("ui", "paid_notice_zone_height_px", paid_notice_zone_height_px)
	cfg.set_value("ui", "paid_notice_zone_bottom_margin_px", paid_notice_zone_bottom_margin_px)
	cfg.set_value("ui", "paid_notice_chrome_style", paid_notice_chrome_style)
	cfg.set_value("ui", "paid_notice_chrome_scale", paid_notice_chrome_scale)
	cfg.set_value("ui", "paid_notice_kind_font_size_px", paid_notice_kind_font_size_px)
	cfg.set_value("ui", "paid_notice_title_font_size_px", paid_notice_title_font_size_px)
	cfg.set_value("ui", "paid_notice_body_font_size_px", paid_notice_body_font_size_px)
	cfg.set_value("ui", "paid_notice_kind_font_color", paid_notice_kind_font_color)
	cfg.set_value("ui", "paid_notice_title_font_color", paid_notice_title_font_color)
	cfg.set_value("ui", "paid_notice_body_font_color", paid_notice_body_font_color)
	cfg.set_value("ui", "paid_notice_text_shadow", paid_notice_text_shadow)
	cfg.set_value("ui", "paid_notice_padding_h_px", paid_notice_padding_h_px)
	cfg.set_value("ui", "paid_notice_padding_v_px", paid_notice_padding_v_px)
	cfg.set_value("ui", "paid_notice_line_separation_px", paid_notice_line_separation_px)
	cfg.set_value("ui", "paid_notice_text_align", paid_notice_text_align)
	cfg.set_value("ui", "paid_notice_pop_scale", paid_notice_pop_scale)
	cfg.set_value("ui", "paid_notice_show_on_live", paid_notice_show_on_live)
	cfg.set_value("ui", "paid_notice_show_on_pause", paid_notice_show_on_pause)
	_save_main_scene_show(cfg)
	cfg.set_value("ui", "paid_notice_enable_superchat", paid_notice_enable_superchat)
	cfg.set_value("ui", "paid_notice_enable_gifted_membership", paid_notice_enable_gifted_membership)
	cfg.set_value("ui", "paid_notice_enable_sub", paid_notice_enable_sub)
	cfg.set_value("ui", "paid_notice_enable_highlight", paid_notice_enable_highlight)
	cfg.set_value("ui", "id_overlay_enabled", id_overlay_enabled)
	cfg.set_value("ui", "id_cell_width_px", id_cell_width_px)
	cfg.set_value("ui", "id_cell_height_px", id_cell_height_px)
	cfg.set_value("ui", "id_cell_padding_px", id_cell_padding_px)
	cfg.set_value("ui", "id_known_icon_fraction", id_known_icon_fraction)
	cfg.set_value("ui", "id_flow_h_separation_px", id_flow_h_separation_px)
	cfg.set_value("ui", "id_block_separation_px", id_block_separation_px)
	cfg.set_value("ui", "icon_cell_background_color", icon_cell_background_color)
	cfg.set_value("ui", "free_promos_panel_visible", free_promos_panel_visible)
	cfg.set_value("ui", "free_promos_corner", free_promos_corner)
	cfg.set_value("ui", "free_promos_margin_x", free_promos_margin_x)
	cfg.set_value("ui", "free_promos_margin_y", free_promos_margin_y)
	cfg.set_value("ui", "free_promos_padding_h_px", free_promos_padding_h_px)
	cfg.set_value("ui", "free_promos_padding_v_px", free_promos_padding_v_px)
	cfg.set_value("ui", "free_promos_font_size_px", free_promos_font_size_px)
	cfg.set_value("ui", "free_promos_max_width_px", free_promos_max_width_px)
	cfg.set_value("ui", "free_promos_chrome_style", free_promos_chrome_style)
	cfg.set_value("ui", "free_promos_chrome_scale", free_promos_chrome_scale)
	cfg.set_value("ui", "double_points_panel_visible", double_points_panel_visible)
	cfg.set_value("ui", "double_points_corner", double_points_corner)
	cfg.set_value("ui", "double_points_margin_x", double_points_margin_x)
	cfg.set_value("ui", "double_points_margin_y", double_points_margin_y)
	cfg.set_value("ui", "double_points_padding_h_px", double_points_padding_h_px)
	cfg.set_value("ui", "double_points_padding_v_px", double_points_padding_v_px)
	cfg.set_value("ui", "double_points_font_size_px", double_points_font_size_px)
	cfg.set_value("ui", "double_points_font_color", double_points_font_color)
	cfg.set_value("ui", "double_points_chrome_style", double_points_chrome_style)
	cfg.set_value("ui", "double_points_chrome_scale", double_points_chrome_scale)
	_save_vertical_layout(cfg)
	_apply_render_limits()
	var err := cfg.save(SETTINGS_PATH)
	if err == OK and _notify_listeners:
		settings_saved.emit()


## Write the same cfg file without emitting [signal settings_saved] (no WS/UDP relayout storm).
func save_settings_quiet() -> void:
	var prev: bool = _notify_listeners
	_notify_listeners = false
	save_settings()
	_notify_listeners = prev


## Editor-only: bump revision, write user://, copy that file into [constant SHIPPED_DEFAULTS_PATH] for the next export.
## Returns empty string on success, otherwise an error message.
func save_as_shipped_defaults() -> String:
	if not OS.has_feature("editor"):
		return "Save as export defaults only works in the Godot editor."
	defaults_revision = maxi(defaults_revision, 0) + 1
	save_settings_quiet()
	var abs_shipped := ProjectSettings.globalize_path(SHIPPED_DEFAULTS_PATH)
	var abs_user := ProjectSettings.globalize_path(SETTINGS_PATH)
	if abs_shipped.is_empty() or abs_user.is_empty():
		return "Could not resolve settings paths."
	var dir_path := abs_shipped.get_base_dir()
	var mk := DirAccess.make_dir_recursive_absolute(dir_path)
	if mk != OK and not DirAccess.dir_exists_absolute(dir_path):
		return "Could not create defaults folder: %s" % error_string(mk)
	var err := DirAccess.copy_absolute(abs_user, abs_shipped)
	if err != OK:
		# Fallback: rewrite via ConfigFile (handles some path edge cases).
		var cfg := ConfigFile.new()
		if cfg.load(SETTINGS_PATH) != OK:
			return "Could not read user settings to copy (%s)." % error_string(err)
		var save_err := cfg.save(SHIPPED_DEFAULTS_PATH)
		if save_err != OK:
			return "Could not write shipped defaults: %s" % error_string(save_err)
	if _notify_listeners:
		settings_saved.emit()
	return ""


## Scalars / strings / colors synced via Flask (layout, fonts, toggles). Machine secrets & paths excluded.
## Plain typed Array (not PackedStringArray(...)) so this stays a valid const expression in GDScript.
const REMOTE_UI_KEYS: Array[String] = [
	"spend_indicator_visible", "spend_indicator_corner", "spend_indicator_margin_x", "spend_indicator_margin_y",
	"spend_indicator_padding_h_px", "spend_indicator_padding_v_px", "spend_indicator_font_size_px",
	"spend_indicator_chrome_style", "spend_indicator_chrome_scale",
	"show_pending_udp_alerts", "alert_queue_max", "alert_hold_sec", "alert_fade_in_sec", "alert_fade_out_sec",
	"alert_hold_sec_when_free", "alert_fade_in_sec_when_free", "alert_fade_out_sec_when_free",
	"show_ping_alerts", "show_failed_command_alerts", "hud_status_panel_visible",
	"custom_alerts_enabled", "custom_alerts_interval_sec", "custom_alerts_hold_sec",
	"alert_zone_x_px", "alert_zone_y_px", "alert_zone_width_px", "alert_zone_height_px", "alert_zone_bottom_margin_px",
	"id_zone_x_px", "id_zone_y_px", "id_zone_width_px", "id_zone_height_px", "id_zone_bottom_margin_px",
	"live_water_bottom_bar_px", "live_water_left_strip_px", "live_water_left_strip_top_px",
	"live_water_gradient_fade_start", "live_water_gradient_fade_end", "live_water_edge_feather_v_px",
	"window_per_pixel_transparency_enabled", "render_max_fps",
	"alert_text_align", "alert_chrome_style", "alert_chrome_scale",
	"alert_title_font_size_px", "alert_subtitle_font_size_px", "alert_padding_h_px", "alert_padding_v_px",
	"alert_command_icon_size_px", "alert_mob_idle_anim_fps",
	"paid_notice_enabled", "paid_notice_queue_max", "paid_notice_default_ttl_sec",
	"paid_notice_fade_in_sec", "paid_notice_fade_out_sec",
	"paid_notice_zone_x_px", "paid_notice_zone_y_px", "paid_notice_zone_width_px",
	"paid_notice_zone_height_px", "paid_notice_zone_bottom_margin_px",
	"paid_notice_chrome_style", "paid_notice_chrome_scale",
	"paid_notice_kind_font_size_px", "paid_notice_title_font_size_px", "paid_notice_body_font_size_px",
	"paid_notice_kind_font_color", "paid_notice_title_font_color", "paid_notice_body_font_color",
	"paid_notice_text_shadow", "paid_notice_padding_h_px", "paid_notice_padding_v_px",
	"paid_notice_line_separation_px", "paid_notice_text_align", "paid_notice_pop_scale",
	"paid_notice_show_on_live", "paid_notice_show_on_pause",
	"paid_notice_enable_superchat", "paid_notice_enable_gifted_membership",
	"paid_notice_enable_sub", "paid_notice_enable_highlight",
	"id_overlay_enabled", "id_cell_width_px", "id_cell_height_px", "id_cell_padding_px",
	"id_known_icon_fraction", "id_flow_h_separation_px", "id_block_separation_px", "icon_cell_background_color",
	"free_promos_panel_visible", "free_promos_corner", "free_promos_margin_x", "free_promos_margin_y",
	"free_promos_padding_h_px", "free_promos_padding_v_px", "free_promos_font_size_px",
	"free_promos_max_width_px", "free_promos_chrome_style", "free_promos_chrome_scale",
	"double_points_panel_visible", "double_points_corner", "double_points_margin_x", "double_points_margin_y",
	"double_points_padding_h_px", "double_points_padding_v_px", "double_points_font_size_px",
	"double_points_font_color", "double_points_chrome_style", "double_points_chrome_scale",
	"summon_march_duration_sec", "summon_march_max_concurrent",
	"summon_march_lane_y_fraction", "summon_march_lane_y_min_fraction", "summon_march_lane_y_max_fraction",
	"summon_march_lane_spacing_px", "summon_march_edge_margin_px", "summon_march_mob_fps",
	"summon_march_sprite_size_px", "summon_march_show_username", "summon_march_username_centered",
	"summon_march_show_monster_name", "summon_march_username_font_size_px", "summon_march_monster_font_size_px",
	"summon_march_username_color", "summon_march_monster_color",
	"summon_march_username_offset_x", "summon_march_username_offset_y",
	"summon_march_monster_offset_x", "summon_march_monster_offset_y",
	"summon_crowned_sprite_scale", "summon_crowned_mob_modulate", "summon_crowned_show_glow",
	"summon_crowned_glow_color", "summon_crowned_glow_rays", "summon_crowned_glow_spin_deg",
	"summon_crowned_glow_radius_scale", "summon_crowned_show_crown", "summon_crowned_crown_scale",
	"summon_crowned_crown_offset_y", "summon_crowned_crown_modulate", "summon_crowned_username_color",
	"summon_crowned_username_font_size_px", "summon_crowned_show_star_prefix",
	"bestiary_show_sprint_chip", "bestiary_show_heat_chip", "bestiary_show_hall",
	"bestiary_zone_x_px", "bestiary_zone_y_px", "bestiary_zone_width_px", "bestiary_zone_height_px",
	"bestiary_zone_bottom_margin_px", "bestiary_panel_pad_px", "bestiary_exp_bar_width_px",
	"bestiary_exp_bar_height_scale", "bestiary_hud_scale", "bestiary_chrome_scale",
	"bestiary_use_compact_exp_bar", "bestiary_zone_font_size_px", "bestiary_header_format",
	"bestiary_chip_font_size_px", "bestiary_chip_name_max_chars", "bestiary_hall_name_max_chars",
	"bestiary_truncate_names", "bestiary_show_xp_text", "bestiary_xp_text_over_bar",
	"bestiary_level_up_banner_sec", "bestiary_level_up_banner_scale", "bestiary_level_up_banner_font_size_px",
	"bestiary_zone_font_color", "bestiary_xp_font_color", "bestiary_sprint_font_color",
	"bestiary_heat_font_color", "bestiary_hall_font_color", "bestiary_banner_font_color",
	"bestiary_shatter_pip_claimed_color", "bestiary_shatter_pip_unclaimed_color",
	"audio_volume", "audio_mute", "shatter_sfx_enabled",
]


func _remote_encode_value(v: Variant) -> Variant:
	if v is Color:
		return (v as Color).to_html(true)
	return v


func _remote_decode_color(v: Variant, fallback: Color) -> Color:
	if v is Color:
		return v as Color
	if v is String:
		var s := (v as String).strip_edges()
		if s.is_valid_html_color():
			return Color(s)
	return fallback


func _chrome_boxes_to_remote(boxes: Array) -> Array:
	var out: Array = []
	var n := mini(boxes.size(), CHROME_BOXES_MAX)
	for i in range(n):
		var box := normalize_chrome_box(boxes[i], i + 1).duplicate(true)
		box["header_font_color"] = _remote_encode_value(box.get("header_font_color", Color.WHITE))
		box["body_font_color"] = _remote_encode_value(box.get("body_font_color", Color.WHITE))
		out.append(box)
	return out


## JSON-safe settings blob for Flask /points-config (no passwords, hosts, or absolute paths).
func to_remote_dict() -> Dictionary:
	var ui: Dictionary = {}
	for key in REMOTE_UI_KEYS:
		ui[key] = _remote_encode_value(get(key))
	ensure_main_scene_show()
	ui["main_scene_show"] = main_scene_show.duplicate(true)
	ui["chrome_boxes"] = _chrome_boxes_to_remote(chrome_boxes)
	ui["custom_alerts"] = _serialize_custom_alerts()
	var vert := vertical_layout.to_remote_dict() if vertical_layout != null else {}
	if typeof(vert.get("chrome_boxes", null)) == TYPE_ARRAY:
		vert["chrome_boxes"] = _chrome_boxes_to_remote(vert["chrome_boxes"])
	vert["vertical_window_enabled"] = vertical_window_enabled
	return {
		"network": {
			"obs_scene_sync_enabled": obs_scene_sync_enabled,
			"obs_pause_scene_name": obs_pause_scene_name,
			"obs_main_scene_name": obs_main_scene_name,
			"obs_log_program_scene": obs_log_program_scene,
			"summon_march_enabled": summon_march_enabled,
			"summon_march_skip_backlog": summon_march_skip_backlog,
			"bestiary_hud_enabled": bestiary_hud_enabled,
		},
		"ui": ui,
		"ui_vertical": vert,
	}


## Apply a remote settings dict (partial merge). Returns number of top-level keys touched.
func apply_remote_dict(payload: Dictionary) -> int:
	if payload.is_empty():
		return 0
	var touched := 0
	var net: Variant = payload.get("network", {})
	if typeof(net) == TYPE_DICTIONARY:
		var nd: Dictionary = net
		if nd.has("obs_scene_sync_enabled"):
			obs_scene_sync_enabled = bool(nd["obs_scene_sync_enabled"])
			touched += 1
		if nd.has("obs_pause_scene_name"):
			obs_pause_scene_name = str(nd["obs_pause_scene_name"])
			touched += 1
		if nd.has("obs_main_scene_name"):
			obs_main_scene_name = str(nd["obs_main_scene_name"])
			touched += 1
		if nd.has("obs_log_program_scene"):
			obs_log_program_scene = bool(nd["obs_log_program_scene"])
			touched += 1
		if nd.has("summon_march_enabled"):
			summon_march_enabled = bool(nd["summon_march_enabled"])
			touched += 1
		if nd.has("summon_march_skip_backlog"):
			summon_march_skip_backlog = bool(nd["summon_march_skip_backlog"])
			touched += 1
		if nd.has("bestiary_hud_enabled"):
			bestiary_hud_enabled = bool(nd["bestiary_hud_enabled"])
			touched += 1
	var ui_raw: Variant = payload.get("ui", {})
	if typeof(ui_raw) == TYPE_DICTIONARY:
		var ui: Dictionary = ui_raw
		for key in REMOTE_UI_KEYS:
			if not ui.has(key):
				continue
			var cur: Variant = get(key)
			var incoming: Variant = ui[key]
			if cur is Color:
				set(key, _remote_decode_color(incoming, cur as Color))
			elif typeof(cur) == TYPE_BOOL:
				set(key, bool(incoming))
			elif typeof(cur) == TYPE_INT:
				set(key, int(incoming))
			elif typeof(cur) == TYPE_FLOAT:
				set(key, float(incoming))
			elif typeof(cur) == TYPE_STRING:
				set(key, str(incoming))
			else:
				set(key, incoming)
			touched += 1
		if ui.has("main_scene_show") and typeof(ui["main_scene_show"]) == TYPE_DICTIONARY:
			main_scene_show = (ui["main_scene_show"] as Dictionary).duplicate(true)
			ensure_main_scene_show()
			touched += 1
		if ui.has("chrome_boxes") and typeof(ui["chrome_boxes"]) == TYPE_ARRAY:
			chrome_boxes = _serialize_chrome_boxes_list(ui["chrome_boxes"])
			touched += 1
		if ui.has("custom_alerts") and typeof(ui["custom_alerts"]) == TYPE_ARRAY:
			custom_alerts = _serialize_custom_alerts_list(ui["custom_alerts"])
			touched += 1
	var vert_raw: Variant = payload.get("ui_vertical", {})
	if typeof(vert_raw) == TYPE_DICTIONARY:
		var vd: Dictionary = vert_raw
		if vd.has("vertical_window_enabled"):
			vertical_window_enabled = bool(vd["vertical_window_enabled"])
			touched += 1
		if vertical_layout == null:
			vertical_layout = UiLayoutData.default_vertical()
		vertical_layout.apply_remote_dict(vd)
		if vd.has("chrome_boxes") and typeof(vd["chrome_boxes"]) == TYPE_ARRAY:
			vertical_layout.chrome_boxes = _serialize_chrome_boxes_list(vd["chrome_boxes"])
		touched += 1
	return touched
