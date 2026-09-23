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

package com.shatteredpixel.shatteredpixeldungeon.windows;

import com.shatteredpixel.shatteredpixeldungeon.SPDAction;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.Bag;
import com.watabou.input.KeyBindings;
import com.watabou.input.KeyEvent;

/**
 * Forced Rebirth sacrifice picker. Back / inventory key cannot cancel.
 */
public class WndRebirth extends WndBag {

	public WndRebirth( Bag bag, ItemSelector selector ) {
		super( bag, selector );
	}

	@Override
	protected WndBag newBagWindow( Bag bag, ItemSelector selector ) {
		return new WndRebirth( bag, selector );
	}

	@Override
	public void onBackPressed() {
		// Must pick an eligible gift — ignore cancel
	}

	@Override
	public boolean onSignal( KeyEvent event ) {
		if (event.pressed && KeyBindings.getActionForKey( event ) == SPDAction.INVENTORY) {
			return true; // consume; do not cancel
		}
		return super.onSignal( event );
	}
}
