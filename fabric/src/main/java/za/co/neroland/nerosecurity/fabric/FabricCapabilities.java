package za.co.neroland.nerosecurity.fabric;

import java.util.Collections;
import java.util.Iterator;

import net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

import za.co.neroland.nerolandcore.platform.FabricEnergyLookup;

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
 * Fabric capability wiring: Core's shared energy lookup on both doors (every part of a door resolves to
 * the controller's side-gated buffer), on the laser grid node, the security camera and the force field
 * emitter (every view insert-only), and the secure chest's gated item storage on the Transfer API. The
 * chest wrapper re-evaluates the automation gate on every single call.
 */
public final class FabricCapabilities {

    private FabricCapabilities() {
    }

    /** Called once from the Fabric entry point, after common init registered the types. */
    public static void register() {
        // Doors: the controller block entities, then every door block so the non-controller parts
        // (which carry no block entity) accept power too. Fabric consults the block providers first;
        // both resolve through DoorEnergyAccess.
        FabricEnergyLookup.ENERGY.registerForBlockEntity((be, dir) -> DoorEnergyAccess.of(be, dir),
                DoorRegistrations.SECURITY_DOOR_ENTITY.get());
        FabricEnergyLookup.ENERGY.registerForBlockEntity((be, dir) -> DoorEnergyAccess.of(be, dir),
                DoorRegistrations.BLAST_DOOR_ENTITY.get());
        FabricEnergyLookup.ENERGY.registerForBlocks(
                (level, pos, state, be, dir) -> DoorEnergyAccess.at(level, pos, dir),
                DoorRegistrations.SECURITY_DOOR.get(), DoorRegistrations.BLAST_DOOR.get());

        // Laser grid node and security camera: insert-only views of their buffers, for every side.
        FabricEnergyLookup.ENERGY.registerForBlockEntity((be, dir) -> LaserEnergyAccess.of(be, dir),
                LaserRegistrations.LASER_GRID_NODE_ENTITY.get());
        FabricEnergyLookup.ENERGY.registerForBlockEntity((be, dir) -> CameraEnergyAccess.of(be, dir),
                CameraRegistrations.SECURITY_CAMERA_ENTITY.get());

        // Force field emitter: insert-only for every side, so no cable can drain the field down.
        FabricEnergyLookup.ENERGY.registerForBlockEntity((be, dir) -> FieldEnergyAccess.of(be, dir),
                FieldRegistrations.EMITTER_BE.get());

        // Secure chest: always sided, always gated.
        ItemStorage.SIDED.registerForBlockEntity(
                (be, dir) -> new GatedChestStorage(be, ContainerStorage.of(be, dir)),
                ChestRegistrations.SECURE_CHEST_BLOCK_ENTITY.get());
    }

    /** The secure chest's item storage: the sided vanilla view, closed whenever automation is not allowed. */
    private static final class GatedChestStorage implements Storage<ItemVariant> {

        private final SecureChestBlockEntity be;
        private final Storage<ItemVariant> delegate;

        GatedChestStorage(SecureChestBlockEntity be, Storage<ItemVariant> delegate) {
            this.be = be;
            this.delegate = delegate;
        }

        private boolean open() {
            return GatedChestAccess.allows(this.be);
        }

        @Override
        public boolean supportsInsertion() {
            return open() && this.delegate.supportsInsertion();
        }

        @Override
        public boolean supportsExtraction() {
            return open() && this.delegate.supportsExtraction();
        }

        @Override
        public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            return open() ? this.delegate.insert(resource, maxAmount, transaction) : 0L;
        }

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            return open() ? this.delegate.extract(resource, maxAmount, transaction) : 0L;
        }

        @Override
        public Iterator<StorageView<ItemVariant>> iterator() {
            return open() ? this.delegate.iterator() : Collections.emptyIterator();
        }
    }
}
