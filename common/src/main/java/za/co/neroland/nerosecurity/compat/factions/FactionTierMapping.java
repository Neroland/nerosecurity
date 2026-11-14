package za.co.neroland.nerosecurity.compat.factions;

import java.util.Locale;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.compat.CompatLevels;
import za.co.neroland.nerosecurity.compat.factions.FactionsBridge.Tier;

/**
 * Faction tier to NeroSecurity level: OUTSIDER (or unknown) grants nothing, ASSOCIATE PUBLIC, MEMBER MEMBER,
 * TRUSTED and INNER_CIRCLE OFFICER; then capped by the binding's max (itself never above OFFICER). A faction never
 * yields OWNER.
 *
 * <p>Holds no NeroFactions type, so plain-JVM tests reach it without the mod.
 */
public final class FactionTierMapping {

    private FactionTierMapping() {
    }

    /**
     * The local tier for a NeroFactions tier id ({@code FactionTierView.id()}: "outsider", "associate", "member",
     * "trusted", "inner_circle"), compared lower-case in {@link Locale#ROOT}. Empty for a null, blank or unknown
     * id, which the faction source treats as an abstention.
     */
    public static Optional<Tier> tierFromId(@Nullable String id) {
        if (id == null) {
            return Optional.empty();
        }
        String wanted = id.trim().toLowerCase(Locale.ROOT);
        if (wanted.isEmpty()) {
            return Optional.empty();
        }
        for (Tier tier : Tier.values()) {
            if (tier.name().toLowerCase(Locale.ROOT).equals(wanted)) {
                return Optional.of(tier);
            }
        }
        return Optional.empty();
    }

    /** The level to grant, or null to abstain. */
    public static @Nullable AccessLevel level(@Nullable Tier tier, @Nullable AccessLevel max) {
        if (tier == null) {
            return null;
        }
        AccessLevel raw = switch (tier) {
            case OUTSIDER -> null;
            case ASSOCIATE -> AccessLevel.PUBLIC;
            case MEMBER -> AccessLevel.MEMBER;
            case TRUSTED, INNER_CIRCLE -> AccessLevel.OFFICER;
        };
        return CompatLevels.cap(raw, max);
    }
}
