package za.co.neroland.nerosecurity.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

import za.co.neroland.nerosecurity.NeroSecurityCommon;

/** NeoForge entry point for NeroSecurity. */
@Mod(NeroSecurityCommon.MOD_ID)
public final class NeroSecurityNeoForge {

    public NeroSecurityNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        NeroSecurityCommon.LOGGER.info("[NeroSecurity] NeoForge bootstrap");
        NeroSecurityCommon.init();
    }
}
