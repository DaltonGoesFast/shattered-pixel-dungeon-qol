/*
 * Shattered Pixel Dungeon
 * Copyright (C) 2014-2026 Evan Debenham
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package com.shatteredpixel.shatteredpixeldungeon.utils;

import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.Statistics;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;

import java.util.ArrayList;

public class TalentAutoPlan {

	public static void tryApply( Hero hero ) {
		if (!SPDSettings.autoTalentPlan() || hero == null || !hero.isAlive() || hero.heroClass == null) {
			return;
		}

		final int maxPasses = 256;
		for (int pass = 0; pass < maxPasses; pass++) {
			boolean progress = false;
			for (int tier = 1; tier <= Talent.MAX_TALENT_TIERS; tier++) {
				hero.pruneStaleTalentAutoPlanEntries( tier );
				ArrayList<String> order = hero.talentAutoOrderForTier( tier );
				if (order == null || order.isEmpty()) {
					continue;
				}
				for (int i = 0; i < order.size(); i++) {
					String name = order.get( i );
					Talent t;
					try {
						t = Talent.valueOf( name );
					} catch (IllegalArgumentException e) {
						order.remove( i );
						i--;
						continue;
					}
					if (!hero.talents.get( tier - 1 ).containsKey( t )) {
						continue;
					}
					if (hero.talentPointsAvailableForAuto( tier ) <= 0) {
						continue;
					}
					if (hero.pointsInTalent( t ) >= t.maxPoints()) {
						continue;
					}
					hero.upgradeTalent( t );
					hero.appendTalentAutoSpendHistory( tier, name );
					order.remove( i );
					i--;
					Statistics.qualifiedForRandomVictoryBadge = false;
					progress = true;
				}
			}
			if (!progress) {
				break;
			}
		}

		for (int tier = 1; tier <= Talent.MAX_TALENT_TIERS; tier++) {
			if (hero.bonusTalentPoints( tier ) > 0) {
				spendRemainingUsingHistoryOrFallback( hero, tier );
			}
		}
	}

	/**
	 * Spend divine-inspiration bonus points when the pending queue no longer covers them:
	 * repeat the recorded auto-plan order, then tier talent order as a last resort.
	 * <p>
	 * Only spends up to {@link Hero#bonusTalentPoints(int)} per call &mdash; normal level
	 * talent points must stay for the queue (first pass) or manual assignment; otherwise
	 * this fallback would drain the whole tier (e.g. after King's Crown / Tengu's Mask
	 * clears the queue and history while inspiration is active).
	 */
	private static void spendRemainingUsingHistoryOrFallback( Hero hero, int tier ) {
		int bonusBudget = hero.bonusTalentPoints( tier );
		int guard = 0;
		while (bonusBudget > 0 && hero.talentPointsAvailable( tier ) > 0 && guard++ < 64) {
			boolean progressed = false;
			ArrayList<String> hist = hero.talentAutoSpendHistoryForTier( tier );
			if (hist != null) {
				for (String name : hist) {
					if (tryUpgradeNamed( hero, tier, name )) {
						progressed = true;
						bonusBudget--;
						break;
					}
				}
			}
			if (!progressed) {
				for (Talent t : hero.talents.get( tier - 1 ).keySet()) {
					if (tryUpgradeNamed( hero, tier, t.name() )) {
						progressed = true;
						bonusBudget--;
						break;
					}
				}
			}
			if (!progressed) {
				break;
			}
		}
	}

	private static boolean tryUpgradeNamed( Hero hero, int tier, String name ) {
		Talent t;
		try {
			t = Talent.valueOf( name );
		} catch (IllegalArgumentException e) {
			return false;
		}
		if (!hero.talents.get( tier - 1 ).containsKey( t )) {
			return false;
		}
		if (hero.pointsInTalent( t ) >= t.maxPoints()) {
			return false;
		}
		if (hero.talentPointsAvailable( tier ) <= 0) {
			return false;
		}
		hero.upgradeTalent( t );
		Statistics.qualifiedForRandomVictoryBadge = false;
		return true;
	}
}
