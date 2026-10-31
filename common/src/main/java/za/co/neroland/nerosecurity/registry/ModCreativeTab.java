package za.co.neroland.nerosecurity.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.nerosecurity.NeroSecurityCommon;

/**
 * NeroSecurity's own creative tab, registered cross-loader through Neroland Core's
 * {@link RegistrationProvider} over the vanilla {@code CREATIVE_MODE_TAB} registry. Every Nero mod owns
 * its tab; nothing is added to Core's "Neroland" tab, which lists Core's own items only.
 *
 * <p>Each registration class contributes through {@link #add(Supplier)} from its
 * {@code addToCreativeTab()} during common init; suppliers resolve lazily when the tab is built.
 */
public final class ModCreativeTab {

    public static final RegistrationProvider<CreativeModeTab> TABS =
            RegistrationProvider.get(Registries.CREATIVE_MODE_TAB, NeroSecurityCommon.MOD_ID);

    /** Item suppliers in display order, drained into the tab when it is built. */
    private static final List<Supplier<? extends ItemLike>> CONTENTS = new ArrayList<>();

    // NOTE: vanilla CreativeModeTab.builder takes (Row, column); the no-arg overload and
    // withTabsBefore/After are NeoForge-only extensions, so they are avoided here (common = raw vanilla).
    public static final RegistryEntry<CreativeModeTab> NEROSECURITY = TABS.register("nerosecurity",
            key -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup.nerosecurity"))
                    .icon(() -> new ItemStack(NeroSecurityItems.SECURITY_HUB.get()))
                    .displayItems((params, output) -> CONTENTS.forEach(s -> output.accept(s.get())))
                    .build());

    private ModCreativeTab() {
    }

    /** Append an item to the NeroSecurity tab. Call during common init, before tabs are built. */
    public static void add(Supplier<? extends ItemLike> item) {
        CONTENTS.add(item);
    }

    /** Force class-load so the static tab registration runs. Call after every {@code addToCreativeTab()}. */
    public static void init() {
    }
}
