/*
 * Pixel Dungeon
 * Copyright (C) 2012-2015 Oleg Dolya
 *
 * Shattered Pixel Dungeon
 * Copyright (C) 2014-2025 Evan Debenham
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
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
import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.Statistics;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Belongings;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroSubClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;
import com.shatteredpixel.shatteredpixeldungeon.journal.Catalog;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.AlchemyScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.HeroSelectScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.JournalScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.RankingsScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.StartScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.TitleScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.WelcomeScene;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndBag;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndInfoItem;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndJournal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a snapshot of current game and UI state for streaming (e.g. WebSocket).
 * Output shape matches the save-file parser's game_summary.json.
 * Call only from the main/game thread.
 */
public class GameStateSnapshot {

	private static final String SOURCE_ID = "shattered-pixel-dungeon";

	public static Map<String, Object> build() {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("source", SOURCE_ID);
		out.put("ui", buildUI());
		if (Dungeon.hero == null || Dungeon.level == null) {
			return out;
		}
		Hero hero = Dungeon.hero;
		// Key order matches game_summary.json for compatibility
		out.put("hero", buildHero(hero));
		out.put("equipped", buildEquipped(hero.belongings));
		out.put("inventory", buildInventory(hero.belongings));
		out.put("identification", buildIdentification());
		out.put("stats", buildStats());
		out.put("challenges", buildChallenges());
		out.put("won", Statistics.gameWon);
		out.put("ascended", Statistics.ascended);
		out.put("seed", buildSeed());
		out.put("duration", (int) Statistics.duration);
		out.put("upgrades_used", Statistics.upgradesUsed);
		out.put("combat_stats", buildCombatStats());
		out.put("buffs", buildBuffs(hero));
		out.put("talents", buildTalents(hero));
		out.put("quests", buildQuests());
		out.put("feeling", formatFeeling(Dungeon.level.feeling));
		return out;
	}

	private static Map<String, Object> buildUI() {
		Map<String, Object> ui = new LinkedHashMap<>();
		Object scene = ShatteredPixelDungeon.scene();
		ui.put("scene", sceneToId(scene));
		if (scene instanceof GameScene || scene instanceof TitleScene) {
			ui.put("open_windows", buildOpenWindows());
		}
		return ui;
	}

	private static String sceneToId(Object scene) {
		if (scene == null) return "unknown";
		if (scene instanceof TitleScene) return "title";
		if (scene instanceof StartScene) return "start";
		if (scene instanceof HeroSelectScene) return "hero_select";
		if (scene instanceof GameScene) return "game";
		if (scene instanceof InterlevelScene) return "interlevel";
		if (scene instanceof AlchemyScene) return "alchemy";
		if (scene instanceof JournalScene) return "journal";
		if (scene instanceof RankingsScene) return "rankings";
		if (scene instanceof WelcomeScene) return "welcome";
		String name = scene.getClass().getSimpleName();
		if (name.equals("SurfaceScene")) return "surface";
		if (name.equals("AmuletScene")) return "amulet";
		if (name.equals("NewsScene")) return "news";
		if (name.equals("ChangesScene")) return "changes";
		if (name.equals("AboutScene")) return "about";
		if (name.equals("SupporterScene")) return "supporter";
		return name.replace("Scene", "").toLowerCase();
	}

	private static List<String> buildOpenWindows() {
		List<String> open = new ArrayList<>();
		if (WndJournal.isOpen()) open.add("journal");
		if (WndBag.INSTANCE != null) open.add("inventory");
		if (WndInfoItem.isOpen()) open.add("item_info");
		return open;
	}

	private static Map<String, Object> buildHero(Hero hero) {
		Map<String, Object> h = new LinkedHashMap<>();
		String classTitle = hero.heroClass == null ? "Unknown" : hero.heroClass.title();
		h.put("class", Messages.titleCase(classTitle));
		String subTitle = hero.subClass == null || hero.subClass == HeroSubClass.NONE ? null : hero.subClass.title();
		h.put("subclass", subTitle != null ? Messages.titleCase(subTitle) : null);
		h.put("hp", hero.HP);
		h.put("ht", hero.HT);
		h.put("exp", hero.exp);
		h.put("lvl", hero.lvl);
		h.put("str", hero.STR);
		return h;
	}

	private static Map<String, Object> buildEquipped(Belongings b) {
		Map<String, Object> eq = new LinkedHashMap<>();
		eq.put("weapon", itemSlot(b.weapon()));
		eq.put("armor", itemSlot(b.armor()));
		eq.put("artifact", itemSlot(b.artifact()));
		eq.put("ring", itemSlot(b.ring()));
		eq.put("misc", itemSlot(b.misc()));
		return eq;
	}

	private static Map<String, Object> itemSlot(Item item) {
		if (item == null) return null;
		Map<String, Object> s = new LinkedHashMap<>();
		s.put("name", item.getClass().getSimpleName());
		s.put("level", item.level());
		if (item instanceof Weapon) {
			Weapon w = (Weapon) item;
			if (w.enchantment != null) s.put("enchantment", w.enchantment.getClass().getSimpleName());
		}
		if (item instanceof Armor) {
			Armor a = (Armor) item;
			if (a.glyph != null) s.put("glyph", a.glyph.getClass().getSimpleName());
		}
		return s;
	}

	private static List<Map<String, Object>> buildInventory(Belongings b) {
		List<Map<String, Object>> inv = new ArrayList<>();
		if (b.backpack == null) return inv;
		for (Item item : b.backpack) {
			Map<String, Object> e = new LinkedHashMap<>();
			e.put("name", item.getClass().getSimpleName());
			e.put("quantity", item.quantity());
			e.put("level", item.level());
			inv.add(e);
		}
		return inv;
	}

	private static Map<String, Object> buildStats() {
		Map<String, Object> s = new LinkedHashMap<>();
		s.put("depth", Dungeon.depth);
		s.put("max_depth", Statistics.deepestFloor);
		s.put("gold", Dungeon.gold);
		s.put("energy", Dungeon.energy);
		s.put("score", Statistics.totalScore);
		s.put("enemies_slain", Statistics.enemiesSlain);
		s.put("food_eaten", Statistics.foodEaten);
		s.put("potions_cooked", Statistics.itemsCrafted);
		s.put("ankhs_used", Statistics.ankhsUsed);
		return s;
	}

	private static List<String> buildChallenges() {
		List<String> list = new ArrayList<>();
		int mask = Dungeon.challenges;
		for (int i = 0; i < Challenges.MASKS.length; i++) {
			if ((mask & Challenges.MASKS[i]) != 0) {
				list.add(Messages.get(Challenges.class, Challenges.NAME_IDS[i]));
			}
		}
		return list;
	}

	private static String buildSeed() {
		if (Dungeon.customSeedText != null && !Dungeon.customSeedText.isEmpty()) {
			return Dungeon.customSeedText;
		}
		try {
			return DungeonSeed.convertToCode(Dungeon.seed);
		} catch (Exception e) {
			return String.valueOf(Dungeon.seed);
		}
	}

	private static Map<String, Object> buildCombatStats() {
		Map<String, Object> c = new LinkedHashMap<>();
		c.put("sneak_attacks", Statistics.sneakAttacks);
		c.put("thrown_assists", Statistics.thrownAttacks);
		c.put("hazard_assists", Statistics.hazardAssistedKills);
		return c;
	}

	private static List<String> buildBuffs(Hero hero) {
		List<String> list = new ArrayList<>();
		for (com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff b : hero.buffs()) {
			list.add(b.getClass().getSimpleName());
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

	private static Map<String, Object> buildQuests() {
		Map<String, Object> q = new LinkedHashMap<>();
		q.put("sadGhost", questMap(false, false, false));
		q.put("wandmaker", questMap(false, false, false));
		q.put("blacksmith", questMap(false, false, false));
		q.put("demon", questMap(false, false, false));
		return q;
	}

	private static Map<String, Object> questMap(boolean spawned, boolean given, boolean completed) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("spawned", spawned);
		m.put("given", given);
		m.put("completed", completed);
		return m;
	}

	private static String formatFeeling(Level.Feeling f) {
		if (f == null) return "None";
		switch (f) {
			case NONE: return "None";
			case CHASM: return "Chasm (Falling Risk)";
			case WATER: return "Flooded (Water Everywhere)";
			case GRASS: return "Overgrown (Vegetation)";
			case SECRETS: return "Hidden Chambers (Secrets)";
			case LARGE: return "None";
			case DARK: return "Darkness (Limited Vision)";
			case TRAPS: return "Dangerous (Extra Traps)";
			default: return f.name();
		}
	}

	private static Map<String, List<Map<String, Object>>> buildIdentification() {
		Map<String, List<Map<String, Object>>> id = new LinkedHashMap<>();
		id.put("potions", buildIdListPotions());
		id.put("scrolls", buildIdListScrolls());
		id.put("rings", buildIdListRings());
		return id;
	}

	private static List<Map<String, Object>> buildIdListPotions() {
		List<Map<String, Object>> list = new ArrayList<>();
		for (Class<?> cls : Catalog.POTIONS.items()) {
			if (!Potion.class.isAssignableFrom(cls)) continue;
			Class<? extends Potion> potionCls = cls.asSubclass(Potion.class);
			String rune = Potion.labelFor(potionCls);
			boolean known = Potion.isKnownInCurrentRun(potionCls);
			list.add(idEntry(cls, rune, known));
		}
		list.sort((a, b) -> ((String) a.get("true_name")).compareToIgnoreCase((String) b.get("true_name")));
		return list;
	}

	private static List<Map<String, Object>> buildIdListScrolls() {
		List<Map<String, Object>> list = new ArrayList<>();
		for (Class<?> cls : Catalog.SCROLLS.items()) {
			if (!Scroll.class.isAssignableFrom(cls)) continue;
			Class<? extends Scroll> scrollCls = cls.asSubclass(Scroll.class);
			String rune = Scroll.labelFor(scrollCls);
			boolean known = Scroll.isKnownInCurrentRun(scrollCls);
			list.add(idEntry(cls, rune, known));
		}
		list.sort((a, b) -> ((String) a.get("true_name")).compareToIgnoreCase((String) b.get("true_name")));
		return list;
	}

	private static List<Map<String, Object>> buildIdListRings() {
		List<Map<String, Object>> list = new ArrayList<>();
		for (Class<?> cls : Catalog.RINGS.items()) {
			if (!Ring.class.isAssignableFrom(cls)) continue;
			Class<? extends Ring> ringCls = cls.asSubclass(Ring.class);
			String rune = Ring.labelFor(ringCls);
			boolean known = Ring.isKnownInCurrentRun(ringCls);
			list.add(idEntry(cls, rune, known));
		}
		list.sort((a, b) -> ((String) a.get("true_name")).compareToIgnoreCase((String) b.get("true_name")));
		return list;
	}

	private static Map<String, Object> idEntry(Class<?> cls, String runeName, boolean isKnown) {
		String simple = cls.getSimpleName();
		Map<String, Object> e = new LinkedHashMap<>();
		e.put("class_name", simple);
		e.put("true_name", formatTrueName(simple));
		e.put("rune_name", runeName != null ? runeName : "");
		e.put("is_known", isKnown);
		return e;
	}

	private static String formatTrueName(String className) {
		String s = className.replaceAll("([a-z])([A-Z])", "$1 $2").replaceAll("([A-Z])([A-Z][a-z])", "$1 $2");
		return s.replace("Of", "of");
	}
}
