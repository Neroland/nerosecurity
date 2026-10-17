package za.co.neroland.nerosecurity.menu.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.field.FieldGeometry;
import za.co.neroland.nerosecurity.field.FieldSettings;
import za.co.neroland.nerosecurity.field.FieldState;
import za.co.neroland.nerosecurity.menu.field.FieldPayloads.FieldSnapshot;

/** The field snapshot's (ok, message) tail and the configure refusal mapping. Plain JVM. */
class FieldSnapshotTest {

    private static FieldSnapshot snapshot(boolean ok, Component message) {
        return new FieldSnapshot(new BlockPos(1, 64, 2), FieldSettings.DEFAULT, 3, 10, 8, 9, 9, 256, 40, 500L, 1000L,
                FieldState.PROJECTING, 0.25f, 0, AccessLevel.OFFICER, false, true, true, ok, message);
    }

    private static FieldSnapshot roundTrip(FieldSnapshot snap) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        FieldSnapshot.STREAM_CODEC.encode(buf, snap);
        FieldSnapshot back = FieldSnapshot.STREAM_CODEC.decode(buf);
        assertEquals(0, buf.readableBytes(), "the whole snapshot is read, tail included");
        return back;
    }

    private static String key(Component c) {
        return ((TranslatableContents) c.getContents()).getKey();
    }

    private static List<Object> args(Component c) {
        return List.of(((TranslatableContents) c.getContents()).getArgs());
    }

    /**
     * The tail round-trips. (Encoding a component needs a bootstrapped game, so the wire test uses no message; the
     * message half is {@code SnapshotMessages}, shared with every other snapshot.)
     */
    @Test
    void tailRoundTripsAndIsLast() {
        FieldSnapshot plain = snapshot(true, null);
        assertEquals(plain, roundTrip(plain));

        FieldSnapshot refused = snapshot(false, null);
        FieldSnapshot back = roundTrip(refused);
        assertFalse(back.ok());
        assertNull(back.message());
        assertEquals(refused, back);

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        FieldSnapshot.STREAM_CODEC.encode(buf, refused);
        int end = buf.writerIndex();
        assertEquals(0, buf.getByte(end - 2), "ok = false is the second-last byte");
        assertEquals(0, buf.getByte(end - 1), "no message: the presence flag is last");
    }

    @Test
    void withOutcomeOnlyChangesTheTail() {
        FieldSnapshot base = snapshot(true, null);
        Component done = Component.translatable("field.nerosecurity.configured");
        FieldSnapshot answered = base.withOutcome(true, done);
        assertTrue(answered.ok());
        assertEquals(done, answered.message());
        assertEquals(base, answered.withOutcome(true, null));
    }

    @Test
    void displayValuesAreNeverNegative() {
        FieldSnapshot forged = new FieldSnapshot(BlockPos.ZERO, null, -1, -2, -3, -4, -5, -6, -7, -8L, -9L, null,
                Float.NaN, -10, null, false, false, false, false, null);
        assertEquals(0, forged.strength());
        assertEquals(0, forged.maxStrength());
        assertEquals(0, forged.range());
        assertEquals(0, forged.area());
        assertEquals(0, forged.placed());
        assertEquals(0, forged.maxArea());
        assertEquals(0, forged.drawPerTick());
        assertEquals(0L, forged.energy());
        assertEquals(0L, forged.capacity());
        assertEquals(0f, forged.loadFraction());
        assertEquals(0, forged.cooldownSeconds());
        assertEquals(FieldSettings.DEFAULT, forged.settings());
        assertEquals(FieldState.IDLE, forged.state());
        assertEquals(AccessLevel.PUBLIC, forged.viewerLevel());
        assertNull(forged.message());
    }

    @Test
    void refusalMapping() {
        FieldSettings current = FieldSettings.DEFAULT;
        assertNull(FieldConfigureRules.refusal(true, false, current, current, 16, 1024), "a valid change is applied");

        assertEquals(FieldConfigureRules.SERVER_DISABLED_KEY,
                key(FieldConfigureRules.refusal(false, true, current, current, 16, 1024)), "disabled comes first");

        FieldSettings toOwner = current.withAccess(current.access().withMinLevel(AccessLevel.OWNER));
        assertEquals(FieldConfigureRules.OWNER_ONLY_KEY,
                key(FieldConfigureRules.refusal(true, false, current, toOwner, 16, 1024)), "non-owner sets OWNER");
        assertNull(FieldConfigureRules.refusal(true, true, current, toOwner, 16, 1024), "the owner may");
        assertEquals(FieldConfigureRules.OWNER_ONLY_KEY,
                key(FieldConfigureRules.refusal(true, false, toOwner, toOwner.withEnabled(!toOwner.enabled()), 16, 1024)),
                "non-owner changes an OWNER field");

        FieldSettings keycardOnly = current.withAccess(current.access().withKeycardOnly(true));
        assertEquals(FieldConfigureRules.OWNER_ONLY_KEY,
                key(FieldConfigureRules.refusal(true, false, current, keycardOnly, 16, 1024)),
                "keycard-only on a field is an OWNER setting: a non-owner cannot turn it on");
        assertEquals(FieldConfigureRules.OWNER_ONLY_KEY,
                key(FieldConfigureRules.refusal(true, false, keycardOnly, current, 16, 1024)),
                "... or off");
        assertNull(FieldConfigureRules.refusal(true, true, current, keycardOnly, 16, 1024), "the owner may");
        assertNull(FieldConfigureRules.refusal(true, false, keycardOnly, keycardOnly.withEnabled(false), 16, 1024),
                "a non-owner may still change other settings of a keycard-only MEMBER field");

        FieldSettings wide = current.withSize(31, 31, current.depth());
        Component range = FieldConfigureRules.refusal(true, true, current, wide, 3, 100_000);
        assertEquals(FieldGeometry.INVALID_RANGE, key(range));
        assertEquals(List.of(3), args(range), "names the emitter's range");

        Component area = FieldConfigureRules.refusal(true, true, current, wide, 64, 9);
        assertEquals(FieldGeometry.INVALID_AREA, key(area));
        assertEquals(List.of(9), args(area), "names the server's area cap");

        assertEquals(FieldGeometry.INVALID_SHAPE,
                key(FieldConfigureRules.refusal(true, true, current, current.withSize(4, 5, 1), 16, 1024)));
    }
}
