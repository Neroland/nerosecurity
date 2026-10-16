package za.co.neroland.nerosecurity.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import org.junit.jupiter.api.Test;

/** DeviceFeedback is a position and one byte, round-trips, and reaches the client listener. */
class DeviceFeedbackTest {

    @Test
    void carriesOnlyThePositionAndTheKind() {
        Set<String> allowed = Set.of("pos", "kind");
        for (RecordComponent component : DeviceFeedback.class.getRecordComponents()) {
            assertTrue(allowed.contains(component.getName()), "unexpected field " + component.getName());
        }
    }

    @Test
    void roundTripsAndRefusesUnknownKinds() {
        BlockPos pos = new BlockPos(3, 70, -9);
        for (byte kind : new byte[] {DeviceFeedback.DENIED, DeviceFeedback.ACCEPTED}) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            DeviceFeedback.STREAM_CODEC.encode(buf, new DeviceFeedback(pos, kind));
            DeviceFeedback back = DeviceFeedback.STREAM_CODEC.decode(buf);
            assertEquals(pos, back.pos());
            assertEquals(kind, back.kind());
        }
        FriendlyByteBuf bad = new FriendlyByteBuf(Unpooled.buffer());
        bad.writeBlockPos(pos);
        bad.writeByte(9);
        assertThrows(DecoderException.class, () -> DeviceFeedback.STREAM_CODEC.decode(bad));
        assertTrue(new DeviceFeedback(pos, (byte) 9).denied(), "an odd kind built locally reads as a refusal");
    }

    @Test
    void hookForwardsToTheListenerAndSurvivesAThrowingOne() {
        List<String> seen = new ArrayList<>();
        try {
            ClientFeedbackHook.setListener((pos, denied) -> seen.add(pos.getX() + ":" + denied));
            ClientFeedbackHook.accept(new DeviceFeedback(new BlockPos(1, 2, 3), DeviceFeedback.DENIED));
            ClientFeedbackHook.accept(new DeviceFeedback(new BlockPos(4, 2, 3), DeviceFeedback.ACCEPTED));
            assertEquals(List.of("1:true", "4:false"), seen);
            ClientFeedbackHook.setListener((pos, denied) -> {
                throw new IllegalStateException("boom");
            });
            ClientFeedbackHook.accept(new DeviceFeedback(BlockPos.ZERO, DeviceFeedback.DENIED));
            ClientFeedbackHook.setListener(null);
            ClientFeedbackHook.accept(new DeviceFeedback(BlockPos.ZERO, DeviceFeedback.DENIED));
            assertFalse(seen.isEmpty());
        } finally {
            ClientFeedbackHook.setListener(null);
        }
    }
}
