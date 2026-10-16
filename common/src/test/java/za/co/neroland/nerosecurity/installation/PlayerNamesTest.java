package za.co.neroland.nerosecurity.installation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Adding a member by name resolves against the online list only: no other source is ever consulted, so no
 * network lookup can run on the server thread.
 */
class PlayerNamesTest {

    @Test
    void onlinePlayerResolvesAndOfflinePlayerDoesNot() {
        UUID alice = UUID.randomUUID();
        Map<String, UUID> online = Map.of("Alice", alice);
        assertEquals(Optional.of(alice), PlayerNames.lookupIn("Alice", n -> Optional.ofNullable(online.get(n))));
        assertEquals(Optional.of(alice), PlayerNames.lookupIn("  Alice ", n -> Optional.ofNullable(online.get(n))),
                "the typed name is trimmed");
        assertTrue(PlayerNames.lookupIn("Bob", n -> Optional.ofNullable(online.get(n))).isEmpty(),
                "an offline player is not resolved (the caller replies: must be online)");
    }

    @Test
    void invalidNamesNeverReachTheLookup() {
        List<String> asked = new ArrayList<>();
        assertTrue(PlayerNames.lookupIn(null, n -> { asked.add(n); return Optional.empty(); }).isEmpty());
        assertTrue(PlayerNames.lookupIn("   ", n -> { asked.add(n); return Optional.empty(); }).isEmpty());
        assertTrue(PlayerNames.lookupIn("x".repeat(PlayerNames.MAX_NAME + 1),
                n -> { asked.add(n); return Optional.empty(); }).isEmpty());
        assertTrue(asked.isEmpty(), "blank or over-long names are refused before any lookup");
    }

    @Test
    void aNullAnswerIsTreatedAsNotFound() {
        assertTrue(PlayerNames.lookupIn("Alice", n -> null).isEmpty());
    }
}
