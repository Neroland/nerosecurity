package za.co.neroland.nerosecurity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import za.co.neroland.nerosecurity.access.AccessSources;
import za.co.neroland.nerosecurity.access.KeycardSource;
import za.co.neroland.nerosecurity.alarm.AlarmDispatcher;
import za.co.neroland.nerosecurity.audit.AuditRecorder;
import za.co.neroland.nerosecurity.audit.RetentionSweeper;
import za.co.neroland.nerosecurity.audit.SecurityAuditLog;
import za.co.neroland.nerosecurity.block.field.FieldLockdownHook;
import za.co.neroland.nerosecurity.compat.CompatSources;
import za.co.neroland.nerosecurity.compat.claims.ClaimsCompat;
import za.co.neroland.nerosecurity.compat.colonies.ColoniesCompat;
import za.co.neroland.nerosecurity.compat.factions.FactionsCompat;
import za.co.neroland.nerosecurity.compat.nerospace.NerospaceCompat;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.data.SecurityData;
import za.co.neroland.nerosecurity.economy.SecurityEconomy;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.link.SecurityLinkModule;
import za.co.neroland.nerosecurity.lockdown.LockdownChannel;
import za.co.neroland.nerosecurity.lockdown.LockdownHooks;
import za.co.neroland.nerosecurity.lockdown.LockdownScheduler;
import za.co.neroland.nerosecurity.lockdown.VisualLockdownHook;
import za.co.neroland.nerosecurity.notice.RecordingNotice;
import za.co.neroland.nerosecurity.network.SecurityNetwork;
import za.co.neroland.nerosecurity.platform.Services;
import za.co.neroland.nerosecurity.privacy.SecurityPrivacy;
import za.co.neroland.nerosecurity.registry.AlarmRegistrations;
import za.co.neroland.nerosecurity.registry.CameraRegistrations;
import za.co.neroland.nerosecurity.registry.ChestRegistrations;
import za.co.neroland.nerosecurity.registry.DoorRegistrations;
import za.co.neroland.nerosecurity.registry.FieldRegistrations;
import za.co.neroland.nerosecurity.registry.LaserRegistrations;
import za.co.neroland.nerosecurity.registry.MonitorRegistrations;
import za.co.neroland.nerosecurity.registry.NeroSecurityBlockEntities;
import za.co.neroland.nerosecurity.registry.NeroSecurityBlocks;
import za.co.neroland.nerosecurity.registry.NeroSecurityDataComponents;
import za.co.neroland.nerosecurity.registry.NeroSecurityItems;
import za.co.neroland.nerosecurity.registry.NeroSecurityMenus;
import za.co.neroland.nerosecurity.registry.ProgrammerRegistrations;
import za.co.neroland.nerosecurity.server.ServerHooks;
import za.co.neroland.nerosecurity.telemetry.NeroSecurityTelemetry;

/**
 * Loader-agnostic entry point for NeroSecurity. Each loader entry point (Fabric / Forge / NeoForge)
 * calls {@link #init()} once during mod construction, then registers the network payloads that
 * {@link #init()} declared on NeroSecurity's own {@code nerosecurity:main} channel.
 *
 * <p>In place: platform and network seams, config, anonymous crash telemetry, the data-erasure hook,
 * the installation store and access resolver, the Security Hub, Linking Tool and keycards, the Keycard
 * Programmer, the Security and Blast Doors, the Secure Chest, the optional NeroEconomy bridge, and the
 * link-module slot. Every GUI opens through
 * {@link za.co.neroland.nerosecurity.menu.MenuOpener}.
 *
 * <p>Progression: personal security milestones are recorded through
 * {@link za.co.neroland.nerosecurity.progression.SecurityMilestones} as player-scope Neroland Core gates;
 * they are records for quest packs, never an access check.
 */
public final class NeroSecurityCommon {

    public static final String MOD_ID = "nerosecurity";
    public static final Logger LOGGER = LoggerFactory.getLogger("NeroSecurity");

    private NeroSecurityCommon() {
    }

    /** Called once per loader during mod construction. The numbered order is load-bearing. */
    public static void init() {
        LOGGER.info("[NeroSecurity] common init");

        // 0. Platform seams (PlatformInfo + NetworkPlatform), resolved here during construction and
        //    never lazily on a tick path — a late ServiceLoader read can throw
        //    ServiceConfigurationError out of gameplay code.
        Services.init();

        // 1. Config first: everything below reads it, including telemetry's opt-out flag.
        NeroSecurityConfig.init();

        // 2. Anonymous, NeroSecurity-only crash reporting. Must follow the config registration and
        //    precede the rest of init so early failures are still reported. On by default in official
        //    builds; opt out with telemetryEnabled=false (takes effect on restart). See PRIVACY.md.
        NeroSecurityTelemetry.init();

        // 3. Registries, through Core's RegistrationProvider (NeoForge/Forge entry points attach them to
        //    the mod bus; on Fabric these calls ARE the registration). Items join Core's shared tab.
        NeroSecurityBlocks.init();
        NeroSecurityBlockEntities.init();
        NeroSecurityDataComponents.init();
        NeroSecurityItems.init();
        NeroSecurityMenus.init();
        // 3a. Access devices, after the base registries whose providers they register through:
        //     the keycard programmer, the security and blast doors, and the secure chest.
        ProgrammerRegistrations.init();
        DoorRegistrations.init();
        ChestRegistrations.init();
        // 3b. Sensors and alerts: the laser grid node (and the sensor settings menu it shares with the
        //     camera), the security camera, the alarm and the security monitor.
        LaserRegistrations.init();
        CameraRegistrations.init();
        AlarmRegistrations.init();
        MonitorRegistrations.init();
        // 3c. Force fields: the force field emitter, the field block it projects and the field settings menu.
        FieldRegistrations.init();
        NeroSecurityItems.addToCreativeTab();
        ProgrammerRegistrations.addToCreativeTab();
        DoorRegistrations.addToCreativeTab();
        ChestRegistrations.addToCreativeTab();
        LaserRegistrations.addToCreativeTab();
        CameraRegistrations.addToCreativeTab();
        AlarmRegistrations.addToCreativeTab();
        MonitorRegistrations.addToCreativeTab();
        FieldRegistrations.addToCreativeTab();

        // 4. Player-data erasure (POPIA/GDPR), registered early: before any store or compat code,
        //    so nothing that holds player data can come online ahead of its erasure path. Every
        //    player-keyed store joins SecurityData's composite eraser.
        SecurityData.init();
        // 4a. The installation store joins the composite eraser before any server starts.
        SecurityInstallations.registerEraser();
        // 4b. Access sources: the built-in membership source is always first.
        AccessSources.init();
        //     The keycard source's transient rejection cooldown joins the composite eraser.
        KeycardSource.registerEraser();
        // 4c. Lockdown transitions are announced on Core's threshold channel.
        LockdownChannel.init();
        // 4d. The audit store joins the composite eraser before any server starts.
        SecurityAuditLog.registerEraser();
        // 4e. The one listener that persists security events (binds to the server via ServerHooks).
        AuditRecorder.init();
        // 4f. Alarms: the dispatcher subscribes to the event bus, drains on the server tick and re-arms
        //     every alarm when a lockdown begins.
        AlarmDispatcher.init();
        // 4g. The "recording active" notice for players entering a monitored area.
        RecordingNotice.init();
        // 4h. Audit retention: purge at server start and on the configured cadence.
        RetentionSweeper.init();
        // 4i. Alarm lockdowns release themselves when their time box runs out.
        LockdownScheduler.init();
        // 4j. Integration access sources (faction, colony, station, claim), after the built-in ones from
        //     4b. Always registered; each abstains while its mod is absent or its switch is off. Their
        //     once-per-session suspension notices reset when the server stops.
        CompatSources.init();
        ServerHooks.onStopping(server -> CompatSources.clearSessionState());
        // 4k. Force fields react to lockdown at once (strength boost, repaint, resync); isolated by
        //     LockdownHooks, and only an accelerator: emitters also read lockdown on their own tick.
        LockdownHooks.register(new FieldLockdownHook());
        // 4l. Device visuals react to lockdown at once (hub and monitor look, door clamps, the lockdown tone);
        //     isolated, and only an accelerator: each device re-derives the same state on its own refresh.
        LockdownHooks.register(new VisualLockdownHook());
        // 4m. Privacy (POPIA/GDPR): the opt-out store and export files join the composite eraser, the opt-out
        //     becomes the audit recorder's actor policy, and the hub's Privacy tab actions go live.
        SecurityPrivacy.init();

        // 5a. Integration bridges, each found through ServiceLoader only when its mod is loaded and this
        //     build includes it. The factions façade also listens for reputation tier crossings, which
        //     mark every force field's verdict cache stale.
        FactionsCompat.init();
        ColoniesCompat.init();
        NerospaceCompat.init();
        ClaimsCompat.init();

        // 5. Compat detection. Optional NeroEconomy bridge for paid security tiers: touches
        //    NeroEconomy only when it is loaded and new enough; claims must happen here, before the
        //    first server starts.
        SecurityEconomy.init();

        // 6. Network: declare payloads once. Each loader registers them on nerosecurity:main right
        //    after this method returns (Forge seals its channel at build()).
        SecurityNetwork.init();

        // 7. Link module LAST — it reads everything above. Guarded; can never break startup.
        SecurityLinkModule.init();
    }
}
