/*
 * Pixel Dungeon
 * Copyright (C) 2012-2015 Oleg Dolya
 *
 * Shattered Pixel Dungeon
 * Copyright (C) 2014-2025 Evan Debenham
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
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

package com.shatteredpixel.shatteredpixeldungeon.utils;

import com.shatteredpixel.shatteredpixeldungeon.Challenges;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Static flags for streaming events (hero death, boss slain, game log messages).
 * The desktop ticker reads these and broadcasts via WebSocket.
 */
public class StreamingEvents {

	public static volatile boolean heroDiedPending = false;
	public static volatile int heroDiedChallengeCount = -1;
	public static volatile int bossSlainDepthPending = -1;

	private static final ConcurrentLinkedQueue<String> pendingLogs = new ConcurrentLinkedQueue<>();

	public static void notifyHeroDied() {
		heroDiedPending = true;
		heroDiedChallengeCount = Challenges.activeChallenges();
	}

	public static void notifyGameLog(String msg) {
		if (msg != null && !msg.trim().isEmpty() && !msg.equals(GLog.NEW_LINE)) {
			pendingLogs.offer(msg);
		}
	}

	public static List<String> drainLogs() {
		if (pendingLogs.isEmpty()) {
			return Collections.emptyList();
		}
		List<String> list = new ArrayList<>();
		String s;
		while ((s = pendingLogs.poll()) != null) {
			list.add(s);
		}
		return list;
	}

	public static void clear() {
		heroDiedPending = false;
		heroDiedChallengeCount = -1;
		bossSlainDepthPending = -1;
		pendingLogs.clear();
	}
}
