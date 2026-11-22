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

package com.watabou.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.watabou.noosa.Game;

import java.util.HashMap;
import java.util.Map;

public class GameSettings {
	
	public static final String DEFAULT_PREFS_FILE = "settings.xml";
	
	private static Preferences prefs;
	private static final Map<String, Object> headlessPrefs = new HashMap<>();
	
	private static Preferences get() {
		if (prefs == null) {
			if (Gdx.app == null) {
				return null;
			}
			prefs = Gdx.app.getPreferences( DEFAULT_PREFS_FILE );
		}
		return prefs;
	}
	
	//allows setting up of preferences directly during game initialization
	public static void set( Preferences prefs ){
		GameSettings.prefs = prefs;
	}
	
	public static boolean contains( String key ){
		Preferences p = get();
		if (p == null) {
			synchronized (headlessPrefs) {
				return headlessPrefs.containsKey(key);
			}
		}
		return p.contains( key );
	}
	
	public static int getInt( String key, int defValue ) {
		return getInt(key, defValue, Integer.MIN_VALUE, Integer.MAX_VALUE);
	}
	
	public static int getInt( String key, int defValue, int min, int max ) {
		try {
			Preferences p = get();
			int i;
			if (p == null) {
				synchronized (headlessPrefs) {
					Object v = headlessPrefs.get(key);
					i = (v instanceof Number) ? ((Number) v).intValue() : defValue;
				}
			} else {
				i = p.getInteger( key, defValue );
			}
			if (i < min || i > max){
				int val = (int)GameMath.gate(min, i, max);
				put(key, val);
				return val;
			} else {
				return i;
			}
		} catch (Exception e) {
			Game.reportException(e);
			put(key, defValue);
			return defValue;
		}
	}

	public static long getLong( String key, long defValue ) {
		return getLong(key, defValue, Long.MIN_VALUE, Long.MAX_VALUE);
	}

	public static long getLong( String key, long defValue, long min, long max ) {
		try {
			Preferences p = get();
			long i;
			if (p == null) {
				synchronized (headlessPrefs) {
					Object v = headlessPrefs.get(key);
					i = (v instanceof Number) ? ((Number) v).longValue() : defValue;
				}
			} else {
				i = p.getLong( key, defValue );
			}
			if (i < min || i > max){
				long val = (long)GameMath.gate(min, i, max);
				put(key, val);
				return val;
			} else {
				return i;
			}
		} catch (Exception e) {
			Game.reportException(e);
			put(key, defValue);
			return defValue;
		}
	}
	
	public static boolean getBoolean( String key, boolean defValue ) {
		try {
			Preferences p = get();
			if (p == null) {
				synchronized (headlessPrefs) {
					Object v = headlessPrefs.get(key);
					return (v instanceof Boolean) ? (Boolean) v : defValue;
				}
			}
			return p.getBoolean(key, defValue);
		} catch (Exception e) {
			Game.reportException(e);
			return defValue;
		}
	}
	
	public static String getString( String key, String defValue ) {
		return getString(key, defValue, Integer.MAX_VALUE);
	}
	
	public static String getString( String key, String defValue, int maxLength ) {
		try {
			Preferences p = get();
			String s;
			if (p == null) {
				synchronized (headlessPrefs) {
					Object v = headlessPrefs.get(key);
					s = (v instanceof String) ? (String) v : defValue;
				}
			} else {
				s = p.getString( key, defValue );
			}
			if (s != null && s.length() > maxLength) {
				put(key, defValue);
				return defValue;
			} else {
				return s;
			}
		} catch (Exception e) {
			Game.reportException(e);
			put(key, defValue);
			return defValue;
		}
	}
	
	public static void put( String key, int value ) {
		Preferences p = get();
		if (p == null) {
			synchronized (headlessPrefs) {
				headlessPrefs.put(key, value);
			}
			return;
		}
		p.putInteger(key, value);
		p.flush();
	}

	public static void put( String key, long value ) {
		Preferences p = get();
		if (p == null) {
			synchronized (headlessPrefs) {
				headlessPrefs.put(key, value);
			}
			return;
		}
		p.putLong(key, value);
		p.flush();
	}
	
	public static void put( String key, boolean value ) {
		Preferences p = get();
		if (p == null) {
			synchronized (headlessPrefs) {
				headlessPrefs.put(key, value);
			}
			return;
		}
		p.putBoolean(key, value);
		p.flush();
	}
	
	public static void put( String key, String value ) {
		Preferences p = get();
		if (p == null) {
			synchronized (headlessPrefs) {
				headlessPrefs.put(key, value);
			}
			return;
		}
		p.putString(key, value);
		p.flush();
	}
	
}
