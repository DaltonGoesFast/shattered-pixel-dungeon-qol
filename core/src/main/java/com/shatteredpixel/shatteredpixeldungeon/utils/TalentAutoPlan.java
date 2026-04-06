/*
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
	}
}
