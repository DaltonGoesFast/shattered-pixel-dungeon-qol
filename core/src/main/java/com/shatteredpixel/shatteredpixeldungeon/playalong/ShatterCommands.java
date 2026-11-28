/*
 * Pixel Dungeon
 * Copyright (C) 2012-2015 Oleg Dolya
 *
 * Shattered Pixel Dungeon
 * Copyright (C) 2014-2026 Evan Debenham
 *
 * This program is free software: you can redistribute it and/or modify
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
 * along with this program. If not, see <http://www.gnu.org/licenses/>
 */

package com.shatteredpixel.shatteredpixeldungeon.playalong;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.Statistics;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.ChampionEnemy;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.ShatterSpawned;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.MobSpawner;
import com.shatteredpixel.shatteredpixeldungeon.desktop.StreamingCommandHandler;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.ChatCommandPayload;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.watabou.utils.Bundle;
import com.watabou.utils.PathFinder;
import com.watabou.utils.Random;
import com.watabou.utils.Reflection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Opt-in Play Along PvP: kill charges spent from the in-game deck hit every other racer.
 * Only for rooms hosted outside DaltonGoesFast.
 */
public final class ShatterCommands {

	public static final String CMD_TRAP = "trap";
	public static final String CMD_DEBUFF = "debuff";
	public static final String CMD_SABOTAGE = "sabotage";
	public static final String CMD_SPAWN = "spawn";
	public static final String CMD_GAS = "gas";
	public static final String CMD_BOMB = "bomb";
	public static final String CMD_HEX = "hex";
	public static final String CMD_DEGRADE = "degrade";
	public static final String CMD_CURSE = "curse";
	public static final String CMD_CHAMPION = "champion";

	private static final String[] ORDERED_CMDS = {
			CMD_TRAP, CMD_DEBUFF, CMD_SABOTAGE, CMD_SPAWN,
			CMD_GAS, CMD_BOMB, CMD_HEX, CMD_DEGRADE,
			CMD_CURSE, CMD_CHAMPION
	};

	private static final Map<String, Integer> COSTS = new LinkedHashMap<>();
	static {
		COSTS.put(CMD_TRAP, 3);
		COSTS.put(CMD_DEBUFF, 3);
		COSTS.put(CMD_SABOTAGE, 4);
		COSTS.put(CMD_SPAWN, 4);
		COSTS.put(CMD_GAS, 5);
		COSTS.put(CMD_BOMB, 5);
		COSTS.put(CMD_HEX, 5);
		COSTS.put(CMD_DEGRADE, 5);
		COSTS.put(CMD_CURSE, 8);
		COSTS.put(CMD_CHAMPION, 8);
	}

	private static final String BUNDLE_CHARGES = "shatter_charges";
	private static final String BUNDLE_ENABLED = "shatter_enabled";
	private static final String BUNDLE_GRACE = "shatter_grace_depth";
	private static final String BUNDLE_PENDING_GRACE = "shatter_pending_grace";

	/** Host-run room flag. Clients mirror this from SessionInfo. */
	private static boolean roomEnabled = false;
	private static int charges = 0;
	/** Depth the joiner is immune on. -1 = no grace. */
	private static int graceDepth = -1;
	private static boolean pendingJoinGrace = false;

	private static final int SPAWN_RADIUS = 4;

	@SuppressWarnings("unchecked")
	private static final Class<? extends ChampionEnemy>[] CHAMPION_TYPES = new Class[]{
			ChampionEnemy.Blazing.class, ChampionEnemy.Projecting.class, ChampionEnemy.AntiMagic.class,
			ChampionEnemy.Giant.class, ChampionEnemy.Blessed.class, ChampionEnemy.Growing.class
	};

	private ShatterCommands() {}

	public static void reset() {
		roomEnabled = false;
		charges = 0;
		graceDepth = -1;
		pendingJoinGrace = false;
	}

	/**
	 * New dungeon for a joining client: keep room flag and join-grace pending,
	 * clear charges (fresh race).
	 */
	public static void onClientDungeonInit() {
		charges = 0;
		graceDepth = -1;
		// pendingJoinGrace and roomEnabled kept from startClientRun
	}

	public static void store(Bundle bundle) {
		bundle.put(BUNDLE_CHARGES, charges);
		bundle.put(BUNDLE_ENABLED, roomEnabled);
		bundle.put(BUNDLE_GRACE, graceDepth);
		bundle.put(BUNDLE_PENDING_GRACE, pendingJoinGrace);
	}

	public static void restore(Bundle bundle) {
		charges = bundle.contains(BUNDLE_CHARGES) ? bundle.getInt(BUNDLE_CHARGES) : 0;
		roomEnabled = bundle.contains(BUNDLE_ENABLED) && bundle.getBoolean(BUNDLE_ENABLED);
		graceDepth = bundle.contains(BUNDLE_GRACE) ? bundle.getInt(BUNDLE_GRACE) : -1;
		pendingJoinGrace = bundle.contains(BUNDLE_PENDING_GRACE) && bundle.getBoolean(BUNDLE_PENDING_GRACE);
		clampCharges();
	}

	/** Cap from deepest floor: Sewers 4 … Halls 12. */
	public static int capForDeepestFloor(int deepestFloor) {
		int floor = Math.max(1, deepestFloor);
		return 4 + 2 * Math.min(4, (floor - 1) / 5);
	}

	public static int cap() {
		return capForDeepestFloor(Statistics.deepestFloor);
	}

	public static int charges() {
		return charges;
	}

	public static boolean roomEnabled() {
		return roomEnabled;
	}

	public static void setRoomEnabled(boolean enabled) {
		if (PlayAlongService.streamFeaturesVisible() || PlayAlongService.hostHoldsStreamRoom()) {
			roomEnabled = false;
			return;
		}
		roomEnabled = enabled;
	}

	/** Apply a remote session flag (clients). Dalton sessions force false. */
	public static void syncRoomEnabledFromSession(boolean enabled) {
		if (PlayAlongService.streamFeaturesVisible()) {
			roomEnabled = false;
			return;
		}
		roomEnabled = enabled;
	}

	public static boolean isActiveHere() {
		return roomEnabled
				&& !PlayAlongService.streamFeaturesVisible()
				&& !PlayAlongService.hostHoldsStreamRoom()
				&& PlayAlongService.isActive();
	}

	public static boolean inJoinGrace() {
		if (!pendingJoinGrace && graceDepth < 0) return false;
		if (PlayAlongService.isHosting()) return false;
		return graceDepth > 0 && Dungeon.depth == graceDepth;
	}

	public static void beginClientJoinGrace() {
		if (PlayAlongService.isHosting()) {
			pendingJoinGrace = false;
			graceDepth = -1;
			return;
		}
		pendingJoinGrace = true;
		graceDepth = -1;
	}

	/** Call when the hero is on a floor after join / load. */
	public static void noteDepth(int depth) {
		if (pendingJoinGrace && depth > 0 && !PlayAlongService.isHosting()) {
			graceDepth = depth;
			pendingJoinGrace = false;
			return;
		}
		if (graceDepth > 0 && depth != graceDepth) {
			graceDepth = -1;
		}
	}

	private static void clampCharges() {
		charges = Math.max(0, Math.min(charges, cap()));
	}

	public static void awardKillCharge(Object cause, Mob mob) {
		if (!isActiveHere()) return;
		if (mob == null || mob.alignment != Char.Alignment.ENEMY) return;
		if (mob.buff(ShatterSpawned.class) != null) return;
		if (!isHeroKill(cause)) return;
		if (charges >= cap()) return;
		charges++;
		if (Dungeon.hero != null && Dungeon.hero.sprite != null) {
			Dungeon.hero.sprite.showStatus(CharSprite.POSITIVE, "+1 shatter");
		}
	}

	private static boolean isHeroKill(Object cause) {
		if (cause == Dungeon.hero) return true;
		if (cause instanceof Weapon || cause instanceof Weapon.Enchantment) return true;
		return false;
	}

	public static int costOf(String cmd) {
		Integer c = COSTS.get(cmd);
		return c != null ? c : Integer.MAX_VALUE;
	}

	public static String[] orderedCommands() {
		return ORDERED_CMDS.clone();
	}

	public static String titleOf(String cmd) {
		if (cmd == null) return "?";
		switch (cmd) {
			case CMD_TRAP: return "Trap";
			case CMD_DEBUFF: return "Debuff";
			case CMD_SABOTAGE: return "Sabotage";
			case CMD_SPAWN: return "Spawn";
			case CMD_GAS: return "Gas";
			case CMD_BOMB: return "Bomb";
			case CMD_HEX: return "Hex";
			case CMD_DEGRADE: return "Degrade";
			case CMD_CURSE: return "Curse";
			case CMD_CHAMPION: return "Champion";
			default: return cmd;
		}
	}

	public static boolean canAfford(String cmd) {
		int cost = costOf(cmd);
		return cost <= cap() && charges >= cost && isActiveHere();
	}

	/**
	 * Host or client presses a Shatter button. Spends charges locally, then relays.
	 * @return null on success, otherwise a short error for the deck status line
	 */
	public static String cast(String cmd) {
		if (cmd == null || !COSTS.containsKey(cmd)) return "Unknown command";
		if (PlayAlongService.streamFeaturesVisible() || PlayAlongService.hostHoldsStreamRoom()) {
			return "Shatter is off on DaltonGoesFast";
		}
		if (!roomEnabled) return "Shatter Commands are off";
		if (!PlayAlongService.isActive()) return "Not in a Play Along room";
		if (Dungeon.hero == null || !Dungeon.hero.isAlive()) return "Hero is dead";
		if (!(ShatteredPixelDungeon.scene() instanceof GameScene)) return "Not in the dungeon";

		int cost = costOf(cmd);
		if (cost > cap()) return "Need a deeper floor for that";
		if (charges < cost) return "Not enough charges";

		ChatCommandPayload payload = buildPayload(cmd);
		if (payload == null) return "Could not prepare cast";

		charges -= cost;
		PlayAlongService.relayShatterCast(payload);
		GLog.i("Shatter: sent %s (%d charges left)", titleOf(cmd), charges);
		return null;
	}

	private static ChatCommandPayload buildPayload(String cmd) {
		ChatCommandPayload p = new ChatCommandPayload();
		p.command = cmd;
		p.username = localCasterName();
		p.rngSeed = Random.Long();
		p.amount = 0;
		p.tier = -1;

		switch (cmd) {
			case CMD_GAS:
				p.rolledClass = StreamingCommandHandler.rollGasClassName();
				break;
			case CMD_BOMB:
				p.rolledClass = StreamingCommandHandler.rollBombClassName();
				break;
			case CMD_TRAP:
				p.rolledClass = StreamingCommandHandler.rollTrapClassName();
				break;
			case CMD_DEBUFF:
				p.rolledClass = StreamingCommandHandler.rollDebuffClassName();
				break;
			case CMD_CHAMPION:
				p.rolledClass = Random.element(CHAMPION_TYPES).getName();
				break;
			case CMD_SPAWN:
			case CMD_HEX:
			case CMD_DEGRADE:
			case CMD_CURSE:
			case CMD_SABOTAGE:
				break;
			default:
				return null;
		}
		return p;
	}

	private static String localCasterName() {
		if (PlayAlongService.isClient()) {
			String n = PlayAlongService.clientUsername();
			if (n != null && !n.isEmpty()) return n;
		}
		String host = PlayAlongService.hostDisplayNameForSession();
		return host != null && !host.isEmpty() ? host : "Host";
	}

	/** Apply an incoming cast on this hero (never the caster). */
	public static void applyIncoming(ChatCommandPayload p) {
		if (p == null || p.command == null) return;
		if (!isActiveHere()) return;
		if (Dungeon.hero == null || Dungeon.level == null) return;
		if (!(ShatteredPixelDungeon.scene() instanceof GameScene)) return;
		if (!Dungeon.hero.isAlive()) return;
		if (inJoinGrace()) return;

		String me = normalize(localCasterName());
		String them = normalize(p.username);
		if (!them.isEmpty() && them.equals(me)) return;

		if (p.rngSeed != 0L) Random.pushGenerator(p.rngSeed);
		try {
			switch (p.command) {
				case CMD_GAS:
					StreamingCommandHandler.handleSpawnGas(p.username, p.rolledClass);
					break;
				case CMD_BOMB:
					StreamingCommandHandler.handleSpawnBomb(p.username, p.rolledClass);
					break;
				case CMD_TRAP:
					StreamingCommandHandler.handleSpawnTrap(p.username, p.rolledClass);
					break;
				case CMD_DEBUFF:
					StreamingCommandHandler.handleChatDebuff(p.username, p.rolledClass);
					break;
				case CMD_HEX:
					StreamingCommandHandler.handleChatHex(p.username);
					break;
				case CMD_DEGRADE:
					StreamingCommandHandler.handleChatDegrade(p.username);
					break;
				case CMD_CURSE:
					StreamingCommandHandler.curseEquippedRandom(p.username, 0);
					break;
				case CMD_SABOTAGE:
					StreamingCommandHandler.handleChatSabotage(p.username, p.rolledClass);
					break;
				case CMD_SPAWN:
					spawnFloorHostile(p.username, null);
					break;
				case CMD_CHAMPION:
					spawnFloorHostile(p.username, p.rolledClass);
					break;
			}
		} finally {
			if (p.rngSeed != 0L) Random.popGenerator();
		}
	}

	private static String normalize(String raw) {
		if (raw == null) return "";
		String s = raw.trim();
		while (s.startsWith("@")) s = s.substring(1).trim();
		return s.toLowerCase(Locale.ROOT);
	}

	private static void spawnFloorHostile(String username, String forcedChampClassName) {
		Hero hero = Dungeon.hero;
		if (hero == null || Dungeon.level == null) return;

		ArrayList<Class<? extends Mob>> rotation = MobSpawner.getMobRotation(Dungeon.depth);
		if (rotation == null || rotation.isEmpty()) return;
		Class<? extends Mob> mobClass = Random.element(rotation);
		Mob mob = Reflection.newInstance(mobClass);
		if (mob == null) return;

		Buff.affect(mob, ShatterSpawned.class);
		if (forcedChampClassName != null && !forcedChampClassName.isEmpty()) {
			Class<? extends ChampionEnemy> champClass = null;
			Class<?> raw = Reflection.forName(forcedChampClassName);
			if (raw != null && ChampionEnemy.class.isAssignableFrom(raw)) {
				@SuppressWarnings("unchecked")
				Class<? extends ChampionEnemy> cast = (Class<? extends ChampionEnemy>) raw;
				champClass = cast;
			}
			if (champClass == null) {
				champClass = Random.element(CHAMPION_TYPES);
			}
			Buff.affect(mob, champClass);
		}

		int heroPos = hero.pos;
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
		if (candidates.isEmpty()) return;

		int cell = Random.element(candidates);
		if (mob.state != mob.PASSIVE) {
			mob.state = mob.WANDERING;
		}
		mob.pos = cell;
		GameScene.add(mob, 0f);
		Dungeon.level.occupyCell(mob);

		String chatter = (username != null && !username.isEmpty()) ? username : "Racer";
		String label = forcedChampClassName != null ? "champion " + mob.name() : mob.name();
		GLog.w("%s shattered a %s next to you!", chatter, label);
	}

	public static boolean hostCanToggle() {
		return PlayAlongService.isHosting()
				&& !PlayAlongService.hostHoldsStreamRoom()
				&& !PlayAlongService.streamFeaturesVisible();
	}
}
