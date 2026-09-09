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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bridge from game UI to the desktop streaming WebSocket (item info layout and ui_state events).
 */
public final class StreamingUI {

	public interface Listener {
		void onItemInfoLayout( Map<String, Object> layout );
		void onUIState( String scene, List<String> openWindows );
	}

	private static volatile Listener listener;

	/** Only the newest deferred notify runs (avoids pre-offset layout + WS spam). */
	private static int layoutNotifyGeneration = 0;
	private static int uiStateNotifyGeneration = 0;
	private static String lastUIStateScene;
	private static final Set<String> lastUIStateWindows = new HashSet<>();

	private StreamingUI() {}

	public static void setListener( Listener l ) {
		listener = l;
		lastUIStateScene = null;
		lastUIStateWindows.clear();
	}

	public static void clearListener() {
		listener = null;
		lastUIStateScene = null;
		lastUIStateWindows.clear();
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

	/** Immediate scene + open_windows. Dedups until scene or the window set changes. Game thread. */
	public static void notifyUIState() {
		Listener l = listener;
		if (l == null) return;
		if (Gdx.app == null) {
			emitUIState( l );
			return;
		}
		final int gen = ++uiStateNotifyGeneration;
		Gdx.app.postRunnable( () -> {
			if (gen != uiStateNotifyGeneration) return;
			Listener current = listener;
			if (current != null) {
				emitUIState( current );
			}
		} );
	}

	private static void emitUIState( Listener l ) {
		String scene = GameStateSnapshot.currentSceneId();
		List<String> open = GameStateSnapshot.currentOpenWindows();
		if (open == null) open = new ArrayList<>();
		if (scene.equals( lastUIStateScene )
				&& lastUIStateWindows.size() == open.size()
				&& lastUIStateWindows.containsAll( open )) {
			return;
		}
		lastUIStateScene = scene;
		lastUIStateWindows.clear();
		lastUIStateWindows.addAll( open );
		l.onUIState( scene, open );
	}
}
