package za.co.neroland.nerosecurity.lockdown;

import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.block.chest.SecureChestBlockEntity;
import za.co.neroland.nerosecurity.block.door.SecurityDoorBlockEntity;
import za.co.neroland.nerosecurity.block.entity.SecurityHubBlockEntity;
import za.co.neroland.nerosecurity.block.monitor.SecurityMonitorBlockEntity;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.sound.SecuritySounds;

/**
 * Paints a lockdown transition the moment it happens: the hub's and monitors' {@code LOCKDOWN} look, the
 * doors' {@code SEALED} clamps, the secure chests' {@code LOCK} strobe, and the lockdown tone at the hub. Only loaded blocks are touched (an unloaded
 * chunk is never loaded for this); each device re-derives the same state from the store on its own refresh,
 * so this hook is an accelerator, not the source of truth. Purely visual: sealing doors to everyone below
 * OWNER is the resolver's lockdown rule. Registered with {@link LockdownHooks}, which isolates it.
 */
public final class VisualLockdownHook implements LockdownHooks.LockdownHook {

    public VisualLockdownHook() {
    }

    @Override
    public void onLockdown(MinecraftServer server, Installation installation, boolean active) {
        if (server == null || installation == null) {
            return;
        }
        DevicePos hubAt = installation.hubPos();
        if (hubAt != null) {
            ServerLevel level = loaded(server, hubAt);
            if (level != null && level.getBlockEntity(hubAt.pos()) instanceof SecurityHubBlockEntity hub
                    && !hub.isRemoved()) {
                hub.refreshVisual(level);
                (active ? SecuritySounds.LOCKDOWN_ON : SecuritySounds.LOCKDOWN_OFF).play(level, hubAt.pos());
            }
        }
        for (Map.Entry<DevicePos, Identifier> device : installation.devices().entrySet()) {
            DevicePos at = device.getKey();
            ServerLevel level = loaded(server, at);
            if (level == null) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(at.pos());
            if (be instanceof SecurityMonitorBlockEntity monitor) {
                monitor.refreshVisual(level);
            } else if (be instanceof SecurityDoorBlockEntity door) {
                door.refreshVisual(level);
            } else if (be instanceof SecureChestBlockEntity chest) {
                chest.refreshVisual(level);
            }
        }
    }

    private static @Nullable ServerLevel loaded(MinecraftServer server, @Nullable DevicePos at) {
        if (at == null) {
            return null;
        }
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, at.dimension()));
        return level != null && level.isLoaded(at.pos()) ? level : null;
    }
}
