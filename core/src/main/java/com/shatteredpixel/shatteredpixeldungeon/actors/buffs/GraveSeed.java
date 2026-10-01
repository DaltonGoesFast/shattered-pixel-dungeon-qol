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

package com.shatteredpixel.shatteredpixeldungeon.actors.buffs;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.GraveShade;
import com.shatteredpixel.shatteredpixeldungeon.effects.CellEmitter;
import com.shatteredpixel.shatteredpixeldungeon.effects.FloatingText;
import com.shatteredpixel.shatteredpixeldungeon.effects.Splash;
import com.shatteredpixel.shatteredpixeldungeon.effects.particles.FlameParticle;
import com.shatteredpixel.shatteredpixeldungeon.effects.particles.LeafParticle;
import com.shatteredpixel.shatteredpixeldungeon.effects.particles.PoisonParticle;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.MissileWeapon;
import com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite;
import com.watabou.utils.Bundle;
import com.watabou.utils.Random;

/** Grave pact: a weak plant benefit stored on a shade by a spirit bow shot. */
public class GraveSeed extends FlavourBuff {

	{
		type = buffType.POSITIVE;
	}

	public static final float DURATION = 5f;
	public static final float EXTENSION = 2f;

	public static final int BARKSKIN    = 0;
	public static final int HASTE       = 1;
	public static final int HEAL        = 2;
	public static final int STRIKE      = 3;
	public static final int INVISIBLE   = 4;
	public static final int CLEANSE     = 5;
	public static final int BLESS       = 6;

	public static final int STRIKE_FIRE  = 0;
	public static final int STRIKE_FROST = 1;
	public static final int STRIKE_TOXIN = 2;

	public int benefit;
	public int strike;
	private boolean extended = false;
	private boolean strikeUsed = false;

	//Point Blank does not help a shot aimed at a shade, so its adjacent bonus is divided back out
	public static boolean rollHit( Hero hero, GraveShade shade, MissileWeapon arrow ){
		float acc = arrow.accuracyFactor(hero, shade);
		if (Dungeon.level.adjacent(hero.pos, shade.pos)){
			acc *= 0.5f / (0.5f + 0.25f*hero.pointsInTalent(Talent.POINT_BLANK));
		}
		return Random.Float() < acc;
	}

	public static void plant( GraveShade shade ){
		GraveSeed seed = shade.buff(GraveSeed.class);
		if (seed != null){
			if (!seed.extended){
				seed.spend(EXTENSION);
				seed.extended = true;
			}
			return;
		}

		seed = Buff.affect(shade, GraveSeed.class, DURATION);
		seed.benefit = Random.Int(7);
		seed.strike = Random.Int(3);

		switch (seed.benefit){
			case HASTE:
				Buff.prolong(shade, Haste.class, 2f);
				break;
			case HEAL:
				int heal = Math.min(4, shade.HT - shade.HP);
				shade.HP += heal;
				if (shade.sprite != null) shade.sprite.showStatusWithIcon(CharSprite.POSITIVE, Integer.toString(heal), FloatingText.HEALING);
				break;
			case INVISIBLE:
				Buff.affect(shade, Invisibility.class, 2f);
				break;
			case CLEANSE:
				PotionOfHealing.cure(shade);
				break;
			case BLESS:
				Buff.prolong(shade, Bless.class, 2f);
				break;
		}
		if (shade.sprite != null) CellEmitter.get(shade.pos).burst(LeafParticle.LEVEL_SPECIFIC, 6);
	}

	public int drBonus(){
		return benefit == BARKSKIN ? Random.NormalIntRange(0, 3) : 0;
	}

	public void onStrike( Char enemy ){
		if (benefit != STRIKE || strikeUsed) return;
		strikeUsed = true;
		switch (strike){
			case STRIKE_FIRE:
				Buff.affect(enemy, Burning.class).reignite(enemy, 8f);
				enemy.sprite.emitter().burst(FlameParticle.FACTORY, 3);
				break;
			case STRIKE_FROST:
				Buff.affect(enemy, Chill.class, 3f);
				Splash.at(enemy.sprite.center(), 0xFFB2D6FF, 5);
				break;
			case STRIKE_TOXIN:
				Poison poison = enemy.buff(Poison.class);
				if (poison == null) poison = Buff.affect(enemy, Poison.class);
				poison.extend(3f);
				CellEmitter.center(enemy.pos).burst(PoisonParticle.SPLASH, 5);
				break;
		}
	}

	private static final String BENEFIT = "benefit";
	private static final String STRIKE_TYPE = "strike";
	private static final String EXTENDED = "extended";
	private static final String STRIKE_USED = "strike_used";

	@Override
	public void storeInBundle( Bundle bundle ) {
		super.storeInBundle( bundle );
		bundle.put( BENEFIT, benefit );
		bundle.put( STRIKE_TYPE, strike );
		bundle.put( EXTENDED, extended );
		bundle.put( STRIKE_USED, strikeUsed );
	}

	@Override
	public void restoreFromBundle( Bundle bundle ) {
		super.restoreFromBundle( bundle );
		benefit = bundle.getInt( BENEFIT );
		strike = bundle.getInt( STRIKE_TYPE );
		extended = bundle.getBoolean( EXTENDED );
		strikeUsed = bundle.getBoolean( STRIKE_USED );
	}
}
