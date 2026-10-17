package za.co.neroland.nerosecurity.block.camera;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

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
import za.co.neroland.nerosecurity.sensor.SensorPermission;
import za.co.neroland.nerosecurity.sensor.SensorPermission.Classification;
import za.co.neroland.nerosecurity.sensor.SensorPermission.Sensed;
import za.co.neroland.nerosecurity.sensor.SensorSettings;
import za.co.neroland.nerosecurity.upgrade.SecurityModule;
import za.co.neroland.nerosecurity.upgrade.SecurityModuleSlots;

/**
 * The Security Camera: a powered presence sensor. It watches a cone in front of it, panning on a
 * deterministic sweep, and raises {@code MOTION_DETECTED} for anything the resolver does not permit
 * that it can see. <b>It never records images</b>: it holds no entity list and no position of anyone,
 * only its settings, energy, one range-module slot (fitted through the shared sensor settings menu), a transient per-entity cooldown and two transient
 * angles (where it last saw an intruder) for the renderer.
 *
 * <h2>Scan (every {@code cameraScanIntervalTicks}, staggered by position)</h2>
 * <ol>
 *   <li>One entity query over {@link CameraGeometry#searchBox} for living, non-spectator entities.</li>
 *   <li>The pure cone test ({@link CameraGeometry#inCone}) on each entity's eye position.</li>
 *   <li>At most {@value CameraRules#MAX_CLASSIFIED_PER_SCAN} cone survivors (a window rotating over the
 *       distance-sorted list, as the laser's does) go on to classification, so a crowd in front of the
 *       camera costs a bounded number of resolver calls per scan.</li>
 *   <li>{@link SensorPermission#classify} with {@link AccessResolver#DEFAULT}: <b>detection is not
 *       authorisation</b>. Entities that would produce nothing (ignored, permitted without
 *       {@code privacyCameraLogsMembers}, or still on cooldown) are dropped here, so they never spend
 *       the raycast budget.</li>
 *   <li>The nearest {@code cameraMaxRaycastsPerScan} survivors get one line-of-sight raycast each.</li>
 *   <li>Visible ones publish their event ({@link CameraRules#eventFor}) with the camera's own
 *       position, never the entity's; an intruder's event carries the player's (or owner's) UUID, a
 *       mob's none. A player who has not yet had the installation's recording notice gets it first.</li>
 * </ol>
 *
 * <p><b>Look direction.</b> After a {@code MOTION_DETECTED} the camera turns toward it for the renderer:
 * the synced yaw/pitch are rounded to {@value CameraRules#LOOK_STEP_DEGREES}-degree steps, synced at most
 * once per {@value CameraRules#LOOK_SYNC_MIN_TICKS} ticks, and decay back to the pan after
 * {@value CameraRules#LOOK_HOLD_TICKS} ticks without a new detection. The update tag carries only
 * {@code Armed} and that coarse look; never the camera's configuration, an entity or a position.
 *
 * <p>Energy follows the {@link InputOnlyEnergy} recipe: every face insert-only, nothing to configure.
 * Unpowered, unlinked or with cameras disabled the camera is dark: not {@code ARMED}, no detection.
 */
public class SecurityCameraBlockEntity extends AbstractMachineBlockEntity implements SensorMenuHost {

    public static final int MODULE_SLOTS = 1;
    public static final int MAX_TRANSFER = 1000;

    private static final String KEY_SETTINGS = "Settings";
    private static final String KEY_MODULES = "Modules";
    // Update-tag only (client sync for the renderer); never written by saveAdditional.
    private static final String KEY_ARMED = "Armed";
    private static final String KEY_HAS_LOOK = "HasLook";
    private static final String KEY_LOOK_YAW = "LookYaw";
    private static final String KEY_LOOK_PITCH = "LookPitch";
    /** Game time the current look was sent (the renderer's tilt anchor); only while there is a look. */
    private static final String KEY_SEEN_AT = "SeenAt";
    /** The server's {@code cameraSweepTicks}, so the client pans on the same clock as the server. */
    private static final String KEY_SWEEP = "Sweep";

    private SensorSettings settings = SensorSettings.DEFAULT;
    private final SecurityModuleSlots modules = new SecurityModuleSlots(MODULE_SLOTS,
            EnumSet.of(SecurityModule.RANGE), this::setChanged);

    // transient (never persisted: a sensor saves its settings, module and energy only)
    private boolean powerLostReported;
    private final Map<UUID, Long> seenCooldowns = new HashMap<>();
    private long playerBreakAt = Long.MIN_VALUE;
    private boolean hasLook;
    private float lastYaw;
    private float lastPitch;
    private long lookSeenAt = Long.MIN_VALUE;
    private long lookSyncedAt = Long.MIN_VALUE;
    private boolean lookDirty;
    private int classifyOffset;
    /** Client copies of the renderer-only {@value #KEY_SEEN_AT} and {@value #KEY_SWEEP}. */
    private long clientSeenAt = Long.MIN_VALUE;
    private int clientSweep = -1;

    public SecurityCameraBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, NeroSecurityConfig.cameraEnergyCapacity(), MAX_TRANSFER, 0, stack -> null);
        // Step 1 of the InputOnlyEnergy recipe: every face INPUT and nothing else permitted.
        installSideConfig(InputOnlyEnergy.sideConfig());
    }

    /** Step 2: nothing for Core's intent packet or configurator to edit. */
    @Override
    public @Nullable SideConfigComponent sideConfig() {
        return null;
    }

    /** Step 3 (per tick): undo any side-config change that slipped past the builder (paste, old save). */
    private void enforceEnergyRouting() {
        if (this.sideConfig != null && InputOnlyEnergy.enforceInputOnly(this.sideConfig.config())) {
            setChanged();
        }
    }

    // --- SensorHost -----------------------------------------------------------------------------

    @Override
    public SensorSettings sensorSettings() {
        return settings;
    }

    /** Stores already-authorised settings (the shared sensor menu's handler resolved and checked them). */
    @Override
    public void setSensorSettings(SensorSettings newSettings) {
        this.settings = newSettings == null ? SensorSettings.DEFAULT : newSettings;
        setChanged();
    }

    @Override
    public boolean armed() {
        return armedState();
    }

    private boolean armedState() {
        BlockState state = getBlockState();
        return state.hasProperty(SecurityCameraBlock.ARMED) && state.getValue(SecurityCameraBlock.ARMED);
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
        return false;
    }

    // --- state ----------------------------------------------------------------------------------

    @Override
    public SecurityModuleSlots modules() {
        return modules;
    }

    /** {@code min(cameraMaxRange, cameraBaseRange + 4 x range modules)}. */
    public int range() {
        return CameraRules.range(NeroSecurityConfig.cameraBaseRange(), NeroSecurityConfig.cameraMaxRange(),
                modules.count(SecurityModule.RANGE));
    }

    /** Whether the renderer has a last-seen direction to tilt toward. */
    public boolean hasLook() {
        return hasLook;
    }

    /** Yaw (degrees, Minecraft convention) toward where the camera last saw an intruder. Never persisted. */
    public float lastYaw() {
        return lastYaw;
    }

    /** Pitch (degrees, positive down) toward where the camera last saw an intruder. Never persisted. */
    public float lastPitch() {
        return lastPitch;
    }

    /**
     * Game time the current look was sent ({@code Long.MIN_VALUE} when none): on the client the synced copy,
     * the anchor of the renderer's tilt-toward / hold / tilt-back timeline.
     */
    public long lookSeenAt() {
        return level != null && level.isClientSide() ? clientSeenAt : lookSyncedAt;
    }

    /** Ticks for one full pan sweep: the synced server value on the client, the config on the server. */
    public int sweepTicks() {
        if (level != null && level.isClientSide() && clientSweep > 0) {
            return clientSweep;
        }
        return NeroSecurityConfig.cameraSweepTicks();
    }

    public DevicePos devicePos(ServerLevel serverLevel) {
        return DevicePos.of(serverLevel, worldPosition);
    }

    /** The installation this camera is linked to, looked up now; null when unlinked or client-side. */
    public @Nullable Installation installation() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        return SecurityInstallations.get(serverLevel.getServer()).atDevice(devicePos(serverLevel)).orElse(null);
    }

    /** Back to the defaults: called when the camera is linked, so nothing set while unlinked carries over. */
    public void resetSettings() {
        setSensorSettings(SensorSettings.DEFAULT);
    }

    // --- ticking --------------------------------------------------------------------------------

    @Override
    protected void serverTick(Level level, BlockPos pos, BlockState state) {
        // Runs before Core's side-config tick, so no face can auto-eject even for one tick.
        enforceEnergyRouting();
        if (!(level instanceof ServerLevel serverLevel) || !(state.getBlock() instanceof SecurityCameraBlock)) {
            return;
        }
        MinecraftServer server = serverLevel.getServer();
        DevicePos at = devicePos(serverLevel);
        Installation installation = SecurityInstallations.get(server).atDevice(at).orElse(null);
        boolean linked = installation != null;
        int draw = NeroSecurityConfig.cameraPowerPerTick();
        boolean nowArmed = CameraRules.armed(linked, NeroSecurityConfig.isCamerasEnabled(), energy.getAmount(), draw);

        // ARMED flips only with armed(): an unpowered or unlinked camera is visibly dark.
        if (state.getValue(SecurityCameraBlock.ARMED) != nowArmed) {
            BlockState flipped = state.setValue(SecurityCameraBlock.ARMED, nowArmed);
            level.setBlock(pos, flipped, Block.UPDATE_CLIENTS);
            state = flipped;
            if (!nowArmed) {
                hasLook = false;
            }
            lookDirty = false; // this sync carries the look state too
            sync();
        }
        maintainLook(level.getGameTime());

        // Power loss, once per transition.
        boolean starved = draw > 0 && energy.getAmount() < draw;
        if (linked && starved && !powerLostReported) {
            powerLostReported = true;
            setChanged();
            publish(SecurityEventType.POWER_LOST, installation, at, null);
        } else if (!starved && powerLostReported) {
            powerLostReported = false;
            setChanged();
        }

        if (!nowArmed) {
            return;
        }
        if (draw > 0) {
            energy.consume(draw);
        }
        long now = level.getGameTime();
        if (CameraRules.scanDue(now, pos.asLong(), NeroSecurityConfig.cameraScanIntervalTicks())) {
            scan(serverLevel, installation, at, state.getValue(SecurityCameraBlock.FACING), now);
        }
    }

    /** One bounded scan: one box query, the cone test, classification, then at most N raycasts. */
    private void scan(ServerLevel serverLevel, Installation installation, DevicePos at, Direction facing, long now) {
        MinecraftServer server = serverLevel.getServer();
        int range = range();
        Vec3 lens = CameraGeometry.lens(worldPosition, facing);
        Vec3 view = CameraGeometry.viewDirection(facing, now, NeroSecurityConfig.cameraSweepTicks());
        AABB box = CameraGeometry.searchBox(lens, facing, range);
        boolean logsMembers = NeroSecurityConfig.privacyCameraLogsMembers();
        int cooldown = NeroSecurityConfig.cameraEventCooldownTicks();
        SensorSettings current = settings;

        List<Entity> found = serverLevel.getEntities((Entity) null, box,
                e -> e instanceof LivingEntity && !e.isSpectator());
        if (found.isEmpty()) {
            return;
        }
        // Cheap geometry first; then at most MAX_CLASSIFIED_PER_SCAN of the survivors are classified.
        List<ConeHit> inCone = new ArrayList<>();
        for (Entity entity : found) {
            Vec3 eye = entity.getEyePosition();
            if (CameraGeometry.inCone(lens, view, eye, range, current.fovDegrees())) {
                inCone.add(new ConeHit(entity, eye, eye.distanceToSqr(lens)));
            }
        }
        if (inCone.isEmpty()) {
            return;
        }
        List<ConeHit> window = CameraRules.classifyWindow(
                CameraGeometry.nearest(inCone, ConeHit::distanceSq, inCone.size()), classifyOffset,
                CameraRules.MAX_CLASSIFIED_PER_SCAN);
        if (inCone.size() > window.size()) {
            classifyOffset += window.size();
        }
        List<Candidate> candidates = new ArrayList<>();
        for (ConeHit hit : window) {
            Entity entity = hit.entity();
            Vec3 eye = hit.eye();
            if (!CameraRules.cooldownElapsed(seenCooldowns.get(entity.getUUID()), now, cooldown)) {
                continue; // reported recently: nothing to publish, so no resolver call either
            }
            Sensed sensed = SensorPermission.describe(entity);
            Classification classification = SensorPermission.classify(AccessResolver.DEFAULT, server, installation,
                    at, sensed, current);
            SecurityEventType type = CameraRules.eventFor(classification, logsMembers);
            if (type == null) {
                continue;
            }
            // A pet is judged by its owner but logged as nobody (the owner was not sensed and had no notice).
            candidates.add(new Candidate(entity, eye, SensorPermission.loggedActor(sensed), type, hit.distanceSq()));
        }
        List<Candidate> nearest = CameraGeometry.nearest(candidates, Candidate::distanceSq,
                NeroSecurityConfig.cameraMaxRaycastsPerScan());
        boolean looked = false;
        for (Candidate candidate : nearest) {
            if (!lineOfSight(serverLevel, lens, candidate)) {
                continue;
            }
            remember(candidate.entity().getUUID(), now, cooldown);
            if (candidate.entity() instanceof ServerPlayer player) {
                // Notice before logging: a player about to be logged hears about it first, this tick.
                RecordingNotice.noticeBeforeLogging(player, installation);
            }
            publish(candidate.type(), installation, at, candidate.subject());
            if (candidate.type() == SecurityEventType.MOTION_DETECTED && !looked) {
                // Nearest visible intruder first: point the renderer there. Coarse direction only, never who.
                looked = lookToward(candidate.eye().subtract(lens), now);
            }
        }
    }

    /** {@code MISS}, or a block hit no nearer than the entity's eye, means the camera can see it. */
    private static boolean lineOfSight(ServerLevel serverLevel, Vec3 lens, Candidate candidate) {
        BlockHitResult hit = serverLevel.clip(new ClipContext(lens, candidate.eye(), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, candidate.entity()));
        if (hit.getType() == HitResult.Type.MISS) {
            return true;
        }
        return hit.getLocation().distanceToSqr(lens) >= candidate.distanceSq() - 1.0E-4D;
    }

    private void remember(UUID entityId, long now, int cooldown) {
        if (seenCooldowns.size() >= CameraRules.COOLDOWN_PRUNE_SIZE) {
            seenCooldowns.values().removeIf(at -> now - at >= cooldown);
            if (seenCooldowns.size() >= CameraRules.COOLDOWN_PRUNE_SIZE) {
                dropOldestCooldown();
            }
        }
        seenCooldowns.put(entityId, now);
    }

    private void dropOldestCooldown() {
        UUID oldest = null;
        long oldestAt = Long.MAX_VALUE;
        for (Map.Entry<UUID, Long> entry : seenCooldowns.entrySet()) {
            if (entry.getValue() < oldestAt) {
                oldestAt = entry.getValue();
                oldest = entry.getKey();
            }
        }
        if (oldest != null) {
            seenCooldowns.remove(oldest);
        }
    }

    /**
     * Turns the transient look toward {@code direction}, rounded to {@link CameraRules#LOOK_STEP_DEGREES}.
     * Marks a sync only when the rounded angles changed; {@link #maintainLook} sends it, rate-limited.
     */
    private boolean lookToward(Vec3 direction, long now) {
        if (direction.lengthSqr() < 1.0E-8D) {
            return false;
        }
        float yaw = CameraRules.quantiseAngle(CameraGeometry.yawOf(direction));
        float pitch = CameraRules.quantiseAngle(CameraGeometry.pitchOf(direction));
        lookSeenAt = now;
        if (!hasLook || yaw != lastYaw || pitch != lastPitch) {
            hasLook = true;
            lastYaw = yaw;
            lastPitch = pitch;
            lookDirty = true;
        }
        maintainLook(now);
        return true;
    }

    /** Decays an old look back to the pan, and sends a pending look change at most every 2 s. */
    private void maintainLook(long now) {
        if (hasLook && CameraRules.lookExpired(lookSeenAt, now)) {
            hasLook = false;
            lookDirty = true;
        }
        if (lookDirty && CameraRules.lookSyncDue(lookSyncedAt, now)) {
            lookDirty = false;
            lookSyncedAt = now;
            sync();
        }
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    /** An entity inside the cone; lives only for that scan. */
    private record ConeHit(Entity entity, Vec3 eye, double distanceSq) {
    }

    /** One candidate of a scan; lives only for that scan. */
    private record Candidate(Entity entity, Vec3 eye, @Nullable UUID subject, SecurityEventType type,
            double distanceSq) {
    }

    // --- menu (the shared sensor settings menu) ---------------------------------------------------

    @Override
    public BlockEntity sensorBlockEntity() {
        return this;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("sensor.nerosecurity.title.camera");
    }

    /** The shared sensor settings menu, bound to this camera (opened via {@code SensorPayloadHandlers.openSettings}). */
    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new SensorSettingsMenu(containerId, inventory, this);
    }

    // --- breaking and removal -------------------------------------------------------------------

    /**
     * A player is breaking this camera (server). Anyone may; a breaker the resolver would not let OPEN
     * at the camera's minimum level publishes {@code DEVICE_FORCED}. Store and event work only.
     */
    public void onPlayerBreak(ServerPlayer player) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        playerBreakAt = serverLevel.getGameTime();
        DevicePos at = devicePos(serverLevel);
        Installation installation = SecurityInstallations.get(serverLevel.getServer()).atDevice(at).orElse(null);
        if (installation == null) {
            return;
        }
        AccessDecision decision = AccessResolver.DEFAULT.resolve(player, installation, AccessAction.OPEN,
                settings.minLevel(), at, null);
        if (!decision.allowed()) {
            publish(SecurityEventType.DEVICE_FORCED, installation, at, player.getUUID());
        }
    }

    /**
     * The camera is being replaced (not unloaded): drop the fitted module and, if linked, publish
     * {@code DEVICE_FORCED} for a non-player removal (explosion, command) and drop the link
     * ({@code unlinkByLoss}) so no ghost device squats the position.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        Containers.dropContents(serverLevel, pos, modules.items());
        MinecraftServer server = serverLevel.getServer();
        DevicePos at = devicePos(serverLevel);
        Installation installation = SecurityInstallations.get(server).atDevice(at).orElse(null);
        if (installation == null) {
            return;
        }
        if (playerBreakAt != serverLevel.getGameTime()) {
            publish(SecurityEventType.DEVICE_FORCED, installation, at, null);
        }
        InstallationService.unlinkByLoss(server, installation, at);
    }

    /** Transient state only; no world access here. */
    @Override
    public void setRemoved() {
        super.setRemoved();
        seenCooldowns.clear();
        hasLook = false;
        lookDirty = false;
    }

    private void publish(SecurityEventType type, Installation installation, DevicePos at, @Nullable UUID actor) {
        // Notice before logging: a named player who has not had this installation's notice gets it first.
        RecordingNotice.noticeBeforeLogging(level instanceof ServerLevel serverLevel ? serverLevel.getServer() : null,
                actor, installation);
        SecurityEventBus.publish(SecurityEvent.of(type, installation.id(), at, actor, System.currentTimeMillis()));
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
        // Step 3 (on load): a corrupt save or an old paste cannot leave an extracting face.
        if (this.sideConfig != null) {
            InputOnlyEnergy.enforceInputOnly(this.sideConfig.config());
        }
        this.settings = SensorSettings.load(input.childOrEmpty(KEY_SETTINGS));
        modules.load(input.childOrEmpty(KEY_MODULES));
        // Present only in the client update tag; absent (defaults) on a disk load.
        this.hasLook = input.getBooleanOr(KEY_HAS_LOOK, false);
        this.lastYaw = input.getFloatOr(KEY_LOOK_YAW, 0.0F);
        this.lastPitch = input.getFloatOr(KEY_LOOK_PITCH, 0.0F);
        this.clientSeenAt = input.getLongOr(KEY_SEEN_AT, Long.MIN_VALUE);
        this.clientSweep = input.getIntOr(KEY_SWEEP, -1);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * Only what the renderer needs: {@code Armed}, the coarse last-seen direction (two angles in
     * {@link CameraRules#LOOK_STEP_DEGREES}-degree steps) with the game time it was sent, and the pan period.
     * Never the camera's configuration (minimum
     * level, entity mode, field of view, module, energy, side config), an entity id, a name or a position.
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(KEY_ARMED, armedState());
        tag.putBoolean(KEY_HAS_LOOK, hasLook);
        if (hasLook) {
            tag.putFloat(KEY_LOOK_YAW, lastYaw);
            tag.putFloat(KEY_LOOK_PITCH, lastPitch);
            tag.putLong(KEY_SEEN_AT, lookSyncedAt);
        }
        tag.putInt(KEY_SWEEP, NeroSecurityConfig.cameraSweepTicks());
        return tag;
    }
}
