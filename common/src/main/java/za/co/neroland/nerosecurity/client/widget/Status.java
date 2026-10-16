package za.co.neroland.nerosecurity.client.widget;

import za.co.neroland.nerosecurity.client.SecurityColours;
import za.co.neroland.nerosecurity.menu.DeviceStates.DeviceState;

/**
 * The four status words a {@link StatusDot} can show. Each state has its own <b>shape</b> as well as its own
 * colour, so colour is never the only signal: OK is a filled square, WARN a hollow square with a dot, OFF an
 * outline only, BAD a cross.
 *
 * <p>Pure Java (no Minecraft client classes), so the shape and colour table is unit-testable. Display only:
 * a status never decides anything.
 */
public enum Status {
    /** Linked and working. Filled green square. */
    OK(Shape.FILLED_SQUARE, SecurityColours.OK),
    /** Needs attention (unpowered, unpaired, an alarm going off). Hollow amber square with a centre dot. */
    WARN(Shape.HOLLOW_WITH_DOT, SecurityColours.WARN),
    /** Off, idle or not loaded. Grey outline only. */
    OFF(Shape.OUTLINE, SecurityColours.OFF),
    /** Broken, overloaded or sealed by lockdown. Red cross. */
    BAD(Shape.CROSS, SecurityColours.LOCK);

    /** The glyph drawn for a state. Every state has a different one. */
    public enum Shape {
        FILLED_SQUARE,
        HOLLOW_WITH_DOT,
        OUTLINE,
        CROSS
    }

    private final Shape shape;
    private final int colour;

    Status(Shape shape, int colour) {
        this.shape = shape;
        this.colour = colour;
    }

    public Shape shape() {
        return shape;
    }

    /** {@code 0xRRGGBB}. */
    public int colour() {
        return colour;
    }

    /** {@code 0xFFRRGGBB}, ready for {@code fill}. */
    public int argb() {
        return SecurityColours.opaque(colour);
    }

    /**
     * The status a hub or monitor device row shows for a server-computed {@link DeviceState}. The row's state
     * word (device.nerosecurity.state.*) is drawn next to it, so the word carries the detail.
     */
    public static Status of(DeviceState state) {
        if (state == null) {
            return OFF;
        }
        return switch (state) {
            case OK -> OK;
            case UNPOWERED, UNPAIRED, ACTIVE -> WARN;
            case IDLE, UNLOADED -> OFF;
            case SEALED, OVERLOADED -> BAD;
        };
    }
}
