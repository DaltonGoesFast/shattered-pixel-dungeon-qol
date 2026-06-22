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

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.ChatSpawned;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Cripple;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Light;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.SpawnScaled;
import com.shatteredpixel.shatteredpixeldungeon.items.Generator;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfStrength;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ScorpioSprite;
import com.watabou.utils.Random;
import com.watabou.utils.Reflection;

public class Scorpio extends Mob {

	private static final int CHAT_DAMAGE_MIN = 24;
	private static final int CHAT_DAMAGE_MAX = 32;
	private static final float CHAT_CRIPPLE_DURATION = 5f;
	private static final float CHAT_ATTACK_SKILL_SCALE_FLOOR = 0.33f;
	private static final int CHAT_ATTACK_SKILL_MIN = 9;

	private boolean isChatSpawned() {
		return buff( ChatSpawned.class ) != null;
	}

	private float chatSpawnScale() {
		SpawnScaled scaled = buff( SpawnScaled.class );
		return scaled != null ? scaled.scale : 1f;
	}
	
	{
		spriteClass = ScorpioSprite.class;
		
		HP = HT = 110;
		defenseSkill = 24;
		viewDistance = Light.DISTANCE;
		
		EXP = 14;
		maxLvl = 27;
		
		loot = Generator.Category.POTION;
		lootChance = 0.5f;

		properties.add(Property.DEMONIC);
	}
	
	@Override
	public int damageRoll() {
		if ( isChatSpawned() ) {
			return Random.NormalIntRange( CHAT_DAMAGE_MIN, CHAT_DAMAGE_MAX );
		}
		return Random.NormalIntRange( 30, 40 );
	}
	
	@Override
	public int attackSkill( Char target ) {
		if ( isChatSpawned() ) {
			float scale = chatSpawnScale();
			return Math.max( CHAT_ATTACK_SKILL_MIN, Math.round( 36 * Math.max( CHAT_ATTACK_SKILL_SCALE_FLOOR, scale ) ) );
		}
		return 36;
	}
	
	@Override
	public int drRoll() {
		return super.drRoll() + Random.NormalIntRange(0, 16);
	}
	
	@Override
	protected boolean canAttack( Char enemy ) {
		return !Dungeon.level.adjacent( pos, enemy.pos )
				&& (super.canAttack(enemy) || new Ballistica( pos, enemy.pos, Ballistica.PROJECTILE).collisionPos == enemy.pos);
	}
	
	@Override
	public int attackProc( Char enemy, int damage ) {
		damage = super.attackProc( enemy, damage );
		if ( isChatSpawned() ) {
			if ( Random.Int( 3 ) == 0 ) {
				Buff.prolong( enemy, Cripple.class, CHAT_CRIPPLE_DURATION );
			}
		} else if ( Random.Int( 2 ) == 0 ) {
			Buff.prolong( enemy, Cripple.class, Cripple.DURATION );
		}
		
		return damage;
	}
	
	@Override
	protected boolean getCloser( int target ) {
		if (state == HUNTING) {
			return enemySeen && getFurther( target );
		} else {
			return super.getCloser( target );
		}
	}
	
	@Override
	public void aggro(Char ch) {
		//cannot be aggroed to something it can't see
		//skip this check if FOV isn't initialized
		if (ch == null || fieldOfView == null
				|| fieldOfView.length != Dungeon.level.length() || fieldOfView[ch.pos]) {
			super.aggro(ch);
		}
	}

	@Override
	public Item createLoot() {
		Class<?extends Potion> loot;
		do{
			loot = (Class<? extends Potion>) Random.oneOf(Generator.Category.POTION.classes);
		} while (loot == PotionOfHealing.class || loot == PotionOfStrength.class);

		return Reflection.newInstance(loot);
	}
	
}
