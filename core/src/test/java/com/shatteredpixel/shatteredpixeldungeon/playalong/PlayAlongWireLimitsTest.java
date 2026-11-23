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

import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.ChatCommandPayload;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.CloseCopy;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.GhostState;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.TombstoneState;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.WireMessage;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A bomb/enemy raid used to disconnect Play Along: one frame per command, an
 * unbounded tombstone list, and a client that blamed the network for any close.
 */
public class PlayAlongWireLimitsTest {

	@Test
	public void busyWorldStateFitsHostFrame() {
		WireMessage world = new WireMessage();
		world.type = "world_state";
		world.sessionId = "session-under-raid";
		world.hostGhost = ghost("host", "Dalton", true);
		List<GhostState> ghosts = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			ghosts.add(ghost("user-" + i, "Adventurer_" + i, false));
		}
		world.ghosts = ghosts;
		List<TombstoneState> tombs = new ArrayList<>();
		for (int i = 0; i < 500; i++) {
			TombstoneState t = new TombstoneState();
			t.userId = "dead-" + i;
			t.username = "Adventurer_" + i;
			t.heroClass = "WARRIOR";
			t.depth = 1 + (i % 25);
			t.branch = 0;
			t.pos = i;
			t.cause = "Bomb";
			tombs.add(t);
		}
		world.tombstones = tombs;

		String json = PlayAlongProtocol.fitWorldStateJson(world);
		assertNotNull(json);
		assertTrue(json.length() <= PlayAlongProtocol.MAX_HOST_FRAME_CHARS);
		assertTrue(json.length() <= PlayAlongProtocol.MAX_CLIENT_INBOUND_CHARS);
		assertEquals(PlayAlongProtocol.MAX_WIRE_TOMBSTONES, world.tombstones.size());
		assertEquals(100, world.ghosts.size());
	}

	@Test
	public void playerStateStaysUnderRelayCap() {
		WireMessage msg = new WireMessage();
		msg.type = "player_state";
		msg.userId = "0123456789abcdef0123456789abcdef";
		msg.heroClass = "HUNTRESS";
		msg.armorTier = 5;
		msg.depth = 24;
		msg.branch = 2;
		msg.pos = 1200;
		msg.facingLeft = true;
		msg.hpPct = 0.42f;
		msg.action = "ATTACK";
		msg.catchup = true;
		String json = PlayAlongProtocol.GSON.toJson(msg);
		assertTrue("player frame " + json.length(), json.length() <= PlayAlongProtocol.MAX_PACKET_LENGTH);
	}

	@Test
	public void chatBurstPacksIntoOneFrame() {
		ArrayDeque<ChatCommandPayload> queue = new ArrayDeque<>();
		for (int i = 0; i < 80; i++) {
			ChatCommandPayload p = new ChatCommandPayload();
			p.command = i % 2 == 0 ? "bomb" : "spawn";
			p.username = "chatter" + i;
			p.monster = i % 2 == 0 ? null : "bat";
			p.rngSeed = 1000L + i;
			queue.add(p);
		}
		List<ChatCommandPayload> batch = PlayAlongProtocol.takeChatBatch(
				queue, "session", PlayAlongProtocol.MAX_CHAT_BATCH, PlayAlongProtocol.MAX_HOST_FRAME_CHARS);
		assertEquals(PlayAlongProtocol.MAX_CHAT_BATCH, batch.size());
		assertEquals(80 - PlayAlongProtocol.MAX_CHAT_BATCH, queue.size());
		String json = PlayAlongProtocol.chatBatchJson("session", batch);
		assertTrue(json.length() <= PlayAlongProtocol.MAX_HOST_FRAME_CHARS);
		assertTrue(json.contains("chat_command_batch"));

		// A single oversized command is still taken so the queue cannot stick.
		ChatCommandPayload huge = new ChatCommandPayload();
		huge.command = "ring_of_wealth";
		huge.username = "donor";
		StringBuilder bundle = new StringBuilder();
		for (int i = 0; i < PlayAlongProtocol.MAX_HOST_FRAME_CHARS + 50; i++) bundle.append('x');
		huge.bundleJson = bundle.toString();
		queue.clear();
		queue.add(huge);
		List<ChatCommandPayload> one = PlayAlongProtocol.takeChatBatch(queue, "session", 4, 64);
		assertEquals(1, one.size());
		assertTrue(queue.isEmpty());
	}

	@Test
	public void closeCopyBlamesNetworkOnlyBeforeOpen() {
		assertEquals(CloseCopy.NETWORK_BLOCKED, PlayAlongProtocol.closeCopy(false, false));
		assertEquals(CloseCopy.NETWORK_LOST, PlayAlongProtocol.closeCopy(false, true));
		assertEquals(CloseCopy.DESYNC, PlayAlongProtocol.closeCopy(true, false));
		assertEquals(CloseCopy.DESYNC_FINAL, PlayAlongProtocol.closeCopy(true, true));

		assertEquals("", PlayAlongProtocol.closeSuffix(null));
		assertEquals("", PlayAlongProtocol.closeSuffix("  \n"));
		assertEquals(": message too large", PlayAlongProtocol.closeSuffix("message too large"));
		assertTrue(PlayAlongProtocol.closeSuffix("x\ny").startsWith(": x y"));
		assertNull(PlayAlongProtocol.fitWorldStateJson(null));
	}

	private static GhostState ghost(String id, String name, boolean host) {
		GhostState g = new GhostState();
		g.userId = id;
		g.username = name;
		g.heroClass = "WARRIOR";
		g.armorTier = 4;
		g.depth = 16;
		g.branch = 0;
		g.pos = 400;
		g.facingLeft = false;
		g.hpPct = 0.8f;
		g.action = "MOVE";
		g.isHost = host;
		g.catchup = false;
		g.lastUpdateMs = 1_700_000_000_000L;
		return g;
	}
}
