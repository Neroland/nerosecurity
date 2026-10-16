package za.co.neroland.nerosecurity.block;

import java.util.Optional;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.access.KeycardSource;

/**
 * The per-device access settings shared by doors and secure chests: the level needed to open it, and
 * whether only a presented keycard counts.
 *
 * <p>Keycard-only does not loosen anything: it only stops grants from every other source (the member
 * list included) from counting. Denies, lockdown and the operator override behave exactly as without
 * it, and a device's settings screen is never keycard-filtered, so an owner can always turn it off.
 *
 * <p>The device holds no installation id; the installation is looked up from the store on every use.
 */
public record DeviceAccessSettings(AccessLevel minLevel, boolean keycardOnly) {

    public static final DeviceAccessSettings DEFAULT = new DeviceAccessSettings(AccessLevel.MEMBER, false);

    public static final String KEY_MIN_LEVEL = "MinLevel";
    public static final String KEY_KEYCARD_ONLY = "KeycardOnly";

    public static final Codec<DeviceAccessSettings> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            AccessLevel.CODEC.optionalFieldOf("min_level", AccessLevel.MEMBER).forGetter(DeviceAccessSettings::minLevel),
            Codec.BOOL.optionalFieldOf("keycard_only", false).forGetter(DeviceAccessSettings::keycardOnly)
    ).apply(inst, DeviceAccessSettings::new));

    public static final StreamCodec<ByteBuf, DeviceAccessSettings> STREAM_CODEC = StreamCodec.composite(
            AccessLevel.STREAM_CODEC, DeviceAccessSettings::minLevel,
            ByteBufCodecs.BOOL, DeviceAccessSettings::keycardOnly,
            DeviceAccessSettings::new);

    public DeviceAccessSettings {
        if (minLevel == null) {
            minLevel = AccessLevel.MEMBER;
        }
    }

    /** The resolver's granting-source filter: keycards only when keycard-only is on, else every source. */
    public @Nullable Set<Identifier> grantingSources() {
        return keycardOnly ? Set.of(KeycardSource.ID) : null;
    }

    public DeviceAccessSettings withMinLevel(AccessLevel level) {
        return new DeviceAccessSettings(level, keycardOnly);
    }

    public DeviceAccessSettings withKeycardOnly(boolean value) {
        return new DeviceAccessSettings(minLevel, value);
    }

    /**
     * Writes {@value #KEY_MIN_LEVEL} and {@value #KEY_KEYCARD_ONLY} into {@code out}. Block entities
     * pass a child, e.g. {@code settings.save(output.child("Settings"))}.
     */
    public void save(ValueOutput out) {
        out.putString(KEY_MIN_LEVEL, minLevel.jsonName());
        out.putBoolean(KEY_KEYCARD_ONLY, keycardOnly);
    }

    /** Reads what {@link #save} wrote; a missing or unknown level gives {@link #DEFAULT}. */
    public static DeviceAccessSettings load(ValueInput in) {
        Optional<AccessLevel> level = AccessLevel.byName(in.getStringOr(KEY_MIN_LEVEL, ""));
        if (level.isEmpty()) {
            return DEFAULT;
        }
        return new DeviceAccessSettings(level.get(), in.getBooleanOr(KEY_KEYCARD_ONLY, false));
    }
}
