package za.co.neroland.nerosecurity.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/** Every event type has a fixed phrase with no placeholder, so no actor can ever be rendered into one. */
class EventPhrasesTest {

    @Test
    void everyTypeHasAPlainPhrase() throws IOException {
        JsonObject lang = JsonParser.parseString(Files.readString(lang(), StandardCharsets.UTF_8)).getAsJsonObject();
        for (SecurityEventType type : SecurityEventType.values()) {
            String key = EventPhrases.key(type);
            assertTrue(key.startsWith("event.nerosecurity.type."), key);
            assertTrue(lang.has(key), "missing phrase for " + type);
            String phrase = lang.get(key).getAsString();
            assertFalse(phrase.isBlank(), key);
            assertFalse(phrase.contains("%"), key + " must take no arguments");
        }
        assertEquals("event.nerosecurity.type.laser_tripped", EventPhrases.key(SecurityEventType.LASER_TRIPPED));
    }

    @Test
    void stageThreeSeverities() {
        assertEquals(SecuritySeverity.CRITICAL, SecurityEventType.LASER_TRIPPED.defaultSeverity);
        assertEquals(SecuritySeverity.CRITICAL, SecurityEventType.DEVICE_FORCED.defaultSeverity);
        assertEquals(SecuritySeverity.WARN, SecurityEventType.MOTION_DETECTED.defaultSeverity);
        assertEquals(SecuritySeverity.WARN, SecurityEventType.ALARM_TRIGGERED.defaultSeverity);
        assertEquals(SecuritySeverity.WARN, SecurityEventType.ALARM_LOCKDOWN_SUPPRESSED.defaultSeverity);
        assertEquals(SecuritySeverity.INFO, SecurityEventType.CAMERA_MEMBER_SEEN.defaultSeverity);
        assertEquals(SecuritySeverity.INFO, SecurityEventType.LOG_READ.defaultSeverity);
        assertEquals(SecuritySeverity.INFO, SecurityEventType.AUDIT_PURGED.defaultSeverity);
    }

    private static Path lang() {
        Path relative = Path.of("common", "src", "main", "resources", "assets", "nerosecurity", "lang", "en_us.json");
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return fail("could not locate " + relative);
    }
}
