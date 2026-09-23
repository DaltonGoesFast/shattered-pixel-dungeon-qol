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

import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.items.EquipableItem;
import com.shatteredpixel.shatteredpixeldungeon.items.Generator;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.KindOfWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.ClassArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.Artifact;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.CloakOfShadows;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.HolyTome;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Food;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfStrength;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.brews.Brew;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.elixirs.Elixir;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.ExoticPotion;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfUpgrade;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.ExoticScroll;
import com.shatteredpixel.shatteredpixeldungeon.items.spells.Spell;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.Runestone;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.Trinket;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.SpiritBow;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MagesStaff;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.MissileWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.darts.TippedDart;
import com.shatteredpixel.shatteredpixeldungeon.journal.Catalog;
import com.shatteredpixel.shatteredpixeldungeon.plants.Plant;
import com.shatteredpixel.shatteredpixeldungeon.plants.Rotberry;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndCommand;
import com.watabou.noosa.Game;
import com.watabou.utils.Bundle;
import com.watabou.utils.Random;
import com.watabou.utils.Reflection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Map;

/**
 * Command pact: after claiming dungeon/shop loot, pick any item of the same kind.
 */
public class Command {

	private static class PendingChoice {
		final Item item;
		final int qty;
		PendingChoice( Item item, int qty ){
			this.item = item;
			this.qty = qty;
		}
	}

	private static final LinkedList<PendingChoice> pending = new LinkedList<>();
	private static boolean windowOpen = false;

	private static final String POTION_LAYOUT = "command_potion_layout";
	private static final String SCROLL_LAYOUT = "command_scroll_layout";
	private static final String RING_LAYOUT = "command_ring_layout";
	private static final long LAYOUT_SEED = 0xC0A11A70L;

	private static ArrayList<Class<?>> potionLayout;
	private static ArrayList<Class<?>> scrollLayout;
	private static ArrayList<Class<?>> ringLayout;

	public static void reset(){
		pending.clear();
		windowOpen = false;
		potionLayout = null;
		scrollLayout = null;
		ringLayout = null;
	}

	public static void initForRun(){
		potionLayout = null;
		scrollLayout = null;
		ringLayout = null;
		ensureLayouts();
	}

	public static void store( Bundle bundle ){
		if (potionLayout != null) bundle.put(POTION_LAYOUT, potionLayout.toArray(new Class[0]));
		if (scrollLayout != null) bundle.put(SCROLL_LAYOUT, scrollLayout.toArray(new Class[0]));
		if (ringLayout != null) bundle.put(RING_LAYOUT, ringLayout.toArray(new Class[0]));
	}

	public static void restore( Bundle bundle ){
		pending.clear();
		windowOpen = false;
		potionLayout = loadLayout(bundle, POTION_LAYOUT);
		scrollLayout = loadLayout(bundle, SCROLL_LAYOUT);
		ringLayout = loadLayout(bundle, RING_LAYOUT);
	}

	public static void offerAfterPickup( Item picked, int incomingQty ){
		if (!Dungeon.isModified(Modifiers.COMMAND) || Modifiers.suppressCommandWindow){
			if (picked != null) picked.commandLoot = false;
			return;
		}
		if (Dungeon.hero == null || !Dungeon.hero.isAlive()){
			if (picked != null) picked.commandLoot = false;
			return;
		}

		Item subject = resolveSubject(picked);
		if (subject == null){
			return;
		}

		ArrayList<Class<?>> choices = choicesFor(subject);
		if (choices == null || choices.isEmpty()){
			subject.commandLoot = false;
			return;
		}

		enqueue(subject, incomingQty);
	}

	private static Item resolveSubject( Item picked ){
		Hero hero = Dungeon.hero;
		Item subject;
		if (picked.quantity() > 0 && hero.belongings.contains(picked)){
			subject = picked;
		} else {
			subject = hero.belongings.getSimilar(picked);
			if (subject == null){
				picked.commandLoot = false;
				return null;
			}
			subject.commandLoot = true;
		}

		if (!subject.commandLoot){
			return null;
		}
		return subject;
	}

	private static void enqueue( Item subject, int qty ){
		pending.add(new PendingChoice(subject, qty));
		tryShowNext();
	}

	public static void tryShowNext(){
		if (windowOpen || pending.isEmpty()) return;
		PendingChoice next = pending.poll();
		if (next == null || next.item == null) return;
		if (!Dungeon.hero.belongings.contains(next.item)){
			tryShowNext();
			return;
		}
		ArrayList<Class<?>> choices = choicesFor(next.item);
		if (choices == null || choices.isEmpty()){
			clearLootIfIdle(next.item);
			tryShowNext();
			return;
		}
		windowOpen = true;
		final Item toShow = next.item;
		final int qty = next.qty;
		final ArrayList<Class<?>> opts = choices;
		Game.runOnRenderThread(() -> GameScene.show(new WndCommand(toShow, opts, qty)));
	}

	public static void onWindowClosed(){
		windowOpen = false;
		tryShowNext();
	}

	public static ArrayList<Class<?>> choicesFor( Item item ){
		if (item == null) return null;

		// Hero/quest uniques stay un-Commandable; SoU/SoStr/trinkets/etc. are unique but allowed
		if (item.unique && !commandableUnique(item)){
			return null;
		}
		if (item.getClass().getName().contains(".quest.")){
			return null;
		}

		ArrayList<Class<?>> list = new ArrayList<>();

		if (item instanceof MeleeWeapon && !(item instanceof MagesStaff) && !(item instanceof SpiritBow)){
			addAll(list, Generator.Category.WEP_T1.classes);
			addAll(list, Generator.Category.WEP_T2.classes);
			addAll(list, Generator.Category.WEP_T3.classes);
			addAll(list, Generator.Category.WEP_T4.classes);
			addAll(list, Generator.Category.WEP_T5.classes);
			list.remove(MagesStaff.class);
			list.remove(SpiritBow.class);
		} else if (item instanceof Armor && !(item instanceof ClassArmor)){
			for (Class<?> cls : Generator.Category.ARMOR.classes){
				if (!ClassArmor.class.isAssignableFrom(cls)){
					list.add(cls);
				}
			}
		} else if (item instanceof TippedDart){
			addAll(list, Catalog.TIPPED_DARTS.items().toArray(new Class[0]));
		} else if (item instanceof MissileWeapon){
			addAll(list, Generator.Category.MIS_T1.classes);
			addAll(list, Generator.Category.MIS_T2.classes);
			addAll(list, Generator.Category.MIS_T3.classes);
			addAll(list, Generator.Category.MIS_T4.classes);
			addAll(list, Generator.Category.MIS_T5.classes);
		} else if (item instanceof Wand){
			addAll(list, Generator.Category.WAND.classes);
		} else if (item instanceof Ring){
			addAll(list, Generator.Category.RING.classes);
		} else if (item instanceof Artifact){
			addAll(list, Generator.Category.ARTIFACT.classes);
			list.remove(CloakOfShadows.class);
			list.remove(HolyTome.class);
			if (item instanceof CloakOfShadows || item instanceof HolyTome){
				return null;
			}
		} else if (item instanceof Trinket){
			addAll(list, Generator.Category.TRINKET.classes);
		} else if (item instanceof ExoticPotion){
			addAll(list, Catalog.EXOTIC_POTIONS.items().toArray(new Class[0]));
		} else if (item instanceof Brew || item instanceof Elixir){
			addAll(list, Catalog.BREWS_ELIXIRS.items().toArray(new Class[0]));
		} else if (item instanceof Potion){
			addAll(list, Generator.Category.POTION.classes);
		} else if (item instanceof ExoticScroll){
			addAll(list, Catalog.EXOTIC_SCROLLS.items().toArray(new Class[0]));
		} else if (item instanceof Spell){
			addAll(list, Catalog.SPELLS.items().toArray(new Class[0]));
		} else if (item instanceof Scroll){
			addAll(list, Generator.Category.SCROLL.classes);
		} else if (item instanceof Plant.Seed){
			addAll(list, Generator.Category.SEED.classes);
			list.remove(Rotberry.Seed.class);
			if (item instanceof Rotberry.Seed) return null;
		} else if (item instanceof Runestone){
			addAll(list, Generator.Category.STONE.classes);
		} else if (item instanceof Food){
			addAll(list, Catalog.FOOD.items().toArray(new Class[0]));
		} else if (item instanceof Bomb){
			addAll(list, Catalog.BOMBS.items().toArray(new Class[0]));
		} else {
			return null;
		}

		// Always include the claimed type if somehow missing
		if (!list.contains(item.getClass())){
			list.add(0, item.getClass());
		}

		list.removeIf(cls -> {
			Item sample = (Item) Reflection.newInstance(cls);
			return sample != null && Challenges.isItemBlocked(sample);
		});

		applyRunLayout(item, list);
		return list.isEmpty() ? null : list;
	}

	private static boolean commandableUnique( Item item ){
		return item instanceof ScrollOfUpgrade
				|| item instanceof PotionOfStrength
				|| item instanceof Trinket
				|| item instanceof Runestone
				|| item instanceof ExoticScroll
				|| item instanceof ExoticPotion
				|| item instanceof Elixir
				|| item instanceof Brew
				|| item instanceof Spell;
	}

	private static void addAll( ArrayList<Class<?>> list, Class<?>[] classes ){
		if (classes == null) return;
		for (Class<?> cls : classes){
			if (cls != null && !list.contains(cls)) list.add(cls);
		}
	}

	private static void applyRunLayout( Item item, ArrayList<Class<?>> list ){
		ensureLayouts();
		if (item instanceof Ring){
			applyLayout(list, ringLayout);
		} else if (item instanceof ExoticPotion){
			applyLayout(list, mappedLayout(potionLayout, ExoticPotion.regToExo));
		} else if (item instanceof Potion && !(item instanceof Brew) && !(item instanceof Elixir)){
			applyLayout(list, potionLayout);
		} else if (item instanceof ExoticScroll){
			applyLayout(list, mappedLayout(scrollLayout, ExoticScroll.regToExo));
		} else if (item instanceof Scroll){
			applyLayout(list, scrollLayout);
		}
	}

	private static void ensureLayouts(){
		if (potionLayout != null && scrollLayout != null && ringLayout != null) return;
		Random.pushGenerator(Dungeon.seed + LAYOUT_SEED);
		try {
			if (potionLayout == null) potionLayout = shuffledCopy(Generator.Category.POTION.classes);
			if (scrollLayout == null) scrollLayout = shuffledCopy(Generator.Category.SCROLL.classes);
			if (ringLayout == null) ringLayout = shuffledCopy(Generator.Category.RING.classes);
		} finally {
			Random.popGenerator();
		}
	}

	private static ArrayList<Class<?>> shuffledCopy( Class<?>[] classes ){
		ArrayList<Class<?>> list = new ArrayList<>();
		addAll(list, classes);
		Class<?>[] arr = list.toArray(new Class[0]);
		Random.shuffle(arr);
		list.clear();
		Collections.addAll(list, arr);
		return list;
	}

	private static ArrayList<Class<?>> mappedLayout( ArrayList<Class<?>> regular, Map<? extends Class<?>, ? extends Class<?>> map ){
		ArrayList<Class<?>> mapped = new ArrayList<>();
		if (regular == null) return mapped;
		for (Class<?> cls : regular){
			Class<?> exo = map.get(cls);
			if (exo != null) mapped.add(exo);
		}
		return mapped;
	}

	private static void applyLayout( ArrayList<Class<?>> list, ArrayList<Class<?>> layout ){
		if (layout == null || layout.isEmpty()) return;
		list.sort(Comparator.comparingInt(cls -> {
			int i = layout.indexOf(cls);
			return i >= 0 ? i : Integer.MAX_VALUE;
		}));
	}

	private static ArrayList<Class<?>> loadLayout( Bundle bundle, String key ){
		if (bundle == null || !bundle.contains(key)) return null;
		Class<?>[] stored = bundle.getClassArray(key);
		if (stored == null || stored.length == 0) return null;
		ArrayList<Class<?>> list = new ArrayList<>();
		addAll(list, stored);
		return list.isEmpty() ? null : list;
	}

	private static boolean stillPending( Item item ){
		for (PendingChoice p : pending){
			if (p.item == item) return true;
		}
		return false;
	}

	private static void clearLootIfIdle( Item item ){
		if (item != null && !stillPending(item)){
			item.commandLoot = false;
		}
	}

	public static void applyChoice( Item original, Class<?> chosenClass, int qty ){
		if (original == null || chosenClass == null || Dungeon.hero == null) return;

		clearLootIfIdle(original);

		if (original.getClass() == chosenClass){
			return;
		}

		Item slice = original;
		boolean sliced = false;
		if (original.stackable && qty > 0 && original.quantity() > qty){
			slice = original.split(qty);
			if (slice == null) return;
			sliced = true;
		}

		Item result = buildReplacement(slice, chosenClass);
		if (result == null){
			if (sliced) original.merge(slice);
			return;
		}

		Hero hero = Dungeon.hero;
		int slot = sliced ? -1 : Dungeon.quickslot.getSlot(original);

		Modifiers.suppressCommandWindow = true;
		try {
			if (sliced){
				if (!result.collect()){
					Dungeon.level.drop(result, hero.pos).sprite.drop();
				}
			} else if (original.isEquipped(hero)){
				original.cursed = false;
				if (original instanceof KindOfWeapon && hero.belongings.secondWep() == original){
					((EquipableItem) original).doUnequip(hero, false);
					((KindOfWeapon) result).equipSecondary(hero);
				} else {
					((EquipableItem) original).doUnequip(hero, false);
					((EquipableItem) result).doEquip(hero);
				}
				hero.spend(-hero.cooldown());
			} else {
				if (original instanceof MissileWeapon && !(original instanceof TippedDart)){
					original.detachAll(hero.belongings.backpack);
				} else {
					original.detach(hero.belongings.backpack);
				}
				if (!result.collect()){
					Dungeon.level.drop(result, hero.pos).sprite.drop();
				} else if (result.stackable && hero.belongings.getSimilar(result) != null){
					result = hero.belongings.getSimilar(result);
				}
			}
			if (slot != -1
					&& result.defaultAction() != null
					&& !Dungeon.quickslot.isNonePlaceholder(slot)
					&& hero.belongings.contains(result)){
				Dungeon.quickslot.setSlot(slot, result);
			}
		} finally {
			Modifiers.suppressCommandWindow = false;
		}

		if (result.isIdentified()){
			Catalog.setSeen(result.getClass());
			Statistics.itemTypesDiscovered.add(result.getClass());
		}
	}

	public static void cancel( Item original ){
		clearLootIfIdle(original);
	}

	private static Item buildReplacement( Item original, Class<?> chosenClass ){
		Item result = (Item) Reflection.newInstance(chosenClass);
		if (result == null) return null;

		result.quantity(original.quantity());
		result.levelKnown = original.levelKnown;
		result.cursedKnown = original.cursedKnown;
		result.cursed = original.cursed;

		if (original instanceof Artifact && result instanceof Artifact){
			((Artifact) result).transferUpgrade(original.visiblyUpgraded());
		} else {
			int level = original.trueLevel();
			result.level(0);
			if (level > 0){
				result.upgrade(level);
			} else if (level < 0){
				result.degrade(-level);
			}
		}

		if (original instanceof Weapon && result instanceof Weapon){
			Weapon o = (Weapon) original;
			Weapon n = (Weapon) result;
			n.enchantment = o.enchantment;
			n.curseInfusionBonus = o.curseInfusionBonus;
			n.masteryPotionBonus = o.masteryPotionBonus;
			n.augment = o.augment;
			n.enchantHardened = o.enchantHardened;
			if (original instanceof MissileWeapon && result instanceof MissileWeapon && original.isUpgradable()){
				Buff.affect(Dungeon.hero, MissileWeapon.UpgradedSetTracker.class)
						.levelThresholds.put(((MissileWeapon) original).setID, Integer.MAX_VALUE);
				((MissileWeapon) n).damage(100 - ((MissileWeapon) original).durabilityLeft());
			}
		}

		if (original instanceof Armor && result instanceof Armor){
			Armor o = (Armor) original;
			Armor n = (Armor) result;
			n.inscribe(o.glyph);
			n.glyphHardened = o.glyphHardened;
			n.augment = o.augment;
			n.curseInfusionBonus = o.curseInfusionBonus;
		}

		if (original instanceof Wand && result instanceof Wand){
			Wand o = (Wand) original;
			Wand n = (Wand) result;
			n.curChargeKnown = o.curChargeKnown;
			n.curseInfusionBonus = o.curseInfusionBonus;
			n.resinBonus = o.resinBonus;
			n.curCharges = o.curCharges;
			n.updateLevel();
		}

		return result;
	}

}
