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

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.Challenges;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.Statistics;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Belongings;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroSubClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.Bag;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.MagicalHolster;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.PotionBandolier;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.ScrollHolder;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.VelvetPouch;
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
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSprite;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSpriteSheet;
import com.shatteredpixel.shatteredpixeldungeon.ui.BuffIcon;
import com.shatteredpixel.shatteredpixeldungeon.ui.BuffIndicator;
import com.shatteredpixel.shatteredpixeldungeon.ui.Icons;
import com.watabou.noosa.Image;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndBag;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndInfoItem;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndJournal;
import com.watabou.gltextures.TextureCache;
import com.watabou.noosa.TextureFilm;
import com.watabou.utils.RectF;

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

	private static TextureFilm largeBuffFilm;
	private static int largeBuffTexW;
	private static int largeBuffTexH;

	public static Map<String, Object> build() {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("source", SOURCE_ID);
		Map<String, Object> ui;
		try {
			ui = buildUI();
		} catch (Exception e) {
			ui = new LinkedHashMap<>();
			ui.put("scene", "unknown");
			ui.put("open_windows", new ArrayList<>());
		}
		out.put("ui", ui);
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
		out.put("hud", buildHud());
		out.put("won", Statistics.gameWon);
		out.put("ascended", Statistics.ascended);
		out.put("seed", buildSeed());
		out.put("duration", (int) Statistics.duration);
		out.put("upgrades_used", Statistics.upgradesUsed);
		out.put("combat_stats", buildCombatStats());
		out.put("sheets", buildSheets());
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
		if (scene instanceof GameScene) {
			ui.put("item_info", ItemInfoLayout.build());
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
		h.put("maxExp", hero.maxExp());
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
		s.put("quantity", item.quantity());
		s.put("image", item.image());
		Map<String, Object> sprite = itemSpriteRect(item.image());
		if (sprite != null) s.put("sprite", sprite);
		if (itemIconVisible(item)) {
			s.put("icon", item.icon);
			Map<String, Object> iconSprite = itemIconSpriteRect(item.icon);
			if (iconSprite != null) s.put("iconSprite", iconSprite);
		}
		s.put("cursed", item.cursed);
		s.put("cursedKnown", item.cursedKnown);
		s.put("displayName", Messages.titleCase(item.name()));
		putItemGlow(s, item);
		putItemSlotOverlays(s, item);
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

	private static boolean itemIconVisible(Item item) {
		return item.icon != -1
				&& (item.isIdentified() || (item instanceof Ring && ((Ring) item).isKnown()));
	}

	private static void putItemGlow(Map<String, Object> s, Item item) {
		ItemSprite.Glowing glow = item.glowing();
		if (glow == null) return;
		Map<String, Object> g = new LinkedHashMap<>();
		g.put("color", String.format("#%06X", glow.color & 0xFFFFFF));
		g.put("r", glow.red);
		g.put("g", glow.green);
		g.put("b", glow.blue);
		s.put("glow", g);
	}

	private static void putItemSlotOverlays(Map<String, Object> s, Item item) {
		String status = item.status();
		if (status != null && !status.isEmpty()) {
			s.put("status", status);
		}
		int buffedLvl = item.buffedVisiblyUpgraded();
		int trueLvl = item.visiblyUpgraded();
		if (buffedLvl != 0 || trueLvl != 0) {
			s.put("levelText", (buffedLvl > 0 ? "+" : "") + buffedLvl);
			if (trueLvl == buffedLvl || buffedLvl <= 0) {
				s.put("levelStyle", buffedLvl > 0 ? "upgraded" : "degraded");
			} else {
				s.put("levelStyle", buffedLvl > trueLvl ? "enhanced" : "warning");
			}
		}
	}

	/** Public for Item Showcase export / offline tooling. */
	public static Map<String, Object> itemIconSpriteRect(int icon) {
		if (icon < 0) return null;
		try {
			RectF r = ItemSpriteSheet.Icons.film.get(icon);
			if (r == null) return null;
			var tex = TextureCache.get(Assets.Sprites.ITEM_ICONS);
			return rectToPixels(r, tex.width, tex.height);
		} catch (Exception e) {
			return null;
		}
	}

	private static List<Map<String, Object>> buildInventory(Belongings b) {
		List<Map<String, Object>> sections = new ArrayList<>();
		if (b.backpack == null) return sections;
		for (Bag bag : b.getBags()) {
			Map<String, Object> section = bagSection(bag, bag == b.backpack);
			if (!((List<?>) section.get("items")).isEmpty()) {
				sections.add(section);
			}
		}
		return sections;
	}

	private static Map<String, Object> bagSection(Bag bag, boolean mainBackpack) {
		Map<String, Object> section = new LinkedHashMap<>();
		section.put("id", bagId(bag));
		section.put("name", Messages.titleCase(bag.name()));
		section.put("icon", bagIconRect(bag));
		section.put("capacity", bag.capacity());
		List<Map<String, Object>> items = new ArrayList<>();
		if (mainBackpack) {
			// Bag.iterator() recurses into nested bags; only list direct backpack contents.
			for (Item item : bag.items) {
				if (item instanceof Bag) continue;
				items.add(itemSlot(item));
			}
		} else {
			for (Item item : bag) {
				items.add(itemSlot(item));
			}
		}
		section.put("items", items);
		return section;
	}

	private static String bagId(Bag bag) {
		if (bag instanceof Belongings.Backpack) return "backpack";
		if (bag instanceof VelvetPouch) return "velvet_pouch";
		if (bag instanceof ScrollHolder) return "scroll_holder";
		if (bag instanceof MagicalHolster) return "magical_holster";
		if (bag instanceof PotionBandolier) return "potion_bandolier";
		return bag.getClass().getSimpleName();
	}

	private static Map<String, Object> bagIconRect(Bag bag) {
		Icons type;
		if (bag instanceof VelvetPouch) type = Icons.SEED_POUCH;
		else if (bag instanceof ScrollHolder) type = Icons.SCROLL_HOLDER;
		else if (bag instanceof MagicalHolster) type = Icons.WAND_HOLSTER;
		else if (bag instanceof PotionBandolier) type = Icons.POTION_BANDOLIER;
		else type = Icons.BACKPACK;
		return uiIconRect(Icons.get(type));
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

	private static Map<String, Object> buildHud() {
		Map<String, Object> hud = new LinkedHashMap<>();
		hud.put("feelingIcon", uiIconRect(Icons.get(Dungeon.level.feeling)));
		hud.put("goldIcon", uiIconRect(Icons.get(Icons.COIN_SML)));
		hud.put("energyIcon", uiIconRect(Icons.get(Icons.ENERGY_SML)));
		if (Challenges.activeChallenges() > 0) {
			hud.put("challengeIcon", uiIconRect(Icons.get(Icons.CHAL_COUNT)));
		}
		return hud;
	}

	private static Map<String, Object> uiIconRect(Image icon) {
		if (icon == null) return null;
		RectF r = icon.frame();
		if (r == null) return null;
		try {
			var tex = TextureCache.get(Assets.Interfaces.ICONS);
			return rectToPixels(r, tex.width, tex.height);
		} catch (Exception e) {
			return null;
		}
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

	private static List<Map<String, Object>> buildBuffs(Hero hero) {
		List<Map<String, Object>> list = new ArrayList<>();
		if (hero == Dungeon.hero && ShatteredPixelDungeon.scene() instanceof GameScene) {
			for (Buff b : BuffIndicator.visibleHeroBuffs()) {
				list.add(buffEntry(b));
			}
			return list;
		}
		for (Buff b : hero.buffs()) {
			if (b.icon() != BuffIndicator.NONE) {
				list.add(buffEntry(b));
			}
		}
		return list;
	}

	private static Map<String, Object> buffEntry(Buff b) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("name", b.getClass().getSimpleName());
		int icon = b.icon();
		m.put("icon", icon);
		Map<String, Object> sprite = buffSpriteRect(icon);
		if (sprite != null) m.put("sprite", sprite);

		BuffIcon iconView = new BuffIcon(b, true);
		b.tintIcon(iconView);
		Map<String, Object> tint = new LinkedHashMap<>();
		tint.put("r", iconView.rm);
		tint.put("g", iconView.gm);
		tint.put("b", iconView.bm);
		m.put("tint", tint);

		String iconText = b.iconTextDisplay();
		if (iconText != null && !iconText.isEmpty()) {
			m.put("iconText", iconText);
			if (b.type == Buff.buffType.POSITIVE) {
				m.put("iconTextColor", "#00FF00");
			} else if (b.type == Buff.buffType.NEGATIVE) {
				m.put("iconTextColor", "#FF0000");
			} else {
				m.put("iconTextColor", "#FFFFFF");
			}
		}

		switch (b.type) {
			case POSITIVE: m.put("buffType", "positive"); break;
			case NEGATIVE: m.put("buffType", "negative"); break;
			default: m.put("buffType", "neutral");
		}
		return m;
	}

	private static Map<String, Object> buildSheets() {
		Map<String, Object> sheets = new LinkedHashMap<>();
		try {
			var itemsTex = TextureCache.get(Assets.Sprites.ITEMS);
			Map<String, Object> items = new LinkedHashMap<>();
			items.put("path", Assets.Sprites.ITEMS);
			items.put("w", itemsTex.width);
			items.put("h", itemsTex.height);
			sheets.put("items", items);

			ensureLargeBuffFilm();
			Map<String, Object> buffs = new LinkedHashMap<>();
			buffs.put("path", Assets.Interfaces.BUFFS_LARGE);
			buffs.put("w", largeBuffTexW);
			buffs.put("h", largeBuffTexH);
			sheets.put("buffs", buffs);

			var iconsTex = TextureCache.get(Assets.Sprites.ITEM_ICONS);
			Map<String, Object> icons = new LinkedHashMap<>();
			icons.put("path", Assets.Sprites.ITEM_ICONS);
			icons.put("w", iconsTex.width);
			icons.put("h", iconsTex.height);
			sheets.put("icons", icons);

			var hudTex = TextureCache.get(Assets.Interfaces.ICONS);
			Map<String, Object> hud = new LinkedHashMap<>();
			hud.put("path", Assets.Interfaces.ICONS);
			hud.put("w", hudTex.width);
			hud.put("h", hudTex.height);
			sheets.put("hud", hud);
		} catch (Exception ignored) {
		}
		return sheets;
	}

	private static void ensureLargeBuffFilm() {
		if (largeBuffFilm == null) {
			var tex = TextureCache.get(Assets.Interfaces.BUFFS_LARGE);
			largeBuffTexW = tex.width;
			largeBuffTexH = tex.height;
			largeBuffFilm = new TextureFilm(tex, BuffIndicator.SIZE_LARGE, BuffIndicator.SIZE_LARGE);
		}
	}

	/** Public for Item Showcase export / offline tooling. */
	public static Map<String, Object> itemSpriteRect(int imageId) {
		RectF r = ItemSpriteSheet.film.get(imageId);
		if (r == null) return null;
		try {
			var tex = TextureCache.get(Assets.Sprites.ITEMS);
			return rectToPixels(r, tex.width, tex.height);
		} catch (Exception e) {
			return null;
		}
	}

	private static Map<String, Object> buffSpriteRect(int icon) {
		if (icon == BuffIndicator.NONE) return null;
		try {
			ensureLargeBuffFilm();
			RectF r = largeBuffFilm.get(icon);
			if (r == null) return null;
			return rectToPixels(r, largeBuffTexW, largeBuffTexH);
		} catch (Exception e) {
			return null;
		}
	}

	private static Map<String, Object> rectToPixels(RectF r, int texW, int texH) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("x", (int) (r.left * texW));
		m.put("y", (int) (r.top * texH));
		m.put("w", (int) (r.width() * texW));
		m.put("h", (int) (r.height() * texH));
		return m;
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
