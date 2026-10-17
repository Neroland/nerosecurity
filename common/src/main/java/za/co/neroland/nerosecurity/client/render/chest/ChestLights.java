package za.co.neroland.nerosecurity.client.render.chest;

import za.co.neroland.nerosecurity.client.SecurityColours;
import za.co.neroland.nerosecurity.client.render.door.DoorLeds;

/**
 * The secure chest's lock plate and LED, from the chest's synced fields. Pure: returns {@code 0xRRGGBB}, or
 * {@link DoorLeds#OFF} for a dark LED. {@code flashOn} is already resolved by the caller (with animations off it is
 * true for the whole deny flash).
 *
 * <ul>
 *   <li>Plate: {@code LOCK} in the deny flash's lit phase; else the minimum level's colour when linked, {@code OFF}
 *       grey when unlinked.</li>
 *   <li>LED, highest first: the deny flash ({@code LOCK} lit / dark), unlinked (dark), lockdown ({@code LOCK} 4 Hz
 *       strobe), recording ({@code REC} 1 Hz), else dark. With animations off every blink holds steady on.</li>
 * </ul>
 */
public final class ChestLights {

    private ChestLights() {
    }

    /** The lock-plate tint. */
    public static int plate(boolean linked, int levelRgb, boolean flashOn) {
        if (flashOn) {
            return SecurityColours.LOCK;
        }
        return linked ? levelRgb & 0xFFFFFF : SecurityColours.OFF;
    }

    /** The LED colour, or {@link DoorLeds#OFF}. */
    public static int led(boolean linked, boolean lockdown, boolean recording, boolean flashing, boolean flashOn,
            long gameTime, boolean animate) {
        if (flashing) {
            return flashOn ? SecurityColours.LOCK : DoorLeds.OFF;
        }
        if (!linked) {
            return DoorLeds.OFF;
        }
        if (lockdown) {
            return DoorLeds.lockdown(true, gameTime, animate);
        }
        return DoorLeds.recording(recording, gameTime, animate);
    }
}
