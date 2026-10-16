package za.co.neroland.nerosecurity.notice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.block.DeviceIds;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;

/** The recording notice reaches everyone a sensor can log. Plain JVM. */
class NoticeCoverageTest {

    private static final Identifier OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld");

    @AfterEach
    void forget() {
        RecordingNotice.resetForTests();
    }

    @Test
    void marginCoversTheWholeSensingEnvelope() {
        assertEquals(32, InstallationAreas.margin(16, 32, 32), "defaults: the sensors' maximum range wins");
        assertEquals(64, InstallationAreas.margin(16, 64, 8));
        assertEquals(48, InstallationAreas.margin(16, 4, 48));
        assertEquals(40, InstallationAreas.margin(40, 32, 32), "a larger notice margin is kept");

        SecurityInstallations store = new SecurityInstallations();
        Installation installation = store.create(UUID.randomUUID(), "A", new DevicePos(OVERWORLD, BlockPos.ZERO), 0L, 8)
                .installation();
        store.link(installation.id(), new DevicePos(OVERWORLD, new BlockPos(10, 64, 0)), DeviceIds.SECURITY_CAMERA, 256);
        // A camera with range modules sees up to cameraMaxRange (32) blocks: 30 blocks out from its lens.
        Vec3 seen = new Vec3(10.5 + 30.0, 64.5, 0.5);
        List<InstallationAreas.Area> narrow = InstallationAreas.areasFor(installation, 16);
        assertFalse(narrow.get(0).box().contains(seen), "the old 16-block margin missed this player");
        List<InstallationAreas.Area> wide = InstallationAreas.areasFor(installation, InstallationAreas.margin(16, 32, 32));
        assertTrue(wide.get(0).box().contains(seen), "the sensing-range margin covers them");
    }

    @Test
    void theNoticeIsDeliveredOncePerInstallationPerSession() {
        RecordingNotice.resetForTests();
        UUID player = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertTrue(RecordingNotice.markNoticed(player, first), "the first sensor to log them shows the notice");
        assertFalse(RecordingNotice.markNoticed(player, first), "never twice per session");
        assertTrue(RecordingNotice.markNoticed(player, second), "each installation has its own notice");
        assertEquals(1, RecordingNotice.rememberedPlayers());
    }
}
