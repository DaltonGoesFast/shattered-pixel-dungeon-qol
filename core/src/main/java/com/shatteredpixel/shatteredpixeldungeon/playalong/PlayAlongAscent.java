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

package com.shatteredpixel.shatteredpixeldungeon.playalong;

/**
 * How a Play Along drop-in should mirror a host who is climbing back out with the Amulet.
 * Pure decisions only; {@link PlayAlongCatchup} applies them to the live game.
 */
public final class PlayAlongAscent {

	private PlayAlongAscent() {}

	/** Join plan for one new racer. Inactive when the host is still descending. */
	public static final class Plan {
		public final boolean active;
		/** Floor the joiner is placed on. Also the floor they cannot climb above. */
		public final int spawnDepth;
		/**
		 * {@link com.shatteredpixel.shatteredpixeldungeon.Statistics#highestAscent} to set before
		 * the first {@code switchLevel}, so that arrival counts as an ascent onto the spawn floor.
		 */
		public final int primedHighestAscent;
		/** Curse stacks to set before that first arrival adds its own +2 on a non-boss floor. */
		public final float primedStacks;

		private Plan(boolean active, int spawnDepth, int primedHighestAscent, float primedStacks) {
			this.active = active;
			this.spawnDepth = spawnDepth;
			this.primedHighestAscent = primedHighestAscent;
			this.primedStacks = primedStacks;
		}

		public static Plan inactive() {
			return new Plan(false, 0, 0, 0f);
		}
	}

	/**
	 * @param ascending host has started the ascension challenge
	 * @param highestAscent shallowest floor the host has reached since starting the climb, or 0
	 * @param hostDepth floor the host is on right now
	 */
	public static Plan plan(PlayAlongProtocol.SessionInfo session) {
		if (session == null || !session.ascending) return Plan.inactive();
		return plan(true, session.highestAscent, session.hostDepth);
	}

	public static Plan plan(boolean ascending, int highestAscent, int hostDepth) {
		if (!ascending) return Plan.inactive();
		int spawn = clamp(hostDepth, 1, 25);
		int progress = spawn;
		if (highestAscent > 0 && highestAscent < spawn) {
			progress = highestAscent;
		}
		boolean boss = isBossFloor(spawn);
		float stacks = stacksAt(progress);
		float primed = boss ? stacks : Math.max(0f, stacks - 2f);
		int primedHighest = boss ? spawn : spawn + 1;
		return new Plan(true, spawn, primedHighest, primed);
	}

	/**
	 * Curse stacks after a normal climb has arrived on {@code depth}
	 * (2 per non-boss floor from 24 down through {@code depth}; boss floors add none).
	 */
	public static float stacksAt(int depth) {
		if (depth >= 25) return 0f;
		float stacks = 0f;
		int floor = Math.max(1, depth);
		for (int f = 24; f >= floor; f--) {
			if (!isBossFloor(f)) stacks += 2f;
		}
		return stacks;
	}

	public static boolean isBossFloor(int depth) {
		return depth == 5 || depth == 10 || depth == 15 || depth == 20 || depth == 25;
	}

	/**
	 * Goo, Tengu, DM-300, the Dwarf King, and Yog are already dead on the climb.
	 * A floor built while the Amulet is held must not arm those fights again.
	 */
	public static boolean clearsBossOnBuild(boolean amuletObtained, int depth) {
		return amuletObtained && isBossFloor(depth);
	}

	/**
	 * Drop-ins still cannot take the stairs up to floors above where they joined.
	 * Ascent does not open that seal; it only changes the run they are playing below it.
	 */
	public static boolean sealsUpward(int spawnDepth, int destDepth) {
		return spawnDepth > 1 && destDepth < spawnDepth;
	}

	/**
	 * A floor this joiner just built, which {@code AscensionChallenge.onLevelSwitch} did not
	 * already treat as a new shallowest floor. Those still need the ascent population
	 * (one beckoned enemy, shops closed) instead of a full descending floor.
	 */
	public static boolean needsUnvisitedConversion(int highestAscent, int depth,
	                                               boolean freshlyGenerated, boolean alreadyConverted,
	                                               boolean bossFloor) {
		if (!freshlyGenerated || alreadyConverted || bossFloor || depth <= 0) return false;
		return highestAscent <= 0 || depth > highestAscent;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
