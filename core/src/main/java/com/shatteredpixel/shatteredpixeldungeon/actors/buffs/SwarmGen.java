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

package com.shatteredpixel.shatteredpixeldungeon.actors.buffs;

import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Ghoul;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mimic;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Necromancer;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Wraith;
import com.shatteredpixel.shatteredpixeldungeon.ui.BuffIndicator;
import com.watabou.utils.Bundle;

/**
 * Tracks clone generation for the Swarms pact. Absent means generation 0 (original).
 * Not revivePersists — the splitter assigns generation on the clone itself.
 */
public class SwarmGen extends Buff {

	{
		type = buffType.NEUTRAL;
		announced = false;
	}

	/** Original is 0; no further splits at {@code generation >= 3}. */
	public int generation = 1;

	private static final String GENERATION = "generation";

	@Override
	public void storeInBundle( Bundle bundle ) {
		super.storeInBundle( bundle );
		bundle.put( GENERATION, generation );
	}

	@Override
	public void restoreFromBundle( Bundle bundle ) {
		super.restoreFromBundle( bundle );
		generation = bundle.getInt( GENERATION );
	}

	@Override
	public int icon() {
		return BuffIndicator.NONE;
	}

	public static int generation( Char ch ) {
		if (ch == null) return 0;
		SwarmGen g = ch.buff( SwarmGen.class );
		return g == null ? 0 : g.generation;
	}

	public static boolean isClone( Char ch ) {
		return generation( ch ) > 0;
	}

	/** Hostiles that may attempt a Swarms-pact split on hit. */
	public static boolean canSplit( Mob m ) {
		if (m == null) return false;
		if (m.alignment != Char.Alignment.ENEMY) return false;
		if (generation( m ) >= 3) return false;
		if (m.properties().contains( Char.Property.BOSS )) return false;
		if (m.properties().contains( Char.Property.LARGE )) return false;
		if (m.properties().contains( Char.Property.IMMOVABLE )) return false;
		if (m instanceof Mimic) return false;
		if (m instanceof Ghoul) return false;
		if (m instanceof Necromancer.NecroSkeleton) return false;
		if (m instanceof Wraith) return false;
		return true;
	}

	public static void set( Mob m, int generation ) {
		if (m == null || generation <= 0) return;
		SwarmGen g = Buff.affect( m, SwarmGen.class );
		g.generation = generation;
	}
}
