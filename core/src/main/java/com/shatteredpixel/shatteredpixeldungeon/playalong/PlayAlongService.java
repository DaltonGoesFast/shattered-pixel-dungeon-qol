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

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.Modifiers;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.desktop.StreamingCommandHandler;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.ChatCommandPayload;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.GhostState;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.SessionInfo;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.StreamEconomyState;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.TombstoneState;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.WipeEvent;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.WireMessage;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.ActionIndicator;
import com.shatteredpixel.shatteredpixeldungeon.utils.DungeonSeed;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.shatteredpixel.shatteredpixeldungeon.utils.StreamingEvents;
import com.watabou.noosa.Game;
import com.watabou.noosa.audio.Sample;
import com.watabou.utils.DeviceCompat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central coordinator for Play Along Mode across Host (Desktop) and Client (Desktop & Android).
 */
public final class PlayAlongService {

	public enum Role {
		NONE,
		HOST,
		CLIENT,
		SPECTATOR
	}

	/**
	 * Fatal doom class used when Dalton dies on the host so client deaths record cleanly in Rankings.
	 */
	public static class HostWipeDoom implements Hero.Doom {
		public final int hostDepth;
		public final String hostCause;
		public final int victims;

		public HostWipeDoom() {
			this(1, "Unknown", 0);
		}

		public HostWipeDoom(int hostDepth, String hostCause, int victims) {
			this.hostDepth = hostDepth;
			this.hostCause = hostCause;
			this.victims = victims;
		}

		@Override
		public void onDeath() {
			Dungeon.fail(this);
			GLog.n("☠ SOUL PACT BROKEN! Slain by Dalton's demise on Depth %d (%s). %d players wiped!",
					hostDepth, hostCause, victims);
		}
	}

	private static volatile Role role = Role.NONE;
	private static volatile SessionInfo liveSessionInfo = null;
	private static volatile boolean statusPolling = false;
	private static volatile long lastPollTimeMs = 0L;

	private static PlayAlongServer hostServer = null;
	private static PlayAlongClient client = null;

	private static volatile SessionInfo activeClientSession = null;
	private static volatile boolean clientJoinPending = false;
	private static volatile String clientUsername = "";
	private static volatile String localUserId = UUID.randomUUID().toString();
	private static volatile long runStartMs = 0L;

	private static final ConcurrentHashMap<String, GhostState> remoteGhosts = new ConcurrentHashMap<>();
	private static volatile GhostState hostGhost = null;
	private static final CopyOnWriteArrayList<TombstoneState> tombstones = new CopyOnWriteArrayList<>();
	private static final Map<Integer, Integer> depthCounts = new ConcurrentHashMap<>();

	private static volatile int hostDepth = 1;
	private static volatile int hostBranch = 0;
	private static volatile int hostPos = 0;
	private static volatile int aliveCount = 0;
	private static volatile int lastWipeCount = -1;
	private static volatile String lastWipeSummary = "";
	private static volatile boolean wipeTriggeredOnClient = false;
	private static volatile HostWipeDoom pendingWipeOnScene = null;
	private static volatile boolean localVictorySent = false;
	private static volatile boolean localDeathSent = false;

	private static volatile StreamEconomyState cachedEconomy = null;
	private static final ConcurrentHashMap<String, PlayAlongClient.WireMessageCallback> pendingStreamRequests = new ConcurrentHashMap<>();
	private static final CopyOnWriteArrayList<ChatCommandPayload> pendingChatCommands = new CopyOnWriteArrayList<>();

	public interface EconomyCallback {
		void onResult(StreamEconomyState state);
	}

	private static long lastMoveSendMs = 0L;

	private PlayAlongService() {}

	public static Role getRole() {
		return role;
	}

	public static boolean isActive() {
		return role != Role.NONE;
	}

	public static boolean isHosting() {
		return role == Role.HOST;
	}

	public static boolean isClient() {
		return role == Role.CLIENT;
	}

	public static boolean isSpectating() {
		return role == Role.SPECTATOR;
	}

	public static boolean isClientJoining() {
		return clientJoinPending && activeClientSession != null;
	}

	public static SessionInfo getClientSession() {
		return activeClientSession;
	}

	public static SessionInfo getLiveSession() {
		return liveSessionInfo;
	}

	public static int getHostDepth() {
		return hostDepth;
	}

	public static int getHostBranch() {
		return hostBranch;
	}

	public static int getHostPos() {
		return hostPos;
	}

	public static int getAliveCount() {
		return aliveCount;
	}

	public static int getLastWipeCount() {
		return lastWipeCount;
	}

	public static String getLastWipeSummary() {
		return lastWipeSummary;
	}

	public static boolean isHostAliveForSpectate() {
		return activeClientSession != null && activeClientSession.active && !wipeTriggeredOnClient;
	}

	public static boolean isLocalHostServerRunning() {
		return hostServer != null;
	}

	public static boolean isStreamServerLive() {
		SessionInfo info = liveSessionInfo;
		return info != null && (info.active || info.serverOnline);
	}

	public static boolean isPlayAlongRunLive() {
		SessionInfo info = liveSessionInfo;
		return info != null && info.active;
	}

	public static StreamEconomyState getCachedEconomy() {
		return cachedEconomy;
	}

	private static String effectiveAddress() {
		if (DeviceCompat.isDesktop() && hostServer != null) {
			return "127.0.0.1:" + SPDSettings.playAlongPort();
		}
		return SPDSettings.playAlongAddress();
	}

	public static List<GhostState> getGhostsForFloor(int depth, int branch) {
		List<GhostState> list = new ArrayList<>();
		if (role != Role.HOST) {
			GhostState hg = hostGhost;
			if (hg != null && hg.depth == depth && hg.branch == branch) {
				list.add(hg);
			}
		}
		for (GhostState g : remoteGhosts.values()) {
			if (g != null && g.depth == depth && g.branch == branch) {
				if (role == Role.CLIENT && g.userId != null && g.userId.equals(localUserId)) {
					continue;
				}
				list.add(g);
			}
		}
		return list;
	}

	public static List<GhostState> getAllRosterGhosts() {
		List<GhostState> list = new ArrayList<>();
		if (hostGhost != null) list.add(hostGhost);
		list.addAll(remoteGhosts.values());
		return list;
	}

	public static List<TombstoneState> getTombstonesForFloor(int depth, int branch) {
		List<TombstoneState> list = new ArrayList<>();
		for (TombstoneState t : tombstones) {
			if (t.depth == depth && t.branch == branch) {
				list.add(t);
			}
		}
		return list;
	}

	public static TombstoneState getTombstoneAt(int depth, int branch, int cell) {
		for (TombstoneState t : tombstones) {
			if (t.depth == depth && t.branch == branch && t.pos == cell) {
				return t;
			}
		}
		return null;
	}

	public static Map<Integer, Integer> getDepthCountsSnapshot() {
		return new LinkedHashMap<>(depthCounts);
	}

	// ---- Host Server Lifecycle (Desktop) ----

	public static synchronized void ensureHostServerStarted() {
		if (!DeviceCompat.isDesktop()) return;
		if (hostServer != null) return;
		int port = SPDSettings.playAlongPort();
		PlayAlongServer srv = new PlayAlongServer(port, new PlayAlongServer.HostCallbacks() {
			@Override
			public void onWorldStateUpdated(List<GhostState> ghosts, List<TombstoneState> tombs, int alive) {
				applyWorldStateSnapshot(null, ghosts, tombs, alive, null);
			}

			@Override
			public void onChatterDied(String username, int depth, String cause) {
				Game.runOnRenderThread(() -> GLog.n("☠ @%s perished on Depth %d (%s)", username, depth, cause));
			}

			@Override
			public void onChatterWon(String username, long durationMs) {
				Game.runOnRenderThread(() -> {
					GLog.p("🏆 @%s escaped with the Amulet of Yendor!", username);
					Sample.INSTANCE.play(Assets.Sounds.LEVELUP);
				});
				Map<String, Object> fields = new LinkedHashMap<>();
				fields.put("username", username);
				fields.put("duration_ms", durationMs);
				StreamingEvents.notifyTyped("playalong_victory", fields);
			}

			@Override
			public void onChatterBossSlain(String username, int depth, int awardedPts) {
				Game.runOnRenderThread(() -> GLog.p(
						"⚔ @%s defeated the Depth %d boss in Play Along (+%d pts)!",
						username, depth, awardedPts));
			}
		});
		try {
			srv.start();
			hostServer = srv;
			System.out.println("[PlayAlong] Host WebSocket server started on 0.0.0.0:" + port);
		} catch (Exception e) {
			System.err.println("[PlayAlong] Failed to start host server on port " + port + ": " + e.getMessage());
		}
	}

	public static synchronized void stopHostServer() {
		if (hostServer != null) {
			hostServer.endSession();
			hostServer.stopServer();
			hostServer = null;
		}
	}

	// ---- Title Screen Live Status Polling ----

	public static void pollLiveStatus(boolean force) {
		long now = System.currentTimeMillis();
		if (!force && (statusPolling || (now - lastPollTimeMs < 6000L))) return;
		statusPolling = true;
		lastPollTimeMs = now;
		PlayAlongClient.pingStatus(effectiveAddress(), info -> {
			statusPolling = false;
			if (info != null && (info.active || info.serverOnline)) {
				liveSessionInfo = info;
				if (role == Role.NONE && info.inDungeon) {
					hostDepth = info.hostDepth;
					hostPos = info.hostPos;
				}
			} else {
				liveSessionInfo = null;
			}
		});
	}

	// ---- Starting & Joining Runs ----

	public static void onHostRunStarted() {
		if (!DeviceCompat.isDesktop() || !SPDSettings.playAlongHostEnabled() || isClientJoining()) {
			return;
		}
		ensureHostServerStarted();
		if (hostServer == null) return;

		resetRuntimeCollections();
		role = Role.HOST;
		lastWipeCount = -1;
		lastWipeSummary = "";

		SessionInfo info = new SessionInfo();
		info.active = true;
		info.sessionId = UUID.randomUUID().toString();
		info.version = Game.version != null ? Game.version : "";
		info.seed = Dungeon.seed;
		info.customSeedText = Dungeon.customSeedText != null ? Dungeon.customSeedText : "";
		info.challenges = Dungeon.challenges;
		info.modifiers = Dungeon.modifiers;
		info.hostClass = Dungeon.hero != null ? Dungeon.hero.heroClass.name() : "WARRIOR";
		info.hostDepth = Dungeon.depth;
		info.hostPos = Dungeon.hero != null ? Dungeon.hero.pos : 0;
		info.hostHp = Dungeon.hero != null ? Dungeon.hero.HP : 20;
		info.hostHt = Dungeon.hero != null ? Dungeon.hero.HT : 20;

		GhostState hg = buildLocalGhostState("IDLE", Dungeon.hero != null ? Dungeon.hero.pos : 0, true);
		hostGhost = hg;
		hostDepth = Dungeon.depth;
		hostBranch = Dungeon.branch;
		hostServer.startSession(info, hg);
	}

	public static void startClientRun(SessionInfo session, HeroClass heroClass, String rawUsername) {
		if (session == null || !session.active) return;

		disconnect();
		resetRuntimeCollections();

		String shortId = Integer.toHexString(Math.abs(localUserId.hashCode()) % 0xFFF);
		String safeName = PlayAlongProtocol.sanitizeUsername(rawUsername, shortId);
		SPDSettings.playAlongUsername(safeName);
		SPDSettings.intro(false);

		role = Role.CLIENT;
		activeClientSession = session;
		clientJoinPending = true;
		clientUsername = safeName;
		wipeTriggeredOnClient = false;
		localVictorySent = false;
		localDeathSent = false;
		runStartMs = System.currentTimeMillis();
		hostDepth = session.hostDepth;
		hostPos = session.hostPos;

		GamesInProgress.selectedClass = heroClass;
		GamesInProgress.randomizedClass = false;
		GamesInProgress.pendingHeroName = safeName;
		GamesInProgress.curSlot = GamesInProgress.PLAY_ALONG_SLOT;
		Dungeon.deleteGame(GamesInProgress.PLAY_ALONG_SLOT, true);

		Dungeon.hero = null;
		Dungeon.daily = Dungeon.dailyReplay = false;
		Dungeon.seed = session.seed;
		Dungeon.customSeedText = (session.customSeedText != null) ? session.customSeedText : "";
		ActionIndicator.clearAction();

		connectClient(session, heroClass.name(), safeName, false);

		InterlevelScene.mode = InterlevelScene.Mode.DESCEND;
		Game.switchScene(InterlevelScene.class);
	}

	public static void clearClientJoinPending() {
		clientJoinPending = false;
	}

	public static void applyClientRulesToDungeon() {
		SessionInfo s = activeClientSession;
		if (s == null) return;
		Dungeon.seed = s.seed;
		Dungeon.customSeedText = (s.customSeedText != null) ? s.customSeedText : "";
		Dungeon.challenges = s.challenges;
		Dungeon.modifiers = s.modifiers;
	}

	private static void connectClient(SessionInfo session, String heroClass, String username, boolean isRejoin) {
		client = new PlayAlongClient(new PlayAlongClient.ClientListener() {
			@Override
			public void onJoinAck(WireMessage msg) {
				if (msg.username != null && !msg.username.isEmpty()) {
					clientUsername = msg.username;
				}
				if (msg.session != null) {
					activeClientSession = msg.session;
				}
				Game.runOnRenderThread(() -> GLog.p("Joined Dalton's Play Along run as %s!", clientUsername));
			}

			@Override
			public void onJoinError(String error) {
				final String err = error != null ? error : "Could not join Play Along run.";
				Game.runOnRenderThread(() -> {
					GLog.n(err);
					disconnect();
				});
			}

			@Override
			public void onWorldState(WireMessage msg) {
				applyWorldStateSnapshot(msg.hostGhost, msg.ghosts, msg.tombstones, msg.activePlayers, msg.depthCounts);
			}

			@Override
			public void onHostAnkhPopped(WireMessage msg) {
				Game.runOnRenderThread(() -> {
					Sample.INSTANCE.play(Assets.Sounds.TELEPORT);
					GameScene.flash(0x60FFFF40);
					GLog.w("✨ DALTON POPPED AN ANKH! (Run still alive — keep going!)");
				});
			}

			@Override
			public void onHostDied(WireMessage msg) {
				triggerHostWipeOnClient(msg.depth, msg.cause, msg.victims);
			}

			@Override
			public void onPlayerDiedNotice(WireMessage msg) {
				Game.runOnRenderThread(() -> GLog.n("☠ @%s perished on Depth %d (%s)",
						msg.username, msg.depth, msg.cause != null ? msg.cause : "slain"));
			}

			@Override
			public void onPlayerWonNotice(WireMessage msg) {
				Game.runOnRenderThread(() -> {
					Sample.INSTANCE.play(Assets.Sounds.LEVELUP);
					GLog.p("🏆 @%s secured the Amulet of Yendor!", msg.username);
				});
			}

			@Override
			public void onMilestoneRewardNotice(WireMessage msg) {
				if (msg == null || msg.pointsAwarded <= 0) return;
				Game.runOnRenderThread(() -> {
					Sample.INSTANCE.play(Assets.Sounds.LEVELUP);
					String kindLabel = "donor".equalsIgnoreCase(msg.rewardKind) ? "Donor" : "Chat";
					GLog.p("✨ Play Along Reward: +%d %s pts (%s)! Balance: %d pts",
							msg.pointsAwarded,
							kindLabel,
							msg.message != null ? msg.message : "Milestone",
							msg.pointsBalance);
				});
			}

			@Override
			public void onStreamMessage(WireMessage msg) {
				if (msg == null) return;
				if (msg.economy != null) {
					applyEconomyUpdate(msg.economy);
				}
				if (msg.requestId != null) {
					PlayAlongClient.WireMessageCallback cb = pendingStreamRequests.remove(msg.requestId);
					if (cb != null) {
						cb.onResult(msg);
					}
				}
			}

			@Override
			public void onChatCommand(WireMessage msg) {
				if (msg == null || msg.chatCommand == null) return;
				final ChatCommandPayload p = msg.chatCommand;
				final boolean isConfig = "spawn_scale_config".equals(p.command);
				if (!isConfig) {
					if (role != Role.CLIENT) return;
					String sender = normalizeChatUsername(p.username);
					if (!sender.isEmpty()) {
						if (sender.equals(normalizeChatUsername(clientUsername))
								|| sender.equals(normalizeChatUsername(SPDSettings.playAlongUsername()))) {
							return;
						}
						StreamEconomyState eco = cachedEconomy;
						if (eco != null && eco.linked
								&& sender.equals(normalizeChatUsername(eco.linkedUsername))) {
							return;
						}
					}
				}
				Game.runOnRenderThread(() -> {
					if (isConfig) {
						StreamingCommandHandler.executeMirroredChatCommand(p);
						return;
					}
					if (role != Role.CLIENT) return;
					if (Dungeon.hero == null || Dungeon.level == null || !(Game.scene() instanceof GameScene)) {
						if (pendingChatCommands.size() < 32) {
							pendingChatCommands.add(p);
						}
						return;
					}
					if (Dungeon.hero.isAlive()) {
						StreamingCommandHandler.executeMirroredChatCommand(p);
					}
				});
			}

			@Override
			public void onConnectionLost(boolean permanentlyFailed) {
				if (permanentlyFailed) {
					Game.runOnRenderThread(() -> GLog.w("Disconnected from Dalton's Play Along lobby."));
				}
			}
		});

		String ver = Game.version != null ? Game.version : "";
		client.connect(effectiveAddress(), localUserId, username, SPDSettings.streamClientToken(),
				heroClass, ver, session.sessionId, Dungeon.depth, Dungeon.branch,
				Dungeon.hero != null ? Dungeon.hero.pos : 0, isRejoin);
	}

	// ---- In-Game Hooks ----

	public static void onSceneCreated() {
		if (role == Role.NONE || Dungeon.hero == null) return;
		HostWipeDoom pending = pendingWipeOnScene;
		if (pending != null && Dungeon.level != null) {
			pendingWipeOnScene = null;
			executeClientWipe(pending);
			return;
		}
		if (role == Role.SPECTATOR) {
			magicMapCurrentFloor();
			if (isHostAliveForSpectate() && (Dungeon.depth != hostDepth || Dungeon.branch != hostBranch)) {
				followHostFloor();
				return;
			}
		}

		if (role == Role.HOST && hostServer != null) {
			GhostState hg = buildLocalGhostState("IDLE", Dungeon.hero.pos, true);
			hostGhost = hg;
			hostDepth = Dungeon.depth;
			hostBranch = Dungeon.branch;
			hostPos = Dungeon.hero.pos;
			hostServer.updateHostState(hg);
			hostServer.updateHostVitals(Dungeon.hero.HP, Dungeon.hero.HT);
		} else if ((role == Role.CLIENT || role == Role.SPECTATOR) && client != null) {
			sendClientState("IDLE", Dungeon.hero.pos);
			if (role == Role.CLIENT && Dungeon.level != null && Dungeon.hero.isAlive()
					&& (Game.scene() instanceof GameScene) && !pendingChatCommands.isEmpty()) {
				List<ChatCommandPayload> drained = new ArrayList<>(pendingChatCommands);
				pendingChatCommands.clear();
				for (ChatCommandPayload p : drained) {
					if (!Dungeon.hero.isAlive()) break;
					StreamingCommandHandler.executeMirroredChatCommand(p);
				}
			}
		}
	}

	public static void onHeroMoved(int from, int to) {
		if (role == Role.NONE || Dungeon.hero == null) return;
		if (role == Role.HOST && hostServer != null) {
			GhostState hg = buildLocalGhostState("MOVE", to, true);
			hg.facingLeft = (to % Dungeon.level.width()) < (from % Dungeon.level.width());
			hostGhost = hg;
			hostDepth = Dungeon.depth;
			hostBranch = Dungeon.branch;
			hostPos = to;
			hostServer.updateHostState(hg);
			hostServer.updateHostVitals(Dungeon.hero.HP, Dungeon.hero.HT);
		} else if (role == Role.CLIENT && client != null) {
			long now = System.currentTimeMillis();
			if (now - lastMoveSendMs >= 60L) {
				lastMoveSendMs = now;
				sendClientState("MOVE", to);
			}
		}
	}

	public static void onHeroAttacked(int targetPos) {
		if (role == Role.NONE || Dungeon.hero == null || Dungeon.level == null) return;
		boolean left = (targetPos % Dungeon.level.width()) < (Dungeon.hero.pos % Dungeon.level.width());
		if (role == Role.HOST && hostServer != null) {
			GhostState hg = buildLocalGhostState("ATTACK", Dungeon.hero.pos, true);
			hg.facingLeft = left;
			hostServer.updateHostState(hg);
			hostServer.updateHostVitals(Dungeon.hero.HP, Dungeon.hero.HT);
		} else if (role == Role.CLIENT && client != null) {
			WireMessage msg = buildClientStateMsg("ATTACK", Dungeon.hero.pos);
			msg.facingLeft = left;
			client.send(msg);
		}
	}

	public static void onHeroAnkhPopped() {
		if (role == Role.HOST && hostServer != null) {
			hostServer.broadcastHostAnkhPopped();
			Map<String, Object> fields = new LinkedHashMap<>();
			fields.put("active_players", hostServer.countAliveClients());
			fields.put("depth", Dungeon.depth);
			StreamingEvents.notifyTyped("playalong_ankh", fields);
		}
	}

	public static void onHeroReallyDied(Object cause) {
		if (role == Role.HOST && hostServer != null) {
			String causeStr = cause != null ? cause.getClass().getSimpleName() : "Unknown";
			WipeEvent wipe = hostServer.triggerHostWipe(Dungeon.depth, causeStr);
			lastWipeCount = wipe.victims;
			lastWipeSummary = wipe.victimNames;

			Map<String, Object> fields = new LinkedHashMap<>();
			fields.put("victims", wipe.victims);
			fields.put("depth", wipe.hostDepth);
			fields.put("victim_names", wipe.victimNames);
			StreamingEvents.notifyTyped("playalong_wipe", fields);
		} else if (role == Role.CLIENT && client != null && !(cause instanceof HostWipeDoom) && !localDeathSent) {
			localDeathSent = true;
			WireMessage msg = new WireMessage();
			msg.type = "player_died";
			msg.depth = Dungeon.depth;
			msg.branch = Dungeon.branch;
			msg.pos = Dungeon.hero != null ? Dungeon.hero.pos : 0;
			msg.cause = cause != null ? cause.getClass().getSimpleName() : "Perished";
			client.send(msg);
		}
	}

	public static void onBossSlain(int depth) {
		if (role == Role.CLIENT && client != null) {
			WireMessage msg = new WireMessage();
			msg.type = "player_boss_slain";
			msg.depth = depth;
			msg.clientToken = SPDSettings.streamClientToken();
			client.send(msg);
		}
	}

	public static void broadcastHostChatCommand(ChatCommandPayload payload) {
		if (payload == null) return;
		if (!"spawn_scale_config".equals(payload.command) && !SPDSettings.playAlongSharedChat()) {
			return;
		}
		if (role == Role.HOST && hostServer != null) {
			hostServer.broadcastChatCommand(payload);
		}
	}

	private static String normalizeChatUsername(String raw) {
		if (raw == null) return "";
		String s = raw.trim();
		while (s.startsWith("@")) {
			s = s.substring(1).trim();
		}
		return s.toLowerCase(Locale.ROOT);
	}

	public static void onAmuletObtained() {
		if (role == Role.CLIENT && client != null && !localVictorySent) {
			localVictorySent = true;
			WireMessage msg = new WireMessage();
			msg.type = "player_won";
			msg.durationMs = Math.max(0L, System.currentTimeMillis() - runStartMs);
			msg.clientToken = SPDSettings.streamClientToken();
			client.send(msg);
		}
	}

	// ---- Always-On Stream Command & Economy Requests ----

	public static void requestStreamEconomy(final EconomyCallback callback) {
		WireMessage req = new WireMessage();
		req.type = "stream_state_req";
		req.requestId = UUID.randomUUID().toString();
		req.clientToken = SPDSettings.streamClientToken();
		dispatchStreamControlRequest(req, 4000, msg -> {
			StreamEconomyState eco = (msg != null && msg.economy != null) ? msg.economy : null;
			if (eco != null) {
				applyEconomyUpdate(eco);
			}
			if (callback != null) {
				callback.onResult(eco);
			}
		});
	}

	public static void requestStreamLinkCode(final PlayAlongClient.WireMessageCallback callback) {
		WireMessage req = new WireMessage();
		req.type = "stream_link_req";
		req.requestId = UUID.randomUUID().toString();
		req.clientToken = SPDSettings.streamClientToken();
		dispatchStreamControlRequest(req, 4500, msg -> {
			if (msg != null && msg.economy != null) {
				applyEconomyUpdate(msg.economy);
			}
			if (callback != null) {
				callback.onResult(msg);
			}
		});
	}

	public static void sendStreamCommand(final String rawCommand, final PlayAlongClient.WireMessageCallback callback) {
		WireMessage req = new WireMessage();
		req.type = "stream_cmd_req";
		req.requestId = UUID.randomUUID().toString();
		req.clientToken = SPDSettings.streamClientToken();
		req.rawCommand = rawCommand != null ? rawCommand.trim() : "";
		dispatchStreamControlRequest(req, 13000, msg -> {
			if (msg != null && msg.economy != null) {
				applyEconomyUpdate(msg.economy);
			}
			if (callback != null) {
				callback.onResult(msg);
			}
		});
	}

	private static void dispatchStreamControlRequest(final WireMessage req, final int timeoutMs,
	                                                 final PlayAlongClient.WireMessageCallback callback) {
		PlayAlongClient active = client;
		if ((role == Role.CLIENT || role == Role.SPECTATOR) && active != null && active.isConnected()) {
			final String reqId = req.requestId;
			pendingStreamRequests.put(reqId, callback);
			active.send(req);
			Thread timeoutWatch = new Thread(() -> {
				try {
					Thread.sleep(timeoutMs);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
				PlayAlongClient.WireMessageCallback timedOut = pendingStreamRequests.remove(reqId);
				if (timedOut != null) {
					timedOut.onResult(null);
				}
			}, "PlayAlongReqTimeout");
			timeoutWatch.setDaemon(true);
			timeoutWatch.start();
			return;
		}
		PlayAlongClient.sendControlRequest(effectiveAddress(), req, timeoutMs, callback);
	}

	private static void applyEconomyUpdate(StreamEconomyState eco) {
		if (eco == null) return;
		cachedEconomy = eco;
		if (role == Role.NONE && eco.inDungeon) {
			hostDepth = eco.hostDepth;
		}
		if (eco.linked && eco.linkedUsername != null && !eco.linkedUsername.trim().isEmpty()) {
			String uname = eco.linkedUsername.trim();
			if (SPDSettings.playAlongUsername().isEmpty()) {
				SPDSettings.playAlongUsername(PlayAlongProtocol.sanitizeUsername(uname, "01"));
			}
		}
	}

	public static void onAppPause() {
		// Socket stays managed by Java-WebSocket; nothing special required on pause.
	}

	public static void onAppResume() {
		if ((role == Role.CLIENT || role == Role.SPECTATOR) && client != null) {
			client.reconnectIfNeeded(Dungeon.depth, Dungeon.branch, Dungeon.hero != null ? Dungeon.hero.pos : 0);
		}
	}

	public static void magicMapCurrentFloor() {
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

	public static void enterSpiritCam() {
		if (role != Role.CLIENT || !isHostAliveForSpectate()) return;
		role = Role.SPECTATOR;
		magicMapCurrentFloor();
		if (Dungeon.depth != hostDepth || Dungeon.branch != hostBranch) {
			followHostFloor();
		} else {
			ShatteredPixelDungeon.seamlessResetScene();
		}
	}

	public static void followHostFloor() {
		if (role != Role.SPECTATOR || !isHostAliveForSpectate()) return;
		if (!(Game.scene() instanceof GameScene)) return;
		InterlevelScene.mode = InterlevelScene.Mode.RETURN;
		InterlevelScene.returnDepth = Math.max(1, hostDepth);
		InterlevelScene.returnBranch = Math.max(0, hostBranch);
		InterlevelScene.returnPos = Math.max(0, hostPos);
		InterlevelScene.debugGenerateSkipped = true;
		Game.switchScene(InterlevelScene.class);
	}

	public static void checkSpectatorFloorFollow() {
		if (role == Role.SPECTATOR && isHostAliveForSpectate()
				&& (Dungeon.depth != hostDepth || Dungeon.branch != hostBranch)
				&& Game.scene() instanceof GameScene) {
			followHostFloor();
		}
	}

	public static void disconnect() {
		if (role == Role.HOST && hostServer != null) {
			hostServer.endSession();
		}
		if (client != null) {
			client.close();
			client = null;
		}
		role = Role.NONE;
		clientJoinPending = false;
		activeClientSession = null;
		wipeTriggeredOnClient = false;
		pendingWipeOnScene = null;
		lastWipeCount = -1;
		lastWipeSummary = "";
		resetRuntimeCollections();
	}

	private static void triggerHostWipeOnClient(int hDepth, String hCause, int victims) {
		if (wipeTriggeredOnClient) return;
		wipeTriggeredOnClient = true;
		if (activeClientSession != null) {
			activeClientSession.active = false;
		}
		final String causeStr = (hCause == null || hCause.isEmpty()) ? "Unknown" : hCause;
		final HostWipeDoom doom = new HostWipeDoom(hDepth, causeStr, victims);

		Game.runOnRenderThread(() -> {
			if (Dungeon.hero == null || Dungeon.level == null) {
				pendingWipeOnScene = doom;
				return;
			}
			executeClientWipe(doom);
		});
	}

	private static void executeClientWipe(HostWipeDoom doom) {
		if (Dungeon.hero == null || Dungeon.level == null) return;
		if (!Dungeon.hero.isAlive()) {
			GameScene.flash(0x88FF0000);
			Sample.INSTANCE.play(SPDSettings.moddedBanners() ? Assets.Sounds.DEATH_MOD : Assets.Sounds.DEATH);
			GLog.n("☠ SOUL PACT BROKEN! Dalton died on Depth %d (%s). %d players wiped!",
					doom.hostDepth, doom.hostCause, doom.victims);
			return;
		}
		Dungeon.hero.HP = 0;
		if (Dungeon.hero.sprite != null) {
			Dungeon.hero.sprite.die();
		}
		com.shatteredpixel.shatteredpixeldungeon.actors.Actor.fixTime();
		GameScene.flash(0x88FF0000);
		Hero.reallyDie(doom);
	}

	private static void applyWorldStateSnapshot(GhostState newHostGhost, List<GhostState> ghosts,
	                                            List<TombstoneState> tombs, int alive,
	                                            Map<Integer, Integer> depths) {
		if (newHostGhost != null) {
			hostGhost = newHostGhost;
			hostDepth = newHostGhost.depth;
			hostBranch = newHostGhost.branch;
			hostPos = newHostGhost.pos;
		}
		if (ghosts != null) {
			remoteGhosts.clear();
			for (GhostState g : ghosts) {
				if (g != null && g.userId != null) {
					remoteGhosts.put(g.userId, g);
				}
			}
		}
		if (tombs != null) {
			tombstones.clear();
			tombstones.addAll(tombs);
		}
		if (depths != null) {
			depthCounts.clear();
			depthCounts.putAll(depths);
		} else if (ghosts != null) {
			depthCounts.clear();
			for (GhostState g : ghosts) {
				if (g != null) {
					depthCounts.put(g.depth, depthCounts.getOrDefault(g.depth, 0) + 1);
				}
			}
		}
		aliveCount = alive;
		if (role == Role.SPECTATOR && isHostAliveForSpectate()
				&& (Dungeon.depth != hostDepth || Dungeon.branch != hostBranch)) {
			Game.runOnRenderThread(PlayAlongService::checkSpectatorFloorFollow);
		}
	}

	private static void resetRuntimeCollections() {
		remoteGhosts.clear();
		hostGhost = null;
		tombstones.clear();
		depthCounts.clear();
		pendingChatCommands.clear();
		aliveCount = 0;
	}

	private static void sendClientState(String action, int pos) {
		if (client == null) return;
		client.send(buildClientStateMsg(action, pos));
	}

	private static WireMessage buildClientStateMsg(String action, int pos) {
		WireMessage msg = new WireMessage();
		msg.type = "player_state";
		msg.userId = localUserId;
		msg.heroClass = Dungeon.hero != null ? Dungeon.hero.heroClass.name() : "WARRIOR";
		msg.armorTier = Dungeon.hero != null ? Dungeon.hero.tier() : 0;
		msg.depth = Dungeon.depth;
		msg.branch = Dungeon.branch;
		msg.pos = pos;
		msg.hpPct = (Dungeon.hero != null && Dungeon.hero.HT > 0)
				? Math.max(0f, Math.min(1f, (float) Dungeon.hero.HP / Dungeon.hero.HT))
				: 0f;
		msg.action = action;
		if (Dungeon.hero != null && Dungeon.hero.sprite != null) {
			msg.facingLeft = Dungeon.hero.sprite.flipHorizontal;
		}
		return msg;
	}

	private static GhostState buildLocalGhostState(String action, int pos, boolean isHost) {
		GhostState g = new GhostState();
		g.userId = isHost ? "host" : localUserId;
		g.username = isHost ? "Dalton" : clientUsername;
		g.heroClass = Dungeon.hero != null ? Dungeon.hero.heroClass.name() : "WARRIOR";
		g.armorTier = Dungeon.hero != null ? Dungeon.hero.tier() : 0;
		g.depth = Dungeon.depth;
		g.branch = Dungeon.branch;
		g.pos = pos;
		g.facingLeft = Dungeon.hero != null && Dungeon.hero.sprite != null && Dungeon.hero.sprite.flipHorizontal;
		g.hpPct = (Dungeon.hero != null && Dungeon.hero.HT > 0)
				? Math.max(0f, Math.min(1f, (float) Dungeon.hero.HP / Dungeon.hero.HT))
				: 1f;
		g.action = action;
		g.isHost = isHost;
		g.lastUpdateMs = System.currentTimeMillis();
		return g;
	}
}
