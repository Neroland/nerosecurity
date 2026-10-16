package za.co.neroland.nerosecurity.sensor;

import java.util.Locale;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import org.jetbrains.annotations.Nullable;

/**
 * Which non-player entities a sensor reacts to. Players (and animals owned by a player, classified as
 * their owner) are always judged by the resolver whatever the mode. Items, XP orbs, projectiles, empty
 * vehicles and spectators never trip anything.
 */
public enum SensorEntityMode {
    /** Only players (and owned animals, as their owner). */
    PLAYERS_ONLY,
    /** Players plus hostile mobs. The default. */
    PLAYERS_AND_HOSTILES,
    /** Every living entity. */
    ALL_LIVING;

    public static final SensorEntityMode DEFAULT = PLAYERS_AND_HOSTILES;

    public static final Codec<SensorEntityMode> CODEC = Codec.STRING.xmap(SensorEntityMode::byName,
            SensorEntityMode::jsonName);

    public static final StreamCodec<ByteBuf, SensorEntityMode> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
            i -> i >= 0 && i < values().length ? values()[i] : DEFAULT, SensorEntityMode::ordinal);

    /** {@code "players_only" | "players_and_hostiles" | "all_living"}; also the lang key suffix. */
    public String jsonName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The translation key of this mode's label ({@code sensor.nerosecurity.mode.<name>}). */
    public String translationKey() {
        return "sensor.nerosecurity.mode." + jsonName();
    }

    /** Parses {@link #jsonName()}; anything else reads as {@link #DEFAULT}. */
    public static SensorEntityMode byName(@Nullable String raw) {
        if (raw != null) {
            for (SensorEntityMode mode : values()) {
                if (mode.jsonName().equals(raw)) {
                    return mode;
                }
            }
        }
        return DEFAULT;
    }
}
