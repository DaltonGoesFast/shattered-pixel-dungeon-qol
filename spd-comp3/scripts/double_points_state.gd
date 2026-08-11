extends Node

## Shared global 2× countdown from Lastest UI. Main [code]DoublePointsPanel[/code] polls; vertical mirrors.

signal active_changed

var active: bool = false
var end_unix: int = 0


func set_countdown(is_active: bool, secs_remaining: int) -> void:
	var now: int = int(Time.get_unix_time_from_system())
	var next_active := is_active and secs_remaining > 0
	var next_end := (now + secs_remaining) if next_active else 0
	if next_active == active and next_end == end_unix:
		return
	active = next_active
	end_unix = next_end
	active_changed.emit()


func clear() -> void:
	if not active and end_unix == 0:
		return
	active = false
	end_unix = 0
	active_changed.emit()


func seconds_left() -> int:
	if not active or end_unix <= 0:
		return 0
	return maxi(0, end_unix - int(Time.get_unix_time_from_system()))


func display_text() -> String:
	var secs := seconds_left()
	if secs <= 0:
		return ""
	# Match Lastest UI / OBS: minutes only, round up.
	var mins: int = int(ceili(float(secs) / 60.0))
	return "2x points: %d min" % mins
