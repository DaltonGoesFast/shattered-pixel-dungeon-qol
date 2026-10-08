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

package com.shatteredpixel.shatteredpixeldungeon.ui;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.QuickSlot;
import com.shatteredpixel.shatteredpixeldungeon.SPDAction;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.HoldFast;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Belongings;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.Bag;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.CellSelector;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSprite;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSpriteSheet;
import com.shatteredpixel.shatteredpixeldungeon.tiles.DungeonTerrainTilemap;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndBag;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndKeyBindings;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndMessage;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndQuickBag;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndUseItem;
import com.watabou.input.ControllerHandler;
import com.watabou.input.GameAction;
import com.watabou.input.KeyBindings;
import com.watabou.noosa.Camera;
import com.watabou.noosa.ColorBlock;
import com.watabou.noosa.Game;
import com.watabou.noosa.Gizmo;
import com.watabou.noosa.Image;
import com.watabou.noosa.PointerArea;
import com.watabou.noosa.ui.Component;
import com.watabou.utils.Point;
import com.watabou.utils.PointF;

import java.util.ArrayList;

public class Toolbar extends Component {

	private Tool btnWait;
	private Tool btnSearch;
	private Tool btnInventory;
	private QuickslotTool[] btnQuick;
	private SlotSwapTool btnSwap;
	
	private PickedUpItem pickedUp;
	
	private boolean lastEnabled = true;
	public boolean examining = false;

	/** 0 unset, otherwise the last quickswapper page size; changing it resets {@link QuickSlotButton#quickSlotPage}. */
	private int swapperPageSize;

	/** Mobile quickslot strip. Inactive unless the slots no longer fit beside the fixed buttons. */
	private QuickslotScrollStrip slotStrip;
	private boolean quickslotScrollActive;
	private int quickslotScrollKey = Integer.MIN_VALUE;

	private static Toolbar instance;

	public enum Mode {
		SPLIT,
		GROUP,
		CENTER
	}
	
	public Toolbar() {
		super();

		instance = this;

		height = btnInventory.height();
	}

	@Override
	public synchronized void destroy() {
		super.destroy();
		if (instance == this) instance = null;
	}

	@Override
	protected void createChildren() {

		add(btnSwap = new SlotSwapTool(128, 0, 21, 23));

		btnQuick = new QuickslotTool[QuickSlot.SLOTS_PER_SET];
		for (int i = 0; i < btnQuick.length; i++){
			add( btnQuick[i] = new QuickslotTool(64, 0, 22, 24, i) );
		}

		// Controller is registered here, then the slot buttons are moved above it.
		// Wait, search, and inventory are created afterward, so they stay above the slots.
		createQuickslotStrip();

		// Hidden button: swap quickslot set / page (e.g. ` key)
		add(new Button(){
			@Override
			protected void onClick() {
				if (SPDSettings.quickSwapper()) {
					QuickSlotButton.advanceQuickSwapperPage();
				} else {
					QuickSlotButton.activeSet = 1 - QuickSlotButton.activeSet;
				}
				Toolbar.updateLayout();
				QuickSlotButton.refresh();
			}
			@Override
			public GameAction keyAction() {
				return SPDAction.QUICKSLOT_SWAP_SET;
			}
		});

		//hidden button for quickslot selector keybind
		add(new Button(){
			@Override
			protected void onClick() {
				if (QuickSlotButton.targetingSlot != -1){
					int cell = QuickSlotButton.autoAim(QuickSlotButton.lastTarget, Dungeon.quickslot.getItem(QuickSlotButton.targetingSlot));

					if (cell != -1){
						GameScene.handleCell(cell);
					} else {
						//couldn't auto-aim, just target the position and hope for the best.
						GameScene.handleCell( QuickSlotButton.lastTarget.pos );
					}
					return;
				}

				if (Dungeon.hero != null && Dungeon.hero.ready && !GameScene.cancel()) {

					int radialSlots = SPDSettings.quickSwapper()
							? QuickSlotButton.lastVisible
							: QuickSlot.SLOTS_PER_SET;
					String[] slotNames = new String[radialSlots];
					Image[] slotIcons = new Image[radialSlots];
					for (int i = 0; i < radialSlots; i++){
						int actual = QuickSlotButton.getActualSlot(i);
						Item item = Dungeon.quickslot.getItem(actual);

						if (item != null && !Dungeon.quickslot.isPlaceholder(actual) &&
								(!Dungeon.hero.belongings.lostInventory() || item.keptThroughLostInventory())){
							slotNames[i] = Messages.titleCase(item.name());
							slotIcons[i] = new ItemSprite(item);
						} else {
							slotNames[i] = Messages.get(Toolbar.class, "quickslot_assign");
							slotIcons[i] = new ItemSprite(ItemSpriteSheet.SOMETHING);
						}
					}

					String info = "";
					if (ControllerHandler.controllerActive){
						info += KeyBindings.getKeyName(KeyBindings.getFirstKeyForAction(GameAction.LEFT_CLICK, true)) + ": " + Messages.get(Toolbar.class, "quickslot_select") + "\n";
						info += KeyBindings.getKeyName(KeyBindings.getFirstKeyForAction(GameAction.RIGHT_CLICK, true)) + ": " + Messages.get(Toolbar.class, "quickslot_assign") + "\n";
						info += KeyBindings.getKeyName(KeyBindings.getFirstKeyForAction(GameAction.BACK, true)) + ": " + Messages.get(Toolbar.class, "quickslot_cancel");
					} else {
						info += Messages.get(WndKeyBindings.class, SPDAction.LEFT_CLICK.name()) + ": " + Messages.get(Toolbar.class, "quickslot_select") + "\n";
						info += Messages.get(WndKeyBindings.class, SPDAction.RIGHT_CLICK.name()) + ": " + Messages.get(Toolbar.class, "quickslot_assign") + "\n";
						info += KeyBindings.getKeyName(KeyBindings.getFirstKeyForAction(GameAction.BACK, false)) + ": " + Messages.get(Toolbar.class, "quickslot_cancel");
					}

					Game.scene().addToFront(new RadialMenu(Messages.get(Toolbar.class, "quickslot_prompt"), info, slotNames, slotIcons) {
						@Override
						public void onSelect(int idx, boolean alt) {
							int actualSlot = QuickSlotButton.getActualSlot(idx);
							Item item = Dungeon.quickslot.getItem(actualSlot);

							if (item == null || Dungeon.quickslot.isPlaceholder(actualSlot)
									|| (Dungeon.hero.belongings.lostInventory() && !item.keptThroughLostInventory())
									|| alt){
								//TODO would be nice to use a radial menu for this too
								// Also a bunch of code could be moved out of here into subclasses of RadialMenu
								GameScene.selectItem(new WndBag.ItemSelector() {
									@Override
									public String textPrompt() {
										return Messages.get(QuickSlotButton.class, "select_item");
									}

									@Override
									public boolean itemSelectable(Item item) {
										return item.defaultAction() != null;
									}

									@Override
									public void onSelect(Item item) {
										if (item != null) {
											QuickSlotButton.set(idx, item);
										}
									}
								});
							} else {

								item.execute(Dungeon.hero);
								if (item.usesTargeting) {
									QuickSlotButton.useTargeting(idx);
								}
							}
							super.onSelect(idx, alt);
						}
					});
				}
			}

			@Override
			public GameAction keyAction() {
				if (btnWait.active) return SPDAction.QUICKSLOT_SELECTOR;
				else				return null;
			}
		});
		
		add(btnWait = new Tool(24, 0, 20, 26) {
			@Override
			protected void onClick() {
				if (Dungeon.hero != null &&  Dungeon.hero.ready && !GameScene.cancel()) {
					examining = false;
					Dungeon.hero.rest(false);
				}
			}
			
			@Override
			public GameAction keyAction() {
				return SPDAction.WAIT;
			}

			@Override
			public GameAction secondaryTooltipAction() {
				return SPDAction.WAIT_OR_PICKUP;
			}

			@Override
			protected String hoverText() {
				return Messages.titleCase(Messages.get(WndKeyBindings.class, "wait"));
			}

			protected boolean onLongClick() {
				if (Dungeon.hero != null && Dungeon.hero.ready && !GameScene.cancel()) {
					examining = false;
					Dungeon.hero.rest(true);
				}
				return true;
			}
		});
		btnWait.icon( 176, 0, 16, 16 );

		//hidden button for rest keybind
		add(new Button(){
			@Override
			protected void onClick() {
				if (Dungeon.hero != null && Dungeon.hero.ready && !GameScene.cancel()) {
					examining = false;
					Dungeon.hero.rest(true);
				}
			}

			@Override
			public GameAction keyAction() {
				if (btnWait.active) return SPDAction.REST;
				else				return null;
			}
		});

		//hidden button for wait / pickup keybind
		add(new Button(){
			@Override
			protected void onClick() {
				if (Dungeon.hero != null && Dungeon.hero.ready && !GameScene.cancel()) {
					Dungeon.hero.waitOrPickup = true;
					if ((Dungeon.level.heaps.get(Dungeon.hero.pos) != null || Dungeon.hero.canSelfTrample())
						&& Dungeon.hero.handle(Dungeon.hero.pos)){
						//trigger hold fast and patient strike here, even if the hero didn't specifically wait
						if (Dungeon.hero.hasTalent(Talent.HOLD_FAST)){
							Buff.affect(Dungeon.hero, HoldFast.class).pos = Dungeon.hero.pos;
						}
						if (Dungeon.hero.hasTalent(Talent.PATIENT_STRIKE)){
							Buff.affect(Dungeon.hero, Talent.PatientStrikeTracker.class).pos = Dungeon.hero.pos;
						}
						Dungeon.hero.next();
					} else {
						examining = false;
						Dungeon.hero.rest(false);
					}
				}
			}

			protected boolean onLongClick() {
				if (Dungeon.hero != null && Dungeon.hero.ready && !GameScene.cancel()) {
					examining = false;
					Dungeon.hero.rest(true);
				}
				return true;
			}

			@Override
			public GameAction keyAction() {
				if (btnWait.active) return SPDAction.WAIT_OR_PICKUP;
				else				return null;
			}
		});
		
		add(btnSearch = new Tool(44, 0, 20, 26) {
			@Override
			protected void onClick() {
				if (Dungeon.hero != null && Dungeon.hero.ready) {
					if (!examining && !GameScene.cancel()) {
						GameScene.selectCell(informer);
						examining = true;
					} else if (examining) {
						informer.onSelect(null);
						Dungeon.hero.search(true);
					}
				}
			}
			
			@Override
			public GameAction keyAction() {
				return SPDAction.EXAMINE;
			}

			@Override
			protected String hoverText() {
				return Messages.titleCase(Messages.get(WndKeyBindings.class, "examine"));
			}
			
			@Override
			protected boolean onLongClick() {
				Dungeon.hero.search(true);
				return true;
			}
		});
		btnSearch.icon( 192, 0, 16, 16 );
		
		add(btnInventory = new Tool(0, 0, 24, 26) {
			private CurrencyIndicator ind;

			private Image arrow;

			@Override
			protected void onClick() {
				if (Dungeon.hero != null && (Dungeon.hero.ready || !Dungeon.hero.isAlive())) {
					if (SPDSettings.interfaceSize() == 2) {
						GameScene.toggleInvPane();
					} else {
						if (!GameScene.cancel()) {
							GameScene.show(new WndBag(Dungeon.hero.belongings.backpack));
						}
					}
				}
			}
			
			@Override
			public GameAction keyAction() {
				return SPDAction.INVENTORY;
			}

			@Override
			public GameAction secondaryTooltipAction() {
				return SPDAction.INVENTORY_SELECTOR;
			}

			@Override
			protected String hoverText() {
				return Messages.titleCase(Messages.get(WndKeyBindings.class, "inventory"));
			}
			
			@Override
			protected boolean onLongClick() {
				GameScene.show(new WndQuickBag(null));
				return true;
			}

			@Override
			protected void createChildren() {
				super.createChildren();
				arrow = Icons.get(Icons.COMPASS);
				arrow.originToCenter();
				arrow.visible = SPDSettings.interfaceSize() == 2;
				arrow.tint(0x3D2E18, 1f);
				add(arrow);

				ind = new CurrencyIndicator();
				add(ind);
			}

			@Override
			protected void layout() {
				super.layout();
				ind.fill(this);
				bringToFront(ind);

				arrow.x = left() + (width - arrow.width())/2;
				arrow.y = bottom()-arrow.height-1;
				arrow.angle = bottom() == camera().height ? 0 : 180;
				PixelScene.align(arrow);
			}

			@Override
			public void enable(boolean value) {
				if (value != active){
					arrow.alpha( value ? 1f : 0.4f );
				}
				super.enable(value);
			}
		});
		btnInventory.icon( 160, 0, 16, 16 );

		//hidden button for inventory selector keybind
		add(new Button(){
			@Override
			protected void onClick() {
				if (Dungeon.hero != null && Dungeon.hero.ready && !GameScene.cancel()) {
					ArrayList<Bag> bags = Dungeon.hero.belongings.getBags();
					String[] names = new String[bags.size()];
					Image[] images = new Image[bags.size()];
					for (int i = 0; i < bags.size(); i++){
						names[i] = Messages.titleCase(bags.get(i).name());
						images[i] = new ItemSprite(bags.get(i));
					}
					String info = "";
					if (ControllerHandler.controllerActive){
						info += KeyBindings.getKeyName(KeyBindings.getFirstKeyForAction(GameAction.LEFT_CLICK, true)) + ": " + Messages.get(Toolbar.class, "container_select") + "\n";
						info += KeyBindings.getKeyName(KeyBindings.getFirstKeyForAction(GameAction.BACK, true)) + ": " + Messages.get(Toolbar.class, "container_cancel");
					} else {
						info += Messages.get(WndKeyBindings.class, SPDAction.LEFT_CLICK.name()) + ": " + Messages.get(Toolbar.class, "container_select") + "\n";
						info += KeyBindings.getKeyName(KeyBindings.getFirstKeyForAction(GameAction.BACK, false)) + ": " + Messages.get(Toolbar.class, "container_cancel");
					}

					Game.scene().addToFront(new RadialMenu(Messages.get(Toolbar.class, "container_prompt"), info, names, images){
						@Override
						public void onSelect(int idx, boolean alt) {
							super.onSelect(idx, alt);
							Bag bag = bags.get(idx);
							ArrayList<Item> items = (ArrayList<Item>) bag.items.clone();

							for(Item i : bag.items){
								if (i instanceof Bag) items.remove(i);
								if (Dungeon.hero.belongings.lostInventory() && !i.keptThroughLostInventory()) items.remove(i);
							}

							if (idx == 0){
								Belongings b = Dungeon.hero.belongings;
								if (b.ring() != null) items.add(0, b.ring());
								if (b.misc() != null) items.add(0, b.misc());
								if (b.artifact() != null) items.add(0, b.artifact());
								if (b.armor() != null) items.add(0, b.armor());
								if (b.secondWep() != null) items.add(0, b.secondWep());
								if (b.weapon() != null) items.add(0, b.weapon());
							}

							if (items.size() == 0){
								GameScene.show(new WndMessage(Messages.get(Toolbar.class, "container_empty")));
								return;
							}

							String[] itemNames = new String[items.size()];
							Image[] itemIcons = new Image[items.size()];
							for (int i = 0; i < items.size(); i++){
								itemNames[i] = Messages.titleCase(items.get(i).name());
								itemIcons[i] = new ItemSprite(items.get(i));
							}

							String info = "";
							if (ControllerHandler.controllerActive){
								info += KeyBindings.getKeyName(KeyBindings.getFirstKeyForAction(GameAction.LEFT_CLICK, true)) + ": " + Messages.get(Toolbar.class, "item_select") + "\n";
								info += KeyBindings.getKeyName(KeyBindings.getFirstKeyForAction(GameAction.RIGHT_CLICK, true)) + ": " + Messages.get(Toolbar.class, "item_use") + "\n";
								info += KeyBindings.getKeyName(KeyBindings.getFirstKeyForAction(GameAction.BACK, true)) + ": " + Messages.get(Toolbar.class, "item_cancel");
							} else {
								info += Messages.get(WndKeyBindings.class, SPDAction.LEFT_CLICK.name()) + ": " + Messages.get(Toolbar.class, "item_select") + "\n";
								info += Messages.get(WndKeyBindings.class, SPDAction.RIGHT_CLICK.name()) + ": " + Messages.get(Toolbar.class, "item_use") + "\n";
								info += KeyBindings.getKeyName(KeyBindings.getFirstKeyForAction(GameAction.BACK, false)) + ": " + Messages.get(Toolbar.class, "item_cancel");
							}

							Game.scene().addToFront(new RadialMenu(Messages.get(Toolbar.class, "item_prompt"), info, itemNames, itemIcons){
								@Override
								public void onSelect(int idx, boolean alt) {
									super.onSelect(idx, alt);
									Item item = items.get(idx);
									if (alt && item.defaultAction() != null) {
										item.execute(Dungeon.hero);
									} else {
										InventoryPane.clearTargetingSlot();
										Game.scene().addToFront(new WndUseItem(null, item));
									}
								}
							});
						}
					});
				}
			}

			@Override
			public GameAction keyAction() {
				if (btnWait.active) return SPDAction.INVENTORY_SELECTOR;
				else				return null;
			}
		});

		add(pickedUp = new PickedUpItem());
	}
	
	@Override
	protected void layout() {

		// Right edge of the toolbar in absolute screen coordinates. Buttons inside the toolbar
		// are positioned using absolute coords (e.g. btnInventory.setPos(right - btnInventory.width(), y))
		// so this must include x for the HUD edit mode horizontal offset to actually move the buttons.
		float right = x + width;

		int quickslotsToShow = 4;
		if (PixelScene.uiCamera.width > 152) quickslotsToShow ++;
		if (PixelScene.uiCamera.width > 170) quickslotsToShow ++;
		int fixedQuickslots = SPDSettings.quickslotsShown();
		if (fixedQuickslots > 0) quickslotsToShow = fixedQuickslots;

		int startingSlot;
		if (SPDSettings.quickSwapper()) {
			int pageSize;
			if (fixedQuickslots > 0) {
				pageSize = fixedQuickslots;
			} else {
				pageSize = quickslotsToShow >= QuickSlot.SLOTS_PER_SET
						? QuickSlot.SLOTS_PER_SET
						: QuickSlotButton.QUICKSWAPPER_PAGE_SIZE;
			}
			if (swapperPageSize != 0 && swapperPageSize != pageSize) {
				QuickSlotButton.quickSlotPage = 0;
			}
			swapperPageSize = pageSize;
			QuickSlotButton.pageSize = pageSize;
			QuickSlotButton.quickSlotPage %= QuickSlotButton.pageCount();

			quickslotsToShow = Math.min(pageSize, QuickSlot.SIZE - QuickSlotButton.quickSlotPage * pageSize);
			QuickSlotButton.lastVisible = quickslotsToShow;
			startingSlot = 0;
			boolean showSwapChip = SPDSettings.showQuickslotSwapButton();
			btnSwap.visible = showSwapChip;
			btnSwap.active = lastEnabled && showSwapChip;
		} else {
			swapperPageSize = 0;
			startingSlot = 0;
			btnSwap.visible = btnSwap.active = false;
			btnSwap.setPos(0, PixelScene.uiCamera.height);
			QuickSlotButton.lastVisible = quickslotsToShow;
		}
		int endingSlot = startingSlot+quickslotsToShow-1;

		for (int i = 0; i < btnQuick.length; i++){
			btnQuick[i].visible = i >= startingSlot && i <= endingSlot;
			btnQuick[i].enable(btnQuick[i].visible && lastEnabled);
			if (i < startingSlot || i > endingSlot){
				btnQuick[i].setPos(btnQuick[i].left(), PixelScene.uiCamera.height);
			}
		}

		if (SPDSettings.interfaceSize() > 0){
			releaseQuickslotScroll();
			btnInventory.setPos(right - btnInventory.width(), y);
			btnWait.setPos(btnInventory.left() - btnWait.width(), y);
			btnSearch.setPos(btnWait.left() - btnSearch.width(), y);

			right = btnSearch.left();
			for(int i = endingSlot; i >= startingSlot; i--) {
				if (i == endingSlot){
					btnQuick[i].border(0, 2);
					btnQuick[i].frame(106, 0, 19, 24);
				} else if (i == 0){
					btnQuick[i].border(2, 1);
					btnQuick[i].frame(86, 0, 20, 24);
				} else {
					btnQuick[i].border(0, 1);
					btnQuick[i].frame(88, 0, 18, 24);
				}
				btnQuick[i].setPos(right-btnQuick[i].width(), y+2);
				right = btnQuick[i].left();
			}

			if (btnSwap.visible) {
				btnSwap.setPos(right - (btnSwap.width() - 2), y + 3);
				right = btnSwap.left();
			}

			return;
		}

		for(int i = startingSlot; i <= endingSlot; i++) {
			if (i == startingSlot && !SPDSettings.flipToolbar() ||
				i == endingSlot && SPDSettings.flipToolbar()){
				btnQuick[i].border(0, 2);
				btnQuick[i].frame(106, 0, 19, 24);
			} else if (i == startingSlot && SPDSettings.flipToolbar() ||
					i == endingSlot && !SPDSettings.flipToolbar()){
				btnQuick[i].border(2, 1);
				btnQuick[i].frame(86, 0, 20, 24);
			} else {
				btnQuick[i].border(0, 1);
				btnQuick[i].frame(88, 0, 18, 24);
			}
		}

		float shift = 0;
		Toolbar.Mode mode;
		try {
			mode = Mode.valueOf(SPDSettings.toolbarMode());
		} catch (Exception e){
			Game.reportException(e);
			mode = PixelScene.landscape() ? Mode.GROUP : Mode.SPLIT;
		}
		switch(mode){
			case SPLIT:
				btnWait.setPos(x, y);
				btnSearch.setPos(btnWait.right(), y);

				btnInventory.setPos(right - btnInventory.width(), y);

				float left = 0;

				btnQuick[startingSlot].setPos(btnInventory.left() - btnQuick[startingSlot].width(), y + 2);
				for (int i = startingSlot+1; i <= endingSlot; i++) {
					btnQuick[i].setPos(btnQuick[i-1].left() - btnQuick[i].width(), y + 2);
					shift = btnSearch.right() - btnQuick[i].left();
				}

				if (btnSwap.visible){
					btnSwap.setPos(btnQuick[endingSlot].left() - (btnSwap.width()-2), y+3);
					shift = btnSearch.right() - btnSwap.left();
				}

				break;

			//center = group but.. well.. centered, so all we need to do is pre-emptively set the right side further in.
			case CENTER:
				float toolbarWidth = btnWait.width() + btnSearch.width() + btnInventory.width();
				for(Button slot : btnQuick){
					if (slot.visible) toolbarWidth += slot.width();
				}
				if (btnSwap.visible) toolbarWidth += btnSwap.width()-2;
				right = x + (width + toolbarWidth)/2;

			case GROUP:
				btnWait.setPos(right - btnWait.width(), y);
				btnSearch.setPos(btnWait.left() - btnSearch.width(), y);
				btnInventory.setPos(btnSearch.left() - btnInventory.width(), y);

				btnQuick[startingSlot].setPos(btnInventory.left() - btnQuick[startingSlot].width(), y + 2);
				for (int i = startingSlot+1; i <= endingSlot; i++) {
					btnQuick[i].setPos(btnQuick[i-1].left() - btnQuick[i].width(), y + 2);
					shift = -btnQuick[i].left();
				}

				if (btnSwap.visible){
					btnSwap.setPos(btnQuick[endingSlot].left() - (btnSwap.width()-2), y+3);
					shift = -btnSwap.left();
				}
				
				break;
		}

		if (shift > 0){
			shift /= 2; //we want to center;
			for (int i = startingSlot; i <= endingSlot; i++) {
				btnQuick[i].setPos(btnQuick[i].left()+shift,  btnQuick[i].top());
			}
			if (btnSwap.visible){
				btnSwap.setPos(btnSwap.left()+shift, btnSwap.top());
			}
		}

		// Mirror anchor: 2 * (x + width/2) so buttons mirror around the toolbar's actual
		// horizontal center even when x != 0 (HUD edit mode horizontal offset).
		right = 2 * x + width;

		if (SPDSettings.flipToolbar()) {

			btnWait.setPos( (right - btnWait.right()), y);
			btnSearch.setPos( (right - btnSearch.right()), y);
			btnInventory.setPos( (right - btnInventory.right()), y);

			for(int i = startingSlot; i <= endingSlot; i++) {
				btnQuick[i].setPos( right - btnQuick[i].right(), y+2);
			}

			if (btnSwap.visible){
				btnSwap.setPos( right - btnSwap.right(), y+3);
			}

		}

		layoutMobileQuickslotScroll();
	}

	public static void updateLayout(){
		if (instance != null) instance.layout();
	}
	
	@Override
	public void update() {
		super.update();
		
		if (lastEnabled != (Dungeon.hero.ready && Dungeon.hero.isAlive())) {
			lastEnabled = (Dungeon.hero.ready && Dungeon.hero.isAlive());
			
			for (Gizmo tool : members.toArray(new Gizmo[0])) {
				if (tool instanceof Tool) {
					((Tool)tool).enable( lastEnabled );
				}
			}
			// Quickslots live inside the scroll strip while it is active, so they are not direct children.
			for (QuickslotTool tool : btnQuick) {
				tool.enable( lastEnabled );
			}
			btnSwap.enable( lastEnabled && btnSwap.visible );
		}
		
		if (!Dungeon.hero.isAlive()) {
			btnInventory.enable(true);
		}
	}

	public void alpha( float value ){
		btnWait.alpha( value );
		btnSearch.alpha( value );
		btnInventory.alpha( value );
		for (QuickslotTool tool : btnQuick){
			tool.alpha(value);
		}
		btnSwap.alpha( value );
		if (slotStrip != null) slotStrip.fade( value );
	}

	public void pickup( Item item, int cell ) {
		pickedUp.reset( item,
			cell,
			btnInventory.centerX(),
			btnInventory.centerY());
	}
	
	private static CellSelector.Listener informer = new CellSelector.Listener() {
		@Override
		public void onSelect( Integer cell ) {
			if (instance != null) {
				instance.examining = false;
				GameScene.examineCell(cell);
			}
		}
		@Override
		public String prompt() {
			return Messages.get(Toolbar.class, "examine_prompt");
		}
	};
	
	private static class Tool extends Button {
		
		private static final int BGCOLOR = 0x7B8073;
		
		protected Image base;
		private Image icon;
		
		public Tool( int x, int y, int width, int height ) {
			super();

			setHotAreaBlockLevel( PointerArea.ALWAYS_BLOCK );
			frame(x, y, width, height);
		}

		public void frame( int x, int y, int width, int height) {
			base.frame( x, y, width, height );

			this.width = width;
			this.height = height;
		}

		public void icon( int x, int y, int width, int height){
			if (icon == null) icon = new Image( Assets.Interfaces.TOOLBAR );
			add(icon);

			icon.frame( x, y, width, height);
		}
		
		@Override
		protected void createChildren() {
			super.createChildren();
			
			base = new Image( Assets.Interfaces.TOOLBAR );
			add( base );
		}
		
		@Override
		protected void layout() {
			super.layout();
			
			base.x = x;
			base.y = y;

			if (icon != null){
				icon.x = x + (width()- icon.width())/2f;
				icon.y = y + (height()- icon.height())/2f;
			}
		}

		public void alpha( float value ){
			base.alpha(value);
			if (icon != null) icon.alpha(value);
		}

		@Override
		protected void onPointerDown() {
			base.brightness( 1.4f );
		}
		
		@Override
		protected void onPointerUp() {
			if (active) {
				base.resetColor();
			} else {
				base.tint( BGCOLOR, 0.7f );
			}
		}
		
		public void enable( boolean value ) {
			if (value != active) {
				if (icon != null) icon.alpha( value ? 1f : 0.4f);
				active = value;
			}
		}
	}
	
	private static class QuickslotTool extends Tool {
		
		private QuickSlotButton slot;
		private int borderLeft = 2;
		private int borderRight = 2;
		private final int slotNum;
		
		public QuickslotTool( int x, int y, int width, int height, int slotNum ) {
			super( x, y, width, height );
			this.slotNum = slotNum;

			slot = new QuickSlotButton( slotNum );
			add( slot );
		}

		public void border( int left, int right ){
			borderLeft = left;
			borderRight = right;
			layout();
		}
		
		@Override
		protected void layout() {
			super.layout();
			slot.setRect( x, y, width, height );
			slot.slotMargins(borderLeft, 2, borderRight, 2);
			applyBankShade();
		}

		@Override
		protected void onPointerUp() {
			if (active) {
				applyBankShade();
			} else {
				super.onPointerUp();
			}
		}

		/** Slots 7–12 use cooler, darker chrome so the second bar is obvious. */
		private void applyBankShade() {
			float a = base.alpha();
			base.resetColor();
			if (QuickSlotButton.getActualSlot(slotNum) >= QuickSlot.SLOTS_PER_SET) {
				base.hardlight(0.68f, 0.70f, 0.76f);
			}
			base.alpha(a);
		}

		@Override
		public void alpha(float value) {
			super.alpha(value);
			slot.alpha(value);
		}

		@Override
		public void enable( boolean value ) {
			super.enable( value && visible );
			slot.enable( value && visible );
		}
	}

	public static SlotSwapTool SWAP_INSTANCE;

	public static class SlotSwapTool extends Tool {

		private Image[] icons = new Image[4];
		private Item[] items = new Item[4];

		public SlotSwapTool(int x, int y, int width, int height) {
			super(x, y, width, height);
			SWAP_INSTANCE = this;
			updateVisuals();
		}

		@Override
		public synchronized void destroy() {
			super.destroy();
			if (SWAP_INSTANCE == this) SWAP_INSTANCE = null;
		}

		@Override
		protected void onClick() {
			super.onClick();
			if (!SPDSettings.quickSwapper()) {
				return;
			}
			QuickSlotButton.advanceQuickSwapperPage();
			Toolbar.updateLayout();
			QuickSlotButton.refresh();
		}

		public void updateVisuals(){
			if (icons[0] == null){
				icons[0] = Icons.get(Icons.CHANGES);
				icons[0].scale.set(PixelScene.align(0.45f));
				add(icons[0]);
			}

			int nextPage = (QuickSlotButton.quickSlotPage + 1) % QuickSlotButton.pageCount();
			int base = nextPage * QuickSlotButton.pageSize;
			int pageEnd = Math.min(base + QuickSlotButton.pageSize, QuickSlot.SIZE);

			for (int i = 1; i < 4; i++){
				int slot = base + (i - 1);
				Item next = slot < pageEnd ? Dungeon.quickslot.getItem(slot) : null;
				if (items[i] == next) {
					continue;
				}
				items[i] = next;
				if (icons[i] != null){
					icons[i].killAndErase();
					icons[i] = null;
				}
				if (items[i] != null){
					icons[i] = new ItemSprite(items[i]);
					icons[i].scale.set(PixelScene.align(0.45f));
					if (Dungeon.quickslot.isPlaceholder(slot)) icons[i].alpha(0.29f);
					add(icons[i]);
				}
			}

			icons[0].x = x + 2 + (8 - icons[0].width())/2;
			icons[0].y = y + 2 + (9 - icons[0].height())/2;
			PixelScene.align(icons[0]);

			if (icons[1] != null){
				icons[1].x = x + 11 + (8 - icons[1].width())/2;
				icons[1].y = y + 2 + (9 - icons[1].height())/2;
				PixelScene.align(icons[1]);
			}

			if (icons[2] != null){
				icons[2].x = x + 2 + (8 - icons[2].width())/2;
				icons[2].y = y + 12 + (9 - icons[2].height())/2;
				PixelScene.align(icons[2]);
			}

			if (icons[3] != null){
				icons[3].x = x + 11 + (8 - icons[3].width())/2;
				icons[3].y = y + 12 + (9 - icons[3].height())/2;
				PixelScene.align(icons[3]);
			}
		}

		@Override
		protected void layout() {
			super.layout();
			updateVisuals();
		}

		@Override
		public void alpha(float value) {
			super.alpha(value);
			for (Image im : icons){
				if (im != null) im.alpha(value);
			}
		}

		@Override
		public void enable(boolean value) {
			super.enable(value);
			for (Image ic : icons){
				if (ic != null && ic.alpha() >= 0.3f){
					ic.alpha( value ? 1 : 0.3f);
				}
			}
		}

		//private

	}
	
	public static class PickedUpItem extends ItemSprite {
		
		private static final float DURATION = 0.5f;
		
		private float startScale;
		private float startX, startY;
		private float endX, endY;
		private float left;
		
		public PickedUpItem() {
			super();
			
			originToCenter();
			
			active =
			visible =
				false;
		}
		
		public void reset( Item item, int cell, float endX, float endY ) {
			view( item );
			
			active =
			visible =
				true;
			
			PointF tile = DungeonTerrainTilemap.raisedTileCenterToWorld(cell);
			Point screen = Camera.main.cameraToScreen(tile.x, tile.y);
			PointF start = camera().screenToCamera(screen.x, screen.y);
			
			x = this.startX = start.x - width() / 2;
			y = this.startY = start.y - width() / 2;
			
			this.endX = endX - width() / 2;
			this.endY = endY - width() / 2;
			left = DURATION;
			
			scale.set( startScale = Camera.main.zoom / camera().zoom );
			
		}
		
		@Override
		public void update() {
			super.update();
			
			if ((left -= Game.elapsed) <= 0) {
				
				visible =
				active =
					false;
				if (emitter != null) emitter.on = false;
				
			} else {
				float p = left / DURATION;
				scale.set( startScale * (float)Math.sqrt( p ) );
				
				x = startX*p + endX*(1-p);
				y = startY*p + endY*(1-p);
			}
		}
	}

	private void createQuickslotStrip() {
		slotStrip = new QuickslotScrollStrip();
		slotStrip.active = false;
		slotStrip.visible = false;
		addToBack( slotStrip );
		// Swap first, then slots, so a slot wins the 2px overlap with the swap chip.
		// Fixed buttons are created after this and stay in front of both.
		btnSwap.givePointerPriority();
		for (QuickslotTool tool : btnQuick) {
			tool.slot.prioritizeForScroll();
		}
	}

	/**
	 * Mobile layout only. If the quickslots and swap chip no longer fit between the fixed
	 * buttons, park them in {@link #slotStrip} at full size and scroll. Desktop returns
	 * before this runs.
	 */
	private void layoutMobileQuickslotScroll() {
		if (!quickslotsOverflow()) {
			releaseQuickslotScroll();
			return;
		}

		float gapLeft;
		float gapRight;
		if (!SPDSettings.flipToolbar()) {
			gapRight = btnInventory.left();
			gapLeft = x;
			if (btnSearch.right() <= gapRight) gapLeft = Math.max( gapLeft, btnSearch.right() );
			if (btnWait.right() <= gapRight) gapLeft = Math.max( gapLeft, btnWait.right() );
		} else {
			gapLeft = btnInventory.right();
			gapRight = x + width;
			if (btnSearch.left() >= gapLeft) gapRight = Math.min( gapRight, btnSearch.left() );
			if (btnWait.left() >= gapLeft) gapRight = Math.min( gapRight, btnWait.left() );
		}
		float gap = gapRight - gapLeft;
		if (gap < 8) {
			releaseQuickslotScroll();
			return;
		}

		ArrayList<Component> row = new ArrayList<>();
		for (QuickslotTool tool : btnQuick) {
			if (tool.visible) row.add( tool );
		}
		if (btnSwap.visible) row.add( btnSwap );
		row.sort( (a, b) -> Float.compare( a.left(), b.left() ) );

		float contentWidth = 0;
		Component prev = null;
		for (int i = 0; i < row.size(); i++) {
			Component c = row.get( i );
			if (prev != null && (prev == btnSwap || c == btnSwap)) {
				contentWidth -= 2;
			}
			contentWidth += c.width();
			prev = c;
		}
		if (contentWidth <= gap + 0.5f) {
			releaseQuickslotScroll();
			return;
		}

		if (!quickslotScrollActive) {
			for (QuickslotTool tool : btnQuick) {
				tool.slot.setScrollPassthrough( true );
			}
			btnSwap.setScrollPassthrough( true );
			quickslotScrollActive = true;
		}

		for (QuickslotTool tool : btnQuick) {
			ensureInStrip( tool );
			if (!tool.visible) tool.setPos( 0, -100 );
		}
		ensureInStrip( btnSwap );
		if (!btnSwap.visible) btnSwap.setPos( 0, -100 );

		float cursor = 0;
		prev = null;
		for (int i = 0; i < row.size(); i++) {
			Component c = row.get( i );
			if (prev != null && (prev == btnSwap || c == btnSwap)) {
				cursor -= 2;
			}
			c.setPos( cursor, c == btnSwap ? 3 : 2 );
			cursor += c.width();
			prev = c;
		}

		int key = QuickSlotButton.quickSlotPage
				+ QuickSlotButton.lastVisible * 32
				+ (btnSwap.visible ? 1 << 10 : 0)
				+ (SPDSettings.flipToolbar() ? 1 << 11 : 0);
		float keepX;
		if (key != quickslotScrollKey) {
			// Slot 0 sits against inventory: the right end when the bar is not flipped.
			keepX = SPDSettings.flipToolbar() ? 0 : Float.MAX_VALUE;
			quickslotScrollKey = key;
		} else {
			keepX = slotStrip.content().camera.scroll.x;
		}

		slotStrip.active = true;
		slotStrip.visible = true;
		slotStrip.content().setSize( Math.max( 1, contentWidth ), height );
		slotStrip.setRect( gapLeft, y, gap, height );
		slotStrip.clampScroll( keepX );
	}

	private boolean quickslotsOverflow() {
		float minX = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY;
		boolean any = false;
		for (QuickslotTool tool : btnQuick) {
			if (!tool.visible) continue;
			any = true;
			minX = Math.min( minX, tool.left() );
			maxX = Math.max( maxX, tool.right() );
		}
		if (btnSwap.visible) {
			any = true;
			minX = Math.min( minX, btnSwap.left() );
			maxX = Math.max( maxX, btnSwap.right() );
		}
		if (!any) return false;
		if (minX < x - 0.5f || maxX > x + width + 0.5f) return true;
		return overlapsX( minX, maxX, btnWait )
				|| overlapsX( minX, maxX, btnSearch )
				|| overlapsX( minX, maxX, btnInventory );
	}

	private static boolean overlapsX( float minX, float maxX, Component other ) {
		return minX < other.right() - 0.5f && maxX > other.left() + 0.5f;
	}

	private void ensureInStrip( Component c ) {
		if (c.parent != slotStrip.content()) {
			slotStrip.content().add( c );
			c.clearCameraTree();
		}
	}

	private void releaseQuickslotScroll() {
		if (!quickslotScrollActive) return;
		for (QuickslotTool tool : btnQuick) {
			tool.slot.setScrollPassthrough( false );
			if (tool.parent != this) {
				add( tool );
				tool.clearCameraTree();
			}
		}
		btnSwap.setScrollPassthrough( false );
		if (btnSwap.parent != this) {
			add( btnSwap );
			btnSwap.clearCameraTree();
		}
		quickslotScrollActive = false;
		quickslotScrollKey = Integer.MIN_VALUE;
		if (slotStrip != null) {
			slotStrip.active = false;
			slotStrip.visible = false;
		}
	}

	/** Horizontal scroller for the mobile quickslot row. Fixed toolbar buttons are not children. */
	private class QuickslotScrollStrip extends ScrollPane {

		private ColorBlock edgeLeft;
		private ColorBlock edgeRight;
		private float edgeAlpha = 0.45f;

		QuickslotScrollStrip() {
			super( new Component() );
		}

		@Override
		protected void createChildren() {
			super.createChildren();
			edgeLeft = new ColorBlock( 3, 1, 0xFF000000 );
			edgeRight = new ColorBlock( 3, 1, 0xFF000000 );
			edgeLeft.alpha( edgeAlpha );
			edgeRight.alpha( edgeAlpha );
			edgeLeft.visible = false;
			edgeRight.visible = false;
			add( edgeLeft );
			add( edgeRight );
		}

		@Override
		protected boolean captureZoomKeys() {
			return false;
		}

		@Override
		protected boolean scrollOnWheelX() {
			return true;
		}

		void fade( float toolbarAlpha ) {
			edgeAlpha = 0.45f * toolbarAlpha;
			if (edgeLeft != null) {
				edgeLeft.alpha( edgeAlpha );
				edgeRight.alpha( edgeAlpha );
			}
		}

		void clampScroll( float x ) {
			scrollTo( x, 0 );
			positionEdges();
		}

		@Override
		protected void layout() {
			super.layout();
			thumb.visible = false;
			positionEdges();
		}

		@Override
		public synchronized void update() {
			super.update();
			if (content != null && content.camera != null) {
				content.camera.scroll.y = 0;
			}
			positionEdges();
		}

		private void positionEdges() {
			if (edgeLeft == null || content == null || content.camera == null) return;
			float max = content.width() - width;
			if (max < 0) max = 0;
			float sx = content.camera.scroll.x;
			boolean moreLeft = sx > 0.5f;
			boolean moreRight = max - sx > 0.5f;
			edgeLeft.visible = moreLeft;
			edgeRight.visible = moreRight;
			if (!moreLeft && !moreRight) return;
			edgeLeft.size( 3, Math.max( 1, height ) );
			edgeRight.size( 3, Math.max( 1, height ) );
			edgeLeft.x = x;
			edgeLeft.y = y;
			edgeRight.x = x + width - 3;
			edgeRight.y = y;
			edgeLeft.alpha( edgeAlpha );
			edgeRight.alpha( edgeAlpha );
		}
	}
}
