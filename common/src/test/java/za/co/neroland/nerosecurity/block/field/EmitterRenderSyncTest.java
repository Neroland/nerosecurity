package za.co.neroland.nerosecurity.block.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.block.field.ForceFieldEmitterBlockEntity.RenderSync;
import za.co.neroland.nerosecurity.field.FieldState;

/** Draw-bucket quantisation and the emitter's renderer resend policy. Plain JVM. */
class EmitterRenderSyncTest {

    @Test
    void bucketIsZeroUnlessProjectingWithDraw() {
        assertEquals(0, RenderSync.drawBucket(FieldState.IDLE, 500, 1000));
        assertEquals(0, RenderSync.drawBucket(FieldState.BROWNOUT, 500, 1000));
        assertEquals(0, RenderSync.drawBucket(FieldState.OVERLOADED, 500, 1000));
        assertEquals(0, RenderSync.drawBucket(FieldState.PROJECTING, 0, 1000));
    }

    @Test
    void bucketQuantisesDrawOverMax() {
        assertEquals(1, RenderSync.drawBucket(FieldState.PROJECTING, 1, 1000));
        assertEquals(1, RenderSync.drawBucket(FieldState.PROJECTING, 250, 1000));
        assertEquals(2, RenderSync.drawBucket(FieldState.PROJECTING, 251, 1000));
        assertEquals(2, RenderSync.drawBucket(FieldState.PROJECTING, 500, 1000));
        assertEquals(3, RenderSync.drawBucket(FieldState.PROJECTING, 750, 1000));
        assertEquals(4, RenderSync.drawBucket(FieldState.PROJECTING, 751, 1000));
        assertEquals(4, RenderSync.drawBucket(FieldState.PROJECTING, 1000, 1000));
        assertEquals(4, RenderSync.drawBucket(FieldState.PROJECTING, 5000, 1000));
        assertEquals(4, RenderSync.drawBucket(FieldState.PROJECTING, 10, 0));
        assertEquals(4, RenderSync.drawBucket(FieldState.PROJECTING, Integer.MAX_VALUE, Integer.MAX_VALUE - 1));
    }

    @Test
    void stateFlipResendsAtOnce() {
        assertTrue(RenderSync.resendDue(FieldState.PROJECTING, 2, false, 100L, FieldState.BROWNOUT, 0, false, 101L));
        assertTrue(RenderSync.resendDue(null, 0, false, Long.MIN_VALUE, FieldState.IDLE, 0, false, 5L));
    }

    @Test
    void nothingChangedSendsNothing() {
        assertFalse(RenderSync.resendDue(FieldState.PROJECTING, 2, true, 100L, FieldState.PROJECTING, 2, true, 1000L));
    }

    @Test
    void otherChangesAreSpacedTenTicks() {
        assertFalse(RenderSync.resendDue(FieldState.PROJECTING, 2, false, 100L, FieldState.PROJECTING, 3, false, 109L));
        assertTrue(RenderSync.resendDue(FieldState.PROJECTING, 2, false, 100L, FieldState.PROJECTING, 3, false, 110L));
        assertFalse(RenderSync.resendDue(FieldState.PROJECTING, 2, false, 100L, FieldState.PROJECTING, 2, true, 105L));
        assertTrue(RenderSync.resendDue(FieldState.PROJECTING, 2, false, 100L, FieldState.PROJECTING, 2, true, 112L));
        // A clock that went backwards (dimension change) does not wedge the resend.
        assertTrue(RenderSync.resendDue(FieldState.PROJECTING, 2, false, 100L, FieldState.PROJECTING, 3, false, 50L));
    }
}
