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
 */

package com.shatteredpixel.shatteredpixeldungeon.android;

import android.content.pm.ApplicationInfo;

import com.watabou.noosa.Game;
import com.zrp200.scrollofdebug.DexClassNames;
import com.zrp200.scrollofdebug.PackageTrie;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Lists game classes from the installed APK so Scroll of Debug can resolve short names. */
public final class AndroidDebugClasses {

	private AndroidDebugClasses() {}

	public static PackageTrie index(String packageName) {
		PackageTrie trie = new PackageTrie(packageName);
		if (AndroidLauncher.instance == null) return trie;

		ApplicationInfo info = AndroidLauncher.instance.getApplicationInfo();
		Set<String> names = new HashSet<>();
		readApk(info.sourceDir, packageName, names);
		if (info.splitSourceDirs != null) {
			for (String split : info.splitSourceDirs) {
				readApk(split, packageName, names);
			}
		}

		ClassLoader loader = AndroidDebugClasses.class.getClassLoader();
		for (String name : names) {
			if (isAnonymous(name)) continue;
			try {
				trie.indexClass(Class.forName(name, false, loader), packageName);
			} catch (Throwable ignored) {
			}
		}
		return trie;
	}

	private static void readApk(String path, String packageName, Set<String> names) {
		if (path == null) return;
		ZipFile zip = null;
		try {
			zip = new ZipFile(path);
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				String entryName = entry.getName();
				if (!entryName.matches("classes\\d*\\.dex")) continue;
				DexClassNames.collect(readFully(zip.getInputStream(entry)), packageName, names);
			}
		} catch (IOException e) {
			Game.reportException(e);
		} finally {
			if (zip != null) try {
				zip.close();
			} catch (IOException ignored) {
			}
		}
	}

	private static boolean isAnonymous(String className) {
		int dollar = className.lastIndexOf('$');
		if (dollar < 0 || dollar == className.length() - 1) return false;
		for (int i = dollar + 1; i < className.length(); i++) {
			if (!Character.isDigit(className.charAt(i))) return false;
		}
		return true;
	}

	private static byte[] readFully(InputStream in) throws IOException {
		try (InputStream stream = in) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[8192];
			int n;
			while ((n = stream.read(buf)) >= 0) {
				out.write(buf, 0, n);
			}
			return out.toByteArray();
		}
	}
}
