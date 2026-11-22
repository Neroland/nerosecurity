package za.co.neroland.nerosecurity.progression;

import java.util.Collection;
import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerolandcore.progression.ProgressionGates;
import za.co.neroland.nerosecurity.NeroSecurityCommon;

/**
 * The personal security milestones NeroSecurity records as player-scope Neroland Core gates. Each one is a
 * gate file under {@code data/nerosecurity/neroland_gates/} with no {@code requires}; a quest pack can read
 * them with a {@code gate_open} objective, and nothing else may read them.
 *
 * <p>Future security code calls {@link #record(ServerPlayer, Milestone)} at the moment the milestone
 * happens (see each constant). Recording is idempotent: Core ignores an open on an already-open gate.
 *
 * <p>Rules (non-negotiable):
 * <ul>
 * <li><b>Never read a milestone gate to decide access.</b> Access comes from keycards, owners, factions and
 * claims. A milestone is a record of what a player has done, not a permission.</li>
 * <li><b>One threshold channel only.</b> Lockdown enter/exit fires Core ThresholdEvents channel
 * {@code nerosecurity:lockdown} (scope is an opaque installation id, never a player, name or position); no
 * other security event is broadcast. Milestones themselves never fire a crossing.</li>
 * <li><b>Privacy (POPIA/GDPR).</b> The player's UUID goes only into {@code ProgressionGates.open}, which
 * stores it in Core's progression store (covered by Core's privacy disclosure). Nothing here logs a name,
 * a UUID or a position, and access logs or camera data never reach a gate.</li>
 * </ul>
 */
public final class SecurityMilestones {

    /** One recordable milestone and the gate it opens. */
    public enum Milestone {
        /** The player encoded their first keycard. */
        FIRST_KEYCARD("first_keycard"),
        /** The player registered a security hub and linked at least one device to it. */
        INSTALLATION_ONLINE("installation_online"),
        /** The owner triggered and then cleared a lockdown on their own installation (a drill, not an alarm). */
        LOCKDOWN_DRILL("lockdown_drill"),
        /** The player wrote their first encrypted keycard (an owner-only card from a programmer with a module). */
        FIRST_ENCRYPTED_KEYCARD("first_encrypted_keycard"),
        /**
         * An installation the player owns reached fortress strength: a hub with at least
         * {@link #FORTRESS_MIN_CAMERAS} cameras, a force field emitter and a blast door linked to it.
         */
        FORTRESS("fortress");

        private final Identifier gate;

        Milestone(String path) {
            this.gate = Identifier.fromNamespaceAndPath(NeroSecurityCommon.MOD_ID, path);
        }

        /** The player-scope record gate this milestone opens. */
        public Identifier gate() {
            return gate;
        }
    }

    /** Cameras an installation needs, alongside a force field emitter and a blast door, for {@link Milestone#FORTRESS}. */
    public static final int FORTRESS_MIN_CAMERAS = 4;

    private static final String CAMERA = "security_camera";
    private static final String FORCE_FIELD = "force_field_emitter";
    private static final String BLAST_DOOR = "blast_door";

    private SecurityMilestones() {
    }

    /**
     * Record {@code milestone} for an online player. Safe to call repeatedly.
     *
     * @return {@code true} if this call opened the gate (first time for this player)
     */
    public static boolean record(ServerPlayer player, Milestone milestone) {
        return ProgressionGates.open(player, milestone.gate());
    }

    /**
     * Record {@code milestone} for a player who may be offline, e.g. an installation that finishes linking
     * after its owner logged out. Uses the offline-capable Core 1.15.0 API. Safe to call repeatedly.
     *
     * @return {@code true} if this call opened the gate (first time for this player)
     */
    public static boolean record(MinecraftServer server, UUID playerId, Milestone milestone) {
        return ProgressionGates.open(server, playerId, milestone.gate());
    }

    /**
     * Whether a hub with these linked devices counts as a fortress: at least {@link #FORTRESS_MIN_CAMERAS}
     * security cameras, at least one force field emitter and at least one blast door, all NeroSecurity
     * blocks. Pure, so it runs in a plain JVM; the device map holds block ids only (no players).
     */
    public static boolean isFortress(boolean hasHub, Collection<Identifier> linkedBlocks) {
        if (!hasHub) {
            return false;
        }
        int cameras = 0;
        boolean field = false;
        boolean door = false;
        for (Identifier block : linkedBlocks) {
            if (block == null || !NeroSecurityCommon.MOD_ID.equals(block.getNamespace())) {
                continue;
            }
            switch (block.getPath()) {
                case CAMERA -> cameras++;
                case FORCE_FIELD -> field = true;
                case BLAST_DOOR -> door = true;
                default -> {
                }
            }
        }
        return cameras >= FORTRESS_MIN_CAMERAS && field && door;
    }
}
