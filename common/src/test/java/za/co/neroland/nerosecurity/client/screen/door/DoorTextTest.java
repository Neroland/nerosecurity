package za.co.neroland.nerosecurity.client.screen.door;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.client.widget.Status;

/** The door screen's rows and lock reasons mirror the server's rules, with a different glyph shape per state. */
class DoorTextTest {

    @Test
    void linkRowIsFilledWhenLinkedAndOutlineWhenNot() {
        assertEquals(new DoorText.Row(Status.OK, "door.nerosecurity.linked"), DoorText.link(true));
        assertEquals(new DoorText.Row(Status.OFF, "device.nerosecurity.unlinked"), DoorText.link(false));
    }

    @Test
    void sealedBeatsUnpoweredAndAWorkingDoorHasNoConditionRow() {
        assertEquals(new DoorText.Row(Status.BAD, "door.nerosecurity.sealed"), DoorText.condition(true, true));
        assertEquals(new DoorText.Row(Status.BAD, "door.nerosecurity.sealed"), DoorText.condition(true, false));
        assertEquals(new DoorText.Row(Status.WARN, "door.nerosecurity.unpowered"), DoorText.condition(false, true));
        assertNull(DoorText.condition(false, false));
    }

    @Test
    void unlinkedDoorsRefuseSettingsAndOwnerDoorsNeedAnOwner() {
        assertEquals("device.nerosecurity.unlinked", DoorText.editWhy(false, AccessLevel.MEMBER, true));
        assertEquals("door.nerosecurity.owner_only_setting", DoorText.editWhy(true, AccessLevel.OWNER, false));
        assertNull(DoorText.editWhy(true, AccessLevel.OWNER, true));
        assertNull(DoorText.editWhy(true, AccessLevel.OFFICER, false));
        assertNull(DoorText.editWhy(true, AccessLevel.PUBLIC, false));
    }
}
