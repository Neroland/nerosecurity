package za.co.neroland.nerosecurity.keycard;

/** The outcome of verifying one presented card against one installation. */
public enum CardStatus {
    /** Signed by this installation, in the ledger, matching it, and presented by its holder if bound. */
    VALID,
    /** No card component at all. */
    BLANK,
    /** A card for a different installation. Not an attack signal: no event is raised. */
    WRONG_INSTALLATION,
    /** The tag does not match: a forged or edited card. */
    BAD_SIGNATURE,
    /** Issued before the last "revoke all". */
    BELOW_FLOOR,
    /** The serial has no ledger row: revoked, wiped, or never issued. */
    UNKNOWN_SERIAL,
    /** The ledger row disagrees with the card. */
    RECORD_MISMATCH,
    /** A bound card whose holder is no longer recorded (erased). */
    REVOKED_BINDING,
    /** A bound card presented by someone other than its holder. */
    NOT_BOUND_HOLDER
}
