package com.shatteredpixel.shatteredpixeldungeon.playalong;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.HeadlessTestEnv;
import com.shatteredpixel.shatteredpixeldungeon.Statistics;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.items.Generator;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.shatteredpixel.shatteredpixeldungeon.levels.SewerLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.secret.SecretRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.SpecialRoom;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.GhostState;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.SessionInfo;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.TombstoneState;
import com.watabou.utils.PathFinder;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end client smoke test simulating Android client connect, join, world_state receipt,
 * and read-only Spirit Cam floor follow without mutating slot 99 progression.
 */
public class PlayAlongAndroidSmokeTest {

	private PlayAlongServer server;
	private int port;

	@Before
	public void setUp() throws Exception {
		HeadlessTestEnv.init();
		PlayAlongService.disconnect();
		PlayAlongService.setClientAddressOverride(null);

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
		}) {
			@Override
			public void onStart() {
				super.onStart();
				started.countDown();
			}
		};
		server.start();
		assertTrue(started.await(5, TimeUnit.SECONDS));
		port = server.getPort();
	}

	@After
	public void tearDown() {
		PlayAlongService.disconnect();
		PlayAlongService.setClientAddressOverride(null);
		if (server != null) {
			server.endSession();
			server.stopServer();
		}
		Actor.clear();
		Dungeon.level = null;
		Dungeon.hero = null;
		Dungeon.generatedLevels.clear();
	}

	private void initClientFloorOne(long seed) {
		Dungeon.seed = seed;
		Dungeon.depth = 1;
		Dungeon.branch = 0;
		Dungeon.challenges = 0;
		Dungeon.modifiers = 0;
		Dungeon.generatedLevels.clear();
		Statistics.reset();
		Statistics.deepestFloor = 1;

		Actor.clear();
		Actor.resetNextID();
		Scroll.initLabels();
		Potion.initColors();
		Ring.initGems();
		SpecialRoom.initForRun();
		SecretRoom.initForRun();
		Generator.fullReset();

		Dungeon.hero = new Hero();
		Dungeon.hero.live();
		HeroClass.MAGE.initHero(Dungeon.hero);

		Dungeon.level = new SewerLevel();
		Dungeon.level.create();
		Dungeon.generatedLevels.add(1);
		Dungeon.hero.pos = Dungeon.level.entrance();
		PathFinder.setMapSize(Dungeon.level.width(), Dungeon.level.height());
		Actor.init();
	}

	@Test
	public void clientConnectsJoinsReceivesWorldStateAndEntersReadOnlySpiritCam() throws Exception {
		server.setRequireJoinCode(true);
		server.setLobbyJoinCode("RACE");

		SessionInfo session = new SessionInfo();
		session.active = true;
		session.sessionId = "android-smoke-session";
		session.version = "";
		session.hostUsername = "StreamLeader";
		session.joinCodeRequired = true;
		session.seed = 44556677L;
		session.hostDepth = 3;
		session.hostPos = 42;
		session.hostHp = 24;
		session.hostHt = 24;

		GhostState hostGhost = new GhostState();
		hostGhost.userId = "host";
		hostGhost.username = "StreamLeader";
		hostGhost.heroClass = "ROGUE";
		hostGhost.depth = 3;
		hostGhost.branch = 0;
		hostGhost.pos = 42;
		hostGhost.hpPct = 1f;
		hostGhost.isHost = true;
		server.startSession(session, hostGhost);

		PlayAlongService.setClientAddressOverride("127.0.0.1:" + port);
		PlayAlongService.startClientRun(session, HeroClass.MAGE, "MobileRacer", "RACE");

		// Wait until client receives join_ack + world_state
		long deadline = System.currentTimeMillis() + 5000;
		while (System.currentTimeMillis() < deadline
				&& (PlayAlongService.getHostDepth() != 3 || PlayAlongService.getGhostStore().getHostGhost() == null)) {
			Thread.sleep(25);
		}

		assertEquals(GamesInProgress.PLAY_ALONG_SLOT, GamesInProgress.curSlot);
		assertTrue(PlayAlongService.isClient());
		assertEquals("StreamLeader", PlayAlongService.getHostDisplayName());
		assertEquals(3, PlayAlongService.getHostDepth());
		assertNotNull(PlayAlongService.getGhostStore().getHostGhost());
		assertEquals("StreamLeader", PlayAlongService.getGhostStore().getHostGhost().username);

		// Simulate client floor 1 state before dying and entering Spirit Cam
		initClientFloorOne(session.seed);
		PlayAlongService.clearClientJoinPending();
		assertEquals(Collections.singletonList(1), Dungeon.generatedLevels);

		PlayAlongService.enterSpiritCam();
		assertTrue(PlayAlongService.isSpectating());

		// Follow host to unvisited Depth 3 via read-only spectator return path
		PlayAlongService.spectatorReturnTo(
				PlayAlongService.getHostDepth(),
				PlayAlongService.getHostBranch(),
				PlayAlongService.getHostPos());

		assertEquals(3, Dungeon.depth);
		assertEquals(0, Dungeon.branch);
		assertNotNull(Dungeon.level);

		// Floor must be magic-mapped for Spirit Cam viewing
		boolean hasMappedDiscoverable = false;
		for (int i = 0; i < Dungeon.level.length(); i++) {
			if (Dungeon.level.discoverable[i]) {
				assertTrue("Expected discoverable tile to be magic-mapped", Dungeon.level.mapped[i]);
				hasMappedDiscoverable = true;
			}
		}
		assertTrue(hasMappedDiscoverable);

		// Read-only invariant: slot 99 progression globals were NOT mutated by viewing Depth 3
		assertEquals(Collections.singletonList(1), Dungeon.generatedLevels);
		assertFalse(Dungeon.levelHasBeenGenerated(3, 0));
		assertEquals(1, Statistics.deepestFloor);
		assertFalse("Slot 99 depth3.dat must not be written by Spirit Cam",
				HeadlessTestEnv.getTempStorageDir().resolve(GamesInProgress.depthFile(99, 3, 0)).toFile().exists());

		// Dungeon.saveAll() must be a safe no-op while spectating
		Dungeon.saveAll();
		assertFalse("Dungeon.saveAll() while spectating must not create slot 99 depth3.dat",
				HeadlessTestEnv.getTempStorageDir().resolve(GamesInProgress.depthFile(99, 3, 0)).toFile().exists());
	}
}
