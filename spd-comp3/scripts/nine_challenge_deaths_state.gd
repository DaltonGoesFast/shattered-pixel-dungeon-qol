extends Node

## Shared persistent 9-challenge death count from Lastest UI.

signal count_changed

var count: int = 1314


func set_count(value: int) -> void:
	var next := maxi(0, value)
	if next == count:
		return
	count = next
	count_changed.emit()


func display_text() -> String:
	return "9c deaths: %d" % count
