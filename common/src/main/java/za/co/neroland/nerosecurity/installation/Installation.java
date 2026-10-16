package za.co.neroland.nerosecurity.installation;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.access.AccessLevel;

/**
 * One installation: the security domain a single Security Hub controls.
 *
 * <p>Mutable, but mutated only through {@link SecurityInstallations} on the server thread; every
 * read accessor returns a copy or an unmodifiable view. The hub block entity holds only this record's
 * id, never a copy of it.
 *
 * <h2>What it holds (POPIA/GDPR)</h2>
 * <ul>
 *   <li><b>owners</b> — player UUIDs, at least one. No names. When erasure removes the last real
 *       owner, the all-zero {@link #TOMBSTONE_OWNER} takes its place and the installation is marked
 *       orphaned; the tombstone grants nothing to anyone.</li>
 *   <li><b>members</b> — player UUID to MEMBER or OFFICER. No join times, no names, no last-seen.</li>
 *   <li><b>hub and device positions</b> — positions of the owner's own blocks, never of players.</li>
 *   <li><b>lockdown</b> — whether it is active, since when, whether a player or an alarm started it,
 *       when an alarm lockdown clears itself, a boolean for "an owner entered it" instead of who did,
 *       and when the last alarm lockdown ended (the cooldown clock).</li>
 *   <li><b>secret</b> — 32 random bytes that sign this installation's keycards. No personal content;
 *       never synced, logged or printed (this class's {@link #toString()} omits it).</li>
 *   <li><b>card ledger</b> — serial to {@link CardRecord} for every live keycard. A bound card's row
 *       holds the one player UUID allowed to present it; nothing else about a card is personal.
 *       Serials only grow ({@code nextSerial}) and a floor ({@code minValidSerial}) kills every card
 *       issued before a "revoke all".</li>
 *   <li><b>automation level</b> — how far hoppers and pipes may reach into this installation's secure
 *       chests (off, MEMBER or OFFICER). Not personal data.</li>
 * </ul>
 */
public final class Installation {

    /** The owner left behind when erasure removes the last real owner. Never a real player. */
    public static final UUID TOMBSTONE_OWNER = new UUID(0L, 0L);
    public static final int NAME_MAX = 32;
    public static final int SECRET_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UUID id;
    private String name;
    private final Set<UUID> owners = new LinkedHashSet<>();
    private final Map<UUID, AccessLevel> members = new LinkedHashMap<>();
    private @Nullable DevicePos hubPos;
    private final Map<DevicePos, Identifier> devices = new LinkedHashMap<>();
    private Lockdown lockdown = Lockdown.NONE;
    /** Epoch ms the last alarm lockdown ended (0 = never): the alarm-lockdown cooldown runs from here. */
    private long lastAlarmLockdownEndedAt;
    private final byte[] secret;
    private boolean orphaned;
    private final long createdAt;

    // Card ledger and automation policy. Mutated only by SecurityInstallations.
    private long nextSerial = 1L;
    private long minValidSerial = 1L;
    private final Map<Long, CardRecord> cards = new LinkedHashMap<>();
    private @Nullable AccessLevel automationLevel;

    // Integration bindings (organisation ids only, never a player): written only through the store's binders,
    // each capped at OFFICER. A binding whose sibling mod is absent is kept, inert.
    private @Nullable Identifier boundFaction;
    private AccessLevel factionMaxLevel = AccessLevel.OFFICER;
    private @Nullable UUID boundColony;
    private AccessLevel colonyMaxLevel = AccessLevel.OFFICER;
    private @Nullable Integer boundStation;
    private boolean notifyOfficers;
    private @Nullable Integer auditRetentionDaysOverride;

    public Installation(UUID id, UUID firstOwner, String name, long createdAt, byte[] secret) {
        this.id = id;
        this.owners.add(firstOwner);
        this.name = InstallationNames.sanitise(name, id);
        this.createdAt = createdAt;
        this.secret = secret != null && secret.length == SECRET_BYTES ? secret.clone() : newSecret();
    }

    /** A new installation with a fresh random id and secret. */
    public static Installation create(UUID firstOwner, String requestedName, long now) {
        return new Installation(UUID.randomUUID(), firstOwner, requestedName, now, newSecret());
    }

    static byte[] newSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    // --- read API ---------------------------------------------------------------------------------

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Set<UUID> owners() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(owners));
    }

    public Map<UUID, AccessLevel> members() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(members));
    }

    public @Nullable DevicePos hubPos() {
        return hubPos;
    }

    public Map<DevicePos, Identifier> devices() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(devices));
    }

    public Lockdown lockdown() {
        return lockdown;
    }

    public boolean isOrphaned() {
        return orphaned;
    }

    /** Epoch ms the last alarm lockdown ended, 0 when none ever has. Not personal data. */
    public long lastAlarmLockdownEndedAt() {
        return lastAlarmLockdownEndedAt;
    }

    public long createdAt() {
        return createdAt;
    }

    /** The stored level of a member, PUBLIC when absent. Owners are not member rows. */
    public AccessLevel memberLevel(UUID player) {
        AccessLevel level = player == null ? null : members.get(player);
        return level == null ? AccessLevel.PUBLIC : level;
    }

    public boolean isOwner(UUID player) {
        return player != null && !TOMBSTONE_OWNER.equals(player) && owners.contains(player);
    }

    public int deviceCount() {
        return devices.size();
    }

    public @Nullable Identifier boundFaction() {
        return boundFaction;
    }

    public AccessLevel factionMaxLevel() {
        return factionMaxLevel;
    }

    /** The bound NeroColonies colony id (an organisation id, not a player), or null. */
    public @Nullable UUID boundColony() {
        return boundColony;
    }

    /** The highest level the colony binding may grant: MEMBER or OFFICER. */
    public AccessLevel colonyMaxLevel() {
        return colonyMaxLevel;
    }

    public @Nullable Integer boundStation() {
        return boundStation;
    }

    public boolean notifyOfficers() {
        return notifyOfficers;
    }

    public @Nullable Integer auditRetentionDaysOverride() {
        return auditRetentionDaysOverride;
    }

    /** The next serial the ledger will issue. Never reused: it only ever increases. */
    public long nextSerial() {
        return nextSerial;
    }

    /** Cards with a serial below this are dead (raised by "revoke all"). Only ever increases. */
    public long minValidSerial() {
        return minValidSerial;
    }

    /** Every live card, in issuance order. An unmodifiable copy. */
    public Map<Long, CardRecord> cards() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(cards));
    }

    /** The ledger row for {@code serial}, or null when it was never issued or has been revoked. */
    public @Nullable CardRecord card(long serial) {
        return cards.get(serial);
    }

    public int cardCount() {
        return cards.size();
    }

    /** How far automation may reach into secure chests: null = off, else MEMBER or OFFICER. */
    public @Nullable AccessLevel automationLevel() {
        return automationLevel;
    }

    /** The opaque scope key used wherever an installation is named outside this mod. */
    public String scopeKey() {
        return "inst:" + id;
    }

    /** Package-private: only this package (the store and {@link KeycardSigner}) reads it. */
    byte[] secret() {
        return secret.clone();
    }

    @Override
    public String toString() {
        return "Installation[" + id + "]";
    }

    // --- package-private mutation (the store only) -------------------------------------------------

    void setName(String name) {
        this.name = InstallationNames.sanitise(name, id);
    }

    void addOwnerInternal(UUID owner) {
        owners.add(owner);
    }

    boolean removeOwnerInternal(UUID owner) {
        return owners.remove(owner);
    }

    boolean hasNoOwners() {
        return owners.isEmpty();
    }

    Set<UUID> rawOwners() {
        return owners;
    }

    Map<UUID, AccessLevel> rawMembers() {
        return members;
    }

    Map<DevicePos, Identifier> rawDevices() {
        return devices;
    }

    void setHubPos(@Nullable DevicePos hubPos) {
        this.hubPos = hubPos;
    }

    void setLockdown(Lockdown lockdown) {
        this.lockdown = lockdown == null ? Lockdown.NONE : lockdown;
    }

    void setLastAlarmLockdownEndedAt(long at) {
        this.lastAlarmLockdownEndedAt = Math.max(0L, at);
    }

    void setOrphaned(boolean orphaned) {
        this.orphaned = orphaned;
    }

    void setBoundFaction(@Nullable Identifier boundFaction) {
        this.boundFaction = boundFaction;
    }

    void setFactionMaxLevel(AccessLevel level) {
        this.factionMaxLevel = level == null ? AccessLevel.OFFICER : level;
    }

    void setBoundColony(@Nullable UUID boundColony) {
        this.boundColony = boundColony;
    }

    void setColonyMaxLevel(AccessLevel level) {
        this.colonyMaxLevel = level == null ? AccessLevel.OFFICER : level;
    }

    void setBoundStation(@Nullable Integer boundStation) {
        this.boundStation = boundStation;
    }

    void setNotifyOfficers(boolean notifyOfficers) {
        this.notifyOfficers = notifyOfficers;
    }

    void setAuditRetentionDaysOverride(@Nullable Integer days) {
        this.auditRetentionDaysOverride = days;
    }

    Map<Long, CardRecord> rawCards() {
        return cards;
    }

    void setNextSerial(long nextSerial) {
        this.nextSerial = nextSerial;
    }

    void setMinValidSerial(long minValidSerial) {
        this.minValidSerial = minValidSerial;
    }

    void setAutomationLevelInternal(@Nullable AccessLevel level) {
        this.automationLevel = level;
    }
}
