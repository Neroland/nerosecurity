package za.co.neroland.nerosecurity.forge;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import za.co.neroland.nerosecurity.client.BerCommand;
import za.co.neroland.nerosecurity.client.feedback.ClientDeviceFeedback;
import za.co.neroland.nerosecurity.client.render.ClientBlockEntityRenderers;
import za.co.neroland.nerosecurity.client.render.DoorChestHubRenderers;
import za.co.neroland.nerosecurity.client.render.SensorFieldRenderers;
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
 * Forge client-only setup: the hub, keycard programmer, door, secure chest settings, sensor settings,
 * alarm, monitor and force field settings screens, and the disconnect cache clear. Loaded only on the
 * physical client (the entry point gates on {@code Dist.CLIENT}).
 */
public final class ForgeClientSetup {

    private ForgeClientSetup() {
    }

    public static void init(BusGroup modBusGroup) {
        FMLClientSetupEvent.getBus(modBusGroup).addListener(event -> event.enqueueWork(() -> {
            MenuScreens.register(NeroSecurityMenus.SECURITY_HUB.get(), SecurityHubScreen::new);
            MenuScreens.register(ProgrammerRegistrations.MENU.get(), KeycardProgrammerScreen::new);
            MenuScreens.register(DoorRegistrations.SECURITY_DOOR_MENU.get(), SecurityDoorScreen::new);
            MenuScreens.register(ChestRegistrations.SECURE_CHEST_SETTINGS_MENU.get(), SecureChestSettingsScreen::new);
            MenuScreens.register(LaserRegistrations.SENSOR_MENU.get(), SensorSettingsScreen::new);
            MenuScreens.register(AlarmRegistrations.ALARM_MENU.get(), AlarmScreen::new);
            MenuScreens.register(MonitorRegistrations.MENU.get(), MonitorScreen::new);
            MenuScreens.register(FieldRegistrations.FIELD_MENU.get(), FieldSettingsScreen::new);
        }));
        EntityRenderersEvent.RegisterRenderers.BUS.addListener(ForgeClientSetup::onRegisterRenderers);
        // Deny/accept sound and flash for the acting player (DeviceFeedback payload).
        ClientDeviceFeedback.init();
        // Client-only /nsber (BER budget readout).
        RegisterClientCommandsEvent.BUS.addListener(event ->
                BerCommand.register(event.getDispatcher()));
        // Forge 26.x has no global event bus; each event class owns a static BUS. A statement-bodied
        // lambda keeps the Consumer/Predicate overloads unambiguous.
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> {
            SecurityNetwork.clearClientCaches();
        });
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        ClientBlockEntityRenderers.registerAll(new ClientBlockEntityRenderers.Sink() {
                    @Override
                    public <T extends BlockEntity, S extends BlockEntityRenderState> void register(
                            BlockEntityType<? extends T> type, BlockEntityRendererProvider<T, S> provider) {
                        event.registerBlockEntityRenderer(type, provider);
                    }
                },
                DoorChestHubRenderers::register,
                SensorFieldRenderers::register);
    }
}
