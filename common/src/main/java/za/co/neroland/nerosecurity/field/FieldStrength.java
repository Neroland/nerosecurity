package za.co.neroland.nerosecurity.field;

/**
 * The strength a force field emitter actually runs at. Pure, so the lockdown rule is tested without a level.
 *
 * <p>Modules give the <em>module strength</em> ({@link FieldPower#strength}); power is always drawn for that.
 * During a lockdown, when the server allows it, the field runs at {@code maxFieldStrength} instead, for free:
 * more load before it overloads, no extra energy. {@code maxFieldStrength} caps both, so a lockdown never makes a
 * field stronger than the server allows.
 */
public final class FieldStrength {

    private FieldStrength() {
    }

    /**
     * @param moduleStrength   the strength the fitted modules give (clamped to {@code 1..maxFieldStrength})
     * @param lockdownBoost    the installation is in lockdown (and lockdowns are enabled)
     * @param raisesToMax      the server's {@code fieldLockdownRaisesToMax}
     * @param maxFieldStrength the server's {@code maxFieldStrength} (at least 1)
     * @return the effective strength: the cap during a boosted lockdown, else the clamped module strength
     */
    public static int effective(int moduleStrength, boolean lockdownBoost, boolean raisesToMax, int maxFieldStrength) {
        int max = Math.max(1, maxFieldStrength);
        if (lockdownBoost && raisesToMax) {
            return max;
        }
        return Math.max(1, Math.min(max, moduleStrength));
    }

    /**
     * The overload meter across a change of load capacity (a lockdown boost starting or ending): rescaled so it stays
     * the same fraction of the new capacity, and never reset. Entering a lockdown therefore gives a stressed field
     * more headroom for new hits but does not wipe the stress it already carries, and leaving it hands the meter back
     * at the same fraction; a lockdown cycle can never lower the meter faster than its normal decay.
     *
     * @param load        the meter now (non-finite or negative reads as 0; +infinity as the 1e9 cap)
     * @param oldCapacity the capacity the meter was measured against
     * @param newCapacity the capacity it is measured against from now on
     * @return the rescaled meter, in {@code 0..1e9}; unchanged (capped) when either capacity is not positive
     */
    public static double rescaleLoad(double load, double oldCapacity, double newCapacity) {
        if (Double.isNaN(load) || load <= 0.0D) {
            return 0.0D;
        }
        double capped = Math.min(1.0E9D, load);
        if (!(oldCapacity > 0.0D) || !(newCapacity > 0.0D) || Double.isInfinite(oldCapacity)
                || Double.isInfinite(newCapacity)) {
            return capped;
        }
        double scaled = capped * newCapacity / oldCapacity;
        return Double.isFinite(scaled) ? Math.max(0.0D, Math.min(1.0E9D, scaled)) : capped;
    }
}
