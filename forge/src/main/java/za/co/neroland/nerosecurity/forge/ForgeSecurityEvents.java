package za.co.neroland.nerosecurity.forge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
//? if >=1.21.6 {
import net.minecraftforge.common.util.Result;
//?} else {
/*import net.minecraftforge.common.MinecraftForge;
*///?}
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

import za.co.neroland.nerosecurity.block.SecurityHubBlock;
import za.co.neroland.nerosecurity.block.field.ForceFieldBlock;
import za.co.neroland.nerosecurity.command.SecurityCommands;
import za.co.neroland.nerosecurity.server.ServerHooks;

/**
 * Forge server-side wiring: the {@code /nerosecurity} command tree (built in common), the server
 * lifecycle hooks ({@link ServerHooks}: started, stopping, end of tick, player join and leave) and the hub
 * break guard. The break event fires for every player break, creative included; Forge reads the result back, so
 * a refused break is denied rather than cancelled.
 */
public final class ForgeSecurityEvents {

    private ForgeSecurityEvents() {
    }

    //? if >=1.21.9 {
    /** Called once from the Forge entry point. */
    public static void register() {
        RegisterCommandsEvent.BUS.addListener(event -> SecurityCommands.register(event.getDispatcher()));
        BlockEvent.BreakEvent.BUS.addListener(event -> {
            if (event.getState().getBlock() instanceof SecurityHubBlock
                    && event.getLevel() instanceof ServerLevel level
                    && event.getPlayer() instanceof ServerPlayer player
                    && !SecurityHubBlock.mayBreak(level, event.getPos(), player)) {
                event.setResult(Result.DENY);
                SecurityHubBlock.refuseBreak(player);
            } else if (event.getState().getBlock() instanceof ForceFieldBlock
                    && event.getLevel() instanceof ServerLevel level
                    && event.getPlayer() instanceof ServerPlayer player
                    && !ForceFieldBlock.mayBreak(level, event.getPos(), player)) {
                event.setResult(Result.DENY);
                ForceFieldBlock.refuseBreak(player);
            }
        });
        ServerStartedEvent.BUS.addListener(event -> ServerHooks.fireStarted(event.getServer()));
        ServerStoppingEvent.BUS.addListener(event -> ServerHooks.fireStopping(event.getServer()));
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> ServerHooks.fireTick(event.server()));
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                ServerHooks.firePlayerJoin(player);
            }
        });
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                ServerHooks.firePlayerLeave(player);
            }
        });
    }
    //?} else if >=1.21.6 {
    /*// Forge 1.21.6 - 1.21.8: the tick event exposes its server through getServer().
    public static void register() {
        RegisterCommandsEvent.BUS.addListener(event -> SecurityCommands.register(event.getDispatcher()));
        BlockEvent.BreakEvent.BUS.addListener(event -> {
            if (event.getState().getBlock() instanceof SecurityHubBlock
                    && event.getLevel() instanceof ServerLevel level
                    && event.getPlayer() instanceof ServerPlayer player
                    && !SecurityHubBlock.mayBreak(level, event.getPos(), player)) {
                event.setResult(Result.DENY);
                SecurityHubBlock.refuseBreak(player);
            } else if (event.getState().getBlock() instanceof ForceFieldBlock
                    && event.getLevel() instanceof ServerLevel level
                    && event.getPlayer() instanceof ServerPlayer player
                    && !ForceFieldBlock.mayBreak(level, event.getPos(), player)) {
                event.setResult(Result.DENY);
                ForceFieldBlock.refuseBreak(player);
            }
        });
        ServerStartedEvent.BUS.addListener(event -> ServerHooks.fireStarted(event.getServer()));
        ServerStoppingEvent.BUS.addListener(event -> ServerHooks.fireStopping(event.getServer()));
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> ServerHooks.fireTick(event.getServer()));
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                ServerHooks.firePlayerJoin(player);
            }
        });
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                ServerHooks.firePlayerLeave(player);
            }
        });
    }
    *///?} else {
    /*// Forge before 1.21.6 (EventBus 6): one global game bus, typed listeners, and a cancellable break event.
    public static void register() {
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> SecurityCommands.register(event.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener((BlockEvent.BreakEvent event) -> {
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
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) -> ServerHooks.fireStarted(event.getServer()));
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> ServerHooks.fireStopping(event.getServer()));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent.Post event) -> ServerHooks.fireTick(event.getServer()));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                ServerHooks.firePlayerJoin(player);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                ServerHooks.firePlayerLeave(player);
            }
        });
    }
    *///?}
}
