package za.co.neroland.nerosecurity.menu.field;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.access.AccessDecision;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.block.field.ForceFieldEmitterBlockEntity;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.menu.field.FieldPayloads.FieldSnapshot;

/** Builds the settings-screen snapshot for a player the resolver has just allowed to configure the emitter. */
public final class FieldSnapshots {

    private FieldSnapshots() {
    }

    public static FieldSnapshot build(ServerLevel level, ForceFieldEmitterBlockEntity emitter, AccessDecision decision) {
        return build(level, emitter, decision, true, null);
    }

    /** As above, carrying the outcome of the action that produced it ({@code ok = false} + reason for a refusal). */
    public static FieldSnapshot build(ServerLevel level, ForceFieldEmitterBlockEntity emitter, AccessDecision decision,
            boolean ok, @Nullable Component message) {
        return new FieldSnapshot(emitter.getBlockPos().immutable(), emitter.settings(), emitter.strength(),
                NeroSecurityConfig.maxFieldStrength(), emitter.range(), emitter.area(), emitter.projectedCount(),
                NeroSecurityConfig.maxFieldArea(),
                emitter.drawPerTick(), emitter.energyAmount(), emitter.energyCapacity(), emitter.fieldState(),
                emitter.loadFraction(), emitter.cooldownSeconds(level.getGameTime()),
                decision.admin() ? AccessLevel.OWNER : decision.effective(), decision.admin(),
                NeroSecurityConfig.isForceFieldBreachable(), NeroSecurityConfig.isForceFieldsEnabled(),
                ok, message);
    }
}
