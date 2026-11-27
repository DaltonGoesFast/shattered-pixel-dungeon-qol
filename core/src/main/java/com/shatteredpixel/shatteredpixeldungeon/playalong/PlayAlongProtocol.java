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

import com.google.gson.Gson;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;

import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.extensions.IExtension;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Shared JSON DTOs, constants, and stream-safe username validation for Play Along Mode.
 */
public final class PlayAlongProtocol {

	public static final int PROTOCOL_VERSION = 1;
	/**
	 * Player → host cap, in characters. The relay closes a player socket with
	 * 1009 when an incoming player frame exceeds this many bytes. Client sends
	 * must stay under it; one oversized frame is a disconnect, not a drop.
	 */
	public static final int MAX_PACKET_LENGTH = 4096;
	/**
	 * Largest host → player text we produce (world snapshot or one chat batch).
	 * The relay allows 8 MiB on the host link. Staying under this keeps a busy
	 * floor from filling that link or the client's parser.
	 */
	public static final int MAX_HOST_FRAME_CHARS = 48 * 1024;
	/** Client ignores inbound text above this instead of parsing it. */
	public static final int MAX_CLIENT_INBOUND_CHARS = 256 * 1024;
	/**
	 * java-websocket inbound frame cap, in bytes. The library default is
	 * unbounded and will buffer a frame that declares a gigantic length.
	 * 1 MiB closes that frame with 1009. Snapshots stay under
	 * {@link #MAX_HOST_FRAME_CHARS}.
	 */
	public static final int MAX_WEBSOCKET_FRAME_BYTES = 1024 * 1024;
	/** Tombstones kept in memory and on the wire. Older deaths fall off. */
	public static final int MAX_WIRE_TOMBSTONES = 40;
	/** Chat mirrors queued on the host before the oldest are dropped. */
	public static final int MAX_QUEUED_CHAT = 160;
	/** Chat mirrors packed into one host → player frame per broadcast tick. */
	public static final int MAX_CHAT_BATCH = 24;
	public static final long RECONNECT_GRACE_MS = 30_000L;
	public static final long CLIENT_RECONNECT_GRACE_MS = 120_000L;

	/**
	 * Which disconnect copy to show. Network blocking is only for a socket
	 * that never opened. A session that was up and then closed is a desync,
	 * and the close code belongs in the message.
	 */
	public enum CloseCopy {
		NETWORK_BLOCKED,
		NETWORK_LOST,
		DESYNC,
		DESYNC_FINAL
	}

	/**
	 * When false, Join-with-room-code UI is hidden everywhere. Dalton's
	 * title-screen Play Along drop-in is unchanged. Flip to true to re-enable.
	 */
	public static final boolean ROOM_CODES_ENABLED = false;

	public static final Gson GSON = new Gson();

	private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_ ]{3,16}$");

	private static final List<String> RESERVED_HOST_NAMES = Arrays.asList(
			"dalton", "daltongoesfast", "host", "streamer"
	);

	// Mirrors Lastest UI/hero_name_blocklist.txt for client/cross-platform stream safety.
	private static final List<String> NAME_BLOCKLIST = Arrays.asList(
			"nigger", "nigga", "faggot", "retard", "rape", "hitler", "nazi",
			"pedo", "paedo", "fuck", "shit", "cunt", "asshole", "bitch",
			"whore", "slut", "dick", "cock", "pussy", "porn", "sex",
			"kill yourself", "kys"
	);

	private PlayAlongProtocol() {}

	public static class SessionInfo {
		public boolean active;
		public boolean serverOnline = false;
		public boolean inDungeon = false;
		public String sessionId = "";
		public String version = "";
		public long seed;
		public String customSeedText = "";
		public int challenges;
		public int modifiers;
		public String hostClass = "WARRIOR";
		public int hostDepth = 1;
		public int hostPos = 0;
		public int hostHp = 20;
		public int hostHt = 20;
		public int activePlayers = 0;
		/** Host has the Amulet of Yendor. */
		public boolean amuletObtained = false;
		/** Host is climbing back out (AscensionChallenge is on). */
		public boolean ascending = false;
		/** Shallowest floor reached since the climb started. 0 while still descending. */
		public int highestAscent = 0;
		/** Host catch-up settings. Empty boss means Dalton has not killed a region boss yet. */
		public boolean catchupEnabled = false;
		public String catchupBoss = "";
		public int catchupEpoch = 0;
		public String catchupConfig = "";
	}

	public static class StreamEconomyState {
		public boolean serverOnline = true;
		public boolean inDungeon = false;
		public boolean playAlongActive = false;
		public int hostDepth = 1;
		public int hostHp = 0;
		public int hostHt = 0;
		public boolean spendDisabled = false;
		public boolean doublePointsActive = false;
		public int doublePointsSecLeft = 0;
		public float deathCostMult = 1f;
		public float bankRatioManual = 0.10f;
		public boolean linked = false;
		public String linkedUsername = "";
		public String linkedPlatform = "";
		public int chatPts = 0;
		public int donorPts = 0;
		public int totalPts = 0;
		public int chatCap = 300;
		public boolean fardUsed = false;
		public int summonCooldownSec = 0;
		public int bestiaryLevel = 1;
		public String bestiaryZone = "Sewers";
		public int bestiaryXp = 0;
		public int bestiaryThreshold = 1000;
		public List<String> bestiaryUnlocked = new ArrayList<>();
		public Map<String, Integer> costs = new LinkedHashMap<>();
		public Map<String, Integer> monsterCosts = new LinkedHashMap<>();
		public Map<String, Integer> championCosts = new LinkedHashMap<>();
		public List<String> freeKeys = new ArrayList<>();
		public List<String> disabledCommands = new ArrayList<>();
	}

	public static class GhostState {
		public String userId = "";
		public String username = "";
		public String heroClass = "WARRIOR";
		public int armorTier = 0;
		public int depth = 1;
		public int branch = 0;
		public int pos = 0;
		public boolean facingLeft = false;
		public float hpPct = 1f;
		public String action = "IDLE"; // IDLE, MOVE, ATTACK, OPERATE, DEAD
		public boolean isHost = false;
		public boolean catchup = false;
		public long lastUpdateMs = 0L;
	}

	public static class TombstoneState {
		public String userId = "";
		public String username = "";
		public String heroClass = "WARRIOR";
		public int depth = 1;
		public int branch = 0;
		public int pos = 0;
		public String cause = "";
	}

	public static class WipeEvent {
		public String sessionId = "";
		public int hostDepth = 1;
		public String hostCause = "Unknown";
		public int victims = 0;
		public String victimNames = "";
		public long timestampMs = 0L;
	}

	public static class ChatCommandPayload {
		public String command;
		public String username;
		public String monster;
		public int amount;
		public String slot;
		public int tier = -1;
		public long rngSeed;
		public String rolledClass;
		public String secondaryClass;
		public boolean positiveOnly;
		public String bundleJson;
	}

	public static class WireMessage {
		public String type;
		public int protocolVersion = PROTOCOL_VERSION;
		public String sessionId;
		public String modVersion;
		public String userId;
		public String username;
		public String heroClass;
		public int armorTier;
		public int depth;
		public int branch;
		public int pos;
		public boolean facingLeft;
		public float hpPct;
		public String action;
		public String cause;
		public String error;
		public long durationMs;
		public int victims;
		public String victimNames;
		public int activePlayers;
		public SessionInfo session;
		public GhostState hostGhost;
		public List<GhostState> ghosts = new ArrayList<>();
		public List<TombstoneState> tombstones = new ArrayList<>();
		public Map<Integer, Integer> depthCounts = new LinkedHashMap<>();

		// Stream command deck, account linking & milestone rewards
		public String clientToken;
		public String rawCommand;
		public String requestId;
		public String linkCode;
		public int linkExpiresSec;
		public boolean commandOk;
		public String message;
		public int pointsBalance;
		public int pointsAwarded;
		public String rewardKind;
		public int killExp;
		public boolean catchup;
		public boolean amuletObtained;
		public boolean ascending;
		public int highestAscent;
		public StreamEconomyState economy;
		public ChatCommandPayload chatCommand;
		/** Several chat mirrors packed into one frame during a burst. */
		public List<ChatCommandPayload> chatCommands;
	}

	/**
	 * Enforces regex {@code ^[a-zA-Z0-9_ ]{3,16}$}, blocks host impersonation,
	 * and censors blocklisted substrings to {@code Adventurer_<suffix>}.
	 */
	public static String sanitizeUsername(String raw, String fallbackSuffix) {
		String suffix = (fallbackSuffix == null || fallbackSuffix.isEmpty()) ? "01" : fallbackSuffix;
		if (suffix.length() > 4) suffix = suffix.substring(0, 4);
		String fallback = "Adventurer_" + suffix;

		if (raw == null) return fallback;
		String trimmed = raw.trim().replaceAll("\\s+", " ");
		if (!USERNAME_PATTERN.matcher(trimmed).matches()) {
			return fallback;
		}
		String lower = trimmed.toLowerCase(Locale.ROOT);
		String collapsed = lower.replace(" ", "").replace("_", "");
		for (String reserved : RESERVED_HOST_NAMES) {
			if (collapsed.equals(reserved) || lower.equals(reserved)) {
				return fallback;
			}
		}
		for (String bad : NAME_BLOCKLIST) {
			String badCollapsed = bad.replace(" ", "");
			if (lower.contains(bad) || collapsed.contains(badCollapsed)) {
				return fallback;
			}
		}
		return trimmed;
	}

	/** Room-code alphabet shared with relay/playalong_relay.py. No 0/O/1/I. */
	public static final String RELAY_ROOM_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

	public static URI buildWebSocketUri(String address) {
		String addr = (address == null || address.trim().isEmpty())
				? SPDSettings.DEFAULT_PLAY_ALONG_ADDRESS
				: address.trim();
		if (addr.startsWith("ws://") || addr.startsWith("wss://")) {
			return URI.create(addr);
		}
		if (!addr.contains(":")) {
			addr = addr + ":" + SPDSettings.DEFAULT_PLAY_ALONG_PORT;
		}
		return URI.create("ws://" + addr);
	}

	/** Custom public room slug shared with relay/playalong_relay.py. */
	private static final java.util.regex.Pattern PUBLIC_ROOM_NAME =
			java.util.regex.Pattern.compile("^[a-z][a-z0-9_-]{2,23}$");

	/**
	 * Parse a join field: room code, public name, {@code dalton}, or a full
	 * {@code ws(s)://…/join/…} URL. Empty when the text is not a join target.
	 */
	public static String parseJoinInput(String raw) {
		if (raw == null) return "";
		String s = raw.trim().replace(" ", "");
		if (s.isEmpty()) return "";
		String lowerFull = s.toLowerCase(Locale.ROOT);
		int mark = lowerFull.indexOf("/join/");
		if (mark >= 0) {
			s = s.substring(mark + "/join/".length());
			int cut = s.indexOf('?');
			if (cut >= 0) s = s.substring(0, cut);
			cut = s.indexOf('#');
			if (cut >= 0) s = s.substring(0, cut);
			cut = s.indexOf('/');
			if (cut >= 0) s = s.substring(0, cut);
		}
		if (s.isEmpty()) return "";
		if (s.equalsIgnoreCase(SPDSettings.PLAY_ALONG_RELAY_ROOM)) {
			return SPDSettings.PLAY_ALONG_RELAY_ROOM;
		}
		String lower = s.toLowerCase(Locale.ROOT);
		if (isPublicRoomName(lower)) {
			return lower;
		}
		String code = s.replace("-", "").toUpperCase(Locale.ROOT);
		if (isPrivateRoomCode(code)) {
			return code;
		}
		return "";
	}

	/** {@code dalton}, a public name, or a 4–5 character room code. */
	public static String normalizeRoomCode(String raw) {
		return parseJoinInput(raw);
	}

	public static boolean isReservedRoom(String room) {
		return room != null && room.equalsIgnoreCase(SPDSettings.PLAY_ALONG_RELAY_ROOM);
	}

	public static boolean isPrivateRoomCode(String room) {
		if (room == null) return false;
		String s = room.trim().toUpperCase(Locale.ROOT);
		if (s.length() < 4 || s.length() > 5) return false;
		for (int i = 0; i < s.length(); i++) {
			if (RELAY_ROOM_ALPHABET.indexOf(s.charAt(i)) < 0) return false;
		}
		return true;
	}

	/** Custom public slug (not the reserved {@code dalton} room). */
	public static boolean isPublicRoomName(String room) {
		if (room == null) return false;
		String s = room.trim().toLowerCase(Locale.ROOT);
		if (s.equals(SPDSettings.PLAY_ALONG_RELAY_ROOM)) return false;
		return PUBLIC_ROOM_NAME.matcher(s).matches();
	}

	/** Reserved or custom public name — not a private random code. */
	public static boolean isPublicJoinTarget(String room) {
		return isReservedRoom(room) || isPublicRoomName(room);
	}

	/**
	 * Normalize a host-configured public room name. Empty when invalid.
	 * Accepts {@code dalton} or a custom slug.
	 */
	public static String normalizeHostRoomName(String raw) {
		if (raw == null) return "";
		String s = raw.trim().toLowerCase(Locale.ROOT);
		if (s.isEmpty()) return "";
		if (s.equals(SPDSettings.PLAY_ALONG_RELAY_ROOM)) {
			return SPDSettings.PLAY_ALONG_RELAY_ROOM;
		}
		return isPublicRoomName(s) ? s : "";
	}

	public static String relayBase() {
		String base = SPDSettings.playAlongRelayUrl();
		while (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		return base;
	}

	/** Public drop-in URL. Viewers of Dalton's stream use this with no room code. */
	public static String daltonJoinAddress() {
		return relayJoinAddress(SPDSettings.PLAY_ALONG_RELAY_ROOM);
	}

	public static String relayJoinAddress(String room) {
		return relayBase() + "/join/" + room;
	}

	/** {@code null} asks the relay for a random room code. */
	public static String relayHostAddress(String reservedRoomOrNull) {
		if (reservedRoomOrNull == null || reservedRoomOrNull.isEmpty()) {
			return relayBase() + "/host";
		}
		return relayBase() + "/host/" + reservedRoomOrNull;
	}

	/** Shared inbound frame cap for every Play Along socket. */
	public static Draft_6455 websocketDraft() {
		return new Draft_6455(Collections.<IExtension>emptyList(), MAX_WEBSOCKET_FRAME_BYTES);
	}

	public static CloseCopy closeCopy(boolean everOpened, boolean permanent) {
		if (!everOpened) {
			return permanent ? CloseCopy.NETWORK_LOST : CloseCopy.NETWORK_BLOCKED;
		}
		return permanent ? CloseCopy.DESYNC_FINAL : CloseCopy.DESYNC;
	}

	/** {@code ": reason"} or empty. Newlines stripped, length capped. */
	public static String closeSuffix(String reason) {
		if (reason == null) return "";
		String s = reason.replace('\n', ' ').replace('\r', ' ').trim();
		if (s.length() > 80) s = s.substring(0, 80).trim();
		if (s.isEmpty()) return "";
		return ": " + s;
	}

	public static <T> List<T> tail(List<T> items, int max) {
		if (items == null || items.isEmpty() || max <= 0) return new ArrayList<>();
		if (items.size() <= max) return new ArrayList<>(items);
		return new ArrayList<>(items.subList(items.size() - max, items.size()));
	}

	/**
	 * Pull a chat batch off {@code queue}. The JSON we would send is kept at
	 * or under {@code maxChars} unless a single command is already larger.
	 */
	public static List<ChatCommandPayload> takeChatBatch(ArrayDeque<ChatCommandPayload> queue,
	                                                     String sessionId, int maxCount, int maxChars) {
		List<ChatCommandPayload> batch = new ArrayList<>();
		if (queue == null || maxCount <= 0) return batch;
		while (!queue.isEmpty() && batch.size() < maxCount) {
			batch.add(queue.peekFirst());
			if (batch.size() > 1 && chatBatchJson(sessionId, batch).length() > maxChars) {
				batch.remove(batch.size() - 1);
				break;
			}
			queue.removeFirst();
			if (chatBatchJson(sessionId, batch).length() > maxChars) break;
		}
		return batch;
	}

	public static String chatBatchJson(String sessionId, List<ChatCommandPayload> batch) {
		WireMessage msg = new WireMessage();
		msg.type = "chat_command_batch";
		msg.sessionId = sessionId == null ? "" : sessionId;
		msg.chatCommands = batch;
		return GSON.toJson(msg);
	}

	/**
	 * Serialize a world snapshot, dropping oldest tombstones and then extra
	 * ghosts until it fits {@link #MAX_HOST_FRAME_CHARS}. Null when even a
	 * minimal snapshot does not fit — the caller skips the send.
	 */
	public static String fitWorldStateJson(WireMessage world) {
		if (world == null) return null;
		if (world.tombstones != null && world.tombstones.size() > MAX_WIRE_TOMBSTONES) {
			world.tombstones = tail(world.tombstones, MAX_WIRE_TOMBSTONES);
		}
		String json = GSON.toJson(world);
		while (json.length() > MAX_HOST_FRAME_CHARS
				&& world.tombstones != null && !world.tombstones.isEmpty()) {
			world.tombstones.remove(0);
			json = GSON.toJson(world);
		}
		while (json.length() > MAX_HOST_FRAME_CHARS
				&& world.ghosts != null && world.ghosts.size() > 1) {
			world.ghosts.remove(world.ghosts.size() - 1);
			json = GSON.toJson(world);
		}
		if (json.length() > MAX_HOST_FRAME_CHARS) return null;
		return json;
	}
}
