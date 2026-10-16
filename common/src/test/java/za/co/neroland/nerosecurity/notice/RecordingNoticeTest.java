package za.co.neroland.nerosecurity.notice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.block.DeviceIds;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;

/** The monitored-area boxes and when an installation counts as recording. Plain JVM. */
class RecordingNoticeTest {

    private static final Identifier OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld");
    private static final Identifier NETHER = Identifier.fromNamespaceAndPath("minecraft", "the_nether");

    @Test
    void areaEnclosesHubAndDevicesPerDimensionWithMargin() {
        SecurityInstallations store = new SecurityInstallations();
        Installation installation = store.create(UUID.randomUUID(), "A", new DevicePos(OVERWORLD, new BlockPos(0, 64, 0)),
                0L, 8).installation();
        store.link(installation.id(), new DevicePos(OVERWORLD, new BlockPos(10, 70, -5)), DeviceIds.SECURITY_CAMERA, 256);
        store.link(installation.id(), new DevicePos(NETHER, new BlockPos(1, 30, 1)), DeviceIds.LASER_GRID_NODE, 256);

        List<InstallationAreas.Area> areas = InstallationAreas.areasFor(installation, 16);
        assertEquals(2, areas.size(), "one box per dimension");
        InstallationAreas.Area overworld = areas.stream().filter(a -> a.dimension().equals(OVERWORLD)).findFirst().orElseThrow();
        assertTrue(overworld.box().contains(new Vec3(5, 66, -2)), "inside the device box");
        assertTrue(overworld.box().contains(new Vec3(-15.5, 64, 0)), "inside the margin");
        assertFalse(overworld.box().contains(new Vec3(-17, 64, 0)), "beyond the margin");
        assertFalse(overworld.box().contains(new Vec3(27.5, 64, 0)));
        assertTrue(overworld.box().contains(new Vec3(26.5, 64, 0)));
    }

    @Test
    void noHubNoDevicesNoArea() {
        SecurityInstallations store = new SecurityInstallations();
        Installation installation = store.create(UUID.randomUUID(), "A", new DevicePos(OVERWORLD, BlockPos.ZERO), 0L, 8)
                .installation();
        store.orphan(installation.id());
        assertTrue(InstallationAreas.areasFor(installation, 16).isEmpty());
    }

    @Test
    void recordsOnlyWhileSomethingLogs() {
        SecurityInstallations store = new SecurityInstallations();
        Installation plain = store.create(UUID.randomUUID(), "A", new DevicePos(OVERWORLD, BlockPos.ZERO), 0L, 8)
                .installation();
        assertTrue(RecordingNotice.records(plain, true, true), "the audit log records every event");
        assertTrue(RecordingNotice.records(plain, true, false));
        assertFalse(RecordingNotice.records(plain, false, true), "no sensor and no log: nothing records");
        store.link(plain.id(), new DevicePos(OVERWORLD, new BlockPos(2, 64, 0)), DeviceIds.LASER_GRID_NODE, 256);
        assertTrue(RecordingNotice.records(plain, false, true), "a sensor senses presence");
        assertFalse(RecordingNotice.records(plain, false, false), "both logging features off: nothing records");
    }
}
