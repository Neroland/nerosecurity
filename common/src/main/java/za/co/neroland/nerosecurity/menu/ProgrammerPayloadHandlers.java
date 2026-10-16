package za.co.neroland.nerosecurity.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.access.AccessAction;
import za.co.neroland.nerosecurity.access.AccessDecision;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.access.AccessResolver;
import za.co.neroland.nerosecurity.block.entity.KeycardProgrammerBlockEntity;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.installation.ServiceResult;
import za.co.neroland.nerosecurity.item.KeycardItem;
import za.co.neroland.nerosecurity.keycard.KeycardService;
import za.co.neroland.nerosecurity.menu.ProgrammerPayloads.ProgrammerClone;
import za.co.neroland.nerosecurity.menu.ProgrammerPayloads.ProgrammerSnapshot;
import za.co.neroland.nerosecurity.menu.ProgrammerPayloads.ProgrammerWipe;
import za.co.neroland.nerosecurity.menu.ProgrammerPayloads.ProgrammerWrite;
import za.co.neroland.nerosecurity.platform.Services;
import za.co.neroland.nerosecurity.sound.SecuritySounds;
import za.co.neroland.nerosecurity.telemetry.NeroSecurityTelemetry;

/**
 * Server-side handlers for the programmer screen's payloads. All run on the server thread, and none
 * trusts the client:
 *
 * <ul>
 *   <li>The sender's open container must be a {@link KeycardProgrammerMenu} bound to the <b>same</b> block
 *       position and still valid; otherwise the payload is dropped (debug line only).</li>
 *   <li>The installation is re-read from the store at that position on every payload; an unlinked
 *       programmer closes the screen.</li>
 *   <li>The slots and the module are re-read from the block entity; the client's toggles are only a
 *       request. {@link KeycardService} resolves the action at its own level and applies the card rules.</li>
 *   <li>Every handler is guarded: an unexpected exception fails closed, is reported by payload type and
 *       exception class only, and the screen closes.</li>
 * </ul>
 *
 * <p>Each outcome re-resolves the view and either re-sends the snapshot (carrying the outcome message,
 * which re-enables the buttons) or closes the screen.
 */
public final class ProgrammerPayloadHandlers {

    private ProgrammerPayloadHandlers() {
    }

    /** Who may look at a programmer: anyone the resolver lets open a MEMBER device at it. */
    public static AccessDecision resolveView(ServerPlayer player, Installation installation, DevicePos at) {
        return AccessResolver.DEFAULT.resolve(player, installation, AccessAction.OPEN, AccessLevel.MEMBER, at, null);
    }

    /** Sends the display snapshot to a player the resolver has just allowed to view the programmer. */
    public static void sendSnapshot(ServerPlayer player, KeycardProgrammerBlockEntity programmer,
            Installation installation, AccessDecision decision, @Nullable Component message) {
        sendSnapshot(player, programmer, installation, decision, true, message);
    }

    /** As above, with whether {@code message} reports a success ({@code ok}) or a refusal. */
    public static void sendSnapshot(ServerPlayer player, KeycardProgrammerBlockEntity programmer,
            Installation installation, AccessDecision decision, boolean ok, @Nullable Component message) {
        Services.NETWORK.sendToPlayer(player, new ProgrammerSnapshot(programmer.getBlockPos().immutable(),
                decision.effective(), programmer.hasEncryptionModule(), NeroSecurityConfig.keycardBasicMaxLevel(),
                installation.cardCount(), NeroSecurityConfig.keycardsPerInstallation(), ok, message));
    }

    public static void handleWrite(ProgrammerWrite payload, ServerPlayer player) {
        runSafely(player, "programmer_write", () -> {
            Bound bound = bound(player, payload.pos());
            if (bound == null) {
                return;
            }
            ServiceResult result = KeycardService.write(player, bound.installation, bound.at,
                    bound.programmer.cardStack(), payload.level(), payload.encrypted(), payload.bind(),
                    bound.programmer.hasEncryptionModule());
            KeycardItem.syncDisplay(bound.programmer.cardStack());
            bound.programmer.container().setChanged();
            if (result.ok()) {
                SecuritySounds.CARD_WRITE.play(player.level(), bound.programmer.getBlockPos());
            }
            finish(player, bound, result);
        });
    }

    public static void handleClone(ProgrammerClone payload, ServerPlayer player) {
        runSafely(player, "programmer_clone", () -> {
            Bound bound = bound(player, payload.pos());
            if (bound == null) {
                return;
            }
            ServiceResult result = KeycardService.clone(player, bound.installation, bound.at,
                    bound.programmer.cardStack(), bound.programmer.blankStack(),
                    bound.programmer.hasEncryptionModule());
            KeycardItem.syncDisplay(bound.programmer.blankStack());
            bound.programmer.container().setChanged();
            if (result.ok()) {
                SecuritySounds.CARD_WRITE.play(player.level(), bound.programmer.getBlockPos());
            }
            finish(player, bound, result);
        });
    }

    public static void handleWipe(ProgrammerWipe payload, ServerPlayer player) {
        runSafely(player, "programmer_wipe", () -> {
            Bound bound = bound(player, payload.pos());
            if (bound == null) {
                return;
            }
            ServiceResult result = KeycardService.wipe(player, bound.installation, bound.at,
                    bound.programmer.cardStack());
            KeycardItem.syncDisplay(bound.programmer.cardStack());
            bound.programmer.container().setChanged();
            if (result.ok()) {
                SecuritySounds.CARD_WIPE.play(player.level(), bound.programmer.getBlockPos());
            }
            finish(player, bound, result);
        });
    }

    /** The programmer, its installation and its store position, re-read for one payload. */
    private record Bound(KeycardProgrammerBlockEntity programmer, Installation installation, DevicePos at) {
    }

    /**
     * Binds a payload to the sender's open programmer menu: same position, still valid, still a programmer,
     * still linked. Null (debug line, or the screen closes when the link is gone) otherwise.
     */
    private static @Nullable Bound bound(ServerPlayer player, BlockPos claimed) {
        if (!(player.containerMenu instanceof KeycardProgrammerMenu menu) || claimed == null
                || !claimed.equals(menu.pos()) || !menu.stillValid(player)
                || !(player.level() instanceof ServerLevel level)
                || !(level.getBlockEntity(claimed) instanceof KeycardProgrammerBlockEntity programmer)) {
            NeroSecurityCommon.LOGGER.debug("[NeroSecurity] stale programmer payload dropped");
            return null;
        }
        DevicePos at = DevicePos.of(level, claimed);
        Installation installation = SecurityInstallations.get(level.getServer()).atDevice(at).orElse(null);
        if (installation == null) {
            player.closeContainer();
            player.sendSystemMessage(Component.translatable("programmer.nerosecurity.unlinked"));
            return null;
        }
        return new Bound(programmer, installation, at);
    }

    /** Re-resolves the view and refreshes the screen with the outcome, or closes it. */
    private static void finish(ServerPlayer player, Bound bound, ServiceResult result) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Installation fresh = SecurityInstallations.get(level.getServer()).atDevice(bound.at).orElse(null);
        if (fresh == null) {
            player.closeContainer();
            player.sendSystemMessage(result.message());
            return;
        }
        AccessDecision decision = resolveView(player, fresh, bound.at);
        if (decision.allowed()) {
            sendSnapshot(player, bound.programmer, fresh, decision, result.ok(), result.message());
        } else {
            player.closeContainer();
            player.sendSystemMessage(decision.message());
        }
    }

    /**
     * Runs a handler body; an unexpected failure fails closed. It is logged and reported by payload type and
     * exception class only (never a name, UUID or position), the screen is closed, and the sender is told.
     */
    private static void runSafely(ServerPlayer player, String payloadType, Runnable body) {
        try {
            body.run();
        } catch (RuntimeException | LinkageError e) {
            NeroSecurityCommon.LOGGER.warn(
                    "[NeroSecurity] programmer payload {} failed ({}); nothing was changed after the failure.",
                    payloadType, e.getClass().getName());
            NeroSecurityTelemetry.captureHandledException(e, "programmer_payload", payloadType);
            try {
                player.closeContainer();
                player.sendSystemMessage(Component.translatable("hub.nerosecurity.failed"));
            } catch (RuntimeException | LinkageError ignored) {
                // The player simply does not get the notice.
            }
        }
    }
}
