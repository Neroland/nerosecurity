package za.co.neroland.nerosecurity.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.energy.NeroEnergyStorage;

import za.co.neroland.nerosecurity.block.door.AbstractSecurityDoorBlock;
import za.co.neroland.nerosecurity.block.door.SecurityDoorBlockEntity;

/**
 * The single place each loader's energy capability asks for a door's energy. Any part of a door
 * resolves to its controller's buffer. A door only ever takes energy from outside: every query, sided
 * or not, gets an insert-only view ({@link InputOnlyEnergy#externalView}), and the door's side
 * configuration is never consulted, so nothing can drain or block a door's buffer through the
 * capability, whatever its faces were set to.
 */
public final class DoorEnergyAccess {

    private DoorEnergyAccess() {
    }

    /**
     * The insert-only energy view of the door that the block at {@code pos} is part of, or null when the
     * block is not a door part or the controller is missing. This is what the loader capability
     * registrations call, for any part position.
     */
    public static @Nullable NeroEnergyStorage at(Level level, BlockPos pos, @Nullable Direction side) {
        if (level == null || pos == null) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        SecurityDoorBlockEntity door = AbstractSecurityDoorBlock.controllerEntity(level, pos, state);
        return door == null ? null : of(door, side);
    }

    /** The external energy view of {@code door}: insert-only for every side and for an unsided query. */
    public static @Nullable NeroEnergyStorage of(SecurityDoorBlockEntity door, @Nullable Direction side) {
        if (door == null || door.isRemoved()) {
            return null;
        }
        return InputOnlyEnergy.externalView(door.getEnergy(), side);
    }
}
