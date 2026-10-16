package za.co.neroland.nerosecurity.access;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The resolver's answer.
 *
 * @param allowed        whether the action may go ahead
 * @param effective      the highest level any source granted (reported even when denied, for the UI)
 * @param required       the level that applied in the end (OWNER while a lockdown is in force)
 * @param reason         why it was refused, or {@link DenyReason#NONE}
 * @param denyingSources ids of every source that voted deny, in evaluation order
 * @param admin          true only for an operator override produced by {@link AdminCheck}
 */
public record AccessDecision(boolean allowed, AccessLevel effective, AccessLevel required,
        DenyReason reason, List<Identifier> denyingSources, boolean admin) {

    public AccessDecision {
        denyingSources = List.copyOf(denyingSources);
    }

    /** An operator override: allowed, reported as OWNER, flagged admin. */
    public static AccessDecision adminOverride(AccessLevel required) {
        return new AccessDecision(true, AccessLevel.OWNER, required, DenyReason.NONE, List.of(), true);
    }

    static AccessDecision deny(AccessLevel effective, AccessLevel required, DenyReason reason) {
        return new AccessDecision(false, effective, required, reason, List.of(), false);
    }

    /**
     * The sentence shown to a refused player; empty when allowed. Never contains a name, a UUID or
     * a position: only level names, a source's display name and fixed text.
     */
    public Component message() {
        if (allowed) {
            return Component.empty();
        }
        return switch (reason) {
            case LOCKDOWN -> Component.translatable("access.nerosecurity.deny.lockdown");
            case SOURCE_DENY -> Component.translatable("access.nerosecurity.deny.source", sourceName());
            case ADMIN_ONLY -> Component.translatable("access.nerosecurity.deny.admin_only");
            case UNKNOWN_INSTALLATION -> Component.translatable("access.nerosecurity.deny.unknown");
            case INSUFFICIENT_LEVEL, NONE -> Component.translatable("access.nerosecurity.deny.level",
                    required.displayName(), effective.displayName());
        };
    }

    private Component sourceName() {
        if (denyingSources.isEmpty()) {
            return Component.translatable("access.nerosecurity.source.unknown");
        }
        Identifier first = denyingSources.get(0);
        return Component.translatable("access." + first.getNamespace() + ".source." + first.getPath());
    }
}
