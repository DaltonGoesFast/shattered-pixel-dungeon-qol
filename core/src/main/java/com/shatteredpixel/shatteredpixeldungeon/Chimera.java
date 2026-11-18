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
import com.shatteredpixel.shatteredpixeldungeon.items.KingsCrown;
import com.shatteredpixel.shatteredpixeldungeon.items.TengusMask;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;

/**
 * Chimera pact: T2/T3 class talent slots are metamorphed at hero creation; T1 is drafted on first
 * dungeon load from anti-duplicated metamorph offers. The mask and crown offer two pre-rolled
 * choices, each from a different class other than the hero's. The first choice from a class grants
 * its signature item.
 */
public class Chimera {

	private static final long SEED_SALT = 0x4368696DL; // "Chim"
	private static final long DRAFT_SALT = 0x44726166L; // "Draf"
	private static final long PAIR_SALT = 0x50616972L; // "Pair"

	private static final int CLASS_TALENT_TIERS = 3;
	private static final int DRAFT_OPTIONS_PER_SLOT = 4;

	private static final LinkedHashMap<Talent, HeroClass> TALENT_CLASSES = new LinkedHashMap<>();
	static {
		for (HeroClass cls : HeroClass.values()){
			ArrayList<LinkedHashMap<Talent, Integer>> clsTalents = new ArrayList<>();
			Talent.initClassTalents( cls, clsTalents );
			for (LinkedHashMap<Talent, Integer> tier : clsTalents){
				for (Talent t : tier.keySet()){
					TALENT_CLASSES.put( t, cls );
				}
			}
		}
	}

	public static boolean active( Hero hero ){
		ensurePairs( hero );
		return hero != null && Dungeon.isModified(Modifiers.CHIMERA) && hero.chimeraSubs != null;
	}

	/** True while Chimera's opening T1 draft still needs to be confirmed. */
	public static boolean draftPending( Hero hero ){
		return hero != null && Dungeon.isModified(Modifiers.CHIMERA) && hero.chimeraDraftPending;
	}

	/** True for the current run's hero viewing their own class; class info outside a run stays natural. */
	public static boolean inRunFor( HeroClass cls ){
		return active( Dungeon.hero ) && Dungeon.hero.heroClass == cls;
	}

	/** Runs once in initHero, after the class talents and before the class kit. */
	public static void rollOpening( Hero hero ){
		ArrayList<LinkedHashMap<Talent, Integer>> natural = new ArrayList<>();
		Talent.initClassTalents( hero.heroClass, natural );

		// Hard-roll T2 and T3 only; T1 uses its own salt so missing offers can be repaired identically.
		Random.pushGenerator( Dungeon.seed + SEED_SALT );
		try {
			for (int tier = 2; tier <= CLASS_TALENT_TIERS; tier++){
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

		Random.pushGenerator( Dungeon.seed + DRAFT_SALT );
		try {
			generateDraftOffers( hero, natural.get(0).keySet() );
		} finally {
			Random.popGenerator();
		}
		rollPairs( hero );
	}

	/** Pre-rolls T1 offer pools (4 choices per slot) from all classes including native, with no repeats across slots. */
	private static void generateDraftOffers( Hero hero, Set<Talent> t1Slots ){
		hero.chimeraDraftOffers = new LinkedHashMap<>();
		hero.chimeraDraftPicks = new LinkedHashMap<>();

		ArrayList<Talent> pool = new ArrayList<>();
		for (HeroClass cls : HeroClass.values()){
			ArrayList<LinkedHashMap<Talent, Integer>> clsTalents = new ArrayList<>();
			Talent.initClassTalents( cls, clsTalents );
			pool.addAll( clsTalents.get(0).keySet() );
		}
		Random.shuffle( pool );

		int index = 0;
		for (Talent slot : t1Slots){
			ArrayList<Talent> list = new ArrayList<>();
			for (int i = 0; i < DRAFT_OPTIONS_PER_SLOT && index < pool.size(); i++){
				list.add( pool.get( index++ ) );
			}
			hero.chimeraDraftOffers.put( slot, list );
		}
		hero.chimeraDraftPending = true;
	}

	/**
	 * Rebuilds missing T1 draft offers (e.g. bad mid-draft save). Keeps picks that are still valid.
	 * Uses {@link #DRAFT_SALT} so repair matches a fresh {@link #rollOpening(Hero)} for this seed.
	 */
	public static boolean ensureDraftOffers( Hero hero ){
		if (!draftPending( hero )) return false;
		if (hero.chimeraDraftOffers != null && !hero.chimeraDraftOffers.isEmpty()) return true;

		LinkedHashMap<Talent, Talent> priorPicks = hero.chimeraDraftPicks;
		Random.pushGenerator( Dungeon.seed + DRAFT_SALT );
		try {
			ArrayList<LinkedHashMap<Talent, Integer>> natural = new ArrayList<>();
			Talent.initClassTalents( hero.heroClass, natural );
			generateDraftOffers( hero, natural.get(0).keySet() );
		} finally {
			Random.popGenerator();
		}
		if (priorPicks != null && hero.chimeraDraftOffers != null) {
			hero.chimeraDraftPicks = new LinkedHashMap<>();
			for (Talent slot : hero.chimeraDraftOffers.keySet()) {
				Talent pick = priorPicks.get( slot );
				ArrayList<Talent> offers = hero.chimeraDraftOffers.get( slot );
				if (pick != null && offers != null && offers.contains( pick )) {
					hero.chimeraDraftPicks.put( slot, pick );
				}
			}
		}
		return hero.chimeraDraftOffers != null && !hero.chimeraDraftOffers.isEmpty();
	}

	/** Writes confirmed T1 picks into metamorphedTalents and clears draft state. */
	public static void applyDraft( Hero hero ){
		if (!draftPending( hero ) || hero.chimeraDraftOffers == null || hero.chimeraDraftPicks == null){
			return;
		}
		for (Talent slot : hero.chimeraDraftOffers.keySet()){
			Talent pick = hero.chimeraDraftPicks.get( slot );
			if (pick == null) return;
			if (pick != slot) {
				hero.metamorphedTalents.put( slot, pick );
			} else {
				hero.metamorphedTalents.remove( slot );
			}
		}
		rebuildClassTiers( hero );
		hero.chimeraDraftPending = false;
		hero.chimeraDraftOffers = null;
		hero.chimeraDraftPicks = null;
	}

	public static boolean draftComplete( Hero hero ){
		if (hero == null || hero.chimeraDraftOffers == null || hero.chimeraDraftPicks == null) return false;
		for (Talent slot : hero.chimeraDraftOffers.keySet()){
			if (!hero.chimeraDraftPicks.containsKey( slot )) return false;
		}
		return true;
	}

	private static final int CHOICES_COUNT = 3;

	/** Three subclasses and three armor abilities, each trio from distinct classes (at most one native),
	 *  on their own seeded generator. */
	private static void rollPairs( Hero hero ){
		Random.pushGenerator( Dungeon.seed + PAIR_SALT );
		try {
			ArrayList<HeroClass> subClassesList = new ArrayList<>( Arrays.asList( HeroClass.values() ) );
			Random.shuffle( subClassesList );
			HeroSubClass[] subs = new HeroSubClass[CHOICES_COUNT];
			for (int i = 0; i < CHOICES_COUNT; i++){
				subs[i] = Random.element( subClassesList.get(i).subClasses() );
			}
			hero.chimeraSubs = subs;

			ArrayList<HeroClass> abilClassesList = new ArrayList<>( Arrays.asList( HeroClass.values() ) );
			Random.shuffle( abilClassesList );
			ArmorAbility[] abils = new ArmorAbility[CHOICES_COUNT];
			for (int i = 0; i < CHOICES_COUNT; i++){
				abils[i] = Random.element( abilClassesList.get(i).armorAbilities() );
			}
			hero.chimeraAbilities = abils;
		} finally {
			Random.popGenerator();
		}
	}

	/** A Chimera run saved before its choices existed gets them now, from the same seed. */
	private static void ensurePairs( Hero hero ){
		if (hero != null && Dungeon.isModified(Modifiers.CHIMERA)
				&& (hero.chimeraSubs == null || hero.chimeraAbilities == null
				|| hero.chimeraSubs.length < CHOICES_COUNT || hero.chimeraAbilities.length < CHOICES_COUNT)){
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

	/** Subclasses the hero info window may name. Unreached Chimera rolls stay hidden. */
	public static HeroSubClass[] visibleSubclasses( HeroClass cls ){
		if (!inRunFor( cls )) return cls.subClasses();
		Hero hero = Dungeon.hero;
		boolean offering = (hero.subClass == null || hero.subClass == HeroSubClass.NONE)
				&& hero.belongings.getItem( TengusMask.class ) != null;
		ArrayList<HeroSubClass> shown = new ArrayList<>();
		for (HeroSubClass sub : hero.chimeraSubs){
			if (offering || sub == hero.subClass) shown.add( sub );
		}
		return shown.toArray( new HeroSubClass[0] );
	}

	/** Armor abilities the hero info window may name. Unreached Chimera rolls stay hidden. */
	public static ArmorAbility[] visibleAbilities( HeroClass cls ){
		if (!inRunFor( cls )) return cls.armorAbilities();
		Hero hero = Dungeon.hero;
		boolean offering = hero.armorAbility == null
				&& hero.belongings.getItem( KingsCrown.class ) != null;
		ArrayList<ArmorAbility> shown = new ArrayList<>();
		for (ArmorAbility abil : hero.chimeraAbilities){
			if (offering || sameAbility( abil, hero.armorAbility )) shown.add( abil );
		}
		return shown.toArray( new ArmorAbility[0] );
	}

	private static boolean sameAbility( ArmorAbility rolled, ArmorAbility chosen ){
		return chosen != null && rolled.getClass() == chosen.getClass();
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

	public static HeroClass classOf( Talent talent ){
		return talent != null ? TALENT_CLASSES.get( talent ) : null;
	}

	public static boolean isMetamorphedDesc( Hero hero, Talent talent ){
		if (hero == null || talent == null) return false;
		HeroClass cls = classOf( talent );
		return cls != null && cls != hero.heroClass;
	}

	/** Gives the class's signature item the first time a choice comes from that class. */
	public static void grantSignature( Hero hero, HeroClass cls ){
		if (!active( hero ) || cls == null || cls == hero.heroClass) return;
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
