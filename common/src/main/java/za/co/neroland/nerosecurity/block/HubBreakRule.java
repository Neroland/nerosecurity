package za.co.neroland.nerosecurity.block;

import za.co.neroland.nerosecurity.access.AccessDecision;

/**
 * Who may break a Security Hub, as a pure rule: the resolver must allow {@code DISSOLVE} (a stored OWNER, or an
 * operator), <em>and</em> the breaker must mean it. A stored owner says so by sneaking; an operator's override
 * already is the deliberate act (operators are let through every other device without sneaking, and the sneak
 * gate on the hub was the one place that still refused them, which read as "a block I placed cannot be broken").
 *
 * <p>The rule never loosens anything for a non-owner: without an allowing decision nothing breaks the hub,
 * sneaking or not, in creative or not. Plain JVM, unit-tested.
 */
public final class HubBreakRule {

    private HubBreakRule() {
    }

    /**
     * @param sneaking whether the breaker holds sneak (server-side flag)
     * @param decision the resolver's answer for {@code DISSOLVE}; null is a refusal
     * @return whether the break may go ahead
     */
    public static boolean mayBreak(boolean sneaking, AccessDecision decision) {
        if (decision == null || !decision.allowed()) {
            return false;
        }
        return sneaking || decision.admin();
    }
}
