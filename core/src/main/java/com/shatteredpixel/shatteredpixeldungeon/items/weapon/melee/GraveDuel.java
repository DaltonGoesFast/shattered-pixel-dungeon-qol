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

package com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.Modifiers;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.GraveRoster;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.GraveShade;

/**
 * Grave pact: the body that performs a duelist weapon ability. Without Grave every call
 * resolves to the hero, so ability code stays unchanged in behaviour.
 */
public class GraveDuel {

	private static GraveShade performer = null;

	/** Picks the nearest living shade to {@code cell}. Returns false (and reports) if none. */
	static boolean begin( int cell ){
		performer = null;
		if (!Dungeon.isModified(Modifiers.GRAVE)) return true;
		performer = GraveRoster.nearestShade(cell);
		if (performer == null){
			GraveRoster.failCall();
			return false;
		}
		return true;
	}

	public static Char striker( Hero hero ){
		if (performer != null && Dungeon.isModified(Modifiers.GRAVE)
				&& performer.isAlive() && Actor.chars().contains(performer)){
			return performer;
		}
		return hero;
	}

	public static boolean canAttack( Hero hero, Char enemy ){
		Char s = striker(hero);
		if (s instanceof GraveShade) return ((GraveShade) s).canStrike(enemy);
		return hero.canAttack(enemy);
	}

	public static boolean attack( Hero hero, Char enemy, float dmgMulti, float dmgBonus, float accMulti ){
		return striker(hero).attack(enemy, dmgMulti, dmgBonus, accMulti);
	}

	/** Self-buff abilities: the shade receives the buff under Grave. */
	public static <T extends Buff> T findBuff( Hero hero, Class<T> buffClass ){
		T b = striker(hero).buff(buffClass);
		if (b != null) return b;
		if (Dungeon.isModified(Modifiers.GRAVE)){
			for (GraveShade s : GraveRoster.livingShades()){
				b = s.buff(buffClass);
				if (b != null) return b;
			}
		}
		return hero.buff(buffClass);
	}
}
