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

package com.shatteredpixel.shatteredpixeldungeon.playalong;

import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.Artifact;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfUpgrade;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;
import com.watabou.utils.Reflection;

import java.io.File;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Play Along catch-up bonus item text.
 * <p>
 * Comma-separated tokens such as {@code potionofhealingx10} or {@code wandoflightning+1}.
 * Names are lowercase item class names (spaces ignored). {@code xN} sets quantity for
 * stackables, {@code +N} sets upgrade level for weapons, armor, wands, and rings.
 * Unknown names are skipped. Upgrade is capped at {@link #MAX_UPGRADE} and quantity at
 * {@link #MAX_QUANTITY}.
 */
public final class PlayAlongCatchupItems {

	/** Highest upgrade a bonus item token may apply. */
	public static final int MAX_UPGRADE = 5;
	/** Highest stack size a bonus item token may request. */
	public static final int MAX_QUANTITY = 20;

	private static final Pattern UPGRADE = Pattern.compile("\\+(\\d+)");
	private static final Pattern QUANTITY = Pattern.compile("x(\\d+)$");

	private static final String ITEM_ROOT = "com.shatteredpixel.shatteredpixeldungeon.items";

	private static volatile Map<String, Class<? extends Item>> index;

	private PlayAlongCatchupItems() {}

	public static final class Parsed {
		public final String token;
		public final String name;
		public final int quantity;
		public final int upgrade;
		public final boolean skipped;
		public final String reason;
		public final Class<? extends Item> type;

		private Parsed(String token, String name, int quantity, int upgrade,
		               boolean skipped, String reason, Class<? extends Item> type) {
			this.token = token;
			this.name = name;
			this.quantity = quantity;
			this.upgrade = upgrade;
			this.skipped = skipped;
			this.reason = reason;
			this.type = type;
		}

		public int giveQuantity(boolean stackable) {
			if (!stackable) return 1;
			return Math.max(1, quantity);
		}
	}

	public interface Resolver {
		Class<? extends Item> resolve(String name);

		boolean artifact(Class<? extends Item> type);

		boolean stackable(Class<? extends Item> type);

		boolean upgradable(Class<? extends Item> type);
	}

	public static List<Parsed> parse(String text, boolean allowArtifacts) {
		return parse(text, allowArtifacts, gameResolver());
	}

	public static List<Parsed> parse(String text, boolean allowArtifacts, Resolver resolver) {
		List<Parsed> out = new ArrayList<>();
		if (text == null || text.trim().isEmpty() || resolver == null) return out;
		String[] parts = text.split(",");
		for (String part : parts) {
			if (part == null) continue;
			String token = part.trim();
			if (token.isEmpty()) continue;
			out.add(parseToken(token, allowArtifacts, resolver));
		}
		return out;
	}

	public static Parsed parseToken(String token, boolean allowArtifacts, Resolver resolver) {
		String compact = token.toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "").replace("-", "");
		int upgrade = 0;
		int quantity = 1;
		Matcher up = UPGRADE.matcher(compact);
		if (up.find()) {
			upgrade = safeInt(up.group(1));
			compact = compact.substring(0, up.start()) + compact.substring(up.end());
		}
		Matcher qty = QUANTITY.matcher(compact);
		if (qty.find()) {
			quantity = safeInt(qty.group(1));
			compact = compact.substring(0, qty.start());
		}
		boolean upgradeCapped = upgrade > MAX_UPGRADE;
		boolean quantityCapped = quantity > MAX_QUANTITY;
		if (upgrade < 0) upgrade = 0;
		if (upgrade > MAX_UPGRADE) upgrade = MAX_UPGRADE;
		if (quantity < 1) quantity = 1;
		if (quantity > MAX_QUANTITY) quantity = MAX_QUANTITY;

		if (compact.isEmpty()) {
			return skipped(token, compact, quantity, upgrade, "empty name");
		}
		Class<? extends Item> type = resolver.resolve(compact);
		if (type == null) {
			logSkip(token, "unknown item");
			return skipped(token, compact, quantity, upgrade, "unknown item");
		}
		if (!allowArtifacts && resolver.artifact(type)) {
			logSkip(token, "artifacts are disabled");
			return skipped(token, compact, quantity, upgrade, "artifacts are disabled");
		}
		if (!resolver.upgradable(type)) {
			upgrade = 0;
		}
		String reason = null;
		if (upgradeCapped || quantityCapped) {
			reason = "capped";
		}
		return new Parsed(token, compact, quantity, upgrade, false, reason, type);
	}

	/** Lowercase class names the running game can resolve, including aliases. */
	public static List<String> knownNames() {
		ensureIndex();
		ArrayList<String> names = new ArrayList<>(index.keySet());
		Collections.sort(names);
		return names;
	}

	public static Resolver gameResolver() {
		return GAME;
	}

	public static Class<? extends Item> resolveGame(String name) {
		if (name == null) return null;
		ensureIndex();
		return index.get(name);
	}

	private static Parsed skipped(String token, String name, int quantity, int upgrade, String reason) {
		return new Parsed(token, name, quantity, upgrade, true, reason, null);
	}

	private static void logSkip(String token, String reason) {
		System.out.println("[PlayAlongCatchup] skipped bonus item '" + token + "': " + reason);
	}

	private static int safeInt(String raw) {
		try {
			return Integer.parseInt(raw);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static final Resolver GAME = new Resolver() {
		@Override
		public Class<? extends Item> resolve(String name) {
			return resolveGame(name);
		}

		@Override
		public boolean artifact(Class<? extends Item> type) {
			return type != null && Artifact.class.isAssignableFrom(type);
		}

		@Override
		public boolean stackable(Class<? extends Item> type) {
			if (type == null) return false;
			try {
				Item item = Reflection.newInstance(type);
				return item != null && item.stackable;
			} catch (Throwable t) {
				return false;
			}
		}

		@Override
		public boolean upgradable(Class<? extends Item> type) {
			if (type == null) return false;
			return Weapon.class.isAssignableFrom(type)
					|| Armor.class.isAssignableFrom(type)
					|| Wand.class.isAssignableFrom(type)
					|| Ring.class.isAssignableFrom(type);
		}
	};

	private static void ensureIndex() {
		if (index != null) return;
		synchronized (PlayAlongCatchupItems.class) {
			if (index != null) return;
			Map<String, Class<? extends Item>> built = new LinkedHashMap<>();
			try {
				String path = ITEM_ROOT.replace('.', '/');
				ClassLoader loader = Item.class.getClassLoader();
				Enumeration<URL> urls = loader.getResources(path);
				while (urls.hasMoreElements()) {
					URL url = urls.nextElement();
					if ("file".equals(url.getProtocol())) {
						walkDir(new File(url.toURI()), ITEM_ROOT, loader, built);
					} else if ("jar".equals(url.getProtocol())) {
						walkJar(url, path, loader, built);
					}
				}
			} catch (Throwable t) {
				System.out.println("[PlayAlongCatchup] item index scan failed: " + t);
			}
			built.put("sou", ScrollOfUpgrade.class);
			built.put("scrollofupgrade", ScrollOfUpgrade.class);
			built.put("scrollsofupgrade", ScrollOfUpgrade.class);
			index = built;
		}
	}

	private static void walkDir(File dir, String pkg, ClassLoader loader, Map<String, Class<? extends Item>> built) {
		File[] files = dir.listFiles();
		if (files == null) return;
		for (File file : files) {
			if (file.isDirectory()) {
				walkDir(file, pkg + "." + file.getName(), loader, built);
			} else if (file.getName().endsWith(".class")) {
				String simple = file.getName().substring(0, file.getName().length() - 6);
				consider(pkg + "." + simple, loader, built);
			}
		}
	}

	private static void walkJar(URL url, String path, ClassLoader loader, Map<String, Class<? extends Item>> built) {
		JarFile owned = null;
		try {
			JarFile jar;
			Object connection = url.openConnection();
			if (connection instanceof JarURLConnection) {
				jar = ((JarURLConnection) connection).getJarFile();
			} else {
				String spec = URLDecoder.decode(url.getPath(), "UTF-8");
				int bang = spec.indexOf('!');
				if (bang >= 0) spec = spec.substring(0, bang);
				if (spec.startsWith("file:")) spec = spec.substring(5);
				owned = new JarFile(spec);
				jar = owned;
			}
			Enumeration<JarEntry> entries = jar.entries();
			String prefix = path.endsWith("/") ? path : path + "/";
			while (entries.hasMoreElements()) {
				JarEntry entry = entries.nextElement();
				String name = entry.getName();
				if (!name.startsWith(prefix) || !name.endsWith(".class")) continue;
				consider(name.substring(0, name.length() - 6).replace('/', '.'), loader, built);
			}
		} catch (Throwable t) {
			System.out.println("[PlayAlongCatchup] jar item scan failed: " + t);
		} finally {
			if (owned != null) {
				try {
					owned.close();
				} catch (Throwable ignored) {
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static void consider(String binary, ClassLoader loader, Map<String, Class<? extends Item>> built) {
		if (binary.indexOf('$') >= 0 && !binary.endsWith("Dart")) {
			// Static nested item classes are still loaded below; anonymous classes are not items.
		}
		try {
			Class<?> raw = Class.forName(binary, false, loader);
			if (!Item.class.isAssignableFrom(raw) || raw.isInterface()) return;
			if ((raw.getModifiers() & java.lang.reflect.Modifier.ABSTRACT) != 0) return;
			if (raw.getEnclosingClass() != null && !java.lang.reflect.Modifier.isStatic(raw.getModifiers())) return;
			String key = raw.getSimpleName().toLowerCase(Locale.ROOT);
			if (!built.containsKey(key)) {
				built.put(key, (Class<? extends Item>) raw);
			}
		} catch (Throwable ignored) {
		}
	}
}
