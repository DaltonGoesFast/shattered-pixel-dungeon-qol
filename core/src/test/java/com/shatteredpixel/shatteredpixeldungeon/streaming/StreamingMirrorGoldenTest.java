package com.shatteredpixel.shatteredpixeldungeon.streaming;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.HeadlessTestEnv;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.ChampionEnemy;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.ChatSpawned;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.Generator;
import com.shatteredpixel.shatteredpixeldungeon.items.Gold;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.shatteredpixel.shatteredpixeldungeon.levels.SewerLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.secret.SecretRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.SpecialRoom;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.ChatCommandPayload;
import com.watabou.utils.PathFinder;
import com.watabou.utils.Random;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Golden determinism tests ensuring shared {@code rngSeed} chat commands produce identical
 * spawns, champions, and drops across independent fixture levels.
 */
public class StreamingMirrorGoldenTest {

	private static final long FIXTURE_LEVEL_SEED = 112233445566L;

	@Before
	public void setUp() {
		HeadlessTestEnv.init();
		StreamingCommandHandler.setHeadlessFixtureMode(true);
		StreamingCommandHandler.clearLastRoll();
	}

	@After
	public void tearDown() {
		StreamingCommandHandler.setHeadlessFixtureMode(false);
		StreamingCommandHandler.clearLastRoll();
		Actor.clear();
		Dungeon.level = null;
		Dungeon.hero = null;
	}

	private void setupFixtureLevel(long seed) {
		Dungeon.seed = seed;
		Dungeon.depth = 1;
		Dungeon.branch = 0;
		Dungeon.challenges = 0;
		Dungeon.modifiers = 0;
		Dungeon.playAlongSessionId = "";

		Actor.clear();
		Actor.resetNextID();

		Random.pushGenerator(seed + 1);
		try {
			Scroll.initLabels();
			Potion.initColors();
			Ring.initGems();
			SpecialRoom.initForRun();
			SecretRoom.initForRun();
			Generator.fullReset();
		} finally {
			Random.resetGenerators();
		}

		GamesInProgress.selectedClass = HeroClass.WARRIOR;
		Dungeon.hero = new Hero();
		Dungeon.hero.live();
		HeroClass.WARRIOR.initHero(Dungeon.hero);

		Dungeon.level = new SewerLevel();
		Dungeon.level.create();
		Dungeon.hero.pos = Dungeon.level.entrance();
		PathFinder.setMapSize(Dungeon.level.width(), Dungeon.level.height());
		Actor.init();
	}

	private Mob findSingleChatSpawnedMob(Set<Mob> baseline) {
		Mob found = null;
		for (Mob m : Dungeon.level.mobs) {
			if (!baseline.contains(m) && m.buff(ChatSpawned.class) != null) {
				found = m;
			}
		}
		return found;
	}

	@Test
	public void fixedRngSeedProducesIdenticalSpawnAcrossFixtures() {
		long commandSeed = 777_888_999L;

		// Fixture A (Host side)
		setupFixtureLevel(FIXTURE_LEVEL_SEED);
		Set<Mob> baseA = new HashSet<>(Dungeon.level.mobs);
		StreamingCommandHandler.clearLastRoll();
		Random.pushGenerator(commandSeed);
		String errA;
		try {
			errA = StreamingCommandHandler.handleSpawn("shaman", "ViewerOne");
		} finally {
			Random.popGenerator();
		}
		assertNull("Expected successful spawn on fixture A, got: " + errA, errA);
		Mob mobA = findSingleChatSpawnedMob(baseA);
		assertNotNull(mobA);
		String rolledClass = StreamingCommandHandler.lastRolledClass;
		int posA = mobA.pos;
		int hpA = mobA.HP;
		int htA = mobA.HT;
		Class<?> classA = mobA.getClass();

		// Fixture B (Mirrored client side)
		setupFixtureLevel(FIXTURE_LEVEL_SEED);
		Set<Mob> baseB = new HashSet<>(Dungeon.level.mobs);
		ChatCommandPayload payload = new ChatCommandPayload();
		payload.command = "spawn";
		payload.username = "ViewerOne";
		payload.monster = "shaman";
		payload.rngSeed = commandSeed;
		payload.rolledClass = rolledClass;

		MirroredChatCommandExecutor executor = StreamingCommandHandler::executeMirroredChatCommand;
		executor.executeMirroredChatCommand(payload);

		Mob mobB = findSingleChatSpawnedMob(baseB);
		assertNotNull(mobB);
		assertEquals(classA, mobB.getClass());
		assertEquals(posA, mobB.pos);
		assertEquals(hpA, mobB.HP);
		assertEquals(htA, mobB.HT);
	}

	@Test
	public void fixedRngSeedProducesIdenticalChampionAndGoldDrop() {
		long champSeed = 314159265L;
		long goldSeed = 271828182L;

		// Fixture A
		setupFixtureLevel(FIXTURE_LEVEL_SEED);
		Set<Mob> baseA = new HashSet<>(Dungeon.level.mobs);
		StreamingCommandHandler.clearLastRoll();
		Random.pushGenerator(champSeed);
		String champErr;
		try {
			champErr = StreamingCommandHandler.handleSpawnChampion("crab", "DonorBob");
		} finally {
			Random.popGenerator();
		}
		assertNull(champErr);
		Mob champA = findSingleChatSpawnedMob(baseA);
		assertNotNull(champA);
		Set<ChampionEnemy> buffsA = champA.buffs(ChampionEnemy.class);
		assertEquals(1, buffsA.size());
		ChampionEnemy buffA = buffsA.iterator().next();
		String champMobCls = StreamingCommandHandler.lastRolledClass;
		String champBuffCls = StreamingCommandHandler.lastSecondaryClass;
		int champPosA = champA.pos;

		Random.pushGenerator(goldSeed);
		String goldErr;
		try {
			goldErr = StreamingCommandHandler.handleDropGold(42, "DonorBob");
		} finally {
			Random.popGenerator();
		}
		assertNull(goldErr);

		int goldPosA = -1;
		for (Heap h : Dungeon.level.heaps.valueList()) {
			for (Item it : h.items) {
				if (it instanceof Gold && it.quantity() == 42) {
					goldPosA = h.pos;
				}
			}
		}
		assertTrue("Expected 42 gold heap on fixture A", goldPosA >= 0);

		// Fixture B
		setupFixtureLevel(FIXTURE_LEVEL_SEED);
		Set<Mob> baseB = new HashSet<>(Dungeon.level.mobs);

		ChatCommandPayload champPayload = new ChatCommandPayload();
		champPayload.command = "champion";
		champPayload.username = "DonorBob";
		champPayload.monster = "crab";
		champPayload.rngSeed = champSeed;
		champPayload.rolledClass = champMobCls;
		champPayload.secondaryClass = champBuffCls;
		StreamingCommandHandler.executeMirroredChatCommand(champPayload);

		Mob champB = findSingleChatSpawnedMob(baseB);
		assertNotNull(champB);
		assertEquals(champA.getClass(), champB.getClass());
		assertEquals(champPosA, champB.pos);
		Set<ChampionEnemy> buffsB = champB.buffs(ChampionEnemy.class);
		assertEquals(1, buffsB.size());
		ChampionEnemy buffB = buffsB.iterator().next();
		assertEquals(buffA.getClass(), buffB.getClass());

		ChatCommandPayload goldPayload = new ChatCommandPayload();
		goldPayload.command = "gold";
		goldPayload.username = "DonorBob";
		goldPayload.amount = 42;
		goldPayload.rngSeed = goldSeed;
		StreamingCommandHandler.executeMirroredChatCommand(goldPayload);

		Heap goldHeapB = Dungeon.level.heaps.get(goldPosA);
		assertNotNull("Expected gold heap at identical cell on fixture B", goldHeapB);
		boolean foundGold = false;
		for (Item it : goldHeapB.items) {
			if (it instanceof Gold && it.quantity() == 42) {
				foundGold = true;
			}
		}
		assertTrue("Expected 42 gold at identical cell on fixture B", foundGold);
	}
}
