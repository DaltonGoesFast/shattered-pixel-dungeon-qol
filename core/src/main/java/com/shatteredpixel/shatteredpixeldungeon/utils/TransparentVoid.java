/*
 * Pixel Dungeon
 * Copyright (C) 2012-2015 Oleg Dolya
 *
 * Shattered Pixel Dungeon
 * Copyright (C) 2014-2026 Evan Debenham
 *
 * This program is free software; you can redistribute it and/or modify
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

package com.shatteredpixel.shatteredpixeldungeon.utils;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.watabou.utils.DeviceCompat;

public final class TransparentVoid {

	private TransparentVoid() {
	}

	/** OBS transparent void mode (desktop launcher + prefs; requires restart after toggle). */
	public static boolean enabled() {
		return DeviceCompat.isDesktop() && SPDSettings.transparentVoidEnabled();
	}

	/** Mirrors {@code FogOfWar} invisible-cell logic when full fog would paint opaque black. */
	public static boolean hidesCell(int cell) {
		if (!enabled() || Dungeon.level == null) return false;

		boolean[] discoverable = Dungeon.level.discoverable;
		boolean[] heroFOV = Dungeon.level.heroFOV;
		boolean[] visited = Dungeon.level.visited;
		boolean[] mapped = Dungeon.level.mapped;
		if (discoverable == null || heroFOV == null || visited == null || mapped == null) {
			return false;
		}
		if (cell < 0 || cell >= discoverable.length) return false;
		if (!discoverable[cell]) return true;
		return !heroFOV[cell] && !visited[cell] && !mapped[cell];
	}

	/** Each horizontal run where {@link #hidesCell} is false ([startCol, endColExclusive)). */
	@FunctionalInterface
	public interface NonVoidHorizontalRunCallback {
		void onRun(int row, int startCol, int endColExclusive);
	}

	/**
	 * Invokes callback for contiguous columns on each row that are not OBS-transparent void cells.
	 */
	public static void forEachNonVoidHorizontalRun(NonVoidHorizontalRunCallback cb) {
		if (!enabled() || Dungeon.level == null || cb == null) return;

		int mw = Dungeon.level.width();
		int mh = Dungeon.level.height();
		int len = Dungeon.level.length();
		for (int row = 0; row < mh; row++) {
			int rowBase = row * mw;
			int col = 0;
			while (col < mw && rowBase + col < len) {
				while (col < mw && hidesCell(rowBase + col)) {
					col++;
				}
				int startCol = col;
				while (col < mw && !hidesCell(rowBase + col)) {
					col++;
				}
				if (startCol < col) {
					cb.onRun(row, startCol, col);
				}
			}
		}
	}
}
