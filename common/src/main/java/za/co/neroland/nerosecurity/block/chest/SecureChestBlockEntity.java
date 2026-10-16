package za.co.neroland.nerosecurity.block.chest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.access.AccessAction;
import za.co.neroland.nerosecurity.access.AccessDecision;
import za.co.neroland.nerosecurity.access.AccessResolver;
import za.co.neroland.nerosecurity.block.DeviceAccessSettings;
import za.co.neroland.nerosecurity.config.NeroSecurityConfig;
import za.co.neroland.nerosecurity.event.SecurityEvent;
import za.co.neroland.nerosecurity.event.SecurityEventBus;
import za.co.neroland.nerosecurity.event.SecurityEventType;
import za.co.neroland.nerosecurity.installation.DevicePos;
import za.co.neroland.nerosecurity.installation.Installation;
import za.co.neroland.nerosecurity.installation.InstallationService;
import za.co.neroland.nerosecurity.installation.SecurityInstallations;
import za.co.neroland.nerosecurity.menu.MenuOpener;
import za.co.neroland.nerosecurity.registry.ChestRegistrations;

/**
 * The secure chest's block entity: 27 slots, the shared {@link DeviceAccessSettings}, and the two
 * gates that protect the contents.
 *
 * <ul>
 *   <li><b>Players</b> open it through {@link #tryOpen}, which asks the resolver for OPEN at the chest's
 *       minimum level (keycards only when the chest is keycard-only). {@link #stillValid} asks again on
 *       every tick the menu is open, so a lockdown or a revoked card closes the screen within a tick.</li>
 *   <li><b>Automation</b> is gated as a {@link WorldlyContainer} here (vanilla hoppers and droppers on
 *       every loader) and again by the loader capability wrappers via {@link GatedChestAccess}.</li>
 * </ul>
 *
 * <p>It deliberately extends {@link BlockEntity} rather than {@code BaseContainerBlockEntity}: Forge
 * patches the latter to expose an ungated whole-inventory item handler on every side, which would walk
 * straight past the automation gate. No lock code, no custom name, and contents never travel on the
 * item: breaking the chest always drops them.
 *
 * <p>The block entity holds no installation pointer; every check looks the installation up from the
 * store by position. Audit events carry the event type, the installation, this device's position and
 * (when a player acted) that player only: never a slot, an item, a count or a name.
 */
public class SecureChestBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {

    public static final int SIZE = 27;

    private static final int[] ALL_SLOTS = IntStream.range(0, SIZE).toArray();
    private static final int[] NO_SLOTS = new int[0];
    private static final String KEY_SETTINGS = "Settings";
    /** Bound on the transient deny-cooldown table, so a crowd cannot grow it without limit. */
    private static final int MAX_DENY_ENTRIES = 64;
    private static final Component TITLE = Component.translatable("container.nerosecurity.secure_chest");

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private DeviceAccessSettings settings = DeviceAccessSettings.DEFAULT;

    // --- transient (never saved) ---------------------------------------------------------------
    /** Players with the chest open, each mapped to whether the contents changed during their session. */
    private final Map<UUID, Boolean> sessions = new HashMap<>();
    /** Game time of the last CHEST_DENIED event per actor (event volume only; the message always shows). */
    private final Map<UUID, Long> deniedAt = new HashMap<>();
    private long lastAutomationEventAt = Long.MIN_VALUE;
    /** Game time of the last player break, so the removal hook can tell a player break from anything else. */
    private long playerBreakAt = Long.MIN_VALUE;

    public SecureChestBlockEntity(BlockPos pos, BlockState state) {
        super(ChestRegistrations.SECURE_CHEST_BLOCK_ENTITY.get(), pos, state);
    }

    // --- settings -------------------------------------------------------------------------------

    public DeviceAccessSettings settings() {
        return settings;
    }

    /** Replaces the settings. Callers have already authorised the change. Not a contents change. */
    public void applySettings(DeviceAccessSettings next) {
        this.settings = next == null ? DeviceAccessSettings.DEFAULT : next;
        super.setChanged(); // saves the chunk without counting as a contents change
    }

    // --- installation lookups -------------------------------------------------------------------

    /** The installation this chest is linked to right now, or null (unlinked, or not on a server). */
    public @Nullable Installation installation() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        return SecurityInstallations.get(serverLevel.getServer())
                .atDevice(DevicePos.of(serverLevel, worldPosition)).orElse(null);
    }

    /** Whether lockdown is enabled on this server and in force on {@code installation}. */
    public static boolean lockdownActive(@Nullable Installation installation) {
        return installation != null && NeroSecurityConfig.isLockdownEnabled() && installation.lockdown().active();
    }

    /** OPEN at this chest's minimum level, counting only keycards when it is keycard-only. */
    public AccessDecision resolveOpen(ServerPlayer player, Installation installation) {
        DevicePos at = level instanceof ServerLevel serverLevel ? DevicePos.of(serverLevel, worldPosition) : null;
        return AccessResolver.DEFAULT.resolve(player, installation, AccessAction.OPEN, settings.minLevel(), at,
                settings.grantingSources());
    }

    /**
     * Whether automation may move items through this chest right now. Fails closed: off the server, on a
     * removed chest, or if the store lookup throws, the answer is no.
     */
    public boolean isAutomationAllowed() {
        if (!(level instanceof ServerLevel) || isRemoved()) {
            return false;
        }
        try {
            Installation installation = installation();
            return GatedChestAccess.automationAllowed(installation != null,
                    installation == null ? null : installation.automationLevel(), settings,
                    lockdownActive(installation));
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    // --- players --------------------------------------------------------------------------------

    /** A player used the chest (not sneaking): open it if the resolver allows, else explain and log. */
    public void tryOpen(ServerPlayer player) {
        Installation installation = installation();
        if (installation != null) {
            AccessDecision decision = resolveOpen(player, installation);
            if (!decision.allowed()) {
                player.sendOverlayMessage(decision.message());
                reportDenied(player, installation);
                return;
            }
        }
        // Unlinked: a plain chest, no events.
        MenuOpener.open(player, this);
    }

    private void reportDenied(ServerPlayer player, Installation installation) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        long now = serverLevel.getGameTime();
        Long last = deniedAt.get(player.getUUID());
        if (!GatedChestAccess.cooldownElapsed(last == null ? Long.MIN_VALUE : last, now,
                NeroSecurityConfig.doorDeniedEventCooldownTicks())) {
            return;
        }
        if (deniedAt.size() >= MAX_DENY_ENTRIES) {
            deniedAt.clear();
        }
        deniedAt.put(player.getUUID(), now);
        publish(SecurityEventType.CHEST_DENIED, installation, player.getUUID());
    }

    /**
     * Called from the block's {@code playerWillDestroy}. Anyone may break a secure chest; a breaker the
     * resolver would not let open it raises {@code CHEST_FORCED}. Never cancels anything.
     */
    public void onPlayerBreak(ServerPlayer breaker) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        this.playerBreakAt = serverLevel.getGameTime();
        Installation installation = installation();
        if (installation == null) {
            return;
        }
        boolean couldOpen;
        try {
            couldOpen = resolveOpen(breaker, installation).allowed();
        } catch (RuntimeException | LinkageError e) {
            couldOpen = false;
        }
        if (!couldOpen) {
            publish(SecurityEventType.CHEST_FORCED, installation, breaker.getUUID());
        }
    }

    /**
     * The block is being removed (not unloaded). The default drops the contents, always. Then, if it was
     * linked: anything other than a player break (an explosion, a command, another mod) raises
     * {@code CHEST_FORCED} with no actor, and the link is dropped so no ghost device remains. Store
     * bookkeeping only; the world is not touched beyond the vanilla content drop.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        sessions.clear();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        try {
            MinecraftServer server = serverLevel.getServer();
            DevicePos at = DevicePos.of(serverLevel, pos);
            Installation installation = SecurityInstallations.get(server).atDevice(at).orElse(null);
            if (installation == null) {
                return;
            }
            if (playerBreakAt != serverLevel.getGameTime()) {
                publish(SecurityEventType.CHEST_FORCED, installation, null);
            }
            InstallationService.unlinkByLoss(server, installation, at);
        } catch (RuntimeException | LinkageError e) {
            NeroSecurityCommon.LOGGER.debug("[NeroSecurity] secure chest removal bookkeeping failed ({})",
                    e.getClass().getName());
        }
    }

    /** Chunk unload or removal: drop transient tables only. No world access here. */
    @Override
    public void setRemoved() {
        deniedAt.clear();
        super.setRemoved();
    }

    // --- Container ------------------------------------------------------------------------------

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack result = ContainerHelper.removeItem(items, slot, count);
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        stack.limitSize(getMaxStackSize(stack));
        setChanged();
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    /**
     * Valid while the player is in range <b>and</b> the resolver still lets them open it, asked again on
     * every call (vanilla calls this each tick while the menu is open). Only server players qualify.
     */
    @Override
    public boolean stillValid(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel) || isRemoved()) {
            return false;
        }
        if (!Container.stillValidBlockEntity(this, player)) {
            return false;
        }
        try {
            Installation installation = installation();
            return installation == null || resolveOpen(serverPlayer, installation).allowed();
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    @Override
    public void startOpen(ContainerUser user) {
        if (isRemoved() || !(level instanceof ServerLevel) || !(user instanceof ServerPlayer player)) {
            return;
        }
        sessions.put(player.getUUID(), Boolean.FALSE);
        Installation installation = installation();
        if (installation != null) {
            publish(SecurityEventType.CHEST_OPENED, installation, player.getUUID());
        }
    }

    @Override
    public void stopOpen(ContainerUser user) {
        if (!(user instanceof ServerPlayer player)) {
            return;
        }
        Boolean changed = sessions.remove(player.getUUID());
        if (Boolean.TRUE.equals(changed)) {
            Installation installation = installation();
            if (installation != null) {
                publish(SecurityEventType.CHEST_CHANGED, installation, player.getUUID()); // once per session
            }
        }
    }

    /**
     * A contents change. With a player session open it marks every open session changed (reported once
     * when each closes); otherwise it is automation, reported with no actor and at most once per
     * {@code chestAutomationEventIntervalTicks}.
     */
    @Override
    public void setChanged() {
        super.setChanged();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!sessions.isEmpty()) {
            sessions.replaceAll((id, changed) -> Boolean.TRUE);
            return;
        }
        long now = serverLevel.getGameTime();
        if (!GatedChestAccess.cooldownElapsed(lastAutomationEventAt, now,
                NeroSecurityConfig.chestAutomationEventIntervalTicks())) {
            return;
        }
        Installation installation = installation();
        if (installation == null) {
            return;
        }
        lastAutomationEventAt = now;
        publish(SecurityEventType.CHEST_CHANGED, installation, null);
    }

    // --- WorldlyContainer (vanilla hoppers, droppers, and sided wrappers on every loader) --------

    @Override
    public int[] getSlotsForFace(Direction direction) {
        return isAutomationAllowed() ? ALL_SLOTS : NO_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
        return isAutomationAllowed();
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
        return isAutomationAllowed();
    }

    // --- MenuProvider ---------------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return TITLE;
    }

    /** The vanilla 3-row chest menu. Refuses (null) a server player the resolver would not let in. */
    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            Installation installation = installation();
            if (installation != null && !resolveOpen(serverPlayer, installation).allowed()) {
                return null;
            }
        }
        return ChestMenu.threeRows(containerId, inventory, this);
    }

    // --- persistence ----------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        settings.save(output.child(KEY_SETTINGS));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        settings = DeviceAccessSettings.load(input.childOrEmpty(KEY_SETTINGS));
    }

    // --- events ---------------------------------------------------------------------------------

    private void publish(SecurityEventType type, Installation installation, @Nullable UUID actor) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        SecurityEventBus.publish(SecurityEvent.of(type, installation.id(), DevicePos.of(serverLevel, worldPosition),
                actor, System.currentTimeMillis()));
    }
}
