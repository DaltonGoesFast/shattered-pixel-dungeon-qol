/*
 * Shattered Pixel Dungeon
 * Copyright (C) 2014-2026 Evan Debenham
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.shatteredpixel.shatteredpixeldungeon.actors.buffs;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.Artifact;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.CloakOfShadows;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.HolyTome;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MagesStaff;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.ui.BuffIndicator;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.watabou.utils.Bundle;

/**
 * Tracks a temporary chat curse on a class kit item (cloak, tome, mage staff).
 * While active the item stays cursed; when the buff ends the curse is removed.
 */
public class ChatClassKitCurse extends FlavourBuff {

	private static final String SLOT = "slot";

	public String slot;

	{
		type = buffType.NEGATIVE;
		announced = true;
	}

	public static boolean isClassKitItem( Item item ) {
		return item instanceof CloakOfShadows
				|| item instanceof HolyTome
				|| item instanceof MagesStaff;
	}

	public static void apply( Hero hero, Item item, String slot, float duration ) {
		if (hero == null || item == null || slot == null || !isClassKitItem(item)) {
			return;
		}

		item.cursed = item.cursedKnown = true;

		if (item instanceof Artifact) {
			((Artifact) item).clearActiveEffect();
		}
		if (item instanceof MagesStaff) {
			((MagesStaff) item).syncImbuedWandCursed();
		}

		ChatClassKitCurse existing = hero.buff(ChatClassKitCurse.class);
		if (existing != null) {
			existing.detach();
		}

		ChatClassKitCurse buff = Buff.affect(hero, ChatClassKitCurse.class, duration);
		buff.slot = slot;
		Item.updateQuickslot();
	}

	public static void endIfCleansed( Hero hero, Item item ) {
		if (hero == null || item == null) {
			return;
		}
		ChatClassKitCurse buff = hero.buff(ChatClassKitCurse.class);
		if (buff == null) {
			return;
		}
		Item tracked = buff.resolveItem(hero);
		if (tracked == item && !item.cursed) {
			buff.detach();
		}
	}

	private Item resolveItem( Hero hero ) {
		if (hero == null || slot == null) {
			return null;
		}
		switch (slot) {
			case "weapon": return hero.belongings.weapon();
			case "armor":  return hero.belongings.armor();
			case "ring":   return hero.belongings.ring();
			case "artifact": return hero.belongings.artifact();
			case "misc":   return hero.belongings.misc();
			default:       return null;
		}
	}

	private void releaseCurse() {
		if (!(target instanceof Hero)) {
			return;
		}
		Hero hero = (Hero) target;
		Item item = resolveItem(hero);
		if (item == null || !isClassKitItem(item)) {
			return;
		}
		item.cursed = false;
		item.cursedKnown = true;
		if (item instanceof MagesStaff) {
			((MagesStaff) item).syncImbuedWandCursed();
		}
		Item.updateQuickslot();
		GLog.p(Messages.get(this, "faded", item.name()));
	}

	@Override
	public boolean act() {
		Hero hero = target instanceof Hero ? (Hero) target : null;
		Item item = resolveItem(hero);
		if (item == null || !item.cursed) {
			detach();
			return true;
		}
		releaseCurse();
		detach();
		return true;
	}

	@Override
	public void detach() {
		super.detach();
		Item.updateQuickslot();
	}

	@Override
	public int icon() {
		return BuffIndicator.TEMP_CURSE;
	}

	@Override
	public String desc() {
		Item item = resolveItem(target instanceof Hero ? (Hero) target : null);
		String itemName = item != null ? item.name() : Messages.get(this, "unknown_item");
		return Messages.get(this, "desc", itemName, dispTurns());
	}

	@Override
	public void storeInBundle( Bundle bundle ) {
		super.storeInBundle(bundle);
		bundle.put(SLOT, slot);
	}

	@Override
	public void restoreFromBundle( Bundle bundle ) {
		super.restoreFromBundle(bundle);
		slot = bundle.getString(SLOT);
	}
}
