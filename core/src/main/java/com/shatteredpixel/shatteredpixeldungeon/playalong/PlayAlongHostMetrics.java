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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe host-side operational counters and once-per-failure-class tracking for Play Along.
 */
public final class PlayAlongHostMetrics {

	private final AtomicInteger activeClients = new AtomicInteger(0);
	private final AtomicInteger aliveClients = new AtomicInteger(0);
	private final AtomicInteger totalJoins = new AtomicInteger(0);
	private final AtomicInteger rejectedJoins = new AtomicInteger(0);
	private final AtomicInteger wipeVictims = new AtomicInteger(0);
	private final AtomicInteger lastWipeVictims = new AtomicInteger(0);
	private final AtomicInteger mirrorDrops = new AtomicInteger(0);
	private final AtomicInteger httpBridgeFailures = new AtomicInteger(0);
	private final AtomicInteger wsSendFailures = new AtomicInteger(0);

	private final Set<String> reportedFailureClasses = ConcurrentHashMap.newKeySet();

	public void setActiveClients(int count) {
		activeClients.set(Math.max(0, count));
	}

	public int getActiveClients() {
		return activeClients.get();
	}

	public void setAliveClients(int count) {
		aliveClients.set(Math.max(0, count));
	}

	public int getAliveClients() {
		return aliveClients.get();
	}

	public void incrementTotalJoins() {
		totalJoins.incrementAndGet();
	}

	public int getTotalJoins() {
		return totalJoins.get();
	}

	public void incrementRejectedJoins() {
		rejectedJoins.incrementAndGet();
	}

	public int getRejectedJoins() {
		return rejectedJoins.get();
	}

	public void recordWipeVictims(int victims) {
		int clamped = Math.max(0, victims);
		lastWipeVictims.set(clamped);
		wipeVictims.addAndGet(clamped);
	}

	public int getWipeVictims() {
		return wipeVictims.get();
	}

	public int getLastWipeVictims() {
		return lastWipeVictims.get();
	}

	public void incrementMirrorDrops() {
		mirrorDrops.incrementAndGet();
	}

	public int getMirrorDrops() {
		return mirrorDrops.get();
	}

	public void incrementHttpBridgeFailures() {
		httpBridgeFailures.incrementAndGet();
	}

	public int getHttpBridgeFailures() {
		return httpBridgeFailures.get();
	}

	public void incrementWsSendFailures() {
		wsSendFailures.incrementAndGet();
	}

	public int getWsSendFailures() {
		return wsSendFailures.get();
	}

	/**
	 * Marks a failure class as reported for the current session.
	 *
	 * @return {@code true} the first time {@code failureClass} is seen, {@code false} afterward.
	 */
	public boolean markFailureClassReported(String failureClass) {
		if (failureClass == null || failureClass.isEmpty()) {
			return false;
		}
		return reportedFailureClasses.add(failureClass);
	}

	public void resetSessionFailureClasses() {
		reportedFailureClasses.clear();
	}

	public void resetAll() {
		activeClients.set(0);
		aliveClients.set(0);
		totalJoins.set(0);
		rejectedJoins.set(0);
		wipeVictims.set(0);
		lastWipeVictims.set(0);
		mirrorDrops.set(0);
		httpBridgeFailures.set(0);
		wsSendFailures.set(0);
		reportedFailureClasses.clear();
	}

	public String formatSummaryLine() {
		return "Clients: " + activeClients.get() + " (" + aliveClients.get() + " alive)"
				+ " | Wiped: " + wipeVictims.get()
				+ " | Mirror Drops: " + mirrorDrops.get()
				+ " | HTTP Errors: " + httpBridgeFailures.get();
	}
}
