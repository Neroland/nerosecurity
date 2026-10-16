package za.co.neroland.nerosecurity.link;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import za.co.neroland.nerosecurity.event.EventPhrases;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.InstallationNames;

/**
 * <b>The only builder of phone-notification text and stored-alert text</b> in NeroSecurity.
 *
 * <p>Notification text is shown on a locked phone's screen and passes through the NeroLink relay and the
 * platform push services (third-party processors). It therefore carries exactly two things: a fixed
 * phrase for what happened and the owner-chosen installation name, plus a deep link that names only the
 * installation id. The inputs of every public method are an installation name, a {@link PushKind}, a count
 * and an installation id: no method here can receive a security event, a player, a player id, a device
 * position or a dimension, so none of those can reach a notification.
 *
 * <p>Phrases are the English strings of the bundled {@code en_us.json} (a dedicated server has no
 * client language), read once; the same table serves chat, the monitor, the app and push copy. When the
 * file cannot be read the enum name is title-cased instead.
 *
 * <p>Caps match the bridge's {@code NotifyCopy}: title 120, body 240, deep link 256 characters.
 */
public final class PushCopy {

    /** The reserved payload member the bridge reads. */
    public static final String FIELD = "_notify";
    public static final int TITLE_MAX = 120;
    public static final int BODY_MAX = 240;
    public static final int LINK_MAX = 256;
    /** The bridge substitutes its relay id here. */
    public static final String SERVER_PLACEHOLDER = "{server}";
    /** The app module segment of the deep link. */
    public static final String APP_MODULE = "security";
    public static final Pattern DEEP_LINK = Pattern.compile(
            "^nerolink://server/[A-Za-z0-9_-]{1,64}/[a-z0-9_-]{1,32}(/[A-Za-z0-9_-]{1,64}){0,2}$");

    private static final String LANG = "/assets/nerosecurity/lang/en_us.json";
    private static final String MORE_KEY = "link.nerosecurity.more";
    private static final String FALLBACK_NAME = "Installation";

    private static volatile Map<String, String> english;

    private PushCopy() {
    }

    // --- phrases ----------------------------------------------------------------------------------

    /** The fixed English phrase for an event type (the same one chat and the monitor use). */
    public static String phrase(SecurityEventType type) {
        return type == null ? "" : english(EventPhrases.key(type), titleCase(type.name()));
    }

    /** The fixed English phrase for a push kind ("Repeated access denied", "Security events", ...). */
    public static String phrase(PushKind kind) {
        if (kind == null) {
            return "";
        }
        if (kind.eventType != null) {
            return phrase(kind.eventType);
        }
        String fallback = switch (kind) {
            case REPEATED_DENIED -> "Repeated access denied";
            case SUMMARY -> "Security events";
            default -> titleCase(kind.name());
        };
        return english(kind.phraseKey(), fallback);
    }

    /** The English string of {@code key} from the bundled language file, else {@code fallback}. */
    static String english(String key, String fallback) {
        String value = table().get(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    // --- builders ---------------------------------------------------------------------------------

    /**
     * The {@code _notify} object: {@code title} = the phrase, {@code body} = the installation name plus
     * {@code " (+N more)"} when {@code extra > 0}, {@code deepLink} = {@link #deepLink(UUID)}. For
     * {@link PushKind#SUMMARY} the title is "Security events".
     */
    public static JsonObject notify(String installationName, PushKind kind, int extra, UUID installationId) {
        JsonObject out = new JsonObject();
        out.addProperty("title", cap(phrase(kind), TITLE_MAX));
        out.addProperty("body", cap(safeName(installationName) + more(extra), BODY_MAX));
        String link = deepLink(installationId);
        if (link != null) {
            out.addProperty("deepLink", link);
        }
        return out;
    }

    /** The stored alert's text: {@code "<phrase> — <name>"} plus {@code " (+N more)"}. */
    public static String alertText(String installationName, PushKind kind, int extra) {
        return cap(phrase(kind) + " — " + safeName(installationName) + more(extra), BODY_MAX);
    }

    /** {@code nerolink://server/{server}/security/<installationId>}; null for a null id. */
    public static String deepLink(UUID installationId) {
        if (installationId == null) {
            return null;
        }
        return "nerolink://server/" + SERVER_PLACEHOLDER + "/" + APP_MODULE + "/" + installationId;
    }

    /**
     * The installation name as it may appear on a lock screen: cleaned again (control and formatting
     * characters, length) even though the store already sanitised it; a generic word when nothing is left.
     */
    static String safeName(String name) {
        String cleaned = InstallationNames.clean(name);
        if (cleaned.isEmpty()) {
            return FALLBACK_NAME;
        }
        return cleaned.length() > Installation.NAME_MAX ? cleaned.substring(0, Installation.NAME_MAX) : cleaned;
    }

    /** The persisted alert id for (installation, kind): one alert per pair, replaced on each emit. */
    public static String alertId(UUID installationId, PushKind kind) {
        return SecurityLinkModule.MODULE_ID + "." + installationId + "." + kind.jsonName();
    }

    private static String more(int extra) {
        if (extra <= 0) {
            return "";
        }
        String pattern = english(MORE_KEY, " (+%s more)");
        try {
            return String.format(Locale.ROOT, pattern, extra);
        } catch (RuntimeException badPattern) {
            return " (+" + extra + " more)";
        }
    }

    private static String cap(String text, int max) {
        StringBuilder out = new StringBuilder(text.length());
        text.codePoints().filter(cp -> cp >= 0x20 && cp != 0x7F).forEach(out::appendCodePoint);
        String clean = out.toString().trim();
        if (clean.length() <= max) {
            return clean;
        }
        int end = max;
        if (Character.isHighSurrogate(clean.charAt(end - 1))) {
            end--;
        }
        return clean.substring(0, end);
    }

    static String titleCase(String enumName) {
        StringBuilder out = new StringBuilder(enumName.length());
        boolean first = true;
        for (String word : enumName.toLowerCase(Locale.ROOT).split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!first) {
                out.append(' ');
            }
            out.append(first ? Character.toUpperCase(word.charAt(0)) + word.substring(1) : word);
            first = false;
        }
        return out.toString();
    }

    private static Map<String, String> table() {
        Map<String, String> local = english;
        if (local == null) {
            synchronized (PushCopy.class) {
                local = english;
                if (local == null) {
                    local = load();
                    english = local;
                }
            }
        }
        return local;
    }

    private static Map<String, String> load() {
        Map<String, String> out = new HashMap<>();
        try (InputStream in = PushCopy.class.getResourceAsStream(LANG)) {
            if (in == null) {
                return Map.of();
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonElement root = JsonParser.parseReader(reader);
                if (root.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().entrySet()) {
                        if (e.getValue().isJsonPrimitive()) {
                            out.put(e.getKey(), e.getValue().getAsString());
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            return Map.of();
        }
        return Map.copyOf(out);
    }
}
