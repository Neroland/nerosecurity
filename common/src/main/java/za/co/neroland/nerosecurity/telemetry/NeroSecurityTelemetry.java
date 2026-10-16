package za.co.neroland.nerosecurity.telemetry;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;

import io.sentry.Breadcrumb;
import io.sentry.ITransaction;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.SpanStatus;
import io.sentry.protocol.Message;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryStackFrame;
import io.sentry.protocol.SentryStackTrace;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.platform.Services;

/**
 * Crash/error reporting for NeroSecurity via Sentry (EU ingest), built to satisfy both the
 * CurseForge moderation rule (external analytics must be disclosed and opt-out-able) and
 * POPIA/GDPR data-minimisation:
 *
 * <ul>
 *   <li><b>Opt-out:</b> gated on {@code telemetryEnabled} in {@link NeroSecurityConfig} (default ON,
 *       disclosed). Set it false to stop reporting (takes effect on restart).</li>
 *   <li><b>NeroSecurity errors only:</b> {@code beforeSend} drops any event whose stack trace does
 *       not touch {@code za.co.neroland.nerosecurity}.</li>
 *   <li><b>No personal data:</b> {@code sendDefaultPii=false} (no IP), no server/host name, no user
 *       identity, no player names/UUIDs. The OS account name and home directory are masked as
 *       {@code [user]} in exception messages, log messages, breadcrumbs and stack-frame file names
 *       and paths. Remaining payload: stack trace, mod/MC/loader/OS/Java versions and the loaded
 *       mod list.</li>
 *   <li><b>Bounded volume:</b> per-session de-duplication plus a hard cap of
 *       {@value #MAX_EVENTS_PER_SESSION} events per game session.</li>
 * </ul>
 *
 * <p>{@link #init()} is called once per loader at bootstrap (from {@code NeroSecurityCommon.init()},
 * right after the config schema is registered) and reads loader facts through
 * {@link Services#PLATFORM}.
 */
public final class NeroSecurityTelemetry {

    /**
     * ============================ THE UNCONFIGURED-BUILD GUARD ============================
     * If {@link #DSN} is set to this literal, every entry point below is a hard no-op:
     * {@link #init()} returns before touching the Sentry SDK, so <b>nothing is ever sent
     * anywhere</b> and no network connection is opened, regardless of the {@code telemetryEnabled}
     * config value.
     *
     * <p>Official builds ship NeroSecurity's real project DSN, so telemetry is active there unless
     * the player opts out. A fork or rebuild that should not report into the official project sets
     * {@link #DSN} back to this placeholder. <b>Do not remove the guard</b>: it is what keeps such a
     * build silent instead of reporting into somebody else's project.
     * ===================================================================================
     */
    private static final String PLACEHOLDER_DSN = "https://REPLACE-ME@sentry.invalid/0";

    /**
     * Sentry DSN for the NeroSecurity project (EU ingest). No secret is involved: a DSN is a public
     * client key, write-only ingest, safe to ship in the jar. It grants permission to SEND events
     * and nothing else: it cannot read issues, and it identifies the NeroSecurity project, never a
     * player. Opt-out via {@code telemetryEnabled=false} in {@code config/nerosecurity.properties};
     * see PRIVACY.md for the full disclosure.
     */
    private static final String DSN =
            "https://dc9e69c311fa377cbfd64c8fa14f4d67@o4511183823241216.ingest.de.sentry.io/4512256287440976";

    /** Stack traces must contain this package for an event to be sent. */
    private static final String PACKAGE_MARKER = "za.co.neroland.nerosecurity";

    /** Hard cap on events per game session (data minimisation + noise control). */
    private static final int MAX_EVENTS_PER_SESSION = 10;

    /** Cap on how many mod ids are attached as crash context (payload bound). */
    private static final int MAX_MODS_REPORTED = 300;

    /** Masks OS-account names in Windows/macOS/Linux home-directory paths. */
    private static final Pattern USER_PATH =
            Pattern.compile("(?i)(?:[A-Z]:)?[/\\\\](?:Users|home)[/\\\\][^/\\\\\\s:;,'\"]+");

    private static volatile boolean active;
    private static final AtomicInteger eventsSent = new AtomicInteger();
    private static final Set<String> seenFingerprints = ConcurrentHashMap.newKeySet();
    private static SentryLogAppender appender;

    private NeroSecurityTelemetry() {
    }

    /**
     * True when the DSN is the placeholder (a fork or rebuild opted out of the official project), in
     * which case telemetry stays wired but sends nothing.
     */
    private static boolean dsnIsPlaceholder() {
        return PLACEHOLDER_DSN.equals(DSN);
    }

    /**
     * Called once per loader at bootstrap. Starts reporting iff a real DSN is configured AND the
     * player has not opted out ({@code telemetryEnabled=true}, the default). Dev (IDE) runs ALSO
     * report — so the developer can test error reporting end to end — but they are tagged
     * {@code environment=development} / {@code runtime=development} so they are trivially filtered
     * out of production metrics.
     */
    public static void init() {
        if (dsnIsPlaceholder()) {
            // Placeholder DSN (a fork or rebuild): send nothing. Logged once at DEBUG so a
            // developer can see why no events arrive, without nagging players.
            NeroSecurityCommon.LOGGER.debug(
                    "[NeroSecurity] Telemetry off - no Sentry DSN configured in this build.");
            return;
        }
        if (!NeroSecurityConfig.isTelemetryEnabled()) {
            return;
        }
        start();
    }

    /**
     * Fires a single synthetic Sentry event to confirm end-to-end reporting on a real (production)
     * jar. The exception originates in NeroSecurity code so it passes the package-only
     * {@code beforeSend} filter; the per-session de-dup means repeat calls in one session collapse
     * to one event (restart to test again).
     *
     * @return {@code true} if telemetry is active and the event was dispatched
     */
    public static boolean sendTestEvent(String origin) {
        if (!active) {
            return false;
        }
        capture(new IllegalStateException(
                "NeroSecurity Sentry test (" + origin + ") — synthetic event, safe to ignore"));
        return true;
    }

    private static synchronized void start() {
        if (active) {
            return;
        }
        try {
            startSentry();
        } catch (RuntimeException | LinkageError e) {
            // Telemetry must never take mod construction down with it.
            NeroSecurityCommon.LOGGER.warn(
                    "[NeroSecurity] Telemetry could not start ({}); continuing without error reporting.",
                    e.getClass().getName());
        }
    }

    private static void startSentry() {
        String modVersion = Services.PLATFORM.getModVersion();
        boolean dev = Services.PLATFORM.isDevelopmentEnvironment();
        Sentry.init(options -> {
            options.setDsn(DSN);
            options.setRelease("nerosecurity@" + modVersion);
            // Dev/IDE runs report under a dedicated environment so they never mix with real releases.
            options.setEnvironment(dev ? "development" : environmentOf(modVersion));
            // POPIA/GDPR: never store the sender's IP address or identity.
            options.setSendDefaultPii(false);
            // The machine's hostname is identifying; never attach it.
            options.setAttachServerName(false);
            options.setEnableUncaughtExceptionHandler(true);
            // Release health: session lifecycle managed manually (started below, ended on JVM
            // shutdown). The session id is random per launch and is NOT linked across sessions.
            options.setEnableAutoSessionTracking(false);
            // Performance: sample a small fraction of traced operations. Timing data only — no
            // personal data in a transaction.
            options.setTracesSampleRate(0.05D);
            options.setBeforeSend((event, hint) -> filterAndScrub(event));
        });
        Sentry.configureScope(scope -> {
            scope.setTag("loader", Services.PLATFORM.getPlatformName().toLowerCase(Locale.ROOT));
            scope.setTag("dist", Services.PLATFORM.isClient() ? "client" : "dedicated_server");
            scope.setTag("runtime", dev ? "development" : "production");
            scope.setTag("mc_version", minecraftVersion());
            // Loaded-mod list (public manifest ids + versions only) for mod-conflict triage.
            try {
                List<String> mods = Services.PLATFORM.getLoadedModIds();
                if (mods != null && !mods.isEmpty()) {
                    if (mods.size() > MAX_MODS_REPORTED) {
                        mods = mods.subList(0, MAX_MODS_REPORTED);
                    }
                    scope.setTag("mod_count", Integer.toString(mods.size()));
                    java.util.Map<String, Object> modContext = new java.util.HashMap<>();
                    modContext.put("count", mods.size());
                    modContext.put("ids", mods);
                    scope.setContexts("loaded_mods", modContext);
                }
            } catch (RuntimeException | LinkageError e) {
                // Mod list not available this early — skip it; the rest of the report is unaffected.
            }
        });
        // Manual release-health session for this play session; closed on a clean JVM shutdown.
        Sentry.startSession();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                Sentry.endSession();
                Sentry.flush(2000L);
            } catch (RuntimeException ignored) {
                // best-effort flush on shutdown
            }
        }, "nerosecurity-sentry-shutdown"));
        if (appender == null) {
            appender = new SentryLogAppender();
            appender.start();
            ((org.apache.logging.log4j.core.Logger) LogManager.getRootLogger()).addAppender(appender);
        }
        active = true;
        NeroSecurityCommon.LOGGER.info(
                "[NeroSecurity] Telemetry enabled (anonymous error reports, EU servers; opt out via "
                        + "telemetryEnabled=false in config/nerosecurity.properties).");
    }

    /**
     * Drops a lightweight, non-identifying breadcrumb onto the current scope — a trail of what the
     * mod was doing that rides along with the next error report. No-op when telemetry is off. The
     * message is scrubbed of OS-account paths exactly like every other payload, and must never be
     * given player names, UUIDs or any other player data.
     */
    public static void breadcrumb(String category, String message) {
        if (!active) {
            return;
        }
        Breadcrumb crumb = new Breadcrumb();
        crumb.setType("default");
        crumb.setCategory(category);
        crumb.setLevel(SentryLevel.INFO);
        crumb.setMessage(scrub(message));
        try {
            Sentry.addBreadcrumb(crumb);
        } catch (RuntimeException | LinkageError ignored) {
            // A lost breadcrumb is harmless.
        }
    }

    /**
     * Times a unit of work as a sampled Sentry transaction (performance tracing). Returns the body's
     * value. When telemetry is off the body simply runs untraced, so call sites stay branch-free.
     * Only timing + operation name are recorded — never personal data.
     */
    public static <T> T trace(String operation, String name, java.util.function.Supplier<T> body) {
        if (!active) {
            return body.get();
        }
        ITransaction tx = Sentry.startTransaction(name, operation);
        try {
            return body.get();
        } catch (RuntimeException | Error e) {
            tx.setThrowable(e);
            tx.setStatus(SpanStatus.INTERNAL_ERROR);
            throw e;
        } finally {
            tx.finish();
        }
    }

    /** {@link #trace(String, String, java.util.function.Supplier)} for a body with no return value. */
    public static void trace(String operation, String name, Runnable body) {
        trace(operation, name, () -> {
            body.run();
            return null;
        });
    }

    /**
     * The running Minecraft version, read from the loader's mod list (where "minecraft" is always
     * present) so we stay off version-specific vanilla version APIs. "unknown" if unavailable.
     */
    private static String minecraftVersion() {
        try {
            for (String mod : Services.PLATFORM.getLoadedModIds()) {
                if (mod.startsWith("minecraft ")) {
                    return mod.substring("minecraft ".length());
                }
            }
        } catch (RuntimeException | LinkageError e) {
            // fall through to unknown
        }
        return "unknown";
    }

    /** Maps the mod version's release channel to a Sentry environment. */
    private static String environmentOf(String version) {
        String v = version.toLowerCase(Locale.ROOT);
        if (v.contains("-alpha")) {
            return "alpha";
        }
        if (v.contains("-beta")) {
            return "beta";
        }
        return "production";
    }

    static boolean isActive() {
        return active;
    }

    /** True if any frame of the throwable (or its causes/suppressed) is NeroSecurity code. */
    static boolean touchesNeroSecurity(Throwable t) {
        int depth = 0;
        while (t != null && depth++ < 16) {
            for (StackTraceElement el : t.getStackTrace()) {
                if (el.getClassName().startsWith(PACKAGE_MARKER)) {
                    return true;
                }
            }
            for (Throwable s : t.getSuppressed()) {
                if (touchesNeroSecurity(s)) {
                    return true;
                }
            }
            t = t.getCause();
        }
        return false;
    }

    /** Capture an exception already known to touch NeroSecurity code. */
    static void capture(Throwable t) {
        if (!active || t == null) {
            return;
        }
        try {
            Sentry.captureException(t);
        } catch (RuntimeException | LinkageError e) {
            NeroSecurityCommon.LOGGER.debug("[NeroSecurity] Telemetry could not report an exception ({})",
                    e.getClass().getName());
        }
    }

    /** Capture a handled exception if it is still clearly from NeroSecurity code. */
    public static void captureHandledException(Throwable t) {
        if (t != null && touchesNeroSecurity(t)) {
            capture(t);
        }
    }

    /**
     * Capture a handled exception with non-identifying labels for triage. {@code source} and
     * {@code operation} must be stable labels (a subsystem name plus a translation key or class
     * name) — never player-authored text, a rendered title, a name, a UUID or a position.
     *
     * <p>Uses Sentry 8.x's {@code captureException(Throwable, ScopeCallback)} overload: tags set
     * inside {@code Sentry.withScope(...)} never reach the event. The fingerprint is source AND
     * operation AND exception type, so unrelated handled failures stay separate issues.
     */
    public static void captureHandledException(Throwable t, String source, String operation) {
        if (!active || t == null || !touchesNeroSecurity(t)) {
            return;
        }
        String safeSource = source == null ? "unknown" : scrub(source);
        String safeOperation = operation == null ? "unknown" : scrub(operation);
        try {
            Sentry.captureException(t, scope -> {
                scope.setLevel(SentryLevel.WARNING);
                scope.setTag("handled", "true");
                scope.setTag("source", safeSource);
                scope.setTag("operation", safeOperation);
                scope.setFingerprint(List.of(NeroSecurityCommon.MOD_ID, safeSource, safeOperation,
                        t.getClass().getName()));
            });
        } catch (RuntimeException | LinkageError e) {
            NeroSecurityCommon.LOGGER.debug("[NeroSecurity] Telemetry could not report a handled exception ({})",
                    e.getClass().getName());
        }
    }

    /** Capture a (scrubbed, truncated) FATAL log line that names NeroSecurity without a throwable. */
    static void captureMessage(String message) {
        if (!active) {
            return;
        }
        String scrubbed = scrub(message);
        if (scrubbed.length() > 4000) {
            scrubbed = scrubbed.substring(0, 4000) + "…[truncated]";
        }
        SentryEvent event = new SentryEvent();
        event.setLevel(SentryLevel.FATAL);
        Message msg = new Message();
        msg.setFormatted(scrubbed);
        event.setMessage(msg);
        try {
            Sentry.captureEvent(event);
        } catch (RuntimeException | LinkageError e) {
            NeroSecurityCommon.LOGGER.debug("[NeroSecurity] Telemetry could not report a log line ({})",
                    e.getClass().getName());
        }
    }

    /**
     * The single privacy/noise gate every outgoing event passes through: keep only
     * NeroSecurity-related events, de-duplicate, rate-limit, and scrub PII. Returning {@code null}
     * drops the event.
     */
    private static SentryEvent filterAndScrub(SentryEvent event) {
        if (!isNeroSecurityRelated(event)) {
            return null;
        }
        String fingerprint = fingerprintOf(event);
        if (!seenFingerprints.add(fingerprint)) {
            return null; // already reported this session
        }
        if (eventsSent.incrementAndGet() > MAX_EVENTS_PER_SESSION) {
            return null;
        }
        // POPIA/GDPR scrubbing: no user identity, no hostname, no OS-account names or home paths.
        event.setUser(null);
        event.setServerName(null);
        List<SentryException> scrubExceptions = event.getExceptions();
        if (scrubExceptions != null) {
            for (SentryException ex : scrubExceptions) {
                String value = ex.getValue();
                if (value != null) {
                    ex.setValue(scrub(value));
                }
                SentryStackTrace st = ex.getStacktrace();
                List<SentryStackFrame> frames = st == null ? null : st.getFrames();
                if (frames != null) {
                    for (SentryStackFrame frame : frames) {
                        frame.setAbsPath(null);
                        String filename = frame.getFilename();
                        if (filename != null) {
                            frame.setFilename(scrub(filename));
                        }
                    }
                }
            }
        }
        Message message = event.getMessage();
        if (message != null) {
            if (message.getFormatted() != null) {
                message.setFormatted(scrub(message.getFormatted()));
            }
            if (message.getMessage() != null) {
                message.setMessage(scrub(message.getMessage()));
            }
        }
        return event;
    }

    private static boolean isNeroSecurityRelated(SentryEvent event) {
        Throwable t = event.getThrowable();
        if (t != null && touchesNeroSecurity(t)) {
            return true;
        }
        List<SentryException> exceptions = event.getExceptions();
        if (exceptions != null) {
            for (SentryException ex : exceptions) {
                SentryStackTrace st = ex.getStacktrace();
                List<SentryStackFrame> frames = st == null ? null : st.getFrames();
                if (frames == null) {
                    continue;
                }
                for (SentryStackFrame frame : frames) {
                    String module = frame.getModule();
                    if (module != null && module.startsWith(PACKAGE_MARKER)) {
                        return true;
                    }
                }
            }
        }
        Message message = event.getMessage();
        String formatted = message == null ? null : message.getFormatted();
        return formatted != null && formatted.contains(PACKAGE_MARKER);
    }

    private static String fingerprintOf(SentryEvent event) {
        StringBuilder sb = new StringBuilder();
        List<SentryException> exceptions = event.getExceptions();
        Message message = event.getMessage();
        if (exceptions != null) {
            for (SentryException ex : exceptions) {
                sb.append(ex.getType()).append('|');
                SentryStackTrace st = ex.getStacktrace();
                List<SentryStackFrame> frames = st == null ? null : st.getFrames();
                if (frames != null) {
                    for (SentryStackFrame frame : frames) {
                        String module = frame.getModule();
                        if (module != null && module.startsWith(PACKAGE_MARKER)) {
                            sb.append(module).append(':').append(frame.getLineno()).append('|');
                        }
                    }
                }
            }
        } else if (message != null) {
            String formatted = message.getFormatted();
            if (formatted != null) {
                sb.append(formatted, 0, Math.min(200, formatted.length()));
            }
        }
        return sb.toString();
    }

    /**
     * Masks the OS account: the JVM's {@code user.home} (in both slash forms) and {@code user.name}
     * become {@code [user]}, and any remaining Windows/macOS/Linux home-directory path is masked the
     * same way. Null-safe; never throws.
     */
    static String scrub(String text) {
        if (text == null) {
            return null;
        }
        String scrubbed = text;
        try {
            String home = System.getProperty("user.home");
            if (home != null && !home.isBlank()) {
                scrubbed = scrubbed.replace(home, "[user]");
                scrubbed = scrubbed.replace(home.replace('\\', '/'), "[user]");
                scrubbed = scrubbed.replace(home.replace('/', '\\'), "[user]");
            }
            String user = System.getProperty("user.name");
            if (user != null && !user.isBlank()) {
                scrubbed = scrubbed.replace(user, "[user]");
            }
        } catch (RuntimeException ignored) {
            // A SecurityManager refusing the property read: fall through to the pattern below.
        }
        return USER_PATH.matcher(scrubbed).replaceAll("/[user]");
    }
}
