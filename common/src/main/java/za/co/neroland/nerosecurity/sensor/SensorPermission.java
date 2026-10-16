package za.co.neroland.nerosecurity.sensor;

import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.access.AccessAction;
import za.co.neroland.nerosecurity.access.AccessDecision;
import za.co.neroland.nerosecurity.access.AccessQuery;
import za.co.neroland.nerosecurity.access.AccessResolver;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;

/**
 * How a sensor judges something it found. <b>Detection is not authorisation:</b> a sensor finds
 * entities by box and ray, and whether a found player is permitted is answered only by
 * {@link AccessResolver#resolve} on that player's UUID ({@code SENSOR_PASS} at the sensor's
 * {@code minLevel}). No proximity, name or member-list shortcut decides it.
 *
 * <p>An animal owned by a player is judged as its owner (by UUID, without loading the owner), so a
 * member's dog never trips the owner's laser and a stranger's wolf does. Other entities are judged by
 * the sensor's {@link SensorEntityMode}. An unlinked sensor senses nothing.
 *
 * <p>Two steps so the rule runs in a plain JVM: {@link #describe(Entity)} reduces a game entity to a
 * {@link Sensed} value; {@link #classify(AccessResolver, MinecraftServer, Installation, DevicePos, Sensed, SensorSettings)}
 * is pure.
 */
public final class SensorPermission {

    /** The verdict for one sensed entity. */
    public enum Classification {
        /** Not something this sensor reacts to (or the sensor is unlinked). No event, no cooldown. */
        IGNORE,
        /** A player (or owned animal) the resolver allows here. Produces no event by default. */
        PERMITTED,
        /** Raise the sensor's event. The event's actor is {@link Sensed#subject()} (null for a mob). */
        INTRUDER
    }

    /** What kind of thing was sensed. */
    public enum Kind {
        /** Spectator, dead, or not a living entity (items, XP, projectiles, empty vehicles). */
        IGNORED,
        /** A player, judged by its own UUID. */
        PLAYER,
        /** An animal with a player owner, judged by the owner's UUID. */
        OWNED,
        /** A hostile mob ({@code Enemy}) without an owner. */
        HOSTILE,
        /** Any other living entity without an owner. */
        PASSIVE
    }

    /**
     * A sensed entity reduced to what the rule needs.
     *
     * @param subject the UUID the resolver judges (the player, or the owner); null for mobs
     */
    public record Sensed(Kind kind, @Nullable UUID subject) {

        public static final Sensed IGNORED = new Sensed(Kind.IGNORED, null);
        public static final Sensed HOSTILE = new Sensed(Kind.HOSTILE, null);
        public static final Sensed PASSIVE = new Sensed(Kind.PASSIVE, null);

        public Sensed {
            if (kind == null) {
                kind = Kind.IGNORED;
            }
            if ((kind == Kind.PLAYER || kind == Kind.OWNED) && subject == null) {
                kind = Kind.IGNORED;
            }
            if (kind != Kind.PLAYER && kind != Kind.OWNED) {
                subject = null;
            }
        }

        public static Sensed player(UUID id) {
            return new Sensed(Kind.PLAYER, id);
        }

        public static Sensed ownedBy(UUID owner) {
            return new Sensed(Kind.OWNED, owner);
        }
    }

    private SensorPermission() {
    }

    /** Reduces a game entity to a {@link Sensed} value. Never loads another entity or chunk. */
    public static Sensed describe(@Nullable Entity entity) {
        if (entity == null || entity.isSpectator() || !entity.isAlive() || !(entity instanceof LivingEntity)) {
            return Sensed.IGNORED;
        }
        if (entity instanceof Player player) {
            // ServerPlayer and fake players alike: by UUID only.
            return Sensed.player(player.getUUID());
        }
        if (entity instanceof OwnableEntity ownable) {
            UUID owner = ownerUuid(ownable);
            if (owner != null) {
                return Sensed.ownedBy(owner);
            }
        }
        return entity instanceof Enemy ? Sensed.HOSTILE : Sensed.PASSIVE;
    }

    /** The owner's UUID from the stored reference, without resolving (loading) the owner entity. */
    static @Nullable UUID ownerUuid(OwnableEntity ownable) {
        EntityReference<LivingEntity> reference = ownable.getOwnerReference();
        return reference == null ? null : reference.getUUID();
    }

    /** Classifies a game entity at a sensor. */
    public static Classification classify(AccessResolver resolver, @Nullable MinecraftServer server,
            @Nullable Installation installation, DevicePos at, @Nullable Entity entity, SensorSettings settings) {
        return classify(resolver, server, installation, at, describe(entity), settings);
    }

    /** The pure rule. */
    public static Classification classify(AccessResolver resolver, @Nullable MinecraftServer server,
            @Nullable Installation installation, DevicePos at, Sensed sensed, SensorSettings settings) {
        if (installation == null || sensed == null || sensed.kind() == Kind.IGNORED) {
            return Classification.IGNORE;
        }
        SensorSettings s = settings == null ? SensorSettings.DEFAULT : settings;
        if (sensed.subject() != null) {
            AccessDecision decision = resolver.resolve(
                    new AccessQuery(server, sensed.subject(), installation, AccessAction.SENSOR_PASS, at), s.minLevel());
            return decision.allowed() ? Classification.PERMITTED : Classification.INTRUDER;
        }
        return switch (s.entityMode()) {
            case PLAYERS_ONLY -> Classification.IGNORE;
            case PLAYERS_AND_HOSTILES -> sensed.kind() == Kind.HOSTILE ? Classification.INTRUDER : Classification.IGNORE;
            case ALL_LIVING -> Classification.INTRUDER;
        };
    }
}
