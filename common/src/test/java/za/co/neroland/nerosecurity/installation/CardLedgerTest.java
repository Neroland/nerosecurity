package za.co.neroland.nerosecurity.installation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.access.AccessLevel;

/** The card ledger and automation policy in the installation store. Plain JVM. */
class CardLedgerTest {

    private static final Identifier OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld");

    private static Installation create(SecurityInstallations store, int x) {
        return store.create(UUID.randomUUID(), "L", new DevicePos(OVERWORLD, new BlockPos(x, 64, 0)), 0L, 8)
                .installation();
    }

    private static long issue(SecurityInstallations store, Installation i, int cap) {
        SecurityInstallations.IssueResult result = store.issueCard(i.id(), AccessLevel.MEMBER, false, null, 0L, cap);
        assertEquals(SecurityInstallations.IssueStatus.OK, result.status());
        return result.serial();
    }

    @Test
    void serialsAreMonotonicAndNeverReused() {
        SecurityInstallations store = new SecurityInstallations();
        Installation i = create(store, 0);
        long first = issue(store, i, 512);
        long second = issue(store, i, 512);
        assertTrue(store.revokeCard(i.id(), second));
        assertFalse(store.revokeCard(i.id(), second), "a revoked row is gone");
        long third = issue(store, i, 512);
        assertTrue(first < second && second < third, "strictly increasing, the revoked serial is not reused");
        assertEquals(2, store.revokeAllCards(i.id()));
        assertEquals(third + 1, i.minValidSerial(), "revoke-all raises the floor to the next serial");
        long fourth = issue(store, i, 512);
        assertTrue(fourth > third);
        assertEquals(1, store.revokeAllCards(i.id()));
        assertTrue(i.minValidSerial() > fourth);
        assertEquals(0, store.revokeAllCards(i.id()), "an empty ledger revokes nothing");
        assertEquals(-1, store.revokeAllCards(UUID.randomUUID()));
    }

    @Test
    void ledgerCapRefusesNotEvicts() {
        SecurityInstallations store = new SecurityInstallations();
        Installation i = create(store, 0);
        long a = issue(store, i, 2);
        long b = issue(store, i, 2);
        SecurityInstallations.IssueResult third = store.issueCard(i.id(), AccessLevel.OFFICER, true, null, 0L, 2);
        assertEquals(SecurityInstallations.IssueStatus.LEDGER_FULL, third.status());
        assertNull(third.record());
        assertEquals(2, i.cardCount());
        assertTrue(i.cards().containsKey(a) && i.cards().containsKey(b), "existing rows untouched");
        assertEquals(b + 1, i.nextSerial(), "a refused issue consumes no serial");
        assertEquals(SecurityInstallations.IssueStatus.NO_INSTALLATION,
                store.issueCard(UUID.randomUUID(), AccessLevel.MEMBER, false, null, 0L, 2).status());
    }

    @Test
    void codecRoundTripsLedgerAndAutomation() {
        SecurityInstallations store = new SecurityInstallations();
        Installation i = create(store, 0);
        UUID holder = UUID.randomUUID();
        store.issueCard(i.id(), AccessLevel.MEMBER, false, null, 11L, 512);
        long bound = store.issueCard(i.id(), AccessLevel.OFFICER, true, holder, 22L, 512).serial();
        long revoked = issue(store, i, 512);
        store.revokeCard(i.id(), revoked);
        assertTrue(store.setAutomationLevel(i.id(), AccessLevel.OFFICER));

        JsonElement json = SecurityInstallations.CODEC.encodeStart(JsonOps.INSTANCE, store).getOrThrow();
        SecurityInstallations copy = SecurityInstallations.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        Installation back = copy.byId(i.id()).orElseThrow();
        assertEquals(i.cards(), back.cards());
        assertEquals(i.nextSerial(), back.nextSerial());
        assertEquals(i.minValidSerial(), back.minValidSerial());
        assertEquals(AccessLevel.OFFICER, back.automationLevel());
        assertEquals(holder, back.card(bound).boundOwner());

        // Hand-edit: rows that break an invariant are dropped, the rest load.
        JsonObject row = json.getAsJsonObject().getAsJsonArray("installations").get(0).getAsJsonObject();
        JsonArray cards = row.getAsJsonArray("cards");
        long next = row.get("next_serial").getAsLong();
        cards.add(JsonParser.parseString("{\"serial\":" + next + ",\"level\":\"member\",\"issued_at\":0}"));
        cards.add(JsonParser.parseString("{\"serial\":" + (next + 5) + ",\"level\":\"member\",\"issued_at\":0}"));
        cards.add(JsonParser.parseString("{\"serial\":0,\"level\":\"member\",\"issued_at\":0}"));
        cards.add(JsonParser.parseString("{\"serial\":" + bound + ",\"level\":\"owner\",\"issued_at\":0}"));
        cards.add(JsonParser.parseString("{\"serial\":" + revoked + ",\"level\":\"admin\",\"issued_at\":0}"));
        cards.add(JsonParser.parseString("{\"serial\":" + revoked + ",\"level\":\"member\",\"bound_owner\":\"nope\"}"));
        cards.add(JsonParser.parseString("{\"serial\":" + revoked + ",\"level\":\"member\",\"bound_owner\":\""
                + Installation.TOMBSTONE_OWNER + "\"}"));
        row.addProperty("automation_level", "owner");
        Installation edited = SecurityInstallations.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow()
                .byId(i.id()).orElseThrow();
        assertEquals(i.cards(), edited.cards(), "every invalid row was dropped; duplicates keep the first");
        assertNull(edited.automationLevel(), "an automation level above OFFICER reads as off");

        // A pre-keycard save (no ledger keys at all) loads with an empty ledger and automation off.
        JsonObject legacy = json.getAsJsonObject().deepCopy();
        JsonObject legacyRow = legacy.getAsJsonArray("installations").get(0).getAsJsonObject();
        for (String key : List.of("cards", "next_serial", "min_valid_serial", "automation_level")) {
            legacyRow.remove(key);
        }
        Installation old = SecurityInstallations.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow()
                .byId(i.id()).orElseThrow();
        assertEquals(Map.of(), old.cards());
        assertEquals(1L, old.nextSerial());
        assertEquals(1L, old.minValidSerial());
        assertNull(old.automationLevel());
    }

    @Test
    void floorAboveNextSerialIsClampedSoSerialsAreNeverReissued() {
        SecurityInstallations store = new SecurityInstallations();
        Installation i = create(store, 0);
        JsonElement json = SecurityInstallations.CODEC.encodeStart(JsonOps.INSTANCE, store).getOrThrow();
        JsonObject row = json.getAsJsonObject().getAsJsonArray("installations").get(0).getAsJsonObject();
        row.addProperty("next_serial", 3);
        row.addProperty("min_valid_serial", 10);
        Installation loaded = SecurityInstallations.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow()
                .byId(i.id()).orElseThrow();
        assertEquals(10L, loaded.minValidSerial());
        assertEquals(10L, loaded.nextSerial());
    }

    @Test
    void automationLevelRejectsOwnerAndPublic() {
        SecurityInstallations store = new SecurityInstallations();
        Installation i = create(store, 0);
        assertNull(i.automationLevel(), "off by default");
        assertFalse(store.setAutomationLevel(i.id(), AccessLevel.OWNER));
        assertFalse(store.setAutomationLevel(i.id(), AccessLevel.PUBLIC));
        assertNull(i.automationLevel());
        assertTrue(store.setAutomationLevel(i.id(), AccessLevel.MEMBER));
        assertEquals(AccessLevel.MEMBER, i.automationLevel());
        assertTrue(store.setAutomationLevel(i.id(), AccessLevel.OFFICER));
        assertTrue(store.setAutomationLevel(i.id(), null));
        assertNull(i.automationLevel());
        assertFalse(store.setAutomationLevel(UUID.randomUUID(), AccessLevel.MEMBER));
    }

    @Test
    void clampLedgerKeepsTheLowestSerials() {
        Map<Long, CardRecord> cards = new java.util.LinkedHashMap<>();
        for (long serial : new long[] { 7L, 2L, 9L, 4L, 1L }) {
            cards.put(serial, new CardRecord(AccessLevel.MEMBER, false, null, 0L));
        }
        assertEquals(2, SecurityInstallations.clampLedger(cards, 3));
        assertEquals(java.util.Set.of(1L, 2L, 4L), cards.keySet());
        assertEquals(0, SecurityInstallations.clampLedger(cards, 3), "already within the cap");
        assertEquals(0, SecurityInstallations.clampLedger(cards, 10));
    }

    @Test
    void loadRefusesRowsBeyondTheCap() {
        SecurityInstallations store = new SecurityInstallations();
        Installation i = create(store, 0);
        int cap = za.co.neroland.nerosecurity.config.NeroSecurityConfig.keycardsPerInstallation();
        int issued = cap + 20;
        for (int n = 0; n < issued; n++) {
            issue(store, i, issued);
        }
        long revoked = 5L;
        assertTrue(store.revokeCard(i.id(), revoked));
        long next = i.nextSerial();
        JsonElement json = SecurityInstallations.CODEC.encodeStart(JsonOps.INSTANCE, store).getOrThrow();
        Installation loaded = SecurityInstallations.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow()
                .byId(i.id()).orElseThrow();
        assertEquals(cap, loaded.cardCount(), "clamped to keycardsPerInstallation");
        long highestKept = loaded.cards().keySet().stream().mapToLong(Long::longValue).max().orElseThrow();
        assertTrue(loaded.cards().keySet().stream().allMatch(s -> s <= highestKept));
        assertEquals((long) cap + 1, highestKept, "the lowest serials are kept (one below was revoked)");
        assertFalse(loaded.cards().containsKey(revoked), "a revoked card never comes back");
        assertEquals(next, loaded.nextSerial(), "no serial is reissued");
    }
}
