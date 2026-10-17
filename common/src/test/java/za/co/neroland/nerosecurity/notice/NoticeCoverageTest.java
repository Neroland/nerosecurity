package za.co.neroland.nerosecurity.notice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

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

    /** The device-side overload never throws and remembers nothing when it has no one to tell. */
    @Test
    void theDeviceNoticeIsSafeWithNoServerOrNoActor() {
        UUID player = UUID.randomUUID();
        SecurityInstallations store = new SecurityInstallations();
        Installation installation = store.create(UUID.randomUUID(), "A", new DevicePos(OVERWORLD, BlockPos.ZERO), 0L, 8)
                .installation();
        RecordingNotice.noticeBeforeLogging(null, player, installation);
        RecordingNotice.noticeBeforeLogging(null, null, installation);
        RecordingNotice.noticeBeforeLogging(null, player, null);
        assertFalse(RecordingNotice.holdsAnyFor(player), "nobody was told, so nobody is remembered");
    }

    /**
     * C6-4: every device that publishes an event naming a player (door, chest, field, alarm, camera, laser, monitor,
     * and the keycard rejection) delivers the notice first. Checked at the source: a block entity that publishes on
     * the bus must also call {@code noticeBeforeLogging}, so a new publish path cannot silently skip it.
     */
    @Test
    void everyDevicePublishSiteDeliversTheNoticeFirst() throws IOException {
        Path root = sources().resolve(Path.of("za", "co", "neroland", "nerosecurity"));
        List<Path> publishers = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root.resolve("block"))) {
            for (Path file : files.filter(f -> f.toString().endsWith("BlockEntity.java")).toList()) {
                if (Files.readString(file, StandardCharsets.UTF_8).contains("SecurityEventBus.publish(")) {
                    publishers.add(file);
                }
            }
        }
        publishers.add(root.resolve(Path.of("access", "KeycardSource.java")));
        assertTrue(publishers.size() >= 8, "door, chest, field, alarm, camera, laser, monitor, keycard: " + publishers);
        for (Path file : publishers) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            int notice = text.indexOf("RecordingNotice.noticeBeforeLogging(");
            assertTrue(notice >= 0, file.getFileName() + " publishes a named event without the notice");
            assertTrue(notice < text.lastIndexOf("SecurityEventBus.publish("),
                    file.getFileName() + " must deliver the notice before it publishes");
        }
    }

    private static Path sources() {
        Path relative = Path.of("common", "src", "main", "java");
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("common/src/main/java not found above " + System.getProperty("user.dir"));
    }
}
