package za.co.neroland.nerosecurity.field;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerosecurity.field.FieldStateMachine.Action;
import za.co.neroland.nerosecurity.field.FieldStateMachine.Inputs;
import za.co.neroland.nerosecurity.field.FieldStateMachine.Result;

/** The emitter's state machine. Pure. */
class FieldStateMachineTest {

    private static final int DRAW = 10;
    private static final long THRESHOLD = 1000L;
    private static final double CAPACITY = 40.0;
    private static final int GRACE = 20;
    private static final int COOLDOWN = 1200;

    private static Inputs in(boolean fieldsEnabled, long energy, double load, boolean breachable, long cooldownUntil,
            long now, int brownoutTicks) {
        return new Inputs(true, fieldsEnabled, true, true, energy, DRAW, THRESHOLD, load, CAPACITY, breachable,
                cooldownUntil, now, brownoutTicks, GRACE, COOLDOWN, true);
    }

    @Test
    void overloadCollapseCooldownRestore() {
        Result overload = FieldStateMachine.step(FieldState.PROJECTING, in(true, 100_000L, CAPACITY, true, 0L, 500L, 0));
        assertEquals(FieldState.OVERLOADED, overload.state());
        assertEquals(Action.COLLAPSE_OVERLOAD, overload.action());
        assertEquals(500L + COOLDOWN, overload.cooldownUntil());
        assertEquals(0.0, overload.load());

        long until = overload.cooldownUntil();
        Result waiting = FieldStateMachine.step(FieldState.OVERLOADED, in(true, 100_000L, 0.0, true, until, until - 1, 0));
        assertEquals(FieldState.OVERLOADED, waiting.state(), "full energy does not end the cooldown");
        assertEquals(Action.NONE, waiting.action());

        Result restored = FieldStateMachine.step(FieldState.OVERLOADED, in(true, THRESHOLD, 0.0, true, until, until, 0));
        assertEquals(FieldState.PROJECTING, restored.state());
        assertEquals(Action.RESTORE, restored.action());

        Result starved = FieldStateMachine.step(FieldState.OVERLOADED, in(true, THRESHOLD - 1, 0.0, true, until, until, 0));
        assertEquals(FieldState.OVERLOADED, starved.state(), "cooled down but below the restart threshold");

        Result unbreachable = FieldStateMachine.step(FieldState.PROJECTING,
                in(true, 100_000L, CAPACITY * 10, false, 0L, 500L, 0));
        assertEquals(FieldState.PROJECTING, unbreachable.state(), "breachable=false: load never overloads");
        assertEquals(Action.NONE, unbreachable.action());
    }

    @Test
    void brownoutGraceAndRestart() {
        int ticks = 0;
        for (int i = 0; i < GRACE; i++) {
            Result r = FieldStateMachine.step(FieldState.PROJECTING, in(true, DRAW - 1, 0.0, true, 0L, i, ticks));
            assertEquals(FieldState.PROJECTING, r.state(), "within grace, tick " + i);
            assertEquals(Action.NONE, r.action());
            ticks = r.brownoutTicks();
        }
        assertEquals(GRACE, ticks);
        Result collapse = FieldStateMachine.step(FieldState.PROJECTING, in(true, DRAW - 1, 0.0, true, 0L, GRACE, ticks));
        assertEquals(FieldState.BROWNOUT, collapse.state());
        assertEquals(Action.COLLAPSE_BROWNOUT, collapse.action());

        Result between = FieldStateMachine.step(FieldState.BROWNOUT, in(true, DRAW * 5L, 0.0, true, 0L, 100L, 0));
        assertEquals(FieldState.BROWNOUT, between.state(), "enough for one tick is not enough to restart");
        Result back = FieldStateMachine.step(FieldState.BROWNOUT, in(true, THRESHOLD, 0.0, true, 0L, 100L, 0));
        assertEquals(FieldState.PROJECTING, back.state());
        assertEquals(Action.RESTORE, back.action());

        Result recovered = FieldStateMachine.step(FieldState.PROJECTING, in(true, DRAW, 0.0, true, 0L, 5L, 7));
        assertEquals(0, recovered.brownoutTicks(), "one powered tick resets the grace counter");

        // Idle wins over everything, and keeps the cooldown.
        Result idle = FieldStateMachine.step(FieldState.OVERLOADED, in(false, 100_000L, 0.0, true, 9_999L, 10L, 0));
        assertEquals(FieldState.IDLE, idle.state());
        assertEquals(9_999L, idle.cooldownUntil());
        Result idleCollapse = FieldStateMachine.step(FieldState.PROJECTING, in(false, 100_000L, 0.0, true, 0L, 10L, 0));
        assertEquals(Action.COLLAPSE_IDLE, idleCollapse.action());
        Result stillCooling = FieldStateMachine.step(FieldState.IDLE, in(true, 100_000L, 0.0, true, 9_999L, 10L, 0));
        assertEquals(FieldState.IDLE, stillCooling.state(), "an overload cooldown survives going idle");
        Result start = FieldStateMachine.step(FieldState.IDLE, in(true, THRESHOLD, 0.0, true, 9_999L, 9_999L, 0));
        assertEquals(FieldState.PROJECTING, start.state());
        assertEquals(Action.PROJECT, start.action());
    }
}
