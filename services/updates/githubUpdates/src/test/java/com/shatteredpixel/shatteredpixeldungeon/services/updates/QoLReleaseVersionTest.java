package com.shatteredpixel.shatteredpixeldungeon.services.updates;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class QoLReleaseVersionTest {

	@Test
	public void parsesTagsAndRunningVersions() {
		QoLReleaseVersion tag = QoLReleaseVersion.parse("v4.0.2-QoL-8");
		QoLReleaseVersion running = QoLReleaseVersion.parse("4.0.2-QoL-8");
		QoLReleaseVersion indev = QoLReleaseVersion.parse("4.0.2-QoL-8-INDEV");

		assertEquals(4, tag.major);
		assertEquals(0, tag.minor);
		assertEquals(2, tag.patch);
		assertEquals(8, tag.qol);
		assertEquals(0, tag.compareTo(running));
		assertEquals(0, tag.compareTo(indev));
		assertEquals("4.0.2-QoL-8", tag.canonical());
	}

	@Test
	public void qolCounterAndBaseVersionOrder() {
		QoLReleaseVersion current = QoLReleaseVersion.parse("4.0.2-QoL-8");
		assertTrue(QoLReleaseVersion.parse("v4.0.2-QoL-9").isNewerThan(current));
		assertTrue(QoLReleaseVersion.parse("v4.0.3").isNewerThan(current));
		assertTrue(QoLReleaseVersion.parse("v4.1.0-QoL-1").isNewerThan(current));
		assertFalse(QoLReleaseVersion.parse("v4.0.2-QoL-8").isNewerThan(current));
		assertFalse(QoLReleaseVersion.parse("v4.0.2").isNewerThan(current));
		assertFalse(QoLReleaseVersion.parse("v4.0.2-QoL-7").isNewerThan(current));
		assertEquals("4.0.3", QoLReleaseVersion.parse("v4.0.3").canonical());
	}

	@Test
	public void ignoresUnparseableText() {
		assertNull(QoLReleaseVersion.parse(null));
		assertNull(QoLReleaseVersion.parse("not-a-version"));
		assertNull(QoLReleaseVersion.parse(""));
	}

	@Test
	public void picksCanonicalAssetThenPlatformFallback() {
		String version = "4.0.2-QoL-9";
		QoLUpdateAssets.Asset[] assets = new QoLUpdateAssets.Asset[]{
				new QoLUpdateAssets.Asset("README.txt", "https://example.test/readme"),
				new QoLUpdateAssets.Asset("JAVA-Shattered-Pixel-Dungeon-QoL-4.0.2-QoL-9.jar", "https://example.test/jar"),
				new QoLUpdateAssets.Asset("WINDOWS-Shattered-Pixel-Dungeon-QoL-4.0.2-QoL-9-x64.zip", "https://example.test/zip"),
				new QoLUpdateAssets.Asset("ANDROID-Shattered-Pixel-Dungeon-QoL-4.0.2-QoL-9.apk", "https://example.test/apk")
		};

		assertEquals("https://example.test/apk",
				QoLUpdateAssets.pickDownloadUrl(QoLUpdateAssets.Platform.ANDROID, version, assets));
		assertEquals("https://example.test/zip",
				QoLUpdateAssets.pickDownloadUrl(QoLUpdateAssets.Platform.WINDOWS, version, assets));
		assertEquals("https://example.test/jar",
				QoLUpdateAssets.pickDownloadUrl(QoLUpdateAssets.Platform.DESKTOP, version, assets));
		assertNull(QoLUpdateAssets.pickDownloadUrl(QoLUpdateAssets.Platform.OTHER, version, assets));

		QoLUpdateAssets.Asset[] renamed = new QoLUpdateAssets.Asset[]{
				new QoLUpdateAssets.Asset("linux-source.zip", "https://example.test/source"),
				new QoLUpdateAssets.Asset("game-android.apk", "https://example.test/any-apk"),
				new QoLUpdateAssets.Asset("ShatteredPixelDungeon.jar", "https://example.test/any-jar")
		};
		assertEquals("https://example.test/any-apk",
				QoLUpdateAssets.pickDownloadUrl(QoLUpdateAssets.Platform.ANDROID, version, renamed));
		assertEquals("https://example.test/any-jar",
				QoLUpdateAssets.pickDownloadUrl(QoLUpdateAssets.Platform.WINDOWS, version, renamed));
		assertEquals("https://example.test/any-jar",
				QoLUpdateAssets.pickDownloadUrl(QoLUpdateAssets.Platform.DESKTOP, version, renamed));
	}

	@Test
	public void canonicalNamesMatchReleasePackaging() {
		assertEquals("ANDROID-Shattered-Pixel-Dungeon-QoL-4.0.2-QoL-8.apk",
				QoLUpdateAssets.canonicalName(QoLUpdateAssets.Platform.ANDROID, "4.0.2-QoL-8"));
		assertEquals("WINDOWS-Shattered-Pixel-Dungeon-QoL-4.0.2-QoL-8-x64.zip",
				QoLUpdateAssets.canonicalName(QoLUpdateAssets.Platform.WINDOWS, "4.0.2-QoL-8"));
		assertEquals("JAVA-Shattered-Pixel-Dungeon-QoL-4.0.2-QoL-8.jar",
				QoLUpdateAssets.canonicalName(QoLUpdateAssets.Platform.DESKTOP, "4.0.2-QoL-8"));
	}
}
