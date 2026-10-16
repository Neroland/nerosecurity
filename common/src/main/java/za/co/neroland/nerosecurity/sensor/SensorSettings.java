package za.co.neroland.nerosecurity.sensor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.block.DeviceSettingsRules;

/**
 * Per-sensor settings shared by the laser grid node and the camera: who may pass unreported
 * ({@code minLevel}, resolved through the resolver on the sensed player's UUID), which non-players it
 * reacts to, and the camera's field of view (60, 90 or 120 degrees; ignored by lasers).
 *
 * <p>The sensor holds no installation id; it looks the installation up from the store on every scan.
 */
public record SensorSettings(AccessLevel minLevel, SensorEntityMode entityMode, int fovDegrees) {

    public static final int[] FOV_OPTIONS = {60, 90, 120};

    public static final SensorSettings DEFAULT =
            new SensorSettings(AccessLevel.MEMBER, SensorEntityMode.PLAYERS_AND_HOSTILES, 90);

    public static final String KEY_MIN_LEVEL = "MinLevel";
    public static final String KEY_ENTITY_MODE = "EntityMode";
    public static final String KEY_FOV = "Fov";

    public static final Codec<SensorSettings> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            AccessLevel.CODEC.optionalFieldOf("min_level", AccessLevel.MEMBER).forGetter(SensorSettings::minLevel),
            SensorEntityMode.CODEC.optionalFieldOf("entity_mode", SensorEntityMode.DEFAULT).forGetter(SensorSettings::entityMode),
            Codec.INT.optionalFieldOf("fov", 90).forGetter(SensorSettings::fovDegrees)
    ).apply(inst, SensorSettings::new));

    public static final StreamCodec<ByteBuf, SensorSettings> STREAM_CODEC = StreamCodec.composite(
            AccessLevel.STREAM_CODEC, SensorSettings::minLevel,
            SensorEntityMode.STREAM_CODEC, SensorSettings::entityMode,
            ByteBufCodecs.VAR_INT, SensorSettings::fovDegrees,
            SensorSettings::new);

    public SensorSettings {
        if (minLevel == null) {
            minLevel = AccessLevel.MEMBER;
        }
        if (entityMode == null) {
            entityMode = SensorEntityMode.DEFAULT;
        }
        fovDegrees = clampFov(fovDegrees);
    }

    /** The nearest of 60, 90 and 120 degrees. */
    public static int clampFov(int degrees) {
        int best = FOV_OPTIONS[0];
        for (int option : FOV_OPTIONS) {
            if (Math.abs(option - degrees) < Math.abs(best - degrees)) {
                best = option;
            }
        }
        return best;
    }

    public SensorSettings withMinLevel(AccessLevel level) {
        return new SensorSettings(level, entityMode, fovDegrees);
    }

    public SensorSettings withEntityMode(SensorEntityMode mode) {
        return new SensorSettings(minLevel, mode, fovDegrees);
    }

    public SensorSettings withFov(int degrees) {
        return new SensorSettings(minLevel, entityMode, degrees);
    }

    /**
     * The post-resolution rule for a sensor settings change (the handler has already resolved
     * {@code CONFIGURE_DEVICE}): an unlinked sensor refuses every change, and only an owner (or an
     * operator) may set a sensor to OWNER or change anything on a sensor that is already OWNER
     * ({@link DeviceSettingsRules#mayApply}).
     *
     * @param linked       whether the sensor is linked to an installation right now
     * @param ownerOrAdmin whether the resolver reported OWNER or an operator override for the actor
     */
    public static boolean mayApply(boolean linked, boolean ownerOrAdmin, SensorSettings current, SensorSettings wanted) {
        if (!linked || current == null || wanted == null) {
            return false;
        }
        return DeviceSettingsRules.mayApply(ownerOrAdmin, current.minLevel(), wanted.minLevel());
    }

    /**
     * Writes {@value #KEY_MIN_LEVEL}, {@value #KEY_ENTITY_MODE} and {@value #KEY_FOV} into {@code out}.
     * Block entities pass a child: {@code settings.save(output.child("Settings"))}.
     */
    public void save(ValueOutput out) {
        out.putString(KEY_MIN_LEVEL, minLevel.jsonName());
        out.putString(KEY_ENTITY_MODE, entityMode.jsonName());
        out.putInt(KEY_FOV, fovDegrees);
    }

    /** Reads what {@link #save} wrote; a missing or unknown level gives {@link #DEFAULT}. */
    public static SensorSettings load(ValueInput in) {
        return AccessLevel.byName(in.getStringOr(KEY_MIN_LEVEL, ""))
                .map(level -> new SensorSettings(level, SensorEntityMode.byName(in.getStringOr(KEY_ENTITY_MODE, "")),
                        in.getIntOr(KEY_FOV, 90)))
                .orElse(DEFAULT);
    }
}
