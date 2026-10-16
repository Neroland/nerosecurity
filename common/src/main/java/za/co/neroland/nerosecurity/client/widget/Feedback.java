package za.co.neroland.nerosecurity.client.widget;

import java.util.Objects;

import net.minecraft.network.chat.Component;

/**
 * One footer line: what the last action did. {@link Kind#OK} for success, {@link Kind#DENY} for a refusal,
 * {@link Kind#HINT} for neutral guidance ("Waiting for the server…"). Immutable; the screen hull keeps the
 * latest one until the next action replaces it.
 */
public record Feedback(Kind kind, Component text) {

    public enum Kind {
        OK,
        DENY,
        HINT
    }

    public Feedback {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(text, "text");
    }

    public static Feedback ok(Component text) {
        return new Feedback(Kind.OK, text);
    }

    public static Feedback deny(Component text) {
        return new Feedback(Kind.DENY, text);
    }

    public static Feedback hint(Component text) {
        return new Feedback(Kind.HINT, text);
    }
}
