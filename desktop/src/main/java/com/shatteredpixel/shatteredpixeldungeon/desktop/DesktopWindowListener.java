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

package com.shatteredpixel.shatteredpixeldungeon.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowListener;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.watabou.noosa.audio.Music;

public class DesktopWindowListener implements Lwjgl3WindowListener {
	
	@Override
	public void created ( Lwjgl3Window lwjgl3Window ) { }
	
	@Override
	public void maximized ( boolean b ) {
		SPDSettings.windowMaximized( b );
		if (b){
			SPDSettings.windowResolution(DesktopPlatformSupport.previousSizes[1]);
		}
	}
	
	@Override
	public void iconified ( boolean b ) { }
	public void focusLost () {
		if (!SPDSettings.playMusicInBackground()) {
			Music.INSTANCE.pause();
		}
	}
	public void focusGained () {
		if (!SPDSettings.playMusicInBackground()){
			Music.INSTANCE.resume();
		}
	}
	public boolean closeRequested () {
		// Return true so LWJGL runs a normal close → Game.pause() → Dungeon.saveAll().
		// Do NOT halt() from the cleanup thread: that used to kill the JVM mid-save and
		// leave *.spdtmp files, so WelcomeScene showed the save-interrupted warning every
		// subsequent launch. Stop streaming/training off-thread (those stops can hang).
		// Watchdog is a last resort only if the process never exits on its own.
		Thread cleanup = new Thread(() -> {
			try {
				TrainingExportBootstrapper.stop();
				StreamingBootstrapper.stop();
			} catch (Throwable t) {
				t.printStackTrace();
			}
			System.out.println("[Desktop] Exit cleanup finished (streaming/training)");
		}, "DesktopRunExitCleanup");
		cleanup.setDaemon(true);
		cleanup.start();

		Thread watchdog = new Thread(() -> {
			try {
				Thread.sleep(10000);
			} catch (InterruptedException ignored) {}
			System.out.println("[Desktop] Force-exit (watchdog)");
			Runtime.getRuntime().halt(0);
		}, "DesktopRunExitWatchdog");
		watchdog.setDaemon(true);
		watchdog.start();

		return true;
	}
	public void filesDropped ( String[] strings ) { }
	public void refreshRequested () { }
}
