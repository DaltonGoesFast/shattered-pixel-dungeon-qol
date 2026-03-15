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

package com.shatteredpixel.shatteredpixeldungeon.desktop;

import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Healing;

/**
 * Invisible buff that processes the chat heal queue. When the current Healing buff
 * finishes, applies the next queued heal. Detaches when queue is empty.
 */
public class HealQueueProcessor extends Buff {

	{
		actPriority = BUFF_PRIO - 1; // act after Healing (which uses HERO_PRIO - 1)
		type = buffType.NEUTRAL;
	}

	@Override
	public boolean act() {
		if (target != null && target.buff(Healing.class) == null) {
			StreamingCommandHandler.processNextQueuedHeal();
		}
		if (!StreamingCommandHandler.hasQueuedHeals()) {
			detach();
			return false;
		}
		spend(TICK);
		return true;
	}
}
