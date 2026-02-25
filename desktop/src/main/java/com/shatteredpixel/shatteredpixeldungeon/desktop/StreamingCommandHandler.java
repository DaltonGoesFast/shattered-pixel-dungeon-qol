/*
 * Pixel Dungeon
 * Copyright (C) 2012-2015 Oleg Dolya
 *
 * Shattered Pixel Dungeon
 * Copyright (C) 2014-2025 Evan Debenham
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package com.shatteredpixel.shatteredpixeldungeon.desktop;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Albino;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Bat;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Brute;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Crab;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.DM100;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Elemental;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Eye;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Ghoul;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Gnoll;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Golem;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Guard;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Monk;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Necromancer;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Rat;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Scorpio;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Shaman;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Skeleton;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Slime;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Snake;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Spinner;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Succubus;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Swarm;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Thief;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Warlock;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.SpawnScaled;
import com.shatteredpixel.shatteredpixeldungeon.items.EquipableItem;
import com.shatteredpixel.shatteredpixeldungeon.items.Gold;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.KindOfWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.ChaoticCenser;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MagesStaff;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.MissileWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfTeleportation;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.watabou.utils.PathFinder;
import com.watabou.utils.Random;
import com.watabou.utils.Reflection;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;

/**
 * Handles spawn commands received via the streaming WebSocket (e.g. from chat).
 * Phase 2: full monster list. Monsters from later areas are scaled down when spawned early.
 */
public final class StreamingCommandHandler {

	private static final float SPAWN_DELAY = 0f;  // 0 = spawn immediately (was 2f)
	private static final int SPAWN_RADIUS = 4;  // tiles away from hero (1–4)
	private static final float MIN_HP_SCALE = 0.25f;  // minimum HP when spawning late-game mobs early

	/** Earliest depth each monster appears (from MobSpawner). Used to scale HP when spawned in earlier areas. */
	private static final Map<String, Integer> NATIVE_DEPTH = new HashMap<>();
	static {
		NATIVE_DEPTH.put("rat", 1);       NATIVE_DEPTH.put("albino", 1);   NATIVE_DEPTH.put("snake", 1);
		NATIVE_DEPTH.put("gnoll", 2);     NATIVE_DEPTH.put("crab", 3);     NATIVE_DEPTH.put("slime", 4);
		NATIVE_DEPTH.put("swarm", 3);     NATIVE_DEPTH.put("thief", 4);    NATIVE_DEPTH.put("skeleton", 6);
		NATIVE_DEPTH.put("dm100", 7);     NATIVE_DEPTH.put("guard", 7);   NATIVE_DEPTH.put("necromancer", 8);
		NATIVE_DEPTH.put("bat", 9);       NATIVE_DEPTH.put("brute", 11);  NATIVE_DEPTH.put("shaman", 11);
		NATIVE_DEPTH.put("spinner", 12);  NATIVE_DEPTH.put("ghoul", 14);  NATIVE_DEPTH.put("elemental", 16);
		NATIVE_DEPTH.put("warlock", 16); NATIVE_DEPTH.put("monk", 17);    NATIVE_DEPTH.put("golem", 18);
		NATIVE_DEPTH.put("succubus", 19); NATIVE_DEPTH.put("eye", 21);    NATIVE_DEPTH.put("scorpio", 23);
	}

	/** Called from main thread via Gdx.app.postRunnable. Returns true if spawn succeeded, false if no valid space. */
	public static boolean handleSpawn(String monsterName, String username) {
		if (Dungeon.hero == null || Dungeon.level == null) return false;
		if (!(ShatteredPixelDungeon.scene() instanceof GameScene)) return false;
		if (!Dungeon.hero.isAlive()) return false;

		Class<? extends Mob> mobClass = "elemental".equals(monsterName)
				? Elemental.random()
				: mobClassForName(monsterName);
		if (mobClass == null) return false;

		Mob mob = Reflection.newInstance(mobClass);
		if (mob == null) return false;

		// Scale down HP, damage, and armor when spawning a late-game monster in an earlier area
		Integer nativeDepth = NATIVE_DEPTH.get(monsterName);
		if (nativeDepth != null && Dungeon.depth < nativeDepth) {
			float scale = Math.max(MIN_HP_SCALE, (float) Dungeon.depth / nativeDepth);
			int newHT = Math.max(1, Math.round(mob.HT * scale));
			int newHP = Math.max(1, Math.round(mob.HP * scale));
			mob.HT = newHT;
			mob.HP = newHP;
			SpawnScaled.affect(mob, scale);
		}

		int heroPos = Dungeon.hero.pos;
		boolean[] spawnPassable = new boolean[Dungeon.level.length()];
		for (int i = 0; i < spawnPassable.length; i++) {
			spawnPassable[i] = Dungeon.level.passable[i] || Dungeon.level.avoid[i];
		}
		PathFinder.buildDistanceMap(heroPos, spawnPassable, SPAWN_RADIUS);

		ArrayList<Integer> candidates = new ArrayList<>();
		for (int p = 0; p < Dungeon.level.length(); p++) {
			int d = PathFinder.distance[p];
			if (d < 1 || d > SPAWN_RADIUS) continue;
			if (Actor.findChar(p) != null) continue;
			if (!Dungeon.level.passable[p] && !Dungeon.level.avoid[p]) continue;
			if (Char.hasProp(mob, Char.Property.LARGE) && !Dungeon.level.openSpace[p]) continue;
			candidates.add(p);
		}
		if (candidates.isEmpty()) return false;

		int cell = Random.element(candidates);
		if (mob.state != mob.PASSIVE) {
			mob.state = mob.WANDERING;
		}
		mob.pos = cell;
		GameScene.add(mob, SPAWN_DELAY);
		ScrollOfTeleportation.appear(mob, cell);
		Dungeon.level.occupyCell(mob);

		String chatter = (username != null && !username.isEmpty()) ? username : "Chat";
		GLog.p(Messages.get(StreamingCommandHandler.class, "chat_spawned"), chatter, monsterName);
		return true;
	}

	/** Drop gold near the hero. Returns true if dropped successfully. */
	public static boolean handleDropGold(int amount, String username) {
		if (Dungeon.hero == null || Dungeon.level == null) return false;
		if (!(ShatteredPixelDungeon.scene() instanceof GameScene)) return false;
		if (!Dungeon.hero.isAlive()) return false;
		if (amount < 1 || amount > 100) return false;

		int heroPos = Dungeon.hero.pos;
		boolean[] spawnPassable = new boolean[Dungeon.level.length()];
		for (int i = 0; i < spawnPassable.length; i++) {
			spawnPassable[i] = Dungeon.level.passable[i] || Dungeon.level.avoid[i];
		}
		PathFinder.buildDistanceMap(heroPos, spawnPassable, SPAWN_RADIUS);

		ArrayList<Integer> candidates = new ArrayList<>();
		for (int p = 0; p < Dungeon.level.length(); p++) {
			int d = PathFinder.distance[p];
			if (d < 1 || d > SPAWN_RADIUS) continue;
			if (Actor.findChar(p) != null) continue;
			if (!Dungeon.level.passable[p] && !Dungeon.level.avoid[p]) continue;
			candidates.add(p);
		}
		if (candidates.isEmpty()) return false;

		int cell = Random.element(candidates);
		Dungeon.level.drop(new Gold(amount), cell).sprite.drop();

		String chatter = (username != null && !username.isEmpty()) ? username : "Chat";
		GLog.h(Messages.get(StreamingCommandHandler.class, "chat_gold_dropped"), chatter, amount);
		return true;
	}

	/** Curse an equipped item in the given slot. Returns item name on success, null on failure. */
	public static String handleCurse(String slot, String username) {
		if (Dungeon.hero == null || Dungeon.level == null) return null;
		if (!(ShatteredPixelDungeon.scene() instanceof GameScene)) return null;
		if (!Dungeon.hero.isAlive()) return null;

		Item item = null;
		String slotName = null;
		if (slot == null) return null;
		switch (slot.toLowerCase()) {
			case "weapon":
				KindOfWeapon wep = Dungeon.hero.belongings.weapon();
				if (wep instanceof Weapon && !(wep instanceof MagesStaff) && !(wep instanceof MissileWeapon))
					item = (Item) wep;
				slotName = "weapon";
				break;
			case "armor":
				item = Dungeon.hero.belongings.armor();
				slotName = "armor";
				break;
			case "ring":
				item = Dungeon.hero.belongings.ring();
				slotName = "ring";
				break;
			case "artifact":
				item = Dungeon.hero.belongings.artifact();
				slotName = "artifact";
				break;
			case "misc":
				item = Dungeon.hero.belongings.misc();
				slotName = "misc";
				break;
			default:
				return null;
		}

		if (item == null) return null;
		if (item.cursed && item.cursedKnown) return null;

		item.cursed = item.cursedKnown = true;
		if (item instanceof Weapon) {
			Weapon w = (Weapon) item;
			w.enchant(Weapon.Enchantment.randomCurse());
		}
		if (item instanceof Armor) {
			Armor a = (Armor) item;
			a.inscribe(Armor.Glyph.randomCurse());
		}

		EquipableItem.equipCursed(Dungeon.hero);
		String chatter = (username != null && !username.isEmpty()) ? username : "Chat";
		String itemName = item.name();
		GLog.n(Messages.get(StreamingCommandHandler.class, "chat_curse"), chatter, slotName);
		return itemName;
	}

	/** Spawn random gas (Chaotic Censer at level +3). Returns gas name on success, null on failure. */
	public static String handleSpawnGas(String username) {
		if (Dungeon.hero == null || Dungeon.level == null) return null;
		if (!(ShatteredPixelDungeon.scene() instanceof GameScene)) return null;
		if (!Dungeon.hero.isAlive()) return null;

		String gasName = ChaoticCenser.spawnGasForChat();
		if (gasName == null) return null;

		String chatter = (username != null && !username.isEmpty()) ? username : "Chat";
		GLog.w(Messages.get(StreamingCommandHandler.class, "chat_gas"), chatter, Messages.titleCase(gasName));
		return gasName;
	}

	/** Phase 2: full monster list. Returns null for unknown. */
	private static Class<? extends Mob> mobClassForName(String name) {
		if (name == null) return null;
		switch (name) {
			case "rat":       return Rat.class;
			case "albino":    return Albino.class;
			case "snake":     return Snake.class;
			case "gnoll":     return Gnoll.class;
			case "crab":      return Crab.class;
			case "slime":     return Slime.class;
			case "swarm":     return Swarm.class;
			case "thief":     return Thief.class;
			case "skeleton":  return Skeleton.class;
			case "bat":       return Bat.class;
			case "brute":     return Brute.class;
			case "shaman":    return Shaman.class;
			case "spinner":   return Spinner.class;
			case "dm100":     return DM100.class;
			case "guard":     return Guard.class;
			case "necromancer": return Necromancer.class;
			case "ghoul":     return Ghoul.class;
			case "warlock":   return Warlock.class;
			case "monk":      return Monk.class;
			case "golem":     return Golem.class;
			case "succubus":  return Succubus.class;
			case "eye":       return Eye.class;
			case "scorpio":   return Scorpio.class;
			default:         return null;
		}
	}
}
