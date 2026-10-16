package za.co.neroland.nerosecurity.block.chest;

import java.util.Locale;

/** Why automation may or may not reach a secure chest right now (the settings screen's reason line). */
public enum AutomationWhy {
    /** Allowed (or the chest is unlinked, so it is a plain chest). */
    OK,
    /** The installation's automation policy is off. */
    OFF,
    /** The chest's level is above the automation level. */
    LEVEL,
    /** Keycard-only chests never allow automation. */
    KEYCARD,
    /** Blocked while the installation is in lockdown. */
    LOCKDOWN;

    /** {@code "chest.nerosecurity.automation.why.<lower_name>"}. */
    public String translationKey() {
        return "chest.nerosecurity.automation.why." + name().toLowerCase(Locale.ROOT);
    }
}
