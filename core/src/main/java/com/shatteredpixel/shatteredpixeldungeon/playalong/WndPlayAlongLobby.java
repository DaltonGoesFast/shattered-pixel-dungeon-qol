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

import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.RoomListing;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.RedButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.ui.ScrollPane;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndOptions;
import com.watabou.noosa.Game;
import com.watabou.noosa.ui.Component;
import com.watabou.utils.DeviceCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Community Play Along lobby: browse public rooms, join by code, host (desktop).
 */
public class WndPlayAlongLobby extends Window {

	private static final int WIDTH = 140;
	private static final int GAP = 2;
	private static final int BTN_H = 16;
	private static final int LIST_H = 96;

	private final ScrollPane listPane;
	private final Component listContent;
	private final RenderedTextBlock status;
	private final List<RoomListing> rooms = new ArrayList<>();
	private float pollAccum = 0f;
	private boolean fetching = false;

	public WndPlayAlongLobby() {
		super();

		RenderedTextBlock title = PixelScene.renderTextBlock(
				Messages.get(this, "title"), 9);
		title.hardlight(TITLE_COLOR);
		title.setPos((WIDTH - title.width()) / 2f, 2);
		add(title);

		status = PixelScene.renderTextBlock(Messages.get(this, "loading"), 6);
		status.maxWidth(WIDTH);
		status.setPos(0, title.bottom() + GAP);
		add(status);

		listContent = new Component();
		listPane = new ScrollPane(listContent);
		add(listPane);
		listPane.setRect(0, status.bottom() + GAP, WIDTH, LIST_H);

		float y = listPane.bottom() + GAP;

		RedButton btnJoin = new RedButton(Messages.get(this, "join_code"), 7) {
			@Override
			protected void onClick() {
				PlayAlongService.promptRoomCodeJoin();
			}
		};
		btnJoin.setRect(0, y, WIDTH, BTN_H);
		add(btnJoin);
		y = btnJoin.bottom() + GAP;

		RedButton btnHost = new RedButton(Messages.get(this, "host"), 7) {
			@Override
			protected void onClick() {
				promptHost();
			}
		};
		btnHost.setRect(0, y, WIDTH, BTN_H);
		add(btnHost);
		y = btnHost.bottom() + GAP;

		RedButton btnRefresh = new RedButton(Messages.get(this, "refresh"), 7) {
			@Override
			protected void onClick() {
				refreshRooms(true);
			}
		};
		btnRefresh.setRect(0, y, WIDTH, BTN_H);
		add(btnRefresh);

		resize(WIDTH, (int) btnRefresh.bottom() + 2);
		refreshRooms(true);
	}

	@Override
	public void update() {
		super.update();
		pollAccum += Game.elapsed;
		if (pollAccum >= PlayAlongProtocol.ROOMS_POLL_INTERVAL_MS / 1000f) {
			pollAccum = 0f;
			refreshRooms(false);
		}
	}

	private void refreshRooms(boolean showLoading) {
		if (fetching) return;
		fetching = true;
		if (showLoading) {
			status.text(Messages.get(this, "loading"));
		}
		PlayAlongService.fetchPublicRooms(list -> {
			fetching = false;
			rooms.clear();
			if (list != null) rooms.addAll(list);
			rebuildList();
		});
	}

	private void rebuildList() {
		listContent.clear();
		float pos = 0;
		if (rooms.isEmpty()) {
			status.text(Messages.get(this, "empty"));
			listPane.content().setSize(WIDTH, 1);
			listPane.scrollTo(0, 0);
			return;
		}
		status.text(Messages.get(this, "found", rooms.size()));

		for (RoomListing room : rooms) {
			if (room == null || room.code == null || room.code.isEmpty()) continue;
			final RoomListing entry = room;
			boolean ok = PlayAlongProtocol.roomListingCompatible(entry, Game.version);
			String label = formatRoomLabel(entry, ok);
			RedButton row = new RedButton(label, 6) {
				@Override
				protected void onClick() {
					if (!PlayAlongProtocol.roomListingCompatible(entry, Game.version)) {
						status.text(Messages.get(WndPlayAlongLobby.this, "version_mismatch"));
						return;
					}
					PlayAlongService.openJoinLobbyForRoom(entry.code);
				}
			};
			if (entry.official) {
				row.textColor(0xFFCC66);
			} else if (!ok) {
				row.textColor(0x888888);
			}
			row.enable(ok);
			row.setRect(0, pos, WIDTH, BTN_H);
			listContent.add(row);
			pos = row.bottom() + 1;
		}
		listContent.setSize(WIDTH, Math.max(pos, LIST_H));
		listPane.content().setSize(listContent.width(), listContent.height());
		listPane.scrollTo(0, 0);
	}

	private static String formatRoomLabel(RoomListing room, boolean compatible) {
		String name = (room.displayName != null && !room.displayName.isEmpty())
				? room.displayName
				: room.code;
		StringBuilder sb = new StringBuilder();
		if (room.official) sb.append("* ");
		sb.append(name);
		sb.append(" [").append(room.code).append("]");
		if (room.inDungeon) {
			sb.append(" D").append(Math.max(1, room.hostDepth));
		}
		sb.append(" · ").append(Math.max(0, room.players)).append("p");
		if (!compatible) sb.append(" (!ver)");
		return sb.toString();
	}

	private void promptHost() {
		if (!PlayAlongService.canHostPlayAlong()) {
			ShatteredPixelDungeon.scene().addToFront(new WndOptions(
					Messages.get(this, "host_title"),
					Messages.get(this, "host_android_later"),
					Messages.get(this, "ok")) {
				@Override
				public void onSelect(int index) {
				}
			});
			return;
		}
		String roomHint = SPDSettings.playAlongRelayRoomName();
		if (roomHint == null || roomHint.isEmpty()) roomHint = Messages.get(this, "host_private_hint");
		String body = DeviceCompat.isAndroid()
				? Messages.get(this, "host_body_android", roomHint)
				: Messages.get(this, "host_body", roomHint);
		ShatteredPixelDungeon.scene().addToFront(new WndOptions(
				Messages.get(this, "host_title"),
				body,
				Messages.get(this, "host_start"),
				Messages.get(this, "cancel")) {
			@Override
			public void onSelect(int index) {
				if (index == 0) {
					PlayAlongService.enableCommunityHostFromLobby();
					hide();
				}
			}
		});
	}
}
