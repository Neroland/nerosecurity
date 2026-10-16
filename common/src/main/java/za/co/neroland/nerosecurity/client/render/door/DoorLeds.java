package za.co.neroland.nerosecurity.client.render.door;

import za.co.neroland.nerosecurity.client.SecurityColours;
import za.co.neroland.nerosecurity.client.anim.Blink;

/**
 * The four-blink grammar applied to a device's status and recording LEDs. Pure: returns {@code 0xRRGGBB}, or
 * {@link #OFF} when the LED is dark this frame. With animations off every blink holds steady on (a still frame,
 * and no flashing for players who turned motion off).
 *
 * <p>Priority, highest first: the deny flash ({@code LOCK} at 8 Hz for 10 ticks), sealed / lockdown ({@code LOCK}
 * strobe), unpowered ({@code WARN} blink), then the level colour steady.
 */
public final class DoorLeds {

    /** The LED is dark this frame. */
    public static final int OFF = -1;

    private DoorLeds() {
    }

    /** The status LED colour. */
    public static int status(int levelRgb, boolean sealed, boolean powerLow, boolean flashing, boolean flashOn,
            long gameTime, boolean animate) {
        if (flashing) {
            return !animate || flashOn ? SecurityColours.LOCK : OFF;
        }
        if (sealed) {
            return !animate || Blink.lockdown(gameTime) ? SecurityColours.LOCK : OFF;
        }
        if (powerLow) {
            return !animate || Blink.warning(gameTime) ? SecurityColours.WARN : OFF;
        }
        return levelRgb & 0xFFFFFF;
    }

    /** The recording LED colour: {@code REC} on the 1 Hz recording blink while recording, else dark. */
    public static int recording(boolean recording, long gameTime, boolean animate) {
        if (!recording) {
            return OFF;
        }
        return !animate || Blink.recording(gameTime) ? SecurityColours.REC : OFF;
    }

    /** The lockdown strip / strobe: {@code LOCK} on the 4 Hz strobe while active, else dark. */
    public static int lockdown(boolean active, long gameTime, boolean animate) {
        if (!active) {
            return OFF;
        }
        return !animate || Blink.lockdown(gameTime) ? SecurityColours.LOCK : OFF;
    }
}
