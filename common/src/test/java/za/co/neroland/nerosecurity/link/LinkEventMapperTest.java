package za.co.neroland.nerosecurity.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.link.LinkAlert;

import za.co.neroland.nerosecurity.event.SecurityEvent;
import za.co.neroland.nerosecurity.event.SecurityEventType;

class LinkEventMapperTest {

    private static final Map<SecurityEventType, LinkAlert.Severity> NOTIFIED = Map.ofEntries(
            Map.entry(SecurityEventType.LASER_TRIPPED, LinkAlert.Severity.CRITICAL),
            Map.entry(SecurityEventType.DOOR_FORCED, LinkAlert.Severity.CRITICAL),
            Map.entry(SecurityEventType.CHEST_FORCED, LinkAlert.Severity.CRITICAL),
            Map.entry(SecurityEventType.DEVICE_FORCED, LinkAlert.Severity.CRITICAL),
            Map.entry(SecurityEventType.FIELD_OVERLOADED, LinkAlert.Severity.CRITICAL),
            Map.entry(SecurityEventType.MOTION_DETECTED, LinkAlert.Severity.WARN),
            Map.entry(SecurityEventType.POWER_LOST, LinkAlert.Severity.WARN),
            Map.entry(SecurityEventType.FIELD_COLLAPSED, LinkAlert.Severity.WARN),
            Map.entry(SecurityEventType.LOCKDOWN_ENTERED, LinkAlert.Severity.CRITICAL),
            Map.entry(SecurityEventType.LOCKDOWN_EXITED, LinkAlert.Severity.INFO),
            Map.entry(SecurityEventType.LOCKDOWN_AUTO_RELEASED, LinkAlert.Severity.INFO),
            Map.entry(SecurityEventType.ADMIN_UNLOCK, LinkAlert.Severity.WARN));

    private static final Set<SecurityEventType> BYPASS = EnumSet.of(SecurityEventType.LOCKDOWN_ENTERED,
            SecurityEventType.LOCKDOWN_EXITED, SecurityEventType.LOCKDOWN_AUTO_RELEASED, SecurityEventType.ADMIN_UNLOCK);

    private static final Set<SecurityEventType> ROW_ONLY = EnumSet.of(SecurityEventType.LOCKDOWN_CONFIRMED,
            SecurityEventType.ALARM_TRIGGERED, SecurityEventType.ALARM_SILENCED,
            SecurityEventType.ALARM_LOCKDOWN_SUPPRESSED);

    private static final Set<SecurityEventType> DENIALS = EnumSet.of(SecurityEventType.DOOR_DENIED,
            SecurityEventType.CHEST_DENIED, SecurityEventType.KEYCARD_REJECTED);

    private static SecurityEvent event(SecurityEventType type, UUID installation) {
        return SecurityEvent.of(type, installation, null, UUID.randomUUID(), 0L);
    }

    @Test
    void theMappingTableExactly() {
        LinkEventMapper mapper = new LinkEventMapper(() -> 1000, () -> 60_000L);
        UUID installation = UUID.randomUUID();
        for (SecurityEventType type : SecurityEventType.values()) {
            Optional<LinkEventMapper.Mapped> mapped = mapper.map(event(type, installation), 0L);
            if (NOTIFIED.containsKey(type)) {
                assertTrue(mapped.isPresent(), type.name());
                PushKind push = mapped.get().push();
                assertEquals(NOTIFIED.get(type), push.severity(), type.name());
                assertEquals(BYPASS.contains(type), push.bypassesCooldown(), type.name());
            } else if (ROW_ONLY.contains(type) || DENIALS.contains(type)) {
                assertTrue(mapped.isPresent(), type.name());
                assertNull(mapped.get().push(), type.name() + " is a live row only");
            } else {
                assertTrue(mapped.isEmpty(), type.name() + " must never reach the link bus");
            }
        }
        for (SecurityEventType never : EnumSet.of(SecurityEventType.CAMERA_MEMBER_SEEN, SecurityEventType.DOOR_OPENED,
                SecurityEventType.CHEST_OPENED, SecurityEventType.CHEST_CHANGED, SecurityEventType.LOG_READ,
                SecurityEventType.AUDIT_PURGED, SecurityEventType.KEYCARD_WRITTEN, SecurityEventType.KEYCARD_CLONED,
                SecurityEventType.KEYCARD_WIPED)) {
            assertFalse(LinkEventMapper.reachesLink(never), never.name());
        }
    }

    @Test
    void repeatedDeniedFiresOnceAtTheThresholdPerWindow() {
        LinkEventMapper mapper = new LinkEventMapper(() -> 3, () -> 60_000L);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertNull(mapper.map(event(SecurityEventType.DOOR_DENIED, a), 0L).get().push());
        assertNull(mapper.map(event(SecurityEventType.CHEST_DENIED, a), 1_000L).get().push());
        assertNull(mapper.map(event(SecurityEventType.DOOR_DENIED, b), 1_500L).get().push(), "per installation");
        assertEquals(PushKind.REPEATED_DENIED, mapper.map(event(SecurityEventType.KEYCARD_REJECTED, a), 2_000L).get().push(),
                "all three denial types count into one window");
        assertNull(mapper.map(event(SecurityEventType.DOOR_DENIED, a), 3_000L).get().push(), "once per window");
        assertNull(mapper.map(event(SecurityEventType.DOOR_DENIED, a), 61_000L).get().push(), "new window starts at 1");
        assertNull(mapper.map(event(SecurityEventType.DOOR_DENIED, a), 62_000L).get().push());
        assertEquals(PushKind.REPEATED_DENIED, mapper.map(event(SecurityEventType.DOOR_DENIED, a), 63_000L).get().push());
    }

    @Test
    void spreadOutDenialsNeverNotify() {
        LinkEventMapper mapper = new LinkEventMapper(() -> 3, () -> 60_000L);
        UUID a = UUID.randomUUID();
        for (int i = 0; i < 10; i++) {
            assertNull(mapper.map(event(SecurityEventType.DOOR_DENIED, a), i * 30_000L).get().push());
        }
    }
}
