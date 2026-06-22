/*
 * Shattered Pixel Dungeon
 * Copyright (C) 2014-2026 Evan Debenham
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.shatteredpixel.shatteredpixeldungeon.ui;

import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.watabou.noosa.Camera;
import com.watabou.noosa.Gizmo;
import com.watabou.noosa.Group;
import com.watabou.noosa.Visual;
import com.watabou.noosa.ui.Component;
import com.watabou.utils.GameMath;
import com.watabou.utils.PointF;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Tracks one HUD region's default bounds, applies user offset, and optionally renders
 * its top-level gizmos (and their descendants) through a per-region {@link Camera} so the
 * region can be scaled independently of the rest of the UI.
 *
 * Scaling via camera (instead of per-visual scale.x/scale.y) avoids fighting internal
 * animations (e.g. HP bar fill, busy spinner) and stat bar width math, since the leaf
 * visuals are untouched -- only the projection they render through changes.
 */
public class HudSlot {

	public final HudRegion region;

	/** Fraction of the slot used as the scaling anchor: (0,0) = top-left, (1,1) = bottom-right. */
	private final PointF anchorFraction;

	private float baseX;
	private float baseY;
	private float baseW;
	private float baseH;

	private final ArrayList<Gizmo> gizmos = new ArrayList<>();
	private final HashMap<Gizmo, PointF> basePositions = new HashMap<>();

	private Camera regionCamera;
	private float appliedScale = 1f;

	public HudSlot( HudRegion region ) {
		this.region = region;
		this.anchorFraction = anchorFor( region );
	}

	/** Anchor corner used when scaling. Picked so the region "grows" away from the screen edge. */
	private static PointF anchorFor( HudRegion r ) {
		switch ( r ) {
			case STATUS:    return new PointF( 0, 1 );
			case LOG:       return new PointF( 0, 1 );
			case TOOLBAR:   return new PointF( 0, 1 );
			case INVENTORY: return new PointF( 1, 1 );
			case MENU:      return new PointF( 1, 0 );
			case TAGS:      return new PointF( 1, 0 );
			case DANGER:    return new PointF( 1, 0 );
			default:        return new PointF( 0, 0 );
		}
	}

	public void addGizmo( Gizmo g ) {
		if ( g != null && !gizmos.contains( g ) ) {
			gizmos.add( g );
		}
	}

	public void setBaseBounds( float x, float y, float w, float h ) {
		baseX = x;
		baseY = y;
		baseW = w;
		baseH = h;
	}

	/** Record positions produced by default layout before applying offsets. */
	public void captureBases() {
		basePositions.clear();
		for ( Gizmo g : gizmos ) {
			if ( g instanceof Component ) {
				Component c = (Component) g;
				basePositions.put( g, new PointF( c.left(), c.top() ) );
			} else if ( g instanceof Visual ) {
				Visual v = (Visual) g;
				basePositions.put( g, new PointF( v.x, v.y ) );
			}
		}
	}

	// ----- unscaled bounds (top-left + size of the slot ignoring user scale) -----

	public float left()   { return baseX + SPDSettings.hudOffsetX( region ); }
	public float top()    { return baseY + SPDSettings.hudOffsetY( region ); }
	public float width()  { return baseW; }
	public float height() { return baseH; }
	public float right()  { return left() + width(); }
	public float bottom() { return top()  + height(); }

	/** Default (pre-offset) bounds, as captured in {@link #setBaseBounds(float, float, float, float)}. */
	public float baseLeft()   { return baseX; }
	public float baseTop()    { return baseY; }
	public float baseRight()  { return baseX + baseW; }
	public float baseBottom() { return baseY + baseH; }

	// ----- displayed bounds (after scaling around anchor) -----

	public float scale() {
		return SPDSettings.hudScale( region );
	}

	public float displayedLeft() {
		float s = scale();
		return left() + anchorFraction.x * baseW * (1f - s);
	}

	public float displayedTop() {
		float s = scale();
		return top() + anchorFraction.y * baseH * (1f - s);
	}

	public float displayedWidth()  { return baseW * scale(); }
	public float displayedHeight() { return baseH * scale(); }

	public boolean contains( float x, float y ) {
		float l = displayedLeft();
		float t = displayedTop();
		return x >= l && x < l + displayedWidth() && y >= t && y < t + displayedHeight();
	}

	/** Apply the position offset to every captured gizmo. Idempotent. */
	public void apply() {
		float ox = SPDSettings.hudOffsetX( region );
		float oy = SPDSettings.hudOffsetY( region );

		for ( Gizmo g : gizmos ) {
			PointF base = basePositions.get( g );
			if ( base == null ) continue;
			if ( g instanceof Component ) {
				Component c = (Component) g;
				c.setPos( base.x + ox, base.y + oy );
			} else if ( g instanceof Visual ) {
				Visual v = (Visual) g;
				v.x = base.x + ox;
				v.y = base.y + oy;
			}
		}
	}

	/**
	 * Reconfigure the region camera for the current {@link #scale()}. Builds/destroys the
	 * camera lazily so we only pay the cost when the region is scaled.
	 *
	 * Call AFTER {@link #apply()} so anchor coordinates use the user's offset.
	 */
	public void applyScale() {
		Camera base = PixelScene.uiCamera;
		if ( base == null ) return;

		float s = scale();

		if ( s == 1f ) {
			disposeCamera();
			appliedScale = 1f;
			return;
		}

		// Anchor in UI coordinates -- the point that stays fixed on screen when scaling.
		float anchorX = left() + anchorFraction.x * baseW;
		float anchorY = top()  + anchorFraction.y * baseH;

		if ( regionCamera == null ) {
			regionCamera = new Camera(
					base.x, base.y,
					Math.max( 1, (int) Math.ceil( base.width  / s ) ),
					Math.max( 1, (int) Math.ceil( base.height / s ) ),
					base.zoom * s );
			regionCamera.fullScreen = base.fullScreen;
			Camera.add( regionCamera );
		} else {
			regionCamera.x = base.x;
			regionCamera.y = base.y;
			regionCamera.resize(
					Math.max( 1, (int) Math.ceil( base.width  / s ) ),
					Math.max( 1, (int) Math.ceil( base.height / s ) ) );
			regionCamera.zoom = base.zoom * s;
		}
		regionCamera.scroll.set( anchorX * (1f - 1f / s), anchorY * (1f - 1f / s) );

		// Reassign cameras. We swap from whatever we previously assigned (uiCamera the first
		// time, regionCamera on subsequent applies) so we don't stomp gizmos that explicitly
		// use a different camera.
		Camera previous = (appliedScale == 1f) ? base : regionCamera;
		for ( Gizmo g : gizmos ) {
			reassignCamera( g, previous, regionCamera );
		}
		appliedScale = s;
	}

	/** Restore all gizmos to {@link PixelScene#uiCamera} and remove the region camera. */
	public void disposeCamera() {
		if ( regionCamera == null ) return;
		Camera base = PixelScene.uiCamera;
		if ( base != null ) {
			for ( Gizmo g : gizmos ) {
				reassignCamera( g, regionCamera, base );
			}
		}
		Camera.remove( regionCamera );
		regionCamera.destroy();
		regionCamera = null;
	}

	public void addOffset( float dx, float dy ) {
		SPDSettings.hudOffsetX( region, GameMath.gate( -500, SPDSettings.hudOffsetX( region ) + dx, 500 ) );
		SPDSettings.hudOffsetY( region, GameMath.gate( -500, SPDSettings.hudOffsetY( region ) + dy, 500 ) );
	}

	public void addScale( float delta ) {
		float s = GameMath.gate( 0.75f, scale() + delta, 1.5f );
		SPDSettings.hudScale( region, s );
	}

	public void resetScale() {
		SPDSettings.hudScale( region, 1f );
	}

	// ----- camera reassignment helpers -----

	/**
	 * Recursively swap any cached {@code from} camera reference to {@code to}. Children with
	 * {@code camera == null} are left alone -- they will lazily inherit the new camera from
	 * their parent on the next call to {@link Gizmo#camera()}.
	 */
	private static void reassignCamera( Gizmo g, Camera from, Camera to ) {
		if ( g == null ) return;
		if ( g.camera == from ) {
			g.camera = to;
		}
		if ( g instanceof Group ) {
			ArrayList<Gizmo> members = membersOf( (Group) g );
			if ( members != null ) {
				for ( int i = 0; i < members.size(); i++ ) {
					reassignCamera( members.get( i ), from, to );
				}
			}
		}
	}

	private static Field membersField;
	@SuppressWarnings("unchecked")
	private static ArrayList<Gizmo> membersOf( Group group ) {
		try {
			if ( membersField == null ) {
				membersField = Group.class.getDeclaredField( "members" );
				membersField.setAccessible( true );
			}
			return (ArrayList<Gizmo>) membersField.get( group );
		} catch ( Exception e ) {
			return null;
		}
	}

}
