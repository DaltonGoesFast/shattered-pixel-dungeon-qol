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

import com.shatteredpixel.shatteredpixeldungeon.Chrome;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.GhostState;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.StyledButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.watabou.noosa.Game;
import com.watabou.utils.ColorMath;

/**
 * Compact clickable HUD badge displaying Dalton's live depth/HP danger & active Play Along racer count.
 */
public class PlayAlongHudIndicator extends StyledButton {

	private String lastLabel = "";

	public PlayAlongHudIndicator() {
		super(Chrome.Type.TAG, "LIVE", 6);
		setSize(58, 14);
	}

	@Override
	public void update() {
		super.update();

		boolean isLocalHost = PlayAlongService.isLocalHostServerRunning();
		if (!PlayAlongService.isActive() && !isLocalHost) {
			PlayAlongService.pollLiveStatus(false);
		}

		boolean shouldShow = PlayAlongService.isActive()
				|| (!isLocalHost && PlayAlongService.isStreamServerLive());
		if (!shouldShow) {
			visible = active = false;
			return;
		}
		visible = active = true;

		int alive = PlayAlongService.getAliveCount();
		int hDepth = PlayAlongService.getHostDepth();
		PlayAlongProtocol.SessionInfo live = PlayAlongService.getLiveSession();
		boolean inDungeon = PlayAlongService.isActive() || (live != null && live.inDungeon);

		String next;
		if (PlayAlongService.isHosting()) {
			String code = PlayAlongService.relayRoomCode();
			if (code != null && !PlayAlongService.relayRoomIsPublic()) {
				next = code + ": " + alive;
			} else {
				next = "LIVE: " + alive;
			}
		} else if (PlayAlongService.isSpectating()) {
			next = "CAM D" + hDepth + " (" + alive + ")";
		} else if (PlayAlongService.isClient()) {
			next = "D" + hDepth + " | " + alive;
		} else if (inDungeon && hDepth > 0) {
			next = "LIVE D" + hDepth;
		} else {
			next = "LIVE";
		}

		if (!next.equals(lastLabel)) {
			lastLabel = next;
			text(next);
			float reqW = Math.max(44, reqWidth() + 6);
			setSize(reqW, 14);
		}

		// Pulse red warning when Dalton is critical (<30% HP) for client racers
		GhostState hg = findHostGhost();
		if (PlayAlongService.isActive() && !PlayAlongService.isHosting() && hg != null && hg.hpPct > 0f && hg.hpPct < 0.30f) {
			float pulse = 0.5f + 0.5f * (float) Math.sin(Game.timeTotal * 8f);
			bg.hardlight(ColorMath.interpolate(0xFFFFFF, 0xFF2222, pulse));
			textColor(ColorMath.interpolate(0xFFFFFF, 0xFF5555, pulse));
		} else if (PlayAlongService.isHosting()) {
			bg.hardlight(0.95f, 0.55f, 0.55f);
			textColor(Window.TITLE_COLOR);
		} else if (!PlayAlongService.isActive()) {
			bg.hardlight(0.85f, 0.35f, 0.35f);
			textColor(Window.TITLE_COLOR);
		} else {
			bg.resetColor();
			textColor(Window.WHITE);
		}
	}

	private GhostState findHostGhost() {
		for (GhostState g : PlayAlongService.getAllRosterGhosts()) {
			if (g != null && g.isHost) return g;
		}
		return null;
	}

	@Override
	protected void onClick() {
		super.onClick();
		if (!PlayAlongService.streamFeaturesVisible()) {
			int mode = WndStreamCommands.resolveDeckMode();
			if (mode == WndStreamCommands.MODE_SHATTER) {
				GameScene.show(new WndStreamCommands(WndStreamCommands.TAB_SHATTER, mode));
			} else {
				GameScene.show(new WndPlayAlongStatus());
			}
			return;
		}
		int tab = (PlayAlongService.isHosting() || PlayAlongService.isSpectating())
				? WndStreamCommands.TAB_ROSTER
				: WndStreamCommands.TAB_SPAWN;
		GameScene.show(new WndStreamCommands(tab));
	}
}
