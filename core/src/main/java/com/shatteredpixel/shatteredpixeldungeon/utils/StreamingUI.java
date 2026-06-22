/*
 * Shattered Pixel Dungeon
 * Copyright (C) 2014-2026 Evan Debenham
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.shatteredpixel.shatteredpixeldungeon.utils;

import com.badlogic.gdx.Gdx;

import java.util.Map;

/**
 * Bridge from game UI to the desktop streaming WebSocket (item info layout events).
 */
public final class StreamingUI {

	public interface Listener {
		void onItemInfoLayout( Map<String, Object> layout );
	}

	private static volatile Listener listener;

	/** Only the newest deferred notify runs (avoids pre-offset layout + WS spam). */
	private static int layoutNotifyGeneration = 0;

	private StreamingUI() {}

	public static void setListener( Listener l ) {
		listener = l;
	}

	public static void clearListener() {
		listener = null;
	}

	/** Notify subscribers after item info opens, resizes, or closes. Call from the game thread. */
	public static void notifyItemInfoLayout() {
		Listener l = listener;
		if (l == null) return;
		if (Gdx.app == null) {
			l.onItemInfoLayout( ItemInfoLayout.build() );
			return;
		}
		final int gen = ++layoutNotifyGeneration;
		Gdx.app.postRunnable( () -> {
			if (gen != layoutNotifyGeneration) return;
			Listener current = listener;
			if (current != null) {
				current.onItemInfoLayout( ItemInfoLayout.build() );
			}
		} );
	}
}
