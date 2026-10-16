package za.co.neroland.nerosecurity.config;

import java.util.concurrent.atomic.AtomicBoolean;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.config.ConfigManager;
import za.co.neroland.nerolandcore.config.ConfigSchema;
import za.co.neroland.nerolandcore.config.ConfigValue;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.event.SecuritySeverity;

/**
 * NeroSecurity config schema, built on Neroland Core's config framework (file
 * {@code config/nerosecurity.properties}, hot-reloadable via {@code /neroland config reload}).
 * Registered once from {@link NeroSecurityCommon#init()}, before anything else — every other
 * subsystem reads it, including telemetry's opt-out flag.
 *
 * <p>Keys land with the features that read them, so the file players see always matches what the
 * mod actually does. Every gameplay key is server-authoritative.
 *
 * <p><b>POPIA/GDPR:</b> {@code telemetryEnabled} is deliberately <b>not</b> server-authoritative —
 * anonymous crash reporting is a per-client opt-out that a server must never force on or off.
 */
public final class NeroSecurityConfig {

    public static final ConfigSchema SCHEMA =
            ConfigSchema.create(NeroSecurityCommon.MOD_ID, "NeroSecurity configuration.");

    /** The absolute retention ceiling (days): the schema bound of every retention key and a runtime clamp. */
    public static final int MAX_RETENTION_DAYS = 90;

    // --- Crash telemetry (client-local opt-out) -----------------------------

    private static final ConfigValue<Boolean> TELEMETRY = SCHEMA.bool(
            "telemetryEnabled", true, false,
            "send anonymous, NeroSecurity-only crash reports (Sentry, EU servers) - ON by default. Sends the "
                    + "stack trace, mod/MC/loader/OS/Java versions and your other installed mods' ids "
                    + "and versions; never your IP, username, UUID, world data or chat; file paths are "
                    + "scrubbed of your account name. Set false to opt out of all of it (takes effect "
                    + "on restart). See PRIVACY.md");

    // --- Kill switches (server-authoritative) --------------------------------

    private static final ConfigValue<Boolean> LOCKDOWN_ENABLED = SCHEMA.bool(
            "lockdownEnabled", true, true,
            "-- Kill switches -- Whether installations can be locked down. While false, lockdown cannot be "
                    + "entered and any stored lockdown state is ignored (it is kept, so turning this back on "
                    + "restores it).");

    private static final ConfigValue<Boolean> LOCKDOWN_CHANNEL_ENABLED = SCHEMA.bool(
            "lockdownChannelEnabled", true, true,
            "Whether lockdown on/off is announced on the Neroland Core threshold channel "
                    + "nerosecurity:lockdown (for quest packs). The scope is an opaque installation id; "
                    + "never a player, name or position.");

    private static final ConfigValue<Boolean> FORCE_FIELDS_ENABLED = SCHEMA.bool(
            "forceFieldsEnabled", true, true,
            "Whether force field emitters project anything. While false emitters stay idle and any field blocks "
                    + "left in the world are passable to everyone and fade.");

    private static final ConfigValue<Boolean> FACTIONS_INTEGRATION_ENABLED = SCHEMA.bool(
            "factionsIntegrationEnabled", true, true,
            "Whether NeroFactions membership on a bound faction may grant access (never above officer).");

    private static final ConfigValue<Boolean> COLONIES_INTEGRATION_ENABLED = SCHEMA.bool(
            "coloniesIntegrationEnabled", true, true,
            "Whether NeroColonies roles on a bound colony may grant access (never above officer).");

    private static final ConfigValue<Boolean> NEROSPACE_INTEGRATION_ENABLED = SCHEMA.bool(
            "nerospaceIntegrationEnabled", true, true,
            "Whether the current owner of a bound Nerospace station gets officer on that installation.");

    private static final ConfigValue<Boolean> CLAIMS_INTEGRATION_ENABLED = SCHEMA.bool(
            "claimsIntegrationEnabled", true, true,
            "Whether a land-claim mod (Open Parties and Claims) may refuse access on claimed land. Claims only "
                    + "ever refuse; they never grant.");

    // --- Installations (server-authoritative) --------------------------------

    private static final ConfigValue<Integer> MAX_INSTALLATIONS_PER_PLAYER = SCHEMA.intRange(
            "maxInstallationsPerPlayer", 8, 1, 64, true,
            "-- Installations -- How many security hubs one player may own (anti-spam).");

    private static final ConfigValue<Integer> MAX_MEMBERS_PER_INSTALLATION = SCHEMA.intRange(
            "maxMembersPerInstallation", 64, 1, 1000, true,
            "Member rows per installation, owners not counted.");

    private static final ConfigValue<Integer> MAX_DEVICE_DISTANCE = SCHEMA.intRange(
            "maxDeviceDistance", 128, 16, 1024, true,
            "How far (Manhattan blocks) from its hub a device may be linked. Same dimension only.");

    private static final ConfigValue<Integer> MAX_DEVICES_PER_INSTALLATION = SCHEMA.intRange(
            "maxDevicesPerInstallation", 256, 16, 4096, true,
            "How many devices one installation may have linked (bounds store size).");

    // --- Keycards (server-authoritative) -------------------------------------

    private static final ConfigValue<Integer> KEYCARDS_PER_INSTALLATION = SCHEMA.intRange(
            "keycardsPerInstallation", 512, 16, 4096, true,
            "-- Keycards -- Issued-card ledger cap per installation. At the cap the programmer refuses to "
                    + "write or copy cards until the owner revokes some; nothing is ever evicted.");

    private static final ConfigValue<String> KEYCARD_BASIC_MAX_LEVEL = SCHEMA.string(
            "keycardBasicMaxLevel", "officer", true,
            "Highest level a non-encrypted (copyable) card may carry: member, officer or owner. OWNER cards "
                    + "must be encrypted unless this is raised to owner. Anything else reads as officer.");

    // --- Doors (server-authoritative) -----------------------------------------

    private static final ConfigValue<Boolean> DOORS_REQUIRE_POWER = SCHEMA.bool(
            "doorsRequirePower", false, true,
            "-- Doors -- When true, an unpowered door admits only its owners and operators. False suits "
                    + "servers without a power mod, where doors could never be charged.");

    private static final ConfigValue<Integer> DOOR_POWER_PER_TICK = SCHEMA.intRange(
            "doorPowerPerTick", 2, 0, 100, true,
            "Energy a linked, closed door draws each tick while it has energy.");

    private static final ConfigValue<Integer> DOOR_OPEN_COST = SCHEMA.intRange(
            "doorOpenCost", 200, 0, 100000, true,
            "Energy taken per opening when available; also the threshold doorsRequirePower checks.");

    private static final ConfigValue<Integer> DOOR_ENERGY_CAPACITY = SCHEMA.intRange(
            "doorEnergyCapacity", 20000, 1000, 1000000, true,
            "Energy buffer per door.");

    private static final ConfigValue<Integer> DOOR_OPEN_TICKS = SCHEMA.intRange(
            "doorOpenTicks", 20, 5, 100, true,
            "Base open/close time in ticks. Each open-speed module divides it by (1 + 0.5 x modules).");

    private static final ConfigValue<Integer> DOOR_AUTO_CLOSE_TICKS = SCHEMA.intRange(
            "doorAutoCloseTicks", 60, 0, 1200, true,
            "Ticks a fully open door waits before closing itself. 0 = stays open until used again.");

    private static final ConfigValue<Double> DOOR_MAX_BLAST_RESISTANCE = SCHEMA.doubleRange(
            "doorMaxBlastResistance", 3600.0, 100.0, 100000.0, true,
            "Hardness modules can never push a door's blast resistance past this (obsidian is 1200).");

    private static final ConfigValue<Boolean> DOOR_REDSTONE_ALLOWED = SCHEMA.bool(
            "doorRedstoneAllowed", true, true,
            "Whether a door may be set to open on redstone. Redstone never opens a door during lockdown.");

    private static final ConfigValue<Integer> DOOR_DENIED_EVENT_COOLDOWN_TICKS = SCHEMA.intRange(
            "doorDeniedEventCooldownTicks", 40, 0, 1200, true,
            "Per actor per device: minimum spacing of door/chest denied events. The deny message is "
                    + "always shown; this only limits event volume.");

    // --- Secure chests (server-authoritative) ---------------------------------

    private static final ConfigValue<String> CHEST_AUTOMATION_DEFAULT = SCHEMA.string(
            "chestAutomationDefault", "off", true,
            "-- Secure chests -- Automation policy for newly registered installations: off, member or "
                    + "officer. Hoppers and pipes act as that level; never during lockdown or on a "
                    + "keycard-only chest. Anything else reads as off.");

    private static final ConfigValue<Integer> CHEST_AUTOMATION_EVENT_INTERVAL_TICKS = SCHEMA.intRange(
            "chestAutomationEventIntervalTicks", 1200, 20, 72000, true,
            "Minimum spacing of automation-caused chest-changed events per chest.");

    // --- Sensors, alarms, audit (server-authoritative) ---------------------------

    private static final ConfigValue<Boolean> CAMERAS_ENABLED = SCHEMA.bool(
            "camerasEnabled", true, true,
            "-- Kill switches (sensors) -- Whether cameras sense anything. While false every camera is dark "
                    + "and raises nothing; the blocks stay.");

    private static final ConfigValue<Boolean> LOG_ENABLED = SCHEMA.bool(
            "auditLogEnabled", true, true,
            "Whether security events are written to the audit log. While false nothing is persisted and log "
                    + "reads say so; events still drive alarms.");

    private static final ConfigValue<Boolean> ALARM_CHAT_ENABLED = SCHEMA.bool(
            "alarmChatEnabled", true, true,
            "Whether alarms may message online owners and officers. Messages name the installation and the "
                    + "event, never a player.");

    private static final ConfigValue<Boolean> ALARM_LOCKDOWN_ENABLED = SCHEMA.bool(
            "alarmLockdownEnabled", true, true,
            "Whether any alarm may be configured (by its owner) to trigger lockdown. Off per alarm by default.");

    private static final ConfigValue<Integer> PRIVACY_LOG_RETENTION_DAYS = SCHEMA.intRange(
            "privacyAuditRetentionDays", 7, 1, MAX_RETENTION_DAYS, true,
            "-- Privacy -- Audit entries older than this many days are purged at server start and every "
                    + "privacyPurgeIntervalMinutes. Never more than privacyRetentionHardCapDays.");

    private static final ConfigValue<Integer> PRIVACY_CAMERA_EVENT_RETENTION_MINUTES = SCHEMA.intRange(
            "privacyCameraEventRetentionMinutes", 60, 1, 1440, true,
            "Presence events (motion detected, member seen by camera) are purged after this many minutes; "
                    + "one day is the ceiling.");

    private static final ConfigValue<Integer> PRIVACY_LOG_MAX_ENTRIES = SCHEMA.intRange(
            "privacyAuditMaxEntriesPerInstallation", 500, 10, 5000, true,
            "Audit entries kept per installation (a ring buffer: the oldest are evicted first).");

    private static final ConfigValue<Integer> PRIVACY_RETENTION_HARD_CAP_DAYS = SCHEMA.intRange(
            "privacyRetentionHardCapDays", MAX_RETENTION_DAYS, 1, MAX_RETENTION_DAYS, true,
            "LOUD: the ceiling for every retention setting, per-installation overrides included. Nothing "
                    + "NeroSecurity logs is ever kept longer than 90 days.");

    private static final ConfigValue<Integer> PRIVACY_PURGE_INTERVAL_MINUTES = SCHEMA.intRange(
            "privacyPurgeIntervalMinutes", 60, 5, 1440, true,
            "How often the retention sweep runs (it also runs at server start and before every log read).");

    private static final ConfigValue<Boolean> PRIVACY_CAMERA_LOGS_MEMBERS = SCHEMA.bool(
            "privacyCameraLogsMembers", false, true,
            "When true, cameras also log permitted players (member seen by camera). Off by default: members "
                    + "are not logged.");

    private static final ConfigValue<Integer> LASER_BASE_RANGE = SCHEMA.intRange(
            "laserBaseRange", 8, 2, 32, true,
            "-- Laser grid -- Blocks a node pairs across; each range module adds 4.");

    private static final ConfigValue<Integer> LASER_MAX_RANGE = SCHEMA.intRange(
            "laserMaxRange", 32, 2, 64, true,
            "Hard cap on a node's pairing range after modules.");

    private static final ConfigValue<Integer> LASER_SCAN_INTERVAL_TICKS = SCHEMA.intRange(
            "laserScanIntervalTicks", 2, 1, 20, true,
            "Beam check cadence per pair, in ticks (staggered across nodes).");

    private static final ConfigValue<Integer> LASER_TRIP_COOLDOWN_TICKS = SCHEMA.intRange(
            "laserTripCooldownTicks", 100, 1, 1200, true,
            "One laser-tripped event per entity per node per this many ticks.");

    private static final ConfigValue<Double> LASER_TRIP_DAMAGE = SCHEMA.doubleRange(
            "laserTripDamage", 0.0, 0.0, 10.0, true,
            "Damage dealt to a non-permitted living entity when it trips a beam. 0 = none.");

    private static final ConfigValue<Integer> LASER_POWER_PER_TICK = SCHEMA.intRange(
            "laserPowerPerTick", 1, 0, 100, true,
            "Energy a node draws each tick while armed.");

    private static final ConfigValue<Integer> LASER_ENERGY_CAPACITY = SCHEMA.intRange(
            "laserEnergyCapacity", 4000, 500, 1000000, true,
            "Energy buffer per laser grid node.");

    private static final ConfigValue<Integer> CAMERA_BASE_RANGE = SCHEMA.intRange(
            "cameraBaseRange", 16, 4, 48, true,
            "-- Cameras -- Length of a camera's view cone; each range module adds 4.");

    private static final ConfigValue<Integer> CAMERA_MAX_RANGE = SCHEMA.intRange(
            "cameraMaxRange", 32, 4, 64, true,
            "Hard cap on a camera's range after modules.");

    private static final ConfigValue<Integer> CAMERA_SCAN_INTERVAL_TICKS = SCHEMA.intRange(
            "cameraScanIntervalTicks", 10, 2, 40, true,
            "Cone scan cadence per camera, in ticks (staggered across cameras).");

    private static final ConfigValue<Integer> CAMERA_MAX_RAYCASTS_PER_SCAN = SCHEMA.intRange(
            "cameraMaxRaycastsPerScan", 8, 1, 32, true,
            "Nearest candidates checked for line of sight per scan.");

    private static final ConfigValue<Integer> CAMERA_EVENT_COOLDOWN_TICKS = SCHEMA.intRange(
            "cameraEventCooldownTicks", 200, 20, 6000, true,
            "One motion-detected event per entity per camera per this many ticks.");

    private static final ConfigValue<Integer> CAMERA_SWEEP_TICKS = SCHEMA.intRange(
            "cameraSweepTicks", 200, 40, 1200, true,
            "Length of a camera's full pan cycle, in ticks.");

    private static final ConfigValue<Integer> CAMERA_POWER_PER_TICK = SCHEMA.intRange(
            "cameraPowerPerTick", 2, 0, 100, true,
            "Energy a camera draws each tick while armed.");

    private static final ConfigValue<Integer> CAMERA_ENERGY_CAPACITY = SCHEMA.intRange(
            "cameraEnergyCapacity", 4000, 500, 1000000, true,
            "Energy buffer per camera.");

    private static final ConfigValue<Integer> ALARM_COALESCE_SECONDS = SCHEMA.intRange(
            "alarmCoalesceSeconds", 10, 1, 120, true,
            "-- Alarms -- Events inside this window after a firing become one '+N more' firing.");

    private static final ConfigValue<Integer> ALARM_REDSTONE_PULSE_TICKS = SCHEMA.intRange(
            "alarmRedstonePulseTicks", 40, 1, 1200, true,
            "Redstone output length per firing, in ticks.");

    private static final ConfigValue<Integer> ALARM_ACTIVE_TICKS = SCHEMA.intRange(
            "alarmActiveTicks", 200, 20, 1200, true,
            "How long an alarm stays lit per firing, in ticks.");

    private static final ConfigValue<Integer> ALARM_SILENCE_MAX_MINUTES = SCHEMA.intRange(
            "alarmSilenceMaxMinutes", 60, 1, 1440, true,
            "Longest an officer may silence an alarm. A lockdown overrides the silence.");

    private static final ConfigValue<String> ALARM_LOCKDOWN_MIN_SEVERITY = SCHEMA.string(
            "alarmLockdownMinSeverity", "critical", true,
            "Lowest firing severity an alarm may lock down on: warn or critical. Anything else reads as critical.");

    private static final ConfigValue<Integer> ALARM_LOCKDOWN_AUTO_RELEASE_SECONDS = SCHEMA.intRange(
            "alarmLockdownAutoReleaseSeconds", 300, 30, 3600, true,
            "An alarm lockdown ends by itself after this many seconds unless an owner confirms it.");

    private static final ConfigValue<Integer> ALARM_LOCKDOWN_COOLDOWN_SECONDS = SCHEMA.intRange(
            "alarmLockdownCooldownSeconds", 600, 0, 86400, true,
            "After an alarm lockdown ends, alarms cannot start another on that installation for this long.");

    private static final ConfigValue<Integer> NOTICE_MARGIN_BLOCKS = SCHEMA.intRange(
            "noticeMarginBlocks", 16, 4, 64, true,
            "-- Notice -- How far beyond the outermost device the monitored-area notice is shown. The notice "
                    + "itself cannot be switched off while anything is logged.");

    private static final ConfigValue<Integer> NOTICE_CHECK_INTERVAL_TICKS = SCHEMA.intRange(
            "noticeCheckIntervalTicks", 40, 10, 200, true,
            "How often online players are checked against monitored areas, in ticks.");

    private static final ConfigValue<Integer> MONITOR_EVENT_COUNT = SCHEMA.intRange(
            "monitorEventCount", 10, 1, 50, true,
            "-- Monitor -- Events a security monitor shows.");

    private static final ConfigValue<Boolean> FORCE_FIELD_BREACHABLE = SCHEMA.bool(
            "forceFieldBreachable", true, true,
            "-- Force fields -- Whether melee, projectiles and explosions overload a field. False makes fields blast-proof and never overloaded (unraidable; not recommended).");

    private static final ConfigValue<Integer> MAX_FIELD_STRENGTH = SCHEMA.intRange(
            "maxFieldStrength", 5, 1, 10, true,
            "Cap on an emitter's strength after strength modules; lockdown raises fields to this.");

    private static final ConfigValue<Integer> MAX_FIELD_AREA = SCHEMA.intRange(
            "maxFieldArea", 400, 16, 4096, true,
            "Most field blocks one emitter may project.");

    private static final ConfigValue<Integer> FIELD_BASE_RANGE = SCHEMA.intRange(
            "fieldBaseRange", 8, 2, 32, true,
            "How far (Chebyshev blocks) from its emitter a field may reach; each range module adds 4.");

    private static final ConfigValue<Integer> FIELD_MAX_RANGE = SCHEMA.intRange(
            "fieldMaxRange", 24, 2, 64, true,
            "Hard cap on an emitter's range after modules.");

    private static final ConfigValue<Integer> FIELD_BASE_POWER_PER_TICK = SCHEMA.intRange(
            "fieldBasePowerPerTick", 5, 0, 1000, true,
            "Energy a projecting emitter draws each tick before its blocks are counted.");

    private static final ConfigValue<Double> FIELD_POWER_PER_BLOCK_STRENGTH = SCHEMA.doubleRange(
            "fieldPowerPerBlockStrength", 0.25, 0.0, 10.0, true,
            "Draw per tick = base + ceil(blocks x strength x this).");

    private static final ConfigValue<Integer> FIELD_ENERGY_CAPACITY = SCHEMA.intRange(
            "fieldEnergyCapacity", 100000, 1000, 10000000, true,
            "Energy buffer per emitter.");

    private static final ConfigValue<Integer> FIELD_RESTART_TICKS = SCHEMA.intRange(
            "fieldRestartTicks", 100, 20, 1200, true,
            "The buffer must hold this many ticks of draw before a field forms or re-forms.");

    private static final ConfigValue<Integer> FIELD_BROWNOUT_GRACE_TICKS = SCHEMA.intRange(
            "fieldBrownoutGraceTicks", 20, 0, 200, true,
            "Ticks without enough energy before a field collapses.");

    private static final ConfigValue<Integer> FORCE_FIELD_OVERLOAD_SECONDS = SCHEMA.intRange(
            "forceFieldOverloadSeconds", 60, 5, 3600, true,
            "After an overload the emitter cannot project again for this many seconds.");

    private static final ConfigValue<Double> FIELD_LOAD_CAPACITY_PER_STRENGTH = SCHEMA.doubleRange(
            "fieldLoadCapacityPerStrength", 40.0, 1.0, 1000.0, true,
            "Overload meter capacity per point of strength.");

    private static final ConfigValue<Double> FIELD_LOAD_DECAY_PER_SECOND = SCHEMA.doubleRange(
            "fieldLoadDecayPerSecond", 2.0, 0.0, 100.0, true,
            "How fast the overload meter cools, per second.");

    private static final ConfigValue<Double> FIELD_MELEE_LOAD = SCHEMA.doubleRange(
            "fieldMeleeLoad", 1.0, 0.0, 100.0, true,
            "Load per hit by a player not permitted to pass.");

    private static final ConfigValue<Double> FIELD_PROJECTILE_LOAD = SCHEMA.doubleRange(
            "fieldProjectileLoad", 2.0, 0.0, 100.0, true,
            "Load per projectile hit (owner not permitted or unknown).");

    private static final ConfigValue<Double> FIELD_EXPLOSION_LOAD_PER_RADIUS = SCHEMA.doubleRange(
            "fieldExplosionLoadPerRadius", 10.0, 0.0, 1000.0, true,
            "Load per block of explosion radius (TNT is 4).");

    private static final ConfigValue<Integer> FIELD_ENERGY_PER_LOAD = SCHEMA.intRange(
            "fieldEnergyPerLoad", 50, 0, 10000, true,
            "Energy drained from the emitter per point of load absorbed.");

    private static final ConfigValue<Double> FIELD_BLAST_RESISTANCE = SCHEMA.doubleRange(
            "fieldBlastResistance", 6.0, 0.0, 1200.0, true,
            "Explosion resistance of a field block while fields are breachable (stone is 6).");

    private static final ConfigValue<Boolean> FIELD_LOCKDOWN_RAISES_TO_MAX = SCHEMA.bool(
            "fieldLockdownRaisesToMax", true, true,
            "Whether lockdown runs fields at maxFieldStrength, at no extra power.");

    private static final ConfigValue<Integer> FIELD_REPROJECT_INTERVAL_TICKS = SCHEMA.intRange(
            "fieldReprojectIntervalTicks", 40, 10, 200, true,
            "How often an emitter re-checks its field blocks, in ticks.");

    private static final ConfigValue<Integer> FIELD_SYNC_INTERVAL_TICKS = SCHEMA.intRange(
            "fieldSyncIntervalTicks", 10, 1, 40, true,
            "How often nearby players are re-sent whether they may pass, in ticks.");

    private static final ConfigValue<Integer> FIELD_BREACH_EVENT_COOLDOWN_TICKS = SCHEMA.intRange(
            "fieldBreachEventCooldownTicks", 200, 20, 6000, true,
            "One force-field-attacked event per attacker per emitter per this many ticks.");

    private static final AtomicBoolean RETENTION_CLAMP_WARNED = new AtomicBoolean();

    private NeroSecurityConfig() {
    }

    // --- force fields and integrations --------------------------------------------------------------

    public static boolean isForceFieldsEnabled() {
        return FORCE_FIELDS_ENABLED.get();
    }

    public static boolean isFactionsIntegrationEnabled() {
        return FACTIONS_INTEGRATION_ENABLED.get();
    }

    public static boolean isColoniesIntegrationEnabled() {
        return COLONIES_INTEGRATION_ENABLED.get();
    }

    public static boolean isNerospaceIntegrationEnabled() {
        return NEROSPACE_INTEGRATION_ENABLED.get();
    }

    public static boolean isClaimsIntegrationEnabled() {
        return CLAIMS_INTEGRATION_ENABLED.get();
    }

    public static boolean isForceFieldBreachable() {
        return FORCE_FIELD_BREACHABLE.get();
    }

    public static int maxFieldStrength() {
        return MAX_FIELD_STRENGTH.get();
    }

    public static int maxFieldArea() {
        return MAX_FIELD_AREA.get();
    }

    public static int fieldBaseRange() {
        return FIELD_BASE_RANGE.get();
    }

    public static int fieldMaxRange() {
        return FIELD_MAX_RANGE.get();
    }

    public static int fieldBasePowerPerTick() {
        return FIELD_BASE_POWER_PER_TICK.get();
    }

    public static double fieldPowerPerBlockStrength() {
        return FIELD_POWER_PER_BLOCK_STRENGTH.get();
    }

    public static int fieldEnergyCapacity() {
        return FIELD_ENERGY_CAPACITY.get();
    }

    public static int fieldRestartTicks() {
        return FIELD_RESTART_TICKS.get();
    }

    public static int fieldBrownoutGraceTicks() {
        return FIELD_BROWNOUT_GRACE_TICKS.get();
    }

    public static int forceFieldOverloadSeconds() {
        return FORCE_FIELD_OVERLOAD_SECONDS.get();
    }

    public static double fieldLoadCapacityPerStrength() {
        return FIELD_LOAD_CAPACITY_PER_STRENGTH.get();
    }

    public static double fieldLoadDecayPerSecond() {
        return FIELD_LOAD_DECAY_PER_SECOND.get();
    }

    public static double fieldMeleeLoad() {
        return FIELD_MELEE_LOAD.get();
    }

    public static double fieldProjectileLoad() {
        return FIELD_PROJECTILE_LOAD.get();
    }

    public static double fieldExplosionLoadPerRadius() {
        return FIELD_EXPLOSION_LOAD_PER_RADIUS.get();
    }

    public static int fieldEnergyPerLoad() {
        return FIELD_ENERGY_PER_LOAD.get();
    }

    public static double fieldBlastResistance() {
        return FIELD_BLAST_RESISTANCE.get();
    }

    public static boolean fieldLockdownRaisesToMax() {
        return FIELD_LOCKDOWN_RAISES_TO_MAX.get();
    }

    public static int fieldReprojectIntervalTicks() {
        return FIELD_REPROJECT_INTERVAL_TICKS.get();
    }

    public static int fieldSyncIntervalTicks() {
        return FIELD_SYNC_INTERVAL_TICKS.get();
    }

    public static int fieldBreachEventCooldownTicks() {
        return FIELD_BREACH_EVENT_COOLDOWN_TICKS.get();
    }

    // --- sensors, alarms, audit -------------------------------------------------

    public static boolean isCamerasEnabled() {
        return CAMERAS_ENABLED.get();
    }

    public static boolean isAuditLogEnabled() {
        return LOG_ENABLED.get();
    }

    public static boolean isAlarmChatEnabled() {
        return ALARM_CHAT_ENABLED.get();
    }

    public static boolean isAlarmLockdownEnabled() {
        return ALARM_LOCKDOWN_ENABLED.get();
    }

    /** The audit retention in days, clamped to {@link #privacyRetentionHardCapDays()} (warned once). */
    public static int privacyAuditRetentionDays() {
        int raw = PRIVACY_LOG_RETENTION_DAYS.get();
        int cap = privacyRetentionHardCapDays();
        int clamped = clampRetentionDays(raw, cap);
        if (clamped != raw && RETENTION_CLAMP_WARNED.compareAndSet(false, true)) {
            NeroSecurityCommon.LOGGER.warn("[NeroSecurity] privacyAuditRetentionDays ({}) exceeds "
                    + "privacyRetentionHardCapDays ({}); using {}.", raw, cap, clamped);
        }
        return clamped;
    }

    /** The runtime clamp behind {@link #privacyAuditRetentionDays()}: 1..min(cap, 90). Pure. */
    public static int clampRetentionDays(int days, int hardCapDays) {
        int cap = Math.max(1, Math.min(MAX_RETENTION_DAYS, hardCapDays));
        return Math.max(1, Math.min(cap, days));
    }

    public static int privacyCameraEventRetentionMinutes() {
        return PRIVACY_CAMERA_EVENT_RETENTION_MINUTES.get();
    }

    public static int privacyAuditMaxEntriesPerInstallation() {
        return PRIVACY_LOG_MAX_ENTRIES.get();
    }

    /** The ceiling for every retention setting; never above {@value #MAX_RETENTION_DAYS}. */
    public static int privacyRetentionHardCapDays() {
        return Math.max(1, Math.min(MAX_RETENTION_DAYS, PRIVACY_RETENTION_HARD_CAP_DAYS.get()));
    }

    public static int privacyPurgeIntervalMinutes() {
        return PRIVACY_PURGE_INTERVAL_MINUTES.get();
    }

    public static boolean privacyCameraLogsMembers() {
        return PRIVACY_CAMERA_LOGS_MEMBERS.get();
    }

    public static int laserBaseRange() {
        return LASER_BASE_RANGE.get();
    }

    public static int laserMaxRange() {
        return LASER_MAX_RANGE.get();
    }

    public static int laserScanIntervalTicks() {
        return LASER_SCAN_INTERVAL_TICKS.get();
    }

    public static int laserTripCooldownTicks() {
        return LASER_TRIP_COOLDOWN_TICKS.get();
    }

    public static double laserTripDamage() {
        return LASER_TRIP_DAMAGE.get();
    }

    public static int laserPowerPerTick() {
        return LASER_POWER_PER_TICK.get();
    }

    public static int laserEnergyCapacity() {
        return LASER_ENERGY_CAPACITY.get();
    }

    public static int cameraBaseRange() {
        return CAMERA_BASE_RANGE.get();
    }

    public static int cameraMaxRange() {
        return CAMERA_MAX_RANGE.get();
    }

    public static int cameraScanIntervalTicks() {
        return CAMERA_SCAN_INTERVAL_TICKS.get();
    }

    public static int cameraMaxRaycastsPerScan() {
        return CAMERA_MAX_RAYCASTS_PER_SCAN.get();
    }

    public static int cameraEventCooldownTicks() {
        return CAMERA_EVENT_COOLDOWN_TICKS.get();
    }

    public static int cameraSweepTicks() {
        return CAMERA_SWEEP_TICKS.get();
    }

    public static int cameraPowerPerTick() {
        return CAMERA_POWER_PER_TICK.get();
    }

    public static int cameraEnergyCapacity() {
        return CAMERA_ENERGY_CAPACITY.get();
    }

    public static int alarmCoalesceSeconds() {
        return ALARM_COALESCE_SECONDS.get();
    }

    public static int alarmRedstonePulseTicks() {
        return ALARM_REDSTONE_PULSE_TICKS.get();
    }

    public static int alarmActiveTicks() {
        return ALARM_ACTIVE_TICKS.get();
    }

    public static int alarmSilenceMaxMinutes() {
        return ALARM_SILENCE_MAX_MINUTES.get();
    }

    /** WARN or CRITICAL; anything else in the file reads as CRITICAL. */
    public static SecuritySeverity alarmLockdownMinSeverity() {
        return parseLockdownMinSeverity(ALARM_LOCKDOWN_MIN_SEVERITY.get());
    }

    /** {@link #alarmLockdownMinSeverity()}'s parsing rule: only {@code warn} lowers it. Pure. */
    public static SecuritySeverity parseLockdownMinSeverity(@Nullable String raw) {
        return raw != null && "warn".equals(raw.trim().toLowerCase(java.util.Locale.ROOT))
                ? SecuritySeverity.WARN : SecuritySeverity.CRITICAL;
    }

    public static int alarmLockdownAutoReleaseSeconds() {
        return ALARM_LOCKDOWN_AUTO_RELEASE_SECONDS.get();
    }

    public static int alarmLockdownCooldownSeconds() {
        return ALARM_LOCKDOWN_COOLDOWN_SECONDS.get();
    }

    public static int noticeMarginBlocks() {
        return NOTICE_MARGIN_BLOCKS.get();
    }

    public static int noticeCheckIntervalTicks() {
        return NOTICE_CHECK_INTERVAL_TICKS.get();
    }

    public static int monitorEventCount() {
        return MONITOR_EVENT_COUNT.get();
    }

    // --- earlier stages -----------------------------------------------------------

    public static int keycardsPerInstallation() {
        return KEYCARDS_PER_INSTALLATION.get();
    }

    /** MEMBER, OFFICER or OWNER; anything else in the file reads as OFFICER. */
    public static AccessLevel keycardBasicMaxLevel() {
        return parseBasicMax(KEYCARD_BASIC_MAX_LEVEL.get());
    }

    /** {@link #keycardBasicMaxLevel()}'s parsing rule, separate so it can be tested without a config file. */
    public static AccessLevel parseBasicMax(@Nullable String raw) {
        AccessLevel level = AccessLevel.byName(raw).orElse(AccessLevel.OFFICER);
        return level == AccessLevel.PUBLIC ? AccessLevel.OFFICER : level;
    }

    public static boolean doorsRequirePower() {
        return DOORS_REQUIRE_POWER.get();
    }

    public static int doorPowerPerTick() {
        return DOOR_POWER_PER_TICK.get();
    }

    public static int doorOpenCost() {
        return DOOR_OPEN_COST.get();
    }

    public static int doorEnergyCapacity() {
        return DOOR_ENERGY_CAPACITY.get();
    }

    public static int doorOpenTicks() {
        return DOOR_OPEN_TICKS.get();
    }

    public static int doorAutoCloseTicks() {
        return DOOR_AUTO_CLOSE_TICKS.get();
    }

    public static double doorMaxBlastResistance() {
        return DOOR_MAX_BLAST_RESISTANCE.get();
    }

    public static boolean doorRedstoneAllowed() {
        return DOOR_REDSTONE_ALLOWED.get();
    }

    public static int doorDeniedEventCooldownTicks() {
        return DOOR_DENIED_EVENT_COOLDOWN_TICKS.get();
    }

    /** Null (off), MEMBER or OFFICER; anything else in the file reads as off. */
    public static @Nullable AccessLevel chestAutomationDefault() {
        return parseAutomation(CHEST_AUTOMATION_DEFAULT.get());
    }

    /** {@link #chestAutomationDefault()}'s parsing rule: only member and officer are policies. */
    public static @Nullable AccessLevel parseAutomation(@Nullable String raw) {
        AccessLevel level = AccessLevel.byName(raw).orElse(null);
        return level == AccessLevel.MEMBER || level == AccessLevel.OFFICER ? level : null;
    }

    public static int chestAutomationEventIntervalTicks() {
        return CHEST_AUTOMATION_EVENT_INTERVAL_TICKS.get();
    }

    public static int maxDevicesPerInstallation() {
        return MAX_DEVICES_PER_INSTALLATION.get();
    }

    /** Lockdown kill switch: false means lockdown is neither enterable nor enforced. */
    public static boolean isLockdownEnabled() {
        return LOCKDOWN_ENABLED.get();
    }

    /** Whether lockdown transitions are published on the {@code nerosecurity:lockdown} threshold channel. */
    public static boolean isLockdownChannelEnabled() {
        return LOCKDOWN_CHANNEL_ENABLED.get();
    }

    public static int maxInstallationsPerPlayer() {
        return MAX_INSTALLATIONS_PER_PLAYER.get();
    }

    public static int maxMembersPerInstallation() {
        return MAX_MEMBERS_PER_INSTALLATION.get();
    }

    public static int maxDeviceDistance() {
        return MAX_DEVICE_DISTANCE.get();
    }

    /**
     * Whether anonymous NeroSecurity-only crash reporting is on (default true, opt-out). Read once at
     * bootstrap by {@code NeroSecurityTelemetry.init()}; changes take effect on restart.
     */
    public static boolean isTelemetryEnabled() {
        return TELEMETRY.get();
    }

    /** Registers the schema with Core's ConfigManager. Called once from common init. */
    public static void init() {
        ConfigManager.register(SCHEMA);
    }
}
