package za.co.neroland.nerosecurity.block.door;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
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
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.access.AccessResolver;
import za.co.neroland.nerosecurity.block.DeviceAccessSettings;
import za.co.neroland.nerosecurity.block.SecurityDevice;
import za.co.neroland.nerosecurity.block.VisualState;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.energy.InputOnlyEnergy;
import za.co.neroland.nerosecurity.event.SecurityEvent;
import za.co.neroland.nerosecurity.event.SecurityEventBus;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.InstallationService;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.menu.MenuOpener;
import za.co.neroland.nerosecurity.menu.door.DoorPayloadHandlers;
import za.co.neroland.nerosecurity.menu.door.SecurityDoorMenu;
import za.co.neroland.nerosecurity.sound.SecuritySounds;
import za.co.neroland.nerosecurity.upgrade.SecurityModule;
import za.co.neroland.nerosecurity.upgrade.SecurityModuleSlots;

/**
 * The controller block entity shared by the Security Door and the Blast Door.
 *
 * <p>Holds the door's access settings, redstone mode, two module slots (hardness, open speed), the
 * energy buffer (through Core's {@link AbstractMachineBlockEntity}; every face accepts energy) and the
 * open/close animation state. It holds <b>no installation pointer</b>: every check looks the
 * installation up from the store by the controller's {@link DevicePos}, so unlinking takes effect on
 * the next interaction.
 *
 * <p>Animation: {@link #openTicks} runs from 0 (closed) to {@link #openDuration()} (fully open) toward
 * {@link #opening}. Both are synced to the client in the update tag (only when the door starts or
 * finishes moving) together with the game time of that change, so a renderer can interpolate.
 *
 * <p>The update tag carries only what the renderer needs ({@value #KEY_OPEN_TICKS}, {@value #KEY_OPENING},
 * {@value #KEY_SYNC_TIME}, {@value #KEY_DURATION}, {@value #KEY_MIN_LEVEL}, {@value #KEY_RECORDING},
 * {@value #KEY_POWER_LOW}, {@value #KEY_SEALED}, {@value #KEY_HARDNESS}); settings, modules, energy and side
 * configuration stay on the server. The visual block-state properties
 * {@link AbstractSecurityDoorBlock#ANIMATING} and {@link AbstractSecurityDoorBlock#SEALED} follow the same
 * ticks; sounds come from {@link SecuritySounds}.
 */
public class SecurityDoorBlockEntity extends AbstractMachineBlockEntity implements MenuProvider {

    public static final int MODULE_SLOTS = 2;
    public static final int MAX_TRANSFER = 1000;

    private static final String KEY_SETTINGS = "Settings";
    private static final String KEY_REDSTONE = "RedstoneMode";
    private static final String KEY_MODULES = "Modules";
    public static final String KEY_OPEN_TICKS = "OpenTicks";
    public static final String KEY_OPENING = "Opening";
    private static final String KEY_AUTO_CLOSE = "AutoClose";
    /** Update-tag keys (renderer only). */
    public static final String KEY_DURATION = "Duration";
    public static final String KEY_SYNC_TIME = "SyncTime";
    public static final String KEY_MIN_LEVEL = "MinLevel";
    public static final String KEY_RECORDING = "Recording";
    public static final String KEY_POWER_LOW = "PowerLow";
    public static final String KEY_SEALED = "Sealed";
    public static final String KEY_HARDNESS = "Hardness";
    /** Bound on the denied-event cooldown map before stale entries are pruned. */
    private static final int COOLDOWN_PRUNE_SIZE = 32;

    private DeviceAccessSettings settings = DeviceAccessSettings.DEFAULT;
    private RedstoneMode redstoneMode = RedstoneMode.IGNORE;
    private final SecurityModuleSlots modules = new SecurityModuleSlots(MODULE_SLOTS,
            EnumSet.of(SecurityModule.HARDNESS, SecurityModule.OPEN_SPEED), this::onModulesChanged);
    private int openTicks;
    private boolean opening;
    private int autoCloseTicks;
    private boolean powerLostReported;

    // transient
    private boolean openedPrivileged;
    private boolean openedByRedstone;
    private boolean brokenByPlayer;
    private long changedAt;
    /** Client copy of the server's duration (the config base and the modules are not synced). */
    private int clientDuration = -1;
    /** Client copies of the renderer-only update-tag fields; on the server, the last values sent. */
    private AccessLevel visualMinLevel = AccessLevel.MEMBER;
    private boolean visualRecording;
    private boolean visualPowerLow;
    private boolean visualSealed;
    private int visualHardness;
    private boolean visualSent;
    private long visualSentAt = Long.MIN_VALUE;
    private final Map<UUID, Long> deniedAt = new HashMap<>();

    public SecurityDoorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, NeroSecurityConfig.doorEnergyCapacity(), MAX_TRANSFER, 0, stack -> null);
        // Every face INPUT and nothing else permitted (SideConfig would otherwise default to ALL_DISABLED
        // and permit OUTPUT/IO). The loader capabilities never read it either: see DoorEnergyAccess.
        installSideConfig(InputOnlyEnergy.sideConfig());
    }

    /**
     * Always null. Core's side-config intent packet and configurator API reach a machine's routing
     * through this seam with only a range check, and a door's energy faces are fixed (insert-only), so
     * the door offers nothing to configure. Core's own ticker and persistence use the field directly.
     */
    @Override
    public @Nullable SideConfigComponent sideConfig() {
        return null;
    }

    /** Undoes any side-config change that slipped past the builder's allowances (paste, old save). */
    private void enforceEnergyRouting() {
        if (this.sideConfig != null && InputOnlyEnergy.enforceInputOnly(this.sideConfig.config())) {
            setChanged();
        }
    }

    // --- state ----------------------------------------------------------------------------------

    public DeviceAccessSettings settings() {
        return settings;
    }

    public RedstoneMode redstoneMode() {
        return redstoneMode;
    }

    public SecurityModuleSlots modules() {
        return modules;
    }

    /** Hardness modules fitted (the client reads the synced count, for its break-progress prediction). */
    public int hardnessModules() {
        if (level != null && level.isClientSide()) {
            return visualHardness;
        }
        return modules.count(SecurityModule.HARDNESS);
    }

    // --- renderer reads (synced; on the server these are the values last sent) ------------------

    /** The door's minimum level, for the status LED colour. */
    public AccessLevel renderMinLevel() {
        return visualMinLevel;
    }

    /** Linked and the audit log is on: the recording LED blinks. */
    public boolean renderRecording() {
        return visualRecording;
    }

    /** Linked, power required, and not enough stored to open: the status LED shows the warning blink. */
    public boolean renderPowerLow() {
        return visualPowerLow;
    }

    /** The installation is in lockdown: clamps on, the status LED strobes. */
    public boolean renderSealed() {
        return visualSealed;
    }

    public int openTicks() {
        return openTicks;
    }

    public boolean opening() {
        return opening;
    }

    /** The game time of the last start/finish of a movement (for client-side interpolation). */
    public long changedAt() {
        return changedAt;
    }

    /** Ticks for a full open or close: {@code doorOpenTicks / (1 + 0.5 x open-speed modules)}, min 5. */
    public int openDuration() {
        if (level != null && level.isClientSide() && clientDuration > 0) {
            return clientDuration;
        }
        return DoorRules.openDuration(NeroSecurityConfig.doorOpenTicks(), modules.count(SecurityModule.OPEN_SPEED));
    }

    /** 0 = closed, 1 = fully open. A client read for the renderer. */
    public float openProgress() {
        return Math.min(1.0F, openTicks / (float) Math.max(1, openDuration()));
    }

    public boolean isFullyOpen() {
        return opening && openTicks >= openDuration();
    }

    /** Whether the door is shut and linked (a closed public door is not "locked"). */
    public boolean isLocked() {
        return !opening && openTicks == 0 && installation() != null;
    }

    public boolean hasPowerToOpen() {
        return DoorRules.hasPowerToOpen(NeroSecurityConfig.doorsRequirePower(), energy.getAmount(),
                NeroSecurityConfig.doorOpenCost());
    }

    /** The installation this door is linked to, looked up now; null when unlinked or client-side. */
    public @Nullable Installation installation() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        return SecurityInstallations.get(serverLevel.getServer()).atDevice(devicePos(serverLevel)).orElse(null);
    }

    public DevicePos devicePos(ServerLevel serverLevel) {
        return DevicePos.of(serverLevel, worldPosition);
    }

    /** Whether lockdown is enforced for {@code installation} right now. */
    public static boolean lockdownActive(@Nullable Installation installation) {
        return installation != null && NeroSecurityConfig.isLockdownEnabled() && installation.lockdown().active();
    }

    /** Back to the defaults (MEMBER, not keycard-only, redstone ignored): called when the door is linked. */
    public void resetSettings() {
        applySettings(DeviceAccessSettings.DEFAULT, RedstoneMode.IGNORE);
    }

    /** Applies settings from the settings screen (already authorised by the payload handler). */
    public void applySettings(DeviceAccessSettings newSettings, RedstoneMode mode) {
        this.settings = newSettings == null ? DeviceAccessSettings.DEFAULT : newSettings;
        this.redstoneMode = mode == null ? RedstoneMode.IGNORE : mode;
        setChanged();
    }

    private void onModulesChanged() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            int duration = openDuration();
            if (openTicks > duration) {
                openTicks = duration;
            }
            sync();
        }
    }

    // --- ticking --------------------------------------------------------------------------------

    @Override
    protected void serverTick(Level level, BlockPos pos, BlockState state) {
        // Runs before Core's side-config tick, so no face can auto-eject even for one tick.
        enforceEnergyRouting();
        if (!(level instanceof ServerLevel serverLevel)
                || !(state.getBlock() instanceof AbstractSecurityDoorBlock door)) {
            return;
        }
        Installation installation = SecurityInstallations.get(serverLevel.getServer())
                .atDevice(devicePos(serverLevel)).orElse(null);
        boolean linked = installation != null;
        boolean lockdown = lockdownActive(installation);

        // 1. Idle draw while a linked door stands shut.
        int idle = NeroSecurityConfig.doorPowerPerTick();
        if (linked && idle > 0 && energy.getAmount() > 0 && !opening && openTicks == 0) {
            energy.consume(idle);
        }

        // 2. Animate.
        int duration = openDuration();
        if (opening && openTicks < duration) {
            openTicks++;
            if (openTicks >= duration) {
                openTicks = duration;
                door.setOpen(level, pos, true);
                autoCloseTicks = NeroSecurityConfig.doorAutoCloseTicks();
                (isBlast() ? SecuritySounds.BLAST_SETTLE : SecuritySounds.DOOR_SETTLE).play(level, pos);
                sync();
            }
        } else if (!opening && openTicks > 0) {
            openTicks--;
            if (openTicks == 0) {
                (isBlast() ? SecuritySounds.BLAST_SETTLE : SecuritySounds.DOOR_SETTLE).play(level, pos);
                sync();
            }
        } else if (openTicks > duration) {
            openTicks = duration;
        }

        // 3. A fully open door: lockdown seals it, else the auto-close countdown.
        if (isFullyOpen()) {
            if (DoorRules.lockdownCloses(lockdown, openedPrivileged)) {
                close();
            } else if (!openedByRedstone && autoCloseTicks > 0 && --autoCloseTicks == 0) {
                close();
            }
        }

        // 4. Redstone.
        boolean redstoneActive = redstoneMode == RedstoneMode.OPEN_WHILE_POWERED && NeroSecurityConfig.doorRedstoneAllowed();
        if (!redstoneActive) {
            openedByRedstone = false;
        } else {
            boolean powered = door.anyPartPowered(level, pos, state.getValue(AbstractSecurityDoorBlock.FACING));
            if (!opening && DoorRules.redstoneMayOpen(redstoneMode, true, powered, lockdown, hasPowerToOpen())) {
                startOpening(null, false, linked ? installation : null);
                openedByRedstone = true;
            } else if (!powered && opening && openedByRedstone) {
                close();
            }
        }

        // 5. Power loss, once per transition.
        boolean starved = NeroSecurityConfig.doorsRequirePower() && energy.getAmount() < NeroSecurityConfig.doorOpenCost();
        if (linked && starved && !powerLostReported) {
            powerLostReported = true;
            setChanged();
            publish(SecurityEventType.POWER_LOST, installation, serverLevel, null);
        } else if (!starved && powerLostReported) {
            powerLostReported = false;
            setChanged();
        }

        // 6. Visual state (renderer only): ANIMATING / SEALED on every part, the LED inputs in the update tag.
        updateVisuals(level, pos, door, linked, lockdown);
    }

    /**
     * Server: re-derives the seal and the LED inputs now instead of on the next tick (the lockdown hook's
     * accelerator). The next tick would reach the same answer.
     */
    public void refreshVisual(ServerLevel serverLevel) {
        if (isRemoved() || !(getBlockState().getBlock() instanceof AbstractSecurityDoorBlock door)) {
            return;
        }
        Installation installation = SecurityInstallations.get(serverLevel.getServer())
                .atDevice(devicePos(serverLevel)).orElse(null);
        updateVisuals(serverLevel, worldPosition, door, installation != null, lockdownActive(installation));
    }

    /** The minimum spacing of visual-only update-tag resends (a lockdown flip is never held back). */
    private static final int VISUAL_SYNC_MIN_TICKS = 10;

    private boolean isBlast() {
        return getBlockState().getBlock() instanceof BlastDoorBlock;
    }

    /**
     * Brings the visual block-state properties and the renderer's update-tag fields up to date. Block states
     * change only on a flip (with the clamp sound when {@code SEALED} flips); the update tag is re-sent only
     * when an LED input changed, at most every {@link #VISUAL_SYNC_MIN_TICKS} unless the seal flipped.
     */
    private void updateVisuals(Level level, BlockPos pos, AbstractSecurityDoorBlock door, boolean linked,
            boolean lockdown) {
        boolean sealed = lockdown; // lockdownActive() already requires a linked installation
        boolean animating = DoorRules.animating(opening, openTicks);
        BlockState now = level.getBlockState(pos);
        if (now.getBlock() == door) {
            boolean wasSealed = now.getValue(AbstractSecurityDoorBlock.SEALED);
            if (now.getValue(AbstractSecurityDoorBlock.ANIMATING) != animating || wasSealed != sealed) {
                door.setVisual(level, pos, animating, sealed);
                if (wasSealed != sealed) {
                    (sealed ? SecuritySounds.CLAMP : SecuritySounds.UNCLAMP).play(level, pos);
                }
            }
        }
        AccessLevel minLevel = settings.minLevel();
        boolean recording = VisualState.recording(linked, NeroSecurityConfig.isAuditLogEnabled());
        boolean powerLow = DoorRules.powerLow(linked, NeroSecurityConfig.doorsRequirePower(), energy.getAmount(),
                NeroSecurityConfig.doorOpenCost());
        int hardness = modules.count(SecurityModule.HARDNESS);
        boolean changed = !visualSent || minLevel != visualMinLevel || recording != visualRecording
                || powerLow != visualPowerLow || sealed != visualSealed || hardness != visualHardness;
        if (!changed) {
            return;
        }
        long time = level.getGameTime();
        boolean urgent = !visualSent || sealed != visualSealed;
        if (!urgent && visualSentAt != Long.MIN_VALUE && time - visualSentAt < VISUAL_SYNC_MIN_TICKS
                && time >= visualSentAt) {
            return; // picked up on a later tick
        }
        visualSent = true;
        visualSentAt = time;
        visualMinLevel = minLevel;
        visualRecording = recording;
        visualPowerLow = powerLow;
        visualSealed = sealed;
        visualHardness = hardness;
        sync();
    }

    private void startOpening(@Nullable UUID actor, boolean privileged, @Nullable Installation installation) {
        opening = true;
        openedPrivileged = privileged;
        openedByRedstone = false;
        int cost = NeroSecurityConfig.doorOpenCost();
        if (cost > 0 && energy.getAmount() > 0) {
            energy.consume(cost);
        }
        sync();
        if (level != null) {
            (isBlast() ? SecuritySounds.BLAST_OPEN : SecuritySounds.DOOR_OPEN).play(level, worldPosition);
        }
        if (installation != null && level instanceof ServerLevel serverLevel) {
            publish(SecurityEventType.DOOR_OPENED, installation, serverLevel, actor);
        }
    }

    /** Starts closing: {@code OPEN} turns false at once, so the door is solid again immediately. */
    public void close() {
        boolean wasMoving = opening || openTicks > 0;
        boolean wasOpening = opening;
        opening = false;
        autoCloseTicks = 0;
        openedPrivileged = false;
        openedByRedstone = false;
        if (level != null && getBlockState().getBlock() instanceof AbstractSecurityDoorBlock door) {
            door.setOpen(level, worldPosition, false);
        }
        if (wasMoving) {
            sync();
        }
        if (wasOpening && level != null) {
            (isBlast() ? SecuritySounds.BLAST_CLOSE : SecuritySounds.DOOR_CLOSE).play(level, worldPosition);
        }
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            changedAt = level.getGameTime();
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    // --- interaction ----------------------------------------------------------------------------

    /** A player used any part of the door (server thread). Sneaking opens the settings screen. */
    public void use(ServerPlayer player, boolean sneaking) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        DevicePos at = devicePos(serverLevel);
        Installation installation = SecurityInstallations.get(serverLevel.getServer()).atDevice(at).orElse(null);
        if (sneaking) {
            openSettings(player, installation, at);
            return;
        }
        if (installation == null) {
            // An unlinked door is public: it toggles freely and publishes nothing.
            if (opening) {
                close();
            } else {
                startOpening(null, false, null);
            }
            return;
        }
        AccessDecision decision = AccessResolver.DEFAULT.resolve(player, installation, AccessAction.OPEN,
                settings.minLevel(), at, settings.grantingSources());
        if (!decision.allowed()) {
            player.sendOverlayMessage(decision.message());
            SecurityDevice.denyFeedback(player, worldPosition);
            denied(player, installation, serverLevel);
            return;
        }
        if (!DoorRules.passesPowerGate(decision, hasPowerToOpen())) {
            player.sendOverlayMessage(Component.translatable("door.nerosecurity.no_power"));
            SecurityDevice.denyFeedback(player, worldPosition);
            denied(player, installation, serverLevel);
            return;
        }
        SecurityDevice.acceptFeedback(player, worldPosition);
        if (opening) {
            close(); // closing is never denied to someone allowed to open, and emits nothing
        } else {
            startOpening(player.getUUID(), DoorRules.isPrivileged(decision), installation);
        }
    }

    /**
     * Sneak-use: CONFIGURE_DEVICE at OFFICER, never keycard-filtered. An unlinked door has no settings
     * screen (linking resets its settings), so nobody can pre-configure it before its owner links it.
     */
    private void openSettings(ServerPlayer player, @Nullable Installation installation, DevicePos at) {
        if (installation == null) {
            player.sendOverlayMessage(Component.translatable("device.nerosecurity.unlinked"));
            return;
        }
        AccessDecision decision = resolveConfigure(player, installation, at);
        if (!decision.allowed()) {
            player.sendOverlayMessage(decision.message());
            SecurityDevice.denyFeedback(player, worldPosition);
            return;
        }
        DoorPayloadHandlers.sendSnapshot(player, this, installation, decision);
        MenuOpener.open(player, this);
    }

    /** The settings-screen decision; the payload handler re-runs it for every change. */
    public static AccessDecision resolveConfigure(ServerPlayer player, @Nullable Installation installation, DevicePos at) {
        AccessLevel required = installation == null ? AccessLevel.PUBLIC : AccessLevel.OFFICER;
        return AccessResolver.DEFAULT.resolve(player, installation, AccessAction.CONFIGURE_DEVICE, required, at, null);
    }

    private void denied(ServerPlayer player, Installation installation, ServerLevel serverLevel) {
        long now = serverLevel.getGameTime();
        UUID actor = player.getUUID();
        long last = deniedAt.getOrDefault(actor, Long.MIN_VALUE);
        int cooldown = NeroSecurityConfig.doorDeniedEventCooldownTicks();
        if (!DoorRules.deniedEventDue(last, now, cooldown)) {
            return;
        }
        if (deniedAt.size() >= COOLDOWN_PRUNE_SIZE) {
            deniedAt.values().removeIf(at -> now - at >= cooldown);
        }
        deniedAt.put(actor, now);
        publish(SecurityEventType.DOOR_DENIED, installation, serverLevel, actor);
    }

    // --- breaking and removal -------------------------------------------------------------------

    /**
     * A player is breaking a part of this door (server). Anyone may; a breaker the resolver would not let
     * OPEN the door publishes {@code DOOR_FORCED}. Store and event work only.
     */
    public void onPlayerBreak(ServerPlayer player) {
        brokenByPlayer = true;
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        DevicePos at = devicePos(serverLevel);
        Installation installation = SecurityInstallations.get(serverLevel.getServer()).atDevice(at).orElse(null);
        if (installation == null) {
            return;
        }
        // Same filter as use(): "forced" means the resolver would refuse this player at this door as configured.
        AccessDecision decision = AccessResolver.DEFAULT.resolve(player, installation, AccessAction.OPEN,
                settings.minLevel(), at, settings.grantingSources());
        if (!decision.allowed()) {
            publish(SecurityEventType.DOOR_FORCED, installation, serverLevel, player.getUUID());
        }
    }

    /**
     * The controller is being replaced (not unloaded): drop the fitted modules, and if it was linked,
     * publish {@code DOOR_FORCED} for a non-player removal (explosion, command) and drop the link so the
     * installation keeps no ghost device. A player break already reported itself.
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
        if (!brokenByPlayer) {
            publish(SecurityEventType.DOOR_FORCED, installation, serverLevel, null);
        }
        InstallationService.unlinkByLoss(server, installation, at);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        deniedAt.clear();
    }

    private void publish(SecurityEventType type, Installation installation, ServerLevel serverLevel, @Nullable UUID actor) {
        SecurityEventBus.publish(SecurityEvent.of(type, installation.id(), devicePos(serverLevel), actor,
                System.currentTimeMillis()));
    }

    // --- persistence and sync -------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        settings.save(output.child(KEY_SETTINGS));
        output.putString(KEY_REDSTONE, redstoneMode.serializedName());
        modules.save(output.child(KEY_MODULES));
        output.putInt(KEY_OPEN_TICKS, openTicks);
        output.putBoolean(KEY_OPENING, opening);
        output.putInt(KEY_AUTO_CLOSE, autoCloseTicks);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        if (this.sideConfig != null) {
            InputOnlyEnergy.enforceInputOnly(this.sideConfig.config());
        }
        this.settings = DeviceAccessSettings.load(input.childOrEmpty(KEY_SETTINGS));
        this.redstoneMode = RedstoneMode.byName(input.getStringOr(KEY_REDSTONE, ""));
        modules.load(input.childOrEmpty(KEY_MODULES));
        this.openTicks = Math.max(0, input.getIntOr(KEY_OPEN_TICKS, 0));
        this.opening = input.getBooleanOr(KEY_OPENING, false);
        this.autoCloseTicks = Math.max(0, input.getIntOr(KEY_AUTO_CLOSE, 0));
        // Renderer-only fields: present in the client's update tag, never written to disk.
        this.clientDuration = input.getIntOr(KEY_DURATION, -1);
        this.changedAt = input.getLongOr(KEY_SYNC_TIME, 0L);
        this.visualMinLevel = AccessLevel.byName(input.getStringOr(KEY_MIN_LEVEL, "")).orElse(AccessLevel.MEMBER);
        this.visualRecording = input.getBooleanOr(KEY_RECORDING, false);
        this.visualPowerLow = input.getBooleanOr(KEY_POWER_LOW, false);
        this.visualSealed = input.getBooleanOr(KEY_SEALED, false);
        this.visualHardness = Math.max(0, input.getIntOr(KEY_HARDNESS, 0));
        this.visualSent = false;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * Only what the renderer needs (S3-11): the movement, its anchor and duration, and the LED inputs. Never the
     * settings, modules, energy, side configuration or anything about a player.
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(KEY_OPEN_TICKS, openTicks);
        tag.putBoolean(KEY_OPENING, opening);
        tag.putLong(KEY_SYNC_TIME, changedAt);
        tag.putInt(KEY_DURATION, openDuration());
        tag.putString(KEY_MIN_LEVEL, visualMinLevel.jsonName());
        tag.putBoolean(KEY_RECORDING, visualRecording);
        tag.putBoolean(KEY_POWER_LOW, visualPowerLow);
        tag.putBoolean(KEY_SEALED, visualSealed);
        tag.putInt(KEY_HARDNESS, visualHardness);
        return tag;
    }

    // --- menu -----------------------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("door.nerosecurity.title");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new SecurityDoorMenu(containerId, inventory, this);
    }
}
