package za.co.neroland.nerosecurity.economy;

import java.util.Set;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;

import za.co.neroland.neroeconomy.api.EconomyCaller;
import za.co.neroland.neroeconomy.api.EconomyResult;
import za.co.neroland.neroeconomy.api.SiblingEconomy;

import za.co.neroland.nerosecurity.NeroSecurityCommon;

/**
 * The <b>only</b> class in NeroSecurity that imports {@code za.co.neroland.neroeconomy}. NeroEconomy is compiled
 * {@code compileOnly} and is optional, so this class is loaded only after {@link SecurityEconomy#init()} has seen
 * {@code isModLoaded("neroeconomy")}; nothing else may reference NeroEconomy types.
 *
 * <p>Privacy (POPIA/GDPR): takes only the UUID the caller already holds, passes it straight to NeroEconomy,
 * and never logs it.
 */
final class NeroEconomyBridge {

    /** Claimed once at common init; {@code null} until then (and forever when the claim failed). */
    private static volatile EconomyCaller caller;

    private NeroEconomyBridge() {
    }

    /**
     * NeroEconomy's cross-mod API version, read reflectively so a newer NeroEconomy is not compared against a
     * constant inlined at compile time. 0 when the field is missing (a NeroEconomy older than 0.3.0).
     */
    static int apiVersion() {
        try {
            return SiblingEconomy.class.getField("API_VERSION").getInt(null);
        } catch (ReflectiveOperationException missing) {
            return 0;
        }
    }

    /** Claims NeroSecurity's handle with its closed set of reason codes. Common init only. */
    static void claim() {
        caller = SiblingEconomy.claim(NeroSecurityCommon.MOD_ID, Set.of(SecurityEconomy.REASON_TIER));
    }

    static EconomyOutcome charge(MinecraftServer server, UUID player, long amount, String reason, String requestKey) {
        EconomyCaller handle = caller;
        if (handle == null) return EconomyOutcome.absentOutcome();
        return outcome(handle.charge(server, player, amount, reason, requestKey));
    }

    private static EconomyOutcome outcome(EconomyResult result) {
        if (result == null) return EconomyOutcome.invalid();
        return result.ok() ? EconomyOutcome.applied(result.translationKey())
                : EconomyOutcome.refused(result.translationKey());
    }
}
