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

package com.shatteredpixel.shatteredpixeldungeon.actors.mobs;

import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.sprites.SoulSprite;
import com.watabou.utils.Bundle;

public class SoulWraith extends Wraith {

	{
		spriteClass = SoulSprite.class;
	}

	/** First action may only close distance. Attacks start the turn after. */
	private boolean opening = true;
	private boolean blockAttack = false;

	@Override
	protected float spawnDelay() {
		return 0f;
	}

	@Override
	public String name() {
		return Messages.get(Wraith.class, "name");
	}

	@Override
	public String description() {
		return Messages.get(Wraith.class, "desc");
	}

	@Override
	protected boolean act() {
		if (!opening || paralysed > 0) {
			return super.act();
		}
		opening = false;
		blockAttack = true;
		try {
			return super.act();
		} finally {
			blockAttack = false;
		}
	}

	@Override
	protected boolean canAttack( Char enemy ) {
		if (blockAttack) {
			return false;
		}
		return super.canAttack( enemy );
	}

	private static final String OPENING = "opening";

	@Override
	public void storeInBundle( Bundle bundle ) {
		super.storeInBundle( bundle );
		bundle.put( OPENING, opening );
	}

	@Override
	public void restoreFromBundle( Bundle bundle ) {
		super.restoreFromBundle( bundle );
		if (bundle.contains( OPENING )) {
			opening = bundle.getBoolean( OPENING );
		}
	}
}
