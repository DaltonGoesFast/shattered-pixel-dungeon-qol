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

import com.google.gson.Gson;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Async writer for gameplay training export files.
 * Never blocks the game thread; drops steps with a throttled warning if the queue is full.
 */
public final class TrainingExportWriter {

	private static final Gson GSON = new Gson();
	private static final int QUEUE_CAPACITY = 256;
	private static final long WARN_INTERVAL_MS = 5000;

	private final BlockingQueue<Event> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
	private final AtomicInteger dropped = new AtomicInteger();
	private volatile long lastWarnMs = 0;
	private volatile boolean running = true;
	private Thread thread;

	private Path runDir;
	private BufferedWriter stepsWriter;

	public void start() {
		running = true;
		thread = new Thread(this::loop, "TrainingExportWriter");
		thread.setDaemon(true);
		thread.start();
		System.out.println("[TrainingExport] Writer started. Output root: " + exportRoot());
	}

	public void stop() {
		running = false;
		queue.offer(Event.poison());
		if (thread != null) {
			try {
				thread.join(5000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		closeStepsWriter();
		System.out.println("[TrainingExport] Writer stopped");
	}

	public void enqueueRunStart(Map<String, Object> manifest) {
		offer(Event.runStart(manifest));
	}

	public void enqueueLevel(Map<String, Object> level, int depth, int branch) {
		offer(Event.level(level, depth, branch));
	}

	public void enqueueStep(Map<String, Object> step) {
		offer(Event.step(step));
	}

	/** Enqueue run end and block until steps.jsonl is closed (or timeout). */
	public void enqueueRunEndAndWait(Map<String, Object> outcome, long timeoutMs) {
		CountDownLatch done = new CountDownLatch(1);
		Event e = Event.runEnd(outcome, done);
		// Never drop run-end — clear queue space if needed.
		while (!queue.offer(e)) {
			Event droppedEvt = queue.poll();
			if (droppedEvt != null && droppedEvt.type != Event.Type.POISON) {
				dropped.incrementAndGet();
				if (droppedEvt.done != null) droppedEvt.done.countDown();
			}
		}
		try {
			if (!done.await(timeoutMs, TimeUnit.MILLISECONDS)) {
				System.err.println("[TrainingExport] timed out waiting for steps.jsonl close");
				closeStepsWriter();
			}
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			closeStepsWriter();
		}
	}

	private void offer(Event e) {
		if (!queue.offer(e)) {
			// Drop oldest pending step-like event to make room
			Event droppedEvt = queue.poll();
			if (droppedEvt != null && droppedEvt.type != Event.Type.POISON) {
				dropped.incrementAndGet();
			}
			if (!queue.offer(e)) {
				dropped.incrementAndGet();
			}
			maybeWarn();
		}
	}

	private void maybeWarn() {
		long now = System.currentTimeMillis();
		if (now - lastWarnMs >= WARN_INTERVAL_MS) {
			lastWarnMs = now;
			System.err.println("[TrainingExport] dropped " + dropped.get() + " events (queue full)");
		}
	}

	private void loop() {
		while (running) {
			try {
				Event e = queue.poll(200, TimeUnit.MILLISECONDS);
				if (e == null) continue;
				if (e.type == Event.Type.POISON) break;
				handle(e);
			} catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
				break;
			} catch (Exception ex) {
				System.err.println("[TrainingExport] writer error: " + ex.getMessage());
				ex.printStackTrace();
			}
		}
		// Drain remaining
		Event e;
		while ((e = queue.poll()) != null) {
			if (e.type == Event.Type.POISON) break;
			try {
				handle(e);
			} catch (Exception ignored) {}
		}
		closeStepsWriter();
	}

	private void handle(Event e) throws IOException {
		try {
			switch (e.type) {
				case RUN_START:
					openRun(e.payload);
					break;
				case LEVEL:
					writeLevel(e.payload, e.depth, e.branch);
					break;
				case STEP:
					appendStep(e.payload);
					break;
				case RUN_END:
					writeOutcome(e.payload);
					closeStepsWriter();
					runDir = null;
					break;
				default:
					break;
			}
		} finally {
			if (e.done != null) e.done.countDown();
		}
	}

	private void openRun(Map<String, Object> manifest) throws IOException {
		closeStepsWriter();
		String runId = String.valueOf(manifest.get("run_id"));
		runDir = exportRoot().resolve(sanitize(runId));
		Files.createDirectories(runDir.resolve("levels"));
		writeJson(runDir.resolve("manifest.json"), manifest, true);
		stepsWriter = new BufferedWriter(new OutputStreamWriter(
				Files.newOutputStream(runDir.resolve("steps.jsonl"),
						StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE),
				StandardCharsets.UTF_8));
		System.out.println("[TrainingExport] Run folder: " + runDir.toAbsolutePath());
	}

	private void writeLevel(Map<String, Object> level, int depth, int branch) throws IOException {
		if (runDir == null) return;
		String name = String.format("d%02d_b%d.json", depth, branch);
		writeJson(runDir.resolve("levels").resolve(name), level, true);
	}

	private void appendStep(Map<String, Object> step) throws IOException {
		if (stepsWriter == null) return;
		stepsWriter.write(GSON.toJson(step));
		stepsWriter.newLine();
		stepsWriter.flush();
	}

	private void writeOutcome(Map<String, Object> outcome) throws IOException {
		if (runDir == null) return;
		Path out = runDir.resolve("outcome.json");
		writeJson(out, outcome, true);
		System.out.println("[TrainingExport] Run ended → " + out.toAbsolutePath() + " (steps.jsonl closed)");
	}

	private void writeJson(Path path, Object obj, boolean fsync) throws IOException {
		Files.createDirectories(path.getParent());
		byte[] data = (GSON.toJson(obj) + "\n").getBytes(StandardCharsets.UTF_8);
		Files.write(path, data);
		if (fsync) {
			try (var fos = new java.io.FileOutputStream(path.toFile(), true)) {
				fos.getFD().sync();
			} catch (Exception ignored) {}
		}
	}

	private void closeStepsWriter() {
		BufferedWriter w = stepsWriter;
		stepsWriter = null;
		if (w == null) return;
		try {
			w.flush();
			w.close();
		} catch (IOException ignored) {
		}
	}

	private static Path exportRoot() {
		String home = System.getProperty("user.home");
		return Paths.get(home, "Documents", "My Games", "SPD", "training_exports");
	}

	private static String sanitize(String s) {
		return s.replaceAll("[^a-zA-Z0-9._-]", "_");
	}

	private static final class Event {
		enum Type { RUN_START, LEVEL, STEP, RUN_END, POISON }

		final Type type;
		final Map<String, Object> payload;
		final int depth;
		final int branch;
		final CountDownLatch done;

		private Event(Type type, Map<String, Object> payload, int depth, int branch, CountDownLatch done) {
			this.type = type;
			this.payload = payload;
			this.depth = depth;
			this.branch = branch;
			this.done = done;
		}

		static Event runStart(Map<String, Object> m) { return new Event(Type.RUN_START, m, 0, 0, null); }
		static Event level(Map<String, Object> m, int d, int b) { return new Event(Type.LEVEL, m, d, b, null); }
		static Event step(Map<String, Object> m) { return new Event(Type.STEP, m, 0, 0, null); }
		static Event runEnd(Map<String, Object> m, CountDownLatch done) {
			return new Event(Type.RUN_END, m, 0, 0, done);
		}
		static Event poison() { return new Event(Type.POISON, null, 0, 0, null); }
	}
}
