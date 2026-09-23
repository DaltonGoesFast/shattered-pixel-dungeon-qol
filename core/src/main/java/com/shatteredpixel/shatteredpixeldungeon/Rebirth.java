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
import com.shatteredpixel.shatteredpixeldungeon.items.Amulet;
import com.shatteredpixel.shatteredpixeldungeon.items.EnergyCrystal;
import com.shatteredpixel.shatteredpixeldungeon.items.EquipableItem;
import com.shatteredpixel.shatteredpixeldungeon.items.Generator;
import com.shatteredpixel.shatteredpixeldungeon.items.Gold;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.ClassArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.Artifact;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.Bag;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndBag;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndRebirth;
import com.watabou.noosa.Game;
import com.watabou.utils.Bundle;
import com.watabou.utils.Callback;
import com.watabou.utils.FileUtils;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Rebirth pact: cross-run gift queue in {@code rebirth.dat}.
 * Amulet sacrifice writes the item; death before sacrifice writes a consolation flag;
 * only a later Rebirth run consumes the queue.
 */
public class Rebirth {

	private static final String REBIRTH_FILE = "rebirth.dat";

	private static final String MODE = "mode";
	private static final String ITEM = "item";
	private static final String MODE_SACRIFICE = "sacrifice";
	private static final String MODE_CONSOLATION = "consolation";

	private static final String SACRIFICED = "rebirth_sacrificed";

	/** True after this run already wrote a sacrifice or amulet-time consolation. */
	public static boolean sacrificedThisRun = false;

	public static void reset(){
		sacrificedThisRun = false;
	}

	public static void store( Bundle bundle ){
		bundle.put( SACRIFICED, sacrificedThisRun );
	}

	public static void restore( Bundle bundle ){
		sacrificedThisRun = bundle.contains( SACRIFICED ) && bundle.getBoolean( SACRIFICED );
	}

	public static boolean eligible( Item item ){
		if (item == null) return false;
		if (item.unique) return false;
		if (item instanceof Amulet) return false;
		if (item instanceof ClassArmor) return false;
		if (item instanceof Bag) return false;
		if (item instanceof Gold || item instanceof EnergyCrystal) return false;
		if (item.getClass().getName().contains(".quest.")) return false;
		return true;
	}

	public static boolean hasEligible( Hero hero ){
		if (hero == null || hero.belongings == null) return false;
		for (Item item : hero.belongings){
			if (eligible(item)) return true;
		}
		return false;
	}

	/**
	 * Forced pick on first amulet obtain. Calls {@code after} once the queue is written
	 * (sacrifice or empty-inventory consolation).
	 */
	public static void offerOnAmuletPickup( Callback after ){
		if (!Dungeon.isModified(Modifiers.REBIRTH) || Dungeon.hero == null){
			if (after != null) after.call();
			return;
		}

		if (!hasEligible(Dungeon.hero)){
			writeConsolation();
			sacrificedThisRun = true;
			if (after != null) after.call();
			return;
		}

		Game.runOnRenderThread(() -> {
			WndBag.ItemSelector selector = new WndBag.ItemSelector() {
				@Override
				public String textPrompt() {
					return Messages.get(WndRebirth.class, "prompt");
				}

				@Override
				public boolean itemSelectable( Item item ) {
					return eligible(item);
				}

				@Override
				public void onSelect( Item item ) {
					if (item == null || !eligible(item)){
						// Should not happen with WndRebirth; re-offer if it does
						Game.runOnRenderThread(() -> offerOnAmuletPickup(after));
						return;
					}
					takeSacrifice(item);
					sacrificedThisRun = true;
					if (after != null) after.call();
				}
			};
			GameScene.show(new WndRebirth(Dungeon.hero.belongings.backpack, selector));
		});
	}

	public static void onRunFailed(){
		if (!Dungeon.isModified(Modifiers.REBIRTH)) return;
		if (sacrificedThisRun) return;
		writeConsolation();
	}

	/** Consume queue into starting inventory when this run has Rebirth on. */
	public static void deliverIfNeeded(){
		if (!Dungeon.isModified(Modifiers.REBIRTH)) return;
		if (Dungeon.hero == null) return;

		Bundle bundle = readFile();
		if (bundle == null || !bundle.contains(MODE)) return;

		String mode = bundle.getString(MODE);
		clearFile();

		Item gift = null;
		if (MODE_SACRIFICE.equals(mode) && bundle.contains(ITEM)){
			gift = (Item) bundle.get(ITEM);
		} else if (MODE_CONSOLATION.equals(mode)){
			gift = Generator.random();
		}

		if (gift == null) return;

		if (gift instanceof Artifact){
			Generator.removeArtifact(((Artifact) gift).getClass());
		}

		gift.commandLoot = false;
		giveGift(gift);
	}

	/**
	 * Gold/energy must never sit in the backpack: they have no drop/throw actions.
	 * Converts any leftover piles from an older Rebirth delivery.
	 */
	public static void claimStrayCurrency(){
		if (Dungeon.hero == null || Dungeon.hero.belongings == null) return;
		for (Gold g : new ArrayList<>(Dungeon.hero.belongings.getAllItems(Gold.class))){
			g.detachAll(Dungeon.hero.belongings.backpack);
			Dungeon.gold += g.quantity();
		}
		for (EnergyCrystal e : new ArrayList<>(Dungeon.hero.belongings.getAllItems(EnergyCrystal.class))){
			e.detachAll(Dungeon.hero.belongings.backpack);
			Dungeon.energy += e.quantity();
		}
	}

	private static void giveGift( Item gift ){
		if (gift instanceof Gold){
			Dungeon.gold += gift.quantity();
			return;
		}
		if (gift instanceof EnergyCrystal){
			Dungeon.energy += gift.quantity();
			return;
		}
		if (!gift.collect(Dungeon.hero.belongings.backpack)){
			// Starting kit always has room; force-add if somehow full
			Dungeon.hero.belongings.backpack.items.add(gift);
		}
	}

	private static void takeSacrifice( Item item ){
		Hero hero = Dungeon.hero;
		if (item.isEquipped(hero) && item instanceof EquipableItem){
			boolean wasCursed = item.cursed;
			item.cursed = false;
			((EquipableItem) item).doUnequip(hero, false, false);
			item.cursed = wasCursed;
		} else {
			item.detachAll(hero.belongings.backpack);
		}
		Dungeon.quickslot.clearItem(item);
		item.updateQuickslot();
		writeSacrifice(item);
	}

	private static void writeSacrifice( Item item ){
		Bundle bundle = new Bundle();
		bundle.put(MODE, MODE_SACRIFICE);
		bundle.put(ITEM, item);
		writeFile(bundle);
	}

	private static void writeConsolation(){
		Bundle bundle = new Bundle();
		bundle.put(MODE, MODE_CONSOLATION);
		writeFile(bundle);
	}

	private static Bundle readFile(){
		try {
			return FileUtils.bundleFromFile(REBIRTH_FILE);
		} catch (IOException e) {
			return null;
		}
	}

	private static void writeFile( Bundle bundle ){
		try {
			FileUtils.bundleToFile(REBIRTH_FILE, bundle);
		} catch (IOException e) {
			ShatteredPixelDungeon.reportException(e);
		}
	}

	private static void clearFile(){
		Bundle empty = new Bundle();
		writeFile(empty);
	}

}
