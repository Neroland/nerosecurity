package za.co.neroland.nerosecurity.menu;

import java.util.UUID;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerosecurity.registry.NeroSecurityBlocks;
import za.co.neroland.nerosecurity.registry.NeroSecurityMenus;

/**
 * The hub screen's menu. It has no slots: its job is to bind the open screen to one installation so
 * every serverbound hub payload can be checked against it, and to close when the player walks away
 * from (or loses) the hub.
 */
public class SecurityHubMenu extends AbstractContainerMenu {

    /** Fewest ticks between two privacy requests or actions answered for one open menu. */
    public static final int PRIVACY_MIN_INTERVAL_TICKS = 10;

    private final @Nullable UUID installationId;
    private final ContainerLevelAccess access;
    /** Server: game time of the last answered privacy request or action ({@code Long.MIN_VALUE} = never). */
    private long lastPrivacyAt = Long.MIN_VALUE;

    /** Client-side factory: the client learns the installation from the snapshot, not from the menu. */
    public SecurityHubMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null, ContainerLevelAccess.NULL);
    }

    public SecurityHubMenu(int containerId, Inventory inventory, @Nullable UUID installationId,
            ContainerLevelAccess access) {
        super(NeroSecurityMenus.SECURITY_HUB.get(), containerId);
        this.installationId = installationId;
        this.access = access;
    }

    /** The installation this menu is bound to (server side; null on the client). */
    public @Nullable UUID installationId() {
        return installationId;
    }

    /**
     * Whether a privacy request or action may be answered now (at most one per
     * {@link #PRIVACY_MIN_INTERVAL_TICKS}); records the time when it may. A clock that went backwards resets.
     */
    public boolean tryPrivacy(long gameTime) {
        if (lastPrivacyAt != Long.MIN_VALUE && gameTime >= lastPrivacyAt
                && gameTime - lastPrivacyAt < PRIVACY_MIN_INTERVAL_TICKS) {
            return false;
        }
        lastPrivacyAt = gameTime;
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /** Valid while the hub block still stands within eight blocks of the player. */
    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, NeroSecurityBlocks.SECURITY_HUB.get());
    }
}
