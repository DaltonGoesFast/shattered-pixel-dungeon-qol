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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package com.shatteredpixel.shatteredpixeldungeon.utils;

import com.shatteredpixel.shatteredpixeldungeon.Challenges;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.DM300;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.DwarfKing;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Goo;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Tengu;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.YogDzewa;
import com.shatteredpixel.shatteredpixeldungeon.items.Amulet;
import com.shatteredpixel.shatteredpixeldungeon.items.Dewdrop;
import com.shatteredpixel.shatteredpixeldungeon.items.EnergyCrystal;
import com.shatteredpixel.shatteredpixeldungeon.items.Gold;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.Artifact;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.AscensionChallenge;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Bless;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.GreaterHaste;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Haste;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Invisibility;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Invulnerability;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Paralysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Static flags / queues for streaming events (death, boss, game log, gameplay telemetry).
 * The desktop ticker drains these and broadcasts via WebSocket.
 */
public class StreamingEvents {

	public static volatile boolean heroDiedPending = false;
	public static volatile int heroDiedChallengeCount = -1;
	public static volatile int bossSlainDepthPending = -1;

	private static final ConcurrentLinkedQueue<String> pendingLogs = new ConcurrentLinkedQueue<>();
	private static final ConcurrentLinkedQueue<TypedEvent> pendingTyped = new ConcurrentLinkedQueue<>();

	/** Run-local first-seen tracking for pickup / use noise control. */
	private static final Set<String> firstPickupClasses = new HashSet<>();
	private static final Set<String> firstUseClasses = new HashSet<>();

	private static final Set<Class<? extends Buff>> BUFF_ALLOWLIST = new HashSet<>();
	static {
		// Skip Barrier — shield tick spam (~80%+ of buff_changed on long runs); not useful for edit cuts.
		BUFF_ALLOWLIST.add(Invisibility.class);
		BUFF_ALLOWLIST.add(Bless.class);
		BUFF_ALLOWLIST.add(Paralysis.class);
		BUFF_ALLOWLIST.add(Invulnerability.class);
		BUFF_ALLOWLIST.add(AscensionChallenge.class);
		BUFF_ALLOWLIST.add(Haste.class);
		BUFF_ALLOWLIST.add(GreaterHaste.class);
	}

	/** Clears run-local first-seen sets (call from Dungeon.init). */
	public static void clearRunState() {
		firstPickupClasses.clear();
		firstUseClasses.clear();
	}

	/** Scalar-field event for WebSocket broadcastEvent. */
	public static final class TypedEvent {
		public final String type;
		public final Map<String, Object> fields;

		public TypedEvent(String type, Map<String, Object> fields) {
			this.type = type;
			this.fields = fields != null ? fields : Collections.emptyMap();
		}
	}

	public static void notifyHeroDied() {
		heroDiedPending = true;
		heroDiedChallengeCount = Challenges.activeChallenges();
	}

	public static void notifyGameLog(String msg) {
		if (msg != null && !msg.trim().isEmpty() && !msg.equals(GLog.NEW_LINE)) {
			pendingLogs.offer(msg);
		}
	}

	public static List<String> drainLogs() {
		if (pendingLogs.isEmpty()) {
			return Collections.emptyList();
		}
		List<String> list = new ArrayList<>();
		String s;
		while ((s = pendingLogs.poll()) != null) {
			list.add(s);
		}
		return list;
	}

	/** Queue a typed gameplay event when streaming is enabled (desktop broadcasts). */
	public static void notifyTyped(String type, Map<String, Object> fields) {
		if (!SPDSettings.streamingEnabled() || type == null || type.isEmpty()) {
			return;
		}
		pendingTyped.offer(new TypedEvent(type, fields));
	}

	public static List<TypedEvent> drainTyped() {
		if (pendingTyped.isEmpty()) {
			return Collections.emptyList();
		}
		List<TypedEvent> list = new ArrayList<>();
		TypedEvent e;
		while ((e = pendingTyped.poll()) != null) {
			list.add(e);
		}
		return list;
	}

	public static void notifyLockOpened(String keyType, String target, int cell, String source) {
		Map<String, Object> f = new LinkedHashMap<>();
		f.put("key_type", keyType != null ? keyType : "unknown");
		f.put("target", target != null ? target : "unknown");
		f.put("cell", cell);
		f.put("depth", Dungeon.depth);
		f.put("branch", Dungeon.branch);
		f.put("source", source != null ? source : "unknown");
		notifyTyped("lock_opened", f);
	}

	public static void notifyTrapTriggered(String trapClass, String trapName, int cell, boolean visible, boolean heroOnTrap) {
		Map<String, Object> f = new LinkedHashMap<>();
		f.put("trap_class", trapClass != null ? trapClass : "Trap");
		if (trapName != null && !trapName.isEmpty()) {
			f.put("trap_name", trapName);
		}
		f.put("cell", cell);
		f.put("depth", Dungeon.depth);
		f.put("visible", visible);
		f.put("hero_on_trap", heroOnTrap);
		notifyTyped("trap_triggered", f);
	}

	public static void notifyAlchemyOpened(boolean viaToolkit) {
		Map<String, Object> f = new LinkedHashMap<>();
		f.put("depth", Dungeon.depth);
		f.put("energy", Dungeon.energy);
		f.put("via_toolkit", viaToolkit);
		notifyTyped("alchemy_opened", f);
	}

	public static void notifyAlchemyCrafted(String recipe, String resultClass, int resultQty, int energyLeft) {
		Map<String, Object> f = new LinkedHashMap<>();
		f.put("recipe", recipe != null ? recipe : "unknown");
		f.put("result_class", resultClass != null ? resultClass : "Item");
		f.put("result_qty", resultQty);
		f.put("energy_left", energyLeft);
		f.put("depth", Dungeon.depth);
		notifyTyped("alchemy_crafted", f);
	}

	public static void notifyAlchemyClosed() {
		Map<String, Object> f = new LinkedHashMap<>();
		f.put("depth", Dungeon.depth);
		notifyTyped("alchemy_closed", f);
	}

	public static void notifyMenuScene(String scene, String openWindowsCsv) {
		Map<String, Object> f = new LinkedHashMap<>();
		f.put("scene", scene != null ? scene : "unknown");
		if (openWindowsCsv != null) {
			f.put("open_windows", openWindowsCsv);
		}
		notifyTyped("menu_scene", f);
	}

	public static void notifyItemIdentified(Item item, boolean byHero) {
		if (item == null) return;
		Map<String, Object> f = baseItemFields(item);
		f.put("by_hero", byHero);
		notifyTyped("item_identified", f);
	}

	/** Shard-of-Oblivion path that marks ID-ready without calling identify(). */
	public static void notifyItemIdReady(Item item) {
		if (item == null) return;
		Map<String, Object> f = baseItemFields(item);
		f.put("by_hero", true);
		f.put("id_ready_only", true);
		notifyTyped("item_identified", f);
	}

	/**
	 * First this-run use of item class (drink/read/throw/shatter/energize).
	 * Returns true if emitted.
	 */
	public static boolean notifyItemUsed(Item item, String action, boolean known) {
		if (item == null || action == null) return false;
		String key = item.getClass().getSimpleName() + ":" + action;
		if (!firstUseClasses.add(key)) return false;
		Map<String, Object> f = baseItemFields(item);
		f.put("action", action);
		f.put("known", known);
		f.put("first", true);
		notifyTyped("item_used", f);
		return true;
	}

	/**
	 * First this-run unID pickup, or always for artifact / amulet / unique.
	 * Returns true if emitted.
	 */
	public static boolean notifyItemPickedUp(Item item) {
		if (item == null || shouldSkipPickupNoise(item)) return false;

		boolean notable = item instanceof Artifact || item instanceof Amulet || item.unique;
		boolean unId = !item.isIdentified();
		String cls = item.getClass().getSimpleName();

		boolean first = false;
		if (unId || notable) {
			first = firstPickupClasses.add(cls);
		}
		if (!notable && !(unId && first)) {
			return false;
		}

		Map<String, Object> f = baseItemFields(item);
		f.put("category", pickupCategory(item));
		f.put("identified", item.isIdentified());
		f.put("qty", item.quantity());
		f.put("first", first);
		notifyTyped("item_picked_up", f);
		return true;
	}

	public static void notifyShop(String side, Item item, int price, int qty) {
		if (item == null || side == null) return;
		Map<String, Object> f = baseItemFields(item);
		f.put("price", price);
		f.put("qty", qty);
		f.put("side", side);
		notifyTyped(side.startsWith("shop_") ? side : ("shop_" + side), f);
	}

	public static void notifyWellDrunk(String well) {
		Map<String, Object> f = new LinkedHashMap<>();
		f.put("well", well != null ? well : "well");
		f.put("depth", Dungeon.depth);
		f.put("branch", Dungeon.branch);
		notifyTyped("well_drunk", f);
	}

	public static void notifySacrificialReward(String prizeClass) {
		Map<String, Object> f = new LinkedHashMap<>();
		f.put("prize_class", prizeClass != null ? prizeClass : "Item");
		f.put("depth", Dungeon.depth);
		f.put("branch", Dungeon.branch);
		notifyTyped("sacrificial_reward", f);
	}

	public static void notifyQuestComplete(String quest) {
		Map<String, Object> f = new LinkedHashMap<>();
		f.put("quest", quest != null ? quest : "quest");
		f.put("depth", Dungeon.depth);
		f.put("branch", Dungeon.branch);
		notifyTyped("quest_complete", f);
	}

	public static void notifyAmuletObtained() {
		Map<String, Object> f = new LinkedHashMap<>();
		f.put("depth", Dungeon.depth);
		f.put("branch", Dungeon.branch);
		notifyTyped("amulet_obtained", f);
	}

	public static void notifyAscended() {
		Map<String, Object> f = new LinkedHashMap<>();
		f.put("depth", Dungeon.depth);
		notifyTyped("ascended", f);
	}

	public static void notifySurfaceReached() {
		Map<String, Object> f = new LinkedHashMap<>();
		f.put("depth", Dungeon.depth);
		notifyTyped("surface_reached", f);
	}

	public static void notifyInterlevelStart(String mode, int fromDepth, int toDepth) {
		Map<String, Object> f = new LinkedHashMap<>();
		f.put("mode", mode != null ? mode : "unknown");
		f.put("from_depth", fromDepth);
		f.put("to_depth", toDepth);
		notifyTyped("interlevel_start", f);
	}

	public static void notifyInterlevelEnd(String mode, int depth) {
		Map<String, Object> f = new LinkedHashMap<>();
		f.put("mode", mode != null ? mode : "unknown");
		f.put("depth", depth);
		notifyTyped("interlevel_end", f);
	}

	public static void notifyMobSlain(Mob mob) {
		if (mob == null) return;
		boolean miniboss = mob.properties().contains(Char.Property.MINIBOSS);
		boolean boss = mob.properties().contains(Char.Property.BOSS);
		if (!miniboss && !boss) return;
		if (isMainlineBoss(mob)) return; // covered by boss_slain

		Map<String, Object> f = new LinkedHashMap<>();
		f.put("mob_class", mob.getClass().getSimpleName());
		try {
			f.put("mob_name", mob.name());
		} catch (Exception ignored) {
			// name() can be fragile mid-die
		}
		f.put("depth", Dungeon.depth);
		f.put("branch", Dungeon.branch);
		f.put("properties", miniboss ? "miniboss" : "boss");
		notifyTyped("mob_slain", f);
	}

	public static void notifyBuffChanged(Buff buff, boolean gained) {
		if (buff == null || !(buff.target instanceof Hero)) return;
		if (!BUFF_ALLOWLIST.contains(buff.getClass())) return;
		Map<String, Object> f = new LinkedHashMap<>();
		f.put("buff", buff.getClass().getSimpleName());
		f.put("gained", gained);
		f.put("depth", Dungeon.depth);
		f.put("branch", Dungeon.branch);
		notifyTyped("buff_changed", f);
	}

	private static Map<String, Object> baseItemFields(Item item) {
		Map<String, Object> f = new LinkedHashMap<>();
		f.put("item_class", item.getClass().getSimpleName());
		try {
			f.put("item_name", item.name());
		} catch (Exception ignored) {
			f.put("item_name", item.getClass().getSimpleName());
		}
		f.put("depth", Dungeon.depth);
		f.put("branch", Dungeon.branch);
		return f;
	}

	private static boolean shouldSkipPickupNoise(Item item) {
		return item instanceof Gold
				|| item instanceof Dewdrop
				|| item instanceof EnergyCrystal;
	}

	private static String pickupCategory(Item item) {
		if (item instanceof Amulet) return "amulet";
		if (item instanceof Artifact) return "artifact";
		if (item.unique) return "unique";
		if (!item.isIdentified()) return "unidentified";
		return "item";
	}

	private static boolean isMainlineBoss(Mob mob) {
		return mob instanceof Goo
				|| mob instanceof Tengu
				|| mob instanceof DM300
				|| mob instanceof DwarfKing
				|| mob instanceof YogDzewa;
	}

	public static void clear() {
		heroDiedPending = false;
		heroDiedChallengeCount = -1;
		bossSlainDepthPending = -1;
		pendingLogs.clear();
		pendingTyped.clear();
		clearRunState();
	}
}
