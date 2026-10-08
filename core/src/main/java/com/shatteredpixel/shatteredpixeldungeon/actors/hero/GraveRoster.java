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

package com.shatteredpixel.shatteredpixeldungeon.actors.hero;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.Modifiers;
import com.shatteredpixel.shatteredpixeldungeon.Statistics;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Hunger;
import com.shatteredpixel.shatteredpixeldungeon.items.GraveChalice;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.GraveShade;
import com.shatteredpixel.shatteredpixeldungeon.effects.CellEmitter;
import com.shatteredpixel.shatteredpixeldungeon.effects.particles.ShadowParticle;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.spells.Stasis;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite;
import com.shatteredpixel.shatteredpixeldungeon.ui.BuffIndicator;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.watabou.noosa.audio.Sample;
import com.watabou.utils.Bundle;
import com.watabou.utils.PathFinder;

/**
 * Grave pact roster: four shade slots on point clocks. Only the lowest dead slot's clock
 * moves; each completed offense against an enemy shaves {@link #SHAVE}.
 * When a wraith is ready, that offense calls it instead: a melee click, a wand zap, or a shot.
 */
public class GraveRoster extends Buff {

	public static final int SLOTS = 4;
	public static final int SHAVE = 4;
	public static final int[] COOLDOWN = { 8, 48, 96, 160 };

	{
		type = buffType.POSITIVE;
		revivePersists = true;
	}

	private int unlocked = 1;
	private final int[] cooldown = { COOLDOWN[0], COOLDOWN[1], COOLDOWN[2], COOLDOWN[3] };
	private final boolean[] alive = new boolean[SLOTS];
	private final int[] summonId = new int[SLOTS];

	private boolean chaliceGiven = false;

	@Override
	public boolean act() {
		ensureChalice();
		diactivate();
		return true;
	}

	public static GraveRoster get(){
		Hero hero = Dungeon.hero;
		if (hero == null || !Dungeon.isModified(Modifiers.GRAVE)) return null;
		GraveRoster roster = hero.buff(GraveRoster.class);
		if (roster == null){
			roster = Buff.affect(hero, GraveRoster.class);
		}
		roster.ensureChalice();
		return roster;
	}

	private void ensureChalice(){
		if (Dungeon.level == null || Dungeon.hero == null || !Dungeon.hero.isAlive()) return;
		if (Dungeon.hero.belongings.getItem(GraveChalice.class) != null) return;
		chaliceGiven = true;
		GraveChalice chalice = new GraveChalice();
		if (!chalice.collect()){
			Dungeon.level.drop(chalice, Dungeon.hero.pos);
		}
		GLog.w( Messages.get(GraveChalice.class, "appears") );
	}

	public static final int BLEED_HUNGER_BASE = 48;

	/**
	 * Grave chalice: summons the ready or counting wraith at once. Costs 1% max HP per 2 points left
	 * and 48 + points left satiety; hunger past starving lands as starvation damage. Can kill.
	 */
	public static void bleed( Hero hero, GraveChalice chalice ){
		GraveRoster roster = get();
		if (roster == null) return;
		roster.validate();

		int idx = roster.lowestReady();
		if (idx < 0) idx = roster.countingSlot();
		if (idx < 0){
			GLog.i( Messages.get(GraveChalice.class, "silent") );
			return;
		}
		if (roster.summonCell(hero) == -1){
			GLog.i( Messages.get(GraveChalice.class, "no_room") );
			return;
		}

		int points = Math.max(0, roster.cooldown[idx]);
		int hpCost = (int)Math.ceil(points * hero.HT / 200f);
		int hungerCost = BLEED_HUNGER_BASE + points;

		hero.sprite.operate(hero.pos);
		hero.sprite.emitter().burst( ShadowParticle.CURSE, 4 + hpCost/10 );
		Sample.INSTANCE.play( Assets.Sounds.CURSED );

		if (hpCost > 0){
			hero.damage( hpCost, chalice );
			if (!hero.isAlive()){
				Dungeon.fail( chalice );
				GLog.n( Messages.get(GraveChalice.class, "ondeath") );
				return;
			}
		}

		Hunger hunger = hero.buff(Hunger.class);
		if (hunger != null){
			hunger.affectHunger( -hungerCost );
			if (!hero.isAlive()) return;
		}

		GLog.n( Messages.get(GraveChalice.class, "drink") );
		roster.summon(hero, idx);
		hero.spendAndNext( Actor.TICK );
	}

	public static boolean owns( GraveShade shade ){
		GraveRoster roster = get();
		if (roster == null || shade.slot < 0 || shade.slot >= SLOTS) return false;
		return roster.alive[shade.slot] && roster.summonId[shade.slot] == shade.summonId;
	}

	public static void onShadeDeath( GraveShade shade ){
		GraveRoster roster = get();
		if (roster == null || !owns(shade)) return;
		roster.alive[shade.slot] = false;
		roster.cooldown[shade.slot] = COOLDOWN[shade.slot];
		BuffIndicator.refreshHero();
	}

	/** Opens slot {@code slotNumber} (1-4). A newly opened slot starts on its full cooldown. */
	public static void unlock( int slotNumber ){
		GraveRoster roster = get();
		if (roster == null) return;
		int idx = slotNumber - 1;
		if (idx < 0 || idx >= SLOTS || idx < roster.unlocked) return;
		for (int i = roster.unlocked; i <= idx; i++){
			roster.cooldown[i] = COOLDOWN[i];
			roster.alive[i] = false;
		}
		roster.unlocked = idx + 1;
		BuffIndicator.refreshHero();
	}

	/**
	 * A completed offense calls a ready wraith and does not also shave the clock.
	 * @return true when a ready wraith was called, including when no cell is free for it
	 */
	public static boolean callIfReady( Hero hero ){
		GraveRoster roster = get();
		if (roster == null || hero == null) return false;
		roster.validate();
		int ready = roster.lowestReady();
		if (ready < 0) return false;
		roster.summon(hero, ready);
		return true;
	}

	/** One completed offense against an enemy, when no wraith was ready to call. */
	public static void noteOffense(){
		GraveRoster roster = get();
		if (roster == null) return;
		roster.validate();
		int idx = roster.countingSlot();
		if (idx >= 0 && roster.cooldown[idx] > 0){
			roster.cooldown[idx] = Math.max(0, roster.cooldown[idx] - SHAVE);
			if (roster.cooldown[idx] == 0){
				GLog.p( Messages.get(GraveRoster.class, "ready") );
				Hero hero = Dungeon.hero;
				if (hero.sprite != null){
					hero.sprite.showStatus( CharSprite.POSITIVE, Messages.get(GraveRoster.class, "status_ready") );
				}
			}
		}
		BuffIndicator.refreshHero();
	}

	private static int attacksLeft( int points ){
		return (points + SHAVE - 1) / SHAVE;
	}

	@Override
	public int icon() {
		return BuffIndicator.CORRUPT;
	}

	@Override
	public String iconTextDisplay() {
		int idx = countingSlot();
		if (idx < 0) return "";
		return Integer.toString(attacksLeft(cooldown[idx]));
	}

	@Override
	public float iconFadePercent() {
		int idx = countingSlot();
		if (idx < 0) return 0;
		return cooldown[idx] / (float) COOLDOWN[idx];
	}

	@Override
	public String desc() {
		validate();
		StringBuilder lines = new StringBuilder();
		int counting = countingSlot();
		for (int i = 0; i < SLOTS; i++){
			if (i > 0) lines.append("\n");
			int n = i + 1;
			if (i >= unlocked){
				lines.append(Messages.get(this, "slot_locked_" + n));
			} else if (alive[i]){
				lines.append(Messages.get(this, "slot_alive", n));
			} else if (cooldown[i] <= 0){
				lines.append(Messages.get(this, "slot_ready", n));
			} else if (i == counting){
				lines.append(Messages.get(this, "slot_counting", n, attacksLeft(cooldown[i])));
			} else {
				lines.append(Messages.get(this, "slot_waiting", n, attacksLeft(cooldown[i])));
			}
		}
		return Messages.get(this, "desc", lines.toString());
	}

	public static GraveShade findShade( int slot ){
		GraveRoster roster = get();
		if (roster == null) return null;
		for (Mob m : Dungeon.level.mobs){
			if (m instanceof GraveShade && ((GraveShade) m).slot == slot && owns((GraveShade) m)){
				return (GraveShade) m;
			}
		}
		if (Stasis.getStasisAlly() instanceof GraveShade){
			GraveShade s = (GraveShade) Stasis.getStasisAlly();
			if (s.slot == slot && owns(s)) return s;
		}
		return null;
	}

	/** Living shades on the current floor. */
	public static java.util.ArrayList<GraveShade> livingShades(){
		java.util.ArrayList<GraveShade> out = new java.util.ArrayList<>();
		GraveRoster roster = get();
		if (roster == null) return out;
		roster.validate();
		for (Mob m : Dungeon.level.mobs.toArray(new Mob[0])){
			if (m instanceof GraveShade && m.isAlive() && owns((GraveShade) m)){
				out.add((GraveShade) m);
			}
		}
		return out;
	}

	/** Nearest living shade to {@code cell}, or null. */
	public static GraveShade nearestShade( int cell ){
		GraveShade best = null;
		for (GraveShade s : livingShades()){
			if (best == null || Dungeon.level.distance(s.pos, cell) < Dungeon.level.distance(best.pos, cell)){
				best = s;
			}
		}
		return best;
	}

	/** The shade already fighting {@code enemy}, else the living shade nearest it, else null. */
	public static GraveShade shadeFor( Char enemy ){
		GraveShade nearest = null;
		for (GraveShade s : livingShades()){
			if (s.isTargeting(enemy)) return s;
			if (nearest == null || Dungeon.level.distance(s.pos, enemy.pos) < Dungeon.level.distance(nearest.pos, enemy.pos)){
				nearest = s;
			}
		}
		return nearest;
	}

	/** Sends every living shade after {@code enemy}. */
	public static void rallyTo( Char enemy ){
		if (enemy == null || !enemy.isAlive() || enemy.alignment != Char.Alignment.ENEMY
				|| !Dungeon.isModified(Modifiers.GRAVE)) return;
		for (GraveShade s : livingShades()){
			if (!s.isTargeting(enemy)) s.command(enemy);
		}
	}

	/** Hero attack-click on an enemy under Grave. Caller spends the hero's time. */
	public static void heroClick( Hero hero, Char target ){
		if (get() == null) return;
		if (callIfReady(hero)) return;

		GraveShade sender = null;
		for (GraveShade s : livingShades()){
			if (s.isTargeting(target)) continue;
			if (sender == null || Dungeon.level.distance(s.pos, target.pos) < Dungeon.level.distance(sender.pos, target.pos)){
				sender = s;
			}
		}

		if (sender != null){
			sender.command(target);
		} else if (livingShades().isEmpty()){
			failCall();
		}
		noteOffense();
	}

	public static void failCall(){
		GLog.w( Messages.get(GraveRoster.class, "no_call") );
	}

	/** The fallen slot closest to returning; ties go to the lower slot. */
	private int countingSlot(){
		int best = -1;
		for (int i = 0; i < unlocked; i++){
			if (!alive[i] && (best == -1 || cooldown[i] < cooldown[best])) best = i;
		}
		return best;
	}

	private int lowestReady(){
		for (int i = 0; i < unlocked; i++){
			if (!alive[i] && cooldown[i] <= 0) return i;
		}
		return -1;
	}

	/** A slot marked alive whose shade is not on this floor (or in stasis) counts as dead. */
	private void validate(){
		if (Dungeon.level == null) return;
		//being below a boss floor means that boss is behind you, even if its death hook never ran
		int earned = 1 + Math.min(SLOTS - 1, Math.max(0, (Statistics.deepestFloor - 1) / 5));
		if (earned > unlocked) unlock(earned);
		for (int i = 0; i < unlocked; i++){
			if (alive[i] && findShade(i) == null){
				alive[i] = false;
				cooldown[i] = COOLDOWN[i];
				BuffIndicator.refreshHero();
			}
		}
	}

	private int summonCell( Hero hero ){
		for (int n : PathFinder.NEIGHBOURS8){
			int c = hero.pos + n;
			if ((Dungeon.level.passable[c] || Dungeon.level.avoid[c]) && Actor.findChar(c) == null){
				return c;
			}
		}
		return -1;
	}

	private boolean summon( Hero hero, int idx ){
		int cell = summonCell(hero);
		if (cell == -1){
			failCall();
			return true;
		}

		GraveShade shade = new GraveShade();
		shade.slot = idx;
		shade.summonId = ++summonId[idx];
		shade.pos = cell;
		shade.refreshStats();
		alive[idx] = true;
		cooldown[idx] = 0;
		BuffIndicator.refreshHero();

		GameScene.add(shade);
		Dungeon.level.occupyCell(shade);
		if (Dungeon.level.heroFOV[cell]){
			CellEmitter.get(cell).burst(ShadowParticle.UP, 6);
			Sample.INSTANCE.play(Assets.Sounds.CURSED, 0.6f, 1.3f);
		}
		return true;
	}

	private static final String UNLOCKED = "unlocked";
	private static final String COOLDOWNS = "cooldowns";
	private static final String ALIVE = "alive";
	private static final String SUMMON_IDS = "summon_ids";
	private static final String CHALICE_GIVEN = "chalice_given";

	@Override
	public void storeInBundle( Bundle bundle ) {
		super.storeInBundle( bundle );
		bundle.put( UNLOCKED, unlocked );
		bundle.put( COOLDOWNS, cooldown );
		bundle.put( ALIVE, alive );
		bundle.put( SUMMON_IDS, summonId );
		bundle.put( CHALICE_GIVEN, chaliceGiven );
	}

	@Override
	public void restoreFromBundle( Bundle bundle ) {
		super.restoreFromBundle( bundle );
		unlocked = Math.max(1, Math.min(SLOTS, bundle.getInt( UNLOCKED )));
		chaliceGiven = bundle.getBoolean( CHALICE_GIVEN );
		int[] cd = bundle.getIntArray( COOLDOWNS );
		boolean[] al = bundle.getBooleanArray( ALIVE );
		int[] ids = bundle.getIntArray( SUMMON_IDS );
		for (int i = 0; i < SLOTS; i++){
			if (cd != null && i < cd.length) cooldown[i] = cd[i];
			if (al != null && i < al.length) alive[i] = al[i];
			if (ids != null && i < ids.length) summonId[i] = ids[i];
		}
	}
}
