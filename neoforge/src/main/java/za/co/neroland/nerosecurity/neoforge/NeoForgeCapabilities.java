package za.co.neroland.nerosecurity.neoforge;

import net.minecraft.core.Direction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.DelegatingResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import za.co.neroland.nerolandcore.platform.NeoForgeEnergyLookup;

import za.co.neroland.nerosecurity.block.camera.CameraEnergyAccess;
import za.co.neroland.nerosecurity.block.chest.GatedChestAccess;
import za.co.neroland.nerosecurity.block.chest.SecureChestBlockEntity;
import za.co.neroland.nerosecurity.block.laser.LaserEnergyAccess;
import za.co.neroland.nerosecurity.energy.DoorEnergyAccess;
import za.co.neroland.nerosecurity.energy.FieldEnergyAccess;
import za.co.neroland.nerosecurity.registry.CameraRegistrations;
import za.co.neroland.nerosecurity.registry.ChestRegistrations;
import za.co.neroland.nerosecurity.registry.DoorRegistrations;
import za.co.neroland.nerosecurity.registry.FieldRegistrations;
import za.co.neroland.nerosecurity.registry.LaserRegistrations;

/**
 * NeoForge capability wiring: Core's shared energy capability on both doors (every part of a door, not
 * only the controller, resolves to the controller's side-gated buffer), on the laser grid node and on the
 * security camera and the force field emitter (every view insert-only), and the secure chest's gated item handler. The chest wrapper re-evaluates the automation gate on every single call, so a policy,
 * settings or lockdown change applies on the next transfer with no capability invalidation.
 */
public final class NeoForgeCapabilities {

    private NeoForgeCapabilities() {
    }

    /** Called once from the NeoForge entry point. */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(NeoForgeCapabilities::onRegisterCapabilities);
    }

    private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        // Doors: the controller block entities first, then every door block so the non-controller
        // parts (which carry no block entity) accept power too. Both resolve through DoorEnergyAccess.
        event.registerBlockEntity(NeoForgeEnergyLookup.ENERGY, DoorRegistrations.SECURITY_DOOR_ENTITY.get(),
                (be, side) -> DoorEnergyAccess.of(be, side));
        event.registerBlockEntity(NeoForgeEnergyLookup.ENERGY, DoorRegistrations.BLAST_DOOR_ENTITY.get(),
                (be, side) -> DoorEnergyAccess.of(be, side));
        event.registerBlock(NeoForgeEnergyLookup.ENERGY,
                (level, pos, state, be, side) -> DoorEnergyAccess.at(level, pos, side),
                DoorRegistrations.SECURITY_DOOR.get(), DoorRegistrations.BLAST_DOOR.get());

        // Laser grid node and security camera: insert-only views of their buffers, for every side.
        event.registerBlockEntity(NeoForgeEnergyLookup.ENERGY, LaserRegistrations.LASER_GRID_NODE_ENTITY.get(),
                (be, side) -> LaserEnergyAccess.of(be, side));
        event.registerBlockEntity(NeoForgeEnergyLookup.ENERGY, CameraRegistrations.SECURITY_CAMERA_ENTITY.get(),
                (be, side) -> CameraEnergyAccess.of(be, side));

        // Force field emitter: insert-only for every side, so no cable can drain the field down.
        event.registerBlockEntity(NeoForgeEnergyLookup.ENERGY, FieldRegistrations.EMITTER_BE.get(),
                (be, side) -> FieldEnergyAccess.of(be, side));

        // Secure chest: always sided (an unsided query is treated as UP), always gated.
        event.registerBlockEntity(Capabilities.Item.BLOCK, ChestRegistrations.SECURE_CHEST_BLOCK_ENTITY.get(),
                (be, side) -> new GatedChestItemHandler(be, side == null ? Direction.UP : side));
    }

    /** The secure chest's item handler: the sided vanilla view, closed whenever automation is not allowed. */
    private static final class GatedChestItemHandler extends DelegatingResourceHandler<ItemResource> {

        private final SecureChestBlockEntity be;

        GatedChestItemHandler(SecureChestBlockEntity be, Direction side) {
            super(new WorldlyContainerWrapper(be, side));
            this.be = be;
        }

        private boolean open() {
            return GatedChestAccess.allows(this.be);
        }

        @Override
        public int size() {
            return open() ? super.size() : 0;
        }

        @Override
        public ItemResource getResource(int index) {
            return open() ? super.getResource(index) : ItemResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int index) {
            return open() ? super.getAmountAsLong(index) : 0L;
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            return open() ? super.getCapacityAsLong(index, resource) : 0L;
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return open() && super.isValid(index, resource);
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return open() ? super.insert(index, resource, amount, transaction) : 0;
        }

        @Override
        public int insert(ItemResource resource, int amount, TransactionContext transaction) {
            return open() ? super.insert(resource, amount, transaction) : 0;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return open() ? super.extract(index, resource, amount, transaction) : 0;
        }

        @Override
        public int extract(ItemResource resource, int amount, TransactionContext transaction) {
            return open() ? super.extract(resource, amount, transaction) : 0;
        }
    }
}
