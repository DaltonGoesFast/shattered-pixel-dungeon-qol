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

package com.shatteredpixel.shatteredpixeldungeon.ui.changelist;

import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.ChangesScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite;
import com.shatteredpixel.shatteredpixeldungeon.ui.Icons;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;

import java.util.ArrayList;

public class Mod_Changes {

	public static void addAllChanges( ArrayList<ChangeInfo> changeInfos ){
		addOverview(changeInfos);
		add_Chimera_T1_Draft(changeInfos);
		add_v4_0_2_QoL_10(changeInfos);
		add_v4_0_2_QoL_8(changeInfos);
		add_v4_0_0_QoL_7(changeInfos);
		add_v4_0_0_QoL_6(changeInfos);
		add_v4_0_0_QoL_5(changeInfos);
		add_v4_0_0(changeInfos);
	}

	private static void add_Chimera_T1_Draft( ArrayList<ChangeInfo> changeInfos ) {
		ChangeInfo changes = new ChangeInfo("Chimera Draft", true, "");
		changes.hardlight(Window.TITLE_COLOR);
		changeInfos.add(changes);

		changes = new ChangeInfo(Messages.get(ChangesScene.class, "new"), false, null);
		changes.hardlight(Window.TITLE_COLOR);
		changeInfos.add(changes);

		changes.addButton( new ChangeButton(Icons.get(Icons.TALENT), "Chimera opening draft",
				"**-** Chimera no longer hard-rolls your four opening talents\n" +
				"**-** On first load, draft each T1 slot from other-class offers (no duplicates across slots)\n" +
				"**-** Tier 2–3 talents, subclass pairs, and armor ability pairs stay random surprises\n" +
				"**-** Confirm to begin; back out via the menu if the board is unusable"));
	}

	private static void addOverview( ArrayList<ChangeInfo> changeInfos ) {
		ChangeInfo changes = new ChangeInfo("SPD-QoL", true, "");
		changes.hardlight(Window.TITLE_COLOR);
		changeInfos.add(changes);

		changes = new ChangeInfo(Messages.get(ChangesScene.class, "mod"), false, null);
		changes.hardlight(Window.TITLE_COLOR);
		changeInfos.add(changes);

		changes.addButton( new ChangeButton(Icons.getSettings(Icons.SETTINGS_QOL), "What this mod adds",
				"This is a community QoL build of Shattered Pixel Dungeon. Official Shattered notes stay on the **v4.X** tab.\n" +
				"\n" +
				"**-** **Pacts** — optional run rules on hero select after your first win. They are not challenges and do not change challenge score or Champion badges.\n" +
				"**-** **Quality of Life** — Settings → Quality of Life for quickslots, buff numbers, target HP bar, auto-talents, and more.\n" +
				"**-** **Updates** — the title Update button can install a newer APK on Android, or open the Windows download in your browser."));

		changes.addButton( new ChangeButton(Icons.get(Icons.PACTS), "Pacts at a glance",
				"Pacts unlock after one win. Mix them freely, or roll a random set. Command, Rebirth, and Legacy stay off on daily and custom-seed runs.\n" +
				"\n" +
				"**Hero:** Death, Frailty, Glass, Devotion, Crimson, Grave, Chimera\n" +
				"**Foes:** Honor, Evolution, Dissonance, Kin, Swarms, Spite, Soul\n" +
				"**Spoils:** Command, Enigma, Metamorphosis, Sacrifice, Rebirth, Legacy, Guild"));
	}

	private static void add_v4_0_2_QoL_10( ArrayList<ChangeInfo> changeInfos ) {
		ChangeInfo changes = new ChangeInfo("4.0.2-QoL-10", true, "");
		changes.hardlight(Window.TITLE_COLOR);
		changeInfos.add(changes);

		changes = new ChangeInfo(Messages.get(ChangesScene.class, "new"), false, null);
		changes.hardlight(Window.TITLE_COLOR);
		changeInfos.add(changes);

		changes.addButton( new ChangeButton(Icons.get(Icons.CHANGES), "In-game updates",
				"**-** Title **Changes** becomes flashing **Update** when a newer GitHub release exists\n" +
				"**-** On Android, **Install** downloads the APK and opens the system installer\n" +
				"**-** On Windows, **Download update** opens the release zip or jar in your browser\n" +
				"**-** Settings → Data → **Check now** forces a recheck, including on mobile data"));

		changes.addButton( new ChangeButton(Icons.getSettings(Icons.SETTINGS_QOL), "Mod Changes tab",
				"**-** Change History has a **QoL** tab for these notes\n" +
				"**-** Title **Mod Changes** opens that tab instead of GitHub\n" +
				"**-** Official Shattered patch notes stay under **v4.X** and older tabs"));

		changes = new ChangeInfo(Messages.get(ChangesScene.class, "changes"), false, null);
		changes.hardlight(CharSprite.WARNING);
		changeInfos.add(changes);

		changes.addButton( new ChangeButton(Icons.get(Icons.PACTS), "Pact and loop tweaks",
				"**-** **Evolution** keeps growing after a Legacy loop, starting from the 50% cap\n" +
				"**-** **Chimera** hides unreached class choices and keeps each offer to one class\n" +
				"**-** A looped Halls sprint win can pay donor points"));

		changes.addButton( new ChangeButton(ChangeIcons.V081_MISC, Messages.get(ChangesScene.class, "misc"),
				"**-** Assigning an item to a quickslot works with tap or long-press inside the scrolling strip\n" +
				"**-** Stream telemetry and post-production tooling for the live build"));
	}

	private static void add_v4_0_2_QoL_8( ArrayList<ChangeInfo> changeInfos ) {
		ChangeInfo changes = new ChangeInfo("4.0.2-QoL-8", true, "");
		changes.hardlight(Window.TITLE_COLOR);
		changeInfos.add(changes);

		changes = new ChangeInfo(Messages.get(ChangesScene.class, "changes"), false, null);
		changes.hardlight(CharSprite.WARNING);
		changeInfos.add(changes);

		changes.addButton( new ChangeButton(Icons.get(Icons.PACTS), "Pact tweaks",
				"**-** **Grave** — shades swing your carried weapon at that weapon's reach and speed. Their hits identify it. A heavy weapon is slower and less accurate in their hands. A melee click at that reach calls a ready shade, the same as a wand zap or a shot.\n" +
				"**-** **Guild** — the prison shop stocks fewer low-tier weapons and more mid- and high-tier ones. Other shops keep a normal mix of unidentified potions and scrolls.\n" +
				"**-** **Sacrifice** — the trinket catalyst stays in its locked chest.\n" +
				"**-** **Enigma** — the Scroll of Debug stays in the bag.\n" +
				"**-** **Crimson** — a thrown potion of healing that hits a living enemy logs that it sears and poisons them."));

		changes.addButton( new ChangeButton(ChangeIcons.V081_MISC, Messages.get(ChangesScene.class, "misc"),
				"**-** Pact runs have their own rankings board\n" +
				"**-** Continue screen has a second page of save slots\n" +
				"**-** **Show second quickslot bar** in Settings → Quality of Life\n" +
				"**-** On phones, a quickslot row that does not fit scrolls with a drag\n" +
				"**-** Pact window title, tabs, and hero-select show how many pacts are on\n" +
				"**-** Settings **Debug** tab; Scroll of Debug only appears in a debug run"));

		changes = new ChangeInfo(Messages.get(ChangesScene.class, "bugfixes"), false, null);
		changes.hardlight(CharSprite.NEGATIVE);
		changeInfos.add(changes);

		changes.addButton( new ChangeButton(ChangeIcons.V061_BUGFIX, Messages.get(ChangesScene.class, "bugfixes"),
				"**-** Floor 1 no longer drops a spawn's experience when the entrance room is rejected"));
	}

	private static void add_v4_0_0_QoL_7( ArrayList<ChangeInfo> changeInfos ) {
		ChangeInfo changes = new ChangeInfo("4.0.0-QoL-7", true, "");
		changes.hardlight(Window.TITLE_COLOR);
		changeInfos.add(changes);

		changes = new ChangeInfo(Messages.get(ChangesScene.class, "new"), false, null);
		changes.hardlight(Window.TITLE_COLOR);
		changeInfos.add(changes);

		changes.addButton( new ChangeButton(Icons.get(Icons.PACTS), "New and expanded pacts",
				"**-** **Crimson** — a strike that draws blood restores you. Ordinary healing does not. A thrown potion of healing bursts on an enemy, damages them, and poisons them.\n" +
				"**-** **Grave** — your hands do not strike. Shades swing the weapon you carry. Up to four can be out. A chalice can call the next one early.\n" +
				"**-** **Guild** — each shop keeps one trade and pays more when you sell that trade back.\n" +
				"**-** **Chimera** — class talent slots are replaced with talents from other classes. Subclass and armor-ability picks can grant that class's signature item."));

		changes = new ChangeInfo(Messages.get(ChangesScene.class, "changes"), false, null);
		changes.hardlight(CharSprite.WARNING);
		changeInfos.add(changes);

		changes.addButton( new ChangeButton(ChangeIcons.V081_MISC, Messages.get(ChangesScene.class, "misc"),
				"**-** **Show turn counts on small buff icons** — on by default\n" +
				"**-** **Modded banners and death sound** — Settings → Advanced, off by default\n" +
				"**-** Flip status pane keeps portrait, bars, and level readable on desktop\n" +
				"**-** Title **DaltonGoesFast** opens the YouTube channel"));
	}

	private static void add_v4_0_0_QoL_6( ArrayList<ChangeInfo> changeInfos ) {
		ChangeInfo changes = new ChangeInfo("4.0.0-QoL-6", true, "");
		changes.hardlight(Window.TITLE_COLOR);
		changeInfos.add(changes);

		changes = new ChangeInfo(Messages.get(ChangesScene.class, "changes"), false, null);
		changes.hardlight(CharSprite.WARNING);
		changeInfos.add(changes);

		changes.addButton( new ChangeButton(Icons.get(Icons.PACTS), "Pact edge cases",
				"**-** **Command + On Diet** — food stays out of Command\n" +
				"**-** **Command + Forbidden Runes** — upgrade scrolls still cap at 8\n" +
				"**-** **Honor** — Gnoll Exile shows its champion affix while still passive\n" +
				"**-** **Sacrifice + Cracked Spyglass** — spyglass items stay hidden on the floor\n" +
				"**-** **Command** now covers statue weapons, armored-statue armor, hermit crab shells, gnoll exile loot, and similar rare prizes\n" +
				"**-** **Metamorphosis** — remade floor-1 armor starts in the bag\n" +
				"**-** **Rebirth** — the next run logs the gift after Metamorphosis"));

		changes.addButton( new ChangeButton(ChangeIcons.V081_MISC, Messages.get(ChangesScene.class, "misc"),
				"**-** **Quickslots shown** — Auto, or a fixed count from 1 to 6\n" +
				"**-** **Debug mode** — Settings → Debug. Debug runs earn no badges, rankings, or daily scores"));
	}

	private static void add_v4_0_0_QoL_5( ArrayList<ChangeInfo> changeInfos ) {
		ChangeInfo changes = new ChangeInfo("4.0.0-QoL-5", true, "");
		changes.hardlight(Window.TITLE_COLOR);
		changeInfos.add(changes);

		changes = new ChangeInfo(Messages.get(ChangesScene.class, "changes"), false, null);
		changes.hardlight(CharSprite.WARNING);
		changeInfos.add(changes);

		changes.addButton( new ChangeButton(Icons.get(Icons.PACTS), "Pact refinements",
				"**-** **Command** — choose before the item leaves the ground or shop. Confirm spends the turn or gold; Cancel leaves it.\n" +
				"**-** **Spite** — hostile deaths leave lit bombs around the corpse. Stronger foes leave more.\n" +
				"**-** **Soul** — a hostile death releases a wraith that closes in before it strikes.\n" +
				"**-** **Sacrifice** — loose treasure on regular floors is carried by monsters. Locked, secret, and puzzle rewards stay put."));
	}

	private static void add_v4_0_0( ArrayList<ChangeInfo> changeInfos ) {
		ChangeInfo changes = new ChangeInfo("4.0.0", true, "");
		changes.hardlight(Window.TITLE_COLOR);
		changeInfos.add(changes);

		changes = new ChangeInfo(Messages.get(ChangesScene.class, "new"), false, null);
		changes.hardlight(Window.TITLE_COLOR);
		changeInfos.add(changes);

		changes.addButton( new ChangeButton(Icons.get(Icons.PACTS), "Pacts",
				"Optional run rules, separate from challenges. Unlock after one win. Pick them on hero select.\n" +
				"\n" +
				"**-** Honor, Glass, Frailty, Death, Devotion\n" +
				"**-** Spite, Soul, Kin, Dissonance, Evolution, Swarms\n" +
				"**-** Metamorphosis, Sacrifice, Command, Rebirth, Legacy, Enigma\n" +
				"\n" +
				"Command, Rebirth, and Legacy are unavailable on daily and custom-seed runs."));

		changes.addButton( new ChangeButton(Icons.getSettings(Icons.SETTINGS_QOL), "Quality of Life",
				"**-** Optional hero and equipment rename\n" +
				"**-** Augment icons on equipped weapons and armor, and on thrown weapons in the quickslot\n" +
				"**-** Settings split into Quality of Life and Advanced tabs\n" +
				"**-** Alternate tilesets deprecated and default to Never\n" +
				"**-** Scroll of Stasis and Scroll of Time Freeze removed"));
	}
}
