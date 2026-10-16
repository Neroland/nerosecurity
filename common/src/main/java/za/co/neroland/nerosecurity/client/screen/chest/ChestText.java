package za.co.neroland.nerosecurity.client.screen.chest;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.block.DeviceSettingsRules;
import za.co.neroland.nerosecurity.block.chest.AutomationWhy;
import za.co.neroland.nerosecurity.client.widget.Status;

/**
 * The secure chest screen's pure wording rules: the automation row (glyph, policy word, reason) and why the
 * controls are locked when they are. Display only — the server computed {@link AutomationWhy} and decides
 * every change; no Minecraft client classes, so it is plain-JVM tested.
 */
public final class ChestText {

    public static final String OWNER_ONLY_KEY = "chest.nerosecurity.owner_only_setting";
    public static final String UNLINKED_KEY = "device.nerosecurity.unlinked";
    public static final String SEALED_KEY = "chest.nerosecurity.sealed";
    public static final String AUTOMATION_KEY = "chest.nerosecurity.automation";
    public static final String AUTOMATION_OFF_KEY = "chest.nerosecurity.automation.off";

    /**
     * The automation row: {@code status} is OK when hoppers and pipes may reach the chest, OFF otherwise;
     * {@code policyLevel} is the installation's automation level, drawn as a pill, or {@code null} for the
     * word "off"; {@code whyKey} is the one-line reason.
     */
    public record Automation(Status status, @Nullable AccessLevel policyLevel, String whyKey) {
    }

    private ChestText() {
    }

    /** The automation row, or {@code null} for an unlinked chest (a plain chest has no policy). */
    public static @Nullable Automation automation(boolean linked, @Nullable AccessLevel policy, boolean allowed,
                                                  @Nullable AutomationWhy why) {
        if (!linked) {
            return null;
        }
        AutomationWhy reason = why == null ? (allowed ? AutomationWhy.OK : AutomationWhy.OFF) : why;
        return new Automation(allowed ? Status.OK : Status.OFF, policy, reason.translationKey());
    }

    /** The lockdown row: a red cross with "Lockdown: sealed", or {@code null} when not locked down. */
    public static @Nullable String sealedKey(boolean lockdown) {
        return lockdown ? SEALED_KEY : null;
    }

    /**
     * Why every setting control is locked, or {@code null} when the viewer may edit. An unlinked chest has no
     * settings; a chest already at OWNER may only be changed by an owner (or an operator, reported as OWNER).
     */
    public static @Nullable String editWhy(boolean linked, AccessLevel minLevel, boolean ownerOrAdmin) {
        if (!linked) {
            return UNLINKED_KEY;
        }
        if (!DeviceSettingsRules.mayApply(ownerOrAdmin, minLevel, minLevel)) {
            return OWNER_ONLY_KEY;
        }
        return null;
    }
}
