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
 * Damage/DR factors are baked at spawn from {@link com.shatteredpixel.shatteredpixeldungeon.utils.SpawnScaleConfig}.
 */
public class SpawnScaled extends Buff {

	{
		type = buffType.NEGATIVE;
		announced = false;
	}

	public float scale = 1f;
	/** Baked damage multiplier (set at spawn). */
	public float damageFactor = 1f;
	/** Baked DR / evasion multiplier (set at spawn). */
	public float drFactor = 1f;

	private static final String SCALE = "scale";
	private static final String DMG = "dmg_factor";
	private static final String DR = "dr_factor";

	@Override
	public void storeInBundle(Bundle bundle) {
		super.storeInBundle(bundle);
		bundle.put(SCALE, scale);
		bundle.put(DMG, damageFactor);
		bundle.put(DR, drFactor);
	}

	@Override
	public void restoreFromBundle(Bundle bundle) {
		super.restoreFromBundle(bundle);
		scale = bundle.getFloat(SCALE);
		if (bundle.contains(DMG)) {
			damageFactor = bundle.getFloat(DMG);
		} else {
			// Legacy saves: approximate old formula
			damageFactor = Math.max(0.05f, scale * 0.25f);
		}
		if (bundle.contains(DR)) {
			drFactor = bundle.getFloat(DR);
		} else {
			drFactor = Math.max(0.15f, scale * 0.7f);
		}
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

	public float damageFactor() {
		return damageFactor;
	}

	public float drFactor() {
		return drFactor;
	}

	public static void affect(com.shatteredpixel.shatteredpixeldungeon.actors.Char target, float scale,
			float damageFactor, float drFactor) {
		SpawnScaled buff = Buff.affect(target, SpawnScaled.class);
		buff.scale = scale;
		buff.damageFactor = damageFactor;
		buff.drFactor = drFactor;
	}

	/** Inherit baked factors from a parent (ghoul/swarm/necro). */
	public static void affect(com.shatteredpixel.shatteredpixeldungeon.actors.Char target, SpawnScaled parent) {
		affect(target, parent.scale, parent.damageFactor, parent.drFactor);
	}

	/** Legacy helper: scale only (derives factors from current live config for depth). Prefer full affect. */
	public static void affect(com.shatteredpixel.shatteredpixeldungeon.actors.Char target, float scale) {
		affect(target, scale, scale, Math.max(0.15f, scale * 0.7f));
	}
}
