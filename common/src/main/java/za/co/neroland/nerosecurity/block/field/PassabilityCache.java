package za.co.neroland.nerosecurity.block.field;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;

import za.co.neroland.nerosecurity.access.AccessResolver;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.field.FieldVerdict;

/**
 * One emitter's server-side verdict cache: "may this player pass" per player UUID, valid for one game tick and one
 * {@link FieldPassability#GENERATION}. Every miss asks {@link FieldVerdict#decide} (the access resolver,
 * {@code PASS_FIELD} at the field's level, keycard-only filter applied), so a revocation, a lockdown or a faction tier
 * change takes effect within the tick it is written. Transient; bounded at {@value #MAX_ENTRIES} players per tick
 * (beyond that a verdict is computed and simply not cached).
 */
final class PassabilityCache {

    static final int MAX_ENTRIES = 256;

    private long tick = Long.MIN_VALUE;
    private long generation = Long.MIN_VALUE;
    private final Map<UUID, Boolean> verdicts = new HashMap<>();

    boolean isPassable(ServerLevel level, ForceFieldEmitterBlockEntity emitter, UUID actor) {
        long now = level.getGameTime();
        long gen = FieldPassability.GENERATION;
        if (tick != now || generation != gen) {
            verdicts.clear();
            tick = now;
            generation = gen;
        }
        Boolean cached = verdicts.get(actor);
        if (cached != null) {
            return cached;
        }
        boolean verdict = FieldVerdict.decide(AccessResolver.DEFAULT, level.getServer(), emitter.installationNow(level),
                emitter.devicePos(level), actor, emitter.settings(), NeroSecurityConfig.isForceFieldsEnabled());
        if (verdicts.size() < MAX_ENTRIES) {
            verdicts.put(actor, verdict);
        }
        return verdict;
    }

    void clear() {
        verdicts.clear();
        tick = Long.MIN_VALUE;
        generation = Long.MIN_VALUE;
    }
}
