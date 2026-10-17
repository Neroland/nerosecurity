package za.co.neroland.nerosecurity.client.render.chest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.client.SecurityColours;
import za.co.neroland.nerosecurity.client.anim.Blink;
import za.co.neroland.nerosecurity.client.render.door.DoorLeds;

/** The secure chest's plate and LED: unlinked grey and dark, lockdown strobe, flash first, steady without animation. */
class ChestLightsTest {

    private static final int LEVEL = 0x55D55A;
    /** A game time in the lockdown strobe's lit phase and one in its dark phase. */
    private static final long STROBE_ON = 0L;
    private static final long STROBE_OFF = Blink.LOCKDOWN_ON;
    /** A game time in the recording blink's dark phase. */
    private static final long REC_OFF = Blink.RECORDING_ON;

    @Test
    void plateIsOffGreyWhileUnlinkedAndTheLevelColourWhenLinked() {
        assertEquals(SecurityColours.OFF, ChestLights.plate(false, LEVEL, false));
        assertEquals(LEVEL, ChestLights.plate(true, LEVEL, false));
        assertEquals(SecurityColours.LOCK, ChestLights.plate(true, LEVEL, true), "deny flash lit phase");
        assertEquals(SecurityColours.LOCK, ChestLights.plate(false, LEVEL, true), "the flash wins over unlinked");
    }

    @Test
    void unlinkedChestHasNoLed() {
        assertEquals(DoorLeds.OFF, ChestLights.led(false, false, false, false, false, STROBE_ON, true));
        assertEquals(DoorLeds.OFF, ChestLights.led(false, true, true, false, false, STROBE_ON, true),
                "stale lockdown / recording bits never light an unlinked chest");
        assertEquals(DoorLeds.OFF, ChestLights.led(false, true, true, false, false, STROBE_ON, false));
    }

    @Test
    void lockdownStrobesLockAndOutranksRecording() {
        assertEquals(SecurityColours.LOCK, ChestLights.led(true, true, true, false, false, STROBE_ON, true));
        assertEquals(DoorLeds.OFF, ChestLights.led(true, true, true, false, false, STROBE_OFF, true), "strobe dark phase");
        assertEquals(SecurityColours.LOCK, ChestLights.led(true, true, true, false, false, STROBE_OFF, false),
                "animations off: steady LOCK, no strobe");
    }

    @Test
    void recordingBlinksWhenLinkedAndNotInLockdown() {
        assertEquals(SecurityColours.REC, ChestLights.led(true, false, true, false, false, 0L, true));
        assertEquals(DoorLeds.OFF, ChestLights.led(true, false, true, false, false, REC_OFF, true));
        assertEquals(SecurityColours.REC, ChestLights.led(true, false, true, false, false, REC_OFF, false));
        assertEquals(DoorLeds.OFF, ChestLights.led(true, false, false, false, false, 0L, true), "not recording: dark");
    }

    @Test
    void denyFlashOutranksEverything() {
        assertEquals(SecurityColours.LOCK, ChestLights.led(true, false, true, true, true, REC_OFF, true));
        assertEquals(DoorLeds.OFF, ChestLights.led(true, true, true, true, false, STROBE_ON, true),
                "flash dark phase wins over the lockdown strobe");
    }
}
