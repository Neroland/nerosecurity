package za.co.neroland.nerosecurity.access;

/** Why an access decision was refused ({@link #NONE} when it was allowed). */
public enum DenyReason {
    NONE,
    INSUFFICIENT_LEVEL,
    LOCKDOWN,
    SOURCE_DENY,
    ADMIN_ONLY,
    UNKNOWN_INSTALLATION
}
