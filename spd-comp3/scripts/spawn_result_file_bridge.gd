extends Node
## Polls the same spawn_result.txt Streamer.bot C# reads. Does **not** delete the file so the C# Execute() can still read and delete it.
## Pipe format matches Streamer.bot: `result|itemName|points`. Trivial first-column acks (`ok`, `success`, …) never enqueue — the alert presenter relies on WebSocket results instead; see `alert_presenter.gd`.

signal spawn_result_file_parsed(spawn_text: String, item_name: String, points_remaining: String)

var _accum: float = 0.0
var _last_mtime: int = 0
var _last_path: String = ""


func _ready() -> void:
	CompanionConfig.settings_saved.connect(_on_settings_saved)


func _on_settings_saved() -> void:
	_accum = 0.0
	var p: String = CompanionConfig.spawn_result_file_path.strip_edges()
	if p != _last_path:
		_last_path = p
		_last_mtime = 0


func _physics_process(delta: float) -> void:
	var path: String = CompanionConfig.spawn_result_file_path.strip_edges()
	if path.is_empty():
		return
	if path != _last_path:
		_last_path = path
		_last_mtime = 0
	var step: float = maxf(0.05, CompanionConfig.spawn_result_file_poll_sec)
	_accum += delta
	if _accum < step:
		return
	_accum = 0.0
	if not FileAccess.file_exists(path):
		return
	var mtime: int = int(FileAccess.get_modified_time(path))
	if mtime > 0 and mtime <= _last_mtime:
		return
	var raw: String = FileAccess.get_file_as_string(path)
	if mtime > 0:
		_last_mtime = mtime
	var parsed: Dictionary = _parse_same_as_streamerbot_csharp(raw)
	var spawn_text: String = str(parsed.get("result", "")).strip_edges()
	var item_name: String = str(parsed.get("item", "")).strip_edges()
	var points: String = str(parsed.get("points", "")).strip_edges()
	if spawn_text.is_empty() and item_name.is_empty():
		return
	spawn_result_file_parsed.emit(spawn_text, item_name, points)


static func _parse_same_as_streamerbot_csharp(raw: String) -> Dictionary:
	var s: String = raw.strip_edges()
	var out_result: String = s
	var item_name: String = ""
	var points: String = ""
	var parts: PackedStringArray = s.split("|")
	var n: int = parts.size()
	if n >= 3:
		var last: String = parts[n - 1].strip_edges()
		if last.is_valid_int():
			points = last
			item_name = parts[1].strip_edges()
			out_result = parts[0].strip_edges()
		elif n >= 2:
			item_name = parts[1].strip_edges()
			out_result = parts[0].strip_edges()
	elif n >= 2:
		item_name = parts[1].strip_edges()
		out_result = parts[0].strip_edges()
	return {"result": out_result, "item": item_name, "points": points}
