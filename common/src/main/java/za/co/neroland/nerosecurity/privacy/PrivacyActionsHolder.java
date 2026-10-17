package za.co.neroland.nerosecurity.privacy;

/** Holds the installed {@link PrivacyActions}; package-private so only the interface's statics touch it. */
final class PrivacyActionsHolder {

    static volatile PrivacyActions current = SecurityPrivacyActions.INSTANCE;

    private PrivacyActionsHolder() {
    }
}
