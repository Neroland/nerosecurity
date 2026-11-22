package za.co.neroland.nerosecurity.privacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.audit.SecurityAuditLog;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;

/** The document NeroSecurity hands Core's {@code /neroland data export}. Plain JVM. */
class CoreExportTest {

    private static final long NOW = 1_800_000_000_000L;
    private static final Identifier OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld");

    @Test
    void nothingStoredMeansNull() {
        assertNull(PrivacyExport.buildForCore(UUID.randomUUID(), new SecurityInstallations(), new SecurityAuditLog(),
                false, NOW));
    }

    @Test
    void anOptOutAloneIsStillTheirData() {
        assertNotNull(PrivacyExport.buildForCore(UUID.randomUUID(), new SecurityInstallations(),
                new SecurityAuditLog(), true, NOW));
    }

    @Test
    void holdsTheSubjectsRowsWithoutAlertsRetentionOrOtherPlayers() {
        UUID subject = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        SecurityInstallations store = new SecurityInstallations();
        Installation mine = store.create(subject, "Mine", new DevicePos(OVERWORLD, new BlockPos(0, 64, 0)), NOW, 8)
                .installation();
        store.setMember(mine.id(), other, AccessLevel.OFFICER, 64);

        JsonObject doc = PrivacyExport.buildForCore(subject, store, new SecurityAuditLog(), false, NOW);

        assertNotNull(doc);
        assertEquals(1, doc.getAsJsonArray("installations_owned").size());
        assertFalse(doc.has("link"), "Core exports NeroLink alerts itself");
        assertFalse(doc.getAsJsonObject("notes").has("retention"), "nothing is written, so no file retention");
        assertTrue(doc.getAsJsonObject("notes").has("erasure"));
        assertFalse(doc.toString().contains(other.toString()), "another player's id never appears");
    }
}
