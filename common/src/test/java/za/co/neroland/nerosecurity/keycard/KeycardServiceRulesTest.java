package za.co.neroland.nerosecurity.keycard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static za.co.neroland.nerosecurity.access.AccessLevel.MEMBER;
import static za.co.neroland.nerosecurity.access.AccessLevel.OFFICER;
import static za.co.neroland.nerosecurity.access.AccessLevel.OWNER;
import static za.co.neroland.nerosecurity.access.AccessLevel.PUBLIC;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;

/** The pure card rules the programmer and commands apply after the resolver allowed the action. */
class KeycardServiceRulesTest {

    @Test
    void mayWrite() {
        for (AccessLevel target : AccessLevel.values()) {
            assertFalse(KeycardService.mayWrite(PUBLIC, target), "PUBLIC writes nothing");
            assertFalse(KeycardService.mayWrite(MEMBER, target), "MEMBER writes nothing (it may only copy)");
            assertTrue(KeycardService.mayWrite(OWNER, target), "OWNER writes " + target);
        }
        assertTrue(KeycardService.mayWrite(OFFICER, PUBLIC));
        assertTrue(KeycardService.mayWrite(OFFICER, MEMBER));
        assertFalse(KeycardService.mayWrite(OFFICER, OFFICER), "never your own level, below OWNER");
        assertFalse(KeycardService.mayWrite(OFFICER, OWNER));
        for (AccessLevel effective : AccessLevel.values()) {
            assertEquals(effective == OWNER, KeycardService.mayWrite(effective, OWNER),
                    "nobody but OWNER writes OWNER");
        }
        assertFalse(KeycardService.mayWrite(null, MEMBER));
        assertFalse(KeycardService.mayWrite(OWNER, null));
    }

    @Test
    void mayClone() {
        // Basic cards: only what the actor could write, or the actor's own tier.
        assertTrue(KeycardService.mayClone(MEMBER, MEMBER, false, false), "MEMBER copies MEMBER cards");
        assertFalse(KeycardService.mayClone(MEMBER, OFFICER, false, false), "MEMBER cannot copy an OFFICER card");
        assertFalse(KeycardService.mayClone(MEMBER, OWNER, false, false), "MEMBER cannot copy an OWNER card");
        assertTrue(KeycardService.mayClone(OFFICER, MEMBER, false, false));
        assertTrue(KeycardService.mayClone(OFFICER, OFFICER, false, false), "OFFICER copies their own tier");
        assertFalse(KeycardService.mayClone(OFFICER, OWNER, false, false), "OFFICER cannot copy an OWNER card");
        assertTrue(KeycardService.mayClone(OWNER, OWNER, false, false));
        assertTrue(KeycardService.mayClone(OFFICER, PUBLIC, false, false));
        assertFalse(KeycardService.mayClone(PUBLIC, PUBLIC, false, true), "copying needs MEMBER");
        assertFalse(KeycardService.mayClone(null, MEMBER, false, true));
        assertFalse(KeycardService.mayClone(OWNER, null, false, true));
        for (AccessLevel effective : AccessLevel.values()) {
            for (AccessLevel level : AccessLevel.values()) {
                if (KeycardService.mayClone(effective, level, false, false)) {
                    assertTrue(KeycardService.mayWrite(effective, level) || effective == level,
                            effective + " copied " + level + " without being able to write it");
                }
            }
        }
        // Encrypted cards: OWNER and a module, whatever the level.
        assertFalse(KeycardService.mayClone(MEMBER, MEMBER, true, true));
        assertFalse(KeycardService.mayClone(OFFICER, MEMBER, true, true), "OFFICER cannot copy encrypted cards");
        assertFalse(KeycardService.mayClone(OWNER, OWNER, true, false), "OWNER without a module cannot");
        assertTrue(KeycardService.mayClone(OWNER, OWNER, true, true), "OWNER with a module can");
    }

    @Test
    void cloneBudgetStopsMembersFillingTheLedger() {
        assertTrue(KeycardService.cloneBudgetAllows(MEMBER, 0, 512));
        assertTrue(KeycardService.cloneBudgetAllows(MEMBER, 384, 512), "exactly 75% is still allowed");
        assertFalse(KeycardService.cloneBudgetAllows(MEMBER, 385, 512), "above 75% a MEMBER may not copy");
        assertTrue(KeycardService.cloneBudgetAllows(OFFICER, 385, 512));
        assertTrue(KeycardService.cloneBudgetAllows(OWNER, 511, 512));
        assertFalse(KeycardService.cloneBudgetAllows(null, 0, 512));
        assertFalse(KeycardService.cloneBudgetAllows(MEMBER, 13, 16));
        assertTrue(KeycardService.cloneBudgetAllows(MEMBER, 12, 16));
    }

    @Test
    void basicLevelAllowed() {
        AccessLevel defaultCap = NeroSecurityConfig.parseBasicMax("officer");
        assertEquals(OFFICER, defaultCap);
        assertFalse(KeycardService.basicLevelAllowed(OWNER, false, defaultCap), "basic OWNER refused at default cap");
        assertTrue(KeycardService.basicLevelAllowed(OWNER, true, defaultCap), "allowed when encrypted");
        assertTrue(KeycardService.basicLevelAllowed(OFFICER, false, defaultCap));
        assertTrue(KeycardService.basicLevelAllowed(OWNER, false, OWNER), "allowed when the cap is raised");
        assertFalse(KeycardService.basicLevelAllowed(OFFICER, false, MEMBER));
    }

    @Test
    void configParsingFallsBackSafely() {
        assertEquals(OFFICER, NeroSecurityConfig.parseBasicMax("junk"));
        assertEquals(OFFICER, NeroSecurityConfig.parseBasicMax("public"));
        assertEquals(OFFICER, NeroSecurityConfig.parseBasicMax(null));
        assertEquals(OWNER, NeroSecurityConfig.parseBasicMax(" Owner "));
        assertEquals(MEMBER, NeroSecurityConfig.parseBasicMax("member"));
        assertNull(NeroSecurityConfig.parseAutomation("off"));
        assertNull(NeroSecurityConfig.parseAutomation("owner"), "automation is capped at OFFICER");
        assertNull(NeroSecurityConfig.parseAutomation("public"));
        assertNull(NeroSecurityConfig.parseAutomation("junk"));
        assertEquals(MEMBER, NeroSecurityConfig.parseAutomation("member"));
        assertEquals(OFFICER, NeroSecurityConfig.parseAutomation("officer"));
    }
}
