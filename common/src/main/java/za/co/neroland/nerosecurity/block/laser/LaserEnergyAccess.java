package za.co.neroland.nerosecurity.block.laser;

import net.minecraft.core.Direction;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.energy.NeroEnergyStorage;

import za.co.neroland.nerosecurity.energy.InputOnlyEnergy;

/**
 * The single place each loader's energy capability asks for a laser grid node's energy. Every query,
 * sided or not, gets an insert-only view ({@link InputOnlyEnergy#externalView}); the node's side
 * configuration is never consulted, so nothing can drain or block the buffer through the capability.
 */
public final class LaserEnergyAccess {

    private LaserEnergyAccess() {
    }

    /** The external energy view of {@code node}: insert-only for every side and for an unsided query. */
    public static @Nullable NeroEnergyStorage of(@Nullable LaserGridNodeBlockEntity node, @Nullable Direction side) {
        if (node == null || node.isRemoved()) {
            return null;
        }
        return InputOnlyEnergy.externalView(node.getEnergy(), side);
    }
}
