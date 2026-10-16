package za.co.neroland.nerosecurity.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

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

/**
 * NeoForge client-only setup: the hub, keycard programmer, door, secure chest settings, sensor settings,
 * alarm, monitor and force field settings screens, and the disconnect cache clear. Loaded only on the
 * physical client (the entry point gates on {@code Dist.CLIENT}).
 */
public final class NeoForgeClientSetup {

    private NeoForgeClientSetup() {
    }

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(NeoForgeClientSetup::onRegisterScreens);
        // Drop the synced hub mirror on leaving a world/server. This is on the GAME bus.
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) ->
                SecurityNetwork.clearClientCaches());
    }

    private static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(NeroSecurityMenus.SECURITY_HUB.get(), SecurityHubScreen::new);
        event.register(ProgrammerRegistrations.MENU.get(), KeycardProgrammerScreen::new);
        event.register(DoorRegistrations.SECURITY_DOOR_MENU.get(), SecurityDoorScreen::new);
        event.register(ChestRegistrations.SECURE_CHEST_SETTINGS_MENU.get(), SecureChestSettingsScreen::new);
        event.register(LaserRegistrations.SENSOR_MENU.get(), SensorSettingsScreen::new);
        event.register(AlarmRegistrations.ALARM_MENU.get(), AlarmScreen::new);
        event.register(MonitorRegistrations.MENU.get(), MonitorScreen::new);
        event.register(FieldRegistrations.FIELD_MENU.get(), FieldSettingsScreen::new);
    }
}
