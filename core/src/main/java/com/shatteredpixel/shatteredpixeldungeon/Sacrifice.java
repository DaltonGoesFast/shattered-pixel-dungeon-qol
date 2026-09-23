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

import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mimic;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.Generator;
import com.shatteredpixel.shatteredpixeldungeon.items.Gold;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.Artifact;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.TrinketCatalyst;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;
import com.shatteredpixel.shatteredpixeldungeon.levels.RegularLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.Room;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.secret.SecretRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.CrystalChoiceRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.CrystalVaultRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.SpecialRoom;
import com.watabou.utils.Random;

import java.util.LinkedHashMap;
import java.util.ListIterator;

/**
 * Sacrifice pact: no random floor equipment / chests / mimics / gold piles;
 * key-locked special rooms replace gear with gold (catalyst kept; secrets untouched);
 * kills pay native loot (with LimitedDrops) and equipment on a per-floor decaying curve.
 * About 1/3 of successful native drops become a normal floor gold pile instead.
 */
public class Sacrifice {

	/** Chance a successful native drop is replaced by floor-scale gold. */
	public static final float NATIVE_TO_GOLD = 1/3f;

	/** Potion / scroll / seed / stone / food — no equipment or gold decks burned. */
	public static Item randomFloorItem(){
		LinkedHashMap<Generator.Category, Float> probs = new LinkedHashMap<>();
		probs.put(Generator.Category.POTION, 8f);
		probs.put(Generator.Category.SCROLL, 8f);
		probs.put(Generator.Category.SEED, 1f);
		probs.put(Generator.Category.STONE, 1f);
		probs.put(Generator.Category.FOOD, 1f);
		Generator.Category cat = Random.chances(probs);
		if (cat == null){
			return Generator.random(Generator.Category.POTION);
		}
		if (cat == Generator.Category.SEED){
			return Generator.randomUsingDefaults(cat);
		}
		return Generator.random(cat);
	}

	/** A normal depth-scaled gold pile (replaces some native drops; no floor gold piles). */
	public static Gold floorGold(){
		Gold gold = new Gold();
		gold.random();
		return gold;
	}

	/** True when this native drop should become gold instead. */
	public static boolean replaceNativeWithGold(){
		return Random.Float() < NATIVE_TO_GOLD;
	}

	/**
	 * After levelgen: in iron-key special rooms, replace weapons/armor/wands/rings/artifacts
	 * with gold. Trinket catalyst, crystal vault/choice rooms, and secret rooms keep their loot.
	 */
	public static void convertLockedRoomLoot(RegularLevel level){
		if (!Dungeon.isModified(Modifiers.SACRIFICE)) return;

		for (Heap heap : level.heaps.valueList()){
			// Crystal chests and crystal puzzle rooms keep their prizes
			if (heap.type == Heap.Type.CRYSTAL_CHEST) continue;
			if (!shouldConvertLockedRoom(level.room(heap.pos))) continue;
			ListIterator<Item> it = heap.items.listIterator();
			while (it.hasNext()){
				Item item = it.next();
				if (isReplaceableRoomGear(item)){
					it.set(floorGold());
				}
			}
		}

		for (Mob mob : level.mobs){
			if (!(mob instanceof Mimic)) continue;
			Mimic mimic = (Mimic) mob;
			if (mimic.items == null || !shouldConvertLockedRoom(level.room(mimic.pos))) continue;
			ListIterator<Item> it = mimic.items.listIterator();
			while (it.hasNext()){
				Item item = it.next();
				if (isReplaceableRoomGear(item)){
					it.set(floorGold());
				}
			}
		}
	}

	/** Weapon / armor / wand / ring / artifact — not catalyst, bombs, or consumables. */
	public static boolean isReplaceableRoomGear(Item item){
		if (item == null || item instanceof TrinketCatalyst) return false;
		return item instanceof Weapon
				|| item instanceof Armor
				|| item instanceof Wand
				|| item instanceof Ring
				|| item instanceof Artifact;
	}

	/**
	 * Iron-key locked specials only. Secrets, crystal vault/choice, and crystal-door pits keep gear.
	 */
	private static boolean shouldConvertLockedRoom(Room room){
		if (!(room instanceof SpecialRoom) || room instanceof SecretRoom) return false;
		if (room instanceof CrystalVaultRoom || room instanceof CrystalChoiceRoom) return false;
		Room.Door door = ((SpecialRoom) room).entrance();
		return door != null && door.type == Room.Door.Type.LOCKED;
	}

	/**
	 * Chance for kill-paid gear on this floor's next paying death.
	 * Advances {@link com.shatteredpixel.shatteredpixeldungeon.levels.Level#sacrificeKillIndex}.
	 */
	public static float nextEquipChance(){
		int killIndex = Dungeon.level.sacrificeKillIndex;
		Dungeon.level.sacrificeKillIndex++;
		if (killIndex == 0) return 1f;
		if (killIndex == 1) return 0.5f;
		if (killIndex == 2) return 0.25f;
		return 0.1f;
	}

	/** Weapon / armor / thrown / wand / ring / artifact, upgrade ladder up to +3. */
	public static Item genEquipment(){
		for (int tries = 0; tries < 20; tries++){
			Generator.Category cat = rollGearCategory();
			Item item;
			if (cat == Generator.Category.WEAPON){
				item = Generator.randomWeapon();
			} else if (cat == Generator.Category.ARMOR){
				item = Generator.randomArmor();
			} else if (cat == Generator.Category.MISSILE){
				item = Generator.randomMissile();
			} else if (cat == Generator.Category.ARTIFACT){
				item = Generator.random(Generator.Category.ARTIFACT);
				if (item == null){
					item = Generator.randomUsingDefaults(Generator.Category.RING);
				}
			} else {
				item = Generator.random(cat);
			}

			if (item == null) continue;
			if (Challenges.isItemBlocked(item)) continue;
			if (item instanceof Bomb) continue;
			// Artifacts stay even though they are not upgradable.
			if (!item.isUpgradable() && !(item instanceof Artifact)) continue;

			if (item.isUpgradable()){
				int n = rollUpgradeLevel();
				if (item instanceof Wand){
					Wand w = (Wand) item;
					w.level(n);
					w.curCharges = w.maxCharges;
				} else {
					item.level(n);
				}
			}

			return item;
		}
		return null;
	}

	/** Matches Generator two-deck weights for gear categories. */
	private static Generator.Category rollGearCategory(){
		LinkedHashMap<Generator.Category, Float> probs = new LinkedHashMap<>();
		if (Random.Int(2) == 0){
			probs.put(Generator.Category.WEAPON, 2f);
			probs.put(Generator.Category.ARMOR, 2f);
			probs.put(Generator.Category.MISSILE, 1f);
			probs.put(Generator.Category.WAND, 1f);
			probs.put(Generator.Category.RING, 1f);
			probs.put(Generator.Category.ARTIFACT, 0f);
		} else {
			probs.put(Generator.Category.WEAPON, 2f);
			probs.put(Generator.Category.ARMOR, 1f);
			probs.put(Generator.Category.MISSILE, 2f);
			probs.put(Generator.Category.WAND, 1f);
			probs.put(Generator.Category.RING, 0f);
			probs.put(Generator.Category.ARTIFACT, 1f);
		}
		Generator.Category cat = Random.chances(probs);
		return cat != null ? cat : Generator.Category.WEAPON;
	}

	/** +0 50%, +1 30%, +2 15%, +3 5%. */
	private static int rollUpgradeLevel(){
		float roll = Random.Float();
		if (roll < 0.50f) return 0;
		if (roll < 0.80f) return 1;
		if (roll < 0.95f) return 2;
		return 3;
	}

}
