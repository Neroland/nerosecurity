package za.co.neroland.nerosecurity.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.block.SecurityHubBlock;

/**
 * Block registrations through Neroland Core's {@link RegistrationProvider}. On Fabric these apply when
 * the class loads, which {@link #init()} forces from common init; on NeoForge/Forge the entry point
 * attaches the deferred registers to the mod bus.
 */
public final class NeroSecurityBlocks {

    public static final RegistrationProvider<Block> BLOCKS =
            RegistrationProvider.get(Registries.BLOCK, NeroSecurityCommon.MOD_ID);

    /**
     * The installation controller. Obsidian-class blast resistance. Pistons already refuse block-entity
     * blocks; the push reaction states the intent explicitly (26.3 renamed BLOCK to IMMOVEABLE).
     */
    public static final RegistryEntry<Block> SECURITY_HUB = BLOCKS.register("security_hub",
            key -> new SecurityHubBlock(BlockBehaviour.Properties.of()
                    .setId(key)
                    .mapColor(MapColor.COLOR_RED)
                    .strength(5.0F, 1200.0F)
                    .requiresCorrectToolForDrops()
                    //? if >=26.3 {
                    /*.pushReaction(PushReaction.IMMOVEABLE)
                    *///?} else {
                    .pushReaction(PushReaction.BLOCK)
                    //?}
                    .sound(SoundType.METAL)));

    private NeroSecurityBlocks() {
    }

    /** Classload-forcing no-op. */
    public static void init() {
    }
}
