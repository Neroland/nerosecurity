package za.co.neroland.nerosecurity.access;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;

/**
 * The single path every NeroSecurity access decision takes. Blocks, payload handlers and commands
 * ask this class; none of them compares levels itself.
 *
 * <h2>Algorithm (fixed order, server-side, nothing cached)</h2>
 * <ol>
 *   <li>An operator ({@link AdminCheck}) is allowed, reported as OWNER and flagged admin. This beats
 *       lockdown and every source.</li>
 *   <li>An admin-only action is refused for everyone else.</li>
 *   <li>No installation: the effective level is PUBLIC and no source is asked.</li>
 *   <li>Every source votes. A throwing source counts as a deny (fail closed). When the caller passes
 *       a granting-source filter (a keycard-only device), grants from sources outside it are treated
 *       as abstentions; every source is still asked, so a deny from any of them still counts.</li>
 *   <li>Any deny refuses the action; the highest grant is still reported for the UI.</li>
 *   <li>The effective level is the highest grant (PUBLIC when nobody granted).</li>
 *   <li>While lockdown is enabled and active on the installation the requirement becomes OWNER, so
 *       owners are never affected by it and nobody below OWNER can stand it down.</li>
 *   <li>Allowed when the effective level reaches the requirement.</li>
 * </ol>
 *
 * <p>Built by constructor injection so the whole thing runs in a plain JVM; {@link #DEFAULT} wires
 * the production registry, operator check and config kill switch.
 */
public final class AccessResolver {

    public static final AccessResolver DEFAULT = new AccessResolver(
            AccessSources::all, AdminCheck.SERVER, NeroSecurityConfig::isLockdownEnabled);

    /** Sources that have already been logged as failing this session (log once per source). */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private final Supplier<List<AccessSource>> sources;
    private final AdminCheck adminCheck;
    private final BooleanSupplier lockdownEnabled;

    public AccessResolver(Supplier<List<AccessSource>> sources, AdminCheck adminCheck,
            BooleanSupplier lockdownEnabled) {
        this.sources = sources;
        this.adminCheck = adminCheck;
        this.lockdownEnabled = lockdownEnabled;
    }

    /** Resolves {@code query} against an explicit required level, with every source able to grant. */
    public AccessDecision resolve(AccessQuery query, AccessLevel required) {
        return resolve(query, required, null);
    }

    /**
     * Resolves {@code query} against an explicit required level, counting grants only from the
     * sources in {@code grantingSources} ({@code null} = every source). The filter drops grants and
     * nothing else: denies from any source, the lockdown rule and the operator override all apply
     * exactly as without it.
     */
    public AccessDecision resolve(AccessQuery query, AccessLevel required,
            @Nullable Set<Identifier> grantingSources) {
        // 1. Operator override.
        if (isAdmin(query)) {
            return AccessDecision.adminOverride(required);
        }
        // 2. Admin-only actions.
        if (query.action().adminOnly) {
            return AccessDecision.deny(AccessLevel.PUBLIC, required, DenyReason.ADMIN_ONLY);
        }
        // 3. Unknown installation = PUBLIC; nothing to grant on, so no source is asked.
        Installation installation = query.installation();
        if (installation == null) {
            boolean allowed = required == AccessLevel.PUBLIC;
            return new AccessDecision(allowed, AccessLevel.PUBLIC, required,
                    allowed ? DenyReason.NONE : DenyReason.UNKNOWN_INSTALLATION, List.of(), false);
        }
        // 4. Every source votes.
        AccessLevel best = AccessLevel.PUBLIC;
        List<Identifier> denies = new ArrayList<>();
        for (AccessSource source : sourceList()) {
            Identifier id = safeId(source);
            AccessVote vote;
            try {
                vote = source.vote(query);
            } catch (RuntimeException | LinkageError e) {
                warnOnce(id, e);
                vote = new AccessVote.Deny(DenyReason.SOURCE_DENY, "access.nerosecurity.deny.source");
            }
            if (vote instanceof AccessVote.Grant grant && grant.level() != null) {
                if (grantingSources == null || grantingSources.contains(id)) {
                    best = AccessLevel.max(best, grant.level());
                }
            } else if (vote instanceof AccessVote.Deny) {
                denies.add(id);
            }
            // Abstain (or a null vote) changes nothing.
        }
        // 5. Deny wins.
        if (!denies.isEmpty()) {
            return new AccessDecision(false, best, required, DenyReason.SOURCE_DENY, denies, false);
        }
        // 6. Effective level.
        AccessLevel effective = best;
        // 7. Lockdown rule.
        if (lockdownEnabled.getAsBoolean() && installation.lockdown().active()) {
            AccessLevel original = required;
            if (!effective.atLeast(AccessLevel.OWNER)) {
                DenyReason reason = effective.atLeast(original) ? DenyReason.LOCKDOWN : DenyReason.INSUFFICIENT_LEVEL;
                return AccessDecision.deny(effective, AccessLevel.OWNER, reason);
            }
            required = AccessLevel.OWNER;
        }
        // 8. The comparison.
        boolean allowed = effective.atLeast(required);
        return new AccessDecision(allowed, effective, required,
                allowed ? DenyReason.NONE : DenyReason.INSUFFICIENT_LEVEL, List.of(), false);
    }

    /** Convenience for block code: builds the query and uses the action's default minimum. */
    public AccessDecision resolve(ServerPlayer actor, @Nullable Installation installation, AccessAction action,
            @Nullable DevicePos at) {
        AccessQuery query = new AccessQuery(actor.level().getServer(), actor.getUUID(), installation, action, at);
        return resolve(query, action.defaultMinimum);
    }

    /**
     * Convenience for device code that sets its own requirement and may restrict which sources grant
     * (a keycard-only door passes {@code Set.of(KeycardSource.ID)}).
     */
    public AccessDecision resolve(ServerPlayer actor, @Nullable Installation installation, AccessAction action,
            AccessLevel required, @Nullable DevicePos at, @Nullable Set<Identifier> grantingSources) {
        AccessQuery query = new AccessQuery(actor.level().getServer(), actor.getUUID(), installation, action, at);
        return resolve(query, required, grantingSources);
    }

    /**
     * Convenience for command code. A player source resolves as that player; any other source (the
     * console, a command block) has no member rows, so only its own operator permission can allow it.
     */
    public AccessDecision resolve(CommandSourceStack source, @Nullable Installation installation,
            AccessAction action) {
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            return resolve(player, installation, action, null);
        }
        AccessResolver console = new AccessResolver(sources, AdminCheck.forSource(source), lockdownEnabled);
        return console.resolve(new AccessQuery(source.getServer(), NON_PLAYER_ACTOR, installation, action, null),
                action.defaultMinimum);
    }

    /** Placeholder actor for non-player command sources. Never an owner or member. */
    private static final java.util.UUID NON_PLAYER_ACTOR = Installation.TOMBSTONE_OWNER;

    private boolean isAdmin(AccessQuery query) {
        try {
            return adminCheck.isAdmin(query.server(), query.actor());
        } catch (RuntimeException | LinkageError e) {
            // An operator lookup that fails grants nothing.
            return false;
        }
    }

    private List<AccessSource> sourceList() {
        List<AccessSource> list = sources.get();
        return list == null ? List.of() : list;
    }

    private static Identifier safeId(AccessSource source) {
        try {
            Identifier id = source.id();
            if (id != null) {
                return id;
            }
        } catch (RuntimeException | LinkageError ignored) {
            // fall through
        }
        return Identifier.fromNamespaceAndPath(NeroSecurityCommon.MOD_ID, "unknown");
    }

    private static void warnOnce(Identifier id, Throwable error) {
        if (WARNED.add(id.toString())) {
            NeroSecurityCommon.LOGGER.warn(
                    "[NeroSecurity] access source {} failed ({}); its vote counts as a deny.",
                    id, error.getClass().getName());
        }
    }
}
