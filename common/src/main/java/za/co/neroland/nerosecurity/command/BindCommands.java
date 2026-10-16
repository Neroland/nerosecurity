package za.co.neroland.nerosecurity.command;

import java.util.Locale;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.access.AccessAction;
import za.co.neroland.nerosecurity.access.AccessDecision;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.access.AccessResolver;
import za.co.neroland.nerosecurity.compat.BindingService;
import za.co.neroland.nerosecurity.compat.CompatSources;
import za.co.neroland.nerosecurity.compat.colonies.ColoniesCompat;
import za.co.neroland.nerosecurity.compat.factions.FactionsCompat;
import za.co.neroland.nerosecurity.compat.nerospace.NerospaceCompat;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.ServiceResult;
import za.co.neroland.nerosecurity.telemetry.NeroSecurityTelemetry;

/**
 * The integration binding commands, merged into the {@code /nerosecurity} tree.
 *
 * <pre>
 * /nerosecurity bind &lt;installation&gt;                                       show the bindings (MEMBER, like the hub's read view)
 * /nerosecurity bind &lt;installation&gt; faction &lt;factionId&gt; [member|officer]  (OWNER) you must be a member of the faction
 * /nerosecurity bind &lt;installation&gt; colony here|&lt;colonyId&gt; [member|officer] (OWNER) you must be a member of the colony
 * /nerosecurity bind &lt;installation&gt; station &lt;id&gt;                          (OWNER) you must own the station
 * /nerosecurity unbind &lt;installation&gt; faction|colony|station            (OWNER)
 * </pre>
 *
 * <p>Players only, no operator permission: every write goes through {@link BindingService}, which resolves OWNER
 * and checks the binder's own membership on every invocation. Suggestions list only the invoker's own factions and
 * stations (never anyone else's), and feedback goes to the invoker alone and never names another player.
 * {@code colony here} resolves the colony (or an outpost's parent colony) the invoker is standing in. The max level
 * defaults to MEMBER ({@link BindingService#DEFAULT_MAX_LEVEL}); OFFICER, the most an integration can ever grant
 * (it shows the organisation's members the hub roster and the audit log), must be typed explicitly. Server thread
 * only.
 */
public final class BindCommands {

    private BindCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("nerosecurity")
                .then(Commands.literal("bind")
                        .then(InstallationArgument.argument(InstallationArgument.Scope.VISIBLE)
                                .executes(ctx -> runSafely(ctx.getSource(), "bind", () -> show(ctx)))
                                .then(Commands.literal("faction")
                                        .then(withMax(Commands.argument("faction", IdentifierArgument.id())
                                                .suggests(OWN_FACTIONS), "bind faction", BindCommands::bindFaction)))
                                .then(Commands.literal("colony")
                                        .then(withMax(Commands.literal("here"), "bind colony", BindCommands::bindColonyHere))
                                        .then(withMax(Commands.argument("colony", UuidArgument.uuid()), "bind colony",
                                                BindCommands::bindColonyId)))
                                .then(Commands.literal("station")
                                        .then(Commands.argument("station", IntegerArgumentType.integer())
                                                .suggests(OWN_STATIONS)
                                                .executes(ctx -> runSafely(ctx.getSource(), "bind station",
                                                        () -> bindStation(ctx)))))))
                .then(Commands.literal("unbind")
                        .then(InstallationArgument.argument(InstallationArgument.Scope.VISIBLE)
                                .then(Commands.literal("faction")
                                        .executes(ctx -> runSafely(ctx.getSource(), "unbind faction",
                                                () -> unbind(ctx, Kind.FACTION))))
                                .then(Commands.literal("colony")
                                        .executes(ctx -> runSafely(ctx.getSource(), "unbind colony",
                                                () -> unbind(ctx, Kind.COLONY))))
                                .then(Commands.literal("station")
                                        .executes(ctx -> runSafely(ctx.getSource(), "unbind station",
                                                () -> unbind(ctx, Kind.STATION)))))));
    }

    private enum Kind { FACTION, COLONY, STATION }

    private interface MaxBody {
        int run(CommandContext<CommandSourceStack> ctx, AccessLevel max);
    }

    /** {@code node} runs with MEMBER (the default), or with an explicit trailing {@code member|officer}. */
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T withMax(T node,
            String name, MaxBody body) {
        return node
                .executes(ctx -> runSafely(ctx.getSource(), name, () -> body.run(ctx, BindingService.DEFAULT_MAX_LEVEL)))
                .then(Commands.literal("member")
                        .executes(ctx -> runSafely(ctx.getSource(), name, () -> body.run(ctx, AccessLevel.MEMBER))))
                .then(Commands.literal("officer")
                        .executes(ctx -> runSafely(ctx.getSource(), name, () -> body.run(ctx, AccessLevel.OFFICER))));
    }

    // --- suggestions: the invoker's own organisations only ----------------------------------------

    private static final SuggestionProvider<CommandSourceStack> OWN_FACTIONS = (ctx, builder) -> {
        ServerPlayer player = ctx.getSource().getPlayer();
        MinecraftServer server = ctx.getSource().getServer();
        if (player != null && server != null) {
            String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
            FactionsCompat.membershipsOf(server, player.getUUID()).stream()
                    .map(Identifier::toString)
                    .filter(id -> id.startsWith(prefix))
                    .sorted()
                    .forEach(builder::suggest);
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> OWN_STATIONS = (ctx, builder) -> {
        ServerPlayer player = ctx.getSource().getPlayer();
        MinecraftServer server = ctx.getSource().getServer();
        if (player != null && server != null) {
            String prefix = builder.getRemaining();
            for (Integer station : NerospaceCompat.stationsOwnedBy(server, player.getUUID())) {
                String text = String.valueOf(station);
                if (text.startsWith(prefix)) {
                    builder.suggest(text);
                }
            }
        }
        return builder.buildFuture();
    };

    // --- subcommands ------------------------------------------------------------------------------

    private static int bindFaction(CommandContext<CommandSourceStack> ctx, AccessLevel max) {
        ServerPlayer player = requirePlayer(ctx.getSource());
        if (player == null) {
            return 0;
        }
        Installation installation = InstallationArgument.resolve(ctx, InstallationArgument.Scope.VISIBLE);
        if (installation == null) {
            return 0;
        }
        Identifier faction = IdentifierArgument.getId(ctx, "faction");
        return report(ctx.getSource(), BindingService.DEFAULT.bindFaction(player, installation, faction, max).result());
    }

    private static int bindColonyHere(CommandContext<CommandSourceStack> ctx, AccessLevel max) {
        ServerPlayer player = requirePlayer(ctx.getSource());
        if (player == null) {
            return 0;
        }
        Installation installation = InstallationArgument.resolve(ctx, InstallationArgument.Scope.VISIBLE);
        if (installation == null) {
            return 0;
        }
        if (!ColoniesCompat.isActive()) {
            ctx.getSource().sendFailure(Component.translatable("compat.nerosecurity.absent"));
            return 0;
        }
        UUID colony = ColoniesCompat.colonyAt(player.level(), player.blockPosition());
        if (colony == null) {
            ctx.getSource().sendFailure(Component.translatable("command.nerosecurity.bind.no_colony_here"));
            return 0;
        }
        return report(ctx.getSource(), BindingService.DEFAULT.bindColony(player, installation, colony, max).result());
    }

    private static int bindColonyId(CommandContext<CommandSourceStack> ctx, AccessLevel max) {
        ServerPlayer player = requirePlayer(ctx.getSource());
        if (player == null) {
            return 0;
        }
        Installation installation = InstallationArgument.resolve(ctx, InstallationArgument.Scope.VISIBLE);
        if (installation == null) {
            return 0;
        }
        UUID colony = UuidArgument.getUuid(ctx, "colony");
        return report(ctx.getSource(), BindingService.DEFAULT.bindColony(player, installation, colony, max).result());
    }

    private static int bindStation(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = requirePlayer(ctx.getSource());
        if (player == null) {
            return 0;
        }
        Installation installation = InstallationArgument.resolve(ctx, InstallationArgument.Scope.VISIBLE);
        if (installation == null) {
            return 0;
        }
        int station = IntegerArgumentType.getInteger(ctx, "station");
        return report(ctx.getSource(), BindingService.DEFAULT.bindStation(player, installation, station).result());
    }

    private static int unbind(CommandContext<CommandSourceStack> ctx, Kind kind) {
        ServerPlayer player = requirePlayer(ctx.getSource());
        if (player == null) {
            return 0;
        }
        Installation installation = InstallationArgument.resolve(ctx, InstallationArgument.Scope.VISIBLE);
        if (installation == null) {
            return 0;
        }
        BindingService service = BindingService.DEFAULT;
        BindingService.Outcome outcome = switch (kind) {
            case FACTION -> service.unbindFaction(player, installation);
            case COLONY -> service.unbindColony(player, installation);
            case STATION -> service.unbindStation(player, installation);
        };
        return report(ctx.getSource(), outcome.result());
    }

    /**
     * The installation's bindings: organisation ids and max levels only, each marked suspended (no owner is still a
     * member) or "(mod not installed)" (the integration is inactive; the binding is kept and does nothing).
     */
    private static int show(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        Installation installation = InstallationArgument.resolve(ctx, InstallationArgument.Scope.VISIBLE);
        if (installation == null) {
            return 0;
        }
        AccessDecision decision = AccessResolver.DEFAULT.resolve(player, installation, AccessAction.OPEN,
                installation.hubPos());
        if (!decision.allowed()) {
            source.sendFailure(decision.message());
            return 0;
        }
        MinecraftServer server = source.getServer();
        BindingService service = BindingService.DEFAULT;

        Identifier faction = installation.boundFaction();
        Component factionText = faction == null ? none() : Component.literal(faction.toString());
        Component factionMark = faction == null ? Component.empty()
                : mark(service.factionsActive(), CompatSources.FACTION.isSuspended(server, installation));

        UUID colony = installation.boundColony();
        Component colonyText = colony == null ? none() : Component.literal(colony.toString());
        Component colonyMark = colony == null ? Component.empty()
                : mark(service.coloniesActive(), CompatSources.COLONY.isSuspended(server, installation));

        Integer station = installation.boundStation();
        Component stationText = station == null ? none() : Component.literal("#" + station)
                .append(service.nerospaceActive() ? Component.empty()
                        : Component.translatable("command.nerosecurity.bind.absent"));

        Component row = Component.translatable("command.nerosecurity.bind.show", installation.name(),
                factionText, installation.factionMaxLevel().displayName(), factionMark,
                colonyText, installation.colonyMaxLevel().displayName(), colonyMark, stationText);
        source.sendSuccess(() -> row, false);
        return 1;
    }

    private static MutableComponent none() {
        return Component.translatable("command.nerosecurity.bind.none");
    }

    private static Component mark(boolean active, boolean suspended) {
        if (!active) {
            return Component.translatable("command.nerosecurity.bind.absent");
        }
        return suspended ? Component.translatable("command.nerosecurity.bind.suspended") : Component.empty();
    }

    // --- plumbing (the same shape as SecurityCommands) ---------------------------------------------

    private static @Nullable ServerPlayer requirePlayer(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.nerosecurity.players_only"));
        }
        return player;
    }

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
