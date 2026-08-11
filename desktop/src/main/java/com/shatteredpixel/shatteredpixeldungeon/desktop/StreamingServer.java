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
import com.shatteredpixel.shatteredpixeldungeon.utils.GameStateSnapshot;

import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * WebSocket server for streaming game state to OBS, Streamer.bot, and custom clients.
 */
public class StreamingServer extends WebSocketServer {

	private static final int DEFAULT_PORT = 5001;

	private static final Gson GSON = new Gson();
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

	/** Broadcast a streaming event (hero_died, boss_slain) to all connected clients. */
	public void broadcastEvent(String type, Map<String, Object> extra) {
		JsonObject obj = new JsonObject();
		obj.addProperty("type", type);
		obj.addProperty("source", "shattered-pixel-dungeon");
		if (extra != null) {
			for (Map.Entry<String, Object> e : extra.entrySet()) {
				Object v = e.getValue();
				if (v instanceof Number) obj.addProperty(e.getKey(), (Number) v);
				else if (v instanceof Boolean) obj.addProperty(e.getKey(), (Boolean) v);
				else if (v instanceof String) obj.addProperty(e.getKey(), (String) v);
			}
		}
		broadcast(obj.toString());
	}

	/** Immediate item info bounds for OBS inventory crop (nested under {@code item_info}). */
	public void broadcastUILayout( Map<String, Object> itemInfo ) {
		JsonObject obj = new JsonObject();
		obj.addProperty( "type", "ui_layout" );
		obj.addProperty( "source", "shattered-pixel-dungeon" );
		if ( itemInfo != null ) {
			obj.add( "item_info", GSON.toJsonTree( itemInfo ) );
		}
		broadcast( obj.toString() );
	}

	/** Echo chatter name on command results so read-only clients (e.g. Godot) don’t rely only on UDP pairing. */
	private void addChatter(JsonObject resp, String chatter) {
		if (chatter != null && !chatter.isEmpty()) {
			resp.addProperty("username", chatter);
		}
	}

	@Override
	public void onOpen(org.java_websocket.WebSocket conn, org.java_websocket.handshake.ClientHandshake handshake) {
		// Send a fresh snapshot from the game thread so new clients get current run, not stale lastPayload
		if (Gdx.app != null) {
			Gdx.app.postRunnable(() -> {
				try {
					Map<String, Object> snapshot = GameStateSnapshot.build();
					String json = GSON.toJson(snapshot);
					if (json != null && !json.isEmpty()) conn.send(json);
				} catch (Throwable t) {
					String json = lastPayload.get();
					if (json != null && !json.isEmpty()) conn.send(json);
				}
			});
		} else {
			String json = lastPayload.get();
			if (json != null && !json.isEmpty()) conn.send(json);
		}
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

			if ("ping".equals(cmd)) {
				if (requestId != null && !requestId.isEmpty()) {
					JsonObject resp = new JsonObject();
					resp.addProperty("type", "ping_result");
					resp.addProperty("request_id", requestId);
					resp.addProperty("success", true);
					resp.addProperty("version", "QoL-3.3.8");
					addChatter(resp, usernameFinal);
					broadcast(resp.toString());
				}
			} else if ("spawn".equals(cmd)) {
				String monster = obj.has("monster") ? obj.get("monster").getAsString() : null;
				if (monster == null || monster.isEmpty()) return;
				String monsterFinal = monster.trim().toLowerCase();
				Gdx.app.postRunnable(() -> {
					String err = null;
					try {
						err = StreamingCommandHandler.handleSpawn(monsterFinal, usernameFinal);
					} catch (Throwable t) {
						err = (t.getMessage() != null) ? t.getMessage() : "Unknown error";
					}
					boolean ok = (err == null);
					if (requestId != null && !requestId.isEmpty()) {
						JsonObject resp = new JsonObject();
						resp.addProperty("type", "spawn_result");
						resp.addProperty("request_id", requestId);
						resp.addProperty("success", ok);
						if (err != null) resp.addProperty("error", err);
						if (ok) resp.addProperty("monster", monsterFinal);
						addChatter(resp, usernameFinal);
						broadcast(resp.toString());
					}
				});
			} else if ("champion".equals(cmd)) {
				String monster = obj.has("monster") ? obj.get("monster").getAsString() : null;
				if (monster == null || monster.isEmpty()) return;
				String monsterFinal = monster.trim().toLowerCase();
				Gdx.app.postRunnable(() -> {
					String err = null;
					try {
						err = StreamingCommandHandler.handleSpawnChampion(monsterFinal, usernameFinal);
					} catch (Throwable t) {
						err = (t.getMessage() != null) ? t.getMessage() : "Unknown error";
					}
					boolean ok = (err == null);
					if (requestId != null && !requestId.isEmpty()) {
						JsonObject resp = new JsonObject();
						resp.addProperty("type", "champion_result");
						resp.addProperty("request_id", requestId);
						resp.addProperty("success", ok);
						if (err != null) resp.addProperty("error", err);
						if (ok) resp.addProperty("monster", monsterFinal);
						addChatter(resp, usernameFinal);
						broadcast(resp.toString());
					}
				});
			} else if ("gold".equals(cmd)) {
				int amount = obj.has("amount") ? obj.get("amount").getAsInt() : 5;
				final int amountFinal = amount;
				Gdx.app.postRunnable(() -> {
					String err = null;
					if (amountFinal < 1 || amountFinal > 100) {
						err = "Amount must be 1-100";
					} else {
						try {
							err = StreamingCommandHandler.handleDropGold(amountFinal, usernameFinal);
						} catch (Throwable t) {
							err = (t.getMessage() != null) ? t.getMessage() : "Unknown error";
						}
					}
					boolean ok = (err == null);
					if (requestId != null && !requestId.isEmpty()) {
						JsonObject resp = new JsonObject();
						resp.addProperty("type", "gold_result");
						resp.addProperty("request_id", requestId);
						resp.addProperty("success", ok);
						if (err != null) resp.addProperty("error", err);
						addChatter(resp, usernameFinal);
						broadcast(resp.toString());
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
						addChatter(resp, usernameFinal);
						broadcast(resp.toString());
					}
				});
			} else if ("curse".equals(cmd)) {
				String slot = obj.has("slot") ? obj.get("slot").getAsString() : null;
				String slotFinal = (slot != null) ? slot.trim().toLowerCase() : "";
				int classKitDuration = 100;
				if (obj.has("class_kit_curse_duration_turns")) {
					try {
						classKitDuration = obj.get("class_kit_curse_duration_turns").getAsInt();
					} catch (Exception ignored) {}
				}
				final int classKitDurationFinal = classKitDuration;
				Gdx.app.postRunnable(() -> {
					StreamingCommandHandler.CurseStreamResult result = slotFinal.isEmpty()
							? StreamingCommandHandler.curseEquippedRandom(usernameFinal, classKitDurationFinal)
							: StreamingCommandHandler.curseEquipped(slotFinal, usernameFinal, classKitDurationFinal);
					boolean ok = (result.errorMessage == null);
					String itemName = ok ? result.itemName : null;
					String err = result.errorMessage;
					if (requestId != null && !requestId.isEmpty()) {
						JsonObject resp = new JsonObject();
						resp.addProperty("type", "curse_result");
						resp.addProperty("request_id", requestId);
						resp.addProperty("success", ok);
						if (itemName != null) resp.addProperty("item_name", itemName);
						if (ok && result.temporary) {
							resp.addProperty("temporary", true);
							resp.addProperty("duration_turns", result.durationTurns);
						}
						if (err != null) resp.addProperty("error", err);
						addChatter(resp, usernameFinal);
						broadcast(resp.toString());
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
						addChatter(resp, usernameFinal);
						broadcast(resp.toString());
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
							addChatter(resp, usernameFinal);
							broadcast(resp.toString());
						}
					});
				});
			} else if ("buff".equals(cmd)) {
				Gdx.app.postRunnable(() -> {
					String result = StreamingCommandHandler.handleChatBuff(usernameFinal);
					boolean ok = (result != null && !result.startsWith("ERR:"));
					String buffName = ok ? result : null;
					String err = (result != null && result.startsWith("ERR:")) ? result.substring(4) : null;
					if (requestId != null && !requestId.isEmpty()) {
						JsonObject resp = new JsonObject();
						resp.addProperty("type", "buff_result");
						resp.addProperty("request_id", requestId);
						resp.addProperty("success", ok);
						if (buffName != null) resp.addProperty("buff_name", buffName);
						if (err != null) resp.addProperty("error", err);
						addChatter(resp, usernameFinal);
						broadcast(resp.toString());
					}
				});
			} else if ("debuff".equals(cmd)) {
				Gdx.app.postRunnable(() -> {
					String result = StreamingCommandHandler.handleChatDebuff(usernameFinal);
					boolean ok = (result != null && !result.startsWith("ERR:"));
					String debuffName = ok ? result : null;
					String err = (result != null && result.startsWith("ERR:")) ? result.substring(4) : null;
					if (requestId != null && !requestId.isEmpty()) {
						JsonObject resp = new JsonObject();
						resp.addProperty("type", "debuff_result");
						resp.addProperty("request_id", requestId);
						resp.addProperty("success", ok);
						if (debuffName != null) resp.addProperty("debuff_name", debuffName);
						if (err != null) resp.addProperty("error", err);
						addChatter(resp, usernameFinal);
						broadcast(resp.toString());
					}
				});
			} else if ("trap".equals(cmd)) {
				Gdx.app.postRunnable(() -> {
					String result = StreamingCommandHandler.handleSpawnTrap(usernameFinal);
					boolean ok = (result != null && !result.startsWith("ERR:"));
					String trapName = ok ? result : null;
					String err = (result != null && result.startsWith("ERR:")) ? result.substring(4) : null;
					if (requestId != null && !requestId.isEmpty()) {
						JsonObject resp = new JsonObject();
						resp.addProperty("type", "trap_result");
						resp.addProperty("request_id", requestId);
						resp.addProperty("success", ok);
						if (trapName != null) resp.addProperty("trap_name", trapName);
						if (err != null) resp.addProperty("error", err);
						addChatter(resp, usernameFinal);
						broadcast(resp.toString());
					}
				});
			} else if ("plant".equals(cmd)) {
				Gdx.app.postRunnable(() -> {
					String result = StreamingCommandHandler.handlePlantRandom(usernameFinal);
					boolean ok = (result != null && !result.startsWith("ERR:"));
					String plantName = ok ? result : null;
					String err = (result != null && result.startsWith("ERR:")) ? result.substring(4) : null;
					if (requestId != null && !requestId.isEmpty()) {
						JsonObject resp = new JsonObject();
						resp.addProperty("type", "plant_result");
						resp.addProperty("request_id", requestId);
						resp.addProperty("success", ok);
						if (plantName != null) resp.addProperty("plant_name", plantName);
						if (err != null) resp.addProperty("error", err);
						addChatter(resp, usernameFinal);
						broadcast(resp.toString());
					}
				});
			} else if ("bomb".equals(cmd)) {
				Gdx.app.postRunnable(() -> {
					String result = StreamingCommandHandler.handleSpawnBomb(usernameFinal);
					boolean ok = (result != null && !result.startsWith("ERR:"));
					String bombName = ok ? result : null;
					String err = (result != null && result.startsWith("ERR:")) ? result.substring(4) : null;
					if (requestId != null && !requestId.isEmpty()) {
						JsonObject resp = new JsonObject();
						resp.addProperty("type", "bomb_result");
						resp.addProperty("request_id", requestId);
						resp.addProperty("success", ok);
						if (bombName != null) resp.addProperty("bomb_name", bombName);
						if (err != null) resp.addProperty("error", err);
						addChatter(resp, usernameFinal);
						broadcast(resp.toString());
					}
				});
			} else if ("transmute".equals(cmd)) {
				Gdx.app.postRunnable(() -> {
					StreamingCommandHandler.TransmuteStreamResult tr = StreamingCommandHandler.transmuteForStream(usernameFinal);
					boolean ok = (tr.errorMessage == null);
					String err = tr.errorMessage;
					if (requestId != null && !requestId.isEmpty()) {
						JsonObject resp = new JsonObject();
						resp.addProperty("type", "transmute_result");
						resp.addProperty("request_id", requestId);
						resp.addProperty("success", ok);
						if (ok && tr.resultName != null) resp.addProperty("item_name", tr.resultName);
						if (ok && tr.originalName != null) resp.addProperty("original_item_name", tr.originalName);
						if (err != null) resp.addProperty("error", err);
						addChatter(resp, usernameFinal);
						broadcast(resp.toString());
					}
				});
			} else if ("summon_bee".equals(cmd)) {
				Gdx.app.postRunnable(() -> {
					String result = StreamingCommandHandler.handleSummonBee(usernameFinal);
					boolean ok = (result != null && !result.startsWith("ERR:"));
					String allyName = ok ? result : null;
					String err = (result != null && result.startsWith("ERR:")) ? result.substring(4) : null;
					if (requestId != null && !requestId.isEmpty()) {
						JsonObject resp = new JsonObject();
						resp.addProperty("type", "summon_bee_result");
						resp.addProperty("request_id", requestId);
						resp.addProperty("success", ok);
						if (allyName != null) resp.addProperty("ally_name", allyName);
						if (err != null) resp.addProperty("error", err);
						addChatter(resp, usernameFinal);
						broadcast(resp.toString());
					}
				});
			} else if ("ward".equals(cmd)) {
				Gdx.app.postRunnable(() -> {
					String result = StreamingCommandHandler.handleSpawnWard(usernameFinal);
					boolean ok = (result != null && !result.startsWith("ERR:"));
					String wardName = ok ? result : null;
					String err = (result != null && result.startsWith("ERR:")) ? result.substring(4) : null;
					if (requestId != null && !requestId.isEmpty()) {
						JsonObject resp = new JsonObject();
						resp.addProperty("type", "ward_result");
						resp.addProperty("request_id", requestId);
						resp.addProperty("success", ok);
						if (wardName != null) resp.addProperty("ward_name", wardName);
						if (err != null) resp.addProperty("error", err);
						addChatter(resp, usernameFinal);
						broadcast(resp.toString());
					}
				});
			} else if ("heal".equals(cmd)) {
				Gdx.app.postRunnable(() -> {
					String result = StreamingCommandHandler.handleChatHeal(usernameFinal);
					boolean ok = (result != null && !result.startsWith("ERR:"));
					String buffName = ok ? result : null;
					String err = (result != null && result.startsWith("ERR:")) ? result.substring(4) : null;
					if (requestId != null && !requestId.isEmpty()) {
						JsonObject resp = new JsonObject();
						resp.addProperty("type", "heal_result");
						resp.addProperty("request_id", requestId);
						resp.addProperty("success", ok);
						if (buffName != null) resp.addProperty("buff_name", buffName);
						if (err != null) resp.addProperty("error", err);
						addChatter(resp, usernameFinal);
						broadcast(resp.toString());
					}
				});
			} else if ("cleanse".equals(cmd)) {
				Gdx.app.postRunnable(() -> {
					String result = StreamingCommandHandler.handleChatCleanse(usernameFinal);
					boolean ok = (result != null && !result.startsWith("ERR:"));
					String buffName = ok ? result : null;
					String err = (result != null && result.startsWith("ERR:")) ? result.substring(4) : null;
					if (requestId != null && !requestId.isEmpty()) {
						JsonObject resp = new JsonObject();
						resp.addProperty("type", "cleanse_result");
						resp.addProperty("request_id", requestId);
						resp.addProperty("success", ok);
						if (buffName != null) resp.addProperty("buff_name", buffName);
						if (err != null) resp.addProperty("error", err);
						addChatter(resp, usernameFinal);
						broadcast(resp.toString());
					}
				});
			} else if ("dew".equals(cmd)) {
				Gdx.app.postRunnable(() -> {
					String result = StreamingCommandHandler.handleChatDew(usernameFinal);
					boolean ok = (result != null && !result.startsWith("ERR:"));
					String itemName = ok ? result : null;
					String err = (result != null && result.startsWith("ERR:")) ? result.substring(4) : null;
					if (requestId != null && !requestId.isEmpty()) {
						JsonObject resp = new JsonObject();
						resp.addProperty("type", "dew_result");
						resp.addProperty("request_id", requestId);
						resp.addProperty("success", ok);
						if (itemName != null) resp.addProperty("item_name", itemName);
						if (err != null) resp.addProperty("error", err);
						addChatter(resp, usernameFinal);
						broadcast(resp.toString());
					}
				});
			} else if ("corrupt_ally".equals(cmd)) {
				Gdx.app.postRunnable(() -> {
					String result = StreamingCommandHandler.handleChatCorruptAlly(usernameFinal);
					boolean ok = (result != null && !result.startsWith("ERR:"));
					String mobName = ok ? result : null;
					String err = (result != null && result.startsWith("ERR:")) ? result.substring(4) : null;
					if (requestId != null && !requestId.isEmpty()) {
						JsonObject resp = new JsonObject();
						resp.addProperty("type", "corrupt_ally_result");
						resp.addProperty("request_id", requestId);
						resp.addProperty("success", ok);
						if (mobName != null) resp.addProperty("mob_name", mobName);
						if (err != null) resp.addProperty("error", err);
						addChatter(resp, usernameFinal);
						broadcast(resp.toString());
					}
				});
			} else if ("hex".equals(cmd)) {
				Gdx.app.postRunnable(() -> {
					String result = StreamingCommandHandler.handleChatHex(usernameFinal);
					boolean ok = (result != null && !result.startsWith("ERR:"));
					String debuffName = ok ? result : null;
					String err = (result != null && result.startsWith("ERR:")) ? result.substring(4) : null;
					if (requestId != null && !requestId.isEmpty()) {
						JsonObject resp = new JsonObject();
						resp.addProperty("type", "hex_result");
						resp.addProperty("request_id", requestId);
						resp.addProperty("success", ok);
						if (debuffName != null) resp.addProperty("debuff_name", debuffName);
						if (err != null) resp.addProperty("error", err);
						addChatter(resp, usernameFinal);
						broadcast(resp.toString());
					}
				});
			} else if ("degrade".equals(cmd)) {
				Gdx.app.postRunnable(() -> {
					String result = StreamingCommandHandler.handleChatDegrade(usernameFinal);
					boolean ok = (result != null && !result.startsWith("ERR:"));
					String debuffName = ok ? result : null;
					String err = (result != null && result.startsWith("ERR:")) ? result.substring(4) : null;
					if (requestId != null && !requestId.isEmpty()) {
						JsonObject resp = new JsonObject();
						resp.addProperty("type", "degrade_result");
						resp.addProperty("request_id", requestId);
						resp.addProperty("success", ok);
						if (debuffName != null) resp.addProperty("debuff_name", debuffName);
						if (err != null) resp.addProperty("error", err);
						addChatter(resp, usernameFinal);
						broadcast(resp.toString());
					}
				});
			} else if ("sabotage".equals(cmd)) {
				Gdx.app.postRunnable(() -> {
					String result = StreamingCommandHandler.handleChatSabotage(usernameFinal);
					boolean ok = (result != null && !result.startsWith("ERR:"));
					String buffName = ok ? result : null;
					String err = (result != null && result.startsWith("ERR:")) ? result.substring(4) : null;
					if (requestId != null && !requestId.isEmpty()) {
						JsonObject resp = new JsonObject();
						resp.addProperty("type", "sabotage_result");
						resp.addProperty("request_id", requestId);
						resp.addProperty("success", ok);
						if (buffName != null) resp.addProperty("buff_name", buffName);
						if (err != null) resp.addProperty("error", err);
						addChatter(resp, usernameFinal);
						broadcast(resp.toString());
					}
				});
			} else if ("ring_of_wealth".equals(cmd)) {
				Gdx.app.postRunnable(() -> {
					String result = StreamingCommandHandler.handleRingOfWealthDrop(usernameFinal);
					boolean ok = (result != null && !result.startsWith("ERR:"));
					String detail = ok ? result : null;
					String err = (result != null && result.startsWith("ERR:")) ? result.substring(4) : null;
					if (requestId != null && !requestId.isEmpty()) {
						JsonObject resp = new JsonObject();
						resp.addProperty("type", "ring_of_wealth_result");
						resp.addProperty("request_id", requestId);
						resp.addProperty("success", ok);
						if (detail != null) resp.addProperty("detail", detail);
						if (err != null) resp.addProperty("error", err);
						addChatter(resp, usernameFinal);
						broadcast(resp.toString());
					}
				});
			} else if ("streamer_search_items".equals(cmd)) {
				String query = obj.has("query") ? obj.get("query").getAsString() : null;
				int limit = 12;
				if (obj.has("limit")) {
					try { limit = obj.get("limit").getAsInt(); } catch (Exception ignored) {}
				}
				final String queryFinal = query;
				final int limitFinal = limit;
				Gdx.app.postRunnable(() -> {
					String result = StreamingCommandHandler.handleStreamerSearchItems(queryFinal, limitFinal);
					boolean ok = (result != null && !result.startsWith("ERR:"));
					String detail = ok ? result : null;
					String err = (result != null && result.startsWith("ERR:")) ? result.substring(4) : null;
					if (requestId != null && !requestId.isEmpty()) {
						JsonObject resp = new JsonObject();
						resp.addProperty("type", "streamer_debug_result");
						resp.addProperty("request_id", requestId);
						resp.addProperty("success", ok);
						if (detail != null) resp.addProperty("detail", detail);
						if (err != null) resp.addProperty("error", err);
						addChatter(resp, usernameFinal);
						broadcast(resp.toString());
					}
				});
			} else if ("streamer_apply_buff".equals(cmd)) {
				String buffName = obj.has("buff") ? obj.get("buff").getAsString() : null;
				float duration = 0f;
				if (obj.has("duration")) {
					try { duration = obj.get("duration").getAsFloat(); } catch (Exception ignored) {}
				}
				final String buffNameFinal = buffName;
				final float durationFinal = duration;
				Gdx.app.postRunnable(() -> {
					String result = StreamingCommandHandler.handleStreamerApplyBuff(
							buffNameFinal, durationFinal, usernameFinal);
					boolean ok = (result != null && !result.startsWith("ERR:"));
					String detail = ok ? result : null;
					String err = (result != null && result.startsWith("ERR:")) ? result.substring(4) : null;
					if (requestId != null && !requestId.isEmpty()) {
						JsonObject resp = new JsonObject();
						resp.addProperty("type", "streamer_debug_result");
						resp.addProperty("request_id", requestId);
						resp.addProperty("success", ok);
						if (detail != null) resp.addProperty("detail", detail);
						if (err != null) resp.addProperty("error", err);
						addChatter(resp, usernameFinal);
						broadcast(resp.toString());
					}
				});
			} else if ("streamer_apply_debuff".equals(cmd)) {
				String debuffName = obj.has("debuff") ? obj.get("debuff").getAsString() : null;
				float duration = 0f;
				if (obj.has("duration")) {
					try { duration = obj.get("duration").getAsFloat(); } catch (Exception ignored) {}
				}
				final String debuffNameFinal = debuffName;
				final float durationFinal = duration;
				Gdx.app.postRunnable(() -> {
					String result = StreamingCommandHandler.handleStreamerApplyDebuff(
							debuffNameFinal, durationFinal, usernameFinal);
					boolean ok = (result != null && !result.startsWith("ERR:"));
					String detail = ok ? result : null;
					String err = (result != null && result.startsWith("ERR:")) ? result.substring(4) : null;
					if (requestId != null && !requestId.isEmpty()) {
						JsonObject resp = new JsonObject();
						resp.addProperty("type", "streamer_debug_result");
						resp.addProperty("request_id", requestId);
						resp.addProperty("success", ok);
						if (detail != null) resp.addProperty("detail", detail);
						if (err != null) resp.addProperty("error", err);
						addChatter(resp, usernameFinal);
						broadcast(resp.toString());
					}
				});
			} else if ("streamer_give_item".equals(cmd)) {
				String itemName = obj.has("item") ? obj.get("item").getAsString() : null;
				int quantity = 1;
				int level = 0;
				if (obj.has("quantity")) {
					try { quantity = obj.get("quantity").getAsInt(); } catch (Exception ignored) {}
				}
				if (obj.has("level")) {
					try { level = obj.get("level").getAsInt(); } catch (Exception ignored) {}
				}
				final String itemNameFinal = itemName;
				final int quantityFinal = quantity;
				final int levelFinal = level;
				Gdx.app.postRunnable(() -> {
					String result = StreamingCommandHandler.handleStreamerGiveItem(
							itemNameFinal, quantityFinal, levelFinal, usernameFinal);
					boolean ok = (result != null && !result.startsWith("ERR:"));
					String detail = ok ? result : null;
					String err = (result != null && result.startsWith("ERR:")) ? result.substring(4) : null;
					if (requestId != null && !requestId.isEmpty()) {
						JsonObject resp = new JsonObject();
						resp.addProperty("type", "streamer_debug_result");
						resp.addProperty("request_id", requestId);
						resp.addProperty("success", ok);
						if (detail != null) resp.addProperty("detail", detail);
						if (err != null) resp.addProperty("error", err);
						addChatter(resp, usernameFinal);
						broadcast(resp.toString());
					}
				});
			} else if (cmd != null && cmd.startsWith("streamer_")) {
				final String cmdFinal = cmd;
				Gdx.app.postRunnable(() -> {
					String result = StreamingCommandHandler.handleStreamerDebug(cmdFinal, usernameFinal);
					boolean ok = (result != null && !result.startsWith("ERR:"));
					String detail = ok ? result : null;
					String err = (result != null && result.startsWith("ERR:")) ? result.substring(4) : null;
					if (requestId != null && !requestId.isEmpty()) {
						JsonObject resp = new JsonObject();
						resp.addProperty("type", "streamer_debug_result");
						resp.addProperty("request_id", requestId);
						resp.addProperty("success", ok);
						if (detail != null) resp.addProperty("detail", detail);
						if (err != null) resp.addProperty("error", err);
						addChatter(resp, usernameFinal);
						broadcast(resp.toString());
					}
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
