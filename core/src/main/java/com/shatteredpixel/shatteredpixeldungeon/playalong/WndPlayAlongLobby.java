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
import com.shatteredpixel.shatteredpixeldungeon.scenes.HeroSelectScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.RedButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.ui.ScrollPane;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndOptions;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndTextInput;
import com.watabou.noosa.Game;
import com.watabou.noosa.Gizmo;
import com.watabou.noosa.ui.Component;
import com.watabou.utils.DeviceCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Community Play Along lobby: browse public rooms, join by code, host wizard.
 * ScrollPane is a direct Window child so its content camera aligns with chrome.
 */
public class WndPlayAlongLobby extends Window {

	private static final int WIDTH = 140;
	private static final int GAP = 2;
	private static final int BTN_H = 16;
	private static final int LIST_H = 80;

	private final List<RoomListing> rooms = new ArrayList<>();
	private final List<Gizmo> body = new ArrayList<>();
	private float pollAccum = 0f;
	private boolean fetching = false;
	private String linkedUsername = null;

	private ScrollPane listPane;
	private Component listContent;
	private RenderedTextBlock status;
	private RenderedTextBlock hostStatus;
	private RedButton btnDisplay;
	private float listTopY;

	public WndPlayAlongLobby() {
		super();
		rebuild();
		refreshRooms(true);
		PlayAlongService.requestLinkedUsername(name -> {
			if (parent == null) return;
			linkedUsername = name;
			if (btnDisplay != null) {
				btnDisplay.text(displayButtonLabel());
			}
		});
	}

	private boolean isHostingNow() {
		return PlayAlongService.canHostPlayAlong() && SPDSettings.playAlongHostEnabled();
	}

	private void clearBody() {
		for (Gizmo g : body) {
			if (g != null) {
				g.killAndErase();
				if (g.parent != null) g.parent.remove(g);
			}
		}
		body.clear();
		listPane = null;
		listContent = null;
		status = null;
		hostStatus = null;
		btnDisplay = null;
	}

	private void addBody(Gizmo g) {
		add(g);
		body.add(g);
	}

	private void rebuild() {
		clearBody();
		float y = 2;

		RenderedTextBlock title = PixelScene.renderTextBlock(
				Messages.get(this, "title"), 9);
		title.hardlight(TITLE_COLOR);
		title.setPos((WIDTH - title.width()) / 2f, y);
		addBody(title);
		y = title.bottom() + GAP;

		btnDisplay = new RedButton(displayButtonLabel(), 7) {
			@Override
			protected void onClick() {
				promptDisplayName();
			}
		};
		btnDisplay.setRect(0, y, WIDTH, BTN_H);
		addBody(btnDisplay);
		y = btnDisplay.bottom() + GAP;

		if (isHostingNow()) {
			y = buildHostingPanel(y);
		}

		status = PixelScene.renderTextBlock(
				rooms.isEmpty() ? Messages.get(this, "loading") : Messages.get(this, "found", rooms.size()),
				6);
		status.maxWidth(WIDTH);
		status.setPos(0, y);
		addBody(status);
		y = status.bottom() + GAP;

		listTopY = y;
		listContent = new Component();
		listPane = new ScrollPane(listContent);
		addBody(listPane);
		listPane.setRect(0, listTopY, WIDTH, LIST_H);
		y = listPane.bottom() + GAP;
		rebuildList();

		RedButton btnJoin = new RedButton(Messages.get(this, "join_code"), 7) {
			@Override
			protected void onClick() {
				PlayAlongService.promptRoomCodeJoin();
			}
		};
		btnJoin.setRect(0, y, WIDTH, BTN_H);
		addBody(btnJoin);
		y = btnJoin.bottom() + GAP;

		if (!isHostingNow()) {
			RedButton btnHost = new RedButton(Messages.get(this, "host"), 7) {
				@Override
				protected void onClick() {
					promptHostWizard();
				}
			};
			btnHost.setRect(0, y, WIDTH, BTN_H);
			addBody(btnHost);
			y = btnHost.bottom() + GAP;
		}

		RedButton btnRefresh = new RedButton(Messages.get(this, "refresh"), 7) {
			@Override
			protected void onClick() {
				refreshRooms(true);
			}
		};
		btnRefresh.setRect(0, y, WIDTH, BTN_H);
		addBody(btnRefresh);
		y = btnRefresh.bottom() + 2;

		resize(WIDTH, (int) Math.ceil(y));
		if (listPane != null) {
			listPane.setRect(0, listTopY, WIDTH, LIST_H);
		}
	}

	private float buildHostingPanel(float y) {
		RenderedTextBlock hdr = PixelScene.renderTextBlock(Messages.get(this, "hosting_title"), 8);
		hdr.hardlight(0xFFCC66);
		hdr.setPos(0, y);
		addBody(hdr);
		y = hdr.bottom() + GAP;

		String code = PlayAlongService.relayRoomCode();
		String line;
		if (code == null || code.isEmpty()) {
			line = Messages.get(this, "hosting_connecting");
		} else if (PlayAlongProtocol.isPublicJoinTarget(code)) {
			line = Messages.get(this, "hosting_public", code);
		} else {
			line = Messages.get(this, "hosting_code", code);
		}
		hostStatus = PixelScene.renderTextBlock(line, 6);
		hostStatus.maxWidth(WIDTH);
		hostStatus.setPos(0, y);
		addBody(hostStatus);
		y = hostStatus.bottom() + GAP;

		if (DeviceCompat.isAndroid()) {
			RenderedTextBlock warn = PixelScene.renderTextBlock(Messages.get(this, "hosting_android_note"), 5);
			warn.hardlight(0xFF8888);
			warn.maxWidth(WIDTH);
			warn.setPos(0, y);
			addBody(warn);
			y = warn.bottom() + GAP;
		}

		RedButton btnCopy = new RedButton(Messages.get(this, "hosting_copy"), 7) {
			@Override
			protected void onClick() {
				PlayAlongService.copyCurrentHostJoinLink();
			}
		};
		btnCopy.setRect(0, y, WIDTH, BTN_H);
		addBody(btnCopy);
		y = btnCopy.bottom() + GAP;

		RedButton btnStop = new RedButton(Messages.get(this, "hosting_stop"), 7) {
			@Override
			protected void onClick() {
				PlayAlongService.stopCommunityHostFromLobby();
				rebuild();
			}
		};
		btnStop.setRect(0, y, WIDTH, BTN_H);
		addBody(btnStop);
		return btnStop.bottom() + GAP + 2;
	}

	private String displayButtonLabel() {
		String cur = SPDSettings.playAlongUsername();
		if (cur == null || cur.isEmpty()) {
			return Messages.get(this, "display_unset");
		}
		if (linkedUsername != null && linkedUsername.equalsIgnoreCase(cur)) {
			return Messages.get(this, "display_linked", cur);
		}
		return Messages.get(this, "display_set", cur);
	}

	private void promptDisplayName() {
		if (linkedUsername != null && !linkedUsername.isEmpty()) {
			ShatteredPixelDungeon.scene().addToFront(new WndOptions(
					Messages.get(this, "display_title"),
					Messages.get(this, "display_body", linkedUsername),
					Messages.get(this, "display_set_custom"),
					Messages.get(this, "display_use_linked", linkedUsername),
					Messages.get(this, "cancel")) {
				@Override
				public void onSelect(int index) {
					if (index == 0) {
						promptDisplayNameInput();
					} else if (index == 1) {
						applyDisplayName(linkedUsername);
					}
				}
			});
		} else {
			promptDisplayNameInput();
		}
	}

	private void promptDisplayNameInput() {
		String cur = SPDSettings.playAlongUsername();
		ShatteredPixelDungeon.scene().addToFront(new WndTextInput(
				Messages.get(this, "display_title"),
				Messages.get(this, "display_prompt"),
				cur == null ? "" : cur,
				16,
				false,
				Messages.get(this, "ok"),
				Messages.get(this, "cancel")) {
			@Override
			public void onSelect(boolean positive, String text) {
				if (!positive || text == null || text.trim().isEmpty()) return;
				applyDisplayName(text);
			}
		});
	}

	private void applyDisplayName(String raw) {
		PlayAlongService.setHostDisplayName(raw);
		if (btnDisplay != null) {
			btnDisplay.text(displayButtonLabel());
		}
	}

	@Override
	public void update() {
		super.update();
		pollAccum += Game.elapsed;
		if (pollAccum >= PlayAlongProtocol.ROOMS_POLL_INTERVAL_MS / 1000f) {
			pollAccum = 0f;
			refreshRooms(false);
		}
		if (isHostingNow() && hostStatus != null) {
			String code = PlayAlongService.relayRoomCode();
			String line;
			if (code == null || code.isEmpty()) {
				line = Messages.get(this, "hosting_connecting");
			} else if (PlayAlongProtocol.isPublicJoinTarget(code)) {
				line = Messages.get(this, "hosting_public", code);
			} else {
				line = Messages.get(this, "hosting_code", code);
			}
			if (!line.equals(hostStatus.text())) {
				hostStatus.text(line);
			}
		}
	}

	private void refreshRooms(boolean showLoading) {
		if (fetching) return;
		fetching = true;
		if (showLoading && status != null) {
			status.text(Messages.get(this, "loading"));
		}
		PlayAlongService.fetchPublicRooms(list -> {
			fetching = false;
			if (parent == null) return;
			rooms.clear();
			if (list != null) rooms.addAll(list);
			rebuildList();
		});
	}

	private void rebuildList() {
		if (listContent == null || listPane == null || status == null) return;
		listContent.clear();
		float pos = 0;
		if (rooms.isEmpty()) {
			status.text(Messages.get(this, "empty"));
			listPane.content().setSize(WIDTH, 1);
			listPane.scrollTo(0, 0);
			listPane.setRect(0, listTopY, WIDTH, LIST_H);
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
		listContent.setSize(WIDTH, Math.max(pos, 1));
		listPane.content().setSize(WIDTH, Math.max(pos, 1));
		listPane.setRect(0, listTopY, WIDTH, LIST_H);
		listPane.scrollTo(0, 0);
	}

	private static String formatRoomLabel(RoomListing room, boolean compatible) {
		String name = (room.displayName != null && !room.displayName.isEmpty())
				? room.displayName
				: room.code;
		StringBuilder sb = new StringBuilder();
		if (room.official) sb.append("* ");
		sb.append(name);
		if (room.displayName != null && !room.displayName.isEmpty()
				&& !room.displayName.equalsIgnoreCase(room.code)) {
			sb.append(" [").append(room.code).append("]");
		}
		if (room.inDungeon) {
			sb.append(" D").append(Math.max(1, room.hostDepth));
		}
		sb.append(" · ").append(Math.max(0, room.players)).append("p");
		if (!compatible) sb.append(" (!ver)");
		return sb.toString();
	}

	private void promptHostWizard() {
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
		ensureDisplayNameThen(() -> ShatteredPixelDungeon.scene().addToFront(new WndOptions(
				Messages.get(this, "host_title"),
				Messages.get(this, "host_wizard_body"),
				Messages.get(this, "host_private"),
				Messages.get(this, "host_public"),
				Messages.get(this, "cancel")) {
			@Override
			public void onSelect(int index) {
				if (index == 0) {
					confirmAndroidThenHost(true, null);
				} else if (index == 1) {
					promptPublicName();
				}
			}
		}));
	}

	private void ensureDisplayNameThen(Runnable next) {
		String cur = SPDSettings.playAlongUsername();
		if (cur != null && !cur.isEmpty()) {
			next.run();
			return;
		}
		if (linkedUsername != null && !linkedUsername.isEmpty()) {
			applyDisplayName(linkedUsername);
			next.run();
			return;
		}
		ShatteredPixelDungeon.scene().addToFront(new WndTextInput(
				Messages.get(this, "display_title"),
				Messages.get(this, "display_prompt_host"),
				"",
				16,
				false,
				Messages.get(this, "ok"),
				Messages.get(this, "cancel")) {
			@Override
			public void onSelect(boolean positive, String text) {
				if (!positive) return;
				if (text != null && !text.trim().isEmpty()) {
					applyDisplayName(text);
				}
				next.run();
			}
		});
	}

	private void promptPublicName() {
		ShatteredPixelDungeon.scene().addToFront(new WndTextInput(
				Messages.get(this, "host_public_name_title"),
				Messages.get(this, "host_public_name_prompt"),
				SPDSettings.playAlongRelayRoomName(),
				24,
				false,
				Messages.get(this, "ok"),
				Messages.get(this, "cancel")) {
			@Override
			public void onSelect(boolean positive, String text) {
				if (!positive) return;
				String name = PlayAlongProtocol.normalizeHostRoomName(text);
				if (name.isEmpty() || PlayAlongProtocol.isReservedRoom(name)
						|| PlayAlongProtocol.isBlockedPublicRoomName(name)
						|| !PlayAlongProtocol.isPublicRoomName(name)) {
					if (status != null) {
						status.text(Messages.get(WndPlayAlongLobby.this, "host_public_name_bad"));
					}
					return;
				}
				if (SPDSettings.playAlongRelayToken().isEmpty()) {
					promptPublicSecret(name);
				} else {
					confirmAndroidThenHost(false, name);
				}
			}
		});
	}

	private void promptPublicSecret(final String publicName) {
		ShatteredPixelDungeon.scene().addToFront(new WndTextInput(
				Messages.get(this, "host_secret_title"),
				Messages.get(this, "host_secret_prompt"),
				"",
				64,
				false,
				Messages.get(this, "ok"),
				Messages.get(this, "cancel")) {
			@Override
			public void onSelect(boolean positive, String text) {
				if (!positive) return;
				String secret = text == null ? "" : text.trim();
				if (secret.isEmpty()) {
					if (status != null) {
						status.text(Messages.get(WndPlayAlongLobby.this, "host_secret_needed"));
					}
					return;
				}
				SPDSettings.playAlongRelayToken(secret);
				confirmAndroidThenHost(false, publicName);
			}
		});
	}

	private void confirmAndroidThenHost(boolean privateRoom, String publicNameOrNull) {
		if (DeviceCompat.isAndroid()) {
			ShatteredPixelDungeon.scene().addToFront(new WndOptions(
					Messages.get(this, "host_title"),
					Messages.get(this, "host_android_confirm"),
					Messages.get(this, "host_start_run"),
					Messages.get(this, "cancel")) {
				@Override
				public void onSelect(int index) {
					if (index == 0) {
						beginHostAndJump(privateRoom, publicNameOrNull);
					}
				}
			});
		} else {
			beginHostAndJump(privateRoom, publicNameOrNull);
		}
	}

	private void beginHostAndJump(boolean privateRoom, String publicNameOrNull) {
		hide();
		PlayAlongService.startHostFromLobby(privateRoom, publicNameOrNull);
		ShatteredPixelDungeon.switchNoFade(HeroSelectScene.class);
	}
}
