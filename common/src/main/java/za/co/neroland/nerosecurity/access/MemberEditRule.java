package za.co.neroland.nerosecurity.access;

/**
 * Who may change whose member level. Kept in the access package so level comparisons never leak
 * into blocks, payload handlers or commands.
 *
 * <p>The rule: the actor (at the level the resolver just reported) may only touch a player who is
 * strictly below them, and only to set a level strictly below them. Nobody can set OWNER and nobody
 * can touch an owner, so the owner set is unreachable through member editing. An OFFICER can
 * therefore only add, remove or change MEMBER rows; only an OWNER (or an operator) manages officers.
 */
public final class MemberEditRule {

    private MemberEditRule() {
    }

    /**
     * @param actor         the editor's effective level (OWNER for an operator override)
     * @param targetCurrent the target's current level: OWNER for an owner, PUBLIC when not a member
     * @param targetNew     the requested level: PUBLIC removes the row
     */
    public static boolean canEdit(AccessLevel actor, AccessLevel targetCurrent, AccessLevel targetNew) {
        if (actor == null || targetCurrent == null || targetNew == null) {
            return false;
        }
        if (targetNew == AccessLevel.OWNER || targetCurrent == AccessLevel.OWNER) {
            return false;
        }
        return targetCurrent.ordinal() < actor.ordinal() && targetNew.ordinal() < actor.ordinal();
    }
}
