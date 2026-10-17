package za.co.neroland.nerosecurity.privacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.UUID;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;

import net.minecraft.network.FriendlyByteBuf;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.audit.RetentionPolicy;
import za.co.neroland.nerosecurity.privacy.PrivacyPayloads.HubPrivacyAction;
import za.co.neroland.nerosecurity.privacy.PrivacyPayloads.HubPrivacyRequest;
import za.co.neroland.nerosecurity.privacy.PrivacyPayloads.PrivacyAction;
import za.co.neroland.nerosecurity.privacy.PrivacyPayloads.PrivacySnapshot;

/** The Privacy tab's payloads carry counts and settings only, and the real privacy actions are installed. */
class PrivacyPayloadsTest {

    @Test
    void noPayloadCarriesAnIdentity() {
        for (Class<?> type : List.of(PrivacySnapshot.class, HubPrivacyRequest.class, HubPrivacyAction.class)) {
            for (RecordComponent component : type.getRecordComponents()) {
                assertFalse(UUID.class.equals(component.getType()), type.getSimpleName() + "." + component.getName());
                assertFalse(String.class.equals(component.getType()), type.getSimpleName() + "." + component.getName());
            }
        }
    }

    @Test
    void actionCodesRoundTripAndUnknownCodesAreRefused() {
        for (PrivacyAction action : PrivacyAction.values()) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            HubPrivacyAction.STREAM_CODEC.encode(buf, new HubPrivacyAction(action));
            assertEquals(action, HubPrivacyAction.STREAM_CODEC.decode(buf).action());
        }
        assertEquals(0, PrivacyAction.EXPORT.code());
        assertEquals(1, PrivacyAction.OPT_OUT.code());
        assertEquals(2, PrivacyAction.OPT_IN.code());
        FriendlyByteBuf bad = new FriendlyByteBuf(Unpooled.buffer());
        bad.writeByte(7);
        assertThrows(DecoderException.class, () -> HubPrivacyAction.STREAM_CODEC.decode(bad));
    }

    @Test
    void theRealActionsAreInstalledAndAvailable() {
        assertSame(SecurityPrivacyActions.INSTANCE, PrivacyActions.current(), "the privacy update is the default");
        assertTrue(PrivacyActions.current().available(), "PrivacySnapshot.actionsAvailable is true");
        PrivacyActions.install(null);
        assertSame(SecurityPrivacyActions.INSTANCE, PrivacyActions.current(), "null restores the real actions");
    }

    @Test
    void snapshotAgesAreRelativeAndNeverNegative() {
        RetentionPolicy policy = RetentionPolicy.of(7, 60, 500, 90, null);
        PrivacySnapshot snap = PrivacyPayloadHandlers.assemble(true, policy, 12, 1_000_000L, 1_090_000L, true, false,
                false, false, true, null);
        assertEquals(7, snap.retentionDays());
        assertEquals(500, snap.maxEntries());
        assertEquals(12, snap.entryCount());
        assertEquals(90, snap.oldestAgeSeconds());
        assertEquals(0, PrivacyPayloadHandlers.assemble(true, policy, 0, 0L, 5_000L, true, false, false, false, true,
                null).oldestAgeSeconds(), "no rows: age zero");
        assertEquals(0, PrivacyPayloadHandlers.assemble(true, policy, 1, 9_000L, 5_000L, true, false, false, false,
                true, null).oldestAgeSeconds(), "a clock that went backwards never goes negative");
    }
}
