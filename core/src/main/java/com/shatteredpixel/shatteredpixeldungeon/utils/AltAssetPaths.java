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

package com.shatteredpixel.shatteredpixeldungeon.utils;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.Statistics;

/**
 * Builds alternate asset paths by inserting a prefix after the directory (e.g.
 * {@code environment/tiles_sewers.png} → {@code environment/01tiles_sewers.png}).
 * Further packs can use prefixes {@code 02}, {@code 03}, etc.
 */
public final class AltAssetPaths {

	private AltAssetPaths() {}

	public static String prefixedVariant( String standardPath ) {
		return prefixedVariant( standardPath, "01" );
	}

	public static String prefixedVariant( String standardPath, String prefix ) {
		int slash = standardPath.lastIndexOf( '/' );
		if ( slash < 0 ) {
			return prefix + standardPath;
		}
		return standardPath.substring( 0, slash + 1 ) + prefix + standardPath.substring( slash + 1 );
	}

	/**
	 * When the run uses the alt prison environment and the hero is on the main-branch
	 * prison chapter (depths 6–10), mob sprites use {@code sprites/01…} alongside tiles.
	 */
	public static String prisonMobSprite( String standardSpritePath ) {
		if ( !SPDSettings.useAltTileset( Statistics.prisonAltTileset ) ) {
			return standardSpritePath;
		}
		if ( Dungeon.branch != 0 || Dungeon.depth < 6 || Dungeon.depth > 10 ) {
			return standardSpritePath;
		}
		return prefixedVariant( standardSpritePath );
	}

	/**
	 * Custom prison quest tilemaps (e.g. {@code custom_tiles/prison_quest.png}) use the {@code 01} prefix
	 * when the alt prison set is active for the run.
	 */
	public static String prisonCustomTile( String standardPath ) {
		if ( !SPDSettings.useAltTileset( Statistics.prisonAltTileset ) ) {
			return standardPath;
		}
		return prefixedVariant( standardPath );
	}

	/**
	 * Custom cave tilemaps (e.g. {@code custom_tiles/caves_boss.png}) use the {@code 01} prefix
	 * when the alt caves set is active for the run.
	 */
	public static String cavesCustomTile( String standardPath ) {
		if ( !SPDSettings.useAltTileset( Statistics.cavesAltTileset ) ) {
			return standardPath;
		}
		return prefixedVariant( standardPath );
	}

	/**
	 * Same path as {@link com.shatteredpixel.shatteredpixeldungeon.levels.PrisonLevel#terrainFeaturesTex()}
	 * / prison boss, without using {@link com.shatteredpixel.shatteredpixeldungeon.Dungeon#level} — safe while
	 * {@link com.shatteredpixel.shatteredpixeldungeon.tiles.CustomTilemap}s are constructed during save load
	 * ({@code Dungeon.level} is still null).
	 */
	public static String prisonTerrainFeaturesTex() {
		if ( !SPDSettings.useAltTileset( Statistics.prisonAltTileset ) ) {
			return Assets.Environment.TERRAIN_FEATURES;
		}
		return prefixedVariant( Assets.Environment.TERRAIN_FEATURES );
	}

	/**
	 * Alt {@code terrain_features.png} for cave chapters when alt caves tiles apply for this run/settings.
	 * Safe to call when {@code Dungeon.level} is null.
	 */
	public static String cavesTerrainFeaturesTex() {
		if ( !SPDSettings.useAltTileset( Statistics.cavesAltTileset ) ) {
			return Assets.Environment.TERRAIN_FEATURES;
		}
		return prefixedVariant( Assets.Environment.TERRAIN_FEATURES );
	}
}
