package za.co.neroland.nerosecurity.neoforge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import za.co.neroland.nerosecurity.block.SecurityHubBlock;
import za.co.neroland.nerosecurity.block.field.ForceFieldBlock;
import za.co.neroland.nerosecurity.command.SecurityCommands;
import za.co.neroland.nerosecurity.server.ServerHooks;

/**
 * NeoForge server-side wiring: the {@code /nerosecurity} command tree (built in common), the server
 * lifecycle hooks ({@link ServerHooks}: started, stopping, end of tick, player join and leave) and the hub
 * break guard. 26.x moved the break event to {@code event.level.block.BreakBlockEvent}; it fires for every
 * player break, creative included, so creative mode cannot bypass the hub's break rule.
 */
public final class NeoForgeSecurityEvents {

    private NeoForgeSecurityEvents() {
    }

    /** Called once from the NeoForge entry point. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                SecurityCommands.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((BreakBlockEvent event) -> {
            if (event.getState().getBlock() instanceof SecurityHubBlock
                    && event.getLevel() instanceof ServerLevel level
                    && event.getPlayer() instanceof ServerPlayer player
                    && !SecurityHubBlock.mayBreak(level, event.getPos(), player)) {
                event.setCanceled(true);
                SecurityHubBlock.refuseBreak(player);
            } else if (event.getState().getBlock() instanceof ForceFieldBlock
                    && event.getLevel() instanceof ServerLevel level
                    && event.getPlayer() instanceof ServerPlayer player
                    && !ForceFieldBlock.mayBreak(level, event.getPos(), player)) {
                event.setCanceled(true);
                ForceFieldBlock.refuseBreak(player);
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> ServerHooks.fireStarted(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> ServerHooks.fireStopping(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> ServerHooks.fireTick(event.getServer()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                ServerHooks.firePlayerJoin(player);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                ServerHooks.firePlayerLeave(player);
            }
        });
    }
}
