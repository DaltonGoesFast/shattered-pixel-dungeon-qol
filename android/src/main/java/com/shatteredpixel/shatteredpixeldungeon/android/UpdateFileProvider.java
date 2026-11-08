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

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Shares one downloaded APK from the app cache with the system package installer.
 * Only files directly inside {@code cache/updates} whose names are a single
 * {@code .apk} path segment are readable.
 */
public class UpdateFileProvider extends ContentProvider {

	public static final String AUTHORITY_SUFFIX = ".updateprovider";

	public static Uri uriFor(File apk) {
		return new Uri.Builder()
				.scheme("content")
				.authority(AndroidLauncher.instance.getPackageName() + AUTHORITY_SUFFIX)
				.appendPath(apk.getName())
				.build();
	}

	@Override
	public boolean onCreate() {
		return true;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		File file;
		try {
			file = fileFor(uri);
		} catch (FileNotFoundException e) {
			return null;
		}
		String[] columns = projection != null ? projection : new String[]{
				OpenableColumns.DISPLAY_NAME,
				OpenableColumns.SIZE
		};
		MatrixCursor cursor = new MatrixCursor(columns, 1);
		Object[] row = new Object[columns.length];
		for (int i = 0; i < columns.length; i++) {
			if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) {
				row[i] = file.getName();
			} else if (OpenableColumns.SIZE.equals(columns[i])) {
				row[i] = file.length();
			} else {
				row[i] = null;
			}
		}
		cursor.addRow(row);
		return cursor;
	}

	@Override
	public String getType(Uri uri) {
		return "application/vnd.android.package-archive";
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		return null;
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		return 0;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		return 0;
	}

	@Override
	public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
		return ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.MODE_READ_ONLY);
	}

	private File fileFor(Uri uri) throws FileNotFoundException {
		String name = uri.getLastPathSegment();
		if (name == null || !name.matches("[A-Za-z0-9._-]+\\.apk")) {
			throw new FileNotFoundException();
		}
		File dir = new File(getContext().getCacheDir(), "updates");
		File file = new File(dir, name);
		try {
			String dirPath = dir.getCanonicalPath();
			String filePath = file.getCanonicalPath();
			if (!filePath.startsWith(dirPath + File.separator) || !file.isFile()) {
				throw new FileNotFoundException();
			}
		} catch (FileNotFoundException e) {
			throw e;
		} catch (Exception e) {
			throw new FileNotFoundException();
		}
		return file;
	}
}
