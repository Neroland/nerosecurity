package za.co.neroland.nerosecurity.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static za.co.neroland.nerosecurity.access.AccessLevel.MEMBER;
import static za.co.neroland.nerosecurity.access.AccessLevel.OFFICER;
import static za.co.neroland.nerosecurity.access.AccessLevel.OWNER;
import static za.co.neroland.nerosecurity.access.AccessLevel.PUBLIC;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.access.AccessLevel;

/** What the programmer screen enables, and the reason it shows when it does not. Display only. */
class ProgrammerUiRulesTest {

    private static final String TOO_HIGH = "programmer.nerosecurity.level_too_high";
    private static final String OWNER_ONLY = "programmer.nerosecurity.owner_only";
    private static final String ENCRYPT = "programmer.nerosecurity.encrypt_required";

    @Test
    void memberCanWriteNothing() {
        for (AccessLevel target : AccessLevel.values()) {
            assertEquals(target == OWNER ? OWNER_ONLY : TOO_HIGH,
                    ProgrammerUiRules.levelBlockedKey(MEMBER, target, false, OFFICER));
        }
        assertNull(ProgrammerUiRules.pick(MEMBER, false, OFFICER, null));
        assertNull(ProgrammerUiRules.pick(MEMBER, false, OFFICER, MEMBER));
    }

    @Test
    void officerWritesBelowItsOwnLevel() {
        assertNull(ProgrammerUiRules.levelBlockedKey(OFFICER, PUBLIC, false, OFFICER));
        assertNull(ProgrammerUiRules.levelBlockedKey(OFFICER, MEMBER, false, OFFICER));
        assertEquals(TOO_HIGH, ProgrammerUiRules.levelBlockedKey(OFFICER, OFFICER, false, OFFICER));
        assertEquals(OWNER_ONLY, ProgrammerUiRules.levelBlockedKey(OFFICER, OWNER, false, OFFICER));
        assertEquals(MEMBER, ProgrammerUiRules.pick(OFFICER, false, OFFICER, null));
        assertEquals(PUBLIC, ProgrammerUiRules.pick(OFFICER, false, OFFICER, PUBLIC));
        assertEquals(MEMBER, ProgrammerUiRules.pick(OFFICER, false, OFFICER, OWNER), "an unwritable choice falls back");
    }

    @Test
    void ownerCardsNeedEncryptionAtTheDefaultCap() {
        assertEquals(ENCRYPT, ProgrammerUiRules.levelBlockedKey(OWNER, OWNER, false, OFFICER));
        assertNull(ProgrammerUiRules.levelBlockedKey(OWNER, OWNER, true, OFFICER));
        assertNull(ProgrammerUiRules.levelBlockedKey(OWNER, OWNER, false, OWNER), "allowed when the cap is raised");
        assertNull(ProgrammerUiRules.levelBlockedKey(OWNER, OFFICER, false, OFFICER));
        assertEquals(OFFICER, ProgrammerUiRules.pick(OWNER, false, OFFICER, null));
        assertEquals(OWNER, ProgrammerUiRules.pick(OWNER, true, OFFICER, null));
        assertEquals(OFFICER, ProgrammerUiRules.pick(OWNER, false, OFFICER, OWNER),
                "turning encryption off drops an OWNER selection to the highest basic level");
    }

    @Test
    void encryptToggleNeedsOwnerThenModule() {
        assertEquals("programmer.nerosecurity.toggle.encrypted.needs_owner",
                ProgrammerUiRules.encryptBlockedKey(OFFICER, true));
        assertEquals("programmer.nerosecurity.toggle.encrypted.needs_owner",
                ProgrammerUiRules.encryptBlockedKey(MEMBER, false));
        assertEquals("programmer.nerosecurity.toggle.encrypted.needs_module",
                ProgrammerUiRules.encryptBlockedKey(OWNER, false));
        assertNull(ProgrammerUiRules.encryptBlockedKey(OWNER, true));
    }
}
