package za.co.neroland.nerosecurity.menu.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.audit.ActorKind;
import za.co.neroland.nerosecurity.audit.AnonymousEvent;
import za.co.neroland.nerosecurity.audit.AuditEntry;
import za.co.neroland.nerosecurity.block.DeviceIds;
import za.co.neroland.nerosecurity.client.screen.monitor.MonitorText;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Lockdown;
import za.co.neroland.nerosecurity.installation.LockdownSource;
import za.co.neroland.nerosecurity.menu.monitor.MonitorSnapshot.DeviceKind;
import za.co.neroland.nerosecurity.menu.monitor.MonitorSnapshot.DeviceRow;
import za.co.neroland.nerosecurity.menu.monitor.MonitorSnapshot.DeviceState;

/** The monitor snapshot: no identity, bounded size, newest first, device filtering. Plain JVM. */
class MonitorSnapshotsTest {

    private static final Identifier OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld");
    private static final Identifier NETHER = Identifier.fromNamespaceAndPath("minecraft", "the_nether");
    private static final DevicePos POS = new DevicePos(OVERWORLD, new BlockPos(1, 64, 2));
    private static final long NOW = 1_000_000_000L;

    private static AuditEntry entry(long at, UUID actor, boolean withheld) {
        SecurityEventType type = SecurityEventType.LASER_TRIPPED;
        return new AuditEntry(at, type, actor, withheld, POS, type.defaultSeverity);
    }

    private static MonitorSnapshot snapshot(List<AnonymousEvent> events) {
        return MonitorSnapshots.assemble(BlockPos.ZERO, "Lab", Lockdown.NONE, true, NOW, true, true,
                List.of(new DeviceRow(new BlockPos(3, 4, 5), DeviceKind.LASER, DeviceState.ARMED)), events, 50);
    }

    /** Monitor rows carry no identity: no UUID string, withheld maps to WITHHELD, null to NONE. */
    @Test
    void monitorRowsNeverExposeIdentity() {
        UUID player = UUID.randomUUID();
        UUID optedOut = UUID.randomUUID();
        List<AuditEntry> entries = List.of(
                entry(NOW - 1_000L, player, false),
                entry(NOW - 2_000L, optedOut, true),
                entry(NOW - 3_000L, null, false));
        List<AnonymousEvent> rows = MonitorSnapshots.rows(entries, NOW, 10);
        assertEquals(List.of(ActorKind.PLAYER, ActorKind.WITHHELD, ActorKind.NONE),
                rows.stream().map(AnonymousEvent::actorKind).toList());
        String text = String.valueOf(rows) + snapshot(rows);
        assertFalse(text.contains(player.toString()), "no actor UUID in rows");
        assertFalse(text.contains(optedOut.toString()), "no withheld UUID in rows");
        assertEquals(new BlockPos(1, 64, 2), rows.get(0).devicePos(), "only the device position travels");
    }

    @Test
    void snapshotShapeHasNoIdentityFields() {
        for (Class<?> type : List.of(MonitorSnapshot.class, DeviceRow.class, AnonymousEvent.class)) {
            for (RecordComponent component : type.getRecordComponents()) {
                Class<?> t = component.getType();
                assertFalse(UUID.class.equals(t), type.getSimpleName() + "." + component.getName() + " is a UUID");
                assertFalse(t.getName().toLowerCase().contains("profile"), component.getName() + " is a profile");
                if (String.class.equals(t)) {
                    assertEquals("installationName", component.getName(), "the only text is the installation name");
                }
                assertFalse(component.getName().toLowerCase().contains("actor") && !ActorKind.class.equals(t),
                        "actor information is a kind only");
                assertFalse(component.getName().toLowerCase().contains("name") && !"installationName".equals(component.getName()),
                        "no names");
            }
        }
        Set<String> eventFields = Set.of("type", "severity", "agoSeconds", "devicePos", "actorKind");
        for (RecordComponent component : AnonymousEvent.class.getRecordComponents()) {
            assertTrue(eventFields.contains(component.getName()), "unexpected event field " + component.getName());
        }
    }

    @Test
    void rowsAreCappedAndNewestFirst() {
        List<AuditEntry> entries = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            entries.add(entry(NOW - i * 61_000L, null, false));
        }
        Collections.shuffle(entries, new Random(7));
        List<AnonymousEvent> ten = MonitorSnapshots.rows(entries, NOW, 10);
        assertEquals(10, ten.size());
        for (int i = 0; i < ten.size(); i++) {
            assertEquals(i * 61, ten.get(i).agoSeconds(), "newest first, no gaps");
        }
        assertEquals(1, MonitorSnapshots.rows(entries, NOW, 0).size(), "limit clamps up to 1");
        assertEquals(MonitorSnapshot.MAX_EVENTS, MonitorSnapshots.rows(entries, NOW, 999).size(), "limit clamps to 50");

        List<AnonymousEvent> all = MonitorSnapshots.rows(entries, NOW, 50);
        assertEquals(5, MonitorSnapshots.assemble(BlockPos.ZERO, "Lab", Lockdown.NONE, true, NOW, true, true,
                List.of(), all, 5).events().size(), "monitorEventCount caps the snapshot");
        assertTrue(MonitorSnapshots.assemble(BlockPos.ZERO, "Lab", Lockdown.NONE, true, NOW, true, false,
                List.of(), all, 10).events().isEmpty(), "audit disabled: no rows at all");
        assertEquals(MonitorSnapshot.MAX_EVENTS, new MonitorSnapshot(BlockPos.ZERO, "Lab", false, null, 0, 0, false,
                false, List.of(), AnonymousEvent.of(new ArrayList<>(entries), NOW)).events().size(),
                "the record itself never holds more than MAX_EVENTS");
    }

    @Test
    void lockdownFieldsAreRelativeSeconds() {
        Lockdown alarm = Lockdown.alarm(NOW - 30_000L, NOW + 90_500L);
        MonitorSnapshot snap = MonitorSnapshots.assemble(BlockPos.ZERO, "Lab", alarm, true, NOW, true, true,
                List.of(), List.of(), 10);
        assertTrue(snap.lockdown());
        assertEquals(LockdownSource.ALARM, snap.source());
        assertEquals(30, snap.lockdownSinceSeconds());
        assertEquals(91, snap.autoReleaseInSeconds(), "rounded up");

        MonitorSnapshot manual = MonitorSnapshots.assemble(BlockPos.ZERO, "Lab", Lockdown.manual(NOW - 5_000L, true),
                true, NOW, true, true, List.of(), List.of(), 10);
        assertEquals(LockdownSource.MANUAL, manual.source());
        assertEquals(0, manual.autoReleaseInSeconds());

        MonitorSnapshot off = MonitorSnapshots.assemble(BlockPos.ZERO, "Lab", alarm, false, NOW, true, true,
                List.of(), List.of(), 10);
        assertFalse(off.lockdown(), "lockdown disabled on the server: not shown as in force");
        assertEquals(0, off.lockdownSinceSeconds());
    }

    @Test
    void deviceRowsKeepOnlyLocalCamerasAndLasers() {
        Map<DevicePos, Identifier> devices = new LinkedHashMap<>();
        devices.put(new DevicePos(OVERWORLD, new BlockPos(9, 64, 0)), DeviceIds.LASER_GRID_NODE);
        devices.put(new DevicePos(OVERWORLD, new BlockPos(1, 64, 0)), DeviceIds.LASER_GRID_NODE);
        devices.put(new DevicePos(OVERWORLD, new BlockPos(5, 70, 0)), DeviceIds.SECURITY_CAMERA);
        devices.put(new DevicePos(OVERWORLD, new BlockPos(0, 64, 0)), DeviceIds.ALARM);
        devices.put(new DevicePos(OVERWORLD, new BlockPos(2, 64, 0)), DeviceIds.SECURITY_MONITOR);
        devices.put(new DevicePos(OVERWORLD, new BlockPos(3, 64, 0)),
                Identifier.fromNamespaceAndPath("nerosecurity", "security_door"));
        devices.put(new DevicePos(NETHER, new BlockPos(4, 64, 0)), DeviceIds.LASER_GRID_NODE);
        List<BlockPos> probed = new ArrayList<>();

        List<DeviceRow> rows = MonitorSnapshots.deviceRows(devices, OVERWORLD, true, (pos, kind) -> {
            probed.add(pos);
            return DeviceState.ARMED;
        });
        assertEquals(List.of(DeviceKind.CAMERA, DeviceKind.LASER, DeviceKind.LASER),
                rows.stream().map(DeviceRow::kind).toList(), "cameras first; alarm, monitor, door, other dimension left out");
        assertEquals(new BlockPos(1, 64, 0), rows.get(1).pos(), "each kind ordered by position");
        assertEquals(3, probed.size(), "only kept rows are probed");

        probed.clear();
        List<DeviceRow> noCameras = MonitorSnapshots.deviceRows(devices, OVERWORLD, false, (pos, kind) -> {
            probed.add(pos);
            return null;
        });
        assertEquals(2, noCameras.size(), "cameras omitted when disabled");
        assertTrue(noCameras.stream().allMatch(r -> r.state() == DeviceState.UNLOADED), "a null probe reads as unloaded");

        Map<DevicePos, Identifier> many = new LinkedHashMap<>();
        for (int i = 0; i < MonitorSnapshot.MAX_DEVICES + 20; i++) {
            many.put(new DevicePos(OVERWORLD, new BlockPos(i, 64, 0)), DeviceIds.LASER_GRID_NODE);
        }
        assertEquals(MonitorSnapshot.MAX_DEVICES,
                MonitorSnapshots.deviceRows(many, OVERWORLD, true, (p, k) -> DeviceState.ARMED).size());
    }

    @Test
    void stateWords() {
        assertEquals(DeviceState.ARMED, MonitorSnapshots.state(DeviceKind.LASER, true, true));
        assertEquals(DeviceState.UNPAIRED, MonitorSnapshots.state(DeviceKind.LASER, false, true));
        assertEquals(DeviceState.UNPOWERED, MonitorSnapshots.state(DeviceKind.LASER, false, false));
        assertEquals(DeviceState.UNPOWERED, MonitorSnapshots.state(DeviceKind.CAMERA, false, true));
        assertEquals(DeviceKind.CAMERA, MonitorSnapshots.kindOf(DeviceIds.SECURITY_CAMERA));
        assertEquals(null, MonitorSnapshots.kindOf(DeviceIds.ALARM));
    }

    @Test
    void streamCodecRoundTripsAndRefusesOversizedLists() {
        UUID player = UUID.randomUUID();
        MonitorSnapshot snap = snapshot(MonitorSnapshots.rows(List.of(entry(NOW - 120_000L, player, false),
                entry(NOW - 1_000L, null, false)), NOW, 10));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        MonitorSnapshot.STREAM_CODEC.encode(buf, snap);
        byte[] wire = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), wire);
        assertFalse(new String(wire, java.nio.charset.StandardCharsets.ISO_8859_1).contains(player.toString()));
        assertEquals(snap, MonitorSnapshot.STREAM_CODEC.decode(buf));

        FriendlyByteBuf forged = new FriendlyByteBuf(Unpooled.buffer());
        forged.writeBlockPos(BlockPos.ZERO);
        forged.writeUtf("x");
        forged.writeBoolean(false);
        forged.writeVarInt(0);
        forged.writeVarInt(0);
        forged.writeVarInt(0);
        forged.writeBoolean(false);
        forged.writeBoolean(false);
        forged.writeVarInt(MonitorSnapshot.MAX_DEVICES + 1);
        assertThrows(RuntimeException.class, () -> MonitorSnapshot.STREAM_CODEC.decode(forged));
    }

    @Test
    void agesReadAsWholeUnits() {
        assertEquals(new MonitorText.Age("under_minute", 0), MonitorText.age(59));
        assertEquals(new MonitorText.Age("minute", 1), MonitorText.age(60));
        assertEquals(new MonitorText.Age("minutes", 5), MonitorText.age(5 * 60 + 59));
        assertEquals(new MonitorText.Age("hour", 1), MonitorText.age(3600));
        assertEquals(new MonitorText.Age("hours", 23), MonitorText.age(86_399));
        assertEquals(new MonitorText.Age("days", 3), MonitorText.age(3 * 86_400));
        assertEquals(new MonitorText.Age("under_minute", 0), MonitorText.age(-5));
    }
}
