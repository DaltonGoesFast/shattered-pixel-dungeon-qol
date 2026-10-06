/*
 * Debug actions shared by the player debug tab and the streamer debug commands.
 * Results starting with "ERR:" are failures; anything else is a success summary.
 */

package com.shatteredpixel.shatteredpixeldungeon.utils;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.Badges;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.Statistics;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.WaterOfHealth;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Degrade;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Healing;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Hunger;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.effects.CellEmitter;
import com.shatteredpixel.shatteredpixeldungeon.effects.FloatingText;
import com.shatteredpixel.shatteredpixeldungeon.effects.Speck;
import com.shatteredpixel.shatteredpixeldungeon.effects.SpellSprite;
import com.shatteredpixel.shatteredpixeldungeon.effects.particles.ShaftParticle;
import com.shatteredpixel.shatteredpixeldungeon.items.Gold;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.MagicalHolster;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.PotionBandolier;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.ScrollHolder;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.VelvetPouch;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfIdentify;
import com.zrp200.scrollofdebug.ScrollOfDebug;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfMagicMapping;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfRemoveCurse;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfTeleportation;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.VialOfBlood;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite;
import com.watabou.noosa.Game;
import com.watabou.noosa.audio.Sample;
import com.watabou.utils.Random;
import com.watabou.utils.Reflection;

import java.util.ArrayList;

public final class PlayerDebugActions {

	public static final int MAX_QUANTITY = 999;
	public static final int MAX_UPGRADE = 99;
	public static final int MAX_FLOOR = 26;

	private PlayerDebugActions() {}

	public static boolean isError(String result) {
		return result != null && result.startsWith("ERR:");
	}

	/** Null when a living hero is in an active run, otherwise an error result. */
	public static String precheck() {
		if (Dungeon.hero == null || Dungeon.level == null)
			return "ERR:Not in an active run (title/menu)";
		if (!(ShatteredPixelDungeon.scene() instanceof GameScene))
			return "ERR:Not in an active run (title/menu)";
		if (!Dungeon.hero.isAlive())
			return "ERR:Hero is dead";
		return null;
	}

	/**
	 * Full heal like a well of healing: removes all debuffs,
	 * cleanses curses on equipped and inventory gear, satisfies hunger, heals to full.
	 */
	public static String healAll() {
		String err = precheck();
		if (err != null) return err;
		Hero hero = Dungeon.hero;

		ArrayList<Buff> negatives = new ArrayList<>();
		for (Buff b : hero.buffs()) {
			if (b.type == Buff.buffType.NEGATIVE) negatives.add(b);
		}
		for (Buff b : negatives) b.detach();

		PotionOfHealing.cure(hero);

		Degrade degrade = hero.buff(Degrade.class);
		if (degrade != null) degrade.detach();

		ArrayList<Item> uncursables = new ArrayList<>();
		for (Item item : hero.belongings) {
			if (ScrollOfRemoveCurse.uncursable(item)) uncursables.add(item);
		}
		if (!uncursables.isEmpty()) {
			ScrollOfRemoveCurse.uncurse(hero, uncursables.toArray(new Item[0]));
		} else {
			hero.belongings.uncurseEquipped();
		}

		Sample.INSTANCE.play(Assets.Sounds.DRINK);
		hero.buff(Hunger.class).satisfy(Hunger.STARVING);

		if (VialOfBlood.delayBurstHealing()) {
			Healing healing = Buff.affect(hero, Healing.class);
			healing.setHeal(hero.HT, 0, VialOfBlood.maxHealPerTurn(), true);
		} else {
			hero.HP = hero.HT;
			hero.sprite.emitter().start(Speck.factory(Speck.HEALING), 0.4f, 4);
			hero.sprite.showStatusWithIcon(CharSprite.POSITIVE, Integer.toString(hero.HT), FloatingText.HEALING);
		}

		CellEmitter.get(hero.pos).start(ShaftParticle.FACTORY, 0.2f, 3);
		hero.interrupt();
		hero.updateHT(false);

		GLog.p(Messages.get(WaterOfHealth.class, "procced"));
		return "Well of healing";
	}

	/** Identify all items in inventory and equipment. */
	public static String identifyAll() {
		String err = precheck();
		if (err != null) return err;
		Hero hero = Dungeon.hero;
		int count = 0;
		for (Item item : hero.belongings) {
			if (item != null && !item.isIdentified()) {
				ScrollOfIdentify.IDItem(item);
				count++;
			}
		}
		Item.updateQuickslot();
		if (count == 0) {
			GLog.i(Messages.get(PlayerDebugActions.class, "identify_none"));
			return "Nothing to identify";
		}
		GLog.p(Messages.get(PlayerDebugActions.class, "identify_all", count));
		return "Identified " + count + " item(s)";
	}

	/** Give any shop bags the hero does not already have. */
	@SuppressWarnings("unchecked")
	public static String giveBags() {
		String err = precheck();
		if (err != null) return err;
		Hero hero = Dungeon.hero;
		Class<? extends Item>[] bags = new Class[]{
				VelvetPouch.class, ScrollHolder.class, PotionBandolier.class, MagicalHolster.class
		};
		ArrayList<String> given = new ArrayList<>();
		for (Class<? extends Item> clazz : bags) {
			if (hero.belongings.getItem(clazz) != null) continue;
			Item bag = Reflection.newInstance(clazz);
			if (bag == null) return "ERR:Failed to create bag";
			bag.identify(false);
			if (!bag.collect(hero.belongings.backpack)) {
				if (given.isEmpty()) return "ERR:Inventory full";
				return "ERR:Inventory full (delivered " + String.join(", ", given) + " only)";
			}
			given.add(bag.name());
		}
		Item.updateQuickslot();
		if (given.isEmpty()) return "Already have all bags";
		String detail = String.join(", ", given);
		GLog.p(Messages.get(PlayerDebugActions.class, "give_bags", detail));
		return detail;
	}

	/** Magic mapping for the current floor (like scroll of magic mapping). */
	public static String revealMap() {
		String err = precheck();
		if (err != null) return err;
		Hero hero = Dungeon.hero;

		int length = Dungeon.level.length();
		int[] map = Dungeon.level.map;
		boolean[] mapped = Dungeon.level.mapped;
		boolean[] discoverable = Dungeon.level.discoverable;
		boolean noticed = false;

		for (int i = 0; i < length; i++) {
			int terr = map[i];
			if (discoverable[i]) {
				mapped[i] = true;
				if ((Terrain.flags[terr] & Terrain.SECRET) != 0) {
					Dungeon.level.discover(i);
					if (Dungeon.level.heroFOV[i]) {
						GameScene.discoverTile(i, terr);
						ScrollOfMagicMapping.discover(i);
						noticed = true;
					}
				}
			}
		}
		GameScene.updateFog();
		GLog.i(Messages.get(ScrollOfMagicMapping.class, "layout"));
		if (noticed) {
			Sample.INSTANCE.play(Assets.Sounds.SECRET);
		}
		SpellSprite.show(hero, SpellSprite.MAP);
		Sample.INSTANCE.play(Assets.Sounds.READ);
		hero.interrupt();
		return "Map revealed";
	}

	/** Teleport to floor exit (down) or entrance (up). */
	public static String gotoStairs(boolean stairsDown) {
		String err = precheck();
		if (err != null) return err;
		Hero hero = Dungeon.hero;

		int cell = stairsDown ? Dungeon.level.exit() : Dungeon.level.entrance();
		if (cell <= 0) {
			return stairsDown ? "ERR:No stairs down on this level" : "ERR:No stairs up on this level";
		}
		if (!ScrollOfTeleportation.teleportToLocation(hero, cell)) {
			return "ERR:Cannot reach stairs (blocked)";
		}
		hero.interrupt();
		GLog.i(Messages.get(ScrollOfTeleportation.class, "tele"));
		return stairsDown ? "Stairs down" : "Stairs up";
	}

	/** Give item by display or class name. */
	public static String giveItem(String itemName, int quantity, int level) {
		String err = precheck();
		if (err != null) return err;
		if (itemName == null || itemName.trim().isEmpty())
			return "ERR:No item name";

		String ambig = DebugItemResolver.ambiguousMessage(itemName);
		if (ambig != null) return "ERR:" + ambig;

		Class<? extends Item> clazz = DebugItemResolver.resolveClass(itemName);
		if (clazz == null)
			return "ERR:" + DebugItemResolver.unknownMessage(itemName);

		return giveItem(clazz, quantity, level);
	}

	/**
	 * Put a Scroll of Debug in the hero's inventory if this is a debug run and they don't have one.
	 * The same grant runs when a debug floor loads; this is the mid-floor replacement.
	 */
	public static String giveScrollOfDebug() {
		String err = precheck();
		if (err != null) return err;
		if (!ScrollOfDebug.isDebugRun()) {
			return "ERR:" + Messages.get(PlayerDebugActions.class, "scroll_denied");
		}
		boolean had = Dungeon.hero.belongings.getItem(ScrollOfDebug.class) != null;
		ScrollOfDebug.handleDebug();
		if (Dungeon.hero.belongings.getItem(ScrollOfDebug.class) == null) {
			return "ERR:" + Messages.get(PlayerDebugActions.class, "scroll_failed");
		}
		String msg = Messages.get(PlayerDebugActions.class, had ? "scroll_already" : "scroll_given");
		if (!had) GLog.p(msg);
		return msg;
	}

	/** Give an item class: identified, uncursed, at the given upgrade level. Gold goes to the purse. */
	public static String giveItem(Class<? extends Item> clazz, int quantity, int level) {
		String err = precheck();
		if (err != null) return err;
		if (clazz == null) return "ERR:No item";
		if (ScrollOfDebug.class.isAssignableFrom(clazz)) {
			return "ERR:" + Messages.get(PlayerDebugActions.class, "scroll_denied");
		}

		Hero hero = Dungeon.hero;
		quantity = Math.max(1, Math.min(quantity, MAX_QUANTITY));
		level = Math.max(0, Math.min(level, MAX_UPGRADE));

		if (clazz == Gold.class) {
			Dungeon.gold += quantity;
			Statistics.goldCollected += quantity;
			Badges.validateGoldCollected();
			hero.sprite.showStatusWithIcon(CharSprite.NEUTRAL, Integer.toString(quantity), FloatingText.GOLD);
			Sample.INSTANCE.play(Assets.Sounds.GOLD, 1, 1, Random.Float(0.9f, 1.1f));
			String detail = quantity + " gold";
			GLog.p(Messages.get(PlayerDebugActions.class, "give_item", detail));
			return detail;
		}

		int remaining = quantity;
		String lastTitle = null;
		while (remaining > 0) {
			Item item = Reflection.newInstance(clazz);
			if (item == null) return "ERR:Failed to create item";

			if (level > 0) {
				item.level(level);
				item.levelKnown = true;
			}
			item.identify(false);
			item.cursed = false;
			item.cursedKnown = true;

			if (item instanceof Wand) {
				((Wand) item).updateLevel();
			}

			int stackAmt = 1;
			if (item.stackable) {
				stackAmt = Math.min(remaining, 20);
				item.quantity(stackAmt);
				remaining -= stackAmt;
			} else {
				remaining--;
			}

			if (!item.collect(hero.belongings.backpack)) {
				if (lastTitle == null)
					return "ERR:Inventory full";
				return "ERR:Inventory full (delivered " + lastTitle + " only)";
			}
			lastTitle = item.title();
		}

		Item.updateQuickslot();
		String detail = lastTitle != null ? lastTitle : clazz.getSimpleName();
		if (quantity > 1) detail += " x" + quantity;
		GLog.p(Messages.get(PlayerDebugActions.class, "give_item", detail));
		return detail;
	}

	/** Set hero level (1–30). */
	public static String setHeroLevel(int level) {
		String err = precheck();
		if (err != null) return err;
		Hero hero = Dungeon.hero;
		int target = Math.max(1, Math.min(level, Hero.MAX_LEVEL));
		int from = hero.lvl;
		if (target == from) return "Already level " + from;
		hero.setLevelForDebug(target);
		GLog.p(Messages.get(PlayerDebugActions.class, "set_level", hero.lvl));
		return "Level " + from + " → " + hero.lvl;
	}

	/** Warp to a dungeon floor (1–26, branch 0). */
	public static String gotoFloor(int depth) {
		String err = precheck();
		if (err != null) return err;
		int dest = Math.max(1, Math.min(depth, MAX_FLOOR));
		if (dest == Dungeon.depth && Dungeon.branch == 0)
			return "Already on floor " + dest;
		InterlevelScene.mode = InterlevelScene.Mode.RETURN;
		InterlevelScene.returnDepth = dest;
		InterlevelScene.returnBranch = 0;
		InterlevelScene.returnPos = -1;
		InterlevelScene.debugGenerateSkipped = true;
		GLog.i(Messages.get(PlayerDebugActions.class, "goto_floor", dest));
		Game.switchScene(InterlevelScene.class);
		return "Floor " + dest;
	}
}
