package za.co.neroland.nerosecurity.client.render.door;

import za.co.neroland.nerosecurity.client.anim.Easing;

/**
 * Pure panel geometry and easing for the door renderers. Coordinates are model pixels in the door's local frame,
 * authored facing north (front = -Z): the controller block spans x 0..16, y 0..16; a Security Door's upper part is
 * y 16..32; a Blast Door's right-hand column (as seen from the front) is x -16..0 and its top row y 16..32.
 *
 * <p>Nothing is ever drawn outside the door's own blocks: panels shrink into the jambs (security door) or withdraw
 * into the outer frame (blast door iris) instead of moving past them. No trigonometry.
 */
public final class DoorPanelMath {

    /** Jamb / frame bar thickness. */
    public static final float FRAME = 2f;
    /** Security door: a panel's closed width (2..8 and 8..14). */
    public static final float PANEL_WIDTH = 6f;
    /** Blast door: a quadrant plate's closed extent from its outer frame bar to the seam. */
    public static final float IRIS_EXTENT = 14f;
    /** Below this width (pixels) a panel is not drawn at all. */
    public static final float MIN_DRAWN = 0.01f;

    private DoorPanelMath() {
    }

    /** The eased progress: easeInOutQuart for the blast door, easeInOutCubic for the security door. */
    public static float eased(float raw, boolean blast) {
        return blast ? Easing.easeInOutQuart(raw) : Easing.easeInOutCubic(raw);
    }

    /**
     * Security door panel spans at eased progress {@code p}:
     * {@code {leftX0, leftX1, rightX0, rightX1}} with left = 2 .. 8 - 6p and right = 8 + 6p .. 14.
     */
    public static float[] securityPanels(float p) {
        float k = Easing.clamp01(p);
        float travel = PANEL_WIDTH * k;
        return new float[] {FRAME, 8f - travel, 8f + travel, 16f - FRAME};
    }

    /**
     * Security door panel U ranges (16-unit texture space) at eased progress {@code p}: the visible part of each
     * panel keeps the texture it had when closed, so the panel slides rather than squashes.
     * {@code {leftU0, leftU1, rightU0, rightU1}}: left 6p..6, right 0..6-6p.
     */
    public static float[] securityPanelUv(float p) {
        float k = Easing.clamp01(p);
        float travel = PANEL_WIDTH * k;
        return new float[] {travel, PANEL_WIDTH, 0f, PANEL_WIDTH - travel};
    }

    /** Blast door: a quadrant plate's visible extent from its outer frame bar toward the seam, {@code 14 x (1 - p)}. */
    public static float irisLength(float p) {
        return IRIS_EXTENT * (1f - Easing.clamp01(p));
    }

    /**
     * Blast door quadrant plate at eased progress {@code p}, in the door's local frame:
     * {@code {x0, y0, x1, y1, u0, v0, u1, v1}}. {@code right} is the column at local x -16..0, {@code top} the row at
     * y 16..32. The plate is anchored on the quadrant's two outer frame bars and its seam-side edge withdraws toward
     * them on both axes (the four plates retract diagonally: an iris). UVs are distances from the outer edge
     * (16-unit space), offset by the travel so the plate slides into the frame.
     */
    public static float[] irisQuadrant(boolean right, boolean top, float p) {
        float len = irisLength(p);
        float travel = IRIS_EXTENT - len;
        float x0;
        float x1;
        if (right) {
            x0 = -16f + FRAME;
            x1 = x0 + len;
        } else {
            x1 = 16f - FRAME;
            x0 = x1 - len;
        }
        float y0;
        float y1;
        if (top) {
            y1 = 32f - FRAME;
            y0 = y1 - len;
        } else {
            y0 = FRAME;
            y1 = y0 + len;
        }
        float uv0 = FRAME + travel;
        return new float[] {x0, y0, x1, y1, uv0, uv0, 16f, 16f};
    }

    /** Whether a span this wide is worth drawing. */
    public static boolean drawn(float width) {
        return width > MIN_DRAWN;
    }

    /** Vertices for one door's panels at progress {@code p}: 2 (security) or 4 (blast) boxes of 48, or 0. */
    public static int panelVertices(boolean blast, float p) {
        if (blast) {
            return drawn(irisLength(p)) ? 4 * 48 : 0;
        }
        float[] s = securityPanels(p);
        return drawn(s[1] - s[0]) ? 2 * 48 : 0;
    }
}
