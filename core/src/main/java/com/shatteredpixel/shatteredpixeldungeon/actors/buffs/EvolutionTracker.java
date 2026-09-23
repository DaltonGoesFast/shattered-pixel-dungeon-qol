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

package com.shatteredpixel.shatteredpixeldungeon.actors.buffs;

import com.shatteredpixel.shatteredpixeldungeon.Modifiers;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.ui.BuffIndicator;
import com.watabou.noosa.Image;

/**
 * Display-only Evolution pact tracker. Combat reads {@link Modifiers#evolutionMultiplier()}.
 */
public class EvolutionTracker extends Buff {

	{
		type = buffType.NEUTRAL;
		announced = false;
		revivePersists = true;
	}

	@Override
	public boolean act() {
		diactivate();
		return true;
	}

	@Override
	public int icon() {
		return BuffIndicator.CORRUPT;
	}

	@Override
	public void tintIcon(Image icon) {
		icon.hardlight(0xFF2222);
	}

	@Override
	public String iconTextDisplay() {
		return Integer.toString(boostPercent());
	}

	@Override
	public String desc() {
		int pct = boostPercent();
		int takenReduce = (int)(100 * (1f - 1f / Modifiers.evolutionMultiplier()));
		return Messages.get(this, "desc", pct, takenReduce);
	}

	private int boostPercent() {
		return Math.round(100 * (Modifiers.evolutionMultiplier() - 1f));
	}
}
