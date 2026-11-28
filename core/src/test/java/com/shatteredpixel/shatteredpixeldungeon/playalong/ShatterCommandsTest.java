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
 */

package com.shatteredpixel.shatteredpixeldungeon.playalong;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ShatterCommandsTest {

	@Test
	public void capGrowsByChapter() {
		assertEquals(4, ShatterCommands.capForDeepestFloor(1));
		assertEquals(4, ShatterCommands.capForDeepestFloor(5));
		assertEquals(6, ShatterCommands.capForDeepestFloor(6));
		assertEquals(6, ShatterCommands.capForDeepestFloor(10));
		assertEquals(8, ShatterCommands.capForDeepestFloor(11));
		assertEquals(8, ShatterCommands.capForDeepestFloor(15));
		assertEquals(10, ShatterCommands.capForDeepestFloor(16));
		assertEquals(10, ShatterCommands.capForDeepestFloor(20));
		assertEquals(12, ShatterCommands.capForDeepestFloor(21));
		assertEquals(12, ShatterCommands.capForDeepestFloor(26));
	}

	@Test
	public void costsMatchDesign() {
		assertEquals(3, ShatterCommands.costOf(ShatterCommands.CMD_TRAP));
		assertEquals(3, ShatterCommands.costOf(ShatterCommands.CMD_DEBUFF));
		assertEquals(4, ShatterCommands.costOf(ShatterCommands.CMD_SABOTAGE));
		assertEquals(4, ShatterCommands.costOf(ShatterCommands.CMD_SPAWN));
		assertEquals(5, ShatterCommands.costOf(ShatterCommands.CMD_GAS));
		assertEquals(5, ShatterCommands.costOf(ShatterCommands.CMD_BOMB));
		assertEquals(5, ShatterCommands.costOf(ShatterCommands.CMD_HEX));
		assertEquals(5, ShatterCommands.costOf(ShatterCommands.CMD_DEGRADE));
		assertEquals(8, ShatterCommands.costOf(ShatterCommands.CMD_CURSE));
		assertEquals(8, ShatterCommands.costOf(ShatterCommands.CMD_CHAMPION));
	}

	@Test
	public void curseNeedsCavesCap() {
		assertTrue(ShatterCommands.costOf(ShatterCommands.CMD_CURSE)
				<= ShatterCommands.capForDeepestFloor(11));
		assertFalse(ShatterCommands.costOf(ShatterCommands.CMD_CURSE)
				<= ShatterCommands.capForDeepestFloor(10));
	}
}
