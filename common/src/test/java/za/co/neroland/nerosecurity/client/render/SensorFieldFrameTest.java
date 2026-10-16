package za.co.neroland.nerosecurity.client.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import net.minecraft.core.Direction;

import org.junit.jupiter.api.Test;

/** The authored-north → facing frame matches the blockstate rotations. Plain JVM. */
class SensorFieldFrameTest {

    private static final float EPS = 1.0e-5f;

    @Test
    void forwardMapsToEachFacing() {
        for (Direction d : Direction.values()) {
            float[] f = SensorFieldRenderers.Frame.dirToBlock(d, 0f, 0f, -1f);
            assertArrayEquals(new float[] {d.getStepX(), d.getStepY(), d.getStepZ()}, f, EPS, d.toString());
        }
    }

    @Test
    void blockstateRotationsCarryUpAndRight() {
        // y 90 (east): authored east (right) turns to south; x 270 (up): authored up turns to south; x 90 (down): north.
        assertArrayEquals(new float[] {0f, 0f, 1f}, SensorFieldRenderers.Frame.dirToBlock(Direction.EAST, 1f, 0f, 0f), EPS);
        assertArrayEquals(new float[] {0f, 0f, 1f}, SensorFieldRenderers.Frame.dirToBlock(Direction.UP, 0f, 1f, 0f), EPS);
        assertArrayEquals(new float[] {0f, 0f, -1f}, SensorFieldRenderers.Frame.dirToBlock(Direction.DOWN, 0f, 1f, 0f), EPS);
    }

    @Test
    void dirToLocalInvertsDirToBlock() {
        float[] v = {0.3f, -0.7f, 0.2f};
        for (Direction d : Direction.values()) {
            float[] w = SensorFieldRenderers.Frame.dirToBlock(d, v[0], v[1], v[2]);
            assertArrayEquals(v, SensorFieldRenderers.Frame.dirToLocal(d, w[0], w[1], w[2]), EPS, d.toString());
        }
    }

    @Test
    void pointsTurnAboutTheBlockCentre() {
        assertArrayEquals(new float[] {0.5f, 0.5f, 0.5f}, SensorFieldRenderers.Frame.toBlock(Direction.WEST, 0.5f, 0.5f, 0.5f), EPS);
        // The lens front (8, 8, 1) px of a south-facing node sits on the south side.
        assertArrayEquals(new float[] {0.5f, 0.5f, 0.9375f},
                SensorFieldRenderers.Frame.toBlock(Direction.SOUTH, 0.5f, 0.5f, 0.0625f), EPS);
    }
}
