package za.co.neroland.nerosecurity.client.render.camera;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.Direction;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.block.camera.CameraGeometry;

/** The camera tilt timeline and pose. Plain JVM. */
class CameraTiltTest {

    private static final float EPS = 1.0e-4f;

    @Test
    void timelineEasesInHoldsAndEasesBack() {
        assertEquals(0f, CameraTilt.weight(-1.0D, true), EPS);
        assertEquals(0f, CameraTilt.weight(0.0D, true), EPS);
        assertEquals(0.5f, CameraTilt.weight(5.0D, true), EPS);
        assertEquals(1f, CameraTilt.weight(10.0D, true), EPS);
        assertEquals(1f, CameraTilt.weight(69.9D, true), EPS);
        assertEquals(0.5f, CameraTilt.weight(75.0D, true), EPS);
        assertEquals(0f, CameraTilt.weight(80.0D, true), EPS);
        assertEquals(0f, CameraTilt.weight(500.0D, true), EPS);
        float prev = 0f;
        for (int t = 0; t <= 10; t++) {
            float w = CameraTilt.weight(t, true);
            assertTrue(w >= prev - EPS);
            prev = w;
        }
    }

    @Test
    void stillFramesStepTheLook() {
        assertEquals(1f, CameraTilt.weight(3.0D, false), EPS);
        assertEquals(1f, CameraTilt.weight(69.0D, false), EPS);
        assertEquals(0f, CameraTilt.weight(70.0D, false), EPS);
    }

    @Test
    void elapsedNeedsALook() {
        assertEquals(-1.0D, CameraTilt.elapsed(false, 10L, 20L, 0f), 1.0e-9);
        assertEquals(-1.0D, CameraTilt.elapsed(true, Long.MIN_VALUE, 20L, 0f), 1.0e-9);
        assertEquals(10.5D, CameraTilt.elapsed(true, 10L, 20L, 0.5f), 1.0e-9);
    }

    @Test
    void panMatchesTheServerOnWholeTicks() {
        for (long t = 0; t < 200; t += 7) {
            assertEquals((float) CameraGeometry.panDegrees(t, 200), CameraTilt.pan(t, 0f, 200), EPS);
        }
    }

    @Test
    void lookAlongTheFacingIsNeutral() {
        // North = yaw 180, east = yaw -90 (Minecraft convention).
        assertArrayEquals(new float[] {0f, 0f, 0f}, CameraTilt.lookPose(Direction.NORTH, 180f, 0f), EPS);
        assertArrayEquals(new float[] {0f, 0f, 0f}, CameraTilt.lookPose(Direction.EAST, -90f, 0f), EPS);
    }

    @Test
    void lookTiltIsClamped() {
        float[] down = CameraTilt.lookPose(Direction.NORTH, 180f, 80f);
        assertEquals(-CameraTilt.MAX_PITCH, down[0], EPS);
        float[] right = CameraTilt.lookPose(Direction.NORTH, -90f, 0f); // due east of a north camera
        assertEquals(-CameraTilt.MAX_WALL_YAW, right[1], EPS);
    }

    @Test
    void sweepMatchesThePanDirection() {
        // A positive (clockwise) pan turns a north camera toward east: local yaw -pan.
        assertArrayEquals(new float[] {0f, -30f, 0f}, CameraTilt.sweepPose(Direction.NORTH, 30f), EPS);
        assertArrayEquals(new float[] {0f, 0f, 30f}, CameraTilt.sweepPose(Direction.UP, 30f), EPS);
    }

    @Test
    void unarmedCameraIsParked() {
        assertArrayEquals(new float[] {0f, 0f, 0f},
                CameraTilt.pose(Direction.SOUTH, 50L, 0f, 200, false, true, 45L, 10f, 10f, true), EPS);
    }

    @Test
    void heldLookWinsOverTheSweep() {
        float[] look = CameraTilt.lookPose(Direction.SOUTH, 20f, 15f);
        assertArrayEquals(look, CameraTilt.pose(Direction.SOUTH, 1030L, 0f, 200, true, true, 1000L, 20f, 15f, true), EPS);
        assertArrayEquals(CameraTilt.sweepPose(Direction.SOUTH, CameraTilt.pan(1100L, 0f, 200)),
                CameraTilt.pose(Direction.SOUTH, 1100L, 0f, 200, true, true, 1000L, 20f, 15f, true), EPS);
    }
}
