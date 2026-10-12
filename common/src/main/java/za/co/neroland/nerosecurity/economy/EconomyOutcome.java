package za.co.neroland.nerosecurity.economy;

import net.minecraft.network.chat.Component;

/**
 * What an optional NeroEconomy call did, in NeroSecurity's own type so no caller ever touches a NeroEconomy class.
 *
 * <ul>
 *   <li>{@link Status#APPLIED}: the credits moved, or the same request key was already applied (a safe
 *       retry). Grant what was paid for.</li>
 *   <li>{@link Status#REFUSED}: nothing moved (not enough credits, today's budget spent, a malformed
 *       request, ...). Do not grant; show {@link #message()} to the player.</li>
 *   <li>{@link Status#ABSENT}: NeroEconomy is not installed (or too old). Nothing moved; the caller decides
 *       the fallback (free, configured another way, or unavailable).</li>
 * </ul>
 *
 * @param status         the outcome
 * @param translationKey a lang key with a short, plain explanation for the player
 */
public record EconomyOutcome(Status status, String translationKey) {

    /** The lang key shown when NeroEconomy is not installed. */
    public static final String ABSENT_KEY = "economy_result.nerosecurity.absent";

    /** The NeroEconomy lang key for a malformed request, used when NeroSecurity rejects one before sending it. */
    public static final String INVALID_KEY = "economy_result.neroeconomy.invalid";

    /** The outcome of one call. */
    public enum Status { APPLIED, REFUSED, ABSENT }

    private static final EconomyOutcome ABSENT = new EconomyOutcome(Status.ABSENT, ABSENT_KEY);
    private static final EconomyOutcome INVALID = new EconomyOutcome(Status.REFUSED, INVALID_KEY);

    /** Whether the request stands as applied: grant what was paid for. */
    public boolean ok() {
        return status == Status.APPLIED;
    }

    /** Whether NeroEconomy is missing, so nothing could move. */
    public boolean absent() {
        return status == Status.ABSENT;
    }

    /** The explanation to show the player. */
    public Component message() {
        return Component.translatable(translationKey);
    }

    static EconomyOutcome absentOutcome() {
        return ABSENT;
    }

    static EconomyOutcome invalid() {
        return INVALID;
    }

    static EconomyOutcome applied(String translationKey) {
        return new EconomyOutcome(Status.APPLIED, translationKey);
    }

    static EconomyOutcome refused(String translationKey) {
        return new EconomyOutcome(Status.REFUSED, translationKey);
    }
}
