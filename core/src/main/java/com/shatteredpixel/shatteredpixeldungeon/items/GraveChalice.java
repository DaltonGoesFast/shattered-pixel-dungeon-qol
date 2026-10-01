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

package com.shatteredpixel.shatteredpixeldungeon.items;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.GraveRoster;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSprite;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSpriteSheet;
import com.watabou.utils.Bundle;

import java.util.ArrayList;

/** Grave pact: trades life and satiety to call the next wraith at once. Costs are never shown. */
public class GraveChalice extends Item {

	public static final String AC_BLEED = "BLEED";

	{
		image = ItemSpriteSheet.ARTIFACT_CHALICE3;
		defaultAction = AC_BLEED;
		unique = true;
		bones = false;

		cursed = true;
		cursedKnown = true;
	}

	@Override
	public ArrayList<String> actions( Hero hero ) {
		ArrayList<String> actions = super.actions( hero );
		actions.remove( AC_DROP );
		actions.remove( AC_THROW );
		actions.add( AC_BLEED );
		return actions;
	}

	@Override
	public void execute( Hero hero, String action ) {
		super.execute( hero, action );
		if (action.equals( AC_BLEED )){
			GraveRoster.bleed( hero, this );
		}
	}

	@Override
	public void restoreFromBundle( Bundle bundle ) {
		super.restoreFromBundle( bundle );
		cursed = true;
		cursedKnown = true;
	}

	private static final ItemSprite.Glowing CURSE_GLOW = new ItemSprite.Glowing( 0x000000 );

	@Override
	public ItemSprite.Glowing glowing() {
		return CURSE_GLOW;
	}

	@Override
	public boolean isUpgradable() {
		return false;
	}

	@Override
	public boolean isIdentified() {
		return true;
	}
}
