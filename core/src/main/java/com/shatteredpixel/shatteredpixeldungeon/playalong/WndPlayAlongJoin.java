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

import com.shatteredpixel.shatteredpixeldungeon.Challenges;
import com.shatteredpixel.shatteredpixeldungeon.Modifiers;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.SessionInfo;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.StreamEconomyState;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.Icons;
import com.shatteredpixel.shatteredpixeldungeon.ui.RedButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.ui.StyledButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.shatteredpixel.shatteredpixeldungeon.utils.DungeonSeed;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndChallenges;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndPacts;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndTextInput;
import com.watabou.noosa.Game;

import java.util.ArrayList;
import java.util.List;

/**
 * Drop-in lobby & Stream Command hub opened when clicking the glowing red DaltonGoesFast [LIVE] button.
 */
public class WndPlayAlongJoin extends Window {

	private static final int WIDTH = 128;
	private static final int GAP = 3;

	private HeroClass selectedClass = HeroClass.WARRIOR;
	private String username;
	private RedButton btnName;
	private RenderedTextBlock linkInfo;
	private final List<RedButton> classButtons = new ArrayList<>();
	/** Server this window should drop into, captured so a later room-code prompt cannot retarget it. */
	private final String joinAddress;

	public WndPlayAlongJoin(final SessionInfo session) {
		super();
		this.joinAddress = PlayAlongService.peekJoinAddress();

		boolean stream = PlayAlongService.streamFeaturesVisible();
		this.username = SPDSettings.playAlongUsername();
		if (this.username == null) this.username = "";
		if (stream && this.username.isEmpty()) {
			this.username = "Chatter";
		}

		String hostLabel = communityHostLabel(session);
		String titleTxt;
		if (stream) {
			titleTxt = session.active ? "Play Along [LIVE]" : "DaltonGoesFast [LIVE]";
		} else {
			titleTxt = hostLabel;
		}
		RenderedTextBlock title = PixelScene.renderTextBlock(titleTxt, 10);
		title.hardlight(stream ? 0xFF4444 : TITLE_COLOR);
		title.setPos((WIDTH - title.width()) / 2f, 2);
		PixelScene.align(title);
		add(title);

		float pos = title.bottom() + GAP;

		String statusTxt;
		if (session.active) {
			String seedDisplay = (session.customSeedText != null && !session.customSeedText.isEmpty())
					? session.customSeedText
					: DungeonSeed.convertToCode(session.seed);
			if (stream) {
				statusTxt = "Dalton: Depth " + session.hostDepth
						+ " (" + session.hostHp + "/" + session.hostHt + " HP)\n"
						+ "Racers: " + session.activePlayers + " • Seed: " + seedDisplay + "\n"
						+ "Goal: Get the Amulet before Dalton dies!";
			} else {
				statusTxt = hostLabel + ": Depth " + session.hostDepth
						+ " (" + session.hostHp + "/" + session.hostHt + " HP)\n"
						+ "Players: " + session.activePlayers + " • Seed: " + seedDisplay + "\n"
						+ "Goal: Get the Amulet before " + hostLabel + " dies!";
			}
		} else if (session.inDungeon) {
			statusTxt = stream
					? ("Dalton: Depth " + session.hostDepth
					+ " (" + session.hostHp + "/" + session.hostHt + " HP)\n"
					+ "Solo Stream Run — Stream commands are LIVE!")
					: (hostLabel + ": Depth " + session.hostDepth
					+ " (" + session.hostHp + "/" + session.hostHt + " HP)\n"
					+ "Host is in a solo run.");
		} else {
			statusTxt = stream
					? ("Dalton is in the menu / between runs.\n"
					+ "Account linking & community commands are ready!")
					: (hostLabel + " is in the menu / between runs.");
		}
		RenderedTextBlock info = PixelScene.renderTextBlock(statusTxt, 6);
		info.maxWidth(WIDTH);
		info.setPos(0, pos);
		add(info);
		pos = info.bottom() + GAP;

		if (stream) {
			linkInfo = PixelScene.renderTextBlock(formatLinkLine(PlayAlongService.getCachedEconomy()), 6);
			linkInfo.hardlight(0xCCCCCC);
			linkInfo.maxWidth(WIDTH);
			linkInfo.setPos(0, pos);
			add(linkInfo);
			pos = linkInfo.bottom() + GAP;

			RedButton btnCommands = new RedButton("STREAM COMMANDS & POINTS", 7) {
				@Override
				protected void onClick() {
					ShatteredPixelDungeon.scene().addToFront(new WndStreamCommands());
				}
			};
			btnCommands.textColor(0x55FF55);
			btnCommands.setRect(0, pos, WIDTH, 18);
			add(btnCommands);
			pos = btnCommands.bottom() + GAP;
		}

		if (session.active) {
			int chalCount = Challenges.activeChallenges(session.challenges);
			int pactCount = Modifiers.activeModifiers(Modifiers.sanitize(session.modifiers));

			RedButton btnChals = new RedButton("Chals: " + chalCount, 7) {
				@Override
				protected void onClick() {
					ShatteredPixelDungeon.scene().addToFront(new WndChallenges(session.challenges, false));
				}
			};
			btnChals.icon(Icons.get(chalCount > 0 ? Icons.CHALLENGE_COLOR : Icons.CHALLENGE_GREY));
			btnChals.setRect(0, pos, WIDTH / 2f - 1, 16);
			add(btnChals);

			RedButton btnPacts = new RedButton("Pacts: " + pactCount, 7) {
				@Override
				protected void onClick() {
					ShatteredPixelDungeon.scene().addToFront(new WndPacts(session.modifiers, false));
				}
			};
			btnPacts.icon(Icons.get(Icons.PACTS));
			btnPacts.setRect(WIDTH / 2f + 1, pos, WIDTH / 2f - 1, 16);
			add(btnPacts);
			pos = btnPacts.bottom() + GAP;

			boolean canResume = PlayAlongArchives.hasArchive(session.sessionId);
			if (canResume) {
				RenderedTextBlock resumeInfo = PixelScene.renderTextBlock(
						"You have a saved race for this run.", 6);
				resumeInfo.hardlight(0x88FF88);
				resumeInfo.maxWidth(WIDTH);
				resumeInfo.setPos(0, pos);
				add(resumeInfo);
				pos = resumeInfo.bottom() + GAP;

				StyledButton btnResume = new RedButton("RESUME YOUR RACE", 8) {
					@Override
					protected void onClick() {
						hide();
						PlayAlongService.useJoinAddress(joinAddress);
						PlayAlongService.continueClientRun(session);
					}
				};
				btnResume.icon(Icons.get(Icons.ENTER));
				btnResume.textColor(TITLE_COLOR);
				btnResume.setRect(0, pos, WIDTH, 20);
				add(btnResume);
				pos = btnResume.bottom() + GAP;
			} else {
				final boolean community = !stream;
				btnName = new RedButton(nameButtonLabel(community), 7) {
					@Override
					protected void onClick() {
						promptDisplayName(community);
					}
				};
				btnName.setRect(0, pos, WIDTH, 16);
				add(btnName);
				pos = btnName.bottom() + GAP;

				RenderedTextBlock pickCls = PixelScene.renderTextBlock("Pick Hero Class:", 6);
				pickCls.hardlight(TITLE_COLOR);
				pickCls.setPos(0, pos);
				add(pickCls);
				pos = pickCls.bottom() + 2;

				HeroClass[] classes = HeroClass.values();
				float clsW = (WIDTH - (classes.length - 1)) / (float) classes.length;
				for (int i = 0; i < classes.length; i++) {
					final HeroClass hc = classes[i];
					RedButton btnCls = new RedButton("", 6) {
						@Override
						protected void onClick() {
							selectedClass = hc;
							refreshClassHighlight();
						}
					};
					btnCls.icon(Icons.get(hc));
					btnCls.setRect(i * (clsW + 1), pos, clsW, 20);
					add(btnCls);
					classButtons.add(btnCls);
				}
				refreshClassHighlight();
				pos += 20 + GAP + 1;

				String joinLabel = community
						? Messages.get(this, "join_run")
						: "DROP INTO RUN";
				StyledButton btnDropIn = new RedButton(joinLabel, 8) {
					@Override
					protected void onClick() {
						startDropIn(session, community);
					}
				};
				btnDropIn.icon(Icons.get(Icons.ENTER));
				btnDropIn.textColor(TITLE_COLOR);
				btnDropIn.setRect(0, pos, WIDTH, 20);
				add(btnDropIn);
				pos = btnDropIn.bottom() + GAP;
			}
		}

		if (stream) {
			RedButton btnYt = new RedButton("Open YouTube Channel", 7) {
				@Override
				protected void onClick() {
					ShatteredPixelDungeon.platform.openURI("https://www.youtube.com/@DaltonGoesFast");
				}
			};
			btnYt.setRect(0, pos, WIDTH, 16);
			add(btnYt);
			pos = btnYt.bottom() + 1;
		}

		resize(WIDTH, (int) Math.ceil(pos));

		if (stream) {
			PlayAlongService.requestStreamEconomy(eco -> Game.runOnRenderThread(() -> {
				if (parent == null || eco == null || linkInfo == null) return;
				linkInfo.text(formatLinkLine(eco));
				if (eco.linked && eco.linkedUsername != null && !eco.linkedUsername.trim().isEmpty()) {
					username = PlayAlongProtocol.sanitizeUsername(eco.linkedUsername, "01");
					if (btnName != null) {
						btnName.text("Name: " + username + " (Linked)");
					}
					linkInfo.hardlight(0x88FF88);
				} else {
					linkInfo.hardlight(0xFFCC44);
				}
			}));
		}
	}

	private String communityHostLabel(SessionInfo session) {
		if (session != null && session.hostDisplayName != null && !session.hostDisplayName.trim().isEmpty()) {
			return session.hostDisplayName.trim();
		}
		String room = PlayAlongProtocol.parseJoinInput(joinAddress);
		if (room != null && !room.isEmpty() && !PlayAlongProtocol.isReservedRoom(room)) {
			return room;
		}
		return "Host";
	}

	private String nameButtonLabel(boolean community) {
		if (username == null || username.isEmpty()) {
			return Messages.get(this, "name_unset");
		}
		return Messages.get(this, "name_set", username);
	}

	private void promptDisplayName(boolean community) {
		String title = community
				? Messages.get(this, "display_title")
				: "Chatter Name";
		String body = community
				? Messages.get(this, "display_prompt")
				: "3-16 alphanumeric characters (shown above your ghost):";
		String ok = community ? Messages.get(this, "ok") : "Set";
		String cancel = community ? Messages.get(this, "cancel") : "Cancel";
		ShatteredPixelDungeon.scene().addToFront(new WndTextInput(
				title,
				body,
				username == null ? "" : username,
				16,
				false,
				ok,
				cancel) {
			@Override
			public void onSelect(boolean positive, String text) {
				if (!positive || text == null || text.trim().isEmpty()) return;
				username = PlayAlongProtocol.sanitizeUsername(text, "01");
				SPDSettings.playAlongUsername(username);
				if (btnName != null) {
					btnName.text(nameButtonLabel(community));
				}
			}
		});
	}

	private void startDropIn(SessionInfo session, boolean community) {
		if (community && (username == null || username.isEmpty())) {
			ShatteredPixelDungeon.scene().addToFront(new WndTextInput(
					Messages.get(this, "display_title"),
					Messages.get(this, "display_prompt"),
					"",
					16,
					false,
					Messages.get(this, "ok"),
					Messages.get(this, "cancel")) {
				@Override
				public void onSelect(boolean positive, String text) {
					if (!positive || text == null || text.trim().isEmpty()) return;
					username = PlayAlongProtocol.sanitizeUsername(text, "01");
					SPDSettings.playAlongUsername(username);
					if (btnName != null) {
						btnName.text(nameButtonLabel(true));
					}
					finishDropIn(session);
				}
			});
			return;
		}
		finishDropIn(session);
	}

	private void finishDropIn(SessionInfo session) {
		hide();
		PlayAlongService.useJoinAddress(joinAddress);
		PlayAlongService.startClientRun(session, selectedClass, username);
	}

	private static String formatLinkLine(StreamEconomyState eco) {
		if (eco == null) return "Checking stream account link...";
		if (!eco.linked) return "Stream account: Not linked (!link)";
		return "Linked: @" + eco.linkedUsername + " (" + eco.totalPts + " pts)";
	}

	private void refreshClassHighlight() {
		HeroClass[] classes = HeroClass.values();
		for (int i = 0; i < classButtons.size() && i < classes.length; i++) {
			RedButton b = classButtons.get(i);
			if (classes[i] == selectedClass) {
				b.alpha(1f);
				b.icon().resetColor();
			} else {
				b.alpha(0.5f);
				b.icon().brightness(0.55f);
			}
		}
	}
}
