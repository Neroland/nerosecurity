package za.co.neroland.nerosecurity.block;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.access.AccessDecision;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.access.DenyReason;

/** The hub break gate: owner-sneak or operator, and never anyone the resolver refused. */
class HubBreakRuleTest {

    private static final AccessDecision OWNER = new AccessDecision(true, AccessLevel.OWNER, AccessLevel.OWNER,
            DenyReason.NONE, List.of(), false);
    private static final AccessDecision ADMIN = AccessDecision.adminOverride(AccessLevel.OWNER);
    private static final AccessDecision REFUSED = new AccessDecision(false, AccessLevel.MEMBER, AccessLevel.OWNER,
            DenyReason.INSUFFICIENT_LEVEL, List.of(), false);

    @Test
    void aStoredOwnerBreaksOnlyWhileSneaking() {
        assertTrue(HubBreakRule.mayBreak(true, OWNER));
        assertFalse(HubBreakRule.mayBreak(false, OWNER), "an owner's plain hit is the accidental-dissolve guard");
    }

    @Test
    void anOperatorBreaksWithOrWithoutSneaking() {
        assertTrue(HubBreakRule.mayBreak(true, ADMIN));
        assertTrue(HubBreakRule.mayBreak(false, ADMIN), "the override is the deliberate act");
    }

    @Test
    void aRefusedBreakerNeverBreaksTheHub() {
        assertFalse(HubBreakRule.mayBreak(true, REFUSED));
        assertFalse(HubBreakRule.mayBreak(false, REFUSED));
        assertFalse(HubBreakRule.mayBreak(true, null));
        assertFalse(HubBreakRule.mayBreak(false, null));
    }
}
