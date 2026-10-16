package za.co.neroland.nerosecurity.compat.colonies;

import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.compat.colonies.ColoniesBridge.Role;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.platform.Services;

/**
 * NeroColonies, which is optional. Every method returns a neutral value (false, STRANGER, null) when NeroColonies
 * is absent, this build has no bridge ({@code nerocolonies_compat=false}) or the bridge fails. Nothing here imports
 * NeroColonies. Logs carry the mod id and exception class only.
 */
public final class ColoniesCompat {

    public static final String MOD_ID = "nerocolonies";

    private static volatile @Nullable ColoniesBridge bridge;
    private static volatile boolean initialised;
    private static volatile boolean warned;

    private ColoniesCompat() {
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
            bridge = ServiceLoader.load(ColoniesBridge.class, ColoniesCompat.class.getClassLoader()).findFirst().orElse(null);
        } catch (ServiceConfigurationError | RuntimeException | LinkageError e) {
            bridge = null;
            NeroSecurityCommon.LOGGER.warn("[NeroSecurity] Could not hook NeroColonies ({}); colony bindings stay inert.",
                    e.getClass().getSimpleName());
            return;
        }
        if (bridge == null) {
            NeroSecurityCommon.LOGGER.info("[NeroSecurity] NeroColonies is installed but this build has no colony "
                    + "integration; colony bindings stay inert.");
        } else {
            NeroSecurityCommon.LOGGER.info("[NeroSecurity] NeroColonies found; installations can be bound to a colony.");
        }
    }

    /** The bridge when NeroColonies is loaded and this build includes it, else null. Ignores the config switch. */
    public static @Nullable ColoniesBridge bridgeOrNull() {
        return bridge;
    }

    public static boolean isLoaded() {
        return bridge != null;
    }

    /** Loaded and {@code coloniesIntegrationEnabled}. */
    public static boolean isActive() {
        return bridge != null && NeroSecurityConfig.isColoniesIntegrationEnabled();
    }

    public static boolean exists(@Nullable MinecraftServer server, UUID colony) {
        ColoniesBridge b = active();
        if (b == null || colony == null) {
            return false;
        }
        try {
            return b.exists(server, colony);
        } catch (RuntimeException | LinkageError e) {
            warn(e);
            return false;
        }
    }

    public static Role roleOf(@Nullable MinecraftServer server, UUID colony, UUID player) {
        ColoniesBridge b = active();
        if (b == null || colony == null || player == null) {
            return Role.STRANGER;
        }
        try {
            Role role = b.roleOf(server, colony, player);
            return role == null ? Role.STRANGER : role;
        } catch (RuntimeException | LinkageError e) {
            warn(e);
            return Role.STRANGER;
        }
    }

    /** The colony (or an outpost's parent colony) at {@code pos}: the {@code bind colony here} convenience only. */
    public static @Nullable UUID colonyAt(ServerLevel level, BlockPos pos) {
        ColoniesBridge b = active();
        if (b == null || level == null || pos == null) {
            return null;
        }
        try {
            return b.colonyAt(level, pos);
        } catch (RuntimeException | LinkageError e) {
            warn(e);
            return null;
        }
    }

    private static @Nullable ColoniesBridge active() {
        return isActive() ? bridge : null;
    }

    private static void warn(Throwable e) {
        if (!warned) {
            warned = true;
            NeroSecurityCommon.LOGGER.warn("[NeroSecurity] NeroColonies bridge failed ({}); treating it as absent for "
                    + "that call.", e.getClass().getSimpleName());
        }
    }
}
