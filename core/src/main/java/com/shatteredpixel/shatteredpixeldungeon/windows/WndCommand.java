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
 * along with this program. If not, see <http://www.gnu.org/licenses/>
 */

package com.shatteredpixel.shatteredpixeldungeon.windows;

import com.shatteredpixel.shatteredpixeldungeon.Command;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.brews.Brew;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.elixirs.Elixir;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.ExoticPotion;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.ExoticScroll;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSprite;
import com.shatteredpixel.shatteredpixeldungeon.ui.ItemButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.watabou.utils.Reflection;

import java.util.ArrayList;

/**
 * Command pact picker. Layout matches {@code Trinity.WndItemtypeSelect}:
 * ItemButtons are children of the window (not a ScrollPane), so they align
 * with the chrome and keep hover name tooltips.
 */
public class WndCommand extends Window {

	private static final int WIDTH = 120;
	private static final int BTN = 19;
	private static final int GAP = 2;

	private final Item original;
	private final int qty;
	private boolean resolved = false;

	public WndCommand( Item original, ArrayList<Class<?>> choices, int qty ){
		super();

		this.original = original;
		this.qty = qty;

		IconTitle titlebar = new IconTitle();
		titlebar.icon(new ItemSprite(original.image(), original.glowing()));
		titlebar.label(Messages.titleCase(original.name()));
		titlebar.setRect(0, 0, WIDTH, 0);
		add(titlebar);

		RenderedTextBlock message = PixelScene.renderTextBlock(Messages.get(this, "message"), 6);
		message.maxWidth(WIDTH);
		message.setPos(0, titlebar.bottom() + GAP);
		add(message);

		int top = (int)message.bottom() + GAP;
		int left = 0;

		for (Class<?> cls : choices){
			Item real = (Item) Reflection.newInstance(cls);
			if (real == null) continue;

			Item display = commandDisplay(real);
			final Class<?> chosen = cls;
			final boolean isOriginal = cls == original.getClass();

			ItemButton btn = new ItemButton(){
				@Override
				protected void onClick() {
					select(chosen);
				}

				@Override
				protected void layout() {
					super.layout();
					if (isOriginal && bg != null){
						bg.hardlight(0.6f, 1f, 0.6f);
					}
				}
			};
			btn.item(display);
			btn.slot().textVisible(false);
			btn.setRect(left, top, BTN, BTN);
			add(btn);

			left += BTN + 1;
			if (left >= WIDTH - BTN){
				left = 0;
				top += BTN + 1;
			}
		}

		if (left > 0){
			top += BTN + 1;
		}

		resize(WIDTH, top);
	}

	/**
	 * Known potions/scrolls/rings show true identity + icon.
	 * Unknown ones show only their mystery color / rune / gem.
	 */
	@SuppressWarnings("unchecked")
	private static Item commandDisplay( Item real ){
		// Brews and elixirs extend Potion but are not in the color handler.
		// Potion.reset() would replace their unique sprites with crimson.
		if (real instanceof Brew || real instanceof Elixir){
			return real;
		}
		if (real instanceof Potion){
			real.reset();
			if (((Potion) real).isKnown()){
				return real;
			}
			Class<? extends Potion> labelCls = (Class<? extends Potion>) real.getClass();
			if (real instanceof ExoticPotion){
				labelCls = ExoticPotion.exoToReg.get(real.getClass());
			}
			String color = Potion.labelFor(labelCls);
			if (color == null) color = "crimson";
			final int img = real.image();
			final String colorName = Messages.get(real, color);
			return new Item(){
				{ image = img; icon = -1; }
				@Override public String name(){ return colorName; }
				@Override public boolean isIdentified(){ return false; }
			};
		}
		if (real instanceof Scroll){
			real.reset();
			if (((Scroll) real).isKnown()){
				return real;
			}
			Class<? extends Scroll> labelCls = (Class<? extends Scroll>) real.getClass();
			if (real instanceof ExoticScroll){
				labelCls = ExoticScroll.exoToReg.get(real.getClass());
			}
			String rune = Scroll.labelFor(labelCls);
			if (rune == null) rune = "KAUNAN";
			final int img = real.image();
			final String runeName = Messages.get(real, rune);
			return new Item(){
				{ image = img; icon = -1; }
				@Override public String name(){ return runeName; }
				@Override public boolean isIdentified(){ return false; }
			};
		}
		if (real instanceof Ring){
			real.reset();
			if (((Ring) real).isKnown()){
				return real;
			}
			String gem = Ring.labelFor((Class<? extends Ring>) real.getClass());
			if (gem == null) gem = "garnet";
			final int img = real.image();
			final String gemName = Messages.get(real, gem);
			return new Item(){
				{ image = img; icon = -1; }
				@Override public String name(){ return gemName; }
				@Override public boolean isIdentified(){ return false; }
			};
		}
		real.levelKnown = true;
		real.cursedKnown = true;
		return real;
	}

	private void select( Class<?> chosen ){
		if (resolved) return;
		resolved = true;
		Command.applyChoice(original, chosen, qty);
		hide();
	}

	@Override
	public void onBackPressed() {
		if (!resolved){
			resolved = true;
			Command.cancel(original);
		}
		super.onBackPressed();
	}

	@Override
	public void destroy() {
		super.destroy();
		if (!resolved){
			resolved = true;
			Command.cancel(original);
		}
		Command.onWindowClosed();
	}

}
