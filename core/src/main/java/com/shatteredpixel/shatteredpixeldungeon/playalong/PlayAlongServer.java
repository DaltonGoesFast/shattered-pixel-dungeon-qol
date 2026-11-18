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

import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.GhostState;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.SessionInfo;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.TombstoneState;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.WipeEvent;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.WireMessage;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
	}

	private static final int MAX_CLIENTS = 100;
	private static final int RATE_LIMIT_PER_SEC = 8;

	private static class ClientRecord {
		String userId;
		String username;
		WebSocket conn;
		GhostState ghost;
		boolean alive = true;
		boolean won = false;
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
	}

	public void stopServer() {
		if (ticker != null) {
			ticker.shutdownNow();
			ticker = null;
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

		ClientRecord rec = clientsByUserId.get(userId);
		if (rec == null) {
			rec = new ClientRecord();
			rec.userId = userId;
			rec.username = safeName;
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
			if (!isRejoin) {
				rec.username = safeName;
				rec.ghost.username = safeName;
				rec.ghost.heroClass = safeHeroClass(msg.heroClass);
				rec.alive = true;
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
		dirty = true;
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
		if (src == null) {
			copy.active = false;
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
