extends RefCounted
class_name SpdUiArt
## Atlas helpers from Shattered Pixel Dungeon interface sheets.
## Rects match Java: StatusPane.java (exp bar), Chrome.java (nine-patch).

const STATUS_PANE := "res://assets/ui_spd/status_pane/status_pane.png"
const CHROME_DIR := "res://assets/ui_spd/chrome/"
const CHROME_WINDOW := CHROME_DIR + "window.png"
const CHROME_WINDOW_SILVER := CHROME_DIR + "window_silver.png"
const CHROME_TOAST := CHROME_DIR + "toast.png"
const CHROME_TOAST_TR := CHROME_DIR + "toast_tr.png"
const CHROME_TOAST_TR_HEAVY := CHROME_DIR + "toast_tr_heavy.png"
const CHROME_TOAST_WHITE := CHROME_DIR + "toast_white.png"
const CHROME_RED_BUTTON := CHROME_DIR + "red_button.png"
const CHROME_GREY_BUTTON := CHROME_DIR + "grey_button.png"
const CHROME_TAG := CHROME_DIR + "tag.png"
const CHROME_GEM := CHROME_DIR + "gem.png"
const CHROME_SCROLL := CHROME_DIR + "scroll.png"
const CHROME_TAB_SET := CHROME_DIR + "tab_set.png"
const CHROME_TAB_SELECTED := CHROME_DIR + "tab_selected.png"
const CHROME_TAB_UNSELECTED := CHROME_DIR + "tab_unselected.png"
const CHROME_BLANK := CHROME_DIR + "blank.png"

## Placeable panel styles (ids match Chrome.Type names, snake_case). Display labels for Settings.
const CHROME_STYLE_IDS := [
	"window",
	"window_silver",
	"toast",
	"toast_tr",
	"toast_tr_heavy",
	"toast_white",
	"red_button",
	"grey_button",
	"grey_button_tr",
	"tag",
	"gem",
	"scroll",
	"tab_set",
	"tab_selected",
	"tab_unselected",
	"blank",
]
const CHROME_STYLE_LABELS := [
	"Window (grey)",
	"Window (silver)",
	"Toast",
	"Toast (transparent)",
	"Toast (transparent heavy)",
	"Toast (white)",
	"Red button",
	"Grey button",
	"Grey button (transparent)",
	"Tag",
	"Gem",
	"Scroll",
	"Tab set",
	"Tab selected",
	"Tab unselected",
	"Blank",
]
const BANNER_BOSS_SLAIN := "res://assets/ui_spd/banners/boss_slain.png"
## 5x 16px icons: Sewers, Prison, Caves, City, Halls (from interfaces/levelicons.png).
const LEVEL_ICONS := "res://assets/ui_spd/levelicons/levelicons.png"
const PIXEL_FONT := "res://assets/fonts/pixel_font.ttf"
const LEVEL_ICON_SIZE := 16

## Cached soft HUD font — do not mutate the shared pixel_font import used by marches.
static var _hud_font_cache = null

## StatusPane.java large exp: new Image(asset, 0, 121, 128, 7)
const EXP_LARGE := Rect2i(0, 121, 128, 7)
## StatusPane.java compact exp: new Image(asset, 0, 48, 17, 4)
const EXP_COMPACT := Rect2i(0, 48, 17, 4)
## StatusPane.java large HP track (optional track under fill): 0, 103, 128, 9
const HP_LARGE_TRACK := Rect2i(0, 103, 128, 9)

## Exp text color from StatusPane.expText.hardlight(0xFFFFAA)
const EXP_TEXT_COLOR := Color(1.0, 1.0, 170.0 / 255.0, 0.6)


static func _load_tex(path: String) -> Texture2D:
	if not ResourceLoader.exists(path):
		push_warning("SpdUiArt: missing texture %s" % path)
		return null
	return load(path) as Texture2D


static func atlas_region(sheet_path: String, region: Rect2i) -> AtlasTexture:
	var sheet := _load_tex(sheet_path)
	if sheet == null:
		return null
	var at := AtlasTexture.new()
	at.atlas = sheet
	at.region = Rect2(region)
	at.filter_clip = true
	return at


static func exp_fill_texture(compact: bool = false) -> AtlasTexture:
	## StatusPane.java lines 164–165
	return atlas_region(STATUS_PANE, EXP_COMPACT if compact else EXP_LARGE)


static func exp_track_texture() -> AtlasTexture:
	## Use large HP bar strip as a muted track under the exp fill.
	return atlas_region(STATUS_PANE, HP_LARGE_TRACK)


static func chrome_style_id_index(style_id: String) -> int:
	var id := style_id.strip_edges().to_lower()
	for i in range(CHROME_STYLE_IDS.size()):
		if CHROME_STYLE_IDS[i] == id:
			return i
	return 0


static func normalize_chrome_style_id(style_id: String) -> String:
	var idx := chrome_style_id_index(style_id)
	return CHROME_STYLE_IDS[idx]


static func chrome_style_label(style_id: String) -> String:
	var idx := chrome_style_id_index(style_id)
	return CHROME_STYLE_LABELS[idx]


static func chrome_style(style_id: String, border_scale: float = 1.0) -> StyleBoxTexture:
	## Margins match Chrome.java NinePatch for each Type (pre-sliced PNGs under ui_spd/chrome/).
	match normalize_chrome_style_id(style_id):
		"window_silver":
			return _style_from_path(CHROME_WINDOW_SILVER, 7, 7, 7, 7, 8, 8, 8, 8, border_scale)
		"toast":
			return chrome_style_toast(border_scale)
		"toast_tr", "grey_button_tr":
			return _style_from_path(CHROME_TOAST_TR, 4, 4, 4, 4, 6, 4, 6, 4, border_scale)
		"toast_tr_heavy":
			return _style_from_path(CHROME_TOAST_TR_HEAVY, 4, 4, 4, 4, 6, 4, 6, 4, border_scale)
		"toast_white":
			return _style_from_path(CHROME_TOAST_WHITE, 4, 4, 4, 4, 6, 4, 6, 4, border_scale)
		"red_button":
			return chrome_style_red_button(border_scale)
		"grey_button":
			return _style_from_path(CHROME_GREY_BUTTON, 2, 2, 2, 2, 5, 4, 5, 4, border_scale)
		"tag":
			return _style_from_path(CHROME_TAG, 3, 3, 3, 3, 4, 4, 4, 4, border_scale)
		"gem":
			return _style_from_path(CHROME_GEM, 13, 13, 13, 13, 14, 14, 14, 14, border_scale)
		"scroll":
			return _style_from_path(CHROME_SCROLL, 5, 11, 5, 11, 5, 11, 5, 11, border_scale)
		"tab_set":
			return _style_from_path(CHROME_TAB_SET, 6, 6, 6, 6, 8, 8, 8, 8, border_scale)
		"tab_selected":
			return _style_from_path(CHROME_TAB_SELECTED, 3, 7, 3, 5, 3, 7, 3, 5, border_scale)
		"tab_unselected":
			return _style_from_path(CHROME_TAB_UNSELECTED, 3, 7, 3, 5, 3, 7, 3, 5, border_scale)
		"blank":
			return _style_from_path(CHROME_BLANK, 0, 0, 0, 0, 0, 0, 0, 0, border_scale)
		_:
			return chrome_style_window(border_scale)


static func chrome_style_window(border_scale: float = 1.0) -> StyleBoxTexture:
	## Chrome.Type.WINDOW — NinePatch(0,0,20,20,6) → sliced asset window.png margin 6
	return _style_from_path(CHROME_WINDOW, 6, 6, 6, 6, 8, 8, 8, 8, border_scale)


static func chrome_style_toast(border_scale: float = 1.0) -> StyleBoxTexture:
	## Chrome.Type.TOAST — margin 4
	return _style_from_path(CHROME_TOAST, 4, 4, 4, 4, 6, 4, 6, 4, border_scale)


static func chrome_style_red_button(border_scale: float = 1.0) -> StyleBoxTexture:
	## Chrome.Type.RED_BUTTON — margin 2 (same as spend_indicator.gd)
	return _style_from_path(CHROME_RED_BUTTON, 2, 2, 2, 2, 5, 4, 5, 4, border_scale)


static func _style_from_path(
	path: String,
	ml: int,
	mt: int,
	mr: int,
	mb: int,
	cl: int,
	ct: int,
	cr: int,
	cb: int,
	border_scale: float = 1.0,
) -> StyleBoxTexture:
	var scale := clampf(border_scale, 0.5, 4.0)
	var tex := _load_tex(path)
	var sb := StyleBoxTexture.new()
	if tex:
		if absf(scale - 1.0) > 0.01:
			var img: Image = tex.get_image()
			if img != null:
				img = img.duplicate()
				var w := maxi(1, int(round(float(img.get_width()) * scale)))
				var h := maxi(1, int(round(float(img.get_height()) * scale)))
				img.resize(w, h, Image.INTERPOLATE_NEAREST)
				sb.texture = ImageTexture.create_from_image(img)
			else:
				sb.texture = tex
		else:
			sb.texture = tex
	sb.texture_margin_left = float(ml) * scale
	sb.texture_margin_top = float(mt) * scale
	sb.texture_margin_right = float(mr) * scale
	sb.texture_margin_bottom = float(mb) * scale
	sb.content_margin_left = float(cl) * scale
	sb.content_margin_top = float(ct) * scale
	sb.content_margin_right = float(cr) * scale
	sb.content_margin_bottom = float(cb) * scale
	return sb


static func apply_nearest(tex_rect: TextureRect) -> void:
	if tex_rect:
		tex_rect.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST


static func apply_label_smooth(lab: Label) -> void:
	## Viewport defaults to NEAREST for pixel art; linear filter softens TTF glyphs on Labels.
	if lab:
		lab.texture_filter = CanvasItem.TEXTURE_FILTER_LINEAR


static func pixel_font() -> Font:
	if ResourceLoader.exists(PIXEL_FONT):
		return load(PIXEL_FONT) as Font
	return ThemeDB.fallback_font


static func hud_font() -> Font:
	## Same face as pixel_font but with gray antialiasing + oversampling for readable overlay text.
	if _hud_font_cache != null:
		return _hud_font_cache
	if not ResourceLoader.exists(PIXEL_FONT):
		return ThemeDB.fallback_font
	var base := load(PIXEL_FONT) as FontFile
	if base == null:
		return ThemeDB.fallback_font
	var soft: FontFile = base.duplicate() as FontFile
	if soft == null:
		return base
	# 1 = gray antialiasing, 1 = light hinting (avoid FontFile enum names for parser compat)
	soft.antialiasing = 1
	soft.hinting = 1
	soft.oversampling = 2.0
	_hud_font_cache = soft
	return soft


static func banner_boss_slain() -> Texture2D:
	return _load_tex(BANNER_BOSS_SLAIN)


static func badge_cell(index: int = 0) -> Texture2D:
	## Prefer pre-sliced badge cells under ui_spd/badges/cells/
	var path := "res://assets/ui_spd/badges/cells/badge_%04d.png" % clampi(index, 0, 127)
	if ResourceLoader.exists(path):
		return load(path) as Texture2D
	return null


static func level_icon_index_for_zone(zone: String) -> int:
	match zone.strip_edges().to_lower():
		"sewers":
			return 0
		"prison":
			return 1
		"caves":
			return 2
		"city":
			return 3
		"halls":
			return 4
		_:
			return -1


static func level_icon_at(sheet_index: int = 0) -> AtlasTexture:
	## Sheet column 0..4 → 16x16 cell from levelicons.png.
	var idx := clampi(sheet_index, 0, 4)
	return atlas_region(
		LEVEL_ICONS, Rect2i(idx * LEVEL_ICON_SIZE, 0, LEVEL_ICON_SIZE, LEVEL_ICON_SIZE)
	)


static func level_icon(level: int = 1) -> AtlasTexture:
	## Bestiary/dungeon level 1..5 → Sewers..Halls icon.
	return level_icon_at(clampi(level, 1, 5) - 1)


static func level_icon_for_zone(zone: String, fallback_level: int = 1) -> AtlasTexture:
	var idx := level_icon_index_for_zone(zone)
	if idx < 0:
		return level_icon(fallback_level)
	return level_icon_at(idx)
