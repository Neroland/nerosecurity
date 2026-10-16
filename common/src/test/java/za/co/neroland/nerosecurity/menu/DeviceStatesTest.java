package za.co.neroland.nerosecurity.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.field.FieldState;
import za.co.neroland.nerosecurity.menu.DeviceStates.DeviceState;

/** The hub's status word per device, as pure rules. */
class DeviceStatesTest {

    @Test
    void doorSealWinsOverPower() {
        assertEquals(DeviceState.SEALED, DeviceStates.door(true, true));
        assertEquals(DeviceState.UNPOWERED, DeviceStates.door(false, true));
        assertEquals(DeviceState.OK, DeviceStates.door(false, false));
    }

    @Test
    void sensorsMatchTheMonitorRule() {
        assertEquals(DeviceState.OK, DeviceStates.sensor(true, true, true));
        assertEquals(DeviceState.UNPAIRED, DeviceStates.sensor(true, false, true));
        assertEquals(DeviceState.UNPOWERED, DeviceStates.sensor(true, false, false));
        assertEquals(DeviceState.UNPOWERED, DeviceStates.sensor(false, false, true), "a camera is never unpaired");
        assertEquals(DeviceState.OK, DeviceStates.sensor(false, true, true));
    }

    @Test
    void alarmAndEmitter() {
        assertEquals(DeviceState.ACTIVE, DeviceStates.alarm(true));
        assertEquals(DeviceState.OK, DeviceStates.alarm(false));
        assertEquals(DeviceState.OK, DeviceStates.emitter(FieldState.PROJECTING));
        assertEquals(DeviceState.UNPOWERED, DeviceStates.emitter(FieldState.BROWNOUT));
        assertEquals(DeviceState.OVERLOADED, DeviceStates.emitter(FieldState.OVERLOADED));
        assertEquals(DeviceState.IDLE, DeviceStates.emitter(FieldState.IDLE));
        assertEquals(DeviceState.IDLE, DeviceStates.emitter(null));
    }

    @Test
    void everyStateHasItsLangKey() {
        for (DeviceState state : DeviceState.values()) {
            assertEquals("device.nerosecurity.state." + state.name().toLowerCase(java.util.Locale.ROOT),
                    state.translationKey());
        }
    }
}
