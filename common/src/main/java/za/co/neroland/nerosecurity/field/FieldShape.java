package za.co.neroland.nerosecurity.field;

import java.util.Locale;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** What a force field emitter projects: a flat wall or a hollow box shell. */
public enum FieldShape {
    /** A wall perpendicular to the emitter's facing. */
    PLANE,
    /** A hollow box shell centred in front of the emitter. */
    BOX;

    /** Lower-case name codec; an unknown name reads as {@link #PLANE}. */
    public static final Codec<FieldShape> CODEC = Codec.STRING.xmap(FieldShape::byName, FieldShape::jsonName);

    public static final StreamCodec<ByteBuf, FieldShape> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
            i -> values()[Math.floorMod(i, values().length)], FieldShape::ordinal);

    public String jsonName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Case-insensitive; anything unknown (or null) is {@link #PLANE}. */
    public static FieldShape byName(String raw) {
        if (raw != null) {
            String wanted = raw.trim().toUpperCase(Locale.ROOT);
            for (FieldShape shape : values()) {
                if (shape.name().equals(wanted)) {
                    return shape;
                }
            }
        }
        return PLANE;
    }
}
