package za.co.neroland.nerosecurity.link;

import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;

/**
 * Reports one linked device's live status for the {@code devices} section and the installation's
 * {@code powered} flag. Implementations must never force-load a chunk: a device whose chunk is not
 * loaded is {@link Status#UNLOADED}. Production: {@link DeviceStatusReader}; tests inject a lambda.
 */
@FunctionalInterface
public interface DeviceStatusSource {

    Status status(Installation installation, DevicePos pos, Identifier blockId, long now);

    /**
     * One device's status. {@code state} is from the closed vocabulary the app maps to a colour (doors
     * {@code open|closed|sealed|unpowered}; chests {@code locked|open}; lasers and cameras
     * {@code armed|unpowered|unpaired|disarmed}; alarms {@code armed|active|silenced}; emitters
     * {@code projecting|collapsed|cooldown|unpowered|off}); null for devices that have none.
     *
     * @param powered       whether it has power; null for devices that do not use power
     * @param tripped       a laser that tripped recently
     * @param silencedUntil epoch ms an alarm is silent until; 0 when not silenced
     */
    record Status(boolean loaded, @Nullable Boolean powered, @Nullable String state, boolean tripped,
            long silencedUntil) {

        public static final Status UNLOADED = new Status(false, null, null, false, 0L);

        public static Status of(@Nullable Boolean powered, @Nullable String state) {
            return new Status(true, powered, state, false, 0L);
        }
    }
}
