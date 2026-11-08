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
import com.shatteredpixel.shatteredpixeldungeon.Chimera;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.Button;
import com.shatteredpixel.shatteredpixeldungeon.ui.Icons;
import com.shatteredpixel.shatteredpixeldungeon.ui.RedButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.ui.TalentButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.TalentIcon;
import com.shatteredpixel.shatteredpixeldungeon.ui.TalentsPane;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.watabou.noosa.Image;
import com.watabou.noosa.PointerArea;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * Chimera opening draft: four empty T1 sockets; fill each from other-class offers, then begin.
 */
public class WndChimeraDraft extends Window {

	public static WndChimeraDraft INSTANCE;

	private static final int WIDTH = 120;
	private static final float GAP = 2;

	private RedButton btnBegin;
	private float slotsTop;
	private final ArrayList<DraftSlotButton> slotButtons = new ArrayList<>();

	public WndChimeraDraft() {
		super();
		INSTANCE = this;

		IconTitle title = new IconTitle();
		title.icon( Icons.get( Icons.TALENT ) );
		title.label( Messages.titleCase( Messages.get( this, "title" ) ) );
		title.setRect( 0, 0, WIDTH, 0 );
		add( title );

		RenderedTextBlock message = PixelScene.renderTextBlock( Messages.get( this, "message" ), 6 );
		message.maxWidth( WIDTH );
		message.setPos( 0, title.bottom() + GAP );
		add( message );

		slotsTop = message.bottom() + 2 * GAP;
		layoutSlots();

		btnBegin = new RedButton( Messages.get( this, "begin" ) ) {
			@Override
			protected void onClick() {
				if (!Chimera.draftComplete( Dungeon.hero )) return;
				Chimera.applyDraft( Dungeon.hero );
				hide();
			}
		};
		placeBegin();
		add( btnBegin );
		refreshBegin();

		resize( WIDTH, (int)btnBegin.bottom() + 1 );
	}

	private float bottomOfSlots() {
		float bottom = slotsTop;
		for (DraftSlotButton btn : slotButtons) {
			bottom = Math.max( bottom, btn.bottom() );
		}
		return bottom;
	}

	private void placeBegin() {
		btnBegin.setRect( 0, bottomOfSlots() + 2 * GAP, WIDTH, 18 );
	}

	private void layoutSlots() {
		for (DraftSlotButton btn : slotButtons) {
			btn.killAndErase();
		}
		slotButtons.clear();

		Hero hero = Dungeon.hero;
		if (hero.chimeraDraftOffers == null) return;
		if (hero.chimeraDraftPicks == null) hero.chimeraDraftPicks = new LinkedHashMap<>();

		int count = hero.chimeraDraftOffers.size();
		float rowWidth = count * TalentButton.WIDTH + Math.max( 0, count - 1 ) * GAP;
		float x = Math.max( 0, (WIDTH - rowWidth) / 2f );
		float rowTop = slotsTop;

		for (Talent slot : hero.chimeraDraftOffers.keySet()) {
			Talent pick = hero.chimeraDraftPicks.get( slot );
			DraftSlotButton btn = new DraftSlotButton( slot, pick );
			btn.setPos( x, rowTop );
			add( btn );
			slotButtons.add( btn );
			x += TalentButton.WIDTH + GAP;
		}
	}

	public void openOffers( Talent slot ) {
		GameScene.show( new WndChimeraDraftOffers( slot ) );
	}

	public void pick( Talent slot, Talent chosen ) {
		Hero hero = Dungeon.hero;
		if (hero.chimeraDraftPicks == null) hero.chimeraDraftPicks = new LinkedHashMap<>();
		hero.chimeraDraftPicks.put( slot, chosen );
		layoutSlots();
		placeBegin();
		bringToFront( btnBegin );
		refreshBegin();
		resize( WIDTH, (int)btnBegin.bottom() + 1 );
	}

	private void refreshBegin() {
		btnBegin.enable( Chimera.draftComplete( Dungeon.hero ) );
	}

	@Override
	public void hide() {
		super.hide();
		if (INSTANCE == this) INSTANCE = null;
	}

	@Override
	public void onBackPressed() {
		// Must finish the draft or exit via the game menu; natural T1 is not a valid Chimera start.
	}

	/** Empty talent chrome until filled with a draft pick. */
	private class DraftSlotButton extends Button {

		private final Talent slot;
		private final Talent pick;
		private Image bg;
		private TalentIcon icon;

		DraftSlotButton( Talent slot, Talent pick ) {
			super();
			hotArea.blockLevel = PointerArea.NEVER_BLOCK;
			this.slot = slot;
			this.pick = pick;

			bg.frame( 0, 0, TalentButton.WIDTH, TalentButton.HEIGHT );
			if (pick != null) {
				icon = new TalentIcon( pick );
				add( icon );
			} else {
				bg.alpha( 0.55f );
			}
		}

		@Override
		protected void createChildren() {
			super.createChildren();
			bg = new Image( Assets.Interfaces.TALENT_BUTTON );
			add( bg );
		}

		@Override
		protected void layout() {
			width = TalentButton.WIDTH;
			height = TalentButton.HEIGHT;
			super.layout();
			bg.x = x;
			bg.y = y;
			if (icon != null) {
				icon.x = x + 2;
				icon.y = y + 2;
				PixelScene.align( icon );
			}
		}

		@Override
		protected void onClick() {
			super.onClick();
			if (pick == null) {
				openOffers( slot );
				return;
			}
			Window info = new WndInfoTalent( pick, 0, new WndInfoTalent.TalentButtonCallback() {
				@Override
				public String prompt() {
					return Messages.titleCase( Messages.get( WndChimeraDraft.class, "change_slot" ) );
				}
				@Override
				public boolean metamorphDesc() {
					return true;
				}
				@Override
				public void call() {
					openOffers( slot );
				}
			} );
			if (ShatteredPixelDungeon.scene() instanceof GameScene) {
				GameScene.show( info );
			} else {
				ShatteredPixelDungeon.scene().addToFront( info );
			}
		}
	}

	public static class WndChimeraDraftOffers extends Window {

		public static WndChimeraDraftOffers INSTANCE;

		public Talent slot;

		public WndChimeraDraftOffers( Talent slot ) {
			super();
			INSTANCE = this;
			this.slot = slot;

			IconTitle title = new IconTitle();
			title.icon( Icons.get( Icons.TALENT ) );
			title.label( Messages.titleCase( Messages.get( WndChimeraDraft.class, "replace_title" ) ) );
			title.setRect( 0, 0, WIDTH, 0 );
			add( title );

			RenderedTextBlock text = PixelScene.renderTextBlock( Messages.get( WndChimeraDraft.class, "replace_desc" ), 6 );
			text.maxWidth( WIDTH );
			text.setPos( 0, title.bottom() + GAP );
			add( text );

			LinkedHashMap<Talent, Integer> offers = new LinkedHashMap<>();
			ArrayList<Talent> list = Dungeon.hero.chimeraDraftOffers.get( slot );
			if (list != null) {
				for (Talent t : list) {
					offers.put( t, 0 );
				}
			}

			TalentsPane.TalentTierPane optionsPane = new TalentsPane.TalentTierPane(
					offers, 1, TalentButton.Mode.CHIMERA_DRAFT_OFFER );
			add( optionsPane );
			optionsPane.title.text( " " );
			optionsPane.setPos( 0, text.bottom() + GAP );
			optionsPane.setSize( WIDTH, optionsPane.height() );
			resize( WIDTH, (int)optionsPane.bottom() );
		}

		@Override
		public void hide() {
			super.hide();
			if (INSTANCE == this) INSTANCE = null;
		}

		@Override
		public void onBackPressed() {
			hide();
		}
	}
}
