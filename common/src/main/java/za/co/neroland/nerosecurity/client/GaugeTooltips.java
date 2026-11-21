package za.co.neroland.nerosecurity.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

/**
 * The gauge tooltips for the device screens: each gauge names itself, gives amount / capacity with units, and
 * its rate. Builders append lines to a list; the screen emits them while the gauge row is hovered. Numbers come
 * from {@link GaugeMath}; every word is an English source lang key. Device-scoped numbers only (the values the
 * screen already draws) — no player names, UUIDs or coordinates. CLIENT-ONLY.
 */
public final class GaugeTooltips {

    /** Load change per second (in percent) treated as steady (whole-percent sync steps). */
    public static final double LOAD_DEADBAND_PER_SECOND = 0.5D;
    /** Energy change per tick treated as steady. */
    public static final double ENERGY_DEADBAND_PER_TICK = 0.05D;

    private GaugeTooltips() {
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gauge.nerosecurity." + key, args);
    }

    /** The current game tick (the rate windows' clock), or 0 before a level exists. */
    public static long gameTick() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0L : mc.level.getGameTime();
    }

    // --- energy -----------------------------------------------------------------------------------

    /**
     * Energy: name, stored / capacity in NE (and %), the rate, and the time until full or empty at that rate.
     * With a known draw ({@code usedPerTick > 0}) the net change splits into "in" and "used"; otherwise the net
     * change alone. {@code cause} (why the device is short of power) is added last when not null.
     */
    public static void energy(List<Component> out, long stored, long capacity, double netPerTick, long usedPerTick,
            @Nullable Component cause) {
        out.add(tr("energy"));
        if (capacity <= 0) {
            out.add(tr("energy.no_buffer"));
            return;
        }
        out.add(tr("energy.amount", GaugeMath.grouped(Math.max(0L, stored)), GaugeMath.grouped(capacity),
                GaugeMath.percent(stored, capacity)));
        if (usedPerTick > 0) {
            double in = Math.max(0D, netPerTick + usedPerTick);
            out.add(tr("energy.flow", GaugeMath.signedRate(in), GaugeMath.signedRate(-usedPerTick)));
        } else {
            out.add(tr("energy.net", GaugeMath.signedRate(netPerTick)));
        }
        if (stored >= capacity) {
            out.add(tr("energy.full"));
        } else if (stored <= 0) {
            out.add(tr("energy.empty"));
        } else {
            long ticks = ticksUntilEdge(stored, capacity, netPerTick);
            if (ticks >= 0) {
                out.add(tr(netPerTick < 0 ? "energy.empty_in" : "energy.full_in", GaugeMath.duration(ticks)));
            }
        }
        if (cause != null) {
            out.add(cause);
        }
    }

    /**
     * Ticks until a buffer at {@code stored} reaches empty (falling) or {@code capacity} (rising) at
     * {@code perTick}, or -1 when steady.
     */
    public static long ticksUntilEdge(long stored, long capacity, double perTick) {
        if (Double.isNaN(perTick) || Math.abs(perTick) < ENERGY_DEADBAND_PER_TICK) {
            return -1L;
        }
        double left = perTick > 0 ? Math.max(0L, capacity - stored) / perTick : Math.max(0L, stored) / -perTick;
        return (long) Math.ceil(left);
    }

    // --- overload meter ---------------------------------------------------------------------------

    /**
     * The force field's overload meter: name, percent of the limit, the trend (and the time to overload while
     * rising), the cooldown while overloaded, and what fills it.
     */
    public static void load(List<Component> out, int percent, double percentPerSecond, boolean overloaded,
            int cooldownSeconds) {
        int p = Math.max(0, Math.min(100, percent));
        out.add(tr("load"));
        out.add(tr("load.amount", p));
        if (overloaded) {
            out.add(tr("load.overloaded", GaugeMath.duration(Math.max(0L, cooldownSeconds) * GaugeMath.TICKS_PER_SECOND)));
        } else {
            int trend = GaugeMath.trend(percentPerSecond, LOAD_DEADBAND_PER_SECOND);
            out.add(trend > 0 ? tr("load.rising", GaugeMath.rate(percentPerSecond))
                    : trend < 0 ? tr("load.falling", GaugeMath.rate(percentPerSecond)) : tr("load.steady"));
            if (trend > 0) {
                long ticks = (long) Math.ceil((100 - p) / (percentPerSecond / GaugeMath.TICKS_PER_SECOND));
                out.add(tr("load.overload_in", GaugeMath.duration(ticks)));
            }
        }
        out.add(tr("load.hint"));
    }
}
