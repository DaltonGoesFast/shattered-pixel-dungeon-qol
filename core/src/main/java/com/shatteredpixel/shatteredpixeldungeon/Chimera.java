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
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroSubClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.abilities.ArmorAbility;
import com.shatteredpixel.shatteredpixeldungeon.items.BrokenSeal;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.Artifact;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.CloakOfShadows;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.HolyTome;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.ScrollOfMetamorphosis;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfMagicMissile;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.SpiritBow;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MagesStaff;
import com.watabou.utils.Random;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;

/**
 * Chimera pact: every class talent slot is metamorphed at hero creation, and the mask and crown
 * offer two pre-rolled choices from any class. The first choice from a class grants its signature item.
 */
public class Chimera {

	private static final long SEED_SALT = 0x4368696DL; // "Chim"
	private static final long PAIR_SALT = 0x50616972L; // "Pair"

	private static final int CLASS_TALENT_TIERS = 3;

	public static boolean active( Hero hero ){
		ensurePairs( hero );
		return hero != null && Dungeon.isModified(Modifiers.CHIMERA) && hero.chimeraSubs != null;
	}

	/** True for the current run's hero viewing their own class; class info outside a run stays natural. */
	public static boolean inRunFor( HeroClass cls ){
		return active( Dungeon.hero ) && Dungeon.hero.heroClass == cls;
	}

	/** Runs once in initHero, after the class talents and before the class kit. */
	public static void rollOpening( Hero hero ){
		Random.pushGenerator( Dungeon.seed + SEED_SALT );
		try {
			ArrayList<LinkedHashMap<Talent, Integer>> natural = new ArrayList<>();
			Talent.initClassTalents( hero.heroClass, natural );

			for (int tier = 1; tier <= CLASS_TALENT_TIERS; tier++){
				for (Talent slot : new ArrayList<>(natural.get(tier-1).keySet())){
					LinkedHashMap<Talent, Integer> offers = ScrollOfMetamorphosis.offersFor( hero, slot, tier );
					if (offers.isEmpty()) continue;
					hero.metamorphedTalents.put( slot, Random.element( offers.keySet() ) );
					rebuildClassTiers( hero );
				}
			}

		} finally {
			Random.popGenerator();
		}
		rollPairs( hero );
	}

	/** Two subclasses and two armor abilities from the other five classes, on their own seeded generator. */
	private static void rollPairs( Hero hero ){
		Random.pushGenerator( Dungeon.seed + PAIR_SALT );
		try {
			ArrayList<HeroSubClass> subs = new ArrayList<>();
			ArrayList<ArmorAbility> abils = new ArrayList<>();
			for (HeroClass cls : HeroClass.values()){
				if (cls == hero.heroClass) continue;
				subs.addAll( Arrays.asList( cls.subClasses() ) );
				abils.addAll( Arrays.asList( cls.armorAbilities() ) );
			}

			HeroSubClass subA = Random.element( subs );
			subs.remove( subA );
			hero.chimeraSubs = new HeroSubClass[]{ subA, Random.element( subs ) };

			ArmorAbility abilA = Random.element( abils );
			abils.remove( abilA );
			hero.chimeraAbilities = new ArmorAbility[]{ abilA, Random.element( abils ) };
		} finally {
			Random.popGenerator();
		}
	}

	/** A Chimera run saved before its pairs existed gets them now, from the same seed. */
	private static void ensurePairs( Hero hero ){
		if (hero != null && Dungeon.isModified(Modifiers.CHIMERA)
				&& (hero.chimeraSubs == null || hero.chimeraAbilities == null)){
			rollPairs( hero );
		}
	}

	private static void rebuildClassTiers( Hero hero ){
		for (int i = 0; i < CLASS_TALENT_TIERS; i++){
			hero.talents.get(i).clear();
		}
		Talent.initClassTalents( hero );
	}

	public static HeroSubClass[] subclassChoices( Hero hero ){
		return active( hero ) ? hero.chimeraSubs : hero.heroClass.subClasses();
	}

	public static ArmorAbility[] abilityChoices( Hero hero ){
		return active( hero ) ? hero.chimeraAbilities : hero.heroClass.armorAbilities();
	}

	public static HeroClass classOf( HeroSubClass sub ){
		for (HeroClass cls : HeroClass.values()){
			for (HeroSubClass s : cls.subClasses()){
				if (s == sub) return cls;
			}
		}
		return null;
	}

	public static HeroClass classOf( ArmorAbility ability ){
		if (ability == null) return null;
		for (HeroClass cls : HeroClass.values()){
			for (ArmorAbility a : cls.armorAbilities()){
				if (a.getClass() == ability.getClass()) return cls;
			}
		}
		return null;
	}

	/** Gives the class's signature item the first time a choice comes from that class. */
	public static void grantSignature( Hero hero, HeroClass cls ){
		if (!active( hero ) || cls == null) return;
		int bit = 1 << cls.ordinal();
		if ((hero.chimeraGifts & bit) != 0) return;
		hero.chimeraGifts |= bit;

		switch (cls){
			case MAGE:
				if (hero.belongings.getItem( MagesStaff.class ) != null) return;
				MagesStaff staff = new MagesStaff( new WandOfMagicMissile() );
				if (Challenges.isItemBlocked( staff )) return;
				staff.identify();
				if (hero.belongings.weapon == null){
					hero.belongings.weapon = staff;
					staff.activate( hero );
					hero.notePickupResult( staff, true );
				} else {
					give( hero, staff );
				}
				break;
			case CLERIC:
				giveArtifact( hero, HolyTome.class, new HolyTome() );
				break;
			case ROGUE:
				giveArtifact( hero, CloakOfShadows.class, new CloakOfShadows() );
				break;
			case HUNTRESS:
				if (hero.belongings.getItem( SpiritBow.class ) != null) return;
				SpiritBow bow = new SpiritBow();
				if (Challenges.isItemBlocked( bow )) return;
				give( hero, bow.identify() );
				break;
			case WARRIOR:
				if (ownsSeal( hero )) return;
				BrokenSeal seal = new BrokenSeal();
				if (Challenges.isItemBlocked( seal )) return;
				seal.identify();
				Armor worn = hero.belongings.armor;
				if (worn != null && worn.checkSeal() == null){
					worn.affixSeal( seal );
					hero.notePickupResult( seal, true );
				} else {
					give( hero, seal );
				}
				break;
			case DUELIST: default:
				break;
		}
	}

	private static void giveArtifact( Hero hero, Class<? extends Artifact> type, Artifact art ){
		if (hero.belongings.getItem( type ) != null) return;
		if (Challenges.isItemBlocked( art )) return;
		art.cursed = false;
		art.identify();
		if (hero.belongings.artifact == null){
			hero.belongings.artifact = art;
			art.activate( hero );
			hero.notePickupResult( art, true );
		} else {
			give( hero, art );
		}
	}

	private static boolean ownsSeal( Hero hero ){
		if (hero.belongings.getItem( BrokenSeal.class ) != null) return true;
		for (Armor a : hero.belongings.getAllItems( Armor.class )){
			if (a.checkSeal() != null) return true;
		}
		return false;
	}

	private static void give( Hero hero, Item item ){
		boolean collected = item.collect();
		hero.notePickupResult( item, collected );
		if (!collected && Dungeon.level != null){
			Dungeon.level.drop( item, hero.pos ).sprite.drop();
		}
	}
}
