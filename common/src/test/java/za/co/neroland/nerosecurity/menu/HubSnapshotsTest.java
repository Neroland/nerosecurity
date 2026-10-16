package za.co.neroland.nerosecurity.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.audit.ActorKind;
import za.co.neroland.nerosecurity.audit.AnonymousEvent;
import za.co.neroland.nerosecurity.block.chest.AutomationWhy;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.event.SecuritySeverity;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Lockdown;
import za.co.neroland.nerosecurity.installation.LockdownSource;
import za.co.neroland.nerosecurity.menu.DeviceStates.DeviceState;
import za.co.neroland.nerosecurity.menu.HubPayloads.DeviceRow;
import za.co.neroland.nerosecurity.menu.HubPayloads.HubSnapshot;
import za.co.neroland.nerosecurity.privacy.PrivacyActions;

/** Hub snapshot v2: device rows, lockdown timing, event cap, and no identity in the new row shapes. */
class HubSnapshotsTest {

    private static final long NOW = 2_000_000_000L;
    private static final Identifier OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld");
    private static final Identifier DOOR = Identifier.fromNamespaceAndPath("nerosecurity", "security_door");
    private static final Identifier ALARM = Identifier.fromNamespaceAndPath("nerosecurity", "alarm");

    @Test
    void deviceRowsAreSortedProbedAndNullMeansUnloaded() {
        Map<DevicePos, Identifier> devices = new LinkedHashMap<>();
        devices.put(new DevicePos(OVERWORLD, new BlockPos(5, 64, 0)), DOOR);
        devices.put(new DevicePos(OVERWORLD, new BlockPos(1, 64, 0)), DOOR);
        devices.put(new DevicePos(OVERWORLD, new BlockPos(9, 64, 0)), ALARM);
        List<DeviceRow> rows = HubSnapshots.deviceRows(devices,
                (at, block) -> ALARM.equals(block) ? DeviceState.ACTIVE : at.pos().getX() == 5 ? null : DeviceState.SEALED);
        assertEquals(3, rows.size());
        assertEquals(ALARM, rows.get(0).block(), "sorted by block id first");
        assertEquals(new BlockPos(1, 64, 0), rows.get(1).pos(), "then by position");
        assertEquals(DeviceState.SEALED, rows.get(1).state());
        assertEquals(DeviceState.UNLOADED, rows.get(2).state(), "a null probe answer reads as unloaded");
    }

    @Test
    void deviceRowsAreCapped() {
        Map<DevicePos, Identifier> devices = new LinkedHashMap<>();
        for (int i = 0; i < HubPayloads.MAX_DEVICES + 10; i++) {
            devices.put(new DevicePos(OVERWORLD, new BlockPos(i, 64, 0)), DOOR);
        }
        int[] probes = {0};
        List<DeviceRow> rows = HubSnapshots.deviceRows(devices, (at, block) -> {
            probes[0]++;
            return DeviceState.OK;
        });
        assertEquals(HubPayloads.MAX_DEVICES, rows.size());
        assertEquals(HubPayloads.MAX_DEVICES, probes[0], "rows that are dropped are never probed");
    }

    @Test
    void lockdownTimingIsRelativeAndOnlyWhileEnforced() {
        UUID id = UUID.randomUUID();
        Lockdown alarm = Lockdown.alarm(NOW - 90_000L, NOW + 30_500L);
        HubSnapshot on = HubSnapshots.assemble(id, "Base", alarm, true, NOW, List.of(), List.of(), AccessLevel.OFFICER,
                false, List.of(), List.of(), true, null);
        assertTrue(on.lockdown());
        assertEquals(LockdownSource.ALARM, on.source());
        assertEquals(90, on.lockdownSinceSeconds());
        assertEquals(31, on.autoReleaseInSeconds(), "rounded up");
        HubSnapshot disabled = HubSnapshots.assemble(id, "Base", alarm, false, NOW, List.of(), List.of(),
                AccessLevel.OFFICER, false, List.of(), List.of(), true, null);
        assertFalse(disabled.lockdown(), "lockdown disabled on the server: not shown as in force");
        assertEquals(0, disabled.lockdownSinceSeconds());
        assertFalse(disabled.lockdownEnabled());
        HubSnapshot none = HubSnapshots.assemble(id, "Base", Lockdown.NONE, true, NOW, List.of(), List.of(),
                AccessLevel.OWNER, true, List.of(), List.of(), false, null);
        assertFalse(none.lockdown());
        assertTrue(none.admin());
        assertFalse(none.ok());
        assertNull(none.message());
    }

    @Test
    void onlyTheLastFiveEventsTravel() {
        List<AnonymousEvent> events = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            events.add(new AnonymousEvent(SecurityEventType.DOOR_OPENED, SecuritySeverity.INFO, i, BlockPos.ZERO,
                    ActorKind.PLAYER));
        }
        HubSnapshot snap = HubSnapshots.assemble(UUID.randomUUID(), "Base", Lockdown.NONE, true, NOW, List.of(),
                List.of(), AccessLevel.OFFICER, false, List.of(), events, true, null);
        assertEquals(HubPayloads.MAX_EVENTS, snap.events().size());
        assertEquals(0, snap.events().get(0).agoSeconds(), "newest first, as given");
    }

    @Test
    void newRowShapesCarryNoIdentity() {
        for (Class<?> type : List.of(DeviceRow.class, AnonymousEvent.class)) {
            for (RecordComponent component : type.getRecordComponents()) {
                assertFalse(UUID.class.equals(component.getType()), type.getSimpleName() + "." + component.getName());
                assertFalse(String.class.equals(component.getType()), type.getSimpleName() + "." + component.getName());
                assertFalse(component.getName().toLowerCase().contains("name"), component.getName());
            }
        }
    }

    @Test
    void stageFiveLangKeysExist() throws IOException {
        JsonObject lang = JsonParser.parseString(Files.readString(
                resources().resolve("assets/nerosecurity/lang/en_us.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        for (DeviceState state : DeviceState.values()) {
            assertTrue(lang.has(state.translationKey()), state.translationKey());
        }
        for (AutomationWhy why : AutomationWhy.values()) {
            assertTrue(lang.has(why.translationKey()), why.translationKey());
        }
        assertTrue(lang.has(PrivacyActions.UNAVAILABLE_KEY));
        assertTrue(lang.has("config.nerosecurity.renderAnimationsEnabled"));
    }

    private static Path resources() {
        Path relative = Path.of("common", "src", "main", "resources");
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("resources not found");
    }
}
