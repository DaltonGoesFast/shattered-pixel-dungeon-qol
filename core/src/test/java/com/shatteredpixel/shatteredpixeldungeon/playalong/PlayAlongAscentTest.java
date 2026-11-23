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

import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongAscent.Plan;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlayAlongAscentTest {

	@Test
	public void descendingHostDoesNotMirror() {
		Plan plan = PlayAlongAscent.plan(false, 0, 13);
		assertFalse(plan.active);

		PlayAlongProtocol.SessionInfo session = new PlayAlongProtocol.SessionInfo();
		session.amuletObtained = true;
		session.ascending = false;
		session.hostDepth = 26;
		session.highestAscent = 0;
		assertFalse(PlayAlongAscent.plan(session).active);
	}

	@Test
	public void cavesAscentSpawnsOnTheHostsFloorNotAFreshRegion() {
		// Host has climbed back to the caves. A descending catch-up would still
		// drop a joiner on floor 21 (after the Dwarf King) and build a new run.
		Plan plan = PlayAlongAscent.plan(true, 13, 13);
		assertTrue(plan.active);
		assertEquals(13, plan.spawnDepth);
		assertEquals(14, plan.primedHighestAscent);
		assertEquals(PlayAlongAscent.stacksAt(13) - 2f, plan.primedStacks, 0.01f);
		assertTrue(PlayAlongAscent.sealsUpward(plan.spawnDepth, 12));
		assertFalse(PlayAlongAscent.sealsUpward(plan.spawnDepth, 13));
		assertFalse(PlayAlongAscent.sealsUpward(plan.spawnDepth, 14));
	}

	@Test
	public void curseStacksMatchANormalClimb() {
		assertEquals(0f, PlayAlongAscent.stacksAt(25), 0.01f);
		assertEquals(2f, PlayAlongAscent.stacksAt(24), 0.01f);
		assertEquals(8f, PlayAlongAscent.stacksAt(20), 0.01f);
		assertEquals(10f, PlayAlongAscent.stacksAt(19), 0.01f);
		assertEquals(22f, PlayAlongAscent.stacksAt(12), 0.01f);
	}

	@Test
	public void bossFloorDoesNotPrimeAnExtraStack() {
		Plan plan = PlayAlongAscent.plan(true, 15, 15);
		assertEquals(15, plan.spawnDepth);
		assertEquals(15, plan.primedHighestAscent);
		assertEquals(PlayAlongAscent.stacksAt(15), plan.primedStacks, 0.01f);
	}

	@Test
	public void hostWhoDoubledBackKeepsTheDeeperCurse() {
		Plan plan = PlayAlongAscent.plan(true, 11, 14);
		assertEquals(14, plan.spawnDepth);
		assertEquals(15, plan.primedHighestAscent);
		assertEquals(PlayAlongAscent.stacksAt(11) - 2f, plan.primedStacks, 0.01f);
		assertTrue(PlayAlongAscent.sealsUpward(plan.spawnDepth, 10));
	}

	@Test
	public void depthIsClampedOntoTheClimb() {
		assertEquals(1, PlayAlongAscent.plan(true, 1, 0).spawnDepth);
		assertEquals(25, PlayAlongAscent.plan(true, 25, 40).spawnDepth);
		assertFalse(PlayAlongAscent.sealsUpward(1, 0));
	}

	@Test
	public void freshFloorsBelowTheJoinStillConvert() {
		assertFalse(PlayAlongAscent.needsUnvisitedConversion(13, 13, true, true, false));
		assertTrue(PlayAlongAscent.needsUnvisitedConversion(13, 14, true, false, false));
		assertFalse(PlayAlongAscent.needsUnvisitedConversion(13, 14, false, false, false));
		assertFalse(PlayAlongAscent.needsUnvisitedConversion(13, 15, true, false, true));
		assertTrue(PlayAlongAscent.needsUnvisitedConversion(0, 12, true, false, false));
	}
}
