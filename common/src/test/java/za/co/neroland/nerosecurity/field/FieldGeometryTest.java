package za.co.neroland.nerosecurity.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.block.DeviceAccessSettings;

/** Where a field's blocks go (test 15). Pure. */
class FieldGeometryTest {

    private static final BlockPos EMITTER = BlockPos.ZERO;

    private static FieldSettings plane(int offset, int w, int h) {
        return new FieldSettings(FieldShape.PLANE, offset, w, h, 1, DeviceAccessSettings.DEFAULT, true);
    }

    private static FieldSettings box(int offset, int w, int h, int d) {
        return new FieldSettings(FieldShape.BOX, offset, w, h, d, DeviceAccessSettings.DEFAULT, true);
    }

    private static Map<Integer, Integer> distanceHistogram(List<BlockPos> positions) {
        Map<Integer, Integer> histogram = new TreeMap<>();
        for (BlockPos p : positions) {
            histogram.merge(FieldGeometry.chebyshev(EMITTER, p), 1, Integer::sum);
        }
        return histogram;
    }

    @Test
    void planeAndBoxPositions() {
        // PLANE 5x5, offset 2, facing north: 25 distinct blocks in the plane z = -2, never the emitter.
        List<BlockPos> wall = FieldGeometry.positions(EMITTER, Direction.NORTH, plane(2, 5, 5));
        assertEquals(25, wall.size());
        assertEquals(25, new HashSet<>(wall).size(), "no duplicates");
        for (BlockPos p : wall) {
            assertEquals(-2, p.getZ(), "a north wall lies in z = -2");
            assertTrue(Math.abs(p.getX()) <= 2 && Math.abs(p.getY()) <= 2);
            assertFalse(p.equals(EMITTER));
        }
        assertEquals(wall, FieldGeometry.positions(EMITTER, Direction.NORTH, plane(2, 5, 5)), "deterministic order");

        // BOX 5x5x5: the hollow shell, 125 - 27 = 98.
        List<BlockPos> shell = FieldGeometry.positions(EMITTER, Direction.NORTH, box(3, 5, 5, 5));
        assertEquals(98, shell.size());
        assertEquals(98, FieldGeometry.count(box(3, 5, 5, 5)));
        assertEquals(98, new HashSet<>(shell).size());
        assertFalse(shell.contains(new BlockPos(0, 0, -3)), "the centre is hollow");

        // An UP-facing plane lies in the XZ plane at y = offset.
        List<BlockPos> roof = FieldGeometry.positions(EMITTER, Direction.UP, plane(2, 5, 3));
        assertEquals(15, roof.size());
        for (BlockPos p : roof) {
            assertEquals(2, p.getY());
        }

        // Every block lies within the reach the validator checks against the range.
        FieldSettings big = box(4, 7, 5, 5);
        int reach = (int) FieldGeometry.reach(big);
        assertTrue(FieldGeometry.validate(big, reach, 4096).isEmpty());
        assertTrue(FieldGeometry.validate(big, reach - 1, 4096).isPresent(), "one less range refuses it");
        for (Direction facing : Direction.values()) {
            for (BlockPos p : FieldGeometry.positions(EMITTER, facing, big)) {
                assertTrue(FieldGeometry.chebyshev(EMITTER, p) <= reach);
                assertFalse(p.equals(EMITTER));
            }
        }

        // A shell hugging the emitter never contains it.
        assertFalse(FieldGeometry.positions(EMITTER, Direction.EAST, box(1, 3, 3, 3)).contains(EMITTER));

        // Rotation symmetry: every facing gives the same multiset of distances.
        for (FieldSettings s : List.of(plane(2, 5, 5), plane(3, 7, 3), box(3, 5, 5, 5), box(4, 7, 5, 3))) {
            Map<Integer, Integer> reference = distanceHistogram(FieldGeometry.positions(EMITTER, Direction.NORTH, s));
            for (Direction facing : Direction.values()) {
                assertEquals(reference, distanceHistogram(FieldGeometry.positions(EMITTER, facing, s)),
                        "same distances facing " + facing + " for " + s);
            }
        }
    }
}
