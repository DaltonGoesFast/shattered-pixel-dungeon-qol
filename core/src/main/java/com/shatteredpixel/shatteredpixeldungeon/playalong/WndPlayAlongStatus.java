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
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.GhostState;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.TombstoneState;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.RedButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.ui.ScrollPane;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.watabou.noosa.ColorBlock;
import com.watabou.noosa.ui.Component;

import java.util.List;
import java.util.Locale;

/**
 * Live roster & depth ladder window for Play Along Mode.
 */
public class WndPlayAlongStatus extends Window {

	private static final int WIDTH = 128;
	private static final int MAX_LIST_HEIGHT = 115;

	public WndPlayAlongStatus() {
		super();

		RenderedTextBlock title = PixelScene.renderTextBlock("Play Along Roster", 10);
		title.hardlight(TITLE_COLOR);
		title.setPos((WIDTH - title.width()) / 2f, 2);
		PixelScene.align(title);
		add(title);

		float pos = title.bottom() + 4;

		if (PlayAlongService.isSpectating() && PlayAlongService.isHostAliveForSpectate()
				&& (Dungeon.depth != PlayAlongService.getHostDepth() || Dungeon.branch != PlayAlongService.getHostBranch())) {
			RedButton btnFollow = new RedButton("Follow Dalton (Depth " + PlayAlongService.getHostDepth() + ")", 7) {
				@Override
				protected void onClick() {
					hide();
					PlayAlongService.followHostFloor();
				}
			};
			btnFollow.setRect(0, pos, WIDTH, 18);
			add(btnFollow);
			pos = btnFollow.bottom() + 4;
		}

		Component content = new Component();
		float cy = 0;

		List<GhostState> roster = PlayAlongService.getAllRosterGhosts();
		if (roster.isEmpty()) {
			RenderedTextBlock empty = PixelScene.renderTextBlock("No other racers connected yet.", 6);
			empty.maxWidth(WIDTH - 6);
			empty.setPos(2, cy);
			content.add(empty);
			cy = empty.bottom() + 4;
		} else {
			for (GhostState g : roster) {
				if (g == null) continue;
				int hpPct = Math.round(g.hpPct * 100f);
				String roleTag = g.isHost ? "[HOST] " : "";
				String cls = g.heroClass != null ? g.heroClass.substring(0, 1) + g.heroClass.substring(1).toLowerCase(Locale.ROOT) : "";
				String line = roleTag + g.username + " — D" + g.depth + " (" + cls + " " + hpPct + "%)";
				RenderedTextBlock row = PixelScene.renderTextBlock(line, 6);
				row.maxWidth(WIDTH - 8);
				if (g.isHost) row.hardlight(TITLE_COLOR);
				else if (g.hpPct < 0.30f) row.hardlight(0xFF5555);
				row.setPos(2, cy);
				content.add(row);
				cy = row.bottom() + 3;
			}
		}

		List<TombstoneState> tombs = PlayAlongService.getTombstonesForFloor(Dungeon.depth, Dungeon.branch);
		if (!tombs.isEmpty()) {
			ColorBlock sep = new ColorBlock(WIDTH - 4, 1, 0xFF444444);
			sep.x = 2;
			sep.y = cy;
			content.add(sep);
			cy += 4;

			RenderedTextBlock tombHdr = PixelScene.renderTextBlock("Fallen on this floor:", 6);
			tombHdr.hardlight(0x88DDFF);
			tombHdr.setPos(2, cy);
			content.add(tombHdr);
			cy = tombHdr.bottom() + 2;

			for (TombstoneState t : tombs) {
				RenderedTextBlock row = PixelScene.renderTextBlock("☠ " + t.username + " (" + t.cause + ")", 6);
				row.maxWidth(WIDTH - 8);
				row.hardlight(0xCCCCCC);
				row.setPos(2, cy);
				content.add(row);
				cy = row.bottom() + 2;
			}
		}

		content.setSize(WIDTH, Math.max(20, cy));
		int listH = Math.min(MAX_LIST_HEIGHT, (int) Math.ceil(content.height()));

		ScrollPane pane = new ScrollPane(content);
		add(pane);
		resize(WIDTH, (int) (pos + listH + 2));
		pane.setRect(0, pos, WIDTH, listH);
	}
}
