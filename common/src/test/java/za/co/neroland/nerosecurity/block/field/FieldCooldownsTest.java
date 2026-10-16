package za.co.neroland.nerosecurity.block.field;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.installation.DevicePos;

/** An overload cooldown survives breaking and re-placing the emitter; the map is bounded and pruned. Pure. */
class FieldCooldownsTest {

    private static final Identifier OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld");

    private static DevicePos at(int x) {
        return new DevicePos(OVERWORLD, new BlockPos(x, 64, 0));
    }

    @Test
    void replacedEmitterAdoptsTheRunningCooldown() {
        FieldCooldowns cooldowns = new FieldCooldowns();
        cooldowns.record(at(1), 1200L, 0L);
        assertEquals(1200L, cooldowns.adopt(at(1), 40L), "a new emitter at the same position waits it out");
        assertEquals(0L, cooldowns.adopt(at(2), 40L), "another position has no cooldown");
        assertEquals(1200L, cooldowns.adopt(at(1), 41L), "adopting does not consume it: break and re-place again");
        cooldowns.record(at(1), 600L, 50L);
        assertEquals(1200L, cooldowns.adopt(at(1), 60L), "a shorter cooldown never shortens a running one");
        assertEquals(0L, cooldowns.adopt(at(1), 1200L), "over");
        assertEquals(0, cooldowns.size(), "an expired entry is dropped when read");
        cooldowns.record(at(3), 10L, 10L);
        assertEquals(0, cooldowns.size(), "nothing to remember for a cooldown already over");
    }

    @Test
    void boundedAndPruned() {
        FieldCooldowns cooldowns = new FieldCooldowns();
        for (int i = 0; i < FieldCooldowns.MAX_ENTRIES; i++) {
            cooldowns.record(at(i), 1000L + i, 0L);
        }
        assertEquals(FieldCooldowns.MAX_ENTRIES, cooldowns.size());
        cooldowns.record(at(-1), 5000L, 0L);
        assertEquals(FieldCooldowns.MAX_ENTRIES, cooldowns.size(), "full: the soonest to expire makes room");
        assertEquals(0L, cooldowns.adopt(at(0), 1L), "the soonest entry was evicted");
        assertEquals(5000L, cooldowns.adopt(at(-1), 1L));
        cooldowns.prune(1500L);
        assertEquals(FieldCooldowns.MAX_ENTRIES - 500, cooldowns.size());
        cooldowns.clear();
        assertEquals(0, cooldowns.size());
    }
}
