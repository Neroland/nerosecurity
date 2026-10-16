package za.co.neroland.nerosecurity.block.chest;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.block.DeviceAccessSettings;

/**
 * The secure chest's automation gate, as pure rules plus the one entry point every loader's item
 * capability wrapper calls.
 *
 * <p>Automation (hoppers, droppers, pipes, item conduits) acts as a virtual actor at the installation's
 * automation policy level. It may move items iff the chest is linked to nothing (a plain chest), or:
 * the policy is not off, the policy reaches the chest's minimum level, the chest is not keycard-only,
 * and the installation is not in lockdown. The policy is capped at OFFICER, so automation can never
 * pass a lockdown or stand in for an owner.
 *
 * <p>The gate is enforced twice: by {@link SecureChestBlockEntity} as a {@code WorldlyContainer}
 * (vanilla hoppers on every loader) and by the loader capability wrappers, which are always present and
 * call {@link #allows(Object)} on every single operation, so a policy, settings or lockdown change
 * applies on the next transfer with no capability invalidation.
 */
public final class GatedChestAccess {

    private GatedChestAccess() {
    }

    /**
     * Whether automation may move items in or out of a secure chest.
     *
     * @param linked          whether the chest is linked to an installation
     * @param policy          the installation's automation level; {@code null} means off
     * @param settings        the chest's own access settings
     * @param lockdownActive  whether lockdown is enabled on the server and active on the installation
     */
    public static boolean automationAllowed(boolean linked, @Nullable AccessLevel policy,
            @Nullable DeviceAccessSettings settings, boolean lockdownActive) {
        if (!linked) {
            return true; // an unlinked secure chest is just a chest
        }
        AccessLevel actor = cappedPolicy(policy);
        if (actor == null || settings == null) {
            return false;
        }
        return actor.atLeast(settings.minLevel()) && !settings.keycardOnly() && !lockdownActive;
    }

    /**
     * The virtual actor's level for a stored policy: {@code null} (off) for nothing or PUBLIC, and never
     * above OFFICER whatever the store says.
     */
    static @Nullable AccessLevel cappedPolicy(@Nullable AccessLevel policy) {
        if (policy == null || policy == AccessLevel.PUBLIC) {
            return null;
        }
        return policy == AccessLevel.OWNER ? AccessLevel.OFFICER : policy;
    }

    /**
     * The capability wrappers' gate: true only for a secure chest whose automation is allowed right now.
     * Anything else (another block entity, a removed chest, the client side) is refused.
     */
    public static boolean allows(@Nullable Object blockEntity) {
        return blockEntity instanceof SecureChestBlockEntity chest && chest.isAutomationAllowed();
    }

    /**
     * Whether an event that was last raised at game time {@code last} may be raised again at {@code now}.
     * {@code Long.MIN_VALUE} means never raised. A clock that went backwards counts as elapsed.
     */
    public static boolean cooldownElapsed(long last, long now, long cooldownTicks) {
        if (last == Long.MIN_VALUE || cooldownTicks <= 0 || now < last) {
            return true;
        }
        return now - last >= cooldownTicks;
    }
}
