package za.co.neroland.nerosecurity.client.screen.sensor;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.block.DeviceSettingsRules;
import za.co.neroland.nerosecurity.client.widget.Status;

/**
 * The sensor screen's pure wording rules (laser grid node and camera): the one status row and why the
 * controls are locked when they are. Display only; no Minecraft client classes, so it is plain-JVM tested.
 */
public final class SensorText {

    public static final String OWNER_ONLY_KEY = "door.nerosecurity.owner_only_setting";
    public static final String UNLINKED_KEY = "device.nerosecurity.unlinked";
    public static final String TITLE_LASER_KEY = "sensor.nerosecurity.title.laser";
    public static final String TITLE_CAMERA_KEY = "sensor.nerosecurity.title.camera";
    public static final String ARMED_KEY = "sensor.nerosecurity.armed";
    public static final String UNPAIRED_KEY = "sensor.nerosecurity.unpaired";
    public static final String NO_POWER_KEY = "sensor.nerosecurity.disarmed.power";
    public static final String DISARMED_KEY = "sensor.nerosecurity.disarmed";
    public static final String NOT_LINKED_KEY = "sensor.nerosecurity.disarmed.unlinked";

    /**
     * The status row. {@code recording} marks the armed row, drawn with the REC colour — the same notice the
     * device's LED gives in the world.
     */
    public record Row(Status status, String key, boolean recording) {
    }

    private SensorText() {
    }

    /** The screen's one-line question, by device kind. */
    public static String titleKey(boolean laser) {
        return laser ? TITLE_LASER_KEY : TITLE_CAMERA_KEY;
    }

    /**
     * Which single sentence describes the sensor now. Order: not linked (it senses nothing) → armed →
     * a laser without a facing partner → no power → not armed for any other reason.
     */
    public static Row state(boolean linked, boolean armed, boolean laser, boolean paired, boolean powered) {
        if (!linked) {
            return new Row(Status.OFF, NOT_LINKED_KEY, false);
        }
        if (armed) {
            return new Row(Status.OK, ARMED_KEY, true);
        }
        if (laser && !paired) {
            return new Row(Status.WARN, UNPAIRED_KEY, false);
        }
        if (!powered) {
            return new Row(Status.WARN, NO_POWER_KEY, false);
        }
        return new Row(Status.WARN, DISARMED_KEY, false);
    }

    /** Index of {@code fov} in {@code options}, or -1 when it is not one of them. */
    public static int indexOf(int[] options, int fov) {
        for (int i = 0; i < options.length; i++) {
            if (options[i] == fov) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Why every setting control is locked, or {@code null} when the viewer may edit. An unlinked sensor has no
     * settings; a sensor already at OWNER may only be changed by an owner (or an operator, reported as OWNER).
     */
    public static @Nullable String editWhy(boolean linked, AccessLevel minLevel, boolean ownerOrAdmin) {
        if (!linked) {
            return UNLINKED_KEY;
        }
        if (!DeviceSettingsRules.mayApply(ownerOrAdmin, minLevel, minLevel)) {
            return OWNER_ONLY_KEY;
        }
        return null;
    }
}
