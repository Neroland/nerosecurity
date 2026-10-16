package za.co.neroland.nerosecurity.client.render.door;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.client.SecurityColours;

/** The four-blink grammar on the door LEDs, and the still frame with animations off. */
class DoorLedsTest {

    private static final int LEVEL = 0x55D55A;

    @Test
    void steadyLevelColourWhenHealthy() {
        for (long t = 0; t < 40; t++) {
            assertEquals(LEVEL, DoorLeds.status(LEVEL, false, false, false, false, t, true));
        }
    }

    @Test
    void warnBlinksAtTwoHertzWhenUnpowered() {
        assertEquals(SecurityColours.WARN, DoorLeds.status(LEVEL, false, true, false, false, 0, true));
        assertEquals(DoorLeds.OFF, DoorLeds.status(LEVEL, false, true, false, false, 5, true));
        assertEquals(SecurityColours.WARN, DoorLeds.status(LEVEL, false, true, false, false, 10, true));
    }

    @Test
    void sealedStrobesLockAndOutranksPower() {
        assertEquals(SecurityColours.LOCK, DoorLeds.status(LEVEL, true, true, false, false, 0, true));
        assertEquals(DoorLeds.OFF, DoorLeds.status(LEVEL, true, true, false, false, 3, true));
    }

    @Test
    void denyFlashOutranksEverything() {
        assertEquals(SecurityColours.LOCK, DoorLeds.status(LEVEL, false, false, true, true, 7, true));
        assertEquals(DoorLeds.OFF, DoorLeds.status(LEVEL, true, true, true, false, 0, true));
    }

    @Test
    void recordingBlinksOneHertz() {
        assertEquals(SecurityColours.REC, DoorLeds.recording(true, 14, true));
        assertEquals(DoorLeds.OFF, DoorLeds.recording(true, 15, true));
        assertEquals(DoorLeds.OFF, DoorLeds.recording(false, 0, true));
    }

    @Test
    void stillFrameHoldsEveryBlinkOn() {
        assertEquals(SecurityColours.WARN, DoorLeds.status(LEVEL, false, true, false, false, 5, false));
        assertEquals(SecurityColours.LOCK, DoorLeds.status(LEVEL, true, false, false, false, 4, false));
        assertEquals(SecurityColours.REC, DoorLeds.recording(true, 19, false));
        assertEquals(SecurityColours.LOCK, DoorLeds.lockdown(true, 4, false));
        assertEquals(DoorLeds.OFF, DoorLeds.lockdown(false, 0, false));
    }
}
