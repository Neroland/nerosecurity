package za.co.neroland.nerosecurity.link;

import za.co.neroland.nerosecurity.NeroSecurityCommon;

/**
 * NeroSecurity's module for Neroland Core's link API ({@code za.co.neroland.nerolandcore.link}),
 * through which a companion client can read player-scoped security data.
 *
 * <p><b>Placeholder.</b> This build registers nothing with {@code NeroLinkRegistry}, so the bridge
 * keeps reporting {@code nerosecurity} as absent. {@link #init()} is already called as the
 * <b>last</b> step of common init — the slot a real module takes, after every store, compat probe
 * and network declaration it would read — so filling it in later changes no ordering.
 *
 * <p>When it is filled in, it must scope every section by what NeroSecurity actually records (the
 * installation owner, never a server-wide roster), never authorise with a nearest-player lookup,
 * and never put a player name, UUID or position into an event scope.
 */
public final class SecurityLinkModule {

    /** The link module id the bridge already lists. */
    public static final String MODULE_ID = NeroSecurityCommon.MOD_ID;

    private SecurityLinkModule() {
    }

    /** Last step of common init. Currently logs at debug and returns; it can never break startup. */
    public static void init() {
        try {
            NeroSecurityCommon.LOGGER.debug("[NeroSecurity] link module not registered in this build");
        } catch (RuntimeException | LinkageError e) {
            // A link module is optional; it must never take mod construction down with it.
        }
    }
}
