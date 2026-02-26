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
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.ChatSpawned;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.SpawnScaled;
import com.shatteredpixel.shatteredpixeldungeon.items.EquipableItem;
import com.shatteredpixel.shatteredpixeldungeon.items.Gold;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.KindOfWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.ChaoticCenser;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.CursedWand;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfMagicMissile;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MagesStaff;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.MissileWeapon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import com.shatteredpixel.shatteredpixeldungeon.items.Generator;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfIdentify;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfMagicMapping;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfRemoveCurse;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfTransmutation;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfTeleportation;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.ExoticScroll;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.watabou.utils.BArray;
import com.watabou.utils.Callback;
import com.watabou.utils.PathFinder;
import com.watabou.utils.Random;
import com.watabou.utils.Reflection;

import java.util.HashMap;
import java.util.HashSet;
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

	/** Called from main thread via Gdx.app.postRunnable. Returns null on success, error message on failure. */
	public static String handleSpawn(String monsterName, String username) {
		if (Dungeon.hero == null || Dungeon.level == null)
			return "Not in an active run (title/menu)";
		if (!(ShatteredPixelDungeon.scene() instanceof GameScene))
			return "Not in an active run (title/menu)";
		if (!Dungeon.hero.isAlive())
			return "Hero is dead";

		Class<? extends Mob> mobClass = "elemental".equals(monsterName)
				? Elemental.random()
				: mobClassForName(monsterName);
		if (mobClass == null)
			return "Unknown monster";

		Mob mob = Reflection.newInstance(mobClass);
		if (mob == null)
			return "Failed to create monster";

		Buff.affect(mob, ChatSpawned.class);

		// Scale down HP, damage, and armor only when spawning a monster from a LATER biome in an EARLIER biome.
		// If the monster is in its native biome (e.g. crab on floor 1 = both sewers), never scale down.
		Integer nativeDepth = NATIVE_DEPTH.get(monsterName);
		if (nativeDepth != null && Dungeon.depth < nativeDepth) {
			int currentRegion = (Dungeon.depth - 1) / 5;  // 0=sewers, 1=prison, 2=caves, 3=city, 4=demon
			int nativeRegion = (nativeDepth - 1) / 5;
			if (currentRegion < nativeRegion) {
				// Different biome (e.g. skeleton in sewers) — scale down
				float scale = Math.max(MIN_HP_SCALE, (float) Dungeon.depth / nativeDepth);
				int newHT = Math.max(1, Math.round(mob.HT * scale));
				int newHP = Math.max(1, Math.round(mob.HP * scale));
				mob.HT = newHT;
				mob.HP = newHP;
				SpawnScaled.affect(mob, scale);
			}
			// Same biome (e.g. crab on floor 1, both sewers) — no scaling
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
		if (candidates.isEmpty())
			return "No space to spawn (hero surrounded or no valid tiles)";

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
		return null;
	}

	/** Drop gold near the hero. Returns null on success, error message on failure. */
	public static String handleDropGold(int amount, String username) {
		if (Dungeon.hero == null || Dungeon.level == null)
			return "Not in an active run (title/menu)";
		if (!(ShatteredPixelDungeon.scene() instanceof GameScene))
			return "Not in an active run (title/menu)";
		if (!Dungeon.hero.isAlive())
			return "Hero is dead";
		if (amount < 1 || amount > 100)
			return "Invalid amount";

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
		if (candidates.isEmpty())
			return "No space to drop gold (hero surrounded)";

		int cell = Random.element(candidates);
		Dungeon.level.drop(new Gold(amount), cell).sprite.drop();

		String chatter = (username != null && !username.isEmpty()) ? username : "Chat";
		GLog.h(Messages.get(StreamingCommandHandler.class, "chat_gold_dropped"), chatter, amount);
		return null;
	}

	/** Curse an equipped item in the given slot. Returns item name on success, error message on failure. */
	public static String handleCurse(String slot, String username) {
		if (Dungeon.hero == null || Dungeon.level == null)
			return "ERR:Not in an active run (title/menu)";
		if (!(ShatteredPixelDungeon.scene() instanceof GameScene))
			return "ERR:Not in an active run (title/menu)";
		if (!Dungeon.hero.isAlive())
			return "ERR:Hero is dead";

		Item item = null;
		String slotName = null;
		if (slot == null) return "ERR:Missing slot";
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
				return "ERR:Invalid slot";
		}

		if (item == null)
			return "ERR:No item in " + slotName + " slot";
		if (item.cursed && item.cursedKnown)
			return "ERR:Item in " + slotName + " is already cursed";

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

	/** Spawn random gas (Chaotic Censer at level +3). Returns gas name on success, error message on failure. */
	public static String handleSpawnGas(String username) {
		if (Dungeon.hero == null || Dungeon.level == null)
			return "ERR:Not in an active run (title/menu)";
		if (!(ShatteredPixelDungeon.scene() instanceof GameScene))
			return "ERR:Not in an active run (title/menu)";
		if (!Dungeon.hero.isAlive())
			return "ERR:Hero is dead";

		String gasName = ChaoticCenser.spawnGasForChat();
		if (gasName == null)
			return "ERR:No valid cell to spawn gas (need visible tiles 2-6 from hero)";

		String chatter = (username != null && !username.isEmpty()) ? username : "Chat";
		GLog.w(Messages.get(StreamingCommandHandler.class, "chat_gas"), chatter, Messages.titleCase(gasName));
		return gasName;
	}

	/** Cursed wand effect classes excluded from chat (AbortRetryFail, Explosion, FireBall, ForestFire). */
	private static final HashSet<Class<? extends CursedWand.CursedEffect>> WAND_EXCLUDED = new HashSet<>();
	static {
		WAND_EXCLUDED.add(CursedWand.AbortRetryFail.class);
		WAND_EXCLUDED.add(CursedWand.Explosion.class);
		WAND_EXCLUDED.add(CursedWand.FireBall.class);
		WAND_EXCLUDED.add(CursedWand.ForestFire.class);
	}

	/** Callback for async cursed wand result. */
	public interface CursedWandResultCallback {
		void onResult(boolean success, String effectName, int rarity, String error);
	}

	/** Trigger a cursed wand effect (excluding AbortRetryFail, Explosion, FireBall, ForestFire). Async.
	 * @param tier -1 for random, 0=common, 1=uncommon, 2=rare, 3=very_rare */
	public static void handleCursedWand(String username, int tier, CursedWandResultCallback callback) {
		if (Dungeon.hero == null || Dungeon.level == null) {
			callback.onResult(false, null, 0, "Not in an active run (title/menu)");
			return;
		}
		if (!(ShatteredPixelDungeon.scene() instanceof GameScene)) {
			callback.onResult(false, null, 0, "Not in an active run (title/menu)");
			return;
		}
		if (!Dungeon.hero.isAlive()) {
			callback.onResult(false, null, 0, "Hero is dead");
			return;
		}

		// Find target cell 2-6 tiles from hero (like gas)
		PathFinder.buildDistanceMap(Dungeon.hero.pos, BArray.not(Dungeon.level.solid, null), 6);
		ArrayList<Integer> candidates = new ArrayList<>();
		for (int i = 0; i < Dungeon.level.length(); i++) {
			if (Dungeon.level.heroFOV[i] && PathFinder.distance[i] != Integer.MAX_VALUE) {
				if (PathFinder.distance[i] >= 2 && PathFinder.distance[i] <= 6) {
					candidates.add(i);
				}
			}
		}
		if (candidates.isEmpty()) {
			callback.onResult(false, null, 0, "No valid target cell (need visible tiles 2-6 from hero)");
			return;
		}

		int targetCell = Random.element(candidates);
		Wand wand = new WandOfMagicMissile();
		wand.level(Dungeon.scalingDepth() / 5);
		Ballistica bolt = new Ballistica(Dungeon.hero.pos, targetCell, Ballistica.MAGIC_BOLT);
		boolean positiveOnly = Random.Float() < com.shatteredpixel.shatteredpixeldungeon.items.trinkets.WondrousResin.positiveCurseEffectChance();

		int[] outRarity = new int[1];
		CursedWand.CursedEffect effect = CursedWand.randomValidEffectExcluding(
				wand, Dungeon.hero, bolt, positiveOnly, WAND_EXCLUDED, outRarity, tier);

		String effectName = effect.getClass().getSimpleName();
		int rarity = outRarity[0];

		effect.FX(wand, Dungeon.hero, bolt, new Callback() {
			@Override
			public void call() {
				effect.effect(wand, Dungeon.hero, bolt, positiveOnly);
				String chatter = (username != null && !username.isEmpty()) ? username : "Chat";
				GLog.w(Messages.get(StreamingCommandHandler.class, "chat_wand"), chatter, effectName);
				callback.onResult(true, effectName, rarity, null);
			}
		});
	}

	/** Use a random scroll like +10 Unstable Spellbook. Returns scroll name on success, error message on failure. */
	public static String handleRandomScroll(String username) {
		if (Dungeon.hero == null || Dungeon.level == null)
			return "ERR:Not in an active run (title/menu)";
		if (!(ShatteredPixelDungeon.scene() instanceof GameScene))
			return "ERR:Not in an active run (title/menu)";
		if (!Dungeon.hero.isAlive())
			return "ERR:Hero is dead";
		if (Dungeon.hero.buff(com.shatteredpixel.shatteredpixeldungeon.actors.buffs.MagicImmune.class) != null)
			return "ERR:Magic immune";
		if (Dungeon.hero.buff(com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Blindness.class) != null)
			return "ERR:Blinded";

		Scroll scroll;
		do {
			scroll = (Scroll) Generator.randomUsingDefaults(Generator.Category.SCROLL);
		} while (scroll == null
				|| ((scroll instanceof ScrollOfIdentify || scroll instanceof ScrollOfRemoveCurse
						|| scroll instanceof ScrollOfMagicMapping) && Random.Int(2) == 0)
				|| (scroll instanceof ScrollOfTransmutation));

		// 50% chance for exotic version (like +10 spellbook empowered option)
		if (ExoticScroll.regToExo.containsKey(scroll.getClass()) && Random.Int(2) == 0) {
			scroll = (Scroll) Reflection.newInstance(ExoticScroll.regToExo.get(scroll.getClass()));
		}

		scroll.anonymize();
		scroll.talentChance = 0;
		scroll.execute(Dungeon.hero, Scroll.AC_READ);
		Talent.onArtifactUsed(Dungeon.hero);

		String scrollName = scroll.trueName();
		String chatter = (username != null && !username.isEmpty()) ? username : "Chat";
		GLog.p(Messages.get(StreamingCommandHandler.class, "chat_scroll"), chatter, scrollName);
		return scrollName;
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
