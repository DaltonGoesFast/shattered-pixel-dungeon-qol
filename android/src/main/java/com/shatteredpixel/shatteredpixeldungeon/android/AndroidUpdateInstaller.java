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

package com.shatteredpixel.shatteredpixeldungeon.android;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import com.watabou.noosa.Game;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Downloads a release APK into the app cache and hands it to the system installer.
 * If install permission, the download, or the installer intent fails, the release
 * page is opened in the browser instead.
 */
public final class AndroidUpdateInstaller {

	public static final int REQUEST_INSTALL_PERMISSION = 4408;

	private static String pendingPage;
	private static String pendingDownload;
	private static boolean awaitingPermission;

	private AndroidUpdateInstaller() {}

	public static void launch(String pageURL, String downloadURL) {
		Activity activity = AndroidLauncher.instance;
		if (activity == null) {
			open(pageURL != null ? pageURL : downloadURL);
			return;
		}
		if (!isApkUrl(downloadURL)) {
			open(pageURL != null ? pageURL : downloadURL);
			return;
		}
		activity.runOnUiThread(() -> {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
					&& !activity.getPackageManager().canRequestPackageInstalls()) {
				pendingPage = pageURL;
				pendingDownload = downloadURL;
				awaitingPermission = true;
				try {
					Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
							Uri.parse("package:" + activity.getPackageName()));
					activity.startActivityForResult(settings, REQUEST_INSTALL_PERMISSION);
				} catch (Exception e) {
					awaitingPermission = false;
					pendingPage = null;
					pendingDownload = null;
					Toast.makeText(activity, "Install permission unavailable. Opening the release page.", Toast.LENGTH_LONG).show();
					open(pageURL);
				}
				return;
			}
			downloadAndInstall(activity, pageURL, downloadURL);
		});
	}

	public static void onActivityResult(int requestCode) {
		if (requestCode != REQUEST_INSTALL_PERMISSION || !awaitingPermission) return;
		Activity activity = AndroidLauncher.instance;
		boolean granted = activity != null && (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
				|| activity.getPackageManager().canRequestPackageInstalls());
		finishPermission(granted);
	}

	/** Some devices return from the unknown-sources screen without onActivityResult. */
	public static void onResume() {
		if (!awaitingPermission) return;
		Activity activity = AndroidLauncher.instance;
		if (activity == null) return;
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
				|| activity.getPackageManager().canRequestPackageInstalls()) {
			finishPermission(true);
		}
	}

	private static void finishPermission(boolean granted) {
		if (!awaitingPermission) return;
		awaitingPermission = false;
		Activity activity = AndroidLauncher.instance;
		String page = pendingPage;
		String download = pendingDownload;
		pendingPage = null;
		pendingDownload = null;
		if (!granted || activity == null || download == null) {
			if (activity != null) {
				Toast.makeText(activity, "Install permission denied. Opening the release page.", Toast.LENGTH_LONG).show();
			}
			open(page);
			return;
		}
		downloadAndInstall(activity, page, download);
	}

	private static boolean isApkUrl(String url) {
		if (url == null) return false;
		String path = url;
		int query = path.indexOf('?');
		if (query >= 0) path = path.substring(0, query);
		return path.toLowerCase(Locale.ROOT).endsWith(".apk");
	}

	private static void downloadAndInstall(Activity activity, String pageURL, String downloadURL) {
		Toast.makeText(activity, "Downloading update…", Toast.LENGTH_SHORT).show();
		new Thread(() -> {
			try {
				File apk = download(activity, downloadURL);
				activity.runOnUiThread(() -> {
					Toast.makeText(activity, "Opening installer…", Toast.LENGTH_SHORT).show();
					install(activity, apk, pageURL);
				});
			} catch (Exception e) {
				activity.runOnUiThread(() -> {
					Toast.makeText(activity, "Couldn't download the update. Opening the release page.", Toast.LENGTH_LONG).show();
					open(pageURL);
				});
			}
		}, "qol-update-download").start();
	}

	private static File download(Activity activity, String downloadURL) throws Exception {
		File dir = new File(activity.getCacheDir(), "updates");
		if (!dir.isDirectory() && !dir.mkdirs()) {
			throw new IllegalStateException("no cache dir");
		}
		File apk = new File(dir, safeName(downloadURL));
		HttpURLConnection connection = (HttpURLConnection) new URL(downloadURL).openConnection();
		connection.setInstanceFollowRedirects(true);
		connection.setConnectTimeout(15000);
		connection.setReadTimeout(30000);
		connection.setRequestProperty("User-Agent", "shattered-pixel-dungeon-qol");
		connection.setRequestProperty("Accept", "application/vnd.android.package-archive,application/octet-stream,*/*");
		int code = connection.getResponseCode();
		if (code != HttpURLConnection.HTTP_OK) {
			connection.disconnect();
			throw new IllegalStateException("HTTP " + code);
		}
		try (InputStream in = connection.getInputStream(); OutputStream out = new FileOutputStream(apk)) {
			byte[] buffer = new byte[8192];
			int read;
			long total = 0;
			while ((read = in.read(buffer)) >= 0) {
				out.write(buffer, 0, read);
				total += read;
			}
			if (total < 1024) {
				throw new IllegalStateException("short download");
			}
		} finally {
			connection.disconnect();
		}
		return apk;
	}

	private static String safeName(String downloadURL) {
		String path = downloadURL;
		int query = path.indexOf('?');
		if (query >= 0) path = path.substring(0, query);
		int slash = path.lastIndexOf('/');
		String name = slash >= 0 ? path.substring(slash + 1) : path;
		if (!name.matches("[A-Za-z0-9._-]+\\.apk")) {
			name = "qol-update.apk";
		}
		return name;
	}

	private static void install(Activity activity, File apk, String pageURL) {
		try {
			Uri uri = UpdateFileProvider.uriFor(apk);
			Intent intent = new Intent(Intent.ACTION_VIEW);
			intent.setDataAndType(uri, "application/vnd.android.package-archive");
			intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
			intent.setClipData(ClipData.newRawUri("", uri));
			activity.startActivity(intent);
		} catch (Exception e) {
			Toast.makeText(activity, "Couldn't open the installer. Opening the release page.", Toast.LENGTH_LONG).show();
			open(pageURL);
		}
	}

	private static void open(String url) {
		if (url == null || url.length() == 0) return;
		if (Game.platform != null) {
			Game.platform.openURI(url);
		}
	}
}
