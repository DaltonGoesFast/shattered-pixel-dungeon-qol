extends RefCounted
class_name SummonMarchRegistry

const MONSTERS: PackedStringArray = [
	"rat", "albino", "snake", "gnoll", "crab", "slime", "swarm", "thief",
	"skeleton", "bat", "brute", "shaman", "spinner", "dm100", "guard",
	"necromancer", "ghoul", "elemental", "warlock", "monk", "golem",
	"succubus", "eye", "scorpio",
]


static func is_valid(monster: String) -> bool:
	return MONSTERS.has(monster.to_lower())


static func display_name(monster: String) -> String:
	var id := monster.to_lower()
	if id == "dm100":
		return "DM-100"
	return id.capitalize()
