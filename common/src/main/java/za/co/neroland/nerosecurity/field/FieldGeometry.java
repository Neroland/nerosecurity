package za.co.neroland.nerosecurity.field;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Where a field's blocks go. Pure: no level access. Distances are Chebyshev (the largest of the three axis
 * distances) from the emitter, so the answer does not depend on which way the emitter faces.
 *
 * <ul>
 *   <li>{@link FieldShape#PLANE}: a {@code width × height} wall perpendicular to the facing, centred on
 *       {@code emitter + facing × offset}. Width runs along {@code facing.getClockWise()} (X for up/down), height
 *       along up (Z for up/down). {@code depth} is ignored.</li>
 *   <li>{@link FieldShape#BOX}: the hollow shell of a {@code width × height × depth} box centred on the same
 *       point, depth along the facing.</li>
 * </ul>
 */
public final class FieldGeometry {

    public static final String INVALID_SHAPE = "field.nerosecurity.invalid.shape";
    public static final String INVALID_RANGE = "field.nerosecurity.invalid.range";
    public static final String INVALID_AREA = "field.nerosecurity.invalid.area";

    private FieldGeometry() {
    }

    /**
     * Whether {@code s} may be projected by an emitter with {@code range} under a cap of {@code maxArea} blocks.
     * Dimensions must be odd and at least 1 (a box's depth at least 3) and the offset at least 1; every block must
     * lie within {@code range} of the emitter; the geometric block count must not exceed {@code maxArea}.
     *
     * @return empty when valid, else the translation key of the first rule broken
     *         ({@link #INVALID_SHAPE}, {@link #INVALID_RANGE}, {@link #INVALID_AREA}, checked in that order)
     */
    public static Optional<String> validate(FieldSettings s, int range, int maxArea) {
        if (s == null || s.offset() < 1 || !oddPositive(s.width()) || !oddPositive(s.height())
                || !oddPositive(s.depth()) || (s.shape() == FieldShape.BOX && s.depth() < 3)) {
            return Optional.of(INVALID_SHAPE);
        }
        if (reach(s) > range) {
            return Optional.of(INVALID_RANGE);
        }
        if (countLong(s) > maxArea) {
            return Optional.of(INVALID_AREA);
        }
        return Optional.empty();
    }

    /** The farthest (Chebyshev) any block of {@code s} lies from its emitter. */
    public static long reach(FieldSettings s) {
        long along = s.offset() + (s.shape() == FieldShape.BOX ? half(s.depth()) : 0L);
        return Math.max(along, Math.max(half(s.width()), half(s.height())));
    }

    /** The geometric block count (before any position is skipped): PLANE w×h; BOX w·h·d − (w−2)(h−2)(d−2). */
    public static int count(FieldSettings s) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, countLong(s)));
    }

    private static long countLong(FieldSettings s) {
        long w = Math.max(0, s.width());
        long h = Math.max(0, s.height());
        if (s.shape() == FieldShape.PLANE) {
            return w * h;
        }
        long d = Math.max(0, s.depth());
        return w * h * d - Math.max(0L, w - 2) * Math.max(0L, h - 2) * Math.max(0L, d - 2);
    }

    /**
     * Every position of the field, in a deterministic order, never including {@code emitter} itself. Call only
     * with settings that passed {@link #validate} (an invalid one yields an empty list).
     */
    public static List<BlockPos> positions(BlockPos emitter, Direction facing, FieldSettings s) {
        if (emitter == null || facing == null || s == null || validate(s, Integer.MAX_VALUE, Integer.MAX_VALUE).isPresent()) {
            return List.of();
        }
        boolean vertical = facing.getAxis() == Direction.Axis.Y;
        Direction widthDir = vertical ? Direction.EAST : facing.getClockWise();
        Direction heightDir = vertical ? Direction.SOUTH : Direction.UP;
        BlockPos centre = emitter.relative(facing, s.offset());
        int hw = half(s.width());
        int hh = half(s.height());
        int hd = s.shape() == FieldShape.BOX ? half(s.depth()) : 0;
        List<BlockPos> out = new ArrayList<>(count(s));
        for (int t = -hd; t <= hd; t++) {
            for (int v = -hh; v <= hh; v++) {
                for (int u = -hw; u <= hw; u++) {
                    if (s.shape() == FieldShape.BOX && Math.abs(u) != hw && Math.abs(v) != hh && Math.abs(t) != hd) {
                        continue; // inside the shell
                    }
                    BlockPos p = centre.relative(widthDir, u).relative(heightDir, v).relative(facing, t);
                    if (!p.equals(emitter)) {
                        out.add(p);
                    }
                }
            }
        }
        return out;
    }

    /** Chebyshev distance between two positions. */
    public static int chebyshev(BlockPos a, BlockPos b) {
        return Math.max(Math.abs(a.getX() - b.getX()), Math.max(Math.abs(a.getY() - b.getY()), Math.abs(a.getZ() - b.getZ())));
    }

    private static boolean oddPositive(int n) {
        return n >= 1 && (n & 1) == 1;
    }

    private static int half(int n) {
        return Math.max(0, (n - 1) / 2);
    }
}
