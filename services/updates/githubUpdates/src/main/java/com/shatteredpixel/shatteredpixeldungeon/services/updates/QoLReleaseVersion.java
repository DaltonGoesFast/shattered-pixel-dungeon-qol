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

package com.shatteredpixel.shatteredpixeldungeon.services.updates;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Version tuple for QoL releases.
 * <p>
 * Accepts a GitHub tag such as {@code v4.0.2-QoL-8} or the running
 * {@code Game.version} ({@code 4.0.2-QoL-8}, or {@code 4.0.2-QoL-8-INDEV}).
 * Comparison is major, then minor, then patch, then the QoL counter.
 * A missing QoL counter is 0, so {@code v4.0.3} is newer than {@code 4.0.2-QoL-8}
 * and {@code v4.0.2} is not newer than {@code 4.0.2-QoL-8}.
 * Suffixes after the QoL counter ({@code -INDEV}, {@code -BETA}, {@code -RC}) are ignored.
 */
public final class QoLReleaseVersion implements Comparable<QoLReleaseVersion> {

	private static final Pattern PATTERN = Pattern.compile(
			"(?i)^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-qol-(\\d+))?");

	public final int major;
	public final int minor;
	public final int patch;
	public final int qol;

	private QoLReleaseVersion(int major, int minor, int patch, int qol) {
		this.major = major;
		this.minor = minor;
		this.patch = patch;
		this.qol = qol;
	}

	public static QoLReleaseVersion parse(String raw) {
		if (raw == null) return null;
		Matcher matcher = PATTERN.matcher(raw.trim());
		if (!matcher.find()) return null;
		int qol = matcher.group(4) == null ? 0 : Integer.parseInt(matcher.group(4));
		return new QoLReleaseVersion(
				Integer.parseInt(matcher.group(1)),
				Integer.parseInt(matcher.group(2)),
				Integer.parseInt(matcher.group(3)),
				qol);
	}

	/** {@code 4.0.2-QoL-8}, or {@code 4.0.2} when the release has no QoL counter. */
	public String canonical() {
		String base = major + "." + minor + "." + patch;
		return qol > 0 ? base + "-QoL-" + qol : base;
	}

	public boolean isNewerThan(QoLReleaseVersion other) {
		return other != null && compareTo(other) > 0;
	}

	@Override
	public int compareTo(QoLReleaseVersion other) {
		int c = Integer.compare(major, other.major);
		if (c != 0) return c;
		c = Integer.compare(minor, other.minor);
		if (c != 0) return c;
		c = Integer.compare(patch, other.patch);
		if (c != 0) return c;
		return Integer.compare(qol, other.qol);
	}
}
