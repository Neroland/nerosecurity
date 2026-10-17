package za.co.neroland.nerosecurity.block.field;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.machine.AbstractMachineBlockEntity;
import za.co.neroland.nerolandcore.sideconfig.SideConfigComponent;

import za.co.neroland.nerosecurity.access.AccessAction;
import za.co.neroland.nerosecurity.access.AccessDecision;
import za.co.neroland.nerosecurity.access.AccessResolver;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.energy.InputOnlyEnergy;
import za.co.neroland.nerosecurity.event.SecurityEvent;
import za.co.neroland.nerosecurity.event.SecurityEventBus;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.field.FieldGeometry;
import za.co.neroland.nerosecurity.field.FieldPower;
import za.co.neroland.nerosecurity.field.FieldSettings;
import za.co.neroland.nerosecurity.field.FieldState;
import za.co.neroland.nerosecurity.field.FieldStateMachine;
import za.co.neroland.nerosecurity.field.FieldStrength;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.InstallationService;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.menu.field.FieldMenu;
import za.co.neroland.nerosecurity.notice.RecordingNotice;
import za.co.neroland.nerosecurity.sound.SecuritySounds;
import za.co.neroland.nerosecurity.upgrade.SecurityModule;
import za.co.neroland.nerosecurity.upgrade.SecurityModuleSlots;

/**
 * The force field emitter's block entity: field settings, four module slots (strength, range), an insert-only energy
 * buffer, the overload meter and the set of field blocks it owns.
 *
 * <p><b>Every tick</b> (server): look the installation up in the store (never cached across ticks); read lockdown
 * from it; step the pure {@link FieldStateMachine}; apply its action (collapse, restore, events); while projecting,
 * draw power for the <em>module</em> strength, cool the meter, and reconcile the blocks every
 * {@code fieldReprojectIntervalTicks} (staggered by position); every {@code fieldSyncIntervalTicks} tell nearby players
 * their own verdict ({@link FieldSync}).
 *
 * <p><b>Passability</b> comes only from {@link PassabilityCache} (the access resolver, per player, per tick). Lockdown
 * raises the effective strength to {@code maxFieldStrength} at no extra power ({@link FieldStrength}); the resolver's
 * lockdown rule is what seals the field to everyone below OWNER.
 *
 * <p><b>Overload meter.</b> A lockdown edge rescales the meter to the new capacity and never resets it
 * ({@link FieldStrength#rescaleLoad}), so cycling a lockdown cannot drain it. An overload cooldown is also recorded in
 * {@link FieldCooldowns}, which a new emitter placed at the same position adopts on its first tick, so breaking and
 * re-placing the emitter cannot cut it short. Melee load is spaced per attacker ({@link BreachCooldowns#tryMelee}).
 *
 * <p><b>Persisted:</b> settings, modules, state, load (and whether it was measured against the lockdown-boosted
 * capacity), cooldown end, owned positions (energy and side config via Core). Per-player maps (verdicts, last sync,
 * breach cooldowns) are transient, bounded, pruned and dropped on unload. Nothing here names a player. The update tag
 * carries only what the renderer draws: {@code State}, {@code DrawBucket} (0..4) and {@code Lockdown}
 * ({@link RenderSync}).
 */
public class ForceFieldEmitterBlockEntity extends AbstractMachineBlockEntity
        implements FieldPassability.Emitter, MenuProvider {

    public static final int MODULE_SLOTS = 4;
    public static final int MAX_TRANSFER = 4000;
    /** Most owned positions read back from a save (the config cap is at most 4096). */
    public static final int MAX_PROJECTED = 4096;
    /** Ticks between retries of positions an entity was standing in (the rest waits for the normal reconcile). */
    public static final int HELD_RETRY_TICKS = 5;
    /** Fewest ticks between two {@code FIELD_OBSTRUCTED} events from one emitter (five minutes). */
    public static final int OBSTRUCTED_EVENT_COOLDOWN_TICKS = 20 * 60 * 5;
    /** Consecutive short reconciles before {@code FIELD_OBSTRUCTED} is published. */
    public static final int OBSTRUCTED_AFTER_RECONCILES = 2;

    /** Why the field was taken down from outside the state machine. */
    public enum CollapseReason { REMOVED, UNLINKED }

    private static final String KEY_SETTINGS = "Settings";
    private static final String KEY_MODULES = "Modules";
    private static final String KEY_STATE = "State";
    private static final String KEY_LOAD = "Load";
    private static final String KEY_COOLDOWN = "CooldownUntil";
    private static final String KEY_PROJECTED = "Projected";
    private static final String KEY_LOAD_BOOSTED = "LoadBoosted";
    private static final String KEY_DRAW_BUCKET = "DrawBucket";
    private static final String KEY_LOCKDOWN = "Lockdown";

    private FieldSettings settings = FieldSettings.DEFAULT;
    private final SecurityModuleSlots modules = new SecurityModuleSlots(MODULE_SLOTS,
            EnumSet.of(SecurityModule.STRENGTH, SecurityModule.RANGE), this::onModulesChanged);
    private FieldState fieldState = FieldState.IDLE;
    private double load;
    private long cooldownUntil;
    private final Set<BlockPos> projected = new HashSet<>();

    // transient (server)
    private int brownoutTicks;
    private boolean lockdownBoost;
    private boolean powerLostReported;
    private boolean restorePending;
    private boolean brokenByPlayer;
    private boolean reconcileRequested = true;
    private boolean syncRequested = true;
    private long lastExplosionTick = Long.MIN_VALUE;
    private int lastExplosionId;
    private boolean cooldownAdopted;
    private int shortReconciles;
    private long lastObstructedAt = Long.MIN_VALUE;
    /** Wanted positions skipped only because an entity stood there: retried every {@value #HELD_RETRY_TICKS} ticks. */
    private final Set<BlockPos> heldByEntity = new HashSet<>();
    private final PassabilityCache verdicts = new PassabilityCache();
    private final Map<UUID, FieldSync.Sent> lastSent = new HashMap<>();
    private final BreachCooldowns breachCooldowns = new BreachCooldowns();
    // this tick's store lookup (re-read when the tick or the generation moves)
    private @Nullable Installation installation;
    private long installationTick = Long.MIN_VALUE;
    private long installationGeneration = Long.MIN_VALUE;
    // renderer sync: what clients were last sent (server) and the synced copies (client)
    private @Nullable FieldState sentState;
    private int sentBucket;
    private boolean sentLockdown;
    private long sentAt = Long.MIN_VALUE;
    private int clientDrawBucket;
    private boolean clientLockdown;

    public ForceFieldEmitterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, NeroSecurityConfig.fieldEnergyCapacity(), MAX_TRANSFER, 0, stack -> null);
        // Every face INPUT and nothing else permitted (never omit the preset); the loader capabilities never read it.
        installSideConfig(InputOnlyEnergy.sideConfig());
    }

    /** Always null: a security device's energy faces are fixed, so Core's configurator finds nothing to edit. */
    @Override
    public @Nullable SideConfigComponent sideConfig() {
        return null;
    }

    private void enforceEnergyRouting() {
        if (this.sideConfig != null && InputOnlyEnergy.enforceInputOnly(this.sideConfig.config())) {
            setChanged();
        }
    }

    // --- reads -----------------------------------------------------------------------------------

    public FieldSettings settings() {
        return settings;
    }

    public SecurityModuleSlots modules() {
        return modules;
    }

    public FieldState fieldState() {
        return fieldState;
    }

    public double load() {
        return load;
    }

    public long cooldownUntil() {
        return cooldownUntil;
    }

    public boolean lockdownBoost() {
        return lockdownBoost;
    }

    /** Renderer: the field state (server truth; the synced copy on the client). */
    public FieldState renderState() {
        return fieldState;
    }

    /** Renderer: draw quantised 0..4 against the largest possible draw ({@link RenderSync#drawBucket}). */
    public int renderDrawBucket() {
        return level != null && level.isClientSide() ? clientDrawBucket : currentDrawBucket();
    }

    /** Renderer: whether the field wears the lockdown look. */
    public boolean renderLockdown() {
        return level != null && level.isClientSide() ? clientLockdown : lockdownBoost;
    }

    private int currentDrawBucket() {
        return RenderSync.drawBucket(fieldState, drawPerTick(), FieldPower.drawPerTick(NeroSecurityConfig.maxFieldArea(),
                NeroSecurityConfig.maxFieldStrength(), NeroSecurityConfig.fieldBasePowerPerTick(),
                NeroSecurityConfig.fieldPowerPerBlockStrength()));
    }

    private void markRenderSent(long now) {
        sentState = fieldState;
        sentBucket = currentDrawBucket();
        sentLockdown = lockdownBoost;
        sentAt = now;
    }

    public long energyAmount() {
        return energy.getAmount();
    }

    public long energyCapacity() {
        return energy.getCapacity();
    }

    /** Module strength: {@code 1 + strength modules}, capped by {@code maxFieldStrength}. Power is drawn for this. */
    public int strength() {
        return FieldPower.strength(modules.count(SecurityModule.STRENGTH), NeroSecurityConfig.maxFieldStrength());
    }

    /** The strength the field runs at: {@code maxFieldStrength} during a boosted lockdown, else {@link #strength()}. */
    public int effectiveStrength() {
        return FieldStrength.effective(strength(), lockdownBoost, NeroSecurityConfig.fieldLockdownRaisesToMax(),
                NeroSecurityConfig.maxFieldStrength());
    }

    /** Chebyshev reach: {@code fieldBaseRange + 4 x range modules}, capped by {@code fieldMaxRange}. */
    public int range() {
        return FieldPower.range(modules.count(SecurityModule.RANGE), NeroSecurityConfig.fieldBaseRange(),
                NeroSecurityConfig.fieldMaxRange());
    }

    /** Geometric block count of the settings (independent of skipped positions). */
    public int area() {
        return FieldGeometry.count(settings);
    }

    /** Energy per tick while projecting, from the module strength (never the lockdown boost). */
    public int drawPerTick() {
        return FieldPower.drawPerTick(area(), strength(), NeroSecurityConfig.fieldBasePowerPerTick(),
                NeroSecurityConfig.fieldPowerPerBlockStrength());
    }

    public double loadCapacity() {
        return FieldPower.loadCapacity(NeroSecurityConfig.fieldLoadCapacityPerStrength(), effectiveStrength());
    }

    /** The meter as a fraction of its capacity, {@code 0..1}. */
    public float loadFraction() {
        double capacity = loadCapacity();
        return capacity <= 0.0D ? 0.0F : (float) Math.max(0.0D, Math.min(1.0D, load / capacity));
    }

    /** Whole seconds left on an overload cooldown at {@code gameTime}, or 0. */
    public int cooldownSeconds(long gameTime) {
        long left = cooldownUntil - gameTime;
        return left <= 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, (left + 19) / 20);
    }

    public boolean settingsValid() {
        return FieldGeometry.validate(settings, range(), NeroSecurityConfig.maxFieldArea()).isEmpty();
    }

    public Direction facing() {
        BlockState state = getBlockState();
        return state.hasProperty(ForceFieldEmitterBlock.FACING) ? state.getValue(ForceFieldEmitterBlock.FACING) : Direction.NORTH;
    }

    /** Whether this emitter currently owns a field block at {@code pos}. */
    public boolean projects(BlockPos pos) {
        return pos != null && projected.contains(pos);
    }

    public boolean holdsBlocks() {
        return !projected.isEmpty();
    }

    /** How many field blocks it currently owns. */
    public int projectedCount() {
        return projected.size();
    }

    Set<BlockPos> projectedMutable() {
        return projected;
    }

    Set<BlockPos> heldMutable() {
        return heldByEntity;
    }

    Map<UUID, FieldSync.Sent> lastSent() {
        return lastSent;
    }

    BreachCooldowns breachCooldowns() {
        return breachCooldowns;
    }

    public DevicePos devicePos(ServerLevel level) {
        return DevicePos.of(level, worldPosition);
    }

    /**
     * The installation this emitter is linked to, from the store: looked up at most once per game tick and
     * {@link FieldPassability#GENERATION}, never kept across ticks.
     */
    public @Nullable Installation installationNow(ServerLevel level) {
        long now = level.getGameTime();
        long gen = FieldPassability.GENERATION;
        if (installationTick != now || installationGeneration != gen) {
            installation = SecurityInstallations.get(level.getServer()).atDevice(devicePos(level)).orElse(null);
            installationTick = now;
            installationGeneration = gen;
        }
        return installation;
    }

    private void forgetInstallation() {
        installation = null;
        installationTick = Long.MIN_VALUE;
        installationGeneration = Long.MIN_VALUE;
    }

    /** The server verdict for {@code actor}: cached for at most one tick and one generation, else the resolver. */
    @Override
    public boolean isPassableFor(ServerLevel level, UUID actor) {
        if (level == null || actor == null || isRemoved()) {
            return false;
        }
        return verdicts.isPassable(level, this, actor);
    }

    // --- writes ----------------------------------------------------------------------------------

    /** Stores settings already authorised and validated by the payload handler; reconciles on the next tick. */
    public void setSettings(FieldSettings newSettings) {
        this.settings = newSettings == null ? FieldSettings.DEFAULT : newSettings;
        this.heldByEntity.clear();
        this.reconcileRequested = true;
        this.syncRequested = true;
        setChanged();
    }

    /** Back to the defaults: called when the emitter is linked, so nothing from a previous link carries over. */
    public void resetSettings() {
        setSettings(FieldSettings.DEFAULT);
    }

    /** Reconcile right now (after a settings change), when the field is up; otherwise on the next tick. */
    public void reconcileNow(ServerLevel level) {
        if (fieldState == FieldState.PROJECTING) {
            if (FieldProjector.reconcile(level, this, installationNow(level))) {
                setChanged();
            }
        } else {
            reconcileRequested = true;
        }
    }

    private void onModulesChanged() {
        setChanged();
        heldByEntity.clear();
        reconcileRequested = true; // range (and so validity) may have changed
        syncRequested = true;
    }

    /** Removes every loaded field block this emitter owns (removal, unlink). */
    public void collapse(CollapseReason reason) {
        if (level instanceof ServerLevel serverLevel && holdsBlocks()) {
            FieldProjector.collapse(serverLevel, this);
            setChanged();
        }
    }

    /**
     * The lockdown hook (an accelerator; the tick reads lockdown from the store anyway): drop the cached installation,
     * raise or lower the boost, repaint, and resync nearby players now.
     */
    public void onLockdownChanged(ServerLevel level, boolean active) {
        forgetInstallation();
        boolean boost = active && NeroSecurityConfig.isLockdownEnabled();
        if (boost != lockdownBoost) {
            applyLockdownBoost(level, boost);
        }
        if (holdsBlocks()) {
            FieldSync.pass(level, this);
        }
        syncRequested = false;
    }

    private void applyLockdownBoost(ServerLevel level, boolean boost) {
        double before = loadCapacity();
        lockdownBoost = boost;
        // Never reset: the meter keeps its fraction of the new capacity, so a lockdown cycle cannot drain it.
        load = FieldStrength.rescaleLoad(load, before, loadCapacity());
        FieldProjector.repaint(level, this, boost);
        syncRequested = true;
        setChanged();
    }

    /**
     * A field block of this emitter was hit. Nothing happens when fields are not breachable or the field is not up; a
     * permitted actor adds nothing; otherwise the load is added, the matching energy drained, and
     * {@code FIELD_BREACH_ATTEMPT} published (rate-limited per attacker; the event carries the emitter's position, never
     * the block's).
     *
     * @param actor       the attacker or shooter, null for an explosion or an unknown shooter
     * @param radius      the explosion radius (explosions only)
     * @param explosionId identity of the explosion (explosions only): one explosion loads an emitter once
     */
    public void absorb(LoadSource source, @Nullable UUID actor, double radius, int explosionId) {
        if (!(level instanceof ServerLevel serverLevel) || isRemoved() || fieldState != FieldState.PROJECTING
                || !NeroSecurityConfig.isForceFieldBreachable()) {
            return;
        }
        long now = serverLevel.getGameTime();
        if (source == LoadSource.EXPLOSION) {
            if (lastExplosionTick == now && lastExplosionId == explosionId) {
                return;
            }
            lastExplosionTick = now;
            lastExplosionId = explosionId;
        }
        if (actor != null && isPassableFor(serverLevel, actor)) {
            return;
        }
        if (source == LoadSource.MELEE && !breachCooldowns.tryMelee(actor, now)) {
            return; // one hit per attacker per MELEE_SPACING_TICKS: load measures swings, not packets
        }
        double added = switch (source) {
            case MELEE -> NeroSecurityConfig.fieldMeleeLoad();
            case PROJECTILE -> NeroSecurityConfig.fieldProjectileLoad();
            case EXPLOSION -> NeroSecurityConfig.fieldExplosionLoadPerRadius() * Math.max(0.0D, radius);
        };
        if (!(added > 0.0D) || Double.isInfinite(added)) {
            return;
        }
        load = Math.min(1.0E9D, load + added);
        long drain = Math.round(added * Math.max(0, NeroSecurityConfig.fieldEnergyPerLoad()));
        if (drain > 0) {
            energy.consume((int) Math.min(Integer.MAX_VALUE, drain));
        }
        setChanged();
        Installation linked = installationNow(serverLevel);
        if (linked != null && breachCooldowns.tryFire(actor, now, NeroSecurityConfig.fieldBreachEventCooldownTicks())) {
            publish(SecurityEventType.FIELD_BREACH_ATTEMPT, linked, serverLevel, actor);
        }
    }

    // --- ticking ---------------------------------------------------------------------------------

    @Override
    protected void serverTick(Level level, BlockPos pos, BlockState state) {
        // Runs before Core's side-config tick, so no face can auto-eject even for one tick.
        enforceEnergyRouting();
        if (!(level instanceof ServerLevel serverLevel) || !(state.getBlock() instanceof ForceFieldEmitterBlock)) {
            return;
        }
        long now = level.getGameTime();
        if (sentState == null) {
            markRenderSent(now); // the chunk data already carried these values
        }
        // 0. A new emitter adopts an overload cooldown its position is still serving (break and re-place).
        if (!cooldownAdopted) {
            cooldownAdopted = true;
            long adopted = FieldCooldowns.session().adopt(devicePos(serverLevel), now);
            if (adopted > cooldownUntil) {
                cooldownUntil = adopted;
                setChanged();
            }
        }
        // 1. The store, once per tick; lockdown read from it (the hook only accelerates this).
        forgetInstallation();
        Installation linked = installationNow(serverLevel);
        boolean boost = linked != null && NeroSecurityConfig.isLockdownEnabled() && linked.lockdown().active();
        if (boost != lockdownBoost) {
            applyLockdownBoost(serverLevel, boost);
        }

        // 2. The state machine.
        FieldState before = fieldState;
        int draw = drawPerTick();
        FieldStateMachine.Inputs inputs = new FieldStateMachine.Inputs(linked != null,
                NeroSecurityConfig.isForceFieldsEnabled(), settings.enabled(), settingsValid(), energy.getAmount(), draw,
                FieldPower.restartThreshold(draw, NeroSecurityConfig.fieldRestartTicks()), load, loadCapacity(),
                NeroSecurityConfig.isForceFieldBreachable(), cooldownUntil, now, brownoutTicks,
                NeroSecurityConfig.fieldBrownoutGraceTicks(), NeroSecurityConfig.forceFieldOverloadSeconds() * 20,
                holdsBlocks());
        FieldStateMachine.Result result = FieldStateMachine.step(fieldState, inputs);
        boolean persisted = result.state() != fieldState || result.cooldownUntil() != cooldownUntil
                || result.load() != load;
        fieldState = result.state();
        brownoutTicks = result.brownoutTicks();
        cooldownUntil = result.cooldownUntil();
        load = result.load();
        apply(result.action(), serverLevel, linked);
        if (persisted) {
            setChanged();
        }
        boolean active = fieldState == FieldState.PROJECTING;
        if (state.getValue(ForceFieldEmitterBlock.ACTIVE) != active) {
            level.setBlock(pos, state.setValue(ForceFieldEmitterBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
        if (fieldState != before) {
            syncRequested = true;
        }

        // 3. Projecting: power, meter, reconcile (staggered).
        if (active) {
            if (draw > 0) {
                energy.consume(draw);
            }
            double decay = Math.max(0.0D, NeroSecurityConfig.fieldLoadDecayPerSecond()) / 20.0D;
            if (load > 0.0D && decay > 0.0D) {
                load = Math.max(0.0D, load - decay);
            }
            int interval = Math.max(1, NeroSecurityConfig.fieldReprojectIntervalTicks());
            if (reconcileRequested || Math.floorMod(now + pos.asLong(), (long) interval) == 0L) {
                reconcileRequested = false;
                if (FieldProjector.reconcile(serverLevel, this, linked)) {
                    setChanged();
                }
                noteObstruction(serverLevel, linked, now);
            } else if (!heldByEntity.isEmpty() && Math.floorMod(now + pos.asLong(), (long) HELD_RETRY_TICKS) == 0L) {
                // Gaps an entity was standing in close as soon as it moves, not at the next full reconcile.
                if (FieldProjector.retryHeld(serverLevel, this, linked)) {
                    setChanged();
                }
            }
        } else {
            heldByEntity.clear();
            shortReconciles = 0;
        }

        // 4. Nearby players' own verdicts (staggered; forced on flips).
        int syncInterval = Math.max(1, NeroSecurityConfig.fieldSyncIntervalTicks());
        if (syncRequested || Math.floorMod(now + pos.asLong(), (long) syncInterval) == 0L) {
            syncRequested = false;
            if (holdsBlocks() || !lastSent.isEmpty()) {
                FieldSync.pass(serverLevel, this);
            }
        }

        // 5. Renderer sync: on change only, spaced, except a state flip, which goes at once.
        if (RenderSync.resendDue(sentState, sentBucket, sentLockdown, sentAt, fieldState, currentDrawBucket(),
                lockdownBoost, now)) {
            markRenderSent(now);
            BlockState current = getBlockState();
            level.sendBlockUpdated(pos, current, current, Block.UPDATE_CLIENTS);
        }
    }

    /**
     * {@code FIELD_OBSTRUCTED} (no actor, the emitter's position): published when fewer blocks are placed than the
     * geometry asks for over {@value #OBSTRUCTED_AFTER_RECONCILES} reconciles in a row, at most once per
     * {@value #OBSTRUCTED_EVENT_COOLDOWN_TICKS} ticks, so an owner learns their field has holes (junk in the plane,
     * water, a claim, an entity) without a stream of rows.
     */
    private void noteObstruction(ServerLevel level, @Nullable Installation linked, long now) {
        if (projectedCount() >= area()) {
            shortReconciles = 0;
            return;
        }
        shortReconciles = Math.min(OBSTRUCTED_AFTER_RECONCILES, shortReconciles + 1);
        if (shortReconciles < OBSTRUCTED_AFTER_RECONCILES || linked == null) {
            return;
        }
        if (lastObstructedAt != Long.MIN_VALUE && now >= lastObstructedAt
                && now - lastObstructedAt < OBSTRUCTED_EVENT_COOLDOWN_TICKS) {
            return;
        }
        lastObstructedAt = now;
        publish(SecurityEventType.FIELD_OBSTRUCTED, linked, level, null);
    }

    private void apply(FieldStateMachine.Action action, ServerLevel level, @Nullable Installation linked) {
        switch (action) {
            case PROJECT -> {
                if (restorePending) {
                    SecuritySounds.FIELD_RESTORE.play(level, worldPosition);
                }
                if (restorePending && linked != null) {
                    publish(SecurityEventType.FIELD_RESTORED, linked, level, null);
                }
                restorePending = false;
                reconcileRequested = true;
            }
            case RESTORE -> {
                SecuritySounds.FIELD_RESTORE.play(level, worldPosition);
                if (linked != null) {
                    publish(SecurityEventType.FIELD_RESTORED, linked, level, null);
                }
                restorePending = false;
                powerLostReported = false;
                reconcileRequested = true;
            }
            case COLLAPSE_BROWNOUT -> {
                FieldProjector.collapse(level, this);
                SecuritySounds.FIELD_COLLAPSE.play(level, worldPosition);
                restorePending = true;
                if (linked != null) {
                    publish(SecurityEventType.FIELD_COLLAPSED, linked, level, null);
                    if (!powerLostReported) {
                        powerLostReported = true;
                        publish(SecurityEventType.POWER_LOST, linked, level, null);
                    }
                }
                setChanged();
            }
            case COLLAPSE_OVERLOAD -> {
                FieldProjector.collapse(level, this);
                SecuritySounds.FIELD_COLLAPSE.play(level, worldPosition);
                FieldCooldowns.session().record(devicePos(level), cooldownUntil, level.getGameTime());
                restorePending = true;
                if (linked != null) {
                    publish(SecurityEventType.FIELD_OVERLOADED, linked, level, null);
                }
                setChanged();
            }
            case COLLAPSE_IDLE -> {
                FieldProjector.collapse(level, this);
                setChanged();
            }
            case NONE -> {
            }
        }
    }

    private void publish(SecurityEventType type, Installation linked, ServerLevel level, @Nullable UUID actor) {
        // Notice before logging: a named player who has not had this installation's notice gets it first.
        RecordingNotice.noticeBeforeLogging(level.getServer(), actor, linked);
        SecurityEventBus.publish(SecurityEvent.of(type, linked.id(), devicePos(level), actor, System.currentTimeMillis()));
    }

    // --- breaking --------------------------------------------------------------------------------

    /**
     * A player is breaking the emitter (server). Anyone may; a breaker the resolver would not let OPEN at the field's
     * level (keycard filter included) publishes {@code DEVICE_FORCED}. Store and event work only.
     */
    public void onPlayerBreak(ServerPlayer player) {
        brokenByPlayer = true;
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        Installation linked = installationNow(serverLevel);
        if (linked == null) {
            return;
        }
        AccessDecision decision = AccessResolver.DEFAULT.resolve(player, linked, AccessAction.OPEN,
                settings.access().minLevel(), devicePos(serverLevel), settings.access().grantingSources());
        if (!decision.allowed()) {
            publish(SecurityEventType.DEVICE_FORCED, linked, serverLevel, player.getUUID());
        }
    }

    /**
     * The emitter is being replaced (not unloaded): take the field down, drop the modules, and if it was linked publish
     * {@code DEVICE_FORCED} for a non-player removal and drop the link so the installation keeps no ghost device.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        collapse(CollapseReason.REMOVED);
        Containers.dropContents(serverLevel, pos, modules.items());
        DevicePos at = DevicePos.of(serverLevel, pos);
        Installation linked = SecurityInstallations.get(serverLevel.getServer()).atDevice(at).orElse(null);
        if (linked == null) {
            return;
        }
        if (!brokenByPlayer) {
            publish(SecurityEventType.DEVICE_FORCED, linked, serverLevel, null);
        }
        InstallationService.unlinkByLoss(serverLevel.getServer(), linked, at);
    }

    /** No world access here (it also runs on chunk unload): only transient state is dropped. */
    @Override
    public void setRemoved() {
        super.setRemoved();
        verdicts.clear();
        lastSent.clear();
        breachCooldowns.clear();
        heldByEntity.clear();
        forgetInstallation();
    }

    // --- persistence -----------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        settings.save(output.child(KEY_SETTINGS));
        modules.save(output.child(KEY_MODULES));
        output.putString(KEY_STATE, fieldState.jsonName());
        output.putDouble(KEY_LOAD, load);
        output.putBoolean(KEY_LOAD_BOOSTED, lockdownBoost);
        output.putLong(KEY_COOLDOWN, cooldownUntil);
        output.store(KEY_PROJECTED, Codec.LONG_STREAM, projected.stream().mapToLong(BlockPos::asLong));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        if (this.sideConfig != null) {
            InputOnlyEnergy.enforceInputOnly(this.sideConfig.config());
        }
        this.settings = FieldSettings.load(input.childOrEmpty(KEY_SETTINGS));
        modules.load(input.childOrEmpty(KEY_MODULES));
        this.fieldState = FieldState.byName(input.getStringOr(KEY_STATE, FieldState.IDLE.jsonName()));
        double readLoad = input.getDoubleOr(KEY_LOAD, 0.0D);
        this.load = Double.isFinite(readLoad) ? Math.max(0.0D, Math.min(1.0E9D, readLoad)) : 0.0D;
        // The capacity the saved meter was measured against: the first tick rescales only if lockdown changed since.
        this.lockdownBoost = input.getBooleanOr(KEY_LOAD_BOOSTED, false);
        this.cooldownUntil = input.getLongOr(KEY_COOLDOWN, 0L);
        projected.clear();
        input.read(KEY_PROJECTED, Codec.LONG_STREAM).ifPresent(stream -> stream.limit(MAX_PROJECTED)
                .forEach(packed -> projected.add(BlockPos.of(packed))));
        // Update-tag keys (client copies only; never written to the save). State arrives through KEY_STATE above.
        this.clientDrawBucket = Math.max(0, Math.min(RenderSync.MAX_BUCKET, input.getIntOr(KEY_DRAW_BUCKET, 0)));
        this.clientLockdown = input.getBooleanOr(KEY_LOCKDOWN, false);
        this.reconcileRequested = true;
        this.syncRequested = true;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * Only what the renderer draws: the state, the draw bucket and the lockdown look. Settings, modules, load,
     * energy and owned positions stay on the server.
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY_STATE, fieldState.jsonName());
        tag.putInt(KEY_DRAW_BUCKET, currentDrawBucket());
        tag.putBoolean(KEY_LOCKDOWN, lockdownBoost);
        return tag;
    }

    // --- menu ------------------------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("field.nerosecurity.title");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new FieldMenu(containerId, inventory, this);
    }

    /** The renderer-sync rules as pure functions (unit-tested). */
    public static final class RenderSync {

        /** Highest draw bucket. */
        public static final int MAX_BUCKET = 4;
        /** Fewest ticks between two resends that are not a state flip. */
        public static final int MIN_SPACING_TICKS = 10;

        private RenderSync() {
        }

        /**
         * {@code 0} unless projecting with a positive draw; otherwise {@code ceil(4 x draw / maxDraw)} in 1..4
         * (4 when the largest possible draw is unknown or reached).
         */
        public static int drawBucket(FieldState state, int draw, int maxDraw) {
            if (state != FieldState.PROJECTING || draw <= 0) {
                return 0;
            }
            if (maxDraw <= 0 || draw >= maxDraw) {
                return MAX_BUCKET;
            }
            int bucket = (int) Math.ceil((double) draw * MAX_BUCKET / maxDraw);
            return Math.max(1, Math.min(MAX_BUCKET, bucket));
        }

        /** Whether to resend: a state flip at once; any other change once {@value #MIN_SPACING_TICKS} ticks have passed. */
        public static boolean resendDue(@Nullable FieldState sentState, int sentBucket, boolean sentLockdown, long sentAt,
                FieldState state, int bucket, boolean lockdown, long now) {
            if (sentState != state) {
                return true;
            }
            if (sentBucket == bucket && sentLockdown == lockdown) {
                return false;
            }
            return sentAt == Long.MIN_VALUE || now < sentAt || now - sentAt >= MIN_SPACING_TICKS;
        }
    }
}
