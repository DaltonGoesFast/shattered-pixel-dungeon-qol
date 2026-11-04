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

package com.shatteredpixel.shatteredpixeldungeon.services.updates;

import com.watabou.utils.DeviceCompat;

import java.util.Locale;

/**
 * Picks the GitHub release asset for this device.
 * <p>
 * Canonical names, where {@code <ver>} is {@link QoLReleaseVersion#canonical()}
 * (for example {@code 4.0.2-QoL-8}):
 * <ul>
 *     <li>Android: {@code ANDROID-Shattered-Pixel-Dungeon-QoL-<ver>.apk}</li>
 *     <li>Windows: {@code WINDOWS-Shattered-Pixel-Dungeon-QoL-<ver>-x64.zip}</li>
 *     <li>Other desktop: {@code JAVA-Shattered-Pixel-Dungeon-QoL-<ver>.jar}</li>
 * </ul>
 * If that exact file is missing, a same-platform asset is used (any {@code .apk}
 * on Android, a zip whose name contains {@code windows} or else a {@code .jar}
 * on Windows, any {@code .jar} on other desktops). No match means the release page.
 */
public final class QoLUpdateAssets {

	public enum Platform {
		ANDROID, WINDOWS, DESKTOP, OTHER
	}

	public static final class Asset {
		public final String name;
		public final String url;

		public Asset(String name, String url) {
			this.name = name;
			this.url = url;
		}
	}

	private QoLUpdateAssets() {}

	public static Platform current() {
		if (DeviceCompat.isAndroid()) return Platform.ANDROID;
		if (DeviceCompat.isDesktop()) {
			String os = System.getProperty("os.name", "");
			if (os.toLowerCase(Locale.ROOT).contains("win")) return Platform.WINDOWS;
			return Platform.DESKTOP;
		}
		return Platform.OTHER;
	}

	public static String canonicalName(Platform platform, String versionLabel) {
		if (platform == null || versionLabel == null || versionLabel.isEmpty()) return null;
		switch (platform) {
			case ANDROID:
				return "ANDROID-Shattered-Pixel-Dungeon-QoL-" + versionLabel + ".apk";
			case WINDOWS:
				return "WINDOWS-Shattered-Pixel-Dungeon-QoL-" + versionLabel + "-x64.zip";
			case DESKTOP:
				return "JAVA-Shattered-Pixel-Dungeon-QoL-" + versionLabel + ".jar";
			default:
				return null;
		}
	}

	/** @return the asset download URL, or null when the release page should be opened */
	public static String pickDownloadUrl(Platform platform, String versionLabel, Asset[] assets) {
		if (platform == null || platform == Platform.OTHER || assets == null) return null;
		String canonical = canonicalName(platform, versionLabel);
		String bestUrl = null;
		int bestScore = 0;
		for (Asset asset : assets) {
			if (asset == null || asset.name == null || asset.url == null || asset.url.isEmpty()) continue;
			int score = score(platform, canonical, asset.name);
			if (score > bestScore) {
				bestScore = score;
				bestUrl = asset.url;
			}
		}
		return bestUrl;
	}

	static int score(Platform platform, String canonical, String name) {
		if (name == null) return 0;
		if (canonical != null && name.equalsIgnoreCase(canonical)) return 100;
		String lower = name.toLowerCase(Locale.ROOT);
		switch (platform) {
			case ANDROID:
				if (!lower.endsWith(".apk")) return 0;
				return lower.contains("android") ? 80 : 40;
			case WINDOWS:
				if (lower.endsWith(".zip") && lower.contains("windows")) return 80;
				if (lower.endsWith(".jar")) return 50;
				return 0;
			case DESKTOP:
				if (!lower.endsWith(".jar")) return 0;
				return lower.contains("java") ? 80 : 40;
			default:
				return 0;
		}
	}
}
