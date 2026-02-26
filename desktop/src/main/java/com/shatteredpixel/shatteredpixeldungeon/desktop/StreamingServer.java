/*
 * Pixel Dungeon
 * Copyright (C) 2012-2015 Oleg Dolya
 *
 * Shattered Pixel Dungeon
 * Copyright (C) 2014-2025 Evan Debenham
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 */

package com.shatteredpixel.shatteredpixeldungeon.desktop;

import com.badlogic.gdx.Gdx;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

/**
 * WebSocket server for streaming game state to OBS, Streamer.bot, and custom clients.
 */
public class StreamingServer extends WebSocketServer {

	private static final int DEFAULT_PORT = 5001;

	private final AtomicReference<String> lastPayload = new AtomicReference<>("{}");

	public StreamingServer(int port) {
		super(new InetSocketAddress("127.0.0.1", port));
		setConnectionLostTimeout(30);
	}

	public static int defaultPort() {
		return DEFAULT_PORT;
	}

	public void setLastPayload(String json) {
		if (json != null) lastPayload.set(json);
	}

	public void broadcastPayload() {
		String json = lastPayload.get();
		if (json != null && !json.isEmpty()) broadcast(json);
	}

	@Override
	public void onOpen(org.java_websocket.WebSocket conn, org.java_websocket.handshake.ClientHandshake handshake) {
		String json = lastPayload.get();
		if (json != null && !json.isEmpty()) conn.send(json);
	}

	@Override
	public void onClose(org.java_websocket.WebSocket conn, int code, String reason, boolean remote) {}

	@Override
	public void onMessage(org.java_websocket.WebSocket conn, String message) {
		try {
			JsonObject obj = new Gson().fromJson(message, JsonObject.class);
			if (obj == null) return;
			String cmd = obj.has("command") ? obj.get("command").getAsString() : null;
			String requestId = obj.has("request_id") ? obj.get("request_id").getAsString() : null;
			String username = obj.has("username") ? obj.get("username").getAsString() : null;
			String usernameFinal = (username != null && !username.isEmpty()) ? username.trim() : null;

			if ("spawn".equals(cmd)) {
				String monster = obj.has("monster") ? obj.get("monster").getAsString() : null;
				if (monster == null || monster.isEmpty()) return;
				String monsterFinal = monster.trim().toLowerCase();
				Gdx.app.postRunnable(() -> {
					String err = StreamingCommandHandler.handleSpawn(monsterFinal, usernameFinal);
					boolean ok = (err == null);
					if (requestId != null && !requestId.isEmpty()) {
						JsonObject resp = new JsonObject();
						resp.addProperty("type", "spawn_result");
						resp.addProperty("request_id", requestId);
						resp.addProperty("success", ok);
						if (err != null) resp.addProperty("error", err);
						conn.send(resp.toString());
					}
				});
			} else if ("gold".equals(cmd)) {
				int amount = obj.has("amount") ? obj.get("amount").getAsInt() : 5;
				int amountFinal = Math.max(1, Math.min(100, amount));
				Gdx.app.postRunnable(() -> {
					String err = StreamingCommandHandler.handleDropGold(amountFinal, usernameFinal);
					boolean ok = (err == null);
					if (requestId != null && !requestId.isEmpty()) {
						JsonObject resp = new JsonObject();
						resp.addProperty("type", "gold_result");
						resp.addProperty("request_id", requestId);
						resp.addProperty("success", ok);
						if (err != null) resp.addProperty("error", err);
						conn.send(resp.toString());
					}
				});
			} else if ("gas".equals(cmd)) {
				Gdx.app.postRunnable(() -> {
					String result = StreamingCommandHandler.handleSpawnGas(usernameFinal);
					boolean ok = (result != null && !result.startsWith("ERR:"));
					String gasName = ok ? result : null;
					String err = (result != null && result.startsWith("ERR:")) ? result.substring(4) : null;
					if (requestId != null && !requestId.isEmpty()) {
						JsonObject resp = new JsonObject();
						resp.addProperty("type", "gas_result");
						resp.addProperty("request_id", requestId);
						resp.addProperty("success", ok);
						if (gasName != null) resp.addProperty("gas_name", gasName);
						if (err != null) resp.addProperty("error", err);
						conn.send(resp.toString());
					}
				});
			} else if ("curse".equals(cmd)) {
				String slot = obj.has("slot") ? obj.get("slot").getAsString() : null;
				if (slot == null || slot.isEmpty()) return;
				String slotFinal = slot.trim().toLowerCase();
				Gdx.app.postRunnable(() -> {
					String result = StreamingCommandHandler.handleCurse(slotFinal, usernameFinal);
					boolean ok = (result != null && !result.startsWith("ERR:"));
					String itemName = ok ? result : null;
					String err = (result != null && result.startsWith("ERR:")) ? result.substring(4) : null;
					if (requestId != null && !requestId.isEmpty()) {
						JsonObject resp = new JsonObject();
						resp.addProperty("type", "curse_result");
						resp.addProperty("request_id", requestId);
						resp.addProperty("success", ok);
						if (itemName != null) resp.addProperty("item_name", itemName);
						if (err != null) resp.addProperty("error", err);
						conn.send(resp.toString());
					}
				});
			} else if ("scroll".equals(cmd)) {
				Gdx.app.postRunnable(() -> {
					String result = StreamingCommandHandler.handleRandomScroll(usernameFinal);
					boolean ok = (result != null && !result.startsWith("ERR:"));
					String scrollName = ok ? result : null;
					String err = (result != null && result.startsWith("ERR:")) ? result.substring(4) : null;
					if (requestId != null && !requestId.isEmpty()) {
						JsonObject resp = new JsonObject();
						resp.addProperty("type", "scroll_result");
						resp.addProperty("request_id", requestId);
						resp.addProperty("success", ok);
						if (scrollName != null) resp.addProperty("scroll_name", scrollName);
						if (err != null) resp.addProperty("error", err);
						conn.send(resp.toString());
					}
				});
			} else if ("wand".equals(cmd)) {
				int tier = -1;
				if (obj.has("tier")) {
					try {
						tier = obj.get("tier").getAsInt();
						if (tier < 0 || tier > 3) tier = -1;
					} catch (Exception ignored) {}
				}
				final int tierFinal = tier;
				Gdx.app.postRunnable(() -> {
					StreamingCommandHandler.handleCursedWand(usernameFinal, tierFinal, (ok, effectName, rarity, err) -> {
						if (requestId != null && !requestId.isEmpty()) {
							JsonObject resp = new JsonObject();
							resp.addProperty("type", "wand_result");
							resp.addProperty("request_id", requestId);
							resp.addProperty("success", ok);
							if (effectName != null) resp.addProperty("effect_name", effectName);
							resp.addProperty("rarity", rarity);
							if (err != null) resp.addProperty("error", err);
							conn.send(resp.toString());
						}
					});
				});
			}
		} catch (Exception ignored) {}
	}

	@Override
	public void onError(org.java_websocket.WebSocket conn, Exception ex) {
		if (ex != null) System.err.println("[StreamingServer] " + ex.getMessage());
	}

	@Override
	public void onStart() {}
}
