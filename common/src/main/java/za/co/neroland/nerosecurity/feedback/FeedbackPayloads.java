package za.co.neroland.nerosecurity.feedback;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerosecurity.network.SecurityNetwork;
import za.co.neroland.nerosecurity.platform.Services;

/**
 * Declares {@link DeviceFeedback} and sends it. Sending goes to the acting player only (one tiny packet), on
 * the server thread, and never throws into the gameplay path that called it.
 */
public final class FeedbackPayloads {

    private static boolean declared;

    private FeedbackPayloads() {
    }

    /** Declares the feedback payload with {@link SecurityNetwork}. Call once from network init. */
    public static void declare() {
        if (declared) {
            return;
        }
        declared = true;
        SecurityNetwork.clientbound(DeviceFeedback.TYPE, DeviceFeedback.STREAM_CODEC, ClientFeedbackHook::accept);
    }

    /** Tells {@code player} (only) that the device at {@code pos} refused them. */
    public static void denied(ServerPlayer player, BlockPos pos) {
        send(player, pos, DeviceFeedback.DENIED);
    }

    /** Tells {@code player} (only) that the device at {@code pos} let them through. */
    public static void accepted(ServerPlayer player, BlockPos pos) {
        send(player, pos, DeviceFeedback.ACCEPTED);
    }

    private static void send(ServerPlayer player, BlockPos pos, byte kind) {
        if (player == null || pos == null) {
            return;
        }
        try {
            Services.NETWORK.sendToPlayer(player, new DeviceFeedback(pos, kind));
        } catch (RuntimeException | LinkageError ignored) {
            // Feedback is cosmetic; the action-bar message has already gone out.
        }
    }
}
