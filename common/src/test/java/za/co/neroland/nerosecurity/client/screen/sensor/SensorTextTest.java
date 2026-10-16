package za.co.neroland.nerosecurity.client.screen.sensor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.client.widget.Status;
import za.co.neroland.nerosecurity.sensor.SensorSettings;

/** The sensor screen shows exactly one status sentence, in a fixed order of precedence. */
class SensorTextTest {

    @Test
    void titleFollowsTheDeviceKind() {
        assertEquals("sensor.nerosecurity.title.laser", SensorText.titleKey(true));
        assertEquals("sensor.nerosecurity.title.camera", SensorText.titleKey(false));
    }

    @Test
    void statusPrecedence() {
        // linked, armed, laser, paired, powered
        assertEquals(new SensorText.Row(Status.OFF, "sensor.nerosecurity.disarmed.unlinked", false),
                SensorText.state(false, true, true, true, true));
        SensorText.Row armed = SensorText.state(true, true, false, false, true);
        assertEquals(new SensorText.Row(Status.OK, "sensor.nerosecurity.armed", true), armed);
        assertTrue(armed.recording());
        assertEquals(new SensorText.Row(Status.WARN, "sensor.nerosecurity.unpaired", false),
                SensorText.state(true, false, true, false, true));
        assertEquals(new SensorText.Row(Status.WARN, "sensor.nerosecurity.disarmed.power", false),
                SensorText.state(true, false, true, true, false));
        assertEquals(new SensorText.Row(Status.WARN, "sensor.nerosecurity.disarmed.power", false),
                SensorText.state(true, false, false, false, false));
        SensorText.Row disarmed = SensorText.state(true, false, false, false, true);
        assertEquals(new SensorText.Row(Status.WARN, "sensor.nerosecurity.disarmed", false), disarmed);
        assertFalse(disarmed.recording());
    }

    @Test
    void fovIndexMatchesTheOptionsTable() {
        assertEquals(0, SensorText.indexOf(SensorSettings.FOV_OPTIONS, 60));
        assertEquals(1, SensorText.indexOf(SensorSettings.FOV_OPTIONS, 90));
        assertEquals(2, SensorText.indexOf(SensorSettings.FOV_OPTIONS, 120));
        assertEquals(-1, SensorText.indexOf(SensorSettings.FOV_OPTIONS, 45));
    }

    @Test
    void unlinkedSensorsRefuseSettingsAndOwnerSensorsNeedAnOwner() {
        assertEquals("device.nerosecurity.unlinked", SensorText.editWhy(false, AccessLevel.MEMBER, true));
        assertEquals("door.nerosecurity.owner_only_setting", SensorText.editWhy(true, AccessLevel.OWNER, false));
        assertNull(SensorText.editWhy(true, AccessLevel.OWNER, true));
        assertNull(SensorText.editWhy(true, AccessLevel.MEMBER, false));
    }
}
