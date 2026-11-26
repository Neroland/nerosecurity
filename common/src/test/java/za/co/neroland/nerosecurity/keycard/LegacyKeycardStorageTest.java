package za.co.neroland.nerosecurity.keycard;

//? if <1.20.5 {
/*import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import za.co.neroland.nerosecurity.access.AccessLevel;
import za.co.neroland.nerosecurity.registry.NeroSecurityDataComponents;

class LegacyKeycardStorageTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void roundTripsSignedCardThroughAnItemSaveAndClearsIt() {
        ItemStack stack = new ItemStack(Items.PAPER);
        UUID installation = UUID.randomUUID();
        byte[] signature = new byte[KeycardData.SIGNATURE_BYTES];
        signature[0] = 42;
        NeroSecurityDataComponents.setKeycard(stack,
                new KeycardData(installation, AccessLevel.OFFICER, 42, true, false, 55, signature, "Test hub"));

        ItemStack loaded = ItemStack.of(stack.save(new CompoundTag()));
        KeycardData card = NeroSecurityDataComponents.keycard(loaded);
        assertEquals(installation, card.installationId());
        assertEquals(AccessLevel.OFFICER, card.level());
        assertEquals(42, card.serial());
        assertTrue(card.encrypted());
        assertEquals("Test hub", card.label());
        assertArrayEquals(signature, card.signature());

        NeroSecurityDataComponents.clearKeycard(loaded);
        assertFalse(NeroSecurityDataComponents.hasKeycard(loaded));
        assertNull(NeroSecurityDataComponents.keycard(loaded));
    }

    @Test
    void rejectsMalformedCardWithoutTreatingItAsBlank() {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.getOrCreateTag().putString("nerosecurity:keycard", "invalid");
        assertNull(NeroSecurityDataComponents.keycard(stack));
        assertTrue(NeroSecurityDataComponents.hasKeycard(stack));
    }
}
*///?}
