package za.co.neroland.nerosecurity.forge;

import java.util.EnumMap;
import java.util.function.BiFunction;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.SidedInvWrapper;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.energy.NeroEnergyStorage;
import za.co.neroland.nerolandcore.platform.ForgeEnergyLookup;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.block.camera.CameraEnergyAccess;
import za.co.neroland.nerosecurity.block.camera.SecurityCameraBlockEntity;
import za.co.neroland.nerosecurity.block.chest.GatedChestAccess;
import za.co.neroland.nerosecurity.block.chest.SecureChestBlockEntity;
import za.co.neroland.nerosecurity.block.door.SecurityDoorBlockEntity;
import za.co.neroland.nerosecurity.block.field.ForceFieldEmitterBlockEntity;
import za.co.neroland.nerosecurity.block.laser.LaserEnergyAccess;
import za.co.neroland.nerosecurity.block.laser.LaserGridNodeBlockEntity;
import za.co.neroland.nerosecurity.energy.DoorEnergyAccess;
import za.co.neroland.nerosecurity.energy.FieldEnergyAccess;

/**
 * Forge capability wiring: Core's shared energy capability on both door controllers, the laser grid node,
 * the security camera and the force field emitter, and the secure chest's gated item handler. Forge attaches capabilities to block
 * entities only, so a door takes power through its controller part (the non-controller parts carry no
 * block entity); NeoForge and Fabric also expose every part.
 *
 * <p>Every view is dynamic: an energy view asks its device's access class ({@link DoorEnergyAccess},
 * {@link LaserEnergyAccess}, {@link CameraEnergyAccess}, {@link FieldEnergyAccess}) again on every call, and each of those returns
 * the insert-only external view (a face's side mode can change at any time and Forge has no invalidation
 * hook for it); the chest handler re-evaluates the automation gate on every call. None is ever an
 * unsided or unchecked wrapper.
 */
public final class ForgeCapabilities {

    private static final Identifier DOOR_ENERGY =
            Identifier.fromNamespaceAndPath(NeroSecurityCommon.MOD_ID, "door_energy");
    private static final Identifier LASER_ENERGY =
            Identifier.fromNamespaceAndPath(NeroSecurityCommon.MOD_ID, "laser_energy");
    private static final Identifier CAMERA_ENERGY =
            Identifier.fromNamespaceAndPath(NeroSecurityCommon.MOD_ID, "camera_energy");
    private static final Identifier FIELD_ENERGY =
            Identifier.fromNamespaceAndPath(NeroSecurityCommon.MOD_ID, "field_energy");
    private static final Identifier CHEST_ITEMS =
            Identifier.fromNamespaceAndPath(NeroSecurityCommon.MOD_ID, "secure_chest_items");

    private ForgeCapabilities() {
    }

    /** Called once from the Forge entry point. */
    public static void register() {
        AttachCapabilitiesEvent.BlockEntities.BUS.addListener(ForgeCapabilities::onAttachBlockEntity);
    }

    private static void onAttachBlockEntity(AttachCapabilitiesEvent.BlockEntities event) {
        if (event.getObject() instanceof SecurityDoorBlockEntity door) {
            EnergyProvider<SecurityDoorBlockEntity> provider = new EnergyProvider<>(door, DoorEnergyAccess::of);
            event.addCapability(DOOR_ENERGY, provider);
            event.addListener(provider::invalidate);
        } else if (event.getObject() instanceof LaserGridNodeBlockEntity node) {
            EnergyProvider<LaserGridNodeBlockEntity> provider = new EnergyProvider<>(node, LaserEnergyAccess::of);
            event.addCapability(LASER_ENERGY, provider);
            event.addListener(provider::invalidate);
        } else if (event.getObject() instanceof SecurityCameraBlockEntity camera) {
            EnergyProvider<SecurityCameraBlockEntity> provider =
                    new EnergyProvider<>(camera, CameraEnergyAccess::of);
            event.addCapability(CAMERA_ENERGY, provider);
            event.addListener(provider::invalidate);
        } else if (event.getObject() instanceof ForceFieldEmitterBlockEntity emitter) {
            EnergyProvider<ForceFieldEmitterBlockEntity> provider =
                    new EnergyProvider<>(emitter, FieldEnergyAccess::of);
            event.addCapability(FIELD_ENERGY, provider);
            event.addListener(provider::invalidate);
        } else if (event.getObject() instanceof SecureChestBlockEntity chest) {
            ChestProvider provider = new ChestProvider(chest);
            event.addCapability(CHEST_ITEMS, provider);
            event.addListener(provider::invalidate);
        }
    }

    /**
     * An energy view that resolves the device's insert-only external view again on every call. The
     * resolver is one of the device access classes, each of which returns
     * {@code InputOnlyEnergy.externalView(...)} (extract is always 0) or null once the block entity is
     * removed.
     */
    private record DynamicEnergy<B extends BlockEntity>(B be, BiFunction<B, Direction, NeroEnergyStorage> resolver,
            @Nullable Direction side) implements NeroEnergyStorage {

        @Nullable
        private NeroEnergyStorage current() {
            return this.resolver.apply(this.be, this.side);
        }

        @Override
        public long getAmount() {
            NeroEnergyStorage view = current();
            return view == null ? 0L : view.getAmount();
        }

        @Override
        public long getCapacity() {
            NeroEnergyStorage view = current();
            return view == null ? 0L : view.getCapacity();
        }

        @Override
        public long insert(long maxAmount, boolean simulate) {
            NeroEnergyStorage view = current();
            return view == null ? 0L : view.insert(maxAmount, simulate);
        }

        @Override
        public long extract(long maxAmount, boolean simulate) {
            NeroEnergyStorage view = current();
            return view == null ? 0L : view.extract(maxAmount, simulate);
        }
    }

    /** One energy capability provider for any powered device, over a per-call resolver. */
    private static final class EnergyProvider<B extends BlockEntity> implements ICapabilityProvider {

        private final B be;
        private final BiFunction<B, Direction, NeroEnergyStorage> resolver;
        private final EnumMap<Direction, LazyOptional<NeroEnergyStorage>> sided = new EnumMap<>(Direction.class);
        @Nullable
        private LazyOptional<NeroEnergyStorage> unsided;

        EnergyProvider(B be, BiFunction<B, Direction, NeroEnergyStorage> resolver) {
            this.be = be;
            this.resolver = resolver;
        }

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
            if (cap != ForgeEnergyLookup.ENERGY) {
                return LazyOptional.empty();
            }
            if (side == null) {
                if (this.unsided == null) {
                    this.unsided = LazyOptional.of(() -> new DynamicEnergy<>(this.be, this.resolver, null));
                }
                return this.unsided.cast();
            }
            return this.sided.computeIfAbsent(side,
                    d -> LazyOptional.<NeroEnergyStorage>of(() -> new DynamicEnergy<>(this.be, this.resolver, d)))
                    .cast();
        }

        void invalidate() {
            if (this.unsided != null) {
                this.unsided.invalidate();
                this.unsided = null;
            }
            this.sided.values().forEach(LazyOptional::invalidate);
            this.sided.clear();
        }
    }

    private static final class ChestProvider implements ICapabilityProvider {

        private final SecureChestBlockEntity chest;
        private final EnumMap<Direction, LazyOptional<IItemHandler>> sided = new EnumMap<>(Direction.class);

        ChestProvider(SecureChestBlockEntity chest) {
            this.chest = chest;
        }

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
            if (cap != net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER) {
                return LazyOptional.empty();
            }
            // Never an unsided wrapper: an unsided query is treated as UP.
            Direction d = side == null ? Direction.UP : side;
            return this.sided.computeIfAbsent(d,
                    k -> LazyOptional.<IItemHandler>of(() -> new GatedChestItemHandler(this.chest, k))).cast();
        }

        void invalidate() {
            this.sided.values().forEach(LazyOptional::invalidate);
            this.sided.clear();
        }
    }

    /** The secure chest's item handler: the sided vanilla view, closed whenever automation is not allowed. */
    private static final class GatedChestItemHandler implements IItemHandler {

        private final SecureChestBlockEntity be;
        private final SidedInvWrapper delegate;

        GatedChestItemHandler(SecureChestBlockEntity be, Direction side) {
            this.be = be;
            this.delegate = new SidedInvWrapper(be, side);
        }

        private boolean open() {
            return GatedChestAccess.allows(this.be);
        }

        @Override
        public int getSlots() {
            return open() ? this.delegate.getSlots() : 0;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return open() ? this.delegate.getStackInSlot(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return open() ? this.delegate.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return open() ? this.delegate.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return open() ? this.delegate.getSlotLimit(slot) : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return open() && this.delegate.isItemValid(slot, stack);
        }
    }
}
