package za.co.neroland.nerosecurity.registry;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.item.SelectedHub;
import za.co.neroland.nerosecurity.keycard.KeycardData;

//? if >=1.20.5 {
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.component.CustomData;
import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;
import za.co.neroland.nerosecurity.NeroSecurityCommon;
//?}

/** Item data components and their legacy NBT representation. */
public final class NeroSecurityDataComponents {

    private static final String SELECTED_HUB_TAG = "nerosecurity:selected_hub";
    private static final String KEYCARD_TAG = "nerosecurity:keycard";

    //? if >=1.20.5 {
    private static final RegistrationProvider<DataComponentType<?>> COMPONENTS =
            RegistrationProvider.get(Registries.DATA_COMPONENT_TYPE, NeroSecurityCommon.MOD_ID);

    /** The linking tool's selected installation: an id and a display name, nothing else. */
    private static final RegistryEntry<DataComponentType<SelectedHub>> SELECTED_HUB =
            COMPONENTS.register("selected_hub", key -> DataComponentType.<SelectedHub>builder()
                    .persistent(SelectedHub.CODEC)
                    .networkSynchronized(SelectedHub.STREAM_CODEC)
                    .build());

    /**
     * An encoded keycard: installation id, level, serial, flags, issue time, a 16-byte tag and a display
     * label. Never a player identity. A blank card has no component.
     */
    private static final RegistryEntry<DataComponentType<KeycardData>> KEYCARD =
            COMPONENTS.register("keycard", key -> DataComponentType.<KeycardData>builder()
                    .persistent(KeycardData.CODEC)
                    .networkSynchronized(KeycardData.STREAM_CODEC)
                    .build());

    //?}

    private NeroSecurityDataComponents() {
    }

    /** Classload-forcing no-op. */
    public static void init() {
    }

    public static @Nullable KeycardData keycard(ItemStack stack) {
        //? if >=1.20.5 {
        KeycardData value = stack.get(KEYCARD.get());
        if (value != null) {
            return value;
        }
        //?}
        return read(legacyTag(stack), KEYCARD_TAG, KeycardData.CODEC);
    }

    public static boolean hasKeycard(ItemStack stack) {
        //? if >=1.20.5 {
        if (stack.has(KEYCARD.get())) {
            return true;
        }
        //?}
        CompoundTag tag = legacyTag(stack);
        return tag != null && tag.contains(KEYCARD_TAG);
    }

    public static void setKeycard(ItemStack stack, KeycardData card) {
        //? if >=1.20.5 {
        clearLegacyTag(stack, KEYCARD_TAG);
        stack.set(KEYCARD.get(), card);
        //?} else {
        /*write(stack, KEYCARD_TAG, KeycardData.CODEC, card);
        *///?}
    }

    public static void clearKeycard(ItemStack stack) {
        //? if >=1.20.5 {
        clearLegacyTag(stack, KEYCARD_TAG);
        stack.remove(KEYCARD.get());
        //?} else {
        /*stack.removeTagKey(KEYCARD_TAG);
        *///?}
    }

    public static @Nullable SelectedHub selectedHub(ItemStack stack) {
        //? if >=1.20.5 {
        SelectedHub value = stack.get(SELECTED_HUB.get());
        if (value != null) {
            return value;
        }
        //?}
        return read(legacyTag(stack), SELECTED_HUB_TAG, SelectedHub.CODEC);
    }

    public static void setSelectedHub(ItemStack stack, SelectedHub selection) {
        //? if >=1.20.5 {
        clearLegacyTag(stack, SELECTED_HUB_TAG);
        stack.set(SELECTED_HUB.get(), selection);
        //?} else {
        /*write(stack, SELECTED_HUB_TAG, SelectedHub.CODEC, selection);
        *///?}
    }

    public static void clearSelectedHub(ItemStack stack) {
        //? if >=1.20.5 {
        clearLegacyTag(stack, SELECTED_HUB_TAG);
        stack.remove(SELECTED_HUB.get());
        //?} else {
        /*stack.removeTagKey(SELECTED_HUB_TAG);
        *///?}
    }

    private static @Nullable CompoundTag legacyTag(ItemStack stack) {
        //? if >=1.20.5 {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag();
        //?} else {
        /*return stack.getTag();
        *///?}
    }

    private static <T> @Nullable T read(@Nullable CompoundTag tag, String key, Codec<T> codec) {
        var value = tag == null ? null : tag.get(key);
        return value == null ? null : codec.parse(NbtOps.INSTANCE, value).result().orElse(null);
    }

    //? if >=1.20.5 {
    private static void clearLegacyTag(ItemStack stack, String key) {
        CompoundTag tag = legacyTag(stack);
        if (tag != null && tag.contains(key)) {
            tag.remove(key);
            if (tag.isEmpty()) {
                stack.remove(DataComponents.CUSTOM_DATA);
            } else {
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            }
        }
    }
    //?} else {
    /*private static <T> void write(ItemStack stack, String key, Codec<T> codec, T value) {
        var data = codec.encodeStart(NbtOps.INSTANCE, value).result()
                .orElseThrow(() -> new IllegalArgumentException("Invalid NeroSecurity item data"));
        stack.getOrCreateTag().put(key, data);
    }
    *///?}
}
