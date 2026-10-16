package za.co.neroland.nerosecurity.client.render.chest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The chest lid timeline and hinge. */
class LidMathTest {

    private static final float EPS = 1.0e-4f;

    @Test
    void swingsInTenTicksWithEaseOut() {
        assertEquals(0f, LidMath.progress(0f, true, 0), EPS);
        assertEquals(0.5f, LidMath.progress(0f, true, 5), EPS);
        assertEquals(1f, LidMath.progress(0f, true, 12), EPS);
        assertEquals(0f, LidMath.progress(1f, false, 10), EPS);
        assertEquals(0f, LidMath.angle(0f), EPS);
        assertEquals(90f, LidMath.angle(1f), EPS);
        assertTrue(LidMath.angle(0.5f) > 45f, "ease-out is past half way at half time");
    }

    @Test
    void reversingMidSwingDoesNotJump() {
        LidMath.Anchor a = new LidMath.Anchor(false, 0);
        assertEquals(0f, a.at(true, 0), EPS);
        assertEquals(0.4f, a.at(true, 4), EPS);
        assertEquals(0.4f, a.at(false, 4), EPS); // re-anchored at the same progress
        assertEquals(0.2f, a.at(false, 6), EPS);
        assertEquals(0f, a.at(false, 100), EPS);
    }

    @Test
    void anAnchorBornOpenStartsOpen() {
        assertEquals(1f, new LidMath.Anchor(true, 50).at(true, 50), EPS);
    }

    @Test
    void hingeLiftsTheFrontUpAndBack() {
        float[] shut = LidMath.hingedCentre(8f, 12f, 8f, 0f);
        assertEquals(12f, shut[1], EPS);
        assertEquals(8f, shut[2], EPS);
        float[] open = LidMath.hingedCentre(8f, 12f, 8f, 90f);
        // Relative to the hinge (y 10, z 15): (2, -7) becomes (7, 2): up and over the back.
        assertEquals(17f, open[1], EPS);
        assertEquals(17f, open[2], EPS);
        assertEquals(8f, open[0], EPS);
    }
}
