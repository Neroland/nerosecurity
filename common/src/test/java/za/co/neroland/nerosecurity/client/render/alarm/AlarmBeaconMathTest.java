package za.co.neroland.nerosecurity.client.render.alarm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Blade angle and light column. Plain JVM. */
class AlarmBeaconMathTest {

    private static final float EPS = 1.0e-4f;

    @Test
    void bladesTurnEighteenDegreesPerTick() {
        assertEquals(0f, AlarmBeaconMath.bladeAngle(0L, 0f, true), EPS);
        assertEquals(18f, AlarmBeaconMath.bladeAngle(1L, 0f, true), EPS);
        assertEquals(90f, AlarmBeaconMath.bladeAngle(5L, 0f, true), EPS);
        assertEquals(9f, AlarmBeaconMath.bladeAngle(100L, 0.5f, true), EPS);
        assertEquals(AlarmBeaconMath.bladeAngle(3L, 0.25f, true), AlarmBeaconMath.bladeAngle(23L, 0.25f, true), EPS);
        assertEquals(0f, AlarmBeaconMath.bladeAngle(7L, 0.5f, false), EPS);
    }

    @Test
    void angleIsContinuousAcrossTheWrap() {
        float before = AlarmBeaconMath.bladeAngle(19L, 0.999f, true);
        assertTrue(before > 359f && before < 360f);
        assertEquals(0f, AlarmBeaconMath.bladeAngle(20L, 0f, true), EPS);
    }

    @Test
    void bladeTipsSitAtTheRadius() {
        assertArrayEquals(new float[] {11.5f, 8f}, AlarmBeaconMath.bladeTip(0f), EPS);
        assertArrayEquals(new float[] {8f, 11.5f}, AlarmBeaconMath.bladeTip(90f), EPS);
        assertArrayEquals(new float[] {4.5f, 8f}, AlarmBeaconMath.bladeTip(180f), EPS);
    }

    @Test
    void columnAlphaBreathes() {
        for (int t = 0; t < 20; t++) {
            float a = AlarmBeaconMath.columnAlpha(t, 0.3f, true);
            assertTrue(a >= 0.10f - EPS && a <= 0.26f + EPS);
        }
        assertEquals(0.18f, AlarmBeaconMath.columnAlpha(0L, 0f, true), EPS);
        assertEquals(0.18f, AlarmBeaconMath.columnAlpha(4L, 0f, false), EPS);
    }
}
