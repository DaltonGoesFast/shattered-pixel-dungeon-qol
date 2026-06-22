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

package com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.TimeFreeze;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSpriteSheet;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.watabou.noosa.audio.Sample;

public class ScrollOfTimeFreeze extends ExoticScroll {

	public static final float DURATION = 15f;

	{
		stackable = false;
		image = ItemSpriteSheet.EXOTIC_STASIS;
	}

	@Override
	public void reset() {
		image = ItemSpriteSheet.EXOTIC_STASIS;
	}

	@Override
	public boolean isKnown() {
		return true;
	}

	@Override
	public void setKnown() {
		//always identified
	}

	@Override
	public String name() {
		return Messages.get(this, "name");
	}

	@Override
	public String desc() {
		return Messages.get(this, "desc");
	}

	@Override
	public void doRead() {
		detach(curUser.belongings.backpack);

		GLog.i( Messages.get(this, "onread") );
		GameScene.flash(0x80FFFFFF);
		Sample.INSTANCE.play(Assets.Sounds.TELEPORT);

		readAnimation();

		// After readAnimation — Invisibility.dispel() inside it removes TimeFreeze if applied earlier
		TimeFreeze freeze = Buff.affect(curUser, TimeFreeze.class);
		freeze.setDuration((int) DURATION);
		freeze.processTime(0f);
	}

}
