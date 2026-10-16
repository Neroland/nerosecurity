package za.co.neroland.nerosecurity.energy;

import java.util.Map;

import net.minecraft.core.Direction;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.energy.NeroEnergyStorage;
import za.co.neroland.nerolandcore.sideconfig.Channel;
import za.co.neroland.nerolandcore.sideconfig.ChannelConfig;
import za.co.neroland.nerolandcore.sideconfig.RelativeFace;
import za.co.neroland.nerolandcore.sideconfig.SideConfig;
import za.co.neroland.nerolandcore.sideconfig.SideGating;
import za.co.neroland.nerolandcore.sideconfig.SideMode;
import za.co.neroland.nerolandcore.sideconfig.SidePreset;

/**
 * Fixed, insert-only energy routing for every powered NeroSecurity device (doors, laser grid nodes,
 * cameras), as pure rules (plain-JVM tested).
 *
 * <p>Core's side-config intent packet and configurator API can reach any machine within reach with no
 * ownership check, so a security device must offer nothing to configure: its side configuration
 * permits only {@link SideMode#INPUT} on every face, auto-eject and auto-input are always off, and
 * every external energy view, sided or not, is insert-only. Nobody can drain a device's buffer or
 * starve it by setting its faces to OUTPUT or DISABLED.
 *
 * <h2>How a powered device uses it</h2>
 * <ol>
 *   <li>Constructor: {@code installSideConfig(InputOnlyEnergy.sideConfig())} (never omit the preset:
 *       Core's builder otherwise defaults every face to disabled).</li>
 *   <li>Override {@code sideConfig()} to return {@code null}, so Core's intent packet and configurator
 *       find nothing to edit (Core's own ticker and persistence use the field directly).</li>
 *   <li>Call {@link #enforceInputOnly(SideConfig)} on the installed config in {@code loadAdditional}
 *       and once per server tick (mark changed when it returns true).</li>
 *   <li>Every loader energy capability returns {@link #externalView(NeroEnergyStorage, Direction)}
 *       over the buffer, never {@code sideConfig().energyView(side)}.</li>
 * </ol>
 */
public final class InputOnlyEnergy {

    private InputOnlyEnergy() {
    }

    /** The energy channel, every face INPUT, no other mode permitted. */
    public static SideConfig sideConfig() {
        SideConfig.Builder builder = SideConfig.builder().channel(Channel.ENERGY).defaultPreset(SidePreset.ALL_INPUT);
        for (SideMode mode : SideMode.VALUES) {
            builder.allow(Channel.ENERGY, mode, mode == SideMode.INPUT);
        }
        return builder.build();
    }

    /**
     * Puts every face of every channel back to INPUT and turns both auto toggles off. A paste or a
     * corrupt save can still carry a face the builder forbids, so devices call this on load and on
     * every server tick.
     *
     * @return whether anything had to change
     */
    public static boolean enforceInputOnly(SideConfig config) {
        if (config == null) {
            return false;
        }
        boolean changed = false;
        for (Map.Entry<Channel, ChannelConfig> entry : config.channels().entrySet()) {
            ChannelConfig channel = entry.getValue();
            for (RelativeFace face : RelativeFace.VALUES) {
                if (channel.mode(face) != SideMode.INPUT) {
                    channel.setMode(face, SideMode.INPUT);
                    changed = true;
                }
            }
            if (channel.autoEject()) {
                config.setAutoEject(entry.getKey(), false);
                changed = true;
            }
            if (channel.autoInput()) {
                config.setAutoInput(entry.getKey(), false);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * The view any outside block gets of a device's buffer through {@code side} (or unsided):
     * insert-only, whatever the side. {@code side} is deliberately ignored.
     */
    public static @Nullable NeroEnergyStorage externalView(@Nullable NeroEnergyStorage buffer, @Nullable Direction side) {
        return buffer == null ? null : SideGating.energy(buffer, () -> SideMode.INPUT);
    }
}
