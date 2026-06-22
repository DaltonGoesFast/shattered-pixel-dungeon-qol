/*
 * Shattered Pixel Dungeon
 * Copyright (C) 2014-2026 Evan Debenham
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.shatteredpixel.shatteredpixeldungeon.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.watabou.input.KeyBindings;
import com.watabou.input.KeyEvent;
import com.watabou.input.PointerEvent;
import com.watabou.noosa.Camera;
import com.watabou.noosa.ColorBlock;
import com.watabou.noosa.Group;
import com.watabou.noosa.PointerArea;
import com.watabou.utils.PointF;
import com.watabou.utils.Signal;

public class HudEditMode extends Group {

	private static HudEditMode instance;
	private static boolean active;

	private final ColorBlock[] borders = new ColorBlock[7];
	private static final int[] BORDER_COLORS = {
		0x66FF4444, 0x6644FF44, 0x664444FF, 0x66FFFF44, 0x66FF44FF, 0x6644FFFF, 0x66FF8844
	};

	private PointerArea dragArea;

	private HudRegion selected;
	private HudRegion dragging;
	private PointF dragStart = new PointF();
	private float offsetAtDragStartX;
	private float offsetAtDragStartY;

	private Signal.Listener<KeyEvent> keyListener;

	/** Snap drag offsets to this pixel grid. Hold Shift to disable snapping. */
	private static final int SNAP_GRID = 4;
	/** Snap to default position when within this many pixels. */
	private static final float SNAP_TO_DEFAULT_THRESHOLD = 3f;
	/** Arrow-key nudge step. Hold Shift for the larger step. */
	private static final float NUDGE_STEP = 1f;
	private static final float NUDGE_STEP_FAST = SNAP_GRID;
	/** +/- key scale step (5%). Hold Shift for 10% steps. */
	private static final float SCALE_STEP = 0.05f;
	private static final float SCALE_STEP_FAST = 0.10f;

	/**
	 * Reset the given region (scale to 1, offsets to 0). If {@code region} is null, reset
	 * every customizable HUD region. Triggers a relayout afterward.
	 */
	private void resetRegion( HudRegion region ) {
		if ( region == null ) {
			for ( HudRegion r : HudRegion.values() ) {
				SPDSettings.hudOffsetX( r, 0 );
				SPDSettings.hudOffsetY( r, 0 );
				SPDSettings.hudScale( r, 1f );
			}
			GLog.i( "HUD reset: all regions" );
		} else {
			SPDSettings.hudOffsetX( region, 0 );
			SPDSettings.hudOffsetY( region, 0 );
			SPDSettings.hudScale( region, 1f );
			GLog.i( "HUD reset: " + region.name().toLowerCase() );
		}
		GameScene.layoutHud();
		updateBorders();
	}

	private static float snap( float v, boolean useGrid ) {
		// Snap to default (0 offset) when very close.
		if ( Math.abs( v ) <= SNAP_TO_DEFAULT_THRESHOLD ) return 0;
		if ( !useGrid ) return v;
		return Math.round( v / SNAP_GRID ) * (float) SNAP_GRID;
	}

	public static boolean isEditing() {
		return active;
	}

	public static void toggle() {
		if (!HudLayout.isActive()) return;
		if (active) {
			disable();
		} else {
			enable();
		}
	}

	public static void enable() {
		if (!HudLayout.isActive() || active) return;
		GameScene scene = GameScene.getScene();
		if (scene == null) return;
		active = true;

		instance = new HudEditMode();
		instance.camera = PixelScene.uiCamera;
		scene.addToFront( instance );

		if (scene.cellSelector() != null) {
			scene.cellSelector().enable( false );
		}

		GameScene.layoutHud();
		scene.showHudEditToast( true );
	}

	public static void disable() {
		disable( true );
	}

	/**
	 * @param notifyScene if true (interactive exit), restores cell selection, relayouts the HUD,
	 *                    and shows the "saved" toast. Pass false from {@code GameScene.destroy()}
	 *                    so we don't queue a toast/relayout while the scene is being torn down.
	 */
	public static void disable( boolean notifyScene ) {
		if (!active) return;
		active = false;
		if (instance != null) {
			HudEditMode toDestroy = instance;
			instance = null;
			toDestroy.killAndErase();
			toDestroy.destroy();
		}
		if (!notifyScene) return;
		GameScene scene = GameScene.getScene();
		if (scene != null) {
			if (scene.cellSelector() != null) {
				scene.cellSelector().enable( Dungeon.hero != null && Dungeon.hero.ready );
			}
			GameScene.layoutHud();
			scene.showHudEditToast( false );
		}
	}

	private HudEditMode() {
		for ( int i = 0; i < borders.length; i++ ) {
			borders[i] = new ColorBlock( 1, 1, BORDER_COLORS[i] );
			borders[i].visible = false;
			add( borders[i] );
		}

		dragArea = new PointerArea( 0, 0, 0, 0 ) {
			@Override
			protected void onPointerDown( PointerEvent event ) {
				Camera cam = camera();
				if ( cam == null ) return;
				float x = (event.current.x - cam.x) / cam.zoom;
				float y = (event.current.y - cam.y) / cam.zoom;
				dragging = HudLayout.regionAt( x, y );
				if ( dragging != null ) {
					selected = dragging;
					dragStart.set( x, y );
					offsetAtDragStartX = SPDSettings.hudOffsetX( dragging );
					offsetAtDragStartY = SPDSettings.hudOffsetY( dragging );
					event.handle();
				}
			}

			@Override
			protected void onDrag( PointerEvent event ) {
				if ( dragging == null ) return;
				Camera cam = camera();
				if ( cam == null ) return;
				float x = (event.current.x - cam.x) / cam.zoom;
				float y = (event.current.y - cam.y) / cam.zoom;
				float dx = x - dragStart.x;
				float dy = y - dragStart.y;
				boolean snapToGrid = !Gdx.input.isKeyPressed( Input.Keys.SHIFT_LEFT )
						&& !Gdx.input.isKeyPressed( Input.Keys.SHIFT_RIGHT );
				SPDSettings.hudOffsetX( dragging, snap( offsetAtDragStartX + dx, snapToGrid ) );
				SPDSettings.hudOffsetY( dragging, snap( offsetAtDragStartY + dy, snapToGrid ) );
				GameScene.layoutHud();
				updateBorders();
				event.handle();
			}

			@Override
			protected void onPointerUp( PointerEvent event ) {
				dragging = null;
			}
		};
		dragArea.blockLevel = PointerArea.ALWAYS_BLOCK;
		dragArea.givePointerPriority();
		add( dragArea );

		// Arrow-key nudging on the currently selected region.
		keyListener = new Signal.Listener<KeyEvent>() {
			@Override
			public boolean onSignal( KeyEvent event ) {
				if ( !event.pressed || !active ) return false;
				// Don't intercept the toggle key — GameScene's listener handles F8 exit.
				if ( KeyBindings.getActionForKey( event ) == com.shatteredpixel.shatteredpixeldungeon.SPDAction.HUD_LAYOUT_EDIT ) {
					return false;
				}

				boolean fast = Gdx.input.isKeyPressed( Input.Keys.SHIFT_LEFT )
						|| Gdx.input.isKeyPressed( Input.Keys.SHIFT_RIGHT );

				// Scale adjustments on the selected region.
				HudSlot slot = selected == null ? null : HudLayout.slot( selected );
				switch ( event.code ) {
					case Input.Keys.PLUS:
					case Input.Keys.EQUALS:
					case Input.Keys.NUMPAD_ADD:
						if ( slot != null ) {
							slot.addScale( fast ? SCALE_STEP_FAST : SCALE_STEP );
							GameScene.layoutHud();
							updateBorders();
						}
						return true;
					case Input.Keys.MINUS:
					case Input.Keys.NUMPAD_SUBTRACT:
						if ( slot != null ) {
							slot.addScale( -(fast ? SCALE_STEP_FAST : SCALE_STEP) );
							GameScene.layoutHud();
							updateBorders();
						}
						return true;
					// Reset keys: R (mnemonic for "reset"), Home/End/Insert as alternates.
					// Avoid 0/Backspace/Delete because users with custom keybindings often map
					// those to LEFT_CLICK, which short-circuits the key signal before it reaches
					// this listener (see KeyEvent.processKeyEvents).
					case Input.Keys.R:
					case Input.Keys.HOME:
					case Input.Keys.END:
					case Input.Keys.INSERT:
						resetRegion( selected );
						return true;
				}

				int dx = 0, dy = 0;
				switch ( event.code ) {
					case Input.Keys.LEFT:  dx = -1; break;
					case Input.Keys.RIGHT: dx =  1; break;
					case Input.Keys.UP:    dy = -1; break;
					case Input.Keys.DOWN:  dy =  1; break;
					default: return true; // block all other keys while editing
				}

				if ( selected == null ) return true;
				float step = fast ? NUDGE_STEP_FAST : NUDGE_STEP;
				if ( dx != 0 ) {
					SPDSettings.hudOffsetX( selected, SPDSettings.hudOffsetX( selected ) + dx * step );
				}
				if ( dy != 0 ) {
					SPDSettings.hudOffsetY( selected, SPDSettings.hudOffsetY( selected ) + dy * step );
				}
				GameScene.layoutHud();
				updateBorders();
				return true;
			}
		};
		KeyEvent.addKeyListener( keyListener );

		updateBorders();
	}

	@Override
	public void destroy() {
		if ( keyListener != null ) {
			KeyEvent.removeKeyListener( keyListener );
			keyListener = null;
		}
		// Group.destroy() will destroy all children, which removes dragArea
		// from PointerEvent's listener list via PointerArea.destroy().
		super.destroy();
		dragArea = null;
	}

	@Override
	public void update() {
		super.update();
		updateBorders();
	}

	private void updateBorders() {
		Camera cam = PixelScene.uiCamera;
		if ( cam == null || dragArea == null ) return;
		dragArea.x = 0;
		dragArea.y = 0;
		dragArea.width = cam.width;
		dragArea.height = cam.height;

		HudSlot[] slots = HudLayout.allSlots();
		for ( int i = 0; i < slots.length && i < borders.length; i++ ) {
			HudSlot slot = slots[i];
			if ( slot == null ) {
				borders[i].visible = false;
				continue;
			}
			borders[i].visible = true;
			borders[i].x = slot.displayedLeft();
			borders[i].y = slot.displayedTop();
			borders[i].size( Math.max( 1, slot.displayedWidth() ), Math.max( 1, slot.displayedHeight() ) );
			if ( slot.region == selected ) {
				borders[i].alpha( 0.85f );
			} else {
				borders[i].alpha( 0.45f );
			}
		}
	}

}
