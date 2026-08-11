/**
 * SPD Companion remote settings panel (F2-like tabs) for points-config.html.
 * Owns #companionPanelRoot. Uses GET/POST /api/companion-settings.
 */
(function (global) {
  'use strict';

  const SCENE_KEYS = [
    'title', 'live_water', 'chrome_boxes', 'id_overlay', 'alerts', 'paid_notices',
    'bestiary', 'summon_march', 'spend_indicator', 'free_promos', 'double_points',
  ];
  const CHROME_STYLES = [
    ['window', 'Window (grey)'], ['window_silver', 'Window (silver)'], ['toast', 'Toast'],
    ['toast_tr', 'Toast (transparent)'], ['toast_tr_heavy', 'Toast (transparent heavy)'],
    ['toast_white', 'Toast (white)'], ['red_button', 'Red button'], ['grey_button', 'Grey button'],
    ['grey_button_tr', 'Grey button (transparent)'], ['tag', 'Tag'], ['gem', 'Gem'],
    ['scroll', 'Scroll'], ['tab_set', 'Tab set'], ['tab_selected', 'Tab selected'],
    ['tab_unselected', 'Tab unselected'], ['blank', 'Blank'],
  ];
  const CORNERS = [['0', 'Top-left'], ['1', 'Top-right'], ['2', 'Bottom-left'], ['3', 'Bottom-right']];
  const ALIGNS = [['left', 'Left'], ['center', 'Center']];

  const TABS = [
    { id: 'status', label: 'Status' },
    { id: 'scene_gates', label: 'Scene gates' },
    { id: 'alerts', label: 'Alerts' },
    { id: 'paid', label: 'Paid notices' },
    { id: 'bestiary_hud', label: 'Bestiary HUD' },
    { id: 'id', label: 'ID overlay' },
    { id: 'corners', label: 'Corners' },
    { id: 'live_water', label: 'Live water' },
    { id: 'safe_network', label: 'Safe network' },
    { id: 'chrome', label: 'Chrome boxes' },
    { id: 'vertical', label: 'Vertical' },
    { id: 'summon', label: 'Summon march' },
    { id: 'advanced', label: 'Advanced' },
  ];

  /** @type {{ path: string, type: string, label: string, min?: number, max?: number, step?: number, options?: string[][] }[]} */
  function uiFields(prefix, defs) {
    return defs.map((d) => Object.assign({}, d, { path: prefix + d.key }));
  }

  const FIELD_GROUPS = {
    alerts: uiFields('ui.', [
      { key: 'alert_zone_x_px', type: 'int', label: 'Zone X' },
      { key: 'alert_zone_y_px', type: 'int', label: 'Zone Y' },
      { key: 'alert_zone_width_px', type: 'int', label: 'Zone width' },
      { key: 'alert_zone_height_px', type: 'int', label: 'Zone height (0=fill)' },
      { key: 'alert_zone_bottom_margin_px', type: 'int', label: 'Bottom margin' },
      { key: 'alert_title_font_size_px', type: 'int', label: 'Title font', min: 8, max: 96 },
      { key: 'alert_subtitle_font_size_px', type: 'int', label: 'Subtitle font', min: 8, max: 96 },
      { key: 'alert_chrome_style', type: 'chrome', label: 'Chrome style' },
      { key: 'alert_chrome_scale', type: 'float', label: 'Chrome scale', min: 0.5, max: 4, step: 0.05 },
      { key: 'alert_padding_h_px', type: 'int', label: 'Pad H', min: 0, max: 64 },
      { key: 'alert_padding_v_px', type: 'int', label: 'Pad V', min: 0, max: 64 },
      { key: 'alert_command_icon_size_px', type: 'int', label: 'Icon size', min: 8, max: 256 },
      { key: 'alert_mob_idle_anim_fps', type: 'float', label: 'Mob anim FPS', min: 0.5, max: 30, step: 0.5 },
      { key: 'alert_text_align', type: 'select', label: 'Text align', options: ALIGNS },
      { key: 'alert_queue_max', type: 'int', label: 'Queue max', min: 1, max: 32 },
      { key: 'alert_hold_sec', type: 'float', label: 'Hold (sec)', min: 0.1, max: 30, step: 0.05 },
      { key: 'alert_fade_in_sec', type: 'float', label: 'Fade in', min: 0.05, max: 5, step: 0.05 },
      { key: 'alert_fade_out_sec', type: 'float', label: 'Fade out', min: 0.05, max: 5, step: 0.05 },
      { key: 'alert_hold_sec_when_free', type: 'float', label: 'Hold when free', min: 0.05, max: 10, step: 0.05 },
      { key: 'alert_fade_in_sec_when_free', type: 'float', label: 'Fade in when free', min: 0.05, max: 5, step: 0.05 },
      { key: 'alert_fade_out_sec_when_free', type: 'float', label: 'Fade out when free', min: 0.05, max: 5, step: 0.05 },
      { key: 'show_pending_udp_alerts', type: 'bool', label: 'Show pending UDP' },
      { key: 'show_failed_command_alerts', type: 'bool', label: 'Show failed commands' },
      { key: 'show_ping_alerts', type: 'bool', label: 'Show ping alerts' },
      { key: 'hud_status_panel_visible', type: 'bool', label: 'HUD status panel' },
    ]),
    paid: uiFields('ui.', [
      { key: 'paid_notice_enabled', type: 'bool', label: 'Enabled' },
      { key: 'paid_notice_zone_x_px', type: 'int', label: 'Zone X' },
      { key: 'paid_notice_zone_y_px', type: 'int', label: 'Zone Y' },
      { key: 'paid_notice_zone_width_px', type: 'int', label: 'Zone width' },
      { key: 'paid_notice_zone_height_px', type: 'int', label: 'Zone height' },
      { key: 'paid_notice_zone_bottom_margin_px', type: 'int', label: 'Bottom margin' },
      { key: 'paid_notice_kind_font_size_px', type: 'int', label: 'Kind font', min: 8, max: 96 },
      { key: 'paid_notice_title_font_size_px', type: 'int', label: 'Title font', min: 8, max: 96 },
      { key: 'paid_notice_body_font_size_px', type: 'int', label: 'Body font', min: 8, max: 96 },
      { key: 'paid_notice_kind_font_color', type: 'color', label: 'Kind color' },
      { key: 'paid_notice_title_font_color', type: 'color', label: 'Title color' },
      { key: 'paid_notice_body_font_color', type: 'color', label: 'Body color' },
      { key: 'paid_notice_chrome_style', type: 'chrome', label: 'Chrome style' },
      { key: 'paid_notice_chrome_scale', type: 'float', label: 'Chrome scale', min: 0.5, max: 4, step: 0.05 },
      { key: 'paid_notice_padding_h_px', type: 'int', label: 'Pad H' },
      { key: 'paid_notice_padding_v_px', type: 'int', label: 'Pad V' },
      { key: 'paid_notice_line_separation_px', type: 'int', label: 'Line sep' },
      { key: 'paid_notice_text_align', type: 'select', label: 'Align', options: ALIGNS },
      { key: 'paid_notice_text_shadow', type: 'bool', label: 'Text shadow' },
      { key: 'paid_notice_pop_scale', type: 'bool', label: 'Pop scale' },
      { key: 'paid_notice_queue_max', type: 'int', label: 'Queue max', min: 1, max: 32 },
      { key: 'paid_notice_default_ttl_sec', type: 'float', label: 'Hold (sec)', min: 0.5, max: 60, step: 0.25 },
      { key: 'paid_notice_fade_in_sec', type: 'float', label: 'Fade in', min: 0.05, max: 5, step: 0.05 },
      { key: 'paid_notice_fade_out_sec', type: 'float', label: 'Fade out', min: 0.05, max: 5, step: 0.05 },
      { key: 'paid_notice_enable_superchat', type: 'bool', label: 'Superchat' },
      { key: 'paid_notice_enable_gifted_membership', type: 'bool', label: 'Gifted membership' },
      { key: 'paid_notice_enable_sub', type: 'bool', label: 'Sub' },
      { key: 'paid_notice_enable_highlight', type: 'bool', label: 'Highlight' },
    ]),
    bestiary_hud: uiFields('ui.', [
      { key: 'bestiary_zone_x_px', type: 'int', label: 'Zone X' },
      { key: 'bestiary_zone_y_px', type: 'int', label: 'Zone Y' },
      { key: 'bestiary_zone_width_px', type: 'int', label: 'Zone width' },
      { key: 'bestiary_zone_height_px', type: 'int', label: 'Zone height (0=fit)' },
      { key: 'bestiary_zone_bottom_margin_px', type: 'int', label: 'Bottom margin' },
      { key: 'bestiary_panel_pad_px', type: 'int', label: 'Panel pad', min: 0, max: 64 },
      { key: 'bestiary_hud_scale', type: 'float', label: 'HUD scale', min: 0.5, max: 4, step: 0.05 },
      { key: 'bestiary_chrome_scale', type: 'float', label: 'Chrome scale', min: 0.5, max: 4, step: 0.05 },
      { key: 'bestiary_exp_bar_width_px', type: 'int', label: 'Exp bar width', min: 64, max: 1600 },
      { key: 'bestiary_exp_bar_height_scale', type: 'float', label: 'Exp bar height scale', min: 0.5, max: 4, step: 0.05 },
      { key: 'bestiary_use_compact_exp_bar', type: 'bool', label: 'Compact exp bar' },
      { key: 'bestiary_header_format', type: 'text', label: 'Header format' },
      { key: 'bestiary_zone_font_size_px', type: 'int', label: 'Zone font', min: 8, max: 96 },
      { key: 'bestiary_chip_font_size_px', type: 'int', label: 'Chip font', min: 8, max: 96 },
      { key: 'bestiary_chip_name_max_chars', type: 'int', label: 'Chip name max', min: 0, max: 32 },
      { key: 'bestiary_hall_name_max_chars', type: 'int', label: 'Hall name max', min: 0, max: 32 },
      { key: 'bestiary_truncate_names', type: 'bool', label: 'Truncate names' },
      { key: 'bestiary_show_xp_text', type: 'bool', label: 'Show XP text' },
      { key: 'bestiary_xp_text_over_bar', type: 'bool', label: 'XP text over bar' },
      { key: 'bestiary_show_sprint_chip', type: 'bool', label: 'Sprint chip' },
      { key: 'bestiary_show_heat_chip', type: 'bool', label: 'Heat chip' },
      { key: 'bestiary_show_hall', type: 'bool', label: 'Hall of fame' },
      { key: 'bestiary_zone_font_color', type: 'color', label: 'Zone color' },
      { key: 'bestiary_xp_font_color', type: 'color', label: 'XP color' },
      { key: 'bestiary_sprint_font_color', type: 'color', label: 'Sprint color' },
      { key: 'bestiary_heat_font_color', type: 'color', label: 'Heat color' },
      { key: 'bestiary_hall_font_color', type: 'color', label: 'Hall color' },
      { key: 'bestiary_banner_font_color', type: 'color', label: 'Banner color' },
      { key: 'bestiary_level_up_banner_sec', type: 'float', label: 'Banner duration', min: 0.5, max: 30, step: 0.25 },
      { key: 'bestiary_level_up_banner_scale', type: 'float', label: 'Banner scale', min: 0.25, max: 8, step: 0.05 },
      { key: 'bestiary_level_up_banner_font_size_px', type: 'int', label: 'Banner font', min: 8, max: 96 },
    ]),
    id: uiFields('ui.', [
      { key: 'id_overlay_enabled', type: 'bool', label: 'Enabled' },
      { key: 'id_zone_x_px', type: 'int', label: 'Zone X' },
      { key: 'id_zone_y_px', type: 'int', label: 'Zone Y' },
      { key: 'id_zone_width_px', type: 'int', label: 'Zone width' },
      { key: 'id_zone_height_px', type: 'int', label: 'Zone height' },
      { key: 'id_zone_bottom_margin_px', type: 'int', label: 'Bottom margin' },
      { key: 'id_cell_width_px', type: 'int', label: 'Cell width' },
      { key: 'id_cell_height_px', type: 'int', label: 'Cell height' },
      { key: 'id_cell_padding_px', type: 'int', label: 'Cell padding' },
      { key: 'id_known_icon_fraction', type: 'float', label: 'Known icon fraction', min: 0.1, max: 1, step: 0.01 },
      { key: 'id_flow_h_separation_px', type: 'int', label: 'Flow H sep' },
      { key: 'id_block_separation_px', type: 'int', label: 'Block sep' },
      { key: 'icon_cell_background_color', type: 'color', label: 'Cell background' },
    ]),
    corners: uiFields('ui.', [
      { key: 'spend_indicator_visible', type: 'bool', label: 'Spend visible' },
      { key: 'spend_indicator_corner', type: 'select', label: 'Spend corner', options: CORNERS },
      { key: 'spend_indicator_margin_x', type: 'int', label: 'Spend margin X' },
      { key: 'spend_indicator_margin_y', type: 'int', label: 'Spend margin Y' },
      { key: 'spend_indicator_padding_h_px', type: 'int', label: 'Spend pad H' },
      { key: 'spend_indicator_padding_v_px', type: 'int', label: 'Spend pad V' },
      { key: 'spend_indicator_font_size_px', type: 'int', label: 'Spend font', min: 8, max: 48 },
      { key: 'spend_indicator_chrome_style', type: 'chrome', label: 'Spend chrome' },
      { key: 'spend_indicator_chrome_scale', type: 'float', label: 'Spend chrome scale', min: 0.5, max: 4, step: 0.05 },
      { key: 'free_promos_panel_visible', type: 'bool', label: 'Free promos visible' },
      { key: 'free_promos_corner', type: 'select', label: 'Free corner', options: CORNERS },
      { key: 'free_promos_margin_x', type: 'int', label: 'Free margin X' },
      { key: 'free_promos_margin_y', type: 'int', label: 'Free margin Y' },
      { key: 'free_promos_padding_h_px', type: 'int', label: 'Free pad H' },
      { key: 'free_promos_padding_v_px', type: 'int', label: 'Free pad V' },
      { key: 'free_promos_font_size_px', type: 'int', label: 'Free font', min: 8, max: 36 },
      { key: 'free_promos_max_width_px', type: 'int', label: 'Free max width', min: 160, max: 1600 },
      { key: 'free_promos_chrome_style', type: 'chrome', label: 'Free chrome' },
      { key: 'free_promos_chrome_scale', type: 'float', label: 'Free chrome scale', min: 0.5, max: 4, step: 0.05 },
      { key: 'double_points_panel_visible', type: 'bool', label: '2× visible' },
      { key: 'double_points_corner', type: 'select', label: '2× corner', options: CORNERS },
      { key: 'double_points_margin_x', type: 'int', label: '2× margin X' },
      { key: 'double_points_margin_y', type: 'int', label: '2× margin Y' },
      { key: 'double_points_padding_h_px', type: 'int', label: '2× pad H' },
      { key: 'double_points_padding_v_px', type: 'int', label: '2× pad V' },
      { key: 'double_points_font_size_px', type: 'int', label: '2× font', min: 8, max: 48 },
      { key: 'double_points_font_color', type: 'color', label: '2× color' },
      { key: 'double_points_chrome_style', type: 'chrome', label: '2× chrome' },
      { key: 'double_points_chrome_scale', type: 'float', label: '2× chrome scale', min: 0.5, max: 4, step: 0.05 },
    ]),
    live_water: uiFields('ui.', [
      { key: 'live_water_bottom_bar_px', type: 'int', label: 'Bottom bar height' },
      { key: 'live_water_left_strip_px', type: 'int', label: 'Left strip width' },
      { key: 'live_water_left_strip_top_px', type: 'int', label: 'Left strip top inset' },
      { key: 'live_water_gradient_fade_start', type: 'float', label: 'Fade start Y', min: 0, max: 1, step: 0.01 },
      { key: 'live_water_gradient_fade_end', type: 'float', label: 'Fade end Y', min: 0, max: 1, step: 0.01 },
      { key: 'live_water_edge_feather_v_px', type: 'int', label: 'Edge feather V', min: 0, max: 512 },
      { key: 'window_per_pixel_transparency_enabled', type: 'bool', label: 'Transparent window' },
      { key: 'render_max_fps', type: 'int', label: 'Max FPS (0=uncapped)', min: 0, max: 240 },
    ]),
    safe_network: [
      { path: 'network.obs_scene_sync_enabled', type: 'bool', label: 'OBS scene sync' },
      { path: 'network.obs_pause_scene_name', type: 'text', label: 'PAUSE if name contains' },
      { path: 'network.obs_main_scene_name', type: 'text', label: 'MAIN if name contains' },
      { path: 'network.obs_log_program_scene', type: 'bool', label: 'Log program scene' },
      { path: 'network.summon_march_enabled', type: 'bool', label: 'Summon march enabled' },
      { path: 'network.summon_march_skip_backlog', type: 'bool', label: 'Skip march backlog' },
      { path: 'network.bestiary_hud_enabled', type: 'bool', label: 'Bestiary HUD enabled' },
    ],
    vertical_zones: uiFields('ui_vertical.', [
      { key: 'vertical_window_enabled', type: 'bool', label: 'Vertical window enabled' },
      { key: 'show_live_water', type: 'bool', label: 'Show live water' },
      { key: 'show_title', type: 'bool', label: 'Show title' },
      { key: 'show_chrome_boxes', type: 'bool', label: 'Show chrome boxes' },
      { key: 'show_id_overlay', type: 'bool', label: 'Show ID' },
      { key: 'show_alerts', type: 'bool', label: 'Show alerts' },
      { key: 'show_paid_notices', type: 'bool', label: 'Show paid' },
      { key: 'show_bestiary', type: 'bool', label: 'Show bestiary' },
      { key: 'show_summon_march', type: 'bool', label: 'Show summon march' },
      { key: 'show_spend_indicator', type: 'bool', label: 'Show spend' },
      { key: 'show_free_promos', type: 'bool', label: 'Show free promos' },
      { key: 'show_double_points', type: 'bool', label: 'Show 2×' },
      { key: 'hide_spend_when_off', type: 'bool', label: 'Hide spend when off' },
      { key: 'alert_zone_x_px', type: 'int', label: 'Alert X' },
      { key: 'alert_zone_y_px', type: 'int', label: 'Alert Y' },
      { key: 'alert_zone_width_px', type: 'int', label: 'Alert W' },
      { key: 'alert_zone_height_px', type: 'int', label: 'Alert H' },
      { key: 'alert_zone_bottom_margin_px', type: 'int', label: 'Alert bottom margin' },
      { key: 'id_zone_x_px', type: 'int', label: 'ID X' },
      { key: 'id_zone_y_px', type: 'int', label: 'ID Y' },
      { key: 'id_zone_width_px', type: 'int', label: 'ID W' },
      { key: 'id_zone_height_px', type: 'int', label: 'ID H' },
      { key: 'id_zone_bottom_margin_px', type: 'int', label: 'ID bottom margin' },
      { key: 'bestiary_zone_x_px', type: 'int', label: 'Bestiary X' },
      { key: 'bestiary_zone_y_px', type: 'int', label: 'Bestiary Y' },
      { key: 'bestiary_zone_width_px', type: 'int', label: 'Bestiary W' },
      { key: 'bestiary_zone_height_px', type: 'int', label: 'Bestiary H' },
      { key: 'bestiary_zone_bottom_margin_px', type: 'int', label: 'Bestiary bottom margin' },
      { key: 'paid_notice_zone_x_px', type: 'int', label: 'Paid X' },
      { key: 'paid_notice_zone_y_px', type: 'int', label: 'Paid Y' },
      { key: 'paid_notice_zone_width_px', type: 'int', label: 'Paid W' },
      { key: 'paid_notice_zone_height_px', type: 'int', label: 'Paid H' },
      { key: 'paid_notice_zone_bottom_margin_px', type: 'int', label: 'Paid bottom margin' },
      { key: 'live_water_bottom_bar_px', type: 'int', label: 'Water bottom bar' },
      { key: 'live_water_left_strip_px', type: 'int', label: 'Water left strip' },
      { key: 'live_water_left_strip_top_px', type: 'int', label: 'Water left top' },
      { key: 'live_water_gradient_fade_start', type: 'float', label: 'Water fade start', min: 0, max: 1, step: 0.01 },
      { key: 'live_water_gradient_fade_end', type: 'float', label: 'Water fade end', min: 0, max: 1, step: 0.01 },
      { key: 'live_water_edge_feather_v_px', type: 'int', label: 'Water feather' },
      { key: 'spend_indicator_corner', type: 'select', label: 'Spend corner', options: CORNERS },
      { key: 'spend_indicator_margin_x', type: 'int', label: 'Spend margin X' },
      { key: 'spend_indicator_margin_y', type: 'int', label: 'Spend margin Y' },
      { key: 'free_promos_corner', type: 'select', label: 'Free corner', options: CORNERS },
      { key: 'free_promos_margin_x', type: 'int', label: 'Free margin X' },
      { key: 'free_promos_margin_y', type: 'int', label: 'Free margin Y' },
      { key: 'double_points_corner', type: 'select', label: '2× corner', options: CORNERS },
      { key: 'double_points_margin_x', type: 'int', label: '2× margin X' },
      { key: 'double_points_margin_y', type: 'int', label: '2× margin Y' },
    ]),
    summon: uiFields('ui.', [
      { key: 'summon_march_duration_sec', type: 'float', label: 'Duration (sec)', min: 1, max: 60, step: 0.5 },
      { key: 'summon_march_max_concurrent', type: 'int', label: 'Max concurrent', min: 1, max: 32 },
      { key: 'summon_march_lane_y_min_fraction', type: 'float', label: 'Lane Y min', min: 0.05, max: 0.95, step: 0.01 },
      { key: 'summon_march_lane_y_max_fraction', type: 'float', label: 'Lane Y max', min: 0.05, max: 0.95, step: 0.01 },
      { key: 'summon_march_lane_spacing_px', type: 'int', label: 'Lane spacing' },
      { key: 'summon_march_edge_margin_px', type: 'int', label: 'Edge margin' },
      { key: 'summon_march_mob_fps', type: 'float', label: 'Mob FPS', min: 1, max: 24, step: 0.5 },
      { key: 'summon_march_sprite_size_px', type: 'int', label: 'Sprite size', min: 16, max: 256 },
      { key: 'summon_march_show_username', type: 'bool', label: 'Show username' },
      { key: 'summon_march_username_centered', type: 'bool', label: 'Username centered' },
      { key: 'summon_march_show_monster_name', type: 'bool', label: 'Show monster name' },
      { key: 'summon_march_username_font_size_px', type: 'int', label: 'Username font', min: 8, max: 96 },
      { key: 'summon_march_monster_font_size_px', type: 'int', label: 'Monster font', min: 8, max: 96 },
      { key: 'summon_march_username_color', type: 'color', label: 'Username color' },
      { key: 'summon_march_monster_color', type: 'color', label: 'Monster color' },
      { key: 'summon_march_username_offset_x', type: 'int', label: 'Username offset X' },
      { key: 'summon_march_username_offset_y', type: 'int', label: 'Username offset Y' },
      { key: 'summon_march_monster_offset_x', type: 'int', label: 'Monster offset X' },
      { key: 'summon_march_monster_offset_y', type: 'int', label: 'Monster offset Y' },
      { key: 'summon_crowned_sprite_scale', type: 'float', label: 'Crowned scale', min: 0.5, max: 3, step: 0.01 },
      { key: 'summon_crowned_mob_modulate', type: 'color', label: 'Crowned modulate' },
      { key: 'summon_crowned_show_glow', type: 'bool', label: 'Show glow' },
      { key: 'summon_crowned_glow_rays', type: 'int', label: 'Glow rays', min: 0, max: 24 },
      { key: 'summon_crowned_glow_spin_deg', type: 'float', label: 'Glow spin deg' },
      { key: 'summon_crowned_glow_radius_scale', type: 'float', label: 'Glow radius scale', min: 0.1, max: 4, step: 0.05 },
      { key: 'summon_crowned_show_crown', type: 'bool', label: 'Show crown' },
      { key: 'summon_crowned_crown_scale', type: 'float', label: 'Crown scale', min: 0.1, max: 3, step: 0.05 },
      { key: 'summon_crowned_crown_offset_y', type: 'float', label: 'Crown offset Y', step: 0.01 },
      { key: 'summon_crowned_crown_modulate', type: 'color', label: 'Crown modulate' },
      { key: 'summon_crowned_username_color', type: 'color', label: 'Crowned username color' },
      { key: 'summon_crowned_username_font_size_px', type: 'int', label: 'Crowned username font (0=same)', min: 0, max: 96 },
      { key: 'summon_crowned_show_star_prefix', type: 'bool', label: 'Star prefix' },
    ]),
  };

  let _root = null;
  let _settings = { network: {}, ui: {}, ui_vertical: {} };
  let _doc = { revision: 0, updated_at: 0, source: '', settings: {} };
  let _dirty = false;
  let _activeTab = 'status';
  let _chromeProfile = 'main'; // main | vertical
  let _chromeIndex = 0;
  let _suppressDirty = false;
  let _msg = null;
  let _log = null;

  function pathGet(obj, path) {
    const parts = path.split('.');
    let cur = obj;
    for (const p of parts) {
      if (cur == null || typeof cur !== 'object') return undefined;
      cur = cur[p];
    }
    return cur;
  }

  function pathSet(obj, path, value) {
    const parts = path.split('.');
    let cur = obj;
    for (let i = 0; i < parts.length - 1; i++) {
      const p = parts[i];
      if (!cur[p] || typeof cur[p] !== 'object') cur[p] = {};
      cur = cur[p];
    }
    cur[parts[parts.length - 1]] = value;
  }

  function colorToHex(v, fallback) {
    if (typeof v === 'string' && /^#?[0-9a-fA-F]{6,8}$/.test(v.trim())) {
      const s = v.trim();
      return s.startsWith('#') ? s.slice(0, 7) : '#' + s.slice(0, 6);
    }
    return fallback || '#ffffff';
  }

  function markDirty() {
    if (_suppressDirty) return;
    _dirty = true;
    updateFooter();
  }

  function clearDirty() {
    _dirty = false;
    updateFooter();
  }

  function updateFooter() {
    const dirtyEl = _root?.querySelector('[data-comp-dirty]');
    const revEl = _root?.querySelector('[data-comp-rev]');
    if (dirtyEl) dirtyEl.textContent = _dirty ? 'Unsaved changes' : 'Saved';
    if (dirtyEl) dirtyEl.classList.toggle('companion-dirty', _dirty);
    if (revEl) {
      const src = _doc.source ? ' · ' + _doc.source : '';
      let when = '';
      if (_doc.updated_at) {
        try { when = ' · ' + new Date(_doc.updated_at * 1000).toLocaleString(); } catch (_) {}
      }
      revEl.textContent = 'Rev ' + (_doc.revision ?? 0) + src + when;
    }
  }

  function fieldId(path) {
    return 'comp_f_' + path.replace(/\./g, '_');
  }

  function renderField(f) {
    const id = fieldId(f.path);
    const row = document.createElement('div');
    row.className = 'row companion-field-row';
    const lab = document.createElement('label');
    lab.htmlFor = id;
    lab.textContent = f.label;
    row.appendChild(lab);
    let input;
    if (f.type === 'bool') {
      input = document.createElement('input');
      input.type = 'checkbox';
      input.id = id;
    } else if (f.type === 'chrome' || f.type === 'select') {
      input = document.createElement('select');
      input.id = id;
      const opts = f.type === 'chrome' ? CHROME_STYLES : (f.options || []);
      opts.forEach(([val, label]) => {
        const o = document.createElement('option');
        o.value = val;
        o.textContent = label;
        input.appendChild(o);
      });
    } else if (f.type === 'color') {
      input = document.createElement('input');
      input.type = 'color';
      input.id = id;
    } else if (f.type === 'text') {
      input = document.createElement('input');
      input.type = 'text';
      input.id = id;
      input.style.width = 'min(100%, 280px)';
    } else {
      input = document.createElement('input');
      input.type = 'number';
      input.id = id;
      if (f.min != null) input.min = f.min;
      if (f.max != null) input.max = f.max;
      if (f.step != null) input.step = f.step;
      else input.step = f.type === 'float' ? 'any' : '1';
    }
    input.dataset.compPath = f.path;
    input.dataset.compType = f.type;
    input.addEventListener('input', markDirty);
    input.addEventListener('change', markDirty);
    row.appendChild(input);
    return row;
  }

  function fillField(f) {
    const el = document.getElementById(fieldId(f.path));
    if (!el) return;
    const v = pathGet(_settings, f.path);
    if (f.type === 'bool') {
      el.checked = !!v;
    } else if (f.type === 'color') {
      el.value = colorToHex(v, '#ffffff');
    } else if (f.type === 'chrome' || f.type === 'select') {
      el.value = v != null ? String(v) : (el.options[0]?.value || '');
    } else if (v != null && v !== '') {
      el.value = v;
    }
  }

  function collectField(f) {
    const el = document.getElementById(fieldId(f.path));
    if (!el) return;
    let val;
    if (f.type === 'bool') val = !!el.checked;
    else if (f.type === 'int') {
      const n = parseInt(el.value, 10);
      val = Number.isFinite(n) ? n : 0;
      if (f.min != null) val = Math.max(f.min, val);
      if (f.max != null) val = Math.min(f.max, val);
    } else if (f.type === 'float') {
      const n = parseFloat(el.value);
      val = Number.isFinite(n) ? n : 0;
      if (f.min != null) val = Math.max(f.min, val);
      if (f.max != null) val = Math.min(f.max, val);
    } else if (f.type === 'color') {
      val = el.value || '#ffffff';
    } else {
      val = el.value;
    }
    pathSet(_settings, f.path, val);
  }

  function fillSceneGates(hostId, sceneShow) {
    const host = document.getElementById(hostId);
    if (!host) return;
    host.innerHTML = '';
    const ss = sceneShow && typeof sceneShow === 'object' ? sceneShow : {};
    SCENE_KEYS.forEach((key) => {
      const e = ss[key] || { pause: true, main: true, other: true };
      const row = document.createElement('div');
      row.className = 'row';
      row.dataset.sgKey = key;
      row.innerHTML =
        '<label style="min-width:7rem;">' + key + '</label>' +
        '<label><input type="checkbox" data-sg="pause"' + (e.pause ? ' checked' : '') + '> pause</label>' +
        '<label><input type="checkbox" data-sg="main"' + (e.main ? ' checked' : '') + '> main</label>' +
        '<label><input type="checkbox" data-sg="other"' + (e.other ? ' checked' : '') + '> other</label>';
      row.querySelectorAll('input').forEach((inp) => {
        inp.addEventListener('change', markDirty);
      });
      host.appendChild(row);
    });
  }

  function collectSceneGates(hostId) {
    const out = {};
    document.querySelectorAll('#' + hostId + ' .row[data-sg-key]').forEach((row) => {
      out[row.dataset.sgKey] = {
        pause: !!row.querySelector('[data-sg="pause"]')?.checked,
        main: !!row.querySelector('[data-sg="main"]')?.checked,
        other: !!row.querySelector('[data-sg="other"]')?.checked,
      };
    });
    return out;
  }

  function chromeBoxesArr() {
    if (_chromeProfile === 'vertical') {
      if (!_settings.ui_vertical) _settings.ui_vertical = {};
      if (!Array.isArray(_settings.ui_vertical.chrome_boxes)) _settings.ui_vertical.chrome_boxes = [];
      return _settings.ui_vertical.chrome_boxes;
    }
    if (!_settings.ui) _settings.ui = {};
    if (!Array.isArray(_settings.ui.chrome_boxes)) _settings.ui.chrome_boxes = [];
    return _settings.ui.chrome_boxes;
  }

  function defaultChromeBox(i) {
    return {
      id: 'cb_html_' + Date.now() + '_' + i,
      name: 'Box ' + i,
      enabled: true,
      style: 'window',
      zone_x_px: 40,
      zone_y_px: 40,
      zone_width_px: 320,
      zone_height_px: 180,
      zone_bottom_margin_px: 0,
      chrome_scale: 1,
      show_on_pause: false,
      show_on_main: true,
      show_on_other: true,
      header_text: '',
      body_text: '',
      header_font_size_px: 18,
      body_font_size_px: 14,
      header_font_color: '#ffff45',
      body_font_color: '#f2ebe0',
      text_align: 'left',
      text_shadow: true,
      padding_h_px: 14,
      padding_v_px: 10,
      line_separation_px: 6,
    };
  }

  function refreshChromeEditor() {
    const boxes = chromeBoxesArr();
    const pick = document.getElementById('comp_chrome_pick');
    const editor = document.getElementById('comp_chrome_editor');
    if (!pick || !editor) return;
    pick.innerHTML = '';
    boxes.forEach((b, i) => {
      const o = document.createElement('option');
      o.value = String(i);
      o.textContent = (b.name || ('Box ' + (i + 1))) + (b.enabled === false ? ' (off)' : '');
      pick.appendChild(o);
    });
    if (!boxes.length) {
      editor.innerHTML = '<p class="sub">No chrome boxes. Click Add.</p>';
      return;
    }
    if (_chromeIndex >= boxes.length) _chromeIndex = boxes.length - 1;
    pick.value = String(_chromeIndex);
    const b = Object.assign(defaultChromeBox(_chromeIndex + 1), boxes[_chromeIndex] || {});
    editor.innerHTML = '';
    const fields = [
      ['name', 'text', 'Name'],
      ['enabled', 'bool', 'Enabled'],
      ['style', 'chrome', 'Style'],
      ['zone_x_px', 'int', 'Zone X'],
      ['zone_y_px', 'int', 'Zone Y'],
      ['zone_width_px', 'int', 'Zone W'],
      ['zone_height_px', 'int', 'Zone H'],
      ['zone_bottom_margin_px', 'int', 'Bottom margin'],
      ['chrome_scale', 'float', 'Chrome scale'],
      ['show_on_pause', 'bool', 'Show on pause'],
      ['show_on_main', 'bool', 'Show on main'],
      ['show_on_other', 'bool', 'Show on other'],
      ['header_text', 'text', 'Header'],
      ['body_text', 'text', 'Body'],
      ['header_font_size_px', 'int', 'Header font'],
      ['body_font_size_px', 'int', 'Body font'],
      ['header_font_color', 'color', 'Header color'],
      ['body_font_color', 'color', 'Body color'],
      ['text_align', 'select', 'Align'],
      ['text_shadow', 'bool', 'Shadow'],
      ['padding_h_px', 'int', 'Pad H'],
      ['padding_v_px', 'int', 'Pad V'],
      ['line_separation_px', 'int', 'Line sep'],
    ];
    fields.forEach(([key, type, label]) => {
      const fake = {
        path: 'chrome.' + key,
        type,
        label,
        options: type === 'select' ? ALIGNS : undefined,
        min: type === 'float' ? 0.5 : undefined,
        max: type === 'float' ? 4 : undefined,
        step: type === 'float' ? 0.05 : undefined,
      };
      const row = renderField(fake);
      const inp = row.querySelector('input,select');
      inp.id = 'comp_cb_' + key;
      delete inp.dataset.compPath;
      inp.dataset.cbKey = key;
      inp.dataset.compType = type;
      if (type === 'bool') inp.checked = !!b[key];
      else if (type === 'color') inp.value = colorToHex(b[key], '#ffffff');
      else if (type === 'chrome' || type === 'select') inp.value = String(b[key] ?? '');
      else inp.value = b[key] != null ? b[key] : '';
      editor.appendChild(row);
    });
  }

  function commitChromeEditor() {
    const boxes = chromeBoxesArr();
    if (!boxes.length || _chromeIndex < 0 || _chromeIndex >= boxes.length) return;
    const b = Object.assign(defaultChromeBox(_chromeIndex + 1), boxes[_chromeIndex]);
    document.querySelectorAll('#comp_chrome_editor [data-cb-key]').forEach((el) => {
      const key = el.dataset.cbKey;
      const type = el.dataset.compType;
      if (type === 'bool') b[key] = !!el.checked;
      else if (type === 'int') b[key] = parseInt(el.value, 10) || 0;
      else if (type === 'float') b[key] = parseFloat(el.value) || 0;
      else if (type === 'color') b[key] = el.value;
      else b[key] = el.value;
    });
    boxes[_chromeIndex] = b;
  }

  function buildShell() {
    _root.innerHTML = `
      <p class="sub companion-lead">Remote layout/UI for SPD Companion. Enable <strong>Settings → Remote → poll</strong> once (leave push-on-save off). Does not sync OBS password or file paths.</p>
      <div class="companion-layout">
        <nav class="companion-nav" id="companionNav"></nav>
        <div class="companion-pages" id="companionPages"></div>
      </div>
      <div class="companion-footer">
        <span data-comp-rev class="companion-rev">Rev 0</span>
        <span data-comp-dirty class="companion-dirty-label">Saved</span>
        <button type="button" id="companionSettingsSave">Save to Flask</button>
        <button type="button" id="companionSettingsReload">Reload</button>
        <button type="button" id="companionUndoBtn" title="Restore previous revision">Undo last save</button>
      </div>
    `;
    const nav = _root.querySelector('#companionNav');
    const pages = _root.querySelector('#companionPages');
    TABS.forEach((t) => {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'companion-nav-btn';
      btn.dataset.tab = t.id;
      btn.textContent = t.label;
      btn.addEventListener('click', () => showTab(t.id));
      nav.appendChild(btn);
      const page = document.createElement('div');
      page.className = 'companion-page';
      page.dataset.page = t.id;
      page.hidden = true;
      pages.appendChild(page);
      buildPage(page, t.id);
    });
    _root.querySelector('#companionSettingsSave').addEventListener('click', save);
    _root.querySelector('#companionSettingsReload').addEventListener('click', () => {
      load().then(() => { if (_msg) _msg('Companion settings reloaded', true); });
    });
    _root.querySelector('#companionUndoBtn').addEventListener('click', undoLast);
    showTab(_activeTab);
    if (location.hash && location.hash.startsWith('#companion-')) {
      const id = location.hash.slice('#companion-'.length).replace(/-/g, '_');
      if (TABS.some((t) => t.id === id)) showTab(id);
    }
  }

  function buildPage(page, id) {
    if (id === 'status') {
      page.innerHTML = `
        <h3 class="companion-page-title">Status</h3>
        <p class="sub">HTML is the source of truth for remote-safe layout. Seed once from the companion (F2 → Remote → Upload current to Flask), then edit here.</p>
        <div class="row"><span class="sub">Heartbeat:</span> <span id="compHeartbeat">—</span></div>
        <div class="row" style="margin-top:0.5rem;gap:0.5rem;flex-wrap:wrap;">
          <button type="button" id="compRefreshHeartbeat">Refresh status</button>
        </div>
        <p class="sub" style="margin-top:0.75rem;">Presets</p>
        <div class="row" style="gap:0.5rem;flex-wrap:wrap;">
          <select id="compPresetPick" style="min-width:140px;"></select>
          <button type="button" id="compPresetApply">Apply preset</button>
          <input type="text" id="compPresetName" placeholder="new preset name" style="width:140px;">
          <button type="button" id="compPresetSave">Save as preset</button>
          <button type="button" id="compPresetDelete">Delete</button>
        </div>
      `;
      page.querySelector('#compRefreshHeartbeat').addEventListener('click', refreshHeartbeat);
      page.querySelector('#compPresetApply').addEventListener('click', applyPreset);
      page.querySelector('#compPresetSave').addEventListener('click', savePreset);
      page.querySelector('#compPresetDelete').addEventListener('click', deletePreset);
      return;
    }
    if (id === 'scene_gates') {
      page.innerHTML = `
        <h3 class="companion-page-title">Scene gates</h3>
        <p class="sub">Horizontal (main window)</p>
        <div id="compSceneGatesH" class="companion-gates"></div>
        <p class="sub" style="margin-top:0.75rem;">Vertical window</p>
        <div id="compSceneGatesV" class="companion-gates"></div>
      `;
      return;
    }
    if (id === 'chrome') {
      page.innerHTML = `
        <h3 class="companion-page-title">Chrome boxes</h3>
        <div class="row">
          <label>Layout</label>
          <select id="comp_chrome_profile">
            <option value="main">Main</option>
            <option value="vertical">Vertical</option>
          </select>
        </div>
        <div class="row" style="gap:0.5rem;flex-wrap:wrap;">
          <select id="comp_chrome_pick" style="min-width:160px;"></select>
          <button type="button" id="comp_chrome_add">Add</button>
          <button type="button" id="comp_chrome_remove">Remove</button>
        </div>
        <div id="comp_chrome_editor"></div>
      `;
      page.querySelector('#comp_chrome_profile').addEventListener('change', (e) => {
        commitChromeEditor();
        _chromeProfile = e.target.value;
        _chromeIndex = 0;
        refreshChromeEditor();
        markDirty();
      });
      page.querySelector('#comp_chrome_pick').addEventListener('change', (e) => {
        commitChromeEditor();
        _chromeIndex = parseInt(e.target.value, 10) || 0;
        refreshChromeEditor();
      });
      page.querySelector('#comp_chrome_add').addEventListener('click', () => {
        commitChromeEditor();
        const boxes = chromeBoxesArr();
        if (boxes.length >= 16) { if (_msg) _msg('Max 16 chrome boxes', false); return; }
        boxes.push(defaultChromeBox(boxes.length + 1));
        _chromeIndex = boxes.length - 1;
        refreshChromeEditor();
        markDirty();
      });
      page.querySelector('#comp_chrome_remove').addEventListener('click', () => {
        const boxes = chromeBoxesArr();
        if (!boxes.length) return;
        boxes.splice(_chromeIndex, 1);
        _chromeIndex = Math.max(0, _chromeIndex - 1);
        refreshChromeEditor();
        markDirty();
      });
      return;
    }
    if (id === 'advanced') {
      page.innerHTML = `
        <h3 class="companion-page-title">Advanced JSON</h3>
        <p class="sub">Escape hatch. Save still collects all tabs; editing JSON then Save uses this blob merged with tab fields (tabs win on conflict for mapped fields).</p>
        <textarea id="companionSettingsJson" rows="14" class="companion-json"></textarea>
        <div class="row" style="margin-top:0.5rem;gap:0.5rem;flex-wrap:wrap;">
          <button type="button" id="compSyncJsonFromTabs">Refresh JSON from tabs</button>
          <button type="button" id="compApplyJsonToForms">Apply JSON → forms</button>
        </div>
      `;
      page.querySelector('#companionSettingsJson').addEventListener('input', markDirty);
      page.querySelector('#compSyncJsonFromTabs').addEventListener('click', () => {
        collectAll();
        if (_msg) _msg('JSON refreshed from tabs', true);
      });
      page.querySelector('#compApplyJsonToForms').addEventListener('click', applyJsonToForms);
      return;
    }
    const title = TABS.find((t) => t.id === id)?.label || id;
    page.innerHTML = `<h3 class="companion-page-title">${title}</h3><div class="companion-fields" data-fields="${id}"></div>`;
    const host = page.querySelector('.companion-fields');
    const groupKey = id === 'vertical' ? 'vertical_zones' : id === 'bestiary_hud' ? 'bestiary_hud' : id;
    const fields = FIELD_GROUPS[groupKey] || FIELD_GROUPS[id] || [];
    fields.forEach((f) => host.appendChild(renderField(f)));
  }

  function showTab(id) {
    if (_activeTab === 'chrome') commitChromeEditor();
    _activeTab = id;
    _root.querySelectorAll('.companion-nav-btn').forEach((b) => {
      b.classList.toggle('active', b.dataset.tab === id);
    });
    _root.querySelectorAll('.companion-page').forEach((p) => {
      p.hidden = p.dataset.page !== id;
    });
    try { history.replaceState(null, '', '#companion-' + id.replace(/_/g, '-')); } catch (_) {}
  }

  function collectAll() {
    if (_activeTab === 'chrome') commitChromeEditor();
    Object.keys(FIELD_GROUPS).forEach((gk) => {
      FIELD_GROUPS[gk].forEach(collectField);
    });
    if (!_settings.ui) _settings.ui = {};
    if (!_settings.ui_vertical) _settings.ui_vertical = {};
    if (!_settings.network) _settings.network = {};
    if (document.getElementById('compSceneGatesH')) {
      _settings.ui.main_scene_show = collectSceneGates('compSceneGatesH');
    }
    if (document.getElementById('compSceneGatesV')) {
      _settings.ui_vertical.scene_show = collectSceneGates('compSceneGatesV');
    }
    const ta = document.getElementById('companionSettingsJson');
    if (ta) ta.value = JSON.stringify(_settings, null, 2);
  }

  function applyJsonToForms() {
    const ta = document.getElementById('companionSettingsJson');
    if (!ta) return;
    try {
      const parsed = JSON.parse(ta.value || '{}');
      if (!parsed || typeof parsed !== 'object') throw new Error('object required');
      _settings = parsed;
      if (!_settings.network) _settings.network = {};
      if (!_settings.ui) _settings.ui = {};
      if (!_settings.ui_vertical) _settings.ui_vertical = {};
      fillAll();
      markDirty();
      if (_msg) _msg('JSON applied to forms', true);
    } catch (e) {
      if (_msg) _msg('JSON invalid: ' + e.message, false);
    }
  }

  function fillAll() {
    _suppressDirty = true;
    Object.keys(FIELD_GROUPS).forEach((gk) => {
      FIELD_GROUPS[gk].forEach(fillField);
    });
    fillSceneGates('compSceneGatesH', (_settings.ui && _settings.ui.main_scene_show) || {});
    fillSceneGates('compSceneGatesV', (_settings.ui_vertical && _settings.ui_vertical.scene_show) || {});
    const ta = document.getElementById('companionSettingsJson');
    if (ta) ta.value = JSON.stringify(_settings, null, 2);
    const prof = document.getElementById('comp_chrome_profile');
    if (prof) prof.value = _chromeProfile;
    refreshChromeEditor();
    _suppressDirty = false;
    clearDirty();
    updateFooter();
    refreshHeartbeat();
    loadPresetList();
  }

  function validate() {
    const errs = [];
    Object.keys(FIELD_GROUPS).forEach((gk) => {
      FIELD_GROUPS[gk].forEach((f) => {
        if (f.type !== 'int' && f.type !== 'float') return;
        const el = document.getElementById(fieldId(f.path));
        if (!el) return;
        const n = f.type === 'int' ? parseInt(el.value, 10) : parseFloat(el.value);
        if (!Number.isFinite(n)) errs.push(f.label + ' invalid');
        if (f.min != null && n < f.min) errs.push(f.label + ' < ' + f.min);
        if (f.max != null && n > f.max) errs.push(f.label + ' > ' + f.max);
      });
    });
    return errs;
  }

  function save() {
    const errs = validate();
    if (errs.length) {
      if (_msg) _msg('Companion: ' + errs[0], false);
      return;
    }
    collectAll();
    fetch('/api/companion-settings', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ settings: _settings, source: 'html' }),
    })
      .then((r) => r.json())
      .then((data) => {
        if (data.error) throw new Error(data.error);
        applyDoc(data);
        if (_msg) _msg('Companion settings saved (rev ' + (data.revision || '?') + ')', true);
        if (_log) _log('Companion settings saved');
      })
      .catch((e) => { if (_msg) _msg('Companion save failed: ' + e.message, false); });
  }

  function applyDoc(doc) {
    _doc = doc || { revision: 0, settings: {} };
    _settings = (_doc.settings && typeof _doc.settings === 'object')
      ? JSON.parse(JSON.stringify(_doc.settings))
      : { network: {}, ui: {}, ui_vertical: {} };
    if (!_settings.network) _settings.network = {};
    if (!_settings.ui) _settings.ui = {};
    if (!_settings.ui_vertical) _settings.ui_vertical = {};
    // Godot puts vertical_window_enabled inside ui_vertical remote dict
    fillAll();
  }

  function load() {
    return fetch('/api/companion-settings')
      .then((r) => r.json())
      .then((data) => {
        if (data && data.error) throw new Error(data.error);
        applyDoc(data);
        return data;
      })
      .catch((e) => { console.warn('companion-settings load', e); });
  }

  function refreshHeartbeat() {
    const el = document.getElementById('compHeartbeat');
    if (!el) return;
    fetch('/api/companion-settings/heartbeat')
      .then((r) => r.json())
      .then((d) => {
        if (d.error) throw new Error(d.error);
        if (!d.last_seen) {
          el.textContent = 'No companion heartbeat yet (enable Remote poll)';
          return;
        }
        const ago = Math.max(0, Math.floor(Date.now() / 1000 - d.last_seen));
        el.textContent = 'Seen ' + ago + 's ago · applied rev ' + (d.applied_revision ?? '?') +
          (d.server_revision != null ? ' · server rev ' + d.server_revision : '');
      })
      .catch(() => { el.textContent = 'Heartbeat unavailable'; });
  }

  function loadPresetList() {
    const pick = document.getElementById('compPresetPick');
    if (!pick) return;
    fetch('/api/companion-settings/presets')
      .then((r) => r.json())
      .then((d) => {
        const names = (d && d.presets) || [];
        pick.innerHTML = '';
        names.forEach((n) => {
          const o = document.createElement('option');
          o.value = n;
          o.textContent = n;
          pick.appendChild(o);
        });
      })
      .catch(() => {});
  }

  function applyPreset() {
    const name = document.getElementById('compPresetPick')?.value;
    if (!name) return;
    fetch('/api/companion-settings/presets/' + encodeURIComponent(name) + '/apply', { method: 'POST' })
      .then((r) => r.json())
      .then((data) => {
        if (data.error) throw new Error(data.error);
        applyDoc(data);
        if (_msg) _msg('Preset applied (rev ' + data.revision + ')', true);
      })
      .catch((e) => { if (_msg) _msg('Preset apply failed: ' + e.message, false); });
  }

  function savePreset() {
    const name = (document.getElementById('compPresetName')?.value || '').trim();
    if (!name) { if (_msg) _msg('Enter a preset name', false); return; }
    collectAll();
    fetch('/api/companion-settings/presets', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, settings: _settings }),
    })
      .then((r) => r.json())
      .then((d) => {
        if (d.error) throw new Error(d.error);
        loadPresetList();
        if (_msg) _msg('Preset saved: ' + name, true);
      })
      .catch((e) => { if (_msg) _msg('Preset save failed: ' + e.message, false); });
  }

  function deletePreset() {
    const name = document.getElementById('compPresetPick')?.value;
    if (!name) return;
    if (!confirm('Delete preset "' + name + '"?')) return;
    fetch('/api/companion-settings/presets/' + encodeURIComponent(name), { method: 'DELETE' })
      .then((r) => r.json())
      .then((d) => {
        if (d.error) throw new Error(d.error);
        loadPresetList();
        if (_msg) _msg('Preset deleted', true);
      })
      .catch((e) => { if (_msg) _msg('Delete failed: ' + e.message, false); });
  }

  function undoLast() {
    fetch('/api/companion-settings/undo', { method: 'POST' })
      .then((r) => r.json())
      .then((data) => {
        if (data.error) throw new Error(data.error);
        applyDoc(data);
        if (_msg) _msg('Restored previous revision (now rev ' + data.revision + ')', true);
      })
      .catch((e) => { if (_msg) _msg('Undo failed: ' + e.message, false); });
  }

  function init(opts) {
    _root = document.getElementById(opts.rootId || 'companionPanelRoot');
    if (!_root) return;
    _msg = opts.showMsg || null;
    _log = opts.logActivity || null;
    buildShell();
    load();
  }

  global.CompanionSettingsPanel = {
    init,
    load,
    save,
    isDirty: () => _dirty,
  };
})(window);
