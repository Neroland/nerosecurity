package za.co.neroland.nerosecurity.compat;

import net.minecraft.resources.Identifier;

import za.co.neroland.nerosecurity.access.AccessSource;
import za.co.neroland.nerosecurity.access.AccessSources;
import za.co.neroland.nerosecurity.compat.claims.ClaimSource;
import za.co.neroland.nerosecurity.compat.claims.ClaimsCompat;
import za.co.neroland.nerosecurity.compat.colonies.ColoniesCompat;
import za.co.neroland.nerosecurity.compat.colonies.ColonySource;
import za.co.neroland.nerosecurity.compat.factions.FactionSource;
import za.co.neroland.nerosecurity.compat.factions.FactionsCompat;
import za.co.neroland.nerosecurity.compat.nerospace.NerospaceCompat;
import za.co.neroland.nerosecurity.compat.nerospace.StationSource;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;

/**
 * The four integration sources, registered after the built-in ones (membership, keycards) in this order: faction,
 * colony, station, claim. They are always registered; each abstains while its sibling is absent or its
 * {@code *IntegrationEnabled} switch is off, so a binding made while a sibling was present stays stored and inert.
 * Grants never exceed OFFICER and only the claim source can deny.
 */
public final class CompatSources {

    public static final FactionSource FACTION =
            new FactionSource(FactionsCompat::bridgeOrNull, NeroSecurityConfig::isFactionsIntegrationEnabled);
    public static final ColonySource COLONY =
            new ColonySource(ColoniesCompat::bridgeOrNull, NeroSecurityConfig::isColoniesIntegrationEnabled);
    public static final StationSource STATION =
            new StationSource(NerospaceCompat::bridgeOrNull, NeroSecurityConfig::isNerospaceIntegrationEnabled);
    public static final ClaimSource CLAIM =
            new ClaimSource(ClaimsCompat::providers, NeroSecurityConfig::isClaimsIntegrationEnabled);

    private CompatSources() {
    }

    /** Init step 4j, after {@code AccessSources.init()}. Idempotent. */
    public static synchronized void init() {
        registerOnce(FACTION);
        registerOnce(COLONY);
        registerOnce(STATION);
        registerOnce(CLAIM);
    }

    /** Server stopped: suspension notices may be published again next session. */
    public static void clearSessionState() {
        FACTION.clearSession();
        COLONY.clearSession();
    }

    private static void registerOnce(AccessSource source) {
        Identifier id = source.id();
        for (AccessSource existing : AccessSources.all()) {
            if (existing.id().equals(id)) {
                return;
            }
        }
        AccessSources.register(source);
    }
}
