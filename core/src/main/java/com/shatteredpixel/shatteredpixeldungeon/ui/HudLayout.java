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

import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.watabou.utils.DeviceCompat;
import com.watabou.utils.RectF;

public class HudLayout {

	public static final float STATUS_HEIGHT = 39;
	/** Visual height covered by the tag indicator stack (4 stacked tags). */
	private static final float TAG_STRIP_HEIGHT = Tag.SIZE * 4;

	private static HudSlot slotStatus;
	private static HudSlot slotLog;
	private static HudSlot slotToolbar;
	private static HudSlot slotInventory;
	private static HudSlot slotMenu;
	private static HudSlot slotTags;
	private static HudSlot slotDanger;

	private static boolean initialized;

	public static boolean isActive() {
		return DeviceCompat.isDesktop() && SPDSettings.interfaceSize() == 2;
	}

	public static void init( GameScene scene ) {
		if (!isActive()) {
			initialized = false;
			return;
		}

		slotStatus = new HudSlot( HudRegion.STATUS );
		slotStatus.addGizmo( scene.statusPane() );

		slotLog = new HudSlot( HudRegion.LOG );
		slotLog.addGizmo( scene.gameLog() );
		slotLog.addGizmo( scene.logBackground() );

		slotToolbar = new HudSlot( HudRegion.TOOLBAR );
		slotToolbar.addGizmo( scene.gameToolbar() );

		slotInventory = new HudSlot( HudRegion.INVENTORY );
		if ( scene.inventoryPane() != null ) {
			slotInventory.addGizmo( scene.inventoryPane() );
		}

		slotMenu = new HudSlot( HudRegion.MENU );
		slotMenu.addGizmo( scene.menuPane() );

		slotTags = new HudSlot( HudRegion.TAGS );
		slotTags.addGizmo( scene.attackIndicator() );
		slotTags.addGizmo( scene.lootIndicator() );
		slotTags.addGizmo( scene.actionIndicator() );
		slotTags.addGizmo( scene.resumeIndicator() );

		slotDanger = new HudSlot( HudRegion.DANGER );
		if ( scene.dangerIndicator() != null ) {
			slotDanger.addGizmo( scene.dangerIndicator() );
		}

		initialized = true;
	}

	public static HudSlot slot( HudRegion region ) {
		switch ( region ) {
			case STATUS: return slotStatus;
			case LOG: return slotLog;
			case TOOLBAR: return slotToolbar;
			case INVENTORY: return slotInventory;
			case MENU: return slotMenu;
			case TAGS: return slotTags;
			case DANGER: return slotDanger;
			default: return null;
		}
	}

	public static HudSlot[] allSlots() {
		return new HudSlot[]{ slotStatus, slotLog, slotToolbar, slotInventory, slotMenu, slotTags, slotDanger };
	}

	public static void apply( GameScene scene ) {
		if (!initialized || !isActive()) return;

		StatusPane status = scene.statusPane();
		slotStatus.setBaseBounds( status.left(), status.top(), status.width(), STATUS_HEIGHT );
		slotStatus.captureBases();
		slotStatus.apply();
		slotStatus.applyScale();

		float logLeft = scene.gameLog().left();
		float logTop = scene.gameLog().top();
		float logW = scene.gameLog().width();
		float logBgH = 45;
		float logH = logBgH + 6;
		slotLog.setBaseBounds( logLeft, logTop - logBgH + 6, logW, logH );
		slotLog.captureBases();
		slotLog.apply();
		slotLog.applyScale();

		Toolbar toolbar = scene.gameToolbar();
		slotToolbar.setBaseBounds( toolbar.left(), toolbar.top(), toolbar.width(), toolbar.height() );
		slotToolbar.captureBases();
		slotToolbar.apply();
		slotToolbar.applyScale();

		InventoryPane inv = scene.inventoryPane();
		if ( inv != null && inv.visible ) {
			slotInventory.setBaseBounds( inv.left(), inv.top(), inv.width(), inv.height() );
			slotInventory.captureBases();
			slotInventory.apply();
			slotInventory.applyScale();
		}

		MenuPane menu = scene.menuPane();
		float menuH = 21;
		slotMenu.setBaseBounds( menu.left(), menu.top(), MenuPane.WIDTH, menuH );
		slotMenu.captureBases();
		slotMenu.apply();
		slotMenu.applyScale();

		// Tag strip: bounding box covers where the four tags would stack even when none are
		// currently visible, so the user can still grab and drag the region in edit mode.
		RectF insets = scene.getCommonInsets();
		boolean tagsOnLeft = SPDSettings.flipTags();
		float tagStripWidth = Tag.SIZE + (tagsOnLeft ? insets.left : insets.right);
		float uiCamWidth = com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene.uiCamera.width;
		float tagStripLeft = tagsOnLeft ? 0 : uiCamWidth - tagStripWidth;
		// Same vertical anchor as layoutHudDefaults: just above toolbar (or status when flipped).
		// Use the slot's BASE top (pre-offset) so that dragging the toolbar/status vertically
		// does not drag the tags with it -- tags own their own region.
		float anchorBottom = tagsOnLeft ? slotStatus.baseTop() : slotToolbar.baseTop();
		float tagStripTop = anchorBottom - TAG_STRIP_HEIGHT;
		slotTags.setBaseBounds( tagStripLeft, tagStripTop, tagStripWidth, TAG_STRIP_HEIGHT );
		slotTags.captureBases();
		slotTags.apply();
		slotTags.applyScale();

		// Danger indicator slot: covers the indicator's natural footprint so it can be grabbed
		// even when no enemies are visible (visible field toggles in update()).
		if ( scene.dangerIndicator() != null ) {
			com.shatteredpixel.shatteredpixeldungeon.ui.DangerIndicator d = scene.dangerIndicator();
			slotDanger.setBaseBounds( d.left(), d.top(),
					Math.max( d.width(), com.shatteredpixel.shatteredpixeldungeon.ui.DangerIndicator.SIZE ),
					com.shatteredpixel.shatteredpixeldungeon.ui.DangerIndicator.HEIGHT );
			slotDanger.captureBases();
			slotDanger.apply();
			slotDanger.applyScale();
		}
	}

	/** Restore region cameras and free their resources. Call from GameScene.destroy(). */
	public static void dispose() {
		HudSlot[] slots = { slotStatus, slotLog, slotToolbar, slotInventory, slotMenu, slotTags, slotDanger };
		for ( HudSlot s : slots ) {
			if ( s != null ) s.disposeCamera();
		}
		initialized = false;
	}

	public static HudRegion regionAt( float x, float y ) {
		if (!initialized) return null;
		// Topmost first for hit testing (menu on top)
		if ( slotMenu != null && slotMenu.contains( x, y ) ) return HudRegion.MENU;
		// Danger sits just below the menu — check before tags/log so it wins on overlap.
		if ( slotDanger != null && slotDanger.contains( x, y ) ) return HudRegion.DANGER;
		// Tags are checked before log/status because they overlap on the right edge.
		if ( slotTags != null && slotTags.contains( x, y ) ) return HudRegion.TAGS;
		if ( slotLog != null && slotLog.contains( x, y ) ) return HudRegion.LOG;
		if ( slotStatus != null && slotStatus.contains( x, y ) ) return HudRegion.STATUS;
		if ( slotInventory != null && slotInventory.contains( x, y ) ) return HudRegion.INVENTORY;
		if ( slotToolbar != null && slotToolbar.contains( x, y ) ) return HudRegion.TOOLBAR;
		return null;
	}

	public static void reset() {
		SPDSettings.resetHudLayout();
		if ( GameScene.getScene() != null ) {
			GameScene.layoutHud();
		}
	}

}
