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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.GhostState;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.SessionInfo;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.StreamEconomyState;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.TombstoneState;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.WipeEvent;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.WireMessage;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.watabou.noosa.Game;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Host-authoritative WebSocket server for Play Along Mode (listens on 0.0.0.0:port for Playit.gg).
 */
public class PlayAlongServer extends WebSocketServer {

	public interface HostCallbacks {
		void onWorldStateUpdated(List<GhostState> ghosts, List<TombstoneState> tombstones, int aliveCount);
		void onChatterDied(String username, int depth, String cause);
		void onChatterWon(String username, long durationMs);
		void onChatterBossSlain(String username, int depth, int awardedPts);
	}

	private static final int MAX_CLIENTS = 100;
	private static final int RATE_LIMIT_PER_SEC = 8;
	private static final String OVERLAY_API_BASE = "http://127.0.0.1:5000";

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
	private final ConcurrentHashMap<String, ClientRecord> clientsByUserId = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<WebSocket, ClientRecord> connToClient = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<WebSocket, long[]> anonRateLimits = new ConcurrentHashMap<>();
	private final CopyOnWriteArrayList<TombstoneState> tombstones = new CopyOnWriteArrayList<>();

	private volatile SessionInfo activeSession;
	private volatile GhostState hostGhost;
	private volatile WipeEvent lastWipeEvent;
	private volatile boolean dirty = false;

	private ScheduledExecutorService ticker;
	private ExecutorService httpPool;

	public PlayAlongServer(int port, HostCallbacks callbacks) {
		super(new InetSocketAddress("0.0.0.0", port));
		this.callbacks = callbacks;
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
		httpPool = Executors.newFixedThreadPool(4, r -> {
			Thread t = new Thread(r, "PlayAlongHttpBridge");
			t.setDaemon(true);
			return t;
		});
	}

	public void stopServer() {
		if (ticker != null) {
			ticker.shutdownNow();
			ticker = null;
		}
		if (httpPool != null) {
			httpPool.shutdownNow();
			httpPool = null;
		}
		try {
			stop(1000);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	public synchronized void startSession(SessionInfo session, GhostState initialHost) {
		this.activeSession = session;
		this.hostGhost = initialHost;
		this.lastWipeEvent = null;
		this.clientsByUserId.clear();
		this.connToClient.clear();
		this.tombstones.clear();
		this.dirty = true;
	}

	public synchronized void endSession() {
		if (this.activeSession != null) {
			this.activeSession.active = false;
		}
		this.clientsByUserId.clear();
		this.connToClient.clear();
		this.tombstones.clear();
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
				: PlayAlongProtocol.sanitizeUsername(senderNorm, "01").toLowerCase(Locale.ROOT);

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
			safeSend(conn, json);
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

	@Override
	public void onOpen(WebSocket conn, ClientHandshake handshake) {
		if (getConnections().size() > MAX_CLIENTS) {
			conn.close(1008, "Lobby full");
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
	}

	@Override
	public void onError(WebSocket conn, Exception ex) {
		if (conn != null) {
			anonRateLimits.remove(conn);
			ClientRecord rec = connToClient.remove(conn);
			if (rec != null && rec.conn == conn) {
				rec.conn = null;
				rec.disconnectedAtMs = System.currentTimeMillis();
			}
		}
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

		WireMessage msg;
		try {
			msg = PlayAlongProtocol.GSON.fromJson(message, WireMessage.class);
		} catch (Exception e) {
			return;
		}
		if (msg == null || msg.type == null) return;

		switch (msg.type) {
			case "status_ping":
				handleStatusPing(conn);
				break;
			case "join":
			case "rejoin":
				handleJoin(conn, msg, "rejoin".equals(msg.type));
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
				handleStreamStateRequest(conn, msg);
				break;
			case "stream_link_req":
				handleStreamLinkRequest(conn, msg);
				break;
			case "stream_cmd_req":
				handleStreamCommandRequest(conn, msg);
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
		SessionInfo snap = snapshotSessionInfo();
		pong.session = snap;
		conn.send(PlayAlongProtocol.GSON.toJson(pong));
	}

	private synchronized void handleJoin(WebSocket conn, WireMessage msg, boolean isRejoin) {
		WipeEvent wipe = lastWipeEvent;
		if (isRejoin && wipe != null && msg.sessionId != null && msg.sessionId.equals(wipe.sessionId)) {
			WireMessage deadMsg = new WireMessage();
			deadMsg.type = "host_died";
			deadMsg.sessionId = wipe.sessionId;
			deadMsg.depth = wipe.hostDepth;
			deadMsg.cause = wipe.hostCause;
			deadMsg.victims = wipe.victims;
			deadMsg.victimNames = wipe.victimNames;
			conn.send(PlayAlongProtocol.GSON.toJson(deadMsg));
			return;
		}

		SessionInfo session = activeSession;
		if (session == null || !session.active) {
			sendJoinError(conn, "Dalton is not currently hosting a Play Along run.");
			return;
		}
		if (msg.modVersion != null && !msg.modVersion.isEmpty()
				&& !session.version.isEmpty() && !msg.modVersion.equals(session.version)) {
			sendJoinError(conn, "Version mismatch: Dalton is running " + session.version + ". Please update to play along.");
			return;
		}
		if (clientsByUserId.size() >= MAX_CLIENTS && (msg.userId == null || !clientsByUserId.containsKey(msg.userId))) {
			sendJoinError(conn, "Play Along lobby is full (" + MAX_CLIENTS + " players).");
			return;
		}

		String userId = (msg.userId != null && !msg.userId.trim().isEmpty())
				? msg.userId.trim()
				: ("u_" + Integer.toHexString(conn.hashCode()));
		if (userId.length() > 36) userId = userId.substring(0, 36);

		String shortSuffix = Integer.toHexString(Math.abs(userId.hashCode()) % 0xFFF);
		String safeName = PlayAlongProtocol.sanitizeUsername(msg.username, shortSuffix);
		String cToken = msg.clientToken != null ? msg.clientToken.trim() : "";

		ClientRecord rec = clientsByUserId.get(userId);
		if (rec == null) {
			rec = new ClientRecord();
			rec.userId = userId;
			rec.username = safeName;
			rec.clientToken = cToken;
			rec.ghost = new GhostState();
			rec.ghost.userId = userId;
			rec.ghost.username = safeName;
			rec.ghost.heroClass = safeHeroClass(msg.heroClass);
			rec.ghost.armorTier = Math.max(0, Math.min(6, msg.armorTier));
			rec.ghost.depth = Math.max(1, msg.depth);
			rec.ghost.branch = msg.branch;
			rec.ghost.pos = msg.pos;
			rec.ghost.hpPct = 1f;
			rec.ghost.isHost = false;
			rec.ghost.lastUpdateMs = System.currentTimeMillis();
			clientsByUserId.put(userId, rec);
		} else {
			if (rec.conn != null && rec.conn != conn) {
				connToClient.remove(rec.conn);
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
			}
		}
		rec.conn = conn;
		connToClient.put(conn, rec);
		anonRateLimits.remove(conn);

		WireMessage ack = new WireMessage();
		ack.type = "join_ack";
		ack.userId = rec.userId;
		ack.username = rec.username;
		ack.session = snapshotSessionInfo();
		conn.send(PlayAlongProtocol.GSON.toJson(ack));

		conn.send(PlayAlongProtocol.GSON.toJson(buildWorldStateMessage()));
		dirty = true;

		if (!rec.clientToken.isEmpty()) {
			final ClientRecord recFinal = rec;
			final String tokenFinal = rec.clientToken;
			submitHttp(() -> syncLinkedUsernameForJoin(recFinal, tokenFinal, shortSuffix));
		}
	}

	private void sendJoinError(WebSocket conn, String err) {
		WireMessage msg = new WireMessage();
		msg.type = "join_error";
		msg.error = err;
		conn.send(PlayAlongProtocol.GSON.toJson(msg));
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
			triggerMilestoneRewardAsync(conn, rec, session.sessionId, token, "victory", 0);
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
		triggerMilestoneRewardAsync(conn, rec, session.sessionId, token, "boss_" + depth, depth);
	}

	private void tickBroadcast() {
		try {
			long now = System.currentTimeMillis();
			boolean removed = false;
			Iterator<Map.Entry<String, ClientRecord>> it = clientsByUserId.entrySet().iterator();
			while (it.hasNext()) {
				ClientRecord rec = it.next().getValue();
				if (rec.conn == null && rec.disconnectedAtMs > 0
						&& (now - rec.disconnectedAtMs) > PlayAlongProtocol.RECONNECT_GRACE_MS) {
					it.remove();
					removed = true;
				}
			}

			if (!dirty && !removed) return;
			dirty = false;

			SessionInfo session = activeSession;
			if (session == null || !session.active) return;

			WireMessage world = buildWorldStateMessage();
			session.activePlayers = world.activePlayers;

			if (!connToClient.isEmpty()) {
				sendToSessionClients(PlayAlongProtocol.GSON.toJson(world));
			}
			if (callbacks != null) {
				callbacks.onWorldStateUpdated(world.ghosts, world.tombstones, world.activePlayers);
			}
		} catch (Exception ignored) {
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

	private SessionInfo snapshotSessionInfo() {
		SessionInfo src = activeSession;
		SessionInfo copy = new SessionInfo();
		boolean heroInDungeon = false;
		try {
			heroInDungeon = Dungeon.hero != null
					&& Dungeon.level != null
					&& Dungeon.hero.isAlive()
					&& (Game.scene() instanceof GameScene || Game.scene() instanceof InterlevelScene);
		} catch (Throwable ignored) {
		}
		copy.inDungeon = heroInDungeon;
		copy.serverOnline = (src != null && src.active) || heroInDungeon;
		if (src == null || !src.active) {
			copy.active = false;
			copy.version = Game.version != null ? Game.version : "";
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

	private void handleStreamStateRequest(final WebSocket conn, WireMessage msg) {
		final String token = msg.clientToken != null ? msg.clientToken.trim() : "";
		final String reqId = msg.requestId;
		submitHttp(() -> {
			SessionInfo snap = snapshotSessionInfo();
			StreamEconomyState eco = fetchEconomyStateFromOverlay(token, snap);
			WireMessage resp = new WireMessage();
			resp.type = "stream_state_resp";
			resp.requestId = reqId;
			resp.economy = eco;
			safeSend(conn, PlayAlongProtocol.GSON.toJson(resp));
		});
	}

	private void handleStreamLinkRequest(final WebSocket conn, WireMessage msg) {
		final String token = msg.clientToken != null ? msg.clientToken.trim() : "";
		final String reqId = msg.requestId;
		submitHttp(() -> {
			SessionInfo snap = snapshotSessionInfo();
			WireMessage resp = new WireMessage();
			resp.type = "stream_link_resp";
			resp.requestId = reqId;
			try {
				JsonObject body = new JsonObject();
				body.addProperty("token", token);
				JsonObject res = httpPostJson(OVERLAY_API_BASE + "/api/playalong/link-request", body);
				if (res != null && res.has("ok") && res.get("ok").getAsBoolean()) {
					resp.commandOk = true;
					resp.linkCode = res.has("code") ? res.get("code").getAsString() : "";
					resp.linkExpiresSec = res.has("expires_in") ? res.get("expires_in").getAsInt() : 300;
				} else {
					resp.commandOk = false;
					resp.message = (res != null && res.has("error"))
							? res.get("error").getAsString()
							: "Overlay server unavailable.";
				}
			} catch (Exception e) {
				resp.commandOk = false;
				resp.message = "Overlay server unavailable (" + e.getMessage() + ")";
			}
			resp.economy = fetchEconomyStateFromOverlay(token, snap);
			safeSend(conn, PlayAlongProtocol.GSON.toJson(resp));
		});
	}

	private void handleStreamCommandRequest(final WebSocket conn, WireMessage msg) {
		final String token = msg.clientToken != null ? msg.clientToken.trim() : "";
		final String rawCmd = msg.rawCommand != null ? msg.rawCommand.trim() : "";
		final String reqId = msg.requestId;
		submitHttp(() -> {
			SessionInfo snap = snapshotSessionInfo();
			WireMessage resp = new WireMessage();
			resp.type = "stream_cmd_resp";
			resp.requestId = reqId;
			try {
				JsonObject body = new JsonObject();
				body.addProperty("token", token);
				body.addProperty("rawMessage", rawCmd);
				JsonObject res = httpPostJson(OVERLAY_API_BASE + "/api/playalong/client-command", body);
				if (res != null) {
					resp.commandOk = res.has("ok") && res.get("ok").getAsBoolean();
					if (res.has("message") && !res.get("message").isJsonNull()) {
						resp.message = res.get("message").getAsString();
					}
					if (res.has("pts") && !res.get("pts").isJsonNull()) {
						resp.pointsBalance = res.get("pts").getAsInt();
					}
					if (res.has("state") && res.get("state").isJsonObject()) {
						resp.economy = parseEconomyState(res.getAsJsonObject("state"), snap);
					}
				} else {
					resp.commandOk = false;
					resp.message = "Overlay server offline.";
				}
			} catch (Exception e) {
				resp.commandOk = false;
				resp.message = "Overlay server offline.";
			}
			if (resp.economy == null) {
				resp.economy = fetchEconomyStateFromOverlay(token, snap);
			}
			safeSend(conn, PlayAlongProtocol.GSON.toJson(resp));
		});
	}

	private void triggerMilestoneRewardAsync(final WebSocket conn, final ClientRecord rec,
	                                         final String sessionId, final String token,
	                                         final String milestone, final int bossDepth) {
		if (token == null || token.isEmpty()) return;
		submitHttp(() -> {
			try {
				JsonObject body = new JsonObject();
				body.addProperty("sessionId", sessionId != null ? sessionId : "");
				body.addProperty("token", token);
				body.addProperty("username", rec.username != null ? rec.username : "");
				body.addProperty("milestone", milestone);
				JsonObject res = httpPostJson(OVERLAY_API_BASE + "/api/playalong/milestone-reward", body);
				if (res == null || !res.has("ok") || !res.get("ok").getAsBoolean()) return;

				int awarded = res.has("awarded") ? res.get("awarded").getAsInt() : 0;
				if (awarded <= 0) return;

				String uname = res.has("username") ? res.get("username").getAsString() : rec.username;
				String label = res.has("label") ? res.get("label").getAsString() : milestone;
				String kind = res.has("kind") ? res.get("kind").getAsString() : "chat";
				int bal = res.has("pts") ? res.get("pts").getAsInt() : 0;

				WireMessage notice = new WireMessage();
				notice.type = "milestone_reward_notice";
				notice.username = uname;
				notice.message = label;
				notice.pointsAwarded = awarded;
				notice.rewardKind = kind;
				notice.pointsBalance = bal;
				notice.depth = bossDepth;
				safeSend(conn, PlayAlongProtocol.GSON.toJson(notice));

				if (bossDepth > 0 && callbacks != null) {
					callbacks.onChatterBossSlain(uname, bossDepth, awarded);
				}
			} catch (Exception ignored) {
			}
		});
	}

	private void syncLinkedUsernameForJoin(ClientRecord rec, String token, String shortSuffix) {
		try {
			StreamEconomyState eco = fetchEconomyStateFromOverlay(token, snapshotSessionInfo());
			if (eco != null && eco.linked && eco.linkedUsername != null && !eco.linkedUsername.trim().isEmpty()) {
				String verified = PlayAlongProtocol.sanitizeUsername(eco.linkedUsername, shortSuffix);
				rec.username = verified;
				if (rec.ghost != null) {
					rec.ghost.username = verified;
				}
				dirty = true;
			}
		} catch (Exception ignored) {
		}
	}

	private StreamEconomyState fetchEconomyStateFromOverlay(String token, SessionInfo snap) {
		try {
			String enc = URLEncoder.encode(token != null ? token : "", "UTF-8");
			String url = OVERLAY_API_BASE + "/api/playalong/client-state?token=" + enc
					+ (snap.inDungeon ? ("&depth=" + snap.hostDepth) : "");
			JsonObject json = httpGetJson(url);
			if (json != null) {
				return parseEconomyState(json, snap);
			}
		} catch (Exception ignored) {
		}
		StreamEconomyState fallback = new StreamEconomyState();
		fallback.serverOnline = snap.serverOnline;
		fallback.inDungeon = snap.inDungeon;
		fallback.playAlongActive = snap.active;
		fallback.hostDepth = snap.hostDepth;
		fallback.hostHp = snap.hostHp;
		fallback.hostHt = snap.hostHt;
		return fallback;
	}

	private static StreamEconomyState parseEconomyState(JsonObject json, SessionInfo snap) {
		StreamEconomyState eco = new StreamEconomyState();
		eco.serverOnline = snap.serverOnline;
		eco.inDungeon = snap.inDungeon;
		eco.playAlongActive = snap.active;
		eco.hostDepth = snap.hostDepth;
		eco.hostHp = snap.hostHp;
		eco.hostHt = snap.hostHt;

		eco.spendDisabled = getBool(json, "spend_disabled", false);
		eco.doublePointsActive = getBool(json, "double_points_active", false);
		eco.doublePointsSecLeft = getInt(json, "double_points_sec_left", 0);
		eco.deathCostMult = getFloat(json, "death_cost_mult", 1f);
		eco.bankRatioManual = getFloat(json, "bank_ratio_manual", 0.10f);

		if (json.has("costs") && json.get("costs").isJsonObject()) {
			readIntMap(json.getAsJsonObject("costs"), eco.costs);
		}
		if (json.has("monster_costs") && json.get("monster_costs").isJsonObject()) {
			readIntMap(json.getAsJsonObject("monster_costs"), eco.monsterCosts);
		}
		if (json.has("champion_costs") && json.get("champion_costs").isJsonObject()) {
			readIntMap(json.getAsJsonObject("champion_costs"), eco.championCosts);
		}
		if (json.has("free_keys") && json.get("free_keys").isJsonArray()) {
			readStringList(json.getAsJsonArray("free_keys"), eco.freeKeys);
		}
		if (json.has("disabled_commands") && json.get("disabled_commands").isJsonArray()) {
			readStringList(json.getAsJsonArray("disabled_commands"), eco.disabledCommands);
		}
		if (json.has("bestiary") && json.get("bestiary").isJsonObject()) {
			JsonObject b = json.getAsJsonObject("bestiary");
			eco.bestiaryLevel = getInt(b, "level", 1);
			eco.bestiaryZone = getStr(b, "zone", "Sewers");
			eco.bestiaryXp = getInt(b, "bar_xp", 0);
			eco.bestiaryThreshold = getInt(b, "bar_threshold", 1000);
			if (b.has("unlocked_monsters") && b.get("unlocked_monsters").isJsonArray()) {
				readStringList(b.getAsJsonArray("unlocked_monsters"), eco.bestiaryUnlocked);
			}
		}
		if (json.has("user") && json.get("user").isJsonObject()) {
			JsonObject u = json.getAsJsonObject("user");
			eco.linked = getBool(u, "linked", false);
			eco.linkedUsername = getStr(u, "username", "");
			eco.linkedPlatform = getStr(u, "platform", "");
			eco.chatPts = getInt(u, "chat_pts", 0);
			eco.donorPts = getInt(u, "donor_pts", 0);
			eco.totalPts = getInt(u, "total_pts", 0);
			eco.chatCap = getInt(u, "chat_cap", 300);
			eco.fardUsed = getBool(u, "fard_used", false);
			eco.summonCooldownSec = getInt(u, "summon_cooldown_sec", 0);
		}
		return eco;
	}

	private static void readIntMap(JsonObject obj, Map<String, Integer> out) {
		for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
			try {
				out.put(e.getKey(), e.getValue().getAsInt());
			} catch (Exception ignored) {
			}
		}
	}

	private static void readStringList(JsonArray arr, List<String> out) {
		for (JsonElement el : arr) {
			if (el != null && !el.isJsonNull()) {
				out.add(el.getAsString());
			}
		}
	}

	private static boolean getBool(JsonObject o, String k, boolean def) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsBoolean() : def;
	}

	private static int getInt(JsonObject o, String k, int def) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : def;
	}

	private static float getFloat(JsonObject o, String k, float def) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsFloat() : def;
	}

	private static String getStr(JsonObject o, String k, String def) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def;
	}

	private void submitHttp(Runnable task) {
		ExecutorService pool = httpPool;
		if (pool != null && !pool.isShutdown()) {
			try {
				pool.submit(task);
			} catch (Exception ignored) {
			}
		}
	}

	private static JsonObject httpGetJson(String urlStr) throws Exception {
		HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
		conn.setRequestMethod("GET");
		conn.setConnectTimeout(1500);
		conn.setReadTimeout(2500);
		return readJsonResponse(conn);
	}

	private static JsonObject httpPostJson(String urlStr, JsonObject payload) throws Exception {
		HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
		conn.setRequestMethod("POST");
		conn.setDoOutput(true);
		conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
		conn.setConnectTimeout(1500);
		conn.setReadTimeout(12000);
		byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
		try (OutputStream os = conn.getOutputStream()) {
			os.write(bytes);
		}
		return readJsonResponse(conn);
	}

	private static JsonObject readJsonResponse(HttpURLConnection conn) throws Exception {
		int code = conn.getResponseCode();
		InputStream is = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
		if (is == null) return null;
		StringBuilder sb = new StringBuilder();
		try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
			String line;
			while ((line = br.readLine()) != null) {
				sb.append(line);
			}
		} finally {
			conn.disconnect();
		}
		String raw = sb.toString().trim();
		if (raw.isEmpty()) return null;
		JsonElement el = JsonParser.parseString(raw);
		return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
	}

	private static void safeSend(WebSocket conn, String json) {
		if (conn != null && conn.isOpen() && json != null) {
			try {
				conn.send(json);
			} catch (Exception ignored) {
			}
		}
	}

	private void sendToSessionClients(String json) {
		for (WebSocket conn : connToClient.keySet()) {
			if (conn != null && conn.isOpen()) {
				try {
					conn.send(json);
				} catch (Exception ignored) {
				}
			}
		}
	}

	private static String safeHeroClass(String raw) {
		if (raw == null) return "WARRIOR";
		switch (raw.toUpperCase()) {
			case "WARRIOR":
			case "MAGE":
			case "ROGUE":
			case "HUNTRESS":
			case "DUELIST":
			case "CLERIC":
				return raw.toUpperCase();
			default:
				return "WARRIOR";
		}
	}

	private static String safeAction(String raw) {
		if (raw == null) return "IDLE";
		switch (raw.toUpperCase()) {
			case "IDLE":
			case "MOVE":
			case "ATTACK":
			case "OPERATE":
			case "DEAD":
				return raw.toUpperCase();
			default:
				return "IDLE";
		}
	}
}
