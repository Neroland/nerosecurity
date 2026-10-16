package za.co.neroland.nerosecurity.client.render.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.client.SecurityColours;
import za.co.neroland.nerosecurity.field.FieldState;

/** Core speed by state and draw bucket, angle and tint. Plain JVM. */
class EmitterCoreMathTest {

    private static final float EPS = 1.0e-3f;

    @Test
    void speedFollowsStateAndBucket() {
        assertEquals(0f, EmitterCoreMath.degreesPerSecond(FieldState.IDLE, 4), EPS);
        assertEquals(0f, EmitterCoreMath.degreesPerSecond(FieldState.OVERLOADED, 4), EPS);
        assertEquals(30f, EmitterCoreMath.degreesPerSecond(FieldState.BROWNOUT, 0), EPS);
        float[] want = {0f, 60f, 120f, 240f, 360f};
        for (int b = 0; b <= 4; b++) {
            assertEquals(want[b], EmitterCoreMath.degreesPerSecond(FieldState.PROJECTING, b), EPS);
        }
        assertEquals(360f, EmitterCoreMath.degreesPerSecond(FieldState.PROJECTING, 9), EPS);
        assertEquals(0f, EmitterCoreMath.degreesPerSecond(FieldState.PROJECTING, -1), EPS);
    }

    @Test
    void angleAdvancesBySpeedAndWrapsSmoothly() {
        assertEquals(3f, EmitterCoreMath.angle(60f, 1L, 0f, true), EPS);
        assertEquals(18f, EmitterCoreMath.angle(360f, 1L, 0f, true), EPS);
        assertEquals(0f, EmitterCoreMath.angle(360f, 5L, 0f, false), EPS);
        for (float dps : new float[] {30f, 60f, 120f, 240f, 360f}) {
            assertEquals(0f, EmitterCoreMath.angle(dps, EmitterCoreMath.WRAP_TICKS, 0f, true), EPS);
            float justBefore = EmitterCoreMath.angle(dps, EmitterCoreMath.WRAP_TICKS - 1, 0.999f, true);
            assertTrue(justBefore > 359f || justBefore < 0.1f, "continuous at the wrap for " + dps);
        }
    }

    @Test
    void tintFollowsState() {
        assertEquals(SecurityColours.FIELD, EmitterCoreMath.tint(FieldState.PROJECTING, false, 0L, true));
        assertEquals(SecurityColours.LOCK, EmitterCoreMath.tint(FieldState.PROJECTING, true, 0L, true));
        assertEquals(SecurityColours.DORMANT, EmitterCoreMath.tint(FieldState.IDLE, true, 0L, true));
        assertEquals(SecurityColours.WARN, EmitterCoreMath.tint(FieldState.BROWNOUT, false, 0L, true));
        assertEquals(SecurityColours.DORMANT, EmitterCoreMath.tint(FieldState.BROWNOUT, false, 7L, true));
        assertEquals(SecurityColours.WARN, EmitterCoreMath.tint(FieldState.BROWNOUT, false, 7L, false));
        assertTrue(EmitterCoreMath.lit(FieldState.PROJECTING, SecurityColours.FIELD));
        assertFalse(EmitterCoreMath.lit(FieldState.OVERLOADED, SecurityColours.DORMANT));
    }
}
