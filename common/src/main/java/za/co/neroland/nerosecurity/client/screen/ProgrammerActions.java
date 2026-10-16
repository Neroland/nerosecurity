package za.co.neroland.nerosecurity.client.screen;

import org.jetbrains.annotations.Nullable;

/**
 * Why each programmer action button is disabled, as a translation key, from what sits in the slots. Pure
 * (no Minecraft classes), so the "every disabled control says why" rule for Write / Copy / Wipe is unit-tested.
 * Display only: the server re-checks every payload; nothing here grants or refuses anything.
 */
public final class ProgrammerActions {

    /** What a machine slot holds, as far as the buttons care. */
    public enum CardState {
        /** Nothing in the slot. */
        EMPTY,
        /** A keycard with no data. */
        BLANK,
        /** A written keycard. */
        ENCODED
    }

    /** Shown while no snapshot has arrived yet. */
    public static final String WAITING = "ui.nerosecurity.waiting";
    public static final String NO_CARD = "programmer.nerosecurity.no_card";
    public static final String NOT_BLANK = "programmer.nerosecurity.not_blank";
    public static final String CARD_BLANK = "programmer.nerosecurity.card_blank";
    public static final String NO_BLANK = "programmer.nerosecurity.no_blank";
    /** Write when the viewer may write no level at all (a MEMBER may only copy and wipe). */
    public static final String NO_LEVEL = "programmer.nerosecurity.level_too_high";

    private ProgrammerActions() {
    }

    /** From a slot's two facts: is there a keycard, and is it blank. */
    public static CardState state(boolean keycard, boolean blank) {
        if (!keycard) {
            return CardState.EMPTY;
        }
        return blank ? CardState.BLANK : CardState.ENCODED;
    }

    /** Why Write is disabled, or null when it is enabled. Needs a writable level and a blank in the CARD slot. */
    public static @Nullable String writeBlockedKey(boolean hasSnapshot, boolean levelWritable, CardState card) {
        if (!hasSnapshot) {
            return WAITING;
        }
        if (!levelWritable) {
            return NO_LEVEL;
        }
        return switch (card) {
            case EMPTY -> NO_CARD;
            case ENCODED -> NOT_BLANK;
            case BLANK -> null;
        };
    }

    /** Why Copy is disabled, or null. Needs a written card in the CARD slot and a blank in the BLANK slot. */
    public static @Nullable String cloneBlockedKey(boolean hasSnapshot, CardState card, CardState blank) {
        if (!hasSnapshot) {
            return WAITING;
        }
        String source = encodedSourceBlockedKey(card);
        if (source != null) {
            return source;
        }
        return blank == CardState.BLANK ? null : NO_BLANK;
    }

    /** Why Wipe is disabled, or null. Needs a written card in the CARD slot. */
    public static @Nullable String wipeBlockedKey(boolean hasSnapshot, CardState card) {
        if (!hasSnapshot) {
            return WAITING;
        }
        return encodedSourceBlockedKey(card);
    }

    private static @Nullable String encodedSourceBlockedKey(CardState card) {
        return switch (card) {
            case EMPTY -> NO_CARD;
            case BLANK -> CARD_BLANK;
            case ENCODED -> null;
        };
    }
}
