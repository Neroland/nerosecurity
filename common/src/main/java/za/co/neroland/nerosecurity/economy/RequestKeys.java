package za.co.neroland.nerosecurity.economy;

import java.util.regex.Pattern;

/**
 * Checks a request key before it is sent to NeroEconomy. A key makes a retry safe (the same key is applied
 * at most once a day), so it should name the <em>thing</em> being paid for, such as a purchase or a session,
 * never a person.
 *
 * <p>Privacy (POPIA/GDPR): a key that holds a UUID or 32 hex digits in a row is refused here, the same
 * rule NeroEconomy applies, so a player identifier can never end up in its ledger by accident.
 */
public final class RequestKeys {

    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9_.:/#-]{1,64}");
    private static final Pattern UUID_LIKE =
            Pattern.compile("(?i).*[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}.*");
    private static final Pattern HEX_RUN = Pattern.compile("(?i).*[0-9a-f]{32}.*");

    private RequestKeys() {
    }

    /** Whether {@code key} is 1 to 64 characters of {@code A-Z a-z 0-9 _ . : / # -} and reads as no identifier. */
    public static boolean isValid(String key) {
        return key != null && ALLOWED.matcher(key).matches()
                && !UUID_LIKE.matcher(key).matches() && !HEX_RUN.matcher(key).matches();
    }

    /** As {@link #isValid}, but {@code null} (no replay protection) is also accepted. */
    public static boolean isValidOrNull(String key) {
        return key == null || isValid(key);
    }
}
