package za.co.neroland.nerosecurity.access;

import java.util.UUID;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

/**
 * The only thing in NeroSecurity that can produce an operator override. Read server-side on every
 * decision; never cached, never stored, never accepted from a packet or an argument.
 */
@FunctionalInterface
public interface AdminCheck {

    boolean isAdmin(@Nullable MinecraftServer server, UUID actor);

    /** Nobody is an admin (tests). */
    AdminCheck NONE = (server, actor) -> false;

    /**
     * An online operator at permission level 2 ({@code LEVEL_GAMEMASTERS}) or above, using exactly the
     * predicate brigadier's {@code requires(Commands.hasPermission(...))} uses. Offline actors are never
     * admin.
     */
    AdminCheck SERVER = (server, actor) -> {
        if (server == null || actor == null) {
            return false;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(actor);
        return player != null
                && Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(player.createCommandSourceStack());
    };

    /**
     * For a command run by a non-player source (the console or a command block): the source's own
     * permission decides, through the same predicate. The actor UUID is ignored.
     */
    static AdminCheck forSource(CommandSourceStack source) {
        return (server, actor) -> Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source);
    }
}
