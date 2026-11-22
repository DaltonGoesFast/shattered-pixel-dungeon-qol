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
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.SessionInfo;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.StreamEconomyState;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.WireMessage;

import org.java_websocket.WebSocket;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Proxies stream-control requests ({@code stream_state_req}, {@code stream_link_req},
 * {@code stream_cmd_req}, milestone rewards, and linked-username lookups) to the local
 * overlay HTTP server without requiring an active Play Along race session.
 * <p>
 * Auth model: stream control is authorized per-viewer by {@code clientToken} (verified by
 * the overlay API), independent of the race lobby {@code joinCode}.
 */
public class StreamControlProxy {

	public interface StatusProvider {
		SessionInfo snapshotStatus();
	}

	public interface TransportCallbacks {
		void safeSend(WebSocket conn, String json);
		void onFailure(String failureClass, String sessionId, Throwable err);
		void onBossSlainReward(String username, int depth, int awardedPts);
	}

	public interface LinkedUsernameConsumer {
		void onVerifiedUsername(String sanitizedUsername);
	}

	private final StatusProvider statusProvider;
	private final TransportCallbacks callbacks;
	private final PlayAlongHostMetrics metrics;

	private volatile String overrideOverlayBaseUrl = null;
	private volatile ExecutorService httpPool;

	public StreamControlProxy(StatusProvider statusProvider,
	                          TransportCallbacks callbacks,
	                          PlayAlongHostMetrics metrics) {
		this.statusProvider = statusProvider;
		this.callbacks = callbacks;
		this.metrics = metrics != null ? metrics : new PlayAlongHostMetrics();
	}

	public synchronized void start() {
		if (httpPool == null || httpPool.isShutdown()) {
			httpPool = Executors.newFixedThreadPool(4, r -> {
				Thread t = new Thread(r, "PlayAlongHttpBridge");
				t.setDaemon(true);
				return t;
			});
		}
	}

	public synchronized void shutdown() {
		if (httpPool != null) {
			httpPool.shutdownNow();
			httpPool = null;
		}
	}

	public void setOverlayBaseUrl(String baseUrl) {
		if (baseUrl == null || baseUrl.trim().isEmpty()) {
			this.overrideOverlayBaseUrl = null;
		} else {
			this.overrideOverlayBaseUrl = SPDSettings.normalizeOverlayUrl(baseUrl);
		}
	}

	public String getOverlayBaseUrl() {
		String override = overrideOverlayBaseUrl;
		return override != null ? override : SPDSettings.streamOverlayUrl();
	}

	public void handleStreamStateRequest(final WebSocket conn, WireMessage msg) {
		final String token = msg.clientToken != null ? msg.clientToken.trim() : "";
		final String reqId = msg.requestId;
		if (!PlayAlongProtocol.isValidClientToken(token)) {
			SessionInfo snap = statusProvider.snapshotStatus();
			WireMessage resp = new WireMessage();
			resp.type = "stream_state_resp";
			resp.requestId = reqId;
			resp.commandOk = false;
			resp.message = "Invalid or missing stream client token.";
			resp.economy = buildFallbackEconomy(snap);
			sendResponse(conn, resp);
			return;
		}
		submitHttp(currentSessionId(), () -> {
			SessionInfo snap = statusProvider.snapshotStatus();
			StreamEconomyState eco = fetchEconomyStateFromOverlay(token, snap);
			WireMessage resp = new WireMessage();
			resp.type = "stream_state_resp";
			resp.requestId = reqId;
			resp.economy = eco;
			sendResponse(conn, resp);
		});
	}

	public void handleStreamLinkRequest(final WebSocket conn, WireMessage msg) {
		final String token = msg.clientToken != null ? msg.clientToken.trim() : "";
		final String reqId = msg.requestId;
		if (!PlayAlongProtocol.isValidClientToken(token)) {
			SessionInfo snap = statusProvider.snapshotStatus();
			WireMessage resp = new WireMessage();
			resp.type = "stream_link_resp";
			resp.requestId = reqId;
			resp.commandOk = false;
			resp.message = "Invalid or missing stream client token.";
			resp.economy = buildFallbackEconomy(snap);
			sendResponse(conn, resp);
			return;
		}
		submitHttp(currentSessionId(), () -> {
			SessionInfo snap = statusProvider.snapshotStatus();
			String sid = snap != null ? snap.sessionId : "";
			WireMessage resp = new WireMessage();
			resp.type = "stream_link_resp";
			resp.requestId = reqId;
			try {
				JsonObject body = new JsonObject();
				body.addProperty("token", token);
				JsonObject res = httpPostJson(getOverlayBaseUrl() + "/api/playalong/link-request", body);
				if (res != null && res.has("ok") && res.get("ok").getAsBoolean()) {
					resp.commandOk = true;
					resp.linkCode = res.has("code") ? res.get("code").getAsString() : "";
					resp.linkExpiresSec = res.has("expires_in") ? res.get("expires_in").getAsInt() : 300;
				} else {
					resp.commandOk = false;
					resp.message = (res != null && res.has("error"))
							? res.get("error").getAsString()
							: "Overlay server unavailable.";
				}
			} catch (Exception e) {
				reportHttpFailure("http_link_request", sid, e);
				resp.commandOk = false;
				resp.message = "Overlay server unavailable (" + e.getClass().getSimpleName() + ")";
			}
			resp.economy = fetchEconomyStateFromOverlay(token, snap);
			sendResponse(conn, resp);
		});
	}

	public void handleStreamCommandRequest(final WebSocket conn, WireMessage msg) {
		final String token = msg.clientToken != null ? msg.clientToken.trim() : "";
		final String rawCmd = msg.rawCommand != null ? msg.rawCommand.trim() : "";
		final String reqId = msg.requestId;
		if (!PlayAlongProtocol.isValidClientToken(token)) {
			SessionInfo snap = statusProvider.snapshotStatus();
			WireMessage resp = new WireMessage();
			resp.type = "stream_cmd_resp";
			resp.requestId = reqId;
			resp.commandOk = false;
			resp.message = "Invalid or missing stream client token.";
			resp.economy = buildFallbackEconomy(snap);
			sendResponse(conn, resp);
			return;
		}
		submitHttp(currentSessionId(), () -> {
			SessionInfo snap = statusProvider.snapshotStatus();
			String sid = snap != null ? snap.sessionId : "";
			WireMessage resp = new WireMessage();
			resp.type = "stream_cmd_resp";
			resp.requestId = reqId;
			try {
				JsonObject body = new JsonObject();
				body.addProperty("token", token);
				body.addProperty("rawMessage", rawCmd);
				JsonObject res = httpPostJson(getOverlayBaseUrl() + "/api/playalong/client-command", body);
				if (res != null) {
					resp.commandOk = res.has("ok") && res.get("ok").getAsBoolean();
					if (res.has("message") && !res.get("message").isJsonNull()) {
						resp.message = res.get("message").getAsString();
					}
					if (res.has("pts") && !res.get("pts").isJsonNull()) {
						resp.pointsBalance = res.get("pts").getAsInt();
					}
					if (res.has("state") && res.get("state").isJsonObject()) {
						resp.economy = parseEconomyState(res.getAsJsonObject("state"), snap);
					}
				} else {
					resp.commandOk = false;
					resp.message = "Overlay server returned empty response.";
				}
			} catch (Exception e) {
				reportHttpFailure("http_client_command", sid, e);
				resp.commandOk = false;
				resp.message = "Overlay server offline (" + e.getClass().getSimpleName() + ").";
			}
			if (resp.economy == null) {
				resp.economy = fetchEconomyStateFromOverlay(token, snap);
			}
			sendResponse(conn, resp);
		});
	}

	public void triggerMilestoneRewardAsync(final WebSocket conn, final String username,
	                                        final String sessionId, final String token,
	                                        final String milestone, final int bossDepth) {
		if (!PlayAlongProtocol.isValidClientToken(token)) return;
		submitHttp(sessionId, () -> {
			try {
				JsonObject body = new JsonObject();
				body.addProperty("sessionId", sessionId != null ? sessionId : "");
				body.addProperty("token", token);
				body.addProperty("username", username != null ? username : "");
				body.addProperty("milestone", milestone);
				JsonObject res = httpPostJson(getOverlayBaseUrl() + "/api/playalong/milestone-reward", body);
				if (res == null || !res.has("ok") || !res.get("ok").getAsBoolean()) return;

				int awarded = res.has("awarded") ? res.get("awarded").getAsInt() : 0;
				if (awarded <= 0) return;

				String uname = res.has("username") ? res.get("username").getAsString() : username;
				String label = res.has("label") ? res.get("label").getAsString() : milestone;
				String kind = res.has("kind") ? res.get("kind").getAsString() : "chat";
				int bal = res.has("pts") ? res.get("pts").getAsInt() : 0;

				WireMessage notice = new WireMessage();
				notice.type = "milestone_reward_notice";
				notice.username = uname;
				notice.message = label;
				notice.pointsAwarded = awarded;
				notice.rewardKind = kind;
				notice.pointsBalance = bal;
				notice.depth = bossDepth;
				sendResponse(conn, notice);

				if (bossDepth > 0 && callbacks != null) {
					callbacks.onBossSlainReward(uname, bossDepth, awarded);
				}
			} catch (Exception e) {
				reportHttpFailure("http_milestone_reward", sessionId, e);
			}
		});
	}

	public void syncLinkedUsernameAsync(final String token, final String shortSuffix,
	                                    final String hostUsername,
	                                    final LinkedUsernameConsumer consumer) {
		if (!PlayAlongProtocol.isValidClientToken(token) || consumer == null) return;
		submitHttp(currentSessionId(), () -> {
			SessionInfo snap = statusProvider.snapshotStatus();
			String sid = snap != null ? snap.sessionId : "";
			try {
				StreamEconomyState eco = fetchEconomyStateFromOverlay(token, snap);
				if (eco != null && eco.linked && eco.linkedUsername != null && !eco.linkedUsername.trim().isEmpty()) {
					String verified = PlayAlongProtocol.sanitizeUsername(
							eco.linkedUsername, shortSuffix, hostUsername);
					consumer.onVerifiedUsername(verified);
				}
			} catch (Exception e) {
				reportHttpFailure("http_join_sync", sid, e);
			}
		});
	}

	private StreamEconomyState fetchEconomyStateFromOverlay(String token, SessionInfo snap) {
		String sid = snap != null ? snap.sessionId : "";
		try {
			String enc = URLEncoder.encode(token != null ? token : "", "UTF-8");
			String url = getOverlayBaseUrl() + "/api/playalong/client-state?token=" + enc
					+ (snap != null && snap.inDungeon ? ("&depth=" + snap.hostDepth) : "");
			JsonObject json = httpGetJson(url);
			if (json != null) {
				return parseEconomyState(json, snap);
			}
		} catch (Exception e) {
			reportHttpFailure("http_client_state", sid, e);
		}
		return buildFallbackEconomy(snap);
	}

	private static StreamEconomyState buildFallbackEconomy(SessionInfo snap) {
		StreamEconomyState fallback = new StreamEconomyState();
		if (snap != null) {
			fallback.serverOnline = snap.serverOnline;
			fallback.inDungeon = snap.inDungeon;
			fallback.playAlongActive = snap.active;
			fallback.hostDepth = snap.hostDepth;
			fallback.hostHp = snap.hostHp;
			fallback.hostHt = snap.hostHt;
		}
		return fallback;
	}

	private static StreamEconomyState parseEconomyState(JsonObject json, SessionInfo snap) {
		StreamEconomyState eco = buildFallbackEconomy(snap);

		eco.spendDisabled = getBool(json, "spend_disabled", false);
		eco.doublePointsActive = getBool(json, "double_points_active", false);
		eco.doublePointsSecLeft = getInt(json, "double_points_sec_left", 0);
		eco.deathCostMult = getFloat(json, "death_cost_mult", 1f);
		eco.bankRatioManual = getFloat(json, "bank_ratio_manual", 0.10f);

		if (json.has("costs") && json.get("costs").isJsonObject()) {
			readIntMap(json.getAsJsonObject("costs"), eco.costs);
		}
		if (json.has("monster_costs") && json.get("monster_costs").isJsonObject()) {
			readIntMap(json.getAsJsonObject("monster_costs"), eco.monsterCosts);
		}
		if (json.has("champion_costs") && json.get("champion_costs").isJsonObject()) {
			readIntMap(json.getAsJsonObject("champion_costs"), eco.championCosts);
		}
		if (json.has("free_keys") && json.get("free_keys").isJsonArray()) {
			readStringList(json.getAsJsonArray("free_keys"), eco.freeKeys);
		}
		if (json.has("disabled_commands") && json.get("disabled_commands").isJsonArray()) {
			readStringList(json.getAsJsonArray("disabled_commands"), eco.disabledCommands);
		}
		if (json.has("bestiary") && json.get("bestiary").isJsonObject()) {
			JsonObject b = json.getAsJsonObject("bestiary");
			eco.bestiaryLevel = getInt(b, "level", 1);
			eco.bestiaryZone = getStr(b, "zone", "Sewers");
			eco.bestiaryXp = getInt(b, "bar_xp", 0);
			eco.bestiaryThreshold = getInt(b, "bar_threshold", 1000);
			if (b.has("unlocked_monsters") && b.get("unlocked_monsters").isJsonArray()) {
				readStringList(b.getAsJsonArray("unlocked_monsters"), eco.bestiaryUnlocked);
			}
		}
		if (json.has("user") && json.get("user").isJsonObject()) {
			JsonObject u = json.getAsJsonObject("user");
			eco.linked = getBool(u, "linked", false);
			eco.linkedUsername = getStr(u, "username", "");
			eco.linkedPlatform = getStr(u, "platform", "");
			eco.chatPts = getInt(u, "chat_pts", 0);
			eco.donorPts = getInt(u, "donor_pts", 0);
			eco.totalPts = getInt(u, "total_pts", 0);
			eco.chatCap = getInt(u, "chat_cap", 300);
			eco.fardUsed = getBool(u, "fard_used", false);
			eco.summonCooldownSec = getInt(u, "summon_cooldown_sec", 0);
		}
		return eco;
	}

	private static void readIntMap(JsonObject obj, Map<String, Integer> out) {
		for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
			try {
				if (e.getValue() != null && !e.getValue().isJsonNull()) {
					out.put(e.getKey(), e.getValue().getAsInt());
				}
			} catch (Exception ex) {
				System.err.println("[PlayAlong] Skipping malformed economy int key '" + e.getKey()
						+ "': " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
			}
		}
	}

	private static void readStringList(JsonArray arr, List<String> out) {
		for (JsonElement el : arr) {
			if (el != null && !el.isJsonNull()) {
				out.add(el.getAsString());
			}
		}
	}

	private static boolean getBool(JsonObject o, String k, boolean def) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsBoolean() : def;
	}

	private static int getInt(JsonObject o, String k, int def) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : def;
	}

	private static float getFloat(JsonObject o, String k, float def) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsFloat() : def;
	}

	private static String getStr(JsonObject o, String k, String def) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def;
	}

	private void submitHttp(String sessionId, Runnable task) {
		ExecutorService pool = httpPool;
		if (pool == null || pool.isShutdown()) {
			start();
			pool = httpPool;
		}
		if (pool != null && !pool.isShutdown()) {
			try {
				pool.submit(task);
			} catch (Exception e) {
				reportHttpFailure("http_pool_submit", sessionId, e);
			}
		}
	}

	private void reportHttpFailure(String failureClass, String sessionId, Throwable err) {
		metrics.incrementHttpBridgeFailures();
		if (callbacks != null) {
			callbacks.onFailure(failureClass, sessionId, err);
		}
	}

	private String currentSessionId() {
		SessionInfo s = statusProvider != null ? statusProvider.snapshotStatus() : null;
		return s != null && s.sessionId != null ? s.sessionId : "";
	}

	private void sendResponse(WebSocket conn, WireMessage resp) {
		if (callbacks != null && conn != null) {
			callbacks.safeSend(conn, PlayAlongProtocol.GSON.toJson(resp));
		}
	}

	private static JsonObject httpGetJson(String urlStr) throws Exception {
		HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
		conn.setRequestMethod("GET");
		conn.setConnectTimeout(1500);
		conn.setReadTimeout(2500);
		return readJsonResponse(conn);
	}

	private static JsonObject httpPostJson(String urlStr, JsonObject payload) throws Exception {
		HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
		conn.setRequestMethod("POST");
		conn.setDoOutput(true);
		conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
		conn.setConnectTimeout(1500);
		conn.setReadTimeout(12000);
		byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
		try (OutputStream os = conn.getOutputStream()) {
			os.write(bytes);
		}
		return readJsonResponse(conn);
	}

	private static JsonObject readJsonResponse(HttpURLConnection conn) throws Exception {
		int code = conn.getResponseCode();
		InputStream is = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
		if (is == null) {
			if (code >= 500) {
				throw new IOException("Overlay HTTP " + code);
			}
			return null;
		}
		StringBuilder sb = new StringBuilder();
		try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
			String line;
			while ((line = br.readLine()) != null) {
				sb.append(line);
			}
		} finally {
			conn.disconnect();
		}
		String raw = sb.toString().trim();
		if (raw.isEmpty()) {
			if (code >= 500) {
				throw new IOException("Overlay HTTP " + code + " (empty body)");
			}
			return null;
		}
		JsonElement el = JsonParser.parseString(raw);
		if (code >= 500) {
			throw new IOException("Overlay HTTP " + code + ": " + raw);
		}
		return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
	}
}
