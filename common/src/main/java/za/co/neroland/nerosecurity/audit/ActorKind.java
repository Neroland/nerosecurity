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

    /**
     * The kind an identity-free surface (monitor, hub event rows, link rows without names, push copy) may show:
     * {@code WITHHELD} reads as {@code PLAYER}, because telling "a player" from "an anonymous player" there
     * would reveal who has opted out. Only the named views (the audited log read) keep {@code WITHHELD}.
     */
    public ActorKind identityFree() {
        return this == WITHHELD ? PLAYER : this;
    }
}
