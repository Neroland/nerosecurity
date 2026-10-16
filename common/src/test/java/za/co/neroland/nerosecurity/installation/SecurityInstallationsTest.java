package za.co.neroland.nerosecurity.installation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.access.AccessLevel;

/** Structural invariants of the installation store, its codec and its lookups. Plain JVM. */
class SecurityInstallationsTest {

    private static final Identifier OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld");
    private static final Identifier NETHER = Identifier.fromNamespaceAndPath("minecraft", "the_nether");
    private static final Identifier DOOR = Identifier.fromNamespaceAndPath("nerosecurity", "test_door");

    private static DevicePos at(int x, int y, int z) {
        return new DevicePos(OVERWORLD, new BlockPos(x, y, z));
    }

    private static Installation create(SecurityInstallations store, UUID owner, String name, DevicePos hub) {
        SecurityInstallations.CreateResult result = store.create(owner, name, hub, 0L, 8);
        assertEquals(SecurityInstallations.CreateStatus.OK, result.status());
        return result.installation();
    }

    @Test
    void deviceBelongsToAtMostOneInstallation() {
        SecurityInstallations store = new SecurityInstallations();
        Installation a = create(store, UUID.randomUUID(), "A", at(0, 64, 0));
        Installation b = create(store, UUID.randomUUID(), "B", at(100, 64, 0));
        DevicePos p = at(5, 64, 5);
        assertEquals(SecurityInstallations.LinkResult.OK, store.link(a.id(), p, DOOR));
        assertEquals(SecurityInstallations.LinkResult.LINKED_ELSEWHERE, store.link(b.id(), p, DOOR));
        assertEquals(SecurityInstallations.LinkResult.ALREADY_LINKED_HERE, store.link(a.id(), p, DOOR));
        assertEquals(a.id(), store.atDevice(p).orElseThrow().id());
        assertEquals(0, b.deviceCount());
        assertTrue(store.unlink(a.id(), p));
        assertTrue(store.atDevice(p).isEmpty());
        assertEquals(SecurityInstallations.LinkResult.OK, store.link(b.id(), p, DOOR), "free again after unlink");
    }

    @Test
    void deviceCapPerInstallation() {
        SecurityInstallations store = new SecurityInstallations();
        Installation a = create(store, UUID.randomUUID(), "A", at(0, 64, 0));
        int max = 3;
        for (int i = 1; i <= max; i++) {
            assertEquals(SecurityInstallations.LinkResult.OK, store.link(a.id(), at(i, 64, 1), DOOR, max));
        }
        assertEquals(SecurityInstallations.LinkResult.LIMIT_REACHED, store.link(a.id(), at(9, 64, 1), DOOR, max));
        assertEquals(max, a.deviceCount());
        assertTrue(store.atDevice(at(9, 64, 1)).isEmpty(), "a refused link leaves no index row");
        assertEquals(SecurityInstallations.LinkResult.ALREADY_LINKED_HERE, store.link(a.id(), at(1, 64, 1), DOOR, max),
                "re-linking an existing device is not counted against the cap");
        assertTrue(store.unlink(a.id(), at(1, 64, 1)));
        assertEquals(SecurityInstallations.LinkResult.OK, store.link(a.id(), at(9, 64, 1), DOOR, max));
    }

    @Test
    void hubPositionCannotBeLinkedAsDevice() {
        SecurityInstallations store = new SecurityInstallations();
        DevicePos hubA = at(0, 64, 0);
        Installation a = create(store, UUID.randomUUID(), "A", hubA);
        Installation b = create(store, UUID.randomUUID(), "B", at(50, 64, 0));
        assertEquals(SecurityInstallations.LinkResult.IS_HUB, store.link(a.id(), hubA, DOOR));
        assertEquals(SecurityInstallations.LinkResult.IS_HUB, store.link(b.id(), hubA, DOOR));
        assertEquals(SecurityInstallations.CreateStatus.POSITION_TAKEN,
                store.create(UUID.randomUUID(), "C", hubA, 0L, 8).status());
        assertTrue(store.atHub(hubA).isPresent());
        assertTrue(store.atDevice(hubA).isEmpty(), "a hub is never a device");
    }

    @Test
    void perPlayerInstallationLimit() {
        SecurityInstallations store = new SecurityInstallations();
        UUID owner = UUID.randomUUID();
        int max = 3;
        for (int i = 0; i < max; i++) {
            assertEquals(SecurityInstallations.CreateStatus.OK, store.create(owner, "", at(i, 64, 0), 0L, max).status());
        }
        assertEquals(SecurityInstallations.CreateStatus.LIMIT_REACHED,
                store.create(owner, "", at(99, 64, 0), 0L, max).status());
        assertEquals(max, store.countOwnedBy(owner));
    }

    @Test
    void memberRowsNeverHoldOwnerOrPublic() {
        SecurityInstallations store = new SecurityInstallations();
        Installation a = create(store, UUID.randomUUID(), "A", at(0, 64, 0));
        UUID player = UUID.randomUUID();
        assertEquals(SecurityInstallations.MemberResult.NOT_A_MEMBER_LEVEL,
                store.setMember(a.id(), player, AccessLevel.OWNER, 64));
        assertEquals(SecurityInstallations.MemberResult.ADDED, store.setMember(a.id(), player, AccessLevel.MEMBER, 64));
        assertEquals(SecurityInstallations.MemberResult.CHANGED, store.setMember(a.id(), player, AccessLevel.OFFICER, 64));
        assertEquals(SecurityInstallations.MemberResult.REMOVED, store.setMember(a.id(), player, AccessLevel.PUBLIC, 64));
        assertFalse(a.members().containsKey(player));
        assertEquals(AccessLevel.PUBLIC, a.memberLevel(player));
        store.setMember(a.id(), UUID.randomUUID(), AccessLevel.MEMBER, 1);
        assertEquals(SecurityInstallations.MemberResult.LIMIT_REACHED,
                store.setMember(a.id(), UUID.randomUUID(), AccessLevel.MEMBER, 1));
    }

    @Test
    void codecRoundTripPreservesEverythingButRejectsBadRows() {
        SecurityInstallations store = new SecurityInstallations();
        UUID owner = UUID.randomUUID();
        UUID officer = UUID.randomUUID();
        Installation a = create(store, owner, "Main Base", at(0, 64, 0));
        store.setMember(a.id(), officer, AccessLevel.OFFICER, 64);
        store.link(a.id(), new DevicePos(OVERWORLD, new BlockPos(3, 64, 3)), DOOR);
        store.setLockdown(a.id(), new Lockdown(true, 1234L, true));

        JsonElement json = SecurityInstallations.CODEC.encodeStart(JsonOps.INSTANCE, store).getOrThrow();
        SecurityInstallations loaded = SecurityInstallations.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        Installation back = loaded.byId(a.id()).orElseThrow();
        assertEquals(a.name(), back.name());
        assertEquals(a.owners(), back.owners());
        assertEquals(a.members(), back.members());
        assertEquals(a.hubPos(), back.hubPos());
        assertEquals(a.devices(), back.devices());
        assertEquals(a.lockdown(), back.lockdown());
        assertEquals(a.isOrphaned(), back.isOrphaned());
        assertArrayEquals(a.secret(), back.secret());
        assertEquals(a.id(), loaded.atDevice(new DevicePos(OVERWORLD, new BlockPos(3, 64, 3))).orElseThrow().id(),
                "the device index is rebuilt on load");
        assertEquals(1, loaded.countOwnedBy(owner), "the owner index is rebuilt on load");

        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        String hand = "{\"schema_version\":1,\"installations\":["
                + "{\"id\":\"" + first + "\",\"name\":\"One\",\"owners\":[\"" + owner + "\"],"
                + "\"members\":[{\"player\":\"" + player + "\",\"level\":\"public\"},"
                + "{\"player\":\"" + owner + "\",\"level\":\"member\"},"
                + "{\"player\":\"not-a-uuid\",\"level\":\"member\"}],"
                + "\"hub\":{\"dim\":\"minecraft:overworld\",\"x\":0,\"y\":64,\"z\":0},"
                + "\"devices\":[{\"dim\":\"minecraft:overworld\",\"x\":0,\"y\":64,\"z\":0,\"block\":\"nerosecurity:test_door\"},"
                + "{\"dim\":\"minecraft:overworld\",\"x\":1,\"y\":64,\"z\":1,\"block\":\"nerosecurity:test_door\"}]},"
                + "{\"id\":\"" + second + "\",\"name\":\"Two\",\"owners\":[\"" + owner + "\"],"
                + "\"hub\":{\"dim\":\"minecraft:overworld\",\"x\":9,\"y\":64,\"z\":9},"
                + "\"devices\":[{\"dim\":\"minecraft:overworld\",\"x\":1,\"y\":64,\"z\":1,\"block\":\"nerosecurity:test_door\"}],"
                + "\"secret\":\"not base64!\"},"
                + "{\"id\":\"" + UUID.randomUUID() + "\",\"name\":\"Ownerless\",\"owners\":[]}"
                + "]}";
        SecurityInstallations dirty = SecurityInstallations.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(hand))
                .getOrThrow();
        assertEquals(2, dirty.size(), "a row with no owner is dropped");
        Installation one = dirty.byId(first).orElseThrow();
        assertTrue(one.members().isEmpty(), "public, owner and malformed member rows are dropped");
        assertEquals(1, one.deviceCount(), "a device on the hub position is dropped");
        Installation two = dirty.byId(second).orElseThrow();
        assertEquals(0, two.deviceCount(), "a device already claimed by an earlier installation is dropped");
        assertEquals(Installation.SECRET_BYTES, one.secret().length, "a missing secret is regenerated");
        assertEquals(Installation.SECRET_BYTES, two.secret().length, "an invalid secret is regenerated");
        assertFalse(one.isOrphaned());
    }

    @Test
    void missingHubLoadsAsOrphaned() {
        UUID id = UUID.randomUUID();
        String hand = "{\"installations\":[{\"id\":\"" + id + "\",\"owners\":[\"" + UUID.randomUUID() + "\"]}]}";
        SecurityInstallations store = SecurityInstallations.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(hand))
                .getOrThrow();
        Installation loaded = store.byId(id).orElseThrow();
        assertTrue(loaded.isOrphaned());
        assertEquals(InstallationNames.defaultFor(id), loaded.name(), "a blank name falls back to the default");
    }

    @Test
    void resolveByIdPrefixAndName() {
        SecurityInstallations store = new SecurityInstallations();
        Installation a = create(store, UUID.randomUUID(), "Alpha", at(0, 64, 0));
        Installation b = create(store, UUID.randomUUID(), "Shared", at(10, 64, 0));
        create(store, UUID.randomUUID(), "Shared", at(20, 64, 0));

        assertEquals(a.id(), store.resolve(a.id().toString(), i -> true).orElseThrow().id());
        assertEquals(a.id(), store.resolve(a.id().toString().substring(0, 8), i -> true).orElseThrow().id());
        assertEquals(a.id(), store.resolve("Alpha", i -> true).orElseThrow().id());
        assertTrue(store.resolve("Shared", i -> true).isEmpty(), "an ambiguous name resolves to nothing");
        assertEquals(SecurityInstallations.Lookup.AMBIGUOUS, store.resolveDetailed("Shared", i -> true).status());
        assertTrue(store.resolve(a.id().toString().substring(0, 7), i -> true).isEmpty(), "prefix shorter than 8");
        assertTrue(store.resolve(a.id().toString(), i -> i.id().equals(b.id())).isEmpty(), "scope applies to ids too");
        Optional<Installation> scoped = store.resolve("Shared", i -> i.id().equals(b.id()));
        assertEquals(b.id(), scoped.orElseThrow().id(), "scope disambiguates");
    }

    @Test
    void namesAreSanitised() {
        UUID id = UUID.randomUUID();
        assertEquals("Basec One", InstallationNames.sanitise("  Base§c \t One\u0000 ", id));
        assertEquals(InstallationNames.defaultFor(id), InstallationNames.sanitise("§\u0007  ", id));
        assertEquals(Installation.NAME_MAX, InstallationNames.sanitise("x".repeat(80), id).length());
        assertNotNull(InstallationNames.shortId(id));
        assertEquals(8, InstallationNames.shortId(id).length());
    }

    @Test
    void toStringNeverCarriesTheSecret() {
        Installation installation = Installation.create(UUID.randomUUID(), "S", 0L);
        String text = installation.toString();
        assertFalse(text.contains(java.util.Base64.getEncoder().encodeToString(installation.secret())));
        assertTrue(text.contains(installation.id().toString()));
    }

    @Test
    void orphanAndDissolveClearTheIndexes() {
        SecurityInstallations store = new SecurityInstallations();
        UUID owner = UUID.randomUUID();
        DevicePos hub = new DevicePos(NETHER, new BlockPos(0, 64, 0));
        Installation a = create(store, owner, "A", hub);
        DevicePos device = new DevicePos(NETHER, new BlockPos(1, 64, 0));
        store.link(a.id(), device, DOOR);
        assertTrue(store.orphan(a.id()));
        assertTrue(a.isOrphaned());
        assertTrue(store.atHub(hub).isEmpty());
        assertTrue(store.dissolve(a.id()));
        assertTrue(store.atDevice(device).isEmpty());
        assertEquals(0, store.countOwnedBy(owner));
    }
}
