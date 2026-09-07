/*
 * Pixel Dungeon / Shattered Pixel Dungeon QoL fork
 * Live chat-spawn combat / paralysis / XP knobs (pushed from points-config via WebSocket).
 */

package com.shatteredpixel.shatteredpixeldungeon.utils;

/**
 * In-memory spawn scaling config. Defaults match the "Default" preset (not Classic).
 * Updated at runtime via WebSocket {@code spawn_scale_config}.
 */
public final class SpawnScaleConfig {

	public static final int EARLY_NATIVE_MAX = 15; // ghoul and before
	public static final int LATE_NATIVE_MIN = 16;  // elemental / warlock / monk+

	// --- Combat (Default) ---
	public static volatile float overallPower = 1f;
	public static volatile float earlyHpMin = 0.25f;
	public static volatile float lateHpMin = 0.15f;
	public static volatile float scorpioHpMin = 0.15f;
	public static volatile float earlySewersDmgMult = 1.00f;
	public static volatile float lateSewersDmgMult = 0.35f;
	public static volatile float earlySewersDmgFloor = 0.05f;
	public static volatile float lateSewersDmgFloor = 0.08f;
	public static volatile float prisonPlusDmgFloor = 0.15f;
	public static volatile float earlyDrMult = 0.70f;
	public static volatile float lateDrMult = 0.40f;
	public static volatile float drFloor = 0.15f;
	/** Never deal 0 damage to hero from chat-spawned scaled hits. */
	public static volatile boolean minOneDamageVsHero = true;
	/** Classic sewers hard cap at 1 damage vs hero (off in Default). */
	public static volatile boolean sewersOneDamageCap = false;
	public static volatile float eyeGazeHeroHtFrac = 0.75f;

	// --- Paralysis (Default = gap-based chapter distance) ---
	public static volatile boolean paralysisEnabled = true;
	/** Stun turns when mob home chapter is 1 / 2 / 3 / 4 chapters later than current. */
	public static volatile int paralysisTurnsGap1 = 1;
	public static volatile int paralysisTurnsGap2 = 2;
	public static volatile int paralysisTurnsGap3 = 3;
	public static volatile int paralysisTurnsGap4 = 4;

	// --- XP by hero chapter (Default = Classic) ---
	public static volatile int xpSewers = 2;
	public static volatile int xpPrison = 5;
	public static volatile int xpCaves = 8;
	public static volatile int xpCity = 11;
	public static volatile int xpHalls = 12;

	private SpawnScaleConfig() {}

	public static boolean isEarlyNative(int nativeDepth) {
		return nativeDepth <= EARLY_NATIVE_MAX;
	}

	public static float hpMinFor(int nativeDepth, boolean scorpio) {
		if (scorpio) return scorpioHpMin;
		return isEarlyNative(nativeDepth) ? earlyHpMin : lateHpMin;
	}

	public static float sewersDmgMultFor(int nativeDepth) {
		return isEarlyNative(nativeDepth) ? earlySewersDmgMult : lateSewersDmgMult;
	}

	public static float sewersDmgFloorFor(int nativeDepth) {
		return isEarlyNative(nativeDepth) ? earlySewersDmgFloor : lateSewersDmgFloor;
	}

	public static float drMultFor(int nativeDepth) {
		return isEarlyNative(nativeDepth) ? earlyDrMult : lateDrMult;
	}

	/**
	 * Turns of spawn-in paralysis from chapter gap (nativeRegion - currentRegion).
	 * Gap 1 = one chapter early (e.g. halls mob in city); gap 4 = halls in sewers.
	 */
	public static int paralysisTurnsForGap(int gap) {
		switch (gap) {
			case 1: return paralysisTurnsGap1;
			case 2: return paralysisTurnsGap2;
			case 3: return paralysisTurnsGap3;
			case 4: return paralysisTurnsGap4;
			default: return 0;
		}
	}

	public static int chatSpawnXpForRegion(int region) {
		switch (region) {
			case 0: return xpSewers;
			case 1: return xpPrison;
			case 2: return xpCaves;
			case 3: return xpCity;
			case 4: return xpHalls;
			default: return xpSewers;
		}
	}

	/** Apply Classic preset (pre-live-scaling constants). */
	public static void applyClassic() {
		overallPower = 1f;
		earlyHpMin = 0.25f;
		lateHpMin = 0.25f;
		scorpioHpMin = 0.15f;
		earlySewersDmgMult = 0.25f;
		lateSewersDmgMult = 0.25f;
		earlySewersDmgFloor = 0.05f;
		lateSewersDmgFloor = 0.05f;
		prisonPlusDmgFloor = 0.15f;
		earlyDrMult = 0.70f;
		lateDrMult = 0.70f;
		drFloor = 0.15f;
		minOneDamageVsHero = true;
		sewersOneDamageCap = true;
		eyeGazeHeroHtFrac = 0.75f;
		paralysisEnabled = true;
		paralysisTurnsGap1 = 0;
		paralysisTurnsGap2 = 1;
		paralysisTurnsGap3 = 2;
		paralysisTurnsGap4 = 3;
		xpSewers = 2;
		xpPrison = 5;
		xpCaves = 8;
		xpCity = 11;
		xpHalls = 12;
	}

	/** Apply Default preset (intended sewers feel). */
	public static void applyDefault() {
		overallPower = 1f;
		earlyHpMin = 0.25f;
		lateHpMin = 0.15f;
		scorpioHpMin = 0.15f;
		earlySewersDmgMult = 1.00f;
		lateSewersDmgMult = 0.35f;
		earlySewersDmgFloor = 0.05f;
		lateSewersDmgFloor = 0.08f;
		prisonPlusDmgFloor = 0.15f;
		earlyDrMult = 0.70f;
		lateDrMult = 0.40f;
		drFloor = 0.15f;
		minOneDamageVsHero = true;
		sewersOneDamageCap = false;
		eyeGazeHeroHtFrac = 0.75f;
		paralysisEnabled = true;
		paralysisTurnsGap1 = 1;
		paralysisTurnsGap2 = 2;
		paralysisTurnsGap3 = 3;
		paralysisTurnsGap4 = 4;
		xpSewers = 2;
		xpPrison = 5;
		xpCaves = 8;
		xpCity = 11;
		xpHalls = 12;
	}

	/** Apply Meaner preset (tougher summons). */
	public static void applyMeaner() {
		applyDefault();
		overallPower = 1.25f;
		earlySewersDmgMult = 1.15f;
		lateSewersDmgMult = 0.50f;
		lateHpMin = 0.20f;
		lateDrMult = 0.50f;
	}
}
