package za.co.neroland.nerosecurity.economy;

import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerosecurity.NeroSecurityCommon;
import za.co.neroland.nerosecurity.platform.Services;

/**
 * Paid security tiers through NeroEconomy, which is optional. Charges are a credit <b>sink</b> into
 * NeroEconomy's Reserve; NeroSecurity never pays out for guarding.
 *
 * <p><b>Rules every security feature must keep</b> (see the README's developer notes):
 * <ul>
 *   <li>An owner can always reach their own economy blocks (Player Shop, Auto-Trader, Market Board listing),
 *       and any player can use a Bank, Trading or Exchange Terminal, whatever lock, guard or zone applies.</li>
 *   <li>Never break a block to enforce a restriction.</li>
 *   <li>Never inspect economy state (balances, holdings) to decide access.</li>
 *   <li>Charge only when the player chooses or renews a tier, never silently. A failed renewal
 *       <em>downgrades</em> protection; it never removes the owner's access to their own blocks.</li>
 * </ul>
 *
 * <p>With NeroEconomy absent the outcome is {@link EconomyOutcome.Status#ABSENT}: tiers are free or
 * configured another way. Server thread only. Privacy (POPIA/GDPR): nothing here logs who paid for which
 * tier, and a request key names the tier period (for example {@code "tier:2:period:2026-10"}), never a player.
 */
public final class SecurityEconomy {

    /** Reason code for a security tier purchase or renewal (player to the Reserve). */
    public static final String REASON_TIER = "security_tier";

    /** Whether NeroEconomy is installed, new enough and claimed. */
    private static volatile boolean active;

    private SecurityEconomy() {
    }

    /**
     * Hooks NeroEconomy when it is installed. Call once from common init (claims close when the first server
     * starts). With NeroEconomy absent, or older than API version 1, this does nothing and every call below
     * returns {@link EconomyOutcome.Status#ABSENT}.
     */
    public static void init() {
        if (active || !Services.PLATFORM.isModLoaded("neroeconomy")) return;
        try {
            int api = NeroEconomyBridge.apiVersion();
            if (api < 1) {
                NeroSecurityCommon.LOGGER.info("[NeroSecurity] NeroEconomy is too old (API version {}); paid security tiers stay off.", api);
                return;
            }
            NeroEconomyBridge.claim();
            active = true;
            NeroSecurityCommon.LOGGER.info("[NeroSecurity] NeroEconomy found; paid security tiers are available.");
        } catch (RuntimeException | LinkageError failure) {
            NeroSecurityCommon.LOGGER.warn("[NeroSecurity] Could not hook NeroEconomy ({}); paid security tiers stay off.",
                    failure.getClass().getSimpleName());
        }
    }

    /** Whether NeroEconomy is hooked, so paid features can be offered. */
    public static boolean isActive() {
        return active;
    }

    /**
     * Charges for a security tier the player chose or renewed. Upgrade only when {@link EconomyOutcome#ok()};
     * on a refused renewal, downgrade (never lock the owner out) and show {@link EconomyOutcome#message()}.
     *
     * @param player     the player paying
     * @param price      the tier's price in whole credits, more than 0
     * @param requestKey the tier period's key (see {@link RequestKeys}), or {@code null} for no replay protection
     */
    public static EconomyOutcome chargeTier(ServerPlayer player, long price, String requestKey) {
        if (!active) return EconomyOutcome.absentOutcome();
        if (player == null || price <= 0L || !RequestKeys.isValidOrNull(requestKey)) return EconomyOutcome.invalid();
        return NeroEconomyBridge.charge(player.level().getServer(), player.getUUID(), price, REASON_TIER, requestKey);
    }
}
