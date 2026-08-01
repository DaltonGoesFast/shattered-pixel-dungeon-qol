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
 */

package com.shatteredpixel.shatteredpixeldungeon.utils;

import com.shatteredpixel.shatteredpixeldungeon.Challenges;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.Statistics;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroSubClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.Bag;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.features.LevelTransition;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap;
import com.shatteredpixel.shatteredpixeldungeon.plants.Plant;
import com.shatteredpixel.shatteredpixeldungeon.scenes.AlchemyScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.watabou.noosa.Game;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds ML-focused snapshots for gameplay training export.
 * No sprites, UI layout, localized names, or overlay fields.
 * Call only from the game/actor thread.
 */
public final class TrainingSnapshot {

	private TrainingSnapshot() {}

	public static Map<String, Object> buildManifest(String runId) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("run_id", runId);
		m.put("seed", seedString());
		m.put("slot", GamesInProgress.curSlot);
		m.put("game_version", Game.version);
		m.put("mod_version", "qol-" + Game.versionCode);
		Hero hero = Dungeon.hero;
		if (hero != null) {
			m.put("hero_class", hero.heroClass == null ? null : hero.heroClass.name());
			m.put("subclass", hero.subClass == null || hero.subClass == HeroSubClass.NONE
					? null : hero.subClass.name());
			m.put("talents", buildTalents(hero));
		}
		m.put("challenges", buildChallenges());
		m.put("started_at", System.currentTimeMillis());
		return m;
	}

	public static Map<String, Object> buildLevel() {
		Map<String, Object> out = new LinkedHashMap<>();
		Level level = Dungeon.level;
		if (level == null) return out;

		out.put("width", level.width());
		out.put("height", level.height());
		out.put("depth", Dungeon.depth);
		out.put("branch", Dungeon.branch);
		out.put("feeling", level.feeling == null ? "NONE" : level.feeling.name());
		out.put("entrance", level.entrance());
		out.put("exit", level.exit());
		out.put("viewDistance", level.viewDistance);
		out.put("transitions", buildTransitions(level));

		out.put("map", copyInts(level.map));
		out.put("passable", packBools(level.passable));
		out.put("solid", packBools(level.solid));
		out.put("losBlocking", packBools(level.losBlocking));
		out.put("water", packBools(level.water));
		out.put("pit", packBools(level.pit));
		out.put("visited", packBools(level.visited));
		out.put("mapped", packBools(level.mapped));

		out.put("mobs", buildAllMobs(level));
		out.put("heaps", buildAllHeaps(level));
		out.put("plants", buildAllPlants(level));
		out.put("traps", buildAllTraps(level, false));
		out.put("blobs", buildAllBlobs(level, null));
		return out;
	}

	/** Agent-perspective observation at action commit time. */
	public static Map<String, Object> buildObservation() {
		Map<String, Object> obs = new LinkedHashMap<>();
		Hero hero = Dungeon.hero;
		Level level = Dungeon.level;
		if (hero == null) return obs;

		obs.put("hero_pos", hero.pos);
		obs.put("hero", buildHero(hero));
		obs.put("inventory", buildInventory(hero));
		obs.put("equipped", buildEquipped(hero));
		obs.put("gold", Dungeon.gold);
		obs.put("energy", Dungeon.energy);

		if (level == null) {
			obs.put("fov_b64", "");
			obs.put("visible_mobs", new ArrayList<>());
			obs.put("visible_heaps", new ArrayList<>());
			obs.put("known_traps", new ArrayList<>());
			obs.put("visible_plants", new ArrayList<>());
			obs.put("visible_blobs", new ArrayList<>());
			return obs;
		}

		boolean[] fov = hero.fieldOfView;
		if (fov == null || fov.length != level.length()) {
			fov = new boolean[level.length()];
			level.updateFieldOfView(hero, fov);
		}
		obs.put("fov_b64", packBools(fov));
		obs.put("visible_mobs", buildVisibleMobs(level, fov));
		obs.put("visible_heaps", buildVisibleHeaps(level, fov));
		obs.put("known_traps", buildKnownTraps(level, fov));
		obs.put("visible_plants", buildVisiblePlants(level, fov));
		obs.put("visible_blobs", buildAllBlobs(level, fov));
		return obs;
	}

	public static Map<String, Object> buildOutcomeDeltas(
			int hpBefore, int goldBefore, int expBefore, int depthBefore, int posBefore,
			boolean died) {
		Map<String, Object> o = new LinkedHashMap<>();
		Hero hero = Dungeon.hero;
		int hp = hero == null ? 0 : hero.HP;
		int exp = hero == null ? 0 : hero.exp;
		int pos = hero == null ? -1 : hero.pos;
		o.put("hp_delta", hp - hpBefore);
		o.put("gold_delta", Dungeon.gold - goldBefore);
		o.put("exp_delta", exp - expBefore);
		o.put("depth_changed", Dungeon.depth != depthBefore);
		o.put("died", died || hero == null || !hero.isAlive());
		o.put("hero_pos_after", pos);
		return o;
	}

	public static Map<String, Object> buildRunOutcome(String deathCauseClass) {
		Map<String, Object> o = new LinkedHashMap<>();
		o.put("won", Statistics.gameWon);
		o.put("ascended", Statistics.ascended);
		o.put("final_depth", Dungeon.depth);
		o.put("duration", (int) Statistics.duration);
		o.put("score", Statistics.totalScore);
		o.put("enemies_slain", Statistics.enemiesSlain);
		o.put("death_cause_class", deathCauseClass);
		o.put("ended_at", System.currentTimeMillis());
		return o;
	}

	public static String sceneId() {
		Object scene = ShatteredPixelDungeon.scene();
		if (scene instanceof GameScene) return "game";
		if (scene instanceof AlchemyScene) return "alchemy";
		if (scene == null) return "unknown";
		String name = scene.getClass().getSimpleName();
		if (name.contains("Shop") || name.contains("Trade")) return "shop";
		return name.replace("Scene", "").toLowerCase();
	}

	public static float turnNow() {
		return (float) ((int) Statistics.duration) + Actor.now();
	}

	public static Map<String, Object> actionCell(String type, int targetCell, Integer targetEntityId) {
		Map<String, Object> a = new LinkedHashMap<>();
		a.put("kind", "cell");
		a.put("type", type);
		a.put("target_cell", targetCell);
		if (targetEntityId != null) a.put("target_entity_id", targetEntityId);
		return a;
	}

	public static Map<String, Object> actionItem(String type, String itemClass, String itemAction, Integer targetCell) {
		Map<String, Object> a = new LinkedHashMap<>();
		a.put("kind", "item");
		a.put("type", type);
		a.put("item_class", itemClass);
		a.put("item_action", itemAction);
		if (targetCell != null) a.put("target_cell", targetCell);
		return a;
	}

	public static Map<String, Object> actionSimple(String kind, String type) {
		Map<String, Object> a = new LinkedHashMap<>();
		a.put("kind", kind);
		a.put("type", type);
		return a;
	}

	public static Map<String, Object> actionCombo(String className) {
		Map<String, Object> a = new LinkedHashMap<>();
		a.put("kind", "combo");
		a.put("type", className);
		return a;
	}

	public static Map<String, Object> actionShop(String type, String itemClass) {
		Map<String, Object> a = new LinkedHashMap<>();
		a.put("kind", "shop");
		a.put("type", type);
		if (itemClass != null) a.put("item_class", itemClass);
		return a;
	}

	public static Map<String, Object> actionAlchemy(String recipeId) {
		Map<String, Object> a = new LinkedHashMap<>();
		a.put("kind", "alchemy");
		a.put("type", "alchemy_combine");
		a.put("recipe_id", recipeId);
		return a;
	}

	private static String seedString() {
		if (Dungeon.customSeedText != null && !Dungeon.customSeedText.isEmpty()) {
			return Dungeon.customSeedText;
		}
		try {
			return DungeonSeed.convertToCode(Dungeon.seed);
		} catch (Exception e) {
			return String.valueOf(Dungeon.seed);
		}
	}

	private static List<String> buildChallenges() {
		List<String> list = new ArrayList<>();
		int mask = Dungeon.challenges;
		for (int i = 0; i < Challenges.MASKS.length; i++) {
			if ((mask & Challenges.MASKS[i]) != 0) {
				list.add(Challenges.NAME_IDS[i]);
			}
		}
		return list;
	}

	private static Map<String, Map<String, Integer>> buildTalents(Hero hero) {
		Map<String, Map<String, Integer>> out = new LinkedHashMap<>();
		String[] tierNames = { "tier1", "tier2", "tier3", "tier4" };
		for (int t = 0; t < tierNames.length && t < hero.talents.size(); t++) {
			LinkedHashMap<Talent, Integer> tier = hero.talents.get(t);
			Map<String, Integer> points = new LinkedHashMap<>();
			for (Talent talent : tier.keySet()) {
				int pt = tier.get(talent);
				if (pt > 0) points.put(talent.name(), pt);
			}
			if (!points.isEmpty()) out.put(tierNames[t], points);
		}
		return out;
	}

	private static Map<String, Object> buildHero(Hero hero) {
		Map<String, Object> h = new LinkedHashMap<>();
		h.put("hp", hero.HP);
		h.put("ht", hero.HT);
		h.put("lvl", hero.lvl);
		h.put("str", hero.STR);
		h.put("exp", hero.exp);
		List<String> buffs = new ArrayList<>();
		for (Buff b : hero.buffs()) {
			buffs.add(b.getClass().getSimpleName());
		}
		h.put("buffs", buffs);
		return h;
	}

	private static List<Map<String, Object>> buildInventory(Hero hero) {
		List<Map<String, Object>> items = new ArrayList<>();
		if (hero.belongings == null || hero.belongings.backpack == null) return items;
		for (Bag bag : hero.belongings.getBags()) {
			if (bag == null) continue;
			boolean main = bag == hero.belongings.backpack;
			if (main) {
				for (Item item : bag.items) {
					if (item instanceof Bag) continue;
					items.add(itemSummary(item));
				}
			} else {
				for (Item item : bag) {
					items.add(itemSummary(item));
				}
			}
		}
		return items;
	}

	private static Map<String, Object> buildEquipped(Hero hero) {
		Map<String, Object> eq = new LinkedHashMap<>();
		eq.put("weapon", itemClassOrNull(hero.belongings.weapon()));
		eq.put("armor", itemClassOrNull(hero.belongings.armor()));
		eq.put("artifact", itemClassOrNull(hero.belongings.artifact()));
		eq.put("misc", itemClassOrNull(hero.belongings.misc()));
		eq.put("ring", itemClassOrNull(hero.belongings.ring()));
		return eq;
	}

	private static String itemClassOrNull(Item item) {
		return item == null ? null : item.getClass().getSimpleName();
	}

	private static Map<String, Object> itemSummary(Item item) {
		Map<String, Object> s = new LinkedHashMap<>();
		s.put("class", item.getClass().getSimpleName());
		s.put("level", item.level());
		s.put("quantity", item.quantity());
		s.put("identified", item.isIdentified());
		s.put("cursed", item.cursed);
		return s;
	}

	private static List<Map<String, Object>> buildTransitions(Level level) {
		List<Map<String, Object>> list = new ArrayList<>();
		if (level.transitions == null) return list;
		for (LevelTransition t : level.transitions) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("type", t.type == null ? null : t.type.name());
			m.put("cell", t.centerCell);
			m.put("destDepth", t.destDepth);
			m.put("destBranch", t.destBranch);
			list.add(m);
		}
		return list;
	}

	private static List<Map<String, Object>> buildAllMobs(Level level) {
		List<Map<String, Object>> list = new ArrayList<>();
		for (Mob mob : level.mobs) {
			list.add(mobEntry(mob));
		}
		return list;
	}

	private static List<Map<String, Object>> buildVisibleMobs(Level level, boolean[] fov) {
		List<Map<String, Object>> list = new ArrayList<>();
		for (Mob mob : level.mobs) {
			if (mob.pos >= 0 && mob.pos < fov.length && fov[mob.pos]) {
				list.add(mobEntry(mob));
			}
		}
		return list;
	}

	private static Map<String, Object> mobEntry(Mob mob) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("id", mob.id());
		m.put("class", mob.getClass().getSimpleName());
		m.put("pos", mob.pos);
		m.put("hp", mob.HP);
		m.put("ht", mob.HT);
		m.put("alignment", mob.alignment == null ? null : mob.alignment.name());
		m.put("state", mobStateTag(mob));
		m.put("hostile", mob.alignment == Char.Alignment.ENEMY);
		return m;
	}

	private static String mobStateTag(Mob mob) {
		if (mob.state == mob.SLEEPING) return "SLEEPING";
		if (mob.state == mob.WANDERING) return "WANDERING";
		if (mob.state == mob.INVESTIGATING) return "INVESTIGATING";
		if (mob.state == mob.HUNTING) return "HUNTING";
		if (mob.state == mob.FLEEING) return "FLEEING";
		if (mob.state == mob.PASSIVE) return "PASSIVE";
		return "UNKNOWN";
	}

	private static List<Map<String, Object>> buildAllHeaps(Level level) {
		List<Map<String, Object>> list = new ArrayList<>();
		for (Heap heap : level.heaps.valueList()) {
			list.add(heapEntry(heap));
		}
		return list;
	}

	private static List<Map<String, Object>> buildVisibleHeaps(Level level, boolean[] fov) {
		List<Map<String, Object>> list = new ArrayList<>();
		for (Heap heap : level.heaps.valueList()) {
			if (heap.pos >= 0 && heap.pos < fov.length && fov[heap.pos] && !heap.hidden) {
				list.add(heapEntry(heap));
			}
		}
		return list;
	}

	private static Map<String, Object> heapEntry(Heap heap) {
		Map<String, Object> h = new LinkedHashMap<>();
		h.put("pos", heap.pos);
		h.put("type", heap.type == null ? null : heap.type.name());
		List<Map<String, Object>> items = new ArrayList<>();
		if (heap.items != null) {
			for (Item item : heap.items) {
				items.add(itemSummary(item));
			}
		}
		h.put("items", items);
		return h;
	}

	private static List<Map<String, Object>> buildAllPlants(Level level) {
		List<Map<String, Object>> list = new ArrayList<>();
		for (Plant plant : level.plants.valueList()) {
			list.add(plantEntry(plant));
		}
		return list;
	}

	private static List<Map<String, Object>> buildVisiblePlants(Level level, boolean[] fov) {
		List<Map<String, Object>> list = new ArrayList<>();
		for (Plant plant : level.plants.valueList()) {
			if (plant.pos >= 0 && plant.pos < fov.length && fov[plant.pos]) {
				list.add(plantEntry(plant));
			}
		}
		return list;
	}

	private static Map<String, Object> plantEntry(Plant plant) {
		Map<String, Object> p = new LinkedHashMap<>();
		p.put("pos", plant.pos);
		p.put("class", plant.getClass().getSimpleName());
		return p;
	}

	private static List<Map<String, Object>> buildAllTraps(Level level, boolean knownOnly) {
		List<Map<String, Object>> list = new ArrayList<>();
		for (Trap trap : level.traps.valueList()) {
			if (knownOnly && !trap.visible) continue;
			list.add(trapEntry(trap));
		}
		return list;
	}

	/** Known traps: visible, or on visited/mapped cells. */
	private static List<Map<String, Object>> buildKnownTraps(Level level, boolean[] fov) {
		List<Map<String, Object>> list = new ArrayList<>();
		for (Trap trap : level.traps.valueList()) {
			int pos = trap.pos;
			boolean known = trap.visible
					|| (pos >= 0 && pos < level.length()
					&& (level.visited[pos] || level.mapped[pos]
					|| (fov != null && pos < fov.length && fov[pos])));
			if (!known) continue;
			// Skip undiscovered (invisible) traps even on visited cells if not revealed —
			// only include if visible OR currently in FOV (player can see the trap tile).
			if (!trap.visible && (fov == null || pos < 0 || pos >= fov.length || !fov[pos])) {
				// visited/mapped but trap not revealed: still unknown to player
				continue;
			}
			list.add(trapEntry(trap));
		}
		return list;
	}

	private static Map<String, Object> trapEntry(Trap trap) {
		Map<String, Object> t = new LinkedHashMap<>();
		t.put("pos", trap.pos);
		t.put("class", trap.getClass().getSimpleName());
		t.put("active", trap.active);
		t.put("revealed", trap.visible);
		return t;
	}

	/** If fov is non-null, only include cells currently in FOV. */
	private static List<Map<String, Object>> buildAllBlobs(Level level, boolean[] fov) {
		List<Map<String, Object>> list = new ArrayList<>();
		if (level.blobs == null) return list;
		for (Map.Entry<Class<? extends Blob>, Blob> e : level.blobs.entrySet()) {
			Blob blob = e.getValue();
			if (blob == null || blob.volume <= 0 || blob.cur == null) continue;
			Map<String, Object> b = new LinkedHashMap<>();
			b.put("class", e.getKey().getSimpleName());
			List<Map<String, Object>> cells = new ArrayList<>();
			int[] cur = blob.cur;
			for (int i = 0; i < cur.length; i++) {
				if (cur[i] <= 0) continue;
				if (fov != null && (i >= fov.length || !fov[i])) continue;
				Map<String, Object> cell = new LinkedHashMap<>();
				cell.put("pos", i);
				cell.put("volume", cur[i]);
				cells.add(cell);
			}
			if (cells.isEmpty()) continue;
			b.put("cells", cells);
			list.add(b);
		}
		return list;
	}

	private static int[] copyInts(int[] src) {
		if (src == null) return new int[0];
		int[] copy = new int[src.length];
		System.arraycopy(src, 0, copy, 0, src.length);
		return copy;
	}

	/** Pack boolean[] into base64 bitstring (LSB first within each byte). */
	public static String packBools(boolean[] flags) {
		if (flags == null || flags.length == 0) return "";
		byte[] bytes = new byte[(flags.length + 7) / 8];
		for (int i = 0; i < flags.length; i++) {
			if (flags[i]) {
				bytes[i / 8] |= (byte) (1 << (i % 8));
			}
		}
		return Base64.getEncoder().encodeToString(bytes);
	}
}
