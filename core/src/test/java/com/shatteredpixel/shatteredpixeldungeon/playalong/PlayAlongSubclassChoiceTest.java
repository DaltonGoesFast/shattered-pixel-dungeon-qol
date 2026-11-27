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

import com.badlogic.gdx.Preferences;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Preparation;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroSubClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.abilities.warrior.HeroicLeap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.KingsCrown;
import com.shatteredpixel.shatteredpixeldungeon.items.TengusMask;
import com.watabou.noosa.Game;
import com.watabou.utils.GameSettings;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Play Along opens the subclass and armor-ability choosers without using the
 * mask or crown, so {@link Item#curUser} is null. Choosing must still apply.
 */
public class PlayAlongSubclassChoiceTest {

	private Hero hero;
	private Hero previousHero;
	private Hero previousUser;

	@BeforeClass
	public static void useMemorySettings() {
		GameSettings.set(new MemoryPreferences());
	}

	@Before
	public void setUp() {
		previousHero = Dungeon.hero;
		previousUser = curUser();
		hero = new Hero();
		hero.heroClass = HeroClass.WARRIOR;
		hero.ready = true;
		Dungeon.hero = hero;
		setCurUser(null);
		if (Game.version == null) {
			Game.version = "test";
		}
	}

	@After
	public void tearDown() {
		Dungeon.hero = previousHero;
		setCurUser(previousUser);
	}

	@Test
	public void nullCurUserWithoutMaskStillAppliesSubclass() {
		TengusMask mask = new TengusMask();
		float time = hero.cooldown();

		mask.choose(HeroSubClass.BERSERKER);

		assertEquals(HeroSubClass.BERSERKER, hero.subClass);
		assertTrue(hero.talents.get(2).containsKey(Talent.ENDLESS_RAGE));
		assertEquals(time, hero.cooldown(), 0.001f);
		assertTrue(hero.ready);
		assertFalse(hero.belongings.backpack.contains(mask));
	}

	@Test
	public void heldMaskIsConsumedWhenCurUserIsNull() {
		TengusMask mask = new TengusMask();
		hero.belongings.backpack.items.add(mask);
		float time = hero.cooldown();

		mask.choose(hero, HeroSubClass.GLADIATOR);

		assertEquals(HeroSubClass.GLADIATOR, hero.subClass);
		assertTrue(hero.talents.get(2).containsKey(Talent.CLEAVE));
		assertFalse(hero.belongings.backpack.contains(mask));
		assertEquals(time + 1f, hero.cooldown(), 0.001f);
		assertFalse(hero.ready);
	}

	@Test
	public void unheldMaskDoesNotConsumeADifferentMask() {
		TengusMask held = new TengusMask();
		hero.belongings.backpack.items.add(held);

		new TengusMask().choose(hero, HeroSubClass.BERSERKER);

		assertTrue(hero.belongings.backpack.contains(held));
		assertEquals(HeroSubClass.BERSERKER, hero.subClass);
		assertTrue(hero.ready);
	}

	@Test
	public void explicitHeroWinsOverStaleCurUser() {
		Hero stale = new Hero();
		stale.heroClass = HeroClass.MAGE;
		setCurUser(stale);
		TengusMask mask = new TengusMask();
		hero.belongings.backpack.items.add(mask);

		mask.choose(hero, HeroSubClass.BERSERKER);

		assertEquals(HeroSubClass.BERSERKER, hero.subClass);
		assertEquals(HeroSubClass.NONE, stale.subClass);
		assertFalse(hero.belongings.backpack.contains(mask));
		assertTrue(stale.belongings.backpack.items.isEmpty());
	}

	@Test
	public void curUserPresentWithNullSpriteDoesNotCrash() {
		Hero stale = new Hero();
		assertNull(hero.sprite);
		assertNull(stale.sprite);
		setCurUser(stale);

		TengusMask mask = new TengusMask();
		hero.belongings.backpack.items.add(mask);
		// QoL-23 called this overload and then curUser.sprite.operate.
		mask.choose(HeroSubClass.BERSERKER);

		assertEquals(HeroSubClass.BERSERKER, hero.subClass);
		assertEquals(HeroSubClass.NONE, stale.subClass);
		assertFalse(hero.belongings.backpack.contains(mask));
		assertNull(hero.sprite);

		KingsCrown crown = new KingsCrown();
		hero.belongings.backpack.items.add(crown);
		crown.upgradeArmor(hero, null, new HeroicLeap());

		assertTrue(hero.armorAbility instanceof HeroicLeap);
		assertFalse(hero.belongings.backpack.contains(crown));
		assertEquals(HeroSubClass.NONE, stale.subClass);
		assertNull(hero.sprite);
	}

	@Test
	public void invisibleAssassinGainsPreparationWithoutConsumingAMask() {
		hero.invisible = 1;

		new TengusMask().choose(HeroSubClass.ASSASSIN);

		assertEquals(HeroSubClass.ASSASSIN, hero.subClass);
		assertNotNull(hero.buff(Preparation.class));
		assertTrue(hero.talents.get(2).containsKey(Talent.ENHANCED_LETHALITY));
		assertTrue(hero.ready);
	}

	@Test
	public void crownChoiceDoesNotConsumeACrownTheHeroDoesNotHold() {
		KingsCrown held = new KingsCrown();
		hero.belongings.backpack.items.add(held);

		new KingsCrown().upgradeArmor(hero, null, new HeroicLeap());

		assertTrue(hero.belongings.backpack.contains(held));
		assertTrue(hero.armorAbility instanceof HeroicLeap);
		assertTrue(hero.talents.get(3).containsKey(Talent.BODY_SLAM));
	}

	@Test
	public void heldCrownIsConsumedAndSetsAbility() {
		KingsCrown crown = new KingsCrown();
		hero.belongings.backpack.items.add(crown);

		crown.upgradeArmor(hero, null, new HeroicLeap());

		assertFalse(hero.belongings.backpack.contains(crown));
		assertTrue(hero.armorAbility instanceof HeroicLeap);
		assertNull(hero.talentAutoOrderTier4);
	}

	@Test
	public void catchupKitGivesMaskAfterTenguAndCrownAfterKing() {
		PlayAlongCatchup.giveMasteryItems(hero, "goo", 2);
		assertNull(hero.belongings.getItem(TengusMask.class));
		assertNull(hero.belongings.getItem(KingsCrown.class));

		PlayAlongCatchup.giveMasteryItems(hero, "tengu", 3);
		assertNotNull(hero.belongings.getItem(TengusMask.class));
		assertNull(hero.belongings.getItem(KingsCrown.class));
		assertEquals(1, count(TengusMask.class));

		PlayAlongCatchup.giveMasteryItems(hero, "dm300", 4);
		assertEquals(1, count(TengusMask.class));
		assertNull(hero.belongings.getItem(KingsCrown.class));

		assertNull(hero.belongings.armor());
		PlayAlongCatchup.giveMasteryItems(hero, "king", 4);
		assertEquals(1, count(TengusMask.class));
		assertEquals(1, count(KingsCrown.class));
		assertNotNull(hero.belongings.armor());
		assertFalse(hero.belongings.armor() instanceof com.shatteredpixel.shatteredpixeldungeon.items.armor.ClassArmor);

		PlayAlongCatchup.giveMasteryItems(hero, "king", 4);
		assertEquals(1, count(TengusMask.class));
		assertEquals(1, count(KingsCrown.class));
	}

	private int count(Class<? extends Item> type) {
		int n = 0;
		for (Item item : hero.belongings.backpack.items) {
			if (type.isInstance(item)) n++;
		}
		return n;
	}

	private static Hero curUser() {
		try {
			Field field = Item.class.getDeclaredField("curUser");
			field.setAccessible(true);
			return (Hero) field.get(null);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	private static void setCurUser(Hero user) {
		try {
			Field field = Item.class.getDeclaredField("curUser");
			field.setAccessible(true);
			field.set(null, user);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	/** Enough of libGDX preferences for settings reads during hero setup. */
	private static final class MemoryPreferences implements Preferences {
		private final Map<String, Object> values = new HashMap<>();

		@Override public Preferences putBoolean(String key, boolean val) { values.put(key, val); return this; }
		@Override public Preferences putInteger(String key, int val) { values.put(key, val); return this; }
		@Override public Preferences putLong(String key, long val) { values.put(key, val); return this; }
		@Override public Preferences putFloat(String key, float val) { values.put(key, val); return this; }
		@Override public Preferences putString(String key, String val) { values.put(key, val); return this; }
		@Override public Preferences put(Map<String, ?> vals) { values.putAll(vals); return this; }
		@Override public boolean getBoolean(String key) { return getBoolean(key, false); }
		@Override public int getInteger(String key) { return getInteger(key, 0); }
		@Override public long getLong(String key) { return getLong(key, 0L); }
		@Override public float getFloat(String key) { return getFloat(key, 0f); }
		@Override public String getString(String key) { return getString(key, ""); }
		@Override public boolean getBoolean(String key, boolean defValue) {
			Object v = values.get(key);
			return v instanceof Boolean ? (Boolean) v : defValue;
		}
		@Override public int getInteger(String key, int defValue) {
			Object v = values.get(key);
			return v instanceof Integer ? (Integer) v : defValue;
		}
		@Override public long getLong(String key, long defValue) {
			Object v = values.get(key);
			return v instanceof Long ? (Long) v : defValue;
		}
		@Override public float getFloat(String key, float defValue) {
			Object v = values.get(key);
			return v instanceof Float ? (Float) v : defValue;
		}
		@Override public String getString(String key, String defValue) {
			Object v = values.get(key);
			return v instanceof String ? (String) v : defValue;
		}
		@Override public Map<String, ?> get() { return new HashMap<>(values); }
		@Override public boolean contains(String key) { return values.containsKey(key); }
		@Override public void clear() { values.clear(); }
		@Override public void remove(String key) { values.remove(key); }
		@Override public void flush() { }
	}
}
