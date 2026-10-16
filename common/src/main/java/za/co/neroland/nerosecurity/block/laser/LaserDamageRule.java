package za.co.neroland.nerosecurity.block.laser;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.installation.Installation;

/**
 * Who a tripped laser may hurt (when {@code laserTripDamage > 0}). Pure, plain-JVM tested.
 *
 * <p><b>A laser never damages anyone who holds stored ownership or membership in the installation</b>,
 * at any level, even during a lockdown, when they classify as not permitted and are detected and logged
 * like anyone else. A drill, or a lockdown an officer started, must never become a way to hurt members in
 * their own base. The subject is the UUID the sensor judged: the player, or a tamed animal's owner (so a
 * member's pet is spared too). Mobs without an owner have no subject and may be hurt.
 */
public final class LaserDamageRule {

    private LaserDamageRule() {
    }

    /** Whether a trip by {@code subject} (null for an unowned mob) may deal damage. */
    public static boolean mayHarm(@Nullable Installation installation, @Nullable UUID subject) {
        if (subject == null) {
            return true;
        }
        if (installation == null) {
            return false; // nothing to judge against: never hurt a player on a guess
        }
        return !installation.isOwner(subject) && !installation.members().containsKey(subject);
    }
}
