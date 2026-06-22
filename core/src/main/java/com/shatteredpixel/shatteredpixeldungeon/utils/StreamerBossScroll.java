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

package com.shatteredpixel.shatteredpixeldungeon.utils;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.Statistics;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.DM300;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.DwarfKing;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Goo;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Tengu;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.YogDzewa;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfStasis;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;

public final class StreamerBossScroll {

	private StreamerBossScroll() {}

	private static final Class<? extends Mob>[] MAIN_BOSSES = new Class[]{
			Goo.class, Tengu.class, DM300.class, DwarfKing.class, YogDzewa.class
	};

	public static boolean isMainChapterBoss( Mob mob ){
		for (Class<? extends Mob> cls : MAIN_BOSSES){
			if (cls.isInstance(mob)) return true;
		}
		return false;
	}

	public static void tryDrop( Mob mob, int pos ){
		if (!SPDSettings.streamingEnabled()) return;
		if (!SPDSettings.streamerBossStasisScroll()) return;
		if (!isMainChapterBoss(mob)) return;
		if (Statistics.streamerStasisBossDropped.contains(mob.getClass())) return;

		Statistics.streamerStasisBossDropped.add(mob.getClass());

		ScrollOfStasis scroll = new ScrollOfStasis();
		scroll.identify(false);
		Dungeon.level.drop(scroll, pos).sprite.drop();
		GLog.i(Messages.get(StreamerBossScroll.class, "dropped"));
	}

}
