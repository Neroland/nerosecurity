package za.co.neroland.nerosecurity.client.anim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The easing curves and the four-blink grammar are pure functions; pin their shape. */
class EasingBlinkTest {

    @Test
    void easingCurvesHitTheirEndsAndNeverRunBackwards() {
        float[] last = {-1f, -1f, -1f};
        for (int i = 0; i <= 100; i++) {
            float t = i / 100f;
            float[] v = {Easing.easeInOutCubic(t), Easing.easeInOutQuart(t), Easing.easeOutCubic(t)};
            for (int k = 0; k < 3; k++) {
                assertTrue(v[k] >= last[k] - 1e-6f, "curve " + k + " decreased at " + t);
                assertTrue(v[k] >= 0f && v[k] <= 1f, "curve " + k + " out of range at " + t);
                last[k] = v[k];
            }
        }
        assertEquals(0f, Easing.easeInOutCubic(0f), 1e-6f);
        assertEquals(1f, Easing.easeInOutCubic(1f), 1e-6f);
        assertEquals(0.5f, Easing.easeInOutCubic(0.5f), 1e-6f);
        assertEquals(0.5f, Easing.easeInOutQuart(0.5f), 1e-6f);
        assertEquals(1f, Easing.easeOutCubic(1f), 1e-6f);
        assertEquals(0f, Easing.easeOutCubic(-3f), 1e-6f);
        assertEquals(1f, Easing.easeInOutQuart(7f), 1e-6f);
        // the heavier blast door starts slower than the security door
        assertTrue(Easing.easeInOutQuart(0.2f) < Easing.easeInOutCubic(0.2f));
        assertEquals(5f, Easing.lerp(0f, 10f, 0.5f), 1e-6f);
        assertEquals(1.0, Easing.clamp01(1.5), 1e-9);
    }

    @Test
    void blinkGrammarDutyCycles() {
        assertEquals(15, countOn(Blink::recording, 20));
        assertEquals(5, countOn(Blink::warning, 10));
        assertEquals(3, countOn(Blink::lockdown, 5));
        // 1 Hz / 2 Hz / 4 Hz: the pattern repeats with the stated period
        for (long t = 0; t < 200; t++) {
            assertEquals(Blink.recording(t), Blink.recording(t + 20));
            assertEquals(Blink.warning(t), Blink.warning(t + 10));
            assertEquals(Blink.lockdown(t), Blink.lockdown(t + 5));
        }
        // negative game times behave like positive ones
        assertEquals(Blink.recording(-1), Blink.recording(19));
        assertTrue(Blink.recording(0));
        assertFalse(Blink.recording(15));
    }

    @Test
    void denyFlashIsEightHertz() {
        int on = 0;
        int samples = 0;
        for (long t = 0; t < 20; t++) {
            for (int p = 0; p < 4; p++) {
                samples++;
                if (Blink.deny(t, p / 4f)) {
                    on++;
                }
            }
        }
        assertEquals(samples / 2, on);
        assertTrue(Blink.deny(0, 0f));
        assertFalse(Blink.deny(1, 0.5f));
        assertTrue(Blink.deny(2, 0.5f));
    }

    private interface Pattern {
        boolean at(long t);
    }

    private static int countOn(Pattern p, int period) {
        int n = 0;
        for (long t = 0; t < period; t++) {
            if (p.at(t)) {
                n++;
            }
        }
        return n;
    }
}
