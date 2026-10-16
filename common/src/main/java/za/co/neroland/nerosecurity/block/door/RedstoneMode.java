package za.co.neroland.nerosecurity.block.door;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * How a door reacts to redstone. Doors never emit redstone. {@link #OPEN_WHILE_POWERED} only works
 * while the server allows door redstone, never during lockdown, and never while the server requires
 * power and the door has none.
 */
public enum RedstoneMode {
    IGNORE("ignore"),
    OPEN_WHILE_POWERED("open");

    public static final StreamCodec<ByteBuf, RedstoneMode> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
            RedstoneMode::byOrdinal, RedstoneMode::ordinal);

    private final String serializedName;

    RedstoneMode(String serializedName) {
        this.serializedName = serializedName;
    }

    /** The value saved in block-entity data. */
    public String serializedName() {
        return serializedName;
    }

    /** The lang key of the redstone toggle's label for this mode. */
    public String translationKey() {
        return "door.nerosecurity.redstone." + serializedName;
    }

    public RedstoneMode next() {
        return this == IGNORE ? OPEN_WHILE_POWERED : IGNORE;
    }

    /** Unknown ordinals read as {@link #IGNORE}. */
    public static RedstoneMode byOrdinal(int ordinal) {
        RedstoneMode[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : IGNORE;
    }

    /** Unknown or missing names read as {@link #IGNORE}. */
    public static RedstoneMode byName(String name) {
        for (RedstoneMode mode : values()) {
            if (mode.serializedName.equals(name)) {
                return mode;
            }
        }
        return IGNORE;
    }
}
