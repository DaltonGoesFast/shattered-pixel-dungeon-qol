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
import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.Chrome;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Acidic;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Albino;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.ArmoredBrute;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Bandit;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Bat;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Brute;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.CausticSlime;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Crab;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.DM100;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.DM200;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.DM201;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.DM300;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.DwarfKing;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Elemental;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Eye;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Ghoul;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Gnoll;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Golem;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Goo;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Guard;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mimic;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Monk;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Necromancer;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Piranha;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Rat;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.RipperDemon;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Scorpio;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Senior;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Shaman;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Skeleton;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Slime;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Snake;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.SpectralNecromancer;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Spinner;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Statue;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Succubus;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Swarm;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Tengu;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Thief;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Warlock;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Wraith;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.YogDzewa;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.GhostState;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.StreamEconomyState;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.TombstoneState;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.TitleScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSprite;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSpriteSheet;
import com.shatteredpixel.shatteredpixeldungeon.ui.Button;
import com.shatteredpixel.shatteredpixeldungeon.ui.RedButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.ui.ScrollPane;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndJournalItem;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndTabbed;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndTextInput;
import com.watabou.noosa.ColorBlock;
import com.watabou.noosa.Game;
import com.watabou.noosa.Gizmo;
import com.watabou.noosa.Group;
import com.watabou.noosa.Image;
import com.watabou.noosa.NinePatch;
import com.watabou.noosa.audio.Sample;
import com.watabou.noosa.ui.Component;
import com.watabou.utils.RectF;
import com.watabou.utils.Reflection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Interactive Stream Command Deck + Play Along Roster window.
 * Works from Title Screen, solo runs, Play Along runs, and Spirit Cam whenever Dalton is live.
 */
public class WndStreamCommands extends WndTabbed {

	public static final int TAB_SPAWN = 0;
	public static final int TAB_CHAOS = 1;
	public static final int TAB_HELP = 2;
	public static final int TAB_COMMUNITY = 3;
	public static final int TAB_ROSTER = 4;

	private static final int WIDTH = 132;
	private static final int GAP = 2;
	private static final int BTN_H = 22;
	private static final int TILE_H = 27;

	private static final Map<String, Class<? extends Mob>> MOB_CLASSES = new HashMap<>();
	static {
		MOB_CLASSES.put("rat", Rat.class);
		MOB_CLASSES.put("albino", Albino.class);
		MOB_CLASSES.put("snake", Snake.class);
		MOB_CLASSES.put("gnoll", Gnoll.class);
		MOB_CLASSES.put("crab", Crab.class);
		MOB_CLASSES.put("slime", Slime.class);
		MOB_CLASSES.put("caustic_slime", CausticSlime.class);
		MOB_CLASSES.put("causticslime", CausticSlime.class);
		MOB_CLASSES.put("swarm", Swarm.class);
		MOB_CLASSES.put("thief", Thief.class);
		MOB_CLASSES.put("bandit", Bandit.class);
		MOB_CLASSES.put("skeleton", Skeleton.class);
		MOB_CLASSES.put("dm100", DM100.class);
		MOB_CLASSES.put("guard", Guard.class);
		MOB_CLASSES.put("necromancer", Necromancer.class);
		MOB_CLASSES.put("spectral_necromancer", SpectralNecromancer.class);
		MOB_CLASSES.put("bat", Bat.class);
		MOB_CLASSES.put("brute", Brute.class);
		MOB_CLASSES.put("armored_brute", ArmoredBrute.class);
		MOB_CLASSES.put("shaman", Shaman.RedShaman.class);
		MOB_CLASSES.put("spinner", Spinner.class);
		MOB_CLASSES.put("dm200", DM200.class);
		MOB_CLASSES.put("dm201", DM201.class);
		MOB_CLASSES.put("ghoul", Ghoul.class);
		MOB_CLASSES.put("elemental", Elemental.FireElemental.class);
		MOB_CLASSES.put("warlock", Warlock.class);
		MOB_CLASSES.put("monk", Monk.class);
		MOB_CLASSES.put("senior", Senior.class);
		MOB_CLASSES.put("golem", Golem.class);
		MOB_CLASSES.put("ripper", RipperDemon.class);
		MOB_CLASSES.put("ripper_demon", RipperDemon.class);
		MOB_CLASSES.put("succubus", Succubus.class);
		MOB_CLASSES.put("eye", Eye.class);
		MOB_CLASSES.put("scorpio", Scorpio.class);
		MOB_CLASSES.put("acidic", Acidic.class);
		MOB_CLASSES.put("wraith", Wraith.class);
		MOB_CLASSES.put("piranha", Piranha.class);
		MOB_CLASSES.put("mimic", Mimic.class);
		MOB_CLASSES.put("statue", Statue.class);
		MOB_CLASSES.put("goo", Goo.class);
		MOB_CLASSES.put("tengu", Tengu.class);
		MOB_CLASSES.put("dm300", DM300.class);
		MOB_CLASSES.put("king", DwarfKing.class);
		MOB_CLASSES.put("dwarf_king", DwarfKing.class);
		MOB_CLASSES.put("yog", YogDzewa.class);
	}

	private static final Map<String, Integer> CMD_ICONS = new HashMap<>();
	static {
		CMD_ICONS.put("heal", ItemSpriteSheet.POTION_CRIMSON);
		CMD_ICONS.put("cleanse", ItemSpriteSheet.EXOTIC_CRIMSON);
		CMD_ICONS.put("buff", ItemSpriteSheet.ELIXIR_MIGHT);
		CMD_ICONS.put("bee", ItemSpriteSheet.HONEYPOT);
		CMD_ICONS.put("ward", ItemSpriteSheet.WAND_WARDING);
		CMD_ICONS.put("ring_of_wealth", ItemSpriteSheet.RING_DIAMOND);
		CMD_ICONS.put("dew", ItemSpriteSheet.DEWDROP);
		CMD_ICONS.put("gold", ItemSpriteSheet.GOLD);
		CMD_ICONS.put("bomb", ItemSpriteSheet.BOMB);
		CMD_ICONS.put("trap", ItemSpriteSheet.RECLAIM_TRAP);
		CMD_ICONS.put("wand", ItemSpriteSheet.WAND_CORRUPTION);
		CMD_ICONS.put("scroll", ItemSpriteSheet.SCROLL_KAUNAN);
		CMD_ICONS.put("gas", ItemSpriteSheet.BREW_CAUSTIC);
		CMD_ICONS.put("plant", ItemSpriteSheet.SEED_SORROWMOSS);
		CMD_ICONS.put("corrupt_ally", ItemSpriteSheet.DUST);
		CMD_ICONS.put("hex", ItemSpriteSheet.WONDROUS_RESIN);
		CMD_ICONS.put("degrade", ItemSpriteSheet.BROKEN_HILT);
		CMD_ICONS.put("sabotage", ItemSpriteSheet.SHRAPNEL_BOMB);
		CMD_ICONS.put("curse", ItemSpriteSheet.CURSE_INFUSE);
		CMD_ICONS.put("transmute", ItemSpriteSheet.RECYCLE);
	}

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
	private final List<CompactLabeledTab> tabButtons = new ArrayList<>();

	public WndStreamCommands() {
		this(TAB_SPAWN);
	}

	public WndStreamCommands(int initialTab) {
		super();
		this.currentTab = Math.max(0, Math.min(4, initialTab));
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

		String[] labels = {"Spawn", "Chaos", "Help", "Comm.", "Roster"};
		for (int i = 0; i < labels.length; i++) {
			final int idx = i;
			CompactLabeledTab tab = new CompactLabeledTab(labels[i]) {
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

		if (currentTab == TAB_SPAWN) {
			cy = buildSpawnTab(cy);
		} else if (currentTab == TAB_CHAOS) {
			cy = buildChaosTab(cy);
		} else if (currentTab == TAB_HELP) {
			cy = buildHelpTab(cy);
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

	private float buildSpawnTab(float cy) {
		float modeW = (WIDTH - GAP) / 2f;
		RedButton btnNormal = new RedButton("Normal (!spawn)", 6) {
			@Override
			protected void onClick() {
				if (championMode) {
					championMode = false;
					rebuildAllPreservingScroll();
				}
			}
		};
		btnNormal.enableScrollPassthrough();
		btnNormal.setRect(0, cy, modeW, 16);
		if (!championMode) {
			btnNormal.textColor(TITLE_COLOR);
		} else {
			btnNormal.alpha(0.6f);
		}
		content.add(btnNormal);

		RedButton btnChamp = new RedButton("Champion (2x)", 6) {
			{
				if (championMode && bg != null) {
					bg.hardlight(1.35f, 0.65f, 0.4f);
				}
			}

			@Override
			protected void onClick() {
				if (!championMode) {
					championMode = true;
					rebuildAllPreservingScroll();
				}
			}

			@Override
			protected void onPointerUp() {
				super.onPointerUp();
				if (championMode && bg != null) {
					bg.hardlight(1.35f, 0.65f, 0.4f);
				}
			}
		};
		btnChamp.enableScrollPassthrough();
		btnChamp.setRect(modeW + GAP, cy, modeW, 16);
		if (championMode) {
			btnChamp.textColor(0xFFDD66);
		} else {
			btnChamp.alpha(0.6f);
		}
		content.add(btnChamp);
		cy = btnChamp.bottom() + 3;

		String hint = championMode
				? "Champion mode: 2x cost, elite buff!"
				: "Tap to spawn • Hold to inspect:";
		RenderedTextBlock spawnHint = PixelScene.renderTextBlock(hint, 6);
		spawnHint.hardlight(championMode ? 0xFFAA55 : 0xCCCCCC);
		spawnHint.maxWidth(WIDTH);
		spawnHint.setPos(0, cy);
		content.add(spawnHint);
		cy = spawnHint.bottom() + 3;

		String cmdId = championMode ? "champion" : "spawn";
		boolean cmdBlocked = eco != null && eco.disabledCommands.contains(cmdId);
		int cols = 4;
		float tileW = (WIDTH - GAP * (cols - 1)) / (float) cols;

		for (int i = 0; i < MONSTERS.length; i++) {
			final String mob = MONSTERS[i];
			int col = i % cols;
			int row = i / cols;
			float bx = col * (tileW + GAP);
			float by = cy + row * (TILE_H + GAP);

			int cost = 0;
			if (eco != null) {
				Integer cObj = championMode ? eco.championCosts.get(mob) : eco.monsterCosts.get(mob);
				cost = cObj != null ? cObj : 100;
			}
			final boolean isFree = eco != null && eco.freeKeys.contains("cost_per_monster." + mob);
			if (isFree) cost = 0;

			final int costFinal = cost;
			final boolean champFinal = championMode;
			String costLabel = isFree ? "FREE!" : (cost + "p");
			int labelColor = isFree ? 0x55FF55 : (championMode ? 0xFFBB55 : Window.WHITE);

			boolean canUse = !requestInFlight
					&& eco != null
					&& eco.linked
					&& !eco.spendDisabled
					&& eco.inDungeon
					&& !cmdBlocked
					&& costFinal <= eco.totalPts;

			String hover = prettyMobName(mob) + " (" + costLabel + ")" + (championMode ? " [Champion]" : "");
			MonsterTileButton tile = new MonsterTileButton(
					createMobIcon(mob),
					costLabel,
					5,
					labelColor,
					championMode,
					canUse,
					hover,
					() -> sendCommand((champFinal ? "!champion " : "!spawn ") + mob),
					() -> showMobInspect(mob, champFinal, costFinal, isFree, false)
			);
			tile.setRect(bx, by, tileW, TILE_H);
			content.add(tile);
		}
		int rows = (MONSTERS.length + cols - 1) / cols;
		return cy + rows * (TILE_H + GAP);
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

		return appendCommandGrid(cy, CHAOS_CMDS);
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
		int cols = 4;
		float tileW = (WIDTH - GAP * (cols - 1)) / (float) cols;
		for (int i = 0; i < unlocked.size(); i++) {
			final String mob = unlocked.get(i);
			int col = i % cols;
			int row = i / cols;
			MonsterTileButton tile = new MonsterTileButton(
					createMobIcon(mob),
					shortMobName(mob),
					5,
					Window.WHITE,
					false,
					linked && cd <= 0,
					prettyMobName(mob) + " (!summon)",
					() -> sendCommand("!summon " + mob),
					() -> showMobInspect(mob, false, 0, true, true)
			);
			tile.setRect(col * (tileW + GAP), cy + row * (TILE_H + GAP), tileW, TILE_H);
			content.add(tile);
		}
		int rows = (unlocked.size() + cols - 1) / cols;
		cy += rows * (TILE_H + GAP) + 4;

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
		if (com.watabou.utils.DeviceCompat.isDesktop()
				&& (PlayAlongService.isHosting() || SPDSettings.playAlongHostEnabled()
				|| PlayAlongService.isLocalHostServerRunning())) {
			RenderedTextBlock hostLine = PixelScene.renderTextBlock(PlayAlongService.hostConnectionSummary(), 6);
			hostLine.hardlight(0xFFCC88);
			hostLine.maxWidth(WIDTH - 4);
			hostLine.setPos(2, cy);
			content.add(hostLine);
			cy = hostLine.bottom() + 4;
		}

		if (com.watabou.utils.DeviceCompat.isDesktop()
				&& (PlayAlongService.isHosting() || SPDSettings.playAlongHostEnabled())) {
			final boolean sharedOn = SPDSettings.playAlongSharedChat();
			RedButton btnSharedChat = new RedButton("Mirror Chat Cmds to Racers: " + (sharedOn ? "ON" : "OFF"), 6) {
				@Override
				protected void onClick() {
					SPDSettings.playAlongSharedChat(!SPDSettings.playAlongSharedChat());
					rebuildAllPreservingScroll();
				}
			};
			btnSharedChat.enableScrollPassthrough();
			btnSharedChat.setRect(0, cy, WIDTH, 16);
			btnSharedChat.textColor(sharedOn ? 0x55FF55 : 0xCCCCCC);
			content.add(btnSharedChat);
			cy = btnSharedChat.bottom() + 4;
		}

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
				String line = roleTag + g.username + " — D" + g.depth + " (" + cls + " " + hpPct + "%)"
						+ (g.catchup ? " · catch-up" : "");
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

			Integer cObj = null;
			if (eco != null) {
				cObj = eco.costs.get(cmdId);
				if (cObj == null && "ring_of_wealth".equals(cmdId)) {
					cObj = eco.costs.get("row");
				}
			}
			boolean isFree = eco != null && eco.freeKeys.contains(cfgKey);
			boolean costKnown = isFree || cObj != null;
			int cost = isFree ? 0 : (cObj != null ? cObj : 0);

			String costTxt = isFree
					? "FREE!"
					: (!costKnown
					? (eco == null ? "..." : "?")
					: ("gold".equals(cmdId) ? (cost + "p/1g") : (cost + "p")));
			final int costFinal = cost;
			int fontSize = title.length() > 10 ? 5 : 6;
			RedButton btn = new RedButton(title + "\n" + costTxt, fontSize) {
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
			btn.leftJustify = true;
			btn.icon(createCommandIcon(cmdId));
			btn.enableScrollPassthrough();
			btn.setRect(bx, by, colW, BTN_H);
			if (isFree) btn.textColor(0x55FF55);

			boolean cmdBlocked = eco != null && (eco.disabledCommands.contains(cmdId)
					|| ("ring_of_wealth".equals(cmdId) && eco.disabledCommands.contains("row")));
			boolean canUse = !requestInFlight
					&& eco != null
					&& eco.linked
					&& !eco.spendDisabled
					&& eco.inDungeon
					&& !cmdBlocked
					&& costKnown
					&& costFinal <= eco.totalPts;
			btn.enable(canUse);
			content.add(btn);
		}
		int rows = (specs.length + 1) / 2;
		return cy + rows * (BTN_H + GAP);
	}

	private void showMobInspect(String mobKey, boolean isChampion, int cost, boolean isFree, boolean isSummonMarch) {
		Mob mob = instantiateMob(mobKey);
		CharSprite sprite = mob.sprite();
		sprite.idle();
		Image preview = new Image(sprite);
		if (isChampion) {
			preview.hardlight(1.3f, 0.95f, 0.55f);
		}

		String title = Messages.titleCase(mob.name()) + (isChampion ? " (Champion)" : "");
		StringBuilder desc = new StringBuilder();
		if (isSummonMarch) {
			desc.append("Command: !summon ").append(mobKey).append(" (Free Bestiary March)");
		} else {
			desc.append("Command: ").append(isChampion ? "!champion " : "!spawn ").append(mobKey)
					.append("\nCost: ").append(isFree ? "FREE!" : (cost + " pts"))
					.append(isChampion ? " (2x elite buff)" : "");
		}
		desc.append("\n\n").append(mob.description());

		ShatteredPixelDungeon.scene().addToFront(new WndJournalItem(preview, title, desc.toString()));
	}

	private static Class<? extends Mob> mobClassForKey(String mobKey) {
		if (mobKey == null) return Rat.class;
		Class<? extends Mob> cls = MOB_CLASSES.get(mobKey.trim().toLowerCase(Locale.ROOT));
		return cls != null ? cls : Rat.class;
	}

	private static Mob instantiateMob(String mobKey) {
		Mob mob = Reflection.newInstance(mobClassForKey(mobKey));
		if (mob == null) {
			mob = new Rat();
		}
		if (mob instanceof Mimic) {
			mob.alignment = Char.Alignment.ENEMY;
		}
		return mob;
	}

	private static Image createMobIcon(String mobKey) {
		Mob mob = instantiateMob(mobKey);
		CharSprite sprite = mob.sprite();
		sprite.idle();
		Image icon = new Image(sprite);

		if (icon.width() >= 16 || icon.height() >= 16) {
			RectF frame = icon.frame();
			float wShrink = frame.width() * (1f - 16f / icon.width());
			if (wShrink > 0) {
				frame.left += wShrink / 2f;
				frame.right -= wShrink / 2f;
			}
			float hShrink = frame.height() * (1f - 16f / icon.height());
			if (hShrink > 0) {
				frame.top += hShrink / 2f;
				frame.bottom -= hShrink / 2f;
			}
			icon.frame(frame);
		}
		return icon;
	}

	private static Image createCommandIcon(String cmdId) {
		Integer spriteId = CMD_ICONS.get(cmdId);
		ItemSprite icon = new ItemSprite(spriteId != null ? spriteId : ItemSpriteSheet.SOMETHING);
		float maxDim = Math.max(icon.width, icon.height);
		if (maxDim > 0f) {
			float s = Math.min(1f, 12f / maxDim);
			icon.scale.set(s);
		}
		return icon;
	}

	private static String shortMobName(String mob) {
		if (mob == null || mob.isEmpty()) return "";
		switch (mob.toLowerCase(Locale.ROOT)) {
			case "dm100": return "DM100";
			case "skeleton": return "Skel.";
			case "necromancer": return "Necro";
			case "shaman": return "Shamn";
			case "spinner": return "Spinn.";
			case "elemental": return "Elem.";
			case "warlock": return "Warlk";
			case "succubus": return "Succ.";
			case "scorpio": return "Scorp.";
			default:
				String pretty = prettyMobName(mob);
				return pretty.length() > 6 ? (pretty.substring(0, 5) + ".") : pretty;
		}
	}

	private static String prettyMobName(String mob) {
		if (mob == null || mob.isEmpty()) return "";
		if ("dm100".equalsIgnoreCase(mob)) return "DM-100";
		return mob.substring(0, 1).toUpperCase(Locale.ROOT) + mob.substring(1);
	}

	private class CompactLabeledTab extends Tab {

		private RenderedTextBlock btLabel;

		public CompactLabeledTab(String label) {
			super();
			btLabel.text(label);
		}

		@Override
		protected void createChildren() {
			super.createChildren();
			btLabel = PixelScene.renderTextBlock(7);
			add(btLabel);
		}

		@Override
		protected void layout() {
			super.layout();
			btLabel.setPos(
					x + (width - btLabel.width()) / 2f,
					y + (height - btLabel.height()) / 2f - (selected ? 1 : 3)
			);
			PixelScene.align(btLabel);
		}

		@Override
		protected void select(boolean value) {
			super.select(value);
			btLabel.alpha(selected ? 1.0f : 0.6f);
		}
	}

	private static final class MonsterTileButton extends Button {

		private final NinePatch bg;
		private final Image spriteIcon;
		private final RenderedTextBlock label;
		private final boolean championStyle;
		private final boolean canExecute;
		private final String hoverLabel;
		private final Runnable onExecute;
		private final Runnable onInspect;

		MonsterTileButton(Image icon, String labelText, int labelSize, int labelColor,
		                  boolean championStyle, boolean canExecute, String hoverLabel,
		                  Runnable onExecute, Runnable onInspect) {
			super();
			this.championStyle = championStyle;
			this.canExecute = canExecute;
			this.hoverLabel = hoverLabel;
			this.onExecute = onExecute;
			this.onInspect = onInspect;

			bg = Chrome.get(Chrome.Type.RED_BUTTON);
			addToBack(bg);

			spriteIcon = icon != null ? icon : new Image();
			add(spriteIcon);

			label = PixelScene.renderTextBlock(labelText, labelSize);
			label.hardlight(labelColor);
			add(label);

			enableScrollPassthrough();
			applyVisualState(false);
		}

		private void applyVisualState(boolean pressed) {
			bg.resetColor();
			spriteIcon.resetColor();

			if (championStyle) {
				bg.hardlight(1.3f, 0.7f, 0.45f);
				spriteIcon.hardlight(1.25f, 1.0f, 0.6f);
			}
			if (pressed) {
				if (championStyle) {
					bg.hardlight(1.55f, 0.85f, 0.55f);
				} else {
					bg.brightness(1.2f);
				}
			}

			if (!canExecute) {
				bg.alpha(0.45f);
				spriteIcon.alpha(0.45f);
				label.alpha(0.4f);
			} else {
				bg.alpha(1f);
				spriteIcon.alpha(1f);
				label.alpha(1f);
			}
		}

		@Override
		protected void layout() {
			super.layout();
			bg.x = x;
			bg.y = y;
			bg.size(width, height);

			spriteIcon.x = x + (width - spriteIcon.width()) / 2f;
			spriteIcon.y = y + 2f + (16f - spriteIcon.height()) / 2f;
			PixelScene.align(spriteIcon);

			label.setPos(
					x + (width - label.width()) / 2f,
					y + height - label.height() - 2f
			);
			PixelScene.align(label);
		}

		@Override
		protected void onPointerDown() {
			applyVisualState(true);
			Sample.INSTANCE.play(Assets.Sounds.CLICK);
		}

		@Override
		protected void onPointerUp() {
			applyVisualState(false);
		}

		@Override
		protected void onClick() {
			if (canExecute && onExecute != null) {
				onExecute.run();
			} else if (onInspect != null) {
				onInspect.run();
			}
		}

		@Override
		protected boolean onLongClick() {
			if (onInspect != null) {
				onInspect.run();
				return true;
			}
			return false;
		}

		@Override
		protected void onRightClick() {
			if (onInspect != null) {
				onInspect.run();
			}
		}

		@Override
		protected String hoverText() {
			return hoverLabel;
		}

		@Override
		protected Group tooltipOverlayParent() {
			for (Gizmo g = parent; g != null; g = g.parent) {
				if (g instanceof WndTabbed) {
					return (Group) g;
				}
			}
			return null;
		}

		@Override
		protected boolean tooltipPreferUiOverlay() {
			return true;
		}
	}
}
