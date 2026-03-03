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

/**
 * Applied to mobs spawned via chat. Used to give region-based EXP on kill.
 * Also shows a black giant-champion-style aura (visual only, no gameplay effects).
 */
public class ChatSpawned extends Buff {

	private static final int AURA_COLOR = 0x555555;  // dark gray, more visible than 0x111111
	private static final int AURA_RAYS = 5;          // same as Giant champion

	{
		type = buffType.NEUTRAL;
		announced = false;
		revivePersists = true;  // keep when ghoul goes down/revive so EXP stays region-based
	}

	@Override
	public int icon() {
		return BuffIndicator.NONE;
	}

	@Override
	public void fx(boolean on) {
		if (on) target.sprite.aura(AURA_COLOR, AURA_RAYS);
		else target.sprite.clearAura();
	}
}
