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

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.effects.Speck;
import com.shatteredpixel.shatteredpixeldungeon.effects.Transmuting;
import com.shatteredpixel.shatteredpixeldungeon.items.BrokenSeal;
import com.shatteredpixel.shatteredpixeldungeon.items.EquipableItem;
import com.shatteredpixel.shatteredpixeldungeon.items.Generator;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.KindOfWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.ClothArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.LeatherArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.MailArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.PlateArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.ScaleArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.Bag;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Food;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfStrength;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.brews.Brew;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.elixirs.Elixir;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.ExoticPotion;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.PotionOfMastery;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfTransmutation;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfUpgrade;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.ExoticScroll;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.ScrollOfEnchantment;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.Runestone;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.Trinket;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MagesStaff;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.MissileWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.darts.Dart;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.darts.TippedDart;
import com.shatteredpixel.shatteredpixeldungeon.journal.Catalog;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.plants.Plant;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.watabou.utils.Bundle;
import com.watabou.utils.Random;
import com.watabou.utils.Reflection;

import java.util.ArrayList;

/**
 * Metamorphosis / Enigma: remake kit on first descent to each new deepest main-path floor
 * (including run start when deepestFloor goes 0→1).
 */
public class FirstDescent {

	private static final String PENDING = "first_descent_pending";

	private static final Class<?>[] REGULAR_ARMOR = {
			ClothArmor.class,
			LeatherArmor.class,
			MailArmor.class,
			ScaleArmor.class,
			PlateArmor.class
	};

	/** Set when deepestFloor increases; applied on main thread in GameScene. */
	public static boolean pending = false;

	public static void reset(){
		pending = false;
	}

	public static void store( Bundle bundle ){
		bundle.put( PENDING, pending );
	}

	public static void restore( Bundle bundle ){
		pending = bundle.contains( PENDING ) && bundle.getBoolean( PENDING );
	}

	/** Call from GameScene after the hero sprite exists. */
	public static void applyIfPending(){
		if (!pending) return;
		pending = false;

		Hero hero = Dungeon.hero;
		if (hero == null || !hero.isAlive()) return;

		boolean metaOn = Dungeon.isModified(Modifiers.METAMORPHOSIS);
		boolean enigmaOn = Dungeon.isModified(Modifiers.ENIGMA);
		if (!metaOn && !enigmaOn) return;

		Random.pushGenerator(Dungeon.seedCurDepth() + 0xF1D57DE5CL);
		Modifiers.suppressCommandWindow = true;
		try {
			Item sampleOld = null;
			Item sampleNew = null;
			boolean metaChanged = false;
			boolean enigmaChanged = false;

			if (metaOn){
				ArrayList<Item> toRemake = collectMetamorphosisTargets(hero);
				for (Item item : toRemake){
					Item result = remakeGear(item);
					if (result == null) continue;
					if (result != item){
						replaceItem(hero, item, result);
						if (sampleOld == null){
							sampleOld = item;
							sampleNew = result;
						}
						metaChanged = true;
					} else if (item instanceof MagesStaff){
						// SoT remakes the imbued wand in place; staff body stays
						metaChanged = true;
					}
				}
			}

			if (enigmaOn){
				ArrayList<Item> toRecycle = collectEnigmaConsumables(hero);
				for (Item item : toRecycle){
					Item result = recycleStack(item);
					if (result != null && result != item){
						replaceBagItem(hero, item, result);
						if (sampleOld == null){
							sampleOld = item;
							sampleNew = result;
						}
						enigmaChanged = true;
					}
				}

				// Unequipped gear in the bag remakes like Metamorphosis (worn slots stay Meta-only)
				ArrayList<Item> bagGear = collectEnigmaUnequippedGear(hero, metaOn);
				for (Item item : bagGear){
					Item result = remakeGear(item);
					if (result == null) continue;
					if (result != item){
						replaceBagItem(hero, item, result);
						if (sampleOld == null){
							sampleOld = item;
							sampleNew = result;
						}
						enigmaChanged = true;
					} else if (item instanceof MagesStaff){
						enigmaChanged = true;
					}
				}
			}

			if (metaChanged || enigmaChanged){
				if (hero.sprite != null){
					hero.sprite.emitter().start(Speck.factory(Speck.CHANGE), 0.2f, 10);
					if (sampleOld != null && sampleNew != null){
						Transmuting.show(hero, sampleOld, sampleNew);
					}
				}
				if (metaChanged){
					GLog.p(Messages.get(FirstDescent.class, "metamorphosis"));
				}
				if (enigmaChanged){
					GLog.p(Messages.get(FirstDescent.class, "enigma"));
				}
			}
		} finally {
			Modifiers.suppressCommandWindow = false;
			Random.popGenerator();
		}
	}

	private static ArrayList<Item> collectMetamorphosisTargets( Hero hero ){
		ArrayList<Item> list = new ArrayList<>();
		addIfMetamorphoseable(list, hero.belongings.weapon);
		addIfMetamorphoseable(list, hero.belongings.armor);
		addIfMetamorphoseable(list, hero.belongings.artifact);
		addIfMetamorphoseable(list, hero.belongings.misc);
		addIfMetamorphoseable(list, hero.belongings.ring);
		addIfMetamorphoseable(list, hero.belongings.secondWep);
		for (Trinket t : hero.belongings.getAllItems(Trinket.class)){
			if (!list.contains(t)) list.add(t);
		}
		return list;
	}

	private static void addIfMetamorphoseable( ArrayList<Item> list, Item item ){
		if (item == null) return;
		// Trinkets and Mage's Staff remake despite unique (staff: imbued wand only)
		if (item.unique && !(item instanceof Trinket) && !(item instanceof MagesStaff)) return;
		list.add(item);
	}

	private static Item remakeGear( Item item ){
		Item result;
		if (item instanceof Armor){
			result = remakeArmor((Armor) item);
		} else if (item instanceof Weapon){
			result = ScrollOfTransmutation.changeItem(item);
			if (result instanceof Weapon){
				rerollWeaponEnchant((Weapon) result, item.cursed);
			}
		} else {
			result = ScrollOfTransmutation.changeItem(item);
		}
		// Legacy kit pieces stay in the kit after a remake
		if (result != null && item.legacyKit) result.legacyKit = true;
		return result;
	}

	private static Armor remakeArmor( Armor old ){
		Class<?> cls;
		do {
			cls = Random.element(REGULAR_ARMOR);
		} while (cls == old.getClass());

		Armor n = (Armor) Reflection.newInstance(cls);
		if (n == null) return null;

		BrokenSeal seal = old.checkSeal() != null ? old.detachSeal() : null;

		n.level(0);
		int level = old.trueLevel();
		if (level > 0){
			n.upgrade(level);
		} else if (level < 0){
			n.degrade(-level);
		}

		n.levelKnown = old.levelKnown;
		n.cursedKnown = old.cursedKnown;
		n.cursed = old.cursed;
		n.curseInfusionBonus = old.curseInfusionBonus;
		n.glyphHardened = false;
		n.augment = Random.oneOf(Armor.Augment.values());

		if (seal != null){
			n.affixSeal(seal);
		}

		// After seal affix so the seal picks up the new glyph
		if (old.cursed){
			n.inscribe(Armor.Glyph.randomCurse());
		} else {
			n.inscribe(Armor.Glyph.random());
		}

		return n;
	}

	private static void rerollWeaponEnchant( Weapon w, boolean cursed ){
		if (cursed){
			w.enchant(Weapon.Enchantment.randomCurse());
		} else {
			w.enchant(Weapon.Enchantment.random());
		}
		w.augment = Random.oneOf(Weapon.Augment.values());
	}

	private static ArrayList<Item> collectEnigmaConsumables( Hero hero ){
		ArrayList<Item> list = new ArrayList<>();
		for (Item item : hero.belongings.backpack){
			if (item instanceof Bag) continue;
			if (isEnigmaConsumable(item)){
				list.add(item);
			}
		}
		return list;
	}

	/** Spare weapons/armor/rings/artifacts/wands/missiles/trinkets in the bag (not worn). */
	private static ArrayList<Item> collectEnigmaUnequippedGear( Hero hero, boolean metaAlsoOn ){
		ArrayList<Item> list = new ArrayList<>();
		for (Item item : hero.belongings.backpack){
			if (item instanceof Bag) continue;
			if (!isEnigmaUnequippedGear(item, hero, metaAlsoOn)) continue;
			list.add(item);
		}
		return list;
	}

	private static boolean isEnigmaUnequippedGear( Item item, Hero hero, boolean metaAlsoOn ){
		if (item == null) return false;
		if (item.isEquipped(hero)) return false;
		// Tipped darts are recycled as consumables; untipped darts are not SoT-able
		if (item instanceof TippedDart || item.getClass() == Dart.class) return false;
		// Meta already remakes every trinket when both pacts are on
		if (metaAlsoOn && item instanceof Trinket) return false;
		if (item.unique && !(item instanceof Trinket) && !(item instanceof MagesStaff)) return false;

		return item instanceof EquipableItem
				|| item instanceof Wand
				|| item instanceof MissileWeapon
				|| item instanceof Trinket;
	}

	private static boolean isEnigmaConsumable( Item item ){
		if (item == null) return false;
		if (isUpgradeCurrency(item)) return false;
		if (item instanceof Potion){
			return !(item instanceof Elixir || item instanceof Brew);
		}
		return item instanceof Scroll
				|| item instanceof Plant.Seed
				|| item instanceof Runestone
				|| item instanceof TippedDart
				|| item instanceof Food;
	}

	private static boolean isUpgradeCurrency( Item item ){
		return item instanceof ScrollOfUpgrade
				|| item instanceof ScrollOfEnchantment
				|| item instanceof PotionOfStrength
				|| item instanceof PotionOfMastery;
	}

	private static boolean isUpgradeCurrencyClass( Class<?> cls ){
		return cls == ScrollOfUpgrade.class
				|| cls == ScrollOfEnchantment.class
				|| cls == PotionOfStrength.class
				|| cls == PotionOfMastery.class;
	}

	private static Item recycleStack( Item item ){
		Item result;
		int tries = 0;
		do {
			if (item instanceof Potion) {
				result = Generator.randomUsingDefaults(Generator.Category.POTION);
				if (item instanceof ExoticPotion){
					result = Reflection.newInstance(ExoticPotion.regToExo.get(result.getClass()));
				}
			} else if (item instanceof Scroll) {
				result = Generator.randomUsingDefaults(Generator.Category.SCROLL);
				if (item instanceof ExoticScroll){
					result = Reflection.newInstance(ExoticScroll.regToExo.get(result.getClass()));
				}
			} else if (item instanceof Plant.Seed) {
				result = Generator.randomUsingDefaults(Generator.Category.SEED);
			} else if (item instanceof Runestone) {
				result = Generator.randomUsingDefaults(Generator.Category.STONE);
			} else if (item instanceof Food) {
				result = Generator.randomUsingDefaults(Generator.Category.FOOD);
			} else {
				result = TippedDart.randomTipped(1);
			}
			tries++;
		} while (tries < 100 && (result == null
				|| result.getClass() == item.getClass()
				|| Challenges.isItemBlocked(result)
				|| isUpgradeCurrencyClass(result.getClass())));

		if (result == null
				|| result.getClass() == item.getClass()
				|| Challenges.isItemBlocked(result)
				|| isUpgradeCurrencyClass(result.getClass())){
			return null;
		}

		result.quantity(item.quantity());
		return result;
	}

	private static void replaceItem( Hero hero, Item item, Item result ){
		int slot = Dungeon.quickslot.getSlot(item);

		if (item.isEquipped(hero)){
			item.cursed = false;
			if (item instanceof KindOfWeapon && hero.belongings.secondWep == item){
				((EquipableItem) item).doUnequip(hero, false);
				((KindOfWeapon) result).equipSecondary(hero);
			} else if (item instanceof EquipableItem && result instanceof EquipableItem){
				((EquipableItem) item).doUnequip(hero, false);
				((EquipableItem) result).doEquip(hero);
			} else {
				((EquipableItem) item).doUnequip(hero, false);
				if (!result.collect()){
					Dungeon.level.drop(result, hero.pos).sprite.drop();
				}
			}
			hero.spend(-hero.cooldown());
		} else {
			replaceBagItem(hero, item, result);
			return;
		}

		if (result.isIdentified()){
			Catalog.setSeen(result.getClass());
			Statistics.itemTypesDiscovered.add(result.getClass());
		}

		if (slot != -1
				&& result.defaultAction() != null
				&& !Dungeon.quickslot.isNonePlaceholder(slot)
				&& hero.belongings.contains(result)){
			Dungeon.quickslot.setSlot(slot, result);
		}
	}

	private static void replaceBagItem( Hero hero, Item item, Item result ){
		int slot = Dungeon.quickslot.getSlot(item);

		item.detachAll(hero.belongings.backpack);

		if (!result.collect()){
			Dungeon.level.drop(result, hero.pos).sprite.drop();
		} else if (result.stackable && hero.belongings.getSimilar(result) != null){
			result = hero.belongings.getSimilar(result);
		}

		if (result.isIdentified()){
			Catalog.setSeen(result.getClass());
			Statistics.itemTypesDiscovered.add(result.getClass());
		}

		if (slot != -1
				&& result.defaultAction() != null
				&& !Dungeon.quickslot.isNonePlaceholder(slot)
				&& hero.belongings.contains(result)){
			Dungeon.quickslot.setSlot(slot, result);
		}
	}

}
