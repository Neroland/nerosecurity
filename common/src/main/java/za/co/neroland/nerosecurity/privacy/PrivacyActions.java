package za.co.neroland.nerosecurity.privacy;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The seam behind the hub's Privacy tab: what happens when a player asks for their own data, or to stop being
 * named in audit rows. Every call acts on the asking player only, never on anyone else.
 *
 * <p>This release ships {@link #NOT_YET}: every action answers "not available until the privacy update" and
 * nothing is done, so the buttons are honest. The privacy update installs the real implementation with
 * {@link #install}. Erasure is deliberately <b>not</b> here: it is server-wide across every Neroland mod, so the
 * screen pre-fills Core's {@code /neroland data eraseme} in chat behind a confirm, and only the player can run it.
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

    /** Placeholder until the privacy update: refuses every action with an honest sentence. */
    PrivacyActions NOT_YET = new PrivacyActions() {

        @Override
        public boolean available() {
            return false;
        }

        @Override
        public Outcome export(ServerPlayer player) {
            return unavailable();
        }

        @Override
        public Outcome setOptOut(ServerPlayer player, boolean optedOut) {
            return unavailable();
        }

        @Override
        public Status status(ServerPlayer player) {
            return new Status(false);
        }

        private Outcome unavailable() {
            return new Outcome(false, Component.translatable(UNAVAILABLE_KEY));
        }
    };

    /** {@code "Not available until the privacy update."} */
    String UNAVAILABLE_KEY = "privacy.nerosecurity.unavailable";

    /** Whether the actions do anything on this server yet (the screen still shows every button). */
    boolean available();

    /** Writes the asking player's own data to a file on the server and says where. */
    Outcome export(ServerPlayer player);

    /** Stops ({@code true}) or resumes ({@code false}) naming the asking player in new audit rows. */
    Outcome setOptOut(ServerPlayer player, boolean optedOut);

    /** The asking player's current choice. */
    Status status(ServerPlayer player);

    /** The installed implementation ({@link #NOT_YET} until the privacy update installs its own). */
    static PrivacyActions current() {
        return PrivacyActionsHolder.current;
    }

    /** Installs {@code actions}; null puts {@link #NOT_YET} back. Call from init. */
    static void install(PrivacyActions actions) {
        PrivacyActionsHolder.current = actions == null ? NOT_YET : actions;
    }
}
