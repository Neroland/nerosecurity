package za.co.neroland.nerosecurity.privacy;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The seam behind the hub's Privacy tab and {@code /nerosecurity privacy}: what happens when a player asks for
 * their own data, or to stop being named in audit rows. Every call acts on the asking player only, never on
 * anyone else.
 *
 * <p>The installed implementation is {@link SecurityPrivacyActions} (export to a JSON file, opt-out and opt-in),
 * so {@link #available()} is true and the screen's buttons work. Erasure is deliberately <b>not</b> here: it is
 * server-wide across every Neroland mod, so the screen pre-fills Core's {@code /neroland data eraseme} in chat
 * behind a confirm, and only the player can run it.
 */
public interface PrivacyActions {

    /** The answer to one action: whether it happened, and the sentence to show. */
    record Outcome(boolean ok, Component message) {

        public Outcome {
            message = message == null ? Component.empty() : message;
        }
    }

    /** What the screen needs to label its buttons for the asking player. */
    record Status(boolean optedOut) {
    }

    /**
     * {@code "Not available until the privacy update."} Kept for the screen's disabled-button sentence and as the
     * handler's answer when an implementation returns no outcome.
     */
    String UNAVAILABLE_KEY = "privacy.nerosecurity.unavailable";

    /** Whether the actions do anything on this server (the screen still shows every button). */
    boolean available();

    /** Writes the asking player's own data to a file on the server and says where. */
    Outcome export(ServerPlayer player);

    /** Stops ({@code true}) or resumes ({@code false}) naming the asking player in audit rows. */
    Outcome setOptOut(ServerPlayer player, boolean optedOut);

    /** The asking player's current choice. */
    Status status(ServerPlayer player);

    /** The installed implementation ({@link SecurityPrivacyActions#INSTANCE} unless a test installed another). */
    static PrivacyActions current() {
        return PrivacyActionsHolder.current;
    }

    /** Installs {@code actions}; null puts {@link SecurityPrivacyActions#INSTANCE} back. Init and tests only. */
    static void install(PrivacyActions actions) {
        PrivacyActionsHolder.current = actions == null ? SecurityPrivacyActions.INSTANCE : actions;
    }
}
