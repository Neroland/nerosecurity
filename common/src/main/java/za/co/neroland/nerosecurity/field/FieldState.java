package za.co.neroland.nerosecurity.field;

import java.util.Locale;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** A force field emitter's state (see {@link FieldStateMachine}). Persisted by name. */
public enum FieldState {
    /** Not linked, switched off (server or emitter) or invalid settings. No field blocks. */
    IDLE,
    /** Drawing power and holding its field blocks. */
    PROJECTING,
    /** Collapsed for lack of energy; re-forms once the buffer holds enough to restart. */
    BROWNOUT,
    /** Collapsed under load; re-forms after its cooldown once the buffer holds enough to restart. */
    OVERLOADED;

    public static final StreamCodec<ByteBuf, FieldState> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
            i -> values()[Math.floorMod(i, values().length)], FieldState::ordinal);

    public String jsonName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The {@code field.nerosecurity.state.*} translation key. */
    public String translationKey() {
        return "field.nerosecurity.state." + jsonName();
    }

    /** Case-insensitive; anything unknown (or null) is {@link #IDLE}. */
    public static FieldState byName(String raw) {
        if (raw != null) {
            String wanted = raw.trim().toUpperCase(Locale.ROOT);
            for (FieldState state : values()) {
                if (state.name().equals(wanted)) {
                    return state;
                }
            }
        }
        return IDLE;
    }
}
