package za.co.neroland.nerosecurity.menu;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.keycard.KeycardService;

/**
 * What the programmer screen enables and why. Pure, so it runs in plain-JVM tests. It mirrors the server's
 * card rules in {@link KeycardService} for display only: the server re-checks everything on the payload,
 * and nothing here grants or refuses anything.
 */
public final class ProgrammerUiRules {

    /** Level buttons from lowest to highest. */
    public static final AccessLevel[] LEVELS = AccessLevel.values();

    private ProgrammerUiRules() {
    }

    /**
     * Why the level button for {@code target} is disabled, as a translation key, or null when it is enabled.
     * {@code programmer.nerosecurity.encrypt_required} takes the basic maximum level as its argument.
     */
    public static @Nullable String levelBlockedKey(AccessLevel viewer, AccessLevel target, boolean encrypted,
            AccessLevel basicMax) {
        if (!KeycardService.mayWrite(viewer, target)) {
            return target == AccessLevel.OWNER && viewer != AccessLevel.OWNER
                    ? "programmer.nerosecurity.owner_only" : "programmer.nerosecurity.level_too_high";
        }
        if (!KeycardService.basicLevelAllowed(target, encrypted, basicMax)) {
            return "programmer.nerosecurity.encrypt_required";
        }
        return null;
    }

    /** Why the "Encrypted" toggle is disabled (owner first, then module), or null when it is enabled. */
    public static @Nullable String encryptBlockedKey(AccessLevel viewer, boolean hasModule) {
        if (viewer != AccessLevel.OWNER) {
            return "programmer.nerosecurity.toggle.encrypted.needs_owner";
        }
        if (!hasModule) {
            return "programmer.nerosecurity.toggle.encrypted.needs_module";
        }
        return null;
    }

    /**
     * The level the screen shows as selected: {@code current} while it is still writable, otherwise the
     * highest writable level, or null when the viewer can write nothing (a MEMBER may only copy and wipe).
     */
    public static @Nullable AccessLevel pick(AccessLevel viewer, boolean encrypted, AccessLevel basicMax,
            @Nullable AccessLevel current) {
        if (current != null && levelBlockedKey(viewer, current, encrypted, basicMax) == null) {
            return current;
        }
        for (int i = LEVELS.length - 1; i >= 0; i--) {
            if (levelBlockedKey(viewer, LEVELS[i], encrypted, basicMax) == null) {
                return LEVELS[i];
            }
        }
        return null;
    }
}
