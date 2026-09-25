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
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.abilities.ArmorAbility;
import com.shatteredpixel.shatteredpixeldungeon.items.Amulet;
import com.shatteredpixel.shatteredpixeldungeon.items.Ankh;
import com.shatteredpixel.shatteredpixeldungeon.items.EquipableItem;
import com.shatteredpixel.shatteredpixeldungeon.items.Generator;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.ClassArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.Artifact;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.CloakOfShadows;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.HolyTome;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.Trinket;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.SpiritBow;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MagesStaff;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.ActionIndicator;
import com.shatteredpixel.shatteredpixeldungeon.utils.DungeonSeed;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndBag;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndOptions;
import com.watabou.noosa.Game;
import com.watabou.utils.Bundle;
import com.watabou.utils.FileUtils;

import java.util.ArrayList;

/**
 * Legacy pact: at the Amulet, end the run or loop onto a new seed with a growing kit.
 * The kit and held ankhs cross loops in memory only; nothing is written outside the save slot.
 */
public class Legacy {

	public static final int PHASE_NONE   = 0;
	public static final int PHASE_CHOOSE = 1;
	public static final int PHASE_PICK   = 2;

	private static final int MAX_SHOP_ANKHS_HELD = 4;
	private static final int ANKH_SHOP_DEPTH = 6;

	private static final String LOOP  = "legacy_loop";
	private static final String STEPS = "legacy_steps";
	private static final String PHASE = "legacy_phase";

	/** Loop 1 is the hero-select start. */
	public static int loop = 1;
	/** New main-path floors entered from loop 2 on; never resets within a run. */
	public static int steps = 0;
	public static int phase = PHASE_NONE;

	private static Carry carry = null;

	private static class Carry {
		int modifiers;
		int challenges;
		HeroClass heroClass;
		String customName;
		int loop;
		int steps;
		ArrayList<Item> kit = new ArrayList<>();
		ArrayList<Boolean> ringKnown = new ArrayList<>();
		ArrayList<Item> ankhs = new ArrayList<>();
		ArmorAbility ability;
	}

	public static boolean active(){
		return Dungeon.isModified(Modifiers.LEGACY);
	}

	public static void reset(){
		loop = 1;
		steps = 0;
		phase = PHASE_NONE;
	}

	public static void store( Bundle bundle ){
		bundle.put( LOOP, loop );
		bundle.put( STEPS, steps );
		bundle.put( PHASE, phase );
	}

	public static void restore( Bundle bundle ){
		loop = bundle.contains( LOOP ) ? bundle.getInt( LOOP ) : 1;
		steps = bundle.contains( STEPS ) ? bundle.getInt( STEPS ) : 0;
		phase = bundle.contains( PHASE ) ? bundle.getInt( PHASE ) : PHASE_NONE;
		carry = null;
	}

	public static float multiplier(){
		if (!active() || steps <= 0) return 1f;
		return 1f + 0.01f * steps;
	}

	/** Called where {@link Statistics#deepestFloor} advances on the main path. */
	public static void onNewDeepestFloor(){
		if (active() && loop >= 2) steps++;
	}

	public static boolean shopStocksAnkh(){
		if (!active()) return true;
		if (Dungeon.depth != ANKH_SHOP_DEPTH) return false;
		return heldAnkhs() < MAX_SHOP_ANKHS_HELD;
	}

	private static int heldAnkhs(){
		if (Dungeon.hero == null || Dungeon.hero.belongings == null) return 0;
		int count = 0;
		for (Item item : Dungeon.hero.belongings){
			if (item instanceof Ankh) count += item.quantity();
		}
		return count;
	}

	public static boolean eligible( Item item ){
		if (item == null || item.legacyKit) return false;
		if (item.getClass().getName().contains(".quest.")) return false;
		return item instanceof Weapon
				|| item instanceof Armor
				|| item instanceof Ring
				|| item instanceof Artifact
				|| item instanceof Wand
				|| item instanceof Trinket;
	}

	private static boolean hasEligible( Hero hero ){
		for (Item item : hero.belongings){
			if (eligible(item)) return true;
		}
		return false;
	}

	// ---- Amulet choice ----

	public static void offer( Amulet amulet ){
		phase = PHASE_CHOOSE;
		Game.runOnRenderThread(() -> showChoose(amulet));
	}

	/** Reopen a choice interrupted by quitting. Call once the game scene exists. */
	public static void resumeIfPending(){
		if (phase == PHASE_NONE) return;
		Hero hero = Dungeon.hero;
		Amulet amulet = hero == null ? null : hero.belongings.getItem(Amulet.class);
		if (!active() || amulet == null || Statistics.amuletObtained){
			phase = PHASE_NONE;
			return;
		}
		Game.runOnRenderThread(() -> {
			if (phase == PHASE_PICK) showPick(amulet);
			else showChoose(amulet);
		});
	}

	private static void showChoose( Amulet amulet ){
		GameScene.show(new WndOptions(
				Messages.get(Legacy.class, "choose_title"),
				Messages.get(Legacy.class, "choose_desc", loop),
				Messages.get(Legacy.class, "end"),
				Messages.get(Legacy.class, "loop")){
			@Override
			protected void onSelect( int index ){
				if (index == 0){
					phase = PHASE_NONE;
					Statistics.amuletObtained = true;
					amulet.claim();
				} else {
					phase = PHASE_PICK;
					showPick(amulet);
				}
			}

			@Override
			public void onBackPressed(){
				// the offer stays until End or Loop
			}
		});
	}

	private static void showPick( Amulet amulet ){
		Hero hero = Dungeon.hero;
		if (!hasEligible(hero)){
			commitLoop(null);
			return;
		}
		GameScene.show(new WndBag(hero.belongings.backpack, new WndBag.ItemSelector(){
			@Override
			public String textPrompt(){
				return Messages.get(Legacy.class, "pick_prompt");
			}

			@Override
			public boolean itemSelectable( Item item ){
				return eligible(item);
			}

			@Override
			public void onSelect( Item item ){
				if (item != null && eligible(item)){
					commitLoop(item);
				} else {
					Game.runOnRenderThread(() -> confirmSkip(amulet));
				}
			}
		}));
	}

	private static void confirmSkip( Amulet amulet ){
		GameScene.show(new WndOptions(
				Messages.get(Legacy.class, "skip_title"),
				Messages.get(Legacy.class, "skip_desc"),
				Messages.get(Legacy.class, "skip"),
				Messages.get(Legacy.class, "back")){
			@Override
			protected void onSelect( int index ){
				if (index == 0){
					commitLoop(null);
				} else {
					showPick(amulet);
				}
			}

			@Override
			public void onBackPressed(){
				hide();
				showPick(amulet);
			}
		});
	}

	// ---- Loop restart ----

	private static void commitLoop( Item picked ){
		Hero hero = Dungeon.hero;
		if (picked != null) picked.legacyKit = true;

		Carry c = new Carry();
		c.modifiers = Dungeon.modifiers;
		c.challenges = Dungeon.challenges;
		c.heroClass = hero.heroClass;
		c.customName = hero.customName;
		c.loop = loop + 1;
		c.steps = steps;

		boolean kitHasClassArmor = false;
		for (Item item : hero.belongings){
			if (item.legacyKit){
				c.kit.add(copy(item));
				c.ringKnown.add(item instanceof Ring && ((Ring) item).isKnown());
				if (item instanceof ClassArmor) kitHasClassArmor = true;
			} else if (item instanceof Ankh){
				c.ankhs.add(copy(item));
			}
		}
		if (kitHasClassArmor && hero.armorAbility != null){
			Bundle b = new Bundle();
			b.put("a", hero.armorAbility);
			c.ability = (ArmorAbility) b.get("a");
		}

		carry = c;
		phase = PHASE_NONE;

		Game.switchScene(InterlevelScene.class, new Game.SceneChangeCallback(){
			@Override
			public void beforeCreate(){
				Dungeon.hero = null;
				Dungeon.daily = false;
				Dungeon.dailyReplay = false;
				Dungeon.customSeedText = "";
				Dungeon.seed = DungeonSeed.randomSeed();
				GamesInProgress.selectedClass = c.heroClass;
				GamesInProgress.pendingHeroName = c.customName;
				ActionIndicator.clearAction();
				InterlevelScene.mode = InterlevelScene.Mode.DESCEND;
			}

			@Override
			public void afterCreate(){}
		});
	}

	private static Item copy( Item item ){
		Bundle b = new Bundle();
		b.put("i", item);
		return (Item) b.get("i");
	}

	public static boolean loopPending(){
		return carry != null;
	}

	public static int carriedModifiers(){
		return carry.modifiers;
	}

	public static int carriedChallenges(){
		return carry.challenges;
	}

	/** From {@link Dungeon#init()}: loop state from the carry, or a fresh run. */
	public static void initForRun(){
		if (carry != null){
			loop = carry.loop;
			steps = carry.steps;
			phase = PHASE_NONE;
		} else {
			reset();
		}
	}

	/** From {@link Dungeon#init()} after the starting kit exists. */
	public static void deliverCarry( Hero hero ){
		if (carry == null) return;
		Carry c = carry;
		carry = null;

		boolean unequipped = false;
		boolean kitHasSeal = false;
		boolean kitHasClassArmor = false;
		ArrayList<Integer> slots = new ArrayList<>();

		for (Item kit : c.kit){
			int slot = -1;
			Class<? extends Item> unique = classUnique(kit);
			if (unique != null){
				Item fresh = findFresh(hero, unique);
				if (fresh != null){
					slot = Dungeon.quickslot.getSlot(fresh);
					if (fresh.isEquipped(hero) && fresh instanceof EquipableItem){
						((EquipableItem) fresh).doUnequip(hero, false, false);
						unequipped = true;
					} else {
						fresh.detachAll(hero.belongings.backpack);
					}
					Dungeon.quickslot.clearItem(fresh);
				}
			}
			slots.add(slot);
			if (kit instanceof Armor && ((Armor) kit).checkSeal() != null) kitHasSeal = true;
			if (kit instanceof ClassArmor) kitHasClassArmor = true;
		}

		if (kitHasSeal && hero.belongings.armor != null && hero.belongings.armor.checkSeal() != null){
			hero.belongings.armor.detachSeal();
		}

		for (int i = 0; i < c.kit.size(); i++){
			Item kit = c.kit.get(i);
			kit.commandLoot = false;
			kit.legacyKit = true;
			if (kit instanceof Artifact){
				Generator.removeArtifact(((Artifact) kit).getClass());
			}
			if (!kit.collect(hero.belongings.backpack)){
				hero.belongings.backpack.items.add(kit);
			}
			if (c.ringKnown.get(i)){
				((Ring) kit).setKnown();
			}
			int slot = slots.get(i);
			if (slot != -1 && kit.defaultAction() != null){
				Dungeon.quickslot.setSlot(slot, kit);
			}
		}

		for (Item ankh : c.ankhs){
			ankh.commandLoot = false;
			if (!ankh.collect(hero.belongings.backpack)){
				hero.belongings.backpack.items.add(ankh);
			}
		}

		if (kitHasClassArmor && c.ability != null){
			hero.armorAbility = c.ability;
			Talent.initArmorTalents(hero);
		}

		if (unequipped){
			hero.spend(-hero.cooldown());
		}
	}

	private static Class<? extends Item> classUnique( Item item ){
		if (item instanceof SpiritBow) return SpiritBow.class;
		if (item instanceof CloakOfShadows) return CloakOfShadows.class;
		if (item instanceof MagesStaff) return MagesStaff.class;
		if (item instanceof HolyTome) return HolyTome.class;
		return null;
	}

	private static Item findFresh( Hero hero, Class<? extends Item> cls ){
		for (Item item : hero.belongings){
			if (cls.isInstance(item) && !item.legacyKit) return item;
		}
		return null;
	}

	/** Old seed's floors must not load into the new one. */
	public static void deleteFloorFiles( int slot ){
		String folder = GamesInProgress.gameFolder(slot);
		for (String file : FileUtils.filesInDir(folder)){
			if (file.contains("depth")){
				FileUtils.deleteFile(folder + "/" + file);
			}
		}
	}
}
