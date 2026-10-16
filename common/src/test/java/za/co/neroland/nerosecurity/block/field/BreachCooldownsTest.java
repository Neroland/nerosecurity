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

    /** Melee load is spaced at least MELEE_SPACING_TICKS per attacker, whatever the packet rate. */
    @Test
    void meleeLoadIsSpacedPerAttacker() {
        BreachCooldowns cooldowns = new BreachCooldowns();
        UUID a = new UUID(1L, 1L);
        UUID b = new UUID(2L, 2L);
        int spacing = BreachCooldowns.MELEE_SPACING_TICKS;
        assertTrue(spacing >= 4);
        assertTrue(cooldowns.tryMelee(a, 100L));
        for (int i = 0; i < 50; i++) {
            assertFalse(cooldowns.tryMelee(a, 100L), "a packet burst in one tick counts once");
        }
        for (long t = 101L; t < 100L + spacing; t++) {
            assertFalse(cooldowns.tryMelee(a, t), "still within the spacing at " + t);
        }
        assertTrue(cooldowns.tryMelee(b, 101L), "another attacker is spaced on its own");
        assertTrue(cooldowns.tryMelee(a, 100L + spacing), "spacing over");
        assertTrue(cooldowns.tryMelee(null, 100L), "no player: not spaced");

        // One packet every tick for 20 ticks (one second): at most 20 / spacing hits count.
        BreachCooldowns spam = new BreachCooldowns();
        int counted = 0;
        for (long t = 0L; t < 20L; t++) {
            for (int burst = 0; burst < 10; burst++) {
                if (spam.tryMelee(a, t)) {
                    counted++;
                }
            }
        }
        assertEquals(20 / spacing, counted);

        // Bounded: a full map of fresh attackers refuses a new one until the spacing passes.
        BreachCooldowns crowd = new BreachCooldowns();
        for (int i = 0; i < BreachCooldowns.MAX_ENTRIES; i++) {
            assertTrue(crowd.tryMelee(new UUID(10L, i), 0L));
        }
        assertFalse(crowd.tryMelee(new UUID(11L, 0L), 1L));
        assertEquals(BreachCooldowns.MAX_ENTRIES, crowd.meleeSize());
        assertTrue(crowd.tryMelee(new UUID(11L, 0L), spacing), "expired entries are pruned to make room");
        crowd.prune(spacing * 2L, 200);
        assertEquals(0, crowd.meleeSize());
        crowd.clear();
        assertEquals(0, crowd.meleeSize());
    }
}
