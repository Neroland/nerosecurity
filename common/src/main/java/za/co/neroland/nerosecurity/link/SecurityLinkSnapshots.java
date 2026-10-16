package za.co.neroland.nerosecurity.link;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.link.LinkAlert;
import za.co.neroland.nerolandcore.link.LinkSnapshotProvider;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.access.AccessAction;
import za.co.neroland.nerosecurity.access.AccessDecision;
import za.co.neroland.nerosecurity.access.AccessQuery;
import za.co.neroland.nerosecurity.audit.ActorKind;
import za.co.neroland.nerosecurity.audit.AuditEntry;
import za.co.neroland.nerosecurity.event.SecurityEvent;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.event.SecuritySeverity;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.Lockdown;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.link.SecurityLinkAccess.Role;

/**
 * NeroSecurity's read-only link sections ({@code SCHEMA_VERSION = 1}): {@code installations}, {@code events},
 * {@code devices}, the legacy {@code log} summary and the public {@code wiki}.
 *
 * <h2>What a section may contain</h2>
 * <ul>
 *   <li>Installation ids (the only UUIDs), owner-chosen installation names, fixed phrases, kinds, ordinal
 *       device labels ("Laser grid 3"), states, counts and times.</li>
 *   <li><b>No coordinates anywhere</b>; {@code dimension} only on an installation row.</li>
 *   <li><b>No player UUID ever</b>, and no member, officer or owner roster. Who did something is an
 *       {@code actorKind} ({@code none|player|withheld|you}); a display name is added only on the deliberate
 *       {@code events?actors=1} read by an OWNER or OFFICER that the resolver allows to read the log, and
 *       that read is itself audited as {@code LOG_READ} before any row is read.</li>
 * </ul>
 *
 * <p>Scope comes from {@link SecurityLinkAccess}, re-resolved on every call. Server thread only (the bridge
 * marshals). Any failure answers an empty section rather than throwing into the bridge.
 */
final class SecurityLinkSnapshots implements LinkSnapshotProvider {

    static final int FEED_LIMIT = 50;
    static final int MAX_LIMIT = 100;
    static final int LOG_ROWS = 10;

    private final LinkContext ctx;

    SecurityLinkSnapshots(LinkContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public String moduleId() {
        return SecurityLinkModule.MODULE_ID;
    }

    @Override
    public int schemaVersion() {
        return SecurityLinkModule.SCHEMA_VERSION;
    }

    @Override
    public List<String> sections() {
        return SecurityLinkModule.SECTIONS;
    }

    @Override
    public JsonObject snapshot(UUID playerId, String section, Map<String, String> params) {
        try {
            if (SecurityLinkModule.SECTION_WIKI.equals(section)) {
                return wiki(params);
            }
            SecurityInstallations store = ctx.installations();
            if (store == null || playerId == null || !ctx.settings().moduleEnabled()) {
                return empty(section);
            }
            return switch (section == null ? "" : section) {
                case SecurityLinkModule.SECTION_INSTALLATIONS -> installations(store, playerId);
                case SecurityLinkModule.SECTION_EVENTS -> events(store, playerId, params);
                case SecurityLinkModule.SECTION_DEVICES -> devices(store, playerId, params);
                case SecurityLinkModule.SECTION_LOG -> log(store, playerId);
                default -> error("unknown section");
            };
        } catch (RuntimeException | LinkageError e) {
            NeroSecurityCommon.LOGGER.warn("[NeroSecurity] link section {} failed ({}).", safeSection(section),
                    e.getClass().getName());
            return empty(section);
        }
    }

    // --- installations ----------------------------------------------------------------------------

    JsonObject installations(SecurityInstallations store, UUID player) {
        LinkSettings settings = ctx.settings();
        long now = ctx.now();
        JsonObject out = header(now);
        out.addProperty("lockdownEnabled", settings.lockdownEnabled());
        out.addProperty("auditEnabled", settings.auditEnabled());
        JsonObject actions = new JsonObject();
        actions.addProperty(SecurityLinkModule.ACTION_LOCKDOWN, settings.lockdownEnabled()
                ? settings.lockdownMode().jsonName() : "off");
        actions.addProperty("lockdownOffline", settings.lockdownOffline());
        actions.addProperty(SecurityLinkModule.ACTION_ACK, settings.ackEnabled());
        actions.addProperty(SecurityLinkModule.ACTION_SILENCE, settings.silenceEnabled());
        out.add("actions", actions);
        List<LinkAlert> alerts = ownAlerts(player);
        JsonArray rows = new JsonArray();
        for (Installation installation : SecurityLinkAccess.visible(ctx, store, player)) {
            Role role = SecurityLinkAccess.visibleRole(ctx, installation, player);
            rows.add(role.atLeastOfficer() ? fullRow(installation, role, settings, alerts, now)
                    : memberRow(installation, role));
        }
        out.add("installations", rows);
        return out;
    }

    private JsonObject memberRow(Installation installation, Role role) {
        JsonObject row = new JsonObject();
        row.addProperty("id", installation.id().toString());
        row.addProperty("name", installation.name());
        addDimension(row, installation);
        row.addProperty("role", role.jsonName());
        JsonObject lockdown = new JsonObject();
        lockdown.addProperty("active", lockdownActive(installation));
        lockdown.addProperty("source", installation.lockdown().source().jsonName());
        row.add("lockdown", lockdown);
        return row;
    }

    private JsonObject fullRow(Installation installation, Role role, LinkSettings settings, List<LinkAlert> alerts,
            long now) {
        JsonObject row = new JsonObject();
        row.addProperty("id", installation.id().toString());
        row.addProperty("name", installation.name());
        addDimension(row, installation);
        row.addProperty("role", role.jsonName());
        row.addProperty("orphaned", installation.isOrphaned());
        row.add("lockdown", lockdownJson(installation));
        row.add("deviceCounts", DeviceKinds.counts(installation.devices()));
        Boolean powered = powered(installation, settings, now);
        if (powered == null) {
            row.add("powered", JsonNull.INSTANCE);
        } else {
            row.addProperty("powered", powered);
        }
        List<AuditEntry> last = settings.auditEnabled() ? ctx.peek(installation, 1) : List.of();
        if (last.isEmpty()) {
            row.add("lastEventAt", JsonNull.INSTANCE);
            row.add("lastEvent", JsonNull.INSTANCE);
        } else {
            AuditEntry entry = last.get(0);
            row.addProperty("lastEventAt", entry.at());
            JsonObject lastEvent = new JsonObject();
            lastEvent.addProperty("type", lower(entry.type().name()));
            lastEvent.addProperty("severity", lower(entry.severity().name()));
            lastEvent.addProperty("sentence", PushCopy.phrase(entry.type()));
            row.add("lastEvent", lastEvent);
        }
        row.addProperty("unackedAlerts", unacked(alerts, installation.id(), now));
        row.addProperty("notifyOfficers", installation.notifyOfficers());
        return row;
    }

    /** True if a loaded power-using device has power, false if loaded ones exist and none has, null otherwise. */
    private @Nullable Boolean powered(Installation installation, LinkSettings settings, long now) {
        DeviceStatusSource source = ctx.deviceStatus();
        int budget = Math.max(1, settings.maxDevices());
        int visited = 0;
        boolean anyPowerUser = false;
        for (Map.Entry<DevicePos, Identifier> device : installation.devices().entrySet()) {
            if (++visited > budget) {
                break;
            }
            DeviceStatusSource.Status status = source.status(installation, device.getKey(), device.getValue(), now);
            if (status == null || !status.loaded() || status.powered() == null) {
                continue;
            }
            if (status.powered()) {
                return true;
            }
            anyPowerUser = true;
        }
        return anyPowerUser ? Boolean.FALSE : null;
    }

    // --- events -----------------------------------------------------------------------------------

    JsonObject events(SecurityInstallations store, UUID player, Map<String, String> params) {
        LinkSettings settings = ctx.settings();
        long now = ctx.now();
        String requested = params == null ? null : params.get("installation");
        boolean wantActors = params != null && "1".equals(params.get("actors"));
        List<LinkAlert> alerts = ownAlerts(player);
        Set<String> alertIds = new HashSet<>();
        for (LinkAlert alert : alerts) {
            alertIds.add(alert.id());
        }
        if (requested == null || requested.isBlank()) {
            JsonObject out = header(now);
            out.addProperty("auditEnabled", settings.auditEnabled());
            out.addProperty("actorsIncluded", false);
            List<Row> merged = new ArrayList<>();
            if (settings.auditEnabled()) {
                for (Installation installation : SecurityLinkAccess.visible(ctx, store, player)) {
                    if (!SecurityLinkAccess.visibleRole(ctx, installation, player).atLeastOfficer()) {
                        continue;
                    }
                    for (AuditEntry entry : ctx.peek(installation, FEED_LIMIT)) {
                        merged.add(new Row(installation, entry));
                    }
                }
            }
            merged.sort(Comparator.comparingLong((Row r) -> r.entry().at()).reversed());
            JsonArray rows = new JsonArray();
            for (Row r : merged.subList(0, Math.min(FEED_LIMIT, merged.size()))) {
                rows.add(auditRow(r.installation(), r.entry(), player, alertIds, null));
            }
            out.add("events", rows);
            return out;
        }
        Installation installation = SecurityLinkAccess.param(ctx, store, player, requested);
        if (installation == null) {
            return error("unknown installation");
        }
        Role role = SecurityLinkAccess.visibleRole(ctx, installation, player);
        if (!role.atLeastOfficer()) {
            return restricted(installation, "events", now);
        }
        int limit = limit(params == null ? null : params.get("limit"));
        JsonObject out = header(now);
        out.addProperty("installation", installation.id().toString());
        out.addProperty("auditEnabled", settings.auditEnabled());
        boolean actors = false;
        if (wantActors && settings.auditEnabled() && mayReadLog(installation, player)) {
            // Audited before anything is read (the same order as the in-game log read).
            ctx.publish(SecurityEvent.of(SecurityEventType.LOG_READ, installation.id(), null, player, now));
            actors = true;
        }
        out.addProperty("actorsIncluded", actors);
        JsonArray rows = new JsonArray();
        if (settings.auditEnabled()) {
            for (AuditEntry entry : ctx.peek(installation, limit)) {
                rows.add(auditRow(installation, entry, player, alertIds, actors ? ctx::displayName : null));
            }
        }
        out.add("events", rows);
        return out;
    }

    private record Row(Installation installation, AuditEntry entry) {
    }

    /** The in-game log-read rule (READ_LOG at OFFICER, lockdown and denies included), no operator override. */
    private boolean mayReadLog(Installation installation, UUID player) {
        AccessDecision decision = ctx.resolver().resolve(new AccessQuery(ctx.server(), player, installation,
                AccessAction.READ_LOG, null), AccessAction.READ_LOG.defaultMinimum);
        return decision.allowed() && !decision.admin();
    }

    private JsonObject auditRow(Installation installation, AuditEntry entry, UUID requester, Set<String> alertIds,
            @Nullable Function<UUID, String> names) {
        String actorKind = actorKind(entry, requester);
        JsonObject row = eventRow(installation, entry.at(), entry.type(), entry.severity(), entry.devicePos(),
                actorKind, alertIds);
        if (names != null && "player".equals(actorKind) && entry.actor() != null) {
            JsonObject actor = new JsonObject();
            actor.addProperty("name", names.apply(entry.actor()));
            row.add("actor", actor);
        }
        return row;
    }

    static String actorKind(AuditEntry entry, UUID requester) {
        ActorKind kind = entry.actorKind();
        return switch (kind) {
            case NONE -> "none";
            case WITHHELD -> "withheld";
            case PLAYER -> requester != null && requester.equals(entry.actor()) ? "you" : "player";
        };
    }

    /**
     * One event row, the shape both the {@code events} section and the live {@code nerosecurity.events}
     * topic use. Never an actor id, never a position.
     */
    static JsonObject eventRow(Installation installation, long at, SecurityEventType type, SecuritySeverity severity,
            @Nullable DevicePos pos, String actorKind, Set<String> alertIds) {
        JsonObject row = new JsonObject();
        String typeName = lower(type.name());
        row.addProperty("id", at + "-" + typeName);
        row.addProperty("at", at);
        row.addProperty("installationId", installation.id().toString());
        row.addProperty("installationName", installation.name());
        row.addProperty("type", typeName);
        row.addProperty("severity", lower((severity == null ? type.defaultSeverity : severity).name()));
        row.addProperty("sentence", PushCopy.phrase(type));
        DeviceKinds.Label label = DeviceKinds.label(installation.devices(), pos);
        if (label != null) {
            JsonObject device = new JsonObject();
            device.addProperty("kind", label.kind());
            device.addProperty("label", label.text());
            row.add("device", device);
        }
        row.addProperty("actorKind", actorKind);
        String alertId = alertIdFor(installation.id(), type, alertIds);
        if (alertId != null) {
            row.addProperty("alertId", alertId);
        }
        return row;
    }

    /** The requester's existing alert this row belongs to, if any. */
    static @Nullable String alertIdFor(UUID installationId, SecurityEventType type, Set<String> alertIds) {
        if (alertIds == null || alertIds.isEmpty()) {
            return null;
        }
        Optional<PushKind> kind = PushKind.of(type);
        String id = kind.isPresent() ? PushCopy.alertId(installationId, kind.get())
                : LinkEventMapper.DENIALS.contains(type) ? PushCopy.alertId(installationId, PushKind.REPEATED_DENIED)
                : null;
        return id != null && alertIds.contains(id) ? id : null;
    }

    static int limit(@Nullable String raw) {
        if (raw == null) {
            return FEED_LIMIT;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value < 1 ? FEED_LIMIT : Math.min(MAX_LIMIT, value);
        } catch (NumberFormatException junk) {
            return FEED_LIMIT;
        }
    }

    // --- devices ----------------------------------------------------------------------------------

    JsonObject devices(SecurityInstallations store, UUID player, Map<String, String> params) {
        long now = ctx.now();
        Installation installation = SecurityLinkAccess.param(ctx, store, player, params);
        if (installation == null) {
            return error("unknown installation");
        }
        if (!SecurityLinkAccess.visibleRole(ctx, installation, player).atLeastOfficer()) {
            return restricted(installation, "devices", now);
        }
        JsonObject out = header(now);
        out.addProperty("installation", installation.id().toString());
        DeviceStatusSource source = ctx.deviceStatus();
        int budget = Math.max(1, ctx.settings().maxDevices());
        Map<DevicePos, Identifier> devices = installation.devices();
        Map<String, Integer> ordinals = new HashMap<>();
        JsonArray rows = new JsonArray();
        int visited = 0;
        for (Map.Entry<DevicePos, Identifier> device : devices.entrySet()) {
            if (++visited > budget) {
                break;
            }
            String kind = DeviceKinds.kind(device.getValue());
            int ordinal = ordinals.merge(kind, 1, Integer::sum);
            JsonObject row = new JsonObject();
            row.addProperty("kind", kind);
            row.addProperty("label", DeviceKinds.labelText(kind, ordinal));
            DeviceStatusSource.Status status = source.status(installation, device.getKey(), device.getValue(), now);
            boolean loaded = status != null && status.loaded();
            row.addProperty("loaded", loaded);
            if (status != null && loaded) {
                if (status.powered() != null) {
                    row.addProperty("powered", status.powered());
                }
                if (status.state() != null) {
                    row.addProperty("state", status.state());
                }
                if (status.tripped()) {
                    row.addProperty("tripped", true);
                }
                if (status.silencedUntil() > 0L) {
                    row.addProperty("silencedUntil", status.silencedUntil());
                }
            }
            rows.add(row);
        }
        out.add("devices", rows);
        return out;
    }

    // --- log (legacy SummaryDoc) ------------------------------------------------------------------

    JsonObject log(SecurityInstallations store, UUID player) {
        LinkSettings settings = ctx.settings();
        long now = ctx.now();
        List<Installation> visible = SecurityLinkAccess.visible(ctx, store, player);
        int inLockdown = 0;
        JsonArray installationItems = new JsonArray();
        List<Row> recent = new ArrayList<>();
        for (Installation installation : visible) {
            boolean locked = lockdownActive(installation);
            if (locked) {
                inLockdown++;
            }
            JsonObject item = new JsonObject();
            item.addProperty("label", installation.name());
            item.addProperty("value", locked ? "Lockdown" : "Normal");
            item.addProperty("status", locked ? "lockdown" : "ok");
            item.addProperty("severity", locked ? "critical" : "info");
            installationItems.add(item);
            if (settings.auditEnabled() && SecurityLinkAccess.visibleRole(ctx, installation, player).atLeastOfficer()) {
                for (AuditEntry entry : ctx.peek(installation, LOG_ROWS)) {
                    recent.add(new Row(installation, entry));
                }
            }
        }
        recent.sort(Comparator.comparingLong((Row r) -> r.entry().at()).reversed());
        int lastHour = 0;
        for (Row r : recent) {
            if (now - r.entry().at() <= 3_600_000L) {
                lastHour++;
            }
        }
        JsonArray eventItems = new JsonArray();
        for (Row r : recent.subList(0, Math.min(LOG_ROWS, recent.size()))) {
            JsonObject item = new JsonObject();
            item.addProperty("label", PushCopy.phrase(r.entry().type()) + " — " + r.installation().name());
            item.addProperty("value", ago(now, r.entry().at()));
            item.addProperty("severity", lower(r.entry().severity().name()));
            eventItems.add(item);
        }
        JsonObject out = header(now);
        out.addProperty("headline", visible.size() + " installation" + (visible.size() == 1 ? "" : "s") + " · "
                + inLockdown + " in lockdown · " + lastHour + " event" + (lastHour == 1 ? "" : "s")
                + " in the last hour");
        JsonArray sections = new JsonArray();
        JsonObject installationsSection = new JsonObject();
        installationsSection.addProperty("title", "Installations");
        installationsSection.add("items", installationItems);
        sections.add(installationsSection);
        JsonObject eventsSection = new JsonObject();
        eventsSection.addProperty("title", "Recent events");
        eventsSection.add("items", eventItems);
        sections.add(eventsSection);
        out.add("sections", sections);
        return out;
    }

    static String ago(long now, long at) {
        long minutes = Math.max(0L, (now - at) / 60_000L);
        if (minutes < 60) {
            return minutes + "m ago";
        }
        long hours = minutes / 60;
        return hours < 48 ? hours + "h ago" : (hours / 24) + "d ago";
    }

    // --- wiki -------------------------------------------------------------------------------------

    /** WIKI CONTRACT v1: public pages, the same for every player ({@code playerId} is ignored). */
    static JsonObject wiki(@Nullable Map<String, String> params) {
        long now = System.currentTimeMillis();
        String slug = params == null ? null : params.get("page");
        if (slug == null || slug.isBlank()) {
            JsonObject out = new JsonObject();
            out.addProperty("mod", SecurityLinkModule.MODULE_ID);
            out.addProperty("title", "NeroSecurity");
            JsonArray pages = new JsonArray();
            for (WikiLibrary.Page page : WikiLibrary.pages()) {
                JsonObject p = new JsonObject();
                p.addProperty("slug", page.slug());
                p.addProperty("title", page.title());
                pages.add(p);
            }
            out.add("pages", pages);
            out.addProperty("asOf", now);
            return out;
        }
        Optional<String> content = WikiLibrary.content(slug);
        if (content.isEmpty()) {
            JsonObject out = new JsonObject();
            out.addProperty("error", "unknown page");
            out.addProperty("slug", WikiLibrary.isSafeSlug(slug) ? slug : "");
            return out;
        }
        JsonObject out = new JsonObject();
        out.addProperty("mod", SecurityLinkModule.MODULE_ID);
        out.addProperty("slug", slug);
        out.addProperty("title", WikiLibrary.titleOf(slug));
        out.addProperty("format", "markdown");
        out.addProperty("content", content.get());
        out.addProperty("asOf", now);
        return out;
    }

    // --- shared -----------------------------------------------------------------------------------

    private List<LinkAlert> ownAlerts(UUID player) {
        try {
            return ctx.alerts(player);
        } catch (RuntimeException | LinkageError e) {
            return List.of();
        }
    }

    /** Unacknowledged, unsnoozed alerts of this module for one installation. */
    static int unacked(List<LinkAlert> alerts, UUID installationId, long now) {
        String prefix = SecurityLinkModule.MODULE_ID + "." + installationId + ".";
        int n = 0;
        for (LinkAlert alert : alerts) {
            if (SecurityLinkModule.MODULE_ID.equals(alert.moduleId()) && alert.id() != null
                    && alert.id().startsWith(prefix) && !alert.acked() && alert.snoozedUntil() <= now) {
                n++;
            }
        }
        return n;
    }

    private boolean lockdownActive(Installation installation) {
        return installation.lockdown().active() && ctx.settings().lockdownEnabled();
    }

    private JsonObject lockdownJson(Installation installation) {
        return lockdownJson(installation.lockdown(), ctx.settings().lockdownEnabled());
    }

    /** {@code {active, since, source, autoReleaseAt, byOwner}}; inactive while lockdown is switched off. */
    static JsonObject lockdownJson(Lockdown lockdown, boolean lockdownEnabled) {
        Lockdown l = lockdown == null ? Lockdown.NONE : lockdown;
        boolean active = l.active() && lockdownEnabled;
        JsonObject out = new JsonObject();
        out.addProperty("active", active);
        out.addProperty("since", active ? l.since() : 0L);
        out.addProperty("source", l.source().jsonName());
        out.addProperty("autoReleaseAt", active ? l.autoReleaseAt() : 0L);
        out.addProperty("byOwner", active && l.enteredByOwner());
        return out;
    }

    private static void addDimension(JsonObject row, Installation installation) {
        DevicePos hub = installation.hubPos();
        Identifier dimension = hub != null ? hub.dimension() : null;
        if (dimension == null) {
            for (DevicePos pos : installation.devices().keySet()) {
                dimension = pos.dimension();
                break;
            }
        }
        if (dimension == null) {
            row.add("dimension", JsonNull.INSTANCE);
        } else {
            row.addProperty("dimension", dimension.toString());
        }
    }

    private static JsonObject restricted(Installation installation, String rowsKey, long now) {
        JsonObject out = header(now);
        out.addProperty("installation", installation.id().toString());
        out.addProperty("restricted", true);
        out.add(rowsKey, new JsonArray());
        return out;
    }

    static JsonObject header(long now) {
        JsonObject out = new JsonObject();
        out.addProperty("schema_version", SecurityLinkModule.SCHEMA_VERSION);
        out.addProperty("asOf", now);
        return out;
    }

    private JsonObject error(String message) {
        JsonObject out = header(ctx.now());
        out.addProperty("error", message);
        return out;
    }

    private JsonObject empty(String section) {
        JsonObject out = header(ctx.now());
        if (SecurityLinkModule.SECTION_INSTALLATIONS.equals(section)) {
            out.add("installations", new JsonArray());
        } else if (SecurityLinkModule.SECTION_EVENTS.equals(section)) {
            out.add("events", new JsonArray());
        } else if (SecurityLinkModule.SECTION_DEVICES.equals(section)) {
            out.add("devices", new JsonArray());
        } else if (SecurityLinkModule.SECTION_LOG.equals(section)) {
            out.add("sections", new JsonArray());
        }
        return out;
    }

    private static String safeSection(@Nullable String section) {
        return section != null && SecurityLinkModule.SECTIONS.contains(section) ? section : "?";
    }

    static String lower(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
