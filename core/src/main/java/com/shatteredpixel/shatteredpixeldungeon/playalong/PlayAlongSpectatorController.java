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

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.FirstDescent;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.Statistics;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.items.Generator;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.secret.SecretRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.SpecialRoom;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.watabou.noosa.Game;
import com.watabou.utils.Bundle;
import com.watabou.utils.PathFinder;

import java.util.ArrayList;

/**
 * Manages Spirit Cam spectator mode using a read-only floor-preview path that never
 * mutates slot 99 save files, {@code Dungeon.generatedLevels}, or run progression globals.
 */
public final class PlayAlongSpectatorController {

	private static final String SNAP_LIM_DROPS = "limDrops";

	public void magicMapCurrentFloor() {
		if (Dungeon.level == null) return;
		int length = Dungeon.level.length();
		int[] map = Dungeon.level.map;
		boolean[] mapped = Dungeon.level.mapped;
		boolean[] visited = Dungeon.level.visited;
		boolean[] discoverable = Dungeon.level.discoverable;

		for (int i = 0; i < length; i++) {
			int terr = map[i];
			if (discoverable[i]) {
				mapped[i] = true;
				visited[i] = true;
				if ((Terrain.flags[terr] & Terrain.SECRET) != 0) {
					Dungeon.level.discover(i);
					if (Game.scene() instanceof GameScene) {
						GameScene.discoverTile(i, terr);
					}
				}
			}
		}
		if (Dungeon.level.heaps != null) {
			for (Heap h : Dungeon.level.heaps.valueList()) {
				if (h != null) h.seen = true;
			}
		}
		if (Game.scene() instanceof GameScene) {
			GameScene.updateFog();
		}
	}

	public void followHostFloor(PlayAlongService.Role role, boolean hostAliveForSpectate,
	                            int hostDepth, int hostBranch, int hostPos) {
		if (role != PlayAlongService.Role.SPECTATOR || !hostAliveForSpectate) return;
		if (!(Game.scene() instanceof GameScene)) return;
		InterlevelScene.mode = InterlevelScene.Mode.RETURN;
		InterlevelScene.returnDepth = Math.max(1, hostDepth);
		InterlevelScene.returnBranch = Math.max(0, hostBranch);
		InterlevelScene.returnPos = Math.max(0, hostPos);
		InterlevelScene.debugGenerateSkipped = false;
		Game.switchScene(InterlevelScene.class);
	}

	public void checkSpectatorFloorFollow(PlayAlongService.Role role, boolean hostAliveForSpectate,
	                                      int hostDepth, int hostBranch, int hostPos) {
		if (role == PlayAlongService.Role.SPECTATOR && hostAliveForSpectate
				&& (Dungeon.depth != hostDepth || Dungeon.branch != hostBranch)
				&& Game.scene() instanceof GameScene) {
			followHostFloor(role, hostAliveForSpectate, hostDepth, hostBranch, hostPos);
		}
	}

	/**
	 * Read-only floor follow for Spirit Cam. Loads an existing slot-99 floor or previews an
	 * ungenerated floor strictly in memory while restoring all {@link Dungeon}/{@link Statistics}
	 * run-progression globals and writing nothing to disk.
	 */
	public void spectatorReturnTo(int targetDepth, int targetBranch, int targetPos) {
		int clampedDepth = Math.max(1, targetDepth);
		int clampedBranch = Math.max(0, targetBranch);

		ArrayList<Integer> savedGeneratedLevels = new ArrayList<>(Dungeon.generatedLevels);
		Bundle snapshot = captureLevelGenGlobals();

		Level level = null;
		Dungeon.depth = clampedDepth;
		Dungeon.branch = clampedBranch;
		try {
			if (Dungeon.levelHasBeenGenerated(clampedDepth, clampedBranch)) {
				try {
					level = Dungeon.loadLevel(GamesInProgress.curSlot);
				} catch (Exception e) {
					level = Dungeon.newLevel();
				}
			} else {
				level = Dungeon.newLevel();
			}
		} finally {
			Dungeon.generatedLevels.clear();
			Dungeon.generatedLevels.addAll(savedGeneratedLevels);
			restoreLevelGenGlobals(snapshot);
		}

		if (level == null) return;

		PathFinder.setMapSize(level.width(), level.height());
		Dungeon.level = level;

		int safePos = targetPos;
		if (safePos < 0 || safePos >= level.length()) {
			safePos = level.entrance();
			if (safePos < 0 || safePos >= level.length()) {
				safePos = Math.max(0, Math.min(level.length() - 1, level.width() + 1));
			}
		}

		if (Dungeon.hero != null) {
			Dungeon.hero.pos = safePos;
			Dungeon.hero.curAction = Dungeon.hero.lastAction = null;
			Actor.init();
		} else {
			Actor.clear();
		}
		magicMapCurrentFloor();
		if (Dungeon.hero != null) {
			Dungeon.observe();
		}
	}

	private static Bundle captureLevelGenGlobals() {
		Bundle snapshot = new Bundle();
		Statistics.storeInBundle(snapshot);
		FirstDescent.store(snapshot);
		Bundle limDrops = new Bundle();
		Dungeon.LimitedDrops.store(limDrops);
		snapshot.put(SNAP_LIM_DROPS, limDrops);
		SpecialRoom.storeRoomsInBundle(snapshot);
		SecretRoom.storeRoomsInBundle(snapshot);
		Generator.storeInBundle(snapshot);
		Actor.storeNextID(snapshot);
		return snapshot;
	}

	private static void restoreLevelGenGlobals(Bundle snapshot) {
		Statistics.restoreFromBundle(snapshot);
		FirstDescent.restore(snapshot);
		Dungeon.LimitedDrops.restore(snapshot.getBundle(SNAP_LIM_DROPS));
		SpecialRoom.restoreRoomsFromBundle(snapshot);
		SecretRoom.restoreRoomsFromBundle(snapshot);
		Generator.restoreFromBundle(snapshot);
		Actor.restoreNextID(snapshot);
	}
}
