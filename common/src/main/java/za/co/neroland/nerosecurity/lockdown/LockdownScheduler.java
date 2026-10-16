package za.co.neroland.nerosecurity.lockdown;

import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.server.MinecraftServer;

import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.InstallationService;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.server.ServerHooks;

/**
 * Ends alarm lockdowns whose time box has run out. Checks once a second (every 20 server ticks); skips
 * while lockdown is switched off on the server (the stored state is kept and ignored, so nothing is
 * released behind the owner's back while the kill switch is on).
 */
public final class LockdownScheduler {

    /** How often, in server ticks, due alarm lockdowns are released. */
    public static final int INTERVAL_TICKS = 20;

    private static final AtomicBoolean INITIALISED = new AtomicBoolean();

    private LockdownScheduler() {
    }

    /** Registers the tick listener. Idempotent. */
    public static void init() {
        if (INITIALISED.compareAndSet(false, true)) {
            ServerHooks.onTick(LockdownScheduler::tick);
        }
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % INTERVAL_TICKS != 0 || !NeroSecurityConfig.isLockdownEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Installation installation : SecurityInstallations.get(server).dueForAutoRelease(now)) {
            InstallationService.autoRelease(server, installation, now);
        }
    }
}
