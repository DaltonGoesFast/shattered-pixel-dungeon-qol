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

package com.shatteredpixel.shatteredpixeldungeon.playalong;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.playalong.PlayAlongProtocol.GhostState;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.HeroSprite;
import com.shatteredpixel.shatteredpixeldungeon.tiles.DungeonTilemap;
import com.shatteredpixel.shatteredpixeldungeon.ui.HealthBar;
import com.watabou.noosa.BitmapText;
import com.watabou.noosa.Camera;
import com.watabou.noosa.Group;
import com.watabou.noosa.MovieClip;
import com.watabou.noosa.TextureFilm;
import com.watabou.noosa.tweeners.PosTweener;
import com.watabou.noosa.tweeners.Tweener;
import com.watabou.utils.PointF;

/**
 * Purely visual ethereal ghost representation of another Play Along player.
 * Never touches Dungeon.level.mobs or turn scheduling.
 */
public class GhostSprite extends Group implements Tweener.Listener, MovieClip.Listener {

	private static final int FRAME_WIDTH = 12;
	private static final int FRAME_HEIGHT = 15;
	private static final float PERSPECTIVE_RAISE = 6f / 16f;
	private static final float MOVE_DURATION = 0.14f;

	private final MovieClip body;
	private final BitmapText nameTag;
	private final HealthBar hpBar;

	private MovieClip.Animation animIdle;
	private MovieClip.Animation animRun;
	private MovieClip.Animation animAttack;

	private PosTweener motion;
	private HeroClass currentClass;
	private int currentTier = -1;
	private int currentTile = -1;
	private boolean isHost;
	private boolean crowded = false;
	private long lastUpdateMs = 0L;

	public GhostSprite(GhostState state) {
		super();

		body = new MovieClip();
		body.listener = this;
		add(body);

		nameTag = new BitmapText("", PixelScene.pixelFont);
		nameTag.scale.set(0.75f);
		add(nameTag);

		hpBar = new HealthBar();
		hpBar.setSize(12, 1.5f);
		add(hpBar);

		applyState(state, true);
	}

	public void applyState(GhostState state, boolean immediate) {
		if (state == null || Dungeon.level == null) return;

		this.isHost = state.isHost;
		HeroClass cls = parseClass(state.heroClass);
		int tier = Math.max(0, Math.min(6, state.armorTier));

		if (cls != currentClass || tier != currentTier) {
			currentClass = cls;
			currentTier = tier;
			body.texture(cls.spritesheet());
			buildAnimations(tier);
			body.play(animIdle);
		}

		String prefix = isHost ? "* " : "";
		String rawName = state.username != null ? state.username : "";
		String safeAscii = rawName.replaceAll("[^\\x20-\\x7E]", "?");
		String label = prefix + safeAscii;
		if (!label.equals(nameTag.text())) {
			nameTag.text(label);
			nameTag.measure();
		}
		if (isHost) {
			nameTag.hardlight(0xFFD740);
		} else {
			nameTag.hardlight(0x88DDFF);
		}

		body.flipHorizontal = state.facingLeft;
		hpBar.level(Math.max(0f, Math.min(1f, state.hpPct)));

		int targetPos = Math.max(0, Math.min(Dungeon.level.length() - 1, state.pos));
		if (immediate || currentTile == -1) {
			currentTile = targetPos;
			body.point(tileToSpritePos(targetPos));
			body.play(animIdle);
			if (isHost && PlayAlongService.isSpectating() && Camera.main != null) {
				Camera.main.panFollow(body, 5f);
			}
		} else if (targetPos != currentTile) {
			int fx = currentTile % Dungeon.level.width();
			int tx = targetPos % Dungeon.level.width();
			if (tx < fx) body.flipHorizontal = true;
			else if (tx > fx) body.flipHorizontal = false;

			currentTile = targetPos;
			if (motion != null) {
				motion.stop(false);
				motion.killAndErase();
			}
			body.play(animRun);
			motion = new PosTweener(body, tileToSpritePos(targetPos), MOVE_DURATION);
			motion.listener = this;
			add(motion);
			if (isHost && PlayAlongService.isSpectating() && Camera.main != null) {
				Camera.main.panFollow(body, 20f);
			}
		}

		if ("ATTACK".equals(state.action) && state.lastUpdateMs != lastUpdateMs) {
			body.play(animAttack, true);
		}
		lastUpdateMs = state.lastUpdateMs;
		applyTintAndAlpha();
	}

	public void setCrowded(boolean crowded) {
		if (this.crowded != crowded) {
			this.crowded = crowded;
			applyTintAndAlpha();
		}
	}

	public int currentTile() {
		return currentTile;
	}

	private void applyTintAndAlpha() {
		float scale = SPDSettings.playAlongGhostOpacityScale();
		if (isHost) {
			body.hardlight(1.25f, 0.85f, 0.28f);
			body.alpha(0.85f * scale);
			nameTag.alpha(0.95f * scale);
			hpBar.visible = true;
		} else if (crowded) {
			body.hardlight(0.55f, 0.85f, 1.3f);
			body.alpha(0.28f * scale);
			nameTag.alpha(0.35f * scale);
			hpBar.visible = false;
		} else {
			body.hardlight(0.55f, 0.85f, 1.3f);
			body.alpha(0.65f * scale);
			nameTag.alpha(0.85f * scale);
			hpBar.visible = true;
		}
	}

	private void buildAnimations(int tier) {
		TextureFilm film = new TextureFilm(HeroSprite.tiers(), tier, FRAME_WIDTH, FRAME_HEIGHT);
		animIdle = new MovieClip.Animation(1, true);
		animIdle.frames(film, 0, 0, 0, 1, 0, 0, 1, 1);

		animRun = new MovieClip.Animation(20, true);
		animRun.frames(film, 2, 3, 4, 5, 6, 7);

		animAttack = new MovieClip.Animation(15, false);
		animAttack.frames(film, 13, 14, 15, 0);
	}

	private PointF tileToSpritePos(int cell) {
		int w = Dungeon.level.width();
		int csize = DungeonTilemap.SIZE;
		return new PointF(
				PixelScene.align(Camera.main, ((cell % w) + 0.5f) * csize - FRAME_WIDTH * 0.5f),
				PixelScene.align(Camera.main, ((cell / w) + 1.0f) * csize - FRAME_HEIGHT - csize * PERSPECTIVE_RAISE)
		);
	}

	@Override
	public void update() {
		super.update();
		if (body != null) {
			hpBar.setPos(body.x + (FRAME_WIDTH - hpBar.width()) / 2f, body.y - 3f);
			nameTag.x = body.x + (FRAME_WIDTH - nameTag.width()) / 2f;
			nameTag.y = hpBar.top() - nameTag.height() - 1f;
		}
	}

	@Override
	public void onComplete(Tweener tweener) {
		if (tweener == motion) {
			motion = null;
			if (body != null) body.play(animIdle);
		}
	}

	@Override
	public void onComplete(MovieClip.Animation anim) {
		if (anim == animAttack && body != null) {
			body.play(animIdle);
		}
	}

	private static HeroClass parseClass(String raw) {
		if (raw == null) return HeroClass.WARRIOR;
		try {
			return HeroClass.valueOf(raw.toUpperCase());
		} catch (Exception e) {
			return HeroClass.WARRIOR;
		}
	}
}
