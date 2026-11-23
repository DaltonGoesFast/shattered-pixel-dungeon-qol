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

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.Badges;
import com.shatteredpixel.shatteredpixeldungeon.Chimera;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Preparation;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroSubClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import com.shatteredpixel.shatteredpixeldungeon.utils.TalentAutoPlan;
import com.shatteredpixel.shatteredpixeldungeon.effects.Speck;
import com.shatteredpixel.shatteredpixeldungeon.journal.Catalog;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSpriteSheet;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndChooseSubclass;
import com.watabou.noosa.audio.Sample;
import com.watabou.noosa.particles.Emitter;

import java.util.ArrayList;

public class TengusMask extends Item {
	
	private static final String AC_WEAR	= "WEAR";
	
	{
		stackable = false;
		image = ItemSpriteSheet.MASK;

		defaultAction = AC_WEAR;

		unique = true;
	}
	
	@Override
	public ArrayList<String> actions( Hero hero ) {
		ArrayList<String> actions = super.actions( hero );
		actions.add( AC_WEAR );
		return actions;
	}
	
	@Override
	public void execute( Hero hero, String action ) {

		super.execute( hero, action );

		if (action.equals( AC_WEAR )) {
			
			curUser = hero;

			GameScene.show( new WndChooseSubclass( this, hero ) );
			
		}
	}
	
	@Override
	public boolean doPickUp(Hero hero, int pos) {
		Badges.validateMastery();
		return super.doPickUp( hero, pos );
	}
	
	@Override
	public boolean isUpgradable() {
		return false;
	}
	
	@Override
	public boolean isIdentified() {
		return true;
	}
	
	public void choose( HeroSubClass way ) {
		// Play Along opens the chooser without Item.execute, so curUser is still null.
		choose( curUser != null ? curUser : Dungeon.hero, way );
	}

	public void choose( Hero hero, HeroSubClass way ) {
		if (hero == null || way == null) {
			return;
		}

		// Consume and play the mask-use effects only when this mask is actually held.
		// A drop-in chooser must still apply the subclass when the hero has no mask.
		boolean held = hero.belongings != null
				&& hero.belongings.backpack != null
				&& hero.belongings.backpack.contains( this );

		if (held) {
			curUser = hero;
			detach( hero.belongings.backpack );
			Catalog.countUse( getClass() );

			hero.spend( Actor.TICK );
			hero.busy();
		}

		hero.talentAutoOrderTier3 = null;
		hero.clearTalentAutoSpendHistory( 3 );
		hero.subClass = way;
		Talent.initSubclassTalents(hero);
		TalentAutoPlan.tryApply( hero );

		if (way == HeroSubClass.ASSASSIN && hero.invisible > 0){
			Buff.affect(hero, Preparation.class);
		}

		if (held && hero.sprite != null) {
			hero.sprite.operate( hero.pos );
			Sample.INSTANCE.play( Assets.Sounds.MASTERY );

			Emitter e = hero.sprite.centerEmitter();
			e.pos(e.x-2, e.y-6, 4, 4);
			e.start(Speck.factory(Speck.MASK), 0.05f, 20);
			GLog.p( Messages.get(this, "used"));
		}
		Chimera.grantSignature( hero, Chimera.classOf( way ) );

	}
}
