package za.co.neroland.nerosecurity.link;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerolandcore.link.LinkAlert;
import za.co.neroland.nerolandcore.link.LinkAlerts;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;

/**
 * {@code /nerosecurity alerts} — the invoker's own unread NeroSecurity alerts, privately:
 *
 * <pre>
 * /nerosecurity alerts                 list: "&lt;age&gt;  &lt;text&gt;  [&lt;handle&gt;]"
 * /nerosecurity alerts ack &lt;handle&gt;    acknowledge the alerts whose handle starts with it
 * /nerosecurity alerts ack all         acknowledge all of them
 * </pre>
 *
 * <p>A handle is the alert id without the module prefix, shortened to the installation's first eight
 * hex characters plus the kind ({@code 6f1c2a9e.laser_tripped}). Players only; every read and write is the
 * invoker's own alert list, so nobody can see or touch another player's alerts. The alert texts are the
 * same notification texts the phone shows (no player, no position).
 */
public final class AlertCommands {

    private AlertCommands() {
    }

    /** Registers the subtree; guarded so a missing Core link API can never break command registration. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        try {
            registerTree(dispatcher);
        } catch (RuntimeException | LinkageError e) {
            NeroSecurityCommon.LOGGER.warn("[NeroSecurity] /nerosecurity alerts not registered ({}).",
                    e.getClass().getName());
        }
    }

    private static void registerTree(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("nerosecurity")
                .then(Commands.literal("alerts")
                        .executes(ctx -> run(ctx.getSource(), "alerts", () -> list(ctx.getSource())))
                        .then(Commands.literal("ack")
                                .then(Commands.literal("all")
                                        .executes(ctx -> run(ctx.getSource(), "alerts ack",
                                                () -> ack(ctx.getSource(), ""))))
                                .then(Commands.argument("handle", StringArgumentType.word())
                                        .executes(ctx -> run(ctx.getSource(), "alerts ack",
                                                () -> ack(ctx.getSource(), StringArgumentType.getString(ctx, "handle"))))))));
    }

    private static int list(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.nerosecurity.players_only"));
            return 0;
        }
        if (!NeroSecurityConfig.isLinkModuleEnabled()) {
            source.sendSuccess(() -> Component.translatable("command.nerosecurity.alerts.none"), false);
            return 1;
        }
        MinecraftServer server = source.getServer();
        long now = System.currentTimeMillis();
        List<LinkAlert> mine = unread(LinkAlerts.get(server).list(player.getUUID()), now);
        if (mine.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.nerosecurity.alerts.none"), false);
            return 1;
        }
        for (LinkAlert alert : mine) {
            Component row = Component.translatable("command.nerosecurity.alerts.row",
                    SecurityLinkSnapshots.ago(now, alert.createdAt()), alert.text(), handle(alert.id()));
            source.sendSuccess(() -> row, false);
        }
        return mine.size();
    }

    private static int ack(CommandSourceStack source, String prefix) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.nerosecurity.players_only"));
            return 0;
        }
        if (!NeroSecurityConfig.isLinkModuleEnabled()) {
            source.sendSuccess(() -> Component.translatable("command.nerosecurity.alerts.acked", 0), false);
            return 0;
        }
        MinecraftServer server = source.getServer();
        LinkAlerts store = LinkAlerts.get(server);
        String wanted = prefix.toLowerCase(Locale.ROOT);
        int acked = 0;
        for (LinkAlert alert : unread(store.list(player.getUUID()), System.currentTimeMillis())) {
            if ((wanted.isEmpty() || handle(alert.id()).toLowerCase(Locale.ROOT).startsWith(wanted)
                    || stripModule(alert.id()).toLowerCase(Locale.ROOT).startsWith(wanted))
                    && store.ack(server, player.getUUID(), alert.id())) {
                acked++;
            }
        }
        int count = acked;
        source.sendSuccess(() -> Component.translatable("command.nerosecurity.alerts.acked", count), false);
        return count;
    }

    /** This module's alerts that are neither acknowledged nor snoozed, newest first. */
    static List<LinkAlert> unread(List<LinkAlert> alerts, long now) {
        List<LinkAlert> out = new ArrayList<>();
        for (LinkAlert alert : alerts) {
            if (SecurityLinkModule.MODULE_ID.equals(alert.moduleId()) && !alert.acked() && alert.snoozedUntil() <= now
                    && alert.id() != null) {
                out.add(alert);
            }
        }
        return out;
    }

    /** {@code nerosecurity.<uuid>.<kind>} to {@code <first 8 of uuid>.<kind>}. */
    static String handle(String alertId) {
        String rest = stripModule(alertId);
        int dot = rest.indexOf('.');
        if (dot < 0) {
            return rest.length() > 8 ? rest.substring(0, 8) : rest;
        }
        String installation = rest.substring(0, dot);
        return (installation.length() > 8 ? installation.substring(0, 8) : installation) + rest.substring(dot);
    }

    private static String stripModule(String alertId) {
        String prefix = SecurityLinkModule.MODULE_ID + ".";
        return alertId.startsWith(prefix) ? alertId.substring(prefix.length()) : alertId;
    }

    @FunctionalInterface
    private interface Body {
        int run();
    }

    private static int run(CommandSourceStack source, String subcommand, Body body) {
        try {
            return body.run();
        } catch (RuntimeException | LinkageError e) {
            NeroSecurityCommon.LOGGER.error("[NeroSecurity] /nerosecurity {} failed ({})", subcommand,
                    e.getClass().getName());
            source.sendFailure(Component.translatable("command.nerosecurity.failed", subcommand));
            return 0;
        }
    }
}
