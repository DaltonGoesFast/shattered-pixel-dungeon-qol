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

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.Modifiers;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.CheckBox;
import com.shatteredpixel.shatteredpixeldungeon.ui.IconButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.Icons;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.ui.ScrollPane;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.watabou.noosa.audio.Sample;
import com.watabou.noosa.ui.Component;

import java.util.ArrayList;

public class WndPacts extends Window {

	private static final int WIDTH		= 120;
	private static final int HEIGHT		= 180;
	private static final int TTL_HEIGHT = 16;
	private static final int BTN_HEIGHT = 16;
	private static final int GAP        = 1;

	private boolean editable;
	private ArrayList<CheckBox> boxes;
	private ArrayList<Integer> boxMasks;

	public WndPacts( int checked, boolean editable ) {

		super();

		this.editable = editable;

		resize( WIDTH, HEIGHT );

		RenderedTextBlock title = PixelScene.renderTextBlock( Messages.get(this, "title"), 12 );
		title.hardlight( TITLE_COLOR );
		title.setPos(
				(WIDTH - title.width()) / 2,
				(TTL_HEIGHT - title.height()) / 2
		);
		PixelScene.align(title);
		add( title );

		ScrollPane pane = new ScrollPane(new Component()){
			@Override
			public void onClick(float x, float y) {
				// click handled by checkboxes / info buttons
			}
		};
		add(pane);
		pane.setRect(0, TTL_HEIGHT, WIDTH, HEIGHT - TTL_HEIGHT);

		Component content = pane.content();
		boxes = new ArrayList<>();
		boxMasks = new ArrayList<>();

		float pos = 0;

		RenderedTextBlock intro = PixelScene.renderTextBlock( Messages.get(this, "intro"), 6 );
		intro.maxWidth(WIDTH);
		intro.setPos(0, pos);
		content.add(intro);
		pos = intro.bottom() + 4;

		for (int i=0; i < Modifiers.NAME_IDS.length; i++) {

			final String pact = Modifiers.NAME_IDS[i];
			final int mask = Modifiers.MASKS[i];
			boolean implemented = Modifiers.isImplemented(mask);
			boolean seedBanned = (mask & Modifiers.SEED_BANNED_MASK) != 0 && Modifiers.seedBannedAtSelect();

			String label = Messages.titleCase(Messages.get(Modifiers.class, pact));
			if (!implemented){
				label += " " + Messages.get(WndPacts.class, "coming_soon");
			} else if (seedBanned){
				label += " " + Messages.get(WndPacts.class, "seed_banned");
			}

			CheckBox cb = new CheckBox( label ){
				@Override
				protected void onPointerDown() {
					// Sound waits for a real tap so a scroll drag does not click.
					if (active) bg.brightness( 1.2f );
				}

				@Override
				protected void onClick() {
					if (!active) return;
					Sample.INSTANCE.play( Assets.Sounds.CLICK );
					super.onClick();
				}
			};
			cb.enableScrollPassthrough();
			cb.checked( (checked & mask) != 0 && !seedBanned );

			if (i > 0) {
				pos += GAP;
			}
			cb.setRect( 0, pos, WIDTH-16, BTN_HEIGHT );

			// enable/disable after setRect — CheckBox.layout() may rebuild text and wipe alpha
			if (implemented && !seedBanned){
				cb.active = editable;
			} else {
				cb.enable(false);
			}

			content.add( cb );
			boxes.add( cb );
			boxMasks.add( mask );

			IconButton info = new IconButton(Icons.get(Icons.INFO)){
				@Override
				protected void onPointerDown() {
					if (icon() != null) icon().brightness( 1.5f );
				}

				@Override
				protected void onClick() {
					Sample.INSTANCE.play( Assets.Sounds.CLICK );
					ShatteredPixelDungeon.scene().add(
							new WndMessage(Messages.get(Modifiers.class, pact+"_desc"))
					);
				}
			};
			info.enableScrollPassthrough();
			info.setRect(cb.right(), pos, 16, BTN_HEIGHT);
			if (!implemented){
				info.icon().alpha(0.3f);
			}
			content.add(info);

			pos = cb.bottom();
		}

		content.setSize(WIDTH, pos);
		pane.scrollTo(0, 0);
	}

	@Override
	public void onBackPressed() {

		if (editable) {
			int value = 0;
			for (int i=0; i < boxes.size(); i++) {
				if (boxes.get( i ).checked() && Modifiers.isImplemented(boxMasks.get(i))) {
					value |= boxMasks.get(i);
				}
			}
			SPDSettings.modifiers( Modifiers.sanitize(value) );
		}

		super.onBackPressed();
	}
}
