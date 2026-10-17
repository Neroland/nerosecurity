package za.co.neroland.nerosecurity.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** What exception text may leave in a telemetry report. Plain JVM; never touches the Sentry SDK. */
class TelemetryScrubTest {

    @Test
    void aHandledExceptionSendsItsClassNameOnlyNeverItsMessage() {
        UUID id = UUID.randomUUID();
        assertNull(NeroSecurityTelemetry.exceptionValue("cannot write world/nerosecurity/exports/" + id + ".json", true),
                "a handled exception's message never travels");
        assertNull(NeroSecurityTelemetry.exceptionValue(null, false));
    }

    @Test
    void aCrashMessageIsScrubbedOfUuidsAndOsAccountPaths() {
        UUID id = UUID.randomUUID();
        String value = NeroSecurityTelemetry.exceptionValue(
                "failed for " + id + " / " + id.toString().replace("-", "") + " at C:\\Users\\someone\\x", false);
        assertFalse(value.contains(id.toString()), value);
        assertFalse(value.contains(id.toString().replace("-", "")), value);
        assertFalse(value.contains("someone"), value);
        assertTrue(value.contains("[uuid]"), value);
        assertEquals("plain text stays", NeroSecurityTelemetry.scrub("plain text stays"));
    }
}
