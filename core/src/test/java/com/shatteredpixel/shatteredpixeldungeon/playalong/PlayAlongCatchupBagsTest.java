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
 */

package com.shatteredpixel.shatteredpixeldungeon.playalong;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Belongings;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.Bag;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.MagicalHolster;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.PotionBandolier;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.ScrollHolder;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.VelvetPouch;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlayAlongCatchupBagsTest {

	private boolean previousDebug;

	@Before
	public void quietBadges() {
		previousDebug = Dungeon.debugRun;
		Dungeon.debugRun = true;
		Dungeon.LimitedDrops.reset();
	}

	@After
	public void restore() {
		Dungeon.debugRun = previousDebug;
		Dungeon.LimitedDrops.reset();
	}

	@Test
	public void bagsScaleWithShopFloors() {
		assertEquals(list(VelvetPouch.class), PlayAlongCatchup.bagsForDepth(1));
		assertEquals(list(VelvetPouch.class), PlayAlongCatchup.bagsForDepth(6));

		assertEquals(list(VelvetPouch.class, ScrollHolder.class), PlayAlongCatchup.bagsForDepth(7));
		assertEquals(list(VelvetPouch.class, ScrollHolder.class), PlayAlongCatchup.bagsForDepth(11));

		assertEquals(list(VelvetPouch.class, ScrollHolder.class, PotionBandolier.class),
				PlayAlongCatchup.bagsForDepth(12));
		assertEquals(list(VelvetPouch.class, ScrollHolder.class, PotionBandolier.class),
				PlayAlongCatchup.bagsForDepth(16));

		assertEquals(allFour(), PlayAlongCatchup.bagsForDepth(17));
		assertEquals(allFour(), PlayAlongCatchup.bagsForDepth(20));
		assertEquals(allFour(), PlayAlongCatchup.bagsForDepth(21));
		assertEquals(allFour(), PlayAlongCatchup.bagsForDepth(26));
	}

	@Test
	public void kingProgressIncludesAscentEvenWhenTheHostHasClimbed() {
		assertEquals(21, PlayAlongCatchup.bagProgressDepth(8, 21, "king"));
		assertEquals(24, PlayAlongCatchup.bagProgressDepth(24, 21, "king"));
		assertEquals(21, PlayAlongCatchup.bagProgressDepth(20, 21, "king"));

		assertEquals(6, PlayAlongCatchup.bagProgressDepth(6, 6, "goo"));
		assertEquals(9, PlayAlongCatchup.bagProgressDepth(9, 6, "goo"));
		assertEquals(14, PlayAlongCatchup.bagProgressDepth(14, 11, "tengu"));
		assertEquals(16, PlayAlongCatchup.bagProgressDepth(16, 16, "dm300"));
		assertEquals(18, PlayAlongCatchup.bagProgressDepth(18, 16, "dm300"));
	}

	@Test
	public void earlyDropInDoesNotTakeShopBags() {
		Belongings.Backpack pack = new Belongings.Backpack();
		assertTrue(new VelvetPouch().collect(pack));

		PlayAlongCatchup.ensureBags(pack, PlayAlongCatchup.bagProgressDepth(6, 6, "goo"));

		assertEquals(1, pack.items.size());
		assertTrue(pack.items.get(0) instanceof VelvetPouch);
		assertTrue(Dungeon.LimitedDrops.VELVET_POUCH.dropped());
		assertFalse(Dungeon.LimitedDrops.SCROLL_HOLDER.dropped());
		assertFalse(Dungeon.LimitedDrops.POTION_BANDOLIER.dropped());
		assertFalse(Dungeon.LimitedDrops.MAGICAL_HOLSTER.dropped());
	}

	@Test
	public void deepDropInKeepsScrollAndPotionCountsAndTucksThem() {
		Belongings.Backpack pack = new Belongings.Backpack();
		assertTrue(new VelvetPouch().collect(pack));

		// A stack already in the pack (starting kit) must move when the bandolier arrives.
		// Plain Scroll/Potion stand-ins: the real subclasses load item sprites.
		// ScrollHolder and PotionBandolier key off those base types, same as SoU / identify / healing.
		KitPotion healing = new KitPotion();
		healing.quantity(2);
		assertTrue(healing.collect(pack));

		int depth = PlayAlongCatchup.bagProgressDepth(8, 21, "king");
		PlayAlongCatchup.ensureBags(pack, depth);

		KitUpgrade upgrades = new KitUpgrade();
		upgrades.quantity(12);
		assertTrue(upgrades.collect(pack));
		KitIdentify identify = new KitIdentify();
		identify.quantity(3);
		assertTrue(identify.collect(pack));
		PlayAlongCatchup.tuckIntoBags(pack);

		assertEquals(12, quantity(pack, KitUpgrade.class));
		assertEquals(3, quantity(pack, KitIdentify.class));
		assertEquals(2, quantity(pack, KitPotion.class));

		assertTrue(inside(pack, ScrollHolder.class, KitUpgrade.class));
		assertTrue(inside(pack, ScrollHolder.class, KitIdentify.class));
		assertTrue(inside(pack, PotionBandolier.class, KitPotion.class));
		assertFalse(loose(pack, KitUpgrade.class));
		assertFalse(loose(pack, KitIdentify.class));
		assertFalse(loose(pack, KitPotion.class));
		assertTrue(has(pack, VelvetPouch.class));
		assertTrue(has(pack, ScrollHolder.class));
		assertTrue(has(pack, PotionBandolier.class));
		assertTrue(has(pack, MagicalHolster.class));
		assertEquals(4, pack.items.size());

		assertEquals(allFour(), PlayAlongCatchup.bagsForDepth(depth));
		assertTrue(Dungeon.LimitedDrops.VELVET_POUCH.dropped());
		assertTrue(Dungeon.LimitedDrops.SCROLL_HOLDER.dropped());
		assertTrue(Dungeon.LimitedDrops.POTION_BANDOLIER.dropped());
		assertTrue(Dungeon.LimitedDrops.MAGICAL_HOLSTER.dropped());

		int before = pack.items.size();
		PlayAlongCatchup.ensureBags(pack, depth);
		assertEquals(before, pack.items.size());
	}

	/** Scroll without a sprite. Same bag rules as Scroll of Upgrade. */
	public static final class KitUpgrade extends Scroll {
		@Override
		public void doRead() {
		}
	}

	/** Scroll without a sprite. Same bag rules as Scroll of Identify. */
	public static final class KitIdentify extends Scroll {
		@Override
		public void doRead() {
		}
	}

	/** Potion without a sprite. Same bag rules as Potion of Healing. */
	public static final class KitPotion extends Potion {
	}

	@SafeVarargs
	private static List<Class<? extends Item>> list(Class<? extends Item>... types) {
		java.util.ArrayList<Class<? extends Item>> bags = new java.util.ArrayList<>();
		for (Class<? extends Item> type : types) bags.add(type);
		return bags;
	}

	private static List<Class<? extends Item>> allFour() {
		return list(VelvetPouch.class, ScrollHolder.class, PotionBandolier.class, MagicalHolster.class);
	}

	private static int quantity(Bag pack, Class<? extends Item> type) {
		int count = 0;
		for (Item item : pack) {
			if (type.isInstance(item)) count += item.quantity();
		}
		return count;
	}

	private static boolean has(Bag pack, Class<? extends Item> type) {
		for (Item item : pack.items) {
			if (type.isInstance(item)) return true;
		}
		return false;
	}

	private static boolean inside(Bag pack, Class<? extends Bag> bagType, Class<? extends Item> itemType) {
		for (Item item : pack.items) {
			if (!bagType.isInstance(item)) continue;
			for (Item inner : ((Bag) item).items) {
				if (itemType.isInstance(inner)) return true;
			}
		}
		return false;
	}

	private static boolean loose(Bag pack, Class<? extends Item> type) {
		for (Item item : pack.items) {
			if (type.isInstance(item)) return true;
		}
		return false;
	}
}
