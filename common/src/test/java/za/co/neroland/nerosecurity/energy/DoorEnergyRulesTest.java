package za.co.neroland.nerosecurity.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.Direction;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.energy.EnergyBuffer;
import za.co.neroland.nerolandcore.energy.NeroEnergyStorage;
import za.co.neroland.nerolandcore.sideconfig.Channel;
import za.co.neroland.nerolandcore.sideconfig.ChannelConfig;
import za.co.neroland.nerolandcore.sideconfig.RelativeFace;
import za.co.neroland.nerolandcore.sideconfig.SideConfig;
import za.co.neroland.nerolandcore.sideconfig.SideMode;

/** A security door's energy can only ever go in: side config, auto toggles and every external view. Plain JVM. */
class DoorEnergyRulesTest {

    private static void assertAllInput(SideConfig config) {
        for (ChannelConfig channel : config.channels().values()) {
            for (RelativeFace face : RelativeFace.VALUES) {
                assertEquals(SideMode.INPUT, channel.mode(face), face + " must stay INPUT");
            }
            assertFalse(channel.autoEject(), "auto-eject must be off");
            assertFalse(channel.autoInput(), "auto-input must be off");
        }
    }

    @Test
    void sideConfigPermitsOnlyInput() {
        SideConfig config = DoorEnergyRules.sideConfig();
        assertEquals(1, config.channels().size(), "energy is the only channel");
        ChannelConfig energy = config.get(Channel.ENERGY);
        for (SideMode mode : SideMode.VALUES) {
            assertEquals(mode == SideMode.INPUT, energy.isAllowed(mode), mode + " permitted?");
        }
        assertAllInput(config);
    }

    @Test
    void setModeAndCycleCannotLeaveInput() {
        SideConfig config = DoorEnergyRules.sideConfig();
        for (RelativeFace face : RelativeFace.VALUES) {
            for (SideMode mode : SideMode.VALUES) {
                assertFalse(config.setMode(Channel.ENERGY, face, mode), mode + " on " + face + " must be refused");
            }
            for (int i = 0; i < SideMode.VALUES.length + 1; i++) {
                config.cycle(Channel.ENERGY, face);
            }
        }
        assertAllInput(config);
        assertFalse(DoorEnergyRules.enforceInputOnly(config), "nothing to undo");
    }

    @Test
    void pastedOrLoadedStateIsForcedBackToInput() {
        for (SideMode pasted : SideMode.VALUES) {
            SideConfig config = DoorEnergyRules.sideConfig();
            int packed = (1 << 24) | (1 << 25); // auto-eject and auto-input on
            for (RelativeFace face : RelativeFace.VALUES) {
                packed |= pasted.ordinal() << (face.index() * 3);
            }
            Map<Channel, Integer> snapshot = new EnumMap<>(Channel.class);
            snapshot.put(Channel.ENERGY, packed);
            config.applyPacked(snapshot);
            for (RelativeFace face : RelativeFace.VALUES) {
                SideMode mode = config.mode(Channel.ENERGY, face);
                assertFalse(mode.canExtract(), "a paste never yields an extracting face (" + pasted + ")");
            }
            assertTrue(DoorEnergyRules.enforceInputOnly(config), "the toggles at least had to be undone");
            assertAllInput(config);
            assertFalse(DoorEnergyRules.enforceInputOnly(config));
        }
    }

    @Test
    void everyExternalViewIsInsertOnly() {
        EnergyBuffer buffer = new EnergyBuffer(10_000, 1_000, 1_000, () -> { });
        buffer.setRaw(5_000);
        List<Direction> sides = new ArrayList<>(List.of(Direction.values()));
        sides.add(null);
        for (Direction side : sides) {
            NeroEnergyStorage view = DoorEnergyRules.externalView(buffer, side);
            assertEquals(0L, view.extract(1_000, true), "simulated extract through " + side);
            assertEquals(0L, view.extract(1_000, false), "extract through " + side);
            assertEquals(5_000L, view.getAmount());
            assertTrue(view.insert(10, true) > 0, "a door still takes energy through " + side);
        }
        assertEquals(5_000, buffer.getRaw(), "nothing left the buffer");
    }
}
