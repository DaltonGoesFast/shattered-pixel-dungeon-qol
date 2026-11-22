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

import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.SessionInfo;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndOptions;

/**
 * Modal shown when Dalton switches saves / starts a new Play Along session.
 * Decline disconnects so players do not splinter onto a stale race.
 */
public class WndPlayAlongSessionSwitch extends WndOptions {

	private final SessionInfo session;
	private final boolean canResume;

	public WndPlayAlongSessionSwitch(SessionInfo session, boolean canResume) {
		super(
				"Play Along",
				canResume
						? "Dalton switched saves.\nResume your race on this run?"
						: "Dalton started a new run.\nJoin with a new hero?",
				canResume ? "Resume" : "Join",
				"Leave"
		);
		this.session = session;
		this.canResume = canResume;
	}

	@Override
	public void onSelect(int index) {
		PlayAlongService.clearSessionMigrationPrompt();
		if (index == 0) {
			if (canResume) {
				PlayAlongService.continueClientRun(session);
			} else {
				PlayAlongService.beginJoinAfterMigration(session);
			}
		} else {
			PlayAlongService.declineSessionMigration();
		}
	}

	@Override
	public void onBackPressed() {
		PlayAlongService.clearSessionMigrationPrompt();
		hide();
		PlayAlongService.declineSessionMigration();
	}
}
