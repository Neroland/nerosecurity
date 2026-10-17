package za.co.neroland.nerosecurity.client.screen.monitor;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.audit.ActorKind;
import za.co.neroland.nerosecurity.installation.LockdownSource;

/**
 * The monitor's wording, from fixed keys only. Nothing here can take a name: the actor is a kind, the position
 * is the device's. The event sentence itself (phrase, actor kind, device position, age) is built by
 * {@code SentenceList.event} with these parts and the shared {@code Ages} vocabulary. No client-only imports, so
 * the wording rules are testable in a plain JVM.
 */
public final class MonitorText {

    private MonitorText() {
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
