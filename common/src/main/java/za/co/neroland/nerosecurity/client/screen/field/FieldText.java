package za.co.neroland.nerosecurity.client.screen.field;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.block.DeviceSettingsRules;
import za.co.neroland.nerosecurity.client.widget.Status;
import za.co.neroland.nerosecurity.field.FieldGeometry;
import za.co.neroland.nerosecurity.field.FieldSettings;
import za.co.neroland.nerosecurity.field.FieldShape;
import za.co.neroland.nerosecurity.field.FieldState;

/**
 * The force field screen's pure rules: the status row, the lock reasons, the stepper bounds (the largest
 * value of one dimension that still fits the emitter's range and the area cap, with the others as they are)
 * and the odd-number rule for sizes. Display courtesy only — {@link FieldGeometry#validate} runs again on the
 * server. No Minecraft client classes, so it is plain-JVM tested.
 */
public final class FieldText {

    public static final String OWNER_ONLY_KEY = "door.nerosecurity.owner_only_setting";
    public static final String UNLINKED_KEY = "device.nerosecurity.unlinked";
    public static final String SERVER_DISABLED_KEY = "field.nerosecurity.disabled_server";
    public static final String LOCKDOWN_KEY = "field.nerosecurity.state.lockdown";
    /** "%s — strength %s/%s, %s/%s blocks, %s/t, load %s%%" for a projecting field. */
    public static final String STATUS_KEY = "field.nerosecurity.status";

    /** Which stepper. */
    public enum Dim {
        OFFSET,
        WIDTH,
        HEIGHT,
        DEPTH
    }

    /**
     * The status row. {@code detailed} means the sentence is {@link #STATUS_KEY} filled with the live numbers;
     * otherwise {@code key} is a one-word state. {@code cooldown} marks the overloaded state, whose key takes
     * the seconds left.
     */
    public record Row(Status status, String key, boolean detailed, boolean cooldown) {
    }

    private FieldText() {
    }

    /** Which row describes the emitter now: server-disabled → unlinked → lockdown → the live state. */
    public static Row state(boolean serverEnabled, boolean linked, boolean lockdown, @Nullable FieldState state) {
        if (!serverEnabled) {
            return new Row(Status.WARN, SERVER_DISABLED_KEY, false, false);
        }
        if (!linked) {
            return new Row(Status.OFF, UNLINKED_KEY, false, false);
        }
        if (lockdown) {
            return new Row(Status.BAD, LOCKDOWN_KEY, false, false);
        }
        FieldState s = state == null ? FieldState.IDLE : state;
        return switch (s) {
            case PROJECTING -> new Row(Status.OK, s.translationKey(), true, false);
            case BROWNOUT -> new Row(Status.WARN, s.translationKey(), false, false);
            case OVERLOADED -> new Row(Status.BAD, s.translationKey(), false, true);
            case IDLE -> new Row(Status.OFF, s.translationKey(), false, false);
        };
    }

    /**
     * Why every setting control is locked, or {@code null} when the viewer may edit: fields are off on this
     * server, the emitter is unlinked, or it is already at OWNER and the viewer is not an owner / operator.
     */
    public static @Nullable String editWhy(boolean serverEnabled, boolean linked, AccessLevel minLevel,
                                           boolean ownerOrAdmin) {
        if (!serverEnabled) {
            return SERVER_DISABLED_KEY;
        }
        if (!linked) {
            return UNLINKED_KEY;
        }
        if (!DeviceSettingsRules.mayApply(ownerOrAdmin, minLevel, minLevel)) {
            return OWNER_ONLY_KEY;
        }
        return null;
    }

    /** The lowest value a dimension may take: distance 1, sizes 1, a shell's depth 3. */
    public static int min(Dim dim, FieldShape shape) {
        return dim == Dim.DEPTH && shape == FieldShape.BOX ? 3 : 1;
    }

    /** The current value of {@code dim} in {@code s}. */
    public static int value(FieldSettings s, Dim dim) {
        return switch (dim) {
            case OFFSET -> s.offset();
            case WIDTH -> s.width();
            case HEIGHT -> s.height();
            case DEPTH -> s.depth();
        };
    }

    /** {@code s} with {@code dim} set to {@code v} (no validation). */
    public static FieldSettings with(FieldSettings s, Dim dim, int v) {
        return switch (dim) {
            case OFFSET -> s.withOffset(v);
            case WIDTH -> s.withSize(v, s.height(), s.depth());
            case HEIGHT -> s.withSize(s.width(), v, s.depth());
            case DEPTH -> s.withSize(s.width(), s.height(), v);
        };
    }

    /**
     * The largest value of {@code dim} (odd for a size) that still passes {@link FieldGeometry#validate} with the
     * other dimensions as in {@code s}; never below {@link #min}. A shell's depth is left as is for a wall.
     */
    public static int max(FieldSettings s, Dim dim, int range, int maxArea) {
        int lo = min(dim, s.shape());
        int step = dim == Dim.OFFSET ? 1 : 2;
        int cap = Math.max(lo, 2 * Math.max(0, range) + 1);
        int best = lo;
        for (int v = lo; v <= cap; v += step) {
            if (FieldGeometry.validate(with(s, dim, v), range, maxArea).isEmpty()) {
                best = v;
            } else if (v > lo) {
                break; // reach and count only grow with the value
            }
        }
        return best;
    }

    /**
     * Snaps a stepped size to an odd number in the direction it moved: an even {@code v} above {@code previous}
     * rounds up, otherwise down (never below 1). A distance is returned unchanged.
     */
    public static int snap(Dim dim, int v, int previous) {
        if (dim == Dim.OFFSET || (v & 1) == 1) {
            return Math.max(1, v);
        }
        return Math.max(1, v > previous ? v + 1 : v - 1);
    }

    /** The one {@code %s} of a validation key: the range for the range rule, the cap for the area rule. */
    public static @Nullable Integer invalidArgument(String key, int range, int maxArea) {
        if (FieldGeometry.INVALID_RANGE.equals(key)) {
            return range;
        }
        if (FieldGeometry.INVALID_AREA.equals(key)) {
            return maxArea;
        }
        return null;
    }
}
