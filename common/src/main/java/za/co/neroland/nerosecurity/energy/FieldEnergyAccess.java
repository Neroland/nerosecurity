package za.co.neroland.nerosecurity.energy;

import net.minecraft.core.Direction;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.energy.NeroEnergyStorage;

import za.co.neroland.nerosecurity.block.field.ForceFieldEmitterBlockEntity;

/**
 * The single place each loader's energy capability asks for a force field emitter's energy. Every query, sided or
 * not, gets an insert-only view ({@link InputOnlyEnergy#externalView}); the emitter's side configuration is never
 * consulted, so nothing can drain or block the buffer through the capability (a raider cannot pull the field down
 * with a cable).
 */
public final class FieldEnergyAccess {

    private FieldEnergyAccess() {
    }

    /** The external energy view of {@code emitter}: insert-only for every side and for an unsided query. */
    public static @Nullable NeroEnergyStorage of(@Nullable ForceFieldEmitterBlockEntity emitter, @Nullable Direction side) {
        if (emitter == null || emitter.isRemoved()) {
            return null;
        }
        return InputOnlyEnergy.externalView(emitter.getEnergy(), side);
    }
}
