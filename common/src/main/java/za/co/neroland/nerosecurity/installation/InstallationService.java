package za.co.neroland.nerosecurity.installation;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.access.AccessAction;
import za.co.neroland.nerosecurity.access.AccessDecision;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.access.AccessQuery;
import za.co.neroland.nerosecurity.access.AccessResolver;
import za.co.neroland.nerosecurity.access.MemberEditRule;
import za.co.neroland.nerosecurity.audit.SecurityAuditLog;
import za.co.neroland.nerosecurity.block.DeviceIds;
import za.co.neroland.nerosecurity.block.field.FieldPassability;
import za.co.neroland.nerosecurity.block.SecurityDevice;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.event.SecurityEvent;
import za.co.neroland.nerosecurity.event.SecurityEventBus;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.event.SecuritySeverity;
import za.co.neroland.nerosecurity.item.SelectedHub;
import za.co.neroland.nerosecurity.lockdown.LockdownHooks;
import za.co.neroland.nerosecurity.lockdown.LockdownRules;
import za.co.neroland.nerosecurity.notice.InstallationAreas;
import za.co.neroland.nerosecurity.progression.SecurityMilestones;
import za.co.neroland.nerosecurity.registry.NeroSecurityDataComponents;

/**
 * Every server-side installation use case, in one place. Each method resolves through
 * {@link AccessResolver#DEFAULT}, mutates {@link SecurityInstallations}, publishes exactly its events on
 * {@link SecurityEventBus} after the store write, and returns a {@link ServiceResult} whose message is
 * safe to show the actor. Nothing else mutates the store.
 *
 * <p>Server thread only.
 */
public final class InstallationService {

    /** The result of registering a new hub: the message plus the new installation on success. */
    public record CreateOutcome(ServiceResult result, @Nullable Installation installation) {
    }

    private InstallationService() {
    }

    // --- lifecycle ------------------------------------------------------------------------------

    /** A hub was placed: register a new installation owned by the placer. */
    public static CreateOutcome createForHub(ServerPlayer placer, ServerLevel level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        int max = NeroSecurityConfig.maxInstallationsPerPlayer();
        SecurityInstallations.CreateResult created = SecurityInstallations.get(server)
                .create(placer.getUUID(), "", DevicePos.of(level, pos), now(), max);
        return switch (created.status()) {
            case OK -> {
                Installation installation = created.installation();
                AccessLevel automation = NeroSecurityConfig.chestAutomationDefault();
                if (automation != null) {
                    SecurityInstallations.get(server).setAutomationLevel(installation.id(), automation);
                }
                InstallationAreas.invalidate(installation.id());
                publish(SecurityEventType.INSTALLATION_CREATED, installation.id(), installation.hubPos(), placer.getUUID());
                yield new CreateOutcome(ServiceResult.success(
                        Component.translatable("hub.nerosecurity.created", installation.name())), installation);
            }
            case LIMIT_REACHED -> new CreateOutcome(ServiceResult.failure(
                    Component.translatable("hub.nerosecurity.limit", max)), null);
            case POSITION_TAKEN -> new CreateOutcome(ServiceResult.failure(
                    Component.translatable("hub.nerosecurity.position_taken")), null);
        };
    }

    /** An owner (or operator) broke the hub: the installation is dissolved. */
    public static ServiceResult dissolveByBreak(ServerPlayer actor, Installation installation) {
        AccessDecision decision = resolve(actor, installation, AccessAction.DISSOLVE);
        if (!decision.allowed()) {
            return ServiceResult.failure(decision.message());
        }
        DevicePos hub = installation.hubPos();
        if (!store(actor).dissolve(installation.id())) {
            return ServiceResult.failure(Component.translatable("tool.nerosecurity.hub_gone"));
        }
        afterDissolve(actor.level().getServer(), installation.id());
        publish(SecurityEventType.INSTALLATION_DISSOLVED, installation.id(), hub, actor.getUUID());
        return ServiceResult.success(Component.translatable("hub.nerosecurity.dissolved"));
    }

    /** The hub disappeared without a player break (explosion, command, another mod): orphan it. */
    public static void orphanByLoss(MinecraftServer server, Installation installation) {
        DevicePos hub = installation.hubPos();
        if (SecurityInstallations.get(server).orphan(installation.id())) {
            InstallationAreas.invalidate(installation.id());
            publish(SecurityEventType.INSTALLATION_ORPHANED, installation.id(), hub, null);
        }
    }

    /**
     * A linked device disappeared without a player unlinking it (broken, blown up, replaced by a
     * command): drop it from the installation so no ghost entry squats the position. Publishes
     * {@code DEVICE_UNLINKED} with no actor. Devices call this from their own removal hook.
     *
     * @return whether a link was removed
     */
    public static boolean unlinkByLoss(MinecraftServer server, Installation installation, DevicePos devicePos) {
        if (server == null || installation == null || devicePos == null) {
            return false;
        }
        if (!SecurityInstallations.get(server).unlink(installation.id(), devicePos)) {
            return false;
        }
        InstallationAreas.invalidate(installation.id());
        publish(SecurityEventType.DEVICE_UNLINKED, installation.id(), devicePos, null);
        return true;
    }

    // --- members --------------------------------------------------------------------------------

    /** Adds, changes or (with PUBLIC) removes a member row, subject to the member edit rule. */
    public static ServiceResult setMember(ServerPlayer actor, Installation installation, UUID target,
            AccessLevel newLevel) {
        AccessDecision decision = resolve(actor, installation, AccessAction.EDIT_MEMBERS);
        if (!decision.allowed()) {
            return ServiceResult.failure(decision.message());
        }
        if (target == null || newLevel == null) {
            return ServiceResult.failure(Component.translatable("hub.nerosecurity.member.unknown_player"));
        }
        if (target.equals(actor.getUUID())) {
            return ServiceResult.failure(Component.translatable("hub.nerosecurity.member.self"));
        }
        if (installation.owners().contains(target)) {
            return ServiceResult.failure(Component.translatable("hub.nerosecurity.member.is_owner"));
        }
        AccessLevel current = installation.memberLevel(target);
        if (!canEdit(decision.effective(), current, newLevel)) {
            return ServiceResult.failure(Component.translatable("hub.nerosecurity.member.rank_too_high"));
        }
        int max = NeroSecurityConfig.maxMembersPerInstallation();
        SecurityInstallations.MemberResult result = store(actor).setMember(installation.id(), target, newLevel, max);
        FieldPassability.bump();
        MinecraftServer server = actor.level().getServer();
        String name = PlayerNames.display(server, target);
        return switch (result) {
            case ADDED -> {
                publish(SecurityEventType.MEMBER_ADDED, installation.id(), null, actor.getUUID());
                yield ServiceResult.success(Component.translatable("hub.nerosecurity.member.added", name,
                        newLevel.displayName()));
            }
            case CHANGED -> {
                publish(SecurityEventType.LEVEL_CHANGED, installation.id(), null, actor.getUUID());
                yield ServiceResult.success(Component.translatable("hub.nerosecurity.member.changed", name,
                        newLevel.displayName()));
            }
            case REMOVED -> {
                publish(SecurityEventType.MEMBER_REMOVED, installation.id(), null, actor.getUUID());
                yield ServiceResult.success(Component.translatable("hub.nerosecurity.member.removed", name));
            }
            case UNCHANGED -> ServiceResult.success(Component.translatable("hub.nerosecurity.member.unchanged", name));
            case LIMIT_REACHED -> ServiceResult.failure(Component.translatable("hub.nerosecurity.member.limit", max));
            case IS_OWNER -> ServiceResult.failure(Component.translatable("hub.nerosecurity.member.is_owner"));
            case NOT_A_MEMBER_LEVEL -> ServiceResult.failure(
                    Component.translatable("hub.nerosecurity.member.rank_too_high"));
            case NO_INSTALLATION -> ServiceResult.failure(Component.translatable("tool.nerosecurity.hub_gone"));
        };
    }

    /**
     * Whether {@code target} already has a member row on {@code installation}. The hub's level-change
     * payload may only edit such rows; new members are added by name, never by a client-chosen id.
     */
    public static boolean isExistingMemberRow(Installation installation, UUID target) {
        return installation != null && target != null && installation.members().containsKey(target);
    }

    /**
     * The member edit rule (delegates to {@link MemberEditRule}): the actor may touch only a player
     * strictly below them, only to a level strictly below them, never an owner and never to OWNER.
     */
    public static boolean canEdit(AccessLevel actor, AccessLevel targetCurrent, AccessLevel targetNew) {
        return MemberEditRule.canEdit(actor, targetCurrent, targetNew);
    }

    public static ServiceResult rename(ServerPlayer actor, Installation installation, String name) {
        AccessDecision decision = resolve(actor, installation, AccessAction.RENAME);
        if (!decision.allowed()) {
            return ServiceResult.failure(decision.message());
        }
        if (!store(actor).rename(installation.id(), name)) {
            return ServiceResult.failure(Component.translatable("tool.nerosecurity.hub_gone"));
        }
        return ServiceResult.success(Component.translatable("hub.nerosecurity.renamed"));
    }

    /**
     * Whether officers also receive this installation's alarm chat ({@code SET_NOTIFY_OFFICERS} at OWNER,
     * resolved now). Audited as {@code NOTIFY_OFFICERS_CHANGED} when it changes.
     */
    public static ServiceResult setNotifyOfficers(ServerPlayer actor, Installation installation, boolean on) {
        AccessDecision decision = resolve(actor, installation, AccessAction.SET_NOTIFY_OFFICERS);
        if (!decision.allowed()) {
            return ServiceResult.failure(decision.message());
        }
        boolean changed = installation.notifyOfficers() != on;
        if (!store(actor).setNotifyOfficers(installation.id(), on)) {
            return ServiceResult.failure(Component.translatable("tool.nerosecurity.hub_gone"));
        }
        if (changed) {
            publish(SecurityEventType.NOTIFY_OFFICERS_CHANGED, installation.id(), null, actor.getUUID());
        }
        return ServiceResult.success(Component.translatable(on ? "command.nerosecurity.notify_officers.on"
                : "command.nerosecurity.notify_officers.off"));
    }

    // --- linking --------------------------------------------------------------------------------

    /** Points a linking tool at an installation. */
    public static ServiceResult selectHub(ServerPlayer actor, ItemStack tool, Installation installation) {
        AccessDecision decision = resolve(actor, installation, AccessAction.LINK_DEVICE);
        if (!decision.allowed()) {
            return ServiceResult.failure(decision.message());
        }
        tool.set(NeroSecurityDataComponents.SELECTED_HUB.get(), new SelectedHub(installation.id(), installation.name()));
        return ServiceResult.success(Component.translatable("tool.nerosecurity.selected", installation.name()));
    }

    /** Whether the block at {@code state} is a NeroSecurity device (class and tag must both agree). */
    public static boolean isDevice(BlockState state) {
        return state.getBlock() instanceof SecurityDevice && state.is(SecurityDevice.DEVICES);
    }

    /**
     * The store position for whatever was clicked at {@code clicked}: a device's controller part for a
     * multi-block device, otherwise the clicked position itself.
     */
    public static DevicePos devicePosOf(ServerLevel level, BlockPos clicked) {
        BlockState state = level.getBlockState(clicked);
        if (state.getBlock() instanceof SecurityDevice device) {
            BlockPos controller = device.devicePos(level, clicked, state);
            return DevicePos.of(level, controller == null ? clicked : controller);
        }
        return DevicePos.of(level, clicked);
    }

    public static ServiceResult linkDevice(ServerPlayer actor, Installation installation, ServerLevel level,
            BlockPos clicked) {
        DevicePos device = devicePosOf(level, clicked);
        BlockPos pos = device.pos();
        AccessDecision decision = AccessResolver.DEFAULT.resolve(new AccessQuery(level.getServer(), actor.getUUID(),
                installation, AccessAction.LINK_DEVICE, device), AccessAction.LINK_DEVICE.defaultMinimum);
        if (!decision.allowed()) {
            return ServiceResult.failure(decision.message());
        }
        BlockState state = level.getBlockState(pos);
        if (!isDevice(state)) {
            return ServiceResult.failure(Component.translatable("tool.nerosecurity.not_a_device"));
        }
        DevicePos hub = installation.hubPos();
        if (hub == null) {
            return ServiceResult.failure(Component.translatable("tool.nerosecurity.hub_gone"));
        }
        int maxDistance = NeroSecurityConfig.maxDeviceDistance();
        if (!device.withinManhattan(hub, maxDistance)) {
            return ServiceResult.failure(Component.translatable("tool.nerosecurity.too_far", maxDistance));
        }
        int before = installation.deviceCount();
        int maxDevices = NeroSecurityConfig.maxDevicesPerInstallation();
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        SecurityInstallations.LinkResult result = store(actor).link(installation.id(), device, blockId, maxDevices);
        return switch (result) {
            case OK -> {
                InstallationAreas.invalidate(installation.id());
                publish(SecurityEventType.DEVICE_LINKED, installation.id(), device, actor.getUUID());
                if (state.getBlock() instanceof SecurityDevice securityDevice) {
                    securityDevice.onLinked(level, pos, installation.id());
                }
                if (before == 0) {
                    MinecraftServer server = level.getServer();
                    for (UUID owner : installation.owners()) {
                        if (!Installation.TOMBSTONE_OWNER.equals(owner)) {
                            SecurityMilestones.record(server, owner, SecurityMilestones.Milestone.INSTALLATION_ONLINE);
                        }
                    }
                }
                yield ServiceResult.success(Component.translatable("tool.nerosecurity.linked", installation.name()));
            }
            case ALREADY_LINKED_HERE -> ServiceResult.failure(Component.translatable("tool.nerosecurity.already_linked"));
            case LINKED_ELSEWHERE -> ServiceResult.failure(Component.translatable("tool.nerosecurity.linked_elsewhere"));
            case IS_HUB -> ServiceResult.failure(Component.translatable("tool.nerosecurity.not_a_device"));
            case LIMIT_REACHED -> ServiceResult.failure(Component.translatable("tool.nerosecurity.device_limit", maxDevices));
            case NO_INSTALLATION -> ServiceResult.failure(Component.translatable("tool.nerosecurity.hub_gone"));
        };
    }

    public static ServiceResult unlinkDevice(ServerPlayer actor, Installation installation, DevicePos device) {
        AccessDecision decision = AccessResolver.DEFAULT.resolve(new AccessQuery(actor.level().getServer(),
                actor.getUUID(), installation, AccessAction.LINK_DEVICE, device), AccessAction.LINK_DEVICE.defaultMinimum);
        if (!decision.allowed()) {
            return ServiceResult.failure(decision.message());
        }
        if (!store(actor).unlink(installation.id(), device)) {
            return ServiceResult.failure(Component.translatable("tool.nerosecurity.not_a_device"));
        }
        InstallationAreas.invalidate(installation.id());
        publish(SecurityEventType.DEVICE_UNLINKED, installation.id(), device, actor.getUUID());
        if (actor.level() instanceof ServerLevel level && level.dimension().identifier().equals(device.dimension())
                && level.getBlockState(device.pos()).getBlock() instanceof SecurityDevice securityDevice) {
            securityDevice.onUnlinked(level, device.pos(), installation.id());
        }
        return ServiceResult.success(Component.translatable("tool.nerosecurity.unlinked", installation.name()));
    }

    // --- lockdown -------------------------------------------------------------------------------

    /**
     * Hub button / {@code lockdown on}. From NORMAL: a manual lockdown ({@code LOCKDOWN_TOGGLE} at
     * OFFICER). During an alarm lockdown the resolver's lockdown rule already demands OWNER, and the call
     * <em>confirms</em> it instead: it becomes manual and no longer clears itself ({@code LOCKDOWN_CONFIRMED}).
     */
    public static ServiceResult enterLockdown(ServerPlayer actor, Installation installation) {
        AccessDecision decision = resolve(actor, installation, AccessAction.LOCKDOWN_TOGGLE);
        if (!decision.allowed()) {
            return ServiceResult.failure(decision.message());
        }
        if (!NeroSecurityConfig.isLockdownEnabled()) {
            return ServiceResult.failure(Component.translatable("hub.nerosecurity.lockdown.disabled"));
        }
        Lockdown current = installation.lockdown();
        boolean byOwner = installation.isOwner(actor.getUUID());
        if (current.isAlarm()) {
            store(actor).setLockdown(installation.id(), current.confirmed(byOwner));
            publish(SecurityEventType.LOCKDOWN_CONFIRMED, installation.id(), null, actor.getUUID());
            return ServiceResult.success(Component.translatable("hub.nerosecurity.lockdown.confirmed"));
        }
        if (current.active()) {
            return ServiceResult.failure(Component.translatable("hub.nerosecurity.lockdown.already"));
        }
        store(actor).setLockdown(installation.id(), Lockdown.manual(now(), byOwner));
        publish(SecurityEventType.LOCKDOWN_ENTERED, installation.id(), null, actor.getUUID());
        applyLockdownEntered(actor.level().getServer(), installation);
        return ServiceResult.success(Component.translatable("hub.nerosecurity.lockdown.entered"));
    }

    /**
     * Hub button / {@code lockdown off}: OWNER or operator only while a lockdown is in force (the
     * resolver's lockdown rule). Leaving an alarm lockdown starts its cooldown and never counts as a drill.
     */
    public static ServiceResult exitLockdown(ServerPlayer actor, Installation installation) {
        AccessDecision decision = resolve(actor, installation, AccessAction.LOCKDOWN_TOGGLE);
        if (!decision.allowed()) {
            return ServiceResult.failure(decision.message());
        }
        if (!NeroSecurityConfig.isLockdownEnabled()) {
            return ServiceResult.failure(Component.translatable("hub.nerosecurity.lockdown.disabled"));
        }
        Lockdown previous = installation.lockdown();
        if (!previous.active()) {
            return ServiceResult.failure(Component.translatable("hub.nerosecurity.lockdown.already"));
        }
        long now = now();
        SecurityInstallations store = store(actor);
        store.setLockdown(installation.id(), Lockdown.NONE);
        if (previous.isAlarm()) {
            store.setLastAlarmLockdownEndedAt(installation.id(), now);
        }
        publish(SecurityEventType.LOCKDOWN_EXITED, installation.id(), null, actor.getUUID());
        applyLockdownExited(actor.level().getServer(), installation);
        if (LockdownRules.countsForDrill(previous, installation.isOwner(actor.getUUID()))) {
            SecurityMilestones.record(actor, SecurityMilestones.Milestone.LOCKDOWN_DRILL);
        }
        return ServiceResult.success(Component.translatable("hub.nerosecurity.lockdown.exited"));
    }

    /** Per installation: the {@code lastAlarmLockdownEndedAt} a suppression was already reported for. */
    private static final Map<UUID, Long> SUPPRESSION_REPORTED = new ConcurrentHashMap<>();

    /**
     * An alarm with the lockdown response fired. Applies every guard of the transition table: lockdown
     * and alarm lockdown enabled on the server, severity at least {@code alarmLockdownMinSeverity}, no
     * lockdown in force (a running one is never extended), the cooldown elapsed, and {@code alarmPos} is
     * an alarm linked to this installation. No resolver: there is no actor. Inside the cooldown it
     * publishes {@code ALARM_LOCKDOWN_SUPPRESSED} once per cooldown window instead.
     *
     * @return true iff an alarm lockdown was entered
     */
    public static boolean enterLockdownByAlarm(MinecraftServer server, Installation installation, DevicePos alarmPos,
            SecuritySeverity severity, long now) {
        if (server == null || installation == null || alarmPos == null) {
            return false;
        }
        SecurityInstallations store = SecurityInstallations.get(server);
        Installation current = store.byId(installation.id()).orElse(null);
        if (current == null || !DeviceIds.ALARM.equals(current.devices().get(alarmPos))) {
            return false;
        }
        long cooldownMs = NeroSecurityConfig.alarmLockdownCooldownSeconds() * 1000L;
        LockdownRules.Verdict verdict = LockdownRules.alarmEntry(current.lockdown(), current.lastAlarmLockdownEndedAt(),
                now, severity, NeroSecurityConfig.alarmLockdownMinSeverity(), NeroSecurityConfig.isLockdownEnabled(),
                NeroSecurityConfig.isAlarmLockdownEnabled(), cooldownMs);
        switch (verdict) {
            case ENTER -> {
                long autoReleaseAt = now + NeroSecurityConfig.alarmLockdownAutoReleaseSeconds() * 1000L;
                store.setLockdown(current.id(), Lockdown.alarm(now, autoReleaseAt));
                SecurityEventBus.publish(SecurityEvent.of(SecurityEventType.LOCKDOWN_ENTERED, current.id(), alarmPos,
                        null, now));
                applyLockdownEntered(server, current);
                return true;
            }
            case COOLDOWN -> {
                Long reported = SUPPRESSION_REPORTED.put(current.id(), current.lastAlarmLockdownEndedAt());
                if (reported == null || reported != current.lastAlarmLockdownEndedAt()) {
                    SecurityEventBus.publish(SecurityEvent.of(SecurityEventType.ALARM_LOCKDOWN_SUPPRESSED, current.id(),
                            alarmPos, null, now));
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    /** The scheduler found {@code installation}'s alarm lockdown due: end it and start the cooldown. */
    public static void autoRelease(MinecraftServer server, Installation installation, long now) {
        if (server == null || installation == null || !NeroSecurityConfig.isLockdownEnabled()) {
            return;
        }
        SecurityInstallations store = SecurityInstallations.get(server);
        Installation current = store.byId(installation.id()).orElse(null);
        if (current == null || !LockdownRules.dueForAutoRelease(current.lockdown(), now)) {
            return;
        }
        store.setLockdown(current.id(), Lockdown.NONE);
        store.setLastAlarmLockdownEndedAt(current.id(), now);
        SecurityEventBus.publish(SecurityEvent.of(SecurityEventType.LOCKDOWN_AUTO_RELEASED, current.id(), null, null, now));
        applyLockdownExited(server, current);
    }

    /**
     * Side effects of entering a lockdown (any source), after the store write and the event: the
     * {@link LockdownHooks} (alarms re-arm, fields raise their strength). Doors seal on their own next tick.
     */
    private static void applyLockdownEntered(MinecraftServer server, Installation installation) {
        FieldPassability.bump();
        LockdownHooks.fire(server, installation, true);
    }

    private static void applyLockdownExited(MinecraftServer server, Installation installation) {
        FieldPassability.bump();
        LockdownHooks.fire(server, installation, false);
    }

    // --- remote lockdown (the companion app; link/SecurityLinkActions) ---------------------------
    //
    // The link action handler has already applied every guard (config switches, stored ownership,
    // the resolver with no operator override, rate limit, idempotency) before calling these. They do
    // the store write and publish the event with the requesting player as actor, exactly like the hub,
    // but never record a drill milestone. {@code server} may be null (plain-JVM tests): the store and
    // the event still happen, the world side effects (lockdown hooks) do not.

    /** NORMAL to a manual lockdown entered by an owner. Returns the new state, or null if nothing changed. */
    public static @Nullable Lockdown enterLockdownRemote(@Nullable MinecraftServer server, SecurityInstallations store,
            UUID actor, Installation installation, long now) {
        Installation current = store.byId(installation.id()).orElse(null);
        if (current == null || current.lockdown().active()) {
            return null;
        }
        Lockdown next = Lockdown.manual(now, true);
        store.setLockdown(current.id(), next);
        SecurityEventBus.publish(SecurityEvent.of(SecurityEventType.LOCKDOWN_ENTERED, current.id(), null, actor, now));
        if (server != null) {
            applyLockdownEntered(server, current);
        }
        return next;
    }

    /** An active alarm lockdown becomes manual (no auto-release). Returns the new state, or null. */
    public static @Nullable Lockdown confirmLockdownRemote(@Nullable MinecraftServer server, SecurityInstallations store,
            UUID actor, Installation installation, long now) {
        Installation current = store.byId(installation.id()).orElse(null);
        if (current == null || !current.lockdown().isAlarm()) {
            return null;
        }
        Lockdown next = current.lockdown().confirmed(true);
        store.setLockdown(current.id(), next);
        SecurityEventBus.publish(SecurityEvent.of(SecurityEventType.LOCKDOWN_CONFIRMED, current.id(), null, actor, now));
        return next;
    }

    /** Any active lockdown ends; leaving an alarm lockdown starts its cooldown. Returns NONE, or null. */
    public static @Nullable Lockdown exitLockdownRemote(@Nullable MinecraftServer server, SecurityInstallations store,
            UUID actor, Installation installation, long now) {
        Installation current = store.byId(installation.id()).orElse(null);
        if (current == null || !current.lockdown().active()) {
            return null;
        }
        boolean wasAlarm = current.lockdown().isAlarm();
        store.setLockdown(current.id(), Lockdown.NONE);
        if (wasAlarm) {
            store.setLastAlarmLockdownEndedAt(current.id(), now);
        }
        SecurityEventBus.publish(SecurityEvent.of(SecurityEventType.LOCKDOWN_EXITED, current.id(), null, actor, now));
        if (server != null) {
            applyLockdownExited(server, current);
        }
        return Lockdown.NONE;
    }

    // --- operator commands ----------------------------------------------------------------------

    public static ServiceResult adminUnlock(CommandSourceStack source, Installation installation) {
        AccessDecision decision = AccessResolver.DEFAULT.resolve(source, installation, AccessAction.ADMIN_UNLOCK);
        if (!decision.allowed()) {
            return ServiceResult.failure(decision.message());
        }
        if (!installation.lockdown().active()) {
            return ServiceResult.failure(Component.translatable("hub.nerosecurity.lockdown.already"));
        }
        boolean wasAlarm = installation.lockdown().isAlarm();
        SecurityInstallations store = SecurityInstallations.get(source.getServer());
        store.setLockdown(installation.id(), Lockdown.NONE);
        if (wasAlarm) {
            store.setLastAlarmLockdownEndedAt(installation.id(), now());
        }
        ServerPlayer player = source.getPlayer();
        publish(SecurityEventType.ADMIN_UNLOCK, installation.id(), null, player == null ? null : player.getUUID());
        applyLockdownExited(source.getServer(), installation);
        return ServiceResult.success(Component.translatable("command.nerosecurity.admin.unlocked",
                InstallationNames.shortId(installation.id())));
    }

    public static ServiceResult adminDissolve(CommandSourceStack source, Installation installation) {
        AccessDecision decision = AccessResolver.DEFAULT.resolve(source, installation, AccessAction.ADMIN_UNLOCK);
        if (!decision.allowed() || !decision.admin()) {
            return ServiceResult.failure(decision.message());
        }
        if (!installation.isOrphaned()) {
            return ServiceResult.failure(Component.translatable("command.nerosecurity.admin.not_orphaned"));
        }
        DevicePos hub = installation.hubPos();
        if (!SecurityInstallations.get(source.getServer()).dissolve(installation.id())) {
            return ServiceResult.failure(Component.translatable("command.nerosecurity.not_found"));
        }
        afterDissolve(source.getServer(), installation.id());
        ServerPlayer player = source.getPlayer();
        publish(SecurityEventType.INSTALLATION_DISSOLVED, installation.id(), hub, player == null ? null : player.getUUID());
        return ServiceResult.success(Component.translatable("command.nerosecurity.admin.dissolved",
                InstallationNames.shortId(installation.id())));
    }

    // --- helpers --------------------------------------------------------------------------------

    /** A dissolved installation's audit log goes with it; its notice area is dropped. */
    private static void afterDissolve(MinecraftServer server, UUID installationId) {
        InstallationAreas.invalidate(installationId);
        SUPPRESSION_REPORTED.remove(installationId);
        if (server == null) {
            return;
        }
        try {
            SecurityAuditLog.get(server).drop(installationId);
        } catch (RuntimeException | LinkageError e) {
            za.co.neroland.nerosecurity.NeroSecurityCommon.LOGGER.warn(
                    "[NeroSecurity] dropping a dissolved installation's audit log failed ({}).", e.getClass().getName());
        }
    }

    private static AccessDecision resolve(ServerPlayer actor, Installation installation, AccessAction action) {
        return AccessResolver.DEFAULT.resolve(actor, installation, action, claimPosition(action, installation));
    }

    /**
     * The position a service action is resolved at. Claims vote on block interactions only (the claim source
     * abstains on a null position), so only a break of the hub ({@code DISSOLVE}) or a click on it with the
     * linking tool ({@code LINK_DEVICE}) carries the hub's position. Lockdown, members, rename and the other
     * hub-level settings are commands and screen buttons: a claim over the hub's chunk never blocks them.
     */
    static @Nullable DevicePos claimPosition(AccessAction action, Installation installation) {
        return action == AccessAction.DISSOLVE || action == AccessAction.LINK_DEVICE ? installation.hubPos() : null;
    }

    private static SecurityInstallations store(ServerPlayer actor) {
        return SecurityInstallations.get(actor.level().getServer());
    }

    private static void publish(SecurityEventType type, UUID installationId, @Nullable DevicePos pos,
            @Nullable UUID actor) {
        SecurityEventBus.publish(SecurityEvent.of(type, installationId, pos, actor, now()));
    }

    private static long now() {
        return System.currentTimeMillis();
    }
}
