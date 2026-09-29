/*
 * Player debug mode: pick a number with a slider, then apply it with one tap.
 */

package com.shatteredpixel.shatteredpixeldungeon.windows;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite;
import com.shatteredpixel.shatteredpixeldungeon.ui.OptionSlider;
import com.shatteredpixel.shatteredpixeldungeon.ui.RedButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.shatteredpixel.shatteredpixeldungeon.utils.PlayerDebugActions;

public class WndPlayerDebugValue extends Window {

	private static final int WIDTH = 120;
	private static final int SLIDER_HEIGHT = 21;
	private static final int BTN_HEIGHT = 16;
	private static final int GAP = 2;

	protected interface Action {
		String apply(int value);
	}

	public static WndPlayerDebugValue heroLevel(){
		return new WndPlayerDebugValue(Messages.get(WndPlayerDebugValue.class, "level_title"),
				1, Hero.MAX_LEVEL, Dungeon.hero.lvl, new Action() {
			@Override
			public String apply(int value) {
				return PlayerDebugActions.setHeroLevel(value);
			}
		});
	}

	public static WndPlayerDebugValue floor(){
		return new WndPlayerDebugValue(Messages.get(WndPlayerDebugValue.class, "floor_title"),
				1, PlayerDebugActions.MAX_FLOOR, Math.max(1, Math.min(Dungeon.depth, PlayerDebugActions.MAX_FLOOR)), new Action() {
			@Override
			public String apply(int value) {
				return PlayerDebugActions.gotoFloor(value);
			}
		});
	}

	private final RenderedTextBlock status;

	private WndPlayerDebugValue(String title, int min, int max, int initial, final Action action){
		super();

		RenderedTextBlock txtTitle = PixelScene.renderTextBlock(title, 9);
		txtTitle.hardlight(Window.TITLE_COLOR);
		txtTitle.maxWidth(WIDTH);
		txtTitle.setPos((WIDTH - txtTitle.width()) / 2f, GAP);
		add(txtTitle);

		final RenderedTextBlock selected = PixelScene.renderTextBlock(
				Messages.get(WndPlayerDebugValue.class, "value", initial), 6);
		selected.setPos(0, txtTitle.bottom() + 2*GAP);
		add(selected);

		final OptionSlider slider = new OptionSlider("",
				Integer.toString(min), Integer.toString(max), min, max) {
			@Override
			protected void onChange() {
				selected.text(Messages.get(WndPlayerDebugValue.class, "value", getSelectedValue()));
			}
		};
		slider.setSelectedValue(initial);
		slider.setRect(0, selected.bottom() + GAP, WIDTH, SLIDER_HEIGHT);
		add(slider);

		RedButton apply = new RedButton(Messages.get(WndPlayerDebugValue.class, "apply")){
			@Override
			protected void onClick() {
				super.onClick();
				report(action.apply(slider.getSelectedValue()));
			}
		};
		apply.setRect(0, slider.bottom() + 2*GAP, WIDTH, BTN_HEIGHT);
		add(apply);

		status = PixelScene.renderTextBlock(6);
		status.maxWidth(WIDTH);
		status.setPos(0, apply.bottom() + 2*GAP);
		add(status);

		resize(WIDTH, (int)(apply.bottom() + 2*GAP + 16));
	}

	private void report(String result){
		if (PlayerDebugActions.isError(result)) {
			status.text(result.substring(4));
			status.hardlight(CharSprite.NEGATIVE);
		} else {
			status.text(result);
			status.hardlight(CharSprite.POSITIVE);
		}
	}
}
