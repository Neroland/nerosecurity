package za.co.neroland.nerosecurity.client.screen;

import net.minecraft.network.chat.Component;

/**
 * Relative time wording for every screen: "just now", "12 s ago", "5 min ago", "3 h ago", "2 d ago", and the
 * countdown form "4 min" / "30 s". Ages are always relative, never a clock time. The bucketing is pure and
 * unit-tested; the components use the {@code time.nerosecurity.*} keys.
 */
public final class Ages {

    /** Below this many seconds an age reads "just now". */
    public static final long NOW_SECONDS = 5L;
    private static final long MINUTE = 60L;
    private static final long HOUR = 60L * MINUTE;
    private static final long DAY = 24L * HOUR;

    public enum Unit {
        NOW("now"),
        SECONDS("seconds"),
        MINUTES("minutes"),
        HOURS("hours"),
        DAYS("days");

        private final String key;

        Unit(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    /** An age rounded down to its largest whole unit. {@code count} is 0 for {@link Unit#NOW}. */
    public record Bucket(Unit unit, long count) {
    }

    private Ages() {
    }

    /** {@code seconds} (negative counts as 0) rounded down to its largest unit. */
    public static Bucket bucket(long seconds) {
        long s = Math.max(0L, seconds);
        if (s < NOW_SECONDS) {
            return new Bucket(Unit.NOW, 0L);
        }
        if (s < MINUTE) {
            return new Bucket(Unit.SECONDS, s);
        }
        if (s < HOUR) {
            return new Bucket(Unit.MINUTES, s / MINUTE);
        }
        if (s < DAY) {
            return new Bucket(Unit.HOURS, s / HOUR);
        }
        return new Bucket(Unit.DAYS, s / DAY);
    }

    /** The lang key for an age: {@code time.nerosecurity.ago.<unit>}. */
    public static String agoKey(Unit unit) {
        return "time.nerosecurity.ago." + unit.key();
    }

    /** "5 min ago" for an event {@code seconds} old. */
    public static Component ago(long seconds) {
        Bucket b = bucket(seconds);
        if (b.unit() == Unit.NOW) {
            return Component.translatable(agoKey(Unit.NOW));
        }
        return Component.translatable(agoKey(b.unit()), b.count());
    }

    /**
     * A countdown, rounded <b>up</b> so it never reads 0 while time remains: "4 min" from 60 s up, otherwise
     * "30 s" (at least 1 s).
     */
    public static Component in(long seconds) {
        long s = Math.max(0L, seconds);
        if (s >= MINUTE) {
            return Component.translatable("time.nerosecurity.in.minutes", ceilMinutes(s));
        }
        return Component.translatable("time.nerosecurity.in.seconds", Math.max(1L, s));
    }

    /** Whole minutes rounded up (61 s → 2). */
    public static long ceilMinutes(long seconds) {
        long s = Math.max(0L, seconds);
        return (s + MINUTE - 1L) / MINUTE;
    }
}
