package za.co.neroland.nerosecurity.installation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.protection.ClaimChecks;

/** NeroSecurity's Core claim check covers hubs and linked devices only. Plain JVM. */
class SecurityClaimCheckTest {

    private static final Identifier OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld");
    private static final Identifier NETHER = Identifier.fromNamespaceAndPath("minecraft", "the_nether");
    private static final Identifier DOOR = Identifier.fromNamespaceAndPath("nerosecurity", "test_door");

    private static DevicePos at(Identifier dimension, int x, int y, int z) {
        return new DevicePos(dimension, new BlockPos(x, y, z));
    }

    @AfterEach
    void cleanup() {
        ClaimChecks.clearForTests();
    }

    @Test
    void coversTheHubAndLinkedDevicesButNotTheLandAroundThem() {
        SecurityInstallations store = new SecurityInstallations();
        DevicePos hub = at(OVERWORLD, 0, 64, 0);
        Installation installation = store.create(UUID.randomUUID(), "Base", hub, 0L, 8).installation();
        DevicePos door = at(OVERWORLD, 3, 64, 0);
        store.link(installation.id(), door, DOOR);

        assertTrue(SecurityClaimCheck.covers(store, hub), "the hub is protected");
        assertTrue(SecurityClaimCheck.covers(store, door), "a linked device is protected");
        assertFalse(SecurityClaimCheck.covers(store, at(OVERWORLD, 4, 64, 0)), "the block next to it is not");
        assertFalse(SecurityClaimCheck.covers(store, at(NETHER, 3, 64, 0)), "same coordinates elsewhere are not");
    }

    @Test
    void registeringTwiceIsOneCheck() {
        SecurityClaimCheck.register();
        SecurityClaimCheck.register();
        assertTrue(ClaimChecks.active());
    }
}
