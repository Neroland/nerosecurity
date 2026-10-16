package za.co.neroland.nerosecurity.block.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** FIELD_BREACH_ATTEMPT is rate-limited per attacker and bounded. Pure. */
class BreachCooldownsTest {

    @Test
    void oneEventPerAttackerPerCooldownAndBounded() {
        BreachCooldowns cooldowns = new BreachCooldowns();
        UUID a = new UUID(1L, 1L);
        UUID b = new UUID(2L, 2L);
        assertTrue(cooldowns.tryFire(a, 100L, 200));
        assertFalse(cooldowns.tryFire(a, 299L, 200), "still cooling down");
        assertTrue(cooldowns.tryFire(b, 150L, 200), "another attacker has its own slot");
        assertTrue(cooldowns.tryFire(a, 300L, 200), "cooldown over");
        assertTrue(cooldowns.tryFire(null, 300L, 200), "explosions share one slot");
        assertFalse(cooldowns.tryFire(null, 310L, 200));

        BreachCooldowns crowd = new BreachCooldowns();
        for (int i = 0; i < BreachCooldowns.MAX_ENTRIES; i++) {
            assertTrue(crowd.tryFire(new UUID(10L, i), 0L, 200));
        }
        assertFalse(crowd.tryFire(new UUID(11L, 0L), 10L, 200), "full: the event is skipped, memory stays bounded");
        assertEquals(BreachCooldowns.MAX_ENTRIES, crowd.size());
        assertTrue(crowd.tryFire(new UUID(11L, 0L), 400L, 200), "expired entries are pruned to make room");
        crowd.prune(400L, 200);
        assertEquals(1, crowd.size());
    }
}
