/*
 * Streamer debug: resolve friendly or class item names to Item classes.
 */

package com.shatteredpixel.shatteredpixeldungeon.desktop;

import com.shatteredpixel.shatteredpixeldungeon.items.Generator;
import com.shatteredpixel.shatteredpixeldungeon.items.Gold;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.journal.Catalog;
import com.watabou.utils.Reflection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StreamerItemResolver {

	private static final String ITEM_PKG = "com.shatteredpixel.shatteredpixeldungeon.items.";

	private static final String[] ITEM_PACKAGES = {
			ITEM_PKG,
			ITEM_PKG + "scrolls.",
			ITEM_PKG + "potions.",
			ITEM_PKG + "potions.exotic.",
			ITEM_PKG + "weapon.melee.",
			ITEM_PKG + "weapon.missiles.",
			ITEM_PKG + "weapon.missiles.darts.",
			ITEM_PKG + "armor.",
			ITEM_PKG + "rings.",
			ITEM_PKG + "wands.",
			ITEM_PKG + "artifacts.",
			ITEM_PKG + "food.",
			ITEM_PKG + "spells.",
			ITEM_PKG + "bombs.",
			ITEM_PKG + "stones.",
			ITEM_PKG + "trinkets.",
			ITEM_PKG + "quest.",
			ITEM_PKG + "scrolls.exotic.",
			ITEM_PKG + "keys.",
			ITEM_PKG + "remains.",
			ITEM_PKG + "potions.brews.",
			ITEM_PKG + "potions.elixirs.",
			ITEM_PKG + "bags.",
			ITEM_PKG + "food.",
	};

	private static final Set<Class<? extends Item>> registered = new HashSet<>();

	private static final class Entry {
		final Class<? extends Item> clazz;
		final String displayKey;
		final String classKey;

		Entry(Class<? extends Item> clazz, String displayKey, String classKey) {
			this.clazz = clazz;
			this.displayKey = displayKey;
			this.classKey = classKey;
		}
	}

	private static ArrayList<Entry> registry;
	private static Map<String, List<Class<? extends Item>>> byKey;

	public static String normalizeKey(String input) {
		if (input == null) return "";
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			if (Character.isLetterOrDigit(c)) {
				sb.append(Character.toLowerCase(c));
			}
		}
		return sb.toString();
	}

	private static void ensureRegistry() {
		if (registry != null) return;
		registry = new ArrayList<>();
		byKey = new HashMap<>();

		for (Generator.Category cat : Generator.Category.values()) {
			if (cat.classes == null) continue;
			for (Class<?> raw : cat.classes) {
				registerIfItem(raw);
			}
		}
		for (Catalog cat : Catalog.values()) {
			for (Class<?> raw : cat.items()) {
				registerIfItem(raw);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static void registerIfItem(Class<?> raw) {
		if (raw == null || !isGiveableItemClass(raw)) return;
		if (Reflection.isMemberClass(raw) && !Reflection.isStatic(raw)) return;
		registerClass((Class<? extends Item>) raw);
	}

	private static boolean isGiveableItemClass(Class<?> raw) {
		return Item.class.isAssignableFrom(raw)
				&& !raw.isInterface()
				&& (raw.getModifiers() & java.lang.reflect.Modifier.ABSTRACT) == 0;
	}

	private static void registerClass(Class<? extends Item> clazz) {
		if (!registered.add(clazz)) return;
		String classKey = normalizeKey(clazz.getSimpleName());
		String displayKey = classKey;
		try {
			Item probe = Reflection.newInstance(clazz);
			if (probe != null) {
				displayKey = normalizeKey(probe.trueName());
			}
		} catch (Exception ignored) {
		}
		registry.add(new Entry(clazz, displayKey, classKey));
		addAlias(classKey, clazz);
		if (!displayKey.equals(classKey)) {
			addAlias(displayKey, clazz);
		}
	}

	private static void addAlias(String key, Class<? extends Item> clazz) {
		if (key.isEmpty()) return;
		List<Class<? extends Item>> list = byKey.get(key);
		if (list == null) {
			list = new ArrayList<>();
			byKey.put(key, list);
		}
		if (!list.contains(clazz)) list.add(clazz);
	}

	/** Resolve user input to an item class, or null. */
	@SuppressWarnings("unchecked")
	public static Class<? extends Item> resolveClass(String input) {
		if (input == null || input.trim().isEmpty()) return null;
		String trimmed = input.trim();

		ensureRegistry();

		if (trimmed.contains(".")) {
			Class<?> c = Reflection.forName(trimmed);
			if (c != null && Item.class.isAssignableFrom(c)) {
				return (Class<? extends Item>) c;
			}
		}

		for (String pkg : ITEM_PACKAGES) {
			Class<?> c = Reflection.forName(pkg + trimmed);
			if (c != null && Item.class.isAssignableFrom(c)) {
				return (Class<? extends Item>) c;
			}
		}

		String key = normalizeKey(trimmed);
		List<Class<? extends Item>> matches = byKey.get(key);
		if (matches != null && matches.size() == 1) {
			return matches.get(0);
		}
		if (matches != null && matches.size() > 1) {
			return null; // ambiguous — caller formats error
		}

		// substring match on display / class keys
		Class<? extends Item> partial = null;
		int partialCount = 0;
		for (Entry e : registry) {
			if (e.displayKey.contains(key) || e.classKey.contains(key)
					|| key.contains(e.displayKey) || key.contains(e.classKey)) {
				partial = e.clazz;
				partialCount++;
			}
		}
		if (partialCount == 1) return partial;
		return null;
	}

	public static String ambiguousMessage(String input) {
		ensureRegistry();
		String key = normalizeKey(input);
		List<Class<? extends Item>> matches = byKey.get(key);
		if (matches == null || matches.size() <= 1) return null;
		List<String> labels = suggestLabels(input, 5);
		if (!labels.isEmpty()) {
			return "Ambiguous item. Did you mean: " + String.join(", ", labels);
		}
		StringBuilder sb = new StringBuilder("Ambiguous item (");
		for (int i = 0; i < matches.size() && i < 5; i++) {
			if (i > 0) sb.append(", ");
			Item probe = Reflection.newInstance(matches.get(i));
			sb.append(probe != null ? probe.trueName() : matches.get(i).getSimpleName());
		}
		if (matches.size() > 5) sb.append(", ...");
		sb.append(")");
		return sb.toString();
	}

	public static String unknownMessage(String input) {
		List<String> suggestions = suggestLabels(input, 5);
		if (suggestions.isEmpty()) {
			return "Unknown item: " + input + " (try class name e.g. ScrollOfUpgrade, or: python streamer_debug.py search <word>)";
		}
		return "Unknown item: " + input + ". Did you mean: " + String.join(", ", suggestions);
	}

	/** Search labels for streamer list/search (display name + class). */
	public static List<String> suggestLabels(String input, int maxResults) {
		if (input == null || input.trim().isEmpty() || maxResults <= 0) return Collections.emptyList();
		ensureRegistry();
		String key = normalizeKey(input.trim());

		ArrayList<Scored> scored = new ArrayList<>();
		for (Entry e : registry) {
			int score = scoreMatch(key, e);
			if (score > 0) scored.add(new Scored(score, e));
		}
		Collections.sort(scored, new Comparator<Scored>() {
			@Override
			public int compare(Scored a, Scored b) {
				return Integer.compare(b.score, a.score);
			}
		});

		ArrayList<String> out = new ArrayList<>();
		for (int i = 0; i < scored.size() && out.size() < maxResults; i++) {
			out.add(formatLabel(scored.get(i).entry));
		}
		return out;
	}

	private static final class Scored {
		final int score;
		final Entry entry;

		Scored(int score, Entry entry) {
			this.score = score;
			this.entry = entry;
		}
	}

	private static int scoreMatch(String key, Entry e) {
		if (key.isEmpty()) return 0;
		if (e.displayKey.equals(key) || e.classKey.equals(key)) return 1000;
		if (e.displayKey.contains(key) || key.contains(e.displayKey)) return 120 + Math.min(key.length(), e.displayKey.length());
		if (e.classKey.contains(key) || key.contains(e.classKey)) return 100 + Math.min(key.length(), e.classKey.length());
		int dDisplay = levenshtein(key, e.displayKey);
		if (dDisplay <= 4) return 60 - dDisplay;
		int dClass = levenshtein(key, e.classKey);
		if (dClass <= 4) return 50 - dClass;
		return 0;
	}

	private static String formatLabel(Entry e) {
		Item probe = Reflection.newInstance(e.clazz);
		String display = probe != null ? probe.trueName() : e.clazz.getSimpleName();
		return display + " (" + e.clazz.getSimpleName() + ")";
	}

	private static int levenshtein(String a, String b) {
		if (a.isEmpty()) return b.length();
		if (b.isEmpty()) return a.length();
		int[] prev = new int[b.length() + 1];
		int[] curr = new int[b.length() + 1];
		for (int j = 0; j <= b.length(); j++) prev[j] = j;
		for (int i = 1; i <= a.length(); i++) {
			curr[0] = i;
			for (int j = 1; j <= b.length(); j++) {
				int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
				curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
			}
			int[] swap = prev;
			prev = curr;
			curr = swap;
		}
		return prev[b.length()];
	}
}
