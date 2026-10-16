package za.co.neroland.nerosecurity.field;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import za.co.neroland.nerosecurity.block.DeviceAccessSettings;

/**
 * One emitter's field settings: shape, distance in front of the emitter, dimensions, who may pass and whether it is
 * switched on. {@code access} is the same per-device record doors and chests use, so a keycard-only field passes
 * only the keycard source as granting. Whether the settings fit the emitter's range and the server's area cap is
 * {@link FieldGeometry#validate}'s job; this record only carries values.
 *
 * <p>Holds no installation id and nothing about a player.
 */
public record FieldSettings(FieldShape shape, int offset, int width, int height, int depth,
        DeviceAccessSettings access, boolean enabled) {

    public static final FieldSettings DEFAULT =
            new FieldSettings(FieldShape.PLANE, 2, 5, 5, 1, DeviceAccessSettings.DEFAULT, true);

    public static final String KEY_SHAPE = "Shape";
    public static final String KEY_OFFSET = "Offset";
    public static final String KEY_WIDTH = "Width";
    public static final String KEY_HEIGHT = "Height";
    public static final String KEY_DEPTH = "Depth";
    public static final String KEY_ACCESS = "Access";
    public static final String KEY_ENABLED = "Enabled";

    public static final Codec<FieldSettings> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            FieldShape.CODEC.optionalFieldOf("shape", DEFAULT.shape()).forGetter(FieldSettings::shape),
            Codec.INT.optionalFieldOf("offset", DEFAULT.offset()).forGetter(FieldSettings::offset),
            Codec.INT.optionalFieldOf("width", DEFAULT.width()).forGetter(FieldSettings::width),
            Codec.INT.optionalFieldOf("height", DEFAULT.height()).forGetter(FieldSettings::height),
            Codec.INT.optionalFieldOf("depth", DEFAULT.depth()).forGetter(FieldSettings::depth),
            DeviceAccessSettings.CODEC.optionalFieldOf("access", DeviceAccessSettings.DEFAULT).forGetter(FieldSettings::access),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(FieldSettings::enabled)
    ).apply(inst, FieldSettings::new));

    /** Wire form. The server re-validates everything a client sends (see {@link FieldGeometry#validate}). */
    public static final StreamCodec<ByteBuf, FieldSettings> STREAM_CODEC = StreamCodec.composite(
            FieldShape.STREAM_CODEC, FieldSettings::shape,
            ByteBufCodecs.VAR_INT, FieldSettings::offset,
            ByteBufCodecs.VAR_INT, FieldSettings::width,
            ByteBufCodecs.VAR_INT, FieldSettings::height,
            ByteBufCodecs.VAR_INT, FieldSettings::depth,
            DeviceAccessSettings.STREAM_CODEC, FieldSettings::access,
            ByteBufCodecs.BOOL, FieldSettings::enabled,
            FieldSettings::new);

    public FieldSettings {
        if (shape == null) {
            shape = FieldShape.PLANE;
        }
        if (access == null) {
            access = DeviceAccessSettings.DEFAULT;
        }
    }

    public FieldSettings withShape(FieldShape value) {
        return new FieldSettings(value, offset, width, height, depth, access, enabled);
    }

    public FieldSettings withOffset(int value) {
        return new FieldSettings(shape, value, width, height, depth, access, enabled);
    }

    public FieldSettings withSize(int newWidth, int newHeight, int newDepth) {
        return new FieldSettings(shape, offset, newWidth, newHeight, newDepth, access, enabled);
    }

    public FieldSettings withAccess(DeviceAccessSettings value) {
        return new FieldSettings(shape, offset, width, height, depth, value, enabled);
    }

    public FieldSettings withEnabled(boolean value) {
        return new FieldSettings(shape, offset, width, height, depth, access, value);
    }

    /** Writes every key into {@code out}. Block entities pass a child: {@code settings.save(output.child("Settings"))}. */
    public void save(ValueOutput out) {
        out.putString(KEY_SHAPE, shape.jsonName());
        out.putInt(KEY_OFFSET, offset);
        out.putInt(KEY_WIDTH, width);
        out.putInt(KEY_HEIGHT, height);
        out.putInt(KEY_DEPTH, depth);
        access.save(out.child(KEY_ACCESS));
        out.putBoolean(KEY_ENABLED, enabled);
    }

    /**
     * Reads what {@link #save} wrote. Junk (a non-positive offset or dimension) gives {@link #DEFAULT}; values that
     * are merely too big for the emitter load as they are and leave it idle until changed.
     */
    public static FieldSettings load(ValueInput in) {
        FieldSettings read = new FieldSettings(
                FieldShape.byName(in.getStringOr(KEY_SHAPE, DEFAULT.shape().jsonName())),
                in.getIntOr(KEY_OFFSET, DEFAULT.offset()),
                in.getIntOr(KEY_WIDTH, DEFAULT.width()),
                in.getIntOr(KEY_HEIGHT, DEFAULT.height()),
                in.getIntOr(KEY_DEPTH, DEFAULT.depth()),
                DeviceAccessSettings.load(in.childOrEmpty(KEY_ACCESS)),
                in.getBooleanOr(KEY_ENABLED, true));
        if (read.offset() < 1 || read.width() < 1 || read.height() < 1 || read.depth() < 1) {
            return DEFAULT;
        }
        return read;
    }
}
