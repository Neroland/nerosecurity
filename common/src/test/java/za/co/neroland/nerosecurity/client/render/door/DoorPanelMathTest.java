package za.co.neroland.nerosecurity.client.render.door;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.client.render.DoorChestHubRenderers;

/** Door panel and iris geometry: closed covers the opening, open draws nothing, never outside the door's blocks. */
class DoorPanelMathTest {

    private static final float EPS = 1.0e-5f;

    @Test
    void securityPanelsClosedMeetAtTheSeamAndFillTheOpening() {
        assertArrayEquals(new float[] {2f, 8f, 8f, 14f}, DoorPanelMath.securityPanels(0f), EPS);
        assertArrayEquals(new float[] {0f, 6f, 0f, 6f}, DoorPanelMath.securityPanelUv(0f), EPS);
    }

    @Test
    void securityPanelsOpenHaveZeroWidthInsideTheJambs() {
        float[] s = DoorPanelMath.securityPanels(1f);
        assertEquals(0f, s[1] - s[0], EPS);
        assertEquals(0f, s[3] - s[2], EPS);
        assertEquals(2f, s[0], EPS);
        assertEquals(14f, s[3], EPS);
        assertEquals(0, DoorPanelMath.panelVertices(false, 1f));
        assertEquals(96, DoorPanelMath.panelVertices(false, 0.5f));
    }

    @Test
    void securityPanelsStayInsideTheFrameAndKeepTheirTexture() {
        for (int i = 0; i <= 100; i++) {
            float p = i / 100f;
            float[] s = DoorPanelMath.securityPanels(p);
            float[] u = DoorPanelMath.securityPanelUv(p);
            assertTrue(s[0] >= 2f - EPS && s[1] <= 8f + EPS && s[2] >= 8f - EPS && s[3] <= 14f + EPS);
            // Visible width equals the visible texture width (slides, never squashes).
            assertEquals(s[1] - s[0], u[1] - u[0], EPS);
            assertEquals(s[3] - s[2], u[3] - u[2], EPS);
            // Symmetric about the seam.
            assertEquals(8f - s[1], s[2] - 8f, EPS);
        }
        // Out-of-range progress is clamped.
        assertArrayEquals(DoorPanelMath.securityPanels(0f), DoorPanelMath.securityPanels(-3f), EPS);
        assertArrayEquals(DoorPanelMath.securityPanels(1f), DoorPanelMath.securityPanels(7f), EPS);
    }

    @Test
    void irisClosedCoversEachQuadrantToTheSeams() {
        // controller column, bottom row
        assertArrayEquals(new float[] {0f, 2f, 14f, 16f, 2f, 2f, 16f, 16f},
                DoorPanelMath.irisQuadrant(false, false, 0f), EPS);
        // right column, top row
        assertArrayEquals(new float[] {-14f, 16f, 0f, 30f, 2f, 2f, 16f, 16f},
                DoorPanelMath.irisQuadrant(true, true, 0f), EPS);
        assertEquals(4 * 48, DoorPanelMath.panelVertices(true, 0f));
    }

    @Test
    void irisWithdrawsDiagonallyIntoTheOuterFrame() {
        for (int i = 0; i <= 100; i++) {
            float p = i / 100f;
            float len = DoorPanelMath.irisLength(p);
            assertEquals(14f * (1f - p), len, 1.0e-4f);
            for (int k = 0; k < 4; k++) {
                boolean right = (k & 1) != 0;
                boolean top = (k & 2) != 0;
                float[] r = DoorPanelMath.irisQuadrant(right, top, p);
                assertEquals(len, r[2] - r[0], 1.0e-4f);
                assertEquals(len, r[3] - r[1], 1.0e-4f);
                assertEquals(len, r[6] - r[4], 1.0e-4f);
                // Anchored on the outer frame bars; never past the door's 2x2 outline.
                assertEquals(right ? -14f : 14f, right ? r[0] : r[2], EPS);
                assertEquals(top ? 30f : 2f, top ? r[3] : r[1], EPS);
                assertTrue(r[0] >= -16f && r[2] <= 16f && r[1] >= 0f && r[3] <= 32f);
            }
        }
        assertFalse(DoorPanelMath.drawn(DoorPanelMath.irisLength(1f)));
        assertEquals(0, DoorPanelMath.panelVertices(true, 1f));
    }

    @Test
    void easingPicksTheDoorsCurve() {
        assertEquals(0f, DoorPanelMath.eased(0f, false), EPS);
        assertEquals(1f, DoorPanelMath.eased(1f, true), EPS);
        assertEquals(0.5f, DoorPanelMath.eased(0.5f, false), EPS);
        assertEquals(0.5f, DoorPanelMath.eased(0.5f, true), EPS);
        // Cubic at 0.25: 4 * 0.25^3 = 0.0625; quartic: 8 * 0.25^4 = 0.03125 (the heavier blast door starts slower).
        assertEquals(0.0625f, DoorPanelMath.eased(0.25f, false), EPS);
        assertEquals(0.03125f, DoorPanelMath.eased(0.25f, true), EPS);
        float prev = -1f;
        for (int i = 0; i <= 50; i++) {
            float v = DoorPanelMath.eased(i / 50f, true);
            assertTrue(v >= prev);
            prev = v;
        }
    }

    @Test
    void facingTurnsMatchTheBlockstateRotation() {
        // The north face (z = 0) lands on east, south, west for y = 90, 180, 270.
        assertArrayEquals(new float[] {16f, 4f}, DoorChestHubRenderers.turn(4f, 0f, 1), EPS);
        assertArrayEquals(new float[] {12f, 16f}, DoorChestHubRenderers.turn(4f, 0f, 2), EPS);
        assertArrayEquals(new float[] {0f, 12f}, DoorChestHubRenderers.turn(4f, 0f, 3), EPS);
        assertArrayEquals(new float[] {4f, 0f}, DoorChestHubRenderers.turn(4f, 0f, 4), EPS);
        // The blast door's right column (local x < 0) lies counter-clockwise of the facing: north for EAST.
        float[] eastRight = DoorChestHubRenderers.turn(-8f, 8f, 1);
        assertTrue(eastRight[1] < 0f);
    }
}
