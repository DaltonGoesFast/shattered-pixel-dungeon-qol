/*
 * Streamer debug: resolve buff/debuff names for apply and search.
 */

package com.shatteredpixel.shatteredpixeldungeon.desktop;

import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Adrenaline;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Barrier;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Blindness;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Cripple;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Daze;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Degrade;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Haste;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Healing;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Hex;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Invisibility;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Levitation;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.MindVision;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Recharging;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Roots;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Slow;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Vulnerable;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Weakness;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StreamerBuffResolver {

	public static final Class<? extends Buff>[] STREAMER_BUFFS = new Class[]{
			Haste.class, Adrenaline.class, Invisibility.class, Levitation.class,
			Barrier.class, Healing.class, Recharging.class, MindVision.class
	};

	public static final Class<? extends Buff>[] STREAMER_DEBUFFS = new Class[]{
			Blindness.class, Weakness.class, Slow.class, Cripple.class,
			Roots.class, Daze.class, Vulnerable.class, Hex.class, Degrade.class
	};

	private static final class Entry {
		final Class<? extends Buff> clazz;
		final String classKey;
		final boolean debuff;

		Entry(Class<? extends Buff> clazz, boolean debuff) {
			this.clazz = clazz;
			this.classKey = StreamerItemResolver.normalizeKey(clazz.getSimpleName());
			this.debuff = debuff;
		}
	}

	private static ArrayList<Entry> registry;
	private static Map<String, List<Class<? extends Buff>>> byKey;

	private static void ensureRegistry() {
		if (registry != null) return;
		registry = new ArrayList<>();
		byKey = new HashMap<>();
		for (Class<? extends Buff> c : STREAMER_BUFFS) register(c, false);
		for (Class<? extends Buff> c : STREAMER_DEBUFFS) register(c, true);
	}

	private static void register(Class<? extends Buff> clazz, boolean debuff) {
		registry.add(new Entry(clazz, debuff));
		String key = StreamerItemResolver.normalizeKey(clazz.getSimpleName());
		addAlias(key, clazz);
	}

	private static void addAlias(String key, Class<? extends Buff> clazz) {
		if (key.isEmpty()) return;
		List<Class<? extends Buff>> list = byKey.get(key);
		if (list == null) {
			list = new ArrayList<>();
			byKey.put(key, list);
		}
		if (!list.contains(clazz)) list.add(clazz);
	}

	@SuppressWarnings("unchecked")
	public static Class<? extends Buff> resolveBuff(String input) {
		return resolve(input, false);
	}

	@SuppressWarnings("unchecked")
	public static Class<? extends Buff> resolveDebuff(String input) {
		return resolve(input, true);
	}

	@SuppressWarnings("unchecked")
	private static Class<? extends Buff> resolve(String input, boolean debuffOnly) {
		if (input == null || input.trim().isEmpty()) return null;
		ensureRegistry();
		String trimmed = input.trim();
		String key = StreamerItemResolver.normalizeKey(trimmed);

		List<Class<? extends Buff>> exact = byKey.get(key);
		if (exact != null) {
			for (Class<? extends Buff> c : exact) {
				if (matchesType(c, debuffOnly)) return c;
			}
		}

		Class<? extends Buff> partial = null;
		int count = 0;
		for (Entry e : registry) {
			if (debuffOnly != e.debuff) continue;
			if (e.classKey.contains(key) || key.contains(e.classKey)) {
				partial = e.clazz;
				count++;
			}
		}
		if (count == 1) return partial;
		return null;
	}

	private static boolean matchesType(Class<? extends Buff> c, boolean debuffOnly) {
		for (Class<? extends Buff> b : STREAMER_DEBUFFS) {
			if (b == c) return debuffOnly;
		}
		return !debuffOnly;
	}

	public static boolean isDebuff(Class<? extends Buff> clazz) {
		for (Class<? extends Buff> d : STREAMER_DEBUFFS) {
			if (d == clazz) return true;
		}
		return false;
	}

	public static String formatLabel(Class<? extends Buff> clazz, boolean debuff) {
		String tag = debuff ? "debuff" : "buff";
		return Messages.titleCase(clazz.getSimpleName()) + " (" + clazz.getSimpleName() + ", " + tag + ")";
	}

	public static String unknownBuffMessage(String input) {
		List<String> sug = suggestLabels(input, 5, false);
		if (sug.isEmpty()) {
			return "Unknown buff: " + input + " (try: Haste, Barrier, MindVision, …)";
		}
		return "Unknown buff: " + input + ". Did you mean: " + String.join(", ", sug);
	}

	public static String unknownDebuffMessage(String input) {
		List<String> sug = suggestLabels(input, 5, true);
		if (sug.isEmpty()) {
			return "Unknown debuff: " + input + " (try: Blindness, Hex, Slow, …)";
		}
		return "Unknown debuff: " + input + ". Did you mean: " + String.join(", ", sug);
	}

	public static List<String> suggestLabels(String input, int maxResults, Boolean debuffOnly) {
		if (input == null || input.trim().isEmpty() || maxResults <= 0) return Collections.emptyList();
		ensureRegistry();
		String key = StreamerItemResolver.normalizeKey(input.trim());
		ArrayList<Scored> scored = new ArrayList<>();
		for (Entry e : registry) {
			if (debuffOnly != null && e.debuff != debuffOnly) continue;
			int score = scoreMatch(key, e.classKey);
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
			Entry e = scored.get(i).entry;
			out.add(formatLabel(e.clazz, e.debuff));
		}
		return out;
	}

	public static List<String> suggestLabelsAny(String input, int maxResults) {
		return suggestLabels(input, maxResults, null);
	}

	private static final class Scored {
		final int score;
		final Entry entry;

		Scored(int score, Entry entry) {
			this.score = score;
			this.entry = entry;
		}
	}

	private static int scoreMatch(String key, String classKey) {
		if (key.isEmpty()) return 0;
		if (classKey.equals(key)) return 1000;
		if (classKey.contains(key) || key.contains(classKey)) return 100 + Math.min(key.length(), classKey.length());
		int d = levenshtein(key, classKey);
		if (d <= 4) return 50 - d;
		return 0;
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
