package za.co.neroland.nerosecurity.fabric;

import net.fabricmc.api.ClientModInitializer;

import za.co.neroland.nerosecurity.NeroSecurityCommon;

/** Fabric client entry point for NeroSecurity. */
public final class NeroSecurityFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        NeroSecurityCommon.LOGGER.info("[NeroSecurity] Fabric client bootstrap");
    }
}
