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

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.ChatCommandPayload;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.StreamEconomyState;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.WireMessage;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.streaming.MirroredChatCommandExecutor;
import com.shatteredpixel.shatteredpixeldungeon.streaming.StreamingCommandHandler;
import com.watabou.noosa.Game;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bridges Play Along clients to stream economy commands, account linking,
 * and shared-RNG chat command mirroring via {@link MirroredChatCommandExecutor}.
 */
public final class PlayAlongStreamBridge {

	public static final int MAX_QUEUED_CHAT_COMMANDS = 32;

	private volatile StreamEconomyState cachedEconomy = null;
	private final ConcurrentHashMap<String, PlayAlongClient.WireMessageCallback> pendingStreamRequests = new ConcurrentHashMap<>();
	private final CopyOnWriteArrayList<ChatCommandPayload> pendingChatCommands = new CopyOnWriteArrayList<>();
	private final AtomicInteger clientMirrorDrops = new AtomicInteger(0);

	private volatile MirroredChatCommandExecutor commandExecutor = StreamingCommandHandler::executeMirroredChatCommand;

	public void setCommandExecutor(MirroredChatCommandExecutor executor) {
		this.commandExecutor = executor != null
				? executor
				: StreamingCommandHandler::executeMirroredChatCommand;
	}

	public StreamEconomyState getCachedEconomy() {
		return cachedEconomy;
	}

	public int getClientMirrorDrops() {
		return clientMirrorDrops.get();
	}

	public void clearPendingChatCommands() {
		pendingChatCommands.clear();
	}

	public void requestStreamEconomy(PlayAlongService.Role role, PlayAlongClient activeClient,
	                                 String address, PlayAlongGhostStore ghostStore,
	                                 final PlayAlongService.EconomyCallback callback) {
		WireMessage req = new WireMessage();
		req.type = "stream_state_req";
		req.requestId = UUID.randomUUID().toString();
		req.clientToken = SPDSettings.streamClientToken();
		dispatchStreamControlRequest(role, activeClient, address, req, 4000, msg -> {
			StreamEconomyState eco = (msg != null && msg.economy != null) ? msg.economy : null;
			if (eco != null) {
				applyEconomyUpdate(role, ghostStore, eco);
			}
			if (callback != null) {
				callback.onResult(eco);
			}
		});
	}

	public void requestStreamLinkCode(PlayAlongService.Role role, PlayAlongClient activeClient,
	                                  String address, PlayAlongGhostStore ghostStore,
	                                  final PlayAlongClient.WireMessageCallback callback) {
		WireMessage req = new WireMessage();
		req.type = "stream_link_req";
		req.requestId = UUID.randomUUID().toString();
		req.clientToken = SPDSettings.streamClientToken();
		dispatchStreamControlRequest(role, activeClient, address, req, 4500, msg -> {
			if (msg != null && msg.economy != null) {
				applyEconomyUpdate(role, ghostStore, msg.economy);
			}
			if (callback != null) {
				callback.onResult(msg);
			}
		});
	}

	public void sendStreamCommand(PlayAlongService.Role role, PlayAlongClient activeClient,
	                              String address, PlayAlongGhostStore ghostStore,
	                              final String rawCommand, final PlayAlongClient.WireMessageCallback callback) {
		WireMessage req = new WireMessage();
		req.type = "stream_cmd_req";
		req.requestId = UUID.randomUUID().toString();
		req.clientToken = SPDSettings.streamClientToken();
		req.rawCommand = rawCommand != null ? rawCommand.trim() : "";
		dispatchStreamControlRequest(role, activeClient, address, req, 13000, msg -> {
			if (msg != null && msg.economy != null) {
				applyEconomyUpdate(role, ghostStore, msg.economy);
			}
			if (callback != null) {
				callback.onResult(msg);
			}
		});
	}

	private void dispatchStreamControlRequest(PlayAlongService.Role role, PlayAlongClient activeClient,
	                                          String address, final WireMessage req, final int timeoutMs,
	                                          final PlayAlongClient.WireMessageCallback callback) {
		if ((role == PlayAlongService.Role.CLIENT || role == PlayAlongService.Role.SPECTATOR)
				&& activeClient != null && activeClient.isConnected()) {
			final String reqId = req.requestId;
			pendingStreamRequests.put(reqId, callback);
			activeClient.send(req);
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
		PlayAlongClient.sendControlRequest(address, req, timeoutMs, callback);
	}

	public void handleIncomingStreamMessage(PlayAlongService.Role role, PlayAlongGhostStore ghostStore, WireMessage msg) {
		if (msg == null) return;
		if (msg.economy != null) {
			applyEconomyUpdate(role, ghostStore, msg.economy);
		}
		if (msg.requestId != null) {
			PlayAlongClient.WireMessageCallback cb = pendingStreamRequests.remove(msg.requestId);
			if (cb != null) {
				cb.onResult(msg);
			}
		}
	}

	public void applyEconomyUpdate(PlayAlongService.Role role, PlayAlongGhostStore ghostStore, StreamEconomyState eco) {
		if (eco == null) return;
		cachedEconomy = eco;
		if (role == PlayAlongService.Role.NONE && eco.inDungeon && ghostStore != null) {
			ghostStore.setHostPosition(eco.hostDepth, ghostStore.getHostBranch(), ghostStore.getHostPos());
		}
		if (eco.linked && eco.linkedUsername != null && !eco.linkedUsername.trim().isEmpty()) {
			String uname = eco.linkedUsername.trim();
			if (SPDSettings.playAlongUsername().isEmpty()) {
				SPDSettings.playAlongUsername(PlayAlongProtocol.sanitizeUsername(uname, "01"));
			}
		}
	}

	public void handleIncomingChatCommand(WireMessage msg, String clientUsername,
	                                      java.util.function.Supplier<PlayAlongService.Role> roleSupplier) {
		if (msg == null || msg.chatCommand == null) return;
		final ChatCommandPayload p = msg.chatCommand;
		final boolean isConfig = "spawn_scale_config".equals(p.command);
		if (!isConfig) {
			if (roleSupplier.get() != PlayAlongService.Role.CLIENT) return;
			if (isSelfChatSender(p.username, clientUsername)) {
				return;
			}
		}
		Game.runOnRenderThread(() -> {
			if (isConfig) {
				commandExecutor.executeMirroredChatCommand(p);
				return;
			}
			if (roleSupplier.get() != PlayAlongService.Role.CLIENT) return;
			if (Dungeon.hero == null || Dungeon.level == null || !(Game.scene() instanceof GameScene)) {
				if (pendingChatCommands.size() < MAX_QUEUED_CHAT_COMMANDS) {
					pendingChatCommands.add(p);
				} else {
					clientMirrorDrops.incrementAndGet();
				}
				return;
			}
			if (Dungeon.hero.isAlive()) {
				commandExecutor.executeMirroredChatCommand(p);
			}
		});
	}

	public void drainPendingChatCommandsIfReady(PlayAlongService.Role role) {
		if (role != PlayAlongService.Role.CLIENT
				|| Dungeon.hero == null || Dungeon.level == null
				|| !Dungeon.hero.isAlive()
				|| !(Game.scene() instanceof GameScene)
				|| pendingChatCommands.isEmpty()) {
			return;
		}
		List<ChatCommandPayload> drained = new ArrayList<>(pendingChatCommands);
		pendingChatCommands.clear();
		for (ChatCommandPayload p : drained) {
			if (!Dungeon.hero.isAlive()) break;
			commandExecutor.executeMirroredChatCommand(p);
		}
	}

	public boolean isSelfChatSender(String rawSender, String clientUsername) {
		String sender = normalizeChatUsername(rawSender);
		if (sender.isEmpty()) return false;
		if (sender.equals(normalizeChatUsername(clientUsername))
				|| sender.equals(normalizeChatUsername(SPDSettings.playAlongUsername()))) {
			return true;
		}
		StreamEconomyState eco = cachedEconomy;
		return eco != null && eco.linked && sender.equals(normalizeChatUsername(eco.linkedUsername));
	}

	public static String normalizeChatUsername(String raw) {
		if (raw == null) return "";
		String s = raw.trim();
		while (s.startsWith("@")) {
			s = s.substring(1).trim();
		}
		return s.toLowerCase(Locale.ROOT);
	}
}
