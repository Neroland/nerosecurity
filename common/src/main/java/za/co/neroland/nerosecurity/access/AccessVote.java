package za.co.neroland.nerosecurity.access;

/** One source's answer to an {@link AccessQuery}. */
public sealed interface AccessVote permits AccessVote.Grant, AccessVote.Deny, AccessVote.Abstain {

    /** The source grants at least {@code level}. The highest grant across all sources wins. */
    record Grant(AccessLevel level) implements AccessVote {
    }

    /**
     * The source refuses. One deny is final, whatever any other source grants.
     *
     * @param detailKey a translation key; never player data
     */
    record Deny(DenyReason reason, String detailKey) implements AccessVote {
    }

    /** The source has no opinion. */
    enum Abstain implements AccessVote {
        INSTANCE
    }
}
