package za.co.neroland.nerosecurity.keycard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static za.co.neroland.nerosecurity.keycard.KeycardTestSupport.create;
import static za.co.neroland.nerosecurity.keycard.KeycardTestSupport.issue;
import static za.co.neroland.nerosecurity.keycard.KeycardTestSupport.withPayload;

import java.util.List;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.KeycardSigner;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;

/** Card verification against real installations, ledgers and signatures. Plain JVM. */
class KeycardVerifierTest {

    private final UUID ownerA = UUID.randomUUID();
    private final UUID ownerB = UUID.randomUUID();
    private final UUID holder = UUID.randomUUID();

    private SecurityInstallations store;
    private Installation a;
    private Installation b;

    @BeforeEach
    void setUp() {
        store = new SecurityInstallations();
        a = create(store, ownerA, "A", 0);
        b = create(store, ownerB, "B", 100);
    }

    @Test
    void signedByIgnoresHolderButNotTheSignature() {
        KeycardData bound = issue(store, a, AccessLevel.OFFICER, false, holder);
        UUID stranger = UUID.randomUUID();
        assertEquals(CardStatus.NOT_BOUND_HOLDER, KeycardVerifier.verify(a, bound, stranger));
        assertTrue(KeycardVerifier.signedBy(a, bound), "issued by A, whoever holds it");
        assertFalse(KeycardVerifier.signedBy(b, bound), "not by B");
        assertFalse(KeycardVerifier.signedBy(a, null));
        KeycardPayload edited = new KeycardPayload(a.id(), AccessLevel.OWNER, bound.serial(), bound.encrypted(),
                bound.bound(), bound.issuedAt());
        assertFalse(KeycardVerifier.signedBy(a, withPayload(bound, edited)), "an edited card is not signed by A");
    }

    @Test
    void cardSignedForAIsRejectedByB() {
        KeycardData cardForA = issue(store, a, AccessLevel.OFFICER, false, null);
        assertEquals(CardStatus.VALID, KeycardVerifier.verify(a, cardForA, holder), "valid where it was issued");
        assertEquals(CardStatus.WRONG_INSTALLATION, KeycardVerifier.verify(b, cardForA, holder));
        assertFalse(KeycardVerifier.isTamper(CardStatus.WRONG_INSTALLATION), "a foreign card is not an attack");

        // Re-labelled for B but still carrying A's tag: the HMAC under B's secret fails.
        issue(store, b, AccessLevel.OFFICER, false, null); // B has a row with the same serial
        KeycardPayload relabelled = new KeycardPayload(b.id(), cardForA.level(), cardForA.serial(),
                cardForA.encrypted(), cardForA.bound(), cardForA.issuedAt());
        KeycardData forged = withPayload(cardForA, relabelled);
        assertEquals(CardStatus.BAD_SIGNATURE, KeycardVerifier.verify(b, forged, holder));
        assertTrue(KeycardVerifier.isTamper(CardStatus.BAD_SIGNATURE));
    }

    @Test
    void tamperedFieldIsRejected() {
        KeycardData card = issue(store, a, AccessLevel.MEMBER, false, null);
        KeycardPayload p = card.payload();
        List<KeycardPayload> edits = List.of(
                new KeycardPayload(p.installationId(), AccessLevel.OWNER, p.serial(), p.encrypted(), p.bound(), p.issuedAt()),
                new KeycardPayload(p.installationId(), p.level(), p.serial() + 1, p.encrypted(), p.bound(), p.issuedAt()),
                new KeycardPayload(p.installationId(), p.level(), p.serial(), !p.encrypted(), p.bound(), p.issuedAt()),
                new KeycardPayload(p.installationId(), p.level(), p.serial(), p.encrypted(), !p.bound(), p.issuedAt()),
                new KeycardPayload(p.installationId(), p.level(), p.serial(), p.encrypted(), p.bound(), p.issuedAt() + 1));
        for (KeycardPayload edit : edits) {
            assertEquals(CardStatus.BAD_SIGNATURE, KeycardVerifier.verify(a, withPayload(card, edit), holder),
                    "edited field must break the signature: " + edit);
        }
        assertEquals(CardStatus.BLANK, KeycardVerifier.verify(a, null, holder));
    }

    @Test
    void revokedSerialIsRejected() {
        KeycardData revoked = issue(store, a, AccessLevel.MEMBER, false, null);
        KeycardData old = issue(store, a, AccessLevel.MEMBER, false, null);
        assertTrue(store.revokeCard(a.id(), revoked.serial()));
        assertEquals(CardStatus.UNKNOWN_SERIAL, KeycardVerifier.verify(a, revoked, holder));
        assertEquals(CardStatus.VALID, KeycardVerifier.verify(a, old, holder), "revoking one card leaves the others");

        assertEquals(1, store.revokeAllCards(a.id()));
        assertEquals(CardStatus.BELOW_FLOOR, KeycardVerifier.verify(a, old, holder));
        assertEquals(CardStatus.BELOW_FLOOR, KeycardVerifier.verify(a, revoked, holder));
        KeycardData fresh = issue(store, a, AccessLevel.MEMBER, false, null);
        assertEquals(CardStatus.VALID, KeycardVerifier.verify(a, fresh, holder), "a card issued after revoke-all works");
    }

    /**
     * Erasure deletes the ledger row bound to the subject, so the card is dead for everyone, holder
     * included. (The row is gone rather than unbound, so it reads as an unknown serial.)
     */
    @Test
    void erasureKillsBoundCard() {
        KeycardData bound = issue(store, a, AccessLevel.OFFICER, false, holder);
        KeycardData unbound = issue(store, a, AccessLevel.MEMBER, false, null);
        assertEquals(CardStatus.VALID, KeycardVerifier.verify(a, bound, holder));
        assertTrue(store.hasCardBoundTo(holder));

        store.erase(holder);

        CardStatus after = KeycardVerifier.verify(a, bound, holder);
        assertEquals(CardStatus.UNKNOWN_SERIAL, after);
        assertTrue(KeycardVerifier.isTamper(after));
        assertFalse(store.hasCardBoundTo(holder));
        assertEquals(CardStatus.VALID, KeycardVerifier.verify(a, unbound, holder), "unbound cards survive erasure");
        assertEquals(CardStatus.UNKNOWN_SERIAL, KeycardVerifier.verify(a, bound, ownerA), "dead for everyone");
    }

    @Test
    void boundCardOnlyForHolder() {
        KeycardData bound = issue(store, a, AccessLevel.MEMBER, false, holder);
        assertTrue(bound.bound());
        assertEquals(CardStatus.VALID, KeycardVerifier.verify(a, bound, holder));
        assertEquals(CardStatus.NOT_BOUND_HOLDER, KeycardVerifier.verify(a, bound, UUID.randomUUID()));
        assertEquals(CardStatus.NOT_BOUND_HOLDER, KeycardVerifier.verify(a, bound, ownerA),
                "not even the owner may present someone else's bound card");
    }

    @Test
    void ledgerMismatchIsRejected() {
        KeycardData card = issue(store, a, AccessLevel.MEMBER, false, null);
        // Edit the saved ledger row's level, as a broken or hand-edited save would.
        JsonElement json = SecurityInstallations.CODEC.encodeStart(JsonOps.INSTANCE, store).getOrThrow();
        for (JsonElement entry : json.getAsJsonObject().getAsJsonArray("installations")) {
            JsonObject row = entry.getAsJsonObject();
            if (row.get("id").getAsString().equals(a.id().toString())) {
                JsonArray cards = row.getAsJsonArray("cards");
                cards.get(0).getAsJsonObject().addProperty("level", "officer");
            }
        }
        SecurityInstallations reloaded = SecurityInstallations.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        Installation edited = reloaded.byId(a.id()).orElseThrow();
        assertEquals(AccessLevel.OFFICER, edited.card(card.serial()).level());
        assertEquals(CardStatus.RECORD_MISMATCH, KeycardVerifier.verify(edited, card, holder));
        assertTrue(KeycardSigner.verifySignature(edited, card.payload(), card.signature()),
                "the secret survives the round trip; only the ledger row disagrees");
    }
}
