package za.co.neroland.nerosecurity.client.screen.door;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.block.DeviceSettingsRules;
import za.co.neroland.nerosecurity.client.widget.Status;

/**
 * The door screen's pure wording rules: which status glyph and sentence each row shows, and why the
 * controls are locked when they are. Mirrors the server's rules for display only ({@link DeviceSettingsRules}
 * decides on the server); no Minecraft client classes, so it is plain-JVM tested.
 */
public final class DoorText {

    /** The key every door, sensor and field screen uses for the owner-only lock. */
    public static final String OWNER_ONLY_KEY = "door.nerosecurity.owner_only_setting";
    public static final String UNLINKED_KEY = "device.nerosecurity.unlinked";
    public static final String LINKED_KEY = "door.nerosecurity.linked";
    public static final String SEALED_KEY = "door.nerosecurity.sealed";
    public static final String UNPOWERED_KEY = "door.nerosecurity.unpowered";

    /** One status row: the glyph and the sentence's lang key. */
    public record Row(Status status, String key) {
    }

    private DoorText() {
    }

    /** The link row: a filled OK square with "Linked to %s", or an outline with the unlinked sentence. */
    public static Row link(boolean linked) {
        return linked ? new Row(Status.OK, LINKED_KEY) : new Row(Status.OFF, UNLINKED_KEY);
    }

    /** The condition row: sealed (lockdown) beats unpowered; {@code null} when the door is simply working. */
    public static @Nullable Row condition(boolean lockdown, boolean unpowered) {
        if (lockdown) {
            return new Row(Status.BAD, SEALED_KEY);
        }
        if (unpowered) {
            return new Row(Status.WARN, UNPOWERED_KEY);
        }
        return null;
    }

    /**
     * Why every setting control is locked, or {@code null} when the viewer may edit. An unlinked door has no
     * settings; a door already at OWNER may only be changed by an owner (or an operator, reported as OWNER).
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
