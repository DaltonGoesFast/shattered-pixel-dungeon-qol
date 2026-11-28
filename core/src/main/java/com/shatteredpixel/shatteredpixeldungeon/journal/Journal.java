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

import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.watabou.utils.Bundle;
import com.watabou.utils.FileUtils;

import java.io.IOException;

public class Journal {
	
	public static final String JOURNAL_FILE = "journal.dat";
	
	private static boolean loaded = false;
	
	public static void loadGlobal(){
		if (loaded){
			return;
		}

		// A missing file is a new profile. A file that exists but cannot be read must
		// stay unloaded so saveGlobal cannot replace it with whatever happens to be in memory.
		if (!FileUtils.fileExists(JOURNAL_FILE)) {
			fullGrantApplied = false;
			loaded = true;
			return;
		}

		try {
			Bundle bundle = FileUtils.bundleFromFile( JOURNAL_FILE );
			Catalog.restore( bundle );
			Bestiary.restore( bundle );
			Document.restore( bundle );
			fullGrantApplied = bundle.getBoolean( FULL_GRANT );
			loaded = true;
		} catch (IOException e){
			ShatteredPixelDungeon.reportException(e);
		}
	}

	//clears all loaded data
	public static void unload(){
		Catalog.unload();
		Bestiary.unload();
		Document.unload();
		fullGrantApplied = false;
		loaded = false;
		saveNeeded = false;
	}
	
	//package-private
	static boolean saveNeeded = false;

	/** Written into journal.dat once an existing profile has been filled in. */
	private static final String FULL_GRANT = "journal_full_grant";
	private static boolean fullGrantApplied = false;

	public static boolean fullGrantApplied(){
		return fullGrantApplied;
	}

	/**
	 * Existing profiles recorded a previous version. Fresh installs (version 0) must not
	 * be filled, and a grant that already ran must not run again.
	 */
	public static boolean shouldGrantFullJournal(int previousVersion, boolean alreadyGranted){
		return previousVersion > 0 && !alreadyGranted;
	}

	/** Mark every catalog, bestiary, and document entry complete. Keeps higher counts. */
	public static void grantFullCompletion(){
		Catalog.grantAll();
		Bestiary.grantAll();
		Document.grantAll();
		fullGrantApplied = true;
		saveNeeded = true;
	}

	public static void saveGlobal(){
		saveGlobal(false);
	}

	public static boolean saveGlobal(boolean force){
		// Play Along joins (and any other path that skips the title journal screens) used to
		// mutate this empty memory and then overwrite journal.dat. Load disk first.
		if (!loaded){
			loadGlobal();
		}
		if (!loaded){
			return false;
		}
		if (!force && !saveNeeded){
			return true;
		}
		
		Bundle bundle = new Bundle();
		
		Catalog.store(bundle);
		Bestiary.store(bundle);
		Document.store(bundle);
		bundle.put( FULL_GRANT, fullGrantApplied );
		
		try {
			FileUtils.bundleToFile( JOURNAL_FILE, bundle );
			saveNeeded = false;
			return true;
		} catch (IOException e) {
			ShatteredPixelDungeon.reportException(e);
			return false;
		}
		
	}

}
