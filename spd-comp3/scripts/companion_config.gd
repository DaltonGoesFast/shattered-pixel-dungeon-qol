extends Node

const SETTINGS_PATH := "user://companion_settings.cfg"

## Fired after settings are written to disk (e.g. from the settings UI or manual save).
signal settings_saved
## Fired after [method load_settings] successfully read the cfg file (incl. F5 reload).
signal settings_loaded

var _notify_listeners: bool = false

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
## Pixel font size for the spend indicator label ([code]Chat Spending[/code]).
var spend_indicator_font_size_px: int = 15
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
## When true, root viewport + window use per-pixel transparency so OBS (layer below) shows through masked regions. Default off: some Windows exports show a blank/white window with transparency on.
var window_per_pixel_transparency_enabled: bool = false
## Caps redraw rate ([member Engine.max_fps]). **0** = unlimited (higher GPU). **30–60** is usually enough for overlays and lowers compositor/GPU load.
var render_max_fps: int = 60
## Title/subtitle alignment: left or center.
var alert_text_align: String = "left"
## Pixel font size for alert title and subtitle (toast text).
var alert_title_font_size_px: int = 14
var alert_subtitle_font_size_px: int = 11
## Width and height (square) for scroll rune + item / mob icons in the alert row (px).
var alert_command_icon_size_px: int = 72
## Frames per second for monster idle animation in alerts (static scroll art ignores this).
var alert_mob_idle_anim_fps: float = 3.0
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
var free_promos_font_size_px: int = 13
## Hard cap on panel width (content is narrower when possible).
var free_promos_max_width_px: int = 320
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
var bestiary_zone_height_px: int = 160
var bestiary_zone_bottom_margin_px: int = 0
var bestiary_exp_bar_width_px: int = 256
var bestiary_exp_bar_height_scale: float = 1.0
## Scales the whole Bestiary panel (chrome, bar, fonts, icons). Banner is separate.
var bestiary_hud_scale: float = 1.0
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

func _ready() -> void:
	load_settings()
	save_settings()
	_notify_listeners = true


func load_settings() -> void:
	var cfg := ConfigFile.new()
	if cfg.load(SETTINGS_PATH) != OK:
		return
	game_ws_host = str(cfg.get_value("network", "game_ws_host", game_ws_host))
	game_ws_port = int(cfg.get_value("network", "game_ws_port", game_ws_port))
	streamerbot_udp_port = int(cfg.get_value("network", "streamerbot_udp_port", streamerbot_udp_port))
	obs_scene_sync_enabled = bool(cfg.get_value("network", "obs_scene_sync_enabled", obs_scene_sync_enabled))
	obs_ws_host = str(cfg.get_value("network", "obs_ws_host", obs_ws_host))
	obs_ws_port = int(cfg.get_value("network", "obs_ws_port", obs_ws_port))
	obs_ws_password = str(cfg.get_value("network", "obs_ws_password", obs_ws_password))
	obs_pause_scene_name = str(cfg.get_value("network", "obs_pause_scene_name", obs_pause_scene_name))
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
	ws_reconnect_sec = float(cfg.get_value("network", "ws_reconnect_sec", ws_reconnect_sec))
	http_fallback_enabled = bool(cfg.get_value("network", "http_fallback_enabled", http_fallback_enabled))
	http_fallback_url = str(cfg.get_value("network", "http_fallback_url", http_fallback_url))
	http_poll_interval_sec = float(
		cfg.get_value("network", "http_poll_interval_sec", http_poll_interval_sec)
	)
	free_promos_http_url = str(cfg.get_value("network", "free_promos_http_url", free_promos_http_url))
	free_promos_poll_sec = maxf(0.5, float(cfg.get_value("network", "free_promos_poll_sec", free_promos_poll_sec)))
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
	bestiary_hud_enabled = bool(cfg.get_value("network", "bestiary_hud_enabled", bestiary_hud_enabled))
	bestiary_base_url = str(cfg.get_value("network", "bestiary_base_url", bestiary_base_url))
	bestiary_poll_sec = maxf(0.25, float(cfg.get_value("network", "bestiary_poll_sec", bestiary_poll_sec)))
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
	show_pending_udp_alerts = bool(
		cfg.get_value("ui", "show_pending_udp_alerts", show_pending_udp_alerts)
	)
	hud_status_panel_visible = bool(
		cfg.get_value("ui", "hud_status_panel_visible", hud_status_panel_visible)
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
	window_per_pixel_transparency_enabled = bool(
		cfg.get_value("ui", "window_per_pixel_transparency_enabled", window_per_pixel_transparency_enabled)
	)
	render_max_fps = clampi(int(cfg.get_value("ui", "render_max_fps", render_max_fps)), 0, 480)
	alert_text_align = str(cfg.get_value("ui", "alert_text_align", alert_text_align))
	alert_title_font_size_px = int(
		cfg.get_value("ui", "alert_title_font_size_px", alert_title_font_size_px)
	)
	alert_subtitle_font_size_px = int(
		cfg.get_value("ui", "alert_subtitle_font_size_px", alert_subtitle_font_size_px)
	)
	alert_command_icon_size_px = int(
		cfg.get_value("ui", "alert_command_icon_size_px", alert_command_icon_size_px)
	)
	alert_mob_idle_anim_fps = float(
		cfg.get_value("ui", "alert_mob_idle_anim_fps", alert_mob_idle_anim_fps)
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
	_apply_render_limits()
	settings_loaded.emit()


## Pixel size of the root 2D view (game / OBS capture area). Uses the viewport visible rect.
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
	var sz := layout_canvas_size(for_control)
	var denom_y := maxf(sz.y, 1.0)
	var denom_x := maxf(sz.x, 1.0)
	var bf := clampf(float(live_water_bottom_bar_px) / denom_y, 0.0, 1.0)
	var lf := clampf(float(live_water_left_strip_px) / denom_x, 0.0, 1.0)
	var lt := clampf(float(live_water_left_strip_top_px) / denom_y, 0.0, 1.0)
	return Vector3(bf, lf, lt)


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
	apply_pixel_zone_layout(
		ctrl,
		canvas,
		alert_zone_x_px,
		alert_zone_y_px,
		alert_zone_width_px,
		alert_zone_height_px,
		alert_zone_bottom_margin_px
	)


func apply_id_zone_layout(ctrl: Control, canvas: Vector2) -> void:
	apply_pixel_zone_layout(
		ctrl,
		canvas,
		id_zone_x_px,
		id_zone_y_px,
		id_zone_width_px,
		id_zone_height_px,
		id_zone_bottom_margin_px
	)


func apply_bestiary_zone_layout(ctrl: Control, canvas: Vector2) -> void:
	apply_pixel_zone_layout(
		ctrl,
		canvas,
		bestiary_zone_x_px,
		bestiary_zone_y_px,
		bestiary_zone_width_px,
		bestiary_zone_height_px,
		bestiary_zone_bottom_margin_px
	)


func save_settings() -> void:
	var cfg := ConfigFile.new()
	cfg.set_value("network", "game_ws_host", game_ws_host)
	cfg.set_value("network", "game_ws_port", game_ws_port)
	cfg.set_value("network", "streamerbot_udp_port", streamerbot_udp_port)
	cfg.set_value("network", "obs_scene_sync_enabled", obs_scene_sync_enabled)
	cfg.set_value("network", "obs_ws_host", obs_ws_host)
	cfg.set_value("network", "obs_ws_port", obs_ws_port)
	cfg.set_value("network", "obs_ws_password", obs_ws_password)
	cfg.set_value("network", "obs_pause_scene_name", obs_pause_scene_name)
	cfg.set_value("network", "obs_log_program_scene", obs_log_program_scene)
	cfg.set_value("network", "obs_reconnect_sec", obs_reconnect_sec)
	cfg.set_value("network", "spawn_result_file_path", spawn_result_file_path)
	cfg.set_value("network", "spawn_result_file_poll_sec", spawn_result_file_poll_sec)
	cfg.set_value("network", "spawn_result_file_icons", spawn_result_file_icons)
	cfg.set_value("network", "spend_lock_file_path", spend_lock_file_path)
	cfg.set_value("network", "spend_lock_poll_sec", spend_lock_poll_sec)
	cfg.set_value("network", "spend_indicator_visible", spend_indicator_visible)
	cfg.set_value("network", "spend_indicator_corner", spend_indicator_corner)
	cfg.set_value("network", "spend_indicator_margin_x", spend_indicator_margin_x)
	cfg.set_value("network", "spend_indicator_margin_y", spend_indicator_margin_y)
	cfg.set_value("network", "spend_indicator_font_size_px", spend_indicator_font_size_px)
	cfg.set_value("network", "ws_reconnect_sec", ws_reconnect_sec)
	cfg.set_value("network", "http_fallback_enabled", http_fallback_enabled)
	cfg.set_value("network", "http_fallback_url", http_fallback_url)
	cfg.set_value("network", "http_poll_interval_sec", http_poll_interval_sec)
	cfg.set_value("network", "free_promos_http_url", free_promos_http_url)
	cfg.set_value("network", "free_promos_poll_sec", free_promos_poll_sec)
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
	cfg.set_value("network", "bestiary_hud_enabled", bestiary_hud_enabled)
	cfg.set_value("network", "bestiary_base_url", bestiary_base_url)
	cfg.set_value("network", "bestiary_poll_sec", bestiary_poll_sec)
	cfg.set_value("network", "bestiary_show_sprint_chip", bestiary_show_sprint_chip)
	cfg.set_value("network", "bestiary_show_heat_chip", bestiary_show_heat_chip)
	cfg.set_value("network", "bestiary_show_hall", bestiary_show_hall)
	cfg.set_value("ui", "bestiary_zone_x_px", bestiary_zone_x_px)
	cfg.set_value("ui", "bestiary_zone_y_px", bestiary_zone_y_px)
	cfg.set_value("ui", "bestiary_zone_width_px", bestiary_zone_width_px)
	cfg.set_value("ui", "bestiary_zone_height_px", bestiary_zone_height_px)
	cfg.set_value("ui", "bestiary_zone_bottom_margin_px", bestiary_zone_bottom_margin_px)
	cfg.set_value("ui", "bestiary_exp_bar_width_px", bestiary_exp_bar_width_px)
	cfg.set_value("ui", "bestiary_exp_bar_height_scale", bestiary_exp_bar_height_scale)
	cfg.set_value("ui", "bestiary_hud_scale", bestiary_hud_scale)
	cfg.set_value("ui", "bestiary_use_compact_exp_bar", bestiary_use_compact_exp_bar)
	cfg.set_value("ui", "bestiary_zone_font_size_px", bestiary_zone_font_size_px)
	cfg.set_value("ui", "bestiary_header_format", bestiary_header_format)
	cfg.set_value("ui", "bestiary_chip_font_size_px", bestiary_chip_font_size_px)
	cfg.set_value("ui", "bestiary_chip_name_max_chars", bestiary_chip_name_max_chars)
	cfg.set_value("ui", "bestiary_hall_name_max_chars", bestiary_hall_name_max_chars)
	cfg.set_value("ui", "bestiary_truncate_names", bestiary_truncate_names)
	cfg.set_value("ui", "bestiary_show_xp_text", bestiary_show_xp_text)
	cfg.set_value("ui", "bestiary_level_up_banner_sec", bestiary_level_up_banner_sec)
	cfg.set_value("ui", "bestiary_level_up_banner_scale", bestiary_level_up_banner_scale)
	cfg.set_value("ui", "bestiary_level_up_banner_font_size_px", bestiary_level_up_banner_font_size_px)
	cfg.set_value("ui", "bestiary_zone_font_color", bestiary_zone_font_color)
	cfg.set_value("ui", "bestiary_xp_font_color", bestiary_xp_font_color)
	cfg.set_value("ui", "bestiary_sprint_font_color", bestiary_sprint_font_color)
	cfg.set_value("ui", "bestiary_heat_font_color", bestiary_heat_font_color)
	cfg.set_value("ui", "bestiary_hall_font_color", bestiary_hall_font_color)
	cfg.set_value("ui", "bestiary_banner_font_color", bestiary_banner_font_color)
	cfg.set_value("ui", "show_pending_udp_alerts", show_pending_udp_alerts)
	cfg.set_value("ui", "hud_status_panel_visible", hud_status_panel_visible)
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
	cfg.set_value("ui", "window_per_pixel_transparency_enabled", window_per_pixel_transparency_enabled)
	cfg.set_value("ui", "render_max_fps", render_max_fps)
	cfg.set_value("ui", "alert_text_align", alert_text_align)
	cfg.set_value("ui", "alert_title_font_size_px", alert_title_font_size_px)
	cfg.set_value("ui", "alert_subtitle_font_size_px", alert_subtitle_font_size_px)
	cfg.set_value("ui", "alert_command_icon_size_px", alert_command_icon_size_px)
	cfg.set_value("ui", "alert_mob_idle_anim_fps", alert_mob_idle_anim_fps)
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
	cfg.set_value("ui", "free_promos_font_size_px", free_promos_font_size_px)
	cfg.set_value("ui", "free_promos_max_width_px", free_promos_max_width_px)
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
