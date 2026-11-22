package com.shatteredpixel.shatteredpixeldungeon.playalong;

import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.ChatCommandPayload;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.GhostState;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.SessionInfo;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.TombstoneState;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.WireMessage;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Headless protocol and host-server tests for {@link PlayAlongServer} and {@link StreamControlProxy}.
 */
public class PlayAlongProtocolServerTest {

	private PlayAlongServer server;
	private int port;
	private final List<String> hostWarnings = new CopyOnWriteArrayList<>();
	private final List<WebSocketClient> openSockets = new ArrayList<>();

	@Before
	public void setUp() throws Exception {
		hostWarnings.clear();
		CountDownLatch started = new CountDownLatch(1);
		server = new PlayAlongServer(0, new PlayAlongServer.HostCallbacks() {
			@Override
			public void onWorldStateUpdated(List<GhostState> ghosts, List<TombstoneState> tombstones, int aliveCount) {}

			@Override
			public void onChatterDied(String username, int depth, String cause) {}

			@Override
			public void onChatterWon(String username, long durationMs) {}

			@Override
			public void onChatterBossSlain(String username, int depth, int awardedPts) {}

			@Override
			public void onHostWarning(String failureClass, String message) {
				hostWarnings.add(failureClass + ":" + message);
			}
		}) {
			@Override
			public void onStart() {
				super.onStart();
				started.countDown();
			}
		};
		server.start();
		assertTrue("PlayAlongServer failed to start", started.await(5, TimeUnit.SECONDS));
		port = server.getPort();
		assertTrue("Expected bound ephemeral port", port > 0);
	}

	@After
	public void tearDown() {
		for (WebSocketClient ws : openSockets) {
			try {
				ws.close();
			} catch (Exception ignored) {
			}
		}
		openSockets.clear();
		if (server != null) {
			server.endSession();
			server.stopServer();
		}
	}

	private SessionInfo startTestSession(String hostUsername, boolean requireCode) {
		server.setRequireJoinCode(requireCode);
		server.setLobbyJoinCode("ABCD");

		SessionInfo s = new SessionInfo();
		s.active = true;
		s.sessionId = "sess-test-1";
		s.version = "4.0.2-QoL-18";
		s.hostUsername = hostUsername;
		s.joinCodeRequired = requireCode;
		s.seed = 987654321L;
		s.hostDepth = 1;
		s.hostPos = 10;
		s.hostHp = 20;
		s.hostHt = 20;

		GhostState hg = new GhostState();
		hg.userId = "host";
		hg.username = hostUsername;
		hg.depth = 1;
		hg.pos = 10;
		hg.isHost = true;
		server.startSession(s, hg);
		return s;
	}

	private TestWsClient connectRawClient() throws Exception {
		TestWsClient client = new TestWsClient(URI.create("ws://127.0.0.1:" + port));
		openSockets.add(client);
		assertTrue("WebSocket client failed to connect", client.connectBlocking(3, TimeUnit.SECONDS));
		return client;
	}

	private WireMessage joinClient(TestWsClient client, String userId, String username,
	                               int protocolVersion, String joinCode, String clientToken) throws Exception {
		WireMessage join = new WireMessage();
		join.type = "join";
		join.protocolVersion = protocolVersion;
		join.userId = userId;
		join.username = username;
		join.joinCode = joinCode;
		join.clientToken = clientToken;
		join.heroClass = "WARRIOR";
		join.modVersion = "4.0.2-QoL-18";
		join.depth = 1;
		join.pos = 12;
		client.send(PlayAlongProtocol.GSON.toJson(join));
		return client.awaitType(3, TimeUnit.SECONDS, "join_ack", "join_error");
	}

	@Test
	public void joinEnforcesProtocolVersionAndLobbyCodeAndSanitizesHostImpersonation() throws Exception {
		startTestSession("StreamerBob", true);

		// 1. Missing protocolVersion in raw JSON is rejected
		TestWsClient rawNoVer = connectRawClient();
		rawNoVer.send("{\"type\":\"join\",\"userId\":\"u-nover\",\"username\":\"Alice\",\"joinCode\":\"ABCD\"}");
		WireMessage errNoVer = rawNoVer.awaitType(3, TimeUnit.SECONDS, "join_error");
		assertNotNull(errNoVer);
		assertTrue(errNoVer.error.contains("Protocol version mismatch"));

		// 2. Wrong protocolVersion is rejected with join_error
		TestWsClient wrongVer = connectRawClient();
		WireMessage errWrongVer = joinClient(wrongVer, "u-badver", "Alice", 99, "ABCD", "");
		assertNotNull(errWrongVer);
		assertEquals("join_error", errWrongVer.type);
		assertTrue(errWrongVer.error.contains("Protocol version mismatch"));

		// 3. Wrong join code is rejected
		TestWsClient badCode = connectRawClient();
		WireMessage errBadCode = joinClient(badCode, "u-badcode", "Alice", PlayAlongProtocol.PROTOCOL_VERSION, "NOPE", "");
		assertNotNull(errBadCode);
		assertEquals("join_error", errBadCode.type);
		assertTrue(errBadCode.error.toLowerCase().contains("join code"));

		// 4. Valid join succeeds and impersonating active hostUsername gets sanitized
		TestWsClient good = connectRawClient();
		WireMessage ack = joinClient(good, "u-good", "StreamerBob", PlayAlongProtocol.PROTOCOL_VERSION, "ABCD", "");
		assertNotNull(ack);
		assertEquals("join_ack", ack.type);
		assertTrue("Expected sanitized fallback name", ack.username.startsWith("Adventurer_"));
		assertEquals("StreamerBob", ack.session.hostUsername);
		assertEquals(1, server.getMetrics().getTotalJoins());
		assertEquals(1, server.getMetrics().getActiveClients());
		assertEquals(3, server.getMetrics().getRejectedJoins());
	}

	@Test
	public void maxClientsAdmissionAllowsPingAndRejoinWhileRejectingOverflow() throws Exception {
		startTestSession("HostOne", false);
		server.setMaxClients(2);

		TestWsClient c1 = connectRawClient();
		assertEquals("join_ack", joinClient(c1, "u1", "RacerOne", 1, "", "tok-11111111").type);

		TestWsClient c2 = connectRawClient();
		assertEquals("join_ack", joinClient(c2, "u2", "RacerTwo", 1, "", "tok-22222222").type);

		// Third distinct user should receive join_error
		TestWsClient c3 = connectRawClient();
		WireMessage fullErr = joinClient(c3, "u3", "RacerThree", 1, "", "tok-33333333");
		assertNotNull(fullErr);
		assertEquals("join_error", fullErr.type);
		assertTrue(fullErr.error.contains("lobby is full"));

		// Status ping must still work even when race lobby is at MAX_CLIENTS
		TestWsClient pinger = connectRawClient();
		WireMessage ping = new WireMessage();
		ping.type = "status_ping";
		pinger.send(PlayAlongProtocol.GSON.toJson(ping));
		WireMessage pong = pinger.awaitType(3, TimeUnit.SECONDS, "status_pong");
		assertNotNull(pong);
		assertEquals(2, pong.session.activePlayers);

		// Existing user u1 can still rejoin on a fresh socket even at capacity
		TestWsClient c1Rejoin = connectRawClient();
		WireMessage rejoin = new WireMessage();
		rejoin.type = "rejoin";
		rejoin.protocolVersion = PlayAlongProtocol.PROTOCOL_VERSION;
		rejoin.sessionId = "sess-test-1";
		rejoin.userId = "u1";
		rejoin.username = "RacerOne";
		c1Rejoin.send(PlayAlongProtocol.GSON.toJson(rejoin));
		WireMessage reAck = c1Rejoin.awaitType(3, TimeUnit.SECONDS, "join_ack");
		assertNotNull(reAck);
		assertEquals("join_ack", reAck.type);
	}

	@Test
	public void rateLimitDropsExcessBurstMessages() throws Exception {
		startTestSession("HostOne", false);

		TestWsClient c = connectRawClient();
		assertEquals("join_ack", joinClient(c, "u-rate", "BurstTester", 1, "", "").type);
		c.drainAll();

		// Send 15 status_ping messages immediately inside 1 second; server cap is 8/sec
		for (int i = 0; i < 15; i++) {
			WireMessage ping = new WireMessage();
			ping.type = "status_ping";
			c.send(PlayAlongProtocol.GSON.toJson(ping));
		}

		// Wait briefly for server responses
		Thread.sleep(400);
		int pongs = 0;
		for (WireMessage m : c.drainAll()) {
			if ("status_pong".equals(m.type)) {
				pongs++;
			}
		}
		assertTrue("Expected rate-limited pongs <= " + PlayAlongServer.RATE_LIMIT_PER_SEC + ", got " + pongs,
				pongs >= 1 && pongs <= PlayAlongServer.RATE_LIMIT_PER_SEC);
	}

	@Test
	public void wipeFanoutKillsAllAliveClientsAndRecordsMetrics() throws Exception {
		startTestSession("HostOne", false);

		TestWsClient alice = connectRawClient();
		assertEquals("join_ack", joinClient(alice, "u-alice", "Alice", 1, "", "").type);
		TestWsClient bob = connectRawClient();
		assertEquals("join_ack", joinClient(bob, "u-bob", "Bob", 1, "", "").type);

		PlayAlongProtocol.WipeEvent wipe = server.triggerHostWipe(10, "Tengu");
		assertEquals(2, wipe.victims);
		assertEquals(2, server.getMetrics().getWipeVictims());
		assertEquals(0, server.getMetrics().getAliveClients());

		WireMessage aliceWipe = alice.awaitType(3, TimeUnit.SECONDS, "host_died");
		WireMessage bobWipe = bob.awaitType(3, TimeUnit.SECONDS, "host_died");
		assertNotNull(aliceWipe);
		assertNotNull(bobWipe);
		assertEquals(10, aliceWipe.depth);
		assertEquals("Tengu", aliceWipe.cause);
		assertEquals(2, aliceWipe.victims);
		assertTrue(aliceWipe.victimNames.contains("Alice"));
		assertTrue(aliceWipe.victimNames.contains("Bob"));
	}

	@Test
	public void reconnectWithinGracePreservesSlotAndAfterGraceFreesSlot() throws Exception {
		startTestSession("HostOne", true);
		server.setMaxClients(1);
		server.setReconnectGraceMs(300);

		// 1. Join Alice with clientToken, close connection, reconnect within 300ms using token (without joinCode)
		TestWsClient alice1 = connectRawClient();
		assertEquals("join_ack",
				joinClient(alice1, "u-alice", "Alice", 1, "ABCD", "tok-alice-9999").type);
		alice1.closeBlocking();
		Thread.sleep(80);

		TestWsClient alice2 = connectRawClient();
		WireMessage rejoin = new WireMessage();
		rejoin.type = "rejoin";
		rejoin.protocolVersion = PlayAlongProtocol.PROTOCOL_VERSION;
		rejoin.sessionId = "sess-test-1";
		rejoin.userId = "u-alice";
		rejoin.username = "Alice";
		rejoin.clientToken = "tok-alice-9999";
		alice2.send(PlayAlongProtocol.GSON.toJson(rejoin));
		WireMessage reAck = alice2.awaitType(3, TimeUnit.SECONDS, "join_ack");
		assertNotNull(reAck);
		assertEquals("join_ack", reAck.type);

		// 2. Disconnect Alice and wait past 300ms grace -> Bob can now take the single slot
		alice2.closeBlocking();
		Thread.sleep(450);

		TestWsClient bob = connectRawClient();
		WireMessage bobAck = joinClient(bob, "u-bob", "Bob", 1, "ABCD", "tok-bob-1111");
		assertNotNull(bobAck);
		assertEquals("join_ack", bobAck.type);
	}

	@Test
	public void chatMirrorSkipsSelfAndBroadcastsConfigToAll() throws Exception {
		startTestSession("HostOne", false);

		TestWsClient sender = connectRawClient();
		assertEquals("join_ack", joinClient(sender, "u-sender", "CasterJoe", 1, "", "").type);
		TestWsClient other = connectRawClient();
		assertEquals("join_ack", joinClient(other, "u-other", "ViewerSue", 1, "", "").type);

		sender.drainAll();
		other.drainAll();

		ChatCommandPayload spawn = new ChatCommandPayload();
		spawn.command = "spawn";
		spawn.username = "@CasterJoe";
		spawn.monster = "rat";
		spawn.rngSeed = 424242L;
		server.broadcastChatCommand(spawn);

		WireMessage otherMsg = other.awaitType(3, TimeUnit.SECONDS, "chat_command");
		assertNotNull(otherMsg);
		assertEquals("rat", otherMsg.chatCommand.monster);
		assertEquals(424242L, otherMsg.chatCommand.rngSeed);

		// Sender must NOT receive their own mirrored command
		Thread.sleep(150);
		for (WireMessage m : sender.drainAll()) {
			assertFalse("Sender should not receive own chat_command", "chat_command".equals(m.type));
		}

		// Config updates must broadcast to ALL clients including sender
		ChatCommandPayload cfg = new ChatCommandPayload();
		cfg.command = "spawn_scale_config";
		cfg.bundleJson = "{\"overall_power\":1.2}";
		server.broadcastChatCommand(cfg);

		WireMessage senderCfg = sender.awaitType(3, TimeUnit.SECONDS, "chat_command");
		WireMessage otherCfg = other.awaitType(3, TimeUnit.SECONDS, "chat_command");
		assertNotNull(senderCfg);
		assertNotNull(otherCfg);
		assertEquals("spawn_scale_config", senderCfg.chatCommand.command);
	}

	@Test
	public void streamControlWorksWithoutRaceSessionAndLogsHttpBridgeFailuresOncePerClass() throws Exception {
		// Explicitly no active race session, and lobby join code required for races
		server.endSession();
		server.setRequireJoinCode(true);
		server.getStreamControlProxy().setOverlayBaseUrl("http://127.0.0.1:1");

		TestWsClient client = connectRawClient();

		// 1. Invalid token fails locally without calling HTTP
		WireMessage badReq = new WireMessage();
		badReq.type = "stream_link_req";
		badReq.requestId = "req-bad";
		badReq.clientToken = "x";
		client.send(PlayAlongProtocol.GSON.toJson(badReq));

		WireMessage badResp = client.awaitType(3, TimeUnit.SECONDS, "stream_link_resp");
		assertNotNull(badResp);
		assertFalse(badResp.commandOk);
		assertEquals(0, server.getMetrics().getHttpBridgeFailures());

		// 2. Valid token works without race session or join code, records HTTP failures, warns once per class
		for (int i = 0; i < 2; i++) {
			WireMessage stateReq = new WireMessage();
			stateReq.type = "stream_state_req";
			stateReq.requestId = "req-state-" + i;
			stateReq.clientToken = "12345678-1234-1234-1234-1234567890ab";
			client.send(PlayAlongProtocol.GSON.toJson(stateReq));

			WireMessage stateResp = client.awaitType(5, TimeUnit.SECONDS, "stream_state_resp");
			assertNotNull(stateResp);
			assertEquals("req-state-" + i, stateResp.requestId);
			assertNotNull(stateResp.economy);
		}

		assertEquals(2, server.getMetrics().getHttpBridgeFailures());
		long httpClientStateWarnings = hostWarnings.stream()
				.filter(w -> w.startsWith("http_client_state:"))
				.count();
		assertEquals("Host should only be warned once per failure class", 1, httpClientStateWarnings);
	}

	private static final class TestWsClient extends WebSocketClient {
		private final BlockingQueue<WireMessage> inbox = new LinkedBlockingQueue<>();
		private final AtomicInteger closedCode = new AtomicInteger(-1);

		TestWsClient(URI serverUri) {
			super(serverUri);
			setConnectionLostTimeout(0);
		}

		@Override
		public void onOpen(ServerHandshake handshakedata) {}

		@Override
		public void onMessage(String message) {
			WireMessage msg = PlayAlongProtocol.GSON.fromJson(message, WireMessage.class);
			if (msg != null) {
				inbox.offer(msg);
			}
		}

		@Override
		public void onClose(int code, String reason, boolean remote) {
			closedCode.set(code);
		}

		@Override
		public void onError(Exception ex) {}

		WireMessage awaitType(long timeout, TimeUnit unit, String... types) throws InterruptedException {
			long deadline = System.nanoTime() + unit.toNanos(timeout);
			while (System.nanoTime() < deadline) {
				long remainingMs = Math.max(1L, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
				WireMessage msg = inbox.poll(remainingMs, TimeUnit.MILLISECONDS);
				if (msg == null) continue;
				for (String t : types) {
					if (t.equals(msg.type)) {
						return msg;
					}
				}
			}
			return null;
		}

		List<WireMessage> drainAll() {
			List<WireMessage> out = new ArrayList<>();
			inbox.drainTo(out);
			return out;
		}
	}
}
