package za.co.neroland.nerosecurity.compat.factions;

import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.event.ThresholdEvents;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.block.field.FieldPassability;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.compat.factions.FactionsBridge.Tier;
import za.co.neroland.nerosecurity.platform.Services;

/**
 * NeroFactions, which is optional. Every method returns a neutral value (false, empty, null) when NeroFactions is
 * absent, when this build has no bridge ({@code nerofactions_compat=false}) or when the bridge fails; no failure
 * leaves this class. Nothing here imports NeroFactions: the bridge is found through {@code META-INF/services}, and
 * only after {@code isModLoaded("nerofactions")}.
 *
 * <p>Logs carry the mod id and exception class only.
 */
public final class FactionsCompat {

    public static final String MOD_ID = "nerofactions";

    /** NeroFactions' tier-crossing channel. Consumed only as a "verdicts may have changed" tick. */
    public static final Identifier REPUTATION_TIER = Identifier.fromNamespaceAndPath(MOD_ID, "reputation_tier");

    private static volatile @Nullable FactionsBridge bridge;
    private static volatile boolean initialised;
    private static volatile boolean warned;

    private FactionsCompat() {
    }

    /** Common init step 5a. Idempotent. */
    public static synchronized void init() {
        if (initialised) {
            return;
        }
        initialised = true;
        if (!Services.PLATFORM.isModLoaded(MOD_ID)) {
            return;
        }
        try {
            bridge = ServiceLoader.load(FactionsBridge.class, FactionsCompat.class.getClassLoader()).findFirst().orElse(null);
        } catch (ServiceConfigurationError | RuntimeException | LinkageError e) {
            bridge = null;
            NeroSecurityCommon.LOGGER.warn("[NeroSecurity] Could not hook NeroFactions ({}); faction bindings stay inert.",
                    e.getClass().getSimpleName());
            return;
        }
        if (bridge == null) {
            NeroSecurityCommon.LOGGER.info("[NeroSecurity] NeroFactions is installed but this build has no faction "
                    + "integration; faction bindings stay inert.");
            return;
        }
        // A tier crossing carries a faction id and no player: it only tells emitters to re-check this tick.
        ThresholdEvents.onSourcedCrossing(c -> {
            if (c != null && c.crossing() != null && REPUTATION_TIER.equals(c.crossing().channel())) {
                FieldPassability.bump();
            }
        });
        NeroSecurityCommon.LOGGER.info("[NeroSecurity] NeroFactions found; installations can be bound to a faction.");
    }

    /** The bridge when NeroFactions is loaded and this build includes it, else null. Ignores the config switch. */
    public static @Nullable FactionsBridge bridgeOrNull() {
        return bridge;
    }

    /** Whether NeroFactions is present with a working bridge (the bind command's "(mod not installed)"). */
    public static boolean isLoaded() {
        return bridge != null;
    }

    /** Whether faction bindings do anything right now: loaded and {@code factionsIntegrationEnabled}. */
    public static boolean isActive() {
        return bridge != null && NeroSecurityConfig.isFactionsIntegrationEnabled();
    }

    public static boolean isMember(@Nullable MinecraftServer server, UUID player, Identifier faction) {
        FactionsBridge b = active();
        if (b == null || player == null || faction == null) {
            return false;
        }
        try {
            return b.isMember(server, player, faction);
        } catch (RuntimeException | LinkageError e) {
            warn(e);
            return false;
        }
    }

    public static Set<Identifier> membershipsOf(@Nullable MinecraftServer server, UUID player) {
        FactionsBridge b = active();
        if (b == null || player == null) {
            return Set.of();
        }
        try {
            Set<Identifier> out = b.membershipsOf(server, player);
            return out == null ? Set.of() : Set.copyOf(out);
        } catch (RuntimeException | LinkageError e) {
            warn(e);
            return Set.of();
        }
    }

    public static @Nullable Tier tierOf(@Nullable MinecraftServer server, UUID player, Identifier faction) {
        FactionsBridge b = active();
        if (b == null || player == null || faction == null) {
            return null;
        }
        try {
            return b.tierOf(server, player, faction);
        } catch (RuntimeException | LinkageError e) {
            warn(e);
            return null;
        }
    }

    private static @Nullable FactionsBridge active() {
        return isActive() ? bridge : null;
    }

    private static void warn(Throwable e) {
        if (!warned) {
            warned = true;
            NeroSecurityCommon.LOGGER.warn("[NeroSecurity] NeroFactions bridge failed ({}); treating it as absent for "
                    + "that call.", e.getClass().getSimpleName());
        }
    }
}
