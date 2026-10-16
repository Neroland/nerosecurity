package za.co.neroland.nerosecurity.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;

import za.co.neroland.nerosecurity.NeroSecurityCommon;

/** NeoForge entry point for NeroSecurity. */
@Mod(NeroSecurityCommon.MOD_ID)
public final class NeroSecurityNeoForge {

    public NeroSecurityNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        NeroSecurityCommon.LOGGER.info("[NeroSecurity] NeoForge bootstrap");
        // Common init declares the payloads and creates the deferred registrations; the calls below
        // consume those declarations.
        NeroSecurityCommon.init();
        // Common init created NeroSecurity's deferred registers through Core's registration seam;
        // this attaches them to our mod event bus.
        RegistrationProvider.attach(modEventBus);
        NeoForgeSecurityNetwork.register(modEventBus);
        NeoForgeSecurityEvents.register();
        // Door energy and the secure chest's gated item handler (RegisterCapabilitiesEvent, mod bus).
        NeoForgeCapabilities.register(modEventBus);
        // Client-only: screens and the disconnect cache clear. Gated so client classes never load on
        // a dedicated server.
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            NeoForgeClientSetup.init(modEventBus);
        }
    }
}
