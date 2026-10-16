package za.co.neroland.nerosecurity.block.laser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** One trip per entity per cooldown; the map stays bounded. Plain JVM. */
class LaserTripCooldownsTest {

    @Test
    void oneTripPerEntityPerCooldown() {
        LaserTripCooldowns cooldowns = new LaserTripCooldowns();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertTrue(cooldowns.tryTrip(a, 1000L, 100));
        assertFalse(cooldowns.tryTrip(a, 1002L, 100));
        assertFalse(cooldowns.tryTrip(a, 1099L, 100));
        assertTrue(cooldowns.tryTrip(b, 1002L, 100), "cooldowns are per entity");
        assertTrue(cooldowns.tryTrip(a, 1100L, 100));
        assertFalse(cooldowns.tryTrip(null, 5000L, 100));
    }

    @Test
    void mapStaysBounded() {
        LaserTripCooldowns cooldowns = new LaserTripCooldowns();
        // Expired entries are pruned once the map passes the prune size.
        for (int i = 0; i < LaserTripCooldowns.PRUNE_ABOVE; i++) {
            cooldowns.tryTrip(UUID.randomUUID(), 0L, 100);
        }
        assertEquals(LaserTripCooldowns.PRUNE_ABOVE, cooldowns.size());
        cooldowns.tryTrip(UUID.randomUUID(), 500L, 100);
        assertEquals(1, cooldowns.size());
        // A flood of distinct entities inside one cooldown never exceeds the hard cap.
        for (int i = 0; i < LaserTripCooldowns.HARD_CAP * 3; i++) {
            assertTrue(cooldowns.tryTrip(UUID.randomUUID(), 600L + i, 1000));
        }
        assertEquals(LaserTripCooldowns.HARD_CAP, cooldowns.size());
        cooldowns.clear();
        assertEquals(0, cooldowns.size());
    }
}
