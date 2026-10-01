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

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.GraveShade;
import com.shatteredpixel.shatteredpixeldungeon.effects.CellEmitter;
import com.shatteredpixel.shatteredpixeldungeon.effects.FloatingText;
import com.shatteredpixel.shatteredpixeldungeon.effects.Lightning;
import com.shatteredpixel.shatteredpixeldungeon.effects.Splash;
import com.shatteredpixel.shatteredpixeldungeon.effects.particles.CorrosionParticle;
import com.shatteredpixel.shatteredpixeldungeon.effects.particles.EnergyParticle;
import com.shatteredpixel.shatteredpixeldungeon.effects.particles.FlameParticle;
import com.shatteredpixel.shatteredpixeldungeon.effects.particles.PoisonParticle;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfBlastWave;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfCorrosion;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfCorruption;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfDisintegration;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfFireblast;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfFrost;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfLightning;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfMagicMissile;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfPrismaticLight;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfRegrowth;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfTransfusion;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.enchantments.Shocking;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.shatteredpixel.shatteredpixeldungeon.plants.Sungrass;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite;
import com.watabou.noosa.audio.Sample;
import com.watabou.utils.Bundle;
import com.watabou.utils.PathFinder;
import com.watabou.utils.Random;

import java.util.ArrayList;

/** Grave pact: wand charges zapped into a shade, each adding proc chance to its swings for a few turns. */
public class GraveImbue extends Buff {

	{
		type = buffType.POSITIVE;
	}

	public static final float CHANCE_PER_CHARGE = 0.3f;
	public static final float CHARGE_DURATION = 4f;

	private Class<? extends Wand> wandClass;
	private int wandLevel;
	private final ArrayList<Float> expiries = new ArrayList<>();

	public static void imbue( GraveShade shade, Wand wand, int charges ){
		GraveImbue imbue = Buff.affect(shade, GraveImbue.class);
		if (imbue.wandClass != wand.getClass()){
			imbue.expiries.clear();
			imbue.wandClass = wand.getClass();
		}
		imbue.wandLevel = Math.max(0, wand.buffedLvl());
		for (int i = 0; i < charges; i++){
			imbue.expiries.add(Actor.now() + CHARGE_DURATION);
		}
		if (shade.sprite != null){
			shade.sprite.centerEmitter().burst(EnergyParticle.FACTORY, 6);
			shade.sprite.showStatus(CharSprite.POSITIVE, "%d%%", Math.round(imbue.chance()*100));
		}
	}

	public float chance(){
		return expiries.size() * CHANCE_PER_CHARGE;
	}

	public boolean grantsReach(){
		return wandClass == WandOfDisintegration.class && !expiries.isEmpty();
	}

	@Override
	public boolean act() {
		float now = Actor.now();
		for (int i = expiries.size()-1; i >= 0; i--){
			if (expiries.get(i) <= now) expiries.remove(i);
		}
		if (expiries.isEmpty()){
			detach();
		} else {
			spend(TICK);
		}
		return true;
	}

	public int proc( GraveShade shade, Char enemy, int damage ){
		if (wandClass == null || wandClass == WandOfDisintegration.class || expiries.isEmpty()){
			return damage;
		}
		float chance = chance();
		float power;
		if (chance > 1f){
			power = chance;
		} else if (Random.Float() < chance){
			power = 1f;
		} else {
			return damage;
		}

		Hero hero = Dungeon.hero;

		if (wandClass == WandOfFireblast.class){
			float powerMulti = power;
			if (enemy.buff(Burning.class) == null){
				Buff.affect(enemy, Burning.class).reignite(enemy, 8f);
				powerMulti -= 1;
			}
			if (powerMulti > 0){
				int burnDamage = Random.NormalIntRange( 1, 3 + Dungeon.scalingDepth()/4 );
				burnDamage = Math.round(burnDamage * 0.67f * powerMulti);
				if (burnDamage > 0) enemy.damage(burnDamage, this);
			}
			enemy.sprite.emitter().burst( FlameParticle.FACTORY, wandLevel + 1 );

		} else if (wandClass == WandOfLightning.class){
			int dist = 2;
			if (power > 1f) dist++;
			if (Dungeon.level.water[enemy.pos] && !enemy.flying) dist++;
			ArrayList<Char> affected = new ArrayList<>();
			ArrayList<Lightning.Arc> arcs = new ArrayList<>();
			Shocking.arc(shade, enemy, dist, affected, arcs);
			affected.remove(enemy);
			for (Char ch : affected){
				if (ch.alignment != shade.alignment){
					ch.damage(Math.round(damage * 0.5f * power), this);
				}
			}
			shade.sprite.parent.addToFront( new Lightning( arcs, null ) );
			Sample.INSTANCE.play( Assets.Sounds.LIGHTNING );

		} else if (wandClass == WandOfFrost.class){
			Chill chill = enemy.buff(Chill.class);
			if (chill != null && chill.cooldown() >= 6f){
				final int freeze = Math.round(4 * power);
				//delayed so the swing's own damage does not break the freeze
				new FlavourBuff(){
					{ actPriority = VFX_PRIO; }
					public boolean act() {
						Buff.affect(target, Frost.class, freeze);
						return super.act();
					}
				}.attachTo(enemy);
			} else {
				float durationToAdd = 3f * power;
				if (chill != null){
					durationToAdd = Math.min(durationToAdd, (6f*power) - chill.cooldown());
				}
				if (durationToAdd > 0) Buff.affect(enemy, Chill.class, durationToAdd);
			}
			Splash.at( enemy.sprite.center(), 0xFFB2D6FF, 5);

		} else if (wandClass == WandOfCorrosion.class){
			Poison poison = enemy.buff(Poison.class);
			if (poison == null){
				poison = Buff.affect(enemy, Poison.class);
				poison.delay(3f);
				poison = enemy.buff(Poison.class);
			} else {
				Buff.affect(enemy, Ooze.class).set(5 * power);
				CellEmitter.center(enemy.pos).burst( CorrosionParticle.SPLASH, 5 );
			}
			if (poison != null){
				poison.extend(power * ((wandLevel / 2f) + 3));
			}
			CellEmitter.center(enemy.pos).burst(PoisonParticle.SPLASH, 5);

		} else if (wandClass == WandOfBlastWave.class){
			if (enemy.buff(Paralysis.class) != null && enemy.buff(BlastTracker.class) == null){
				enemy.buff(Paralysis.class).detach();
				enemy.damage(Math.round(damage * 0.25f * power), this);
				WandOfBlastWave.BlastWave.blast(enemy.pos);
				Sample.INSTANCE.play( Assets.Sounds.BLAST );
				Buff.prolong(enemy, BlastTracker.class, 5f);
			}
			if (enemy.isAlive()){
				Ballistica trajectory = new Ballistica(shade.pos, enemy.pos, Ballistica.STOP_TARGET);
				trajectory = new Ballistica(trajectory.collisionPos, trajectory.path.get(trajectory.path.size()-1), Ballistica.PROJECTILE);
				WandOfBlastWave.throwChar(enemy, trajectory, power > 1f ? 3 : 2, true, true, this);
			}

		} else if (wandClass == WandOfMagicMissile.class){
			float gain = power > 1f ? 1f : 0.5f;
			for (Wand.Charger c : hero.buffs(Wand.Charger.class)){
				if (!(c.wand() instanceof WandOfMagicMissile)){
					c.gainCharge(gain);
				}
			}

		} else if (wandClass == WandOfPrismaticLight.class){
			Buff.prolong( enemy, Cripple.class, Math.round((1 + wandLevel) * power));

		} else if (wandClass == WandOfTransfusion.class){
			int heal = Math.min(hero.HT - hero.HP, Math.round(damage * 0.2f * power));
			if (heal > 0){
				hero.HP += heal;
				hero.sprite.showStatusWithIcon(CharSprite.POSITIVE, Integer.toString(heal), FloatingText.HEALING);
			}
			if (enemy.buff(Charm.class) != null){
				int shield = Math.round((2 + wandLevel) * power);
				Buff.affect(hero, Barrier.class).setShield(shield);
				hero.sprite.showStatusWithIcon(CharSprite.POSITIVE, Integer.toString(shield), FloatingText.SHIELDING);
			}

		} else if (wandClass == WandOfCorruption.class){
			Buff.prolong( enemy, Amok.class, Math.round((4 + wandLevel * 2) * power));

		} else if (wandClass == WandOfRegrowth.class){
			if (isGrass(shade.pos) || isGrass(enemy.pos)){
				Buff.affect(hero, Sungrass.Health.class).boost(Math.round(3 * power));
			}
			plantGrass(enemy.pos);
			if (power > 1f){
				ArrayList<Integer> spots = new ArrayList<>();
				for (int n : PathFinder.NEIGHBOURS8){
					int cell = enemy.pos + n;
					if (canPlant(cell) && Dungeon.level.map[cell] != Terrain.GRASS) spots.add(cell);
				}
				if (!spots.isEmpty()) plantGrass(Random.element(spots));
			}
		}

		return damage;
	}

	private static boolean isGrass( int cell ){
		int terr = Dungeon.level.map[cell];
		return terr == Terrain.GRASS || terr == Terrain.HIGH_GRASS || terr == Terrain.FURROWED_GRASS;
	}

	private static boolean canPlant( int cell ){
		int terr = Dungeon.level.map[cell];
		return terr == Terrain.EMPTY || terr == Terrain.EMBERS || terr == Terrain.EMPTY_DECO || terr == Terrain.GRASS;
	}

	private static void plantGrass( int cell ){
		if (canPlant(cell) && Dungeon.level.map[cell] != Terrain.GRASS){
			Level.set(cell, Terrain.GRASS);
			GameScene.updateMap(cell);
		}
	}

	public static class BlastTracker extends FlavourBuff {}

	private static final String WAND = "wand";
	private static final String LEVEL = "level";
	private static final String EXPIRIES = "expiries";

	@Override
	public void storeInBundle( Bundle bundle ) {
		super.storeInBundle( bundle );
		if (wandClass != null) bundle.put( WAND, wandClass );
		bundle.put( LEVEL, wandLevel );
		float[] times = new float[expiries.size()];
		for (int i = 0; i < times.length; i++) times[i] = expiries.get(i);
		bundle.put( EXPIRIES, times );
	}

	@Override
	@SuppressWarnings("unchecked")
	public void restoreFromBundle( Bundle bundle ) {
		super.restoreFromBundle( bundle );
		if (bundle.contains(WAND)) wandClass = (Class<? extends Wand>) bundle.getClass( WAND );
		wandLevel = bundle.getInt( LEVEL );
		expiries.clear();
		if (bundle.contains(EXPIRIES)){
			for (float t : bundle.getFloatArray( EXPIRIES )) expiries.add(t);
		}
	}
}
