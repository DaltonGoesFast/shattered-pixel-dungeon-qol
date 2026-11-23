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

import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.CloakOfShadows;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfUpgrade;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfFireblast;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfLightning;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.darts.Dart;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongCatchupItems.Parsed;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PlayAlongCatchupItemsTest {

	private final PlayAlongCatchupItems.Resolver resolver = new PlayAlongCatchupItems.Resolver() {
		private final Map<String, Class<? extends Item>> names = new HashMap<>();
		{
			names.put("scrollofupgrade", ScrollOfUpgrade.class);
			names.put("sou", ScrollOfUpgrade.class);
			names.put("wandoflightning", WandOfLightning.class);
			names.put("wandoffireblast", WandOfFireblast.class);
			names.put("potionofhealing", PotionOfHealing.class);
			names.put("dart", Dart.class);
			names.put("cloakofshadows", CloakOfShadows.class);
		}

		@Override
		public Class<? extends Item> resolve(String name) {
			return names.get(name);
		}

		@Override
		public boolean artifact(Class<? extends Item> type) {
			return type == CloakOfShadows.class;
		}

		@Override
		public boolean stackable(Class<? extends Item> type) {
			return type == ScrollOfUpgrade.class || type == PotionOfHealing.class || type == Dart.class;
		}

		@Override
		public boolean upgradable(Class<? extends Item> type) {
			return type == WandOfLightning.class || type == WandOfFireblast.class || type == Dart.class;
		}
	};

	@Test
	public void quantitiesUpgradesAndCombinedSuffixes() {
		List<Parsed> parsed = PlayAlongCatchupItems.parse(
				"potionofhealingx10, wandoffireblast+1, dartx20, sou",
				false,
				resolver);

		assertEquals(4, parsed.size());

		Parsed healing = parsed.get(0);
		assertFalse(healing.skipped);
		assertEquals(10, healing.quantity);
		assertEquals(0, healing.upgrade);
		assertEquals(10, healing.giveQuantity(true));

		Parsed wand = parsed.get(1);
		assertEquals(WandOfFireblast.class, wand.type);
		assertEquals(1, wand.upgrade);
		assertEquals(1, wand.quantity);

		Parsed dart = parsed.get(2);
		assertEquals(20, dart.quantity);
		assertEquals(0, dart.upgrade);

		Parsed sou = parsed.get(3);
		assertEquals(ScrollOfUpgrade.class, sou.type);
		assertFalse(sou.skipped);
	}

	@Test
	public void capsUpgradeAndQuantity() {
		List<Parsed> parsed = PlayAlongCatchupItems.parse(
				"wandoflightning+9, dartx100, potionofhealing+3x50",
				false,
				resolver);

		assertEquals(PlayAlongCatchupItems.MAX_UPGRADE, parsed.get(0).upgrade);
		assertEquals("capped", parsed.get(0).reason);

		assertEquals(PlayAlongCatchupItems.MAX_QUANTITY, parsed.get(1).quantity);
		assertEquals("capped", parsed.get(1).reason);

		Parsed both = parsed.get(2);
		assertEquals(0, both.upgrade);
		assertEquals(PlayAlongCatchupItems.MAX_QUANTITY, both.quantity);
		assertEquals(1, both.giveQuantity(false));
	}

	@Test
	public void typosAndArtifactsAreSkipped() {
		List<Parsed> parsed = PlayAlongCatchupItems.parse(
				"notarealitem, cloakofshadows,  , wandoflightning+1",
				false,
				resolver);

		assertEquals(3, parsed.size());
		assertTrue(parsed.get(0).skipped);
		assertEquals("unknown item", parsed.get(0).reason);
		assertNull(parsed.get(0).type);

		assertTrue(parsed.get(1).skipped);
		assertEquals("artifacts are disabled", parsed.get(1).reason);

		assertFalse(parsed.get(2).skipped);
		assertEquals(1, parsed.get(2).upgrade);
	}

	@Test
	public void artifactsAllowedWhenConfigured() {
		List<Parsed> parsed = PlayAlongCatchupItems.parse("cloakofshadows+2", true, resolver);
		assertEquals(1, parsed.size());
		assertFalse(parsed.get(0).skipped);
		assertEquals(CloakOfShadows.class, parsed.get(0).type);
		assertEquals(0, parsed.get(0).upgrade);
	}

	@Test
	public void spacesAndAliasResolveOnTheGameIndex() {
		assertEquals(ScrollOfUpgrade.class, PlayAlongCatchupItems.resolveGame("sou"));
		assertEquals(ScrollOfUpgrade.class, PlayAlongCatchupItems.resolveGame("scrollofupgrade"));
		assertNull(PlayAlongCatchupItems.resolveGame("notarealitemxyz"));
		assertTrue(PlayAlongCatchupItems.knownNames().contains("scrollofupgrade"));
	}

	@Test
	public void brokenCatchupConfigFallsBackToDefaults() {
		PlayAlongCatchup.Config broken = PlayAlongCatchup.Config.parse("{");
		assertTrue(broken.enabled);
		assertEquals(6, broken.row("goo").spawnFloor);
		assertEquals(11, broken.row("tengu").level);
		assertEquals(13, broken.row("dm300").str);
		assertEquals(8, broken.row("king").sou);
		assertEquals(4, broken.row("king").weaponTier);
		assertFalse(broken.allowArtifacts);

		PlayAlongCatchup.Config partial = PlayAlongCatchup.Config.parse(
				"{\"enabled\":false,\"king\":{\"weapon_tier\":5,\"spawn_floor\":99}}");
		assertFalse(partial.enabled);
		assertEquals(5, partial.row("king").weaponTier);
		assertEquals(21, partial.row("king").spawnFloor);
	}
}
