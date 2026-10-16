package za.co.neroland.nerosecurity.keycard;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.installation.Installation;

/**
 * The {@code nerosecurity:keycard} item component: what an encoded card carries.
 *
 * <p>Everything except {@code label} is covered by the 16-byte HMAC tag in {@code signature}; change
 * any signed field and the card stops verifying. Who a bound card belongs to is deliberately <b>not</b>
 * stored here: the card only says that it is bound, and the server's card ledger knows the holder. So
 * no player identity ever sits on an item, in a tooltip or in a client's memory.
 *
 * <p>{@code label} is the installation's display name at write time, for the tooltip only. It is not
 * signed and nothing may ever decide anything from it.
 *
 * <p>A blank card simply has no component.
 */
public record KeycardData(UUID installationId, AccessLevel level, long serial, boolean encrypted, boolean bound,
        long issuedAt, byte[] signature, String label) {

    public static final int SIGNATURE_BYTES = 16;
    public static final int LABEL_MAX = Installation.NAME_MAX;

    /** Exactly {@link #SIGNATURE_BYTES} bytes; anything else is a decode error. */
    private static final Codec<byte[]> SIGNATURE_CODEC = Codec.BYTE_BUFFER.comapFlatMap(buffer -> {
        ByteBuffer copy = buffer.duplicate();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return bytes.length == SIGNATURE_BYTES ? DataResult.success(bytes)
                : DataResult.error(() -> "Keycard signature must be " + SIGNATURE_BYTES + " bytes");
    }, ByteBuffer::wrap);

    public static final Codec<KeycardData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            UUIDUtil.CODEC.fieldOf("installation").forGetter(KeycardData::installationId),
            AccessLevel.CODEC.fieldOf("level").forGetter(KeycardData::level),
            Codec.LONG.fieldOf("serial").forGetter(KeycardData::serial),
            Codec.BOOL.fieldOf("encrypted").forGetter(KeycardData::encrypted),
            Codec.BOOL.fieldOf("bound").forGetter(KeycardData::bound),
            Codec.LONG.fieldOf("issued_at").forGetter(KeycardData::issuedAt),
            SIGNATURE_CODEC.fieldOf("signature").forGetter(KeycardData::signature),
            Codec.string(0, LABEL_MAX).optionalFieldOf("label", "").forGetter(KeycardData::label)
    ).apply(inst, KeycardData::new));

    private static final StreamCodec<ByteBuf, String> LABEL_STREAM = ByteBufCodecs.stringUtf8(LABEL_MAX);

    /** Same fields as {@link #CODEC}; a signature of the wrong length fails the decode. */
    public static final StreamCodec<ByteBuf, KeycardData> STREAM_CODEC = StreamCodec.of(
            (buf, card) -> {
                UUIDUtil.STREAM_CODEC.encode(buf, card.installationId());
                AccessLevel.STREAM_CODEC.encode(buf, card.level());
                buf.writeLong(card.serial());
                buf.writeBoolean(card.encrypted());
                buf.writeBoolean(card.bound());
                buf.writeLong(card.issuedAt());
                buf.writeBytes(card.signature);
                LABEL_STREAM.encode(buf, card.label());
            },
            buf -> {
                UUID installation = UUIDUtil.STREAM_CODEC.decode(buf);
                AccessLevel level = AccessLevel.STREAM_CODEC.decode(buf);
                long serial = buf.readLong();
                boolean encrypted = buf.readBoolean();
                boolean bound = buf.readBoolean();
                long issuedAt = buf.readLong();
                if (buf.readableBytes() < SIGNATURE_BYTES) {
                    throw new DecoderException("Truncated keycard signature");
                }
                byte[] signature = new byte[SIGNATURE_BYTES];
                buf.readBytes(signature);
                String label = LABEL_STREAM.decode(buf);
                return new KeycardData(installation, level, serial, encrypted, bound, issuedAt, signature, label);
            });

    public KeycardData {
        Objects.requireNonNull(installationId, "installationId");
        Objects.requireNonNull(level, "level");
        signature = signature == null ? new byte[0] : signature.clone();
        label = label == null ? "" : label.length() > LABEL_MAX ? label.substring(0, LABEL_MAX) : label;
    }

    /** A copy of the tag (records would otherwise hand out the internal array). */
    @Override
    public byte[] signature() {
        return signature.clone();
    }

    /** The signed part of this card. */
    public KeycardPayload payload() {
        return new KeycardPayload(installationId, level, serial, encrypted, bound, issuedAt);
    }

    /** Builds a card from a payload, its tag and a display label. */
    public static KeycardData of(KeycardPayload payload, byte[] signature, String label) {
        return new KeycardData(payload.installationId(), payload.level(), payload.serial(), payload.encrypted(),
                payload.bound(), payload.issuedAt(), signature, label);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof KeycardData card)) {
            return false;
        }
        return serial == card.serial && encrypted == card.encrypted && bound == card.bound
                && issuedAt == card.issuedAt && installationId.equals(card.installationId) && level == card.level
                && Arrays.equals(signature, card.signature) && label.equals(card.label);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(installationId, level, serial, encrypted, bound, issuedAt, label);
        return 31 * result + Arrays.hashCode(signature);
    }

    /** Serial and level only: never the tag. */
    @Override
    public String toString() {
        return "KeycardData[#" + serial + ", " + level.jsonName() + (encrypted ? ", encrypted" : "")
                + (bound ? ", bound" : "") + "]";
    }
}
