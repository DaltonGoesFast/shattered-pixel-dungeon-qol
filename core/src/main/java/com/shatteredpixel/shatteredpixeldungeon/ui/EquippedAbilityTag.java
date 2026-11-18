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

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.SPDAction;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.abilities.ArmorAbility;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.ClassArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.Artifact;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfForce;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSprite;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndInfoArmorAbility;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndUseItem;
import com.watabou.input.GameAction;
import com.watabou.noosa.BitmapText;
import com.watabou.noosa.Visual;

public class EquippedAbilityTag extends Tag {

	public static final int COLOR_ARTIFACT = 0x185898;
	public static final int COLOR_ABILITY = DangerIndicator.COLOR;

	private final int slotIndex;
	private Item currentItem;
	private ArmorAbility currentAbility;

	private ItemSprite itemSprite;
	private HeroIcon abilityIcon;
	private BitmapText statusText;

	public EquippedAbilityTag(int slotIndex) {
		super(slotIndex == 2 ? COLOR_ABILITY : COLOR_ARTIFACT);
		this.slotIndex = slotIndex;
		setColor(slotIndex == 2 ? COLOR_ABILITY : COLOR_ARTIFACT);
		setSize(SIZE, SIZE);
		visible = false;
	}

	public static Item getItem(int slotIndex) {
		if (Dungeon.hero == null || Dungeon.hero.belongings == null) return null;
		if (slotIndex == 0) return Dungeon.hero.belongings.artifact;
		if (slotIndex == 1) return Dungeon.hero.belongings.misc;
		return null;
	}

	public static boolean isItemEligible(Item item, Hero hero) {
		if (item == null || hero == null) return false;
		if (item instanceof Artifact) return true;
		if (item instanceof RingOfForce && hero.heroClass == HeroClass.DUELIST) return true;
		return false;
	}

	public static boolean isArmorAbilityEligible(Hero hero) {
		if (hero == null || hero.armorAbility == null) return false;
		return hero.belongings.armor instanceof ClassArmor;
	}

	public boolean isSlotEligible() {
		if (Dungeon.hero == null) return false;
		if (slotIndex == 2) {
			return isArmorAbilityEligible(Dungeon.hero);
		} else {
			return isItemEligible(getItem(slotIndex), Dungeon.hero);
		}
	}

	@Override
	protected void createChildren() {
		super.createChildren();

		if (slotIndex != 2) {
			itemSprite = new ItemSprite();
			add(itemSprite);
		}

		statusText = new BitmapText(PixelScene.pixelFont);
		statusText.hardlight(0xFFFFFF);
		add(statusText);
	}

	@Override
	public GameAction keyAction() {
		switch (slotIndex) {
			case 0: return SPDAction.ABILITY_TAB_1;
			case 1: return SPDAction.ABILITY_TAB_2;
			case 2: return SPDAction.ABILITY_TAB_3;
			default: return null;
		}
	}

	@Override
	protected void layout() {
		super.layout();

		Visual icon = (slotIndex == 2) ? abilityIcon : itemSprite;
		if (icon != null) {
			icon.x = x + (SIZE - icon.width()) / 2f + 1;
			icon.y = y + (height - icon.height()) / 2f;
			PixelScene.align(icon);
		}

		if (statusText != null && statusText.text() != null && !statusText.text().isEmpty()) {
			statusText.measure();
			if (statusText.width > 16) {
				statusText.scale.set(PixelScene.align(0.8f));
			} else {
				statusText.scale.set(1f);
			}
			statusText.x = x + 3;
			statusText.y = y + 2;
			PixelScene.align(statusText);
		}
	}

	@Override
	public void update() {
		super.update();

		if (!SPDSettings.equippedAbilityTabs()) {
			if (visible) {
				visible = false;
				requestParentLayout();
			}
			return;
		}

		boolean eligible = isSlotEligible();
		if (eligible != visible) {
			visible = eligible;
			requestParentLayout();
		}

		if (visible) {
			updateVisuals();
		}
	}

	private void requestParentLayout() {
		if (parent instanceof InventoryPane) {
			((InventoryPane) parent).layout();
		}
	}

	public void updateVisuals() {
		if (Dungeon.hero == null) return;

		if (slotIndex == 2) {
			ArmorAbility ability = Dungeon.hero.armorAbility;
			if (ability != currentAbility || abilityIcon == null) {
				currentAbility = ability;
				if (abilityIcon != null) {
					remove(abilityIcon);
				}
				if (ability != null) {
					abilityIcon = new HeroIcon(ability);
					add(abilityIcon);
					bringToFront(statusText);
				}
			}

			if (Dungeon.hero.belongings.armor instanceof ClassArmor) {
				ClassArmor armor = (ClassArmor) Dungeon.hero.belongings.armor;
				float charge = armor.charge;
				statusText.text((int) Math.floor(charge) + "%");
				statusText.measure();

				float cost = (ability != null) ? ability.chargeUse(Dungeon.hero) : 0;
				boolean canUse = charge >= cost;
				if (abilityIcon != null) {
					abilityIcon.alpha(canUse ? 1.0f : 0.45f);
				}
				if (canUse) {
					statusText.hardlight(0xFFFFFF);
				} else {
					statusText.hardlight(0xCC6666);
				}
			}
		} else {
			Item item = getItem(slotIndex);
			if (item != currentItem) {
				currentItem = item;
				if (item != null && itemSprite != null) {
					itemSprite.view(item);
				}
			}

			if (item != null) {
				String st = item.status();
				statusText.text(st != null ? st : "");
				statusText.measure();

				boolean usable = true;
				if (item instanceof Artifact) {
					if (st != null && (st.startsWith("0/") || st.equals("0%"))) {
						usable = false;
					}
				} else if (item instanceof RingOfForce && Dungeon.hero.heroClass == HeroClass.DUELIST) {
					RingOfForce.BrawlersStance stance = Dungeon.hero.buff(RingOfForce.BrawlersStance.class);
					boolean activeStance = (stance != null && stance.active);
					statusText.text(activeStance ? "ON" : "OFF");
					statusText.measure();
					statusText.hardlight(activeStance ? 0x66FF66 : 0xAAAAAA);
					usable = true;
				}

				if (itemSprite != null) {
					itemSprite.alpha(usable ? 1.0f : 0.45f);
				}
			}
		}

		layout();
	}

	@Override
	protected void onClick() {
		super.onClick();
		if (Dungeon.hero == null || !Dungeon.hero.isAlive() || !Dungeon.hero.ready) return;

		if (slotIndex == 2) {
			if (Dungeon.hero.belongings.armor instanceof ClassArmor) {
				ClassArmor armor = (ClassArmor) Dungeon.hero.belongings.armor;
				GameScene.centerNextWndOnInvPane();
				armor.execute(Dungeon.hero, ClassArmor.AC_ABILITY);
			}
		} else {
			Item item = getItem(slotIndex);
			if (item != null && Dungeon.hero.belongings.contains(item) && !GameScene.cancel()) {
				GameScene.centerNextWndOnInvPane();
				item.execute(Dungeon.hero);
			}
		}
	}

	@Override
	protected void onRightClick() {
		if (Dungeon.hero == null) return;
		if (slotIndex == 2) {
			if (Dungeon.hero.armorAbility != null) {
				GameScene.show(new WndInfoArmorAbility(Dungeon.hero.heroClass, Dungeon.hero.armorAbility));
			}
		} else {
			Item item = getItem(slotIndex);
			if (item != null && Dungeon.hero.belongings.contains(item)) {
				GameScene.show(new WndUseItem(null, item));
			}
		}
	}

	@Override
	protected boolean onLongClick() {
		onRightClick();
		return true;
	}

	@Override
	protected String hoverText() {
		if (slotIndex == 2) {
			if (Dungeon.hero != null && Dungeon.hero.armorAbility != null && Dungeon.hero.belongings.armor instanceof ClassArmor) {
				ClassArmor armor = (ClassArmor) Dungeon.hero.belongings.armor;
				String text = Messages.titleCase(Dungeon.hero.armorAbility.name());
				text += " (" + (int) Math.floor(armor.charge) + "%)";
				return text;
			}
		} else {
			Item item = getItem(slotIndex);
			if (item != null) {
				String text = Messages.titleCase(item.name());
				String status = item.status();
				if (status != null && !status.isEmpty()) {
					text += " (" + status + ")";
				}
				return text;
			}
		}
		return null;
	}

	@Override
	protected void onPointerDown() {
		Visual icon = (slotIndex == 2) ? abilityIcon : itemSprite;
		if (icon != null) {
			icon.lightness(0.7f);
		}
	}

	@Override
	protected void onPointerUp() {
		Visual icon = (slotIndex == 2) ? abilityIcon : itemSprite;
		if (icon != null) {
			icon.resetColor();
		}
	}

	public void alpha(float value) {
		if (bg != null) bg.alpha(value);
		Visual icon = (slotIndex == 2) ? abilityIcon : itemSprite;
		if (icon != null) icon.alpha(value);
		if (statusText != null) statusText.alpha(value);
	}
}
