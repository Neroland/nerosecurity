package za.co.neroland.nerosecurity.platform;

import java.util.function.Predicate;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The few vanilla calls whose shape differs between the Minecraft lines NeroSecurity builds for, kept in one
 * place so gameplay code stays one copy. Each method is a straight pass-through on 26.x.
 */
public final class VersionCompat {

    private VersionCompat() {
    }

    /** A short message above the hotbar (the action bar) for one player. */
    public static void overlay(Player player, Component message) {
        //? if >=26.1 {
        player.sendOverlayMessage(message);
        //?} else {
        /*player.displayClientMessage(message, true);
        *///?}
    }

    /** A chat-line system message to one player (server or client side). */
    public static void systemMessage(Player player, Component message) {
        //? if >=26.1 {
        player.sendSystemMessage(message);
        //?} else {
        /*player.displayClientMessage(message, false);
        *///?}
    }

    /** The command requirement for permission level 2 (operators / gamemasters). */
    public static Predicate<CommandSourceStack> gamemasters() {
        //? if >=1.21.6 {
        return Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
        //?} else {
        /*return source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS);
        *///?}
    }

    /** The level a server player is in (typed as {@link ServerLevel} from 1.21.6; {@code serverLevel()} before). */
    public static ServerLevel serverLevel(ServerPlayer player) {
        //? if >=1.21.6 {
        return player.level();
        //?} else {
        /*return player.serverLevel();
        *///?}
    }
}
