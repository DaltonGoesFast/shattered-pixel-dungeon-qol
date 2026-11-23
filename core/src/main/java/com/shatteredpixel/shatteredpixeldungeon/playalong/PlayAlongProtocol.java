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

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
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
	public static final int MAX_PACKET_LENGTH = 4096;
	public static final long RECONNECT_GRACE_MS = 30_000L;
	public static final long CLIENT_RECONNECT_GRACE_MS = 120_000L;

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
		public StreamEconomyState economy;
		public ChatCommandPayload chatCommand;
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
}
