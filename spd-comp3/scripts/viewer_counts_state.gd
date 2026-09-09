extends Node

## Shared viewer-count state for the horizontal and vertical companion canvases.

signal counts_changed

var twitch: int = -1
var youtube: int = -1
var tiktok: int = -1
var stale: Dictionary = {
	"twitch": true,
	"youtube": true,
	"tiktok": true,
}
var errors: Dictionary = {}


func set_count(platform: String, value: int) -> void:
	if platform not in ["twitch", "youtube", "tiktok"]:
		return
	var next := maxi(0, value)
	var changed := int(get(platform)) != next or bool(stale.get(platform, true))
	set(platform, next)
	stale[platform] = false
	errors.erase(platform)
	if changed:
		counts_changed.emit()


func set_error(platform: String, message: String) -> void:
	if platform not in ["twitch", "youtube", "tiktok"]:
		return
	var changed := not bool(stale.get(platform, false)) or str(errors.get(platform, "")) != message
	stale[platform] = true
	errors[platform] = message
	if changed:
		counts_changed.emit()


func count_for(platform: String) -> int:
	return int(get(platform))


func is_stale(platform: String) -> bool:
	return bool(stale.get(platform, true))
