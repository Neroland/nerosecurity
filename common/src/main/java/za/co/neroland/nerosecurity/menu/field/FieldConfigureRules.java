package za.co.neroland.nerosecurity.menu.field;

import java.util.Optional;

import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.block.DeviceSettingsRules;
import za.co.neroland.nerosecurity.field.FieldGeometry;
import za.co.neroland.nerosecurity.field.FieldSettings;

/**
 * Why the server refuses a force field configure intent, as the reason the player sees (chat and the screen's
 * footer). Pure: the handler passes in the live config and the emitter's facts, so the mapping is testable in a
 * plain JVM. Checked in this order: force fields disabled on the server, an owner-only change by a non-owner (the
 * OWNER minimum level, and the keycard-only flag either way), then the geometry (shape, the emitter's range, the server's area cap). {@code null} = the change may be applied.
 */
public final class FieldConfigureRules {

    /** The refusal for a field change by someone below OWNER that touches an OWNER setting. */
    public static final String OWNER_ONLY_KEY = "door.nerosecurity.owner_only_setting";
    /** The refusal while {@code forceFieldsEnabled} is off. */
    public static final String SERVER_DISABLED_KEY = "field.nerosecurity.disabled_server";

    private FieldConfigureRules() {
    }

    public static @Nullable Component refusal(boolean serverEnabled, boolean ownerOrAdmin, FieldSettings current,
            FieldSettings wanted, int range, int maxArea) {
        if (!serverEnabled) {
            return Component.translatable(SERVER_DISABLED_KEY);
        }
        if (!DeviceSettingsRules.mayApply(ownerOrAdmin, current.access().minLevel(), wanted.access().minLevel())) {
            return Component.translatable(OWNER_ONLY_KEY);
        }
        if (!ownerOrAdmin && current.access().keycardOnly() != wanted.access().keycardOnly()) {
            return Component.translatable(OWNER_ONLY_KEY);
        }
        Optional<String> invalid = FieldGeometry.validate(wanted, range, maxArea);
        return invalid.map(key -> invalidMessage(key, range, maxArea)).orElse(null);
    }

    /** The geometry refusal with its number: the emitter's range or the server's area cap. */
    static Component invalidMessage(String key, int range, int maxArea) {
        if (FieldGeometry.INVALID_RANGE.equals(key)) {
            return Component.translatable(key, range);
        }
        if (FieldGeometry.INVALID_AREA.equals(key)) {
            return Component.translatable(key, maxArea);
        }
        return Component.translatable(key);
    }
}
