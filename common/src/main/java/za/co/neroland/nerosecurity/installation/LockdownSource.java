package za.co.neroland.nerosecurity.installation;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

/** What started a lockdown: a privileged player (or command), or an owner-enabled alarm response. */
public enum LockdownSource {
    MANUAL,
    ALARM;

    /** {@code "manual" | "alarm"}. */
    public String jsonName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Parses {@link #jsonName()}; anything else (junk, missing) reads as {@link #MANUAL}. */
    public static LockdownSource byName(@Nullable String raw) {
        return "alarm".equals(raw) ? ALARM : MANUAL;
    }
}
