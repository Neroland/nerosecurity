package za.co.neroland.nerosecurity.link;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonObject;

import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.installation.DevicePos;

/**
 * How the link module names devices without coordinates: a fixed <b>kind</b> from the stored block id and
 * a 1-based <b>ordinal</b> among the installation's devices of that kind, in the store's own device order
 * ("Laser grid 3"). The same position always gets the same label within one snapshot. Pure.
 */
public final class DeviceKinds {

    /** Every kind the link module names; anything else is {@link #OTHER}. */
    public static final List<String> KINDS = List.of("security_door", "blast_door", "laser_grid_node",
            "security_camera", "alarm", "secure_chest", "force_field_emitter", "keycard_programmer",
            "security_monitor");
    public static final String OTHER = "other";

    private static final String LABEL_PREFIX = "link.nerosecurity.device.";

    /** One device's coordinate-free name. */
    public record Label(String kind, String text) {
    }

    private DeviceKinds() {
    }

    /** The kind for a stored block id: its path when it is one of {@link #KINDS}, else {@link #OTHER}. */
    public static String kind(@Nullable Identifier blockId) {
        if (blockId == null || !NeroSecurityCommon.MOD_ID.equals(blockId.getNamespace())) {
            return OTHER;
        }
        String path = blockId.getPath();
        return KINDS.contains(path) ? path : OTHER;
    }

    /**
     * {@code {doors, fields, lasers, cameras, alarms, chests, other, total}}; doors are security and blast
     * doors, {@code other} is everything else (programmers, monitors, unknown ids), {@code total} every device.
     */
    public static JsonObject counts(Map<DevicePos, Identifier> devices) {
        int doors = 0;
        int fields = 0;
        int lasers = 0;
        int cameras = 0;
        int alarms = 0;
        int chests = 0;
        int other = 0;
        for (Identifier id : devices.values()) {
            switch (kind(id)) {
                case "security_door", "blast_door" -> doors++;
                case "force_field_emitter" -> fields++;
                case "laser_grid_node" -> lasers++;
                case "security_camera" -> cameras++;
                case "alarm" -> alarms++;
                case "secure_chest" -> chests++;
                default -> other++;
            }
        }
        JsonObject out = new JsonObject();
        out.addProperty("doors", doors);
        out.addProperty("fields", fields);
        out.addProperty("lasers", lasers);
        out.addProperty("cameras", cameras);
        out.addProperty("alarms", alarms);
        out.addProperty("chests", chests);
        out.addProperty("other", other);
        out.addProperty("total", devices.size());
        return out;
    }

    /**
     * The label of the device at {@code pos}: its kind and its 1-based ordinal among the devices of that kind
     * in {@code devices} iteration order. Null when {@code pos} is null or not one of the devices.
     */
    public static @Nullable Label label(Map<DevicePos, Identifier> devices, @Nullable DevicePos pos) {
        if (pos == null || devices == null) {
            return null;
        }
        Identifier target = devices.get(pos);
        if (target == null) {
            return null;
        }
        String kind = kind(target);
        int ordinal = 0;
        for (Map.Entry<DevicePos, Identifier> e : devices.entrySet()) {
            if (kind.equals(kind(e.getValue()))) {
                ordinal++;
            }
            if (e.getKey().equals(pos)) {
                return new Label(kind, labelText(kind, ordinal));
            }
        }
        return null;
    }

    /** The English label for the {@code ordinal}-th device of {@code kind} ("Door 2"). */
    public static String labelText(String kind, int ordinal) {
        String safeKind = KINDS.contains(kind) ? kind : OTHER;
        String pattern = PushCopy.english(LABEL_PREFIX + safeKind, fallbackPattern(safeKind));
        try {
            return String.format(Locale.ROOT, pattern, ordinal);
        } catch (RuntimeException badPattern) {
            return String.format(Locale.ROOT, fallbackPattern(safeKind), ordinal);
        }
    }

    private static String fallbackPattern(String kind) {
        return switch (kind) {
            case "security_door" -> "Door %s";
            case "blast_door" -> "Blast door %s";
            case "laser_grid_node" -> "Laser grid %s";
            case "security_camera" -> "Camera %s";
            case "alarm" -> "Alarm %s";
            case "secure_chest" -> "Chest %s";
            case "force_field_emitter" -> "Field %s";
            case "keycard_programmer" -> "Programmer %s";
            case "security_monitor" -> "Monitor %s";
            default -> "Device %s";
        };
    }
}
