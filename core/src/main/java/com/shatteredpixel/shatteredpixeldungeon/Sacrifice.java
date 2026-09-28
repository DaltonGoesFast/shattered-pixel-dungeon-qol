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
 * along with this program. If not, see <http://www.gnu.org/licenses/>
 */

package com.shatteredpixel.shatteredpixeldungeon;

import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mimic;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.Gold;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.Torch;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.DriedRose;
import com.shatteredpixel.shatteredpixeldungeon.items.journal.DocumentPage;
import com.shatteredpixel.shatteredpixeldungeon.items.journal.Guidebook;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.Key;
import com.shatteredpixel.shatteredpixeldungeon.items.quest.CeremonialCandle;
import com.shatteredpixel.shatteredpixeldungeon.levels.RegularLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.Room;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.connection.ConnectionRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.standard.StandardRoom;
import com.watabou.utils.Random;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

/**
 * Sacrifice pact: ungated floor loot is generated normally, then taken off the ground and
 * carried by enemies on that floor. Killing a carrier drops its cargo alongside native loot.
 * Special, secret, and quest rooms, tombs, crystal chests, and shops keep their loot.
 */
public class Sacrifice {

	/** Main dungeon regular floors only; boss floors, vault, and mines are untouched. */
	public static boolean active(){
		return Dungeon.isModified(Modifiers.SACRIFICE) && Dungeon.branch == 0;
	}

	/** Keys, torches, quest items, and journal pages stay on the floor. */
	public static boolean isCarriable(Item item){
		return item != null
				&& !(item instanceof Key)
				&& !(item instanceof Torch)
				&& !(item instanceof CeremonialCandle)
				&& !(item instanceof Guidebook)
				&& !(item instanceof DocumentPage)
				&& !(item instanceof DriedRose.Petal);
	}

	/** Standard or connection rooms, excluding quest rooms that extend StandardRoom. */
	private static boolean isOpenRoom(Room room){
		if (!(room instanceof StandardRoom) && !(room instanceof ConnectionRoom)) return false;
		return !room.getClass().getName().contains(".rooms.quest.");
	}

	/** Removes walk-up loot (heaps, chests, suspicious-chest mimics) from open rooms. */
	public static ArrayList<Item> takeOpenRoomLoot(RegularLevel level){
		ArrayList<Item> taken = new ArrayList<>();

		for (Heap heap : level.heaps.valueList()){
			if (heap.type != Heap.Type.HEAP
					&& heap.type != Heap.Type.CHEST
					&& heap.type != Heap.Type.SKELETON) continue;
			if (!isOpenRoom(level.room(heap.pos))) continue;
			Iterator<Item> it = heap.items.iterator();
			while (it.hasNext()){
				Item item = it.next();
				if (isCarriable(item)){
					taken.add(item);
					it.remove();
				}
			}
			if (heap.items.isEmpty()){
				level.heaps.remove(heap.pos);
			}
		}

		Iterator<Mob> mobs = level.mobs.iterator();
		while (mobs.hasNext()){
			Mob mob = mobs.next();
			if (!(mob instanceof Mimic)) continue;
			Mimic mimic = (Mimic) mob;
			if (mimic.items == null || !isOpenRoom(level.room(mimic.pos))) continue;
			Iterator<Item> it = mimic.items.iterator();
			while (it.hasNext()){
				Item item = it.next();
				if (isCarriable(item)){
					taken.add(item);
					it.remove();
				}
			}
			if (mimic.items.isEmpty()){
				mobs.remove();
			}
		}

		return taken;
	}

	private static boolean canCarry(RegularLevel level, Mob mob){
		if (mob.alignment != Char.Alignment.ENEMY) return false;
		if (mob instanceof Mimic) return false;
		if (Char.hasProp(mob, Char.Property.BOSS) || Char.hasProp(mob, Char.Property.MINIBOSS)) return false;
		return isOpenRoom(level.room(mob.pos));
	}

	/**
	 * Deals the hoard one item per carrier, wrapping around when items outnumber carriers.
	 * Returns items that found no carrier.
	 */
	public static ArrayList<Item> assignToCarriers(RegularLevel level, ArrayList<Item> hoard){
		ArrayList<Mob> carriers = new ArrayList<>();
		for (Mob mob : level.mobs){
			if (canCarry(level, mob)) carriers.add(mob);
		}
		if (carriers.isEmpty() || hoard.isEmpty()){
			return hoard;
		}

		// mobs is a HashSet; sort first so seeded levelgen deals the same way every time
		Collections.sort(carriers, (a, b) -> Integer.compare(a.pos, b.pos));
		Random.shuffle(carriers);
		for (int i = 0; i < hoard.size(); i++){
			Item item = hoard.get(i);
			if (!(item instanceof Gold)) Modifiers.markCommandLoot(item);
			carriers.get(i % carriers.size()).addSacrificeCargo(item);
		}
		return new ArrayList<>();
	}

}
