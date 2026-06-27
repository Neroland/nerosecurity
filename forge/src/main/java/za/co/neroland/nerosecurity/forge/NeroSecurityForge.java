package za.co.neroland.nerosecurity.forge;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import za.co.neroland.nerosecurity.NeroSecurityCommon;

/** MinecraftForge entry point for NeroSecurity. */
@Mod(NeroSecurityCommon.MOD_ID)
public final class NeroSecurityForge {

    public NeroSecurityForge(FMLJavaModLoadingContext context) {
        NeroSecurityCommon.LOGGER.info("[NeroSecurity] Forge bootstrap");
        NeroSecurityCommon.init();
    }
}
