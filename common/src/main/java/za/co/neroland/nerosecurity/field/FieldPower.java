package za.co.neroland.nerosecurity.field;

/**
 * The emitter's arithmetic, pure so it is tested without a level. Power is always computed from the strength the
 * modules give; the lockdown boost raises the effective strength (and so the load capacity) for free.
 */
public final class FieldPower {

    /** Range each range module adds. */
    public static final int RANGE_PER_MODULE = 4;

    private FieldPower() {
    }

    /** Energy per tick while projecting: {@code base + ceil(area × strength × perBlockStrength)}, never negative. */
    public static int drawPerTick(int area, int strength, int basePerTick, double perBlockStrength) {
        double variable = Math.ceil((double) Math.max(0, area) * Math.max(0, strength) * Math.max(0.0, perBlockStrength));
        long total = Math.max(0, basePerTick) + (long) Math.min(Integer.MAX_VALUE, variable);
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    /** The energy the buffer must hold before a field forms or re-forms: {@code drawPerTick × restartTicks}. */
    public static long restartThreshold(int drawPerTick, int restartTicks) {
        return (long) Math.max(0, drawPerTick) * Math.max(0, restartTicks);
    }

    /** Module strength: 1 plus one per strength module, capped by {@code maxFieldStrength} (and at least 1). */
    public static int strength(int strengthModules, int maxFieldStrength) {
        int max = Math.max(1, maxFieldStrength);
        return Math.max(1, Math.min(max, 1 + Math.max(0, strengthModules)));
    }

    /** Range: {@code base + 4 × modules}, capped by {@code maxRange}. */
    public static int range(int rangeModules, int baseRange, int maxRange) {
        long r = baseRange + (long) RANGE_PER_MODULE * Math.max(0, rangeModules);
        return (int) Math.max(0L, Math.min(maxRange, r));
    }

    /** The overload meter's capacity at {@code effectiveStrength}. */
    public static double loadCapacity(double perStrength, int effectiveStrength) {
        return Math.max(0.0, perStrength) * Math.max(1, effectiveStrength);
    }
}
