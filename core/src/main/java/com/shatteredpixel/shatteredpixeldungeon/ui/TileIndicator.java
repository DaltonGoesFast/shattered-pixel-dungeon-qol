/*
 * Pixel Dungeon
 * Copyright (C) 2012-2015 Oleg Dolya
 *
 * Shattered Pixel Dungeon
 * Copyright (C) 2014-2025 Evan Debenham
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

package com.shatteredpixel.shatteredpixeldungeon.ui;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.tiles.DungeonTilemap;
import com.watabou.input.PointerEvent;
import com.watabou.noosa.Camera;
import com.watabou.noosa.Image;
import com.watabou.noosa.ui.Component;
import com.watabou.utils.PointF;

/**
 * Shows which tile the pointer is over so the player knows exactly what they will click.
 */
public class TileIndicator extends Component {

	private final DungeonTilemap tilemap;
	private Image image;

	public TileIndicator( DungeonTilemap tilemap ) {
		this.tilemap = tilemap;
		visible = false;
	}

	@Override
	protected void createChildren() {
		image = new Image( Assets.Interfaces.TILE_INDICATOR );
		add( image );
	}

	@Override
	public void update() {
		super.update();
		// Scale to tile size once texture is loaded
		if ( image.width > 0 && image.scale.x == 1f ) {
			float s = DungeonTilemap.SIZE / (float) image.width;
			image.scale.set( s );
		}
		PointF hover = PointerEvent.currentHoverPos();
		if ( hover != null && SPDSettings.tileIndicator() ) {
			int cell = tilemap.screenToTile( (int) hover.x, (int) hover.y );
			if ( Dungeon.level != null && Dungeon.level.insideMap( cell ) ) {
				PointF p = DungeonTilemap.tileToWorld( cell );
				image.x = p.x;
				image.y = p.y;
				visible = true;
			} else {
				visible = false;
			}
		} else {
			visible = false;
		}
	}

	@Override
	public Camera camera() {
		return Camera.main;
	}
}
