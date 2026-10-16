package za.co.neroland.nerosecurity.server;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.NeroSecurityCommon;

/**
 * Server lifecycle seam: each loader calls the {@code fire*} methods from its own server events
 * (started, stopping, end of every server tick, player join, player leave); common code registers
 * listeners with the {@code on*} methods. Events flow loader to common, so no ServiceLoader is needed.
 *
 * <p><b>Listener isolation.</b> Every listener call is guarded: one that throws is logged by listener
 * class and exception class only (once per listener class per session) and the next still runs. A
 * listener is never detached. Listeners are kept in registration order.
 *
 * <p>Server thread only. Register from common init, before the first server starts.
 */
public final class ServerHooks {

    @FunctionalInterface
    public interface Started {
        void on(MinecraftServer server);
    }

    @FunctionalInterface
    public interface Stopping {
        void on(MinecraftServer server);
    }

    /** Runs once at the end of every server tick. Use {@code server.getTickCount()} to pace work. */
    @FunctionalInterface
    public interface Tick {
        void on(MinecraftServer server);
    }

    @FunctionalInterface
    public interface PlayerJoin {
        void on(ServerPlayer player);
    }

    @FunctionalInterface
    public interface PlayerLeave {
        void on(ServerPlayer player);
    }

    private static final List<Started> STARTED = new CopyOnWriteArrayList<>();
    private static final List<Stopping> STOPPING = new CopyOnWriteArrayList<>();
    private static final List<Tick> TICK = new CopyOnWriteArrayList<>();
    private static final List<PlayerJoin> JOIN = new CopyOnWriteArrayList<>();
    private static final List<PlayerLeave> LEAVE = new CopyOnWriteArrayList<>();
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    /** The running server between "started" and "stopping"; null otherwise. The one server holder. */
    private static volatile @Nullable MinecraftServer current;

    private ServerHooks() {
    }

    /**
     * The running server, or null before the first server has started and after it began stopping. For
     * callers that are handed no server (Core's link API passes only a player UUID). Server thread only.
     */
    public static @Nullable MinecraftServer currentServer() {
        return current;
    }

    // --- registration (common code) ---------------------------------------------------------------

    public static void onStarted(Started listener) {
        STARTED.add(require(listener));
    }

    public static void onStopping(Stopping listener) {
        STOPPING.add(require(listener));
    }

    public static void onTick(Tick listener) {
        TICK.add(require(listener));
    }

    public static void onPlayerJoin(PlayerJoin listener) {
        JOIN.add(require(listener));
    }

    public static void onPlayerLeave(PlayerLeave listener) {
        LEAVE.add(require(listener));
    }

    // --- dispatch (loaders) -----------------------------------------------------------------------

    public static void fireStarted(MinecraftServer server) {
        if (server != null) {
            current = server;
        }
        fire(STARTED, server, (listener, s) -> listener.on(s), "started");
    }

    public static void fireStopping(MinecraftServer server) {
        fire(STOPPING, server, (listener, s) -> listener.on(s), "stopping");
        current = null;
    }

    public static void fireTick(MinecraftServer server) {
        fire(TICK, server, (listener, s) -> listener.on(s), "tick");
    }

    public static void firePlayerJoin(ServerPlayer player) {
        fire(JOIN, player, (listener, p) -> listener.on(p), "join");
    }

    public static void firePlayerLeave(ServerPlayer player) {
        fire(LEAVE, player, (listener, p) -> listener.on(p), "leave");
    }

    @FunctionalInterface
    private interface Call<L, A> {
        void run(L listener, A argument);
    }

    private static <L, A> void fire(List<L> listeners, A argument, Call<L, A> call, String hook) {
        if (argument == null) {
            return;
        }
        for (L listener : listeners) {
            try {
                call.run(listener, argument);
            } catch (RuntimeException | LinkageError e) {
                String listenerClass = listener.getClass().getName();
                if (WARNED.add(listenerClass)) {
                    NeroSecurityCommon.LOGGER.warn(
                            "[NeroSecurity] server {} listener {} failed ({}); other listeners still ran.",
                            hook, listenerClass, e.getClass().getName());
                }
            }
        }
    }

    private static <T> T require(T listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        return listener;
    }

    /** Tests only: forget every listener. */
    static void reset() {
        STARTED.clear();
        STOPPING.clear();
        TICK.clear();
        JOIN.clear();
        LEAVE.clear();
        WARNED.clear();
        current = null;
    }

    /** Tests only: dispatches to a consumer list the same isolated way. */
    static <A> void fireForTest(List<Consumer<A>> listeners, A argument) {
        fire(listeners, argument, Consumer::accept, "test");
    }
}
