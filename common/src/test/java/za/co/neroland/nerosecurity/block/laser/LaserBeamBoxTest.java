package za.co.neroland.nerosecurity.block.laser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import org.junit.jupiter.api.Test;

/** The beam volume. Plain JVM. */
class LaserBeamBoxTest {

    private static final double EPS = 1.0E-9;

    /** 16. */
    @Test
    void beamBoxIsThinAndSpansBothFaces() {
        BlockPos from = new BlockPos(0, 64, 0);
        BlockPos to = new BlockPos(5, 64, 0);
        AABB box = LaserPairing.beamBox(from, to);
        // Face to face along X: node faces at x = 1 and x = 5.
        assertEquals(1.0D, box.minX, EPS);
        assertEquals(5.0D, box.maxX, EPS);
        // 0.3 wide across, centred on the node centres.
        assertEquals(0.3D, box.maxY - box.minY, EPS);
        assertEquals(0.3D, box.maxZ - box.minZ, EPS);
        assertEquals(64.5D, (box.minY + box.maxY) / 2.0D, EPS);
        assertEquals(0.5D, (box.minZ + box.maxZ) / 2.0D, EPS);
        // Same box from either end.
        AABB reverse = LaserPairing.beamBox(to, from);
        assertEquals(box.minX, reverse.minX, EPS);
        assertEquals(box.maxX, reverse.maxX, EPS);
        assertEquals(box.minY, reverse.minY, EPS);
        assertEquals(box.maxZ, reverse.maxZ, EPS);
        // A player standing in the gap intersects it; one beside it does not; the nodes' own blocks do not.
        assertTrue(box.intersects(new AABB(2.7, 64.0, 0.2, 3.3, 65.8, 0.8)));
        assertFalse(box.intersects(new AABB(2.7, 64.0, 1.2, 3.3, 65.8, 1.8)));
        assertFalse(box.intersects(new AABB(0.0, 64.0, 0.0, 1.0, 65.0, 1.0)));
    }

    @Test
    void verticalBeamSpansYAndAdjacentNodesHaveNoLength() {
        AABB vertical = LaserPairing.beamBox(new BlockPos(2, 60, 2), new BlockPos(2, 70, 2));
        assertEquals(61.0D, vertical.minY, EPS);
        assertEquals(70.0D, vertical.maxY, EPS);
        assertEquals(0.3D, vertical.maxX - vertical.minX, EPS);
        assertEquals(0.3D, vertical.maxZ - vertical.minZ, EPS);

        AABB adjacent = LaserPairing.beamBox(new BlockPos(0, 64, 0), new BlockPos(1, 64, 0));
        assertEquals(0.0D, adjacent.maxX - adjacent.minX, EPS);
    }
}
