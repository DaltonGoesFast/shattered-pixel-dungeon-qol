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
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Adrenaline;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Barrier;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Blindness;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.ChampionEnemy;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.ChatSpawned;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Cripple;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Daze;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Haste;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Healing;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Invisibility;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Levitation;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.MindVision;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Paralysis;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Recharging;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Roots;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Slow;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.SpawnScaled;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Vulnerable;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Weakness;
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
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfTransmutation;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.ExoticScroll;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.AlarmTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.BlazingTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.BurningTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.ChillingTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.ConfusionTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.CorrosionTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.CursingTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.DisarmingTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.DisintegrationTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.DistortionTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.ExplosiveTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.FlashingTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.FlockTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.FrostTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.GatewayTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.GeyserTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.GnollRockfallTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.GrimTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.GuardianTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.GrippingTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.OozeTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.PitfallTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.PoisonDartTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.RockfallTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.ShockingTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.StormTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.SummoningTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.TeleportationTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.TenguDartTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.ToxicTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.WarpingTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.WeakeningTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.WornDartTrap;
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

		// Paralysis when spawning a monster outside its native biome. Max 3 turns (Halls), then 2 (City), 1 (Caves), 0 (Prison).
		// Reduced by 1 when spawning in the Prison.
		// Use affect+spend (not prolong) so we SET duration; prolong would extend existing and stack across duplicate spawns.
		Integer nativeDepthForParalysis = NATIVE_DEPTH.get(monsterName);
		if (nativeDepthForParalysis != null) {
			int nativeRegion = (nativeDepthForParalysis - 1) / 5;  // 0=sewers, 1=prison, 2=caves, 3=city, 4=halls
			int currentRegion = (Dungeon.depth - 1) / 5;
			if (currentRegion < nativeRegion) {
				int turns = Math.max(0, nativeRegion - 1);  // Halls=3, City=2, Caves=1, Prison=0
				if (currentRegion == 1) turns = Math.max(0, turns - 1);  // reduce by 1 in prison
				if (turns > 0) {
					Buff.detach(mob, Paralysis.class);
					// Pass turns-1: FlavourBuff.visualcooldown adds +1 for display, so we subtract to match
					float duration = Math.max(1f, turns - 1f);
					Buff.affect(mob, Paralysis.class, duration);
				}
			}
		}

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

	@SuppressWarnings("unchecked")
	private static final Class<? extends ChampionEnemy>[] CHAMPION_TYPES = new Class[]{
			ChampionEnemy.Blazing.class, ChampionEnemy.Projecting.class, ChampionEnemy.AntiMagic.class,
			ChampionEnemy.Giant.class, ChampionEnemy.Blessed.class, ChampionEnemy.Growing.class
	};

	/** Spawn a champion version of the given monster. Same placement/scaling as handleSpawn; cost is 2× base (handled by overlay). */
	public static String handleSpawnChampion(String monsterName, String username) {
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
		Buff.affect(mob, Random.element(CHAMPION_TYPES));

		Integer nativeDepthForParalysis = NATIVE_DEPTH.get(monsterName);
		if (nativeDepthForParalysis != null) {
			int nativeRegion = (nativeDepthForParalysis - 1) / 5;
			int currentRegion = (Dungeon.depth - 1) / 5;
			if (currentRegion < nativeRegion) {
				int turns = Math.max(0, nativeRegion - 1);
				if (currentRegion == 1) turns = Math.max(0, turns - 1);
				if (turns > 0) {
					Buff.detach(mob, Paralysis.class);
					float duration = Math.max(1f, turns - 1f);
					Buff.affect(mob, Paralysis.class, duration);
				}
			}
		}

		Integer nativeDepth = NATIVE_DEPTH.get(monsterName);
		if (nativeDepth != null && Dungeon.depth < nativeDepth) {
			int currentRegion = (Dungeon.depth - 1) / 5;
			int nativeRegion = (nativeDepth - 1) / 5;
			if (currentRegion < nativeRegion) {
				float scale = Math.max(MIN_HP_SCALE, (float) Dungeon.depth / nativeDepth);
				int newHT = Math.max(1, Math.round(mob.HT * scale));
				int newHP = Math.max(1, Math.round(mob.HP * scale));
				mob.HT = newHT;
				mob.HP = newHP;
				SpawnScaled.affect(mob, scale);
			}
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
		GLog.p(Messages.get(StreamingCommandHandler.class, "chat_champion_spawned"), chatter, monsterName);
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

	// All traps that can be chosen for chat spawn. Remove classes from TRAP_BLACKLIST to allow them.
	// Full list (33): Alarm, Blazing, Burning, Chilling, Confusion, Corrosion, Cursing, Disarming,
	// Disintegration, Distortion, Explosive, Flashing, Flock, Frost, Gateway, Geyser, GnollRockfall,
	// Grim, Guardian, Gripping, Ooze, Pitfall, PoisonDart, Rockfall, Shocking, Storm, Summoning,
	// Teleportation, TenguDart, Toxic, Warping, Weakening, WornDart.
	@SuppressWarnings("unchecked")
	private static final Class<? extends Trap>[] CHAT_TRAP_POOL = new Class[]{
			AlarmTrap.class, BlazingTrap.class, BurningTrap.class, ChillingTrap.class, ConfusionTrap.class,
			CorrosionTrap.class, CursingTrap.class, DisarmingTrap.class, DisintegrationTrap.class, DistortionTrap.class,
			ExplosiveTrap.class, FlashingTrap.class, FlockTrap.class, FrostTrap.class, GatewayTrap.class,
			GeyserTrap.class, GnollRockfallTrap.class, GrimTrap.class, GuardianTrap.class, GrippingTrap.class,
			OozeTrap.class, PitfallTrap.class, PoisonDartTrap.class, RockfallTrap.class, ShockingTrap.class,
			StormTrap.class, SummoningTrap.class, TeleportationTrap.class, TenguDartTrap.class, ToxicTrap.class,
			WarpingTrap.class, WeakeningTrap.class, WornDartTrap.class
	};

	/** Default blacklist: instant death, drop to next depth, or very high damage. Add/remove as desired. */
	private static final HashSet<Class<? extends Trap>> TRAP_BLACKLIST = new HashSet<>();
	static {
		TRAP_BLACKLIST.add(GrimTrap.class);           // instant death
		TRAP_BLACKLIST.add(DisintegrationTrap.class); // instant death
		TRAP_BLACKLIST.add(PitfallTrap.class);       // drop to next depth
		TRAP_BLACKLIST.add(ExplosiveTrap.class);     // high damage
		TRAP_BLACKLIST.add(RockfallTrap.class);      // AOE damage
		TRAP_BLACKLIST.add(GnollRockfallTrap.class);// AOE damage
	}

	/** Spawn a random (non-blacklisted) trap near the hero, same placement logic as gold. Returns trap name on success, error message on failure. */
	public static String handleSpawnTrap(String username) {
		if (Dungeon.hero == null || Dungeon.level == null)
			return "ERR:Not in an active run (title/menu)";
		if (!(ShatteredPixelDungeon.scene() instanceof GameScene))
			return "ERR:Not in an active run (title/menu)";
		if (!Dungeon.hero.isAlive())
			return "ERR:Hero is dead";

		ArrayList<Class<? extends Trap>> allowed = new ArrayList<>();
		for (Class<? extends Trap> c : CHAT_TRAP_POOL) {
			if (!TRAP_BLACKLIST.contains(c)) allowed.add(c);
		}
		if (allowed.isEmpty())
			return "ERR:All traps are blacklisted";

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
			if (Dungeon.level.traps.get(p) != null) continue;
			candidates.add(p);
		}
		if (candidates.isEmpty())
			return "ERR:No space to place trap (hero surrounded or no valid tiles)";

		int cell = Random.element(candidates);
		Class<? extends Trap> trapClass = Random.element(allowed);
		Trap trap = Reflection.newInstance(trapClass);
		trap.reveal();

		Level.set(cell, Terrain.TRAP);
		Dungeon.level.setTrap(trap, cell);

		String trapName = trap.name();
		String chatter = (username != null && !username.isEmpty()) ? username : "Chat";
		GLog.w(Messages.get(StreamingCommandHandler.class, "chat_trap"), chatter, trapName);
		return trapName;
	}

	/** Transmute a random transmutable item (bag or equipped). Returns new item name on success, error on failure. */
	public static String handleTransmute(String username) {
		if (Dungeon.hero == null || Dungeon.level == null)
			return "ERR:Not in an active run (title/menu)";
		if (!(ShatteredPixelDungeon.scene() instanceof GameScene))
			return "ERR:Not in an active run (title/menu)";
		if (!Dungeon.hero.isAlive())
			return "ERR:Hero is dead";

		ScrollOfTransmutation.TransmuteResult tr = ScrollOfTransmutation.transmuteOneRandom(Dungeon.hero);
		if (tr == null)
			return "ERR:No transmutable item (need at least one weapon, armor, ring, artifact, potion, scroll, wand, seed, runestone, or trinket)";

		String resultName = tr.result.name();
		String originalName = tr.originalName;
		String chatter = (username != null && !username.isEmpty()) ? username : "Chat";
		GLog.p(Messages.get(StreamingCommandHandler.class, "chat_transmuted"), chatter, originalName, resultName);
		return resultName;
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
		Scroll.chatScrollNoKill = true;
		try {
			scroll.execute(Dungeon.hero, Scroll.AC_READ);
		} finally {
			Scroll.chatScrollNoKill = false;
		}
		Dungeon.hero.spend(-1f);  // Chat scroll: no turn cost
		Talent.onArtifactUsed(Dungeon.hero);

		String scrollName = scroll.trueName();
		String chatter = (username != null && !username.isEmpty()) ? username : "Chat";
		GLog.p(Messages.get(StreamingCommandHandler.class, "chat_scroll"), chatter, scrollName);
		return scrollName;
	}

	/** Buffs for chat !buff (random choice). Excludes Paralysis, Burning, Poison, Awareness. */
	private static final Class<? extends Buff>[] CHAT_BUFFS = new Class[]{
			Haste.class, Adrenaline.class, Invisibility.class, Levitation.class,
			Barrier.class, Healing.class, Recharging.class, MindVision.class
	};

	/** Debuffs for chat !debuff (random choice). Excludes Paralysis, Burning, Poison. */
	private static final Class<? extends Buff>[] CHAT_DEBUFFS = new Class[]{
			Blindness.class, Weakness.class, Slow.class, Cripple.class,
			Roots.class, Daze.class, Vulnerable.class
	};

	/** Apply a random buff to the hero. Returns buff name on success, ERR:... on failure. */
	public static String handleChatBuff(String username) {
		if (Dungeon.hero == null || Dungeon.level == null)
			return "ERR:Not in an active run (title/menu)";
		if (!(ShatteredPixelDungeon.scene() instanceof GameScene))
			return "ERR:Not in an active run (title/menu)";
		if (!Dungeon.hero.isAlive())
			return "ERR:Hero is dead";

		Class<? extends Buff> buffClass = Random.element(CHAT_BUFFS);
		String buffName = Messages.titleCase(buffClass.getSimpleName());

		if (buffClass == Healing.class) {
			int totalHeal = Math.max(1, Math.round(Dungeon.hero.HT * 0.1f));
			int perTick = Math.max(1, totalHeal / 10);
			Healing h = Buff.affect(Dungeon.hero, Healing.class);
			h.setHeal(totalHeal, 0, perTick);
		} else if (buffClass == Barrier.class) {
			int shield = Math.max(1, Math.round(Dungeon.hero.HT * 0.1f));
			Barrier b = Buff.affect(Dungeon.hero, Barrier.class);
			b.setShield(shield);
		} else if (buffClass == Haste.class) {
			Buff.affect(Dungeon.hero, Haste.class, Haste.DURATION);
		} else if (buffClass == Adrenaline.class) {
			Buff.affect(Dungeon.hero, Adrenaline.class, Adrenaline.DURATION);
		} else if (buffClass == Invisibility.class) {
			Buff.affect(Dungeon.hero, Invisibility.class, Invisibility.DURATION);
		} else if (buffClass == Levitation.class) {
			Buff.affect(Dungeon.hero, Levitation.class, Levitation.DURATION);
		} else if (buffClass == Recharging.class) {
			Buff.affect(Dungeon.hero, Recharging.class, 8f);
		} else if (buffClass == MindVision.class) {
			Buff.affect(Dungeon.hero, MindVision.class, MindVision.DURATION);
		}

		String chatter = (username != null && !username.isEmpty()) ? username : "Chat";
		GLog.p(Messages.get(StreamingCommandHandler.class, "chat_buff"), chatter, buffName);
		return buffName;
	}

	/** Apply a random debuff to the hero. Returns debuff name on success, ERR:... on failure. */
	public static String handleChatDebuff(String username) {
		if (Dungeon.hero == null || Dungeon.level == null)
			return "ERR:Not in an active run (title/menu)";
		if (!(ShatteredPixelDungeon.scene() instanceof GameScene))
			return "ERR:Not in an active run (title/menu)";
		if (!Dungeon.hero.isAlive())
			return "ERR:Hero is dead";

		Class<? extends Buff> debuffClass;
		ArrayList<Class<? extends Buff>> pool = new ArrayList<>(java.util.Arrays.asList(CHAT_DEBUFFS));
		do {
			if (pool.isEmpty())
				return "ERR:No debuff could be applied (e.g. Roots fails when flying)";
			debuffClass = Random.element(pool);
			pool.remove(debuffClass);
		} while (debuffClass == Roots.class && Dungeon.hero.flying);

		String debuffName = Messages.titleCase(debuffClass.getSimpleName());

		if (debuffClass == Blindness.class) {
			Buff.affect(Dungeon.hero, Blindness.class, Blindness.DURATION);
		} else if (debuffClass == Weakness.class) {
			Buff.affect(Dungeon.hero, Weakness.class, Weakness.DURATION);
		} else if (debuffClass == Slow.class) {
			Buff.affect(Dungeon.hero, Slow.class, Slow.DURATION);
		} else if (debuffClass == Cripple.class) {
			Buff.affect(Dungeon.hero, Cripple.class, Cripple.DURATION);
		} else if (debuffClass == Roots.class) {
			Buff.affect(Dungeon.hero, Roots.class, Roots.DURATION);
		} else if (debuffClass == Daze.class) {
			Buff.affect(Dungeon.hero, Daze.class, Daze.DURATION);
		} else if (debuffClass == Vulnerable.class) {
			Buff.affect(Dungeon.hero, Vulnerable.class, Vulnerable.DURATION);
		}

		String chatter = (username != null && !username.isEmpty()) ? username : "Chat";
		GLog.n(Messages.get(StreamingCommandHandler.class, "chat_debuff"), chatter, debuffName);
		return debuffName;
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
