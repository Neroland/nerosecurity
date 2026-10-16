package za.co.neroland.nerosecurity.block.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.junit.jupiter.api.Test;

/** The camera's pure geometry: cone, pan, search box and the nearest-first raycast cap. */
class CameraGeometryTest {

    private static final double EPS = 1.0E-9D;
    private static final Vec3 ORIGIN = Vec3.ZERO;
    private static final Vec3 NORTH = new Vec3(0, 0, -1);

    /** 13. */
    @Test
    void coneAngleAndRange() {
        // FOV 90: half-angle 45 degrees.
        assertTrue(CameraGeometry.inCone(ORIGIN, NORTH, new Vec3(0, 0, -10), 16, 90), "straight ahead");
        assertTrue(CameraGeometry.inCone(ORIGIN, NORTH, new Vec3(4, 0, -5), 16, 90), "about 39 degrees off");
        assertFalse(CameraGeometry.inCone(ORIGIN, NORTH, new Vec3(6, 0, -5), 16, 90), "about 50 degrees off");
        assertTrue(CameraGeometry.inCone(ORIGIN, NORTH, new Vec3(6, 0, -5), 16, 120), "inside a 120 cone");
        assertFalse(CameraGeometry.inCone(ORIGIN, NORTH, new Vec3(4, 0, -5), 16, 60), "outside a 60 cone");
        assertFalse(CameraGeometry.inCone(ORIGIN, NORTH, new Vec3(0, 0, 5), 16, 120), "behind");
        // Range.
        assertTrue(CameraGeometry.inCone(ORIGIN, NORTH, new Vec3(0, 0, -16), 16, 90), "exactly at range");
        assertFalse(CameraGeometry.inCone(ORIGIN, NORTH, new Vec3(0, 0, -16.01), 16, 90), "beyond range");
        assertTrue(CameraGeometry.inCone(ORIGIN, NORTH, ORIGIN, 16, 60), "at the lens");
    }

    /** 13 (pan). */
    @Test
    void panStartsAtFacingIsBoundedAndPeriodic() {
        int sweep = 200;
        assertEquals(0.0D, CameraGeometry.panDegrees(0, sweep), EPS);
        assertEquals(45.0D, CameraGeometry.panDegrees(50, sweep), EPS);
        assertEquals(-45.0D, CameraGeometry.panDegrees(150, sweep), EPS);
        for (long t = -400; t <= 1200; t += 7) {
            double pan = CameraGeometry.panDegrees(t, sweep);
            assertTrue(Math.abs(pan) <= CameraGeometry.PAN_HALF_ARC_DEGREES + EPS);
            assertEquals(pan, CameraGeometry.panDegrees(t + sweep, sweep), EPS, "periodic at " + t);
            assertEquals(pan, CameraGeometry.panDegrees(t + 5L * sweep, sweep), EPS);
        }
        assertEquals(0.0D, CameraGeometry.panDegrees(123, 0), EPS, "no sweep, no pan");

        for (Direction facing : Direction.values()) {
            Vec3 atZero = CameraGeometry.viewDirection(facing, 0, sweep);
            assertVec(new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ()), atZero);
            assertVec(atZero, CameraGeometry.viewDirection(facing, sweep * 3L, sweep));
        }
        // A north camera at full right pan looks north-east (clockwise from above), still horizontal and unit.
        Vec3 right = CameraGeometry.viewDirection(Direction.NORTH, 50, sweep);
        assertVec(new Vec3(Math.sqrt(0.5D), 0, -Math.sqrt(0.5D)), right);
        // Ceiling and floor cameras keep their vertical axis.
        assertVec(new Vec3(0, -1, 0), CameraGeometry.viewDirection(Direction.DOWN, 50, sweep));
    }

    @Test
    void searchBoxIsClippedToTheFrontHalfSpace() {
        BlockPos pos = new BlockPos(10, 64, 10);
        for (Direction facing : Direction.values()) {
            Vec3 lens = CameraGeometry.lens(pos, facing);
            AABB box = CameraGeometry.searchBox(lens, facing, 16);
            Vec3 ahead = lens.add(facing.getStepX() * 8.0D, facing.getStepY() * 8.0D, facing.getStepZ() * 8.0D);
            Vec3 behind = lens.add(facing.getStepX() * -1.0D, facing.getStepY() * -1.0D, facing.getStepZ() * -1.0D);
            assertTrue(box.contains(ahead), facing + " ahead");
            assertFalse(box.contains(behind), facing + " behind the mount");
            assertFalse(box.contains(new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)),
                    facing + " the camera block itself");
        }
        // The lens sits just outside the camera's own block.
        Vec3 lens = CameraGeometry.lens(pos, Direction.EAST);
        assertTrue(lens.x > pos.getX() + 1.0D);
    }

    /** 14. */
    @Test
    void candidatesAreCappedNearestFirst() {
        List<Double> distances = List.of(25.0D, 4.0D, 100.0D, 1.0D, 49.0D, 9.0D, 16.0D, 64.0D, 36.0D, 81.0D);
        List<Double> picked = CameraGeometry.nearest(distances, d -> d, 8);
        assertEquals(List.of(1.0D, 4.0D, 9.0D, 16.0D, 25.0D, 36.0D, 49.0D, 64.0D), picked);
        assertEquals(List.of(1.0D), CameraGeometry.nearest(distances, d -> d, 1));
        assertEquals(3, CameraGeometry.nearest(List.of(3.0D, 2.0D, 1.0D), d -> d, 32).size());
        assertTrue(CameraGeometry.nearest(distances, d -> d, 0).isEmpty());
        assertTrue(CameraGeometry.nearest(List.<Double>of(), d -> d, 8).isEmpty());
    }

    @Test
    void lookAnglesFollowMinecraftConventions() {
        assertEquals(0.0F, CameraGeometry.yawOf(new Vec3(0, 0, 1)), 1.0E-4F, "south");
        assertEquals(90.0F, CameraGeometry.yawOf(new Vec3(-1, 0, 0)), 1.0E-4F, "west");
        assertEquals(90.0F, CameraGeometry.pitchOf(new Vec3(0, -1, 0)), 1.0E-4F, "straight down");
        assertEquals(0.0F, CameraGeometry.pitchOf(new Vec3(1, 0, 0)), 1.0E-4F, "level");
    }

    private static void assertVec(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, 1.0E-9D);
        assertEquals(expected.y, actual.y, 1.0E-9D);
        assertEquals(expected.z, actual.z, 1.0E-9D);
    }
}
