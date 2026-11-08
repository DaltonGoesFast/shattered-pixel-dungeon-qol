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


import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net;
import com.watabou.noosa.Game;
import com.watabou.utils.Bundle;
import com.watabou.utils.DeviceCompat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLProtocolException;

/**
 * Update checks against {@code DaltonGoesFast/shattered-pixel-dungeon-qol} releases.
 * Versions come from the release tag ({@code v4.0.2-QoL-8}), not from an
 * "internal version number" line in the notes. Network failures are silent.
 */
public class GitHubUpdates extends UpdateService {

	static final String RELEASES_URL =
			"https://api.github.com/repos/DaltonGoesFast/shattered-pixel-dungeon-qol/releases?per_page=20";

	private static final Pattern DESC_PATTERN = Pattern.compile("(.*?)(\r\n|\n|\r)(\r\n|\n|\r)---", Pattern.DOTALL);
	private static final int MAX_DESC = 480;

	private static final Pattern MIN_ANDROID = Pattern.compile("Android .*\\(API ([0-9]*)\\)\\+ Devices", Pattern.CASE_INSENSITIVE);
	private static final Pattern MIN_IOS = Pattern.compile("iOS ([0-9]*)\\+ Devices", Pattern.CASE_INSENSITIVE);

	@Override
	public boolean supportsUpdatePrompts() {
		return true;
	}

	@Override
	public boolean supportsBetaChannel() {
		return true;
	}

	@Override
	public void checkForUpdate(boolean useMetered, boolean includeBetas, UpdateResultCallback callback) {

		if (!useMetered && !Game.platform.connectedToUnmeteredNetwork()){
			callback.onConnectionFailed();
			return;
		}

		Net.HttpRequest httpGet = new Net.HttpRequest(Net.HttpMethods.GET);
		httpGet.setUrl(RELEASES_URL);
		httpGet.setHeader("Accept", "application/vnd.github+json");
		httpGet.setHeader("User-Agent", "shattered-pixel-dungeon-qol");

		Gdx.net.sendHttpRequest(httpGet, new Net.HttpResponseListener() {
			@Override
			public void handleHttpResponse(Net.HttpResponse httpResponse) {
				try {
					Bundle root = Bundle.read(httpResponse.getResultAsStream());
					if (root == null || !root.contains(Bundle.DEFAULT_KEY)) {
						callback.onConnectionFailed();
						return;
					}
					Bundle[] releases = root.getBundleArray();
					if (releases == null) {
						callback.onConnectionFailed();
						return;
					}

					QoLReleaseVersion running = QoLReleaseVersion.parse(Game.version);
					QoLReleaseVersion bestVersion = null;
					Bundle bestRelease = null;

					for (Bundle release : releases) {
						if (release.getBoolean("draft")) continue;
						if (!includeBetas && release.getBoolean("prerelease")) continue;

						QoLReleaseVersion version = QoLReleaseVersion.parse(release.getString("tag_name"));
						if (version == null) {
							version = QoLReleaseVersion.parse(release.getString("name"));
						}
						if (version == null || (running != null && !version.isNewerThan(running))) continue;
						if (bestVersion != null && !version.isNewerThan(bestVersion)) continue;
						if (!compatibleWithThisDevice(release)) continue;

						bestVersion = version;
						bestRelease = release;
					}

					if (bestRelease == null || bestVersion == null) {
						callback.onNoUpdateFound();
						return;
					}

					AvailableUpdateData update = new AvailableUpdateData();
					String name = bestRelease.getString("name");
					String tag = bestRelease.getString("tag_name");
					update.versionName = name != null && name.length() > 0 ? name : tag;
					update.versionCode = bestVersion.qol;
					update.desc = summary(bestRelease.getString("body"));
					update.URL = bestRelease.getString("html_url");
					update.downloadURL = pickDownload(bestRelease, bestVersion);

					callback.onUpdateAvailable(update);
				} catch (Exception e) {
					callback.onConnectionFailed();
				}
			}

			@Override
			public void failed(Throwable t) {
				// Old devices that cannot speak GitHub's TLS still get no prompt and no error.
				if (t instanceof SSLProtocolException) {
					callback.onNoUpdateFound();
				} else {
					callback.onConnectionFailed();
				}
			}

			@Override
			public void cancelled() {
				callback.onConnectionFailed();
			}
		});
	}

	private static boolean compatibleWithThisDevice(Bundle release) {
		String body = release.getString("body");
		if (body == null || body.length() == 0) return true;
		if (DeviceCompat.isAndroid()) {
			Matcher minAndroid = MIN_ANDROID.matcher(body);
			if (minAndroid.find() && DeviceCompat.getPlatformVersion() < Integer.parseInt(minAndroid.group(1))) {
				return false;
			}
		} else if (DeviceCompat.isiOS()) {
			Matcher minIOS = MIN_IOS.matcher(body);
			if (minIOS.find() && DeviceCompat.getPlatformVersion() < Integer.parseInt(minIOS.group(1))) {
				return false;
			}
		}
		return true;
	}

	private static String summary(String body) {
		if (body == null) return null;
		String text = body.trim();
		if (text.length() == 0) return null;
		Matcher matcher = DESC_PATTERN.matcher(text);
		if (matcher.find()) {
			text = matcher.group(1).trim();
		}
		if (text.length() > MAX_DESC) {
			text = text.substring(0, MAX_DESC).trim() + "…";
		}
		return text.length() == 0 ? null : text;
	}

	private static String pickDownload(Bundle release, QoLReleaseVersion version) {
		if (!release.contains("assets")) return null;
		Bundle[] raw = release.getBundleArray("assets");
		if (raw == null || raw.length == 0) return null;
		QoLUpdateAssets.Asset[] assets = new QoLUpdateAssets.Asset[raw.length];
		for (int i = 0; i < raw.length; i++) {
			assets[i] = new QoLUpdateAssets.Asset(
					raw[i].getString("name"),
					raw[i].getString("browser_download_url"));
		}
		return QoLUpdateAssets.pickDownloadUrl(QoLUpdateAssets.current(), version.canonical(), assets);
	}

	@Override
	public void initializeUpdate(AvailableUpdateData update) {
		Game.platform.launchUpdate(update.URL, update.downloadURL);
	}

	@Override
	public boolean supportsReviews() {
		return false;
	}

	@Override
	public void initializeReview(ReviewResultCallback callback) {
		callback.onComplete();
	}

	@Override
	public void openReviewURI() {
		//does nothing
	}
}
