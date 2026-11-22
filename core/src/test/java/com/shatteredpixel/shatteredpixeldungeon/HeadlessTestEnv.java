package com.shatteredpixel.shatteredpixeldungeon;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.shatteredpixel.shatteredpixeldungeon.messages.Languages;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.watabou.noosa.Game;
import com.watabou.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Minimal headless libGDX file/native bootstrap for core unit tests.
 */
public final class HeadlessTestEnv {

	private static boolean initialized = false;
	private static Path tempStorageDir;
	private static File assetsDir;

	private HeadlessTestEnv() {}

	public static synchronized void init() {
		Game.version = "4.0.2-QoL-18";
		if (initialized) {
			return;
		}
		GdxNativesLoader.load();

		File direct = new File("src/main/assets");
		File fromRoot = new File("core/src/main/assets");
		assetsDir = direct.isDirectory() ? direct : fromRoot;

		try {
			tempStorageDir = java.nio.file.Files.createTempDirectory("spd-headless-test");
			tempStorageDir.toFile().deleteOnExit();
		} catch (IOException e) {
			throw new RuntimeException("Failed to create temp test directory", e);
		}

		Gdx.files = new Files() {
			@Override
			public FileHandle getFileHandle(String path, FileType type) {
				switch (type) {
					case Internal: return internal(path);
					case External: return external(path);
					case Absolute: return absolute(path);
					case Local: return local(path);
					case Classpath: return classpath(path);
					default: return local(path);
				}
			}

			@Override
			public FileHandle classpath(String path) {
				return new FileHandle(new File(path), FileType.Classpath) {};
			}

			@Override
			public FileHandle internal(String path) {
				return new FileHandle(new File(assetsDir, path), FileType.Internal) {};
			}

			@Override
			public FileHandle external(String path) {
				return new FileHandle(tempStorageDir.resolve(path).toFile(), FileType.External) {};
			}

			@Override
			public FileHandle absolute(String path) {
				return new FileHandle(new File(path), FileType.Absolute) {};
			}

			@Override
			public FileHandle local(String path) {
				return new FileHandle(tempStorageDir.resolve(path).toFile(), FileType.Local) {};
			}

			@Override
			public String getExternalStoragePath() {
				return tempStorageDir.toAbsolutePath().toString() + File.separator;
			}

			@Override
			public boolean isExternalStorageAvailable() {
				return true;
			}

			@Override
			public String getLocalStoragePath() {
				return tempStorageDir.toAbsolutePath().toString() + File.separator;
			}

			@Override
			public boolean isLocalStorageAvailable() {
				return true;
			}
		};

		FileUtils.setDefaultFileProperties(Files.FileType.Local, "");
		Messages.setup(Languages.ENGLISH);
		initialized = true;
	}

	public static Path getTempStorageDir() {
		return tempStorageDir;
	}
}
