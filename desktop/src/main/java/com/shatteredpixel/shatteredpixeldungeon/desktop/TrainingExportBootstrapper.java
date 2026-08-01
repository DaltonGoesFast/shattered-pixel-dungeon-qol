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

package com.shatteredpixel.shatteredpixeldungeon.desktop;

import com.shatteredpixel.shatteredpixeldungeon.utils.TrainingExport;

/**
 * Registers the training export listener and starts the async writer.
 */
public final class TrainingExportBootstrapper {

	private static TrainingExportWriter writer;

	private TrainingExportBootstrapper() {}

	public static void start() {
		if (writer != null) return;
		writer = new TrainingExportWriter();
		writer.start();
		TrainingExport.setListener(new TrainingExport.Listener() {
			@Override
			public void onRunStart(java.util.Map<String, Object> manifest) {
				writer.enqueueRunStart(manifest);
			}

			@Override
			public void onLevelEnter(java.util.Map<String, Object> levelSnapshot, int depth, int branch) {
				writer.enqueueLevel(levelSnapshot, depth, branch);
			}

			@Override
			public void onAction(java.util.Map<String, Object> step) {
				writer.enqueueStep(step);
			}

			@Override
			public void onRunEnd(java.util.Map<String, Object> outcome) {
				// Block until steps.jsonl is closed so Save & Quit releases the file lock.
				writer.enqueueRunEndAndWait(outcome, 5000);
			}
		});
		System.out.println("[TrainingExport] Enabled (gameplay training export)");
	}

	public static void stop() {
		TrainingExport.onSessionEnd();
		TrainingExport.clearListener();
		if (writer != null) {
			writer.stop();
			writer = null;
		}
	}
}
