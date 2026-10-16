package za.co.neroland.nerosecurity.feedback;

import io.netty.handler.codec.DecoderException;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerosecurity.NeroSecurityCommon;

/**
 * Clientbound {@code nerosecurity:device_feedback}: "the device at {@code pos} just refused (or accepted)
 * you". Sent only to the player who acted, never broadcast. It carries the device's block position and one
 * byte, nothing else: no reason (the action bar already says why), no identity, no level.
 *
 * <p>The client plays {@code SecuritySounds.DENY} (or the quiet {@code ACCEPT} click) at the device and, for a
 * refusal, flashes the device's LED for ten ticks.
 *
 * @param pos  the device's block position (a multi-block device's controller)
 * @param kind {@link #DENIED} or {@link #ACCEPTED}
 */
public record DeviceFeedback(BlockPos pos, byte kind) implements CustomPacketPayload {

    public static final byte DENIED = 0;
    public static final byte ACCEPTED = 1;

    public static final Type<DeviceFeedback> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(NeroSecurityCommon.MOD_ID, "device_feedback"));

    public static final StreamCodec<FriendlyByteBuf, DeviceFeedback> STREAM_CODEC = StreamCodec.of(
            (buf, v) -> {
                buf.writeBlockPos(v.pos);
                buf.writeByte(v.kind);
            },
            buf -> {
                BlockPos pos = buf.readBlockPos();
                byte kind = buf.readByte();
                if (kind != DENIED && kind != ACCEPTED) {
                    throw new DecoderException("unknown device feedback kind");
                }
                return new DeviceFeedback(pos, kind);
            });

    public DeviceFeedback {
        pos = pos.immutable();
        kind = kind == ACCEPTED ? ACCEPTED : DENIED;
    }

    public boolean denied() {
        return kind == DENIED;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
