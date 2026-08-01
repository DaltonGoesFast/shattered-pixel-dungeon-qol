extends Node2D

const _UnitScene: PackedScene = preload("res://scenes/summon_march_unit.tscn")


func _ready() -> void:
	SummonPollService.summon_received.connect(_on_summon_received)


func _on_summon_received(event: Dictionary) -> void:
	if not CompanionConfig.summon_march_enabled:
		return
	var max_n: int = clampi(CompanionConfig.summon_march_max_concurrent, 1, 32)
	# Drop oldest finished-bound units when over capacity
	while get_child_count() >= max_n:
		var oldest: Node = get_child(0)
		if oldest == null:
			break
		oldest.queue_free()
		# Count includes nodes pending free; remove from tree now so next spawn can proceed
		remove_child(oldest)
	var unit: Node2D = _UnitScene.instantiate()
	add_child(unit)

	var viewport_size := get_viewport_rect().size
	if unit.has_method("setup"):
		unit.setup(event, viewport_size)
