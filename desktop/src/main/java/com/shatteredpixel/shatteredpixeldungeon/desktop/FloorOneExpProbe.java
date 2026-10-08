/*
 * Headless-ish floor 1 EXP dump. Boots a hidden LWJGL app so preferences,
 * Messages, and levelgen match a real desktop run, then prints each mob's EXP.
 *
 *   gradlew :desktop:runFloorOneExpProbe -PprobeSeed=JNC-ZKQ-VTN
 */

package com.shatteredpixel.shatteredpixeldungeon.desktop;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3FileHandle;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Preferences;
import com.badlogic.gdx.utils.Architecture;
import com.badlogic.gdx.utils.Os;
import com.badlogic.gdx.utils.SharedLibraryLoader;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.RegularLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.Room;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap;
import com.shatteredpixel.shatteredpixeldungeon.messages.Languages;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite;
import com.watabou.noosa.Game;
import com.watabou.utils.FileUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public class FloorOneExpProbe {

	private static final AtomicInteger EXIT_CODE = new AtomicInteger(1);

	public static void main(String[] args) {
		String seed = (args != null && args.length > 0 && args[0] != null && !args[0].isEmpty())
				? args[0] : "JNC-ZKQ-VTN";

		if (System.getProperty("os.name").contains("FreeBSD")) {
			SharedLibraryLoader.os = Os.Linux;
			if (System.getProperty("os.arch").contains("64") || System.getProperty("os.arch").startsWith("armv8")) {
				SharedLibraryLoader.bitness = Architecture.Bitness._64;
			}
		}

		String title = "SPD Floor One Exp Probe";
		String vendor = "shatteredpixel";
		String basePath = "";
		Files.FileType baseFileType = null;
		if (SharedLibraryLoader.os == Os.Windows) {
			basePath = "AppData/Roaming/." + vendor + "/" + title + "/";
			baseFileType = Files.FileType.External;
		} else if (SharedLibraryLoader.os == Os.MacOsX) {
			basePath = "Library/Application Support/" + title + "/";
			baseFileType = Files.FileType.External;
		} else {
			String xdg = System.getenv("XDG_DATA_HOME");
			if (xdg == null) xdg = System.getProperty("user.home") + "/.local/share";
			String titleLinux = title.toLowerCase(Locale.ROOT).replace(" ", "-");
			basePath = xdg + "/." + vendor + "/" + titleLinux + "/";
			baseFileType = Files.FileType.Absolute;
		}

		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.setTitle(title);
		config.setPreferencesConfig(basePath, baseFileType);
		SPDSettings.set(new Lwjgl3Preferences(new Lwjgl3FileHandle(basePath + SPDSettings.DEFAULT_PREFS_FILE, baseFileType)));
		FileUtils.setDefaultFileProperties(baseFileType, basePath);
		config.setWindowedMode(64, 64);
		config.setInitialVisible(false);
		config.disableAudio(true);

		new Lwjgl3Application(new ApplicationAdapter() {
			@Override
			public void create() {
				try {
					run(seed);
					EXIT_CODE.set(0);
				} catch (Throwable t) {
					System.err.println("[FloorOneExp] failure:");
					t.printStackTrace(System.err);
					EXIT_CODE.set(1);
				} finally {
					Gdx.app.exit();
				}
			}
		}, config);

		System.exit(EXIT_CODE.get());
	}

	static void run(String seedText) throws Exception {
		Messages.setup(Languages.ENGLISH);
		if (Game.version == null) Game.version = "probe";

		SPDSettings.customSeed(seedText);
		SPDSettings.challenges(0);
		SPDSettings.modifiers(0);
		GamesInProgress.selectedClass = HeroClass.WARRIOR;
		Dungeon.daily = false;
		Dungeon.dailyReplay = false;
		Dungeon.initSeed();
		Dungeon.init();

		System.out.println("seedText=" + Dungeon.customSeedText + " seed=" + Dungeon.seed);
		System.out.println("challenges=" + Dungeon.challenges + " modifiers=" + Dungeon.modifiers
				+ " heroClass=" + Dungeon.hero.heroClass
				+ " lvl=" + Dungeon.hero.lvl + " exp=" + Dungeon.hero.exp
				+ " maxExp=" + Dungeon.hero.maxExp());

		Dungeon.level = Dungeon.newLevel();
		Level level = Dungeon.level;

		int chasms = 0;
		for (int i = 0; i < level.length(); i++) {
			if (level.map[i] == Terrain.CHASM) chasms++;
		}
		System.out.println("feeling=" + level.feeling + " size=" + level.width() + "x" + level.height()
				+ " chasmTiles=" + chasms + " mobs=" + level.mobs.size());

		if (level instanceof RegularLevel) {
			StringBuilder rooms = new StringBuilder();
			for (Room r : ((RegularLevel) level).rooms()) {
				if (rooms.length() > 0) rooms.append(", ");
				rooms.append(r.getClass().getSimpleName());
			}
			System.out.println("rooms=" + rooms);
		}

		StringBuilder traps = new StringBuilder();
		for (Trap t : level.traps.valueList()) {
			if (traps.length() > 0) traps.append(", ");
			traps.append(t.getClass().getSimpleName()).append("@").append(t.pos);
		}
		System.out.println("traps=" + traps);

		int sumExp = 0;
		int sumAward = 0;
		int enemyAward = 0;
		int enemyCount = 0;
		ArrayList<Mob> enemies = new ArrayList<>();
		for (Mob mob : level.mobs) {
			int award = Dungeon.hero.lvl <= mob.maxLvl ? mob.EXP : 0;
			boolean enemy = mob.alignment == Char.Alignment.ENEMY;
			sumExp += mob.EXP;
			sumAward += award;
			if (enemy) {
				enemyAward += award;
				enemyCount++;
				enemies.add(mob);
			}
			int terr = level.map[mob.pos];
			System.out.println("mob " + mob.getClass().getSimpleName()
					+ " EXP=" + mob.EXP
					+ " maxLvl=" + mob.maxLvl
					+ " award=" + award
					+ " align=" + mob.alignment
					+ " pos=" + mob.pos
					+ " terrain=" + terr
					+ " pit=" + level.pit[mob.pos]);
		}
		System.out.println("sumEXP=" + sumExp + " sumAward=" + sumAward
				+ " enemies=" + enemyCount + " enemyAward=" + enemyAward);

		// Melee kill: die(hero) -> destroy -> earnExp. Quiet sprites so the floater
		// and death anim do not need a game scene. Loot is unrelated to the XP int.
		CharSprite quiet = new CharSprite() {
			@Override
			public void die() { }
		};
		quiet.visible = false;
		Dungeon.hero.sprite = quiet;
		Field lootChance = Mob.class.getDeclaredField("lootChance");
		lootChance.setAccessible(true);
		for (Mob mob : enemies) {
			mob.sprite = quiet;
			lootChance.setFloat(mob, 0f);
			int before = Dungeon.hero.exp;
			int lvlBefore = Dungeon.hero.lvl;
			try {
				mob.die(Dungeon.hero);
			} catch (Throwable t) {
				System.out.println("die threw after exp " + before + " -> " + Dungeon.hero.exp
						+ " for " + mob.getClass().getSimpleName() + ": " + t);
			}
			System.out.println("killed " + mob.getClass().getSimpleName()
					+ " deltaExp=" + (Dungeon.hero.exp - before)
					+ " lvl " + lvlBefore + "->" + Dungeon.hero.lvl
					+ " now=" + Dungeon.hero.exp + "/" + Dungeon.hero.maxExp());
		}
		System.out.println("afterMelee lvl=" + Dungeon.hero.lvl + " exp=" + Dungeon.hero.exp
				+ "/" + Dungeon.hero.maxExp());

		if ("JNC-ZKQ-VTN".equals(Dungeon.customSeedText)) {
			if (enemyAward != 10 || Dungeon.hero.lvl < 2) {
				throw new IllegalStateException("seed JNC-ZKQ-VTN enemyAward=" + enemyAward
						+ " after melee lvl=" + Dungeon.hero.lvl
						+ " exp=" + Dungeon.hero.exp + "/" + Dungeon.hero.maxExp()
						+ " (expected 10 XP and level 2)");
			}
		}
	}
}
