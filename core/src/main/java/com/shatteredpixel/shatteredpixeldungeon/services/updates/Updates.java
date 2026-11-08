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

import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.watabou.utils.Callback;

public class Updates {

	public static UpdateService service;

	public static boolean supportsUpdates(){
		return service != null;
	}

	// One network check per process. A failure, including being offline, does not retry.
	private static boolean checkedThisLaunch = false;

	public static boolean supportsUpdatePrompts(){
		return supportsUpdates() && service.supportsUpdatePrompts();
	}

	public static boolean supportsBetaChannel(){
		return supportsUpdates() && service.supportsBetaChannel();
	}

	public static void checkForUpdate(){
		checkForUpdate(false, null);
	}

	/**
	 * @param force when true, rechecks even if this launch already checked, ignores the
	 *              auto-check setting, and allows metered networks (for Settings → Check now).
	 * @param callback optional; called after the network result. Auto-check passes null.
	 */
	public static void checkForUpdate(boolean force, UpdateService.UpdateResultCallback callback){
		if (!supportsUpdatePrompts()) {
			if (callback != null) callback.onConnectionFailed();
			return;
		}
		if (!force && !SPDSettings.updates()) return;
		if (!force && checkedThisLaunch) return;
		checkedThisLaunch = true;

		//We do this so that automatically enabled beta checking (for users who DLed a beta) persists afterward
		if (SPDSettings.betas()){
			SPDSettings.betas(true);
		}

		// Auto-check still respects "Only check on WiFi". Manual Check now always allows cellular.
		boolean useMetered = force || !SPDSettings.WiFi();

		service.checkForUpdate(useMetered, SPDSettings.betas(), new UpdateService.UpdateResultCallback() {
			@Override
			public void onUpdateAvailable(AvailableUpdateData update) {
				updateData = update;
				if (callback != null) callback.onUpdateAvailable(update);
			}

			@Override
			public void onNoUpdateFound() {
				if (force) {
					updateData = null;
				}
				if (callback != null) callback.onNoUpdateFound();
			}

			@Override
			public void onConnectionFailed() {
				// Auto-check stays quiet. checkedThisLaunch is already set, so this launch does not retry.
				if (callback != null) callback.onConnectionFailed();
			}
		});
	}

	public static void launchUpdate( AvailableUpdateData data ){
		service.initializeUpdate( data );
	}

	private static AvailableUpdateData updateData = null;

	public static boolean updateAvailable(){
		return updateData != null;
	}

	public static AvailableUpdateData updateData(){
		return updateData;
	}

	public static void clearUpdate(){
		updateData = null;
	}

	public static boolean supportsReviews() {
		return supportsUpdates() && service.supportsReviews();
	}

	public static void launchReview(Callback callback){
		if (supportsUpdates()){
			service.initializeReview(new UpdateService.ReviewResultCallback() {
				@Override
				public void onComplete() {
					callback.call();
				}
			});
		} else {
			callback.call();
		}
	}

	public static void openReviewURI(){
		if (supportsUpdates()){
			service.openReviewURI();
		}
	}

}
