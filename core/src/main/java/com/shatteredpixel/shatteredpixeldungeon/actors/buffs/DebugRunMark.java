/*
 * Permanent reminder that the current run was started in player debug mode.
 */

package com.shatteredpixel.shatteredpixeldungeon.actors.buffs;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.ui.BuffIndicator;
import com.watabou.noosa.Image;

public class DebugRunMark extends Buff {

	{
		type = buffType.NEUTRAL;
		revivePersists = true;
	}

	public static void ensureOn( Hero hero ){
		if (hero != null && Dungeon.debugRun && hero.buff(DebugRunMark.class) == null) {
			Buff.affect(hero, DebugRunMark.class);
		}
	}

	@Override
	public void detach() {
		//permanent for the whole run
	}

	@Override
	public int icon() {
		//any value other than NONE keeps the buff in the tray; the image comes from customIcon
		return BuffIndicator.MIND_VISION;
	}

	@Override
	public boolean customIcon( Image icon, boolean large ) {
		icon.texture( Assets.Interfaces.DEBUG_RUN );
		icon.frame( icon.texture.uvRectBySize( 0, 0, 6, 7 ) );
		icon.scale.set( large ? 2 : 1 );
		return true;
	}
}
