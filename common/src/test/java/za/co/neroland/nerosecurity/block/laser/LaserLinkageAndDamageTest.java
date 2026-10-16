package za.co.neroland.nerosecurity.block.laser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.access.AccessResolver;
import za.co.neroland.nerosecurity.access.AdminCheck;
import za.co.neroland.nerosecurity.access.MembershipSource;
import za.co.neroland.nerosecurity.block.laser.LaserPairing.Cell;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.Lockdown;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.sensor.SensorPermission;
import za.co.neroland.nerosecurity.sensor.SensorPermission.Classification;
import za.co.neroland.nerosecurity.sensor.SensorPermission.Sensed;
import za.co.neroland.nerosecurity.sensor.SensorSettings;

/** Laser pairing is per installation, and lasers never hurt owners or members, even in lockdown. Plain JVM. */
class LaserLinkageAndDamageTest {

    private static final Identifier OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld");
    private static final DevicePos HUB = new DevicePos(OVERWORLD, new BlockPos(0, 64, 0));
    private static final DevicePos NODE = new DevicePos(OVERWORLD, new BlockPos(4, 64, 0));

    @Test
    void aStrangersNodeNeverExtendsTheBeam() {
        Direction walking = Direction.EAST;
        Direction facingBack = walking.getOpposite();
        assertEquals(Cell.FACING_NODE, LaserPairing.cell(true, facingBack, walking, false, true));
        assertEquals(Cell.BLOCKED, LaserPairing.cell(true, facingBack, walking, false, false),
                "a facing node of another installation (or none) blocks");
        assertEquals(Cell.BLOCKED, LaserPairing.cell(true, walking, walking, false, false));
        assertEquals(Cell.OPEN, LaserPairing.cell(false, null, walking, true, false), "air is air whoever owns it");

        // Open air, then a facing node at distance 6: it pairs only when it belongs to the same installation.
        int range = 8;
        assertEquals(0, LaserPairing.partnerDistance(range,
                d -> d == 6 ? LaserPairing.cell(true, facingBack, walking, false, false)
                        : LaserPairing.cell(false, null, walking, true, true)),
                "the stranger's node is not a partner");
        assertEquals(6, LaserPairing.partnerDistance(range,
                d -> d == 6 ? LaserPairing.cell(true, facingBack, walking, false, true)
                        : LaserPairing.cell(false, null, walking, true, true)),
                "a node of the same installation pairs");
    }

    @Test
    void lasersNeverHurtOwnersOrMembersEvenInLockdown() {
        UUID owner = UUID.randomUUID();
        UUID officer = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        SecurityInstallations store = new SecurityInstallations();
        Installation installation = store.create(owner, "Lab", HUB, 0L, 8).installation();
        store.setMember(installation.id(), officer, AccessLevel.OFFICER, 64);
        store.setMember(installation.id(), member, AccessLevel.MEMBER, 64);
        store.setLockdown(installation.id(), Lockdown.manual(1L, true));
        Installation locked = store.byId(installation.id()).orElseThrow();
        assertTrue(locked.lockdown().active());

        AccessResolver resolver = new AccessResolver(() -> List.of(MembershipSource.INSTANCE), AdminCheck.NONE,
                () -> true);
        // During the lockdown members and officers classify as intruders: detected and logged...
        for (UUID subject : List.of(member, officer)) {
            assertEquals(Classification.INTRUDER, SensorPermission.classify(resolver, null, locked, NODE,
                    Sensed.player(subject), SensorSettings.DEFAULT), "members are logged during a lockdown");
            // ...but never hurt (nor their pets, which are judged as their owner).
            assertFalse(LaserDamageRule.mayHarm(locked, subject));
        }
        assertEquals(Classification.PERMITTED, SensorPermission.classify(resolver, null, locked, NODE,
                Sensed.player(owner), SensorSettings.DEFAULT), "owners are never locked out");
        assertFalse(LaserDamageRule.mayHarm(locked, owner));
        assertTrue(LaserDamageRule.mayHarm(locked, stranger), "a stranger may be hurt");
        assertTrue(LaserDamageRule.mayHarm(locked, null), "an unowned mob may be hurt");
        assertFalse(LaserDamageRule.mayHarm(null, stranger), "no installation: never hurt a player on a guess");
    }
}
