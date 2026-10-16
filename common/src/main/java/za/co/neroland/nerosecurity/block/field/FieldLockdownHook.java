package za.co.neroland.nerosecurity.block.field;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.lockdown.LockdownHooks;
import za.co.neroland.nerosecurity.registry.FieldRegistrations;

/**
 * Raises (or lowers) every loaded emitter of an installation the moment its lockdown begins (or ends): effective
 * strength to {@code maxFieldStrength} at no extra power, the meter rescaled to the new capacity (never reset, so
 * cycling a lockdown cannot drain it), the lockdown look painted, nearby players resynced. Unloaded emitters are never loaded for this: they read lockdown from the store on their first tick, so
 * the hook is an accelerator, not the source of truth. Sealing the field to everyone below OWNER is the resolver's
 * lockdown rule, not this hook. Registered with {@link LockdownHooks}, which isolates it.
 */
public final class FieldLockdownHook implements LockdownHooks.LockdownHook {

    public FieldLockdownHook() {
    }

    @Override
    public void onLockdown(MinecraftServer server, Installation installation, boolean active) {
        if (server == null || installation == null) {
            return;
        }
        Identifier emitterId = FieldRegistrations.EMITTER_ID;
        List<DevicePos> emitters = new ArrayList<>();
        for (Map.Entry<DevicePos, Identifier> device : installation.devices().entrySet()) {
            if (emitterId.equals(device.getValue())) {
                emitters.add(device.getKey());
            }
        }
        for (DevicePos at : emitters) {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, at.dimension()));
            if (level == null || !level.isLoaded(at.pos())) {
                continue;
            }
            if (level.getBlockEntity(at.pos()) instanceof ForceFieldEmitterBlockEntity emitter && !emitter.isRemoved()) {
                emitter.onLockdownChanged(level, active);
            }
        }
    }
}
