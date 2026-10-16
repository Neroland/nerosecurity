package za.co.neroland.nerosecurity.block.alarm;

import java.util.Locale;
import java.util.Optional;

/**
 * What an alarm does when it fires. {@link #CHAT} is carried out once per firing by the dispatcher (not
 * once per alarm), so several alarms never send several messages. {@link #LOCKDOWN} is off by default
 * and only an owner may switch it either way.
 */
public enum AlarmResponse {
    REDSTONE,
    SOUND,
    HIGHLIGHT,
    CHAT,
    LOCKDOWN;

    /** Lower-case name, as stored in NBT and used in translation keys. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** {@code "alarm.nerosecurity.response.<key>"}. */
    public String translationKey() {
        return "alarm.nerosecurity.response." + key();
    }

    /** The response with this lower-case name; empty for anything unknown. */
    public static Optional<AlarmResponse> byKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        for (AlarmResponse response : values()) {
            if (response.key().equals(key)) {
                return Optional.of(response);
            }
        }
        return Optional.empty();
    }
}
