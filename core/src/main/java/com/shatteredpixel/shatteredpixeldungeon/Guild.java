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

import com.shatteredpixel.shatteredpixeldungeon.items.Generator;
import com.shatteredpixel.shatteredpixeldungeon.items.Honeypot;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.Stylus;
import com.shatteredpixel.shatteredpixeldungeon.items.Torch;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.ClassArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.LeatherArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.MailArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.PlateArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.ScaleArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.Artifact;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.items.food.SmallRation;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfIdentify;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfMagicMapping;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfRemoveCurse;
import com.shatteredpixel.shatteredpixeldungeon.items.spells.Alchemize;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfAugmentation;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.darts.TippedDart;
import com.watabou.utils.Random;

import java.util.ArrayList;

/**
 * Guild pact: each shop keeps one craft. Gear on the shelf keeps its rolled level and curse,
 * hidden until bought, and the keeper pays more when you sell his own trade back.
 */
public class Guild {

	public static final int ARMS   = 0;
	public static final int MAIL   = 1;
	public static final int ARCANA = 2;
	public static final int STORES = 3;

	public static final float SELL_PREMIUM = 1.3f;

	public static boolean active(){
		return Dungeon.isModified(Modifiers.GUILD);
	}

	public static int craft(){
		switch (Dungeon.depth){
			case 6: default: return ARMS;
			case 11:         return MAIL;
			case 16:         return ARCANA;
			case 20: case 21: return STORES;
		}
	}

	/** Craft shelf, the vanilla potion and scroll mix, healing, ration, and imp torches. Bag, ankh, and sandbags come from ShopRoom. */
	public static ArrayList<Item> stock(){
		ArrayList<Item> items = new ArrayList<>();

		switch (craft()){
			case ARMS:
				for (int i = 0; i < 2; i++) items.add( hide( Generator.randomUsingDefaults( Generator.wepTiers[1] ) ) );
				for (int i = 0; i < 4; i++) items.add( hide( Generator.randomUsingDefaults( Generator.wepTiers[2] ) ) );
				for (int i = 0; i < 2; i++) items.add( hide( Generator.randomUsingDefaults( Generator.wepTiers[3] ) ) );
				int lastTier = 2;
				if (Random.Int(2) == 0) lastTier = Random.Int(4) == 0 ? 4 : 3;
				items.add( hide( Generator.randomUsingDefaults( Generator.wepTiers[lastTier] ) ) );
				for (int i = 0; i < 5; i++) items.add( hide( Generator.randomUsingDefaults( Generator.misTiers[1] ) ) );
				for (int i = 0; i < 2; i++) items.add( TippedDart.randomTipped(2) );
				items.add( new Stylus() );
				for (int i = 0; i < 2; i++) items.add( new ScrollOfRemoveCurse() );
				items.add( new StoneOfAugmentation() );
				break;

			case MAIL:
				for (int i = 0; i < 5; i++) items.add( hide( new MailArmor().random() ) );
				items.add( hide( new LeatherArmor().random() ) );
				items.add( hide( new ScaleArmor().random() ) );
				for (int i = 0; i < 3; i++) items.add( hide( new PlateArmor().random() ) );
				items.add( new ScrollOfRemoveCurse() );
				items.add( new StoneOfAugmentation() );
				break;

			case ARCANA:
				for (int i = 0; i < 9; i++) items.add( hide( Generator.randomUsingDefaults( Generator.Category.WAND ) ) );
				for (int i = 0; i < 5; i++) items.add( hide( Generator.randomUsingDefaults( Generator.Category.RING ) ) );
				items.add( hide( Generator.random( Generator.Category.ARTIFACT ) ) );
				for (int i = 0; i < 2; i++) items.add( new ScrollOfIdentify() );
				break;

			case STORES:
				items.add( new ScrollOfMagicMapping() );
				items.add( new Alchemize().quantity( Random.IntRange(2, 3) ) );
				for (int i = 0; i < 6; i++) items.add( Generator.randomUsingDefaults( Generator.Category.POTION ) );
				for (int i = 0; i < 6; i++) items.add( Generator.randomUsingDefaults( Generator.Category.SCROLL ) );
				for (int i = 0; i < 2; i++){
					switch (Random.Int(4)){
						case 0:          items.add( new Bomb() );            break;
						case 1: case 2:  items.add( new Bomb.DoubleBomb() ); break;
						case 3:          items.add( new Honeypot() );        break;
					}
				}
				break;
		}

		//Same unidentified mix as a normal shop, so healing and remove curse are not the only colors.
		//The imp already stocks six random potions and six random scrolls.
		if (craft() != STORES){
			items.add( Generator.randomUsingDefaults( Generator.Category.POTION ) );
			items.add( Generator.randomUsingDefaults( Generator.Category.POTION ) );
			if (craft() != ARCANA) items.add( new ScrollOfIdentify() );
			if (craft() == ARCANA) items.add( new ScrollOfRemoveCurse() );
			items.add( new ScrollOfMagicMapping() );
			for (int i = 0; i < 2; i++){
				items.add( Random.Int(2) == 0 ?
						Generator.randomUsingDefaults( Generator.Category.POTION ) :
						Generator.randomUsingDefaults( Generator.Category.SCROLL ) );
			}
		}

		items.add( new PotionOfHealing() );
		items.add( new SmallRation() );

		if (craft() == STORES){
			items.add( new Torch() );
			items.add( new Torch() );
			items.add( new Torch() );
		}

		return items;
	}

	/** Rerolls level at shelf odds, keeps the curse, and holds a good enchant or glyph off the item until it is bought. */
	private static Item hide( Item item ){
		if (!(item instanceof Artifact)){
			//+0: 45%, +1: 35%, +2: 15%, +3: 5%
			float roll = Random.Float();
			item.level( roll < 0.45f ? 0 : roll < 0.80f ? 1 : roll < 0.95f ? 2 : 3 );
			if (item instanceof Wand) ((Wand) item).curCharges = ((Wand) item).maxCharges;
		}
		if (item instanceof Weapon && ((Weapon) item).hasGoodEnchant()){
			((Weapon) item).shelfEnchant = ((Weapon) item).enchantment;
			((Weapon) item).enchant( null );
		} else if (item instanceof Armor && ((Armor) item).hasGoodGlyph()){
			((Armor) item).shelfGlyph = ((Armor) item).glyph;
			((Armor) item).inscribe( null );
		}
		item.levelKnown = false;
		item.cursedKnown = false;
		return item;
	}

	public static boolean isTrade( Item item ){
		switch (craft()){
			case ARMS:   return item instanceof Weapon;
			case MAIL:   return item instanceof Armor && !(item instanceof ClassArmor);
			case ARCANA: return item instanceof Wand || item instanceof Ring || item instanceof Artifact;
			case STORES: return item instanceof Potion || item instanceof Scroll || item instanceof Bomb
					|| item instanceof Honeypot || item instanceof Alchemize || item instanceof Torch;
		}
		return false;
	}

	/** Gold the keeper pays for an item worth {@code value}; the craft's own trade earns the premium. */
	public static int paysFor( Item item, int value ){
		if (!active() || value <= 0 || !isTrade( item )) return value;
		return Math.round( value * SELL_PREMIUM );
	}

	public static int paysFor( Item item ){
		return paysFor( item, item.value() );
	}
}
