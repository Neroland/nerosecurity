package za.co.neroland.nerosecurity.alarm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.block.DeviceIds;
import za.co.neroland.nerosecurity.block.alarm.AlarmBlockEntity;
import za.co.neroland.nerosecurity.block.alarm.AlarmRules;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.event.SecurityEvent;
import za.co.neroland.nerosecurity.event.SecurityEventBus;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.lockdown.LockdownHooks;
import za.co.neroland.nerosecurity.server.ServerHooks;

/**
 * Turns security events into alarm firings.
 *
 * <ol>
 *   <li>One bus listener (all installations) feeds every <b>subscribable</b> event type
 *       ({@link AlarmRules#subscribable()}) to the {@link AlarmCoalescer}; alarm, lockdown, audit,
 *       membership, configuration and keycard-bookkeeping events, and member sightings, never reach it.</li>
 *   <li>A leading-edge firing is queued and carried out at the end of the same server tick, outside the
 *       publisher's call stack (so a firing never re-enters a door, sensor or removal that published the
 *       event); every 20 ticks the coalescer is drained for "+N more" summaries.</li>
 *   <li>For each firing the installation is re-read from the store (gone: dropped). Its linked alarms are
 *       visited by stored block id, only where their chunk is loaded (never force-loaded), bounded by
 *       {@code maxDevicesPerInstallation}. If at least one is armed and subscribed, one
 *       {@code ALARM_TRIGGERED} (no actor) is published, each such alarm runs its responses, and if any
 *       of them asked for chat, {@link AlarmChat#send} runs once.</li>
 * </ol>
 *
 * <p>{@link #init()} also registers the lockdown hook that re-arms every loaded alarm when a lockdown
 * begins ({@link #armAll}); unloaded alarms are covered structurally, because an alarm counts as armed
 * whenever its installation is locked down. Logs carry event types and counts only.
 */
public final class AlarmDispatcher {

    /** Drain cadence in server ticks. */
    public static final int DRAIN_INTERVAL_TICKS = 20;
    /** Upper bound on queued leading-edge firings between two ticks. */
    static final int MAX_PENDING = AlarmCoalescer.DEFAULT_MAX_WINDOWS;

    private static final AtomicBoolean INITIALISED = new AtomicBoolean();
    private static final AlarmCoalescer COALESCER = new AlarmCoalescer(
            () -> NeroSecurityConfig.alarmCoalesceSeconds() * 1000L, AlarmCoalescer.DEFAULT_MAX_WINDOWS);
    private static final Deque<AlarmCoalescer.Emit> PENDING = new ArrayDeque<>();

    /** Re-arms alarms when a lockdown begins. One instance, so registering twice is a no-op. */
    private static final LockdownHooks.LockdownHook REARM = (server, installation, active) -> {
        if (active) {
            armAll(server, installation);
        }
    };

    private AlarmDispatcher() {
    }

    /** Init step 4f: subscribe to the bus, register the tick drain and the lockdown re-arm hook. Idempotent. */
    public static void init() {
        if (!INITIALISED.compareAndSet(false, true)) {
            return;
        }
        SecurityEventBus.subscribe(AlarmDispatcher::onEvent);
        ServerHooks.onTick(AlarmDispatcher::tick);
        ServerHooks.onStopping(server -> reset());
        LockdownHooks.register(REARM);
    }

    /** Whether the dispatcher feeds {@code type} to the coalescer at all. */
    public static boolean feeds(SecurityEventType type) {
        return AlarmRules.isSubscribable(type);
    }

    static void onEvent(SecurityEvent event) {
        if (event == null || !feeds(event.type())) {
            return;
        }
        AlarmFiring leading = COALESCER.offer(event.installationId(), event.type(), event.severity(),
                System.currentTimeMillis());
        if (leading != null) {
            synchronized (PENDING) {
                if (PENDING.size() < MAX_PENDING) {
                    PENDING.add(new AlarmCoalescer.Emit(event.installationId(), leading));
                }
            }
        }
    }

    private static void tick(MinecraftServer server) {
        List<AlarmCoalescer.Emit> due;
        synchronized (PENDING) {
            due = PENDING.isEmpty() ? new ArrayList<>() : new ArrayList<>(PENDING);
            PENDING.clear();
        }
        if (server.getTickCount() % DRAIN_INTERVAL_TICKS == 0) {
            due.addAll(COALESCER.drain(System.currentTimeMillis()));
        }
        if (due.isEmpty()) {
            return;
        }
        Map<Identifier, ServerLevel> levels = levels(server);
        for (AlarmCoalescer.Emit emit : due) {
            try {
                dispatch(server, levels, emit.installationId(), emit.firing());
            } catch (RuntimeException | LinkageError e) {
                NeroSecurityCommon.LOGGER.warn("[NeroSecurity] alarm firing for {} failed ({}); other firings still ran.",
                        emit.firing().type(), e.getClass().getName());
            }
        }
    }

    private static Map<Identifier, ServerLevel> levels(MinecraftServer server) {
        Map<Identifier, ServerLevel> out = new HashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            out.put(level.dimension().identifier(), level);
        }
        return out;
    }

    /** A linked alarm whose chunk is loaded, with its level. */
    private record Loaded(ServerLevel level, AlarmBlockEntity alarm) {
    }

    private static void dispatch(MinecraftServer server, Map<Identifier, ServerLevel> levels, UUID installationId,
            AlarmFiring firing) {
        Installation installation = SecurityInstallations.get(server).byId(installationId).orElse(null);
        if (installation == null) {
            return;
        }
        long now = System.currentTimeMillis();
        List<Loaded> armed = new ArrayList<>();
        for (Loaded loaded : loadedAlarms(levels, installation)) {
            if (loaded.alarm().wouldFire(installation, firing, now)) {
                armed.add(loaded);
            }
        }
        if (armed.isEmpty()) {
            return;
        }
        SecurityEventBus.publish(SecurityEvent.of(SecurityEventType.ALARM_TRIGGERED, installation.id(), null, null, now));
        boolean chat = false;
        for (Loaded a : armed) {
            try {
                chat |= a.alarm().fire(a.level(), installation, firing, now);
            } catch (RuntimeException | LinkageError e) {
                NeroSecurityCommon.LOGGER.warn("[NeroSecurity] an alarm failed to respond to {} ({}); others still ran.",
                        firing.type(), e.getClass().getName());
            }
        }
        if (chat) {
            AlarmChat.send(server, installation, firing);
        }
    }

    /**
     * Every linked alarm of {@code installation} whose chunk is loaded, in device order, at most
     * {@code maxDevicesPerInstallation} positions visited. Never loads a chunk.
     */
    private static List<Loaded> loadedAlarms(Map<Identifier, ServerLevel> levels, Installation installation) {
        List<Loaded> out = new ArrayList<>();
        int budget = Math.max(1, NeroSecurityConfig.maxDevicesPerInstallation());
        int visited = 0;
        for (Map.Entry<DevicePos, Identifier> device : installation.devices().entrySet()) {
            if (++visited > budget) {
                break;
            }
            if (!DeviceIds.ALARM.equals(device.getValue())) {
                continue;
            }
            DevicePos at = device.getKey();
            ServerLevel level = levels.get(at.dimension());
            if (level == null || !level.isLoaded(at.pos())) {
                continue;
            }
            if (level.getBlockEntity(at.pos()) instanceof AlarmBlockEntity alarm && !alarm.isRemoved()) {
                out.add(new Loaded(level, alarm));
            }
        }
        return out;
    }

    /** A lockdown began on {@code installation}: clear the silence on every loaded alarm of it. */
    public static void armAll(MinecraftServer server, Installation installation) {
        if (server == null || installation == null) {
            return;
        }
        Installation current = SecurityInstallations.get(server).byId(installation.id()).orElse(installation);
        for (Loaded loaded : loadedAlarms(levels(server), current)) {
            loaded.alarm().rearm();
        }
    }

    /** Server stop: forget windows and queued firings (they belong to that server). */
    static void reset() {
        COALESCER.clear();
        synchronized (PENDING) {
            PENDING.clear();
        }
    }
}
