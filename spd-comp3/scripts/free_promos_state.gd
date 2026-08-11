extends Node

## Shared [code]free_until[/code] state from Lastest UI points-config. [member FreePromosPanel] syncs here; [code]alert_presenter[/code] uses it for faster toasts.

signal active_changed

const COMMAND_TO_COST: Dictionary = {
	"gold": "cost_per_gold",
	"curse": "cost_per_curse",
	"gas": "cost_per_gas",
	"scroll": "cost_per_scroll",
	"trap": "cost_per_trap",
	"bomb": "cost_per_bomb",
	"transmute": "cost_per_transmute",
	"summon_bee": "cost_per_ally_bee",
	"ward": "cost_per_ward",
	"buff": "cost_per_buff",
	"debuff": "cost_per_debuff",
	"wand": "cost_per_wand",
	"heal": "cost_per_heal",
	"cleanse": "cost_per_cleanse",
	"dew": "cost_per_dew",
	"hex": "cost_per_hex",
	"degrade": "cost_per_degrade",
	"sabotage": "cost_per_sabotage",
	"corrupt_ally": "cost_per_corrupt_ally",
	"ring_of_wealth": "cost_per_ring_of_wealth",
}

## Each entry: [code]{ "key": String, "end": int }[/code] unix seconds.
var _active: Array = []


func set_active(rows: Array) -> void:
	_active = rows.duplicate(true)
	active_changed.emit()


func get_active() -> Array:
	_prune_expired()
	return _active.duplicate(true)


func has_any_active() -> bool:
	_prune_expired()
	return not _active.is_empty()


func is_command_free(command: String, monster_hint: String = "") -> bool:
	_prune_expired()
	if _active.is_empty():
		return false
	var cmd := command.strip_edges().to_lower()
	if cmd.is_empty():
		return has_any_active()
	for item in _active:
		var key := str(item.get("key", "")).strip_edges()
		if key.is_empty():
			continue
		if _cost_key_matches_command(cmd, monster_hint, key):
			return true
	return false


func is_result_type_free(type_name: String, row: Dictionary) -> bool:
	var cmd := _command_from_result_type(type_name)
	var monster := ""
	if cmd == "spawn" or cmd == "champion":
		monster = str(row.get("monster_hint", "")).strip_edges()
	return is_command_free(cmd, monster)


func _command_from_result_type(type_name: String) -> String:
	var t := type_name.strip_edges().to_lower()
	if t.ends_with("_result"):
		return t.trim_suffix("_result")
	return t


func _prune_expired() -> void:
	var now: int = int(Time.get_unix_time_from_system())
	var keep: Array = []
	for item in _active:
		if int(item.get("end", 0)) > now:
			keep.append(item)
	if keep.size() != _active.size():
		_active = keep


func _cost_key_matches_command(cmd: String, monster_hint: String, cost_key: String) -> bool:
	var key_lower := cost_key.to_lower()
	if cmd == "spawn" or cmd == "champion":
		return _spawn_cost_key_matches(monster_hint, key_lower)
	var base: String = str(COMMAND_TO_COST.get(cmd, ""))
	if base.is_empty():
		return false
	return key_lower == base


func _spawn_cost_key_matches(monster_hint: String, cost_key_lower: String) -> bool:
	if cost_key_lower == "cost_per_monster":
		return true
	var slug_hint := monster_hint.strip_edges().to_lower().replace(" ", "_")
	var dot_prefix := "cost_per_monster."
	var us_prefix := "cost_per_monster_"
	if cost_key_lower.begins_with(dot_prefix):
		var slug := cost_key_lower.substr(dot_prefix.length())
		if slug_hint.is_empty():
			return true
		return slug == slug_hint
	if cost_key_lower.begins_with(us_prefix):
		var slug2 := cost_key_lower.substr(us_prefix.length())
		if slug_hint.is_empty():
			return true
		return slug2 == slug_hint
	return false
