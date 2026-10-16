package za.co.neroland.nerosecurity.compat.factions;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.compat.CompatLevels;
import za.co.neroland.nerosecurity.compat.factions.FactionsBridge.Tier;

/**
 * Faction tier to NeroSecurity level: OUTSIDER (or unknown) grants nothing, ASSOCIATE PUBLIC, MEMBER MEMBER,
 * TRUSTED and INNER_CIRCLE OFFICER; then capped by the binding's max (itself never above OFFICER). A faction never
 * yields OWNER.
 */
public final class FactionTierMapping {

    private FactionTierMapping() {
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
