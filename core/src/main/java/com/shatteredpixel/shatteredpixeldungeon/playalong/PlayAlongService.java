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
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.RoomListing;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.SessionInfo;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.StreamEconomyState;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.TombstoneState;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.WipeEvent;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.WireMessage;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.TitleScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.ActionIndicator;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndTextInput;
import com.shatteredpixel.shatteredpixeldungeon.utils.DungeonSeed;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.shatteredpixel.shatteredpixeldungeon.utils.StreamingEvents;
import com.watabou.noosa.Game;
import com.watabou.noosa.audio.Sample;
import com.watabou.utils.DeviceCompat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
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
	private static volatile PlayAlongRelayHost relayHost = null;
	private static PlayAlongClient client = null;

	/** Address the current viewer session should use (relay room or direct host:port). */
	private static volatile String clientServerAddress = null;
	/** Address the latest title-screen live check actually reached. */
	private static volatile String discoveredAddress = null;
	private static volatile String lastAnnouncedRoom = null;
	private static volatile long lastMetaPublishMs = 0L;
	private static volatile boolean relayDownAnnounced = false;
	private static volatile boolean connectHintShown = false;
	/** One desync line per outage. Cleared when a rejoin is acknowledged. */
	private static volatile boolean desyncHintShown = false;
	private static volatile boolean chatDrainQueued = false;
	private static final int MAX_PENDING_CHAT = 64;
	private static final int CHAT_APPLY_PER_FRAME = 4;

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
	private static volatile boolean hostAscendingLive = false;
	private static volatile int hostBranch = 0;
	private static volatile int hostPos = 0;
	private static volatile int aliveCount = 0;
	private static volatile int lastWipeCount = -1;
	private static volatile String lastWipeSummary = "";
	private static volatile boolean wipeTriggeredOnClient = false;
	private static volatile HostWipeDoom pendingWipeOnScene = null;
	private static volatile boolean localVictorySent = false;
	private static volatile boolean localDeathSent = false;
	private static volatile String pendingHostNotice = null;
	private static volatile boolean clientHasJoinedOnce = false;
	private static volatile int pendingKillExp = 0;
	private static volatile int lastKillExpDepth = -1;

	private static volatile StreamEconomyState cachedEconomy = null;
	private static final ConcurrentHashMap<String, PlayAlongClient.WireMessageCallback> pendingStreamRequests = new ConcurrentHashMap<>();
	private static final CopyOnWriteArrayList<ChatCommandPayload> pendingChatCommands = new CopyOnWriteArrayList<>();
	private static volatile boolean sessionMigrationPromptOpen = false;
	private static volatile boolean sessionMigrationHandled = false;
	private static volatile SessionInfo pendingTitleJoinSession = null;

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

	/** Host is currently climbing out. Updated from the session snapshot and from world state. */
	public static boolean isHostAscending() {
		return hostAscendingLive;
	}

	public static void noteHostAscent(boolean amuletObtained, boolean ascending, int highestAscent) {
		hostAscendingLive = ascending;
		SessionInfo session = activeClientSession;
		if (session == null) return;
		session.amuletObtained = amuletObtained;
		session.ascending = ascending;
		session.highestAscent = highestAscent;
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
		return info != null && (info.active || info.inDungeon);
	}

	public static boolean isPlayAlongRunLive() {
		SessionInfo info = liveSessionInfo;
		return info != null && info.active;
	}

	public static StreamEconomyState getCachedEconomy() {
		return cachedEconomy;
	}

	private static String effectiveAddress() {
		// The machine that is hosting talks to its own server for stream commands.
		if (canHostPlayAlong() && hostServer != null
				&& role != Role.CLIENT && role != Role.SPECTATOR && !clientJoinPending) {
			return "127.0.0.1:" + SPDSettings.playAlongPort();
		}
		if (clientServerAddress != null && !clientServerAddress.isEmpty()
				&& (role == Role.CLIENT || role == Role.SPECTATOR || clientJoinPending)) {
			return clientServerAddress;
		}
		if (discoveredAddress != null && !discoveredAddress.isEmpty()) {
			return discoveredAddress;
		}
		// Public drop-in. Direct host:port is only used when a live check fell back to it.
		return PlayAlongProtocol.daltonJoinAddress();
	}

	/** Address captured for the join window that is about to open. */
	public static String peekJoinAddress() {
		if (clientServerAddress != null && !clientServerAddress.isEmpty()) return clientServerAddress;
		if (discoveredAddress != null && !discoveredAddress.isEmpty()) return discoveredAddress;
		return PlayAlongProtocol.daltonJoinAddress();
	}

	public static void useJoinAddress(String address) {
		if (address != null && !address.isEmpty()) {
			clientServerAddress = address;
		}
	}

	/** Title-screen drop-in. Uses whichever server the live check reached. */
	public static void armDiscoveredJoin() {
		if (discoveredAddress != null && !discoveredAddress.isEmpty()) {
			clientServerAddress = discoveredAddress;
		} else {
			clientServerAddress = PlayAlongProtocol.daltonJoinAddress();
		}
	}

	public static String relayRoomCode() {
		PlayAlongRelayHost host = relayHost;
		return host == null ? null : host.roomCode();
	}

	public static boolean relayRoomIsPublic() {
		return PlayAlongProtocol.isPublicJoinTarget(relayRoomCode());
	}

	/**
	 * Host-authoritative stream economy. True only when this process holds the
	 * reserved {@code dalton} room via the relay — not based on client claims.
	 */
	public static boolean hostHoldsStreamRoom() {
		return PlayAlongProtocol.isReservedRoom(relayRoomCode());
	}

	/** UX helper: stream commands / points UI only for dalton sessions. */
	public static boolean streamFeaturesVisible() {
		if (role == Role.HOST) {
			return hostHoldsStreamRoom();
		}
		String join = clientServerAddress;
		if (join == null || join.isEmpty()) join = discoveredAddress;
		if (join == null || join.isEmpty()) join = peekJoinAddress();
		return isDaltonJoinAddress(join);
	}

	private static boolean isDaltonJoinAddress(String addr) {
		if (addr == null || addr.isEmpty()) return false;
		String lower = addr.toLowerCase(Locale.ROOT);
		int mark = lower.indexOf("/join/");
		if (mark >= 0) {
			String room = addr.substring(mark + "/join/".length());
			int cut = room.indexOf('?');
			if (cut >= 0) room = room.substring(0, cut);
			cut = room.indexOf('/');
			if (cut >= 0) room = room.substring(0, cut);
			return PlayAlongProtocol.isReservedRoom(room);
		}
		return lower.contains("/join/" + SPDSettings.PLAY_ALONG_RELAY_ROOM);
	}

	public static String hostConnectionSummary() {
		String direct = SPDSettings.playAlongAddress();
		if (!canHostPlayAlong() || !SPDSettings.playAlongRelayHost()) {
			return Messages.get(PlayAlongService.class, "roster_off", direct);
		}
		String code = relayRoomCode();
		if (code == null || code.isEmpty()) {
			return Messages.get(PlayAlongService.class, "roster_wait", direct);
		}
		if (PlayAlongProtocol.isReservedRoom(code)) {
			return Messages.get(PlayAlongService.class, "roster_public", code, direct);
		}
		if (PlayAlongProtocol.isPublicRoomName(code)) {
			return Messages.get(PlayAlongService.class, "roster_named", code, direct);
		}
		return Messages.get(PlayAlongService.class, "roster_code", code, direct);
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

	/** Desktop or Android may host; Android is foreground/outbound-relay only. */
	public static boolean canHostPlayAlong() {
		return DeviceCompat.isDesktop() || DeviceCompat.isAndroid();
	}

	// ---- Host Server Lifecycle (Desktop + Android) ----

	public static synchronized void ensureHostServerStarted() {
		if (!canHostPlayAlong()) return;
		if (hostServer != null) {
			if (SPDSettings.playAlongRelayHost()) startRelay();
			return;
		}
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
		if (hostServer != null && SPDSettings.playAlongRelayHost()) {
			startRelay();
		}
	}

	public static synchronized void stopHostServer() {
		if (hostServer != null) {
			hostServer.endSession();
		}
		stopRelay();
		if (hostServer != null) {
			hostServer.stopServer();
			hostServer = null;
		}
	}

	public static synchronized void onRelaySettingChanged(boolean enabled) {
		if (!canHostPlayAlong()) return;
		if (enabled) {
			ensureHostServerStarted();
		} else {
			stopRelay();
		}
	}

	/** Relay URL or Dalton room token changed. Re-dial so the new values apply. */
	public static synchronized void onRelayEndpointChanged() {
		if (!canHostPlayAlong()) return;
		boolean restart = SPDSettings.playAlongRelayHost() && hostServer != null;
		stopRelay();
		if (restart) startRelay();
	}

	/**
	 * Android hosting is foreground-only. Pause drops the relay room into the
	 * reclaim window; resume redials if hosting is still enabled.
	 */
	public static synchronized void onAndroidLifecyclePause() {
		if (!DeviceCompat.isAndroid()) return;
		stopRelay();
	}

	public static synchronized void onAndroidLifecycleResume() {
		if (!DeviceCompat.isAndroid()) return;
		if (SPDSettings.playAlongHostEnabled() && SPDSettings.playAlongRelayHost()) {
			ensureHostServerStarted();
		}
	}

	private static void startRelay() {
		if (relayHost != null || hostServer == null || !SPDSettings.playAlongRelayHost()) return;
		final PlayAlongServer server = hostServer;
		PlayAlongRelayHost host = new PlayAlongRelayHost(server, new PlayAlongRelayHost.Listener() {
			@Override
			public void onRoomReady(String code) {
				relayDownAnnounced = false;
				Game.runOnRenderThread(() -> announceRoom(code));
			}

			@Override
			public void onRelayDown() {
				Game.runOnRenderThread(() -> announceRelayDown());
			}

			@Override
			public void onRelayDenied() {
				Game.runOnRenderThread(() -> {
					lastAnnouncedRoom = null;
					GLog.w(Messages.get(PlayAlongService.class, "relay_denied"));
					System.out.println("[PlayAlong] Reserved room token was rejected.");
				});
			}
		});
		relayHost = host;
		if (SPDSettings.playAlongRelayPublicNameMissingSecret()) {
			System.out.println("[PlayAlong] Public room name needs a host secret.");
			GLog.w(Messages.get(PlayAlongService.class, "room_name_needs_secret"));
		}
		String publicRoom = SPDSettings.playAlongRelayHostRoomOrNull();
		System.out.println("[PlayAlong] Dialing relay "
				+ (publicRoom == null
				? PlayAlongProtocol.relayHostAddress(null)
				: PlayAlongProtocol.relayHostAddress(publicRoom))
				+ (publicRoom == null ? " (private room code)" : " (public room " + publicRoom + ")"));
		host.start();
	}

	private static void stopRelay() {
		PlayAlongRelayHost host = relayHost;
		relayHost = null;
		lastAnnouncedRoom = null;
		if (host != null) host.stop();
	}

	private static void announceRoom(String code) {
		if (code == null || code.isEmpty() || code.equals(lastAnnouncedRoom)) return;
		lastAnnouncedRoom = code;
		if (PlayAlongProtocol.isReservedRoom(code)) {
			GLog.p(Messages.get(PlayAlongService.class, "room_ready_public", code));
			System.out.println("[PlayAlong] Public relay room '" + code + "' is live.");
		} else if (PlayAlongProtocol.isPublicRoomName(code)) {
			GLog.p(Messages.get(PlayAlongService.class, "room_ready_named", code));
			System.out.println("[PlayAlong] Public relay room '" + code + "' is live.");
		} else {
			GLog.p(Messages.get(PlayAlongService.class, "room_ready", code));
			System.out.println("[PlayAlong] Relay room code " + code
					+ ". Set a public room name + host secret to claim a named room.");
		}
		publishRelayMeta(true);
	}

	private static void announceRelayDown() {
		lastAnnouncedRoom = null;
		if (relayDownAnnounced) return;
		relayDownAnnounced = true;
		GLog.w(Messages.get(PlayAlongService.class, "relay_down"));
		System.out.println("[PlayAlong] Relay unavailable. Direct host:port still works.");
	}

	/**
	 * Publish lobby listing fields to the relay. Only public/named/reserved rooms
	 * appear in {@code GET /rooms}; private codes are never listed.
	 */
	public static void publishRelayMeta() {
		publishRelayMeta(false);
	}

	/** Browser / join-facing host label (never empty). */
	public static String hostDisplayNameForSession() {
		if (hostHoldsStreamRoom()
				|| PlayAlongProtocol.isReservedRoom(SPDSettings.playAlongRelayHostRoomOrNull())
				|| PlayAlongProtocol.isReservedRoom(relayRoomCode())) {
			return "Dalton";
		}
		String n = SPDSettings.playAlongUsername();
		if (n == null || n.trim().isEmpty()) return "Host";
		return PlayAlongProtocol.sanitizeUsername(n, "01");
	}

	/** Persist display name, refresh relay meta + live session field when hosting. */
	public static void setHostDisplayName(String raw) {
		String safe = PlayAlongProtocol.sanitizeUsername(raw, "01");
		SPDSettings.playAlongUsername(safe);
		publishRelayMeta(true);
		if (hostServer != null) {
			SessionInfo info = hostServer.getActiveSession();
			if (info != null) {
				info.hostDisplayName = hostDisplayNameForSession();
			}
		}
	}

	public interface LinkedUsernameCallback {
		void accept(String linkedUsernameOrNull);
	}

	/**
	 * Resolve Twitch/YouTube linked name via the dalton stream control path
	 * (not the local host socket). Callback always on the render thread.
	 */
	public static void requestLinkedUsername(LinkedUsernameCallback callback) {
		if (callback == null) return;
		StreamEconomyState cached = cachedEconomy;
		if (cached != null && cached.linked && cached.linkedUsername != null
				&& !cached.linkedUsername.trim().isEmpty()) {
			String linked = PlayAlongProtocol.sanitizeUsername(cached.linkedUsername, "01");
			Game.runOnRenderThread(() -> callback.accept(linked));
			return;
		}
		WireMessage req = new WireMessage();
		req.type = "stream_state_req";
		req.requestId = UUID.randomUUID().toString();
		req.clientToken = SPDSettings.streamClientToken();
		PlayAlongClient.sendControlRequest(PlayAlongProtocol.daltonJoinAddress(), req, 4000, msg -> {
			StreamEconomyState eco = (msg != null) ? msg.economy : null;
			if (eco != null) applyEconomyUpdate(eco);
			String linked = null;
			if (eco != null && eco.linked && eco.linkedUsername != null
					&& !eco.linkedUsername.trim().isEmpty()) {
				linked = PlayAlongProtocol.sanitizeUsername(eco.linkedUsername, "01");
			}
			final String out = linked;
			Game.runOnRenderThread(() -> callback.accept(out));
		});
	}

	public static void publishRelayMeta(boolean force) {
		PlayAlongRelayHost host = relayHost;
		if (host == null || !host.isUp()) return;
		long now = System.currentTimeMillis();
		if (!force && now - lastMetaPublishMs < 5_000L) return;
		lastMetaPublishMs = now;
		Map<String, Object> meta = new LinkedHashMap<>();
		String display = hostDisplayNameForSession();
		meta.put("displayName", display);
		meta.put("version", Game.version == null ? "" : Game.version);
		meta.put("protocolVersion", PlayAlongProtocol.PROTOCOL_VERSION);
		boolean inDungeon = Dungeon.hero != null && Dungeon.level != null;
		meta.put("inDungeon", inDungeon);
		if (inDungeon) {
			meta.put("hostDepth", Dungeon.depth);
			meta.put("challenges", Dungeon.challenges);
			meta.put("modifiers", Dungeon.modifiers);
		} else if (hostServer != null) {
			SessionInfo info = hostServer.getActiveSession();
			if (info != null) {
				meta.put("hostDepth", info.hostDepth);
				meta.put("inDungeon", info.inDungeon);
				meta.put("challenges", info.challenges);
				meta.put("modifiers", info.modifiers);
			}
		}
		host.sendMeta(meta);
	}

	public interface RoomsCallback {
		void accept(List<RoomListing> rooms);
	}

	/** Background fetch of {@code GET /rooms}. Callback always on the render thread. */
	public static void fetchPublicRooms(RoomsCallback callback) {
		if (callback == null) return;
		Thread t = new Thread(() -> {
			List<RoomListing> rooms = fetchPublicRoomsSync();
			Game.runOnRenderThread(() -> callback.accept(rooms));
		}, "PlayAlongRooms");
		t.setDaemon(true);
		t.start();
	}

	private static List<RoomListing> fetchPublicRoomsSync() {
		HttpURLConnection conn = null;
		try {
			URL url = new URL(PlayAlongProtocol.relayRoomsUrl());
			conn = (HttpURLConnection) url.openConnection();
			conn.setConnectTimeout(4000);
			conn.setReadTimeout(4000);
			conn.setRequestMethod("GET");
			conn.setRequestProperty("Accept", "application/json");
			int code = conn.getResponseCode();
			if (code != 200) return new ArrayList<>();
			StringBuilder sb = new StringBuilder();
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					sb.append(line);
				}
			}
			RoomListing[] parsed = PlayAlongProtocol.GSON.fromJson(sb.toString(), RoomListing[].class);
			if (parsed == null) return new ArrayList<>();
			return new ArrayList<>(Arrays.asList(parsed));
		} catch (Exception e) {
			return new ArrayList<>();
		} finally {
			if (conn != null) conn.disconnect();
		}
	}

	/** Enable relay hosting from the community lobby (desktop + Android). */
	public static void enableCommunityHostFromLobby() {
		startHostFromLobby(true, null);
	}

	/**
	 * Claim a private or public relay room, then the caller jumps to hero select.
	 * @param privateRoom true clears the public room name so the relay assigns a code
	 * @param publicNameOrNull normalized public slug when not private
	 */
	public static void startHostFromLobby(boolean privateRoom, String publicNameOrNull) {
		if (!canHostPlayAlong()) {
			GLog.w(Messages.get(PlayAlongService.class, "host_desktop_only"));
			return;
		}
		if (privateRoom) {
			SPDSettings.playAlongRelayRoomName("");
		} else {
			String name = PlayAlongProtocol.normalizeHostRoomName(publicNameOrNull);
			if (name.isEmpty() || PlayAlongProtocol.isReservedRoom(name)
					|| !PlayAlongProtocol.isPublicRoomName(name)) {
				GLog.w(Messages.get(PlayAlongService.class, "host_public_name_bad"));
				return;
			}
			if (SPDSettings.playAlongRelayToken().isEmpty()) {
				GLog.w(Messages.get(PlayAlongService.class, "room_name_needs_secret"));
				return;
			}
			SPDSettings.playAlongRelayRoomName(name);
		}
		SPDSettings.playAlongHostEnabled(true);
		SPDSettings.playAlongRelayHost(true);
		lastAnnouncedRoom = null;
		ensureHostServerStarted();
		onRelayEndpointChanged();
		onHostSettingChanged(true);
		GLog.p(Messages.get(PlayAlongService.class, "host_lobby_starting"));
	}

	/** Stop hosting from the lobby panel. */
	public static void stopCommunityHostFromLobby() {
		SPDSettings.playAlongHostEnabled(false);
		onHostSettingChanged(false);
		stopRelay();
		GLog.w(Messages.get(PlayAlongService.class, "host_lobby_stopped"));
	}

	/** Copy the current relay join URL (or wait message) to the clipboard. */
	public static void copyCurrentHostJoinLink() {
		String code = relayRoomCode();
		if (code == null || code.isEmpty()) {
			GLog.w(Messages.get(PlayAlongService.class, "host_lobby_copy_wait"));
			return;
		}
		String url = PlayAlongProtocol.relayJoinAddress(code);
		try {
			if (com.badlogic.gdx.Gdx.app != null && com.badlogic.gdx.Gdx.app.getClipboard() != null) {
				com.badlogic.gdx.Gdx.app.getClipboard().setContents(url);
			}
		} catch (Exception ignored) {
		}
		GLog.p(Messages.get(PlayAlongService.class, "code_copied", url));
	}

	/** Join a listed or typed room after a successful status ping. */
	public static void openJoinLobbyForRoom(String roomCode) {
		if (roomCode == null || roomCode.isEmpty()) return;
		SPDSettings.playAlongLastRoom(roomCode);
		final String url = PlayAlongProtocol.relayJoinAddress(roomCode);
		PlayAlongClient.pingStatus(url, info -> Game.runOnRenderThread(() -> {
			if (info == null || (!info.active && !info.inDungeon)) {
				GLog.w(Messages.get(PlayAlongService.class, "code_offline"));
				return;
			}
			clientServerAddress = url;
			discoveredAddress = url;
			liveSessionInfo = info;
			if (Game.scene() != null) {
				Game.scene().addToFront(new WndPlayAlongJoin(info));
			}
		}));
	}

	// ---- Title Screen Live Status Polling ----

	public static void pollLiveStatus(boolean force) {
		long now = System.currentTimeMillis();
		if (!force && (statusPolling || (now - lastPollTimeMs < 6000L))) return;
		statusPolling = true;
		lastPollTimeMs = now;
		// The host's own title button watches the local server. Everyone else
		// tries the public relay room first, then the direct host:port fallback.
		if (canHostPlayAlong() && hostServer != null) {
			PlayAlongClient.pingStatus("127.0.0.1:" + SPDSettings.playAlongPort(), info -> {
				statusPolling = false;
				applyPolledSession(info, null, false);
			});
			return;
		}
		final String relay = PlayAlongProtocol.daltonJoinAddress();
		PlayAlongClient.pingStatus(relay, info -> {
			if (info != null && (info.active || info.inDungeon)) {
				statusPolling = false;
				applyPolledSession(info, relay, true);
				return;
			}
			PlayAlongClient.pingStatus(SPDSettings.playAlongAddress(), direct -> {
				statusPolling = false;
				if (direct != null && (direct.active || direct.inDungeon)) {
					applyPolledSession(direct, SPDSettings.playAlongAddress(), true);
				} else {
					applyPolledSession(null, null, true);
				}
			});
		});
	}

	private static void applyPolledSession(SessionInfo info, String address, boolean updateDiscovery) {
		if (info != null && (info.active || info.inDungeon)) {
			liveSessionInfo = info;
			if (updateDiscovery && address != null) {
				discoveredAddress = address;
			}
			if (role == Role.NONE && info.inDungeon) {
				hostDepth = info.hostDepth;
				hostPos = info.hostPos;
			}
		} else {
			liveSessionInfo = null;
			if (updateDiscovery && role == Role.NONE) {
				discoveredAddress = null;
			}
		}
	}

	// ---- Starting & Joining Runs ----

	public static void onHostRunStarted() {
		PlayAlongCatchup.onNewHostRun();
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
		if (!canHostPlayAlong()) return;
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
		if (!canHostPlayAlong() || hostServer == null
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
		if (!canHostPlayAlong() || isClientJoining()
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
				hostGhost = hg;
				hostDepth = Dungeon.depth;
				hostBranch = Dungeon.branch;
				hostPos = Dungeon.hero.pos;

				WireMessage world = hostServer.resumeSession(
						hg,
						Dungeon.hero.HP,
						Dungeon.hero.HT,
						Dungeon.hero.heroClass != null ? Dungeon.hero.heroClass.name() : "WARRIOR");
				if (world != null) {
					applyWorldStateSnapshot(hg, world.ghosts, world.tombstones, world.activePlayers, world.depthCounts);
					pendingHostNotice = world.activePlayers > 0
							? ("Play Along resumed (" + world.activePlayers
							+ (world.activePlayers == 1 ? " racer" : " racers") + " still connected)!")
							: "Play Along lobby resumed!";
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
		}

		SessionInfo info = new SessionInfo();
		info.active = true;
		info.sessionId = sessionId;
		info.version = Game.version != null ? Game.version : "";
		info.seed = Dungeon.seed;
		info.customSeedText = Dungeon.customSeedText != null ? Dungeon.customSeedText : "";
		info.challenges = Dungeon.challenges;
		info.modifiers = Dungeon.modifiers;
		info.hostClass = Dungeon.hero.heroClass != null ? Dungeon.hero.heroClass.name() : "WARRIOR";
		info.hostDisplayName = hostDisplayNameForSession();
		info.hostDepth = Dungeon.depth;
		info.hostPos = Dungeon.hero.pos;
		info.hostHp = Dungeon.hero.HP;
		info.hostHt = Dungeon.hero.HT;

		GhostState hg = buildLocalGhostState("IDLE", Dungeon.hero.pos, true);
		hostGhost = hg;
		hostDepth = Dungeon.depth;
		hostBranch = Dungeon.branch;
		hostPos = Dungeon.hero.pos;
		hostServer.startSession(info, hg);

		if (persistIdIfNew && stampedNewId
				&& GamesInProgress.curSlot > 0 && GamesInProgress.curSlot != GamesInProgress.PLAY_ALONG_SLOT) {
			try {
				Dungeon.saveGame(GamesInProgress.curSlot);
			} catch (Throwable ignored) {
			}
		}

		if (isLoadedSave) {
			pendingHostNotice = !savedSid.isEmpty()
					? "Play Along lobby restored for this save (racers can reconnect)!"
					: "Play Along lobby is now LIVE for this run!";
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
		if (session == null || !session.active) return;

		String joinAddr = clientServerAddress;
		if (joinAddr == null || joinAddr.isEmpty()) joinAddr = discoveredAddress;
		if (joinAddr == null || joinAddr.isEmpty()) joinAddr = PlayAlongProtocol.daltonJoinAddress();

		parkCurrentClientProgress();
		disconnect();
		clientServerAddress = joinAddr;
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
		clientHasJoinedOnce = false;
		sessionMigrationHandled = true;
		discardPendingKillExp();
		runStartMs = System.currentTimeMillis();
		hostDepth = session.hostDepth;
		hostPos = session.hostPos;
		noteHostAscent(session.amuletObtained, session.ascending, session.highestAscent);

		int slot = PlayAlongArchives.allocateForNewRun(session.sessionId);
		GamesInProgress.selectedClass = heroClass;
		GamesInProgress.randomizedClass = false;
		GamesInProgress.pendingHeroName = safeName;
		GamesInProgress.curSlot = slot;
		Dungeon.deleteGame(slot, true);
		PlayAlongArchives.remember(session.sessionId, slot);
		PlayAlongCatchup.prepareNewRacer(session);

		Dungeon.hero = null;
		Dungeon.daily = Dungeon.dailyReplay = false;
		Dungeon.seed = session.seed;
		Dungeon.customSeedText = (session.customSeedText != null) ? session.customSeedText : "";
		Dungeon.playAlongSessionId = session.sessionId != null ? session.sessionId : "";
		ActionIndicator.clearAction();

		connectClient(session, heroClass.name(), safeName, false);

		InterlevelScene.mode = InterlevelScene.Mode.DESCEND;
		Game.switchScene(InterlevelScene.class);
	}

	/** Resume a parked Play Along save for the host's current sessionId. */
	public static void continueClientRun(SessionInfo session) {
		if (session == null || !session.active) return;
		int slot = PlayAlongArchives.findSlot(session.sessionId);
		if (slot <= 0 || !GamesInProgress.gameExists(slot)) {
			GLog.w("No saved Play Along race for this run — pick a class to join.");
			beginJoinAfterMigration(session);
			return;
		}

		parkCurrentClientProgress();
		if (client != null) {
			client.close();
			client = null;
		}
		resetRuntimeCollections();

		GamesInProgress.Info info = GamesInProgress.check(slot);
		String safeName = SPDSettings.playAlongUsername();
		if (safeName == null || safeName.isEmpty()) {
			safeName = "Chatter";
		}
		String heroClassName = (info != null && info.heroClass != null)
				? info.heroClass.name() : HeroClass.WARRIOR.name();

		role = Role.CLIENT;
		activeClientSession = session;
		clientJoinPending = false;
		clientUsername = safeName;
		wipeTriggeredOnClient = false;
		localVictorySent = false;
		localDeathSent = false;
		clientHasJoinedOnce = false;
		sessionMigrationHandled = true;
		discardPendingKillExp();
		runStartMs = System.currentTimeMillis();
		hostDepth = session.hostDepth;
		hostPos = session.hostPos;
		noteHostAscent(session.amuletObtained, session.ascending, session.highestAscent);
		if (clientServerAddress == null || clientServerAddress.isEmpty()) {
			clientServerAddress = discoveredAddress != null && !discoveredAddress.isEmpty()
					? discoveredAddress
					: PlayAlongProtocol.daltonJoinAddress();
		}

		GamesInProgress.curSlot = slot;
		PlayAlongArchives.touch(session.sessionId);
		PlayAlongCatchup.clearPending();
		ActionIndicator.clearAction();

		int depth = info != null ? Math.max(1, info.depth) : 1;
		connectClientAt(session, heroClassName, safeName, true, depth, 0, 0);

		GLog.p("Resuming your Play Along race on Dalton's save!");
		InterlevelScene.mode = InterlevelScene.Mode.CONTINUE;
		Game.switchScene(InterlevelScene.class);
	}

	public static void clearSessionMigrationPrompt() {
		sessionMigrationPromptOpen = false;
	}

	/** Decline following the host: disconnect and return to title (progress stays archived). */
	public static void declineSessionMigration() {
		sessionMigrationHandled = true;
		sessionMigrationPromptOpen = false;
		pendingTitleJoinSession = null;
		parkCurrentClientProgress();
		disconnect();
		Game.switchScene(TitleScene.class);
		GLog.w("Left Play Along. Rejoin from the title screen when ready.");
	}

	/** Leave the stale race and open class select on the title screen. */
	public static void beginJoinAfterMigration(SessionInfo session) {
		sessionMigrationHandled = true;
		sessionMigrationPromptOpen = false;
		parkCurrentClientProgress();
		if (client != null) {
			client.close();
			client = null;
		}
		role = Role.NONE;
		clientJoinPending = false;
		activeClientSession = null;
		resetRuntimeCollections();
		pendingTitleJoinSession = session;
		liveSessionInfo = session;
		Game.switchScene(TitleScene.class);
	}

	/** Called from TitleScene after a forced Join migration. */
	public static void consumePendingTitleJoin() {
		SessionInfo pending = pendingTitleJoinSession;
		pendingTitleJoinSession = null;
		if (pending == null || !pending.active) return;
		if (Game.scene() != null) {
			Game.scene().addToFront(new WndPlayAlongJoin(pending));
		}
	}

	private static void parkCurrentClientProgress() {
		if (role != Role.CLIENT && role != Role.SPECTATOR) return;
		SessionInfo cur = activeClientSession;
		if (cur == null || cur.sessionId == null || cur.sessionId.trim().isEmpty()) return;
		int slot = GamesInProgress.curSlot;
		if (!GamesInProgress.isPlayAlongSlot(slot)) return;
		try {
			if (Dungeon.hero != null && Dungeon.level != null
					&& (Dungeon.hero.isAlive()
					|| com.shatteredpixel.shatteredpixeldungeon.windows.WndResurrect.instance != null)) {
				Dungeon.playAlongSessionId = cur.sessionId.trim();
				Dungeon.saveAll();
			}
		} catch (Throwable ignored) {
		}
		PlayAlongArchives.remember(cur.sessionId.trim(), slot);
	}

	private static void handleHostSessionChanged(SessionInfo newSession) {
		Game.runOnRenderThread(() -> {
			if (sessionMigrationPromptOpen) return;

			boolean inPlayAlong = role == Role.CLIENT || role == Role.SPECTATOR
					|| (GamesInProgress.isPlayAlongSlot(GamesInProgress.curSlot)
					&& Dungeon.hero != null
					&& Game.scene() instanceof GameScene);
			if (!inPlayAlong) return;

			parkCurrentClientProgress();
			if (client != null) {
				client.close();
				client = null;
			}

			sessionMigrationHandled = false;

			if (newSession != null && newSession.active) {
				promptSessionMigration(newSession);
				return;
			}

			// Close reason / join_error fallback: discover the live session via status ping.
			PlayAlongClient.pingStatus(effectiveAddress(), info -> Game.runOnRenderThread(() -> {
				if (info != null && info.active) {
					promptSessionMigration(info);
				} else {
					declineSessionMigration();
				}
			}));
		});
	}

	private static void promptSessionMigration(SessionInfo session) {
		if (session == null || !session.active) {
			declineSessionMigration();
			return;
		}
		if (sessionMigrationPromptOpen) return;
		sessionMigrationPromptOpen = true;
		liveSessionInfo = session;

		String sid = session.sessionId != null ? session.sessionId.trim() : "";
		boolean canResume = PlayAlongArchives.hasArchive(sid);
		GLog.w(canResume
				? "Dalton switched saves — Resume or Leave?"
				: "Dalton started a new run — Join or Leave?");
		if (Game.scene() != null) {
			Game.scene().addToFront(new WndPlayAlongSessionSwitch(session, canResume));
		} else {
			sessionMigrationPromptOpen = false;
			declineSessionMigration();
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
		Dungeon.playAlongSessionId = s.sessionId != null ? s.sessionId : "";
		if (GamesInProgress.isPlayAlongSlot(GamesInProgress.curSlot) && s.sessionId != null) {
			PlayAlongArchives.remember(s.sessionId, GamesInProgress.curSlot);
		}
	}

	private static void connectClient(SessionInfo session, String heroClass, String username, boolean isRejoin) {
		connectClientAt(session, heroClass, username, isRejoin,
				Dungeon.depth, Dungeon.branch, Dungeon.hero != null ? Dungeon.hero.pos : 0);
	}

	private static void connectClientAt(SessionInfo session, String heroClass, String username,
	                                    boolean isRejoin, int depth, int branch, int pos) {
		connectHintShown = false;
		if (clientServerAddress == null || clientServerAddress.isEmpty()) {
			clientServerAddress = discoveredAddress != null && !discoveredAddress.isEmpty()
					? discoveredAddress
					: PlayAlongProtocol.daltonJoinAddress();
		}
		client = new PlayAlongClient(new PlayAlongClient.ClientListener() {
			@Override
			public void onJoinAck(WireMessage msg) {
				if (msg.username != null && !msg.username.isEmpty()) {
					clientUsername = msg.username;
				}
				if (msg.session != null) {
					activeClientSession = msg.session;
					noteHostAscent(msg.session.amuletObtained, msg.session.ascending, msg.session.highestAscent);
				}
				final boolean wasReconnect = clientHasJoinedOnce;
				clientHasJoinedOnce = true;
				desyncHintShown = false;
				Game.runOnRenderThread(() -> {
					if (wasReconnect) {
						GLog.p("Reconnected to Dalton's Play Along run as %s!", clientUsername);
						if (Dungeon.hero != null && Dungeon.level != null && (role == Role.CLIENT || role == Role.SPECTATOR)) {
							sendClientState(role == Role.SPECTATOR ? "DEAD" : "IDLE", Dungeon.hero.pos);
						}
					} else {
						GLog.p("Joined Dalton's Play Along run as %s!", clientUsername);
					}
				});
			}

			@Override
			public void onJoinError(String error) {
				final String err = error != null ? error : "Could not join Play Along run.";
				Game.runOnRenderThread(() -> {
					if (err.contains("different Play Along")) {
						handleHostSessionChanged(null);
						return;
					}
					GLog.n(err);
					disconnect();
				});
			}

			@Override
			public void onWorldState(WireMessage msg) {
				noteHostAscent(msg.amuletObtained, msg.ascending, msg.highestAscent);
				applyWorldStateSnapshot(msg.hostGhost, msg.ghosts, msg.tombstones, msg.activePlayers, msg.depthCounts);
			}

			@Override
			public void onSessionChanged(SessionInfo session) {
				handleHostSessionChanged(session);
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
				PlayAlongCatchup.onClientWiped();
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
			public void onKillExpRewardNotice(WireMessage msg) {
				if (msg == null || msg.pointsAwarded <= 0) return;
				Game.runOnRenderThread(() -> {
					Sample.INSTANCE.play(Assets.Sounds.LEVELUP);
					GLog.p("✨ Kill EXP Reward: +%d Donor pts (D%d, %d EXP)! Balance: %d pts",
							msg.pointsAwarded,
							msg.depth,
							msg.killExp,
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
				if (msg == null) return;
				final List<ChatCommandPayload> incoming = new ArrayList<>();
				if (msg.chatCommands != null) {
					for (ChatCommandPayload c : msg.chatCommands) {
						if (c != null) incoming.add(c);
					}
				}
				if (msg.chatCommand != null) incoming.add(msg.chatCommand);
				if (incoming.isEmpty()) return;
				Game.runOnRenderThread(() -> enqueueMirroredChat(incoming));
			}

			@Override
			public void onConnectionLost(boolean permanentlyFailed, int closeCode, String closeReason, boolean everOpened) {
				PlayAlongProtocol.CloseCopy copy = PlayAlongProtocol.closeCopy(everOpened, permanentlyFailed);
				if (copy == PlayAlongProtocol.CloseCopy.NETWORK_BLOCKED
						|| copy == PlayAlongProtocol.CloseCopy.NETWORK_LOST) {
					if (permanentlyFailed || !connectHintShown) {
						connectHintShown = true;
						final boolean permanent = permanentlyFailed;
						Game.runOnRenderThread(() -> GLog.w(connectionFailureMessage(permanent, false, closeCode, closeReason)));
					}
					return;
				}
				if (permanentlyFailed || !desyncHintShown) {
					desyncHintShown = true;
					final boolean permanent = permanentlyFailed;
					final int code = closeCode;
					final String reason = closeReason;
					Game.runOnRenderThread(() -> GLog.w(connectionFailureMessage(permanent, true, code, reason)));
				}
			}
		});

		String ver = Game.version != null ? Game.version : "";
		int heroLvl = Dungeon.hero != null ? Math.max(1, Dungeon.hero.lvl) : 1;
		client.connect(effectiveAddress(), localUserId, username, SPDSettings.streamClientToken(),
				heroClass, heroLvl, ver, session.sessionId, depth, branch, pos, isRejoin);
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
			String notice = pendingHostNotice;
			pendingHostNotice = null;
			if (notice != null) {
				GLog.p(notice);
			}
			GhostState hg = buildLocalGhostState("IDLE", Dungeon.hero.pos, true);
			hostGhost = hg;
			hostDepth = Dungeon.depth;
			hostBranch = Dungeon.branch;
			hostPos = Dungeon.hero.pos;
			hostServer.updateHostState(hg);
			hostServer.updateHostVitals(Dungeon.hero.HP, Dungeon.hero.HT);
			publishRelayMeta();
		} else if ((role == Role.CLIENT || role == Role.SPECTATOR) && client != null) {
			if (role == Role.CLIENT) {
				maybeFlushKillExpOnDepthChange();
			}
			sendClientState("IDLE", Dungeon.hero.pos);
			if (role == Role.CLIENT && Dungeon.level != null && Dungeon.hero.isAlive()
					&& (Game.scene() instanceof GameScene) && !pendingChatCommands.isEmpty()) {
				scheduleChatDrain();
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
		} else if (role == Role.CLIENT) {
			discardPendingKillExp();
			if (client != null && !(cause instanceof HostWipeDoom) && !localDeathSent) {
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
	}

	public static void onKillExp(int exp) {
		if (role != Role.CLIENT || exp <= 0) return;
		if (PlayAlongCatchup.blocksKillExpReward()) return;
		if (lastKillExpDepth < 0) {
			lastKillExpDepth = Dungeon.depth;
		}
		pendingKillExp += exp;
	}

	private static void maybeFlushKillExpOnDepthChange() {
		int cur = Dungeon.depth;
		PlayAlongCatchup.noteDepth(cur);
		if (lastKillExpDepth < 0) {
			lastKillExpDepth = cur;
			return;
		}
		if (cur == lastKillExpDepth) return;
		flushPendingKillExp(lastKillExpDepth);
		lastKillExpDepth = cur;
	}

	private static void flushPendingKillExp(int cashedDepth) {
		int exp = pendingKillExp;
		pendingKillExp = 0;
		if (exp <= 0 || role != Role.CLIENT || client == null) return;
		WireMessage msg = new WireMessage();
		msg.type = "player_kill_exp_batch";
		msg.killExp = exp;
		msg.depth = cashedDepth;
		msg.clientToken = SPDSettings.streamClientToken();
		client.send(msg);
	}

	private static void discardPendingKillExp() {
		pendingKillExp = 0;
		lastKillExpDepth = -1;
	}

	public static void onBossSlain(int depth) {
		if (role != Role.CLIENT) {
			PlayAlongCatchup.noteBossSlain(depth);
		}
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
		boolean isConfig = "spawn_scale_config".equals(payload.command);
		if (!isConfig && !SPDSettings.playAlongSharedChat()) {
			return;
		}
		// Chat mirrors (and stream economy) only while this host holds dalton.
		if (!isConfig && !hostHoldsStreamRoom()) {
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
			int cashedDepth = lastKillExpDepth > 0 ? lastKillExpDepth : Dungeon.depth;
			flushPendingKillExp(cashedDepth);
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
		if (client != null) {
			client.setSpectating(true);
		}
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

	/** Other hosts' rooms. Dalton's public room uses the title-screen button instead. */
	public static void promptRoomCodeJoin() {
		if (!PlayAlongProtocol.ROOM_CODES_ENABLED) return;
		if (Game.scene() == null) return;
		String initial = SPDSettings.playAlongLastRoom();
		try {
			if (com.badlogic.gdx.Gdx.app != null && com.badlogic.gdx.Gdx.app.getClipboard() != null
					&& com.badlogic.gdx.Gdx.app.getClipboard().hasContents()) {
				String clip = com.badlogic.gdx.Gdx.app.getClipboard().getContents();
				String fromClip = PlayAlongProtocol.parseJoinInput(clip);
				if (!fromClip.isEmpty()) {
					initial = clip.trim();
				}
			}
		} catch (Exception ignored) {
		}
		Game.scene().addToFront(new WndTextInput(
				Messages.get(PlayAlongService.class, "code_title"),
				Messages.get(PlayAlongService.class, "code_prompt"),
				initial == null ? "" : initial,
				120,
				false,
				Messages.get(PlayAlongService.class, "code_ok"),
				Messages.get(PlayAlongService.class, "code_cancel")) {
			@Override
			public void onSelect(boolean positive, String text) {
				if (!positive) return;
				final String code = PlayAlongProtocol.parseJoinInput(text);
				if (code.isEmpty()) {
					GLog.w(Messages.get(PlayAlongService.class, "code_invalid"));
					return;
				}
				SPDSettings.playAlongLastRoom(code);
				final String url = PlayAlongProtocol.relayJoinAddress(code);
				PlayAlongClient.pingStatus(url, info -> Game.runOnRenderThread(() -> {
					if (info == null || (!info.active && !info.inDungeon)) {
						GLog.w(Messages.get(PlayAlongService.class, "code_offline"));
						return;
					}
					clientServerAddress = url;
					discoveredAddress = url;
					liveSessionInfo = info;
					if (Game.scene() != null) {
						Game.scene().addToFront(new WndPlayAlongJoin(info));
					}
				}));
			}
		});
	}

	private static String connectionFailureMessage(boolean permanent, boolean everOpened, int code, String reason) {
		String addr = clientServerAddress == null ? "" : clientServerAddress;
		int mark = addr.indexOf("/join/");
		boolean privateRoom = false;
		if (mark >= 0) {
			String room = addr.substring(mark + "/join/".length());
			int cut = room.indexOf('?');
			if (cut >= 0) room = room.substring(0, cut);
			cut = room.indexOf('/');
			if (cut >= 0) room = room.substring(0, cut);
			privateRoom = !room.equalsIgnoreCase(SPDSettings.PLAY_ALONG_RELAY_ROOM);
		}
		PlayAlongProtocol.CloseCopy copy = PlayAlongProtocol.closeCopy(everOpened, permanent);
		String suffix = PlayAlongProtocol.closeSuffix(reason);
		switch (copy) {
			case NETWORK_BLOCKED:
				return Messages.get(PlayAlongService.class, privateRoom ? "connect_blocked_room" : "connect_blocked");
			case NETWORK_LOST:
				return Messages.get(PlayAlongService.class, privateRoom ? "connect_lost_room" : "connect_lost");
			case DESYNC:
				return Messages.get(PlayAlongService.class,
						privateRoom ? "connect_desync_room" : "connect_desync", code, suffix);
			case DESYNC_FINAL:
			default:
				return Messages.get(PlayAlongService.class,
						privateRoom ? "connect_desync_final_room" : "connect_desync_final", code, suffix);
		}
	}

	/** Queue mirrored bombs/spawns and apply a few per frame so a raid cannot freeze the client. */
	private static void enqueueMirroredChat(List<ChatCommandPayload> incoming) {
		if (incoming == null) return;
		boolean gameplay = false;
		for (ChatCommandPayload p : incoming) {
			if (p == null || p.command == null) continue;
			if ("catchup".equals(p.command)) {
				GLog.i(PlayAlongCatchup.describeForCommand());
				continue;
			}
			// Spawn scaling used to apply immediately, including after death.
			// It must not sit behind a raid queue or get cleared with it.
			if ("spawn_scale_config".equals(p.command)) {
				StreamingCommandHandler.executeMirroredChatCommand(p);
				continue;
			}
			if (!acceptMirroredChat(p)) continue;
			// Hero null means the run is still loading. Keep the command until
			// the scene exists. Drop it only once a dead hero is actually here.
			if (Dungeon.hero != null && !Dungeon.hero.isAlive()) continue;
			while (pendingChatCommands.size() >= MAX_PENDING_CHAT) {
				pendingChatCommands.remove(0);
			}
			pendingChatCommands.add(p);
			gameplay = true;
		}
		if (gameplay && Dungeon.hero != null && Dungeon.level != null && Game.scene() instanceof GameScene) {
			scheduleChatDrain();
		}
	}

	private static boolean acceptMirroredChat(ChatCommandPayload p) {
		if ("spawn_scale_config".equals(p.command)) return true;
		if (role != Role.CLIENT) return false;
		String sender = normalizeChatUsername(p.username);
		if (!sender.isEmpty()) {
			if (sender.equals(normalizeChatUsername(clientUsername))
					|| sender.equals(normalizeChatUsername(SPDSettings.playAlongUsername()))) {
				return false;
			}
			StreamEconomyState eco = cachedEconomy;
			if (eco != null && eco.linked
					&& sender.equals(normalizeChatUsername(eco.linkedUsername))) {
				return false;
			}
		}
		return true;
	}

	private static void scheduleChatDrain() {
		if (chatDrainQueued || pendingChatCommands.isEmpty()) return;
		chatDrainQueued = true;
		Game.runOnRenderThread(() -> {
			chatDrainQueued = false;
			int budget = CHAT_APPLY_PER_FRAME;
			while (budget > 0 && !pendingChatCommands.isEmpty()) {
				ChatCommandPayload p = pendingChatCommands.remove(0);
				if ("spawn_scale_config".equals(p.command)) {
					StreamingCommandHandler.executeMirroredChatCommand(p);
					budget--;
					continue;
				}
				if (role != Role.CLIENT || Dungeon.hero == null || Dungeon.level == null
						|| !(Game.scene() instanceof GameScene)) {
					pendingChatCommands.add(0, p);
					return;
				}
				if (!Dungeon.hero.isAlive()) {
					applyQueuedConfigAndDropRest();
					return;
				}
				StreamingCommandHandler.executeMirroredChatCommand(p);
				budget--;
			}
			if (!pendingChatCommands.isEmpty()) scheduleChatDrain();
		});
	}

	/** A death mid-raid drops bombs and spawns. Spawn-scale config still applies. */
	private static void applyQueuedConfigAndDropRest() {
		List<ChatCommandPayload> config = new ArrayList<>();
		for (ChatCommandPayload queued : pendingChatCommands) {
			if (queued != null && "spawn_scale_config".equals(queued.command)) {
				config.add(queued);
			}
		}
		pendingChatCommands.clear();
		for (ChatCommandPayload queued : config) {
			StreamingCommandHandler.executeMirroredChatCommand(queued);
		}
	}

	public static void disconnect() {
		discardPendingKillExp();
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
		clientServerAddress = null;
		connectHintShown = false;
		desyncHintShown = false;
		chatDrainQueued = false;
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
		hostAscendingLive = false;
		tombstones.clear();
		depthCounts.clear();
		pendingChatCommands.clear();
		aliveCount = 0;
		pendingKillExp = 0;
		lastKillExpDepth = -1;
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
		msg.heroLvl = Dungeon.hero != null ? Math.max(1, Dungeon.hero.lvl) : 1;
		msg.armorTier = Dungeon.hero != null ? Dungeon.hero.tier() : 0;
		msg.depth = Dungeon.depth;
		msg.branch = Dungeon.branch;
		msg.pos = pos;
		msg.hpPct = (Dungeon.hero != null && Dungeon.hero.HT > 0)
				? Math.max(0f, Math.min(1f, (float) Dungeon.hero.HP / Dungeon.hero.HT))
				: 0f;
		msg.action = action;
		msg.catchup = PlayAlongCatchup.isCatchupHero();
		if (Dungeon.hero != null && Dungeon.hero.sprite != null) {
			msg.facingLeft = Dungeon.hero.sprite.flipHorizontal;
		}
		return msg;
	}

	private static GhostState buildLocalGhostState(String action, int pos, boolean isHost) {
		GhostState g = new GhostState();
		g.userId = isHost ? "host" : localUserId;
		g.username = isHost ? hostDisplayNameForSession() : clientUsername;
		g.heroClass = Dungeon.hero != null ? Dungeon.hero.heroClass.name() : "WARRIOR";
		g.heroLvl = Dungeon.hero != null ? Math.max(1, Dungeon.hero.lvl) : 1;
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
		g.catchup = !isHost && PlayAlongCatchup.isCatchupHero();
		g.lastUpdateMs = System.currentTimeMillis();
		return g;
	}
}
