package za.co.neroland.nerosecurity.lockdown;

import java.util.Optional;
import java.util.function.BooleanSupplier;

import net.minecraft.resources.Identifier;

import za.co.neroland.nerolandcore.event.ThresholdEvents;
import za.co.neroland.nerolandcore.event.ThresholdEvents.ThresholdCrossing;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.event.SecurityEvent;
import za.co.neroland.nerosecurity.event.SecurityEventBus;

/**
 * Publishes lockdown transitions on Neroland Core's threshold channel {@code nerosecurity:lockdown},
 * so quest packs and other mods can react to a base locking down without depending on NeroSecurity.
 *
 * <p>One crossing per transition: value 1 and rising on entry; value 0 and falling on exit, an
 * operator unlock or an alarm lockdown releasing itself. An owner confirming an alarm lockdown is not
 * a transition and publishes nothing. The scope is the installation's opaque {@code inst:<id>} key and nothing else: never
 * a player, a name or a position. Fires only while both {@code lockdownChannelEnabled} and
 * {@code lockdownEnabled} are on. A failure on Core's side is swallowed so it can never break lockdown.
 */
public final class LockdownChannel {

    public static final Identifier CHANNEL = Identifier.fromNamespaceAndPath(NeroSecurityCommon.MOD_ID, "lockdown");

    private static SecurityEventBus.Subscription subscription;

    private LockdownChannel() {
    }

    /** Init step 4c: subscribes to the security event bus. Idempotent. */
    public static synchronized void init() {
        if (subscription == null) {
            subscription = SecurityEventBus.subscribe(
                    listener(NeroSecurityConfig::isLockdownChannelEnabled, NeroSecurityConfig::isLockdownEnabled));
        }
    }

    /** A bus listener with injectable flags (production passes the config accessors). */
    public static SecurityEventBus.Listener listener(BooleanSupplier channelEnabled, BooleanSupplier lockdownEnabled) {
        return event -> {
            Optional<ThresholdCrossing> crossing = crossingFor(event);
            if (crossing.isEmpty() || !channelEnabled.getAsBoolean() || !lockdownEnabled.getAsBoolean()) {
                return;
            }
            try {
                ThresholdEvents.fire(NeroSecurityCommon.MOD_ID, crossing.get());
            } catch (RuntimeException | LinkageError e) {
                NeroSecurityCommon.LOGGER.warn("[NeroSecurity] lockdown channel publish failed ({}).",
                        e.getClass().getName());
            }
        };
    }

    /** The crossing an event maps to, if it is a lockdown transition. */
    public static Optional<ThresholdCrossing> crossingFor(SecurityEvent event) {
        boolean rising;
        switch (event.type()) {
            case LOCKDOWN_ENTERED -> rising = true;
            case LOCKDOWN_EXITED, ADMIN_UNLOCK, LOCKDOWN_AUTO_RELEASED -> rising = false;
            default -> {
                return Optional.empty();
            }
        }
        return Optional.of(new ThresholdCrossing(CHANNEL, "inst:" + event.installationId(), rising ? 1L : 0L, 1L, rising));
    }
}
