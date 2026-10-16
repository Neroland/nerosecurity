package za.co.neroland.nerosecurity.compat.nerospace;

import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.platform.Services;

/**
 * Nerospace, which is optional. Every method returns a neutral value (false, empty) when Nerospace is absent, this
 * build has no bridge ({@code nerospace_compat=false}) or the bridge fails. Nothing here imports Nerospace. Logs
 * carry the mod id and exception class only.
 */
public final class NerospaceCompat {

    public static final String MOD_ID = "nerospace";

    private static volatile @Nullable NerospaceBridge bridge;
    private static volatile boolean initialised;
    private static volatile boolean warned;

    private NerospaceCompat() {
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
            bridge = ServiceLoader.load(NerospaceBridge.class, NerospaceCompat.class.getClassLoader()).findFirst().orElse(null);
        } catch (ServiceConfigurationError | RuntimeException | LinkageError e) {
            bridge = null;
            NeroSecurityCommon.LOGGER.warn("[NeroSecurity] Could not hook Nerospace ({}); station bindings stay inert.",
                    e.getClass().getSimpleName());
            return;
        }
        if (bridge == null) {
            NeroSecurityCommon.LOGGER.info("[NeroSecurity] Nerospace is installed but this build has no station "
                    + "integration; station bindings stay inert.");
        } else {
            NeroSecurityCommon.LOGGER.info("[NeroSecurity] Nerospace found; installations can be bound to a station.");
        }
    }

    /** The bridge when Nerospace is loaded and this build includes it, else null. Ignores the config switch. */
    public static @Nullable NerospaceBridge bridgeOrNull() {
        return bridge;
    }

    public static boolean isLoaded() {
        return bridge != null;
    }

    /** Loaded and {@code nerospaceIntegrationEnabled}. */
    public static boolean isActive() {
        return bridge != null && NeroSecurityConfig.isNerospaceIntegrationEnabled();
    }

    public static boolean ownedBy(@Nullable MinecraftServer server, int station, UUID player) {
        NerospaceBridge b = active();
        if (b == null || player == null) {
            return false;
        }
        try {
            return b.ownedBy(server, station, player);
        } catch (RuntimeException | LinkageError e) {
            warn(e);
            return false;
        }
    }

    public static List<Integer> stationsOwnedBy(@Nullable MinecraftServer server, UUID player) {
        NerospaceBridge b = active();
        if (b == null || player == null) {
            return List.of();
        }
        try {
            List<Integer> out = b.stationsOwnedBy(server, player);
            return out == null ? List.of() : List.copyOf(out);
        } catch (RuntimeException | LinkageError e) {
            warn(e);
            return List.of();
        }
    }

    private static @Nullable NerospaceBridge active() {
        return isActive() ? bridge : null;
    }

    private static void warn(Throwable e) {
        if (!warned) {
            warned = true;
            NeroSecurityCommon.LOGGER.warn("[NeroSecurity] Nerospace bridge failed ({}); treating it as absent for "
                    + "that call.", e.getClass().getSimpleName());
        }
    }
}
