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

package com.shatteredpixel.shatteredpixeldungeon;

import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.SpawnScaled;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Acidic;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Albino;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.ArmoredBrute;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Bandit;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Bat;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Brute;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.CausticSlime;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Crab;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.DM100;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.DM200;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.DM201;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Elemental;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Eye;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Ghoul;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Gnoll;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.GnollExile;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Golem;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Guard;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.HermitCrab;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.MobSpawner;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Monk;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Necromancer;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Rat;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Scorpio;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Senior;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Shaman;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Skeleton;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Slime;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Snake;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.SpectralNecromancer;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Spinner;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Succubus;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Swarm;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Thief;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Warlock;
import com.watabou.utils.Random;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Kin / Dissonance roster and frozen Default spawn scaling.
 * Chat summons keep their own live scaler; they only share {@link #nativeDepthForChatName}.
 */
public final class PactRoster {

	private static final int ROTATION_SIZE = 8;
	private static final float EXOTIC_CHANCE = 0.10f;
	/** Salt so Kin species rolls do not collide with other seedCurDepth consumers. */
	private static final long KIN_SEED_SALT = 0x4B696E21L; // "Kin!"

	// Frozen Default preset — not live SpawnScaleConfig.
	private static final float EARLY_HP_MIN = 0.25f;
	private static final float LATE_HP_MIN = 0.15f;
	private static final float SCORPIO_HP_MIN = 0.15f;
	private static final float EARLY_SEWERS_DMG_MULT = 1.00f;
	private static final float LATE_SEWERS_DMG_MULT = 0.35f;
	private static final float EARLY_SEWERS_DMG_FLOOR = 0.05f;
	private static final float LATE_SEWERS_DMG_FLOOR = 0.08f;
	private static final float PRISON_PLUS_DMG_FLOOR = 0.15f;
	private static final float EARLY_DR_MULT = 0.70f;
	private static final float LATE_DR_MULT = 0.40f;
	private static final float DR_FLOOR = 0.15f;
	private static final int EARLY_NATIVE_MAX = 15;

	/** Curated Kin base type per regular floor. */
	private static final HashMap<Integer, Class<? extends Mob>> KIN_TABLE = new HashMap<>();
	static {
		KIN_TABLE.put(1, Rat.class);
		KIN_TABLE.put(2, Gnoll.class);
		KIN_TABLE.put(3, Crab.class);
		KIN_TABLE.put(4, Slime.class);
		KIN_TABLE.put(6, Skeleton.class);
		KIN_TABLE.put(7, Thief.class);
		KIN_TABLE.put(8, DM100.class);
		KIN_TABLE.put(9, Necromancer.class);
		KIN_TABLE.put(11, Bat.class);
		KIN_TABLE.put(12, Brute.class);
		KIN_TABLE.put(13, Shaman.class);
		KIN_TABLE.put(14, Spinner.class);
		KIN_TABLE.put(16, Ghoul.class);
		KIN_TABLE.put(17, Elemental.class);
		KIN_TABLE.put(18, Warlock.class);
		KIN_TABLE.put(19, Monk.class);
		KIN_TABLE.put(21, Succubus.class);
		KIN_TABLE.put(22, Eye.class);
		KIN_TABLE.put(23, Scorpio.class);
		KIN_TABLE.put(24, Scorpio.class);
	}

	@SuppressWarnings("unchecked")
	private static final Class<? extends Mob>[] DISSONANCE_POOL = new Class[]{
			Rat.class, Snake.class, Gnoll.class, Swarm.class, Crab.class, Slime.class,
			Skeleton.class, Thief.class, DM100.class, Guard.class, Necromancer.class,
			Bat.class, Brute.class, Shaman.class, Spinner.class, DM200.class, Ghoul.class,
			Elemental.class, Warlock.class, Monk.class, Golem.class,
			Succubus.class, Eye.class, Scorpio.class
	};

	@SuppressWarnings("unchecked")
	private static final Class<? extends Mob>[][] REGION_EXOTICS = new Class[][]{
			{ Albino.class, GnollExile.class, HermitCrab.class, CausticSlime.class },
			{ Bandit.class, SpectralNecromancer.class },
			{ ArmoredBrute.class, DM201.class },
			{ Elemental.ChaosElemental.class, Senior.class },
			{ Acidic.class }
	};

	/** Earliest home depth per species (shared with chat name lookup). */
	private static final HashMap<Class<? extends Mob>, Integer> NATIVE_DEPTH = new HashMap<>();
	private static final HashMap<String, Integer> NATIVE_DEPTH_BY_NAME = new HashMap<>();
	static {
		putNative(Rat.class, "rat", 1);
		putNative(Albino.class, "albino", 1);
		putNative(Snake.class, "snake", 1);
		putNative(Gnoll.class, "gnoll", 2);
		putNative(GnollExile.class, null, 2);
		putNative(Crab.class, "crab", 3);
		putNative(HermitCrab.class, null, 3);
		putNative(Swarm.class, "swarm", 3);
		putNative(Slime.class, "slime", 4);
		putNative(CausticSlime.class, null, 4);
		putNative(Thief.class, "thief", 4);
		putNative(Bandit.class, null, 4);
		putNative(Skeleton.class, "skeleton", 6);
		putNative(DM100.class, "dm100", 7);
		putNative(Guard.class, "guard", 7);
		putNative(Necromancer.class, "necromancer", 8);
		putNative(SpectralNecromancer.class, null, 8);
		putNative(Bat.class, "bat", 11);
		putNative(Brute.class, "brute", 11);
		putNative(ArmoredBrute.class, null, 11);
		putNative(Shaman.class, "shaman", 11);
		putNative(Spinner.class, "spinner", 12);
		putNative(DM200.class, null, 13);
		putNative(DM201.class, null, 13);
		putNative(Ghoul.class, "ghoul", 14);
		putNative(Elemental.class, "elemental", 16);
		putNative(Warlock.class, "warlock", 16);
		putNative(Monk.class, "monk", 17);
		putNative(Senior.class, null, 17);
		putNative(Golem.class, "golem", 18);
		putNative(Succubus.class, "succubus", 19);
		putNative(Eye.class, "eye", 21);
		putNative(Scorpio.class, "scorpio", 23);
		putNative(Acidic.class, null, 23);
	}

	private static void putNative( Class<? extends Mob> cls, String chatName, int depth ){
		NATIVE_DEPTH.put(cls, depth);
		if (chatName != null){
			NATIVE_DEPTH_BY_NAME.put(chatName, depth);
		}
	}

	private PactRoster() {}

	public static boolean overridesRotation( int depth ){
		if (depth < 1 || depth > 24 || Dungeon.bossLevel(depth)) return false;
		return Dungeon.isModified(Modifiers.KIN) || Dungeon.isModified(Modifiers.DISSONANCE);
	}

	public static ArrayList<Class<? extends Mob>> rotation( int depth ){
		boolean kin = Dungeon.isModified(Modifiers.KIN);
		boolean dissonance = Dungeon.isModified(Modifiers.DISSONANCE);

		if (kin){
			Class<? extends Mob> species = seededFloorSpecies(depth, dissonance);
			return fillRotation(species);
		}

		// Dissonance alone: mixed roster, no Rat Skull alt swaps
		ArrayList<Class<? extends Mob>> list = new ArrayList<>(ROTATION_SIZE);
		for (int i = 0; i < ROTATION_SIZE; i++){
			list.add(resolveConcrete(Random.element(DISSONANCE_POOL)));
		}
		return list;
	}

	/**
	 * One species for the floor (Kin, or Kin+Dissonance).
	 * Seeded so refilling {@code mobsToSpawn} cannot change the floor.
	 */
	private static Class<? extends Mob> seededFloorSpecies( int depth, boolean dissonance ){
		Random.pushGenerator(Dungeon.seedForDepth(depth, Dungeon.branch) + KIN_SEED_SALT);
		try {
			Class<? extends Mob> base;
			if (dissonance){
				base = Random.element(DISSONANCE_POOL);
			} else {
				base = KIN_TABLE.get(depth);
			}
			if (base == null){
				base = Rat.class;
			}
			if (Random.Float() < EXOTIC_CHANCE){
				return exoticFor(base, depth);
			}
			return base;
		} finally {
			Random.popGenerator();
		}
	}

	private static Class<? extends Mob> exoticFor( Class<? extends Mob> base, int depth ){
		if (base == Elemental.class){
			return Elemental.ChaosElemental.class;
		}
		Class<? extends Mob> alt = MobSpawner.RARE_ALTS.get(base);
		if (alt != null){
			return alt;
		}
		int region = Math.min(4, Math.max(0, (depth - 1) / 5));
		Class<? extends Mob>[] pool = REGION_EXOTICS[region];
		return Random.element(pool);
	}

	private static ArrayList<Class<? extends Mob>> fillRotation( Class<? extends Mob> species ){
		ArrayList<Class<? extends Mob>> list = new ArrayList<>(ROTATION_SIZE);
		for (int i = 0; i < ROTATION_SIZE; i++){
			list.add(resolveConcrete(species));
		}
		return list;
	}

	/** Shaman / Elemental markers become a concrete color; other classes pass through. */
	private static Class<? extends Mob> resolveConcrete( Class<? extends Mob> cls ){
		if (cls == Shaman.class){
			return Shaman.random();
		}
		if (cls == Elemental.class){
			return randomElementalColor();
		}
		return cls;
	}

	/** Fire / frost / shock only — no Rat Skull chaos roll. */
	private static Class<? extends Mob> randomElementalColor(){
		float roll = Random.Float();
		if (roll < 0.4f){
			return Elemental.FireElemental.class;
		} else if (roll < 0.8f){
			return Elemental.FrostElemental.class;
		} else {
			return Elemental.ShockElemental.class;
		}
	}

	public static Integer nativeDepth( Class<? extends Mob> cls ){
		if (cls == null) return null;
		Class<?> c = cls;
		while (c != null && Mob.class.isAssignableFrom(c)){
			Integer d = NATIVE_DEPTH.get(c);
			if (d != null) return d;
			c = c.getSuperclass();
		}
		return null;
	}

	/** Shared with desktop chat spawn so name→depth cannot drift from class map. */
	public static Integer nativeDepthForChatName( String name ){
		if (name == null) return null;
		return NATIVE_DEPTH_BY_NAME.get(name);
	}

	public static int chapterKillExp( int depth ){
		switch (Math.max(0, (depth - 1) / 5)){
			case 0: return 4;
			case 1: return 6;
			case 2: return 8;
			case 3: return 11;
			default: return 14;
		}
	}

	/**
	 * Dissonance post-create: scale late-on-early combat down, or bump early-on-late rewards.
	 * Never scales combat up. No paralysis.
	 */
	public static void applyDissonance( Mob mob ){
		if (mob == null) return;
		Integer nativeDepth = nativeDepth(mob.getClass());
		if (nativeDepth == null) return;

		if (Dungeon.depth < nativeDepth){
			scaleDown(mob, nativeDepth);
		} else if (Dungeon.depth > nativeDepth){
			mob.EXP = chapterKillExp(Dungeon.depth);
			mob.maxLvl = Hero.MAX_LEVEL - 1;
		}
	}

	private static void scaleDown( Mob mob, int nativeDepth ){
		boolean scorpio = Scorpio.class.isAssignableFrom(mob.getClass());
		boolean early = nativeDepth <= EARLY_NATIVE_MAX;
		float minScale = scorpio ? SCORPIO_HP_MIN : (early ? EARLY_HP_MIN : LATE_HP_MIN);
		float scale = Math.max(minScale, (float) Dungeon.depth / nativeDepth);
		scale = Math.max(0.01f, scale);

		float damageFactor;
		if (Dungeon.depth <= 5){
			float floor = early ? EARLY_SEWERS_DMG_FLOOR : LATE_SEWERS_DMG_FLOOR;
			float mult = early ? EARLY_SEWERS_DMG_MULT : LATE_SEWERS_DMG_MULT;
			damageFactor = Math.max(floor, scale * mult);
		} else {
			damageFactor = Math.max(PRISON_PLUS_DMG_FLOOR, scale);
		}
		float drMult = early ? EARLY_DR_MULT : LATE_DR_MULT;
		float drFactor = Math.max(DR_FLOOR, scale * drMult);

		int newHT = Math.max(1, Math.round(mob.HT * scale));
		int newHP = Math.max(1, Math.round(mob.HP * scale));
		mob.HT = newHT;
		mob.HP = newHP;
		SpawnScaled.affect(mob, scale, damageFactor, drFactor);
	}
}
