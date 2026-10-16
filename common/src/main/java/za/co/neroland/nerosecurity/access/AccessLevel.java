package za.co.neroland.nerosecurity.access;

import java.util.Locale;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The four rungs of NeroSecurity's local access ladder, lowest first.
 *
 * <p>{@code ADMIN} is deliberately <b>not</b> a constant here. A server operator's override is a
 * property of one access decision ({@link AccessDecision#admin()}), produced only by the resolver's
 * {@link AdminCheck} short-circuit. Nothing can store it: no member row, no packet, no command
 * argument and no keycard can carry an admin level, because there is no value to carry.
 *
 * <p>Level comparisons live in this package only. Blocks, payload handlers and commands ask
 * {@link AccessResolver} for a decision instead of comparing levels themselves.
 */
public enum AccessLevel {
    PUBLIC(0xA0A0A0, "public"),
    MEMBER(0x55D55A, "member"),
    OFFICER(0xE0A526, "officer"),
    OWNER(0xE04545, "owner");

    /** Colour the UI uses when a decision was an operator override. Not a rung. */
    public static final int ADMIN_COLOUR = 0xFFFFFF;
    public static final String ADMIN_KEY = "access.nerosecurity.level.admin";
    public static final String ADMIN_MEANING_KEY = "access.nerosecurity.level.admin.meaning";

    /** String codec over {@link #jsonName()}; unknown names are a decode error. */
    public static final Codec<AccessLevel> CODEC = Codec.STRING.comapFlatMap(
            name -> byName(name).map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "Unknown access level")),
            AccessLevel::jsonName);

    /** Network codec: the ordinal as a var-int. An out-of-range ordinal fails the decode. */
    public static final StreamCodec<ByteBuf, AccessLevel> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
            AccessLevel::byOrdinal, AccessLevel::ordinal);

    /** 0xRRGGBB. */
    public final int colour;
    private final String key;

    AccessLevel(int colour, String key) {
        this.colour = colour;
        this.key = key;
    }

    public String translationKey() {
        return "access.nerosecurity.level." + key;
    }

    public String meaningKey() {
        return "access.nerosecurity.level." + key + ".meaning";
    }

    /** {@code "public" | "member" | "officer" | "owner"}. */
    public String jsonName() {
        return key;
    }

    /** The level's display name in its colour. */
    public MutableComponent displayName() {
        return Component.translatable(translationKey()).withColor(colour);
    }

    /** The admin display name in its colour (UI only; ADMIN is not a level). */
    public static MutableComponent adminDisplayName() {
        return Component.translatable(ADMIN_KEY).withColor(ADMIN_COLOUR);
    }

    /** Case-insensitive lookup; {@link Optional#empty()} for anything unknown. */
    public static Optional<AccessLevel> byName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        for (AccessLevel level : values()) {
            if (level.key.equals(wanted)) {
                return Optional.of(level);
            }
        }
        return Optional.empty();
    }

    public boolean atLeast(AccessLevel other) {
        return ordinal() >= other.ordinal();
    }

    public static AccessLevel max(AccessLevel a, AccessLevel b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    private static AccessLevel byOrdinal(int ordinal) {
        AccessLevel[] all = values();
        if (ordinal < 0 || ordinal >= all.length) {
            throw new DecoderException("Invalid access level");
        }
        return all[ordinal];
    }
}
