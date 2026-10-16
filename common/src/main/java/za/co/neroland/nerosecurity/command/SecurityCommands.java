package za.co.neroland.nerosecurity.command;

import java.util.List;
import java.util.UUID;

import java.util.Map;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.access.AccessAction;
import za.co.neroland.nerosecurity.access.AccessDecision;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.access.AccessResolver;
import za.co.neroland.nerosecurity.audit.AuditEntry;
import za.co.neroland.nerosecurity.audit.AuditService;
import za.co.neroland.nerosecurity.event.EventPhrases;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.CardRecord;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.InstallationNames;
import za.co.neroland.nerosecurity.installation.InstallationService;
import za.co.neroland.nerosecurity.installation.PlayerNames;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.installation.ServiceResult;
import za.co.neroland.nerosecurity.keycard.KeycardService;
import za.co.neroland.nerosecurity.telemetry.NeroSecurityTelemetry;

/**
 * The {@code /nerosecurity} command tree, registered identically from all three loaders.
 *
 * <pre>
 * /nerosecurity installations                     (players; no permission) your own installations
 * /nerosecurity lockdown &lt;installation&gt; on|off   (players; no permission) resolved exactly like the hub
 * /nerosecurity log &lt;installation&gt; [count]       (OFFICER; audited as LOG_READ) newest events, 1..100, default 20
 * /nerosecurity keycards &lt;installation&gt; list      (OFFICER, as reading the log) serials, levels, flags, ages
 * /nerosecurity keycards &lt;installation&gt; revoke &lt;serial&gt; | revokeall   (OWNER)
 * /nerosecurity automation &lt;installation&gt; off|member|officer           (OWNER) secure-chest automation
 * /nerosecurity notify-officers &lt;installation&gt; on|off                  (OWNER) officers also get alarm chat
 * /nerosecurity admin unlock &lt;installation&gt;      (LEVEL_GAMEMASTERS)
 * /nerosecurity admin list                        (LEVEL_GAMEMASTERS) ids, names and counts; no identities
 * /nerosecurity admin dissolve &lt;installation&gt;    (LEVEL_GAMEMASTERS) orphaned installations only
 * </pre>
 *
 * <p>Feedback goes to the invoker only ({@code sendSuccess(..., false)}). Suggestions are id prefixes,
 * never names or owners. Every access decision goes through the resolver inside
 * {@link InstallationService}. Server thread only.
 */
public final class SecurityCommands {

    private SecurityCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("nerosecurity")
                .then(Commands.literal("installations")
                        .executes(ctx -> runSafely(ctx.getSource(), "installations", () -> listOwn(ctx.getSource()))))
                .then(Commands.literal("lockdown")
                        .then(InstallationArgument.argument(InstallationArgument.Scope.VISIBLE)
                                .then(Commands.literal("on")
                                        .executes(ctx -> runSafely(ctx.getSource(), "lockdown", () -> lockdown(ctx, true))))
                                .then(Commands.literal("off")
                                        .executes(ctx -> runSafely(ctx.getSource(), "lockdown", () -> lockdown(ctx, false))))))
                .then(Commands.literal("log")
                        .then(InstallationArgument.argument(InstallationArgument.Scope.VISIBLE)
                                .executes(ctx -> runSafely(ctx.getSource(), "log", () -> log(ctx, DEFAULT_LOG_COUNT)))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, AuditService.MAX_READ))
                                        .executes(ctx -> runSafely(ctx.getSource(), "log",
                                                () -> log(ctx, IntegerArgumentType.getInteger(ctx, "count")))))))
                .then(Commands.literal("keycards")
                        .then(InstallationArgument.argument(InstallationArgument.Scope.VISIBLE)
                                .then(Commands.literal("list")
                                        .executes(ctx -> runSafely(ctx.getSource(), "keycards list", () -> listCards(ctx))))
                                .then(Commands.literal("revoke")
                                        .then(Commands.argument("serial", LongArgumentType.longArg(1))
                                                .executes(ctx -> runSafely(ctx.getSource(), "keycards revoke",
                                                        () -> revokeCard(ctx)))))
                                .then(Commands.literal("revokeall")
                                        .executes(ctx -> runSafely(ctx.getSource(), "keycards revokeall",
                                                () -> revokeAllCards(ctx))))))
                .then(Commands.literal("automation")
                        .then(InstallationArgument.argument(InstallationArgument.Scope.VISIBLE)
                                .then(Commands.literal("off")
                                        .executes(ctx -> runSafely(ctx.getSource(), "automation", () -> automation(ctx, null))))
                                .then(Commands.literal("member")
                                        .executes(ctx -> runSafely(ctx.getSource(), "automation",
                                                () -> automation(ctx, AccessLevel.MEMBER))))
                                .then(Commands.literal("officer")
                                        .executes(ctx -> runSafely(ctx.getSource(), "automation",
                                                () -> automation(ctx, AccessLevel.OFFICER))))))
                .then(Commands.literal("notify-officers")
                        .then(InstallationArgument.argument(InstallationArgument.Scope.VISIBLE)
                                .then(Commands.literal("on")
                                        .executes(ctx -> runSafely(ctx.getSource(), "notify-officers",
                                                () -> notifyOfficers(ctx, true))))
                                .then(Commands.literal("off")
                                        .executes(ctx -> runSafely(ctx.getSource(), "notify-officers",
                                                () -> notifyOfficers(ctx, false))))))
                .then(Commands.literal("admin")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("unlock")
                                .then(InstallationArgument.argument(InstallationArgument.Scope.ALL)
                                        .executes(ctx -> runSafely(ctx.getSource(), "admin unlock", () -> adminUnlock(ctx)))))
                        .then(Commands.literal("list")
                                .executes(ctx -> runSafely(ctx.getSource(), "admin list", () -> adminList(ctx.getSource()))))
                        .then(Commands.literal("dissolve")
                                .then(InstallationArgument.argument(InstallationArgument.Scope.ALL)
                                        .executes(ctx -> runSafely(ctx.getSource(), "admin dissolve", () -> adminDissolve(ctx)))))));
        BindCommands.register(dispatcher);
    }

    // --- player subcommands ---------------------------------------------------------------------

    /** The invoker's own installations: short id, name, the invoker's stored level, lockdown flag. */
    private static int listOwn(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        MinecraftServer server = source.getServer();
        if (player == null || server == null) {
            source.sendFailure(Component.translatable("command.nerosecurity.players_only"));
            return 0;
        }
        UUID self = player.getUUID();
        List<Installation> visible = SecurityInstallations.get(server).visibleTo(self);
        if (visible.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.nerosecurity.installations.none"), false);
            return 1;
        }
        for (Installation installation : visible) {
            Component level = installation.isOwner(self)
                    ? AccessLevel.OWNER.displayName()
                    : installation.memberLevel(self).displayName();
            Component suffix = installation.lockdown().active()
                    ? Component.translatable("command.nerosecurity.installations.lockdown") : Component.empty();
            source.sendSuccess(() -> Component.translatable("command.nerosecurity.installations.row",
                    InstallationNames.shortId(installation.id()), installation.name(), level, suffix), false);
        }
        return visible.size();
    }

    private static int lockdown(CommandContext<CommandSourceStack> ctx, boolean on) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.nerosecurity.players_only"));
            return 0;
        }
        Installation installation = InstallationArgument.resolve(ctx, InstallationArgument.Scope.VISIBLE);
        if (installation == null) {
            return 0;
        }
        ServiceResult result = on ? InstallationService.enterLockdown(player, installation)
                : InstallationService.exitLockdown(player, installation);
        return report(source, result);
    }

    // --- audit log ------------------------------------------------------------------------------

    private static final int DEFAULT_LOG_COUNT = 20;

    /**
     * The privileged log read: {@link AuditService#read} resolves READ_LOG at OFFICER and records the read
     * as a LOG_READ row before returning anything. Only then are actors rendered, through the cache-only
     * {@link PlayerNames#display} ("?" when the server has no local name; never a remote lookup).
     */
    private static int log(CommandContext<CommandSourceStack> ctx, int count) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        Installation installation = InstallationArgument.resolve(ctx, InstallationArgument.Scope.VISIBLE);
        if (installation == null) {
            return 0;
        }
        AuditService.ReadResult result = AuditService.read(player, installation, null, count);
        if (!result.ok()) {
            source.sendFailure(result.message());
            return 0;
        }
        if (result.entries().isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.nerosecurity.log.none"), false);
            return 0;
        }
        MinecraftServer server = source.getServer();
        long now = System.currentTimeMillis();
        source.sendSuccess(() -> Component.translatable("command.nerosecurity.log.header", installation.name(),
                result.entries().size()), false);
        for (AuditEntry entry : result.entries()) {
            Component row = Component.translatable("command.nerosecurity.log.row", logAge(now - entry.at()),
                    EventPhrases.phrase(entry.type()), logPos(entry.devicePos()), logActor(server, entry));
            source.sendSuccess(() -> row, false);
        }
        return result.entries().size();
    }

    /** "12s ago", "5m ago", "3h ago", "2d ago"; never an absolute time. */
    private static Component logAge(long millis) {
        long seconds = Math.max(0L, millis) / 1000L;
        if (seconds < 60) {
            return Component.translatable("command.nerosecurity.log.age.seconds", seconds);
        }
        if (seconds < 3600) {
            return Component.translatable("command.nerosecurity.log.age.minutes", seconds / 60);
        }
        if (seconds < 86_400) {
            return Component.translatable("command.nerosecurity.log.age.hours", seconds / 3600);
        }
        return Component.translatable("command.nerosecurity.log.age.days", seconds / 86_400);
    }

    /** The owner's device position, or a dash. */
    private static Component logPos(DevicePos pos) {
        if (pos == null) {
            return Component.translatable("command.nerosecurity.log.pos.none");
        }
        return Component.literal(pos.pos().getX() + " " + pos.pos().getY() + " " + pos.pos().getZ());
    }

    private static Component logActor(MinecraftServer server, AuditEntry entry) {
        if (entry.actorWithheld()) {
            return Component.translatable("command.nerosecurity.log.actor.anonymous");
        }
        if (entry.actor() == null) {
            return Component.translatable("command.nerosecurity.log.actor.none");
        }
        String name = PlayerNames.display(server, entry.actor());
        return PlayerNames.UNKNOWN.equals(name)
                ? Component.translatable("command.nerosecurity.log.actor.unknown") : Component.literal(name);
    }

    // --- keycards and automation ----------------------------------------------------------------

    /** Ledger rows: serial, level, flags and age. Never a holder, a name or an absolute time. */
    private static int listCards(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Installation installation = InstallationArgument.resolve(ctx, InstallationArgument.Scope.VISIBLE);
        if (installation == null) {
            return 0;
        }
        AccessDecision decision = AccessResolver.DEFAULT.resolve(source, installation, AccessAction.READ_LOG);
        if (!decision.allowed()) {
            source.sendFailure(decision.message());
            return 0;
        }
        Map<Long, CardRecord> cards = installation.cards();
        if (cards.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.nerosecurity.keycards.none"), false);
            return 0;
        }
        long now = System.currentTimeMillis();
        cards.forEach((serial, card) -> {
            Component encrypted = card.encrypted()
                    ? Component.translatable("command.nerosecurity.keycards.encrypted") : Component.empty();
            Component bound = card.boundOwner() != null
                    ? Component.translatable("command.nerosecurity.keycards.bound") : Component.empty();
            source.sendSuccess(() -> Component.translatable("command.nerosecurity.keycards.row", serial,
                    card.level().displayName(), encrypted, bound, age(now - card.issuedAt())), false);
        });
        return cards.size();
    }

    /** "3 d", "5 h" or "just now"; never an absolute time. */
    private static Component age(long millis) {
        long hours = Math.max(0L, millis) / 3_600_000L;
        if (hours >= 24) {
            return Component.translatable("command.nerosecurity.keycards.age.days", hours / 24);
        }
        if (hours >= 1) {
            return Component.translatable("command.nerosecurity.keycards.age.hours", hours);
        }
        return Component.translatable("command.nerosecurity.keycards.age.now");
    }

    private static int revokeCard(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = requirePlayer(ctx.getSource());
        if (player == null) {
            return 0;
        }
        Installation installation = InstallationArgument.resolve(ctx, InstallationArgument.Scope.VISIBLE);
        if (installation == null) {
            return 0;
        }
        long serial = LongArgumentType.getLong(ctx, "serial");
        return report(ctx.getSource(), KeycardService.revoke(player, installation, serial));
    }

    private static int revokeAllCards(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = requirePlayer(ctx.getSource());
        if (player == null) {
            return 0;
        }
        Installation installation = InstallationArgument.resolve(ctx, InstallationArgument.Scope.VISIBLE);
        return installation == null ? 0 : report(ctx.getSource(), KeycardService.revokeAll(player, installation));
    }

    private static int automation(CommandContext<CommandSourceStack> ctx, AccessLevel level) {
        ServerPlayer player = requirePlayer(ctx.getSource());
        if (player == null) {
            return 0;
        }
        Installation installation = InstallationArgument.resolve(ctx, InstallationArgument.Scope.VISIBLE);
        return installation == null ? 0
                : report(ctx.getSource(), KeycardService.setAutomation(player, installation, level));
    }

    /** OWNER: whether officers also receive alarm chat. Re-resolved by the service on every use. */
    private static int notifyOfficers(CommandContext<CommandSourceStack> ctx, boolean on) {
        ServerPlayer player = requirePlayer(ctx.getSource());
        if (player == null) {
            return 0;
        }
        Installation installation = InstallationArgument.resolve(ctx, InstallationArgument.Scope.VISIBLE);
        return installation == null ? 0
                : report(ctx.getSource(), InstallationService.setNotifyOfficers(player, installation, on));
    }

    private static ServerPlayer requirePlayer(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.nerosecurity.players_only"));
        }
        return player;
    }

    // --- operator subcommands -------------------------------------------------------------------

    private static int adminUnlock(CommandContext<CommandSourceStack> ctx) {
        Installation installation = InstallationArgument.resolve(ctx, InstallationArgument.Scope.ALL);
        return installation == null ? 0 : report(ctx.getSource(), InstallationService.adminUnlock(ctx.getSource(), installation));
    }

    private static int adminDissolve(CommandContext<CommandSourceStack> ctx) {
        Installation installation = InstallationArgument.resolve(ctx, InstallationArgument.Scope.ALL);
        return installation == null ? 0 : report(ctx.getSource(), InstallationService.adminDissolve(ctx.getSource(), installation));
    }

    /** Ids, names, flags and counts only: no owner or member identities. */
    private static int adminList(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        if (server == null) {
            source.sendFailure(Component.translatable("command.nerosecurity.not_found"));
            return 0;
        }
        List<Installation> all = SecurityInstallations.get(server).all();
        if (all.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.nerosecurity.admin.list.empty"), false);
            return 0;
        }
        for (Installation installation : all) {
            long owners = installation.owners().stream().filter(owner -> !Installation.TOMBSTONE_OWNER.equals(owner)).count();
            Component orphaned = installation.isOrphaned()
                    ? Component.translatable("command.nerosecurity.admin.list.orphaned") : Component.empty();
            Component locked = installation.lockdown().active()
                    ? Component.translatable("command.nerosecurity.installations.lockdown") : Component.empty();
            source.sendSuccess(() -> Component.translatable("command.nerosecurity.admin.list.row",
                    InstallationNames.shortId(installation.id()), installation.name(), owners,
                    installation.members().size(), installation.deviceCount(), orphaned, locked), false);
        }
        return all.size();
    }

    // --- plumbing -------------------------------------------------------------------------------

    private static int report(CommandSourceStack source, ServiceResult result) {
        if (result.ok()) {
            source.sendSuccess(result::message, false);
            return 1;
        }
        source.sendFailure(result.message());
        return 0;
    }

    private interface CommandBody {
        int run();
    }

    /** Catches a failing subcommand, reports it by subcommand name only, and tells the invoker. */
    private static int runSafely(CommandSourceStack source, String subcommand, CommandBody body) {
        try {
            return body.run();
        } catch (RuntimeException | LinkageError e) {
            NeroSecurityTelemetry.captureHandledException(e, "command", "/nerosecurity " + subcommand);
            NeroSecurityCommon.LOGGER.error("[NeroSecurity] /nerosecurity {} failed ({})", subcommand,
                    e.getClass().getName());
            source.sendFailure(Component.translatable("command.nerosecurity.failed", subcommand));
            return 0;
        }
    }
}
