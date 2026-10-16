package za.co.neroland.nerosecurity.client.screen.monitor;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.audit.ActorKind;
import za.co.neroland.nerosecurity.audit.AnonymousEvent;
import za.co.neroland.nerosecurity.event.EventPhrases;
import za.co.neroland.nerosecurity.installation.LockdownSource;

/**
 * The monitor's wording, built from the phrase table and fixed keys only. Nothing here can take a name:
 * an event sentence is "phrase — actor kind at device position, age", e.g.
 * "Laser grid tripped — a player at 12, 64, -3, 5 minutes ago". No client-only imports, so the wording
 * rules are testable in a plain JVM.
 */
public final class MonitorText {

    private static final String DURATION = "monitor.nerosecurity.duration.";
    private static final long MINUTE = 60L;
    private static final long HOUR = 60L * MINUTE;
    private static final long DAY = 24L * HOUR;

    private MonitorText() {
    }

    /** A duration rounded down to its largest whole unit. */
    public record Age(String unit, long count) {
    }

    /** {@code seconds} as under_minute, minute(s), hour(s) or day(s). */
    public static Age age(long seconds) {
        long s = Math.max(0L, seconds);
        if (s < MINUTE) {
            return new Age("under_minute", 0L);
        }
        if (s < HOUR) {
            long m = s / MINUTE;
            return new Age(m == 1 ? "minute" : "minutes", m);
        }
        if (s < DAY) {
            long h = s / HOUR;
            return new Age(h == 1 ? "hour" : "hours", h);
        }
        long d = s / DAY;
        return new Age(d == 1 ? "day" : "days", d);
    }

    /** "5 minutes ago", "less than a minute ago". */
    public static Component ago(long seconds) {
        Age age = age(seconds);
        String fallback = switch (age.unit()) {
            case "under_minute" -> "less than a minute";
            case "minute" -> "1 minute";
            case "hour" -> "1 hour";
            case "day" -> "1 day";
            default -> "%s " + age.unit();
        };
        Component duration = Component.translatableWithFallback(DURATION + age.unit(), fallback, age.count());
        return Component.translatable("monitor.nerosecurity.ago", duration);
    }

    /** "x, y, z", or a dash when there is no position. */
    public static Component position(@Nullable BlockPos pos) {
        if (pos == null) {
            return Component.translatable("monitor.nerosecurity.pos.none");
        }
        return Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
    }

    /** The actor kind in words; never who. */
    public static Component actor(ActorKind kind) {
        return switch (kind == null ? ActorKind.NONE : kind) {
            case PLAYER -> Component.translatable("monitor.nerosecurity.actor.player");
            case WITHHELD -> Component.translatable("monitor.nerosecurity.actor.withheld");
            case NONE -> Component.translatable("monitor.nerosecurity.actor.none");
        };
    }

    /** One event as a sentence, aged by {@code extraSeconds} since the snapshot arrived. */
    public static Component sentence(AnonymousEvent event, long extraSeconds) {
        return Component.translatable("monitor.nerosecurity.row", EventPhrases.phrase(event.type()),
                actor(event.actorKind()), position(event.devicePos()),
                ago(event.agoSeconds() + Math.max(0L, extraSeconds)));
    }

    /** "Normal", "LOCKDOWN", or "LOCKDOWN (alarm, clears in N min)". */
    public static MutableComponent status(boolean lockdown, LockdownSource source, int autoReleaseInSeconds) {
        if (!lockdown) {
            return Component.translatable("monitor.nerosecurity.status.normal");
        }
        if (source == LockdownSource.ALARM && autoReleaseInSeconds > 0) {
            return Component.translatable("monitor.nerosecurity.status.lockdown_alarm",
                    Math.max(1, (autoReleaseInSeconds + 59) / 60));
        }
        return Component.translatable("monitor.nerosecurity.status.lockdown");
    }
}
