package za.co.neroland.nerosecurity.link;

import java.util.List;
import java.util.UUID;

import za.co.neroland.nerolandcore.link.LinkModuleInfo;
import za.co.neroland.nerolandcore.link.NeroLinkRegistry;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.data.SecurityData;
import za.co.neroland.nerosecurity.platform.Services;

/**
 * NeroSecurity's module for Neroland Core's link API, read by the NeroLink bridge and its companion app.
 *
 * <ul>
 *   <li><b>Sections</b> ({@link SecurityLinkSnapshots}): {@code installations}, {@code events},
 *       {@code devices}, the legacy {@code log} summary and the public {@code wiki}.</li>
 *   <li><b>Actions</b> ({@link SecurityLinkActions}): {@code lockdown} (off by default), {@code ack_event},
 *       {@code silence_alarm}. Owner-only, rate-limited and idempotent.</li>
 *   <li><b>Live events</b> ({@link SecurityLinkEvents}): topic {@code nerosecurity.events}, coalesced phone
 *       notifications built only by {@link PushCopy}, and Core alerts.</li>
 *   <li>The in-game login summary ({@link LoginAlertSummary}) and {@code /nerosecurity alerts}.</li>
 * </ul>
 *
 * <p>Scope is always the requester's stored role at an installation ({@link SecurityLinkAccess}); nothing
 * authorises by proximity, no section carries a coordinate or another player's id, and no event scope
 * carries a player name, id or position. The module persists nothing of its own; its transient per-player
 * state joins the composite eraser as store {@code "link"}.
 *
 * <p>{@link #init()} is the last step of common init, guarded: a missing or older Core link API logs one
 * warning and leaves every installation working.
 */
public final class SecurityLinkModule {

    /** The link module id the bridge lists. */
    public static final String MODULE_ID = NeroSecurityCommon.MOD_ID;
    public static final int SCHEMA_VERSION = 1;

    public static final String SECTION_INSTALLATIONS = "installations";
    public static final String SECTION_EVENTS = "events";
    public static final String SECTION_DEVICES = "devices";
    public static final String SECTION_LOG = "log";
    public static final String SECTION_WIKI = "wiki";
    public static final List<String> SECTIONS = List.of(SECTION_INSTALLATIONS, SECTION_EVENTS, SECTION_DEVICES,
            SECTION_LOG, SECTION_WIKI);

    public static final String ACTION_LOCKDOWN = "lockdown";
    public static final String ACTION_ACK = "ack_event";
    public static final String ACTION_SILENCE = "silence_alarm";
    /** The whole list: nothing else is ever an action. */
    public static final List<String> ACTIONS = List.of(ACTION_LOCKDOWN, ACTION_ACK, ACTION_SILENCE);

    private static final LinkRequestDedup DEDUP = new LinkRequestDedup();
    private static final LinkRateLimiter LIMITER = LinkRateLimiter.standard();
    private static volatile SecurityLinkEvents.Pipeline pipeline;

    private SecurityLinkModule() {
    }

    /** Last step of common init. Guarded; it can never break startup. */
    public static void init() {
        try {
            if (!NeroSecurityConfig.isLinkModuleEnabled()) {
                NeroSecurityCommon.LOGGER.info("[NeroSecurity] link module disabled by config (linkModuleEnabled=false).");
                return;
            }
            LinkContext ctx = LiveLinkContext.INSTANCE;
            SecurityLinkEvents.Pipeline events = new SecurityLinkEvents.Pipeline(ctx);
            pipeline = events;
            // Erasure first, so no transient per-player state can exist without its eraser.
            SecurityData.addStore(SecurityLinkEvents.eraser(events, DEDUP, LIMITER));
            LinkModuleInfo info = new LinkModuleInfo(MODULE_ID, modVersion(), SCHEMA_VERSION, SECTIONS, ACTIONS);
            NeroLinkRegistry.registerSnapshotProvider(new SecurityLinkSnapshots(ctx), info);
            NeroLinkRegistry.registerActionHandler(new SecurityLinkActions(ctx, DEDUP, LIMITER), info);
            SecurityLinkEvents.init(events);
            LoginAlertSummary.init(ctx);
            NeroSecurityCommon.LOGGER.info("[NeroSecurity] link module registered ({} sections, {} actions).",
                    SECTIONS.size(), ACTIONS.size());
        } catch (RuntimeException | LinkageError e) {
            // A link module is optional; it must never take mod construction down with it.
            NeroSecurityCommon.LOGGER.warn("[NeroSecurity] link module not registered ({}); installations are "
                    + "unaffected.", e.getClass().getName());
        }
    }

    /** Whether the link module holds any transient state for {@code player} (erasure-conformance probe). */
    public static boolean holdsAnyFor(UUID player) {
        SecurityLinkEvents.Pipeline p = pipeline;
        return (p != null && p.holdsAnyFor(player)) || DEDUP.holdsAnyFor(player) || LIMITER.holdsAnyFor(player)
                || LoginAlertSummary.holdsAnyFor(player);
    }

    private static String modVersion() {
        try {
            String version = Services.PLATFORM.getModVersion();
            return version == null || version.isBlank() ? "unknown" : version;
        } catch (RuntimeException | LinkageError e) {
            return "unknown";
        }
    }
}
