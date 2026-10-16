package za.co.neroland.nerosecurity.event;

import java.util.Locale;

import net.minecraft.network.chat.Component;

/**
 * The fixed phrase table for security events: the only source of event wording for chat, the monitor
 * and push copy. A phrase names what happened, never who: phrases take no arguments, so no actor can
 * ever be rendered into one. Every {@link SecurityEventType} has a key in {@code en_us.json}.
 */
public final class EventPhrases {

    private static final String PREFIX = "event.nerosecurity.type.";

    private EventPhrases() {
    }

    /** {@code "event.nerosecurity.type.<lower_name>"}. */
    public static String key(SecurityEventType type) {
        return PREFIX + type.name().toLowerCase(Locale.ROOT);
    }

    /** The translatable phrase for {@code type}. Never carries an argument. */
    public static Component phrase(SecurityEventType type) {
        return Component.translatable(key(type));
    }
}
