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

package com.shatteredpixel.shatteredpixeldungeon.scenes;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.Chrome;
import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.ui.ExitButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.Icons;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.ui.ScrollPane;
import com.shatteredpixel.shatteredpixeldungeon.ui.StyledButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.TitleBackground;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.shatteredpixel.shatteredpixeldungeon.windows.IconTitle;
import com.watabou.noosa.Camera;
import com.watabou.noosa.NinePatch;
import com.watabou.noosa.audio.Music;
import com.watabou.noosa.ui.Component;
import com.watabou.utils.RectF;

public class ModChangesScene extends PixelScene {

	public static final int WIDTH_P = 135;
	public static final int WIDTH_L = 220;

	private static final String[] TAB_KEYS = {
			"tab_overview", "tab_qol", "tab_hero", "tab_foes", "tab_spoils"
	};

	private static final String[] BODY_KEYS = {
			"body_overview", "body_qol", "body_hero", "body_foes", "body_spoils"
	};

	public static int tabSelected = 0;

	@Override
	public void create() {
		super.create();

		Music.INSTANCE.playTracks(
				new String[]{Assets.Music.THEME_1, Assets.Music.THEME_2},
				new float[]{1, 1},
				false);

		uiCamera.visible = false;

		int w = Camera.main.width;
		int h = Camera.main.height;

		RectF insets = getCommonInsets();

		TitleBackground BG = new TitleBackground(w, h);

		w -= insets.left + insets.right;
		h -= insets.top + insets.bottom;

		IconTitle title = new IconTitle(
				Icons.getSettings(Icons.SETTINGS_QOL),
				Messages.get(this, "title"));
		title.setSize(200, 0);
		title.setPos(
				insets.left + (w - title.reqWidth()) / 2f,
				insets.top + (20 - title.height()) / 2f
		);
		align(title);
		add(title);

		ExitButton btnExit = new ExitButton();
		btnExit.setPos(insets.left + w - btnExit.width(), insets.top);
		add(btnExit);

		NinePatch panel = Chrome.get(Chrome.Type.TOAST);

		int pw = (landscape() ? WIDTH_L : WIDTH_P) + panel.marginHor();
		int ph = h - 36;

		panel.size(pw, ph);
		panel.x = insets.left + (w - pw) / 2f;
		panel.y = insets.top + 20;
		align(panel);
		add(panel);

		if (tabSelected < 0 || tabSelected >= TAB_KEYS.length) {
			tabSelected = 0;
		}

		ScrollPane list = new ScrollPane(new Component());
		add(list);

		Component content = list.content();
		content.clear();

		RenderedTextBlock heading = PixelScene.renderTextBlock(
				Messages.get(this, TAB_KEYS[tabSelected]), 9);
		heading.hardlight(Window.TITLE_COLOR);
		heading.maxWidth((int)panel.innerWidth());
		heading.setPos(0, 1);
		content.add(heading);

		RenderedTextBlock body = PixelScene.renderTextBlock(
				Messages.get(this, BODY_KEYS[tabSelected]), 6);
		body.maxWidth((int)panel.innerWidth());
		body.setPos(0, heading.bottom() + 4);
		content.add(body);

		content.setSize(panel.innerWidth(), (int)Math.ceil(body.bottom() + 2));

		list.setRect(
				panel.x + panel.marginLeft(),
				panel.y + panel.marginTop() - 1,
				panel.innerWidth() + 2,
				panel.innerHeight() + 2);
		list.scrollTo(0, 0);

		float left = list.left() - 4f;
		float top = list.bottom();
		float tabW = (list.width() + 8f) / TAB_KEYS.length;

		for (int i = 0; i < TAB_KEYS.length; i++) {
			left = setupTabButton(i, Messages.get(this, TAB_KEYS[i]), left, top, tabW);
		}

		addToBack(BG);
		fadeIn();
	}

	private float setupTabButton(int idx, String text, float left, float top, float width) {
		StyledButton button = new StyledButton(Chrome.Type.GREY_BUTTON_TR, text, 7) {
			@Override
			protected void onClick() {
				super.onClick();
				if (tabSelected != idx) {
					tabSelected = idx;
					ShatteredPixelDungeon.seamlessResetScene();
				}
			}
		};
		if (tabSelected != idx) button.textColor(0xBBBBBB);
		button.setRect(left, top, width, tabSelected == idx ? 19 : 15);
		addToBack(button);
		return button.right();
	}

	@Override
	protected void onBackPressed() {
		ShatteredPixelDungeon.switchNoFade(TitleScene.class);
	}
}
