package za.co.neroland.nerosecurity.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.block.SecurityHubBlock;
import za.co.neroland.nerosecurity.block.field.ForceFieldBlock;
import za.co.neroland.nerosecurity.command.SecurityCommands;
import za.co.neroland.nerosecurity.server.ServerHooks;

/** Fabric entry point for NeroSecurity. */
public final class NeroSecurityFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        NeroSecurityCommon.LOGGER.info("[NeroSecurity] Fabric bootstrap");
        // Common init declares the payloads; the registration below consumes those declarations,
        // so the ordering is mandatory on every loader.
        NeroSecurityCommon.init();
        FabricSecurityNetwork.registerCommon();
        // Door energy and the secure chest's gated item storage.
        FabricCapabilities.register();
        // The /nerosecurity tree; the tree itself is loader-agnostic.
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                SecurityCommands.register(dispatcher));
        // Hub break guard: fires for every player break, creative included, so creative mode cannot
        // bypass the hub's break rule. Returning false cancels the break.
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (state.getBlock() instanceof SecurityHubBlock && level instanceof ServerLevel serverLevel
                    && player instanceof ServerPlayer serverPlayer
                    && !SecurityHubBlock.mayBreak(serverLevel, pos, serverPlayer)) {
                SecurityHubBlock.refuseBreak(serverPlayer);
                return false;
            }
            // Force field blocks: only an owner or an operator removes one, in creative too.
            if (state.getBlock() instanceof ForceFieldBlock && level instanceof ServerLevel serverLevel
                    && player instanceof ServerPlayer serverPlayer
                    && !ForceFieldBlock.mayBreak(serverLevel, pos, serverPlayer)) {
                ForceFieldBlock.refuseBreak(serverPlayer);
                return false;
            }
            return true;
        });
        // Server lifecycle seam for common code (audit binding, retention sweep, notices, lockdown timer).
        ServerLifecycleEvents.SERVER_STARTED.register(ServerHooks::fireStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(ServerHooks::fireStopping);
        ServerTickEvents.END_SERVER_TICK.register(ServerHooks::fireTick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ServerHooks.firePlayerJoin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ServerHooks.firePlayerLeave(handler.player));
    }
}
