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
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.MirrorImage;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.elixirs.ElixirOfMight;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfMight;

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

	/** Settings clamp; bits 0–30 are storable. */
	public static final int MAX_VALUE       = Integer.MAX_VALUE;

	/** Bits that have gameplay this build. */
	public static final int[] IMPLEMENTED_MASKS = {
			HONOR, GLASS, FRAILTY, DEATH, DEVOTION, SPITE, SOUL,
			KIN, DISSONANCE, METAMORPHOSIS, COMMAND, ENIGMA, REBIRTH, SWARMS, SACRIFICE, EVOLUTION,
			LEGACY
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
			"command",
			"death",
			"devotion",
			"dissonance",
			"enigma",
			"evolution",
			"frailty",
			"glass",
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
			COMMAND,
			DEATH,
			DEVOTION,
			DISSONANCE,
			ENIGMA,
			EVOLUTION,
			FRAILTY,
			GLASS,
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
	public static final int[] HERO_PACTS = { DEATH, FRAILTY, GLASS, DEVOTION };

	/** Pact window: who is in the dungeon and what they do. */
	public static final int[] FOES_PACTS = {
			HONOR, EVOLUTION, DISSONANCE, KIN, SWARMS, SPITE, SOUL
	};

	/** Pact window: items and what carries between runs. */
	public static final int[] SPOILS_PACTS = {
			COMMAND, ENIGMA, METAMORPHOSIS, SACRIFICE, REBIRTH, LEGACY
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

	/** Devotion covers every ally except Mirror Images. */
	public static boolean isDevotionAlly( Char ch ){
		if (ch == null || ch == Dungeon.hero) return false;
		if (ch.alignment != Char.Alignment.ALLY) return false;
		if (ch instanceof MirrorImage) return false;
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

	/** Hostiles only when Evolution is on. */
	public static boolean evolutionEligible( Char ch ){
		return Dungeon.isModified(EVOLUTION)
				&& ch != null
				&& ch.alignment == Char.Alignment.ENEMY;
	}

	/**
	 * Global Evolution multiplier from deepest main-path floor.
	 * Floor 1 = 1.00, +2% per new deepest floor, capped at 1.50.
	 */
	public static float evolutionMultiplier(){
		if (!Dungeon.isModified(EVOLUTION)) return 1f;
		int deepest = Math.max(1, Statistics.deepestFloor);
		return 1f + Math.min(0.50f, 0.02f * (deepest - 1));
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
