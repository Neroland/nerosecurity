package za.co.neroland.nerosecurity.audit;

import java.util.UUID;

import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;

/**
 * The privacy opt-out hook: whether a player's identity is withheld from the audit log. Consulted for
 * every persisted event of every type, the log-read row included. A withheld actor is stored as
 * {@code actor = null, actorWithheld = true} ("anonymous"). The default withholds nobody; the opt-out
 * store installs its own policy through {@link AuditRecorder#setActorPolicy}.
 */
@FunctionalInterface
public interface ActorPolicy {

    boolean withholdActor(@Nullable MinecraftServer server, UUID actor);

    ActorPolicy NONE = (server, actor) -> false;
}
