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

package com.shatteredpixel.shatteredpixeldungeon.items.bombs;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;

import java.util.ArrayList;

public class SpiteBomb extends Bomb {

	{
		stackable = false;
		defaultAction = null;
		usesTargeting = false;
		// Sit under real loot so pickup still takes the drop, while the sprite draws the bomb.
		dropsDownHeap = true;
	}

	@Override
	public boolean ignoresFire() {
		return true;
	}

	@Override
	protected boolean destroysItems() {
		return false;
	}

	@Override
	protected int explosionRange() {
		return 0;
	}

	@Override
	public boolean doPickUp(Hero hero, int pos) {
		return false;
	}

	@Override
	public ArrayList<String> actions(Hero hero) {
		return new ArrayList<>();
	}

	@Override
	public int value() {
		return 0;
	}
}
