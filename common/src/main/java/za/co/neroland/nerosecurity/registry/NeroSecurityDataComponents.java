package za.co.neroland.nerosecurity.registry;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.item.SelectedHub;
import za.co.neroland.nerosecurity.keycard.KeycardData;

/** Item data components through Neroland Core's {@link RegistrationProvider}. */
public final class NeroSecurityDataComponents {

    public static final RegistrationProvider<DataComponentType<?>> COMPONENTS =
            RegistrationProvider.get(Registries.DATA_COMPONENT_TYPE, NeroSecurityCommon.MOD_ID);

    /** The linking tool's selected installation: an id and a display name, nothing else. */
    public static final RegistryEntry<DataComponentType<SelectedHub>> SELECTED_HUB =
            COMPONENTS.register("selected_hub", key -> DataComponentType.<SelectedHub>builder()
                    .persistent(SelectedHub.CODEC)
                    .networkSynchronized(SelectedHub.STREAM_CODEC)
                    .build());

    /**
     * An encoded keycard: installation id, level, serial, flags, issue time, a 16-byte tag and a display
     * label. Never a player identity. A blank card has no component.
     */
    public static final RegistryEntry<DataComponentType<KeycardData>> KEYCARD =
            COMPONENTS.register("keycard", key -> DataComponentType.<KeycardData>builder()
                    .persistent(KeycardData.CODEC)
                    .networkSynchronized(KeycardData.STREAM_CODEC)
                    .build());

    private NeroSecurityDataComponents() {
    }

    /** Classload-forcing no-op. */
    public static void init() {
    }
}
