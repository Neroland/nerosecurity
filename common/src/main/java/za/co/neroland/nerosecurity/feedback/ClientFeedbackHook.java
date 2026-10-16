package za.co.neroland.nerosecurity.feedback;

import net.minecraft.core.BlockPos;

import org.jetbrains.annotations.Nullable;

/**
 * Where a received {@link DeviceFeedback} goes on the client. Common code with no client-only imports, so
 * the payload declaration can name it on a dedicated server too; the client renderer side installs a
 * {@link Listener} at client init (the deny sound, the LED flash) and this class only forwards to it.
 * Until a listener is installed, feedback is dropped. A listener that throws is ignored.
 */
public final class ClientFeedbackHook {

    /** Reacts to one feedback packet on the client thread. */
    @FunctionalInterface
    public interface Listener {
        void onFeedback(BlockPos pos, boolean denied);
    }

    private static volatile @Nullable Listener listener;

    private ClientFeedbackHook() {
    }

    /** Installs the client's listener (replaces any earlier one). */
    public static void setListener(@Nullable Listener next) {
        listener = next;
    }

    /** The payload handler: forwards to the listener, if any. */
    public static void accept(DeviceFeedback feedback) {
        Listener current = listener;
        if (current == null || feedback == null) {
            return;
        }
        try {
            current.onFeedback(feedback.pos(), feedback.denied());
        } catch (RuntimeException | LinkageError ignored) {
            // Cosmetic only: a broken listener never breaks the connection.
        }
    }
}
