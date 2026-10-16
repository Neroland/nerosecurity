package za.co.neroland.nerosecurity.command;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.InstallationNames;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;

/**
 * The {@code <installation>} command argument: a full id, an id prefix of at least eight hex characters,
 * or an exact display name (quote a name that contains spaces). Suggestions are the first eight hex characters of each id in scope and nothing
 * else: never names, never owners. A player's scope is the installations they own or belong to; the
 * operator subcommands' scope is every installation.
 */
public final class InstallationArgument {

    /** Which installations an invocation may address. */
    public enum Scope { VISIBLE, ALL }

    private static final String NAME = "installation";

    private InstallationArgument() {
    }

    public static RequiredArgumentBuilder<CommandSourceStack, String> argument(Scope scope) {
        // string(): an unquoted id prefix or single-word name as before, or a "quoted name with spaces".
        return Commands.argument(NAME, StringArgumentType.string())
                .suggests((ctx, builder) -> {
                    MinecraftServer server = ctx.getSource().getServer();
                    Predicate<Installation> filter = filter(ctx.getSource(), scope);
                    if (server != null && filter != null) {
                        String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
                        for (Installation installation : SecurityInstallations.get(server).all()) {
                            String shortId = InstallationNames.shortId(installation.id());
                            if (filter.test(installation) && shortId.startsWith(prefix)) {
                                builder.suggest(shortId);
                            }
                        }
                    }
                    return builder.buildFuture();
                });
    }

    /**
     * Resolves the argument within scope, telling the invoker "not found" or "ambiguous" itself.
     *
     * @return the installation, or null after a failure message was sent
     */
    public static @Nullable Installation resolve(CommandContext<CommandSourceStack> ctx, Scope scope) {
        CommandSourceStack source = ctx.getSource();
        Predicate<Installation> filter = filter(source, scope);
        if (filter == null || source.getServer() == null) {
            source.sendFailure(Component.translatable("command.nerosecurity.not_found"));
            return null;
        }
        SecurityInstallations.LookupResult result = SecurityInstallations.get(source.getServer())
                .resolveDetailed(StringArgumentType.getString(ctx, NAME), filter);
        switch (result.status()) {
            case FOUND -> {
                return result.installation();
            }
            case AMBIGUOUS -> source.sendFailure(Component.translatable("command.nerosecurity.ambiguous"));
            default -> source.sendFailure(Component.translatable("command.nerosecurity.not_found"));
        }
        return null;
    }

    private static @Nullable Predicate<Installation> filter(CommandSourceStack source, Scope scope) {
        if (scope == Scope.ALL) {
            return installation -> true;
        }
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return null;
        }
        UUID id = player.getUUID();
        return installation -> installation.isOwner(id) || installation.members().containsKey(id);
    }
}
