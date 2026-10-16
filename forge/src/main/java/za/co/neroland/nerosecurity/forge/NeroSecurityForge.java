package za.co.neroland.nerosecurity.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;

import za.co.neroland.nerosecurity.NeroSecurityCommon;

/** MinecraftForge entry point for NeroSecurity. */
@Mod(NeroSecurityCommon.MOD_ID)
public final class NeroSecurityForge {

    public NeroSecurityForge(FMLJavaModLoadingContext context) {
        NeroSecurityCommon.LOGGER.info("[NeroSecurity] Forge bootstrap");
        // Common init declares the payloads; the channel below is sealed the moment it is built,
        // so that ordering is mandatory on Forge.
        NeroSecurityCommon.init();
        // Common init created NeroSecurity's deferred registers through Core's registration seam;
        // this attaches them to our mod bus group.
        RegistrationProvider.attach(context.getModBusGroup());
        ForgeSecurityNetwork.register();
        ForgeSecurityEvents.register();
        // Door energy and the secure chest's gated item handler (AttachCapabilitiesEvent).
        ForgeCapabilities.register();
        // Client-only: screens and the disconnect cache clear. Gated so client classes never load on
        // a dedicated server.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ForgeClientSetup.init(context.getModBusGroup());
        }
    }
}
