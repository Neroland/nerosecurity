package za.co.neroland.nerosecurity.sensor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.access.AccessResolver;
import za.co.neroland.nerosecurity.access.AdminCheck;
import za.co.neroland.nerosecurity.access.MembershipSource;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.sensor.SensorPermission.Classification;
import za.co.neroland.nerosecurity.sensor.SensorPermission.Sensed;

/** Sensors detect; the resolver authorises, by UUID. Plain JVM over {@link Sensed} descriptions. */
class SensorPermissionTest {

    private static final DevicePos HUB = new DevicePos(Identifier.fromNamespaceAndPath("minecraft", "overworld"),
            new BlockPos(0, 64, 0));
    private static final DevicePos LASER = new DevicePos(HUB.dimension(), new BlockPos(4, 64, 0));

    private final UUID owner = UUID.randomUUID();
    private final UUID officer = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private final AccessResolver resolver =
            new AccessResolver(() -> List.of(MembershipSource.INSTANCE), AdminCheck.NONE, () -> true);
    private Installation installation;

    @BeforeEach
    void setUp() {
        SecurityInstallations store = new SecurityInstallations();
        installation = store.create(owner, "Lab", HUB, 0L, 8).installation();
        store.setMember(installation.id(), officer, AccessLevel.OFFICER, 64);
        store.setMember(installation.id(), member, AccessLevel.MEMBER, 64);
    }

    private Classification classify(Sensed sensed, SensorSettings settings) {
        return SensorPermission.classify(resolver, null, installation, LASER, sensed, settings);
    }

    /** 10. */
    @Test
    void membersNeverTripAtDefaultLevel() {
        assertEquals(Classification.PERMITTED, classify(Sensed.player(member), SensorSettings.DEFAULT));
        assertEquals(Classification.PERMITTED, classify(Sensed.player(owner), SensorSettings.DEFAULT));
        assertEquals(Classification.INTRUDER, classify(Sensed.player(stranger), SensorSettings.DEFAULT));
        SensorSettings officersOnly = SensorSettings.DEFAULT.withMinLevel(AccessLevel.OFFICER);
        assertEquals(Classification.INTRUDER, classify(Sensed.player(member), officersOnly));
        assertEquals(Classification.PERMITTED, classify(Sensed.player(officer), officersOnly));
        // A spectator (or anything not alive / not living) is described as IGNORED.
        assertEquals(Classification.IGNORE, classify(Sensed.IGNORED, SensorSettings.DEFAULT));
        assertEquals(Classification.IGNORE, classify(new Sensed(SensorPermission.Kind.PLAYER, null), SensorSettings.DEFAULT),
                "a player with no UUID is never judged");
    }

    /** 11. */
    @Test
    void entityModeMatrix() {
        for (SensorEntityMode mode : SensorEntityMode.values()) {
            SensorSettings s = SensorSettings.DEFAULT.withEntityMode(mode);
            assertEquals(mode == SensorEntityMode.PLAYERS_ONLY ? Classification.IGNORE : Classification.INTRUDER,
                    classify(Sensed.HOSTILE, s), "hostile under " + mode);
            assertEquals(mode == SensorEntityMode.ALL_LIVING ? Classification.INTRUDER : Classification.IGNORE,
                    classify(Sensed.PASSIVE, s), "passive under " + mode);
            assertEquals(Classification.IGNORE, classify(Sensed.IGNORED, s), "an item entity under " + mode);
            assertEquals(Classification.INTRUDER, classify(Sensed.player(stranger), s), "players are judged in every mode");
            assertEquals(Classification.PERMITTED, classify(Sensed.player(member), s));
        }
    }

    /** 12. */
    @Test
    void ownedAnimalResolvesAsOwner() {
        assertEquals(Classification.PERMITTED, classify(Sensed.ownedBy(member), SensorSettings.DEFAULT));
        assertEquals(Classification.INTRUDER, classify(Sensed.ownedBy(stranger), SensorSettings.DEFAULT));
        SensorSettings playersOnly = SensorSettings.DEFAULT.withEntityMode(SensorEntityMode.PLAYERS_ONLY);
        assertEquals(Classification.INTRUDER, classify(Sensed.ownedBy(stranger), playersOnly),
                "an owned animal is judged as its owner whatever the mode");
        for (Sensed sensed : List.of(Sensed.player(owner), Sensed.player(stranger), Sensed.ownedBy(stranger),
                Sensed.HOSTILE, Sensed.PASSIVE)) {
            assertEquals(Classification.IGNORE, SensorPermission.classify(resolver, null, null, LASER, sensed,
                    SensorSettings.DEFAULT.withEntityMode(SensorEntityMode.ALL_LIVING)), "an unlinked sensor senses nothing");
        }
    }

    /**
     * C6-2: a pet is judged by its owner, but the owner is never the logged actor (no notice, maybe offline, not
     * the one sensed): the row is published with no actor, like a mob's. A player is logged as themselves.
     */
    @Test
    void anOwnedAnimalIsNeverLoggedAsItsOwner() {
        assertEquals(stranger, SensorPermission.loggedActor(Sensed.player(stranger)), "a player is logged as themselves");
        assertEquals(null, SensorPermission.loggedActor(Sensed.ownedBy(stranger)), "the pet's owner is not logged");
        assertEquals(Classification.INTRUDER, classify(Sensed.ownedBy(stranger), SensorSettings.DEFAULT),
                "the stranger's pet still trips the sensor (the event fires)");
        assertEquals(null, SensorPermission.loggedActor(Sensed.HOSTILE));
        assertEquals(null, SensorPermission.loggedActor(Sensed.PASSIVE));
        assertEquals(null, SensorPermission.loggedActor(Sensed.IGNORED));
        assertEquals(null, SensorPermission.loggedActor(null));
    }

    @Test
    void sensedNormalisesSubjects() {
        assertEquals(null, new Sensed(SensorPermission.Kind.HOSTILE, stranger).subject(), "mobs carry no subject");
        assertEquals(stranger, Sensed.ownedBy(stranger).subject());
    }

    @Test
    void settingsCodecClampAndRules() {
        assertEquals(90, SensorSettings.DEFAULT.fovDegrees());
        assertEquals(60, SensorSettings.clampFov(10));
        assertEquals(120, SensorSettings.clampFov(400));
        assertEquals(90, SensorSettings.clampFov(95));
        assertEquals(SensorEntityMode.PLAYERS_AND_HOSTILES, SensorEntityMode.byName("junk"));
        SensorSettings ownerOnly = SensorSettings.DEFAULT.withMinLevel(AccessLevel.OWNER);
        assertFalse(SensorSettings.mayApply(false, true, SensorSettings.DEFAULT, SensorSettings.DEFAULT),
                "an unlinked sensor refuses every change, even an owner's");
        assertTrue(SensorSettings.mayApply(true, false, SensorSettings.DEFAULT,
                SensorSettings.DEFAULT.withEntityMode(SensorEntityMode.ALL_LIVING)));
        assertFalse(SensorSettings.mayApply(true, false, SensorSettings.DEFAULT, ownerOnly), "only an owner sets OWNER");
        assertFalse(SensorSettings.mayApply(true, false, ownerOnly, ownerOnly.withFov(60)),
                "only an owner changes anything on an OWNER sensor");
        assertTrue(SensorSettings.mayApply(true, true, ownerOnly, SensorSettings.DEFAULT));
    }
}
