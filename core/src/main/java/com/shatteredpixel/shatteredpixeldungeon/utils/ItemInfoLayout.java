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

import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.InventoryPane;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndInfoItem;
import com.watabou.noosa.Game;
import com.watabou.utils.PlatformSupport;
import com.watabou.utils.RectF;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Screen-space bounds for the item info popup and inventory strip, used by streaming / OBS layout.
 * Call only from the main/game thread.
 */
public final class ItemInfoLayout {

	private ItemInfoLayout() {}

	/** Layout map for {@code ui.item_info} in game snapshots and {@code ui_layout} events. */
	public static Map<String, Object> build() {
		Map<String, Object> out = new LinkedHashMap<>();
		WndInfoItem wnd = WndInfoItem.activeInstance();
		if (wnd == null) {
			out.put("open", false);
			out.put("inv_top", inventoryTopY());
			return out;
		}

		int top = wnd.streamTop();
		int bottom = wnd.streamBottom();
		int height = bottom - top;

		out.put("open", true);
		out.put("top", top);
		out.put("bottom", bottom);
		out.put("height", height);
		out.put("inv_top", inventoryTopY());
		return out;
	}

	private static int inventoryTopY() {
		Object scene = ShatteredPixelDungeon.scene();
		if (scene instanceof GameScene) {
			GameScene gs = (GameScene) scene;
			InventoryPane inv = gs.inventoryPane();
			if (inv != null && inv.visible) {
				return (int) inv.top();
			}
		}
		RectF insets = Game.platform.getSafeInsets(PlatformSupport.INSET_BLK);
		float screenH = PixelScene.uiCamera.height;
		return (int) (screenH - InventoryPane.HEIGHT - insets.bottom);
	}
}
