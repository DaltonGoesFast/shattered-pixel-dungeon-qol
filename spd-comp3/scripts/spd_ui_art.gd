extends RefCounted
class_name SpdUiArt
## Atlas helpers from Shattered Pixel Dungeon interface sheets.
## Rects match Java: StatusPane.java (exp bar), Chrome.java (nine-patch).

const STATUS_PANE := "res://assets/ui_spd/status_pane/status_pane.png"
const CHROME_WINDOW := "res://assets/ui_spd/chrome/window.png"
const CHROME_TOAST := "res://assets/ui_spd/chrome/toast.png"
const CHROME_RED_BUTTON := "res://assets/ui_spd/chrome/red_button.png"
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


static func chrome_style_window() -> StyleBoxTexture:
	## Chrome.Type.WINDOW — NinePatch(0,0,20,20,6) → sliced asset window.png margin 6
	return _style_from_path(CHROME_WINDOW, 6, 6, 6, 6, 8, 8, 8, 8)


static func chrome_style_toast() -> StyleBoxTexture:
	## Chrome.Type.TOAST — margin 4
	return _style_from_path(CHROME_TOAST, 4, 4, 4, 4, 6, 4, 6, 4)


static func chrome_style_red_button() -> StyleBoxTexture:
	## Chrome.Type.RED_BUTTON — margin 2 (same as spend_indicator.gd)
	return _style_from_path(CHROME_RED_BUTTON, 2, 2, 2, 2, 5, 4, 5, 4)


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
) -> StyleBoxTexture:
	var tex := _load_tex(path)
	var sb := StyleBoxTexture.new()
	if tex:
		sb.texture = tex
	sb.texture_margin_left = ml
	sb.texture_margin_top = mt
	sb.texture_margin_right = mr
	sb.texture_margin_bottom = mb
	sb.content_margin_left = cl
	sb.content_margin_top = ct
	sb.content_margin_right = cr
	sb.content_margin_bottom = cb
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
