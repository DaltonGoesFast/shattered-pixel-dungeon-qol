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

package com.shatteredpixel.shatteredpixeldungeon.playalong;

import com.google.gson.annotations.SerializedName;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.Statistics;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.DM300;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.DwarfKing;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Goo;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Pylon;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Tengu;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.KingsCrown;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.Bag;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.MagicalHolster;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.PotionBandolier;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.ScrollHolder;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.VelvetPouch;
import com.shatteredpixel.shatteredpixeldungeon.items.TengusMask;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.ClothArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.LeatherArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.MailArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.PlateArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.ScaleArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.Artifact;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.CloakOfShadows;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.HolyTome;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Food;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfIdentify;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfUpgrade;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.AssassinsBlade;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Crossbow;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Cudgel;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Dagger;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Dirk;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Flail;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Glaive;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Gloves;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Greatsword;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Katana;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Longsword;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Mace;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MagesStaff;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Quarterstaff;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Rapier;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Scimitar;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Shortsword;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Sickle;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Spear;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Sword;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.WarHammer;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Whip;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.WornShortsword;
import com.shatteredpixel.shatteredpixeldungeon.journal.Bestiary;
import com.shatteredpixel.shatteredpixeldungeon.levels.features.LevelTransition;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongCatchupItems.Parsed;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.SessionInfo;
import com.watabou.utils.Bundle;
import com.watabou.utils.DeviceCompat;
import com.watabou.utils.GameSettings;
import com.watabou.utils.Random;
import com.watabou.utils.Reflection;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Play Along catch-up: a new racer who drops in after Dalton has killed a region boss
 * starts on the first floor of that region with a plain kit, and cannot go back up.
 * The kit includes the containers a normal run would already own by the host's depth.
 */
public final class PlayAlongCatchup {

	private static final String PREF_GRANT = "play_along_catchup_grant";
	private static final String BUNDLE_KEY = "play_along_catchup";

	private static final int BIT_GOO = 1;
	private static final int BIT_TENGU = 2;
	private static final int BIT_DM300 = 4;
	private static final int BIT_KING = 8;

	private static volatile Config config = Config.defaults();
	private static volatile boolean configReady = false;
	private static int slainMask = 0;
	private static int epoch = 0;

	private static Offer pending;
	private static boolean active = false;
	private static int spawnDepth = 0;
	private static boolean rewardsUnlocked = false;
	private static String bossId = "";
	private static String kitSummary = "";
	private static String arrivalMessage = "";

	private PlayAlongCatchup() {}

	public static Config current() {
		if (!configReady) {
			loadFromDisk();
		}
		return config;
	}

	public static void applyConfigJson(String json) {
		config = Config.parse(json);
		configReady = true;
		System.out.println("[PlayAlongCatchup] config applied (enabled=" + config.enabled + ")");
	}

	/** New host run. Loaded saves restore epoch from the bundle instead. */
	public static void onNewHostRun() {
		epoch = 0;
		slainMask = 0;
	}

	/** Fresh dungeon init. Keeps a pending viewer offer so it can be applied after the hero exists. */
	public static void onDungeonInit() {
		active = false;
		spawnDepth = 0;
		rewardsUnlocked = false;
		bossId = "";
		kitSummary = "";
		arrivalMessage = "";
		slainMask = 0;
	}

	public static void noteBossSlain(int depth) {
		if (depth == 5) slainMask |= BIT_GOO;
		else if (depth == 10) slainMask |= BIT_TENGU;
		else if (depth == 15) slainMask |= BIT_DM300;
		else if (depth == 20) slainMask |= BIT_KING;
	}

	public static void onHostWipe() {
		epoch++;
	}

	public static void onClientWiped() {
		GameSettings.put(PREF_GRANT, "");
	}

	public static void fillSession(SessionInfo info) {
		if (info == null) return;
		Config cfg = current();
		info.catchupEnabled = cfg.enabled;
		info.catchupBoss = lastBossId();
		info.catchupEpoch = epoch;
		info.catchupConfig = cfg.toJson();
	}

	public static String lastBossId() {
		int mask = effectiveMask();
		if ((mask & BIT_KING) != 0) return "king";
		if ((mask & BIT_DM300) != 0) return "dm300";
		if ((mask & BIT_TENGU) != 0) return "tengu";
		if ((mask & BIT_GOO) != 0) return "goo";
		return "";
	}

	public static boolean isCatchupHero() {
		return active && spawnDepth > 1;
	}

	public static boolean blocksKillExpReward() {
		if (!active || rewardsUnlocked) return false;
		Config cfg = current();
		if (!cfg.expRewardsAfterFirstFloor) return false;
		return Dungeon.depth <= spawnDepth;
	}

	public static void noteDepth(int depth) {
		if (active && !rewardsUnlocked && depth > spawnDepth) {
			rewardsUnlocked = true;
		}
	}

	public static boolean blockAscent(LevelTransition transition) {
		if (!active || transition == null || spawnDepth <= 1) return false;
		if (transition.type != LevelTransition.Type.REGULAR_ENTRANCE
				&& transition.type != LevelTransition.Type.SURFACE) {
			return false;
		}
		return transition.destDepth < spawnDepth;
	}

	public static void clearPending() {
		pending = null;
	}

	/** Called when a viewer starts a new race (not a resume). */
	public static void prepareNewRacer(SessionInfo session) {
		pending = null;
		if (session == null || !session.active) return;
		Config cfg = Config.parse(session.catchupConfig);
		if (session.catchupConfig == null || session.catchupConfig.trim().isEmpty()) {
			cfg = current();
		}
		if (!cfg.enabled) return;
		String boss = session.catchupBoss == null ? "" : session.catchupBoss.trim().toLowerCase(Locale.ROOT);
		if (!cfg.hasBoss(boss)) return;
		if (cfg.oncePerSession && grantMatches(session.sessionId, session.catchupEpoch)) {
			System.out.println("[PlayAlongCatchup] " + session.sessionId + " already caught up this session");
			return;
		}
		pending = new Offer(boss, cfg, session.sessionId, session.catchupEpoch);
	}

	/**
	 * After {@link Dungeon#init()} on a new client run. Applies the kit and points
	 * {@link Dungeon#depth} at the spawn floor. Returns that floor, or 0 when catch-up is not used.
	 */
	public static int prepareSpawnDepth() {
		Offer offer = pending;
		pending = null;
		if (offer == null || !PlayAlongService.isClient()) return 0;
		try {
			Hero hero = Dungeon.hero;
			if (hero == null) return 0;
			Row row = offer.config.row(offer.boss);
			applyKit(hero, offer.boss, row, offer.config);
			active = true;
			spawnDepth = clamp(row.spawnFloor, 2, 24);
			bossId = offer.boss;
			rewardsUnlocked = !offer.config.expRewardsAfterFirstFloor;
			Dungeon.depth = spawnDepth;
			rememberGrant(offer.sessionId, offer.epoch);
			arrivalMessage = "Catch-up: you arrive on floor " + spawnDepth
					+ " (" + label(offer.boss) + "). The way back is sealed.";
			System.out.println("[PlayAlongCatchup] spawned " + offer.boss + " kit on floor " + spawnDepth);
			return spawnDepth;
		} catch (Throwable t) {
			System.out.println("[PlayAlongCatchup] kit failed, starting on floor 1: " + t);
			active = false;
			spawnDepth = 0;
			Dungeon.depth = 1;
			return 0;
		}
	}

	/** After the spawn floor itself has been generated. */
	public static void finishSpawn() {
		if (!active || spawnDepth <= 1) return;
		for (int floor = 1; floor < spawnDepth; floor++) {
			if (!Statistics.floorsExplored.containsKey(floor)) {
				Statistics.floorsExplored.put(floor, 1f);
			}
		}
		markBossesSeen(bossId);
		com.shatteredpixel.shatteredpixeldungeon.FirstDescent.pending = false;
	}

	public static void showArrivalMessage() {
		if (arrivalMessage == null || arrivalMessage.isEmpty()) return;
		String msg = arrivalMessage;
		arrivalMessage = "";
		com.shatteredpixel.shatteredpixeldungeon.utils.GLog.p(msg);
	}

	public static String describeForCommand() {
		if (isCatchupHero() && kitSummary != null && !kitSummary.isEmpty()) {
			return kitSummary;
		}
		String boss = lastBossId();
		if (boss.isEmpty() || !current().enabled) {
			return "Catch-up is off until Dalton has killed a region boss.";
		}
		return describeOffer(boss);
	}

	public static String describeOffer(String boss) {
		Config cfg = current();
		if (!cfg.hasBoss(boss)) {
			return "Catch-up is off until Dalton has killed a region boss.";
		}
		Row row = cfg.row(boss);
		StringBuilder sb = new StringBuilder();
		sb.append("Catch-up after ").append(label(boss));
		sb.append(": floor ").append(row.spawnFloor);
		sb.append(", lvl ").append(row.level);
		sb.append(", STR ").append(row.str);
		sb.append(", ").append(row.sou).append(" SoU");
		sb.append(", tier ").append(row.weaponTier).append("/").append(row.armorTier);
		sb.append(" weapon/armor, +0 wand or ring, healing, food, identify, ");
		sb.append(bagList(bagProgressDepth(PlayAlongService.getHostDepth(), row.spawnFloor, boss)));
		sb.append(".");
		String bonus = combinedBonus(cfg, row);
		if (!bonus.isEmpty()) {
			sb.append(" Bonus: ").append(bonus).append(".");
		}
		return sb.toString();
	}

	/** Builds a text kit for every region. Does not touch the live hero. */
	public static String debugReport() {
		StringBuilder sb = new StringBuilder();
		String[] bosses = new String[]{"goo", "tengu", "dm300", "king"};
		HeroClass cls = Dungeon.hero != null && Dungeon.hero.heroClass != null
				? Dungeon.hero.heroClass : HeroClass.WARRIOR;
		for (String boss : bosses) {
			Row row = current().row(boss);
			sb.append(label(boss)).append(": floor ").append(row.spawnFloor);
			sb.append(" lvl ").append(row.level);
			sb.append(" STR ").append(row.str);
			sb.append(" SoU x").append(row.sou);
			sb.append(" weapon ").append(weaponName(cls, row.weaponTier));
			sb.append(" armor ").append(armorName(row.armorTier));
			sb.append(" sigil lvl ").append(Math.max(0, (row.level - 1) / 2));
			String bonus = combinedBonus(current(), row);
			if (!bonus.isEmpty()) {
				List<Parsed> parsed = PlayAlongCatchupItems.parse(bonus, current().allowArtifacts);
				sb.append(" bonus");
				for (Parsed p : parsed) {
					sb.append(" [");
					if (p.skipped) sb.append("skip ").append(p.token);
					else sb.append(p.name).append(" x").append(p.quantity).append(" +").append(p.upgrade);
					sb.append("]");
				}
			}
			sb.append("\n");
		}
		return sb.toString().trim();
	}

	/** Debug runs only: put one region's kit on the current hero. */
	public static String debugApply(String boss) {
		if (!DeviceCompat.isDebug() && (Dungeon.hero == null || !Dungeon.debugRun)) {
			return "ERR:Catch-up test heroes are debug-mode only";
		}
		if (Dungeon.hero == null) return "ERR:No hero in a run";
		String id = boss == null ? "" : boss.trim().toLowerCase(Locale.ROOT);
		if (!current().hasBoss(id)) return "ERR:Unknown region " + boss;
		try {
			applyKit(Dungeon.hero, id, current().row(id), current());
			active = true;
			spawnDepth = current().row(id).spawnFloor;
			bossId = id;
			rewardsUnlocked = !current().expRewardsAfterFirstFloor;
			for (int floor = 1; floor < spawnDepth; floor++) {
				if (!Statistics.floorsExplored.containsKey(floor)) {
					Statistics.floorsExplored.put(floor, 1f);
				}
			}
			markBossesSeen(id);
			return "Applied " + label(id) + " catch-up kit. " + kitSummary;
		} catch (Throwable t) {
			return "ERR:" + (t.getMessage() != null ? t.getMessage() : t.toString());
		}
	}

	public static void store(Bundle bundle) {
		if (bundle == null) return;
		Bundle sub = new Bundle();
		sub.put("active", active);
		sub.put("spawn", spawnDepth);
		sub.put("rewards", rewardsUnlocked);
		sub.put("boss", bossId == null ? "" : bossId);
		sub.put("kit", kitSummary == null ? "" : kitSummary);
		sub.put("epoch", epoch);
		sub.put("slain", slainMask);
		bundle.put(BUNDLE_KEY, sub);
	}

	public static void restore(Bundle bundle) {
		active = false;
		spawnDepth = 0;
		rewardsUnlocked = false;
		bossId = "";
		kitSummary = "";
		if (bundle == null || !bundle.contains(BUNDLE_KEY)) return;
		Bundle sub = bundle.getBundle(BUNDLE_KEY);
		if (sub == null || sub.isNull()) return;
		active = sub.getBoolean("active");
		spawnDepth = sub.getInt("spawn");
		rewardsUnlocked = sub.getBoolean("rewards");
		bossId = sub.contains("boss") ? sub.getString("boss") : "";
		kitSummary = sub.contains("kit") ? sub.getString("kit") : "";
		if (sub.contains("epoch")) epoch = sub.getInt("epoch");
		if (sub.contains("slain")) slainMask = sub.getInt("slain");
	}

	private static void applyKit(Hero hero, String boss, Row row, Config cfg) {
		hero.setLevelForDebug(row.level);
		hero.STR = clamp(row.str, 10, 20);
		hero.updateHT(false);
		hero.HP = hero.HT;

		if (hero.heroClass == HeroClass.MAGE && hero.belongings.weapon instanceof MagesStaff) {
			MagesStaff staff = (MagesStaff) hero.belongings.weapon;
			int target = Math.max(0, row.weaponTier - 1);
			while (staff.level() < target && staff.level() < PlayAlongCatchupItems.MAX_UPGRADE) {
				staff.upgrade(false);
			}
			staff.identify();
			staff.enchantment = null;
			staff.cursed = false;
		} else {
			MeleeWeapon weapon = weaponFor(hero.heroClass, row.weaponTier);
			if (weapon != null) {
				prepareGear(weapon);
				KindOfWeaponSwap(hero, weapon);
			}
		}

		Armor armor = armorFor(row.armorTier);
		if (armor != null) {
			com.shatteredpixel.shatteredpixeldungeon.items.BrokenSeal seal = null;
			if (hero.belongings.armor != null) {
				seal = hero.belongings.armor.detachSeal();
			}
			prepareArmor(armor);
			hero.belongings.armor = armor;
			if (seal != null) {
				armor.affixSeal(seal);
				armor.inscribe(null);
			}
			armor.activate(hero);
		}

		raiseSignature(hero);

		// Bags before consumables so scrolls, potions, and thrown weapons collect into them.
		// These are the containers earlier shops sell; the way back up is sealed.
		int bagDepth = bagProgressDepth(PlayAlongService.getHostDepth(), row.spawnFloor, boss);
		ensureBags(hero.belongings.backpack, bagDepth);

		if (row.sou > 0) {
			ScrollOfUpgrade sou = new ScrollOfUpgrade();
			sou.quantity(row.sou);
			sou.identify().collect();
		}
		PotionOfHealing healing = new PotionOfHealing();
		healing.quantity(2);
		healing.identify().collect();
		new Food().quantity(1).collect();
		ScrollOfIdentify identify = new ScrollOfIdentify();
		identify.quantity(3);
		identify.identify().collect();

		giveRandomWandOrRing(hero);
		giveBonus(hero, combinedBonus(cfg, row), cfg.allowArtifacts);
		giveMasteryItems(hero, boss, row.armorTier);

		tuckIntoBags(hero.belongings.backpack);
		kitSummary = describeKit(hero, boss, row, bagDepth);
	}

	/**
	 * Tengu's Mask once the host has killed Tengu, and the King's Crown once the
	 * host has killed the Dwarf King. The player uses them from the backpack.
	 * The crown needs equipped armor, which the kit provides.
	 */
	static void giveMasteryItems(Hero hero, String boss, int armorTier) {
		if (hero == null || hero.belongings == null) return;
		if (bossAtLeast(boss, "tengu") && hero.belongings.getItem(TengusMask.class) == null) {
			new TengusMask().collect();
		}
		if (!bossAtLeast(boss, "king")) return;
		if (hero.belongings.armor() == null) {
			Armor armor = armorFor(armorTier);
			prepareArmor(armor);
			hero.belongings.armor = armor;
			armor.activate(hero);
		}
		if (hero.belongings.getItem(KingsCrown.class) == null) {
			new KingsCrown().collect();
		}
	}

	private static void KindOfWeaponSwap(Hero hero, MeleeWeapon weapon) {
		com.shatteredpixel.shatteredpixeldungeon.items.KindOfWeapon old = hero.belongings.weapon;
		hero.belongings.weapon = weapon;
		weapon.activate(hero);
		for (int s = 0; s < com.shatteredpixel.shatteredpixeldungeon.QuickSlot.SIZE; s++) {
			if (Dungeon.quickslot.getItem(s) == old) {
				Dungeon.quickslot.setSlot(s, weapon);
			}
		}
	}

	private static void raiseSignature(Hero hero) {
		int target = Math.max(0, (hero.lvl - 1) / 2);
		if (hero.belongings.artifact instanceof CloakOfShadows || hero.belongings.artifact instanceof HolyTome) {
			Artifact artifact = (Artifact) hero.belongings.artifact;
			int cap = Math.min(10, target);
			if (artifact.level() < cap) {
				artifact.upgrade(cap - artifact.level());
			}
			artifact.identify();
			artifact.cursed = false;
		}
	}

	private static void giveRandomWandOrRing(Hero hero) {
		ArrayList<Class<? extends Item>> pool = new ArrayList<>();
		addClasses(pool, com.shatteredpixel.shatteredpixeldungeon.items.Generator.Category.WAND.classes);
		addClasses(pool, com.shatteredpixel.shatteredpixeldungeon.items.Generator.Category.RING.classes);
		if (pool.isEmpty()) return;
		Class<? extends Item> pick = pool.get(Random.Int(pool.size()));
		Item item = Reflection.newInstance(pick);
		if (item == null) return;
		item.level(0);
		item.cursed = false;
		if (item instanceof Weapon) {
			((Weapon) item).enchantment = null;
		}
		item.identify().collect();
	}

	@SuppressWarnings("unchecked")
	private static void addClasses(List<Class<? extends Item>> pool, Class<?>[] classes) {
		if (classes == null) return;
		for (Class<?> cls : classes) {
			if (cls == null || !Item.class.isAssignableFrom(cls)) continue;
			String name = cls.getName();
			if (name.contains(".exotic.")) continue;
			pool.add((Class<? extends Item>) cls);
		}
	}

	private static void giveBonus(Hero hero, String text, boolean allowArtifacts) {
		List<Parsed> parsed = PlayAlongCatchupItems.parse(text, allowArtifacts);
		for (Parsed p : parsed) {
			if (p.skipped || p.type == null) continue;
			try {
				Item item = Reflection.newInstance(p.type);
				if (item == null) {
					System.out.println("[PlayAlongCatchup] could not build " + p.name);
					continue;
				}
				boolean stackable = item.stackable;
				int count = p.giveQuantity(stackable);
				if (stackable && count > 1) item.quantity(count);
				if (p.upgrade > 0 && (item instanceof Weapon || item instanceof Armor
						|| item instanceof com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand
						|| item instanceof com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring)) {
					if (item instanceof MagesStaff) {
						while (item.level() < p.upgrade) {
							((MagesStaff) item).upgrade(false);
						}
					} else {
						item.level(p.upgrade);
					}
				}
				if (item instanceof Weapon) {
					((Weapon) item).enchantment = null;
				}
				if (item instanceof Armor) {
					((Armor) item).inscribe(null);
				}
				item.cursed = false;
				item.identify().collect();
			} catch (Throwable t) {
				System.out.println("[PlayAlongCatchup] skipped bonus item '" + p.token + "': " + t);
			}
		}
	}

	private static void prepareGear(MeleeWeapon weapon) {
		weapon.identify();
		weapon.enchantment = null;
		weapon.cursed = false;
		weapon.level(0);
	}

	private static void prepareArmor(Armor armor) {
		armor.identify();
		armor.inscribe(null);
		armor.cursed = false;
		armor.level(0);
	}

	private static String describeKit(Hero hero, String boss, Row row, int bagDepth) {
		String weapon = hero.belongings.weapon != null ? hero.belongings.weapon.getClass().getSimpleName() : "none";
		String armor = hero.belongings.armor != null ? hero.belongings.armor.getClass().getSimpleName() : "none";
		return "Catch-up (" + label(boss) + "): floor " + row.spawnFloor
				+ ", level " + hero.lvl + ", STR " + hero.STR
				+ ", " + row.sou + " upgrade scrolls, "
				+ weapon + " / " + armor
				+ ", bags [" + bagList(bagDepth) + "]"
				+ ". Upstairs is sealed. Donor EXP "
				+ (current().expRewardsAfterFirstFloor && !rewardsUnlocked
				? "starts after you go down." : "is on.");
	}

	/**
	 * Containers a normal run already owns by {@code depth}.
	 * The hero starts with a velvet pouch. Shops on 6, 11, and 16 each sell one more,
	 * in that order: scroll holder, potion bandolier, magical holster.
	 * Leaving floor 16 (or being past floor 20, including the ascent) means all four.
	 */
	static List<Class<? extends Item>> bagsForDepth(int depth) {
		List<Class<? extends Item>> bags = new ArrayList<>();
		bags.add(VelvetPouch.class);
		if (depth > 20) {
			bags.add(ScrollHolder.class);
			bags.add(PotionBandolier.class);
			bags.add(MagicalHolster.class);
			return bags;
		}
		if (depth > 6) bags.add(ScrollHolder.class);
		if (depth > 11) bags.add(PotionBandolier.class);
		if (depth > 16) bags.add(MagicalHolster.class);
		return bags;
	}

	/**
	 * Depth used to pick bags. The host's current floor wins when they are ahead of the
	 * drop-in floor. After the Dwarf King, progress is at least 21 so an ascent
	 * (host floor back above the drop-in floor) still grants every bag.
	 */
	static int bagProgressDepth(int hostDepth, int spawnFloor, String boss) {
		int progress = Math.max(hostDepth, Math.max(spawnFloor, 1));
		if (bossAtLeast(boss, "king")) {
			progress = Math.max(progress, 21);
		}
		return progress;
	}

	static String bagList(int depth) {
		StringBuilder sb = new StringBuilder();
		for (Class<? extends Item> type : bagsForDepth(depth)) {
			if (sb.length() > 0) sb.append(", ");
			sb.append(type.getSimpleName());
		}
		return sb.toString();
	}

	/** Give any missing bags for {@code depth} and remember them so later shops do not sell a copy. */
	static void ensureBags(Bag pack, int depth) {
		if (pack == null) return;
		for (Class<? extends Item> type : bagsForDepth(depth)) {
			if (owns(pack, type)) {
				markBagFound(type);
				continue;
			}
			Item bag = Reflection.newInstance(type);
			if (bag == null) continue;
			if (bag.collect(pack)) {
				markBagFound(type);
			}
		}
	}

	/** Pull loose kit items into bags that were granted after those items were collected. */
	static void tuckIntoBags(Bag pack) {
		if (pack == null) return;
		for (Item item : new ArrayList<>(pack.items)) {
			if (item instanceof Bag) {
				((Bag) item).grabItems(pack);
			}
		}
	}

	private static boolean owns(Bag pack, Class<? extends Item> type) {
		for (Item item : pack) {
			if (type.isInstance(item)) return true;
		}
		return false;
	}

	private static void markBagFound(Class<? extends Item> type) {
		if (type == VelvetPouch.class) Dungeon.LimitedDrops.VELVET_POUCH.drop();
		else if (type == ScrollHolder.class) Dungeon.LimitedDrops.SCROLL_HOLDER.drop();
		else if (type == PotionBandolier.class) Dungeon.LimitedDrops.POTION_BANDOLIER.drop();
		else if (type == MagicalHolster.class) Dungeon.LimitedDrops.MAGICAL_HOLSTER.drop();
	}

	/**
	 * Dropped-state of the four containers just before skipped floors are generated.
	 * Null when those floors are not being sealed.
	 */
	private static boolean[] sealedBagDrops;

	/**
	 * Skipped floors are generated before the spawn floor, and their shops would
	 * otherwise claim the next bag via {@code LimitedDrops}. Those floors are behind
	 * the sealed stair, so hold every bag as already dropped until generation finishes.
	 */
	public static void sealShopBagsForPregen() {
		if (sealedBagDrops != null) return;
		Dungeon.LimitedDrops[] bags = bagDrops();
		sealedBagDrops = new boolean[bags.length];
		for (int i = 0; i < bags.length; i++) {
			sealedBagDrops[i] = bags[i].dropped();
			bags[i].drop();
		}
	}

	/** Restore bags the racer does not own, so the spawn floor and later shops can still sell them. */
	public static void unsealShopBagsAfterPregen() {
		if (sealedBagDrops == null) return;
		Dungeon.LimitedDrops[] bags = bagDrops();
		for (int i = 0; i < bags.length && i < sealedBagDrops.length; i++) {
			if (!sealedBagDrops[i]) bags[i].count = 0;
		}
		sealedBagDrops = null;
	}

	private static Dungeon.LimitedDrops[] bagDrops() {
		return new Dungeon.LimitedDrops[] {
				Dungeon.LimitedDrops.VELVET_POUCH,
				Dungeon.LimitedDrops.SCROLL_HOLDER,
				Dungeon.LimitedDrops.POTION_BANDOLIER,
				Dungeon.LimitedDrops.MAGICAL_HOLSTER
		};
	}

	private static void markBossesSeen(String boss) {
		if (!bossAtLeast(boss, "goo")) return;
		Bestiary.setSeen(Goo.class);
		if (!bossAtLeast(boss, "tengu")) return;
		Bestiary.setSeen(Tengu.class);
		if (!bossAtLeast(boss, "dm300")) return;
		Bestiary.setSeen(DM300.class);
		Bestiary.setSeen(Pylon.class);
		if (!bossAtLeast(boss, "king")) return;
		Bestiary.setSeen(DwarfKing.class);
	}

	private static boolean bossAtLeast(String boss, String min) {
		return rank(boss) >= rank(min);
	}

	private static int rank(String boss) {
		if ("king".equals(boss)) return 4;
		if ("dm300".equals(boss)) return 3;
		if ("tengu".equals(boss)) return 2;
		if ("goo".equals(boss)) return 1;
		return 0;
	}

	private static int effectiveMask() {
		int mask = slainMask;
		int[] scores = Statistics.bossScores;
		if (scores != null) {
			if (scores.length > 0 && scores[0] > 0) mask |= BIT_GOO;
			if (scores.length > 1 && scores[1] > 0) mask |= BIT_TENGU;
			if (scores.length > 2 && scores[2] > 0) mask |= BIT_DM300;
			if (scores.length > 3 && scores[3] > 0) mask |= BIT_KING;
		}
		int depth = 1;
		try {
			depth = Math.max(Dungeon.depth, Statistics.deepestFloor);
		} catch (Throwable ignored) {
		}
		if (depth >= 21) mask |= BIT_GOO | BIT_TENGU | BIT_DM300 | BIT_KING;
		else if (depth >= 16) mask |= BIT_GOO | BIT_TENGU | BIT_DM300;
		else if (depth >= 11) mask |= BIT_GOO | BIT_TENGU;
		else if (depth >= 6) mask |= BIT_GOO;
		return mask;
	}

	private static String label(String boss) {
		if ("dm300".equals(boss)) return "DM-300";
		if ("tengu".equals(boss)) return "Tengu";
		if ("king".equals(boss)) return "Dwarf King";
		if ("goo".equals(boss)) return "Goo";
		return boss;
	}

	private static String weaponName(HeroClass cls, int tier) {
		if (cls == HeroClass.MAGE) return "MagesStaff+" + Math.max(0, tier - 1);
		MeleeWeapon weapon = weaponFor(cls, tier);
		return weapon == null ? "none" : weapon.getClass().getSimpleName();
	}

	private static String armorName(int tier) {
		Armor armor = armorFor(tier);
		return armor == null ? "none" : armor.getClass().getSimpleName();
	}

	private static MeleeWeapon weaponFor(HeroClass cls, int tier) {
		int t = clamp(tier, 1, 5);
		if (cls == null) cls = HeroClass.WARRIOR;
		switch (cls) {
			case MAGE:
				return null;
			case ROGUE:
				switch (t) {
					case 1: return new Dagger();
					case 2: return new Dirk();
					case 3: return new Sickle();
					case 4: return new AssassinsBlade();
					default: return new Glaive();
				}
			case HUNTRESS:
				switch (t) {
					case 1: return new Gloves();
					case 2: return new Spear();
					case 3: return new Whip();
					case 4: return new Crossbow();
					default: return new Glaive();
				}
			case DUELIST:
				switch (t) {
					case 1: return new Rapier();
					case 2: return new Dirk();
					case 3: return new Scimitar();
					case 4: return new Katana();
					default: return new Greatsword();
				}
			case CLERIC:
				switch (t) {
					case 1: return new Cudgel();
					case 2: return new Quarterstaff();
					case 3: return new Mace();
					case 4: return new Flail();
					default: return new WarHammer();
				}
			case WARRIOR:
			default:
				switch (t) {
					case 1: return new WornShortsword();
					case 2: return new Shortsword();
					case 3: return new Sword();
					case 4: return new Longsword();
					default: return new Greatsword();
				}
		}
	}

	private static Armor armorFor(int tier) {
		switch (clamp(tier, 1, 5)) {
			case 1: return new ClothArmor();
			case 2: return new LeatherArmor();
			case 3: return new MailArmor();
			case 4: return new ScaleArmor();
			default: return new PlateArmor();
		}
	}

	private static String combinedBonus(Config cfg, Row row) {
		String all = cfg.allRegionsBonusItems == null ? "" : cfg.allRegionsBonusItems.trim();
		String rowBonus = row.bonusItems == null ? "" : row.bonusItems.trim();
		if (all.isEmpty()) return rowBonus;
		if (rowBonus.isEmpty()) return all;
		return all + ", " + rowBonus;
	}

	private static boolean grantMatches(String sessionId, int sessionEpoch) {
		if (sessionId == null || sessionId.isEmpty()) return false;
		String stored = GameSettings.getString(PREF_GRANT, "");
		return stored != null && stored.equals(grantKey(sessionId, sessionEpoch));
	}

	private static void rememberGrant(String sessionId, int sessionEpoch) {
		if (sessionId == null || sessionId.isEmpty()) return;
		GameSettings.put(PREF_GRANT, grantKey(sessionId, sessionEpoch));
	}

	private static String grantKey(String sessionId, int sessionEpoch) {
		return sessionId + "#" + sessionEpoch;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static void loadFromDisk() {
		synchronized (PlayAlongCatchup.class) {
			if (configReady) return;
			configReady = true;
			File file = findConfigFile();
			if (file == null) {
				config = Config.defaults();
				return;
			}
			try {
				String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
				com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(text).getAsJsonObject();
				if (root.has("play_along_catchup") && root.get("play_along_catchup").isJsonObject()) {
					config = Config.parse(root.get("play_along_catchup").toString());
				} else {
					config = Config.defaults();
				}
			} catch (Throwable t) {
				System.out.println("[PlayAlongCatchup] bad points config, using defaults: " + t);
				config = Config.defaults();
			}
		}
	}

	private static File findConfigFile() {
		File cursor = new File(System.getProperty("user.dir", "."));
		for (int i = 0; i < 4 && cursor != null; i++) {
			File nested = new File(cursor, "Lastest UI/points_config.json");
			if (nested.isFile()) return nested;
			File flat = new File(cursor, "points_config.json");
			if (flat.isFile()) return flat;
			cursor = cursor.getParentFile();
		}
		return null;
	}

	private static final class Offer {
		final String boss;
		final Config config;
		final String sessionId;
		final int epoch;

		Offer(String boss, Config config, String sessionId, int epoch) {
			this.boss = boss;
			this.config = config;
			this.sessionId = sessionId == null ? "" : sessionId;
			this.epoch = epoch;
		}
	}

	public static final class Config {
		public boolean enabled = true;

		@SerializedName("exp_rewards_after_first_floor")
		public boolean expRewardsAfterFirstFloor = true;

		@SerializedName("once_per_session")
		public boolean oncePerSession = true;

		@SerializedName("allow_artifacts")
		public boolean allowArtifacts = false;

		public Row goo = Row.goo();
		public Row tengu = Row.tengu();
		public Row dm300 = Row.dm300();
		public Row king = Row.king();

		@SerializedName("all_regions_bonus_items")
		public String allRegionsBonusItems = "";

		public static Config defaults() {
			Config cfg = new Config();
			cfg.goo = Row.goo();
			cfg.tengu = Row.tengu();
			cfg.dm300 = Row.dm300();
			cfg.king = Row.king();
			return cfg;
		}

		public static Config parse(String json) {
			if (json == null || json.trim().isEmpty()) return defaults();
			try {
				Config parsed = PlayAlongProtocol.GSON.fromJson(json, Config.class);
				if (parsed == null) return defaults();
				return parsed.sanitized();
			} catch (Throwable t) {
				System.out.println("[PlayAlongCatchup] broken config, using defaults: " + t);
				return defaults();
			}
		}

		public String toJson() {
			return PlayAlongProtocol.GSON.toJson(sanitized());
		}

		public boolean hasBoss(String boss) {
			return "goo".equals(boss) || "tengu".equals(boss) || "dm300".equals(boss) || "king".equals(boss);
		}

		public Row row(String boss) {
			Config safe = sanitized();
			if ("tengu".equals(boss)) return safe.tengu;
			if ("dm300".equals(boss)) return safe.dm300;
			if ("king".equals(boss)) return safe.king;
			return safe.goo;
		}

		public Config sanitized() {
			Config cfg = new Config();
			cfg.enabled = enabled;
			cfg.expRewardsAfterFirstFloor = expRewardsAfterFirstFloor;
			cfg.oncePerSession = oncePerSession;
			cfg.allowArtifacts = allowArtifacts;
			cfg.allRegionsBonusItems = clip(allRegionsBonusItems);
			cfg.goo = goo == null ? Row.goo() : goo.sanitized(Row.goo());
			cfg.tengu = tengu == null ? Row.tengu() : tengu.sanitized(Row.tengu());
			cfg.dm300 = dm300 == null ? Row.dm300() : dm300.sanitized(Row.dm300());
			cfg.king = king == null ? Row.king() : king.sanitized(Row.king());
			return cfg;
		}

		private static String clip(String raw) {
			if (raw == null) return "";
			String trimmed = raw.trim();
			if (trimmed.length() > 240) return trimmed.substring(0, 240);
			return trimmed;
		}
	}

	public static final class Row {
		@SerializedName("spawn_floor")
		public int spawnFloor;
		public int level;
		public int str;
		public int sou;
		@SerializedName("weapon_tier")
		public int weaponTier;
		@SerializedName("armor_tier")
		public int armorTier;
		@SerializedName("bonus_items")
		public String bonusItems = "";

		static Row goo() {
			return make(6, 6, 11, 2, 2, 2);
		}

		static Row tengu() {
			return make(11, 11, 12, 4, 3, 3);
		}

		static Row dm300() {
			return make(16, 16, 13, 6, 4, 4);
		}

		static Row king() {
			return make(21, 21, 14, 8, 4, 4);
		}

		private static Row make(int floor, int level, int str, int sou, int weapon, int armor) {
			Row row = new Row();
			row.spawnFloor = floor;
			row.level = level;
			row.str = str;
			row.sou = sou;
			row.weaponTier = weapon;
			row.armorTier = armor;
			row.bonusItems = "";
			return row;
		}

		Row sanitized(Row fallback) {
			Row row = new Row();
			row.spawnFloor = spawnFloor >= 2 && spawnFloor <= 24 ? spawnFloor : fallback.spawnFloor;
			row.level = level >= 1 && level <= 30 ? level : fallback.level;
			row.str = str >= 10 && str <= 20 ? str : fallback.str;
			row.sou = sou >= 0 && sou <= 20 ? sou : fallback.sou;
			row.weaponTier = weaponTier >= 1 && weaponTier <= 5 ? weaponTier : fallback.weaponTier;
			row.armorTier = armorTier >= 1 && armorTier <= 5 ? armorTier : fallback.armorTier;
			row.bonusItems = bonusItems == null ? "" : bonusItems.trim();
			if (row.bonusItems.length() > 240) row.bonusItems = row.bonusItems.substring(0, 240);
			return row;
		}
	}
}
