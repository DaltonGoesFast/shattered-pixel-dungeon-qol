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
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.plants.Plant;
import com.shatteredpixel.shatteredpixeldungeon.plants.Rotberry;
import com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite;
import com.shatteredpixel.shatteredpixeldungeon.ui.BuffIndicator;
import com.watabou.noosa.Image;
import com.watabou.noosa.particles.Emitter;
import com.watabou.utils.Bundle;

import java.util.ArrayList;

//Shared time-freeze logic (hourglass artifact, streamer scroll, etc.)
public class TimeFreeze extends Buff {

	public static final String PRESSES = "presses";

	{
		type = buffType.POSITIVE;
	}

	protected float left = -1;
	protected float maxLeft = -1;

	protected ArrayList<Integer> presses = new ArrayList<>();

	/** Returns any active time-freeze buff, including hourglass's inner subclass. */
	public static TimeFreeze on( Char ch ){
		if (ch == null) return null;
		for (Buff b : ch.buffs()) {
			if (b instanceof TimeFreeze) {
				return (TimeFreeze) b;
			}
		}
		return null;
	}

	public void setDuration( int turns ){
		left = turns + 1; //add 1 as we're spending it on our action
		maxLeft = left;
	}

	public void processTime( float time ){
		if (left < 0) return;

		left -= time;

		if (left < -0.001f){
			detach();
		} else {
			BuffIndicator.refreshHero();
		}
	}

	public void setDelayedPress( int cell ){
		if (!presses.contains(cell))
			presses.add(cell);
	}

	public void triggerPresses(){
		ArrayList<Integer> toTrigger = presses;
		presses = new ArrayList<>();
		Actor.add(new Actor() {
			{
				actPriority = VFX_PRIO;
			}

			@Override
			protected boolean act() {
				for (int cell : toTrigger){
					Plant p = Dungeon.level.plants.get(cell);
					if (p != null){
						p.trigger();
					}
					Trap t = Dungeon.level.traps.get(cell);
					if (t != null){
						t.trigger();
					}
				}
				Actor.remove(this);
				return true;
			}
		});
	}

	public void disarmPresses(){
		for (int cell : presses){
			Plant p = Dungeon.level.plants.get(cell);
			if (p != null && !(p instanceof Rotberry)) {
				Dungeon.level.uproot(cell);
			}
			Trap t = Dungeon.level.traps.get(cell);
			if (t != null && t.disarmedByActivation) {
				t.disarm();
			}
		}

		presses = new ArrayList<>();
	}

	@Override
	public void detach(){
		super.detach();
		triggerPresses();
		if (target != null) target.next();
	}

	@Override
	public void fx(boolean on) {
		if (!(target instanceof Hero)) return;
		Emitter.freezeEmitters = on;
		if (on){
			for (Mob mob : Dungeon.level.mobs.toArray(new Mob[0])) {
				if (mob.sprite != null) mob.sprite.add(CharSprite.State.PARALYSED);
			}
		} else {
			for (Mob mob : Dungeon.level.mobs.toArray(new Mob[0])) {
				if (mob.paralysed <= 0) mob.sprite.remove(CharSprite.State.PARALYSED);
			}
		}
	}

	@Override
	public int icon() {
		return BuffIndicator.TIME;
	}

	@Override
	public void tintIcon(Image icon) {
		icon.hardlight(1f, 0.5f, 0);
	}

	@Override
	public float iconFadePercent() {
		if (left < 0 || maxLeft <= 0) return 0;
		return Math.max(0, (maxLeft - left) / maxLeft);
	}

	@Override
	public String iconTextDisplay() {
		if (left < 0) return "";
		return Integer.toString((int)(left + 0.001f));
	}

	@Override
	public String desc() {
		if (left < 0){
			return Messages.get(this, "desc_indefinite");
		}
		return Messages.get(this, "desc", Messages.decimalFormat("#.##", Math.max(0, left)));
	}

	private static final String LEFT = "left";
	private static final String MAX_LEFT = "maxLeft";

	@Override
	public void storeInBundle(Bundle bundle) {
		super.storeInBundle(bundle);

		int[] values = new int[presses.size()];
		for (int i = 0; i < values.length; i ++)
			values[i] = presses.get(i);
		bundle.put( PRESSES , values );

		bundle.put( LEFT, left);
		bundle.put( MAX_LEFT, maxLeft);
	}

	@Override
	public void restoreFromBundle(Bundle bundle) {
		super.restoreFromBundle(bundle);

		int[] values = bundle.getIntArray( PRESSES );
		for (int value : values)
			presses.add(value);

		left = bundle.getFloat(LEFT);
		maxLeft = bundle.contains(MAX_LEFT) ? bundle.getFloat(MAX_LEFT) : left;
	}

}
