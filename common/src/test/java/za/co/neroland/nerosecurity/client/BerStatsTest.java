package za.co.neroland.nerosecurity.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** BerStats rolls frames on a new (gameTime, partial) pair and averages the closed window. */
class BerStatsTest {

    @BeforeEach
    @AfterEach
    void clear() {
        BerStats.reset();
    }

    @Test
    void countsRollIntoTheWindowPerFrame() {
        for (int f = 0; f < 10; f++) {
            BerStats.frame(100 + f, 0.25f);
            BerStats.frame(100 + f, 0.25f); // same frame: no roll
            BerStats.submitted();
            BerStats.submitted();
            BerStats.culled();
            BerStats.vertices(48);
            BerStats.vertices(16);
            BerStats.nanos(500_000);
        }
        BerStats.endFrame();
        BerStats.Snapshot a = BerStats.average();
        assertEquals(10, a.frames());
        assertEquals(2.0, a.submitted(), 1e-9);
        assertEquals(1.0, a.culled(), 1e-9);
        assertEquals(64.0, a.vertices(), 1e-9);
        assertEquals(0.5, a.millis(), 1e-9);
        assertEquals(64, a.peakVertices());
        assertTrue(BerStats.f3Line().startsWith("[NeroSecurity] BER 2/3 64 verts"), BerStats.f3Line());
        assertTrue(BerStats.record().contains("budget 60000"));
    }

    @Test
    void windowKeepsTheLastTwoHundredFrames() {
        for (int f = 0; f < BerStats.WINDOW + 50; f++) {
            BerStats.frame(f, 0f);
            BerStats.vertices(f < 50 ? 1000 : 10);
        }
        BerStats.endFrame();
        BerStats.Snapshot a = BerStats.average();
        assertEquals(BerStats.WINDOW, a.frames());
        assertEquals(10.0, a.vertices(), 1e-9);
    }

    @Test
    void emptyStatsAreZero() {
        assertEquals(0, BerStats.average().frames());
        BerStats.vertices(-5);
        BerStats.frame(1, 0f);
        BerStats.endFrame();
        assertEquals(0.0, BerStats.average().vertices(), 1e-9);
    }
}
