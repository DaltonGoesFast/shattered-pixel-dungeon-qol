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

import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.GhostState;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.TombstoneState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe client-side and host-side store of Play Along ghosts, tombstones,
 * floor occupancy counts, and live host position snapshots.
 */
public final class PlayAlongGhostStore {

	private final ConcurrentHashMap<String, GhostState> remoteGhosts = new ConcurrentHashMap<>();
	private volatile GhostState hostGhost = null;
	private final CopyOnWriteArrayList<TombstoneState> tombstones = new CopyOnWriteArrayList<>();
	private final Map<Integer, Integer> depthCounts = new ConcurrentHashMap<>();

	private volatile int hostDepth = 1;
	private volatile int hostBranch = 0;
	private volatile int hostPos = 0;
	private volatile int aliveCount = 0;

	public int getHostDepth() {
		return hostDepth;
	}

	public int getHostBranch() {
		return hostBranch;
	}

	public int getHostPos() {
		return hostPos;
	}

	public int getAliveCount() {
		return aliveCount;
	}

	public GhostState getHostGhost() {
		return hostGhost;
	}

	public void setHostPosition(int depth, int branch, int pos) {
		this.hostDepth = depth;
		this.hostBranch = branch;
		this.hostPos = pos;
	}

	public void setHostGhost(GhostState ghost) {
		this.hostGhost = ghost;
		if (ghost != null) {
			this.hostDepth = ghost.depth;
			this.hostBranch = ghost.branch;
			this.hostPos = ghost.pos;
		}
	}

	public void applyWorldStateSnapshot(GhostState newHostGhost, List<GhostState> ghosts,
	                                    List<TombstoneState> tombs, int alive,
	                                    Map<Integer, Integer> depths) {
		if (newHostGhost != null) {
			setHostGhost(newHostGhost);
		}
		if (ghosts != null) {
			remoteGhosts.clear();
			for (GhostState g : ghosts) {
				if (g != null && g.userId != null) {
					remoteGhosts.put(g.userId, g);
				}
			}
		}
		if (tombs != null) {
			tombstones.clear();
			tombstones.addAll(tombs);
		}
		if (depths != null) {
			depthCounts.clear();
			depthCounts.putAll(depths);
		} else if (ghosts != null) {
			depthCounts.clear();
			for (GhostState g : ghosts) {
				if (g != null) {
					depthCounts.put(g.depth, depthCounts.getOrDefault(g.depth, 0) + 1);
				}
			}
		}
		aliveCount = alive;
	}

	public List<GhostState> getGhostsForFloor(int depth, int branch, PlayAlongService.Role role, String localUserId) {
		List<GhostState> list = new ArrayList<>();
		if (role != PlayAlongService.Role.HOST) {
			GhostState hg = hostGhost;
			if (hg != null && hg.depth == depth && hg.branch == branch) {
				list.add(hg);
			}
		}
		for (GhostState g : remoteGhosts.values()) {
			if (g != null && g.depth == depth && g.branch == branch) {
				if (role == PlayAlongService.Role.CLIENT && g.userId != null && g.userId.equals(localUserId)) {
					continue;
				}
				list.add(g);
			}
		}
		return list;
	}

	public List<GhostState> getAllRosterGhosts() {
		List<GhostState> list = new ArrayList<>();
		GhostState hg = hostGhost;
		if (hg != null) list.add(hg);
		list.addAll(remoteGhosts.values());
		return list;
	}

	public List<TombstoneState> getTombstonesForFloor(int depth, int branch) {
		List<TombstoneState> list = new ArrayList<>();
		for (TombstoneState t : tombstones) {
			if (t.depth == depth && t.branch == branch) {
				list.add(t);
			}
		}
		return list;
	}

	public TombstoneState getTombstoneAt(int depth, int branch, int cell) {
		for (TombstoneState t : tombstones) {
			if (t.depth == depth && t.branch == branch && t.pos == cell) {
				return t;
			}
		}
		return null;
	}

	public Map<Integer, Integer> getDepthCountsSnapshot() {
		return new LinkedHashMap<>(depthCounts);
	}

	public void clear() {
		remoteGhosts.clear();
		hostGhost = null;
		tombstones.clear();
		depthCounts.clear();
		aliveCount = 0;
	}
}
