package za.co.neroland.nerosecurity.block.door;

import za.co.neroland.nerosecurity.access.AccessDecision;
import za.co.neroland.nerosecurity.access.AccessLevel;

/**
 * The door's pure rules, kept free of world and config access so they run in a plain JVM. The block
 * entity feeds them config values and its own state; nothing here decides access on its own: every
 * rule that takes an {@link AccessDecision} refines a decision the resolver already made.
 */
public final class DoorRules {

    /** The shortest open/close animation, whatever modules are fitted. */
    public static final int MIN_OPEN_TICKS = 5;
    /** Each hardness module multiplies blast resistance and divides break speed by this. */
    public static final double HARDNESS_FACTOR = 1.5D;

    private DoorRules() {
    }

    /** Base ticks divided by {@code 1 + 0.5 x modules}, rounded, never under {@link #MIN_OPEN_TICKS}. */
    public static int openDuration(int baseTicks, int openSpeedModules) {
        int modules = Math.max(0, openSpeedModules);
        return Math.max(MIN_OPEN_TICKS, (int) Math.round(baseTicks / (1.0D + 0.5D * modules)));
    }

    /**
     * How much tougher hardness modules make a door: {@code 1.5^modules}, limited so the resulting
     * blast resistance never exceeds {@code cap}. Never below 1, so modules never weaken a door.
     */
    public static double hardnessMultiplier(double baseResistance, int hardnessModules, double cap) {
        double multiplier = Math.pow(HARDNESS_FACTOR, Math.max(0, hardnessModules));
        if (baseResistance > 0.0D && cap > 0.0D) {
            multiplier = Math.min(multiplier, Math.max(1.0D, cap / baseResistance));
        }
        return Math.max(1.0D, multiplier);
    }

    /** Blast resistance with hardness modules, capped at {@code cap} (the base itself is capped too). */
    public static float blastResistance(float baseResistance, int hardnessModules, double cap) {
        double scaled = baseResistance * hardnessMultiplier(baseResistance, hardnessModules, cap);
        return (float) Math.min(scaled, Math.max(0.0D, cap));
    }

    /** Break progress per tick with hardness modules: vanilla progress divided by the same multiplier. */
    public static float destroyProgress(float vanillaProgress, float baseResistance, int hardnessModules, double cap) {
        return (float) (vanillaProgress / hardnessMultiplier(baseResistance, hardnessModules, cap));
    }

    /** Whether the buffer may open the door for anyone: always when power is not required. */
    public static boolean hasPowerToOpen(boolean requirePower, long energy, long openCost) {
        return !requirePower || energy >= openCost;
    }

    /** OWNER and the operator override open an unpowered door by hand; nobody else does. */
    public static boolean mayOpenUnpowered(AccessLevel effective, boolean admin) {
        return admin || effective == AccessLevel.OWNER;
    }

    /**
     * The power gate applied after the resolver allowed OPEN. Fail-secure for strangers, never for the
     * owner: below OWNER an unpowered door refuses, OWNER/ADMIN pass.
     */
    public static boolean passesPowerGate(AccessDecision decision, boolean hasPowerToOpen) {
        return decision.allowed() && (hasPowerToOpen || mayOpenUnpowered(decision.effective(), decision.admin()));
    }

    /** Whether the decision came from an OWNER or the operator override (keeps a door open in lockdown). */
    public static boolean isPrivileged(AccessDecision decision) {
        return decision.allowed() && mayOpenUnpowered(decision.effective(), decision.admin());
    }

    /**
     * Whether a redstone signal may open the door: the mode must ask for it, the server must allow door
     * redstone, the installation must not be in lockdown, and the door must have power to open.
     */
    public static boolean redstoneMayOpen(RedstoneMode mode, boolean redstoneAllowed, boolean powered,
            boolean lockdownActive, boolean hasPowerToOpen) {
        return mode == RedstoneMode.OPEN_WHILE_POWERED && redstoneAllowed && powered && !lockdownActive
                && hasPowerToOpen;
    }

    /** An open door closes on lockdown unless an OWNER or operator opened it this cycle. */
    public static boolean lockdownCloses(boolean lockdownActive, boolean openedPrivileged) {
        return lockdownActive && !openedPrivileged;
    }

    /**
     * The door's {@code ANIMATING} look: from the moment it starts opening until it is shut and still again
     * (so an open door is drawn by the renderer with its panels retracted).
     */
    public static boolean animating(boolean opening, int openTicks) {
        return opening || openTicks > 0;
    }

    /**
     * The door's power-low LED: a linked door that needs power and cannot afford an open. An unlinked door, or
     * a server that does not require power, never shows it.
     */
    public static boolean powerLow(boolean linked, boolean requirePower, long energy, long openCost) {
        return linked && !hasPowerToOpen(requirePower, energy, openCost);
    }

    /** Whether a denied event may be published again for one actor at one door. */
    public static boolean deniedEventDue(long lastAt, long now, int cooldownTicks) {
        return lastAt == Long.MIN_VALUE || now - lastAt >= Math.max(0, cooldownTicks);
    }
}
