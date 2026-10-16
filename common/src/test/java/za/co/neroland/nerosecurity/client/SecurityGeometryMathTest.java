package za.co.neroland.nerosecurity.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** The pure maths behind the beam quads and rotated cubes. */
class SecurityGeometryMathTest {

    @Test
    void beamOffsetsArePerpendicularAndHalfWidthLong() {
        float[][] dirs = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}, {1, 1, 0}, {0.3f, -0.9f, 0.2f}, {-4, 2, 7}};
        for (float[] d : dirs) {
            float[][] o = SecurityGeometry.beamOffsets(0, 0, 0, d[0], d[1], d[2], 0.0625f);
            float len = (float) Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
            for (float[] v : o) {
                assertEquals(0.0625f, (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]), 1e-5f);
                assertEquals(0f, (v[0] * d[0] + v[1] * d[1] + v[2] * d[2]) / len, 1e-5f);
            }
            assertEquals(0f, o[0][0] * o[1][0] + o[0][1] * o[1][1] + o[0][2] * o[1][2], 1e-6f);
        }
        assertNull(SecurityGeometry.beamOffsets(1, 2, 3, 1, 2, 3, 0.1f));
    }

    @Test
    void rotateFollowsTheRightHandRule() {
        float[] r = SecurityGeometry.rotate(1, 0, 0, 0, 90, 0); // +X about +Y -> -Z
        assertEquals(0f, r[0], 1e-6f);
        assertEquals(-1f, r[2], 1e-6f);
        r = SecurityGeometry.rotate(0, 1, 0, 90, 0, 0); // +Y about +X -> +Z
        assertEquals(1f, r[2], 1e-6f);
        r = SecurityGeometry.rotate(1, 0, 0, 0, 0, 90); // +X about +Z -> +Y
        assertEquals(1f, r[1], 1e-6f);
        assertEquals(16, SecurityGeometry.FACE_VERTICES * 2);
        assertEquals(48, SecurityGeometry.BOX_VERTICES);
        assertEquals(0.5f, SecurityGeometry.px(8), 1e-6f);
    }
}
