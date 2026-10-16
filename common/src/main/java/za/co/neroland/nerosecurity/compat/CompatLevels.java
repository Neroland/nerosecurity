package za.co.neroland.nerosecurity.compat;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.access.AccessLevel;

/** The one cap every integration grant passes through: never above OFFICER, never above the binding's own max. */
public final class CompatLevels {

    /** The highest level any integration can ever grant. */
    public static final AccessLevel CEILING = AccessLevel.OFFICER;

    private CompatLevels() {
    }

    /** The lower of two levels (null-safe: null reads as PUBLIC). */
    public static AccessLevel min(@Nullable AccessLevel a, @Nullable AccessLevel b) {
        AccessLevel x = a == null ? AccessLevel.PUBLIC : a;
        AccessLevel y = b == null ? AccessLevel.PUBLIC : b;
        return x.ordinal() <= y.ordinal() ? x : y;
    }

    /**
     * {@code level} capped by the binding's {@code max}, which is itself capped at {@link #CEILING}; a null max
     * reads as OFFICER. Null stays null (the source abstains).
     */
    public static @Nullable AccessLevel cap(@Nullable AccessLevel level, @Nullable AccessLevel max) {
        if (level == null) {
            return null;
        }
        return min(level, min(max == null ? CEILING : max, CEILING));
    }
}
