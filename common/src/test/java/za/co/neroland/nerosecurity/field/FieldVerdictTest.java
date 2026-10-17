package za.co.neroland.nerosecurity.field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.access.AccessQuery;
import za.co.neroland.nerosecurity.access.AccessResolver;
import za.co.neroland.nerosecurity.access.AccessSource;
import za.co.neroland.nerosecurity.access.AccessVote;
import za.co.neroland.nerosecurity.access.AdminCheck;
import za.co.neroland.nerosecurity.access.DenyReason;
import za.co.neroland.nerosecurity.access.KeycardSource;
import za.co.neroland.nerosecurity.access.MembershipSource;
import za.co.neroland.nerosecurity.block.DeviceAccessSettings;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.Lockdown;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;

/** Passability is a resolver decision on PASS_FIELD, nothing else. Plain JVM, server null. */
class FieldVerdictTest {

    private static final Identifier OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld");
    private static final DevicePos EMITTER = new DevicePos(OVERWORLD, new BlockPos(4, 64, 4));

    private final UUID owner = UUID.randomUUID();
    private final UUID officer = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private SecurityInstallations store;
    private Installation installation;

    @BeforeEach
    void setUp() {
        store = new SecurityInstallations();
        installation = store.create(owner, "Field", new DevicePos(OVERWORLD, new BlockPos(0, 64, 0)), 0L, 8).installation();
        store.setMember(installation.id(), officer, AccessLevel.OFFICER, 64);
        store.setMember(installation.id(), member, AccessLevel.MEMBER, 64);
        installation = store.byId(installation.id()).orElseThrow();
    }

    private static AccessSource fixed(Identifier id, AccessVote vote) {
        return new AccessSource() {
            @Override
            public Identifier id() {
                return id;
            }

            @Override
            public AccessVote vote(AccessQuery query) {
                return vote;
            }
        };
    }

    private static AccessResolver resolver(List<AccessSource> sources, boolean admin) {
        return new AccessResolver(() -> sources, admin ? (s, a) -> true : AdminCheck.NONE, () -> true);
    }

    private static FieldSettings at(AccessLevel min, boolean keycardOnly) {
        return FieldSettings.DEFAULT.withAccess(new DeviceAccessSettings(min, keycardOnly));
    }

    @Test
    void aStoredOwnerPassesAKeycardOnlyField() {
        AccessResolver membership = resolver(List.of(MembershipSource.INSTANCE), false);
        assertTrue(pass(membership, installation, owner, at(AccessLevel.MEMBER, true), true),
                "the owner passes their own keycard-only field without a card");
        assertTrue(pass(membership, installation, owner, at(AccessLevel.OWNER, true), true),
                "... at OWNER too");
        assertFalse(pass(membership, installation, officer, at(AccessLevel.MEMBER, true), true),
                "an officer without a card is still stopped");
        assertFalse(pass(membership, installation, member, at(AccessLevel.MEMBER, true), true));

        AccessResolver claimed = resolver(List.of(MembershipSource.INSTANCE, fixed(
                Identifier.fromNamespaceAndPath("nerosecurity", "claim"),
                new AccessVote.Deny(DenyReason.SOURCE_DENY, "access.nerosecurity.deny.claim"))), false);
        assertFalse(pass(claimed, installation, owner, at(AccessLevel.MEMBER, true), true),
                "the owner pass-through drops the filter only; a deny still wins");
    }

    @Test
    void aClaimOverTheEmitterNeverSealsTheOwnerInside() {
        // A claim-like source: denies only when the query carries a block position, as the real claim source does.
        AccessSource claimAtPosition = new AccessSource() {
            @Override
            public Identifier id() {
                return Identifier.fromNamespaceAndPath("nerosecurity", "claim");
            }

            @Override
            public AccessVote vote(AccessQuery query) {
                return query.at() == null ? AccessVote.Abstain.INSTANCE
                        : new AccessVote.Deny(DenyReason.SOURCE_DENY, "access.nerosecurity.deny.claim");
            }
        };
        AccessResolver r = resolver(List.of(MembershipSource.INSTANCE, claimAtPosition), false);
        assertTrue(pass(r, installation, owner, at(AccessLevel.MEMBER, true), true),
                "the stored owner passes their own field even under a hostile claim");
        assertFalse(pass(r, installation, officer, at(AccessLevel.MEMBER, false), true),
                "everyone else still meets the claim's deny");
    }

    private boolean pass(AccessResolver r, Installation i, UUID who, FieldSettings s, boolean enabled) {
        return FieldVerdict.decide(r, null, i, EMITTER, who, s, enabled);
    }

    @Test
    void passabilityDecision() {
        AccessResolver membership = resolver(List.of(MembershipSource.INSTANCE), false);
        FieldSettings memberField = at(AccessLevel.MEMBER, false);
        FieldSettings officerField = at(AccessLevel.OFFICER, false);

        assertTrue(pass(membership, installation, member, memberField, true), "member passes a MEMBER field");
        assertFalse(pass(membership, installation, member, officerField, true), "member is stopped by an OFFICER field");
        assertTrue(pass(membership, installation, officer, officerField, true));
        assertFalse(pass(membership, installation, stranger, memberField, true), "stranger is stopped");
        assertFalse(pass(membership, null, owner, memberField, true), "unlinked emitter: solid for everyone");
        assertTrue(pass(membership, installation, stranger, memberField, false), "kill switch: passable to all");
        assertTrue(pass(resolver(List.of(MembershipSource.INSTANCE), true), installation, stranger, officerField, true),
                "an operator passes");

        // Keycard-only: only the keycard source's grant counts.
        AccessResolver withCard = resolver(List.of(MembershipSource.INSTANCE,
                fixed(KeycardSource.ID, new AccessVote.Grant(AccessLevel.MEMBER))), false);
        assertTrue(pass(withCard, installation, stranger, at(AccessLevel.MEMBER, true), true), "a valid card passes");
        assertFalse(pass(membership, installation, officer, at(AccessLevel.MEMBER, true), true),
                "keycard-only: membership alone does not");

        // Deny wins at the field.
        AccessResolver claimed = resolver(List.of(MembershipSource.INSTANCE, fixed(
                Identifier.fromNamespaceAndPath("nerosecurity", "claim"),
                new AccessVote.Deny(DenyReason.SOURCE_DENY, "access.nerosecurity.deny.claim"))), false);
        assertFalse(pass(claimed, installation, officer, memberField, true), "a claim deny stops even an officer");
        assertFalse(pass(claimed, installation, owner, memberField, true), "... and the owner");

        // Lockdown seals the field for everyone below OWNER.
        store.setLockdown(installation.id(), new Lockdown(true, 1L, true));
        Installation locked = store.byId(installation.id()).orElseThrow();
        assertFalse(pass(membership, locked, officer, memberField, true), "lockdown: officer stopped");
        assertTrue(pass(membership, locked, owner, memberField, true), "lockdown: owner passes");

        // Keycard-only never seals the owner out, lockdown or not.
        assertTrue(pass(membership, locked, owner, at(AccessLevel.MEMBER, true), true),
                "lockdown + keycard-only: the owner still passes without a card");
        assertFalse(pass(membership, locked, officer, at(AccessLevel.MEMBER, true), true));

        // A throwing resolver path fails closed.
        AccessResolver broken = new AccessResolver(() -> {
            throw new IllegalStateException("boom");
        }, AdminCheck.NONE, () -> true);
        assertFalse(pass(broken, installation, owner, memberField, true));
    }
}
