package za.co.neroland.nerosecurity.lockdown;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.server.MinecraftServer;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.installation.Installation;

/**
 * Side effects of a lockdown transition that live outside the installation service: alarms re-arming
 * when a lockdown begins, force fields raised to maximum, and so on. {@code InstallationService} calls
 * {@link #fire} after the store write and the transition event, on entry ({@code active = true}, any
 * source) and on every exit (manual, automatic, operator).
 *
 * <p>Each hook is isolated: one that throws is logged by class and exception class only (once per class)
 * and the others still run; the transition itself has already happened and is never undone.
 */
public final class LockdownHooks {

    /** Reacts to a lockdown transition. Server thread; must not throw (it is isolated anyway). */
    @FunctionalInterface
    public interface LockdownHook {
        void onLockdown(MinecraftServer server, Installation installation, boolean active);
    }

    private static final List<LockdownHook> HOOKS = new CopyOnWriteArrayList<>();
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private LockdownHooks() {
    }

    /** Adds a hook. Call from init; adding the same instance twice is ignored. */
    public static void register(LockdownHook hook) {
        if (hook == null) {
            throw new IllegalArgumentException("hook must not be null");
        }
        if (!HOOKS.contains(hook)) {
            HOOKS.add(hook);
        }
    }

    /** Tests only. */
    public static boolean unregister(LockdownHook hook) {
        return HOOKS.remove(hook);
    }

    /** Runs every hook for one transition, each isolated. */
    public static void fire(MinecraftServer server, Installation installation, boolean active) {
        if (installation == null) {
            return;
        }
        for (LockdownHook hook : HOOKS) {
            try {
                hook.onLockdown(server, installation, active);
            } catch (RuntimeException | LinkageError e) {
                String hookClass = hook.getClass().getName();
                if (WARNED.add(hookClass)) {
                    NeroSecurityCommon.LOGGER.warn("[NeroSecurity] lockdown hook {} failed ({}); others still ran.",
                            hookClass, e.getClass().getName());
                }
            }
        }
    }
}
