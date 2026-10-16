package za.co.neroland.nerosecurity.block.camera;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The camera's geometry as pure functions: the deterministic pan, the view direction, the lens
 * position, the search box and the cone test. The server's scan and the client's renderer call the
 * same functions, so what a camera appears to look at is exactly what it senses. Nothing here touches
 * a level, an entity or a clock; game time is passed in.
 *
 * <p>Pan: {@code 45 deg x sin(2 pi x (gameTime mod sweepTicks) / sweepTicks)}. A wall camera turns
 * that angle left and right around the vertical axis; a ceiling or floor camera turns the same angle
 * around the vertical axis too, which spins its housing but leaves its (vertical) view axis where it is.
 */
public final class CameraGeometry {

    /** Half the pan arc in degrees: the camera turns this far either side of {@code FACING}. */
    public static final double PAN_HALF_ARC_DEGREES = 45.0D;

    /** How far in front of the block centre the lens sits (just past the front face). */
    public static final double LENS_OFFSET = 0.55D;

    private CameraGeometry() {
    }

    /**
     * The pan angle in degrees at {@code gameTime}: 0 at the start of each cycle, periodic with
     * {@code sweepTicks}, within plus or minus {@link #PAN_HALF_ARC_DEGREES}. A non-positive sweep
     * length means no pan.
     */
    public static double panDegrees(long gameTime, int sweepTicks) {
        if (sweepTicks <= 0) {
            return 0.0D;
        }
        long phase = Math.floorMod(gameTime, (long) sweepTicks);
        return PAN_HALF_ARC_DEGREES * Math.sin(2.0D * Math.PI * phase / sweepTicks);
    }

    /**
     * The unit view direction of a camera facing {@code facing} at {@code gameTime}. Horizontal
     * facings are rotated by {@link #panDegrees} around the vertical axis (positive = clockwise seen
     * from above); vertical facings keep their axis.
     */
    public static Vec3 viewDirection(Direction facing, long gameTime, int sweepTicks) {
        Vec3 base = new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        if (facing.getAxis() == Direction.Axis.Y) {
            return base;
        }
        return rotateAroundY(base, panDegrees(gameTime, sweepTicks));
    }

    /** {@code v} rotated by {@code degrees} around the vertical axis (clockwise seen from above). */
    public static Vec3 rotateAroundY(Vec3 v, double degrees) {
        double rad = Math.toRadians(degrees);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        // Clockwise from above in Minecraft's axes (+X east, +Z south): north (0,0,-1) turns toward east.
        return new Vec3(v.x * cos - v.z * sin, v.y, v.x * sin + v.z * cos);
    }

    /** The lens: just in front of the block centre along {@code facing}, so a ray never hits the camera itself. */
    public static Vec3 lens(BlockPos pos, Direction facing) {
        return new Vec3(pos.getX() + 0.5D + facing.getStepX() * LENS_OFFSET,
                pos.getY() + 0.5D + facing.getStepY() * LENS_OFFSET,
                pos.getZ() + 0.5D + facing.getStepZ() * LENS_OFFSET);
    }

    /**
     * The entity query box: a cube of side {@code 2 x range} centred on the lens, clipped to the
     * half-space in front of {@code facing}. It covers every part of the cone that lies in front of the
     * mounting plane, at every pan angle and FOV; nothing behind the mount (the wall) is ever sensed.
     */
    public static AABB searchBox(Vec3 lens, Direction facing, double range) {
        double minX = lens.x - range;
        double minY = lens.y - range;
        double minZ = lens.z - range;
        double maxX = lens.x + range;
        double maxY = lens.y + range;
        double maxZ = lens.z + range;
        switch (facing) {
            case EAST -> minX = lens.x;
            case WEST -> maxX = lens.x;
            case UP -> minY = lens.y;
            case DOWN -> maxY = lens.y;
            case SOUTH -> minZ = lens.z;
            case NORTH -> maxZ = lens.z;
            default -> {
            }
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * The cone test: {@code target} is within {@code range} of {@code lens} and the angle between
     * {@code (target - lens)} and {@code view} is at most half of {@code fovDegrees}. A target at the lens
     * itself counts as inside. {@code view} must be a unit vector.
     */
    public static boolean inCone(Vec3 lens, Vec3 view, Vec3 target, double range, int fovDegrees) {
        double dx = target.x - lens.x;
        double dy = target.y - lens.y;
        double dz = target.z - lens.z;
        double distSq = dx * dx + dy * dy + dz * dz;
        if (distSq > range * range) {
            return false;
        }
        if (distSq < 1.0E-12D) {
            return true;
        }
        double dist = Math.sqrt(distSq);
        double dot = (dx * view.x + dy * view.y + dz * view.z) / dist;
        return dot >= Math.cos(Math.toRadians(fovDegrees / 2.0D)) - 1.0E-9D;
    }

    /**
     * At most {@code cap} of {@code candidates}, nearest first by {@code distanceSq}. These are the only
     * candidates a scan spends a line-of-sight raycast on.
     */
    public static <T> List<T> nearest(List<T> candidates, ToDoubleFunction<T> distanceSq, int cap) {
        if (candidates.isEmpty() || cap <= 0) {
            return List.of();
        }
        List<T> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingDouble(distanceSq));
        return sorted.size() <= cap ? sorted : List.copyOf(sorted.subList(0, cap));
    }

    /** The yaw in degrees (Minecraft convention: 0 = south, 90 = west) of a direction. */
    public static float yawOf(Vec3 direction) {
        return (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
    }

    /** The pitch in degrees (Minecraft convention: positive looks down) of a direction. */
    public static float pitchOf(Vec3 direction) {
        double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        return (float) Math.toDegrees(-Math.atan2(direction.y, horizontal));
    }
}
