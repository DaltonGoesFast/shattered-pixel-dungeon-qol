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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package com.shatteredpixel.shatteredpixeldungeon.journal;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Rat;
import com.shatteredpixel.shatteredpixeldungeon.items.Ankh;
import com.shatteredpixel.shatteredpixeldungeon.items.Dewdrop;
import com.shatteredpixel.shatteredpixeldungeon.items.Gold;
import com.watabou.noosa.Game;
import com.watabou.utils.Bundle;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JournalProgressTest {

	static {
		// Document's static initializer asks whether the build is INDEV.
		Game.version = "4.0.3-QoL-test";
	}

	@Before
	public void resetJournal() {
		Dungeon.debugRun = false;
		Journal.unload();
	}

	@Test
	public void freshProfilesAreNotGranted() {
		assertFalse(Journal.shouldGrantFullJournal(0, false));
		assertTrue(Journal.shouldGrantFullJournal(965, false));
		assertFalse(Journal.shouldGrantFullJournal(965, true));
	}

	@Test
	public void fullGrantMarksCatalogBestiaryAndDocuments() {
		Journal.grantFullCompletion();

		for (Catalog cat : Catalog.values()) {
			assertEquals(cat.name(), cat.totalItems(), cat.totalSeen());
		}
		assertTrue(Catalog.isSeen(Gold.class));
		assertTrue(Catalog.useCount(Gold.class) >= 1);

		for (Bestiary cat : Bestiary.values()) {
			assertEquals(cat.name(), cat.totalEntities(), cat.totalSeen());
		}
		assertTrue(Bestiary.isSeen(Rat.class));
		assertTrue(Bestiary.encounterCount(Rat.class) >= 1);

		for (Document doc : Document.values()) {
			assertTrue(doc.name(), doc.allPagesFound());
			for (String page : doc.pageNames()) {
				assertTrue(doc.name() + " " + page, doc.isPageRead(page));
			}
		}
		assertTrue(Document.HALLS_KING.isPageRead(Document.KING_ATTRITION));
		assertFalse(Journal.fullGrantApplied());
		Bundle bundle = new Bundle();
		Journal.store(bundle);
		assertTrue(bundle.getBoolean(Journal.FULL_GRANT));
	}

	@Test
	public void fullGrantKeepsHigherUseAndEncounterCounts() {
		Journal.grantFullCompletion();
		Catalog.countUses(Gold.class, 4);
		Bestiary.countEncounters(Rat.class, 4);
		int uses = Catalog.useCount(Gold.class);
		int encounters = Bestiary.encounterCount(Rat.class);

		Journal.grantFullCompletion();

		assertEquals(uses, Catalog.useCount(Gold.class));
		assertEquals(encounters, Bestiary.encounterCount(Rat.class));
		assertTrue(uses > 1);
		assertTrue(encounters > 1);
	}

	@Test
	public void restoreMergesDiskProgressWithLaterSightings() {
		Catalog.setSeen(Gold.class);
		Bundle disk = new Bundle();
		Catalog.store(disk);

		Journal.unload();
		Dungeon.debugRun = false;
		Catalog.setSeen(Dewdrop.class);
		Catalog.restore(disk);

		assertTrue(Catalog.isSeen(Gold.class));
		assertTrue(Catalog.isSeen(Dewdrop.class));
		assertFalse(Catalog.isSeen(Ankh.class));
	}
}
