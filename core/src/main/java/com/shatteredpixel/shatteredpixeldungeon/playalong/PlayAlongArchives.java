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

package com.shatteredpixel.shatteredpixeldungeon.playalong;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side Play Along save archives: up to 4 slots (96–99) keyed by host sessionId.
 */
public final class PlayAlongArchives {

	public static final int SLOT_FIRST = 96;
	public static final int SLOT_COUNT = 4;
	public static final int MAX_ARCHIVES = 4;

	private PlayAlongArchives() {}

	public static boolean isPlayAlongSlot(int slot) {
		return slot >= SLOT_FIRST && slot < SLOT_FIRST + SLOT_COUNT;
	}

	public static int findSlot(String sessionId) {
		if (sessionId == null || sessionId.trim().isEmpty()) return -1;
		rebuildFromDiskIfNeeded();
		Integer slot = loadSlots().get(sessionId.trim());
		if (slot == null) return -1;
		if (!GamesInProgress.gameExists(slot)) {
			return -1;
		}
		return slot;
	}

	public static boolean hasArchive(String sessionId) {
		return findSlot(sessionId) > 0;
	}

	public static void remember(String sessionId, int slot) {
		if (sessionId == null || sessionId.trim().isEmpty() || !isPlayAlongSlot(slot)) return;
		String sid = sessionId.trim();
		LinkedHashMap<String, Integer> slots = loadSlots();
		List<String> order = loadOrder();

		// Drop any other session pointing at this slot.
		Iterator<Map.Entry<String, Integer>> it = slots.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, Integer> e = it.next();
			if (e.getValue() == slot && !e.getKey().equals(sid)) {
				it.remove();
				order.remove(e.getKey());
			}
		}

		slots.put(sid, slot);
		order.remove(sid);
		order.add(sid);
		trimToMax(slots, order);
		save(slots, order);
	}

	public static void forget(String sessionId) {
		if (sessionId == null || sessionId.trim().isEmpty()) return;
		String sid = sessionId.trim();
		LinkedHashMap<String, Integer> slots = loadSlots();
		List<String> order = loadOrder();
		slots.remove(sid);
		order.remove(sid);
		save(slots, order);
	}

	public static void touch(String sessionId) {
		if (sessionId == null || sessionId.trim().isEmpty()) return;
		String sid = sessionId.trim();
		LinkedHashMap<String, Integer> slots = loadSlots();
		List<String> order = loadOrder();
		if (!slots.containsKey(sid)) return;
		order.remove(sid);
		order.add(sid);
		save(slots, order);
	}

	/**
	 * Picks a free Play Along slot for a brand-new race. Evicts the LRU archive if all 4 are full.
	 */
	public static int allocateForNewRun(String sessionId) {
		rebuildFromDiskIfNeeded();
		LinkedHashMap<String, Integer> slots = loadSlots();
		List<String> order = loadOrder();

		if (sessionId != null && !sessionId.trim().isEmpty()) {
			Integer existing = slots.get(sessionId.trim());
			if (existing != null && isPlayAlongSlot(existing)) {
				Dungeon.deleteGame(existing, true);
				GamesInProgress.setUnknown(existing);
				return existing;
			}
		}

		for (int slot = SLOT_FIRST; slot < SLOT_FIRST + SLOT_COUNT; slot++) {
			if (!isSlotClaimed(slots, slot) && !GamesInProgress.gameExists(slot)) {
				return slot;
			}
		}

		// Prefer reclaiming orphan files (exist on disk but not in index).
		for (int slot = SLOT_FIRST; slot < SLOT_FIRST + SLOT_COUNT; slot++) {
			if (!isSlotClaimed(slots, slot)) {
				Dungeon.deleteGame(slot, true);
				GamesInProgress.setUnknown(slot);
				return slot;
			}
		}

		// Evict LRU.
		if (!order.isEmpty()) {
			String oldest = order.get(0);
			Integer slot = slots.get(oldest);
			if (slot != null && isPlayAlongSlot(slot)) {
				Dungeon.deleteGame(slot, true);
				GamesInProgress.setUnknown(slot);
				slots.remove(oldest);
				order.remove(0);
				save(slots, order);
				return slot;
			}
		}

		int fallback = GamesInProgress.PLAY_ALONG_SLOT;
		Dungeon.deleteGame(fallback, true);
		GamesInProgress.setUnknown(fallback);
		return fallback;
	}

	public static void rebuildFromDiskIfNeeded() {
		LinkedHashMap<String, Integer> slots = loadSlots();
		List<String> order = loadOrder();
		boolean changed = false;

		for (int slot = SLOT_FIRST; slot < SLOT_FIRST + SLOT_COUNT; slot++) {
			GamesInProgress.Info info = GamesInProgress.check(slot);
			if (info == null) continue;
			String sid = info.playAlongSessionId != null ? info.playAlongSessionId.trim() : "";
			if (sid.isEmpty()) continue;
			if (!slots.containsKey(sid)) {
				slots.put(sid, slot);
				if (!order.contains(sid)) order.add(sid);
				changed = true;
			}
		}

		// Legacy: slot 99 may predate session stamping — keep it usable via allocate/orphan path.
		if (changed) {
			trimToMax(slots, order);
			save(slots, order);
		}
	}

	private static boolean isSlotClaimed(Map<String, Integer> slots, int slot) {
		for (Integer s : slots.values()) {
			if (s != null && s == slot) return true;
		}
		return false;
	}

	private static void trimToMax(LinkedHashMap<String, Integer> slots, List<String> order) {
		while (order.size() > MAX_ARCHIVES) {
			String oldest = order.remove(0);
			Integer slot = slots.remove(oldest);
			if (slot != null && isPlayAlongSlot(slot)) {
				Dungeon.deleteGame(slot, true);
				GamesInProgress.setUnknown(slot);
			}
		}
	}

	private static LinkedHashMap<String, Integer> loadSlots() {
		LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
		try {
			String raw = SPDSettings.playAlongArchivesJson();
			if (raw == null || raw.trim().isEmpty()) return map;
			JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
			if (!root.has("slots") || !root.get("slots").isJsonObject()) return map;
			JsonObject slots = root.getAsJsonObject("slots");
			for (Map.Entry<String, JsonElement> e : slots.entrySet()) {
				if (e.getKey() == null || e.getKey().isEmpty()) continue;
				int slot = e.getValue().getAsInt();
				if (isPlayAlongSlot(slot)) {
					map.put(e.getKey(), slot);
				}
			}
		} catch (Exception ignored) {
		}
		return map;
	}

	private static List<String> loadOrder() {
		List<String> order = new ArrayList<>();
		try {
			String raw = SPDSettings.playAlongArchivesJson();
			if (raw == null || raw.trim().isEmpty()) return order;
			JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
			if (!root.has("order") || !root.get("order").isJsonArray()) {
				order.addAll(loadSlots().keySet());
				return order;
			}
			JsonArray arr = root.getAsJsonArray("order");
			for (JsonElement el : arr) {
				if (el != null && el.isJsonPrimitive()) {
					String sid = el.getAsString();
					if (sid != null && !sid.isEmpty() && !order.contains(sid)) {
						order.add(sid);
					}
				}
			}
		} catch (Exception ignored) {
		}
		return order;
	}

	private static void save(LinkedHashMap<String, Integer> slots, List<String> order) {
		JsonObject root = new JsonObject();
		JsonObject slotObj = new JsonObject();
		for (Map.Entry<String, Integer> e : slots.entrySet()) {
			slotObj.addProperty(e.getKey(), e.getValue());
		}
		JsonArray orderArr = new JsonArray();
		for (String sid : order) {
			if (slots.containsKey(sid)) orderArr.add(sid);
		}
		root.add("slots", slotObj);
		root.add("order", orderArr);
		SPDSettings.playAlongArchivesJson(root.toString());
	}
}
