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

package com.shatteredpixel.shatteredpixeldungeon.playalong;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.GhostState;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.TombstoneState;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSprite;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSpriteSheet;
import com.shatteredpixel.shatteredpixeldungeon.tiles.DungeonTilemap;
import com.watabou.noosa.Group;
import com.watabou.utils.PointF;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Renders Play Along ghosts and spectral tombstones on the current dungeon floor.
 */
public class PlayAlongGhostLayer extends Group {

	private final Map<String, GhostSprite> ghostSprites = new HashMap<>();
	private final Map<String, ItemSprite> tombSprites = new HashMap<>();

	@Override
	public void update() {
		super.update();

		if (!PlayAlongService.isActive() || Dungeon.level == null) {
			clearAll();
			return;
		}

		List<GhostState> floorGhosts = PlayAlongService.getGhostsForFloor(Dungeon.depth, Dungeon.branch);
		Set<String> seenGhosts = new HashSet<>();

		// Count density per tile to fade crowded chatter clusters on mobile screens
		Map<Integer, Integer> tileDensity = new HashMap<>();
		for (GhostState g : floorGhosts) {
			if (g.userId == null) continue;
			tileDensity.put(g.pos, tileDensity.getOrDefault(g.pos, 0) + 1);
		}

		for (GhostState g : floorGhosts) {
			if (g.userId == null) continue;
			seenGhosts.add(g.userId);
			GhostSprite sprite = ghostSprites.get(g.userId);
			if (sprite == null) {
				sprite = new GhostSprite(g);
				ghostSprites.put(g.userId, sprite);
				add(sprite);
			} else {
				sprite.applyState(g, false);
			}
			boolean crowded = !g.isHost && tileDensity.getOrDefault(g.pos, 0) > 10;
			sprite.setCrowded(crowded);
		}

		Iterator<Map.Entry<String, GhostSprite>> gIt = ghostSprites.entrySet().iterator();
		while (gIt.hasNext()) {
			Map.Entry<String, GhostSprite> e = gIt.next();
			if (!seenGhosts.contains(e.getKey())) {
				e.getValue().killAndErase();
				gIt.remove();
			}
		}

		List<TombstoneState> floorTombs = PlayAlongService.getTombstonesForFloor(Dungeon.depth, Dungeon.branch);
		for (TombstoneState t : floorTombs) {
			if (t.userId == null || tombSprites.containsKey(t.userId)) continue;
			ItemSprite tomb = new ItemSprite(ItemSpriteSheet.TOMB, null);
			PointF p = DungeonTilemap.tileToWorld(Math.max(0, Math.min(Dungeon.level.length() - 1, t.pos)));
			tomb.x = p.x + (DungeonTilemap.SIZE - tomb.width()) / 2f;
			tomb.y = p.y + (DungeonTilemap.SIZE - tomb.height()) / 2f;
			tomb.hardlight(0.55f, 0.9f, 1.3f);
			tomb.alpha(0.75f);
			tombSprites.put(t.userId, tomb);
			addToBack(tomb);
		}
	}

	private void clearAll() {
		if (!ghostSprites.isEmpty()) {
			for (GhostSprite s : ghostSprites.values()) {
				s.killAndErase();
			}
			ghostSprites.clear();
		}
		if (!tombSprites.isEmpty()) {
			for (ItemSprite s : tombSprites.values()) {
				s.killAndErase();
			}
			tombSprites.clear();
		}
	}
}
