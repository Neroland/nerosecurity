package za.co.neroland.nerosecurity.privacy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.link.LinkAlert;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.audit.AuditEntry;
import za.co.neroland.nerosecurity.audit.SecurityAuditLog;
import za.co.neroland.nerosecurity.installation.CardRecord;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;

/**
 * The data-subject access export (GDPR Art. 15/20, POPIA s23) behind {@code /nerosecurity privacy export} and
 * the hub's "Export my data": everything NeroSecurity holds about <b>one player</b>, as one JSON file at
 * {@code <world>/nerosecurity/exports/<uuid>.json}.
 *
 * <p><b>Never another player's personal data.</b> Installations the subject owns carry their own blocks'
 * positions and <em>counts</em> of co-owners and members, never who they are. Membership rows name the
 * installation and the subject's level, never its owners. Audit rows are only those where the subject is the
 * actor. Keycard rows are only those bound to the subject. The NeroLink part is the subject's own alerts that
 * NeroSecurity raised in Neroland Core's per-player alert list (module {@code nerosecurity}): their text, severity,
 * time and state, plus the count still unread. Other modules' alerts are theirs to export.
 *
 * <p>The path is built from the subject's own UUID and checked to stay inside the export folder; on a POSIX
 * file system the folder and file are restricted to the server's own user. An export is a one-off deliverable:
 * it is deleted by an erasure request and after {@link #RETENTION_DAYS} days. Failures are logged and reported
 * by exception class only, because the real message holds the path, and the file name is the UUID.
 */
public final class PrivacyExport {

    public static final int FORMAT_VERSION = 1;
    /** Export files are deleted this many days after they were written. */
    public static final int RETENTION_DAYS = 7;
    /** Relative to the world folder; shown to the player. */
    public static final String RELATIVE_DIR = "nerosecurity/exports";

    private static final long DAY_MS = 86_400_000L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Set<PosixFilePermission> OWNER_FILE = PosixFilePermissions.fromString("rw-------");
    private static final Set<PosixFilePermission> OWNER_DIRECTORY = PosixFilePermissions.fromString("rwx------");

    /** What was written: the path relative to the world folder, and counts for the chat summary. */
    public record Result(String relativePath, int owned, int memberships, int cards, int auditRows) {
    }

    private PrivacyExport() {
    }

    // --- the document (pure) -----------------------------------------------------------------------------

    /** The export document for {@code subject} from explicit stores (plain-JVM testable). */
    public static JsonObject build(UUID subject, SecurityInstallations installations, SecurityAuditLog audit,
            boolean optedOut, List<LinkAlert> alerts, long now) {
        JsonObject root = new JsonObject();
        root.addProperty("mod", NeroSecurityCommon.MOD_ID);
        root.addProperty("format", FORMAT_VERSION);
        root.addProperty("exported_at", Instant.ofEpochMilli(now).toString());
        root.addProperty("subject", subject.toString());

        JsonObject privacy = new JsonObject();
        privacy.addProperty("opted_out", optedOut);
        root.add("privacy", privacy);

        JsonArray owned = new JsonArray();
        JsonArray memberships = new JsonArray();
        JsonArray cards = new JsonArray();
        for (Installation installation : installations.all()) {
            if (installation.isOwner(subject)) {
                JsonObject row = new JsonObject();
                row.addProperty("installation_id", installation.id().toString());
                row.addProperty("name", installation.name());
                DevicePos hub = installation.hubPos();
                row.add("hub", hub == null ? null : pos(hub));
                row.addProperty("created_at", Instant.ofEpochMilli(installation.createdAt()).toString());
                row.addProperty("other_owner_count", Math.max(0, installation.owners().size() - 1));
                row.addProperty("member_count", installation.members().size());
                row.addProperty("device_count", installation.deviceCount());
                row.addProperty("lockdown_active", installation.lockdown().active());
                row.addProperty("notify_officers", installation.notifyOfficers());
                owned.add(row);
            }
            AccessLevel level = installation.members().get(subject);
            if (level != null) {
                JsonObject row = new JsonObject();
                row.addProperty("installation_id", installation.id().toString());
                row.addProperty("installation_name", installation.name());
                row.addProperty("level", level.jsonName());
                memberships.add(row);
            }
            for (Map.Entry<Long, CardRecord> card : installation.cards().entrySet()) {
                if (subject.equals(card.getValue().boundOwner())) {
                    JsonObject row = new JsonObject();
                    row.addProperty("installation_id", installation.id().toString());
                    row.addProperty("serial", card.getKey());
                    row.addProperty("level", card.getValue().level().jsonName());
                    row.addProperty("encrypted", card.getValue().encrypted());
                    row.addProperty("issued_at", Instant.ofEpochMilli(card.getValue().issuedAt()).toString());
                    cards.add(row);
                }
            }
        }
        root.add("installations_owned", owned);
        root.add("memberships", memberships);
        root.add("keycards_bound_to_you", cards);

        JsonArray rows = new JsonArray();
        for (Map.Entry<UUID, List<AuditEntry>> log : audit.rowsByActor(subject).entrySet()) {
            for (AuditEntry entry : log.getValue()) {
                JsonObject row = new JsonObject();
                row.addProperty("installation_id", log.getKey().toString());
                row.addProperty("at", Instant.ofEpochMilli(entry.at()).toString());
                row.addProperty("type", entry.type().name().toLowerCase(java.util.Locale.ROOT));
                row.addProperty("severity", entry.severity().name().toLowerCase(java.util.Locale.ROOT));
                row.add("device", entry.devicePos() == null ? null : pos(entry.devicePos()));
                rows.add(row);
            }
        }
        root.add("audit_rows_naming_you", rows);

        JsonObject link = new JsonObject();
        JsonArray alertRows = new JsonArray();
        int unacked = 0;
        for (LinkAlert alert : alerts == null ? List.<LinkAlert>of() : alerts) {
            if (alert == null || !NeroSecurityCommon.MOD_ID.equals(alert.moduleId())) {
                continue; // only the alerts NeroSecurity raised for the subject
            }
            if (!alert.acked()) {
                unacked++;
            }
            JsonObject row = new JsonObject();
            row.addProperty("text", alert.text());
            row.addProperty("severity", alert.severity().name().toLowerCase(java.util.Locale.ROOT));
            row.addProperty("raised_at", Instant.ofEpochMilli(alert.createdAt()).toString());
            row.addProperty("acked", alert.acked());
            if (alert.snoozedUntil() > 0L) {
                row.addProperty("snoozed_until", Instant.ofEpochMilli(alert.snoozedUntil()).toString());
            }
            alertRows.add(row);
        }
        link.addProperty("unackedAlerts", unacked);
        link.add("alerts", alertRows);
        root.add("link", link);

        JsonObject notes = new JsonObject();
        notes.addProperty("audit", "Only rows where you are the actor. Rows recorded while you had opted out carry "
                + "no identity and cannot be linked to you, so they are not here. Opting out is not retroactive: rows "
                + "recorded before you opted out keep your id, and are here, until they expire or you ask for "
                + "erasure. A record of you reading a security log always names you.");
        notes.addProperty("others", "Co-owners and members of your installations appear as counts only; the owners "
                + "of installations you belong to are not named. They are other people's data.");
        notes.addProperty("erasure", "'/neroland data eraseme' removes all of this (and this file) from every "
                + "Neroland mod on this server. Installations you own alone are kept for their blocks, pass to an "
                + "anonymous placeholder owner and are marked orphaned for an operator.");
        notes.addProperty("retention", "This file is deleted after " + RETENTION_DAYS + " days.");
        root.add("notes", notes);
        return root;
    }

    private static JsonObject pos(DevicePos pos) {
        JsonObject out = new JsonObject();
        out.addProperty("dimension", pos.dimension().toString());
        out.addProperty("x", pos.pos().getX());
        out.addProperty("y", pos.pos().getY());
        out.addProperty("z", pos.pos().getZ());
        return out;
    }

    // --- files -------------------------------------------------------------------------------------------

    public static Path directory(MinecraftServer server) {
        return directory(server.getWorldPath(LevelResource.ROOT));
    }

    static Path directory(Path worldRoot) {
        return worldRoot.resolve("nerosecurity").resolve("exports").normalize();
    }

    /** One subject's export path, refusing anything that escapes the export folder. */
    static Path file(Path directory, UUID subject) {
        Path root = directory.normalize();
        Path output = root.resolve(subject + ".json").normalize();
        if (!output.startsWith(root) || !output.getParent().equals(root)) {
            throw new IllegalStateException("Unsafe export path");
        }
        return output;
    }

    /** The path the player is told, relative to the world folder. */
    public static String relativePath(UUID subject) {
        return RELATIVE_DIR + "/" + subject + ".json";
    }

    /** Builds and writes the subject's export, replacing an earlier one. Server thread. */
    public static Result export(MinecraftServer server, UUID subject, boolean optedOut, List<LinkAlert> alerts)
            throws IOException {
        SecurityInstallations installations = SecurityInstallations.get(server);
        SecurityAuditLog audit = SecurityAuditLog.get(server);
        JsonObject doc = build(subject, installations, audit, optedOut, alerts, System.currentTimeMillis());
        Path dir = directory(server);
        write(dir, file(dir, subject), GSON.toJson(doc));
        return new Result(relativePath(subject), doc.getAsJsonArray("installations_owned").size(),
                doc.getAsJsonArray("memberships").size(), doc.getAsJsonArray("keycards_bound_to_you").size(),
                doc.getAsJsonArray("audit_rows_naming_you").size());
    }

    /** Writes {@code json} to {@code output}, restricting folder and file where the file system allows it. */
    static void write(Path root, Path output, String json) throws IOException {
        Files.createDirectories(root);
        try {
            Files.setPosixFilePermissions(root, OWNER_DIRECTORY);
        } catch (UnsupportedOperationException | IOException bestEffort) {
            // Not POSIX (Windows), or not ours to re-mode: the platform defaults apply.
        }
        try {
            if (Files.exists(output)) {
                Files.setPosixFilePermissions(output, OWNER_FILE);
            } else {
                Files.createFile(output, PosixFilePermissions.asFileAttribute(OWNER_FILE));
            }
        } catch (UnsupportedOperationException notPosix) {
            // No POSIX permission view on this file system.
        }
        Files.writeString(output, json, StandardCharsets.UTF_8);
    }

    /** Erasure: an export must not outlive the data it was taken from. Never throws. */
    public static boolean delete(@Nullable Path directory, UUID subject) {
        if (directory == null || subject == null) {
            return false;
        }
        try {
            return Files.deleteIfExists(file(directory, subject));
        } catch (IOException | RuntimeException failure) {
            NeroSecurityCommon.LOGGER.warn("[NeroSecurity] an export file could not be deleted ({}).",
                    failure.getClass().getName());
            return false;
        }
    }

    /** Conformance probe: does an export file exist for {@code subject}? */
    public static boolean exists(@Nullable Path directory, UUID subject) {
        try {
            return directory != null && subject != null && Files.exists(file(directory, subject));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Retention: deletes export files older than {@link #RETENTION_DAYS}. Returns how many. Never throws. */
    public static int purgeExpired(@Nullable Path directory, long now) {
        if (directory == null || !Files.isDirectory(directory)) {
            return 0;
        }
        long cutoff = now - RETENTION_DAYS * DAY_MS;
        int removed = 0;
        try (Stream<Path> listing = Files.list(directory)) {
            for (Path file : listing.toList()) {
                if (Files.isRegularFile(file) && file.getFileName().toString().endsWith(".json")
                        && Files.getLastModifiedTime(file).toMillis() <= cutoff && Files.deleteIfExists(file)) {
                    removed++;
                }
            }
        } catch (IOException | RuntimeException failure) {
            NeroSecurityCommon.LOGGER.warn("[NeroSecurity] export retention sweep failed ({}).",
                    failure.getClass().getName());
        }
        return removed;
    }
}
