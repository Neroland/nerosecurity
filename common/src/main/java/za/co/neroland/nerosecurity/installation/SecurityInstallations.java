package za.co.neroland.nerosecurity.installation;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.data.SavedDataRecovery;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.data.SecurityData;
import za.co.neroland.nerosecurity.event.SecurityEvent;
import za.co.neroland.nerosecurity.event.SecurityEventBus;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.lockdown.LockdownRules;

/**
 * The persistent installation store: every installation on the server plus three indexes.
 *
 * <ul>
 *   <li><b>byId</b> — installation id to record.</li>
 *   <li><b>deviceIndex</b> — every device <em>and hub</em> position to its installation id, so one
 *       position belongs to at most one installation and no hub can be linked as a device.</li>
 *   <li><b>byOwner</b> — owner UUID to the installations they own (the per-player limit).</li>
 * </ul>
 *
 * <p>Overworld-resident and read only through {@link #get(MinecraftServer)}, which routes through
 * Core's {@code SavedDataRecovery} so a corrupt file falls back to the last-known-good backup instead
 * of crashing every access check. The store enforces structural invariants only; who may change
 * what is decided by {@link InstallationService}, the only production caller of the mutators.
 *
 * <p><b>Privacy (POPIA/GDPR).</b> Owner and member UUIDs, levels, block positions of the owner's own
 * hardware, lockdown flags, and the card ledger (a bound card's row names its holder). No names.
 * Erasure ({@link #erase(UUID)}) removes member rows, owner entries and every card row bound to the
 * player (which kills that card for everyone); an installation left without a real owner is tombstoned
 * and orphaned. Logs carry counts.
 */
public final class SecurityInstallations extends SavedData {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroSecurityCommon.MOD_ID, "installations");
    public static final int SCHEMA_VERSION = 1;

    /** The ledger cap used on load when the config cannot be read (the largest configurable cap). */
    static final int LEDGER_CAP_FALLBACK = 4096;

    /** The whole on-disk format (schema version 1). Public so conformance tests can seed a store. */
    public static final Codec<SecurityInstallations> CODEC = buildCodec();

    /** DFU-free: the codec is the whole format. */
    public static final SavedDataType<SecurityInstallations> TYPE =
            new SavedDataType<>(ID, SecurityInstallations::new, CODEC, null);

    /** The production eraser, joined to {@link SecurityData}'s composite at init. */
    private static final SecurityData.StoreEraser ERASER = eraser(SecurityInstallations::get);

    private final Map<UUID, Installation> byId = new LinkedHashMap<>();
    private final Map<DevicePos, UUID> deviceIndex = new HashMap<>();
    private final Map<UUID, Set<UUID>> byOwner = new HashMap<>();
    /** Transient: ids whose lockdown is an active alarm lockdown (rebuilt on load, kept by setLockdown). */
    private final Set<UUID> pendingAutoRelease = new LinkedHashSet<>();

    public SecurityInstallations() {
    }

    /** The only accessor. */
    public static SecurityInstallations get(MinecraftServer server) {
        return SavedDataRecovery.get(server.overworld(), TYPE, SecurityInstallations::new, ID.toString());
    }

    /** Init step 4a: joins the composite eraser. */
    public static void registerEraser() {
        SecurityData.addStore(ERASER);
    }

    // --- read API -------------------------------------------------------------------------------

    public synchronized Optional<Installation> byId(UUID id) {
        return Optional.ofNullable(id == null ? null : byId.get(id));
    }

    /** The installation a <em>device</em> at {@code pos} is linked to (hub positions excluded). */
    public synchronized Optional<Installation> atDevice(DevicePos pos) {
        Installation found = indexed(pos);
        return Optional.ofNullable(found != null && found.rawDevices().containsKey(pos) ? found : null);
    }

    /** The installation whose hub stands at {@code pos}. */
    public synchronized Optional<Installation> atHub(DevicePos pos) {
        Installation found = indexed(pos);
        return Optional.ofNullable(found != null && pos.equals(found.hubPos()) ? found : null);
    }

    public synchronized Set<UUID> ownedBy(UUID owner) {
        Set<UUID> owned = byOwner.get(owner);
        return owned == null ? Set.of() : Set.copyOf(owned);
    }

    public synchronized int countOwnedBy(UUID owner) {
        Set<UUID> owned = byOwner.get(owner);
        return owned == null ? 0 : owned.size();
    }

    /** Installations {@code player} owns or is a member of. */
    public synchronized List<Installation> visibleTo(UUID player) {
        List<Installation> out = new ArrayList<>();
        for (Installation installation : byId.values()) {
            if (installation.isOwner(player) || installation.rawMembers().containsKey(player)) {
                out.add(installation);
            }
        }
        return out;
    }

    /** Every installation. Operator commands only. */
    public synchronized List<Installation> all() {
        return new ArrayList<>(byId.values());
    }

    public synchronized int size() {
        return byId.size();
    }

    /** The outcome of {@link #resolveDetailed}. */
    public enum Lookup { FOUND, NOT_FOUND, AMBIGUOUS }

    /** A lookup outcome plus the installation when found. */
    public record LookupResult(Lookup status, @Nullable Installation installation) {
    }

    /** {@link #resolveDetailed} without the not-found/ambiguous distinction. */
    public Optional<Installation> resolve(String input, Predicate<Installation> scope) {
        LookupResult result = resolveDetailed(input, scope);
        return Optional.ofNullable(result.installation());
    }

    /**
     * Resolves a user-typed reference within {@code scope}: an exact UUID, else a unique id prefix of at
     * least eight hex characters, else a unique exact display name.
     */
    public synchronized LookupResult resolveDetailed(String input, Predicate<Installation> scope) {
        if (input == null || input.isBlank()) {
            return new LookupResult(Lookup.NOT_FOUND, null);
        }
        String text = input.trim();
        try {
            Installation exact = byId.get(UUID.fromString(text));
            if (exact != null && scope.test(exact)) {
                return new LookupResult(Lookup.FOUND, exact);
            }
        } catch (IllegalArgumentException notAUuid) {
            // fall through to prefix and name
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.length() >= 8 && lower.matches("[0-9a-f-]+")) {
            LookupResult byPrefix = unique(i -> i.id().toString().startsWith(lower), scope);
            if (byPrefix.status() != Lookup.NOT_FOUND) {
                return byPrefix;
            }
        }
        return unique(i -> i.name().equals(text), scope);
    }

    private LookupResult unique(Predicate<Installation> match, Predicate<Installation> scope) {
        Installation found = null;
        for (Installation installation : byId.values()) {
            if (scope.test(installation) && match.test(installation)) {
                if (found != null) {
                    return new LookupResult(Lookup.AMBIGUOUS, null);
                }
                found = installation;
            }
        }
        return new LookupResult(found == null ? Lookup.NOT_FOUND : Lookup.FOUND, found);
    }

    // --- mutation API ---------------------------------------------------------------------------

    public enum CreateStatus { OK, LIMIT_REACHED, POSITION_TAKEN }

    public record CreateResult(CreateStatus status, @Nullable Installation installation) {
    }

    public enum MemberResult { ADDED, CHANGED, REMOVED, UNCHANGED, NOT_A_MEMBER_LEVEL, LIMIT_REACHED, IS_OWNER, NO_INSTALLATION }

    public enum LinkResult { OK, ALREADY_LINKED_HERE, LINKED_ELSEWHERE, IS_HUB, LIMIT_REACHED, NO_INSTALLATION }

    public synchronized CreateResult create(UUID owner, String name, DevicePos hubPos, long now, int maxPerPlayer) {
        if (deviceIndex.containsKey(hubPos)) {
            return new CreateResult(CreateStatus.POSITION_TAKEN, null);
        }
        if (countOwnedBy(owner) >= maxPerPlayer) {
            return new CreateResult(CreateStatus.LIMIT_REACHED, null);
        }
        Installation installation = Installation.create(owner, name, now);
        installation.setHubPos(hubPos);
        byId.put(installation.id(), installation);
        index(installation);
        setDirty();
        return new CreateResult(CreateStatus.OK, installation);
    }

    /** Removes the record and every index row. */
    public synchronized boolean dissolve(UUID id) {
        Installation removed = byId.remove(id);
        if (removed == null) {
            return false;
        }
        unindex(removed);
        pendingAutoRelease.remove(id);
        setDirty();
        return true;
    }

    /** The hub is gone: drop its position and mark the installation orphaned. */
    public synchronized boolean orphan(UUID id) {
        Installation installation = byId.get(id);
        if (installation == null) {
            return false;
        }
        DevicePos hub = installation.hubPos();
        if (hub != null && id.equals(deviceIndex.get(hub))) {
            deviceIndex.remove(hub);
        }
        installation.setHubPos(null);
        installation.setOrphaned(true);
        setDirty();
        return true;
    }

    // --- integration bindings -------------------------------------------------------------------
    // Organisation ids only (a faction id, a colony id, a station id), never a player. Callers (the binding
    // service) have resolved BIND_INTEGRATION at OWNER and checked the binder's membership. Each max level is
    // MEMBER or OFFICER: an integration can never grant OWNER, so OWNER (and PUBLIC) are refused here.

    /** Whether {@code level} may cap an integration binding: MEMBER or OFFICER only. */
    public static boolean isBindingMaxLevel(@Nullable AccessLevel level) {
        return level == AccessLevel.MEMBER || level == AccessLevel.OFFICER;
    }

    /** Binds the installation to a faction. Refuses (false) a null faction or a max level above OFFICER. */
    public synchronized boolean bindFaction(UUID id, Identifier faction, AccessLevel max) {
        Installation installation = byId.get(id);
        if (installation == null || faction == null || !isBindingMaxLevel(max)) {
            return false;
        }
        installation.setBoundFaction(faction);
        installation.setFactionMaxLevel(max);
        setDirty();
        return true;
    }

    /** Removes the faction binding (the max level returns to OFFICER). False when there was none. */
    public synchronized boolean unbindFaction(UUID id) {
        Installation installation = byId.get(id);
        if (installation == null || installation.boundFaction() == null) {
            return false;
        }
        installation.setBoundFaction(null);
        installation.setFactionMaxLevel(AccessLevel.OFFICER);
        setDirty();
        return true;
    }

    /** Binds the installation to a colony (its id). Refuses (false) a null colony or a max level above OFFICER. */
    public synchronized boolean bindColony(UUID id, UUID colony, AccessLevel max) {
        Installation installation = byId.get(id);
        if (installation == null || colony == null || !isBindingMaxLevel(max)) {
            return false;
        }
        installation.setBoundColony(colony);
        installation.setColonyMaxLevel(max);
        setDirty();
        return true;
    }

    /** Removes the colony binding (the max level returns to OFFICER). False when there was none. */
    public synchronized boolean unbindColony(UUID id) {
        Installation installation = byId.get(id);
        if (installation == null || installation.boundColony() == null) {
            return false;
        }
        installation.setBoundColony(null);
        installation.setColonyMaxLevel(AccessLevel.OFFICER);
        setDirty();
        return true;
    }

    /** Binds the installation to a Nerospace station id. Its current owner gets OFFICER (never OWNER). */
    public synchronized boolean bindStation(UUID id, int station) {
        Installation installation = byId.get(id);
        if (installation == null) {
            return false;
        }
        installation.setBoundStation(station);
        setDirty();
        return true;
    }

    /** Removes the station binding. False when there was none. */
    public synchronized boolean unbindStation(UUID id) {
        Installation installation = byId.get(id);
        if (installation == null || installation.boundStation() == null) {
            return false;
        }
        installation.setBoundStation(null);
        setDirty();
        return true;
    }

    /** Sets whether officers receive the installation's alarm chat. Callers have authorised it. */
    public synchronized boolean setNotifyOfficers(UUID id, boolean notifyOfficers) {
        Installation installation = byId.get(id);
        if (installation == null) {
            return false;
        }
        installation.setNotifyOfficers(notifyOfficers);
        setDirty();
        return true;
    }

    public synchronized boolean rename(UUID id, String name) {
        Installation installation = byId.get(id);
        if (installation == null) {
            return false;
        }
        installation.setName(name);
        setDirty();
        return true;
    }

    /** Sets a member row; PUBLIC removes it. Owners and the OWNER/PUBLIC levels never become rows. */
    public synchronized MemberResult setMember(UUID id, UUID target, AccessLevel level, int maxMembers) {
        Installation installation = byId.get(id);
        if (installation == null) {
            return MemberResult.NO_INSTALLATION;
        }
        if (target == null || level == null || level == AccessLevel.OWNER || Installation.TOMBSTONE_OWNER.equals(target)) {
            return MemberResult.NOT_A_MEMBER_LEVEL;
        }
        if (installation.rawOwners().contains(target)) {
            return MemberResult.IS_OWNER;
        }
        Map<UUID, AccessLevel> members = installation.rawMembers();
        AccessLevel current = members.get(target);
        if (level == AccessLevel.PUBLIC) {
            if (current == null) {
                return MemberResult.UNCHANGED;
            }
            members.remove(target);
            setDirty();
            return MemberResult.REMOVED;
        }
        if (current == level) {
            return MemberResult.UNCHANGED;
        }
        if (current == null) {
            if (members.size() >= maxMembers) {
                return MemberResult.LIMIT_REACHED;
            }
            members.put(target, level);
            setDirty();
            return MemberResult.ADDED;
        }
        members.put(target, level);
        setDirty();
        return MemberResult.CHANGED;
    }

    /** {@link #link(UUID, DevicePos, Identifier, int)} without a device cap. Tests only. */
    LinkResult link(UUID id, DevicePos pos, Identifier blockId) {
        return link(id, pos, blockId, Integer.MAX_VALUE);
    }

    /**
     * Links a device position to an installation. One position belongs to at most one installation, a hub
     * position is never a device, and an installation holds at most {@code maxDevices} devices.
     */
    public synchronized LinkResult link(UUID id, DevicePos pos, Identifier blockId, int maxDevices) {
        Installation installation = byId.get(id);
        if (installation == null) {
            return LinkResult.NO_INSTALLATION;
        }
        UUID holder = deviceIndex.get(pos);
        if (holder != null) {
            Installation other = byId.get(holder);
            if (other != null && pos.equals(other.hubPos())) {
                return LinkResult.IS_HUB;
            }
            return holder.equals(id) ? LinkResult.ALREADY_LINKED_HERE : LinkResult.LINKED_ELSEWHERE;
        }
        if (installation.rawDevices().size() >= maxDevices) {
            return LinkResult.LIMIT_REACHED;
        }
        installation.rawDevices().put(pos, blockId);
        deviceIndex.put(pos, id);
        setDirty();
        return LinkResult.OK;
    }

    public synchronized boolean unlink(UUID id, DevicePos pos) {
        Installation installation = byId.get(id);
        if (installation == null || installation.rawDevices().remove(pos) == null) {
            return false;
        }
        if (id.equals(deviceIndex.get(pos))) {
            deviceIndex.remove(pos);
        }
        setDirty();
        return true;
    }

    // --- card ledger and automation policy -------------------------------------------------------

    public enum IssueStatus { OK, LEDGER_FULL, NO_INSTALLATION }

    /** The outcome of {@link #issueCard}: on {@link IssueStatus#OK} the new serial and its row. */
    public record IssueResult(IssueStatus status, long serial, @Nullable CardRecord record) {

        static IssueResult refused(IssueStatus status) {
            return new IssueResult(status, 0L, null);
        }
    }

    /**
     * Adds a ledger row under a fresh serial. The store does not authorise: the caller has already
     * decided the actor may issue this card. At the cap the call is refused; nothing is ever evicted,
     * so no revoked card can come back.
     */
    public synchronized IssueResult issueCard(UUID id, AccessLevel level, boolean encrypted, @Nullable UUID boundOwner,
            long now, int cap) {
        if (level == null) {
            throw new IllegalArgumentException("card level must not be null");
        }
        Installation installation = byId.get(id);
        if (installation == null) {
            return IssueResult.refused(IssueStatus.NO_INSTALLATION);
        }
        if (installation.rawCards().size() >= cap) {
            return IssueResult.refused(IssueStatus.LEDGER_FULL);
        }
        long serial = installation.nextSerial();
        installation.setNextSerial(serial + 1);
        CardRecord record = new CardRecord(level, encrypted, boundOwner, now);
        installation.rawCards().put(serial, record);
        setDirty();
        return new IssueResult(IssueStatus.OK, serial, record);
    }

    /** Removes one ledger row; the card with that serial is dead from now on. False when absent. */
    public synchronized boolean revokeCard(UUID id, long serial) {
        Installation installation = byId.get(id);
        if (installation == null || installation.rawCards().remove(serial) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    /**
     * Clears the ledger and raises the serial floor to the next serial, so every card issued so far is
     * dead even if a row were somehow restored.
     *
     * @return how many rows were removed (-1 when the installation does not exist)
     */
    public synchronized int revokeAllCards(UUID id) {
        Installation installation = byId.get(id);
        if (installation == null) {
            return -1;
        }
        int removed = installation.rawCards().size();
        installation.rawCards().clear();
        installation.setMinValidSerial(Math.max(installation.minValidSerial(), installation.nextSerial()));
        setDirty();
        return removed;
    }

    /** Whether {@code level} is a valid automation policy: off (null), MEMBER or OFFICER. */
    public static boolean isAutomationLevel(@Nullable AccessLevel level) {
        return level == null || level == AccessLevel.MEMBER || level == AccessLevel.OFFICER;
    }

    /** Sets the secure-chest automation policy. Refuses OWNER and PUBLIC (returns false). */
    public synchronized boolean setAutomationLevel(UUID id, @Nullable AccessLevel level) {
        Installation installation = byId.get(id);
        if (installation == null || !isAutomationLevel(level)) {
            return false;
        }
        installation.setAutomationLevelInternal(level);
        setDirty();
        return true;
    }

    public synchronized boolean setLockdown(UUID id, Lockdown state) {
        Installation installation = byId.get(id);
        if (installation == null) {
            return false;
        }
        installation.setLockdown(state);
        trackAutoRelease(installation);
        setDirty();
        return true;
    }

    /** Records when the installation's last alarm lockdown ended (the alarm-lockdown cooldown clock). */
    public synchronized boolean setLastAlarmLockdownEndedAt(UUID id, long at) {
        Installation installation = byId.get(id);
        if (installation == null) {
            return false;
        }
        installation.setLastAlarmLockdownEndedAt(at);
        setDirty();
        return true;
    }

    /** Sets the installation-wide alarm silence deadline (epoch ms, 0 = none). Not personal data. */
    public synchronized boolean setAlarmSilencedUntil(UUID id, long until) {
        Installation installation = byId.get(id);
        if (installation == null) {
            return false;
        }
        if (installation.alarmSilencedUntil() != Math.max(0L, until)) {
            installation.setAlarmSilencedUntil(until);
            setDirty();
        }
        return true;
    }

    /** Installations whose alarm lockdown is due to end by itself at {@code now}. Scheduler only. */
    public synchronized List<Installation> dueForAutoRelease(long now) {
        List<Installation> due = new ArrayList<>();
        for (UUID id : pendingAutoRelease) {
            Installation installation = byId.get(id);
            if (installation != null && LockdownRules.dueForAutoRelease(installation.lockdown(), now)) {
                due.add(installation);
            }
        }
        return due;
    }

    private void trackAutoRelease(Installation installation) {
        if (installation.lockdown().isAlarm()) {
            pendingAutoRelease.add(installation.id());
        } else {
            pendingAutoRelease.remove(installation.id());
        }
    }

    // --- erasure (POPIA/GDPR) -------------------------------------------------------------------

    /**
     * Removes {@code player} from every installation: member rows go; owner entries go; card rows
     * bound to the player go (the bound card is dead for everyone from then on); an installation left
     * without a real owner gets the tombstone owner, is marked orphaned, loses the "entered by
     * owner" lockdown flag and is renamed to {@link InstallationNames#orphanedFor(UUID)} (the old label may
     * name the erased player; co-owned installations keep theirs). Serials and the automation policy are not personal data and stay.
     *
     * @return the ids of installations this call orphaned
     */
    public synchronized List<UUID> erase(UUID player) {
        List<UUID> orphaned = new ArrayList<>();
        if (player == null) {
            return orphaned;
        }
        boolean changed = false;
        for (Installation installation : byId.values()) {
            if (installation.rawMembers().remove(player) != null) {
                changed = true;
            }
            if (installation.rawCards().values().removeIf(card -> player.equals(card.boundOwner()))) {
                changed = true;
            }
            if (installation.removeOwnerInternal(player)) {
                changed = true;
                if (installation.hasNoOwners()) {
                    installation.addOwnerInternal(Installation.TOMBSTONE_OWNER);
                    installation.setOrphaned(true);
                    // The owner-chosen label may name the erased player: replace it with a neutral one.
                    installation.setName(InstallationNames.orphanedFor(installation.id()));
                    installation.setLockdown(installation.lockdown().withEnteredByOwner(false));
                    orphaned.add(installation.id());
                }
            }
        }
        if (changed) {
            rebuildOwnerIndex();
            setDirty();
        }
        return orphaned;
    }

    /** Conformance probe: does any installation still hold a member row for {@code player}? */
    public synchronized boolean hasMemberRow(UUID player) {
        for (Installation installation : byId.values()) {
            if (installation.rawMembers().containsKey(player)) {
                return true;
            }
        }
        return false;
    }

    /** Conformance probe: is {@code player} still an owner of any installation? */
    public synchronized boolean isOwnerAnywhere(UUID player) {
        for (Installation installation : byId.values()) {
            if (installation.rawOwners().contains(player)) {
                return true;
            }
        }
        return false;
    }

    /** Conformance probe: does any installation still hold a card row bound to {@code player}? */
    public synchronized boolean hasCardBoundTo(UUID player) {
        for (Installation installation : byId.values()) {
            for (CardRecord card : installation.rawCards().values()) {
                if (player.equals(card.boundOwner())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A {@link SecurityData.StoreEraser} over the store {@code lookup} returns for a server. Production
     * passes {@link #get(MinecraftServer)}; tests pass a store built with {@code new} (server null).
     */
    public static SecurityData.StoreEraser eraser(Function<MinecraftServer, SecurityInstallations> lookup) {
        return new SecurityData.StoreEraser() {
            @Override
            public String name() {
                return "installations";
            }

            @Override
            public void erase(MinecraftServer server, UUID player) {
                List<UUID> orphaned = lookup.apply(server).erase(player);
                if (server != null) {
                    long now = System.currentTimeMillis();
                    for (UUID id : orphaned) {
                        SecurityEventBus.publish(SecurityEvent.of(SecurityEventType.INSTALLATION_ORPHANED, id, null, null, now));
                    }
                }
            }

            @Override
            public void backupNow(MinecraftServer server) {
                SavedDataRecovery.backupNow(server.overworld(), TYPE, lookup.apply(server), ID.toString());
            }
        };
    }

    // --- indexes --------------------------------------------------------------------------------

    private @Nullable Installation indexed(DevicePos pos) {
        UUID id = pos == null ? null : deviceIndex.get(pos);
        return id == null ? null : byId.get(id);
    }

    private void index(Installation installation) {
        DevicePos hub = installation.hubPos();
        if (hub != null) {
            deviceIndex.put(hub, installation.id());
        }
        for (DevicePos device : installation.rawDevices().keySet()) {
            deviceIndex.put(device, installation.id());
        }
        for (UUID owner : installation.rawOwners()) {
            if (!Installation.TOMBSTONE_OWNER.equals(owner)) {
                byOwner.computeIfAbsent(owner, ignored -> new LinkedHashSet<>()).add(installation.id());
            }
        }
    }

    private void unindex(Installation installation) {
        deviceIndex.values().removeIf(installation.id()::equals);
        for (UUID owner : installation.rawOwners()) {
            Set<UUID> owned = byOwner.get(owner);
            if (owned != null) {
                owned.remove(installation.id());
                if (owned.isEmpty()) {
                    byOwner.remove(owner);
                }
            }
        }
    }

    private void rebuildOwnerIndex() {
        byOwner.clear();
        for (Installation installation : byId.values()) {
            for (UUID owner : installation.rawOwners()) {
                if (!Installation.TOMBSTONE_OWNER.equals(owner)) {
                    byOwner.computeIfAbsent(owner, ignored -> new LinkedHashSet<>()).add(installation.id());
                }
            }
        }
    }

    // --- persistence (schema version 1) ---------------------------------------------------------

    private record PosRow(String dim, int x, int y, int z) {
        static final Codec<PosRow> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("dim").forGetter(PosRow::dim),
                Codec.INT.fieldOf("x").forGetter(PosRow::x),
                Codec.INT.fieldOf("y").forGetter(PosRow::y),
                Codec.INT.fieldOf("z").forGetter(PosRow::z)
        ).apply(inst, PosRow::new));

        static PosRow of(DevicePos pos) {
            return new PosRow(pos.dimension().toString(), pos.pos().getX(), pos.pos().getY(), pos.pos().getZ());
        }

        @Nullable DevicePos toPos() {
            Identifier id = Identifier.tryParse(dim);
            return id == null ? null : new DevicePos(id, new BlockPos(x, y, z));
        }
    }

    private record DeviceRow(String dim, int x, int y, int z, String block) {
        static final Codec<DeviceRow> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("dim").forGetter(DeviceRow::dim),
                Codec.INT.fieldOf("x").forGetter(DeviceRow::x),
                Codec.INT.fieldOf("y").forGetter(DeviceRow::y),
                Codec.INT.fieldOf("z").forGetter(DeviceRow::z),
                Codec.STRING.fieldOf("block").forGetter(DeviceRow::block)
        ).apply(inst, DeviceRow::new));
    }

    private record MemberRow(String player, String level) {
        static final Codec<MemberRow> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("player").forGetter(MemberRow::player),
                Codec.STRING.fieldOf("level").forGetter(MemberRow::level)
        ).apply(inst, MemberRow::new));
    }

    /** Lockdown state. {@code source} and {@code auto_release_at} are optional (absent in older saves). */
    private record LockdownRow(boolean active, long since, boolean byOwner, String source, long autoReleaseAt) {
        static final Codec<LockdownRow> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.BOOL.optionalFieldOf("active", false).forGetter(LockdownRow::active),
                Codec.LONG.optionalFieldOf("since", 0L).forGetter(LockdownRow::since),
                Codec.BOOL.optionalFieldOf("by_owner", false).forGetter(LockdownRow::byOwner),
                Codec.STRING.optionalFieldOf("source", "manual").forGetter(LockdownRow::source),
                Codec.LONG.optionalFieldOf("auto_release_at", 0L).forGetter(LockdownRow::autoReleaseAt)
        ).apply(inst, LockdownRow::new));

        static LockdownRow of(Lockdown lockdown) {
            return new LockdownRow(lockdown.active(), lockdown.since(), lockdown.enteredByOwner(),
                    lockdown.source().jsonName(), lockdown.autoReleaseAt());
        }

        /** Junk source reads as manual; a release time on an inactive or manual lockdown reads as 0. */
        Lockdown toLockdown() {
            return new Lockdown(active, since, byOwner, LockdownSource.byName(source), Math.max(0L, autoReleaseAt));
        }
    }

    private record Row(String id, String name, long createdAt, List<String> owners, List<MemberRow> members,
            Optional<PosRow> hub, List<DeviceRow> devices, Optional<LockdownRow> lockdown, Optional<String> secret,
            boolean orphaned, Optional<String> boundFaction, String factionMaxLevel, Optional<Integer> boundStation,
            boolean notifyOfficers, Optional<Integer> auditRetentionDays, List<Dynamic<?>> audit) {
        static final MapCodec<Row> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.STRING.fieldOf("id").forGetter(Row::id),
                Codec.STRING.optionalFieldOf("name", "").forGetter(Row::name),
                Codec.LONG.optionalFieldOf("created_at", 0L).forGetter(Row::createdAt),
                Codec.STRING.listOf().optionalFieldOf("owners", List.of()).forGetter(Row::owners),
                MemberRow.CODEC.listOf().optionalFieldOf("members", List.of()).forGetter(Row::members),
                PosRow.CODEC.optionalFieldOf("hub").forGetter(Row::hub),
                DeviceRow.CODEC.listOf().optionalFieldOf("devices", List.of()).forGetter(Row::devices),
                LockdownRow.CODEC.optionalFieldOf("lockdown").forGetter(Row::lockdown),
                Codec.STRING.optionalFieldOf("secret").forGetter(Row::secret),
                Codec.BOOL.optionalFieldOf("orphaned", false).forGetter(Row::orphaned),
                Codec.STRING.optionalFieldOf("bound_faction").forGetter(Row::boundFaction),
                Codec.STRING.optionalFieldOf("faction_max_level", "officer").forGetter(Row::factionMaxLevel),
                Codec.INT.optionalFieldOf("bound_station").forGetter(Row::boundStation),
                Codec.BOOL.optionalFieldOf("notify_officers", false).forGetter(Row::notifyOfficers),
                Codec.INT.optionalFieldOf("audit_retention_days").forGetter(Row::auditRetentionDays),
                Codec.PASSTHROUGH.listOf().optionalFieldOf("audit", List.of()).forGetter(Row::audit)
        ).apply(inst, Row::new));
    }

    /** One ledger row. Every field is optional so one bad row is dropped on load, not the whole list. */
    private record CardRow(long serial, String level, boolean encrypted, Optional<String> boundOwner, long issuedAt) {
        static final Codec<CardRow> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.LONG.optionalFieldOf("serial", -1L).forGetter(CardRow::serial),
                Codec.STRING.optionalFieldOf("level", "").forGetter(CardRow::level),
                Codec.BOOL.optionalFieldOf("encrypted", false).forGetter(CardRow::encrypted),
                Codec.STRING.optionalFieldOf("bound_owner").forGetter(CardRow::boundOwner),
                Codec.LONG.optionalFieldOf("issued_at", 0L).forGetter(CardRow::issuedAt)
        ).apply(inst, CardRow::new));
    }

    /**
     * The card ledger and automation policy, flattened into the same installation object as
     * {@link Row} (same schema version; every key optional so older saves load unchanged).
     */
    private record LedgerRow(long nextSerial, long minValidSerial, List<CardRow> cards, String automationLevel) {
        static final MapCodec<LedgerRow> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.LONG.optionalFieldOf("next_serial", 1L).forGetter(LedgerRow::nextSerial),
                Codec.LONG.optionalFieldOf("min_valid_serial", 1L).forGetter(LedgerRow::minValidSerial),
                CardRow.CODEC.listOf().optionalFieldOf("cards", List.of()).forGetter(LedgerRow::cards),
                Codec.STRING.optionalFieldOf("automation_level", "off").forGetter(LedgerRow::automationLevel)
        ).apply(inst, LedgerRow::new));
    }

    /**
     * The colony binding, flattened into the same installation object (Row is at the codec builder's
     * field limit). Both keys optional, so older saves load unchanged; a malformed colony id is dropped.
     */
    private record BindingRow(Optional<String> boundColony, String colonyMaxLevel) {
        static final MapCodec<BindingRow> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.STRING.optionalFieldOf("bound_colony").forGetter(BindingRow::boundColony),
                Codec.STRING.optionalFieldOf("colony_max_level", "officer").forGetter(BindingRow::colonyMaxLevel)
        ).apply(inst, BindingRow::new));
    }

    /**
     * One installation on disk: {@link Row}, {@link LedgerRow} and {@link BindingRow} keys side by side in one
     * object, plus {@code last_alarm_lockdown_ended_at} (optional, 0 = never) and {@code alarm_silenced_until}
     * (optional, 0 = none; same schema version).
     */
    private record Entry(Row row, LedgerRow ledger, BindingRow bindings, long lastAlarmLockdownEndedAt,
            long alarmSilencedUntil) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Row.MAP_CODEC.forGetter(Entry::row),
                LedgerRow.MAP_CODEC.forGetter(Entry::ledger),
                BindingRow.MAP_CODEC.forGetter(Entry::bindings),
                Codec.LONG.optionalFieldOf("last_alarm_lockdown_ended_at", 0L).forGetter(Entry::lastAlarmLockdownEndedAt),
                Codec.LONG.optionalFieldOf("alarm_silenced_until", 0L).forGetter(Entry::alarmSilencedUntil)
        ).apply(inst, Entry::new));
    }

    private record Root(int schemaVersion, List<Entry> installations) {
    }

    private static Codec<SecurityInstallations> buildCodec() {
        Codec<Root> root = RecordCodecBuilder.create(inst -> inst.group(
                Codec.INT.optionalFieldOf("schema_version", SCHEMA_VERSION).forGetter(Root::schemaVersion),
                Entry.CODEC.listOf().optionalFieldOf("installations", List.of()).forGetter(Root::installations)
        ).apply(inst, Root::new));
        return root.xmap(SecurityInstallations::fromData, SecurityInstallations::toData);
    }

    private synchronized Root toData() {
        List<Entry> rows = new ArrayList<>();
        for (Installation i : byId.values()) {
            List<String> owners = new ArrayList<>();
            i.rawOwners().forEach(owner -> owners.add(owner.toString()));
            List<MemberRow> members = new ArrayList<>();
            i.rawMembers().forEach((player, level) -> members.add(new MemberRow(player.toString(), level.jsonName())));
            List<DeviceRow> devices = new ArrayList<>();
            i.rawDevices().forEach((pos, block) -> devices.add(new DeviceRow(pos.dimension().toString(),
                    pos.pos().getX(), pos.pos().getY(), pos.pos().getZ(), block.toString())));
            Lockdown lockdown = i.lockdown();
            List<CardRow> cards = new ArrayList<>();
            i.rawCards().forEach((serial, card) -> cards.add(new CardRow(serial, card.level().jsonName(),
                    card.encrypted(), Optional.ofNullable(card.boundOwner()).map(UUID::toString), card.issuedAt())));
            AccessLevel automation = i.automationLevel();
            LedgerRow ledger = new LedgerRow(i.nextSerial(), i.minValidSerial(), cards,
                    automation == null ? "off" : automation.jsonName());
            rows.add(new Entry(new Row(i.id().toString(), i.name(), i.createdAt(), owners, members,
                    Optional.ofNullable(i.hubPos()).map(PosRow::of), devices,
                    Optional.of(LockdownRow.of(lockdown)),
                    Optional.of(Base64.getEncoder().encodeToString(i.secret())), i.isOrphaned(),
                    Optional.ofNullable(i.boundFaction()).map(Identifier::toString), i.factionMaxLevel().jsonName(),
                    Optional.ofNullable(i.boundStation()), i.notifyOfficers(),
                    Optional.ofNullable(i.auditRetentionDaysOverride()), List.of()), ledger,
                    new BindingRow(Optional.ofNullable(i.boundColony()).map(UUID::toString),
                            i.colonyMaxLevel().jsonName()),
                    i.lastAlarmLockdownEndedAt(), i.alarmSilencedUntil()));
        }
        return new Root(SCHEMA_VERSION, rows);
    }

    /**
     * Rebuilds the store and its indexes from disk, dropping anything that breaks an invariant: malformed
     * ids, rows with no owner, member rows at PUBLIC/OWNER or for an owner, a device on the hub, and a
     * position already claimed by an earlier installation (first wins). Warnings carry counts only.
     */
    private static SecurityInstallations fromData(Root root) {
        SecurityInstallations store = new SecurityInstallations();
        if (root.schemaVersion() > SCHEMA_VERSION) {
            NeroSecurityCommon.LOGGER.warn("[NeroSecurity] installations store has schema version {} (this build reads {}); "
                    + "loading what is understood.", root.schemaVersion(), SCHEMA_VERSION);
        }
        int droppedRows = 0;
        int droppedEntries = 0;
        int regeneratedSecrets = 0;
        int ledgerCap = ledgerCapForLoad();
        for (Entry entry : root.installations()) {
            Row row = entry.row();
            UUID id = parseUuid(row.id());
            if (id == null || store.byId.containsKey(id)) {
                droppedRows++;
                continue;
            }
            List<UUID> owners = new ArrayList<>();
            for (String raw : row.owners()) {
                UUID owner = parseUuid(raw);
                if (owner != null && !owners.contains(owner)) {
                    owners.add(owner);
                } else {
                    droppedEntries++;
                }
            }
            if (owners.isEmpty()) {
                droppedRows++;
                continue;
            }
            byte[] secret = decodeSecret(row.secret());
            if (secret == null) {
                regeneratedSecrets++;
                secret = Installation.newSecret();
            }
            Installation installation = new Installation(id, owners.get(0), row.name(), row.createdAt(), secret);
            for (int n = 1; n < owners.size(); n++) {
                installation.addOwnerInternal(owners.get(n));
            }
            for (MemberRow member : row.members()) {
                UUID player = parseUuid(member.player());
                Optional<AccessLevel> level = AccessLevel.byName(member.level());
                if (player == null || level.isEmpty() || level.get() == AccessLevel.PUBLIC
                        || level.get() == AccessLevel.OWNER || owners.contains(player)
                        || Installation.TOMBSTONE_OWNER.equals(player)) {
                    droppedEntries++;
                    continue;
                }
                installation.rawMembers().put(player, level.get());
            }
            DevicePos hub = row.hub().map(PosRow::toPos).orElse(null);
            if (hub != null && store.deviceIndex.containsKey(hub)) {
                droppedEntries++;
                hub = null;
            }
            installation.setHubPos(hub);
            installation.setOrphaned(row.orphaned() || hub == null);
            for (DeviceRow device : row.devices()) {
                Identifier dim = Identifier.tryParse(device.dim());
                Identifier block = Identifier.tryParse(device.block());
                if (dim == null || block == null) {
                    droppedEntries++;
                    continue;
                }
                DevicePos pos = new DevicePos(dim, new BlockPos(device.x(), device.y(), device.z()));
                if (pos.equals(hub) || store.deviceIndex.containsKey(pos)
                        || installation.rawDevices().containsKey(pos)) {
                    droppedEntries++;
                    continue;
                }
                installation.rawDevices().put(pos, block);
                store.deviceIndex.put(pos, id);
            }
            row.lockdown().ifPresent(l -> installation.setLockdown(l.toLockdown()));
            installation.setLastAlarmLockdownEndedAt(entry.lastAlarmLockdownEndedAt());
            installation.setAlarmSilencedUntil(entry.alarmSilencedUntil());
            row.boundFaction().map(Identifier::tryParse).ifPresent(installation::setBoundFaction);
            installation.setFactionMaxLevel(AccessLevel.byName(row.factionMaxLevel())
                    .filter(level -> level == AccessLevel.MEMBER || level == AccessLevel.OFFICER)
                    .orElse(AccessLevel.OFFICER));
            row.boundStation().ifPresent(installation::setBoundStation);
            entry.bindings().boundColony().map(SecurityInstallations::parseUuid).ifPresent(installation::setBoundColony);
            installation.setColonyMaxLevel(AccessLevel.byName(entry.bindings().colonyMaxLevel())
                    .filter(SecurityInstallations::isBindingMaxLevel)
                    .orElse(AccessLevel.OFFICER));
            installation.setNotifyOfficers(row.notifyOfficers());
            row.auditRetentionDays().filter(days -> days >= 0).ifPresent(installation::setAuditRetentionDaysOverride);
            droppedEntries += loadLedger(installation, entry.ledger(), ledgerCap);
            store.byId.put(id, installation);
            store.index(installation);
            store.trackAutoRelease(installation);
        }
        if (droppedRows > 0 || droppedEntries > 0) {
            NeroSecurityCommon.LOGGER.warn("[NeroSecurity] installations store: dropped {} invalid installation(s) "
                    + "and {} invalid entr(ies) on load.", droppedRows, droppedEntries);
        }
        if (regeneratedSecrets > 0) {
            NeroSecurityCommon.LOGGER.warn("[NeroSecurity] installations store: regenerated {} missing or invalid "
                    + "signing secret(s); cards signed with the old secret will stop working.", regeneratedSecrets);
        }
        return store;
    }

    /**
     * Restores the card ledger and automation policy. Serial counters are clamped to at least 1 and
     * the next serial to at least the floor (so a serial can never be issued twice). A row is dropped
     * when its serial is at or above the next serial, below the floor, or a duplicate (first wins);
     * when its level is unknown; or when its bound owner is malformed or the tombstone. An unknown
     * automation level reads as off. Finally the ledger is clamped to {@code cap} rows (see
     * {@link #clampLedger}).
     *
     * @return how many rows were dropped
     */
    private static int loadLedger(Installation installation, LedgerRow ledger, int cap) {
        long floor = Math.max(1L, ledger.minValidSerial());
        long next = Math.max(floor, Math.max(1L, ledger.nextSerial()));
        installation.setMinValidSerial(floor);
        installation.setNextSerial(next);
        int dropped = 0;
        for (CardRow card : ledger.cards()) {
            Optional<AccessLevel> level = AccessLevel.byName(card.level());
            UUID bound = null;
            boolean badOwner = false;
            if (card.boundOwner().isPresent()) {
                bound = parseUuid(card.boundOwner().get());
                badOwner = bound == null || Installation.TOMBSTONE_OWNER.equals(bound);
            }
            if (card.serial() >= next || card.serial() < floor || level.isEmpty() || badOwner
                    || installation.rawCards().containsKey(card.serial())) {
                dropped++;
                continue;
            }
            installation.rawCards().put(card.serial(), new CardRecord(level.get(), card.encrypted(), bound,
                    card.issuedAt()));
        }
        dropped += clampLedger(installation.rawCards(), cap);
        AccessLevel automation = AccessLevel.byName(ledger.automationLevel())
                .filter(SecurityInstallations::isAutomationLevel).orElse(null);
        installation.setAutomationLevelInternal(automation);
        return dropped;
    }

    /**
     * Refuses ledger rows beyond {@code cap} on load, keeping the lowest serials and dropping the rest.
     * Dropping a row only ever kills a card: a revoked card has no row, and the serial floor and next
     * serial are untouched, so nothing revoked can come back and no serial is issued twice.
     *
     * @return how many rows were dropped
     */
    static int clampLedger(Map<Long, CardRecord> cards, int cap) {
        int limit = Math.max(0, cap);
        if (cards.size() <= limit) {
            return 0;
        }
        TreeSet<Long> serials = new TreeSet<>(cards.keySet());
        int dropped = 0;
        while (serials.size() > limit) {
            cards.remove(serials.pollLast());
            dropped++;
        }
        return dropped;
    }

    /** The ledger cap to clamp to on load; the largest configurable cap if the config cannot be read. */
    private static int ledgerCapForLoad() {
        try {
            return NeroSecurityConfig.keycardsPerInstallation();
        } catch (RuntimeException | LinkageError unavailable) {
            return LEDGER_CAP_FALLBACK;
        }
    }

    private static @Nullable UUID parseUuid(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    /** The decoded secret, or null when missing or invalid. */
    private static byte[] decodeSecret(Optional<String> encoded) {
        if (encoded.isEmpty()) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded.get());
            return bytes.length == Installation.SECRET_BYTES ? bytes : null;
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    /** Read-only view for tests and snapshots. */
    synchronized Map<DevicePos, UUID> deviceIndexView() {
        return Collections.unmodifiableMap(new HashMap<>(deviceIndex));
    }
}
