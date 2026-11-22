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
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.ChatCommandPayload;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.GhostState;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.SessionInfo;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.StreamEconomyState;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.TombstoneState;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.WipeEvent;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.WireMessage;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.shatteredpixel.shatteredpixeldungeon.streaming.MirroredChatCommandExecutor;
import com.shatteredpixel.shatteredpixeldungeon.ui.ActionIndicator;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.shatteredpixel.shatteredpixeldungeon.utils.StreamingEvents;
import com.watabou.noosa.Game;
import com.watabou.noosa.audio.Sample;
import com.watabou.utils.DeviceCompat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Session and Soul Pact wipe coordinator for Play Along Mode across Host (Desktop)
 * and Client (Desktop & Android), delegating ghost state to {@link PlayAlongGhostStore},
 * stream economy/mirroring to {@link PlayAlongStreamBridge}, and read-only Spirit Cam
 * to {@link PlayAlongSpectatorController}.
 */
public final class PlayAlongService {

	public enum Role {
		NONE,
		HOST,
		CLIENT,
		SPECTATOR
	}

	/**
	 * Fatal doom class used when the host dies so client deaths record cleanly in Rankings.
	 */
	public static class HostWipeDoom implements Hero.Doom {
		public final int hostDepth;
		public final String hostCause;
		public final int victims;
		public final String hostName;

		public HostWipeDoom() {
			this(1, "Unknown", 0, "Host");
		}

		public HostWipeDoom(int hostDepth, String hostCause, int victims) {
			this(hostDepth, hostCause, victims, "Host");
		}

		public HostWipeDoom(int hostDepth, String hostCause, int victims, String hostName) {
			this.hostDepth = hostDepth;
			this.hostCause = hostCause;
			this.victims = victims;
			this.hostName = (hostName != null && !hostName.trim().isEmpty()) ? hostName.trim() : "Host";
		}

		@Override
		public void onDeath() {
			Dungeon.fail(this);
			GLog.n("☠ SOUL PACT BROKEN! Slain by %s's demise on Depth %d (%s). %d players wiped!",
					hostName, hostDepth, hostCause, victims);
		}
	}

	public interface EconomyCallback {
		void onResult(StreamEconomyState state);
	}

	private static final PlayAlongGhostStore GHOST_STORE = new PlayAlongGhostStore();
	private static final PlayAlongStreamBridge STREAM_BRIDGE = new PlayAlongStreamBridge();
	private static final PlayAlongSpectatorController SPECTATOR = new PlayAlongSpectatorController();

	private static volatile Role role = Role.NONE;
	private static volatile SessionInfo liveSessionInfo = null;
	private static volatile boolean statusPolling = false;
	private static volatile long lastPollTimeMs = 0L;

	private static PlayAlongServer hostServer = null;
	private static PlayAlongClient client = null;
	private static volatile String clientAddressOverride = null;

	private static volatile SessionInfo activeClientSession = null;
	private static volatile boolean clientJoinPending = false;
	private static volatile String clientUsername = "";
	private static volatile String localUserId = UUID.randomUUID().toString();
	private static volatile long runStartMs = 0L;

	private static volatile int lastWipeCount = -1;
	private static volatile String lastWipeSummary = "";
	private static volatile boolean wipeTriggeredOnClient = false;
	private static volatile HostWipeDoom pendingWipeOnScene = null;
	private static volatile boolean localVictorySent = false;
	private static volatile boolean localDeathSent = false;
	private static volatile String pendingHostNotice = null;
	private static volatile boolean clientHasJoinedOnce = false;

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

	public static String getHostDisplayName() {
		if (role == Role.HOST && hostServer != null) {
			SessionInfo s = hostServer.getActiveSession();
			if (s != null) return s.displayHostName();
			return SPDSettings.playAlongHostUsernameSafe();
		}
		if (activeClientSession != null) {
			return activeClientSession.displayHostName();
		}
		if (liveSessionInfo != null) {
			return liveSessionInfo.displayHostName();
		}
		return SPDSettings.playAlongHostUsernameSafe();
	}

	public static int getHostDepth() {
		return GHOST_STORE.getHostDepth();
	}

	public static int getHostBranch() {
		return GHOST_STORE.getHostBranch();
	}

	public static int getHostPos() {
		return GHOST_STORE.getHostPos();
	}

	public static int getAliveCount() {
		return GHOST_STORE.getAliveCount();
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

	public static PlayAlongServer getHostServer() {
		return hostServer;
	}

	public static PlayAlongHostMetrics getHostMetrics() {
		return hostServer != null ? hostServer.getMetrics() : null;
	}

	public static boolean isStreamServerLive() {
		SessionInfo info = liveSessionInfo;
		return info != null && (info.active || info.inDungeon);
	}

	public static boolean isPlayAlongRunLive() {
		SessionInfo info = liveSessionInfo;
		return info != null && info.active;
	}

	public static StreamEconomyState getCachedEconomy() {
		return STREAM_BRIDGE.getCachedEconomy();
	}

	public static PlayAlongGhostStore getGhostStore() {
		return GHOST_STORE;
	}

	public static PlayAlongStreamBridge getStreamBridge() {
		return STREAM_BRIDGE;
	}

	public static void setCommandExecutor(MirroredChatCommandExecutor executor) {
		STREAM_BRIDGE.setCommandExecutor(executor);
	}

	public static void setClientAddressOverride(String address) {
		clientAddressOverride = (address == null || address.trim().isEmpty()) ? null : address.trim();
	}

	private static String effectiveAddress() {
		String override = clientAddressOverride;
		if (override != null) {
			return override;
		}
		if (DeviceCompat.isDesktop() && hostServer != null) {
			return "127.0.0.1:" + SPDSettings.playAlongPort();
		}
		return SPDSettings.playAlongAddress();
	}

	public static List<GhostState> getGhostsForFloor(int depth, int branch) {
		return GHOST_STORE.getGhostsForFloor(depth, branch, role, localUserId);
	}

	public static List<GhostState> getAllRosterGhosts() {
		return GHOST_STORE.getAllRosterGhosts();
	}

	public static List<TombstoneState> getTombstonesForFloor(int depth, int branch) {
		return GHOST_STORE.getTombstonesForFloor(depth, branch);
	}

	public static TombstoneState getTombstoneAt(int depth, int branch, int cell) {
		return GHOST_STORE.getTombstoneAt(depth, branch, cell);
	}

	public static Map<Integer, Integer> getDepthCountsSnapshot() {
		return GHOST_STORE.getDepthCountsSnapshot();
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
					if (Gdx.audio != null) {
						Sample.INSTANCE.play(Assets.Sounds.LEVELUP);
					}
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

			@Override
			public void onHostWarning(String failureClass, String message) {
				Game.runOnRenderThread(() -> GLog.w("[PlayAlong] %s", message));
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
			if (info != null && (info.active || info.inDungeon)) {
				liveSessionInfo = info;
				if (role == Role.NONE && info.inDungeon) {
					GHOST_STORE.setHostPosition(info.hostDepth, GHOST_STORE.getHostBranch(), info.hostPos);
				}
			} else {
				liveSessionInfo = null;
			}
		});
	}

	// ---- Starting & Joining Runs ----

	public static void onHostRunStarted() {
		startOrResumeHostSession(false, false, false);
	}

	public static void onHostRunLoaded() {
		startOrResumeHostSession(true, true, true);
	}

	public static void onQuitToTitle() {
		if (role == Role.HOST && hostServer != null) {
			boolean runStillAlive = Dungeon.hero != null
					&& (Dungeon.hero.isAlive() || com.shatteredpixel.shatteredpixeldungeon.windows.WndResurrect.instance != null);
			if (runStillAlive) {
				role = Role.NONE;
				pendingHostNotice = null;
				System.out.println("[PlayAlong] Host saved & quit to title — keeping Play Along session alive.");
				return;
			}
		}
		disconnect();
	}

	public static void onHostSettingChanged(boolean enabled) {
		if (!DeviceCompat.isDesktop()) return;
		if (enabled) {
			ensureHostServerStarted();
			if (role == Role.NONE && !isClientJoining()
					&& Game.scene() instanceof GameScene
					&& Dungeon.hero != null && Dungeon.hero.isAlive()) {
				startOrResumeHostSession(true, true, false);
				String notice = pendingHostNotice;
				pendingHostNotice = null;
				if (notice != null) {
					GLog.p(notice);
				}
			}
		} else {
			endHostSessionIfRunning();
		}
	}

	public static void onHostSaveDeleted(String deletedSessionId) {
		if (!DeviceCompat.isDesktop() || hostServer == null
				|| deletedSessionId == null || deletedSessionId.trim().isEmpty()) {
			return;
		}
		SessionInfo existing = hostServer.getActiveSession();
		if (existing != null && existing.active && deletedSessionId.trim().equals(existing.sessionId)) {
			endHostSessionIfRunning();
		}
	}

	private static void startOrResumeHostSession(boolean allowResumeExisting, boolean isLoadedSave,
	                                             boolean persistIdIfNew) {
		if (!DeviceCompat.isDesktop() || isClientJoining()
				|| role == Role.CLIENT || role == Role.SPECTATOR) {
			return;
		}
		if (!SPDSettings.playAlongHostEnabled()) {
			endHostSessionIfRunning();
			if (!isLoadedSave) {
				Dungeon.playAlongSessionId = "";
			}
			return;
		}
		if (Dungeon.hero == null) return;

		ensureHostServerStarted();
		if (hostServer == null) return;

		String savedSid = Dungeon.playAlongSessionId != null ? Dungeon.playAlongSessionId.trim() : "";

		if (allowResumeExisting && !savedSid.isEmpty()) {
			SessionInfo existing = hostServer.getActiveSession();
			if (existing != null && existing.active && savedSid.equals(existing.sessionId)) {
				role = Role.HOST;
				lastWipeCount = -1;
				lastWipeSummary = "";

				GhostState hg = buildLocalGhostState("IDLE", Dungeon.hero.pos, true);
				GHOST_STORE.setHostGhost(hg);

				WireMessage world = hostServer.resumeSession(
						hg,
						Dungeon.hero.HP,
						Dungeon.hero.HT,
						Dungeon.hero.heroClass != null ? Dungeon.hero.heroClass.name() : "WARRIOR");
				if (world != null) {
					applyWorldStateSnapshot(hg, world.ghosts, world.tombstones, world.activePlayers, world.depthCounts);
					String codeNote = hostServer.isJoinCodeRequired()
							? (" [Code: " + hostServer.getLobbyJoinCode() + "]")
							: "";
					pendingHostNotice = world.activePlayers > 0
							? ("Play Along resumed (" + world.activePlayers
							+ (world.activePlayers == 1 ? " racer" : " racers") + " still connected)!" + codeNote)
							: ("Play Along lobby resumed!" + codeNote);
					System.out.println("[PlayAlong] Resumed Play Along session " + savedSid
							+ " (activePlayers=" + world.activePlayers + ")");
					return;
				}
			}
		}

		resetRuntimeCollections();
		role = Role.HOST;
		lastWipeCount = -1;
		lastWipeSummary = "";

		boolean stampedNewId = false;
		String sessionId;
		if (isLoadedSave && !savedSid.isEmpty()) {
			sessionId = savedSid;
		} else {
			sessionId = UUID.randomUUID().toString();
			Dungeon.playAlongSessionId = sessionId;
			stampedNewId = isLoadedSave;
			if (hostServer.isJoinCodeRequired()) {
				hostServer.rotateLobbyJoinCode();
			}
		}

		SessionInfo info = new SessionInfo();
		info.active = true;
		info.sessionId = sessionId;
		info.version = Game.version != null ? Game.version : "";
		info.hostUsername = SPDSettings.playAlongHostUsernameSafe();
		info.joinCodeRequired = hostServer.isJoinCodeRequired();
		info.seed = Dungeon.seed;
		info.customSeedText = Dungeon.customSeedText != null ? Dungeon.customSeedText : "";
		info.challenges = Dungeon.challenges;
		info.modifiers = Dungeon.modifiers;
		info.hostClass = Dungeon.hero.heroClass != null ? Dungeon.hero.heroClass.name() : "WARRIOR";
		info.hostDepth = Dungeon.depth;
		info.hostPos = Dungeon.hero.pos;
		info.hostHp = Dungeon.hero.HP;
		info.hostHt = Dungeon.hero.HT;

		GhostState hg = buildLocalGhostState("IDLE", Dungeon.hero.pos, true);
		GHOST_STORE.setHostGhost(hg);
		hostServer.startSession(info, hg);

		if (persistIdIfNew && stampedNewId
				&& GamesInProgress.curSlot > 0 && GamesInProgress.curSlot != GamesInProgress.PLAY_ALONG_SLOT) {
			try {
				Dungeon.saveGame(GamesInProgress.curSlot);
			} catch (Throwable t) {
				System.err.println("[PlayAlong] Failed to persist sessionId on save load: "
						+ t.getClass().getSimpleName() + ": " + t.getMessage());
			}
		}

		if (isLoadedSave) {
			String codeNote = hostServer.isJoinCodeRequired()
					? (" [Code: " + hostServer.getLobbyJoinCode() + "]")
					: "";
			pendingHostNotice = (!savedSid.isEmpty()
					? "Play Along lobby restored for this save (racers can reconnect)!"
					: "Play Along lobby is now LIVE for this run!") + codeNote;
			System.out.println("[PlayAlong] " + (!savedSid.isEmpty() ? "Restored" : "Started")
					+ " Play Along session " + sessionId + " on save load.");
		}
	}

	private static void endHostSessionIfRunning() {
		if (hostServer != null) {
			SessionInfo existing = hostServer.getActiveSession();
			if (existing != null && existing.active) {
				hostServer.endSession();
			}
		}
		if (role == Role.HOST) {
			role = Role.NONE;
		}
		pendingHostNotice = null;
		lastWipeCount = -1;
		lastWipeSummary = "";
		resetRuntimeCollections();
	}

	public static void startClientRun(SessionInfo session, HeroClass heroClass, String rawUsername) {
		startClientRun(session, heroClass, rawUsername, SPDSettings.playAlongJoinCode());
	}

	public static void startClientRun(SessionInfo session, HeroClass heroClass, String rawUsername, String joinCode) {
		if (session == null || !session.active) return;

		disconnect();
		resetRuntimeCollections();

		String shortId = Integer.toHexString(Math.abs(localUserId.hashCode()) % 0xFFF);
		String safeName = PlayAlongProtocol.sanitizeUsername(rawUsername, shortId, session.hostUsername);
		SPDSettings.playAlongUsername(safeName);
		SPDSettings.intro(false);
		if (joinCode != null && !joinCode.trim().isEmpty()) {
			SPDSettings.playAlongJoinCode(joinCode);
		}

		role = Role.CLIENT;
		activeClientSession = session;
		clientJoinPending = true;
		clientUsername = safeName;
		wipeTriggeredOnClient = false;
		localVictorySent = false;
		localDeathSent = false;
		clientHasJoinedOnce = false;
		runStartMs = System.currentTimeMillis();
		GHOST_STORE.setHostPosition(session.hostDepth, 0, session.hostPos);

		GamesInProgress.selectedClass = heroClass;
		GamesInProgress.randomizedClass = false;
		GamesInProgress.pendingHeroName = safeName;
		GamesInProgress.curSlot = GamesInProgress.PLAY_ALONG_SLOT;
		if (Gdx.files != null) {
			Dungeon.deleteGame(GamesInProgress.PLAY_ALONG_SLOT, true);
		}

		Dungeon.hero = null;
		Dungeon.daily = Dungeon.dailyReplay = false;
		Dungeon.seed = session.seed;
		Dungeon.customSeedText = (session.customSeedText != null) ? session.customSeedText : "";
		ActionIndicator.clearAction();

		connectClient(session, heroClass.name(), safeName, joinCode, false);

		if (Game.instance != null) {
			InterlevelScene.mode = InterlevelScene.Mode.DESCEND;
			Game.switchScene(InterlevelScene.class);
		}
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

	private static void connectClient(SessionInfo session, String heroClass, String username,
	                                  String joinCode, boolean isRejoin) {
		final String hostName = session.displayHostName();
		client = new PlayAlongClient(new PlayAlongClient.ClientListener() {
			@Override
			public void onJoinAck(WireMessage msg) {
				if (msg.username != null && !msg.username.isEmpty()) {
					clientUsername = msg.username;
				}
				if (msg.session != null) {
					activeClientSession = msg.session;
				}
				final boolean wasReconnect = clientHasJoinedOnce;
				clientHasJoinedOnce = true;
				final String currentHost = getHostDisplayName();
				Game.runOnRenderThread(() -> {
					if (wasReconnect) {
						GLog.p("Reconnected to %s's Play Along run as %s!", currentHost, clientUsername);
						if (Dungeon.hero != null && Dungeon.level != null && (role == Role.CLIENT || role == Role.SPECTATOR)) {
							sendClientState(role == Role.SPECTATOR ? "DEAD" : "IDLE", Dungeon.hero.pos);
						}
					} else {
						GLog.p("Joined %s's Play Along run as %s!", currentHost, clientUsername);
					}
				});
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
				final String currentHost = getHostDisplayName();
				Game.runOnRenderThread(() -> {
					if (Gdx.audio != null) {
						Sample.INSTANCE.play(Assets.Sounds.TELEPORT);
					}
					if (Game.scene() instanceof GameScene) {
						GameScene.flash(0x60FFFF40);
					}
					GLog.w("✨ %s POPPED AN ANKH! (Run still alive — keep going!)", currentHost);
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
					if (Gdx.audio != null) {
						Sample.INSTANCE.play(Assets.Sounds.LEVELUP);
					}
					GLog.p("🏆 @%s secured the Amulet of Yendor!", msg.username);
				});
			}

			@Override
			public void onMilestoneRewardNotice(WireMessage msg) {
				if (msg == null || msg.pointsAwarded <= 0) return;
				Game.runOnRenderThread(() -> {
					if (Gdx.audio != null) {
						Sample.INSTANCE.play(Assets.Sounds.LEVELUP);
					}
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
				STREAM_BRIDGE.handleIncomingStreamMessage(role, GHOST_STORE, msg);
			}

			@Override
			public void onChatCommand(WireMessage msg) {
				STREAM_BRIDGE.handleIncomingChatCommand(msg, clientUsername, PlayAlongService::getRole);
			}

			@Override
			public void onConnectionLost(boolean permanentlyFailed) {
				if (permanentlyFailed) {
					Game.runOnRenderThread(() -> GLog.w("Disconnected from %s's Play Along lobby.", hostName));
				}
			}
		});

		String ver = Game.version != null ? Game.version : "";
		client.connect(effectiveAddress(), localUserId, username, SPDSettings.streamClientToken(),
				joinCode, heroClass, ver, session.sessionId, Dungeon.depth, Dungeon.branch,
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
			if (isHostAliveForSpectate()
					&& (Dungeon.depth != GHOST_STORE.getHostDepth() || Dungeon.branch != GHOST_STORE.getHostBranch())) {
				followHostFloor();
				return;
			}
		}

		if (role == Role.HOST && hostServer != null) {
			String notice = pendingHostNotice;
			pendingHostNotice = null;
			if (notice != null) {
				GLog.p(notice);
			}
			GhostState hg = buildLocalGhostState("IDLE", Dungeon.hero.pos, true);
			GHOST_STORE.setHostGhost(hg);
			hostServer.updateHostState(hg);
			hostServer.updateHostVitals(Dungeon.hero.HP, Dungeon.hero.HT);
		} else if ((role == Role.CLIENT || role == Role.SPECTATOR) && client != null) {
			sendClientState("IDLE", Dungeon.hero.pos);
			STREAM_BRIDGE.drainPendingChatCommandsIfReady(role);
		}
	}

	public static void onHeroMoved(int from, int to) {
		if (role == Role.NONE || Dungeon.hero == null) return;
		if (role == Role.HOST && hostServer != null) {
			GhostState hg = buildLocalGhostState("MOVE", to, true);
			hg.facingLeft = (to % Dungeon.level.width()) < (from % Dungeon.level.width());
			GHOST_STORE.setHostGhost(hg);
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
		STREAM_BRIDGE.requestStreamEconomy(role, client, effectiveAddress(), GHOST_STORE, callback);
	}

	public static void requestStreamLinkCode(final PlayAlongClient.WireMessageCallback callback) {
		STREAM_BRIDGE.requestStreamLinkCode(role, client, effectiveAddress(), GHOST_STORE, callback);
	}

	public static void sendStreamCommand(final String rawCommand, final PlayAlongClient.WireMessageCallback callback) {
		STREAM_BRIDGE.sendStreamCommand(role, client, effectiveAddress(), GHOST_STORE, rawCommand, callback);
	}

	public static void onAppPause() {
		// Socket stays managed by Java-WebSocket; nothing special required on pause.
	}

	public static void onAppResume() {
		if ((role == Role.CLIENT || role == Role.SPECTATOR) && client != null) {
			client.reconnectIfNeeded(Dungeon.depth, Dungeon.branch, Dungeon.hero != null ? Dungeon.hero.pos : 0);
		}
	}

	// ---- Spirit Cam (Read-Only Spectator) ----

	public static void magicMapCurrentFloor() {
		SPECTATOR.magicMapCurrentFloor();
	}

	public static void enterSpiritCam() {
		if (role != Role.CLIENT || !isHostAliveForSpectate()) return;
		role = Role.SPECTATOR;
		if (client != null) {
			client.setSpectating(true);
		}
		magicMapCurrentFloor();
		if (Dungeon.depth != GHOST_STORE.getHostDepth() || Dungeon.branch != GHOST_STORE.getHostBranch()) {
			followHostFloor();
		} else if (Game.instance != null) {
			ShatteredPixelDungeon.seamlessResetScene();
		}
	}

	public static void followHostFloor() {
		SPECTATOR.followHostFloor(
				role,
				isHostAliveForSpectate(),
				GHOST_STORE.getHostDepth(),
				GHOST_STORE.getHostBranch(),
				GHOST_STORE.getHostPos());
	}

	public static void checkSpectatorFloorFollow() {
		SPECTATOR.checkSpectatorFloorFollow(
				role,
				isHostAliveForSpectate(),
				GHOST_STORE.getHostDepth(),
				GHOST_STORE.getHostBranch(),
				GHOST_STORE.getHostPos());
	}

	public static void spectatorReturnTo(int targetDepth, int targetBranch, int targetPos) {
		SPECTATOR.spectatorReturnTo(targetDepth, targetBranch, targetPos);
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
		clientHasJoinedOnce = false;
		activeClientSession = null;
		wipeTriggeredOnClient = false;
		pendingWipeOnScene = null;
		pendingHostNotice = null;
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
		final HostWipeDoom doom = new HostWipeDoom(hDepth, causeStr, victims, getHostDisplayName());

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
			if (Game.scene() instanceof GameScene) {
				GameScene.flash(0x88FF0000);
			}
			if (Gdx.audio != null) {
				Sample.INSTANCE.play(SPDSettings.moddedBanners() ? Assets.Sounds.DEATH_MOD : Assets.Sounds.DEATH);
			}
			GLog.n("☠ SOUL PACT BROKEN! %s died on Depth %d (%s). %d players wiped!",
					doom.hostName, doom.hostDepth, doom.hostCause, doom.victims);
			return;
		}
		Dungeon.hero.HP = 0;
		if (Dungeon.hero.sprite != null) {
			Dungeon.hero.sprite.die();
		}
		com.shatteredpixel.shatteredpixeldungeon.actors.Actor.fixTime();
		if (Game.scene() instanceof GameScene) {
			GameScene.flash(0x88FF0000);
		}
		Hero.reallyDie(doom);
	}

	private static void applyWorldStateSnapshot(GhostState newHostGhost, List<GhostState> ghosts,
	                                            List<TombstoneState> tombs, int alive,
	                                            Map<Integer, Integer> depths) {
		GHOST_STORE.applyWorldStateSnapshot(newHostGhost, ghosts, tombs, alive, depths);
		if (role == Role.SPECTATOR && isHostAliveForSpectate()
				&& (Dungeon.depth != GHOST_STORE.getHostDepth() || Dungeon.branch != GHOST_STORE.getHostBranch())) {
			Game.runOnRenderThread(PlayAlongService::checkSpectatorFloorFollow);
		}
	}

	private static void resetRuntimeCollections() {
		GHOST_STORE.clear();
		STREAM_BRIDGE.clearPendingChatCommands();
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
		g.username = isHost ? SPDSettings.playAlongHostUsernameSafe() : clientUsername;
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
