package za.co.neroland.nerosecurity.link;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.link.LinkAlert;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.server.ServerHooks;

/**
 * One private chat line shortly after a player joins, when they have unread NeroSecurity alerts:
 * "[NeroSecurity] 3 unread security alerts (2 installations). /nerosecurity alerts to review." Counts
 * only, never an alert's text, never anyone else. Sent {@value #DELAY_TICKS} ticks after joining (so it is
 * not lost in the join noise), dropped if the player leaves first.
 *
 * <p>Gated by {@code loginAlertSummaryEnabled}, {@code pushNotificationsEnabled} and the module switch.
 * The pending countdown is keyed by player UUID in memory only; erasure forgets it.
 */
public final class LoginAlertSummary {

    static final int DELAY_TICKS = 40;

    private static final AtomicBoolean INITIALISED = new AtomicBoolean();
    private static final Map<UUID, Integer> PENDING = new ConcurrentHashMap<>();
    private static final int MAX_PENDING = 4096;

    private LoginAlertSummary() {
    }

    static void init(LinkContext ctx) {
        if (!INITIALISED.compareAndSet(false, true)) {
            return;
        }
        ServerHooks.onPlayerJoin(player -> schedule(player.getUUID()));
        ServerHooks.onPlayerLeave(player -> PENDING.remove(player.getUUID()));
        ServerHooks.onStopping(server -> PENDING.clear());
        ServerHooks.onTick(server -> tick(server, ctx));
    }

    static void schedule(UUID player) {
        if (player == null) {
            return;
        }
        if (PENDING.size() >= MAX_PENDING) {
            PENDING.clear();
        }
        PENDING.put(player, DELAY_TICKS);
    }

    private static void tick(MinecraftServer server, LinkContext ctx) {
        if (PENDING.isEmpty()) {
            return;
        }
        for (Iterator<Map.Entry<UUID, Integer>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Integer> e = it.next();
            int left = e.getValue() - 1;
            if (left > 0) {
                e.setValue(left);
                continue;
            }
            it.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player == null) {
                continue;
            }
            UUID id = e.getKey();
            deliver(ctx.settings(), () -> ctx.alerts(id), ctx.now(), counts -> player.sendSystemMessage(
                    Component.translatable("link.nerosecurity.login_summary", counts.alerts(), counts.installations())));
        }
    }

    /** Unread alerts and the installations they belong to. */
    record Counts(int alerts, int installations) {
    }

    /**
     * Counts this module's unacknowledged, unsnoozed alerts and their distinct installations (from the
     * alert id {@code nerosecurity.<installationId>.<kind>}); null when there are none.
     */
    static @Nullable Counts counts(List<LinkAlert> alerts, long now) {
        if (alerts == null) {
            return null;
        }
        int n = 0;
        Set<String> installations = new HashSet<>();
        String prefix = SecurityLinkModule.MODULE_ID + ".";
        for (LinkAlert alert : alerts) {
            if (alert == null || !SecurityLinkModule.MODULE_ID.equals(alert.moduleId()) || alert.acked()
                    || alert.snoozedUntil() > now || alert.id() == null || !alert.id().startsWith(prefix)) {
                continue;
            }
            n++;
            String rest = alert.id().substring(prefix.length());
            int dot = rest.indexOf('.');
            installations.add(dot < 0 ? rest : rest.substring(0, dot));
        }
        return n == 0 ? null : new Counts(n, installations.size());
    }

    /**
     * Runs the summary for one player: checks the switches, reads their alerts and, when any are unread,
     * hands the counts (and only the counts) to {@code send}. Never throws.
     *
     * @return whether a line was sent
     */
    static boolean deliver(LinkSettings settings, Supplier<List<LinkAlert>> alerts, long now,
            java.util.function.Consumer<Counts> send) {
        try {
            if (!settings.moduleEnabled() || !settings.pushEnabled() || !settings.loginSummaryEnabled()) {
                return false;
            }
            Counts counts = counts(alerts.get(), now);
            if (counts == null) {
                return false;
            }
            send.accept(counts);
            return true;
        } catch (RuntimeException | LinkageError e) {
            NeroSecurityCommon.LOGGER.warn("[NeroSecurity] login alert summary failed ({}).", e.getClass().getName());
            return false;
        }
    }

    static void forget(UUID player) {
        if (player != null) {
            PENDING.remove(player);
        }
    }

    static boolean holdsAnyFor(UUID player) {
        return player != null && PENDING.containsKey(player);
    }
}
