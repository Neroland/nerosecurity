package za.co.neroland.nerosecurity.field;

import java.util.Set;
import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.access.AccessAction;
import za.co.neroland.nerosecurity.access.AccessQuery;
import za.co.neroland.nerosecurity.access.AccessResolver;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;

/**
 * The one decision behind every server-side "may this player pass this field": {@code PASS_FIELD} at the field's
 * minimum level through the access resolver, by the player's UUID, with the keycard-only filter when set. So deny
 * wins (a claim refuses even an officer), lockdown seals for everyone below OWNER, and an operator passes. A stored
 * owner of the installation is never filtered: owners always pass their own fields, keycard-only included, so a
 * field can never seal its owner out. Pure: the resolver is injected.
 */
public final class FieldVerdict {

    private FieldVerdict() {
    }

    /**
     * @param fieldsEnabled the server's {@code forceFieldsEnabled}: false makes every field passable to everyone
     *                      (the kill switch removes behaviour, it never blocks)
     * @return true when {@code actor} may pass; false for an unlinked emitter or anything missing (fail closed)
     */
    public static boolean decide(AccessResolver resolver, @Nullable MinecraftServer server,
            @Nullable Installation installation, DevicePos emitter, UUID actor, FieldSettings settings,
            boolean fieldsEnabled) {
        if (!fieldsEnabled) {
            return true;
        }
        if (resolver == null || installation == null || actor == null || settings == null) {
            return false;
        }
        try {
            // A stored owner always passes their own field: the keycard-only filter is dropped, and no position is
            // passed so a land claim over the emitter's chunk cannot seal the owner inside their own shell (claims
            // vote on block interactions only). Any other deny still wins.
            boolean owner = installation.isOwner(actor);
            Set<Identifier> filter = owner ? null : settings.access().grantingSources();
            DevicePos at = owner ? null : emitter;
            return resolver.resolve(new AccessQuery(server, actor, installation, AccessAction.PASS_FIELD, at),
                    settings.access().minLevel(), filter).allowed();
        } catch (RuntimeException e) {
            return false;
        }
    }
}
