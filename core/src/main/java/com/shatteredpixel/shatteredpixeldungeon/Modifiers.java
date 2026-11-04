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

import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.ChatSpawned;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.EvolutionTracker;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.GraveShade;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.MirrorImage;
import com.shatteredpixel.shatteredpixeldungeon.effects.FloatingText;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.elixirs.ElixirOfMight;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfMight;
import com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite;
import com.watabou.utils.Bundle;
import com.watabou.utils.Random;

/**
 * Run-wide pacts (UI) / modifiers (code). Parallel to {@link Challenges}; does not
 * affect challenge count, Champion badges, or score multipliers.
 */
public class Modifiers {

	// Stable bit assignment in design order — do not renumber.
	public static final int HONOR           = 1;
	public static final int GLASS           = 2;
	public static final int FRAILTY         = 4;
	public static final int DEATH           = 8;
	public static final int DEVOTION        = 16;
	public static final int SPITE           = 32;
	public static final int SOUL            = 64;
	public static final int KIN             = 128;
	public static final int DISSONANCE      = 256;
	public static final int EVOLUTION       = 512;
	public static final int METAMORPHOSIS   = 1024;
	public static final int SACRIFICE       = 2048;
	public static final int COMMAND         = 4096;
	public static final int SWARMS          = 8192;
	public static final int REBIRTH         = 16384;
	public static final int ENIGMA          = 32768;
	public static final int LEGACY          = 65536;
	public static final int CRIMSON         = 131072;
	public static final int GRAVE           = 262144;
	public static final int GUILD           = 524288;
	public static final int CHIMERA         = 1048576;

	/** Settings clamp; bits 0–30 are storable. */
	public static final int MAX_VALUE       = Integer.MAX_VALUE;

	/** Bits that have gameplay this build. */
	public static final int[] IMPLEMENTED_MASKS = {
			HONOR, GLASS, FRAILTY, DEATH, DEVOTION, SPITE, SOUL,
			KIN, DISSONANCE, METAMORPHOSIS, COMMAND, ENIGMA, REBIRTH, SWARMS, SACRIFICE, EVOLUTION,
			LEGACY, CRIMSON, GRAVE, GUILD, CHIMERA
	};

	/** Bits forbidden on daily / custom-seed runs (seed comparability). */
	public static final int SEED_BANNED_MASK = COMMAND | REBIRTH | LEGACY;

	/** While true, Level/Heap drops mark items as dungeon Command loot. */
	public static boolean markingDungeonLoot = false;

	/** Suppresses Command window during replacement collect(). */
	public static boolean suppressCommandWindow = false;

	/** Daily and custom-seed runs forbid Command, Rebirth, and Legacy (seed comparability). */
	public static boolean seedBanned(){
		return Dungeon.daily || (Dungeon.customSeedText != null && !Dungeon.customSeedText.isEmpty());
	}

	/** Hero-select check before a run exists: custom seed or daily forbids Command/Rebirth. */
	public static boolean seedBannedAtSelect(){
		return Dungeon.daily || !SPDSettings.customSeed().isEmpty();
	}

	/** Implemented masks that can be rolled / toggled in the current seed mode. */
	public static int[] randomizableMasks(){
		if (seedBannedAtSelect()){
			int n = 0;
			for (int m : IMPLEMENTED_MASKS){
				if ((m & SEED_BANNED_MASK) == 0) n++;
			}
			int[] out = new int[n];
			int i = 0;
			for (int m : IMPLEMENTED_MASKS){
				if ((m & SEED_BANNED_MASK) == 0) out[i++] = m;
			}
			return out;
		}
		return IMPLEMENTED_MASKS;
	}

	/** Strip seed-banned bits (Command, Rebirth) from a mask. */
	public static int stripSeedBanned( int mask ){
		if (seedBanned() || seedBannedAtSelect()){
			mask &= ~SEED_BANNED_MASK;
		}
		return mask;
	}

	/** Ids parallel to {@link #MASKS}. The pact window groups these into tabs. */
	public static final String[] NAME_IDS = {
			"chimera",
			"command",
			"crimson",
			"death",
			"devotion",
			"dissonance",
			"enigma",
			"evolution",
			"frailty",
			"glass",
			"grave",
			"guild",
			"honor",
			"kin",
			"legacy",
			"metamorphosis",
			"rebirth",
			"sacrifice",
			"soul",
			"spite",
			"swarms"
	};

	public static final int[] MASKS = {
			CHIMERA,
			COMMAND,
			CRIMSON,
			DEATH,
			DEVOTION,
			DISSONANCE,
			ENIGMA,
			EVOLUTION,
			FRAILTY,
			GLASS,
			GRAVE,
			GUILD,
			HONOR,
			KIN,
			LEGACY,
			METAMORPHOSIS,
			REBIRTH,
			SACRIFICE,
			SOUL,
			SPITE,
			SWARMS
	};

	/** Pact window: rules on you and your side. */
	public static final int[] HERO_PACTS = { DEATH, FRAILTY, GLASS, DEVOTION, CRIMSON, GRAVE, CHIMERA };

	/** Pact window: who is in the dungeon and what they do. */
	public static final int[] FOES_PACTS = {
			HONOR, EVOLUTION, DISSONANCE, KIN, SWARMS, SPITE, SOUL
	};

	/** Pact window: items and what carries between runs. */
	public static final int[] SPOILS_PACTS = {
			COMMAND, ENIGMA, METAMORPHOSIS, SACRIFICE, REBIRTH, LEGACY, GUILD
	};

	public static String idForMask( int mask ){
		for (int i = 0; i < MASKS.length; i++){
			if (MASKS[i] == mask) return NAME_IDS[i];
		}
		return null;
	}

	public static boolean isImplemented( int mask ){
		for (int m : IMPLEMENTED_MASKS){
			if (m == mask) return true;
		}
		return false;
	}

	public static int activeModifiers(){
		return activeModifiers(Dungeon.modifiers);
	}

	public static int activeModifiers( int mask ){
		int count = 0;
		for (int m : MASKS){
			if ((mask & m) != 0) count++;
		}
		return count;
	}

	/** Keep only implemented bits when persisting from the editable window. */
	public static int sanitize( int mask ){
		int value = 0;
		for (int m : IMPLEMENTED_MASKS){
			if ((mask & m) != 0) value |= m;
		}
		return stripSeedBanned( value );
	}

	public static void markCommandLoot( Item item ){
		if (item != null) item.commandLoot = true;
	}

	public static void clearCommandLoot( Item item ){
		if (item != null) item.commandLoot = false;
	}

	public static boolean honorEligible( Mob m ){
		if (m == null) return false;
		if (m.buff(ChatSpawned.class) != null) return false;
		if (m.alignment != Char.Alignment.ENEMY) return false;
		return true;
	}

	/** Devotion covers every ally except Mirror Images and Grave shades. */
	public static boolean isDevotionAlly( Char ch ){
		if (ch == null || ch == Dungeon.hero) return false;
		if (ch.alignment != Char.Alignment.ALLY) return false;
		if (ch instanceof MirrorImage) return false;
		if (ch instanceof GraveShade) return false;
		return true;
	}

	public static int glassExtraHTSources( Hero hero ){
		int sources = 0;
		if (hero.HTBoost > 0) sources++;
		if (hero.buff(ElixirOfMight.HTBoost.class) != null) sources++;
		// Each equipped Ring of Might counts as one source
		if (hero.belongings != null){
			for (RingOfMight.Might buff : hero.buffs(RingOfMight.Might.class)){
				if (buff != null) sources++;
			}
		}
		return sources;
	}

	public static void applyGlassHT( Char ch ){
		if (!Dungeon.isModified(GLASS) || ch == null) return;
		Hero hero = Dungeon.hero;
		if (hero == null) return;

		if (ch == hero){
			int extras = glassExtraHTSources(hero);
			ch.HT = Math.max(1, hero.lvl + extras);
			ch.HP = Math.min(ch.HP, ch.HT);
		} else if (ch.alignment == Char.Alignment.ALLY){
			int cap = hero.lvl;
			if (ch.HT > cap){
				ch.HT = cap;
				ch.HP = Math.min(ch.HP, ch.HT);
			}
		}
	}

	/** Crimson blocks hit-point restoration on the hero only. */
	public static boolean crimsonBlocksHeal( Char ch ){
		return ch != null && ch == Dungeon.hero && Dungeon.isModified(CRIMSON);
	}

	/** Heal from a hero attack. Under Glass, any hit that removed hit points heals 1. */
	public static void crimsonStrikeHeal( Char attacker, int preGlassDmg, int hpRemoved ){
		if (!Dungeon.isModified(CRIMSON) || hpRemoved <= 0) return;
		boolean shade = attacker instanceof GraveShade;
		if (attacker != Dungeon.hero && !shade) return;
		float heal;
		if (Dungeon.isModified(GLASS)){
			heal = 1;
		} else {
			int basis = Math.min(preGlassDmg, hpRemoved);
			if (basis <= 0) return;
			heal = Math.max(1, basis / 4);
		}
		//Grave shades restore at half the hero's rate; fractions round randomly
		if (shade){
			heal *= CRIMSON_SHADE_RATE;
			heal = (int)heal + (Random.Float() < heal % 1 ? 1 : 0);
		}
		if (heal >= 1) crimsonHeal((int)heal);
	}

	public static final float CRIMSON_SHADE_RATE = 0.5f;

	public static void crimsonHeal( int amount ){
		Hero hero = Dungeon.hero;
		if (hero == null || !hero.isAlive()) return;
		amount = Math.min(amount, hero.HT - hero.HP);
		if (amount <= 0) return;
		hero.HP += amount;
		if (hero.sprite != null){
			hero.sprite.showStatusWithIcon(CharSprite.POSITIVE, Integer.toString(amount), FloatingText.HEALING);
		}
	}

	/** Hostiles only when Evolution is on. */
	public static boolean evolutionEligible( Char ch ){
		return Dungeon.isModified(EVOLUTION)
				&& ch != null
				&& ch.alignment == Char.Alignment.ENEMY;
	}

	/**
	 * Percent banked at floor 1 of the current loop. 0 on loop 1.
	 * Loop 2+ is at least {@link #EVOLUTION_LOOP_FLOOR}; a later loop keeps the
	 * previous loop's ending percent when that is higher.
	 */
	public static int evolutionBase = 0;

	/** First-loop cap, and the least a later loop may start from. */
	public static final int EVOLUTION_LOOP_FLOOR = 50;

	private static final String EVOLUTION_BASE = "evolution_base";

	/**
	 * Global Evolution multiplier.
	 * Loop 1 ({@link Legacy#loop} is 1-based): +2% per new deepest floor, cap +50%.
	 * Floor 1 = 1.00, floor 26 = 1.50. Unchanged for saves that have not looped.
	 * Loop 2+: floor 1 starts at {@link #evolutionBase} (at least +50%) and each new
	 * deepest floor adds +1%. The base is the previous loop's ending percent, so
	 * growth past 50% is kept and a loop never opens below 50%.
	 */
	public static float evolutionMultiplier(){
		if (!Dungeon.isModified(EVOLUTION)) return 1f;
		int gained = floorsGainedThisLoop();
		if (Legacy.loop <= 1){
			return 1f + Math.min(0.50f, 0.02f * gained);
		}
		return 1f + (loopedEvolutionBase() + gained) / 100f;
	}

	/** Tracker stacks. Same number as the percent bonus. */
	public static int evolutionPercent(){
		if (!Dungeon.isModified(EVOLUTION)) return 0;
		if (Legacy.loop <= 1){
			return Math.round(100f * (evolutionMultiplier() - 1f));
		}
		return loopedEvolutionBase() + floorsGainedThisLoop();
	}

	/** New main-path floors since floor 1 of this loop. Ascent does not subtract. */
	private static int floorsGainedThisLoop(){
		return Math.max(0, Statistics.deepestFloor - 1);
	}

	private static int loopedEvolutionBase(){
		return Math.max(EVOLUTION_LOOP_FLOOR, evolutionBase);
	}

	/**
	 * Percent to bank before {@link Statistics} resets for the next loop.
	 * Floor 1 of that loop starts here: at least 50, or higher once a loop has grown past it.
	 */
	public static int evolutionBaseForNextLoop(){
		if (!Dungeon.isModified(EVOLUTION)) return 0;
		return Math.max(EVOLUTION_LOOP_FLOOR, evolutionPercent());
	}

	public static void storeEvolution( Bundle bundle ){
		bundle.put( EVOLUTION_BASE, evolutionBase );
	}

	public static void restoreEvolution( Bundle bundle ){
		evolutionBase = bundle.contains( EVOLUTION_BASE ) ? bundle.getInt( EVOLUTION_BASE ) : 0;
	}

	/**
	 * Looped saves written before the base was stored still have Evolution derived from
	 * the reset deepest floor, so the tracker reads near 0. On load, a run with
	 * Evolution on and {@link Legacy#loop} above 1 whose base is below 50 is raised to 50.
	 * Stacks then read as 50 plus floors gained this loop. A base already at or above 50
	 * (a later loop that kept its surplus) is left alone. Loop 1 is not touched.
	 */
	public static void migrateEvolutionOnLoad(){
		if (!Dungeon.isModified(EVOLUTION)) return;
		if (Legacy.loop <= 1) return;
		if (evolutionBase < EVOLUTION_LOOP_FLOOR){
			evolutionBase = EVOLUTION_LOOP_FLOOR;
		}
	}

	/** Hostiles only when Evolution or Legacy growth is on. */
	public static boolean growthEligible( Char ch ){
		return ch != null
				&& ch.alignment == Char.Alignment.ENEMY
				&& (Dungeon.isModified(EVOLUTION) || Legacy.multiplier() > 1f);
	}

	/** Evolution times Legacy; combat reads this one factor. */
	public static float growthMultiplier(){
		return evolutionMultiplier() * Legacy.multiplier();
	}

	/** Display-only hero buff; combat reads {@link #evolutionMultiplier()} directly. */
	public static void ensureEvolutionTracker( Hero hero ){
		if (hero != null && Dungeon.isModified(EVOLUTION)){
			Buff.affect(hero, EvolutionTracker.class);
		}
	}

}
