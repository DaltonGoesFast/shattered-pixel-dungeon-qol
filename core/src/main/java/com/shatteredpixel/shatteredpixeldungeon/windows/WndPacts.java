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
import com.watabou.noosa.audio.Sample;
import com.watabou.noosa.ui.Component;

import java.util.ArrayList;

public class WndPacts extends WndTabbed {

	private static final int WIDTH		= 120;
	private static final int TTL_HEIGHT = 16;
	private static final int BTN_HEIGHT = 16;
	private static final int GAP        = 1;

	private static int lastIdx = 0;

	private boolean editable;
	private ArrayList<CheckBox> boxes;
	private ArrayList<Integer> boxMasks;

	private ScrollPane pane;
	private Component[] pages;
	private float listTop;
	private float listH;

	public WndPacts( int checked, boolean editable ) {

		super();

		this.editable = editable;
		boxes = new ArrayList<>();
		boxMasks = new ArrayList<>();

		RenderedTextBlock title = PixelScene.renderTextBlock( Messages.get(this, "title"), 12 );
		title.hardlight( TITLE_COLOR );
		title.setPos(
				(WIDTH - title.width()) / 2,
				(TTL_HEIGHT - title.height()) / 2
		);
		PixelScene.align(title);
		add( title );

		RenderedTextBlock intro = PixelScene.renderTextBlock( Messages.get(this, "intro"), 6 );
		intro.maxWidth(WIDTH);
		intro.setPos(0, TTL_HEIGHT);
		PixelScene.align(intro);
		add(intro);

		listTop = intro.bottom() + 4;

		// Scroll pane first so its listener sits under the checkboxes.
		// Pointer dispatch is newest-first; a later pane swallows taps.
		Component content = new Component();
		pane = new ScrollPane(content){
			@Override
			public void onClick(float x, float y) {
				// click handled by checkboxes / info buttons
			}
		};
		add(pane);

		pages = new Component[]{
				buildPage(Modifiers.HERO_PACTS, checked),
				buildPage(Modifiers.FOES_PACTS, checked),
				buildPage(Modifiers.SPOILS_PACTS, checked)
		};

		float tallest = 0;
		for (Component page : pages) {
			page.visible = page.active = false;
			content.add(page);
			if (page.height() > tallest) tallest = page.height();
		}

		int maxH = (int)(PixelScene.uiCamera.height - chrome.marginVer() - tabHeight() - 8);
		int height = (int)Math.ceil(listTop + tallest);
		if (height > maxH) height = maxH;
		resize( WIDTH, height );
		listH = height - listTop;
		pane.setRect(0, listTop, WIDTH, listH);

		add( new LabeledTab( Messages.get(this, "hero") ){
			@Override
			protected void select( boolean value ) {
				super.select( value );
				if (selected) showPage( 0 );
			}
		} );
		add( new LabeledTab( Messages.get(this, "foes") ){
			@Override
			protected void select( boolean value ) {
				super.select( value );
				if (selected) showPage( 1 );
			}
		} );
		add( new LabeledTab( Messages.get(this, "spoils") ){
			@Override
			protected void select( boolean value ) {
				super.select( value );
				if (selected) showPage( 2 );
			}
		} );

		layoutTabs();

		if (lastIdx < 0 || lastIdx >= tabs.size()) lastIdx = 0;
		select( lastIdx );
	}

	private void showPage( int index ) {
		lastIdx = index;
		for (int i = 0; i < pages.length; i++) {
			pages[i].visible = pages[i].active = i == index;
		}
		pane.content().setSize( WIDTH, pages[index].height() );
		pane.setRect( 0, listTop, WIDTH, listH );
		pane.scrollTo( 0, 0 );
	}

	private Component buildPage( int[] masks, int checked ) {
		Component page = new Component();
		float pos = 0;

		for (int i = 0; i < masks.length; i++) {

			final int mask = masks[i];
			final String pact = Modifiers.idForMask( mask );
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

			page.add( cb );
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
			page.add(info);

			pos = cb.bottom();
		}

		page.setSize( WIDTH, pos );
		return page;
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
