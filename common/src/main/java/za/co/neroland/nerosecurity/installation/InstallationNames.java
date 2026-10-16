package za.co.neroland.nerosecurity.installation;

import java.util.UUID;

/** Sanitises owner-chosen installation names. */
public final class InstallationNames {

    private InstallationNames() {
    }

    /**
     * Strips control characters, the formatting sign and Unicode control/format characters, trims,
     * collapses whitespace runs to one space and truncates to {@link Installation#NAME_MAX}. Returns an
     * empty string when nothing is left (callers substitute {@link #defaultFor(UUID)}).
     */
    public static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length());
        boolean pendingSpace = false;
        for (int i = 0; i < raw.length(); ) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);
            int type = Character.getType(cp);
            if (cp < 0x20 || cp == 0x7F || cp == '§' || type == Character.CONTROL || type == Character.FORMAT
                    || type == Character.SURROGATE || type == Character.PRIVATE_USE || type == Character.UNASSIGNED) {
                continue;
            }
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                pendingSpace = out.length() > 0;
                continue;
            }
            if (pendingSpace) {
                out.append(' ');
                pendingSpace = false;
            }
            out.appendCodePoint(cp);
        }
        String result = out.toString();
        if (result.codePointCount(0, result.length()) > Installation.NAME_MAX) {
            result = result.substring(0, result.offsetByCodePoints(0, Installation.NAME_MAX)).trim();
        }
        return result;
    }

    /** {@link #clean(String)}, falling back to {@link #defaultFor(UUID)} when nothing usable is left. */
    public static String sanitise(String raw, UUID id) {
        String cleaned = clean(raw);
        return cleaned.isEmpty() ? defaultFor(id) : cleaned;
    }

    /** {@code "Installation " + the first eight hex characters of the id}. */
    public static String defaultFor(UUID id) {
        return "Installation " + shortId(id);
    }

    /** The first eight hex characters of an id: the only form ids are suggested or listed in. */
    public static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }
}
