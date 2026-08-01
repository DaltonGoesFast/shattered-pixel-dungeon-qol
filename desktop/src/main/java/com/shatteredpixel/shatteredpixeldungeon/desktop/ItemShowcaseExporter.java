/*
 * Item Showcase: short-lived LWJGL bootstrap that exports item JSON for shorts.
 * Keeps game class loading inside create() so Messages/Gdx.files are available.
 */

package com.shatteredpixel.shatteredpixeldungeon.desktop;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3FileHandle;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Preferences;
import com.badlogic.gdx.utils.Architecture;
import com.badlogic.gdx.utils.Os;
import com.badlogic.gdx.utils.SharedLibraryLoader;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.watabou.utils.FileUtils;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public class ItemShowcaseExporter {

	private static final AtomicInteger EXIT_CODE = new AtomicInteger(1);

	public static void main(String[] args) {
		if (args == null || args.length == 0) {
			System.err.println("Usage: ItemShowcaseExporter <itemId> --out <dir> [--enchant Name] [--glyph Name] [--level N]");
			System.exit(2);
			return;
		}

		if (System.getProperty("os.name").contains("FreeBSD")) {
			SharedLibraryLoader.os = Os.Linux;
			if (System.getProperty("os.arch").contains("64") || System.getProperty("os.arch").startsWith("armv8")) {
				SharedLibraryLoader.bitness = Architecture.Bitness._64;
			}
		}

		String title = "SPD Item Showcase Export";
		String vendor = "shatteredpixel";

		String basePath = "";
		Files.FileType baseFileType = null;
		if (SharedLibraryLoader.os == Os.Windows) {
			if (System.getProperties().getProperty("os.name").equals("Windows XP")) {
				basePath = "Application Data/." + vendor + "/" + title + "/";
			} else {
				basePath = "AppData/Roaming/." + vendor + "/" + title + "/";
			}
			baseFileType = Files.FileType.External;
		} else if (SharedLibraryLoader.os == Os.MacOsX) {
			basePath = "Library/Application Support/" + title + "/";
			baseFileType = Files.FileType.External;
		} else if (SharedLibraryLoader.os == Os.Linux) {
			String XDGHome = System.getenv("XDG_DATA_HOME");
			if (XDGHome == null) XDGHome = System.getProperty("user.home") + "/.local/share";
			String titleLinux = title.toLowerCase(Locale.ROOT).replace(" ", "-");
			basePath = XDGHome + "/." + vendor + "/" + titleLinux + "/";
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

		final String[] exportArgs = args;
		new Lwjgl3Application(new ApplicationAdapter() {
			@Override
			public void create() {
				try {
					ItemShowcaseExportRunner.run(exportArgs);
					EXIT_CODE.set(0);
				} catch (ItemShowcaseExportRunner.ExportException e) {
					System.err.println("[ItemShowcase] " + e.getMessage());
					EXIT_CODE.set(e.exitCode);
				} catch (Throwable t) {
					System.err.println("[ItemShowcase] Unexpected failure:");
					t.printStackTrace(System.err);
					EXIT_CODE.set(1);
				} finally {
					Gdx.app.exit();
				}
			}
		}, config);

		System.exit(EXIT_CODE.get());
	}
}
