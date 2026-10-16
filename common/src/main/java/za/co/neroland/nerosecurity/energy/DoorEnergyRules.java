package za.co.neroland.nerosecurity.energy;

import net.minecraft.core.Direction;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.energy.NeroEnergyStorage;
import za.co.neroland.nerolandcore.sideconfig.SideConfig;

/**
 * The fixed energy routing of a security door: exactly {@link InputOnlyEnergy}, which every powered
 * NeroSecurity device shares. Kept as the door's named entry point.
 *
 * <p>A door only ever takes energy in. Its side configuration permits nothing but INPUT on any face,
 * auto-eject and auto-input are always off, and every external energy view, sided or not, is
 * insert-only. Nothing a player does to the door's side configuration (a configurator tool, a
 * side-config packet, a paste) can make a door give energy away or refuse it.
 */
public final class DoorEnergyRules {

    private DoorEnergyRules() {
    }

    /** {@link InputOnlyEnergy#sideConfig()}. */
    public static SideConfig sideConfig() {
        return InputOnlyEnergy.sideConfig();
    }

    /** {@link InputOnlyEnergy#enforceInputOnly(SideConfig)}. */
    public static boolean enforceInputOnly(SideConfig config) {
        return InputOnlyEnergy.enforceInputOnly(config);
    }

    /** {@link InputOnlyEnergy#externalView(NeroEnergyStorage, Direction)}. */
    public static NeroEnergyStorage externalView(NeroEnergyStorage buffer, @Nullable Direction side) {
        return InputOnlyEnergy.externalView(buffer, side);
    }
}
