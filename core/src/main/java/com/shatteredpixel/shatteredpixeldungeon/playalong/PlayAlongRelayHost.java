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

import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;

import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.enums.Opcode;
import org.java_websocket.enums.ReadyState;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.protocols.IProtocol;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLSession;

/**
 * Outbound host link to the room-code relay. Player sockets are presented to
 * {@link PlayAlongServer} as virtual WebSocket connections so gameplay code
 * keeps using the same host-authoritative path.
 *
 * <p>Host frames are a small envelope ({@code t}, {@code c}, {@code d}). The
 * {@code d} field is the unchanged Play Along text the player sent or should
 * receive.
 */
public final class PlayAlongRelayHost {

	public interface Listener {
		void onRoomReady(String code);
		void onRelayDown();
		void onRelayDenied();
	}

	/** Gson shape of the relay multiplexing envelope. */
	public static class Env {
		public String t;
		public String c;
		public String d;
		/** Host-only reclaim secret. Present on private {@code room} frames. */
		public String k;
	}

	/**
	 * Largest relay→host frame we will JSON-parse. Player payloads are capped
	 * at {@link PlayAlongProtocol#MAX_PACKET_LENGTH}; the envelope around one
	 * of those is far smaller than this.
	 */
	private static final int MAX_RELAY_FRAME = 32 * 1024;

	private final PlayAlongServer server;
	private final Listener listener;
	private final ConcurrentHashMap<String, VirtualClient> virtuals = new ConcurrentHashMap<>();

	private volatile WebSocketClient socket;
	private volatile String roomCode = "";
	/** Lets a private room reclaim the same code after a host-link blip. */
	private volatile String hostSecret = "";
	private volatile boolean running = false;
	private final AtomicBoolean reconnecting = new AtomicBoolean(false);
	private int reconnectDelayMs = 2000;

	public PlayAlongRelayHost(PlayAlongServer server, Listener listener) {
		this.server = server;
		this.listener = listener;
	}

	public String roomCode() {
		String code = roomCode;
		return code == null || code.isEmpty() ? null : code;
	}

	public boolean isUp() {
		WebSocketClient ws = socket;
		return ws != null && ws.isOpen() && roomCode() != null;
	}

	public void start() {
		running = true;
		reconnecting.set(false);
		reconnectDelayMs = 2000;
		connect();
	}

	public void stop() {
		running = false;
		reconnecting.set(true);
		WebSocketClient cur = socket;
		socket = null;
		roomCode = "";
		hostSecret = "";
		if (cur != null) {
			try {
				cur.close();
			} catch (Exception ignored) {
			}
		}
		dropVirtuals();
	}

	private void connect() {
		if (!running) return;
		final String token = SPDSettings.playAlongRelayToken();
		final boolean reserved = token != null && !token.isEmpty();
		final String savedCode = roomCode;
		final String savedSecret = hostSecret;
		final boolean resumePrivate = !reserved
				&& savedCode != null && !savedCode.isEmpty()
				&& savedSecret != null && !savedSecret.isEmpty()
				&& !PlayAlongProtocol.isReservedRoom(savedCode);
		final String url = reserved
				? PlayAlongProtocol.relayHostAddress(SPDSettings.PLAY_ALONG_RELAY_ROOM)
				: (resumePrivate
				? PlayAlongProtocol.relayHostAddress(savedCode)
				: PlayAlongProtocol.relayHostAddress(null));
		final URI uri;
		try {
			uri = new URI(url);
		} catch (Exception e) {
			System.err.println("[PlayAlong] Bad relay URL: " + url);
			notifyDown();
			scheduleReconnect();
			return;
		}

		final AtomicBoolean opened = new AtomicBoolean(false);
		WebSocketClient ws = new WebSocketClient(uri) {
			@Override
			public void onOpen(ServerHandshake handshakedata) {
				opened.set(true);
				if (reserved || resumePrivate) {
					Env auth = new Env();
					auth.t = "auth";
					auth.d = reserved ? token : savedSecret;
					try {
						send(PlayAlongProtocol.GSON.toJson(auth));
					} catch (Exception ignored) {
					}
				}
			}

			@Override
			public void onMessage(String message) {
				if (!running || socket != this || message == null) return;
				if (message.length() > MAX_RELAY_FRAME) return;
				try {
					Env env = PlayAlongProtocol.GSON.fromJson(message, Env.class);
					handleEnvelope(env);
				} catch (Exception ignored) {
				}
			}

			@Override
			public void onClose(int code, String reason, boolean remote) {
				if (socket != this) return;
				socket = null;
				dropVirtuals();
				if (!running) return;
				String why = reason == null ? "" : reason.toLowerCase(Locale.ROOT);
				if (why.contains("reserved room disabled") || (reserved && why.contains("unauthorized"))) {
					running = false;
					if (listener != null) listener.onRelayDenied();
					return;
				}
				if (why.contains("unauthorized")) {
					// Grace for this private code has ended. Open a new one.
					roomCode = "";
					hostSecret = "";
				}
				notifyDown();
				scheduleReconnect();
			}

			@Override
			public void onError(Exception ex) {
				// onClose follows and decides whether to reconnect.
				if (socket != this || !running) return;
				if (!opened.get()) notifyDown();
			}
		};
		ws.setConnectionLostTimeout(20);
		WebSocketClient previous = socket;
		socket = ws;
		if (previous != null) {
			try {
				previous.close();
			} catch (Exception ignored) {
			}
		}
		ws.connect();
	}

	private void notifyDown() {
		if (listener != null) listener.onRelayDown();
	}

	private void scheduleReconnect() {
		if (!running) return;
		if (!reconnecting.compareAndSet(false, true)) return;
		final int delay = reconnectDelayMs;
		reconnectDelayMs = Math.min(15000, Math.max(2000, reconnectDelayMs * 2));
		Thread t = new Thread(() -> {
			try {
				Thread.sleep(delay);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				reconnecting.set(false);
				return;
			}
			reconnecting.set(false);
			if (running) connect();
		}, "PlayAlongRelayReconnect");
		t.setDaemon(true);
		t.start();
	}

	private void handleEnvelope(Env env) {
		if (env == null || env.t == null || server == null) return;
		if ("room".equals(env.t)) {
			roomCode = env.d == null ? "" : env.d.trim();
			if (env.k != null && !env.k.isEmpty()) {
				hostSecret = env.k;
			} else if (PlayAlongProtocol.isReservedRoom(roomCode)) {
				hostSecret = "";
			}
			reconnectDelayMs = 2000;
			if (listener != null && !roomCode.isEmpty()) {
				listener.onRoomReady(roomCode);
			}
			return;
		}
		if (env.c == null || env.c.isEmpty()) return;
		if ("open".equals(env.t)) {
			VirtualClient previous = virtuals.remove(env.c);
			if (previous != null) previous.remoteClose();
			VirtualClient created = new VirtualClient(env.c);
			virtuals.put(env.c, created);
			try {
				server.onOpen(created, null);
			} catch (Exception ignored) {
			}
			return;
		}
		VirtualClient client = virtuals.get(env.c);
		if (client == null) return;
		if ("msg".equals(env.t)) {
			if (env.d == null) return;
			if (env.d.length() > PlayAlongProtocol.MAX_PACKET_LENGTH) {
				client.close(1009, "Message too large");
				return;
			}
			try {
				server.onMessage(client, env.d);
			} catch (Exception ignored) {
			}
		} else if ("close".equals(env.t)) {
			client.remoteClose();
		}
	}

	boolean sendEnvelope(Env env) {
		WebSocketClient ws = socket;
		if (ws == null || !ws.isOpen() || env == null) return false;
		try {
			ws.send(PlayAlongProtocol.GSON.toJson(env));
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private void dropVirtuals() {
		for (VirtualClient client : virtuals.values()) {
			client.remoteClose();
		}
		virtuals.clear();
	}

	private final class VirtualClient implements WebSocket {
		private final String id;
		private final AtomicBoolean open = new AtomicBoolean(true);
		private Object attachment;

		private VirtualClient(String id) {
			this.id = id;
		}

		private boolean markClosed() {
			if (!open.compareAndSet(true, false)) return false;
			virtuals.remove(id, this);
			return true;
		}

		void remoteClose() {
			if (!markClosed()) return;
			try {
				server.onClose(this, 1001, "closed", true);
			} catch (Exception ignored) {
			}
		}

		private void localClose(int code, String reason) {
			if (!markClosed()) return;
			Env env = new Env();
			env.t = "close";
			env.c = id;
			sendEnvelope(env);
			try {
				server.onClose(this, code, reason == null ? "" : reason, false);
			} catch (Exception ignored) {
			}
		}

		@Override
		public void close(int code, String message) {
			localClose(code, message);
		}

		@Override
		public void close(int code) {
			localClose(code, "");
		}

		@Override
		public void close() {
			localClose(1000, "");
		}

		@Override
		public void closeConnection(int code, String message) {
			localClose(code, message);
		}

		@Override
		public void send(String text) {
			if (!open.get()) throw new WebsocketNotConnectedException();
			Env env = new Env();
			env.t = "msg";
			env.c = id;
			env.d = text;
			if (!sendEnvelope(env)) {
				throw new WebsocketNotConnectedException();
			}
		}

		@Override
		public void send(ByteBuffer bytes) {
			throw new WebsocketNotConnectedException();
		}

		@Override
		public void send(byte[] bytes) {
			throw new WebsocketNotConnectedException();
		}

		@Override
		public void sendFrame(Framedata framedata) {
		}

		@Override
		public void sendFrame(Collection<Framedata> frames) {
		}

		@Override
		public void sendPing() {
			if (!open.get()) throw new WebsocketNotConnectedException();
		}

		@Override
		public void sendFragmentedFrame(Opcode op, ByteBuffer buffer, boolean fin) {
		}

		@Override
		public boolean hasBufferedData() {
			return false;
		}

		@Override
		public InetSocketAddress getRemoteSocketAddress() {
			return null;
		}

		@Override
		public InetSocketAddress getLocalSocketAddress() {
			return null;
		}

		@Override
		public boolean isOpen() {
			return open.get();
		}

		@Override
		public boolean isClosing() {
			return false;
		}

		@Override
		public boolean isFlushAndClose() {
			return false;
		}

		@Override
		public boolean isClosed() {
			return !open.get();
		}

		@Override
		public Draft getDraft() {
			return null;
		}

		@Override
		public ReadyState getReadyState() {
			return open.get() ? ReadyState.OPEN : ReadyState.CLOSED;
		}

		@Override
		public String getResourceDescriptor() {
			return "/relay/" + id;
		}

		@Override
		public <T> void setAttachment(T attachment) {
			this.attachment = attachment;
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> T getAttachment() {
			return (T) attachment;
		}

		@Override
		public boolean hasSSLSupport() {
			return false;
		}

		@Override
		public SSLSession getSSLSession() {
			throw new IllegalArgumentException("relay virtual socket has no TLS session");
		}

		@Override
		public IProtocol getProtocol() {
			return null;
		}
	}
}
