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
import com.shatteredpixel.shatteredpixeldungeon.utils.GameStateSnapshot;
import com.shatteredpixel.shatteredpixeldungeon.utils.StreamingEvents;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Starts the streaming WebSocket server and a ticker that pushes game state every second.
 */
public final class StreamingBootstrapper {

	private static final Gson GSON = new Gson();
	private static final long TICK_INTERVAL_MS = 1000;

	private static final AtomicReference<StreamingServer> serverRef = new AtomicReference<>();
	private static volatile boolean running = true;

	public static void start(final int port) {
		Thread thread = new Thread(() -> {
			waitForGdx();
			StreamingServer server = new StreamingServer(port);
			serverRef.set(server);
			try {
				server.start();
				System.out.println("[Streaming] WebSocket server started on ws://127.0.0.1:" + port);
			} catch (Exception e) {
				serverRef.compareAndSet(server, null);
				System.err.println("[Streaming] Could not start WebSocket server on port " + port + " (is it in use?): " + e.getMessage());
				return;
			}
			while (running) {
				try {
					Thread.sleep(TICK_INTERVAL_MS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
				if (!running) break;
				if (Gdx.app == null) continue;
				Gdx.app.postRunnable(() -> {
					StreamingServer s = serverRef.get();
					if (s == null) return;
					try {
						Map<String, Object> snapshot = GameStateSnapshot.build();
						String json = GSON.toJson(snapshot);
						s.setLastPayload(json);
						s.broadcastPayload();
						if (StreamingEvents.heroDiedPending) {
							s.broadcastEvent("hero_died", null);
							StreamingEvents.heroDiedPending = false;
						}
						if (StreamingEvents.bossSlainDepthPending >= 0) {
							int depth = StreamingEvents.bossSlainDepthPending;
							StreamingEvents.bossSlainDepthPending = -1;
							s.broadcastEvent("boss_slain", Map.of("depth", depth));
						}
					} catch (Exception e) {
						e.printStackTrace();
						Map<String, Object> fallback = new LinkedHashMap<>();
						fallback.put("source", "shattered-pixel-dungeon");
						Map<String, Object> ui = new LinkedHashMap<>();
						ui.put("scene", "unknown");
						ui.put("open_windows", Collections.emptyList());
						fallback.put("ui", ui);
						String json = GSON.toJson(fallback);
						s.setLastPayload(json);
						s.broadcastPayload();
					}
				});
			}
		}, "StreamingServer");
		thread.setDaemon(true);
		thread.start();
	}

	/** Stops the server in a background thread so the caller is not blocked. */
	public static void stop() {
		running = false;
		StreamingServer s = serverRef.getAndSet(null);
		if (s != null) {
			Thread stopper = new Thread(() -> {
				try {
					s.stop();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}, "StreamingServerStopper");
			stopper.setDaemon(true);
			stopper.start();
		}
	}

	private static void waitForGdx() {
		while (Gdx.app == null) {
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}
}
