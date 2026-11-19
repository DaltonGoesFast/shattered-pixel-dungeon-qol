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

import com.badlogic.gdx.Gdx;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.GhostState;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.StreamEconomyState;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.TombstoneState;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.TitleScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.RedButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.ui.ScrollPane;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndTabbed;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndTextInput;
import com.watabou.noosa.ColorBlock;
import com.watabou.noosa.Game;
import com.watabou.noosa.Gizmo;
import com.watabou.noosa.ui.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Interactive Stream Command Deck + Play Along Roster window.
 * Works from Title Screen, solo runs, Play Along runs, and Spirit Cam whenever Dalton is live.
 */
public class WndStreamCommands extends WndTabbed {

	public static final int TAB_HELP = 0;
	public static final int TAB_CHAOS = 1;
	public static final int TAB_COMMUNITY = 2;
	public static final int TAB_ROSTER = 3;

	private static final int WIDTH = 132;
	private static final int GAP = 2;
	private static final int BTN_H = 22;

	private static final String[][] HELP_CMDS = {
			{"heal", "cost_per_heal", "Heal", "!heal"},
			{"cleanse", "cost_per_cleanse", "Cleanse", "!cleanse"},
			{"buff", "cost_per_buff", "Buff", "!buff"},
			{"bee", "cost_per_ally_bee", "Allied Bee", "!bee"},
			{"ward", "cost_per_ward", "Ward", "!ward"},
			{"ring_of_wealth", "cost_per_ring_of_wealth", "RoW Loot", "!row"},
			{"dew", "cost_per_dew", "Dewdrop", "!dew"},
			{"gold", "cost_per_gold", "Drop Gold", "__GOLD__"},
	};

	private static final String[][] CHAOS_CMDS = {
			{"bomb", "cost_per_bomb", "Bomb", "!bomb"},
			{"trap", "cost_per_trap", "Trap", "!trap"},
			{"wand", "cost_per_wand", "Cursed Wand", "!wand"},
			{"scroll", "cost_per_scroll", "Scroll", "!scroll"},
			{"gas", "cost_per_gas", "Gas", "!gas"},
			{"plant", "cost_per_plant", "Plant", "!plant"},
			{"corrupt_ally", "cost_per_corrupt_ally", "Corrupt Ally", "!corruptally"},
			{"hex", "cost_per_hex", "Hex", "!hex"},
			{"degrade", "cost_per_degrade", "Degrade", "!degrade"},
			{"sabotage", "cost_per_sabotage", "Sabotage", "!sabotage"},
			{"curse", "cost_per_curse", "Curse Gear", "!curse"},
			{"transmute", "cost_per_transmute", "Transmute", "!transmute"},
	};

	private static final String[] MONSTERS = {
			"rat", "albino", "snake", "gnoll", "crab", "slime", "swarm", "thief",
			"skeleton", "dm100", "guard", "necromancer",
			"bat", "brute", "shaman", "spinner", "ghoul",
			"elemental", "warlock", "monk", "golem", "succubus",
			"eye", "scorpio"
	};

	private int currentTab;
	private int winHeight;
	private float listTop;

	private StreamEconomyState eco;
	private String pendingLinkCode = null;
	private String statusText = "";
	private int statusColor = Window.WHITE;
	private boolean requestInFlight = false;
	private boolean fetchingEconomy = false;
	private boolean championMode = false;
	private float refreshAccum = 0f;

	private ClearableGroup headerGroup;
	private ScrollPane pane;
	private ClearableGroup content;
	private final List<LabeledTab> tabButtons = new ArrayList<>();

	public WndStreamCommands() {
		this(TAB_HELP);
	}

	public WndStreamCommands(int initialTab) {
		super();
		this.currentTab = Math.max(0, Math.min(3, initialTab));
		this.eco = PlayAlongService.getCachedEconomy();

		int maxH = Math.max(140, (int) (PixelScene.uiCamera.height - chrome.marginVer() - tabHeight() - 10));
		this.winHeight = Math.min(206, maxH);
		resize(WIDTH, winHeight);

		content = new ClearableGroup();
		pane = new ScrollPane(content) {
			@Override
			public void onClick(float x, float y) {
				// Buttons inside content handle their own taps with scroll passthrough.
			}
		};
		add(pane);

		headerGroup = new ClearableGroup();
		add(headerGroup);

		String[] labels = {"Help", "Chaos", "Comm.", "Roster"};
		for (int i = 0; i < labels.length; i++) {
			final int idx = i;
			LabeledTab tab = new LabeledTab(labels[i]) {
				@Override
				protected void select(boolean value) {
					super.select(value);
					if (selected && currentTab != idx) {
						currentTab = idx;
						rebuildTabPage(true);
					}
				}
			};
			tabButtons.add(tab);
			add(tab);
		}
		layoutTabs();

		buildHeader();
		rebuildTabPage(true);
		select(currentTab);

		refreshEconomy(false);
	}

	@Override
	public void update() {
		super.update();
		refreshAccum += Game.elapsed;
		if (refreshAccum >= 4.0f) {
			refreshAccum = 0f;
			refreshEconomy(false);
		}
	}

	private static final class ClearableGroup extends Component {
		boolean isAliveGroup() {
			return exists && members != null;
		}

		void destroyAndClear() {
			if (members == null) return;
			ArrayList<Gizmo> old = new ArrayList<>(members);
			clear();
			for (Gizmo g : old) {
				if (g != null) {
					g.destroy();
				}
			}
		}
	}

	private boolean isWindowDestroyed() {
		return !exists
				|| headerGroup == null || !headerGroup.isAliveGroup()
				|| content == null || !content.isAliveGroup();
	}

	private void refreshEconomy(boolean showSpinStatus) {
		if (isWindowDestroyed() || fetchingEconomy) return;
		fetchingEconomy = true;
		if (showSpinStatus) {
			statusText = "Refreshing...";
			statusColor = 0xCCCCCC;
			rebuildAllPreservingScroll();
		}
		PlayAlongService.requestStreamEconomy(state -> Game.runOnRenderThread(() -> {
			fetchingEconomy = false;
			if (parent == null || isWindowDestroyed()) return;
			if (state != null) {
				eco = state;
				if (state.linked && pendingLinkCode != null) {
					pendingLinkCode = null;
					statusText = "Linked as @" + state.linkedUsername + "!";
					statusColor = 0x55FF55;
				}
				if (showSpinStatus && statusText.equals("Refreshing...")) {
					statusText = "Updated!";
					statusColor = 0x55FF55;
				}
			} else if (showSpinStatus) {
				statusText = "Could not reach stream server.";
				statusColor = 0xFF5555;
			}
			rebuildAllPreservingScroll();
		}));
	}

	private void requestLinkCode() {
		if (isWindowDestroyed() || requestInFlight) return;
		requestInFlight = true;
		statusText = "Generating link code...";
		statusColor = 0xCCCCCC;
		rebuildAllPreservingScroll();

		PlayAlongService.requestStreamLinkCode(resp -> Game.runOnRenderThread(() -> {
			requestInFlight = false;
			if (parent == null || isWindowDestroyed()) return;
			if (resp == null) {
				statusText = "Could not reach server for link code.";
				statusColor = 0xFF5555;
			} else if (resp.commandOk && resp.linkCode != null && !resp.linkCode.isEmpty()) {
				pendingLinkCode = resp.linkCode;
				statusText = "Type '!link " + pendingLinkCode + "' in YouTube/Twitch chat.";
				statusColor = 0x55FF55;
			} else {
				statusText = resp.message != null && !resp.message.isEmpty()
						? resp.message
						: "Link request failed.";
				statusColor = 0xFF5555;
			}
			if (resp != null && resp.economy != null) {
				eco = resp.economy;
			}
			rebuildAllPreservingScroll();
		}));
	}

	private void sendCommand(final String rawCommand) {
		if (isWindowDestroyed() || requestInFlight || rawCommand == null || rawCommand.trim().isEmpty()) return;
		requestInFlight = true;
		statusText = "Sending " + rawCommand + "...";
		statusColor = 0xCCCCCC;
		rebuildAllPreservingScroll();

		PlayAlongService.sendStreamCommand(rawCommand, resp -> Game.runOnRenderThread(() -> {
			requestInFlight = false;
			if (parent == null || isWindowDestroyed()) return;
			if (resp == null) {
				statusText = "Timed out waiting for stream server.";
				statusColor = 0xFF5555;
			} else {
				if (resp.economy != null) {
					eco = resp.economy;
				}
				statusText = (resp.message != null && !resp.message.trim().isEmpty())
						? resp.message.trim()
						: (resp.commandOk ? "Command succeeded!" : "Command failed.");
				statusColor = resp.commandOk ? 0x55FF55 : 0xFF5555;
			}
			rebuildAllPreservingScroll();
		}));
	}

	private void rebuildAllPreservingScroll() {
		if (isWindowDestroyed()) return;
		float sy = (pane != null && pane.content() != null && pane.content().camera != null)
				? pane.content().camera.scroll.y : 0f;
		buildHeader();
		rebuildTabPage(false);
		if (pane != null) {
			pane.scrollTo(0, sy);
		}
	}

	private void buildHeader() {
		if (isWindowDestroyed()) return;
		headerGroup.destroyAndClear();
		float py = 1f;

		RenderedTextBlock title = PixelScene.renderTextBlock("Stream Command Deck", 9);
		title.hardlight(TITLE_COLOR);
		title.setPos(0, py);
		headerGroup.add(title);

		RedButton btnRefresh = new RedButton("Sync", 6) {
			@Override
			protected void onClick() {
				refreshEconomy(true);
			}
		};
		btnRefresh.setRect(WIDTH - 28, 0, 28, 12);
		headerGroup.add(btnRefresh);
		py = Math.max(title.bottom(), btnRefresh.bottom()) + 2;

		if (eco == null) {
			RenderedTextBlock loading = PixelScene.renderTextBlock("Connecting to Dalton's stream...", 6);
			loading.hardlight(0xCCCCCC);
			loading.maxWidth(WIDTH);
			loading.setPos(0, py);
			headerGroup.add(loading);
			py = loading.bottom() + 2;
		} else if (!eco.linked) {
			if (pendingLinkCode != null) {
				RenderedTextBlock codeTxt = PixelScene.renderTextBlock("Chat: !link " + pendingLinkCode, 7);
				codeTxt.hardlight(0x88FF88);
				codeTxt.maxWidth(WIDTH - 36);
				codeTxt.setPos(0, py + 2);
				headerGroup.add(codeTxt);

				RedButton btnCopy = new RedButton("Copy", 6) {
					@Override
					protected void onClick() {
						try {
							if (Gdx.app != null && Gdx.app.getClipboard() != null) {
								Gdx.app.getClipboard().setContents("!link " + pendingLinkCode);
								statusText = "Copied '!link " + pendingLinkCode + "'!";
								statusColor = 0x55FF55;
								rebuildAllPreservingScroll();
							}
						} catch (Exception ignored) {
						}
					}
				};
				btnCopy.setRect(WIDTH - 34, py, 34, 14);
				headerGroup.add(btnCopy);
				py = Math.max(codeTxt.bottom(), btnCopy.bottom()) + 2;
			} else {
				RenderedTextBlock unlinked = PixelScene.renderTextBlock("Stream: Not Linked", 6);
				unlinked.hardlight(0xFFCC44);
				unlinked.setPos(0, py + 3);
				headerGroup.add(unlinked);

				RedButton btnLink = new RedButton("Link (!link)", 6) {
					@Override
					protected void onClick() {
						requestLinkCode();
					}
				};
				btnLink.textColor(TITLE_COLOR);
				btnLink.setRect(WIDTH - 54, py, 54, 14);
				btnLink.enable(!requestInFlight);
				headerGroup.add(btnLink);
				py = Math.max(unlinked.bottom(), btnLink.bottom()) + 2;
			}
		} else {
			String twoX = eco.doublePointsActive ? " [2x!]" : "";
			String acctLine = "@" + eco.linkedUsername + " • " + eco.totalPts + " pts" + twoX;
			RenderedTextBlock acct = PixelScene.renderTextBlock(acctLine, 7);
			acct.hardlight(eco.doublePointsActive ? 0x55FF55 : TITLE_COLOR);
			acct.maxWidth(WIDTH);
			acct.setPos(0, py);
			headerGroup.add(acct);
			py = acct.bottom() + 1;

			String capPart = eco.chatCap > 0 ? (eco.chatPts + "/" + eco.chatCap) : String.valueOf(eco.chatPts);
			String subLine = "Chat: " + capPart + " • Donor: " + eco.donorPts
					+ (eco.deathCostMult > 1.01f ? (" • " + eco.deathCostMult + "x") : "");
			RenderedTextBlock sub = PixelScene.renderTextBlock(subLine, 6);
			sub.hardlight(0xCCCCCC);
			sub.maxWidth(WIDTH);
			sub.setPos(0, py);
			headerGroup.add(sub);
			py = sub.bottom() + 2;
		}

		if (eco != null && eco.spendDisabled) {
			RenderedTextBlock banner = PixelScene.renderTextBlock("SPENDING PAUSED BY STREAMER", 6);
			banner.hardlight(0xFF5555);
			banner.maxWidth(WIDTH);
			banner.setPos(0, py);
			headerGroup.add(banner);
			py = banner.bottom() + 2;
		} else if (eco != null && !eco.inDungeon) {
			RenderedTextBlock banner = PixelScene.renderTextBlock("Dalton in menu (dungeon cmds paused)", 6);
			banner.hardlight(0xFFCC44);
			banner.maxWidth(WIDTH);
			banner.setPos(0, py);
			headerGroup.add(banner);
			py = banner.bottom() + 2;
		}

		if (statusText != null && !statusText.isEmpty()) {
			RenderedTextBlock st = PixelScene.renderTextBlock(statusText, 6);
			st.hardlight(statusColor);
			st.maxWidth(WIDTH);
			st.setPos(0, py);
			headerGroup.add(st);
			py = st.bottom() + 2;
		}

		ColorBlock sep = new ColorBlock(WIDTH, 1, 0xFF444444);
		sep.x = 0;
		sep.y = py;
		headerGroup.add(sep);
		listTop = py + 3;
	}

	private void rebuildTabPage(boolean resetScroll) {
		if (isWindowDestroyed()) return;
		content.destroyAndClear();
		float cy = 0f;

		if (currentTab == TAB_HELP) {
			cy = buildHelpTab(cy);
		} else if (currentTab == TAB_CHAOS) {
			cy = buildChaosTab(cy);
		} else if (currentTab == TAB_COMMUNITY) {
			cy = buildCommunityTab(cy);
		} else {
			cy = buildRosterTab(cy);
		}

		content.setSize(WIDTH, Math.max(20f, cy));
		float listH = Math.max(24f, winHeight - listTop);
		pane.setRect(0, listTop, WIDTH, listH);
		if (resetScroll) {
			pane.scrollTo(0, 0);
		}
	}

	private float buildHelpTab(float cy) {
		return appendCommandGrid(cy, HELP_CMDS);
	}

	private float buildChaosTab(float cy) {
		RenderedTextBlock tricksHdr = PixelScene.renderTextBlock("Sabotage & Effects:", 6);
		tricksHdr.hardlight(TITLE_COLOR);
		tricksHdr.setPos(0, cy);
		content.add(tricksHdr);
		cy = tricksHdr.bottom() + 3;

		cy = appendCommandGrid(cy, CHAOS_CMDS) + 4;

		ColorBlock sep = new ColorBlock(WIDTH, 1, 0xFF444444);
		sep.x = 0;
		sep.y = cy;
		content.add(sep);
		cy += 4;

		RenderedTextBlock spawnHdr = PixelScene.renderTextBlock("Monster Spawner:", 6);
		spawnHdr.hardlight(TITLE_COLOR);
		spawnHdr.setPos(0, cy);
		content.add(spawnHdr);
		cy = spawnHdr.bottom() + 2;

		RedButton btnChamp = new RedButton("Champion (2x Cost): " + (championMode ? "ON" : "OFF"), 6) {
			@Override
			protected void onClick() {
				championMode = !championMode;
				rebuildAllPreservingScroll();
			}
		};
		btnChamp.enableScrollPassthrough();
		btnChamp.textColor(championMode ? 0xFF5555 : Window.WHITE);
		btnChamp.setRect(0, cy, WIDTH, 16);
		content.add(btnChamp);
		cy = btnChamp.bottom() + 3;

		String cmdId = championMode ? "champion" : "spawn";
		boolean cmdBlocked = eco != null && eco.disabledCommands.contains(cmdId);
		float colW = (WIDTH - GAP) / 2f;

		for (int i = 0; i < MONSTERS.length; i++) {
			final String mob = MONSTERS[i];
			int col = i % 2;
			int row = i / 2;
			float bx = col * (colW + GAP);
			float by = cy + row * (BTN_H + GAP);

			int cost = 0;
			if (eco != null) {
				Integer cObj = championMode ? eco.championCosts.get(mob) : eco.monsterCosts.get(mob);
				cost = cObj != null ? cObj : 100;
			}
			boolean isFree = eco != null && eco.freeKeys.contains("cost_per_monster." + mob);
			if (isFree) cost = 0;

			String label = prettyMobName(mob) + "\n" + (isFree ? "FREE!" : (cost + "p"));
			final int costFinal = cost;
			RedButton btn = new RedButton(label, 6) {
				@Override
				protected void onClick() {
					sendCommand((championMode ? "!champion " : "!spawn ") + mob);
				}
			};
			btn.multiline = true;
			btn.enableScrollPassthrough();
			btn.setRect(bx, by, colW, BTN_H);
			if (isFree) {
				btn.textColor(0x55FF55);
			} else if (championMode) {
				btn.textColor(0xFFAA66);
			}
			boolean canUse = !requestInFlight
					&& eco != null
					&& eco.linked
					&& !eco.spendDisabled
					&& eco.inDungeon
					&& !cmdBlocked
					&& costFinal <= eco.totalPts;
			btn.enable(canUse);
			content.add(btn);
		}
		int rows = (MONSTERS.length + 1) / 2;
		return cy + rows * (BTN_H + GAP);
	}

	private float buildCommunityTab(float cy) {
		boolean linked = eco != null && eco.linked && !requestInFlight;
		boolean fardUsed = eco != null && eco.fardUsed;

		String fardLabel = fardUsed
				? "!fard (Used this stream)"
				: "!fard — Extend Global 2x (+3/+6m)";
		RedButton btnFard = new RedButton(fardLabel, 6) {
			@Override
			protected void onClick() {
				sendCommand("!fard");
			}
		};
		btnFard.enableScrollPassthrough();
		btnFard.setRect(0, cy, WIDTH, 18);
		if (!fardUsed && linked) btnFard.textColor(0x55FF55);
		btnFard.enable(linked && !fardUsed);
		content.add(btnFard);
		cy = btnFard.bottom() + 4;

		String bInfo = eco != null
				? ("Bestiary L" + eco.bestiaryLevel + " (" + eco.bestiaryZone + "): "
				+ eco.bestiaryXp + "/" + eco.bestiaryThreshold + " XP")
				: "Bestiary Summon:";
		RenderedTextBlock bHdr = PixelScene.renderTextBlock(bInfo, 6);
		bHdr.hardlight(TITLE_COLOR);
		bHdr.maxWidth(WIDTH);
		bHdr.setPos(0, cy);
		content.add(bHdr);
		cy = bHdr.bottom() + 1;

		int cd = eco != null ? eco.summonCooldownSec : 0;
		String cdLine = cd > 0 ? ("Summon cooldown: " + cd + "s") : "Click monster to !summon march:";
		RenderedTextBlock cdTxt = PixelScene.renderTextBlock(cdLine, 6);
		cdTxt.hardlight(cd > 0 ? 0xFFCC44 : 0xCCCCCC);
		cdTxt.maxWidth(WIDTH);
		cdTxt.setPos(0, cy);
		content.add(cdTxt);
		cy = cdTxt.bottom() + 3;

		List<String> unlocked = (eco != null && !eco.bestiaryUnlocked.isEmpty())
				? eco.bestiaryUnlocked
				: java.util.Collections.singletonList("rat");
		float colW = (WIDTH - GAP) / 2f;
		int btnH = 16;
		for (int i = 0; i < unlocked.size(); i++) {
			final String mob = unlocked.get(i);
			int col = i % 2;
			int row = i / 2;
			RedButton btn = new RedButton(prettyMobName(mob), 6) {
				@Override
				protected void onClick() {
					sendCommand("!summon " + mob);
				}
			};
			btn.enableScrollPassthrough();
			btn.setRect(col * (colW + GAP), cy + row * (btnH + GAP), colW, btnH);
			btn.enable(linked && cd <= 0);
			content.add(btn);
		}
		int rows = (unlocked.size() + 1) / 2;
		cy += rows * (btnH + GAP) + 4;

		ColorBlock sep = new ColorBlock(WIDTH, 1, 0xFF444444);
		sep.x = 0;
		sep.y = cy;
		content.add(sep);
		cy += 4;

		int pct = eco != null ? Math.round(eco.bankRatioManual * 100f) : 10;
		RedButton btnBank = new RedButton("Bank Chat -> Donor (!bank)", 6) {
			@Override
			protected void onClick() {
				ShatteredPixelDungeon.scene().addToFront(new WndTextInput(
						"Bank Points",
						"Convert chat pts to permanent donor pts (" + pct + "% rate). Enter amount or 'all':",
						"all",
						12,
						false,
						"Bank",
						"Cancel") {
					@Override
					public void onSelect(boolean positive, String text) {
						if (positive) {
							String arg = text != null ? text.trim() : "";
							sendCommand(arg.isEmpty() ? "!bank all" : ("!bank " + arg));
						}
					}
				});
			}
		};
		btnBank.enableScrollPassthrough();
		btnBank.setRect(0, cy, WIDTH, 16);
		btnBank.enable(linked);
		content.add(btnBank);
		cy = btnBank.bottom() + GAP;

		RedButton btnGive = new RedButton("Give Points (!givepoints)", 6) {
			@Override
			protected void onClick() {
				ShatteredPixelDungeon.scene().addToFront(new WndTextInput(
						"Give Points",
						"Enter '<amount> <username>' (example: 50 @bob):",
						"",
						32,
						false,
						"Send",
						"Cancel") {
					@Override
					public void onSelect(boolean positive, String text) {
						if (positive && text != null && !text.trim().isEmpty()) {
							sendCommand("!givepoints " + text.trim());
						}
					}
				});
			}
		};
		btnGive.enableScrollPassthrough();
		btnGive.setRect(0, cy, WIDTH, 16);
		btnGive.enable(linked);
		content.add(btnGive);
		cy = btnGive.bottom() + GAP;

		RedButton btnRelink = new RedButton("Switch / Re-link (!link)", 6) {
			@Override
			protected void onClick() {
				pendingLinkCode = null;
				if (eco != null) eco.linked = false;
				requestLinkCode();
			}
		};
		btnRelink.enableScrollPassthrough();
		btnRelink.setRect(0, cy, WIDTH, 16);
		btnRelink.enable(!requestInFlight);
		content.add(btnRelink);
		cy = btnRelink.bottom() + 2;

		return cy;
	}

	private float buildRosterTab(float cy) {
		if (PlayAlongService.isSpectating() && PlayAlongService.isHostAliveForSpectate()
				&& Dungeon.level != null
				&& (Dungeon.depth != PlayAlongService.getHostDepth()
				|| Dungeon.branch != PlayAlongService.getHostBranch())) {
			RedButton btnFollow = new RedButton("Follow Dalton (Depth " + PlayAlongService.getHostDepth() + ")", 7) {
				@Override
				protected void onClick() {
					hide();
					PlayAlongService.followHostFloor();
				}
			};
			btnFollow.enableScrollPassthrough();
			btnFollow.setRect(0, cy, WIDTH, 18);
			content.add(btnFollow);
			cy = btnFollow.bottom() + 4;
		}

		if (!PlayAlongService.isActive()) {
			if (PlayAlongService.isPlayAlongRunLive()) {
				RenderedTextBlock note = PixelScene.renderTextBlock(
						"Play Along race is LIVE on Depth " + PlayAlongService.getHostDepth()
								+ "!\nDrop in from the Title Screen button.", 6);
				note.hardlight(0xFFCC44);
				note.maxWidth(WIDTH - 4);
				note.setPos(2, cy);
				content.add(note);
				cy = note.bottom() + 4;

				if (!(ShatteredPixelDungeon.scene() instanceof TitleScene)) {
					RedButton btnTitle = new RedButton("Return to Title to Join", 6) {
						@Override
						protected void onClick() {
							hide();
							PlayAlongService.pollLiveStatus(true);
							ShatteredPixelDungeon.switchNoFade(TitleScene.class);
						}
					};
					btnTitle.enableScrollPassthrough();
					btnTitle.setRect(0, cy, WIDTH, 16);
					content.add(btnTitle);
					cy = btnTitle.bottom() + 4;
				}
			} else {
				String soloTxt = (eco != null && eco.inDungeon)
						? ("Dalton is in a solo stream run on Depth " + eco.hostDepth + ".")
						: "No Play Along ghost race is currently active.";
				RenderedTextBlock note = PixelScene.renderTextBlock(soloTxt, 6);
				note.hardlight(0xCCCCCC);
				note.maxWidth(WIDTH - 4);
				note.setPos(2, cy);
				content.add(note);
				cy = note.bottom() + 4;
			}
			return cy;
		}

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
				String cls = g.heroClass != null
						? g.heroClass.substring(0, 1) + g.heroClass.substring(1).toLowerCase(Locale.ROOT)
						: "";
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

		if (Dungeon.level != null) {
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
		}
		return cy;
	}

	private float appendCommandGrid(float cy, String[][] specs) {
		float colW = (WIDTH - GAP) / 2f;
		for (int i = 0; i < specs.length; i++) {
			final String cmdId = specs[i][0];
			final String cfgKey = specs[i][1];
			final String title = specs[i][2];
			final String wireCmd = specs[i][3];

			int col = i % 2;
			int row = i / 2;
			float bx = col * (colW + GAP);
			float by = cy + row * (BTN_H + GAP);

			int cost = 0;
			if (eco != null) {
				Integer cObj = eco.costs.get(cmdId);
				cost = cObj != null ? cObj : 0;
			}
			boolean isFree = eco != null && eco.freeKeys.contains(cfgKey);
			if (isFree) cost = 0;

			String costTxt = isFree ? "FREE!" : ("gold".equals(cmdId) ? (cost + "p/1g") : (cost + "p"));
			final int costFinal = cost;
			RedButton btn = new RedButton(title + "\n" + costTxt, 6) {
				@Override
				protected void onClick() {
					if ("__GOLD__".equals(wireCmd)) {
						ShatteredPixelDungeon.scene().addToFront(new WndTextInput(
								"Drop Gold",
								"Enter gold amount (1-100). Cost: " + costFinal + "p per 1 gold:",
								"10",
								3,
								false,
								"Drop",
								"Cancel") {
							@Override
							public void onSelect(boolean positive, String text) {
								if (positive && text != null && !text.trim().isEmpty()) {
									sendCommand("!gold " + text.trim());
								}
							}
						});
					} else {
						sendCommand(wireCmd);
					}
				}
			};
			btn.multiline = true;
			btn.enableScrollPassthrough();
			btn.setRect(bx, by, colW, BTN_H);
			if (isFree) btn.textColor(0x55FF55);

			boolean cmdBlocked = eco != null && eco.disabledCommands.contains(cmdId);
			boolean canUse = !requestInFlight
					&& eco != null
					&& eco.linked
					&& !eco.spendDisabled
					&& eco.inDungeon
					&& !cmdBlocked
					&& costFinal <= eco.totalPts;
			btn.enable(canUse);
			content.add(btn);
		}
		int rows = (specs.length + 1) / 2;
		return cy + rows * (BTN_H + GAP);
	}

	private static String prettyMobName(String mob) {
		if (mob == null || mob.isEmpty()) return "";
		if ("dm100".equalsIgnoreCase(mob)) return "DM-100";
		return mob.substring(0, 1).toUpperCase(Locale.ROOT) + mob.substring(1);
	}
}
