package za.co.neroland.nerosecurity.menu.alarm;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.access.AccessAction;
import za.co.neroland.nerosecurity.access.AccessDecision;
import za.co.neroland.nerosecurity.access.AccessResolver;
import za.co.neroland.nerosecurity.block.alarm.AlarmBlockEntity;
import za.co.neroland.nerosecurity.block.alarm.AlarmResponse;
import za.co.neroland.nerosecurity.block.alarm.AlarmRules;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.event.SecurityEvent;
import za.co.neroland.nerosecurity.event.SecurityEventBus;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.menu.MenuOpener;
import za.co.neroland.nerosecurity.menu.alarm.AlarmPayloads.AlarmConfigure;
import za.co.neroland.nerosecurity.menu.alarm.AlarmPayloads.AlarmSilence;
import za.co.neroland.nerosecurity.menu.alarm.AlarmPayloads.AlarmSnapshot;
import za.co.neroland.nerosecurity.platform.Services;
import za.co.neroland.nerosecurity.telemetry.NeroSecurityTelemetry;

/**
 * Server side of the alarm settings screen. Everything starts from nothing the client claims: the
 * sender's open menu must be an {@link AlarmMenu} bound to the same position, the block there must still
 * be an alarm, the installation is looked up again from the store, and every change is authorised again
 * by the resolver:
 *
 * <ul>
 *   <li>subscriptions and responses: {@code CONFIGURE_DEVICE} at OFFICER;</li>
 *   <li>switching the lockdown response on or off: additionally {@code CONFIGURE_ALARM_LOCKDOWN} at
 *       OWNER, and switching it on needs the server to allow alarm lockdowns; otherwise the <b>whole</b>
 *       payload is refused (nothing else in it is applied either);</li>
 *   <li>any edit at all to an alarm whose lockdown response is on: {@code CONFIGURE_ALARM_LOCKDOWN} at
 *       OWNER, so an officer cannot turn the owner's lockdown alarm into a tripwire by changing what it
 *       listens to; otherwise the whole payload is refused;</li>
 *   <li>silencing: {@code SILENCE_ALARM} at OFFICER, capped by {@code alarmSilenceMaxMinutes}.</li>
 * </ul>
 *
 * <p>An unlinked alarm has no settings screen (it reacts to nothing until linked, and linking resets
 * it). Every handler is guarded: an unexpected exception fails closed, is reported by payload type and
 * exception class only, and the screen closes.
 */
public final class AlarmPayloadHandlers {

    private AlarmPayloadHandlers() {
    }

    /** Sneak-use on an alarm: authorise, send the snapshot, open the settings menu. */
    public static void openSettings(ServerPlayer player, AlarmBlockEntity alarm) {
        runSafely(player, "alarm_open_settings", () -> {
            Installation installation = alarm.installation();
            if (installation == null) {
                player.sendOverlayMessage(Component.translatable("device.nerosecurity.unlinked"));
                return;
            }
            AccessDecision decision = resolve(player, alarm, installation, AccessAction.CONFIGURE_DEVICE);
            if (!decision.allowed()) {
                player.sendOverlayMessage(decision.message());
                return;
            }
            BlockPos pos = alarm.getBlockPos().immutable();
            sendSnapshot(player, alarm, decision);
            MenuOpener.open(player, new SimpleMenuProvider(
                    (containerId, inventory, opener) -> new AlarmMenu(containerId, inventory, pos,
                            ContainerLevelAccess.create(alarm.getLevel(), pos)),
                    Component.translatable("alarm.nerosecurity.title")));
        });
    }

    public static void handleConfigure(AlarmConfigure payload, ServerPlayer player) {
        runSafely(player, "alarm_configure", () -> {
            AlarmBlockEntity alarm = bound(player, payload.pos());
            if (alarm == null) {
                return;
            }
            Installation installation = authorised(player, alarm);
            if (installation == null) {
                return;
            }
            Set<SecurityEventType> wantedTypes = AlarmRules.sanitiseSubscribed(payload.subscribed());
            Set<AlarmResponse> wantedResponses = AlarmRules.copyResponses(payload.responses());
            Set<AlarmResponse> currentResponses = alarm.responses();
            Set<SecurityEventType> currentTypes = alarm.subscribed();
            boolean needsOwner = AlarmRules.editNeedsOwner(currentTypes, currentResponses, wantedTypes,
                    wantedResponses);
            boolean ownerMay = needsOwner
                    && resolve(player, alarm, installation, AccessAction.CONFIGURE_ALARM_LOCKDOWN).allowed();
            switch (AlarmRules.configureChange(currentTypes, currentResponses, wantedTypes, wantedResponses, ownerMay,
                    serverAllowsLockdown())) {
                case OWNER_ONLY -> {
                    player.sendSystemMessage(Component.translatable("alarm.nerosecurity.lockdown.owner_only"));
                    refresh(player, alarm);
                    return;
                }
                case LOCKDOWN_ARMED_OWNER_ONLY -> {
                    player.sendSystemMessage(Component.translatable("alarm.nerosecurity.lockdown.owner_only_armed"));
                    refresh(player, alarm);
                    return;
                }
                case SERVER_DISABLED -> {
                    player.sendSystemMessage(Component.translatable("alarm.nerosecurity.lockdown.server_disabled"));
                    refresh(player, alarm);
                    return;
                }
                default -> {
                    // UNCHANGED or ALLOWED: apply.
                }
            }
            boolean changed = !wantedTypes.equals(currentTypes) || !wantedResponses.equals(currentResponses);
            if (changed) {
                alarm.applySettings(wantedTypes, wantedResponses);
                publish(player, alarm, installation, SecurityEventType.DEVICE_CONFIGURED);
            }
            player.sendOverlayMessage(Component.translatable("alarm.nerosecurity.configured"));
            refresh(player, alarm);
        });
    }

    public static void handleSilence(AlarmSilence payload, ServerPlayer player) {
        runSafely(player, "alarm_silence", () -> {
            AlarmBlockEntity alarm = bound(player, payload.pos());
            if (alarm == null) {
                return;
            }
            Installation installation = alarm.installation();
            if (installation == null) {
                player.closeContainer();
                player.sendSystemMessage(Component.translatable("device.nerosecurity.unlinked"));
                return;
            }
            AccessDecision decision = resolve(player, alarm, installation, AccessAction.SILENCE_ALARM);
            if (!decision.allowed()) {
                player.sendSystemMessage(decision.message());
                refresh(player, alarm);
                return;
            }
            int minutes = AlarmRules.clampSilenceMinutes(payload.minutes(), NeroSecurityConfig.alarmSilenceMaxMinutes());
            alarm.silenceUntil(AlarmRules.silencedUntil(System.currentTimeMillis(), minutes));
            publish(player, alarm, installation, SecurityEventType.ALARM_SILENCED);
            player.sendOverlayMessage(Component.translatable("alarm.nerosecurity.silenced", minutes));
            refresh(player, alarm);
        });
    }

    // --- shared ---------------------------------------------------------------------------------

    static boolean serverAllowsLockdown() {
        return NeroSecurityConfig.isLockdownEnabled() && NeroSecurityConfig.isAlarmLockdownEnabled();
    }

    /** The installation if the sender may still configure this alarm; otherwise closes and returns null. */
    private static @Nullable Installation authorised(ServerPlayer player, AlarmBlockEntity alarm) {
        Installation installation = alarm.installation();
        if (installation == null) {
            player.closeContainer();
            player.sendSystemMessage(Component.translatable("device.nerosecurity.unlinked"));
            return null;
        }
        AccessDecision decision = resolve(player, alarm, installation, AccessAction.CONFIGURE_DEVICE);
        if (!decision.allowed()) {
            player.closeContainer();
            player.sendSystemMessage(decision.message());
            return null;
        }
        return installation;
    }

    static void sendSnapshot(ServerPlayer player, AlarmBlockEntity alarm, AccessDecision decision) {
        long now = System.currentTimeMillis();
        Services.NETWORK.sendToPlayer(player, new AlarmSnapshot(alarm.getBlockPos().immutable(), alarm.subscribed(),
                alarm.responses(), AlarmRules.silencedForSeconds(now, alarm.silencedUntil()), decision.effective(),
                decision.admin(), serverAllowsLockdown(),
                Math.max(1, (NeroSecurityConfig.alarmLockdownAutoReleaseSeconds() + 59) / 60)));
    }

    /** Re-sends the snapshot while the player may still configure the alarm; closes the screen otherwise. */
    private static void refresh(ServerPlayer player, AlarmBlockEntity alarm) {
        Installation installation = alarm.installation();
        if (installation == null) {
            player.closeContainer();
            return;
        }
        AccessDecision decision = resolve(player, alarm, installation, AccessAction.CONFIGURE_DEVICE);
        if (decision.allowed()) {
            sendSnapshot(player, alarm, decision);
        } else {
            player.closeContainer();
            player.sendSystemMessage(decision.message());
        }
    }

    /** The action at its default minimum, never keycard filtered. */
    private static AccessDecision resolve(ServerPlayer player, AlarmBlockEntity alarm, Installation installation,
            AccessAction action) {
        DevicePos at = player.level() instanceof ServerLevel level ? DevicePos.of(level, alarm.getBlockPos()) : null;
        return AccessResolver.DEFAULT.resolve(player, installation, action, at);
    }

    private static void publish(ServerPlayer player, AlarmBlockEntity alarm, Installation installation,
            SecurityEventType type) {
        if (player.level() instanceof ServerLevel level) {
            SecurityEventBus.publish(SecurityEvent.of(type, installation.id(), DevicePos.of(level, alarm.getBlockPos()),
                    player.getUUID(), System.currentTimeMillis()));
        }
    }

    /**
     * The alarm the sender's open settings menu is bound to, if it matches {@code claimed}; null (and a
     * debug line) for a stale or forged payload.
     */
    private static @Nullable AlarmBlockEntity bound(ServerPlayer player, @Nullable BlockPos claimed) {
        if (claimed == null || !(player.containerMenu instanceof AlarmMenu menu)
                || !claimed.equals(menu.pos()) || !menu.stillValid(player)
                || !(player.level().getBlockEntity(claimed) instanceof AlarmBlockEntity alarm)) {
            NeroSecurityCommon.LOGGER.debug("[NeroSecurity] stale alarm payload dropped");
            return null;
        }
        return alarm;
    }

    /**
     * Runs a handler body; an unexpected failure fails closed. Logged and reported by payload type and
     * exception class only (never a name, UUID or position); the screen closes and the sender is told.
     */
    private static void runSafely(ServerPlayer player, String payloadType, Runnable body) {
        try {
            body.run();
        } catch (RuntimeException | LinkageError e) {
            NeroSecurityCommon.LOGGER.warn("[NeroSecurity] alarm payload {} failed ({}); nothing was changed after the failure.",
                    payloadType, e.getClass().getName());
            NeroSecurityTelemetry.captureHandledException(e, "alarm_payload", payloadType);
            try {
                player.closeContainer();
                player.sendSystemMessage(Component.translatable("hub.nerosecurity.failed"));
            } catch (RuntimeException | LinkageError ignored) {
                // The player simply does not get the notice.
            }
        }
    }
}
