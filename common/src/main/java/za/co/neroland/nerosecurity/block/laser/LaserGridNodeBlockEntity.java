package za.co.neroland.nerosecurity.block.laser;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

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
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.InstallationService;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.menu.sensor.SensorMenuHost;
import za.co.neroland.nerosecurity.menu.sensor.SensorSettingsMenu;
import za.co.neroland.nerosecurity.notice.RecordingNotice;
import za.co.neroland.nerosecurity.registry.NeroSecurityDamageTypes;
import za.co.neroland.nerosecurity.sensor.SensorPermission;
import za.co.neroland.nerosecurity.sensor.SensorPermission.Classification;
import za.co.neroland.nerosecurity.sensor.SensorPermission.Sensed;
import za.co.neroland.nerosecurity.sensor.SensorSettings;
import za.co.neroland.nerosecurity.sound.SecuritySounds;
import za.co.neroland.nerosecurity.upgrade.SecurityModule;
import za.co.neroland.nerosecurity.upgrade.SecurityModuleSlots;

/**
 * The laser grid node's block entity: sensor settings, two range-module slots, an insert-only energy
 * buffer, and the beam sweep.
 *
 * <p><b>Detection is not authorisation.</b> The sweep finds living entities inside the thin beam box;
 * every one of them is judged only by {@link SensorPermission#classify} (the resolver, on a UUID, at the
 * node's {@code minLevel}). A permitted entity produces nothing: no event, no damage, no cooldown entry.
 *
 * <p><b>Tick budget.</b> Per pair, one {@code getEntities} over a 0.3-wide box every
 * {@code laserScanIntervalTicks} (staggered by position), by one node of the pair only
 * ({@link LaserPairing#sweeps}); at most {@value #MAX_ENTITIES_PER_SCAN} entities judged per sweep; a
 * per-entity cooldown ({@link LaserTripCooldowns}, bounded); pairing re-checked every 20 ticks (staggered)
 * and on a neighbour change. The installation is looked up from the store every tick, never cached.
 *
 * <p>It holds <b>no installation pointer</b> and persists only its settings, modules and energy. The
 * partner position and the last trip time are transient; they travel in the update tag (only when they
 * change) so a renderer can draw the beam and a trip flash. The update tag carries nothing else: no
 * settings, modules or energy reach clients. A node pairs only with a facing node of the same
 * installation, and a trip never damages an owner or member (see {@link LaserDamageRule}).
 */
public class LaserGridNodeBlockEntity extends AbstractMachineBlockEntity implements SensorMenuHost {

    public static final int MODULE_SLOTS = 2;
    public static final int MAX_TRANSFER = 1000;
    /** Upper bound on entities judged per sweep (the window rotates, so a crowd is still covered). */
    public static final int MAX_ENTITIES_PER_SCAN = 32;
    /** At most one trip-flash sync per this many ticks. */
    public static final int TRIP_SYNC_MIN_TICKS = 10;

    private static final String KEY_SETTINGS = "Settings";
    private static final String KEY_MODULES = "Modules";
    private static final String KEY_HAS_PARTNER = "HasPartner";
    private static final String KEY_PARTNER = "Partner";
    private static final String KEY_TRIP_AT = "TripAt";
    private static final String KEY_ARMED = "Armed";

    private SensorSettings settings = SensorSettings.DEFAULT;
    private final SecurityModuleSlots modules = new SecurityModuleSlots(MODULE_SLOTS,
            EnumSet.of(SecurityModule.RANGE), this::onModulesChanged);

    // transient (server)
    private @Nullable BlockPos partner;
    private boolean pairDirty = true;
    private final LaserTripCooldowns cooldowns = new LaserTripCooldowns();
    private boolean powerLostReported;
    private boolean brokenByPlayer;
    private long lastTripAt = Long.MIN_VALUE;
    private long lastTripSyncAt = Long.MIN_VALUE;
    private int sweepOffset;

    // client copies from the update tag
    private @Nullable BlockPos clientPartner;
    private long clientTripAt = Long.MIN_VALUE;

    public LaserGridNodeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, NeroSecurityConfig.laserEnergyCapacity(), MAX_TRANSFER, 0, stack -> null);
        // Every face INPUT and nothing else permitted; the loader capabilities never read it either.
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

    // --- SensorHost / SensorMenuHost ------------------------------------------------------------

    @Override
    public SensorSettings sensorSettings() {
        return settings;
    }

    /** Stores settings already authorised by the payload handler. */
    @Override
    public void setSensorSettings(SensorSettings newSettings) {
        this.settings = newSettings == null ? SensorSettings.DEFAULT : newSettings;
        setChanged();
    }

    /** Back to the defaults: called when the node is linked, so nothing set while unlinked carries over. */
    public void resetSettings() {
        setSensorSettings(SensorSettings.DEFAULT);
    }

    /**
     * Linked, paired and powered right now. Server: computed from the store (looked up now), the partner
     * and the buffer. Client: the {@code ARMED} blockstate.
     */
    @Override
    public boolean armed() {
        if (!(level instanceof ServerLevel serverLevel)) {
            BlockState state = getBlockState();
            return state.hasProperty(LaserGridNodeBlock.ARMED) && state.getValue(LaserGridNodeBlock.ARMED);
        }
        return installation(serverLevel) != null && partner != null && powered();
    }

    @Override
    public long energyAmount() {
        return energy.getAmount();
    }

    @Override
    public long energyCapacity() {
        return energy.getCapacity();
    }

    @Override
    public boolean isLaser() {
        return true;
    }

    @Override
    public BlockEntity sensorBlockEntity() {
        return this;
    }

    @Override
    public SecurityModuleSlots modules() {
        return modules;
    }

    @Override
    public boolean paired() {
        return beamPartner() != null;
    }

    // --- reads for the renderer ----------------------------------------------------------------

    /** The partner node's position: server truth on the server, the synced copy on the client. */
    public @Nullable BlockPos beamPartner() {
        return level != null && level.isClientSide() ? clientPartner : partner;
    }

    /** Game time of the last trip (for a flash), or {@link Long#MIN_VALUE} if none since load. */
    public long lastTripGameTime() {
        return level != null && level.isClientSide() ? clientTripAt : lastTripAt;
    }

    /** Pairing range: {@code min(laserMaxRange, laserBaseRange + 4 x range modules)}. */
    public int range() {
        return LaserPairing.range(NeroSecurityConfig.laserBaseRange(), NeroSecurityConfig.laserMaxRange(),
                modules.count(SecurityModule.RANGE));
    }

    /** A neighbour changed: re-check the pair on the next tick. */
    public void markPairDirty() {
        pairDirty = true;
    }

    private void onModulesChanged() {
        setChanged();
        pairDirty = true; // range may have changed
    }

    private boolean powered() {
        int draw = NeroSecurityConfig.laserPowerPerTick();
        return draw <= 0 || energy.getAmount() >= draw;
    }

    private @Nullable Installation installation(ServerLevel serverLevel) {
        return SecurityInstallations.get(serverLevel.getServer()).atDevice(DevicePos.of(serverLevel, worldPosition))
                .orElse(null);
    }

    // --- ticking --------------------------------------------------------------------------------

    @Override
    protected void serverTick(Level level, BlockPos pos, BlockState state) {
        // Runs before Core's side-config tick, so no face can auto-eject even for one tick.
        enforceEnergyRouting();
        if (!(level instanceof ServerLevel serverLevel) || !(state.getBlock() instanceof LaserGridNodeBlock)) {
            return;
        }
        long now = level.getGameTime();
        long key = pos.asLong();
        Direction facing = state.getValue(LaserGridNodeBlock.FACING);

        // 1. Pairing (every 20 ticks, staggered, or after a neighbour/module change): only with a facing
        //    node linked to the same installation, never a stranger's or an unlinked one.
        Installation installation = installation(serverLevel);
        if (pairDirty || LaserPairing.pairDue(now, key)) {
            pairDirty = false;
            setPartner(installation == null ? null
                    : LaserPairing.findPartner(level, pos, facing, range(),
                            at -> sameInstallation(serverLevel, installation, at)));
        }

        // 2. Armed = linked + paired + powered; the blockstate follows only on a flip.
        boolean linked = installation != null;
        boolean powered = powered();
        boolean armed = linked && partner != null && powered;
        if (state.getValue(LaserGridNodeBlock.ARMED) != armed) {
            level.setBlock(pos, state.setValue(LaserGridNodeBlock.ARMED, armed), Block.UPDATE_ALL);
        }

        // 3. Power: draw while armed; POWER_LOST once per transition while linked.
        int draw = NeroSecurityConfig.laserPowerPerTick();
        if (armed && draw > 0) {
            energy.consume(draw);
        }
        if (linked && !powered && !powerLostReported) {
            powerLostReported = true;
            publish(SecurityEventType.POWER_LOST, installation, serverLevel, null);
        } else if (powered && powerLostReported) {
            powerLostReported = false;
        }

        // 4. Sweep (staggered; one node per pair).
        if (!armed || !LaserPairing.scanDue(now, key, NeroSecurityConfig.laserScanIntervalTicks())) {
            return;
        }
        BlockPos other = partner;
        BlockState otherState = level.getBlockState(other);
        if (!(otherState.getBlock() instanceof LaserGridNodeBlock)
                || otherState.getValue(LaserGridNodeBlock.FACING) != facing.getOpposite()) {
            pairDirty = true; // the partner went away since the last pairing; re-pair next tick
            return;
        }
        if (!sameInstallation(serverLevel, installation, other)) {
            pairDirty = true; // the partner was unlinked or moved to another installation; re-pair next tick
            return;
        }
        if (!LaserPairing.sweeps(key, other.asLong(), otherState.getValue(LaserGridNodeBlock.ARMED))) {
            return;
        }
        sweep(serverLevel, installation, other, now);
    }

    /** Whether the node at {@code other} is linked to {@code installation} (store lookup, never cached). */
    private static boolean sameInstallation(ServerLevel serverLevel, Installation installation, BlockPos other) {
        return SecurityInstallations.get(serverLevel.getServer()).atDevice(DevicePos.of(serverLevel, other))
                .map(found -> found.id().equals(installation.id())).orElse(false);
    }

    private void sweep(ServerLevel serverLevel, Installation installation, BlockPos other, long now) {
        if (worldPosition.distManhattan(other) <= 1) {
            return; // adjacent nodes: no gap for anyone to stand in
        }
        AABB box = LaserPairing.beamBox(worldPosition, other);
        List<Entity> found = serverLevel.getEntities((Entity) null, box,
                e -> e instanceof LivingEntity && !e.isSpectator());
        int size = found.size();
        if (size == 0) {
            return;
        }
        MinecraftServer server = serverLevel.getServer();
        DevicePos at = DevicePos.of(serverLevel, worldPosition);
        int cooldown = NeroSecurityConfig.laserTripCooldownTicks();
        double damage = NeroSecurityConfig.laserTripDamage();
        int limit = Math.min(size, MAX_ENTITIES_PER_SCAN);
        int start = size > limit ? Math.floorMod(sweepOffset, size) : 0;
        sweepOffset += limit;
        boolean tripped = false;
        for (int i = 0; i < limit; i++) {
            Entity entity = found.get((start + i) % size);
            Sensed sensed = SensorPermission.describe(entity);
            Classification verdict = SensorPermission.classify(AccessResolver.DEFAULT, server, installation, at,
                    sensed, settings);
            if (verdict != Classification.INTRUDER) {
                continue; // permitted or ignored: nothing at all
            }
            if (!cooldowns.tryTrip(entity.getUUID(), now, cooldown)) {
                continue;
            }
            tripped = true;
            if (entity instanceof ServerPlayer player) {
                // Notice before logging: a player about to be logged hears about it first, this tick.
                RecordingNotice.noticeBeforeLogging(player, installation);
            }
            // A pet is judged by its owner but logged as nobody (the owner was not sensed and had no notice).
            publish(SecurityEventType.LASER_TRIPPED, installation, serverLevel, SensorPermission.loggedActor(sensed));
            // Members and owners are detected and logged during a lockdown, but never hurt.
            if (damage > 0.0D && LaserDamageRule.mayHarm(installation, sensed.subject())) {
                // Built from the registry directly: DamageSources#source(key) is not public on every loader.
                serverLevel.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).get(NeroSecurityDamageTypes.LASER)
                        .ifPresent(type -> entity.hurtServer(serverLevel, new DamageSource(type), (float) damage));
            }
        }
        if (tripped) {
            SecuritySounds.LASER_TRIP.play(serverLevel, worldPosition);
            lastTripAt = now;
            if (lastTripSyncAt == Long.MIN_VALUE || now - lastTripSyncAt >= TRIP_SYNC_MIN_TICKS) {
                lastTripSyncAt = now;
                sync();
            }
        }
    }

    private void setPartner(@Nullable BlockPos found) {
        if (found == null ? partner == null : found.equals(partner)) {
            return;
        }
        partner = found;
        sync();
    }

    private void sync() {
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    // --- interaction ----------------------------------------------------------------------------

    /**
     * A player is breaking this node (server). Anyone may; a breaker the resolver would not let OPEN at
     * the node's level publishes {@code DEVICE_FORCED}. Store and event work only.
     */
    public void onPlayerBreak(ServerPlayer player) {
        brokenByPlayer = true;
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        Installation installation = installation(serverLevel);
        if (installation == null) {
            return;
        }
        AccessDecision decision = AccessResolver.DEFAULT.resolve(player, installation, AccessAction.OPEN,
                settings.minLevel(), DevicePos.of(serverLevel, worldPosition), null);
        if (!decision.allowed()) {
            publish(SecurityEventType.DEVICE_FORCED, installation, serverLevel, player.getUUID());
        }
    }

    /**
     * The node is being replaced (not unloaded): drop the fitted modules, and if it was linked, publish
     * {@code DEVICE_FORCED} for a non-player removal (explosion, command) and drop the link
     * so the installation keeps no ghost device. A player break already reported itself.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        Containers.dropContents(serverLevel, pos, modules.items());
        DevicePos at = DevicePos.of(serverLevel, pos);
        Installation installation = SecurityInstallations.get(serverLevel.getServer()).atDevice(at).orElse(null);
        if (installation == null) {
            return;
        }
        if (!brokenByPlayer) {
            publish(SecurityEventType.DEVICE_FORCED, installation, serverLevel, null);
        }
        InstallationService.unlinkByLoss(serverLevel.getServer(), installation, at);
    }

    /** No world access here: only transient state is dropped. */
    @Override
    public void setRemoved() {
        super.setRemoved();
        cooldowns.clear();
        partner = null;
    }

    private void publish(SecurityEventType type, Installation installation, ServerLevel serverLevel, @Nullable UUID actor) {
        // Notice before logging: a named player who has not had this installation's notice gets it first.
        RecordingNotice.noticeBeforeLogging(serverLevel.getServer(), actor, installation);
        SecurityEventBus.publish(SecurityEvent.of(type, installation.id(), DevicePos.of(serverLevel, worldPosition),
                actor, System.currentTimeMillis()));
    }

    // --- persistence and sync -------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        settings.save(output.child(KEY_SETTINGS));
        modules.save(output.child(KEY_MODULES));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        if (this.sideConfig != null) {
            InputOnlyEnergy.enforceInputOnly(this.sideConfig.config());
        }
        this.settings = SensorSettings.load(input.childOrEmpty(KEY_SETTINGS));
        modules.load(input.childOrEmpty(KEY_MODULES));
        // Update-tag keys (client copies only; never written to the save).
        this.clientPartner = input.getBooleanOr(KEY_HAS_PARTNER, false)
                ? BlockPos.of(input.getLongOr(KEY_PARTNER, 0L)) : null;
        this.clientTripAt = input.getLongOr(KEY_TRIP_AT, Long.MIN_VALUE);
        this.pairDirty = true;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * Only what a renderer needs: armed, the partner position and the last trip time. The sensor's
     * configuration (minimum level, entity mode, modules, energy, side config) is never sent to clients in
     * range: the settings screen gets it from its own snapshot, to players allowed to configure the node.
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        BlockState state = getBlockState();
        tag.putBoolean(KEY_ARMED, state.hasProperty(LaserGridNodeBlock.ARMED) && state.getValue(LaserGridNodeBlock.ARMED));
        tag.putBoolean(KEY_HAS_PARTNER, partner != null);
        if (partner != null) {
            tag.putLong(KEY_PARTNER, partner.asLong());
        }
        tag.putLong(KEY_TRIP_AT, lastTripAt);
        return tag;
    }

    // --- menu -----------------------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("sensor.nerosecurity.title.laser");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new SensorSettingsMenu(containerId, inventory, this);
    }
}
