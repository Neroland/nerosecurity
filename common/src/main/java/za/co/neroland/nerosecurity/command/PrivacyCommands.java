package za.co.neroland.nerosecurity.command;

import java.util.function.Function;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.privacy.PrivacyActions;
import za.co.neroland.nerosecurity.telemetry.NeroSecurityTelemetry;

/**
 * {@code /nerosecurity privacy optout|optin|export|status}: the player's own privacy controls (POPIA/GDPR).
 * No permission level and no target argument: every subcommand acts on the invoking player only, through the
 * same {@link PrivacyActions} the hub's Privacy tab uses. Feedback goes to the invoker only. Erasure is Neroland
 * Core's {@code /neroland data eraseme}, which covers every Neroland mod.
 */
public final class PrivacyCommands {

    public static final String KEY_STATUS_OUT = "command.nerosecurity.privacy.status.out";
    public static final String KEY_STATUS_IN = "command.nerosecurity.privacy.status.in";

    private PrivacyCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("nerosecurity")
                .then(Commands.literal("privacy")
                        .executes(ctx -> run(ctx.getSource(), "status", PrivacyCommands::status))
                        .then(Commands.literal("status")
                                .executes(ctx -> run(ctx.getSource(), "status", PrivacyCommands::status)))
                        .then(Commands.literal("optout")
                                .executes(ctx -> run(ctx.getSource(), "optout",
                                        p -> PrivacyActions.current().setOptOut(p, true))))
                        .then(Commands.literal("optin")
                                .executes(ctx -> run(ctx.getSource(), "optin",
                                        p -> PrivacyActions.current().setOptOut(p, false))))
                        .then(Commands.literal("export")
                                .executes(ctx -> run(ctx.getSource(), "export", p -> PrivacyActions.current().export(p))))));
    }

    private static PrivacyActions.Outcome status(ServerPlayer player) {
        PrivacyActions.Status status = PrivacyActions.current().status(player);
        boolean out = status != null && status.optedOut();
        return new PrivacyActions.Outcome(true, Component.translatable(out ? KEY_STATUS_OUT : KEY_STATUS_IN));
    }

    private static int run(CommandSourceStack source, String operation,
            Function<ServerPlayer, PrivacyActions.Outcome> action) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.nerosecurity.players_only"));
            return 0;
        }
        try {
            PrivacyActions.Outcome outcome = action.apply(player);
            if (outcome == null) {
                source.sendFailure(Component.translatable(PrivacyActions.UNAVAILABLE_KEY));
                return 0;
            }
            if (outcome.ok()) {
                source.sendSuccess(outcome::message, false);
                return 1;
            }
            source.sendFailure(outcome.message());
            return 0;
        } catch (RuntimeException | LinkageError e) {
            NeroSecurityCommon.LOGGER.warn("[NeroSecurity] privacy command {} failed ({}).", operation,
                    e.getClass().getName());
            NeroSecurityTelemetry.captureHandledException(e, "privacy_command", operation);
            source.sendFailure(Component.translatable("command.nerosecurity.failed", "privacy " + operation));
            return 0;
        }
    }
}
