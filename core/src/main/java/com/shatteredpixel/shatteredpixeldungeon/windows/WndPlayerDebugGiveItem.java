/*
 * Player debug mode: search for an item, pick quantity and upgrade, then give it with one tap.
 */

package com.shatteredpixel.shatteredpixeldungeon.windows;

import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSprite;
import com.shatteredpixel.shatteredpixeldungeon.ui.Icons;
import com.shatteredpixel.shatteredpixeldungeon.ui.RedButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.shatteredpixel.shatteredpixeldungeon.utils.DebugItemResolver;
import com.shatteredpixel.shatteredpixeldungeon.utils.PlayerDebugActions;
import com.watabou.utils.Reflection;

import java.util.List;

public class WndPlayerDebugGiveItem extends Window {

	private static final int WIDTH = 120;
	private static final int BTN_HEIGHT = 16;
	private static final int GAP = 2;
	private static final int MAX_RESULTS = 6;

	//kept between openings so repeated gives only need one tap
	private static String query = "";
	private static Class<? extends Item> selected;
	private static int quantity = 1;
	private static int upgrade = 0;

	public WndPlayerDebugGiveItem(){
		this(null);
	}

	private WndPlayerDebugGiveItem(String result){
		super();

		RenderedTextBlock title = PixelScene.renderTextBlock(Messages.get(this, "title"), 9);
		title.hardlight(Window.TITLE_COLOR);
		title.setPos((WIDTH - title.width()) / 2f, GAP);
		add(title);

		float pos = title.bottom() + 2*GAP;

		RedButton btnSearch = new RedButton(query.isEmpty()
				? Messages.get(this, "search")
				: Messages.get(this, "search_for", query), 7){
			@Override
			protected void onClick() {
				super.onClick();
				ShatteredPixelDungeon.scene().addToFront(new WndTextInput(
						Messages.get(WndPlayerDebugGiveItem.class, "search_title"),
						null, query, 30, false,
						Messages.get(WndPlayerDebugGiveItem.class, "search_go"),
						Messages.get(WndPlayerDebugGiveItem.class, "cancel")){
					@Override
					public void onSelect(boolean positive, String text) {
						if (!positive) return;
						query = text == null ? "" : text.trim();
						List<Class<? extends Item>> found = DebugItemResolver.search(query, MAX_RESULTS);
						selected = found.size() == 1 ? found.get(0) : null;
						reopen(null);
					}
				});
			}
		};
		btnSearch.icon(Icons.get(Icons.MAGNIFY));
		btnSearch.setRect(0, pos, WIDTH, BTN_HEIGHT);
		add(btnSearch);
		pos = btnSearch.bottom();

		List<Class<? extends Item>> results = DebugItemResolver.search(query, MAX_RESULTS);
		if (!query.isEmpty() && results.isEmpty()) {
			RenderedTextBlock none = PixelScene.renderTextBlock(Messages.get(this, "no_matches"), 6);
			none.maxWidth(WIDTH);
			none.setPos(0, pos + GAP);
			add(none);
			pos = none.bottom();
		}
		for (final Class<? extends Item> clazz : results) {
			Item probe = Reflection.newInstance(clazz);
			String label = probe != null ? Messages.titleCase(probe.trueName()) : clazz.getSimpleName();
			RedButton btnItem = new RedButton(label, 6){
				@Override
				protected void onClick() {
					super.onClick();
					selected = clazz;
					reopen(null);
				}
			};
			if (probe != null) btnItem.icon(new ItemSprite(probe));
			if (clazz == selected) btnItem.textColor(Window.TITLE_COLOR);
			btnItem.setRect(0, pos + 1, WIDTH, BTN_HEIGHT);
			add(btnItem);
			pos = btnItem.bottom();
		}

		pos += 2*GAP;

		RedButton btnQuantity = new RedButton(Messages.get(this, "quantity", quantity), 7){
			@Override
			protected void onClick() {
				super.onClick();
				askNumber(Messages.get(WndPlayerDebugGiveItem.class, "quantity_title"), quantity, true);
			}
		};
		btnQuantity.setRect(0, pos, WIDTH/2f - 1, BTN_HEIGHT);
		add(btnQuantity);

		RedButton btnUpgrade = new RedButton(Messages.get(this, "upgrade", upgrade), 7){
			@Override
			protected void onClick() {
				super.onClick();
				askNumber(Messages.get(WndPlayerDebugGiveItem.class, "upgrade_title"), upgrade, false);
			}
		};
		btnUpgrade.setRect(WIDTH/2f + 1, pos, WIDTH/2f - 1, BTN_HEIGHT);
		add(btnUpgrade);
		pos = btnUpgrade.bottom() + 2*GAP;

		RedButton btnGive = new RedButton(Messages.get(this, "give")){
			@Override
			protected void onClick() {
				super.onClick();
				reopen(PlayerDebugActions.giveItem(selected, quantity, upgrade));
			}
		};
		btnGive.enable(selected != null);
		btnGive.setRect(0, pos, WIDTH, BTN_HEIGHT + 2);
		add(btnGive);
		pos = btnGive.bottom();

		RenderedTextBlock status = PixelScene.renderTextBlock(6);
		status.maxWidth(WIDTH);
		if (result != null) {
			if (PlayerDebugActions.isError(result)) {
				status.text(result.substring(4));
				status.hardlight(CharSprite.NEGATIVE);
			} else {
				status.text(result);
				status.hardlight(CharSprite.POSITIVE);
			}
		}
		status.setPos(0, pos + GAP);
		add(status);
		pos = Math.max(status.bottom(), pos + GAP + 16);

		resize(WIDTH, (int)Math.ceil(pos));
	}

	private void askNumber(String title, int current, final boolean isQuantity){
		ShatteredPixelDungeon.scene().addToFront(new WndTextInput(
				title, null, Integer.toString(current), 3, false,
				Messages.get(WndPlayerDebugGiveItem.class, "set"),
				Messages.get(WndPlayerDebugGiveItem.class, "cancel")){
			@Override
			public void onSelect(boolean positive, String text) {
				if (!positive) return;
				int value;
				try {
					value = Integer.parseInt(text.trim());
				} catch (Exception e) {
					return;
				}
				if (isQuantity) {
					quantity = Math.max(1, Math.min(value, PlayerDebugActions.MAX_QUANTITY));
				} else {
					upgrade = Math.max(0, Math.min(value, PlayerDebugActions.MAX_UPGRADE));
				}
				reopen(null);
			}
		});
	}

	private void reopen(String result){
		hide();
		ShatteredPixelDungeon.scene().addToFront(new WndPlayerDebugGiveItem(result));
	}
}
