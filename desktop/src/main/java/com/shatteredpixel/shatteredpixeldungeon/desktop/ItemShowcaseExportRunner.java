/*
 * Runs after Gdx is initialized. Resolves an item, builds showcase JSON with
 * in-game info() text and sprite rects matching GameStateSnapshot.
 */

package com.shatteredpixel.shatteredpixeldungeon.desktop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import com.shatteredpixel.shatteredpixeldungeon.messages.Languages;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.utils.GameStateSnapshot;
import com.watabou.gltextures.TextureCache;
import com.watabou.utils.Reflection;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ItemShowcaseExportRunner {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private static final String[] ENCHANT_PACKAGES = {
			"com.shatteredpixel.shatteredpixeldungeon.items.weapon.enchantments.",
			"com.shatteredpixel.shatteredpixeldungeon.items.weapon.curses."
	};

	private static final String[] GLYPH_PACKAGES = {
			"com.shatteredpixel.shatteredpixeldungeon.items.armor.glyphs.",
			"com.shatteredpixel.shatteredpixeldungeon.items.armor.curses."
	};

	private ItemShowcaseExportRunner() {}

	static void run(String[] args) throws Exception {
		ParsedArgs parsed = ParsedArgs.parse(args);

		// Messages may have failed or loaded empty if touched too early; force EN setup now.
		Messages.setup(Languages.ENGLISH);

		// Warm item / icon textures so rect helpers match live snapshot.
		TextureCache.get(Assets.Sprites.ITEMS);
		TextureCache.get(Assets.Sprites.ITEM_ICONS);

		Class<? extends Item> clazz = StreamerItemResolver.resolveClass(parsed.itemId);
		if (clazz == null) {
			throw new ExportException("Unknown or ambiguous item: '" + parsed.itemId + "'", 3);
		}

		Item item = Reflection.newInstance(clazz);
		if (item == null) {
			throw new ExportException("Could not instantiate item class: " + clazz.getName(), 4);
		}

		item.identify();
		if (parsed.level != null) {
			item.level(parsed.level);
		}

		List<Map<String, Object>> properties = new ArrayList<>();

		if (parsed.enchant != null) {
			if (!(item instanceof Weapon)) {
				throw new ExportException("Enchantment requested but item is not a weapon: " + clazz.getSimpleName(), 5);
			}
			Weapon.Enchantment ench = resolveEnchantment(parsed.enchant);
			((Weapon) item).enchant(ench);
			Map<String, Object> prop = new LinkedHashMap<>();
			prop.put("kind", "enchantment");
			prop.put("name", ench.name());
			prop.put("class_name", ench.getClass().getSimpleName());
			prop.put("desc", plainText(ench.desc()));
			prop.put("has_icon", false);
			properties.add(prop);
		}

		if (parsed.glyph != null) {
			if (!(item instanceof Armor)) {
				throw new ExportException("Glyph requested but item is not armor: " + clazz.getSimpleName(), 5);
			}
			Armor.Glyph glyph = resolveGlyph(parsed.glyph);
			((Armor) item).inscribe(glyph);
			Map<String, Object> prop = new LinkedHashMap<>();
			prop.put("kind", "glyph");
			prop.put("name", glyph.name());
			prop.put("class_name", glyph.getClass().getSimpleName());
			prop.put("desc", plainText(glyph.desc()));
			prop.put("has_icon", false);
			properties.add(prop);
		}

		String displayName = Messages.titleCase(item.name());
		String description = plainText(item.desc());
		String infoText = plainText(item.info());
		String statsBlock = deriveStatsBlock(infoText, description);

		Map<String, Object> sprite = GameStateSnapshot.itemSpriteRect(item.image());
		if (sprite == null) {
			throw new ExportException("Missing sprite mapping for image id " + item.image() + " (" + clazz.getSimpleName() + ")", 6);
		}
		sprite = new LinkedHashMap<>(sprite);
		sprite.put("sheet", Assets.Sprites.ITEMS);

		Map<String, Object> iconSprite = null;
		if (item.icon != -1) {
			iconSprite = GameStateSnapshot.itemIconSpriteRect(item.icon);
			if (iconSprite != null) {
				iconSprite = new LinkedHashMap<>(iconSprite);
				iconSprite.put("sheet", Assets.Sprites.ITEM_ICONS);
			}
		}

		String itemIdSlug = slugify(parsed.itemId);
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("item_id", itemIdSlug);
		out.put("class_name", clazz.getSimpleName());
		out.put("display_name", displayName);
		out.put("description", description);
		out.put("info_text", infoText);
		out.put("stats_block", statsBlock);
		out.put("properties", properties);
		out.put("properties_text", formatPropertiesText(properties));
		out.put("sprite", sprite);
		out.put("icon_sprite", iconSprite);
		if (item instanceof MeleeWeapon) {
			out.put("tier", ((MeleeWeapon) item).tier);
		} else if (item instanceof Armor) {
			out.put("tier", ((Armor) item).tier);
		} else {
			out.put("tier", null);
		}
		out.put("level", item.level());
		out.put("image", item.image());
		out.put("showcase_str_context", Hero.STARTING_STR); // info() without hero omits STR modifiers

		Path outDir = Path.of(parsed.outDir);
		Files.createDirectories(outDir);
		Path jsonPath = outDir.resolve("item.json");
		Files.writeString(jsonPath, GSON.toJson(out) + "\n", StandardCharsets.UTF_8);
		System.out.println("[ItemShowcase] Wrote " + jsonPath.toAbsolutePath());
	}

	private static String deriveStatsBlock(String infoText, String description) {
		if (infoText == null) return "";
		String trimmedInfo = infoText.trim();
		String trimmedDesc = description == null ? "" : description.trim();
		if (!trimmedDesc.isEmpty() && trimmedInfo.startsWith(trimmedDesc)) {
			String rest = trimmedInfo.substring(trimmedDesc.length()).trim();
			return rest;
		}
		// Fallback: everything after the first blank line
		String[] parts = trimmedInfo.split("\n\n", 2);
		return parts.length > 1 ? parts[1].trim() : "";
	}

	private static String formatPropertiesText(List<Map<String, Object>> properties) {
		if (properties == null || properties.isEmpty()) return "";
		StringBuilder sb = new StringBuilder();
		for (Map<String, Object> p : properties) {
			if (sb.length() > 0) sb.append("\n\n");
			sb.append(p.get("name")).append("\n").append(p.get("desc"));
		}
		return sb.toString();
	}

	/** Strip SPD underscore emphasis markers used for in-game highlighting. */
	static String plainText(String raw) {
		if (raw == null) return "";
		return raw.replace("_", "");
	}

	static String slugify(String input) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			if (Character.isLetterOrDigit(c)) {
				sb.append(Character.toLowerCase(c));
			} else if (c == '_' || c == '-' || c == ' ') {
				if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '_') sb.append('_');
			}
		}
		return sb.toString();
	}

	@SuppressWarnings("unchecked")
	private static Weapon.Enchantment resolveEnchantment(String name) throws ExportException {
		Class<?> cls = findClass(name, ENCHANT_PACKAGES, allEnchantClasses());
		if (cls == null || !Weapon.Enchantment.class.isAssignableFrom(cls)) {
			throw new ExportException("Unknown enchantment: '" + name + "'", 5);
		}
		Weapon.Enchantment ench = (Weapon.Enchantment) Reflection.newInstance((Class<? extends Weapon.Enchantment>) cls);
		if (ench == null) {
			throw new ExportException("Could not instantiate enchantment: " + name, 5);
		}
		return ench;
	}

	@SuppressWarnings("unchecked")
	private static Armor.Glyph resolveGlyph(String name) throws ExportException {
		Class<?> cls = findClass(name, GLYPH_PACKAGES, allGlyphClasses());
		if (cls == null || !Armor.Glyph.class.isAssignableFrom(cls)) {
			throw new ExportException("Unknown glyph: '" + name + "'", 5);
		}
		Armor.Glyph glyph = (Armor.Glyph) Reflection.newInstance((Class<? extends Armor.Glyph>) cls);
		if (glyph == null) {
			throw new ExportException("Could not instantiate glyph: " + name, 5);
		}
		return glyph;
	}

	private static List<Class<?>> allEnchantClasses() {
		List<Class<?>> all = new ArrayList<>();
		all.addAll(Arrays.asList(Weapon.Enchantment.common));
		all.addAll(Arrays.asList(Weapon.Enchantment.uncommon));
		all.addAll(Arrays.asList(Weapon.Enchantment.rare));
		all.addAll(Arrays.asList(Weapon.Enchantment.curses));
		return all;
	}

	private static List<Class<?>> allGlyphClasses() {
		List<Class<?>> all = new ArrayList<>();
		all.addAll(Arrays.asList(Armor.Glyph.common));
		all.addAll(Arrays.asList(Armor.Glyph.uncommon));
		all.addAll(Arrays.asList(Armor.Glyph.rare));
		all.addAll(Arrays.asList(Armor.Glyph.curses));
		return all;
	}

	private static Class<?> findClass(String name, String[] packages, List<Class<?>> known) {
		String key = StreamerItemResolver.normalizeKey(name);
		for (Class<?> c : known) {
			if (StreamerItemResolver.normalizeKey(c.getSimpleName()).equals(key)) {
				return c;
			}
		}
		String pascal = name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
		for (String pkg : packages) {
			try {
				return Class.forName(pkg + pascal);
			} catch (ClassNotFoundException ignored) {
			}
			try {
				return Class.forName(pkg + name);
			} catch (ClassNotFoundException ignored) {
			}
		}
		return null;
	}

	static final class ExportException extends Exception {
		final int exitCode;

		ExportException(String message, int exitCode) {
			super(message);
			this.exitCode = exitCode;
		}
	}

	private static final class ParsedArgs {
		final String itemId;
		final String outDir;
		final String enchant;
		final String glyph;
		final Integer level;

		ParsedArgs(String itemId, String outDir, String enchant, String glyph, Integer level) {
			this.itemId = itemId;
			this.outDir = outDir;
			this.enchant = enchant;
			this.glyph = glyph;
			this.level = level;
		}

		static ParsedArgs parse(String[] args) throws ExportException {
			if (args.length < 1) {
				throw new ExportException("Missing item id", 2);
			}
			String itemId = null;
			String outDir = null;
			String enchant = null;
			String glyph = null;
			Integer level = null;

			for (int i = 0; i < args.length; i++) {
				String a = args[i];
				if ("--out".equals(a)) {
					if (i + 1 >= args.length) throw new ExportException("--out requires a path", 2);
					outDir = args[++i];
				} else if ("--enchant".equals(a)) {
					if (i + 1 >= args.length) throw new ExportException("--enchant requires a name", 2);
					enchant = args[++i];
				} else if ("--glyph".equals(a)) {
					if (i + 1 >= args.length) throw new ExportException("--glyph requires a name", 2);
					glyph = args[++i];
				} else if ("--level".equals(a)) {
					if (i + 1 >= args.length) throw new ExportException("--level requires an integer", 2);
					try {
						level = Integer.parseInt(args[++i]);
					} catch (NumberFormatException e) {
						throw new ExportException("Invalid --level: " + args[i], 2);
					}
				} else if (a.startsWith("-")) {
					throw new ExportException("Unknown flag: " + a, 2);
				} else if (itemId == null) {
					itemId = a;
				} else {
					throw new ExportException("Unexpected argument: " + a, 2);
				}
			}
			if (itemId == null || itemId.isBlank()) {
				throw new ExportException("Missing item id", 2);
			}
			if (outDir == null || outDir.isBlank()) {
				throw new ExportException("Missing --out <dir>", 2);
			}
			return new ParsedArgs(itemId, outDir, enchant, glyph, level);
		}
	}
}
