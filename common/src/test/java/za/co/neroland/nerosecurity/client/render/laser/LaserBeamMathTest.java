package za.co.neroland.nerosecurity.client.render.laser;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

import org.junit.jupiter.api.Test;

/** Beam half geometry, its box, pulse, scroll and trip strobe. Plain JVM. */
class LaserBeamMathTest {

    private static final float EPS = 1.0e-5f;

    @Test
    void halfRunsFromTheLensToTheMidpoint() {
        // Node facing north, partner 6 blocks north facing south: lens fronts at z = 1/16 and -6 + 15/16.
        float[] s = LaserBeamMath.halfSegment(Direction.NORTH, 0, 0, -6);
        assertArrayEquals(new float[] {0.5f, 0.5f, 0.0625f}, new float[] {s[0], s[1], s[2]}, EPS);
        float partnerLens = -6f + 0.9375f;
        assertArrayEquals(new float[] {0.5f, 0.5f, (0.0625f + partnerLens) / 2f}, new float[] {s[3], s[4], s[5]}, EPS);
    }

    @Test
    void bothHalvesMeetAtTheSameMidpoint() {
        BlockPos a = new BlockPos(10, 64, 10);
        BlockPos b = new BlockPos(18, 64, 10);
        float[] ha = LaserBeamMath.halfSegment(Direction.EAST, b.getX() - a.getX(), 0, 0);
        float[] hb = LaserBeamMath.halfSegment(Direction.WEST, a.getX() - b.getX(), 0, 0);
        assertEquals(a.getX() + ha[3], b.getX() + hb[3], EPS);
        assertEquals(a.getY() + ha[4], b.getY() + hb[4], EPS);
        assertEquals(a.getZ() + ha[5], b.getZ() + hb[5], EPS);
    }

    @Test
    void boxCoversOnlyItsOwnHalf() {
        BlockPos a = new BlockPos(0, 64, 0);
        BlockPos b = new BlockPos(0, 64, -8);
        AABB box = LaserBeamMath.box(a, Direction.NORTH, b);
        float[] s = LaserBeamMath.halfSegment(Direction.NORTH, 0, 0, -8);
        assertEquals(s[5] - 0.3D, box.minZ, 1.0e-5);
        assertEquals(s[2] + 0.3D, box.maxZ, 1.0e-5);
        assertTrue(box.minZ > -8.0D + 4.0D - 0.5D, "never reaches into the partner's half");
        assertEquals(new AABB(a), LaserBeamMath.box(a, Direction.NORTH, null));
    }

    @Test
    void pulseAndScrollStayInRange() {
        for (int t = 0; t < 80; t++) {
            float p = LaserBeamMath.pulse(t, 0.5f, true);
            assertTrue(p >= 0f && p <= 1f);
            float u = LaserBeamMath.uStart(t, 0.5f, true);
            assertTrue(u >= 0f && u + 0.5f <= 1f + EPS);
        }
        assertEquals(LaserBeamMath.pulse(3, 0f, true), LaserBeamMath.pulse(43, 0f, true), EPS);
        assertEquals(1f, LaserBeamMath.pulse(17, 0.3f, false), EPS);
        assertEquals(0f, LaserBeamMath.uStart(17, 0.3f, false), EPS);
    }

    @Test
    void tripStrobesAtFourHertzForFortyTicks() {
        long trip = 1000L;
        assertTrue(LaserBeamMath.tripping(trip, 1000L, 0f));
        assertTrue(LaserBeamMath.tripping(trip, 1039L, 0.9f));
        assertFalse(LaserBeamMath.tripping(trip, 1040L, 0f));
        assertFalse(LaserBeamMath.tripping(trip, 999L, 0f));
        assertFalse(LaserBeamMath.tripping(Long.MIN_VALUE, 5L, 0f));
        assertTrue(LaserBeamMath.tripWhite(trip, 1000L, 0f, true));
        assertTrue(LaserBeamMath.tripWhite(trip, 1002L, 0f, true));
        assertFalse(LaserBeamMath.tripWhite(trip, 1003L, 0f, true));
        assertTrue(LaserBeamMath.tripWhite(trip, 1005L, 0f, true));
        assertFalse(LaserBeamMath.tripWhite(trip, 1002L, 0f, false), "still frames do not strobe");
    }
}
