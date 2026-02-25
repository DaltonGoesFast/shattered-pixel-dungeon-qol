/*
 * Pixel Dungeon
 * Copyright (C) 2012-2015 Oleg Dolya
 *
 * Shattered Pixel Dungeon
 * Copyright (C) 2014-2025 Evan Debenham
 *
 * This program is free software; you can redistribute it and/or modify
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

import com.shatteredpixel.shatteredpixeldungeon.ui.BuffIndicator;
import com.watabou.utils.Bundle;

/**
 * Applied to mobs spawned via chat in an earlier area than their native depth.
 * Scales down damage dealt and armor (DR) to match the current area.
 */
public class SpawnScaled extends Buff {

	{
		type = buffType.NEGATIVE;
		announced = false;
	}

	public float scale = 1f;

	/** Additional multiplier for damage/armor (scaled back more than HP). */
	private static final float DAMAGE_DR_MULT = 0.7f;

	private static final String SCALE = "scale";

	@Override
	public void storeInBundle(Bundle bundle) {
		super.storeInBundle(bundle);
		bundle.put(SCALE, scale);
	}

	@Override
	public void restoreFromBundle(Bundle bundle) {
		super.restoreFromBundle(bundle);
		scale = bundle.getFloat(SCALE);
	}

	@Override
	public boolean act() {
		spend(TICK);
		return true;
	}

	@Override
	public int icon() {
		return BuffIndicator.NONE;
	}

	/** Multiplier for damage dealt by this mob (scaled back more than HP). */
	public float damageFactor() {
		return Math.max(0.15f, scale * DAMAGE_DR_MULT);
	}

	/** Multiplier for armor (DR) when this mob is defending (scaled back more than HP). */
	public float drFactor() {
		return Math.max(0.15f, scale * DAMAGE_DR_MULT);
	}

	public static void affect(com.shatteredpixel.shatteredpixeldungeon.actors.Char target, float scale) {
		SpawnScaled buff = Buff.affect(target, SpawnScaled.class);
		buff.scale = scale;
	}
}
