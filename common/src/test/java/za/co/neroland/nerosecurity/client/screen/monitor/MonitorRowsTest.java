package za.co.neroland.nerosecurity.client.screen.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.client.screen.monitor.MonitorRows.Kind;
import za.co.neroland.nerosecurity.client.screen.monitor.MonitorRows.Row;
import za.co.neroland.nerosecurity.client.widget.Status;
import za.co.neroland.nerosecurity.installation.LockdownSource;
import za.co.neroland.nerosecurity.menu.monitor.MonitorSnapshot;
import za.co.neroland.nerosecurity.menu.monitor.MonitorSnapshot.DeviceKind;
import za.co.neroland.nerosecurity.menu.monitor.MonitorSnapshot.DeviceRow;
import za.co.neroland.nerosecurity.menu.monitor.MonitorSnapshot.DeviceState;

/** The monitor's SENSORS column: cameras then lasers under their headings, text only, shapes per state. */
class MonitorRowsTest {

    private static final DeviceRow CAM = new DeviceRow(new BlockPos(10, 67, -1), DeviceKind.CAMERA, DeviceState.ARMED);
    private static final DeviceRow LASER = new DeviceRow(new BlockPos(15, 65, -3), DeviceKind.LASER, DeviceState.UNPAIRED);

    private static MonitorSnapshot snapshot(boolean camerasDisabled, List<DeviceRow> devices) {
        return new MonitorSnapshot(BlockPos.ZERO, "Lab", false, LockdownSource.MANUAL, 0, 0, camerasDisabled, false,
                devices, List.of(), true, null);
    }

    private static List<Kind> kinds(List<Row> rows) {
        return rows.stream().map(Row::kind).toList();
    }

    @Test
    void camerasComeFirstUnderTheirHeadingThenLasers() {
        List<Row> rows = MonitorRows.of(snapshot(false, List.of(LASER, CAM)));
        assertEquals(List.of(Kind.HEADING, Kind.DEVICE, Kind.HEADING, Kind.DEVICE), kinds(rows));
        assertEquals(MonitorRows.CAMERAS, rows.get(0).key());
        assertSame(CAM, rows.get(1).device());
        assertEquals(MonitorRows.LASERS, rows.get(2).key());
        assertSame(LASER, rows.get(3).device());
        assertEquals(2, MonitorRows.sensorCount(snapshot(false, List.of(LASER, CAM))));
    }

    @Test
    void anEmptyGroupShowsADash() {
        List<Row> rows = MonitorRows.of(snapshot(false, List.of()));
        assertEquals(List.of(Kind.HEADING, Kind.NOTE, Kind.HEADING, Kind.NOTE), kinds(rows));
        assertEquals(MonitorRows.NONE, rows.get(1).key());
        assertEquals(MonitorRows.NONE, rows.get(3).key());
    }

    @Test
    void disabledCamerasAreOneWarningLineAndNoCameraRows() {
        List<Row> rows = MonitorRows.of(snapshot(true, List.of(CAM, LASER)));
        assertEquals(List.of(Kind.HEADING, Kind.WARNING, Kind.HEADING, Kind.DEVICE), kinds(rows));
        assertEquals(MonitorRows.CAMERAS_DISABLED, rows.get(1).key());
        assertSame(LASER, rows.get(3).device());
    }

    @Test
    void everyRowIsTextOnly() {
        for (Row row : MonitorRows.of(snapshot(false, List.of(CAM, LASER)))) {
            if (row.kind() == Kind.DEVICE) {
                assertNull(row.key());
            } else {
                assertNull(row.device());
                assertEquals(true, row.key().startsWith("monitor.nerosecurity."));
            }
        }
    }

    /** Each sensor state has its own glyph shape, so colour is never the only signal. */
    @Test
    void statesMapToDistinctShapes() {
        assertEquals(Status.OK, MonitorRows.status(DeviceState.ARMED));
        assertEquals(Status.WARN, MonitorRows.status(DeviceState.UNPOWERED));
        assertEquals(Status.WARN, MonitorRows.status(DeviceState.UNPAIRED));
        assertEquals(Status.OFF, MonitorRows.status(DeviceState.UNLOADED));
        assertEquals(Status.OFF, MonitorRows.status(null));
        assertNotEquals(MonitorRows.status(DeviceState.ARMED).shape(), MonitorRows.status(DeviceState.UNPOWERED).shape());
        assertNotEquals(MonitorRows.status(DeviceState.ARMED).shape(), MonitorRows.status(DeviceState.UNLOADED).shape());
        assertNotEquals(MonitorRows.status(DeviceState.UNPOWERED).shape(), MonitorRows.status(DeviceState.UNLOADED).shape());
        assertEquals(Status.BAD, MonitorRows.installationStatus(true));
        assertEquals(Status.OK, MonitorRows.installationStatus(false));
        assertNotEquals(Status.BAD.shape(), Status.OK.shape());
    }

    @Test
    void blockKeysNameTheSensorKinds() {
        assertEquals("block.nerosecurity.security_camera", MonitorRows.blockKey(DeviceKind.CAMERA));
        assertEquals("block.nerosecurity.laser_grid_node", MonitorRows.blockKey(DeviceKind.LASER));
    }
}
