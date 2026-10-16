package za.co.neroland.nerosecurity.compat.claims;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.platform.Services;

/**
 * Land-claim mods, which are optional and only ever take access away. Providers come from
 * {@code META-INF/services} and are loaded only when one of {@link #KNOWN_CLAIM_MODS} is installed; with none,
 * every check here answers "allowed" and {@link ClaimSource} abstains.
 *
 * <p>With several providers the answers are AND-ed. A provider that throws counts as "not allowed" (fail closed:
 * deny) and is warned about once, by provider id and exception class. No player data is logged.
 */
public final class ClaimsCompat {

    /** Claim mods a provider exists for. Open Parties and Claims today. */
    public static final List<String> KNOWN_CLAIM_MODS = List.of("openpartiesandclaims");

    private static volatile List<ClaimProvider> providers = List.of();
    private static volatile boolean initialised;
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private ClaimsCompat() {
    }

    /** Common init step 5a. Idempotent. */
    public static synchronized void init() {
        if (initialised) {
            return;
        }
        initialised = true;
        boolean anyInstalled = false;
        for (String mod : KNOWN_CLAIM_MODS) {
            anyInstalled |= Services.PLATFORM.isModLoaded(mod);
        }
        if (!anyInstalled) {
            return;
        }
        List<ClaimProvider> found = new ArrayList<>();
        try {
            Iterator<ClaimProvider> it = ServiceLoader.load(ClaimProvider.class, ClaimsCompat.class.getClassLoader()).iterator();
            while (true) {
                try {
                    if (!it.hasNext()) {
                        break;
                    }
                    ClaimProvider provider = it.next();
                    if (provider != null) {
                        found.add(provider);
                    }
                } catch (ServiceConfigurationError | LinkageError e) {
                    // A provider for a claim mod that is not installed fails to link: skip it.
                    NeroSecurityCommon.LOGGER.debug("[NeroSecurity] skipped a claim provider ({})", e.getClass().getSimpleName());
                }
            }
        } catch (RuntimeException | LinkageError e) {
            NeroSecurityCommon.LOGGER.warn("[NeroSecurity] Could not load claim providers ({}); claims are not consulted.",
                    e.getClass().getSimpleName());
        }
        providers = List.copyOf(found);
        if (providers.isEmpty()) {
            NeroSecurityCommon.LOGGER.info("[NeroSecurity] A claim mod is installed but this build has no claim "
                    + "integration for it; claims are not consulted.");
        } else {
            NeroSecurityCommon.LOGGER.info("[NeroSecurity] {} claim provider(s) active; claimed land can refuse access.",
                    providers.size());
        }
    }

    /** The loaded providers (empty when no claim mod is installed or this build has none). */
    public static List<ClaimProvider> providers() {
        return providers;
    }

    /** Whether claims are consulted right now: a provider is loaded and {@code claimsIntegrationEnabled}. */
    public static boolean isActive() {
        return !providers.isEmpty() && NeroSecurityConfig.isClaimsIntegrationEnabled();
    }

    /** Whether {@code actor} may interact at {@code pos}; true when claims are inactive. */
    public static boolean mayInteract(@Nullable MinecraftServer server, Identifier dimension, BlockPos pos, UUID actor) {
        return !isActive() || mayInteract(providers, server, dimension, pos, actor);
    }

    /**
     * The projection rule: may a field place blocks in this chunk? True when claims are inactive, or when at least
     * one real owner (never the erasure tombstone) may interact at the chunk's centre column.
     *
     * <p>Note: this rule is 2-D and column-wide (it probes one position at y=0 per chunk), which is right for
     * per-chunk claim mods such as OPAC; a 3-D (cuboid) claim provider would need the field positions' y-range.
     */
    public static boolean mayBuild(@Nullable MinecraftServer server, Identifier dimension, int chunkX, int chunkZ,
            Set<UUID> owners) {
        return !isActive() || mayBuild(providers, server, dimension, chunkX, chunkZ, owners);
    }

    // --- pure forms (tests and the façade) ----------------------------------------------------------

    /** AND over {@code list}; true when it is empty; a null or throwing provider answers false. */
    public static boolean mayInteract(List<ClaimProvider> list, @Nullable MinecraftServer server, Identifier dimension,
            BlockPos pos, UUID actor) {
        if (list == null || list.isEmpty()) {
            return true;
        }
        if (dimension == null || pos == null || actor == null) {
            return false;
        }
        for (ClaimProvider provider : list) {
            if (provider == null) {
                return false;
            }
            try {
                if (!provider.mayInteract(server, dimension, pos, actor)) {
                    return false;
                }
            } catch (RuntimeException | LinkageError e) {
                warnOnce(provider, e);
                return false;
            }
        }
        return true;
    }

    /** {@link #mayBuild(MinecraftServer, Identifier, int, int, Set)} over an explicit provider list. */
    public static boolean mayBuild(List<ClaimProvider> list, @Nullable MinecraftServer server, Identifier dimension,
            int chunkX, int chunkZ, Set<UUID> owners) {
        if (list == null || list.isEmpty()) {
            return true;
        }
        if (owners == null) {
            return false;
        }
        BlockPos centre = new BlockPos((chunkX << 4) + 8, 0, (chunkZ << 4) + 8);
        for (UUID owner : owners) {
            if (owner != null && !Installation.TOMBSTONE_OWNER.equals(owner)
                    && mayInteract(list, server, dimension, centre, owner)) {
                return true;
            }
        }
        return false;
    }

    private static void warnOnce(ClaimProvider provider, Throwable e) {
        String id;
        try {
            id = String.valueOf(provider.id());
        } catch (RuntimeException | LinkageError ignored) {
            id = provider.getClass().getSimpleName();
        }
        if (WARNED.add(id)) {
            NeroSecurityCommon.LOGGER.warn("[NeroSecurity] claim provider {} failed ({}); treating the position as "
                    + "claimed against the player.", id, e.getClass().getSimpleName());
        }
    }
}
