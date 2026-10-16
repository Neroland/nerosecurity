package za.co.neroland.nerosecurity.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.gui.screens.MenuScreens;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.client.screen.KeycardProgrammerScreen;
import za.co.neroland.nerosecurity.client.screen.SecurityHubScreen;
import za.co.neroland.nerosecurity.client.screen.alarm.AlarmScreen;
import za.co.neroland.nerosecurity.client.screen.chest.SecureChestSettingsScreen;
import za.co.neroland.nerosecurity.client.screen.door.SecurityDoorScreen;
import za.co.neroland.nerosecurity.client.screen.field.FieldSettingsScreen;
import za.co.neroland.nerosecurity.client.screen.monitor.MonitorScreen;
import za.co.neroland.nerosecurity.client.screen.sensor.SensorSettingsScreen;
import za.co.neroland.nerosecurity.network.SecurityNetwork;
import za.co.neroland.nerosecurity.registry.AlarmRegistrations;
import za.co.neroland.nerosecurity.registry.ChestRegistrations;
import za.co.neroland.nerosecurity.registry.DoorRegistrations;
import za.co.neroland.nerosecurity.registry.FieldRegistrations;
import za.co.neroland.nerosecurity.registry.LaserRegistrations;
import za.co.neroland.nerosecurity.registry.MonitorRegistrations;
import za.co.neroland.nerosecurity.registry.NeroSecurityMenus;
import za.co.neroland.nerosecurity.registry.ProgrammerRegistrations;

/** Fabric client entry point for NeroSecurity. */
public final class NeroSecurityFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        NeroSecurityCommon.LOGGER.info("[NeroSecurity] Fabric client bootstrap");
        // Clientbound receivers (client-only API) — registered here, off the dedicated server.
        FabricSecurityNetwork.registerClient();

        MenuScreens.register(NeroSecurityMenus.SECURITY_HUB.get(), SecurityHubScreen::new);
        MenuScreens.register(ProgrammerRegistrations.MENU.get(), KeycardProgrammerScreen::new);
        MenuScreens.register(DoorRegistrations.SECURITY_DOOR_MENU.get(), SecurityDoorScreen::new);
        MenuScreens.register(ChestRegistrations.SECURE_CHEST_SETTINGS_MENU.get(), SecureChestSettingsScreen::new);
        MenuScreens.register(LaserRegistrations.SENSOR_MENU.get(), SensorSettingsScreen::new);
        MenuScreens.register(AlarmRegistrations.ALARM_MENU.get(), AlarmScreen::new);
        MenuScreens.register(MonitorRegistrations.MENU.get(), MonitorScreen::new);
        MenuScreens.register(FieldRegistrations.FIELD_MENU.get(), FieldSettingsScreen::new);

        // Drop any synced mirror caches on leaving a world/server, so one session's security state
        // can never be shown in the next (or on a server that does not run NeroSecurity).
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                SecurityNetwork.clearClientCaches());
    }
}
