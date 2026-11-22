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

import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.SessionInfo;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.WireMessage;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cross-platform WebSocket client for Play Along Mode (Desktop & Android).
 */
public class PlayAlongClient {

	public interface StatusCallback {
		void onResult(SessionInfo info);
	}

	public interface WireMessageCallback {
		void onResult(WireMessage msg);
	}

	public interface ClientListener {
		void onJoinAck(WireMessage msg);
		void onJoinError(String error);
		void onWorldState(WireMessage msg);
		void onHostAnkhPopped(WireMessage msg);
		void onHostDied(WireMessage msg);
		void onPlayerDiedNotice(WireMessage msg);
		void onPlayerWonNotice(WireMessage msg);
		void onMilestoneRewardNotice(WireMessage msg);
		void onStreamMessage(WireMessage msg);
		void onChatCommand(WireMessage msg);
		void onConnectionLost(boolean permanentlyFailed);
	}

	private static final int PING_TIMEOUT_MS = 1600;

	private final ClientListener listener;
	private volatile WebSocketClient socket;
	private volatile boolean intentionalClose = false;
	private volatile boolean reconnecting = false;
	private volatile long disconnectedSinceMs = 0L;

	private volatile String lastAddress;
	private volatile String lastUserId;
	private volatile String lastUsername;
	private volatile String lastClientToken;
	private volatile String lastJoinCode;
	private volatile String lastHeroClass;
	private volatile String lastModVersion;
	private volatile String lastSessionId;
	private volatile int lastDepth = 1;
	private volatile int lastBranch = 0;
	private volatile int lastPos = 0;
	private volatile String lastAction = "IDLE";

	public PlayAlongClient(ClientListener listener) {
		this.listener = listener;
	}

	public void setSpectating(boolean spectating) {
		this.lastAction = spectating ? "DEAD" : "IDLE";
	}

	public static void pingStatus(final String address, final StatusCallback callback) {
		Thread t = new Thread(() -> {
			SessionInfo result = null;
			WebSocketClient pingSocket = null;
			try {
				URI uri = PlayAlongProtocol.buildWebSocketUri(address);
				CountDownLatch latch = new CountDownLatch(1);
				AtomicReference<SessionInfo> ref = new AtomicReference<>();

				pingSocket = new WebSocketClient(uri) {
					@Override
					public void onOpen(ServerHandshake handshakedata) {
						WireMessage ping = new WireMessage();
						ping.type = "status_ping";
						send(PlayAlongProtocol.GSON.toJson(ping));
					}

					@Override
					public void onMessage(String message) {
						try {
							WireMessage msg = PlayAlongProtocol.GSON.fromJson(message, WireMessage.class);
							if (msg != null && "status_pong".equals(msg.type)) {
								ref.set(msg.session);
								latch.countDown();
							}
						} catch (Exception e) {
							System.err.println("[PlayAlongClient] Status ping parse failed: "
									+ e.getClass().getSimpleName() + ": " + e.getMessage());
						}
					}

					@Override
					public void onClose(int code, String reason, boolean remote) {
						latch.countDown();
					}

					@Override
					public void onError(Exception ex) {
						latch.countDown();
					}
				};
				pingSocket.setConnectionLostTimeout(0);
				if (pingSocket.connectBlocking(PING_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
					latch.await(PING_TIMEOUT_MS, TimeUnit.MILLISECONDS);
					result = ref.get();
				}
			} catch (Exception e) {
				System.err.println("[PlayAlongClient] Status ping failed (" + address + "): "
						+ e.getClass().getSimpleName() + ": " + e.getMessage());
			} finally {
				if (pingSocket != null) {
					try {
						pingSocket.close();
					} catch (Exception e) {
						System.err.println("[PlayAlongClient] Failed to parse message: "
								+ e.getClass().getSimpleName() + ": " + e.getMessage());
					}
				}
			}
			if (callback != null) {
				callback.onResult(result);
			}
		}, "PlayAlongStatusPing");
		t.setDaemon(true);
		t.start();
	}

	public static void sendControlRequest(final String address, final WireMessage req,
	                                      final int timeoutMs, final WireMessageCallback callback) {
		Thread t = new Thread(() -> {
			WireMessage result = null;
			WebSocketClient ws = null;
			try {
				URI uri = PlayAlongProtocol.buildWebSocketUri(address);
				CountDownLatch latch = new CountDownLatch(1);
				AtomicReference<WireMessage> ref = new AtomicReference<>();
				final String reqJson = PlayAlongProtocol.GSON.toJson(req);
				final String expectedReqId = req.requestId;

				ws = new WebSocketClient(uri) {
					@Override
					public void onOpen(ServerHandshake handshakedata) {
						send(reqJson);
					}

					@Override
					public void onMessage(String message) {
						try {
							WireMessage msg = PlayAlongProtocol.GSON.fromJson(message, WireMessage.class);
							if (msg != null && msg.type != null
									&& ("stream_state_resp".equals(msg.type)
									|| "stream_link_resp".equals(msg.type)
									|| "stream_cmd_resp".equals(msg.type))) {
								if (expectedReqId == null || msg.requestId == null || expectedReqId.equals(msg.requestId)) {
									ref.set(msg);
									latch.countDown();
								}
							}
						} catch (Exception e) {
							System.err.println("[PlayAlongClient] Control response parse failed: "
									+ e.getClass().getSimpleName() + ": " + e.getMessage());
						}
					}

					@Override
					public void onClose(int code, String reason, boolean remote) {
						latch.countDown();
					}

					@Override
					public void onError(Exception ex) {
						latch.countDown();
					}
				};
				ws.setConnectionLostTimeout(0);
				if (ws.connectBlocking(Math.min(3000, timeoutMs), TimeUnit.MILLISECONDS)) {
					latch.await(timeoutMs, TimeUnit.MILLISECONDS);
					result = ref.get();
				}
			} catch (Exception e) {
				System.err.println("[PlayAlongClient] Control request failed (" + address + "): "
						+ e.getClass().getSimpleName() + ": " + e.getMessage());
			} finally {
				if (ws != null) {
					try {
						ws.close();
					} catch (Exception e) {
						System.err.println("[PlayAlongClient] Failed to parse message: "
								+ e.getClass().getSimpleName() + ": " + e.getMessage());
					}
				}
			}
			if (callback != null) {
				callback.onResult(result);
			}
		}, "PlayAlongControlReq");
		t.setDaemon(true);
		t.start();
	}

	public synchronized void connect(String address, String userId, String username,
	                                 String clientToken, String heroClass, String modVersion, String sessionId,
	                                 int depth, int branch, int pos, boolean isRejoin) {
		connect(address, userId, username, clientToken, "", heroClass, modVersion, sessionId, depth, branch, pos, isRejoin);
	}

	public synchronized void connect(String address, String userId, String username,
	                                 String clientToken, String joinCode, String heroClass, String modVersion, String sessionId,
	                                 int depth, int branch, int pos, boolean isRejoin) {
		closeInternal();
		this.intentionalClose = false;
		this.disconnectedSinceMs = 0L;
		this.lastAddress = address;
		this.lastUserId = userId;
		this.lastUsername = username;
		this.lastClientToken = clientToken;
		this.lastJoinCode = joinCode != null ? joinCode.trim() : "";
		this.lastHeroClass = heroClass;
		this.lastModVersion = modVersion;
		this.lastSessionId = sessionId;
		this.lastDepth = depth;
		this.lastBranch = branch;
		this.lastPos = pos;
		this.lastAction = "IDLE";

		openSocket(isRejoin);
	}

	public synchronized void reconnectIfNeeded(int depth, int branch, int pos) {
		if (intentionalClose || lastAddress == null) return;
		this.lastDepth = depth;
		this.lastBranch = branch;
		this.lastPos = pos;
		WebSocketClient cur = socket;
		if (cur != null && cur.isOpen()) return;
		openSocket(true);
	}

	private void openSocket(final boolean isRejoin) {
		try {
			URI uri = PlayAlongProtocol.buildWebSocketUri(lastAddress);
			final AtomicBoolean opened = new AtomicBoolean(false);
			WebSocketClient ws = new WebSocketClient(uri) {
				@Override
				public void onOpen(ServerHandshake handshakedata) {
					opened.set(true);
					reconnecting = false;

					WireMessage join = new WireMessage();
					join.type = isRejoin ? "rejoin" : "join";
					join.protocolVersion = PlayAlongProtocol.PROTOCOL_VERSION;
					join.sessionId = lastSessionId;
					join.userId = lastUserId;
					join.username = lastUsername;
					join.clientToken = lastClientToken;
					join.joinCode = lastJoinCode;
					join.heroClass = lastHeroClass;
					join.modVersion = lastModVersion;
					join.depth = lastDepth;
					join.branch = lastBranch;
					join.pos = lastPos;
					join.action = lastAction;
					send(PlayAlongProtocol.GSON.toJson(join));
				}

				@Override
				public void onMessage(String message) {
					if (message == null || message.length() > 65536) return;
					try {
						WireMessage msg = PlayAlongProtocol.GSON.fromJson(message, WireMessage.class);
						if (msg == null || msg.type == null || listener == null) return;
						switch (msg.type) {
							case "join_ack":
								disconnectedSinceMs = 0L;
								listener.onJoinAck(msg);
								break;
							case "join_error":
								intentionalClose = true;
								listener.onJoinError(msg.error);
								close();
								break;
							case "world_state":
								listener.onWorldState(msg);
								break;
							case "host_ankh_popped":
								listener.onHostAnkhPopped(msg);
								break;
							case "host_died":
								intentionalClose = true;
								listener.onHostDied(msg);
								break;
							case "player_died_notice":
								listener.onPlayerDiedNotice(msg);
								break;
							case "player_won_notice":
								listener.onPlayerWonNotice(msg);
								break;
							case "milestone_reward_notice":
								listener.onMilestoneRewardNotice(msg);
								break;
							case "stream_state_resp":
							case "stream_link_resp":
							case "stream_cmd_resp":
								listener.onStreamMessage(msg);
								break;
							case "chat_command":
								listener.onChatCommand(msg);
								break;
						}
					} catch (Exception e) {
						System.err.println("[PlayAlongClient] Failed to parse message: "
								+ e.getClass().getSimpleName() + ": " + e.getMessage());
					}
				}

				@Override
				public void onClose(int code, String reason, boolean remote) {
					if (!intentionalClose) {
						scheduleReconnect();
					}
				}

				@Override
				public void onError(Exception ex) {
					if (!opened.get() && !intentionalClose) {
						scheduleReconnect();
					}
				}
			};
			ws.setConnectionLostTimeout(20);
			this.socket = ws;
			ws.connect();
		} catch (Exception e) {
			scheduleReconnect();
		}
	}

	private void scheduleReconnect() {
		if (intentionalClose || reconnecting) return;
		reconnecting = true;
		if (disconnectedSinceMs == 0L) {
			disconnectedSinceMs = System.currentTimeMillis();
		}
		Thread t = new Thread(() -> {
			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				reconnecting = false;
				return;
			}
			reconnecting = false;
			if (intentionalClose) return;
			long elapsed = System.currentTimeMillis() - disconnectedSinceMs;
			if (elapsed > PlayAlongProtocol.CLIENT_RECONNECT_GRACE_MS) {
				intentionalClose = true;
				if (listener != null) listener.onConnectionLost(true);
				return;
			}
			if (listener != null) listener.onConnectionLost(false);
			openSocket(true);
		}, "PlayAlongReconnect");
		t.setDaemon(true);
		t.start();
	}

	public void send(WireMessage msg) {
		if (msg != null && "player_state".equals(msg.type)) {
			if (msg.depth > 0) this.lastDepth = msg.depth;
			this.lastBranch = msg.branch;
			this.lastPos = msg.pos;
			if (msg.action != null && !msg.action.isEmpty()) {
				this.lastAction = msg.action;
			}
		} else if (msg != null && "player_died".equals(msg.type)) {
			this.lastAction = "DEAD";
		}
		WebSocketClient ws = socket;
		if (ws != null && ws.isOpen()) {
			try {
				ws.send(PlayAlongProtocol.GSON.toJson(msg));
			} catch (Exception e) {
				System.err.println("[PlayAlongClient] WebSocket send failed: "
						+ e.getClass().getSimpleName() + ": " + e.getMessage());
			}
		}
	}

	public boolean isConnected() {
		WebSocketClient ws = socket;
		return ws != null && ws.isOpen();
	}

	public synchronized void close() {
		this.intentionalClose = true;
		closeInternal();
	}

	private void closeInternal() {
		WebSocketClient ws = this.socket;
		this.socket = null;
		if (ws != null) {
			try {
				ws.close();
			} catch (Exception ignored) {
			}
		}
	}
}
