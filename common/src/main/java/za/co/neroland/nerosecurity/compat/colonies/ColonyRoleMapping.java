package za.co.neroland.nerosecurity.compat.colonies;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.compat.CompatLevels;
import za.co.neroland.nerosecurity.compat.colonies.ColoniesBridge.Role;

/**
 * Colony role to NeroSecurity level: STRANGER and ENEMY grant nothing (abstain, never deny: only claims deny),
 * ALLY MEMBER, CHIEF and OWNER OFFICER; then capped by the binding's max (never above OFFICER).
 */
public final class ColonyRoleMapping {

    private ColonyRoleMapping() {
    }

    /** The level to grant, or null to abstain. */
    public static @Nullable AccessLevel level(@Nullable Role role, @Nullable AccessLevel max) {
        if (role == null) {
            return null;
        }
        AccessLevel raw = switch (role) {
            case STRANGER, ENEMY -> null;
            case ALLY -> AccessLevel.MEMBER;
            case CHIEF, OWNER -> AccessLevel.OFFICER;
        };
        return CompatLevels.cap(raw, max);
    }
}
