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

package com.shatteredpixel.shatteredpixeldungeon.utils;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bridge from game logic to the desktop training export writer.
 * Pattern mirrors {@link StreamingUI}: no-op unless enabled and a listener is registered.
 */
public final class TrainingExport {

	public interface Listener {
		void onRunStart(Map<String, Object> manifest);
		void onLevelEnter(Map<String, Object> levelSnapshot, int depth, int branch);
		void onAction(Map<String, Object> step);
		void onRunEnd(Map<String, Object> outcome);
	}

	private static volatile Listener listener;

	private static boolean runActive = false;
	private static String runId;
	private static int stepCounter = 0;

	/** Pending targeted item action awaiting cell select (merged into one step). */
	private static PendingItemAction pendingItem;

	/** Open step awaiting hero.ready() for outcome deltas. */
	private static PendingStep pendingStep;

	private TrainingExport() {}

	public static void setListener(Listener l) {
		listener = l;
	}

	public static void clearListener() {
		listener = null;
		pendingItem = null;
		pendingStep = null;
		runActive = false;
	}

	public static boolean isEnabled() {
		return SPDSettings.trainingExportEnabled() && listener != null;
	}

	public static void onRunStartIfNeeded() {
		if (!isEnabled() || runActive) return;
		if (Dungeon.hero == null) return;
		String seed;
		try {
			seed = Dungeon.customSeedText != null && !Dungeon.customSeedText.isEmpty()
					? Dungeon.customSeedText
					: DungeonSeed.convertToCode(Dungeon.seed);
		} catch (Exception e) {
			seed = String.valueOf(Dungeon.seed);
		}
		runId = "run_" + seed + "_" + GamesInProgress.curSlot + "_" + System.currentTimeMillis();
		stepCounter = 0;
		runActive = true;
		pendingItem = null;
		pendingStep = null;
		Listener l = listener;
		if (l != null) {
			l.onRunStart(TrainingSnapshot.buildManifest(runId));
		}
	}

	public static void onLevelEnter() {
		if (!isEnabled()) return;
		onRunStartIfNeeded();
		if (!runActive) return;
		Listener l = listener;
		if (l != null) {
			l.onLevelEnter(TrainingSnapshot.buildLevel(), Dungeon.depth, Dungeon.branch);
		}
	}

	public static void logGameplayAction(Map<String, Object> action) {
		logGameplayAction(action, null);
	}

	public static void logGameplayAction(Map<String, Object> action, String sceneOverride) {
		if (!isEnabled() || !runActive || action == null) return;
		// Emit previous step if somehow still open (should be finalized by onHeroReady)
		emitPendingStep(false);

		Hero hero = Dungeon.hero;
		int hp = hero == null ? 0 : hero.HP;
		int exp = hero == null ? 0 : hero.exp;
		int pos = hero == null ? -1 : hero.pos;

		Map<String, Object> step = new LinkedHashMap<>();
		step.put("step", stepCounter++);
		step.put("turn", TrainingSnapshot.turnNow());
		step.put("depth", Dungeon.depth);
		step.put("branch", Dungeon.branch);
		String scene = sceneOverride != null ? sceneOverride : TrainingSnapshot.sceneId();
		step.put("scene", scene);
		step.put("action", action);
		step.put("obs", TrainingSnapshot.buildObservation());

		pendingStep = new PendingStep(step, hp, Dungeon.gold, exp, Dungeon.depth, pos);

		// Shop/alchemy don't go through Hero.ready — emit immediately with current deltas
		if (!"game".equals(scene)) {
			emitPendingStep(false);
		}
	}

	/** Called from Hero.ready() to fill outcome deltas and emit the pending step. */
	public static void onHeroReady() {
		if (!isEnabled()) return;
		emitPendingStep(false);
	}

	public static void onHeroDied(Object cause) {
		if (!isEnabled()) return;
		emitPendingStep(true);
		String causeClass = cause == null ? null : cause.getClass().getSimpleName();
		endRun(causeClass);
	}

	public static void onRunWon() {
		if (!isEnabled()) return;
		emitPendingStep(false);
		endRun(null);
	}

	/** Best-effort finalize on game exit / window close. */
	public static void onSessionEnd() {
		if (!isEnabled() || !runActive) return;
		emitPendingStep(false);
		endRun(null);
	}

	private static void endRun(String deathCauseClass) {
		if (!runActive) return;
		Listener l = listener;
		if (l != null) {
			l.onRunEnd(TrainingSnapshot.buildRunOutcome(deathCauseClass));
		}
		runActive = false;
		pendingItem = null;
		pendingStep = null;
	}

	private static void emitPendingStep(boolean died) {
		if (pendingStep == null) return;
		Map<String, Object> outcome = TrainingSnapshot.buildOutcomeDeltas(
				pendingStep.hpBefore,
				pendingStep.goldBefore,
				pendingStep.expBefore,
				pendingStep.depthBefore,
				pendingStep.posBefore,
				died
		);
		pendingStep.step.put("outcome", outcome);
		Listener l = listener;
		if (l != null) {
			l.onAction(pendingStep.step);
		}
		pendingStep = null;
	}

	// ---- Action helpers ----

	public static void logHeroAction(HeroAction action) {
		if (!isEnabled() || action == null) return;
		Map<String, Object> a;
		if (action instanceof HeroAction.Move) {
			a = TrainingSnapshot.actionCell("move", action.dst, null);
		} else if (action instanceof HeroAction.Attack) {
			Char ch = ((HeroAction.Attack) action).target;
			a = TrainingSnapshot.actionCell("attack", ch == null ? action.dst : ch.pos,
					ch == null ? null : ch.id());
		} else if (action instanceof HeroAction.Interact) {
			Char ch = ((HeroAction.Interact) action).ch;
			a = TrainingSnapshot.actionCell("interact", ch == null ? -1 : ch.pos,
					ch == null ? null : ch.id());
		} else if (action instanceof HeroAction.PickUp) {
			a = TrainingSnapshot.actionCell("pick_up", action.dst, null);
		} else if (action instanceof HeroAction.OpenChest) {
			a = TrainingSnapshot.actionCell("open_chest", action.dst, null);
		} else if (action instanceof HeroAction.Buy) {
			a = TrainingSnapshot.actionCell("buy", action.dst, null);
		} else if (action instanceof HeroAction.Unlock) {
			a = TrainingSnapshot.actionCell("unlock", action.dst, null);
		} else if (action instanceof HeroAction.Mine) {
			a = TrainingSnapshot.actionCell("mine", action.dst, null);
		} else if (action instanceof HeroAction.LvlTransition) {
			a = TrainingSnapshot.actionCell("stairs", action.dst, null);
		} else if (action instanceof HeroAction.Alchemy) {
			a = TrainingSnapshot.actionCell("alchemy", action.dst, null);
		} else {
			a = TrainingSnapshot.actionCell(action.getClass().getSimpleName(), action.dst, null);
		}
		logGameplayAction(a);
	}

	public static void onItemExecute(Item item, String itemAction) {
		if (!isEnabled() || item == null) return;
		String cls = item.getClass().getSimpleName();
		String actionName = itemAction == null ? "DEFAULT" : itemAction;
		boolean targeting = item.usesTargeting
				|| Item.AC_THROW.equals(actionName)
				|| Wand.AC_ZAP.equals(actionName);
		if (targeting) {
			pendingItem = new PendingItemAction(cls, actionName, typeFromItemAction(actionName));
			return;
		}
		logGameplayAction(TrainingSnapshot.actionItem(
				typeFromItemAction(actionName), cls, actionName, null));
	}

	/** Complete a pending targeted item action with the chosen cell. */
	public static void onItemTargetSelected(Integer cell) {
		if (!isEnabled()) return;
		if (pendingItem == null) return;
		if (cell == null) {
			clearPendingItem();
			return;
		}
		PendingItemAction p = pendingItem;
		pendingItem = null;
		logGameplayAction(TrainingSnapshot.actionItem(p.type, p.itemClass, p.itemAction, cell));
	}

	public static void clearPendingItem() {
		pendingItem = null;
	}

	public static boolean hasPendingItem() {
		return pendingItem != null;
	}

	public static void logWait() {
		logGameplayAction(TrainingSnapshot.actionSimple("wait", "wait"));
	}

	public static void logRest() {
		logGameplayAction(TrainingSnapshot.actionSimple("wait", "rest"));
	}

	public static void logResume() {
		logGameplayAction(TrainingSnapshot.actionSimple("resume", "resume"));
	}

	public static void logCombo(String className) {
		logGameplayAction(TrainingSnapshot.actionCombo(className));
	}

	public static void logShop(String type, String itemClass) {
		logGameplayAction(TrainingSnapshot.actionShop(type, itemClass), "shop");
	}

	public static void logAlchemy(String recipeId) {
		logGameplayAction(TrainingSnapshot.actionAlchemy(recipeId), "alchemy");
	}

	private static String typeFromItemAction(String itemAction) {
		if (itemAction == null) return "use";
		String lower = itemAction.toLowerCase();
		if (lower.contains("zap")) return "zap";
		if (lower.contains("throw")) return "throw";
		if (lower.contains("drink")) return "drink";
		if (lower.contains("eat")) return "eat";
		if (lower.contains("read")) return "read";
		if (lower.contains("equip")) return "equip";
		if (lower.contains("unequip")) return "unequip";
		if (lower.contains("drop")) return "drop";
		return lower.replace(' ', '_');
	}

	private static final class PendingItemAction {
		final String itemClass;
		final String itemAction;
		final String type;

		PendingItemAction(String itemClass, String itemAction, String type) {
			this.itemClass = itemClass;
			this.itemAction = itemAction;
			this.type = type;
		}
	}

	private static final class PendingStep {
		final Map<String, Object> step;
		final int hpBefore;
		final int goldBefore;
		final int expBefore;
		final int depthBefore;
		final int posBefore;

		PendingStep(Map<String, Object> step, int hpBefore, int goldBefore, int expBefore,
					int depthBefore, int posBefore) {
			this.step = step;
			this.hpBefore = hpBefore;
			this.goldBefore = goldBefore;
			this.expBefore = expBefore;
			this.depthBefore = depthBefore;
			this.posBefore = posBefore;
		}
	}
}
