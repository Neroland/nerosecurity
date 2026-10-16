package za.co.neroland.nerosecurity.audit;

/**
 * What kind of actor an audit row has, without saying who: the only actor information the monitor,
 * alarm screens and other identity-free surfaces ever see.
 */
public enum ActorKind {
    /** No player: a mob, automation, an explosion, the console, a timer. */
    NONE,
    /** A player, identity not shown here. */
    PLAYER,
    /** A player whose privacy opt-out withheld their identity ("anonymous"). */
    WITHHELD;

    public static ActorKind of(AuditEntry entry) {
        if (entry.actorWithheld()) {
            return WITHHELD;
        }
        return entry.actor() == null ? NONE : PLAYER;
    }
}
