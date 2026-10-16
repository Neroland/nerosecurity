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
}
