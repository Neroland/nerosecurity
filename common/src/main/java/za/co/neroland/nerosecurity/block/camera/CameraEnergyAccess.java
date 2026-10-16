package za.co.neroland.nerosecurity.block.camera;

import net.minecraft.core.Direction;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.energy.NeroEnergyStorage;

import za.co.neroland.nerosecurity.energy.InputOnlyEnergy;

/**
 * The single place each loader's energy capability asks for a camera's energy (step 4 of the
 * {@link InputOnlyEnergy} recipe). Every query, sided or not, gets an insert-only view; the camera's
 * side configuration is never consulted, so nothing can drain or starve a camera through the capability.
 */
public final class CameraEnergyAccess {

    private CameraEnergyAccess() {
    }

    /** The external energy view of {@code camera}: insert-only for every side and for an unsided query. */
    public static @Nullable NeroEnergyStorage of(@Nullable SecurityCameraBlockEntity camera, @Nullable Direction side) {
        if (camera == null || camera.isRemoved()) {
            return null;
        }
        return InputOnlyEnergy.externalView(camera.getEnergy(), side);
    }
}
