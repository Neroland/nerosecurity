package za.co.neroland.nerosecurity.fabric;

import net.fabricmc.api.ModInitializer;

import za.co.neroland.nerosecurity.NeroSecurityCommon;

/** Fabric entry point for NeroSecurity. */
public final class NeroSecurityFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        NeroSecurityCommon.LOGGER.info("[NeroSecurity] Fabric bootstrap");
        NeroSecurityCommon.init();
    }
}
