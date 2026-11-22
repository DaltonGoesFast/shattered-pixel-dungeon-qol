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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.GhostState;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.SessionInfo;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.TombstoneState;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.WipeEvent;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.WireMessage;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.watabou.noosa.Game;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Host-authoritative WebSocket server for Play Along race synchronization
 * (listens on 0.0.0.0:port for Playit.gg). Stream economy / {@code !link} /
 * command requests are delegated to {@link StreamControlProxy}.
 */
public class PlayAlongServer extends WebSocketServer {

	public interface HostCallbacks {
		void onWorldStateUpdated(List<GhostState> ghosts, List<TombstoneState> tombstones, int aliveCount);
		void onChatterDied(String username, int depth, String cause);
		void onChatterWon(String username, long durationMs);
		void onChatterBossSlain(String username, int depth, int awardedPts);
		default void onHostWarning(String failureClass, String message) {}
	}

	public static final int MAX_CLIENTS = 100;
	public static final int RATE_LIMIT_PER_SEC = 8;
	private static final int RAW_SOCKET_OVERHEAD_CAP = 32;
	private static final long BOOT_REJOIN_GRACE_MS = 90_000L;

	private static class ClientRecord {
		String userId;
		String username;
		String clientToken = "";
		WebSocket conn;
		GhostState ghost;
		boolean alive = true;
		boolean won = false;
		final Set<Integer> claimedBossDepths = new HashSet<>();
		long disconnectedAtMs = 0L;
		long rateWindowStartMs = 0L;
		int rateCount = 0;
	}

	private final HostCallbacks callbacks;
	private final PlayAlongHostMetrics metrics = new PlayAlongHostMetrics();
	private final StreamControlProxy streamControlProxy;

	private final ConcurrentHashMap<String, ClientRecord> clientsByUserId = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<WebSocket, ClientRecord> connToClient = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<WebSocket, long[]> anonRateLimits = new ConcurrentHashMap<>();
	private final CopyOnWriteArrayList<TombstoneState> tombstones = new CopyOnWriteArrayList<>();

	private volatile SessionInfo activeSession;
	private volatile GhostState hostGhost;
	private volatile WipeEvent lastWipeEvent;
	private volatile boolean dirty = false;
	private final long serverStartedAtMs = System.currentTimeMillis();
	private volatile boolean sessionExplicitlyEnded = false;

	private volatile int maxClients = MAX_CLIENTS;
	private volatile long reconnectGraceMs = PlayAlongProtocol.RECONNECT_GRACE_MS;
	private volatile boolean requireJoinCode;
	private volatile String lobbyJoinCode;

	private ScheduledExecutorService ticker;

	public PlayAlongServer(int port, HostCallbacks callbacks) {
		super(new InetSocketAddress("0.0.0.0", port));
		this.callbacks = callbacks;
		this.requireJoinCode = SPDSettings.playAlongRequireJoinCode();
		String savedCode = SPDSettings.playAlongJoinCode();
		if (savedCode == null || savedCode.isEmpty()) {
			savedCode = PlayAlongProtocol.generateJoinCode();
			SPDSettings.playAlongJoinCode(savedCode);
		}
		this.lobbyJoinCode = savedCode;
		this.streamControlProxy = new StreamControlProxy(
				this::snapshotSessionInfo,
				new StreamControlProxy.TransportCallbacks() {
					@Override
					public void safeSend(WebSocket conn, String json) {
						PlayAlongServer.this.safeSend(conn, json);
					}

					@Override
					public void onFailure(String failureClass, String sessionId, Throwable err) {
						reportFailure(failureClass, sessionId, err);
					}

					@Override
					public void onBossSlainReward(String username, int depth, int awardedPts) {
						if (PlayAlongServer.this.callbacks != null) {
							PlayAlongServer.this.callbacks.onChatterBossSlain(username, depth, awardedPts);
						}
					}
				},
				metrics
		);
		setReuseAddr(true);
		setConnectionLostTimeout(30);
	}

	@Override
	public void onStart() {
		ticker = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "PlayAlongBroadcast");
			t.setDaemon(true);
			return t;
		});
		ticker.scheduleAtFixedRate(this::tickBroadcast, 200, 200, TimeUnit.MILLISECONDS);
		streamControlProxy.start();
	}

	public void stopServer() {
		if (ticker != null) {
			ticker.shutdownNow();
			ticker = null;
		}
		streamControlProxy.shutdown();
		try {
			stop(1000);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	public PlayAlongHostMetrics getMetrics() {
		return metrics;
	}

	public StreamControlProxy getStreamControlProxy() {
		return streamControlProxy;
	}

	public SessionInfo getActiveSession() {
		return activeSession;
	}

	public int getMaxClients() {
		return maxClients;
	}

	public void setMaxClients(int maxClients) {
		this.maxClients = Math.max(1, maxClients);
	}

	public long getReconnectGraceMs() {
		return reconnectGraceMs;
	}

	public void setReconnectGraceMs(long reconnectGraceMs) {
		this.reconnectGraceMs = Math.max(50L, reconnectGraceMs);
	}

	public boolean isJoinCodeRequired() {
		return requireJoinCode;
	}

	public void setRequireJoinCode(boolean requireJoinCode) {
		this.requireJoinCode = requireJoinCode;
		SessionInfo s = activeSession;
		if (s != null) {
			s.joinCodeRequired = requireJoinCode;
		}
	}

	public String getLobbyJoinCode() {
		return lobbyJoinCode;
	}

	public void setLobbyJoinCode(String code) {
		if (code != null && !code.trim().isEmpty()) {
			this.lobbyJoinCode = code.trim().toUpperCase(Locale.ROOT);
		}
	}

	public String rotateLobbyJoinCode() {
		String next = PlayAlongProtocol.generateJoinCode();
		this.lobbyJoinCode = next;
		SPDSettings.playAlongJoinCode(next);
		return next;
	}

	private String currentSessionId() {
		SessionInfo s = activeSession;
		return (s != null && s.sessionId != null) ? s.sessionId : "";
	}

	private void reportFailure(String failureClass, String sessionId, Throwable err) {
		String sid = (sessionId != null && !sessionId.isEmpty()) ? sessionId : "-";
		String errType = err != null ? err.getClass().getSimpleName() : "UnknownError";
		String errMsg = (err != null && err.getMessage() != null) ? err.getMessage() : "no details";
		System.err.println("[PlayAlong] Failure [" + failureClass + "] sessionId=" + sid
				+ " (" + errType + ": " + errMsg + ")");
		if (metrics.markFailureClassReported(failureClass) && callbacks != null) {
			callbacks.onHostWarning(failureClass, failureClass + ": " + errType + " (" + errMsg + ")");
		}
	}

	private void closeAllSessionClients(int code, String reason) {
		List<WebSocket> conns = new ArrayList<>(connToClient.keySet());
		String sid = currentSessionId();
		for (WebSocket conn : conns) {
			if (conn != null && conn.isOpen()) {
				try {
					conn.close(code, reason);
				} catch (Exception e) {
					reportFailure("ws_close_client", sid, e);
				}
			}
		}
	}

	public synchronized void startSession(SessionInfo session, GhostState initialHost) {
		if (this.activeSession != null && (session == null || this.activeSession.sessionId == null
				|| !this.activeSession.sessionId.equals(session.sessionId))) {
			closeAllSessionClients(1000, "New Play Along session started");
		}
		if (session != null) {
			if (session.hostUsername == null || session.hostUsername.trim().isEmpty()) {
				session.hostUsername = SPDSettings.playAlongHostUsernameSafe();
			}
			session.joinCodeRequired = requireJoinCode;
		}
		this.activeSession = session;
		this.hostGhost = initialHost;
		this.lastWipeEvent = null;
		this.sessionExplicitlyEnded = false;
		this.clientsByUserId.clear();
		this.connToClient.clear();
		this.tombstones.clear();
		this.metrics.resetSessionFailureClasses();
		this.metrics.setActiveClients(0);
		this.metrics.setAliveClients(0);
		this.dirty = true;
	}

	public synchronized WireMessage resumeSession(GhostState currentHost, int hp, int ht, String heroClass) {
		if (this.activeSession == null || !this.activeSession.active) {
			return null;
		}
		this.sessionExplicitlyEnded = false;
		this.hostGhost = currentHost;
		if (currentHost != null) {
			this.activeSession.hostDepth = currentHost.depth;
			this.activeSession.hostPos = currentHost.pos;
		}
		this.activeSession.hostHp = hp;
		this.activeSession.hostHt = ht;
		this.activeSession.joinCodeRequired = requireJoinCode;
		if (heroClass != null && !heroClass.isEmpty()) {
			this.activeSession.hostClass = heroClass;
		}
		this.dirty = true;
		return buildWorldStateMessage();
	}

	public synchronized void endSession() {
		this.sessionExplicitlyEnded = true;
		if (this.activeSession != null) {
			this.activeSession.active = false;
		}
		closeAllSessionClients(1000, "Play Along session ended");
		this.clientsByUserId.clear();
		this.connToClient.clear();
		this.tombstones.clear();
		this.metrics.setActiveClients(0);
		this.metrics.setAliveClients(0);
		this.dirty = false;
	}

	public void updateHostState(GhostState ghost) {
		this.hostGhost = ghost;
		if (activeSession != null && ghost != null) {
			activeSession.hostDepth = ghost.depth;
			activeSession.hostPos = ghost.pos;
		}
		this.dirty = true;
	}

	public void updateHostVitals(int hp, int ht) {
		if (activeSession != null) {
			activeSession.hostHp = hp;
			activeSession.hostHt = ht;
		}
		if (hostGhost != null && ht > 0) {
			hostGhost.hpPct = Math.max(0f, Math.min(1f, (float) hp / ht));
		}
		this.dirty = true;
	}

	public void broadcastHostAnkhPopped() {
		SessionInfo session = activeSession;
		if (session == null || !session.active) return;

		WireMessage msg = new WireMessage();
		msg.type = "host_ankh_popped";
		msg.sessionId = session.sessionId;
		msg.activePlayers = countAliveClients();
		sendToSessionClients(PlayAlongProtocol.GSON.toJson(msg));
	}

	private static String normalizeChatUsername(String raw) {
		if (raw == null) return "";
		String s = raw.trim();
		while (s.startsWith("@")) {
			s = s.substring(1).trim();
		}
		return s.toLowerCase(Locale.ROOT);
	}

	public void broadcastChatCommand(PlayAlongProtocol.ChatCommandPayload payload) {
		SessionInfo session = activeSession;
		if (session == null || !session.active || payload == null) return;

		WireMessage msg = new WireMessage();
		msg.type = "chat_command";
		msg.sessionId = session.sessionId;
		msg.chatCommand = payload;
		String json = PlayAlongProtocol.GSON.toJson(msg);

		boolean isConfig = "spawn_scale_config".equals(payload.command);
		String senderNorm = normalizeChatUsername(payload.username);
		String senderSanitized = senderNorm.isEmpty()
				? ""
				: PlayAlongProtocol.sanitizeUsername(senderNorm, "01", session.hostUsername).toLowerCase(Locale.ROOT);

		for (Map.Entry<WebSocket, ClientRecord> entry : connToClient.entrySet()) {
			WebSocket conn = entry.getKey();
			ClientRecord rec = entry.getValue();
			if (rec == null || (!isConfig && !rec.alive)) continue;
			if (!isConfig && !senderNorm.isEmpty()) {
				String recNorm = normalizeChatUsername(rec.username);
				if (recNorm.equals(senderNorm)
						|| (!senderSanitized.isEmpty() && recNorm.equals(senderSanitized))) {
					continue;
				}
			}
			if (!safeSend(conn, json)) {
				metrics.incrementMirrorDrops();
			}
		}
	}

	public synchronized WipeEvent triggerHostWipe(int hostDepth, String hostCause) {
		SessionInfo session = activeSession;
		WipeEvent wipe = new WipeEvent();
		wipe.sessionId = session != null ? session.sessionId : "";
		wipe.hostDepth = hostDepth;
		wipe.hostCause = (hostCause == null || hostCause.isEmpty()) ? "Unknown" : hostCause;
		wipe.timestampMs = System.currentTimeMillis();

		List<String> names = new ArrayList<>();
		int victims = 0;
		for (ClientRecord rec : clientsByUserId.values()) {
			if (rec.alive) {
				victims++;
				names.add(rec.username);
				rec.alive = false;
				if (rec.ghost != null) rec.ghost.action = "DEAD";
			}
		}
		wipe.victims = victims;
		if (!names.isEmpty()) {
			int cap = Math.min(names.size(), 6);
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < cap; i++) {
				if (i > 0) sb.append(", ");
				sb.append(names.get(i));
			}
			if (names.size() > cap) {
				sb.append(" +").append(names.size() - cap).append(" more");
			}
			wipe.victimNames = sb.toString();
		}

		this.lastWipeEvent = wipe;
		metrics.recordWipeVictims(victims);
		metrics.setAliveClients(0);
		if (session != null) {
			session.active = false;
			session.activePlayers = 0;
		}

		WireMessage msg = new WireMessage();
		msg.type = "host_died";
		msg.sessionId = wipe.sessionId;
		msg.depth = wipe.hostDepth;
		msg.cause = wipe.hostCause;
		msg.victims = wipe.victims;
		msg.victimNames = wipe.victimNames;
		sendToSessionClients(PlayAlongProtocol.GSON.toJson(msg));

		return wipe;
	}

	public int countAliveClients() {
		int count = 0;
		for (ClientRecord rec : clientsByUserId.values()) {
			if (rec.alive) count++;
		}
		return count;
	}

	public int getKnownClientCount() {
		return clientsByUserId.size();
	}

	@Override
	public void onOpen(WebSocket conn, ClientHandshake handshake) {
		if (getConnections().size() > (maxClients + RAW_SOCKET_OVERHEAD_CAP)) {
			conn.close(1008, "Server socket capacity exceeded");
		}
	}

	@Override
	public void onClose(WebSocket conn, int code, String reason, boolean remote) {
		anonRateLimits.remove(conn);
		ClientRecord rec = connToClient.remove(conn);
		if (rec != null && rec.conn == conn) {
			rec.conn = null;
			rec.disconnectedAtMs = System.currentTimeMillis();
			dirty = true;
		}
		metrics.setActiveClients(connToClient.size());
	}

	@Override
	public void onError(WebSocket conn, Exception ex) {
		if (ex != null) {
			reportFailure("ws_socket_error", currentSessionId(), ex);
		}
		if (conn != null) {
			anonRateLimits.remove(conn);
			ClientRecord rec = connToClient.remove(conn);
			if (rec != null && rec.conn == conn) {
				rec.conn = null;
				rec.disconnectedAtMs = System.currentTimeMillis();
			}
		}
		metrics.setActiveClients(connToClient.size());
	}

	@Override
	public void onMessage(WebSocket conn, String message) {
		if (message == null || message.length() > PlayAlongProtocol.MAX_PACKET_LENGTH) {
			conn.close(1009, "Message too large");
			return;
		}
		if (!allowRate(conn)) {
			return;
		}

		JsonObject rawObj;
		WireMessage msg;
		try {
			JsonElement el = JsonParser.parseString(message);
			if (el == null || !el.isJsonObject()) {
				return;
			}
			rawObj = el.getAsJsonObject();
			msg = PlayAlongProtocol.GSON.fromJson(rawObj, WireMessage.class);
		} catch (Exception e) {
			reportFailure("ws_invalid_json", currentSessionId(), e);
			return;
		}
		if (msg == null || msg.type == null) return;

		switch (msg.type) {
			case "status_ping":
				handleStatusPing(conn);
				break;
			case "join":
			case "rejoin":
				handleJoin(conn, rawObj, msg, "rejoin".equals(msg.type));
				break;
			case "player_state":
				handlePlayerState(conn, msg);
				break;
			case "player_died":
				handlePlayerDied(conn, msg);
				break;
			case "player_won":
				handlePlayerWon(conn, msg);
				break;
			case "player_boss_slain":
				handlePlayerBossSlain(conn, msg);
				break;
			case "stream_state_req":
				streamControlProxy.handleStreamStateRequest(conn, msg);
				break;
			case "stream_link_req":
				streamControlProxy.handleStreamLinkRequest(conn, msg);
				break;
			case "stream_cmd_req":
				streamControlProxy.handleStreamCommandRequest(conn, msg);
				break;
			default:
				// Strict host authority: ignore any unauthorized or host-only packet types from clients.
				break;
		}
	}

	private boolean allowRate(WebSocket conn) {
		long now = System.currentTimeMillis();
		ClientRecord rec = connToClient.get(conn);
		if (rec != null) {
			if (now - rec.rateWindowStartMs >= 1000L) {
				rec.rateWindowStartMs = now;
				rec.rateCount = 1;
				return true;
			}
			rec.rateCount++;
			return rec.rateCount <= RATE_LIMIT_PER_SEC;
		}
		long[] bucket = anonRateLimits.computeIfAbsent(conn, k -> new long[]{now, 0});
		if (now - bucket[0] >= 1000L) {
			bucket[0] = now;
			bucket[1] = 1;
			return true;
		}
		bucket[1]++;
		return bucket[1] <= RATE_LIMIT_PER_SEC;
	}

	private void handleStatusPing(WebSocket conn) {
		WireMessage pong = new WireMessage();
		pong.type = "status_pong";
		pong.session = snapshotSessionInfo();
		safeSend(conn, PlayAlongProtocol.GSON.toJson(pong));
	}

	private synchronized void handleJoin(WebSocket conn, JsonObject rawObj, WireMessage msg, boolean isRejoin) {
		if (!rawObj.has("protocolVersion") || !rawObj.get("protocolVersion").isJsonPrimitive()
				|| msg.protocolVersion != PlayAlongProtocol.PROTOCOL_VERSION) {
			metrics.incrementRejectedJoins();
			String clientVer = rawObj.has("protocolVersion") ? String.valueOf(msg.protocolVersion) : "missing";
			sendJoinError(conn, "Protocol version mismatch: server requires v"
					+ PlayAlongProtocol.PROTOCOL_VERSION + " (client sent " + clientVer + "). Please update to play along.");
			conn.close(1008, "Protocol version mismatch");
			return;
		}

		WipeEvent wipe = lastWipeEvent;
		if (isRejoin && wipe != null && msg.sessionId != null && msg.sessionId.equals(wipe.sessionId)) {
			WireMessage deadMsg = new WireMessage();
			deadMsg.type = "host_died";
			deadMsg.sessionId = wipe.sessionId;
			deadMsg.depth = wipe.hostDepth;
			deadMsg.cause = wipe.hostCause;
			deadMsg.victims = wipe.victims;
			deadMsg.victimNames = wipe.victimNames;
			safeSend(conn, PlayAlongProtocol.GSON.toJson(deadMsg));
			return;
		}

		SessionInfo session = activeSession;
		String hostName = (session != null) ? session.displayHostName() : SPDSettings.playAlongHostUsernameSafe();
		if (session == null || !session.active) {
			if (isRejoin && session == null && !sessionExplicitlyEnded
					&& (System.currentTimeMillis() - serverStartedAtMs) < BOOT_REJOIN_GRACE_MS) {
				conn.close(1013, "Host is loading save");
				return;
			}
			metrics.incrementRejectedJoins();
			sendJoinError(conn, hostName + " is not currently hosting a Play Along run.");
			return;
		}
		if (isRejoin && msg.sessionId != null && !msg.sessionId.isEmpty()
				&& session.sessionId != null && !session.sessionId.isEmpty()
				&& !msg.sessionId.equals(session.sessionId)) {
			metrics.incrementRejectedJoins();
			sendJoinError(conn, hostName + " started a different Play Along run! Return to title to join.");
			return;
		}
		if (msg.modVersion != null && !msg.modVersion.isEmpty()
				&& !session.version.isEmpty() && !msg.modVersion.equals(session.version)) {
			metrics.incrementRejectedJoins();
			sendJoinError(conn, "Version mismatch: " + hostName + " is running " + session.version + ". Please update to play along.");
			return;
		}

		String userId = (msg.userId != null && !msg.userId.trim().isEmpty())
				? msg.userId.trim()
				: ("u_" + Integer.toHexString(conn.hashCode()));
		if (userId.length() > 36) userId = userId.substring(0, 36);

		String cToken = msg.clientToken != null ? msg.clientToken.trim() : "";
		ClientRecord existingRec = clientsByUserId.get(userId);

		if (requireJoinCode) {
			String suppliedCode = msg.joinCode != null ? msg.joinCode.trim() : "";
			boolean codeMatches = !suppliedCode.isEmpty() && suppliedCode.equalsIgnoreCase(lobbyJoinCode);
			boolean trustedReconnect = isRejoin && existingRec != null
					&& !existingRec.clientToken.isEmpty() && existingRec.clientToken.equals(cToken);
			if (!codeMatches && !trustedReconnect) {
				metrics.incrementRejectedJoins();
				sendJoinError(conn, "Invalid or missing lobby join code.");
				conn.close(1008, "Invalid join code");
				return;
			}
		}

		purgeExpiredDisconnectedClients(System.currentTimeMillis());

		if (!clientsByUserId.containsKey(userId) && clientsByUserId.size() >= maxClients) {
			metrics.incrementRejectedJoins();
			sendJoinError(conn, "Play Along lobby is full (" + maxClients + " players).");
			conn.close(1008, "Lobby full");
			return;
		}

		String shortSuffix = Integer.toHexString(Math.abs(userId.hashCode()) % 0xFFF);
		String safeName = PlayAlongProtocol.sanitizeUsername(msg.username, shortSuffix, session.hostUsername);

		ClientRecord rec = clientsByUserId.get(userId);
		boolean isNewClient = (rec == null);
		if (rec == null) {
			boolean rejoinedAsDead = isRejoin && "DEAD".equalsIgnoreCase(msg.action);
			rec = new ClientRecord();
			rec.userId = userId;
			rec.username = safeName;
			rec.clientToken = cToken;
			rec.alive = !rejoinedAsDead;
			rec.ghost = new GhostState();
			rec.ghost.userId = userId;
			rec.ghost.username = safeName;
			rec.ghost.heroClass = safeHeroClass(msg.heroClass);
			rec.ghost.armorTier = Math.max(0, Math.min(6, msg.armorTier));
			rec.ghost.depth = Math.max(1, msg.depth);
			rec.ghost.branch = msg.branch;
			rec.ghost.pos = msg.pos;
			rec.ghost.hpPct = rejoinedAsDead ? 0f : 1f;
			rec.ghost.action = rejoinedAsDead ? "DEAD" : "IDLE";
			rec.ghost.isHost = false;
			rec.ghost.lastUpdateMs = System.currentTimeMillis();
			clientsByUserId.put(userId, rec);
		} else {
			if (rec.conn != null && rec.conn != conn) {
				WebSocket prevConn = rec.conn;
				connToClient.remove(prevConn);
				if (prevConn.isOpen()) {
					try {
						prevConn.close(1000, "Replaced by new connection");
					} catch (Exception e) {
						reportFailure("ws_replace_conn", session.sessionId, e);
					}
				}
			}
			rec.disconnectedAtMs = 0L;
			if (!cToken.isEmpty()) {
				rec.clientToken = cToken;
			}
			if (!isRejoin) {
				rec.username = safeName;
				rec.ghost.username = safeName;
				rec.ghost.heroClass = safeHeroClass(msg.heroClass);
				rec.alive = true;
				rec.won = false;
				rec.claimedBossDepths.clear();
			} else if (rec.ghost != null && rec.alive) {
				if (msg.depth > 0) rec.ghost.depth = Math.max(1, Math.min(50, msg.depth));
				rec.ghost.branch = Math.max(0, Math.min(10, msg.branch));
				rec.ghost.pos = Math.max(0, msg.pos);
				rec.ghost.lastUpdateMs = System.currentTimeMillis();
			}
		}
		rec.conn = conn;
		connToClient.put(conn, rec);
		anonRateLimits.remove(conn);

		if (isNewClient) {
			metrics.incrementTotalJoins();
		}
		metrics.setActiveClients(connToClient.size());
		metrics.setAliveClients(countAliveClients());

		WireMessage ack = new WireMessage();
		ack.type = "join_ack";
		ack.userId = rec.userId;
		ack.username = rec.username;
		ack.session = snapshotSessionInfo();
		safeSend(conn, PlayAlongProtocol.GSON.toJson(ack));

		safeSend(conn, PlayAlongProtocol.GSON.toJson(buildWorldStateMessage()));
		dirty = true;

		if (!rec.clientToken.isEmpty()) {
			final ClientRecord recFinal = rec;
			streamControlProxy.syncLinkedUsernameAsync(
					rec.clientToken,
					shortSuffix,
					session.hostUsername,
					verified -> {
						recFinal.username = verified;
						if (recFinal.ghost != null) {
							recFinal.ghost.username = verified;
						}
						dirty = true;
					});
		}
	}

	private void sendJoinError(WebSocket conn, String err) {
		WireMessage msg = new WireMessage();
		msg.type = "join_error";
		msg.error = err;
		safeSend(conn, PlayAlongProtocol.GSON.toJson(msg));
	}

	private void handlePlayerState(WebSocket conn, WireMessage msg) {
		ClientRecord rec = connToClient.get(conn);
		if (rec == null || !rec.alive || rec.ghost == null) return;

		rec.ghost.heroClass = safeHeroClass(msg.heroClass != null ? msg.heroClass : rec.ghost.heroClass);
		rec.ghost.armorTier = Math.max(0, Math.min(6, msg.armorTier));
		rec.ghost.depth = Math.max(1, Math.min(50, msg.depth));
		rec.ghost.branch = Math.max(0, Math.min(10, msg.branch));
		rec.ghost.pos = Math.max(0, msg.pos);
		rec.ghost.facingLeft = msg.facingLeft;
		rec.ghost.hpPct = Math.max(0f, Math.min(1f, msg.hpPct));
		rec.ghost.action = safeAction(msg.action);
		rec.ghost.lastUpdateMs = System.currentTimeMillis();
		dirty = true;
	}

	private void handlePlayerDied(WebSocket conn, WireMessage msg) {
		ClientRecord rec = connToClient.get(conn);
		if (rec == null || !rec.alive) return;

		rec.alive = false;
		if (rec.ghost != null) {
			rec.ghost.action = "DEAD";
			rec.ghost.hpPct = 0f;
			rec.ghost.lastUpdateMs = System.currentTimeMillis();
		}
		metrics.setAliveClients(countAliveClients());

		TombstoneState tomb = new TombstoneState();
		tomb.userId = rec.userId;
		tomb.username = rec.username;
		tomb.heroClass = rec.ghost != null ? rec.ghost.heroClass : "WARRIOR";
		tomb.depth = Math.max(1, msg.depth);
		tomb.branch = Math.max(0, msg.branch);
		tomb.pos = Math.max(0, msg.pos);
		tomb.cause = (msg.cause != null && msg.cause.length() <= 64) ? msg.cause.trim() : "Perished";
		tombstones.add(tomb);

		WireMessage notice = new WireMessage();
		notice.type = "player_died_notice";
		notice.username = rec.username;
		notice.depth = tomb.depth;
		notice.cause = tomb.cause;
		sendToSessionClients(PlayAlongProtocol.GSON.toJson(notice));

		if (callbacks != null) {
			callbacks.onChatterDied(rec.username, tomb.depth, tomb.cause);
		}
		dirty = true;
	}

	private void handlePlayerWon(WebSocket conn, WireMessage msg) {
		ClientRecord rec = connToClient.get(conn);
		if (rec == null || rec.won) return;

		rec.won = true;
		WireMessage notice = new WireMessage();
		notice.type = "player_won_notice";
		notice.username = rec.username;
		notice.durationMs = Math.max(0L, msg.durationMs);
		sendToSessionClients(PlayAlongProtocol.GSON.toJson(notice));

		if (callbacks != null) {
			callbacks.onChatterWon(rec.username, notice.durationMs);
		}
		SessionInfo session = activeSession;
		if (session != null && session.active) {
			String token = (msg.clientToken != null && !msg.clientToken.trim().isEmpty())
					? msg.clientToken.trim() : rec.clientToken;
			streamControlProxy.triggerMilestoneRewardAsync(
					conn, rec.username, session.sessionId, token, "victory", 0);
		}
		dirty = true;
	}

	private void handlePlayerBossSlain(WebSocket conn, WireMessage msg) {
		ClientRecord rec = connToClient.get(conn);
		SessionInfo session = activeSession;
		if (rec == null || !rec.alive || session == null || !session.active) return;

		int depth = msg.depth;
		if (depth != 5 && depth != 10 && depth != 15 && depth != 20 && depth != 25) return;

		synchronized (rec.claimedBossDepths) {
			if (rec.claimedBossDepths.contains(depth)) return;
			rec.claimedBossDepths.add(depth);
		}

		String token = (msg.clientToken != null && !msg.clientToken.trim().isEmpty())
				? msg.clientToken.trim() : rec.clientToken;
		streamControlProxy.triggerMilestoneRewardAsync(
				conn, rec.username, session.sessionId, token, "boss_" + depth, depth);
	}

	private boolean purgeExpiredDisconnectedClients(long now) {
		boolean removed = false;
		Iterator<Map.Entry<String, ClientRecord>> it = clientsByUserId.entrySet().iterator();
		while (it.hasNext()) {
			ClientRecord rec = it.next().getValue();
			if (rec.conn == null && rec.disconnectedAtMs > 0
					&& (now - rec.disconnectedAtMs) > reconnectGraceMs) {
				it.remove();
				removed = true;
			}
		}
		if (removed) {
			metrics.setAliveClients(countAliveClients());
		}
		return removed;
	}

	private void tickBroadcast() {
		try {
			long now = System.currentTimeMillis();
			boolean removed = purgeExpiredDisconnectedClients(now);

			if (!dirty && !removed) return;
			dirty = false;

			SessionInfo session = activeSession;
			if (session == null || !session.active) return;

			WireMessage world = buildWorldStateMessage();
			session.activePlayers = world.activePlayers;
			metrics.setAliveClients(world.activePlayers);

			if (!connToClient.isEmpty()) {
				sendToSessionClients(PlayAlongProtocol.GSON.toJson(world));
			}
			if (callbacks != null) {
				callbacks.onWorldStateUpdated(world.ghosts, world.tombstones, world.activePlayers);
			}
		} catch (Exception e) {
			reportFailure("ws_tick_broadcast", currentSessionId(), e);
		}
	}

	private WireMessage buildWorldStateMessage() {
		WireMessage world = new WireMessage();
		world.type = "world_state";
		world.sessionId = activeSession != null ? activeSession.sessionId : "";
		world.hostGhost = hostGhost;

		List<GhostState> ghosts = new ArrayList<>();
		Map<Integer, Integer> depths = new LinkedHashMap<>();
		int alive = 0;
		for (ClientRecord rec : clientsByUserId.values()) {
			if (rec.alive && rec.ghost != null) {
				alive++;
				ghosts.add(rec.ghost);
				depths.put(rec.ghost.depth, depths.getOrDefault(rec.ghost.depth, 0) + 1);
			}
		}
		world.ghosts = ghosts;
		world.tombstones = new ArrayList<>(tombstones);
		world.depthCounts = depths;
		world.activePlayers = alive;
		return world;
	}

	public SessionInfo snapshotSessionInfo() {
		SessionInfo src = activeSession;
		SessionInfo copy = new SessionInfo();
		boolean heroInDungeon = false;
		try {
			heroInDungeon = Dungeon.hero != null
					&& Dungeon.level != null
					&& Dungeon.hero.isAlive()
					&& (Game.scene() instanceof GameScene || Game.scene() instanceof InterlevelScene);
		} catch (Throwable t) {
			reportFailure("snapshot_hero_state", src != null ? src.sessionId : "", t);
		}
		copy.inDungeon = heroInDungeon;
		copy.serverOnline = (src != null && src.active) || heroInDungeon;
		copy.joinCodeRequired = requireJoinCode;
		if (src == null || !src.active) {
			copy.active = false;
			copy.version = Game.version != null ? Game.version : "";
			copy.hostUsername = SPDSettings.playAlongHostUsernameSafe();
			if (heroInDungeon && Dungeon.hero != null) {
				copy.hostDepth = Dungeon.depth;
				copy.hostPos = Dungeon.hero.pos;
				copy.hostHp = Dungeon.hero.HP;
				copy.hostHt = Dungeon.hero.HT;
				if (Dungeon.hero.heroClass != null) {
					copy.hostClass = Dungeon.hero.heroClass.name();
				}
			}
			return copy;
		}
		copy.active = src.active;
		copy.sessionId = src.sessionId;
		copy.version = src.version;
		copy.hostUsername = src.displayHostName();
		copy.seed = src.seed;
		copy.customSeedText = src.customSeedText;
		copy.challenges = src.challenges;
		copy.modifiers = src.modifiers;
		copy.hostClass = src.hostClass;
		copy.hostDepth = src.hostDepth;
		copy.hostPos = src.hostPos;
		copy.hostHp = src.hostHp;
		copy.hostHt = src.hostHt;
		copy.activePlayers = countAliveClients();
		return copy;
	}

	private boolean safeSend(WebSocket conn, String json) {
		if (conn == null || !conn.isOpen() || json == null) {
			return false;
		}
		try {
			conn.send(json);
			return true;
		} catch (Exception e) {
			metrics.incrementWsSendFailures();
			reportFailure("ws_send", currentSessionId(), e);
			return false;
		}
	}

	private void sendToSessionClients(String json) {
		for (WebSocket conn : connToClient.keySet()) {
			safeSend(conn, json);
		}
	}

	private static String safeHeroClass(String raw) {
		if (raw == null) return "WARRIOR";
		switch (raw.toUpperCase(Locale.ROOT)) {
			case "WARRIOR":
			case "MAGE":
			case "ROGUE":
			case "HUNTRESS":
			case "DUELIST":
			case "CLERIC":
				return raw.toUpperCase(Locale.ROOT);
			default:
				return "WARRIOR";
		}
	}

	private static String safeAction(String raw) {
		if (raw == null) return "IDLE";
		switch (raw.toUpperCase(Locale.ROOT)) {
			case "IDLE":
			case "MOVE":
			case "ATTACK":
			case "OPERATE":
			case "DEAD":
				return raw.toUpperCase(Locale.ROOT);
			default:
				return "IDLE";
		}
	}
}
