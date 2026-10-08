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

package com.shatteredpixel.shatteredpixeldungeon.items.potions;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.Challenges;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.Modifiers;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.AscensionChallenge;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Bleeding;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Blindness;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Cripple;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Drowsy;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Healing;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Poison;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Slow;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.SpawnScaled;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Vertigo;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Vulnerable;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Weakness;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.effects.CellEmitter;
import com.shatteredpixel.shatteredpixeldungeon.effects.particles.PoisonParticle;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSpriteSheet;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.watabou.noosa.audio.Sample;

public class PotionOfHealing extends Potion {

	{
		icon = ItemSpriteSheet.Icons.POTION_HEALING;

		bones = true;
	}
	
	@Override
	public void apply( Hero hero ) {
		identify();
		cure( hero );
		heal( hero );
	}

	public static void heal( Char ch ){
		if (ch == Dungeon.hero && Dungeon.isChallenged(Challenges.NO_HEALING)){
			pharmacophobiaProc(Dungeon.hero);
		} else if (!Modifiers.crimsonBlocksHeal(ch)) {
			//starts out healing 30 hp, equalizes with hero health total at level 11
			Healing healing = Buff.affect(ch, Healing.class);
			if (Dungeon.isModified(Modifiers.GLASS)){
				healing.setHeal((int) (0.8f * ch.HT), 0.25f, 0, true);
			} else {
				healing.setHeal((int) (0.8f * ch.HT + 14), 0.25f, 0, true);
			}
			if (ch == Dungeon.hero){
				GLog.p( Messages.get(PotionOfHealing.class, "heal") );
			}
		}
	}

	@Override
	public void doThrow( Hero hero ) {
		if (Dungeon.isModified(Modifiers.CRIMSON)){
			GameScene.selectCell(thrower);
		} else {
			super.doThrow(hero);
		}
	}

	@Override
	public void shatter( int cell ) {
		Char ch = Actor.findChar( cell );
		//Only a living enemy is harmed. Anything else still splashes harmlessly.
		boolean harmful = Dungeon.isModified(Modifiers.CRIMSON)
				&& !Dungeon.isChallenged(Challenges.NO_HEALING)
				&& ch != null
				&& ch.alignment == Char.Alignment.ENEMY
				&& ch.isAlive();
		if (!harmful) {
			super.shatter( cell );
			return;
		}

		splash( cell );
		if (Dungeon.level.heroFOV[cell]) {
			GLog.i( Messages.get(this, "shatter") );
			Sample.INSTANCE.play( Assets.Sounds.SHATTER );
		}

		int dr = Math.round(ch.drRoll() * AscensionChallenge.statModifier(ch));
		if (ch.buff(SpawnScaled.class) != null) {
			dr = Math.round(dr * ch.buff(SpawnScaled.class).drFactor());
		}
		int impact = Math.max(0, 4 + Dungeon.depth - dr);
		ch.lastHpRemoved = 0;
		ch.damage( impact, this );
		if (ch.lastHpRemoved > 0){
			Modifiers.crimsonHeal( Math.max(1, ch.lastHpRemoved / 4) );
		}

		if (ch.isAlive()){
			Poison poison = ch.buff(Poison.class);
			if (poison == null) {
				poison = Buff.affect(ch, Poison.class);
				poison.delay(3f);
				poison = ch.buff(Poison.class);
			}
			if (poison != null){
				poison.extend(3 + Dungeon.depth / 5);
			}
			CellEmitter.center(ch.pos).burst(PoisonParticle.SPLASH, 5);
		}
	}

	public static void pharmacophobiaProc( Hero hero ){
		// harms the hero for ~40% of their max HP in poison
		Buff.affect( hero, Poison.class).set(4 + hero.lvl/2);
	}
	
	public static void cure( Char ch ) {
		Buff.detach( ch, Poison.class );
		Buff.detach( ch, Cripple.class );
		Buff.detach( ch, Weakness.class );
		Buff.detach( ch, Vulnerable.class );
		Buff.detach( ch, Bleeding.class );
		Buff.detach( ch, Blindness.class );
		Buff.detach( ch, Drowsy.class );
		Buff.detach( ch, Slow.class );
		Buff.detach( ch, Vertigo.class);
	}

	@Override
	public int value() {
		return isKnown() ? 30 * quantity : super.value();
	}
}
