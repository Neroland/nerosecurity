package za.co.neroland.nerosecurity.field;

/**
 * The emitter's state machine, evaluated once per server tick. Pure: the block entity gathers {@link Inputs},
 * calls {@link #step}, then applies the {@link Result} (removes blocks on a collapse, publishes the event, draws
 * power while projecting).
 *
 * <pre>
 * Transitions, in this order:
 *   any        -&gt; IDLE        not linked | fields disabled on the server | emitter switched off | settings invalid
 *                               (COLLAPSE_IDLE when it was holding blocks; cooldown and load preserved; no event)
 *   PROJECTING -&gt; OVERLOADED  load &gt;= capacity and fields are breachable (COLLAPSE_OVERLOAD; cooldown starts; load 0)
 *   PROJECTING -&gt; BROWNOUT    energy &lt; draw for more than the grace ticks (COLLAPSE_BROWNOUT)
 *   OVERLOADED -&gt; PROJECTING  cooldown over and energy &gt;= restart threshold (RESTORE)
 *   BROWNOUT   -&gt; PROJECTING  energy &gt;= restart threshold (RESTORE)
 *   IDLE       -&gt; PROJECTING  cooldown over and energy &gt;= restart threshold (PROJECT)
 * </pre>
 *
 * With {@code breachable == false} the overload transition is unreachable.
 */
public final class FieldStateMachine {

    /** What the block entity must do after a step. */
    public enum Action {
        NONE,
        /** Start projecting from IDLE (no event; the block entity publishes FIELD_RESTORED only after a collapse). */
        PROJECT,
        /** Remove the field: out of energy (FIELD_COLLAPSED, POWER_LOST once). */
        COLLAPSE_BROWNOUT,
        /** Remove the field: overloaded (FIELD_OVERLOADED). */
        COLLAPSE_OVERLOAD,
        /** Remove the field: it went idle (no event). */
        COLLAPSE_IDLE,
        /** A collapsed field forms again (FIELD_RESTORED). */
        RESTORE
    }

    /**
     * One tick's facts.
     *
     * @param linked             the emitter is linked to an installation
     * @param fieldsEnabled      the server's {@code forceFieldsEnabled}
     * @param settingsEnabled    the emitter's own on switch
     * @param settingsValid      the settings fit the emitter's range and the area cap
     * @param energy             stored energy
     * @param drawPerTick        energy per tick while projecting (module strength, never the lockdown boost)
     * @param restartThreshold   energy needed to form or re-form
     * @param load               the overload meter
     * @param loadCapacity       the meter's capacity at the effective strength
     * @param breachable         the server's {@code forceFieldBreachable}
     * @param cooldownUntil      game time an overload cooldown ends (0 = none)
     * @param gameTime           now, in game ticks
     * @param brownoutTicks      consecutive projecting ticks without enough energy so far
     * @param brownoutGraceTicks how many such ticks are tolerated
     * @param overloadCooldownTicks the cooldown an overload starts
     * @param holdingBlocks      the emitter currently owns field blocks
     */
    public record Inputs(boolean linked, boolean fieldsEnabled, boolean settingsEnabled, boolean settingsValid,
            long energy, int drawPerTick, long restartThreshold, double load, double loadCapacity, boolean breachable,
            long cooldownUntil, long gameTime, int brownoutTicks, int brownoutGraceTicks, int overloadCooldownTicks,
            boolean holdingBlocks) {
    }

    /** The new state and what to do; the block entity stores {@code brownoutTicks}, {@code cooldownUntil}, {@code load}. */
    public record Result(FieldState state, Action action, int brownoutTicks, long cooldownUntil, double load) {
    }

    private FieldStateMachine() {
    }

    public static Result step(FieldState current, Inputs in) {
        FieldState state = current == null ? FieldState.IDLE : current;
        // 1. Idle wins over everything.
        if (!in.linked() || !in.fieldsEnabled() || !in.settingsEnabled() || !in.settingsValid()) {
            boolean collapse = state == FieldState.PROJECTING || in.holdingBlocks();
            return new Result(FieldState.IDLE, collapse ? Action.COLLAPSE_IDLE : Action.NONE, 0, in.cooldownUntil(), in.load());
        }
        boolean cooled = in.gameTime() >= in.cooldownUntil();
        boolean canStart = in.energy() >= in.restartThreshold();
        return switch (state) {
            case IDLE -> cooled && canStart
                    ? new Result(FieldState.PROJECTING, Action.PROJECT, 0, in.cooldownUntil(), in.load())
                    : new Result(FieldState.IDLE, Action.NONE, 0, in.cooldownUntil(), in.load());
            case PROJECTING -> projecting(in);
            case OVERLOADED -> cooled && canStart
                    ? new Result(FieldState.PROJECTING, Action.RESTORE, 0, in.cooldownUntil(), in.load())
                    : new Result(FieldState.OVERLOADED, Action.NONE, 0, in.cooldownUntil(), in.load());
            case BROWNOUT -> canStart
                    ? new Result(FieldState.PROJECTING, Action.RESTORE, 0, in.cooldownUntil(), in.load())
                    : new Result(FieldState.BROWNOUT, Action.NONE, 0, in.cooldownUntil(), in.load());
        };
    }

    private static Result projecting(Inputs in) {
        if (in.breachable() && in.load() >= in.loadCapacity()) {
            long until = in.gameTime() + Math.max(0, in.overloadCooldownTicks());
            return new Result(FieldState.OVERLOADED, Action.COLLAPSE_OVERLOAD, 0, until, 0.0);
        }
        if (in.energy() < in.drawPerTick()) {
            int starved = in.brownoutTicks() + 1;
            if (starved > Math.max(0, in.brownoutGraceTicks())) {
                return new Result(FieldState.BROWNOUT, Action.COLLAPSE_BROWNOUT, 0, in.cooldownUntil(), in.load());
            }
            return new Result(FieldState.PROJECTING, Action.NONE, starved, in.cooldownUntil(), in.load());
        }
        return new Result(FieldState.PROJECTING, Action.NONE, 0, in.cooldownUntil(), in.load());
    }
}
