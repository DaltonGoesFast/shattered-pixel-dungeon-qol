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

package com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.AllyBuff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Amok;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.GraveImbue;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.GraveSeed;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.GraveRoster;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfAccuracy;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfFuror;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Flail;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Quarterstaff;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.RoundShield;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Scimitar;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite;
import com.shatteredpixel.shatteredpixeldungeon.sprites.GraveShadeSprite;
import com.watabou.noosa.audio.Sample;
import com.watabou.utils.BArray;
import com.watabou.utils.Bundle;
import com.watabou.utils.PathFinder;
import com.watabou.utils.Random;

/** Grave pact: a friendly wraith that swings the hero's equipped melee weapon. */
public class GraveShade extends DirectableAlly {

	{
		spriteClass = GraveShadeSprite.class;

		HP = HT = 1;
		flying = true;
		attacksAutomatically = false;

		properties.add(Property.UNDEAD);
		properties.add(Property.INORGANIC);

		immunities.add( AllyBuff.class );
	}

	public int slot = 0;
	public int summonId = 0;

	private boolean commanded = false;

	public static final int LEASH = 6;
	public static final int LEASH_GRACE = 3;
	public static final float SOUL_LINK_MIN = 0.5f;
	public static final float SOUL_LINK_MAX = 0.75f;

	private int strayTurns = 0;

	public void refreshStats(){
		defenseSkill = (10 + Dungeon.scalingDepth()) * 5;
	}

	public static MeleeWeapon heroWeapon(){
		Hero hero = Dungeon.hero;
		if (hero == null) return null;
		if (hero.belongings.abilityWeapon instanceof MeleeWeapon){
			return (MeleeWeapon) hero.belongings.abilityWeapon;
		}
		if (hero.belongings.weapon() instanceof MeleeWeapon){
			return (MeleeWeapon) hero.belongings.weapon();
		}
		return null;
	}

	@Override
	protected boolean act() {
		if (!GraveRoster.owns(this)){
			destroy();
			if (sprite != null) sprite.killAndErase();
			return true;
		}
		if (Dungeon.hero != null && Dungeon.level.distance(pos, Dungeon.hero.pos) > LEASH){
			strayTurns++;
			if (strayTurns > LEASH_GRACE){
				die( null );
				return true;
			}
			if (strayTurns == 1 && sprite != null){
				sprite.showStatus( CharSprite.WARNING, Messages.get(this, "fading") );
			}
			if (sprite != null) sprite.alpha( 1f - 0.2f * strayTurns );
		} else if (strayTurns > 0){
			strayTurns = 0;
			if (sprite != null) sprite.alpha( 1f );
		}
		refreshStats();
		return super.act();
	}

	@Override
	public void damage( int dmg, Object src ) {
		if (dmg > 0 && isAlive() && src instanceof Char && ((Char) src).alignment == Alignment.ENEMY
				&& Dungeon.hero != null && Dungeon.hero.isAlive()){
			float exact = dmg * Random.Float( SOUL_LINK_MIN, SOUL_LINK_MAX );
			int link = (int) exact + (Random.Float() < exact - (int) exact ? 1 : 0);
			if (link > 0) Dungeon.hero.damage( link, src );
		}
		super.damage( dmg, src );
	}

	public void command( Char ch ){
		commanded = true;
		targetChar(ch);
		commanded = false;
	}

	@Override
	public void aggro(Char ch) {
		if (ch == null || commanded){
			super.aggro(ch);
		}
	}

	@Override
	protected Char chooseEnemy() {
		recentlyAttackedBy.clear();
		if (enemy != null && (!enemy.isAlive()
				|| !Actor.chars().contains(enemy)
				|| (enemy.alignment == Alignment.ALLY && enemy.buff(Amok.class) == null))){
			followHero();
		}
		if (enemy == null){
			Char threat = nearestThreat();
			if (threat != null) command(threat);
		}
		return enemy;
	}

	/** Nearest visible enemy currently hunting the hero or this shade. */
	private Char nearestThreat(){
		Hero hero = Dungeon.hero;
		Char best = null;
		for (Mob m : Dungeon.level.mobs){
			if (m.alignment != Alignment.ENEMY || !m.isAlive() || m.invisible > 0 || m.state != m.HUNTING) continue;
			if (!m.isTargeting(hero) && !m.isTargeting(this)) continue;
			if (fieldOfView != null && !fieldOfView[m.pos]) continue;
			if (best == null || Dungeon.level.distance(pos, m.pos) < Dungeon.level.distance(pos, best.pos)){
				best = m;
			}
		}
		return best;
	}

	public int reach(){
		MeleeWeapon wep = heroWeapon();
		if (wep == null) return 0;
		int reach = wep.reachFactor(this);
		GraveImbue imbue = buff(GraveImbue.class);
		if (imbue != null && imbue.grantsReach()) reach++;
		return reach;
	}

	@Override
	protected boolean canAttack(Char enemy) {
		return canStrike(enemy);
	}

	public boolean canStrike( Char enemy ){
		if (enemy == null || enemy == this || heroWeapon() == null || !Actor.chars().contains(enemy)){
			return false;
		}
		if (Dungeon.level.adjacent(pos, enemy.pos)) return true;

		int reach = reach();
		if (Dungeon.level.distance(pos, enemy.pos) > reach) return false;

		boolean[] passable = BArray.not(Dungeon.level.solid, null);
		for (Char ch : Actor.chars()) {
			if (ch != this) passable[ch.pos] = false;
		}
		PathFinder.buildDistanceMap(enemy.pos, passable, reach);
		return PathFinder.distance[pos] <= reach;
	}

	@Override
	public int damageRoll() {
		MeleeWeapon wep = heroWeapon();
		if (wep == null) return 0;
		return wep.damageRoll(Dungeon.hero);
	}

	@Override
	public int attackSkill( Char target ) {
		Hero hero = Dungeon.hero;
		float skill = (9 + hero.lvl) * RingOfAccuracy.accuracyMultiplier(hero);
		if (buff(Scimitar.SwordDance.class) != null){
			skill *= 1.5f;
		}
		MeleeWeapon wep = heroWeapon();
		if (wep != null){
			//flail spin is tracked on the shade; encumbrance is read from the hero
			Char accOwner = buff(Flail.SpinAbilityTracker.class) != null ? this : hero;
			skill *= wep.accuracyFactor(accOwner, target);
		}
		return Math.round(skill);
	}

	@Override
	public float attackDelay() {
		MeleeWeapon wep = heroWeapon();
		if (wep == null) return 1f;
		Hero hero = Dungeon.hero;
		float delay = wep.delayFactor(hero);
		if (buff(Scimitar.SwordDance.class) != null && hero.buff(Scimitar.SwordDance.class) == null){
			float furor = RingOfFuror.attackSpeedMultiplier(hero);
			delay *= furor / (furor + 0.6f);
		}
		return delay;
	}

	@Override
	public int defenseSkill( Char enemy ) {
		if (buff(RoundShield.GuardTracker.class) != null){
			return INFINITE_EVASION;
		}
		int evasion = super.defenseSkill(enemy);
		if (buff(Quarterstaff.DefensiveStance.class) != null){
			evasion *= 3;
		}
		return evasion;
	}

	@Override
	public String defenseVerb() {
		RoundShield.GuardTracker guard = buff(RoundShield.GuardTracker.class);
		if (guard != null){
			guard.hasBlocked = true;
			Sample.INSTANCE.play(Assets.Sounds.HIT_PARRY, 1, Random.Float(0.96f, 1.05f));
			return Messages.get(RoundShield.GuardTracker.class, "guarded");
		}
		return super.defenseVerb();
	}

	@Override
	public int attackProc( Char enemy, int damage ) {
		damage = super.attackProc( enemy, damage );
		if (enemy instanceof Mob) {
			((Mob)enemy).aggro( this );
		}
		MeleeWeapon wep = heroWeapon();
		if (wep != null){
			damage = wep.proc( this, enemy, damage );
		}
		GraveImbue imbue = buff(GraveImbue.class);
		if (imbue != null){
			damage = imbue.proc( this, enemy, damage );
		}
		GraveSeed seed = buff(GraveSeed.class);
		if (seed != null){
			seed.onStrike( enemy );
		}
		return damage;
	}

	@Override
	public int drRoll() {
		int dr = super.drRoll();
		GraveSeed seed = buff(GraveSeed.class);
		if (seed != null){
			dr += seed.drBonus();
		}
		return dr;
	}

	@Override
	public void die( Object cause ) {
		super.die( cause );
		GraveRoster.onShadeDeath( this );
	}

	private static final String SLOT = "slot";
	private static final String SUMMON_ID = "summon_id";
	private static final String STRAY_TURNS = "stray_turns";

	@Override
	public void storeInBundle( Bundle bundle ) {
		super.storeInBundle( bundle );
		bundle.put( SLOT, slot );
		bundle.put( SUMMON_ID, summonId );
		bundle.put( STRAY_TURNS, strayTurns );
	}

	@Override
	public void restoreFromBundle( Bundle bundle ) {
		super.restoreFromBundle( bundle );
		slot = bundle.getInt( SLOT );
		summonId = bundle.getInt( SUMMON_ID );
		strayTurns = bundle.getInt( STRAY_TURNS );
		refreshStats();
	}
}
