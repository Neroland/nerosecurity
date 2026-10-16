package za.co.neroland.nerosecurity.registry;

import java.util.Set;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.block.entity.SecurityHubBlockEntity;

/**
 * Block-entity types through Neroland Core's {@link RegistrationProvider}.
 *
 * <p><b>Fabric 26.1.2:</b> the two-argument {@code BlockEntityType} constructor is private there (public
 * from 26.2), which is why {@code fabric/src/main/resources/nerosecurity.accesswidener} widens it.
 */
public final class NeroSecurityBlockEntities {

    public static final RegistrationProvider<BlockEntityType<?>> BLOCK_ENTITIES =
            RegistrationProvider.get(Registries.BLOCK_ENTITY_TYPE, NeroSecurityCommon.MOD_ID);

    public static final RegistryEntry<BlockEntityType<SecurityHubBlockEntity>> SECURITY_HUB =
            BLOCK_ENTITIES.register("security_hub",
                    key -> new BlockEntityType<>(SecurityHubBlockEntity::new,
                            Set.of(NeroSecurityBlocks.SECURITY_HUB.get())));

    private NeroSecurityBlockEntities() {
    }

    /** Classload-forcing no-op. */
    public static void init() {
    }
}
